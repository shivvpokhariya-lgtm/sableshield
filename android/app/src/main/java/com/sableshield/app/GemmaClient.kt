package com.sableshield.app

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Live Gemma 4 analysis via the Gemini API — same model, prompt and
 * JSON contract as the web app's live mode (gemma-4-26b-a4b-it).
 *
 * Called from a background thread (network on main thread crashes).
 * The user's key is typed into the app and stays on the device; it is
 * never bundled into the APK. No key → caller falls back to ScamEngine.
 */
object GemmaClient {

    data class Result(val verdict: ScamEngine.Verdict?, val error: String?)

    fun analyze(apiKey: String, text: String): Result {
        try {
            val prompt = "You are SableShield, a scam-call detector. Analyse this caller transcript and return ONLY valid JSON (no markdown, no fences):\n" +
                "{\n" +
                "  \"type\": \"scam\" or \"safe\",\n" +
                "  \"score\": \"e.g. 92% SCAM RISK or LOW RISK\",\n" +
                "  \"title\": \"short verdict\",\n" +
                "  \"sub\": \"one sentence explanation\",\n" +
                "  \"flags\": [\"red flag 1\", \"red flag 2\"],\n" +
                "  \"safe\": [\"safe reason 1\"]\n" +
                "}\nTranscript: $text"
            val url = URL(
                "https://generativelanguage.googleapis.com/v1beta/models/gemma-4-26b-a4b-it:generateContent" +
                    "?key=" + URLEncoder.encode(apiKey, "UTF-8")
            )
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.connectTimeout = 30_000
            conn.readTimeout = 60_000
            val body = JSONObject()
                .put(
                    "contents",
                    JSONArray().put(
                        JSONObject().put(
                            "parts",
                            JSONArray().put(JSONObject().put("text", prompt))
                        )
                    )
                )
                .put(
                    "generationConfig",
                    JSONObject().put("temperature", 0.2).put("maxOutputTokens", 800)
                )
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                return Result(null, "Gemma API HTTP ${conn.responseCode}")
            }
            val resp = conn.inputStream.bufferedReader().readText()
            var out = JSONObject(resp)
                .getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")
            out = out.replace("```json", "").replace("```", "").trim()
            val v = JSONObject(out)
            val flags = mutableListOf<String>()
            val fArr = v.optJSONArray("flags")
            if (fArr != null) {
                for (i in 0 until fArr.length()) flags.add(fArr.getString(i))
            }
            val safe = mutableListOf<String>()
            val sArr = v.optJSONArray("safe")
            if (sArr != null) {
                for (i in 0 until sArr.length()) safe.add(sArr.getString(i))
            }
            return Result(
                ScamEngine.Verdict(
                    type = v.optString("type", "scam"),
                    title = v.optString("title"),
                    score = v.optString("score"),
                    sub = v.optString("sub"),
                    flags = flags,
                    safe = safe
                ),
                null
            )
        } catch (e: Exception) {
            return Result(null, e.message ?: "Gemma error")
        }
    }
}
