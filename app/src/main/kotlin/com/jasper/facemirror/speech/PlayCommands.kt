package com.jasper.facemirror.speech

/** Игра включается только фразой «Давай играть», выключается «Стоп». */
object PlayCommands {
    private val startPhrase = Regex("""давай\s+играть""")

    private val invite = Regex(
        """сыграем|поиграем|давай\s+в\s+|хочешь\s+(по)?играть|во что сыграем|данетки|загадк""",
        RegexOption.IGNORE_CASE,
    )

    fun isStart(phrase: String): Boolean {
        if (phrase.isBlank()) return false
        return startPhrase.containsMatchIn(normalize(phrase))
    }

    fun isInvite(text: String): Boolean {
        if (text.isBlank()) return false
        return invite.containsMatchIn(text)
    }

    internal fun normalize(phrase: String): String =
        phrase
            .lowercase()
            .replace('ё', 'е')
            .replace(Regex("""[^\p{L}\p{N}\s]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
}
