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

    suspend fun generate(prompt: String): String? = withContext(Dispatchers.IO) {
        if (!isConfigured) {
            Log.w(TAG, "Gemini API key is not configured")
            return@withContext null
        }

        for (model in MODELS) {
            val result = requestModel(model, prompt)
            if (result != null) return@withContext result
        }
        null
    }

    private fun requestModel(model: String, prompt: String): String? {
        val url = URL(
            "https://generativelanguage.googleapis.com/v1beta/models/" +
                "$model:generateContent?key=$apiKey"
        )
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            doOutput = true
            connectTimeout = 12_000
            readTimeout = 20_000
        }

        val body = JSONObject().apply {
            put("contents", org.json.JSONArray().put(
                JSONObject().put("parts", org.json.JSONArray().put(
                    JSONObject().put("text", prompt)
                ))
            ))
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.9)
                put("maxOutputTokens", 512)
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
            val text = parts.optJSONObject(0)?.optString("text")?.takeIf { it.isNotBlank() }
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

    companion object {
        private const val TAG = "GeminiClient"
        private val MODELS = listOf(
            "gemini-3.1-flash-lite-preview",
            "gemini-flash-latest",
            "gemini-2.0-flash",
        )
    }
}
