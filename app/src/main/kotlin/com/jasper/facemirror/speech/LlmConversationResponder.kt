package com.jasper.facemirror.speech

import com.jasper.facemirror.llm.GeminiClient
import com.jasper.facemirror.model.FaceExpression
import com.jasper.facemirror.model.GreetingReply
import com.jasper.facemirror.model.VoiceEmotion
import org.json.JSONObject

class LlmConversationResponder(
    private val client: GeminiClient = GeminiClient(),
) {
    val isAvailable: Boolean get() = client.isConfigured

    suspend fun respond(userPhrase: String, session: List<ChatTurn> = emptyList()): GreetingReply? {
        if (!isAvailable) return null

        val prompt = JasperLlmPrompt.build(userPhrase, session)

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
            GreetingReply(
                text = reply,
                voice = parseVoice(json.optString("voice", "cartoon")),
                expression = parseExpression(json.optString("expression", "neutral")),
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun parseExpression(value: String): FaceExpression = when (value.lowercase()) {
        "happy" -> FaceExpression.HAPPY
        "playful" -> FaceExpression.PLAYFUL
        "sad" -> FaceExpression.SAD
        "offended" -> FaceExpression.OFFENDED
        "surprised" -> FaceExpression.SURPRISED
        "angry" -> FaceExpression.ANGRY
        "afraid", "fear", "scared" -> FaceExpression.AFRAID
        "sleepy", "tired" -> FaceExpression.SLEEPY
        else -> FaceExpression.NEUTRAL
    }

    private fun parseVoice(value: String): VoiceEmotion = when (value.lowercase()) {
        "happy" -> VoiceEmotion.HAPPY
        "warm" -> VoiceEmotion.WARM
        "playful" -> VoiceEmotion.PLAYFUL
        "calm" -> VoiceEmotion.CALM
        "sad" -> VoiceEmotion.SAD
        "offended" -> VoiceEmotion.OFFENDED
        "angry" -> VoiceEmotion.ANGRY
        "afraid", "fear", "scared" -> VoiceEmotion.AFRAID
        "sleepy", "tired" -> VoiceEmotion.SLEEPY
        else -> VoiceEmotion.CARTOON
    }
}
