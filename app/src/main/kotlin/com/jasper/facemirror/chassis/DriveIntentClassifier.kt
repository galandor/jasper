package com.jasper.facemirror.chassis

import android.util.Log
import com.jasper.facemirror.debug.JasperTiming
import com.jasper.facemirror.llm.GeminiClient
import org.json.JSONObject

/**
 * Крошечный классификатор: команда машинке или обычный разговор.
 * Ловит кривой STT («джазпер на лево»), который regex не берёт.
 */
class DriveIntentClassifier(
    private val client: GeminiClient = GeminiClient(),
) {
    val isAvailable: Boolean get() = client.isConfigured

    suspend fun classify(transcripts: List<String>): DriveAction? {
        if (!isAvailable) return null
        val unique = transcripts.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (unique.isEmpty()) return null

        val startedAt = JasperTiming.now()
        val raw = client.generate(
            prompt = buildPrompt(unique),
            temperature = 0.1,
            maxOutputTokens = 256,
            timeoutMs = 5_000,
            firstModelOnly = true,
        )
        if (raw == null) {
            JasperTiming.elapsed("классификатор команд", startedAt, "нет ответа Gemini transcripts=$unique")
            return null
        }

        val action = parseCmd(raw)
        JasperTiming.elapsed("классификатор команд", startedAt, "$unique → $action")
        Log.i(TAG, "classify $unique → $action raw=${raw.take(80)}")
        return action
    }

    private fun parseCmd(raw: String): DriveAction? {
        return try {
            val cleaned = raw.trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()
            val jsonStart = cleaned.indexOf('{')
            val jsonEnd = cleaned.lastIndexOf('}')
            val jsonText = if (jsonStart >= 0 && jsonEnd > jsonStart) {
                cleaned.substring(jsonStart, jsonEnd + 1)
            } else {
                cleaned
            }
            val json = JSONObject(jsonText)
            when (json.optString("cmd").lowercase().trim()) {
                "forward" -> DriveAction.FORWARD
                "back", "backward" -> DriveAction.BACKWARD
                "left" -> DriveAction.ROTATE_LEFT
                "right" -> DriveAction.ROTATE_RIGHT
                "strafe_left" -> DriveAction.STRAFE_LEFT
                "strafe_right" -> DriveAction.STRAFE_RIGHT
                "follow" -> DriveAction.FOLLOW
                "wander" -> DriveAction.WANDER
                "stop" -> DriveAction.STOP
                "connect" -> DriveAction.CONNECT
                else -> null
            }
        } catch (e: Exception) {
            Log.w(TAG, "bad classifier JSON: ${raw.take(120)}", e)
            null
        }
    }

    private fun buildPrompt(transcripts: List<String>): String {
        val lines = transcripts.joinToString("\n") { "- \"$it\"" }
        return """
            Voice control for a small robot car named Jasper (Джаспер).
            STT is noisy: name often comes as джаспер, жаспер, аспер, джазпер, джеспер, jasper.
            Commands may be split: "на лево", "в перед", "на право".

            Move/reconnect commands MUST start with the name.
            "аспер вперед", "джаспер назад", "жаспер налево" → that command.
            WITHOUT the name, "вперед", "назад", "налево", "поехали", "едь" are chat or a game turn — cmd=none.
            EXCEPTION: stop (стоп, стой, остановись, тормоз) works WITHOUT the name. cmd=stop.
            If it's greeting/chat/a game, cmd=none.
            "джаспер подключись" / reconnect / bluetooth after the name → connect.

            Same utterance, alternative transcripts:
            $lines

            JSON only:
            {"cmd":"forward|back|left|right|strafe_left|strafe_right|follow|wander|stop|connect|none"}
        """.trimIndent()
    }

    companion object {
        private const val TAG = "JasperChassis"
    }
}
