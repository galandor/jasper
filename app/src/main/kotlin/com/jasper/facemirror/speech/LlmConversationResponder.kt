package com.jasper.facemirror.speech

import com.jasper.facemirror.llm.GeminiClient
import com.jasper.facemirror.model.GreetingReply
import com.jasper.facemirror.model.VoiceEmotion
import org.json.JSONObject

class LlmConversationResponder(
    private val client: GeminiClient = GeminiClient(),
) {
    val isAvailable: Boolean get() = client.isConfigured

    suspend fun respond(userPhrase: String, history: List<String> = emptyList()): GreetingReply? {
        if (!isAvailable) return null

        val historyBlock = if (history.isEmpty()) {
            ""
        } else {
            "\nНедавние фразы пользователя: ${history.joinToString(" | ")}"
        }

        val prompt = """
            Ты Jasper — мультяшный неоновый персонаж: глаза и улыбка на чёрном экране.
            Говоришь коротко, живо, по-дружески, иногда с юмором. Всегда на русском.

            Пользователь сказал: "$userPhrase"$historyBlock

            Придумай короткий ответ по смыслу (до 12 слов).
            На приветствие — поприветствуй. На вопрос — ответь. На шутку — подыграй.
            Если фраза непонятна, бессмысленна или это шум — не отвечай.

            Ответь ТОЛЬКО JSON:
            {"should_reply":true,"reply":"текст","emotion":"cartoon"}
            emotion: cartoon | happy | warm | playful | calm
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
            if (!json.optBoolean("should_reply", false)) return null
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
