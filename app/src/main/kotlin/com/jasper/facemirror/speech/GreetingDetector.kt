package com.jasper.facemirror.speech

import com.jasper.facemirror.model.FaceExpression
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
            pattern = Regex("""боюсь|страшно|испугал|жуть""", RegexOption.IGNORE_CASE),
            replies = listOf(
                GreetingReply("Ой-ой!", VoiceEmotion.AFRAID, FaceExpression.AFRAID),
                GreetingReply("Страшно...", VoiceEmotion.AFRAID, FaceExpression.AFRAID),
            ),
        ),
        Trigger(
            pattern = Regex("""злюсь|бесит|разозли|достал|ненавижу""", RegexOption.IGNORE_CASE),
            replies = listOf(
                GreetingReply("Гррр!", VoiceEmotion.ANGRY, FaceExpression.ANGRY),
                GreetingReply("Не нравится!", VoiceEmotion.ANGRY, FaceExpression.ANGRY),
            ),
        ),
        Trigger(
            pattern = Regex("""спать|сонн|устал|засыпа""", RegexOption.IGNORE_CASE),
            replies = listOf(
                GreetingReply("Хо-о-он...", VoiceEmotion.SLEEPY, FaceExpression.SLEEPY),
                GreetingReply("Так сонно...", VoiceEmotion.SLEEPY, FaceExpression.SLEEPY),
            ),
        ),
        Trigger(
            pattern = Regex("""не хочу.*видеть|уходи|отвали""", RegexOption.IGNORE_CASE),
            replies = listOf(
                GreetingReply("Эх...", cartoon, FaceExpression.OFFENDED),
                GreetingReply("Обидно...", VoiceEmotion.SAD, FaceExpression.SAD),
            ),
        ),
        Trigger(
            pattern = Regex("""рад.*видеть|скучал|люблю""", RegexOption.IGNORE_CASE),
            replies = listOf(
                GreetingReply("И я рад!", cartoon, FaceExpression.HAPPY),
                GreetingReply("Ура, ты здесь!", VoiceEmotion.HAPPY, FaceExpression.HAPPY),
            ),
        ),
        Trigger(
            pattern = Regex("""приветик|хай|хей""", RegexOption.IGNORE_CASE),
            replies = listOf(
                GreetingReply("Привет-привет!", cartoon, FaceExpression.PLAYFUL),
                GreetingReply("Хей-хей!", cartoon, FaceExpression.PLAYFUL),
            ),
        ),
        Trigger(
            pattern = Regex("""здравствуй|добрый день|доброе утро|добрый вечер""", RegexOption.IGNORE_CASE),
            replies = listOf(
                GreetingReply("Здравствуй!", cartoon, FaceExpression.HAPPY),
                GreetingReply("Рад тебя видеть!", VoiceEmotion.WARM, FaceExpression.HAPPY),
            ),
        ),
        Trigger(
            pattern = Regex("""привет""", RegexOption.IGNORE_CASE),
            replies = listOf(
                GreetingReply("Привет!", cartoon, FaceExpression.HAPPY),
                GreetingReply("Приве-ет!", cartoon, FaceExpression.HAPPY),
                GreetingReply("Ура, привет!", cartoon, FaceExpression.PLAYFUL),
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
}
