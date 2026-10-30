package com.sableshield.app

import android.Manifest
import android.animation.ValueAnimator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.CallLog
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.text.DateFormat
import java.util.Date

/**
 * SableShield Android — the main app screen, reached from the shield-only
 * landing page with a slide transition.
 *
 * Theme: exactly like the deployed site (bg #050505, burgundy #800020
 * buttons, hot-pink #FF2E63 accent, /shield hero with the no-sign
 * runner logo, starfield, pill badges). Pure English throughout.
 *
 * First run shows a plain start page: an optional demo, a privacy
 * policy checkbox, and one setup step (permissions + overlay).
 * Once the policy is accepted and mic + overlay are granted, the
 * main screen appears.
 *
 * Two ways to use it live:
 *  1. API KEY + Analyse — paste a Gemini key, paste/type what a caller
 *     said, live Gemma 4 verdict. No key → rule-based verdict.
 *     The key is typed by the user and kept in private app prefs only.
 *  2. LIVE MIC / automatic calls — real call on speaker: on-device
 *     Vosk transcription (English / Hindi / Auto = both + Hinglish)
 *     + rule engine, live flags + verdict. On a real call the service
 *     starts automatically and stays silent until the first scam
 *     pattern slides a warning in over the call screen.
 *
 * Plus the fledged loop: call-log permission → recent calls list with
 * contact names → reported numbers flagged → manual report adds numbers
 * to the on-device reported set. Overlay permission lets the live
 * verdict float above the call screen.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var statusText: TextView
    private lateinit var transcriptText: TextView
    private lateinit var flagsText: TextView
    private lateinit var flagsLabel: TextView
    private lateinit var verdictText: TextView
    private lateinit var apiKeyEdit: EditText
    private lateinit var liveTextEdit: EditText
    private lateinit var langGroup: RadioGroup
    private lateinit var recentCallsText: TextView
    private lateinit var reportNumberEdit: EditText
    private lateinit var permissionGate: View
    private lateinit var mainScroll: View
    private lateinit var demoCard: View
    private lateinit var demoTranscriptText: TextView
    private lateinit var demoVerdictText: TextView
    private lateinit var demoFlagsText: TextView
    private lateinit var cbPolicy: CheckBox
    private lateinit var gateStatusText: TextView

    private val transcriptBuffer = StringBuilder()
    private val handler = Handler(Looper.getMainLooper())
    private var flickerAnimator: ValueAnimator? = null
    private var demoRunnable: Runnable? = null

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
            val audioOk = granted[Manifest.permission.RECORD_AUDIO] == true
            postGateStatus(
                if (audioOk) "Permissions granted — finishing setup…"
                else "Live listening needs the mic permission — tap Start again to grant it."
            )
            loadRecentCalls()
            refreshGate()
        }

    private val serviceReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                MicListenService.ACTION_PARTIAL -> {
                    val p = intent.getStringExtra(MicListenService.EXTRA_TEXT) ?: ""
                    if (p.isNotEmpty()) setStatus("Listening: $p")
                }
                MicListenService.ACTION_FINAL -> {
                    val text = intent.getStringExtra(MicListenService.EXTRA_TEXT) ?: ""
                    if (text.isEmpty()) return
                    transcriptBuffer.append(text).append(" ")
                    transcriptText.text = transcriptBuffer.toString()
                    showVerdict(ScamEngine.analyze(transcriptBuffer.toString()), via = "rules (on-device)")
                }
                MicListenService.ACTION_STATUS -> {
                    setStatus(intent.getStringExtra(MicListenService.EXTRA_TEXT) ?: "")
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = getSharedPreferences("sableshield", Context.MODE_PRIVATE)

        statusText = findViewById(R.id.statusText)
        transcriptText = findViewById(R.id.transcriptText)
        flagsText = findViewById(R.id.flagsText)
        flagsLabel = findViewById(R.id.flagsLabel)
        verdictText = findViewById(R.id.verdictText)
        apiKeyEdit = findViewById(R.id.apiKeyEdit)
        liveTextEdit = findViewById(R.id.liveTextEdit)
        langGroup = findViewById(R.id.langGroup)
        recentCallsText = findViewById(R.id.recentCallsText)
        reportNumberEdit = findViewById(R.id.reportNumberEdit)
        permissionGate = findViewById(R.id.permissionGate)
        mainScroll = findViewById(R.id.mainScroll)
        demoCard = findViewById(R.id.demoCard)
        demoTranscriptText = findViewById(R.id.demoTranscriptText)
        demoVerdictText = findViewById(R.id.demoVerdictText)
        demoFlagsText = findViewById(R.id.demoFlagsText)
        cbPolicy = findViewById(R.id.cbPolicy)
        gateStatusText = findViewById(R.id.gateStatusText)

        apiKeyEdit.setText(prefs.getString("api_key", ""))
        val savedLang = prefs.getString("lang", ModelDownloader.LANG_AUTO)
        findViewById<RadioButton>(
            when (savedLang) {
                ModelDownloader.LANG_HI -> R.id.rbHindi
                ModelDownloader.LANG_EN -> R.id.rbEnglish
                else -> R.id.rbAuto
            }
        ).isChecked = true

        langGroup.setOnCheckedChangeListener { _, checkedId ->
            val lang = when (checkedId) {
                R.id.rbHindi -> ModelDownloader.LANG_HI
                R.id.rbEnglish -> ModelDownloader.LANG_EN
                else -> ModelDownloader.LANG_AUTO
            }
            prefs.edit().putString("lang", lang).apply()
            setStatus("Language: $lang — the model download and listening will use this language.")
        }

        findViewById<Button>(R.id.btnDownload).setOnClickListener { downloadModel() }
        findViewById<Button>(R.id.btnStart).setOnClickListener { startListening() }
        findViewById<Button>(R.id.btnStop).setOnClickListener { stopAll() }
        findViewById<Button>(R.id.btnAnalyse).setOnClickListener { analyseTypedText() }
        findViewById<Button>(R.id.btnPermissions).setOnClickListener { requestPermissionsIfNeeded() }
        findViewById<Button>(R.id.btnOverlay).setOnClickListener { requestOverlay() }
        findViewById<Button>(R.id.btnRefreshCalls).setOnClickListener { loadRecentCalls() }
        findViewById<Button>(R.id.btnReport).setOnClickListener { reportNumber() }
        findViewById<Button>(R.id.btnHangUp).setOnClickListener { hangUp() }
        findViewById<Button>(R.id.btnReportScam).setOnClickListener { reportNumber() }

        // Start page buttons: optional demo, then the setup process
        // (policy accept -> permissions -> overlay -> main screen).
        findViewById<Button>(R.id.btnShowDemo).setOnClickListener { showDemoCard() }
        findViewById<Button>(R.id.btnStartUsing).setOnClickListener { startSetup() }
        findViewById<Button>(R.id.btnDemoScam).setOnClickListener {
            playDemo(DemoScripts.SCAM_LINES)
        }
        findViewById<Button>(R.id.btnDemoSafe).setOnClickListener {
            playDemo(DemoScripts.SAFE_LINES)
        }

        // Site load sequence: nav drops in first (1.2s), then /shield rises
        // after it (0.8s delay, 1.6s), then tagline (1.6s), badges (2.1s),
        // overlay lines (2.6s) follow in slow stages.
        val navRow = findViewById<View>(R.id.navRow)
        val heroRow = findViewById<View>(R.id.heroRow)
        val taglineText = findViewById<View>(R.id.taglineText)
        val badgesRow = findViewById<View>(R.id.badgesRow)
        val overlaysRow = findViewById<View>(R.id.overlaysRow)
        fadeUp(navRow, delayMs = 0, durationMs = 1200, fromYdp = -16f)
        fadeUp(heroRow, delayMs = 800, durationMs = 1600, fromYdp = 24f)
        fadeUp(taglineText, delayMs = 1600, durationMs = 1600, fromYdp = 24f)
        fadeUp(badgesRow, delayMs = 2100, durationMs = 1600, fromYdp = 24f)
        fadeUp(overlaysRow, delayMs = 2600, durationMs = 1600, fromYdp = 24f)

        // Hero wordmark: white melting into light silver, like the site's
        // .shield-gradient (white -> #E6E6E6 at 45% -> #C0C0C0).
        val heroText = findViewById<TextView>(R.id.heroText)
        heroText.post {
            heroText.paint.shader = LinearGradient(
                0f, 0f, heroText.width.toFloat(), 0f,
                intArrayOf(
                    Color.WHITE,
                    Color.parseColor("#E6E6E6"),
                    Color.parseColor("#C0C0C0")
                ),
                floatArrayOf(0f, 0.45f, 1f),
                Shader.TileMode.CLAMP
            )
            heroText.invalidate()
        }

        refreshGate()

        // Opened from the "call in progress" notification, or the
        // automatic trampoline path: start at once.
        if (intent?.action == CallStateReceiver.ACTION_START_LISTENING) {
            startListening()
        }
    }

    private fun fadeUp(view: View, delayMs: Long, durationMs: Long, fromYdp: Float) {
        val density = resources.displayMetrics.density
        view.alpha = 0f
        view.translationY = fromYdp * density
        view.animate()
            .alpha(1f)
            .translationY(0f)
            .setStartDelay(delayMs)
            .setDuration(durationMs)
            .start()
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter().apply {
            addAction(MicListenService.ACTION_PARTIAL)
            addAction(MicListenService.ACTION_FINAL)
            addAction(MicListenService.ACTION_STATUS)
        }
        ContextCompat.registerReceiver(
            this, serviceReceiver, filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        loadRecentCalls()
        refreshGate()
    }

    override fun onPause() {
        try {
            unregisterReceiver(serviceReceiver)
        } catch (ignored: Exception) {
        }
        demoRunnable?.let { handler.removeCallbacks(it) }
        demoRunnable = null
        super.onPause()
    }

    // ---------- permissions page (gate) ----------

    private fun refreshGate() {
        // Opened from the call notification with the mic ready: the
        // user tapped to start listening, so never block that path.
        if (intent?.action == CallStateReceiver.ACTION_START_LISTENING &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        ) {
            mainScroll.visibility = View.VISIBLE
            hideGate()
            return
        }
        // Setup is complete once: policy accepted + mic granted +
        // overlay on. Until then the start page is all the user sees
        // (the main screen stays hidden behind it). Completing here
        // (not only in startSetup) covers the overlay Settings
        // round-trip, after which no second tap is needed.
        val setupDone = prefs.getBoolean("policy_accepted", false) &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED &&
            Settings.canDrawOverlays(this)
        if (setupDone) {
            if (!prefs.getBoolean("setup_complete", false)) {
                prefs.edit().putBoolean("setup_complete", true).apply()
            }
            mainScroll.visibility = View.VISIBLE
            hideGate()
        } else {
            mainScroll.visibility = View.GONE
            if (prefs.getBoolean("demo_shown", false)) {
                demoCard.visibility = View.VISIBLE
            }
            permissionGate.alpha = 1f
            permissionGate.translationY = 0f
            permissionGate.visibility = View.VISIBLE
        }
    }

    private fun hideGate() {
        if (permissionGate.visibility != View.VISIBLE) return
        permissionGate.animate()
            .alpha(0f)
            .translationY(-100f)
            .setDuration(300)
            .withEndAction { permissionGate.visibility = View.GONE }
            .start()
    }

    // ---------- start page: demo + setup ----------

    private fun postGateStatus(msg: String) {
        if (permissionGate.visibility == View.VISIBLE) gateStatusText.text = msg
        else setStatus(msg)
    }

    private fun showDemoCard() {
        prefs.edit().putBoolean("demo_shown", true).apply()
        demoCard.visibility = View.VISIBLE
        gateStatusText.text = "Demo ready — tap a demo call above."
    }

    private fun startSetup() {
        if (!cbPolicy.isChecked) {
            gateStatusText.text = "Accept the privacy policy to continue."
            return
        }
        prefs.edit().putBoolean("policy_accepted", true).apply()
        val missing = allPermissions().filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            gateStatusText.text = "Granting permissions…"
            permissionLauncher.launch(missing.toTypedArray())
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            gateStatusText.text = "One more step: allow display over other apps."
            requestOverlay()
            return
        }
        completeSetup()
    }

    private fun completeSetup() {
        prefs.edit().putBoolean("setup_complete", true).apply()
        mainScroll.visibility = View.VISIBLE
        hideGate()
        setStatus("Setup complete — SableShield guards every call.")
    }

    private fun playDemo(lines: List<Pair<String, String>>) {
        demoRunnable?.let { handler.removeCallbacks(it) }
        val shown = StringBuilder()
        demoTranscriptText.text = ""
        demoVerdictText.text = "Listening…"
        demoVerdictText.setBackgroundResource(R.drawable.card_bg)
        demoFlagsText.text = ""
        demoFlagsText.setTextColor(ContextCompat.getColor(this, R.color.flag))
        var i = 0
        val step = object : Runnable {
            override fun run() {
                if (i >= lines.size) {
                    showDemoVerdict(ScamEngine.analyze(shown.toString()))
                    demoRunnable = null
                    return
                }
                val (who, text) = lines[i]
                shown.append(who).append(": ").append(text).append(" ")
                demoTranscriptText.text = shown.toString()
                i++
                demoRunnable = this
                handler.postDelayed(this, 1400)
            }
        }
        demoRunnable = step
        handler.post(step)
    }

    private fun showDemoVerdict(v: ScamEngine.Verdict) {
        demoVerdictText.text = "${v.title}\n${v.score}\n${v.sub}"
        if (v.type == "scam") {
            demoVerdictText.setBackgroundResource(R.drawable.verdict_scam_bg)
            demoFlagsText.setTextColor(ContextCompat.getColor(this, R.color.flag))
            demoFlagsText.text = v.flags.joinToString("\n") { "• $it" }
        } else {
            demoVerdictText.setBackgroundResource(R.drawable.verdict_safe_bg)
            demoFlagsText.setTextColor(ContextCompat.getColor(this, R.color.safe_text))
            demoFlagsText.text = v.safe.joinToString("\n") { "• $it" }
        }
    }

    // ---------- permissions ----------

    private fun allPermissions(): List<String> {
        val list = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.READ_CONTACTS
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        return list
    }

    private fun requestPermissionsIfNeeded() {
        val missing = allPermissions().filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) permissionLauncher.launch(missing.toTypedArray())
    }

    private fun requestOverlay() {
        if (Settings.canDrawOverlays(this)) {
            setStatus("Overlay permission is on — the live verdict will appear above the call screen.")
        } else {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }
    }

    private fun selectedLang(): String =
        when (langGroup.checkedRadioButtonId) {
            R.id.rbHindi -> ModelDownloader.LANG_HI
            R.id.rbEnglish -> ModelDownloader.LANG_EN
            else -> ModelDownloader.LANG_AUTO
        }

    private fun currentApiKey(): String {
        val key = apiKeyEdit.text.toString().trim()
        prefs.edit().putString("api_key", key).apply()
        return key
    }

    // ---------- model download ----------

    private fun downloadModel() {
        val lang = selectedLang()
        if (lang == ModelDownloader.LANG_AUTO) {
            // Auto needs both models; download whichever is missing,
            // one after the other.
            val missing = listOf(ModelDownloader.LANG_EN, ModelDownloader.LANG_HI)
                .filter { !ModelDownloader.isDownloaded(this, it) }
            if (missing.isEmpty()) {
                setStatus("Models (English + Hindi) are already downloaded — tap Start.")
                return
            }
            downloadOneThenRest(missing)
            return
        }
        if (ModelDownloader.isDownloaded(this, lang)) {
            setStatus("Model ($lang) is already downloaded — tap Start.")
            return
        }
        setStatus("Downloading model ($lang, ~40MB)…")
        ModelDownloader.download(
            this, lang,
            onProgress = { p -> if (p >= 0) setStatus("Model download: $p%") },
            onDone = { ok, msg -> setStatus(if (ok) "Model ready ($lang) — tap Start." else msg) }
        )
    }

    private fun downloadOneThenRest(remaining: List<String>) {
        if (remaining.isEmpty()) {
            setStatus("Models ready (English + Hindi) — tap Start.")
            return
        }
        val lang = remaining.first()
        setStatus("Downloading model ($lang, ~40MB)…")
        ModelDownloader.download(
            this, lang,
            onProgress = { p -> if (p >= 0) setStatus("Model download ($lang): $p%") },
            onDone = { ok, msg ->
                if (ok) downloadOneThenRest(remaining.drop(1))
                else setStatus(msg)
            }
        )
    }

    // ---------- live mic ----------

    private fun startListening() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            setStatus("Grant the mic permission first.")
            requestPermissionsIfNeeded()
            return
        }
        val lang = selectedLang()
        val ready = when (lang) {
            ModelDownloader.LANG_HI -> ModelDownloader.isDownloaded(this, ModelDownloader.LANG_HI)
            ModelDownloader.LANG_EN -> ModelDownloader.isDownloaded(this, ModelDownloader.LANG_EN)
            else -> ModelDownloader.isDownloaded(this, ModelDownloader.LANG_EN) ||
                ModelDownloader.isDownloaded(this, ModelDownloader.LANG_HI)
        }
        if (!ready) {
            setStatus("Download the voice model first (Download button).")
            return
        }
        transcriptBuffer.clear()
        transcriptText.text = getString(R.string.transcript_hint)
        flagsText.text = getString(R.string.flags_hint)
        ContextCompat.startForegroundService(this, MicListenService.startIntent(this, lang))
        setStatus("Listening started… keep the call on speaker.")
    }

    private fun stopAll() {
        handler.removeCallbacksAndMessages(null)
        stopService(Intent(this, MicListenService::class.java))
        setStatus(getString(R.string.status_idle))
    }

    private fun hangUp() {
        handler.removeCallbacksAndMessages(null)
        stopService(Intent(this, MicListenService::class.java))
        stopFlicker()
        setStatus("Call ended.")
    }


    // ---------- typed-text analysis (API key → Gemma, else rules) ----------

    private fun analyseTypedText() {
        val text = liveTextEdit.text.toString().trim()
        if (text.isEmpty()) {
            setStatus("Type or paste what the caller said first.")
            return
        }
        val key = currentApiKey()
        if (key.isEmpty()) {
            showVerdict(ScamEngine.analyze(text), via = "rules (no key)")
            return
        }
        setStatus("Analysing with Gemma 4…")
        Thread {
            val result = GemmaClient.analyze(key, text)
            runOnUiThread {
                if (result.verdict != null) {
                    showVerdict(result.verdict, via = "Gemma 4 live")
                } else {
                    setStatus("Gemma failed (${result.error}) — showing the rule-based verdict.")
                    showVerdict(ScamEngine.analyze(text), via = "rules (fallback)")
                }
            }
        }.start()
    }

    // ---------- recent calls + reported numbers ----------

    private fun normalizeNumber(raw: String): String =
        raw.filter { it.isDigit() }.takeLast(10)

    private fun reportedNumbers(): MutableSet<String> =
        prefs.getStringSet("reported_numbers", emptySet())?.toMutableSet() ?: mutableSetOf()

    private fun loadRecentCalls() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG)
            != PackageManager.PERMISSION_GRANTED
        ) {
            recentCallsText.text = "Grant the call log permission — then recent calls will appear."
            return
        }
        val reported = reportedNumbers()
        val lines = mutableListOf<String>()
        try {
            val cursor = contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(
                    CallLog.Calls.NUMBER,
                    CallLog.Calls.CACHED_NAME,
                    CallLog.Calls.TYPE,
                    CallLog.Calls.DATE
                ),
                null, null,
                "${CallLog.Calls.DATE} DESC"
            )
            cursor?.use {
                val numIdx = it.getColumnIndex(CallLog.Calls.NUMBER)
                val nameIdx = it.getColumnIndex(CallLog.Calls.CACHED_NAME)
                val typeIdx = it.getColumnIndex(CallLog.Calls.TYPE)
                val dateIdx = it.getColumnIndex(CallLog.Calls.DATE)
                while (it.moveToNext() && lines.size < 20) {
                    val number = if (numIdx >= 0) it.getString(numIdx) ?: "" else ""
                    val name = if (nameIdx >= 0) it.getString(nameIdx) else null
                    val type = when (if (typeIdx >= 0) it.getInt(typeIdx) else -1) {
                        CallLog.Calls.INCOMING_TYPE -> "Incoming"
                        CallLog.Calls.OUTGOING_TYPE -> "Outgoing"
                        CallLog.Calls.MISSED_TYPE -> "Missed"
                        else -> "Call"
                    }
                    val date = if (dateIdx >= 0) {
                        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                            .format(Date(it.getLong(dateIdx)))
                    } else ""
                    val display = if (!name.isNullOrEmpty()) "$name ($number)" else number
                    val flag = if (normalizeNumber(number) in reported) " — REPORTED SCAM" else ""
                    lines.add("$type · $display · $date$flag")
                }
            }
        } catch (e: SecurityException) {
            recentCallsText.text = "Call log permission was not granted."
            return
        } catch (e: Exception) {
            recentCallsText.text = "Recent calls could not be loaded — tap Refresh again."
            return
        }
        recentCallsText.text =
            if (lines.isEmpty()) "No recent calls found."
            else lines.joinToString("\n")
    }

    private fun reportNumber() {
        var raw = reportNumberEdit.text.toString().trim()
        if (raw.isEmpty()) {
            raw = prefs.getString("last_caller", "") ?: ""
        }
        val normalized = normalizeNumber(raw)
        if (normalized.length < 10) {
            setStatus("Enter the full number to report, or wait for a call first.")
            return
        }
        val set = reportedNumbers()
        set.add(normalized)
        prefs.edit().putStringSet("reported_numbers", set).apply()
        setStatus("Number reported ($normalized) — it will now show a REPORTED SCAM flag in recent calls.")
        reportNumberEdit.text.clear()
        loadRecentCalls()
    }

    // ---------- verdict UI ----------

    private fun showVerdict(v: ScamEngine.Verdict, via: String) {
        verdictText.text = "${v.title}\n${v.score}\n${v.sub}\n(via $via)"
        if (v.type == "scam") {
            verdictText.setBackgroundResource(R.drawable.verdict_scam_bg)
            flagsLabel.text = "Red flags detected"
            flagsLabel.setTextColor(ContextCompat.getColor(this, R.color.flag))
            flagsText.setTextColor(ContextCompat.getColor(this, R.color.flag))
            flagsText.text = v.flags.joinToString("\n") { "• $it" }
            startFlicker()
        } else {
            verdictText.setBackgroundResource(R.drawable.verdict_safe_bg)
            flagsLabel.text = "Why this looks safe"
            flagsLabel.setTextColor(ContextCompat.getColor(this, R.color.safe_text))
            flagsText.setTextColor(ContextCompat.getColor(this, R.color.text2))
            flagsText.text = v.safe.joinToString("\n") { "• $it" }
            stopFlicker()
        }
    }

    // Site .synth-verdict title flicker (synthFlicker, 4.2s): a broken
    // neon sign — brief dips and shifts, looping while a scam verdict shows.
    private fun startFlicker() {
        stopFlicker()
        flickerAnimator = ValueAnimator.ofFloat(
            1f, 0.55f, 1f, 0.85f, 1f, 0.65f, 1f, 0.9f, 1f
        ).apply {
            duration = 4200
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { verdictText.alpha = it.animatedValue as Float }
            start()
        }
    }

    private fun stopFlicker() {
        flickerAnimator?.cancel()
        flickerAnimator = null
        verdictText.alpha = 1f
    }

    private fun setStatus(msg: String) {
        statusText.text = msg
    }
}
