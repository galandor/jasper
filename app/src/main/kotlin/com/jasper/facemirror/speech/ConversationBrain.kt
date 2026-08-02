package com.jasper.facemirror.speech

import com.jasper.facemirror.model.GreetingReply
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * LLM отвечает на любую фразу; без ключа — только локальные приветствия.
 * Предыдущий запрос отменяется при новой фразе или перебивании.
 */
class ConversationBrain(
    private val scope: CoroutineScope,
    private val llm: LlmConversationResponder = LlmConversationResponder(),
    private val local: GreetingDetector = GreetingDetector,
) {
    private var activeJob: Job? = null

    fun cancelPending() {
        activeJob?.cancel()
        activeJob = null
    }

    fun respondToPhrase(
        phrase: String,
        history: List<String> = emptyList(),
        onReply: (GreetingReply) -> Unit,
    ) {
        cancelPending()
        activeJob = scope.launch {
            try {
                val reply = if (llm.isAvailable) {
                    llm.respond(phrase, history) ?: local.match(phrase)
                } else {
                    local.match(phrase)
                }
                if (isActive) {
                    reply?.let {
                        withContext(Dispatchers.Main) { onReply(it) }
                    }
                }
            } catch (_: CancellationException) {
                // Новая фраза или перебивание — тихо отменяем
            }
        }
    }
}
