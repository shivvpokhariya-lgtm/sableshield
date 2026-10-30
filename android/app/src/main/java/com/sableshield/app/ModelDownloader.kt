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
 * The zip's top folder is stripped so model files land directly in the
 * language directory the Vosk Model() constructor expects.
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

    fun modelDir(context: Context, lang: String): File =
        File(context.filesDir, "vosk-model-$lang")

    fun isDownloaded(context: Context, lang: String): Boolean {
        val dir = modelDir(context, lang)
        // am/final.mdl is present in every unpacked Vosk model.
        return File(dir, "am/final.mdl").exists()
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
            try {
                val dir = modelDir(appContext, lang)
                if (dir.exists()) dir.deleteRecursively()
                dir.mkdirs()
                val zipFile = File(appContext.cacheDir, "vosk-model-$lang.zip")
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 30_000
                conn.readTimeout = 60_000
                conn.connect()
                if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                    done(false, "Download failed: HTTP ${conn.responseCode}")
                } else {
                val total = conn.contentLength
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
                unzipStrippingTopFolder(zipFile, dir)
                zipFile.delete()
                if (isDownloaded(appContext, lang)) {
                    done(true, "Model ready ($lang)")
                } else {
                    done(false, "Unzip finished but the model files were not found — the URL may have changed, check the models page in the README.")
                }
                }
            } catch (e: Exception) {
                done(false, "Download error: ${e.message}")
            }
        }.start()
    }

    private fun unzipStrippingTopFolder(zipFile: File, destDir: File) {
        ZipInputStream(BufferedInputStream(zipFile.inputStream())).use { zin ->
            val destCanonical = destDir.canonicalPath
            while (true) {
                val entry = zin.nextEntry ?: break
                // Strip the zip's top folder (e.g. vosk-model-small-hi-0.22/...).
                val rel = entry.name.substringAfter("/", "")
                if (rel.isEmpty()) continue
                val out = File(destDir, rel)
                // Zip-slip guard.
                if (!out.canonicalPath.startsWith(destCanonical)) continue
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
