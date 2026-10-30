package com.sableshield.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.telephony.TelephonyManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/**
 * Call-state detection only. PHONE_STATE carries OFFHOOK / IDLE / RINGING
 * — never audio.
 *
 * On OFFHOOK (a call is now active):
 *  - If the mic is granted, a voice model is downloaded, and the overlay
 *    permission is on, the translucent trampoline activity is launched
 *    directly AND via a full-screen-intent notification. The direct
 *    start works up to Android 14; on Android 15+ the system drops it
 *    silently, and the full-screen intent (the path used by incoming
 *    calls and alarms) is what actually opens the trampoline. The trampoline starts the mic service from the foreground
 *    and finishes at once — the call screen never changes, and
 *    listening starts automatically, in English + Hindi + Hinglish
 *    when Auto is selected.
 *  - Otherwise a high-priority tap-to-start notification is posted:
 *    one tap opens the app, which asks for the missing permissions
 *    and starts listening.
 * On IDLE the service stops itself and the call notification is
 * cancelled. Nothing is ever recorded or stored — transcripts live
 * only in memory for the duration of the call.
 */
class CallStateReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_START_LISTENING = "com.sableshield.app.START_LISTENING"
        const val CHANNEL_CALL = "sableshield_call"
        const val NOTIF_CALL_ID = 2001
        const val NOTIF_FSI_ID = 2003
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        when (state) {
            // Save the incoming number for the in-app Report flow.
            // (Needs READ_CALL_LOG on newer Android to be delivered.)
            TelephonyManager.EXTRA_STATE_RINGING -> {
                val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
                if (!number.isNullOrEmpty()) {
                    context.getSharedPreferences("sableshield", Context.MODE_PRIVATE)
                        .edit().putString("last_caller", number).apply()
                }
            }
            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                val prefs = context.getSharedPreferences("sableshield", Context.MODE_PRIVATE)
                val lang = prefs.getString("lang", ModelDownloader.LANG_AUTO)
                    ?: ModelDownloader.LANG_AUTO
                val micOk = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
                val modelOk = when (lang) {
                    ModelDownloader.LANG_HI -> ModelDownloader.isDownloaded(context, ModelDownloader.LANG_HI)
                    ModelDownloader.LANG_EN -> ModelDownloader.isDownloaded(context, ModelDownloader.LANG_EN)
                    else -> ModelDownloader.isDownloaded(context, ModelDownloader.LANG_EN) ||
                        ModelDownloader.isDownloaded(context, ModelDownloader.LANG_HI)
                }
                if (micOk && modelOk && Settings.canDrawOverlays(context)) {
                    // Automatic path: translucent trampoline, no UI change.
                    // The direct start works up to Android 14; on Android
                    // 15+ the background-activity ban drops it silently, so
                    // a full-screen-intent notification is posted as well —
                    // that intent IS allowed to open the trampoline, which
                    // starts the mic service and cancels both notifications.
                    val tramp = Intent(context, CallTrampolineActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        putExtra(MicListenService.EXTRA_LANG, lang)
                    }
                    try {
                        context.startActivity(tramp)
                    } catch (ignored: Exception) {
                    }
                    postFullScreenNotification(context, tramp)
                } else {
                    // FIX (2026-10-30): the old tap notification had no
                    // priority/category, so on many phones it arrived
                    // silently (or not at all without notification
                    // permission) and the user saw nothing on a call.
                    postTapNotification(context, modelOk)
                }
            }
            TelephonyManager.EXTRA_STATE_IDLE -> {
                context.stopService(Intent(context, MicListenService::class.java))
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.cancel(NOTIF_CALL_ID)
                nm.cancel(NOTIF_FSI_ID)
            }
        }
    }

    private fun postTapNotification(context: Context, modelOk: Boolean = true) {
        val open = Intent(context, MainActivity::class.java).apply {
            action = ACTION_START_LISTENING
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context, 0, open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_CALL,
                context.getString(R.string.notif_channel),
                NotificationManager.IMPORTANCE_HIGH
            )
            nm.createNotificationChannel(ch)
        }
        val notif = NotificationCompat.Builder(context, CHANNEL_CALL)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(context.getString(R.string.notif_call_title))
            .setContentText(
                if (!modelOk) "Voice model not set up yet — tap to open the app, it installs itself and starts listening."
                else context.getString(R.string.notif_call_text)
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        nm.notify(NOTIF_CALL_ID, notif)
    }

    private fun postFullScreenNotification(context: Context, tramp: Intent) {
        val pending = PendingIntent.getActivity(
            context, 1, tramp,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_CALL,
                context.getString(R.string.notif_channel),
                NotificationManager.IMPORTANCE_HIGH
            )
            nm.createNotificationChannel(ch)
        }
        val notif = NotificationCompat.Builder(context, CHANNEL_CALL)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(context.getString(R.string.notif_call_title))
            .setContentText(context.getString(R.string.notif_fsi_text))
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setFullScreenIntent(pending, true)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        nm.notify(NOTIF_FSI_ID, notif)
    }
}
