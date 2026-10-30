package com.sableshield.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/**
 * One-time voice-model download. Models are ~40MB each, Apache 2.0,
 * from the official Vosk models page (alphacephei.com/vosk/models).
 *
 * BUG FIX (2026-10-30) — why downloads failed with ENOENT:
 * The old code unzipped straight into the final model folder and
 * treated the model as "downloaded" if only am/final.mdl existed.
 * A slow / interrupted download therefore left a PARTIAL folder that
 * passed the check, and Vosk's Model() then threw
 * ENOENT (No such file or directory) on the first missing file
 * (conf/model.conf, graph/HCLG.fst, ...). The Download button then
 * said "already downloaded", so the user could never recover.
 *
 * New rules, all enforced here:
 *  1. Download to a temp zip, unzip to a TEMP folder, never the final one.
 *  2. A model counts as downloaded only if EVERY file Vosk needs exists.
 *  3. Only a fully verified temp folder is swapped into place.
 *     Any failure leaves the old folder untouched and deletes the temp.
 *  4. An incomplete download (bytes < server size) is rejected, never unzipped.
 *  5. The zip's top folder is stripped only if ALL entries share it —
 *     entries without a slash are no longer silently dropped.
 */
object ModelDownloader {

    const val LANG_EN = "en"
    const val LANG_HI = "hi"
    // Auto: both models decode the same mic stream in parallel.
    const val LANG_AUTO = "auto"

    private val URLS = mapOf(
        LANG_EN to "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip",
        LANG_HI to "https://alphacephei.com/vosk/models/vosk-model-small-hi-0.22.zip"
    )

    /** Files Vosk's Model() actually opens. Missing any one = ENOENT at load. */
    private val REQUIRED_FILES = listOf(
        "am/final.mdl",
        "conf/model.conf",
        "conf/mfcc.conf",
        "graph/HCLG.fst"
    )

    fun modelDir(context: Context, lang: String): File =
        File(context.filesDir, "vosk-model-$lang")

    private fun tempDir(context: Context, lang: String): File =
        File(context.filesDir, "vosk-model-$lang.tmp")

    /** Names of required files that are missing from [dir] (empty = complete). */
    fun missingFiles(dir: File): List<String> =
        REQUIRED_FILES.filter { !File(dir, it).exists() }

    fun isDownloaded(context: Context, lang: String): Boolean {
        val dir = modelDir(context, lang)
        return dir.isDirectory && missingFiles(dir).isEmpty()
    }

    /**
     * Downloads + unzips on a background thread. Callbacks run on the
     * main thread. onProgress receives 0..100 (or -1 when size unknown).
     */
    fun download(
        context: Context,
        lang: String,
        onProgress: (Int) -> Unit,
        onDone: (Boolean, String) -> Unit
    ) {
        val appContext = context.applicationContext
        val url = URLS[lang] ?: run {
            onDone(false, "Unknown language: $lang")
            return
        }
        Thread {
            val main = Handler(Looper.getMainLooper())
            fun done(ok: Boolean, msg: String) = main.post { onDone(ok, msg) }
            fun progress(p: Int) = main.post { onProgress(p) }
            val tmp = tempDir(appContext, lang)
            try {
                // Cache dir must exist before FileOutputStream, or the
                // OS throws ENOENT on the zip itself.
                val cacheDir = appContext.cacheDir
                if (!cacheDir.exists()) cacheDir.mkdirs()
                val zipFile = File(cacheDir, "vosk-model-$lang.zip")
                zipFile.parentFile?.mkdirs()
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.instanceFollowRedirects = true
                conn.connectTimeout = 30_000
                conn.readTimeout = 180_000
                conn.connect()
                if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                    done(false, "Download failed: HTTP ${conn.responseCode} from $url")
                    return@Thread
                }
                val total = conn.contentLengthLong
                var read = 0L
                conn.inputStream.use { input ->
                    FileOutputStream(zipFile).use { out ->
                        val buf = ByteArray(8192)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            read += n
                            if (total > 0) progress(((read * 100) / total).toInt())
                        }
                    }
                }
                if (total > 0 && read < total) {
                    zipFile.delete()
                    done(false, "Download incomplete ($read of $total bytes) — check connection and tap Download again.")
                    return@Thread
                }
                installFromZip(zipFile, lang, appContext, { ok, msg -> done(ok, msg) })
            } catch (e: Exception) {
                try { tmp.deleteRecursively() } catch (ignored: Exception) { }
                done(false, "Download error (${e.javaClass.simpleName}): ${e.message} — check connection and tap Download again.")
            }
        }.start()
    }

    /**
     * DIRECT LISTENING (2026-10-30): install a voice model bundled in
     * the APK itself (assets/models/vosk-model-<lang>.zip). No network,
     * no alphacephei download, no ENOENT from a partial download — the
     * model is already on the phone the moment the app is installed.
     * Falls back to the network download only if the asset is absent.
     */
    fun installBundled(
        context: Context,
        lang: String,
        onDone: (Boolean, String) -> Unit
    ) {
        val appContext = context.applicationContext
        Thread {
            val main = Handler(Looper.getMainLooper())
            fun done(ok: Boolean, msg: String) = main.post { onDone(ok, msg) }
            try {
                val cacheDir = appContext.cacheDir
                if (!cacheDir.exists()) cacheDir.mkdirs()
                val zipFile = File(cacheDir, "vosk-model-$lang.zip")
                appContext.assets.open("models/vosk-model-$lang.zip").use { input ->
                    FileOutputStream(zipFile).use { out -> input.copyTo(out) }
                }
                installFromZip(zipFile, lang, appContext, { ok, msg -> done(ok, msg) })
            } catch (e: Exception) {
                done(false, "Bundled model not available (${e.javaClass.simpleName}) — use Download instead.")
            }
        }.start()
    }

    /** Unzip + verify + swap a zip into place. Shared by download and bundled install. */
    private fun installFromZip(
        zipFile: File,
        lang: String,
        appContext: Context,
        done: (Boolean, String) -> Unit
    ) {
        val tmp = tempDir(appContext, lang)
        try {
            if (tmp.exists()) tmp.deleteRecursively()
            tmp.mkdirs()
            unzip(zipFile, tmp)
            zipFile.delete()
            val missing = missingFiles(tmp)
            if (missing.isNotEmpty()) {
                tmp.deleteRecursively()
                done(false, "Model files missing after unzip: ${missing.joinToString(", ")}")
                return
            }
            val finalDir = modelDir(appContext, lang)
            if (finalDir.exists()) finalDir.deleteRecursively()
            if (!tmp.renameTo(finalDir)) {
                tmp.copyRecursively(finalDir, overwrite = true)
                tmp.deleteRecursively()
            }
            if (isDownloaded(appContext, lang)) {
                done(true, "Model ready ($lang)")
            } else {
                done(false, "Model could not be installed — try again.")
            }
        } catch (e: Exception) {
            try { tmp.deleteRecursively() } catch (ignored: Exception) { }
            done(false, "Model install error (${e.javaClass.simpleName}): ${e.message}")
        }
    }

    private fun unzip(zipFile: File, destDir: File) {
        // First pass: collect entry names to find the common top folder.
        val names = mutableListOf<String>()
        ZipInputStream(BufferedInputStream(zipFile.inputStream())).use { zin ->
            while (true) {
                val entry = zin.nextEntry ?: break
                names.add(entry.name)
                zin.closeEntry()
            }
        }
        val topFolder = names
            .filter { it.contains("/") }
            .map { it.substringBefore("/") }
            .groupingBy { it }.eachCount()
            .maxByOrNull { it.value }?.key
            ?.takeIf { top -> names.all { it == "$top/" || it.startsWith("$top/") } }
        ZipInputStream(BufferedInputStream(zipFile.inputStream())).use { zin ->
            val destCanonical = destDir.canonicalPath
            while (true) {
                val entry = zin.nextEntry ?: break
                val rel = if (topFolder != null) {
                    entry.name.removePrefix("$topFolder/")
                } else {
                    entry.name
                }
                if (rel.isEmpty()) {
                    zin.closeEntry()
                    continue
                }
                val out = File(destDir, rel)
                // Zip-slip guard.
                if (!out.canonicalPath.startsWith(destCanonical)) {
                    zin.closeEntry()
                    continue
                }
                if (entry.isDirectory) {
                    out.mkdirs()
                } else {
                    out.parentFile?.mkdirs()
                    FileOutputStream(out).use { fos ->
                        zin.copyTo(fos)
                    }
                }
                zin.closeEntry()
            }
        }
    }
}
