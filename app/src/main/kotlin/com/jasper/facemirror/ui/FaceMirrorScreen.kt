package com.jasper.facemirror.ui

import android.Manifest
import android.content.pm.PackageManager
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
import com.jasper.facemirror.model.DialogPhase
import com.jasper.facemirror.model.FaceExpression
import com.jasper.facemirror.model.FaceGestureReactions
import com.jasper.facemirror.model.FaceState
import com.jasper.facemirror.model.GreetingReply
import com.jasper.facemirror.model.SpeechState
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

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        hasCameraPermission = permissions[Manifest.permission.CAMERA] == true
        hasMicPermission = permissions[Manifest.permission.RECORD_AUDIO] == true
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
            return
        }
        lastReplyMs = now

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

    fun handleUserPhrase(phrase: String, history: List<String>) {
        if (phrase.isBlank()) return

        // Пока Jasper говорит — микрофон выключен; отбрасываем эхо, если оно просочилось
        if (isJasperSpeaking || dialogPhase == DialogPhase.SPEAKING) {
            speechEngine?.acknowledgePhrase()
            return
        }

        speechEngine?.acknowledgePhrase()

        if (InterruptCommands.isStopCommand(phrase)) {
            interruptJasper()
            faceExpression = FaceExpression.NEUTRAL
            return
        }

        if (dialogPhase == DialogPhase.THINKING) {
            conversationBrain.cancelPending()
            lastReplyMs = 0L
        }

        dialogPhase = DialogPhase.THINKING
        conversationBrain.respondToPhrase(
            phrase = phrase,
            history = history,
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

    Box(modifier = Modifier.fillMaxSize()) {
        NeonFace(
            faceState = faceState,
            expression = faceExpression,
            dialogPhase = dialogPhase,
            isSpeaking = isJasperSpeaking || dialogPhase == DialogPhase.SPEAKING,
            lipPulse = lipPulse,
        )

        if (allPermissionsGranted) {
            CameraAnalyzer(onFaceState = { state ->
                faceState = state
                maybeReactToSmile(state)
            })
            SpeechListener(
                onEngineReady = { engine ->
                    speechEngine = engine
                    if (dialogPhase == DialogPhase.IDLE) {
                        dialogPhase = DialogPhase.LISTENING
                    }
                },
                onSpeechState = { state ->
                    speechState = state

                    val phrase = state.recognizedText
                    if (phrase.isNotBlank()) {
                        handleUserPhrase(phrase, state.history)
                    }
                },
            )
        } else {
            dialogPhase = DialogPhase.IDLE
            PermissionPrompt(
                onRequestPermission = {
                    permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.CAMERA,
                            Manifest.permission.RECORD_AUDIO,
                        ),
                    )
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
private fun CameraAnalyzer(onFaceState: (FaceState) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val onFaceStateRef = rememberUpdatedState(onFaceState)
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
                        FaceAnalyzer { state ->
                            mainExecutor.execute { onFaceStateRef.value(state) }
                        },
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
