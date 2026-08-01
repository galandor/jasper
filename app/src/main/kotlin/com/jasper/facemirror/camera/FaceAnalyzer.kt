package com.jasper.facemirror.camera

import android.annotation.SuppressLint
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.jasper.facemirror.model.FaceState

class FaceAnalyzer(
    private val onFaceState: (FaceState) -> Unit,
) : ImageAnalysis.Analyzer {

    private val detector: FaceDetector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setMinFaceSize(0.15f)
            .enableTracking()
            .build()
    )

    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        val inputImage = InputImage.fromMediaImage(
            mediaImage,
            imageProxy.imageInfo.rotationDegrees
        )

        detector.process(inputImage)
            .addOnSuccessListener { faces ->
                val state = if (faces.isEmpty()) {
                    FaceState.Idle
                } else {
                    val face = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
                        ?: return@addOnSuccessListener

                    val leftOpen = face.leftEyeOpenProbability?.let { eyeOpenAmount(it) } ?: 1f
                    val rightOpen = face.rightEyeOpenProbability?.let { eyeOpenAmount(it) } ?: 1f
                    val smile = face.smilingProbability?.coerceIn(0f, 1f) ?: 0f

                    // Инвертируем yaw — фронтальная камера работает как зеркало
                    FaceState(
                        leftEyeOpen = leftOpen,
                        rightEyeOpen = rightOpen,
                        smile = smile,
                        yaw = -face.headEulerAngleY,
                        pitch = -face.headEulerAngleX,
                        roll = face.headEulerAngleZ,
                        isDetected = true,
                    )
                }
                onFaceState(state)
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }

    /** ML Kit возвращает вероятность «открытости»; ниже порога — глаз закрыт. */
    private fun eyeOpenAmount(probability: Float): Float {
        return when {
            probability < BLINK_THRESHOLD -> 0f
            probability < BLINK_SOFT_ZONE -> {
                (probability - BLINK_THRESHOLD) / (BLINK_SOFT_ZONE - BLINK_THRESHOLD)
            }
            else -> 1f
        }
    }

    companion object {
        private const val BLINK_THRESHOLD = 0.25f
        private const val BLINK_SOFT_ZONE = 0.45f
    }
}
