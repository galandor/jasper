package com.jasper.facemirror.chassis

import com.jasper.facemirror.model.FaceExpression
import com.jasper.facemirror.model.GreetingReply
import com.jasper.facemirror.model.VoiceEmotion

/**
 * Команда шасси. Буква — протокол машинки `%A#` … `%W#`.
 * [holdMs] — сколько слать импульс; поворот короче, чтобы не крутиться на 360°.
 */
enum class DriveAction(
    val code: Char,
    val hold: Boolean,
    val holdMs: Long,
    val ack: GreetingReply?,
) {
    FORWARD('A', hold = true, holdMs = 2500, ack = GreetingReply("Поехали!", VoiceEmotion.PLAYFUL, FaceExpression.PLAYFUL)),
    BACKWARD('B', hold = true, holdMs = 2500, ack = GreetingReply("Назад!", VoiceEmotion.CARTOON, FaceExpression.SURPRISED)),
    STRAFE_LEFT('C', hold = true, holdMs = 2500, ack = GreetingReply("Боком!", VoiceEmotion.PLAYFUL, FaceExpression.PLAYFUL)),
    STRAFE_RIGHT('D', hold = true, holdMs = 2500, ack = GreetingReply("Боком!", VoiceEmotion.PLAYFUL, FaceExpression.PLAYFUL)),
    ROTATE_LEFT('E', hold = true, holdMs = 600, ack = GreetingReply("Налево!", VoiceEmotion.CARTOON, FaceExpression.PLAYFUL)),
    ROTATE_RIGHT('F', hold = true, holdMs = 600, ack = GreetingReply("Направо!", VoiceEmotion.CARTOON, FaceExpression.PLAYFUL)),
    FOLLOW('W', hold = false, holdMs = 0, ack = GreetingReply("Бегу за тобой!", VoiceEmotion.HAPPY, FaceExpression.HAPPY)),
    WANDER('T', hold = false, holdMs = 0, ack = GreetingReply("Погуляю!", VoiceEmotion.PLAYFUL, FaceExpression.PLAYFUL)),
    STOP('S', hold = false, holdMs = 0, ack = null),
}

/**
 * Движение только с именем в начале.
 * STT часто: «джазпер», «на лево» вместо «налево».
 */
object DriveCommands {
    private val trailingFluff = Regex("""\s+(пожалуйста|давай|ну)$""")

    private val motorStopWord = Regex("""(^|\s)(стоп|стой|остановись|тормоз)(\s|$)""")

    private val motionHint = Regex(
        """лев|прав|перед|еха|езж|зад|стоп|стой|тормоз|бок|гуля|след|пойд|повор|крут|развер""",
    )

    private val rules = listOf(
        Regex("""(стоп|стой|остановись|тормоз)""") to DriveAction.STOP,
        Regex("""(за\s*мной|следуй|подойди)""") to DriveAction.FOLLOW,
        Regex("""(погуляй|гуляй|объезжай|сам(\s+езди|\s+поезжай)?)""") to DriveAction.WANDER,
        Regex("""(назад)""") to DriveAction.BACKWARD,
        Regex("""(боком\s+(на\s*)?лев|в\s*лев\w*\s+боком)""") to DriveAction.STRAFE_LEFT,
        Regex("""(боком\s+(на\s*)?прав|в\s*прав\w*\s+боком)""") to DriveAction.STRAFE_RIGHT,
        Regex("""(на\s*лев[аоеы]?|в\s*лев[аоеы]?|левее|левей|(^|\s)лево(\s|$)|поверн\w*\s*(на\s*)?лев)""") to DriveAction.ROTATE_LEFT,
        Regex("""(на\s*прав[аоеы]?|в\s*прав[аоеы]?|правее|правей|(^|\s)право(\s|$)|поверн\w*\s*(на\s*)?прав)""") to DriveAction.ROTATE_RIGHT,
        Regex("""(поехали|в\s*перед|езжай|поезжай)""") to DriveAction.FORWARD,
    )

    fun parse(phrase: String): DriveAction? {
        val command = commandAfterName(phrase) ?: return null
        return matchCommand(command)
    }

    fun parseAny(phrases: Iterable<String>): DriveAction? =
        phrases.firstNotNullOfOrNull { parse(it) }

    /** Имя + похоже на руление — в LLM не отправлять, даже если команду не разобрали. */
    fun isChassisTalk(phrase: String): Boolean {
        val command = commandAfterName(phrase) ?: return false
        if (command.isEmpty()) return false
        if (matchCommand(command) != null) return true
        val compact = command.replace(" ", "")
        return motionHint.containsMatchIn(command) || motionHint.containsMatchIn(compact)
    }

    fun containsStopWord(phrase: String): Boolean {
        if (phrase.isBlank()) return false
        return motorStopWord.containsMatchIn(normalize(phrase))
    }

    private fun commandAfterName(phrase: String): String? {
        val normalized = normalize(phrase)
        if (normalized.isEmpty()) return null
        val tokens = normalized.split(' ')
        val commandStart = skipName(tokens) ?: return null
        return tokens.drop(commandStart).joinToString(" ")
            .replace(trailingFluff, "")
            .trim()
    }

    private fun matchCommand(command: String): DriveAction? {
        if (command.isEmpty()) return null
        val compact = command.replace(" ", "")
        return rules.firstOrNull { (pattern, _) ->
            pattern.containsMatchIn(command) || pattern.containsMatchIn(compact)
        }?.second
    }

    private fun skipName(tokens: List<String>): Int? {
        if (tokens.isEmpty()) return null
        var index = 0
        if (tokens[index] == "эй") {
            index++
            if (index >= tokens.size) return null
        }
        if (!looksLikeJasper(tokens[index])) return null
        return index + 1
    }

    private fun looksLikeJasper(token: String): Boolean {
        if (token.length !in 5..12) return false
        if (token.startsWith("джас") ||
            token.startsWith("джаз") ||
            token.startsWith("джес") ||
            token.startsWith("джез") ||
            token.startsWith("jasp")
        ) {
            return true
        }
        return NAME_ALIASES.any { alias -> levenshtein(token, alias) <= 2 }
    }

    internal fun normalize(phrase: String): String =
        phrase
            .lowercase()
            .replace('ё', 'е')
            .replace(Regex("""[^\p{L}\p{N}\s]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()

    private fun levenshtein(left: String, right: String): Int {
        if (left == right) return 0
        if (left.isEmpty()) return right.length
        if (right.isEmpty()) return left.length
        val previous = IntArray(right.length + 1) { it }
        val current = IntArray(right.length + 1)
        for (i in left.indices) {
            current[0] = i + 1
            for (j in right.indices) {
                val cost = if (left[i] == right[j]) 0 else 1
                current[j + 1] = minOf(
                    current[j] + 1,
                    previous[j + 1] + 1,
                    previous[j] + cost,
                )
            }
            for (j in previous.indices) previous[j] = current[j]
        }
        return previous[right.length]
    }

    private val NAME_ALIASES = listOf("джаспер", "джазпер", "джеспер", "jasper")
}
