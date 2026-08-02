package com.jasper.facemirror.speech

import com.jasper.facemirror.model.GreetingReply
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * LLM отвечает на любую фразу; без ключа — только локальные приветствия.
 */
class ConversationBrain(
    private val scope: CoroutineScope,
    private val llm: LlmConversationResponder = LlmConversationResponder(),
    private val local: GreetingDetector = GreetingDetector,
) {
    fun respondToPhrase(
        phrase: String,
        history: List<String> = emptyList(),
        onReply: (GreetingReply) -> Unit,
    ) {
        scope.launch {
            val reply = if (llm.isAvailable) {
                llm.respond(phrase, history) ?: local.match(phrase)
            } else {
                local.match(phrase)
            }
            reply?.let {
                withContext(Dispatchers.Main) { onReply(it) }
            }
        }
    }
}
