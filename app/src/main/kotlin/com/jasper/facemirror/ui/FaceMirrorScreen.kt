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
import com.jasper.facemirror.camera.FaceAnalyzer
import com.jasper.facemirror.model.FaceState
import java.util.concurrent.Executors

@Composable
fun FaceMirrorScreen() {
    val context = LocalContext.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    var faceState by remember { mutableStateOf(FaceState.Idle) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasCameraPermission = granted
    }

    Box(modifier = Modifier.fillMaxSize()) {
        NeonFace(faceState = faceState)

        if (hasCameraPermission) {
            CameraAnalyzer(onFaceState = { faceState = it })
        } else {
            PermissionPrompt(
                onRequestPermission = {
                    permissionLauncher.launch(Manifest.permission.CAMERA)
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
                        FaceAnalyzer { state -> onFaceStateRef.value(state) },
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
                // Камера недоступна — лицо остаётся в idle-режиме
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            cameraProvider?.unbindAll()
            analyzerExecutor.shutdown()
        }
    }
}
