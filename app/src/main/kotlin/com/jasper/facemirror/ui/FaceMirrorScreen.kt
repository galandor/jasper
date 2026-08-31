package com.jasper.facemirror.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.util.Size as AndroidSize
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.jasper.facemirror.R
import com.jasper.facemirror.audio.JasperVoiceSpeaker
import com.jasper.facemirror.camera.FaceAnalyzer
import com.jasper.facemirror.chassis.ChassisDriver
import com.jasper.facemirror.chassis.DriveAction
import com.jasper.facemirror.chassis.DriveCommands
import com.jasper.facemirror.debug.JasperTiming
import com.jasper.facemirror.openbot.DriveSession
import com.jasper.facemirror.openbot.GamepadButton
import com.jasper.facemirror.openbot.GamepadHub
import com.jasper.facemirror.openbot.LearnCommands
import com.jasper.facemirror.openbot.LearnIntent
import com.jasper.facemirror.model.DialogPhase
import com.jasper.facemirror.model.FaceExpression
import com.jasper.facemirror.model.FaceGestureReactions
import com.jasper.facemirror.model.FaceState
import com.jasper.facemirror.model.GreetingReply
import com.jasper.facemirror.model.SpeechState
import com.jasper.facemirror.model.VoiceEmotion
import com.jasper.facemirror.speech.ConversationBrain
import com.jasper.facemirror.speech.InterruptCommands
import com.jasper.facemirror.speech.SpeechRecognizerEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

private const val REPLY_COOLDOWN_MS = 2500L
private const val EXPRESSION_HOLD_MS = 5000L
private const val SMILE_THRESHOLD = 0.52f
private const val SMILE_HOLD_FRAMES = 6
private const val SMILE_REACTION_COOLDOWN_MS = 10000L

@Composable
fun FaceMirrorScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var hasBluetoothPermission by remember {
        mutableStateOf(hasBluetoothConnectPermission(context))
    }

    var faceState by remember { mutableStateOf(FaceState.Idle) }
    var speechState by remember { mutableStateOf(SpeechState.Idle) }
    var faceExpression by remember { mutableStateOf(FaceExpression.NEUTRAL) }
    var dialogPhase by remember { mutableStateOf(DialogPhase.IDLE) }
    var isJasperSpeaking by remember { mutableStateOf(false) }
    var lipPulse by remember { mutableFloatStateOf(0f) }
    var lastReplyMs by remember { mutableLongStateOf(0L) }
    var lastSmileReplyMs by remember { mutableLongStateOf(0L) }
    var smileHoldFrames by remember { mutableIntStateOf(0) }
    var expressionResetJob by remember { mutableStateOf<Job?>(null) }

    val voiceSpeaker = remember { JasperVoiceSpeaker(context) }
    val conversationBrain = remember(scope) { ConversationBrain(scope) }
    val chassisDriver = remember { ChassisDriver(context, scope) }
    val driveSession = remember { DriveSession(context, chassisDriver) }
    val driveHud by driveSession.hud.collectAsState()
    val padControl by GamepadHub.control.collectAsState()
    val padConnected by GamepadHub.connected.collectAsState()
    var speechEngine by remember { mutableStateOf<SpeechRecognizerEngine?>(null) }

    DisposableEffect(voiceSpeaker) {
        voiceSpeaker.setOnSpeakingChanged { speaking ->
            isJasperSpeaking = speaking
            if (!speaking) lipPulse = 0f
        }
        voiceSpeaker.setOnLipPulse { pulse -> lipPulse = pulse }
        onDispose { }
    }

    DisposableEffect(Unit) {
        onDispose { voiceSpeaker.release() }
    }

    DisposableEffect(hasBluetoothPermission) {
        if (hasBluetoothPermission) {
            chassisDriver.start()
        }
        onDispose { chassisDriver.release() }
    }

    DisposableEffect(driveSession) {
        onDispose { driveSession.release() }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        hasCameraPermission = permissions[Manifest.permission.CAMERA] == true
        hasMicPermission = permissions[Manifest.permission.RECORD_AUDIO] == true
        hasBluetoothPermission = hasBluetoothConnectPermission(context)
    }

    val allPermissionsGranted = hasCameraPermission && hasMicPermission

    fun resetExpressionLater() {
        expressionResetJob?.cancel()
        expressionResetJob = scope.launch {
            delay(EXPRESSION_HOLD_MS)
            if (dialogPhase != DialogPhase.SPEAKING && dialogPhase != DialogPhase.THINKING) {
                faceExpression = FaceExpression.NEUTRAL
            }
        }
    }

    fun interruptJasper() {
        val wasActive = isJasperSpeaking ||
            dialogPhase == DialogPhase.THINKING ||
            dialogPhase == DialogPhase.SPEAKING
        if (!wasActive) return

        voiceSpeaker.stop()
        conversationBrain.cancelPending()
        isJasperSpeaking = false
        dialogPhase = DialogPhase.INTERRUPTED
        lastReplyMs = 0L
        speechEngine?.resumeListening()

        scope.launch {
            delay(400)
            if (dialogPhase == DialogPhase.INTERRUPTED) {
                dialogPhase = DialogPhase.LISTENING
            }
        }
    }

    fun respondWithVoice(reply: GreetingReply, ignoreCooldown: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!ignoreCooldown &&
            now - lastReplyMs < REPLY_COOLDOWN_MS &&
            dialogPhase != DialogPhase.INTERRUPTED
        ) {
            JasperTiming.event(
                "ответ пропущен",
                "cooldown ${now - lastReplyMs}мс < ${REPLY_COOLDOWN_MS}мс текст='${reply.text}'",
            )
            return
        }
        lastReplyMs = now
        JasperTiming.event("TTS", "говорим '${reply.text}' voice=${reply.voice}")

        speechEngine?.pauseListening()

        faceExpression = reply.expression
        dialogPhase = DialogPhase.SPEAKING
        expressionResetJob?.cancel()

        voiceSpeaker.speakGreeting(reply) {
            isJasperSpeaking = false
            dialogPhase = DialogPhase.LISTENING
            speechEngine?.resumeListening()
            resetExpressionLater()
        }
    }

    fun canReactToFaceGesture(): Boolean {
        // Не смотрим speechState.isSpeaking — RMS микрофона даёт ложные срабатывания
        if (isJasperSpeaking) return false
        if (dialogPhase == DialogPhase.THINKING || dialogPhase == DialogPhase.SPEAKING) return false
        return true
    }

    fun maybeReactToSmile(state: FaceState) {
        if (!state.isDetected) {
            smileHoldFrames = 0
            return
        }
        if (!canReactToFaceGesture()) {
            smileHoldFrames = 0
            return
        }

        if (state.smile >= SMILE_THRESHOLD) {
            smileHoldFrames++
        } else {
            smileHoldFrames = 0
            return
        }

        val now = System.currentTimeMillis()
        if (smileHoldFrames < SMILE_HOLD_FRAMES) return
        if (now - lastSmileReplyMs < SMILE_REACTION_COOLDOWN_MS) return

        lastSmileReplyMs = now
        smileHoldFrames = 0
        respondWithVoice(FaceGestureReactions.smileReply(), ignoreCooldown = true)
    }

    fun applyDrive(drive: DriveAction) {
        JasperTiming.event("машинка", "execute $drive connected=${chassisDriver.isConnected}")
        conversationBrain.cancelPending()
        if (isJasperSpeaking || dialogPhase == DialogPhase.SPEAKING || dialogPhase == DialogPhase.THINKING) {
            voiceSpeaker.stop()
            isJasperSpeaking = false
        }
        if (drive == DriveAction.CONNECT) {
            dialogPhase = DialogPhase.THINKING
            faceExpression = FaceExpression.SURPRISED
            scope.launch {
                val ok = chassisDriver.reconnect()
                if (dialogPhase == DialogPhase.INTERRUPTED) return@launch
                if (ok) {
                    respondWithVoice(
                        GreetingReply("Подключился!", VoiceEmotion.HAPPY, FaceExpression.HAPPY),
                        ignoreCooldown = true,
                    )
                } else {
                    respondWithVoice(
                        GreetingReply("Машинка не слышит!", VoiceEmotion.SAD, FaceExpression.SAD),
                        ignoreCooldown = true,
                    )
                }
            }
            return
        }
        chassisDriver.execute(drive)
        if (drive == DriveAction.STOP) {
            interruptJasper()
            faceExpression = FaceExpression.NEUTRAL
            dialogPhase = DialogPhase.LISTENING
            return
        }
        if (chassisDriver.connectFinished && !chassisDriver.isConnected) {
            respondWithVoice(
                GreetingReply("Машинка не слышит!", VoiceEmotion.SAD, FaceExpression.SAD),
                ignoreCooldown = true,
            )
            return
        }
        faceExpression = drive.ack?.expression ?: FaceExpression.PLAYFUL
        dialogPhase = DialogPhase.LISTENING
        resetExpressionLater()
    }

    fun applyDriveSequence(actions: List<DriveAction>) {
        if (actions.size <= 1 || actions.contains(DriveAction.CONNECT)) {
            actions.firstOrNull()?.let { applyDrive(it) }
            return
        }
        JasperTiming.event(
            "машинка",
            "очередь ${actions.joinToString(" → ")} connected=${chassisDriver.isConnected}",
        )
        conversationBrain.cancelPending()
        if (isJasperSpeaking || dialogPhase == DialogPhase.SPEAKING || dialogPhase == DialogPhase.THINKING) {
            voiceSpeaker.stop()
            isJasperSpeaking = false
        }
        chassisDriver.executeSequence(actions)
        if (chassisDriver.connectFinished && !chassisDriver.isConnected) {
            respondWithVoice(
                GreetingReply("Машинка не слышит!", VoiceEmotion.SAD, FaceExpression.SAD),
                ignoreCooldown = true,
            )
            return
        }
        faceExpression = actions.first().ack?.expression ?: FaceExpression.PLAYFUL
        dialogPhase = DialogPhase.LISTENING
        resetExpressionLater()
    }

    fun resolveDrive(candidates: List<String>): DriveAction? {
        var drive = DriveCommands.parseAny(candidates)
        if (drive == null && chassisDriver.isDriving) {
            drive = DriveCommands.parseMotionAny(candidates)
        }
        return drive
    }

    /** [gate] уже подтвердил, что фразу можно исполнять; порядок берём из разбора всей реплики. */
    fun resolveDriveSequence(candidates: List<String>, gate: DriveAction): List<DriveAction> {
        val sequence = DriveCommands.parseSequenceAny(candidates)
        return if (sequence.size > 1) sequence else listOf(gate)
    }

    fun handleUserPhrase(phrase: String, history: List<String>, alternatives: List<String> = emptyList()) {
        if (phrase.isBlank()) return

        val candidates = (listOf(phrase) + alternatives).distinct()
        val learn = LearnCommands.parseAny(candidates)
        if (learn != null) {
            JasperTiming.event("путь", "openbot $learn")
            speechEngine?.acknowledgePhrase()
            val line = when (learn) {
                LearnIntent.START_RECORD -> if (driveHud.recording) "Уже пишу!" else driveSession.toggleRecord()
                LearnIntent.STOP_RECORD -> if (driveHud.recording) driveSession.toggleRecord() else "Я и так не пишу."
                LearnIntent.START_AUTOPILOT -> if (driveHud.autopilot) "Уже сам еду!" else driveSession.toggleAutopilot()
                LearnIntent.STOP_AUTOPILOT -> if (driveHud.autopilot) driveSession.toggleAutopilot() else "Я и так не сам."
            } ?: return
            respondWithVoice(
                GreetingReply(line, VoiceEmotion.PLAYFUL, FaceExpression.PLAYFUL),
                ignoreCooldown = true,
            )
            return
        }

        val drive = resolveDrive(candidates)
        val chassisTalk = drive != null || candidates.any { DriveCommands.isChassisTalk(it) }
        Log.i("JasperChassis", "STT='$phrase' alts=$alternatives drive=$drive")
        JasperTiming.event(
            "фраза",
            "STT='$phrase' alts=$alternatives regex=$drive chassisTalk=$chassisTalk " +
                "phase=$dialogPhase speaking=$isJasperSpeaking driving=${chassisDriver.isDriving}",
        )

        if (drive != null) {
            val sequence = resolveDriveSequence(candidates, drive)
            JasperTiming.event("путь", "regex → ${sequence.joinToString(" → ")} (без сети)")
            speechEngine?.acknowledgePhrase()
            applyDriveSequence(sequence)
            return
        }

        if (chassisDriver.isDriving && candidates.any { DriveCommands.containsStopWord(it) }) {
            JasperTiming.event("путь", "стоп по слову во время езды")
            speechEngine?.acknowledgePhrase()
            applyDrive(DriveAction.STOP)
            return
        }

        // Пока Jasper говорит — микрофон выключен; отбрасываем эхо, если оно просочилось
        if (isJasperSpeaking || dialogPhase == DialogPhase.SPEAKING) {
            JasperTiming.event("путь", "отброшено как эхо TTS")
            speechEngine?.acknowledgePhrase()
            return
        }

        speechEngine?.acknowledgePhrase()

        if (InterruptCommands.isStopCommand(phrase)) {
            JasperTiming.event("путь", "interrupt без LLM")
            interruptJasper()
            driveSession.onButton(GamepadButton.EMERGENCY_STOP)
            chassisDriver.stop()
            faceExpression = FaceExpression.NEUTRAL
            return
        }

        if (candidates.all { DriveCommands.isNameOnly(it) }) {
            JasperTiming.event("путь", "только имя — ждём команду, без Gemini")
            return
        }

        if (dialogPhase == DialogPhase.THINKING) {
            conversationBrain.cancelPending()
            lastReplyMs = 0L
        }

        val llmPath = if (chassisTalk) {
            "gemini_classify (regex не взял команду)"
        } else {
            "gemini_chat без классификатора"
        }
        JasperTiming.event("путь", "$llmPath — сеть, может занять секунды")
        dialogPhase = DialogPhase.THINKING
        conversationBrain.respondToPhrase(
            phrase = phrase,
            history = history,
            alternatives = alternatives,
            classifyDrive = chassisTalk,
            onDrive = { action ->
                if (dialogPhase == DialogPhase.INTERRUPTED) return@respondToPhrase
                applyDrive(action)
            },
            onReply = { reply ->
                if (dialogPhase == DialogPhase.INTERRUPTED) return@respondToPhrase
                respondWithVoice(reply)
            },
            onNoReply = {
                if (dialogPhase != DialogPhase.INTERRUPTED) {
                    dialogPhase = DialogPhase.LISTENING
                }
            },
        )
    }

    LaunchedEffect(padConnected) {
        driveSession.onGamepadConnected(padConnected)
    }

    LaunchedEffect(padControl) {
        driveSession.onGamepadControl(padControl)
    }

    LaunchedEffect(Unit) {
        GamepadHub.buttons.collect { button ->
            val line = driveSession.onButton(button) ?: return@collect
            respondWithVoice(
                GreetingReply(line, VoiceEmotion.PLAYFUL, FaceExpression.PLAYFUL),
                ignoreCooldown = true,
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        NeonFace(
            faceState = faceState,
            expression = faceExpression,
            dialogPhase = dialogPhase,
            isSpeaking = isJasperSpeaking || dialogPhase == DialogPhase.SPEAKING,
            lipPulse = lipPulse,
        )
        DriveHud(state = driveHud)

        if (allPermissionsGranted) {
            CameraAnalyzer(
                onFaceState = { state ->
                    faceState = state
                    maybeReactToSmile(state)
                },
                shouldCapture = { driveSession.wantsFrames },
                onFrame = { bitmap -> driveSession.onFrame(bitmap) },
            )
            SpeechListener(
                onEngineReady = { engine ->
                    speechEngine = engine
                    if (dialogPhase == DialogPhase.IDLE) {
                        dialogPhase = DialogPhase.LISTENING
                    }
                },
                onSpeechState = { state ->
                    if (state.recognizedText != speechState.recognizedText ||
                        state.partialText != speechState.partialText
                    ) {
                        speechState = state
                    }

                    if (chassisDriver.isDriving && DriveCommands.containsStopWord(state.partialText)) {
                        driveSession.onButton(GamepadButton.EMERGENCY_STOP)
                        chassisDriver.stop()
                        interruptJasper()
                        faceExpression = FaceExpression.NEUTRAL
                        dialogPhase = DialogPhase.LISTENING
                        speechEngine?.consumeUtteranceAndRestart()
                    } else if (state.recognizedText.isBlank() && state.partialText.isNotBlank()) {
                        val candidates = listOf(state.partialText)
                        val drive = resolveDrive(candidates)
                        if (drive != null) {
                            val sequence = resolveDriveSequence(candidates, drive)
                            JasperTiming.event(
                                "путь",
                                "partial → ${sequence.joinToString(" → ")} '${state.partialText}'",
                            )
                            speechEngine?.consumeUtteranceAndRestart()
                            applyDriveSequence(sequence)
                        }
                    } else if (state.recognizedText.isNotBlank()) {
                        handleUserPhrase(state.recognizedText, state.history, state.recognizedAlternatives)
                    }
                },
            )
        } else {
            dialogPhase = DialogPhase.IDLE
            PermissionPrompt(
                onRequestPermission = {
                    permissionLauncher.launch(requiredAppPermissions())
                },
            )
        }
    }
}

@Composable
private fun PermissionPrompt(onRequestPermission: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Button(
            onClick = onRequestPermission,
            modifier = Modifier.padding(bottom = 48.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = androidx.compose.ui.graphics.Color(0xFF00F0FF),
                contentColor = androidx.compose.ui.graphics.Color.Black,
            ),
        ) {
            Text(stringResource(R.string.grant_permission))
        }
    }
}

@Composable
private fun CameraAnalyzer(
    onFaceState: (FaceState) -> Unit,
    shouldCapture: () -> Boolean = { false },
    onFrame: (android.graphics.Bitmap) -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val onFaceStateRef = rememberUpdatedState(onFaceState)
    val shouldCaptureRef = rememberUpdatedState(shouldCapture)
    val onFrameRef = rememberUpdatedState(onFrame)
    val mainExecutor = remember { ContextCompat.getMainExecutor(context) }
    val analyzerExecutor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(lifecycleOwner) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        var cameraProvider: ProcessCameraProvider? = null

        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()

            val imageAnalysis = ImageAnalysis.Builder()
                .setTargetResolution(AndroidSize(640, 480))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { analysis ->
                    analysis.setAnalyzer(
                        analyzerExecutor,
                        FaceAnalyzer(
                            onFaceState = { state ->
                                mainExecutor.execute { onFaceStateRef.value(state) }
                            },
                            shouldCapture = { shouldCaptureRef.value() },
                            onFrame = { bitmap -> onFrameRef.value(bitmap) },
                        ),
                    )
                }

            try {
                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    imageAnalysis,
                )
            } catch (_: Exception) {
                // Камера недоступна
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            cameraProvider?.unbindAll()
            analyzerExecutor.shutdown()
        }
    }
}

@Composable
private fun SpeechListener(
    onEngineReady: (SpeechRecognizerEngine) -> Unit,
    onSpeechState: (SpeechState) -> Unit,
) {
    val context = LocalContext.current
    val onSpeechStateRef = rememberUpdatedState(onSpeechState)
    val onEngineReadyRef = rememberUpdatedState(onEngineReady)
    val mainExecutor = remember { ContextCompat.getMainExecutor(context) }
    val engine = remember {
        SpeechRecognizerEngine(context) { state ->
            mainExecutor.execute { onSpeechStateRef.value(state) }
        }
    }

    DisposableEffect(Unit) {
        onEngineReadyRef.value(engine)
        engine.start()
        onDispose { engine.stop() }
    }
}

private fun hasBluetoothConnectPermission(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.BLUETOOTH_CONNECT,
    ) == PackageManager.PERMISSION_GRANTED
}

private fun requiredAppPermissions(): Array<String> = buildList {
    add(Manifest.permission.CAMERA)
    add(Manifest.permission.RECORD_AUDIO)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        add(Manifest.permission.BLUETOOTH_CONNECT)
    }
}.toTypedArray()

