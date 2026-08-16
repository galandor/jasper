package com.jasper.facemirror.camera

import android.annotation.SuppressLint
import android.os.SystemClock
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.jasper.facemirror.model.FaceState
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

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
            .build(),
    )
    private val busy = AtomicBoolean(false)
    private var lastPosted: FaceState? = null
    private var lastPostedAt = 0L
    private var lastAnalyzeAt = 0L

    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastAnalyzeAt < MIN_ANALYZE_INTERVAL_MS) {
            imageProxy.close()
            return
        }
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }
        if (!busy.compareAndSet(false, true)) {
            imageProxy.close()
            return
        }
        lastAnalyzeAt = now

        val inputImage = InputImage.fromMediaImage(
            mediaImage,
            imageProxy.imageInfo.rotationDegrees,
        )

        detector.process(inputImage)
            .addOnSuccessListener(DirectExecutor) { faces ->
                val state = if (faces.isEmpty()) {
                    FaceState.Idle
                } else {
                    val face = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
                        ?: return@addOnSuccessListener

                    val leftOpen = face.leftEyeOpenProbability?.let { eyeOpenAmount(it) } ?: 1f
                    val rightOpen = face.rightEyeOpenProbability?.let { eyeOpenAmount(it) } ?: 1f
                    val smile = face.smilingProbability?.coerceIn(0f, 1f) ?: 0f

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
                if (shouldPost(state)) {
                    lastPosted = state
                    lastPostedAt = SystemClock.elapsedRealtime()
                    onFaceState(state)
                }
            }
            .addOnCompleteListener(DirectExecutor) {
                busy.set(false)
                imageProxy.close()
            }
    }

    private fun eyeOpenAmount(probability: Float): Float {
        return when {
            probability < BLINK_THRESHOLD -> 0f
            probability < BLINK_SOFT_ZONE -> {
                (probability - BLINK_THRESHOLD) / (BLINK_SOFT_ZONE - BLINK_THRESHOLD)
            }
            else -> 1f
        }
    }

    private fun shouldPost(state: FaceState): Boolean {
        val prev = lastPosted
        if (prev != null && prev.isDetected != state.isDetected) return true
        val elapsed = SystemClock.elapsedRealtime() - lastPostedAt
        if (elapsed < MIN_POST_INTERVAL_MS) return false
        if (prev == null) return true
        if (state.smile >= SMILE_TRACK_THRESHOLD || prev.smile >= SMILE_TRACK_THRESHOLD) return true
        return !approxEquals(prev, state)
    }

    private fun approxEquals(a: FaceState, b: FaceState): Boolean {
        return abs(a.leftEyeOpen - b.leftEyeOpen) < 0.08f &&
            abs(a.rightEyeOpen - b.rightEyeOpen) < 0.08f &&
            abs(a.smile - b.smile) < 0.05f &&
            abs(a.yaw - b.yaw) < 2f &&
            abs(a.pitch - b.pitch) < 2f &&
            abs(a.roll - b.roll) < 2f
    }

    companion object {
        private const val BLINK_THRESHOLD = 0.25f
        private const val BLINK_SOFT_ZONE = 0.45f
        private const val MIN_ANALYZE_INTERVAL_MS = 100L
        private const val MIN_POST_INTERVAL_MS = 100L
        private const val SMILE_TRACK_THRESHOLD = 0.45f
        private val DirectExecutor = Executor { it.run() }
    }
}
