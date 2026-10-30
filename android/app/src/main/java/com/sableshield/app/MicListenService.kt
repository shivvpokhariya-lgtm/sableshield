package com.sableshield.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer

/**
 * Foreground mic service. Listens through the MICROPHONE while the call
 * is on speaker — that is the only audio path Android gives a third-party
 * app. Audio is transcribed ON-DEVICE by Vosk and never leaves the phone;
 * only transcript text is analysed in memory, then discarded.
 *
 * Language modes (EXTRA_LANG):
 *  - "en": English model only (also catches Roman Hinglish through the
 *    engine's Hinglish patterns).
 *  - "hi": Hindi model only (Devanagari output).
 *  - "auto": BOTH models decode the same mic stream in parallel — the
 *    English model hears English + Roman Hinglish, the Hindi model hears
 *    Devanagari Hindi. Whichever model produces text wins that line.
 *
 * Silence rule: while listening, NOTHING appears on the call screen.
 * The overlay banner is created hidden and slides in only the moment
 * the first scam pattern is found, together with a heads-up
 * notification reading "This is likely to be a scam". The mandatory
 * foreground-service notification stays low-priority and silent.
 */
class MicListenService : Service() {

    companion object {
        const val ACTION_PARTIAL = "com.sableshield.app.ACTION_PARTIAL"
        const val ACTION_FINAL = "com.sableshield.app.ACTION_FINAL"
        const val ACTION_STATUS = "com.sableshield.app.ACTION_STATUS"
        const val EXTRA_TEXT = "text"
        const val EXTRA_LANG = "lang"
        const val CHANNEL_LISTEN = "sableshield_listen"
        const val CHANNEL_SCAM = "sableshield_scam"
        const val NOTIF_ID = 1001
        const val NOTIF_SCAM_ID = 2002
        const val SAMPLE_RATE = 16000

        fun startIntent(context: Context, lang: String): Intent =
            Intent(context, MicListenService::class.java).putExtra(EXTRA_LANG, lang)
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val serviceTranscript = StringBuilder()

    @Volatile private var recording = false
    private var recordThread: Thread? = null
    private var audioRecord: AudioRecord? = null
    private var models: List<Model> = emptyList()
    private var recognizers: List<Recognizer> = emptyList()
    private var lastPartial: String = ""

    // Delivery signal (pace + volume) and the 20-second silence rule.
    // This is NOT emotion reading: it only notes fast, loud speech,
    // which is the pressured-delivery pattern of most scam calls.
    @Volatile private var lastSpeechMs: Long = 0L
    private var lastFinalMs: Long = 0L
    @Volatile private var silenceNoted: Boolean = false
    private var chunkEnergy: Double = 0.0
    private var chunkSamples: Long = 0L
    private val silenceRunnable = object : Runnable {
        override fun run() {
            if (recording) {
                val quietFor = System.currentTimeMillis() - lastSpeechMs
                if (quietFor > 20_000 && !silenceNoted) {
                    silenceNoted = true
                    broadcastStatus("Still listening — 20 seconds with no speech. Detection stays on for the whole call.")
                }
                mainHandler.postDelayed(this, 5000)
            }
        }
    }

    // Sliding warning banner above the call screen (needs the overlay
    // permission). Created HIDDEN — it only slides in on scam risk.
    private var windowManager: WindowManager? = null
    private var overlayView: TextView? = null
    private var overlayVisible = false
    private var scamNotified = false
    private var gemmaFired = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val lang = intent?.getStringExtra(EXTRA_LANG)
            ?: getSharedPreferences("sableshield", Context.MODE_PRIVATE)
                .getString("lang", ModelDownloader.LANG_AUTO)
            ?: ModelDownloader.LANG_AUTO
        startForegroundNotification()
        // The guard is up: clear any call-start notification (tap or
        // full-screen) that summoned it.
        try {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(CallStateReceiver.NOTIF_CALL_ID)
            nm.cancel(CallStateReceiver.NOTIF_FSI_ID)
        } catch (ignored: Exception) {
        }
        lastSpeechMs = System.currentTimeMillis()
        lastFinalMs = 0L
        silenceNoted = false
        chunkEnergy = 0.0
        chunkSamples = 0L
        mainHandler.removeCallbacks(silenceRunnable)
        mainHandler.postDelayed(silenceRunnable, 5000)
        stopRecording()
        serviceTranscript.clear()
        lastPartial = ""
        scamNotified = false
        gemmaFired = false
        // FIX (2026-10-30): the banner used to be created HIDDEN and only
        // appeared on the first scam flag, so on a real call the user saw
        // nothing and concluded listening never started. It now shows
        // "listening" the moment a call starts, and turns into the scam
        // warning when a pattern is found.
        createListeningOverlay()

        val wanted = when (lang) {
            ModelDownloader.LANG_HI -> listOf(ModelDownloader.LANG_HI)
            ModelDownloader.LANG_EN -> listOf(ModelDownloader.LANG_EN)
            else -> listOf(ModelDownloader.LANG_EN, ModelDownloader.LANG_HI)
        }
        val ready = wanted.filter { ModelDownloader.isDownloaded(this, it) }
        if (ready.isEmpty()) {
            broadcastStatus("Voice model not found — tap Download first (Auto needs English and/or Hindi).")
            stopSelf()
            return START_NOT_STICKY
        }
        broadcastStatus("LIVE — listening ($lang). Keep the call on speaker.")
        recording = true
        recordThread = Thread {
            try {
                // FIX (2026-10-30): load each language separately. The old
                // code loaded all models in one map{} — one broken model
                // (e.g. a partial download throwing ENOENT) killed the
                // whole service silently. Now a broken language is reported
                // by name and the other language keeps listening.
                val loadedModels = mutableListOf<Model>()
                val loadedLangs = mutableListOf<String>()
                for (langCode in ready) {
                    try {
                        loadedModels.add(Model(ModelDownloader.modelDir(this, langCode).absolutePath))
                        loadedLangs.add(langCode)
                    } catch (e: Exception) {
                        val missing = ModelDownloader.missingFiles(
                            ModelDownloader.modelDir(this, langCode)
                        )
                        broadcastStatus(
                            "Model ($langCode) failed to load (${e.message}" +
                                (if (missing.isNotEmpty()) "; missing: ${missing.joinToString(", ")}" else "") +
                                ") — re-download it from the app."
                        )
                    }
                }
                if (loadedModels.isEmpty()) {
                    broadcastStatus("Voice model files are incomplete — tap Download in the app to re-download, then call again.")
                    mainHandler.post { stopSelf() }
                    return@Thread
                }
                if (loadedLangs.size < ready.size) {
                    broadcastStatus("Listening with ${loadedLangs.joinToString(", ")} only.")
                }
                val loadedRecognizers = loadedModels.map { Recognizer(it, SAMPLE_RATE.toFloat()) }
                models = loadedModels
                recognizers = loadedRecognizers
                val minBuf = AudioRecord.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                val recorder = AudioRecord.Builder()
                    .setAudioSource(MediaRecorder.AudioSource.MIC)
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .build()
                    )
                    .setBufferSizeInBytes(minBuf * 2)
                    .build()
                audioRecord = recorder
                if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                    broadcastStatus("Mic did not start — check the permission.")
                    mainHandler.post { stopSelf() }
                    return@Thread
                }
                recorder.startRecording()
                val buf = ByteArray(4096)
                while (recording) {
                    val n = recorder.read(buf, 0, buf.size)
                    if (n <= 0) continue
                    // Delivery signal: sum of squares of this chunk's
                    // 16-bit samples, for the pace + volume note.
                    try {
                        var s = 0.0
                        var i = 0
                        while (i + 1 < n) {
                            var sample = (buf[i].toInt() and 0xFF) or
                                (buf[i + 1].toInt() shl 8)
                            if (sample >= 32768) sample -= 65536
                            s += sample.toDouble() * sample.toDouble()
                            i += 2
                        }
                        chunkEnergy += s
                        chunkSamples += n / 2
                    } catch (ignored: Exception) {
                    }
                    for (rec in loadedRecognizers) {
                        try {
                            if (rec.acceptWaveForm(buf, n)) {
                                val text = try {
                                    JSONObject(rec.result).optString("text", "")
                                } catch (e: Exception) {
                                    ""
                                }
                                if (text.isNotEmpty()) onFinalText(text)
                            } else {
                                val partial = try {
                                    JSONObject(rec.partialResult).optString("partial", "")
                                } catch (e: Exception) {
                                    ""
                                }
                                if (partial.isNotEmpty() && partial != lastPartial) {
                                    lastPartial = partial
                                    lastSpeechMs = System.currentTimeMillis()
                                    broadcast(ACTION_PARTIAL, partial)
                                }
                            }
                        } catch (ignored: Exception) {
                        }
                    }
                }
                // Trailing words when the loop stops.
                for (rec in loadedRecognizers) {
                    try {
                        val tail = JSONObject(rec.finalResult).optString("text", "")
                        if (tail.isNotEmpty()) onFinalText(tail)
                    } catch (ignored: Exception) {
                    }
                }
            } catch (e: SecurityException) {
                broadcastStatus("Mic permission missing — grant it and try again.")
                mainHandler.post { stopSelf() }
            } catch (e: Exception) {
                broadcastStatus("Model load failed: ${e.message}")
                mainHandler.post { stopSelf() }
            }
        }.apply { start() }
        return START_STICKY
    }

    private fun startForegroundNotification() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_LISTEN,
                getString(R.string.notif_channel),
                NotificationManager.IMPORTANCE_LOW
            )
            nm.createNotificationChannel(ch)
        }
        val notif = NotificationCompat.Builder(this, CHANNEL_LISTEN)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setSilent(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIF_ID, notif,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIF_ID, notif)
        }
    }

    private fun onFinalText(text: String) {
        serviceTranscript.append(text).append(" ")
        lastSpeechMs = System.currentTimeMillis()
        silenceNoted = false
        // Pressured delivery: fast AND loud in the same chunk.
        // Conservative thresholds (pauses count against both), so this
        // fires only on clear cases — never presented as an emotion.
        try {
            val now = System.currentTimeMillis()
            val words = text.trim().split(" ", "\t", "\n").filter { it.isNotEmpty() }.size
            val dt = if (lastFinalMs > 0) now - lastFinalMs else 0L
            val wpm = if (dt > 300) words * 60000.0 / dt else 0.0
            val rms = if (chunkSamples > 0) kotlin.math.sqrt(chunkEnergy / chunkSamples) else 0.0
            if (wpm > 180 && rms > 8000) {
                broadcastStatus("Delivery note: fast, loud speech — pressured delivery pattern.")
            }
        } catch (ignored: Exception) {
        }
        lastFinalMs = System.currentTimeMillis()
        chunkEnergy = 0.0
        chunkSamples = 0L
        broadcast(ACTION_FINAL, text)
        val verdict = ScamEngine.analyze(serviceTranscript.toString())
        if (verdict.type == "scam") showScamWarning(verdict)
    }

    /**
     * First scam pattern found: slide the warning banner in over the
     * call screen, fire one heads-up notification, and (once, if the
     * user typed a key) ask Gemma 4 to confirm on the transcript.
     */
    private fun showScamWarning(verdict: ScamEngine.Verdict) {
        val firstFlag = verdict.flags.firstOrNull() ?: ""
        // FIX (2026-10-30): save the scam report now, while the call is
        // live. When the call ends the service stops and the transcript
        // is gone — without this, the user opened the app after the call
        // and found no report at all. MainActivity shows this on next open.
        try {
            getSharedPreferences("sableshield", Context.MODE_PRIVATE).edit()
                .putString("last_type", "scam")
                .putString("last_report", "${verdict.score} — ${verdict.title}")
                .putString("last_flags", verdict.flags.joinToString("\n") { "• $it" })
                .apply()
        } catch (ignored: Exception) {
        }
        mainHandler.post {
            overlayView?.let { view ->
                view.text = "This is likely to be a scam\n${verdict.score}\n$firstFlag"
                view.setBackgroundColor(Color.parseColor("#800020"))
                if (!overlayVisible) {
                    overlayVisible = true
                    view.visibility = TextView.VISIBLE
                    view.translationY = -300f
                    view.alpha = 0f
                    view.animate()
                        .translationY(0f)
                        .alpha(1f)
                        .setDuration(400)
                        .start()
                }
            }
        }
        if (!scamNotified) {
            scamNotified = true
            postScamNotification(verdict, firstFlag)
        }
        if (!gemmaFired) {
            val key = getSharedPreferences("sableshield", Context.MODE_PRIVATE)
                .getString("api_key", "") ?: ""
            if (key.isNotEmpty()) {
                gemmaFired = true
                val transcript = serviceTranscript.toString()
                Thread {
                    val result = GemmaClient.analyze(key, transcript)
                    val gv = result.verdict
                    if (gv != null && gv.type == "scam") {
                        mainHandler.post {
                            overlayView?.let {
                                it.text = "This is likely to be a scam\n${verdict.score}\nGemma: ${gv.score}"
                            }
                        }
                        broadcastStatus("Gemma 4 confirms: ${gv.score}")
                    }
                }.start()
            }
        }
    }

    private fun postScamNotification(verdict: ScamEngine.Verdict, firstFlag: String) {
        try {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val ch = NotificationChannel(
                    CHANNEL_SCAM,
                    getString(R.string.notif_channel),
                    NotificationManager.IMPORTANCE_HIGH
                )
                nm.createNotificationChannel(ch)
            }
            val open = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pending = PendingIntent.getActivity(
                this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val text = if (firstFlag.isNotEmpty()) "${verdict.score} — $firstFlag" else verdict.score
            val notif = NotificationCompat.Builder(this, CHANNEL_SCAM)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle(getString(R.string.notif_scam_title))
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setContentIntent(pending)
                .setAutoCancel(true)
                .build()
            nm.notify(NOTIF_SCAM_ID, notif)
        } catch (ignored: Exception) {
        }
    }

    private fun createListeningOverlay() {
        if (!Settings.canDrawOverlays(this)) return
        try {
            val wm = getSystemService(WINDOW_SERVICE) as WindowManager
            windowManager = wm
            val view = TextView(this).apply {
                text = "SableShield is listening — keep the call on speaker"
                setTextColor(Color.WHITE)
                textSize = 16f
                setPadding(32, 24, 32, 24)
                setBackgroundColor(Color.parseColor("#1A1A1A"))
                visibility = TextView.VISIBLE
            }
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                y = 200
            }
            wm.addView(view, params)
            overlayView = view
            overlayVisible = true // listening banner is visible from call start
        } catch (ignored: Exception) {
            overlayView = null
        }
    }

    private fun stopRecording() {
        recording = false
        try {
            audioRecord?.stop()
        } catch (ignored: Exception) {
        }
        try {
            audioRecord?.release()
        } catch (ignored: Exception) {
        }
        audioRecord = null
        for (rec in recognizers) {
            try {
                rec.close()
            } catch (ignored: Exception) {
            }
        }
        recognizers = emptyList()
        for (model in models) {
            try {
                model.close()
            } catch (ignored: Exception) {
            }
        }
        models = emptyList()
        recordThread = null
    }

    private fun broadcast(action: String, text: String) {
        val i = Intent(action).putExtra(EXTRA_TEXT, text)
        i.setPackage(packageName)
        sendBroadcast(i)
    }

    private fun broadcastStatus(text: String) = broadcast(ACTION_STATUS, text)

    override fun onDestroy() {
        mainHandler.removeCallbacks(silenceRunnable)
        stopRecording()
        try {
            overlayView?.let { windowManager?.removeView(it) }
        } catch (ignored: Exception) {
        }
        overlayView = null
        windowManager = null
        overlayVisible = false
        super.onDestroy()
    }
}
