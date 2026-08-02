package com.jasper.facemirror.audio

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import com.jasper.facemirror.model.GreetingReply
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

class JasperVoiceSpeaker(
    context: Context,
) {
    private val tts = TextToSpeech(context.applicationContext, ::onInit)
    private val ready = AtomicBoolean(false)
    private var onSpeakingChanged: ((Boolean) -> Unit)? = null
    private var onComplete: (() -> Unit)? = null
    private var preferredVoice: Voice? = null

    fun setOnSpeakingChanged(listener: (Boolean) -> Unit) {
        onSpeakingChanged = listener
    }

    fun speakGreeting(reply: GreetingReply, onComplete: () -> Unit = {}) {
        if (!ready.get()) {
            onComplete()
            return
        }
        this.onComplete = onComplete

        preferredVoice?.let { tts.voice = it }

        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, UTTERANCE_GREETING)
        }

        tts.setPitch(reply.pitch)
        tts.setSpeechRate(reply.speechRate)
        tts.speak(reply.text, TextToSpeech.QUEUE_FLUSH, params, UTTERANCE_GREETING)
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
        preferredVoice = pickRussianVoice()
        preferredVoice?.let { tts.voice = it }

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

    /**
     * Для мультяшного звучания предпочитаем лёгкий/высокий голос.
     * На разных телефонах набор голосов разный — это best effort.
     */
    private fun pickRussianVoice(): Voice? {
        val russianVoices = tts.voices
            ?.filter { voice ->
                voice.locale.language == "ru"
            }
            ?.sortedByDescending { it.quality }
            .orEmpty()

        if (russianVoices.isEmpty()) return null

        val cartoonKeywords = listOf(
            "child", "kid", "junior", "xenia", "milena",
            "female", "женск", "network", "local",
        )
        return russianVoices.firstOrNull { voice ->
            cartoonKeywords.any { keyword ->
                voice.name.contains(keyword, ignoreCase = true)
            }
        } ?: russianVoices.first()
    }

    companion object {
        private const val UTTERANCE_GREETING = "greeting"
    }
}
