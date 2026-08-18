package com.jasper.facemirror.speech

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.jasper.facemirror.debug.JasperTiming
import com.jasper.facemirror.model.SpeechState
import org.json.JSONObject
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import java.util.concurrent.Executors

/**
 * Непрерывный офлайн STT на Vosk. Тот же публичный API, что раньше был
 * у обёртки над Google `SpeechRecognizer`: pause/resume для TTS, acknowledge,
 * consume на ранней команде с partial.
 */
class SpeechRecognizerEngine(
    context: Context,
    private val onState: (SpeechState) -> Unit,
) : RecognitionListener {

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val unpackExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "jasper-vosk-init").apply { isDaemon = true }
    }

    private var model: Model? = null
    private var recognizer: Recognizer? = null
    private var speechService: SpeechService? = null

    private var state = SpeechState()
    private var shouldRun = false
    private var paused = false
    private var utteranceConsumed = false
    private var startGeneration = 0
    private var loadAttempts = 0

    private var listenStartedAt = 0L
    private var readyAt = 0L
    private var firstPartialAt = 0L
    private var speechBeginAt = 0L
    private var lastPartial = ""

    fun start() {
        JasperTiming.event(
            "STT старт движка",
            "engine=vosk model=small-ru-0.22 " +
                "device=${Build.MODEL} sdk=${Build.VERSION.SDK_INT}",
        )
        shouldRun = true
        paused = false
        val gen = ++startGeneration
        unpackExecutor.execute { loadAndListen(gen) }
    }

    fun stop() {
        shouldRun = false
        startGeneration++
        runOnMain {
            tearDownMic()
            recognizer?.close()
            recognizer = null
            model?.close()
            model = null
            updateState { SpeechState.Idle }
        }
    }

    fun pauseListening() {
        paused = true
        JasperTiming.event("STT пауза (TTS)")
        runOnMain {
            speechService?.setPause(true)
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
        JasperTiming.event("STT resume через 250мс")
        if (!shouldRun) return
        mainHandler.postDelayed({
            if (!shouldRun || paused) return@postDelayed
            speechService?.reset()
            speechService?.setPause(false)
            beginUtteranceClock()
            JasperTiming.event("STT слушаю")
            updateState { copy(isListening = true) }
        }, TTS_RESUME_DELAY_MS)
    }

    /** Сбрасывает обработанную фразу, чтобы можно было сказать её снова. */
    fun acknowledgePhrase() {
        runOnMain {
            updateState { copy(recognizedText = "", partialText = "", recognizedAlternatives = emptyList()) }
        }
    }

    /**
     * Команда уже взята с partial — сбрасываем текущую реплику Vosk,
     * чтобы хвост той же фразы не ушёл вторым финалом.
     */
    fun consumeUtteranceAndRestart() {
        runOnMain {
            utteranceConsumed = true
            lastPartial = ""
            speechService?.reset()
            beginUtteranceClock()
            updateState {
                copy(
                    recognizedText = "",
                    partialText = "",
                    recognizedAlternatives = emptyList(),
                    isSpeaking = false,
                    mouthOpen = 0f,
                )
            }
        }
    }

    private fun loadAndListen(gen: Int) {
        try {
            val unpackStarted = JasperTiming.now()
            val dir = VoskModelStore.unpack(appContext)
            JasperTiming.elapsed("STT распаковка модели", unpackStarted, dir.absolutePath)
            if (!shouldRun || gen != startGeneration) return

            val loadStarted = JasperTiming.now()
            LibVosk.setLogLevel(LogLevel.WARNINGS)
            val loadedModel = Model(dir.absolutePath)
            val loadedRecognizer = Recognizer(loadedModel, SAMPLE_RATE)
            JasperTiming.elapsed("STT модель в памяти", loadStarted)
            loadAttempts = 0

            mainHandler.post {
                if (!shouldRun || gen != startGeneration) {
                    loadedRecognizer.close()
                    loadedModel.close()
                    return@post
                }
                model = loadedModel
                recognizer = loadedRecognizer
                startMic(loadedRecognizer)
            }
        } catch (e: Exception) {
            JasperTiming.event("STT vosk ошибка загрузки", e.message ?: e.javaClass.simpleName)
            if (!shouldRun || gen != startGeneration) return
            loadAttempts++
            if (loadAttempts <= MAX_LOAD_ATTEMPTS) {
                mainHandler.postDelayed({
                    if (shouldRun && gen == startGeneration) {
                        unpackExecutor.execute { loadAndListen(gen) }
                    }
                }, 2_000L)
            }
        }
    }

    private fun startMic(rec: Recognizer) {
        if (!shouldRun || paused) return
        try {
            if (speechService == null) {
                speechService = SpeechService(rec, SAMPLE_RATE)
            }
            beginUtteranceClock()
            JasperTiming.event("STT слушаю")
            val started = speechService?.startListening(this) == true
            if (started) {
                readyAt = JasperTiming.now()
                JasperTiming.elapsed("STT готов", listenStartedAt)
                updateState { copy(isListening = true) }
            } else {
                JasperTiming.event("STT vosk уже слушает")
                updateState { copy(isListening = true) }
            }
        } catch (e: Exception) {
            JasperTiming.event("STT vosk микрофон", e.message ?: e.javaClass.simpleName)
            tearDownMic()
            mainHandler.postDelayed({
                if (shouldRun && !paused) recognizer?.let { startMic(it) }
            }, 1_000L)
        }
    }

    private fun tearDownMic() {
        try {
            speechService?.cancel()
            speechService?.shutdown()
        } catch (_: Exception) {
        }
        speechService = null
    }

    private fun beginUtteranceClock() {
        listenStartedAt = JasperTiming.now()
        readyAt = 0L
        firstPartialAt = 0L
        speechBeginAt = 0L
        lastPartial = ""
        utteranceConsumed = false
    }

    override fun onPartialResult(hypothesis: String?) {
        val text = jsonField(hypothesis, "partial")
        if (text.isEmpty()) {
            if (state.partialText.isNotEmpty() && !utteranceConsumed) {
                updateState { copy(partialText = "", isSpeaking = false) }
            }
            return
        }
        if (utteranceConsumed) {
            utteranceConsumed = false
        }
        if (speechBeginAt == 0L) {
            speechBeginAt = JasperTiming.now()
            JasperTiming.elapsed("STT начал слышать речь", listenStartedAt)
        }
        if (firstPartialAt == 0L) {
            firstPartialAt = JasperTiming.now()
            JasperTiming.elapsed("STT первый partial", listenStartedAt, "'$text'")
        }
        lastPartial = text
        updateState {
            copy(
                partialText = text,
                isSpeaking = true,
                isListening = true,
            )
        }
    }

    override fun onResult(hypothesis: String?) {
        emitFinal(jsonField(hypothesis, "text"), fromEndpoint = true)
    }

    override fun onFinalResult(hypothesis: String?) {
        emitFinal(jsonField(hypothesis, "text"), fromEndpoint = false)
    }

    override fun onError(exception: Exception?) {
        JasperTiming.event(
            "STT vosk ошибка",
            exception?.message ?: exception?.javaClass?.simpleName.orEmpty(),
        )
        if (!shouldRun || paused) return
        tearDownMic()
        recognizer?.let { rec ->
            mainHandler.postDelayed({
                if (shouldRun && !paused) startMic(rec)
            }, 400L)
        }
    }

    override fun onTimeout() {
        JasperTiming.event("STT vosk timeout")
        if (shouldRun && !paused) {
            speechService?.startListening(this)
        }
    }

    private fun emitFinal(text: String, fromEndpoint: Boolean) {
        if (utteranceConsumed) {
            utteranceConsumed = false
            beginUtteranceClock()
            return
        }
        if (text.isEmpty()) {
            beginUtteranceClock()
            updateState { copy(partialText = "", isSpeaking = false, isListening = true) }
            return
        }
        val source = if (fromEndpoint) "endpoint" else "stop"
        val now = JasperTiming.now()
        JasperTiming.elapsed(
            "STT финал",
            listenStartedAt,
            "фраза='$text' источник=$source " +
                "ждал_ready=${delta(readyAt, listenStartedAt)}мс " +
                "первый_partial=${delta(firstPartialAt, listenStartedAt)}мс " +
                "речь=${delta(now, speechBeginAt)}мс",
        )
        updateState {
            copy(
                recognizedText = text,
                partialText = "",
                recognizedAlternatives = emptyList(),
                history = (history + text).takeLast(5),
                isSpeaking = false,
                isListening = true,
            )
        }
        beginUtteranceClock()
    }

    private fun updateState(transform: SpeechState.() -> SpeechState) {
        state = state.transform()
        onState(state)
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }

    private fun delta(at: Long, from: Long): Long = if (at > 0L) at - from else -1L

    companion object {
        private const val SAMPLE_RATE = 16_000.0f
        private const val TTS_RESUME_DELAY_MS = 250L
        private const val MAX_LOAD_ATTEMPTS = 3

        private fun jsonField(raw: String?, key: String): String {
            if (raw.isNullOrBlank()) return ""
            return try {
                JSONObject(raw).optString(key, "").trim()
            } catch (_: Exception) {
                ""
            }
        }
    }
}
