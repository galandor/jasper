package com.jasper.facemirror.audio

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

class JasperVoiceSpeaker(
    context: Context,
) {
    private val tts = TextToSpeech(context.applicationContext, ::onInit)
    private val ready = AtomicBoolean(false)
    private var onSpeakingChanged: ((Boolean) -> Unit)? = null
    private var onComplete: (() -> Unit)? = null

    fun setOnSpeakingChanged(listener: (Boolean) -> Unit) {
        onSpeakingChanged = listener
    }

    fun speakHello(onComplete: () -> Unit = {}) {
        if (!ready.get()) {
            onComplete()
            return
        }
        this.onComplete = onComplete
        tts.speak(
            "Привет!",
            TextToSpeech.QUEUE_FLUSH,
            Bundle.EMPTY,
            UTTERANCE_HELLO,
        )
    }

    fun stop() {
        tts.stop()
        onSpeakingChanged?.invoke(false)
        onComplete?.invoke()
        onComplete = null
    }

    fun release() {
        stop()
        tts.shutdown()
    }

    private fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) return
        tts.language = Locale.forLanguageTag("ru-RU")
        tts.setSpeechRate(1.05f)
        tts.setPitch(1.2f)
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                onSpeakingChanged?.invoke(true)
            }

            override fun onDone(utteranceId: String?) {
                onSpeakingChanged?.invoke(false)
                onComplete?.invoke()
                onComplete = null
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                onSpeakingChanged?.invoke(false)
                onComplete?.invoke()
                onComplete = null
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                onSpeakingChanged?.invoke(false)
                onComplete?.invoke()
                onComplete = null
            }
        })
        ready.set(true)
    }

    companion object {
        private const val UTTERANCE_HELLO = "hello"
    }
}
