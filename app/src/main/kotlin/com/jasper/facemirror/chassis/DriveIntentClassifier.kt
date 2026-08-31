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
            STT is noisy: name often comes as джазпер, джеспер, jasper, джаспер.
            Commands may be split: "на лево", "в перед", "на право".

            If the user is telling the car to move or to reconnect Bluetooth, pick a command.
            Name is optional: "едь вперед", "вперед", "налево", "поехали" are still commands.
            If it's greeting/chat/unrelated, cmd=none.
            If ambiguous but it looks like a move command, pick the command.
            "подключись к машинке" / reconnect / bluetooth → connect.

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
