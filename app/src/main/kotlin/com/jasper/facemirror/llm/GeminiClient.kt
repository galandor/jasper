package com.jasper.facemirror.llm

import android.util.Log
import com.jasper.facemirror.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

class GeminiClient(
    private val apiKey: String = BuildConfig.GEMINI_API_KEY,
) {
    val isConfigured: Boolean get() = apiKey.isNotBlank()

    suspend fun generate(
        prompt: String,
        temperature: Double = 0.9,
        maxOutputTokens: Int = 512,
        timeoutMs: Int = 20_000,
        firstModelOnly: Boolean = false,
    ): String? = withContext(Dispatchers.IO) {
        if (!isConfigured) {
            Log.w(TAG, "Gemini API key is not configured")
            return@withContext null
        }

        val models = if (firstModelOnly) listOf(MODELS.first()) else MODELS
        for (model in models) {
            val result = requestModel(model, prompt, temperature, maxOutputTokens, timeoutMs)
            if (result != null) return@withContext result
        }
        null
    }

    private fun requestModel(
        model: String,
        prompt: String,
        temperature: Double,
        maxOutputTokens: Int,
        timeoutMs: Int,
    ): String? {
        val url = URL(
            "https://generativelanguage.googleapis.com/v1beta/models/" +
                "$model:generateContent?key=$apiKey"
        )
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            doOutput = true
            connectTimeout = minOf(8_000, timeoutMs)
            readTimeout = timeoutMs
        }

        val body = JSONObject().apply {
            put("contents", org.json.JSONArray().put(
                JSONObject().put("parts", org.json.JSONArray().put(
                    JSONObject().put("text", prompt)
                ))
            ))
            put("generationConfig", JSONObject().apply {
                put("temperature", temperature)
                put("maxOutputTokens", maxOutputTokens)
                put("responseMimeType", "application/json")
            })
        }

        try {
            connection.outputStream.bufferedWriter().use { it.write(body.toString()) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val responseText = stream.bufferedReader().use(BufferedReader::readText)

            if (code !in 200..299) {
                Log.w(TAG, "Gemini $model failed: HTTP $code — ${responseText.take(200)}")
                return null
            }

            val candidates = JSONObject(responseText).optJSONArray("candidates") ?: return null
            val first = candidates.optJSONObject(0) ?: return null
            val parts = first.optJSONObject("content")?.optJSONArray("parts") ?: return null
            val text = extractText(parts)
            if (text != null) {
                Log.d(TAG, "Gemini response from $model: ${text.take(120)}")
            }
            return text
        } catch (e: Exception) {
            Log.w(TAG, "Gemini $model error: ${e.message}")
            return null
        } finally {
            connection.disconnect()
        }
    }

    private fun extractText(parts: org.json.JSONArray): String? {
        val combined = StringBuilder()
        for (index in 0 until parts.length()) {
            val part = parts.optJSONObject(index) ?: continue
            if (part.optBoolean("thought", false)) continue
            val text = part.optString("text")
            if (text.isNotBlank()) combined.append(text)
        }
        if (combined.isNotBlank()) return combined.toString()
        for (index in 0 until parts.length()) {
            val text = parts.optJSONObject(index)?.optString("text")
            if (!text.isNullOrBlank()) return text
        }
        return null
    }

    companion object {
        private const val TAG = "GeminiClient"
        private val MODELS = listOf(
            "gemini-3.1-flash-lite-preview",
            "gemini-flash-latest",
            "gemini-2.0-flash",
        )
    }
}
