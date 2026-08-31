package com.jasper.facemirror.openbot

import com.jasper.facemirror.chassis.DriveCommands

enum class LearnIntent {
    START_RECORD,
    STOP_RECORD,
    START_AUTOPILOT,
    STOP_AUTOPILOT,
}

object LearnCommands {
    private val startRecord = Regex("""(записывай|запись|пиши\s+езд|учись|обучай)""")
    private val stopRecord = Regex("""(стоп\s+запись|хватит\s+писать|закончи\s+запись)""")
    private val startAuto = Regex("""(автопилот|езди\s+сам|поехал\s+сам|сам\s+езди)""")
    private val stopAuto = Regex("""(ручно|я\s+рулю|хватит\s+сам)""")

    fun parse(phrase: String): LearnIntent? {
        val n = DriveCommands.normalize(phrase)
        if (n.isEmpty()) return null
        return when {
            stopRecord.containsMatchIn(n) -> LearnIntent.STOP_RECORD
            startRecord.containsMatchIn(n) -> LearnIntent.START_RECORD
            stopAuto.containsMatchIn(n) -> LearnIntent.STOP_AUTOPILOT
            startAuto.containsMatchIn(n) -> LearnIntent.START_AUTOPILOT
            else -> null
        }
    }

    fun parseAny(phrases: Iterable<String>): LearnIntent? =
        phrases.firstNotNullOfOrNull { parse(it) }
}
