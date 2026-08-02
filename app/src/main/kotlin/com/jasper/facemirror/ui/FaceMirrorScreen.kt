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
private const val BARGE_IN_MIN_AMPLITUDE = 0.18f

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
    var lastReplyMs by remember { mutableLongStateOf(0L) }
    var lastProcessedPhrase by remember { mutableStateOf("") }
    var expressionResetJob by remember { mutableStateOf<Job?>(null) }

    val voiceSpeaker = remember { JasperVoiceSpeaker(context) }
    val conversationBrain = remember(scope) { ConversationBrain(scope) }

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

        scope.launch {
            delay(400)
            if (dialogPhase == DialogPhase.INTERRUPTED) {
                dialogPhase = DialogPhase.LISTENING
            }
        }
    }

    fun respondWithVoice(reply: GreetingReply) {
        val now = System.currentTimeMillis()
        if (now - lastReplyMs < REPLY_COOLDOWN_MS && dialogPhase != DialogPhase.INTERRUPTED) return
        lastReplyMs = now

        faceExpression = reply.expression
        dialogPhase = DialogPhase.SPEAKING
        isJasperSpeaking = true
        expressionResetJob?.cancel()

        voiceSpeaker.speakGreeting(reply) {
            isJasperSpeaking = false
            dialogPhase = DialogPhase.LISTENING
            resetExpressionLater()
        }
    }

    fun handleUserPhrase(phrase: String, history: List<String>) {
        if (phrase.isBlank() || phrase == lastProcessedPhrase) return
        lastProcessedPhrase = phrase

        if (InterruptCommands.isStopCommand(phrase)) {
            interruptJasper()
            faceExpression = FaceExpression.NEUTRAL
            return
        }

        if (isJasperSpeaking) {
            interruptJasper()
        } else if (dialogPhase == DialogPhase.THINKING) {
            conversationBrain.cancelPending()
            lastReplyMs = 0L
        }

        dialogPhase = DialogPhase.THINKING
        conversationBrain.respondToPhrase(
            phrase = phrase,
            history = history,
        ) { reply ->
            if (dialogPhase == DialogPhase.INTERRUPTED) return@respondToPhrase
            respondWithVoice(reply)
        }
    }

    fun maybeBargeIn(state: SpeechState) {
        if (!isJasperSpeaking && dialogPhase != DialogPhase.SPEAKING) return

        val userTalking = state.isSpeaking &&
            state.amplitude >= BARGE_IN_MIN_AMPLITUDE
        val partialFromUser = state.partialText.length >= 2

        if (userTalking || partialFromUser) {
            interruptJasper()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        NeonFace(
            faceState = faceState,
            expression = faceExpression,
            dialogPhase = dialogPhase,
        )

        if (allPermissionsGranted) {
            CameraAnalyzer(onFaceState = { faceState = it })
            SpeechListener(
                onEngineReady = {
                    if (dialogPhase == DialogPhase.IDLE) {
                        dialogPhase = DialogPhase.LISTENING
                    }
                },
                onSpeechState = { state ->
                    speechState = state
                    maybeBargeIn(state)

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
