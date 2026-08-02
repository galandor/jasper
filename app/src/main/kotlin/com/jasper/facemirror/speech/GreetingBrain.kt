package com.jasper.facemirror.speech

import com.jasper.facemirror.model.GreetingReply
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Сначала пробует LLM (если настроен API-ключ), иначе — локальные правила.
 */
class GreetingBrain(
    private val scope: CoroutineScope,
    private val llm: LlmGreetingResponder = LlmGreetingResponder(),
    private val local: GreetingDetector = GreetingDetector,
) {
    fun respondToPhrase(
        phrase: String,
        onReply: (GreetingReply) -> Unit,
    ) {
        scope.launch {
            val reply = if (llm.isAvailable) {
                llm.respond(phrase) ?: local.match(phrase)
            } else {
                local.match(phrase)
            }
            reply?.let {
                withContext(Dispatchers.Main) { onReply(it) }
            }
        }
    }
}
