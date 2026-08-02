package com.jasper.facemirror.audio

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import com.jasper.facemirror.model.GreetingReply
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.random.Random

class JasperVoiceSpeaker(
    context: Context,
) {
    private val tts = TextToSpeech(context.applicationContext, ::onInit)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val ready = AtomicBoolean(false)
    private var onSpeakingChanged: ((Boolean) -> Unit)? = null
    private var onLipPulse: ((Float) -> Unit)? = null
    private var onComplete: (() -> Unit)? = null
    private var preferredVoice: Voice? = null
    private var lipFallbackRunnable: Runnable? = null

    fun setOnSpeakingChanged(listener: (Boolean) -> Unit) {
        onSpeakingChanged = listener
    }

    fun setOnLipPulse(listener: (Float) -> Unit) {
        onLipPulse = listener
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
        stopLipFallback()
        tts.stop()
        mainHandler.post {
            onSpeakingChanged?.invoke(false)
            onComplete?.invoke()
            onComplete = null
        }
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
                mainHandler.post {
                    onSpeakingChanged?.invoke(true)
                    startLipFallback()
                }
            }

            override fun onRangeStart(
                utteranceId: String?,
                start: Int,
                end: Int,
                frame: Int,
            ) {
                val syllableWeight = (end - start).coerceIn(1, 12) / 12f
                val openness = (0.45f + syllableWeight * 0.5f + Random.nextFloat() * 0.12f)
                    .coerceIn(0.35f, 1f)
                mainHandler.post { onLipPulse?.invoke(openness) }
            }

            override fun onDone(utteranceId: String?) {
                mainHandler.post {
                    stopLipFallback()
                    onSpeakingChanged?.invoke(false)
                    onComplete?.invoke()
                    onComplete = null
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                mainHandler.post {
                    stopLipFallback()
                    onSpeakingChanged?.invoke(false)
                    onComplete?.invoke()
                    onComplete = null
                }
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                mainHandler.post {
                    stopLipFallback()
                    onSpeakingChanged?.invoke(false)
                    onComplete?.invoke()
                    onComplete = null
                }
            }
        })
        ready.set(true)
    }

    private fun startLipFallback() {
        stopLipFallback()
        val runnable = object : Runnable {
            override fun run() {
                onLipPulse?.invoke(0.3f + Random.nextFloat() * 0.45f)
                mainHandler.postDelayed(this, 95L + Random.nextLong(40))
            }
        }
        lipFallbackRunnable = runnable
        mainHandler.post(runnable)
    }

    private fun stopLipFallback() {
        lipFallbackRunnable?.let { mainHandler.removeCallbacks(it) }
        lipFallbackRunnable = null
    }

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
