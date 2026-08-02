package com.jasper.facemirror.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.jasper.facemirror.model.SpeechState
import java.util.Locale

class SpeechRecognizerEngine(
    private val context: Context,
    private val onState: (SpeechState) -> Unit,
) : RecognitionListener {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null
    private var state = SpeechState()
    private var shouldRun = false
    private var paused = false

    fun start() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) return
        shouldRun = true
        mainHandler.post {
            if (speechRecognizer == null) {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).also {
                    it.setRecognitionListener(this)
                }
            }
            updateState { copy(isListening = true) }
            startListening()
        }
    }

    fun stop() {
        shouldRun = false
        mainHandler.post {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
            speechRecognizer = null
            updateState { SpeechState.Idle }
        }
    }

    fun pauseListening() {
        paused = true
        mainHandler.post {
            speechRecognizer?.cancel()
            updateState {
                copy(
                    partialText = "",
                    isSpeaking = false,
                    mouthOpen = 0f,
                )
            }
        }
    }

    fun resumeListening() {
        paused = false
        if (shouldRun) scheduleRestart(250L)
    }

    /** Сбрасывает обработанную фразу, чтобы можно было сказать её снова. */
    fun acknowledgePhrase() {
        mainHandler.post {
            updateState { copy(recognizedText = "", partialText = "") }
        }
    }

    private fun startListening() {
        if (!shouldRun || paused || speechRecognizer == null) return
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale("ru", "RU").toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, Locale("ru", "RU").toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }
        speechRecognizer?.startListening(intent)
    }

    private fun scheduleRestart(delayMs: Long = 150L) {
        if (!shouldRun || paused) return
        mainHandler.postDelayed({ startListening() }, delayMs)
    }

    private fun updateState(transform: SpeechState.() -> SpeechState) {
        state = state.transform()
        onState(state)
    }

    override fun onReadyForSpeech(params: Bundle?) {
        updateState { copy(isListening = true) }
    }

    override fun onBeginningOfSpeech() {
        updateState { copy(isSpeaking = true) }
    }

    override fun onRmsChanged(rmsdB: Float) {
        val amplitude = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
        val mouth = if (amplitude > 0.08f) {
            ((amplitude - 0.08f) / 0.92f).coerceIn(0f, 1f)
        } else {
            0f
        }
        updateState {
            copy(
                amplitude = amplitude,
                mouthOpen = mouth,
                isSpeaking = mouth > 0.1f || partialText.isNotEmpty(),
            )
        }
    }

    override fun onPartialResults(partialResults: Bundle?) {
        val text = partialResults
            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()
            ?.trim()
            .orEmpty()
        if (text.isNotEmpty()) {
            updateState { copy(partialText = text, isSpeaking = true) }
        }
    }

    override fun onResults(results: Bundle?) {
        val text = results
            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()
            ?.trim()
            .orEmpty()

        if (text.isNotEmpty()) {
            updateState {
                copy(
                    recognizedText = text,
                    partialText = "",
                    history = (history + text).takeLast(5),
                    isSpeaking = false,
                    mouthOpen = 0f,
                )
            }
        } else {
            updateState { copy(partialText = "", isSpeaking = false, mouthOpen = 0f) }
        }
        scheduleRestart()
    }

    override fun onEndOfSpeech() {
        updateState { copy(isSpeaking = partialText.isNotEmpty()) }
    }

    override fun onError(error: Int) {
        val delay = when (error) {
            SpeechRecognizer.ERROR_NO_MATCH,
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
            -> 80L
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> 400L
            else -> 300L
        }
        updateState {
            copy(
                partialText = if (error == SpeechRecognizer.ERROR_NO_MATCH) partialText else "",
                isSpeaking = false,
                mouthOpen = 0f,
            )
        }
        scheduleRestart(delay)
    }

    override fun onBufferReceived(buffer: ByteArray?) = Unit
    override fun onEvent(eventType: Int, params: Bundle?) = Unit
}
