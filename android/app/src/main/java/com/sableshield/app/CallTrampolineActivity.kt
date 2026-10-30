package com.sableshield.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.core.content.ContextCompat

/**
 * Invisible trampoline for automatic call listening.
 *
 * Android 12+ blocks starting a foreground service from a background
 * broadcast receiver — but a foreground activity may start one. The
 * receiver only launches this activity when the overlay permission is
 * granted (a hard exemption from the background-activity-start ban),
 * and this activity is fully translucent with no UI at all: the call
 * screen stays visible underneath, nothing flashes, and it finishes
 * the instant the mic service is running. If anything about the
 * direct start fails, it falls back to opening the main app instead.
 */
class CallTrampolineActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val lang = intent?.getStringExtra(MicListenService.EXTRA_LANG)
            ?: ModelDownloader.LANG_AUTO
        try {
            ContextCompat.startForegroundService(
                this, MicListenService.startIntent(this, lang)
            )
        } catch (e: Exception) {
            val open = Intent(this, MainActivity::class.java).apply {
                action = CallStateReceiver.ACTION_START_LISTENING
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            startActivity(open)
        }
        finish()
    }
}
