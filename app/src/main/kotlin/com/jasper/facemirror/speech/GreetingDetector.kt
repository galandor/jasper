package com.jasper.facemirror.speech

import com.jasper.facemirror.model.GreetingReply
import com.jasper.facemirror.model.VoiceEmotion

object GreetingDetector {

    private data class Trigger(
        val pattern: Regex,
        val replies: List<GreetingReply>,
    )

    private val cartoon = VoiceEmotion.CARTOON

    private val triggers = listOf(
        Trigger(
            pattern = Regex("""приветик|хай|хей""", RegexOption.IGNORE_CASE),
            replies = listOf(
                GreetingReply("Привет-привет!", cartoon),
                GreetingReply("Хей-хей!", cartoon),
                GreetingReply("Ура, приветик!", cartoon),
            ),
        ),
        Trigger(
            pattern = Regex("""здравствуй|добрый день|доброе утро|добрый вечер""", RegexOption.IGNORE_CASE),
            replies = listOf(
                GreetingReply("Здравствуй!", cartoon),
                GreetingReply("О, привет!", cartoon),
                GreetingReply("Рад тебя видеть!", cartoon),
            ),
        ),
        Trigger(
            pattern = Regex("""привет""", RegexOption.IGNORE_CASE),
            replies = listOf(
                GreetingReply("Привет!", cartoon),
                GreetingReply("Приве-ет!", cartoon),
                GreetingReply("О, привет!", cartoon),
                GreetingReply("Ура, привет!", cartoon),
                GreetingReply("Йоу, привет!", cartoon),
            ),
        ),
    )

    fun match(text: String): GreetingReply? {
        val normalized = text.trim()
        if (normalized.isEmpty()) return null
        return triggers
            .firstOrNull { it.pattern.containsMatchIn(normalized) }
            ?.replies
            ?.random()
    }

    /** @deprecated используйте [match] */
    fun isHello(text: String): Boolean = match(text) != null
}
