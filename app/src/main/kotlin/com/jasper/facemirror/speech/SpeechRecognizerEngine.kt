package com.jasper.facemirror.speech

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognitionService
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.jasper.facemirror.debug.JasperTiming
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
    private var listenSession: ListenSession? = null
    private var suppressErrorRestart = false
    private var utteranceConsumed = false

    fun start() {
        val available = SpeechRecognizer.isRecognitionAvailable(context)
        val onDevice = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
        JasperTiming.event(
            "STT старт движка",
            "available=$available onDevice=$onDevice " +
                "device=${android.os.Build.MODEL} sdk=${Build.VERSION.SDK_INT}",
        )
        if (!available) {
            JasperTiming.event("STT нет RecognitionService на устройстве")
            return
        }
        shouldRun = true
        mainHandler.post {
            if (speechRecognizer == null) {
                speechRecognizer = createPreferredRecognizer().also {
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
            listenSession = null
            updateState { SpeechState.Idle }
        }
    }

    fun pauseListening() {
        paused = true
        JasperTiming.event("STT пауза (TTS)")
        mainHandler.post {
            speechRecognizer?.cancel()
            listenSession = null
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
        if (shouldRun) scheduleRestart(250L)
    }

    /** Сбрасывает обработанную фразу, чтобы можно было сказать её снова. */
    fun acknowledgePhrase() {
        val clear = {
            updateState { copy(recognizedText = "", partialText = "", recognizedAlternatives = emptyList()) }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            clear()
        } else {
            mainHandler.post(clear)
        }
    }

    /**
     * Команда уже взята с partial — бросаем текущую сессию, чтобы Google
     * не ждал ещё секунду тишины.
     */
    fun consumeUtteranceAndRestart() {
        val restart = {
            utteranceConsumed = true
            listenSession?.consumed = true
            suppressErrorRestart = true
            listenSession = null
            speechRecognizer?.cancel()
            updateState {
                copy(
                    recognizedText = "",
                    partialText = "",
                    recognizedAlternatives = emptyList(),
                    isSpeaking = false,
                    mouthOpen = 0f,
                )
            }
            if (shouldRun && !paused) scheduleRestart(60L)
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            restart()
        } else {
            mainHandler.post(restart)
        }
    }

    private fun startListening() {
        if (!shouldRun || paused || speechRecognizer == null) {
            if (!paused) {
                JasperTiming.event(
                    "STT startListening пропущен",
                    "shouldRun=$shouldRun recognizer=${speechRecognizer != null}",
                )
            }
            return
        }
        listenSession = ListenSession()
        utteranceConsumed = false
        JasperTiming.event("STT слушаю")
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale("ru", "RU").toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, Locale("ru", "RU").toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            // Google иначе держит паузу ~1.5с после короткой команды
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 200)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 250)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 400)
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
        val session = listenSession
        if (session != null) {
            session.readyAt = JasperTiming.now()
            JasperTiming.elapsed("STT готов", session.startedAt)
        }
        updateState { copy(isListening = true) }
    }

    override fun onBeginningOfSpeech() {
        val session = listenSession
        if (session != null) {
            session.speechBeginAt = JasperTiming.now()
            JasperTiming.elapsed("STT начал слышать речь", session.startedAt)
        }
        updateState { copy(isSpeaking = true) }
    }

    override fun onRmsChanged(rmsdB: Float) {
        listenSession?.let { session ->
            if (rmsdB > session.peakRms) session.peakRms = rmsdB
        }
        // UI не использует RMS; не дёргаем Compose на каждом тике.
    }

    override fun onPartialResults(partialResults: Bundle?) {
        if (utteranceConsumed || listenSession?.consumed == true) return
        val all = partialResults
            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            .orEmpty()
        val text = all.firstOrNull().orEmpty()
        if (text.isNotEmpty()) {
            val session = listenSession
            if (session != null && session.firstPartialAt == 0L) {
                session.firstPartialAt = JasperTiming.now()
                JasperTiming.elapsed("STT первый partial", session.startedAt, "'$text'")
            }
            if (session != null) {
                session.lastPartialAt = JasperTiming.now()
                session.lastPartial = text
            }
            updateState {
                copy(
                    partialText = text,
                    recognizedAlternatives = all,
                    isSpeaking = true,
                )
            }
        }
    }

    override fun onResults(results: Bundle?) {
        if (utteranceConsumed || listenSession?.consumed == true) {
            utteranceConsumed = false
            listenSession = null
            val skip = suppressErrorRestart
            suppressErrorRestart = false
            if (!skip) scheduleRestart(60L)
            return
        }
        val all = results
            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            .orEmpty()

        logSessionEnd(ok = all.isNotEmpty(), phrase = all.firstOrNull().orEmpty(), alts = all)

        if (all.isNotEmpty()) {
            updateState {
                copy(
                    recognizedText = all.first(),
                    recognizedAlternatives = all,
                    partialText = "",
                    history = (history + all.first()).takeLast(5),
                    isSpeaking = false,
                    mouthOpen = 0f,
                )
            }
        } else {
            updateState { copy(partialText = "", recognizedAlternatives = emptyList(), isSpeaking = false, mouthOpen = 0f) }
        }
        listenSession = null
        scheduleRestart()
    }

    override fun onEndOfSpeech() {
        val session = listenSession
        if (session != null) {
            session.endOfSpeechAt = JasperTiming.now()
            val speechMs = if (session.speechBeginAt > 0L) {
                session.endOfSpeechAt - session.speechBeginAt
            } else {
                -1L
            }
            JasperTiming.elapsed(
                "STT конец речи",
                session.startedAt,
                "говорил=${speechMs}мс partial='${session.lastPartial}'",
            )
        }
        updateState { copy(isSpeaking = partialText.isNotEmpty()) }
    }

    override fun onError(error: Int) {
        val skipRestart = suppressErrorRestart
        suppressErrorRestart = false
        val delay = when (error) {
            SpeechRecognizer.ERROR_NO_MATCH,
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
            -> 80L
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> 400L
            else -> 300L
        }
        logSessionEnd(ok = false, phrase = "", alts = emptyList(), error = errorName(error), restartMs = delay)
        listenSession = null
        updateState {
            copy(
                partialText = if (error == SpeechRecognizer.ERROR_NO_MATCH) partialText else "",
                isSpeaking = false,
                mouthOpen = 0f,
            )
        }
        if (!skipRestart) scheduleRestart(delay)
    }

    override fun onBufferReceived(buffer: ByteArray?) = Unit
    override fun onEvent(eventType: Int, params: Bundle?) = Unit

    private fun logSessionEnd(
        ok: Boolean,
        phrase: String,
        alts: List<String>,
        error: String? = null,
        restartMs: Long = 150L,
    ) {
        val session = listenSession ?: return
        val now = JasperTiming.now()
        val readyMs = delta(session.readyAt, session.startedAt)
        val speechMs = if (session.speechBeginAt > 0L && session.endOfSpeechAt > 0L) {
            session.endOfSpeechAt - session.speechBeginAt
        } else if (session.speechBeginAt > 0L) {
            now - session.speechBeginAt
        } else {
            -1L
        }
        val afterEndMs = if (session.endOfSpeechAt > 0L) now - session.endOfSpeechAt else -1L
        val firstPartialMs = delta(session.firstPartialAt, session.startedAt)
        val peak = if (session.peakRms == Float.NEGATIVE_INFINITY) "n/a" else "%.1f".format(session.peakRms)
        val detail = buildString {
            if (ok) {
                append("фраза='$phrase' alts=$alts")
            } else {
                append("ошибка=${error ?: "пусто"} restart=${restartMs}мс")
                if (session.lastPartial.isNotEmpty()) append(" partial='${session.lastPartial}'")
            }
            append(" | ждал_ready=${readyMs}мс")
            append(" первый_partial=${firstPartialMs}мс")
            append(" речь=${speechMs}мс")
            append(" пауза_после_конца=${afterEndMs}мс")
            append(" peakRms=$peak")
        }
        JasperTiming.elapsed(if (ok) "STT финал" else "STT ошибка", session.startedAt, detail)
    }

    private fun delta(at: Long, from: Long): Long = if (at > 0L) at - from else -1L

    /**
     * Xiaomi/MIUI отдаёт свой распознаватель по умолчанию — часто NO_MATCH без partials.
     * Берём Google Speech Services, если пакет есть.
     */
    private fun createPreferredRecognizer(): SpeechRecognizer {
        @Suppress("DEPRECATION")
        val services = context.packageManager.queryIntentServices(
            Intent(RecognitionService.SERVICE_INTERFACE),
            0,
        )
        val names = services.mapNotNull { resolve ->
            val info = resolve.serviceInfo ?: return@mapNotNull null
            ComponentName(info.packageName, info.name)
        }
        JasperTiming.event(
            "STT сервисы",
            names.joinToString { it.flattenToShortString() }.ifEmpty { "пусто" },
        )

        val google = ComponentName(GOOGLE_APP_PACKAGE, GOOGLE_RECOGNITION_SERVICE)
        if (isServiceVisible(google)) {
            tryCreate(google)?.let { return it }
        }

        names.firstOrNull { it.packageName.startsWith("com.google.android.") && it != google }
            ?.let { tryCreate(it)?.let { recognizer -> return recognizer } }

        names.firstOrNull { component ->
            val p = component.packageName.lowercase()
            "xiaomi" !in p && "miui" !in p && "mibrain" !in p
        }?.let { tryCreate(it)?.let { recognizer -> return recognizer } }

        JasperTiming.event("STT выбран сервис", "системный default")
        return SpeechRecognizer.createSpeechRecognizer(context)
    }

    private fun isServiceVisible(component: ComponentName): Boolean {
        return try {
            @Suppress("DEPRECATION")
            context.packageManager.getServiceInfo(component, 0)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun tryCreate(component: ComponentName): SpeechRecognizer? {
        return try {
            SpeechRecognizer.createSpeechRecognizer(context, component).also {
                JasperTiming.event("STT выбран сервис", component.flattenToString())
            }
        } catch (e: Exception) {
            JasperTiming.event(
                "STT сервис не взялся",
                "${component.flattenToShortString()}: ${e.message}",
            )
            null
        }
    }

    private class ListenSession {
        val startedAt: Long = JasperTiming.now()
        var readyAt: Long = 0L
        var speechBeginAt: Long = 0L
        var firstPartialAt: Long = 0L
        var lastPartialAt: Long = 0L
        var lastPartial: String = ""
        var endOfSpeechAt: Long = 0L
        var peakRms: Float = Float.NEGATIVE_INFINITY
        var consumed: Boolean = false
    }

    companion object {
        private const val GOOGLE_APP_PACKAGE = "com.google.android.googlequicksearchbox"
        private const val GOOGLE_RECOGNITION_SERVICE =
            "com.google.android.voicesearch.serviceapi.GoogleRecognitionService"

        private fun errorName(error: Int): String = when (error) {
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "NETWORK_TIMEOUT"
            SpeechRecognizer.ERROR_NETWORK -> "NETWORK"
            SpeechRecognizer.ERROR_AUDIO -> "AUDIO"
            SpeechRecognizer.ERROR_SERVER -> "SERVER"
            SpeechRecognizer.ERROR_CLIENT -> "CLIENT"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "SPEECH_TIMEOUT"
            SpeechRecognizer.ERROR_NO_MATCH -> "NO_MATCH"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "RECOGNIZER_BUSY"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "PERMISSIONS"
            SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> "TOO_MANY_REQUESTS"
            SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> "SERVER_DISCONNECTED"
            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> "LANGUAGE_NOT_SUPPORTED"
            SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "LANGUAGE_UNAVAILABLE"
            else -> "UNKNOWN($error)"
        }
    }
}
