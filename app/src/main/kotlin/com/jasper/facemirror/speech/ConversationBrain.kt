package com.jasper.facemirror.speech

import android.util.Log
import com.jasper.facemirror.chassis.DriveAction
import com.jasper.facemirror.chassis.DriveIntentClassifier
import com.jasper.facemirror.debug.JasperTiming
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
 * Сначала классификатор команд машинки (только если фраза похожа на руление),
 * потом обычный диалог. Классификатор не должен глушить разговор:
 * при ошибке/таймауте идём в чат.
 */
class ConversationBrain(
    private val scope: CoroutineScope,
    private val llm: LlmConversationResponder = LlmConversationResponder(),
    private val local: GreetingDetector = GreetingDetector,
    private val driveClassifier: DriveIntentClassifier = DriveIntentClassifier(),
) {
    private var activeJob: Job? = null
    private val session = SessionTranscript()

    fun cancelPending() {
        activeJob?.cancel()
        activeJob = null
    }

    fun respondToPhrase(
        phrase: String,
        alternatives: List<String> = emptyList(),
        classifyDrive: Boolean = true,
        onDrive: (DriveAction) -> Unit = {},
        onReply: (GreetingReply) -> Unit,
        onNoReply: () -> Unit = {},
    ) {
        cancelPending()
        activeJob = scope.launch {
            val brainStartedAt = JasperTiming.now()
            try {
                val transcripts = (listOf(phrase) + alternatives).distinct()
                JasperTiming.event(
                    "мозг старт",
                    "phrase='$phrase' alts=$alternatives llm=${llm.isAvailable} " +
                        "classifier=${driveClassifier.isAvailable} classifyDrive=$classifyDrive " +
                        "session=${session.size}",
                )
                if (classifyDrive && driveClassifier.isAvailable) {
                    val classifyStartedAt = JasperTiming.now()
                    var timedOut = true
                    val drive = withTimeoutOrNull(CLASSIFY_TIMEOUT_MS) {
                        val result = driveClassifier.classify(transcripts)
                        timedOut = false
                        result
                    }
                    if (timedOut) {
                        JasperTiming.elapsed(
                            "мозг классификатор",
                            classifyStartedAt,
                            "ТАЙМАУТ ${CLASSIFY_TIMEOUT_MS}мс",
                        )
                    } else {
                        JasperTiming.elapsed(
                            "мозг классификатор",
                            classifyStartedAt,
                            "результат=$drive",
                        )
                    }
                    if (drive != null) {
                        JasperTiming.elapsed("мозг итог", brainStartedAt, "путь=gemini_drive команда=$drive")
                        if (isActive) {
                            withContext(Dispatchers.Main) { onDrive(drive) }
                        }
                        return@launch
                    }
                } else if (!classifyDrive) {
                    JasperTiming.event("мозг классификатор", "пропущен — не похоже на руление")
                }
                val chatStartedAt = JasperTiming.now()
                val prior = session.snapshot()
                val reply = if (llm.isAvailable) {
                    llm.respond(phrase, prior) ?: local.match(phrase)
                } else {
                    local.match(phrase)
                }
                JasperTiming.elapsed(
                    "мозг чат",
                    chatStartedAt,
                    if (reply != null) "ответ='${reply.text}'" else "нет ответа",
                )
                JasperTiming.elapsed(
                    "мозг итог",
                    brainStartedAt,
                    if (reply != null) "путь=чат" else "путь=тишина",
                )
                if (isActive) {
                    withContext(Dispatchers.Main) {
                        if (!isActive) return@withContext
                        if (reply != null) {
                            session.addUser(phrase)
                            session.addJasper(reply.text)
                            JasperTiming.event(
                                "мозг сессия",
                                "ходов=${session.size} jasper='${reply.text}'",
                            )
                            onReply(reply)
                        } else {
                            onNoReply()
                        }
                    }
                }
            } catch (e: CancellationException) {
                JasperTiming.elapsed("мозг итог", brainStartedAt, "отменён")
                throw e
            } catch (e: Exception) {
                JasperTiming.elapsed("мозг итог", brainStartedAt, "ошибка ${e.message}")
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
