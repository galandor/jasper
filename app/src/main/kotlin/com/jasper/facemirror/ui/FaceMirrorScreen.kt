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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.jasper.facemirror.audio.JasperSoundPlayer
import com.jasper.facemirror.audio.JasperVoiceSpeaker
import com.jasper.facemirror.camera.FaceAnalyzer
import com.jasper.facemirror.model.FaceState
import com.jasper.facemirror.model.GreetingReply
import com.jasper.facemirror.model.SpeechState
import com.jasper.facemirror.speech.GreetingDetector
import com.jasper.facemirror.speech.SpeechRecognizerEngine
import kotlinx.coroutines.delay
import java.util.concurrent.Executors

private const val HELLO_COOLDOWN_MS = 4000L

@Composable
fun FaceMirrorScreen() {
    val context = LocalContext.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    var faceState by remember { mutableStateOf(FaceState.Idle) }
    var speechState by remember { mutableStateOf(SpeechState.Idle) }
    var isGreeting by remember { mutableStateOf(false) }
    var isReplying by remember { mutableStateOf(false) }
    var wasFaceDetected by remember { mutableStateOf(false) }
    var lastHelloResponseMs by remember { mutableLongStateOf(0L) }
    var lastProcessedPhrase by remember { mutableStateOf("") }

    val soundPlayer = remember { JasperSoundPlayer() }
    val voiceSpeaker = remember { JasperVoiceSpeaker(context) }
    var speechEngine by remember { mutableStateOf<SpeechRecognizerEngine?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            soundPlayer.release()
            voiceSpeaker.release()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        hasCameraPermission = permissions[Manifest.permission.CAMERA] == true
        hasMicPermission = permissions[Manifest.permission.RECORD_AUDIO] == true
    }

    LaunchedEffect(faceState.isDetected) {
        if (faceState.isDetected && !wasFaceDetected) {
            isGreeting = true
            soundPlayer.playGreeting()
            wasFaceDetected = true
            delay(2500)
            isGreeting = false
        } else if (!faceState.isDetected) {
            wasFaceDetected = false
            isGreeting = false
        }
    }


    val allPermissionsGranted = hasCameraPermission && hasMicPermission

    fun respondToHello(reply: GreetingReply) {
        val now = System.currentTimeMillis()
        if (now - lastHelloResponseMs < HELLO_COOLDOWN_MS) return
        lastHelloResponseMs = now

        speechEngine?.pauseListening()
        isReplying = true
        isGreeting = true
        voiceSpeaker.speakGreeting(reply) {
            isReplying = false
            isGreeting = false
            speechEngine?.resumeListening()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        NeonFace(
            faceState = faceState,
            isGreeting = isGreeting,
        )

        RecognizedWordsOverlay(speechState = speechState)

        if (allPermissionsGranted) {
            CameraAnalyzer(onFaceState = { faceState = it })
            SpeechListener(
                onEngineReady = { speechEngine = it },
                onSpeechState = { state ->
                    speechState = state
                    val phrase = state.recognizedText
                    if (phrase.isNotBlank() && phrase != lastProcessedPhrase) {
                        lastProcessedPhrase = phrase
                        GreetingDetector.match(phrase)?.let { reply ->
                            respondToHello(reply)
                        }
                    }
                },
            )
        } else {
            PermissionPrompt(
                onRequestPermission = {
                    permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.CAMERA,
                            Manifest.permission.RECORD_AUDIO,
                        )
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
