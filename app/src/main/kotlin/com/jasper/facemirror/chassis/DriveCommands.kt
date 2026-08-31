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
    CONNECT(' ', hold = false, holdMs = 0, ack = null),
}

/**
 * Движение только с именем в начале (джаспер / жаспер / аспер).
 * «Стоп» — исключение: без имени, гасит и езду, и игру.
 */
object DriveCommands {
    private val trailingFluff = Regex("""\s+(пожалуйста|давай|ну)$""")

    private val motorStopWord = Regex("""(^|\s)(стоп|стой|остановись|тормоз)(\s|$)""")

    private val motionHint = Regex(
        """лев|прав|перед|прям|еха|езж|едь|едем|еди|иди|зад|стоп|стой|тормоз|бок|гуля|след|пойд|повор|крут|развер|подключ|соедини|машин|блютуз""",
    )

    /** «боком» произносят и до направления, и после: «налево боком». */
    private val sidewaysWord = Regex("""бок(ом)?""")

    private val rules = listOf(
        Regex("""(стоп|стой|остановись|тормоз)""") to DriveAction.STOP,
        Regex("""(подключ\w*|переподключ\w*|соедини\w*|коннект|блютуз)""") to DriveAction.CONNECT,
        Regex("""(за\s*мной|следуй|подойди)""") to DriveAction.FOLLOW,
        Regex("""(погуляй|гуляй|объезжай|сам(\s+езди|\s+поезжай)?)""") to DriveAction.WANDER,
        Regex("""(назад)""") to DriveAction.BACKWARD,
        Regex("""(боком\s+(на\s*)?лев|в\s*лев\w*\s+боком)""") to DriveAction.STRAFE_LEFT,
        Regex("""(боком\s+(на\s*)?прав|в\s*прав\w*\s+боком)""") to DriveAction.STRAFE_RIGHT,
        Regex("""(на\s*лев[аоеы]?|в\s*лев[аоеы]?|слева|левее|левей|(^|\s)лево(\s|$)|поверн\w*\s*(на\s*)?лев)""") to DriveAction.ROTATE_LEFT,
        Regex("""(на\s*прав[аоеы]?|в\s*прав[аоеы]?|справа|правее|правей|(^|\s)право(\s|$)|поверн\w*\s*(на\s*)?прав)""") to DriveAction.ROTATE_RIGHT,
        Regex("""(поехали|поехал|вперед|в\s*перед|прямо|езжай|поезжай|едь|едем)""") to DriveAction.FORWARD,
    )

    fun parse(phrase: String): DriveAction? {
        val command = commandAfterName(phrase)
        if (command != null) return matchCommand(command)
        val tokens = tokenize(phrase)
        if (tokens.size >= 2 && looksLikeJasper(tokens[0])) {
            return matchCommand(tokens.drop(1).joinToString(" ").replace(trailingFluff, "").trim())
        }
        if (containsStopWord(phrase)) return DriveAction.STOP
        return null
    }

    fun parseAny(phrases: Iterable<String>): DriveAction? {
        val list = phrases.toList()
        list.firstNotNullOfOrNull { parse(it) }?.let { return it }
        if (list.any { addressed(it) }) {
            list.firstNotNullOfOrNull { matchCommand(normalize(it)) }?.let { return it }
        }
        if (list.any { containsStopWord(it) }) return DriveAction.STOP
        return null
    }

    /**
     * Все команды фразы в порядке произнесения.
     * Движение — только после имени; «стоп» можно без имени.
     */
    fun parseSequence(phrase: String): List<DriveAction> {
        val tokens = tokenize(phrase)
        val actions = mutableListOf<DriveAction>()
        var index = 0
        while (index < tokens.size) {
            val stop = matchStopAt(tokens, index)
            if (stop != null) {
                if (actions.lastOrNull() != DriveAction.STOP) actions += DriveAction.STOP
                index += stop
                continue
            }
            val commandIndex = skipNameFrom(tokens, index)
            if (commandIndex == null) {
                index++
                continue
            }
            if (commandIndex >= tokens.size) break
            val matched = matchAt(tokens, commandIndex)
            if (matched == null) {
                index = maxOf(commandIndex, index + 1)
                continue
            }
            var (size, action) = matched
            val strafe = strafeUpgrade(action, tokens.getOrNull(commandIndex + size))
            if (strafe != null) {
                action = strafe
                size++
            }
            if (actions.lastOrNull() != action) actions += action
            index = commandIndex + size
        }
        return actions
    }

    fun parseSequenceAny(phrases: Iterable<String>): List<DriveAction> =
        phrases.map { parseSequence(it) }.maxByOrNull { it.size }.orEmpty()

    /**
     * Самое короткое совпадение, начинающееся с [index]: правила ищут подстроку,
     * поэтому широкое окно поймало бы команду, сказанную позже.
     */
    private fun matchAt(tokens: List<String>, index: Int): Pair<Int, DriveAction>? {
        for (size in 1..MAX_COMMAND_TOKENS) {
            if (index + size > tokens.size) return null
            val chunk = tokens.subList(index, index + size)
            if (chunk.drop(1).any { isAddressToken(it) }) return null
            val action = matchCommand(chunk.joinToString(" "))
            if (action != null) return size to action
        }
        return null
    }

    private fun strafeUpgrade(action: DriveAction, next: String?): DriveAction? {
        if (next == null || !sidewaysWord.matches(next)) return null
        return when (action) {
            DriveAction.ROTATE_LEFT -> DriveAction.STRAFE_LEFT
            DriveAction.ROTATE_RIGHT -> DriveAction.STRAFE_RIGHT
            else -> null
        }
    }

    private fun isAddressToken(token: String): Boolean = token == "эй" || looksLikeJasper(token)

    private fun matchStopAt(tokens: List<String>, index: Int): Int? {
        if (index >= tokens.size) return null
        return if (containsStopWord(tokens[index])) 1 else null
    }

    private fun skipNameFrom(tokens: List<String>, index: Int): Int? {
        if (index >= tokens.size) return null
        val relative = skipName(tokens.subList(index, tokens.size)) ?: return null
        return index + relative
    }

    /** Похоже на руление: имя + движение. Без имени это чат или игра. */
    fun isChassisTalk(phrase: String): Boolean {
        val command = commandAfterName(phrase)
        if (command != null) {
            if (command.isEmpty()) return false
            if (matchCommand(command) != null) return true
            val compact = command.replace(" ", "")
            if (motionHint.containsMatchIn(command) || motionHint.containsMatchIn(compact)) return true
        }
        val tokens = tokenize(phrase)
        if (tokens.size >= 2 && looksLikeJasper(tokens[0])) {
            val rest = tokens.drop(1).joinToString(" ")
            if (matchCommand(rest) != null || motionHint.containsMatchIn(rest)) return true
        }
        return false
    }

    /** Только имя («Джаспер», «эй джазпер») — ждём команду, без Gemini. */
    fun isNameOnly(phrase: String): Boolean {
        val command = commandAfterName(phrase) ?: return false
        return command.isEmpty()
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

    private fun tokenize(phrase: String): List<String> =
        normalize(phrase).split(' ').filter { it.isNotEmpty() }

    private fun addressed(phrase: String): Boolean {
        val tokens = tokenize(phrase)
        return tokens.isNotEmpty() && looksLikeJasper(tokens[0])
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
        if (token.length !in 4..14) return false
        if (NAME_PREFIXES.any { token.startsWith(it) }) return true
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

    private const val MAX_COMMAND_TOKENS = 3

    private val NAME_ALIASES = listOf(
        "джаспер", "жаспер", "аспер", "джазпер", "джеспер", "jasper",
    )

    private val NAME_PREFIXES = listOf(
        "джас", "джаз", "джес", "джез", "jasp",
        "жас", "жаз", "асп", "гас", "газ", "расп", "rasp", "спер", "ясп",
    )
}
