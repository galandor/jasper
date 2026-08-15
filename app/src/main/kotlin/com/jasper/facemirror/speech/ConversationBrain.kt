package com.jasper.facemirror.speech

import android.util.Log
import com.jasper.facemirror.chassis.DriveAction
import com.jasper.facemirror.chassis.DriveIntentClassifier
import com.jasper.facemirror.model.GreetingReply
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Сначала крошечный классификатор команд машинки, потом обычный диалог.
 * Классификатор не должен глушить разговор: при ошибке/таймауте идём в чат.
 */
class ConversationBrain(
    private val scope: CoroutineScope,
    private val llm: LlmConversationResponder = LlmConversationResponder(),
    private val local: GreetingDetector = GreetingDetector,
    private val driveClassifier: DriveIntentClassifier = DriveIntentClassifier(),
) {
    private var activeJob: Job? = null

    fun cancelPending() {
        activeJob?.cancel()
        activeJob = null
    }

    fun respondToPhrase(
        phrase: String,
        history: List<String> = emptyList(),
        alternatives: List<String> = emptyList(),
        onDrive: (DriveAction) -> Unit = {},
        onReply: (GreetingReply) -> Unit,
        onNoReply: () -> Unit = {},
    ) {
        cancelPending()
        activeJob = scope.launch {
            try {
                val transcripts = (listOf(phrase) + alternatives).distinct()
                if (driveClassifier.isAvailable) {
                    val drive = withTimeoutOrNull(CLASSIFY_TIMEOUT_MS) {
                        driveClassifier.classify(transcripts)
                    }
                    if (drive != null) {
                        if (isActive) {
                            withContext(Dispatchers.Main) { onDrive(drive) }
                        }
                        return@launch
                    }
                }
                val reply = if (llm.isAvailable) {
                    llm.respond(phrase, history) ?: local.match(phrase)
                } else {
                    local.match(phrase)
                }
                if (isActive) {
                    withContext(Dispatchers.Main) {
                        if (reply != null) {
                            onReply(reply)
                        } else {
                            onNoReply()
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "respondToPhrase failed: ${e.message}")
                if (isActive) {
                    withContext(Dispatchers.Main) { onNoReply() }
                }
            }
        }
    }

    companion object {
        private const val TAG = "JasperChassis"
        private const val CLASSIFY_TIMEOUT_MS = 6_000L
    }
}
