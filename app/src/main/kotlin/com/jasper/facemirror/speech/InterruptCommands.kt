package com.jasper.facemirror.speech

/** Мгновенные команды без LLM — перебивают речь Jasper. */
object InterruptCommands {
    private val stopPattern = Regex(
        """^(стоп|тише|тихо|подожди|замолчи|хватит|заткнись)(\s|$)""",
        RegexOption.IGNORE_CASE,
    )

    fun isStopCommand(phrase: String): Boolean =
        stopPattern.containsMatchIn(phrase.trim())
}
