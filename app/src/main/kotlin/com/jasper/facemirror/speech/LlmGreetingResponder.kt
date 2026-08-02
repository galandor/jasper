package com.jasper.facemirror.speech

import com.jasper.facemirror.llm.GeminiClient
import com.jasper.facemirror.model.GreetingReply
import com.jasper.facemirror.model.VoiceEmotion
import org.json.JSONObject

class LlmGreetingResponder(
    private val client: GeminiClient = GeminiClient(),
) {
    val isAvailable: Boolean get() = client.isConfigured

    suspend fun respond(userPhrase: String): GreetingReply? {
        if (!isAvailable) return null

        val prompt = """
            Ты Jasper — мультяшный неоновый персонаж (глаза и улыбка на чёрном экране).
            Пользователь сказал: "$userPhrase"

            Определи, является ли это приветствием (привет, здравствуй, хай, доброе утро, здорово, йо и т.п.).
            Если да — придумай короткий живой ответ (до 8 слов), мультяшный и дружелюбный.
            Если нет — is_greeting = false.

            Ответь ТОЛЬКО JSON:
            {"is_greeting":true,"reply":"текст","emotion":"cartoon"}
            emotion: cartoon | happy | warm | playful
        """.trimIndent()

        val raw = client.generate(prompt) ?: return null
        return parseResponse(raw)
    }

    private fun parseResponse(raw: String): GreetingReply? {
        return try {
            val cleaned = raw.trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()
            val json = JSONObject(cleaned)
            if (!json.optBoolean("is_greeting", false)) return null
            val reply = json.optString("reply").trim()
            if (reply.isBlank()) return null
            val emotion = when (json.optString("emotion", "cartoon").lowercase()) {
                "happy" -> VoiceEmotion.HAPPY
                "warm" -> VoiceEmotion.WARM
                "playful" -> VoiceEmotion.PLAYFUL
                "calm" -> VoiceEmotion.CALM
                else -> VoiceEmotion.CARTOON
            }
            GreetingReply(reply, emotion)
        } catch (_: Exception) {
            null
        }
    }
}
