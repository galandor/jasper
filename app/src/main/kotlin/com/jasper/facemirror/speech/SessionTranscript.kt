package com.jasper.facemirror.speech

data class ChatTurn(
    val fromJasper: Boolean,
    val text: String,
)

/** Реплики текущей сессии приложения. Живёт, пока жив [ConversationBrain]. */
class SessionTranscript(
    private val maxTurns: Int = DEFAULT_MAX_TURNS,
) {
    private val turns = ArrayDeque<ChatTurn>()

    @Synchronized
    fun addUser(text: String) = add(fromJasper = false, text)

    @Synchronized
    fun addJasper(text: String) = add(fromJasper = true, text)

    @Synchronized
    fun snapshot(): List<ChatTurn> = turns.toList()

    @get:Synchronized
    val size: Int get() = turns.size

    private fun add(fromJasper: Boolean, text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        turns.addLast(ChatTurn(fromJasper, trimmed))
        while (turns.size > maxTurns) {
            turns.removeFirst()
        }
    }

    companion object {
        const val DEFAULT_MAX_TURNS = 50
    }
}
