package com.jasper.facemirror.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import com.jasper.facemirror.model.FaceExpression
import com.jasper.facemirror.model.FaceState
import kotlin.math.cos
import kotlin.math.sin

private val BackgroundBlack = Color(0xFF000000)
private val NeonCyan = Color(0xFF00F0FF)
private val NeonPink = Color(0xFFFF2DAA)
private val NeonYellow = Color(0xFFFFEA00)
private val NeonPurple = Color(0xFFB388FF)

@Composable
fun NeonFace(
    faceState: FaceState,
    expression: FaceExpression = FaceExpression.NEUTRAL,
    modifier: Modifier = Modifier,
) {
    // Глаза всегда открыты — моргание с камеры отключено
    val eyeOpen = 1f

    val smile by animateFloatAsState(
        targetValue = expression.smileAmount,
        animationSpec = spring(stiffness = Spring.StiffnessLow, dampingRatio = 0.72f),
        label = "smile",
    )
    val yaw by animateFloatAsState(
        targetValue = faceState.yaw.coerceIn(-35f, 35f),
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = 0.75f),
        label = "yaw",
    )
    val pitch by animateFloatAsState(
        targetValue = faceState.pitch.coerceIn(-25f, 25f),
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = 0.75f),
        label = "pitch",
    )

    val expressionBounce = remember { Animatable(1f) }
    LaunchedEffect(expression) {
        if (expression != FaceExpression.NEUTRAL) {
            expressionBounce.snapTo(1f)
            expressionBounce.animateTo(
                1.06f,
                spring(stiffness = Spring.StiffnessMedium, dampingRatio = 0.5f),
            )
            expressionBounce.animateTo(
                1f,
                spring(stiffness = Spring.StiffnessLow, dampingRatio = 0.65f),
            )
        }
    }

    val glowSpeed = when (expression) {
        FaceExpression.HAPPY, FaceExpression.PLAYFUL -> 900
        FaceExpression.OFFENDED, FaceExpression.SAD -> 3200
        else -> 2200
    }

    val infiniteTransition = rememberInfiniteTransition(label = "neonPulse")
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(glowSpeed, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glowPulse",
    )

    val idlePhase = remember { Animatable(0f) }
    LaunchedEffect(faceState.isDetected) {
        if (!faceState.isDetected) {
            while (true) {
                idlePhase.animateTo(
                    targetValue = idlePhase.value + 1f,
                    animationSpec = tween(5000, easing = LinearEasing),
                )
            }
        }
    }

    val gazeX = if (faceState.isDetected) {
        (yaw / 35f) * 0.4f
    } else {
        sin(idlePhase.value * 2f * Math.PI.toFloat()) * 0.2f
    }
    val gazeY = if (faceState.isDetected) {
        (pitch / 25f) * 0.3f
    } else {
        cos(idlePhase.value * 2f * Math.PI.toFloat()) * 0.12f
    }

    val mouthColor = when (expression) {
        FaceExpression.HAPPY, FaceExpression.PLAYFUL -> NeonYellow
        FaceExpression.SAD, FaceExpression.OFFENDED -> NeonPurple
        FaceExpression.SURPRISED -> NeonCyan
        else -> NeonPink
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundBlack),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val scale = size.height * 0.0016f * expressionBounce.value
            val cx = size.width / 2f
            val cy = size.height * 0.44f

            withTransform({
                translate(cx, cy)
                scale(scale, scale, pivot = Offset.Zero)
            }) {
                val eyeSpacing = size.width / scale * 0.22f
                val eyeRadius = size.height / scale * 0.11f
                val mouthY = eyeRadius * 1.55f

                drawNeonEye(
                    center = Offset(-eyeSpacing / 2f, 0f),
                    radius = eyeRadius,
                    openAmount = eyeOpen,
                    gazeX = gazeX,
                    gazeY = gazeY,
                    headYaw = yaw,
                    color = NeonCyan,
                    glowIntensity = glowPulse,
                )
                drawNeonEye(
                    center = Offset(eyeSpacing / 2f, 0f),
                    radius = eyeRadius,
                    openAmount = eyeOpen,
                    gazeX = gazeX,
                    gazeY = gazeY,
                    headYaw = yaw,
                    color = NeonCyan,
                    glowIntensity = glowPulse,
                )

                drawNeonMouth(
                    smile = smile,
                    y = mouthY,
                    width = eyeSpacing * 0.85f,
                    color = mouthColor,
                    glowIntensity = glowPulse,
                    surprised = expression == FaceExpression.SURPRISED,
                )
            }
        }
    }
}

private fun DrawScope.drawNeonEye(
    center: Offset,
    radius: Float,
    openAmount: Float,
    gazeX: Float,
    gazeY: Float,
    headYaw: Float,
    color: Color,
    glowIntensity: Float,
) {
    val squish = openAmount.coerceIn(0.2f, 1f)
    val rx = radius
    val ry = radius * squish

    drawNeonEllipse(
        center = center,
        radiusX = rx,
        radiusY = ry,
        color = color,
        coreWidth = 4.5f,
        glowIntensity = glowIntensity,
    )

    val maxOffsetX = rx * 0.42f
    val maxOffsetY = ry * 0.42f
    val pupilCenter = Offset(
        center.x + gazeX * maxOffsetX + (headYaw / 35f) * maxOffsetX * 0.35f,
        center.y + gazeY * maxOffsetY,
    )
    val pupilRadius = radius * 0.18f * squish

    drawNeonFilledCircle(
        center = pupilCenter,
        radius = pupilRadius,
        color = color,
        glowIntensity = glowIntensity,
    )
}

private fun DrawScope.drawNeonMouth(
    smile: Float,
    y: Float,
    width: Float,
    color: Color,
    glowIntensity: Float,
    surprised: Boolean = false,
) {
    if (surprised) {
        val mouthW = width * 0.18f
        val mouthH = width * 0.14f
        drawNeonEllipse(
            center = Offset(0f, y + mouthH * 0.2f),
            radiusX = mouthW / 2f,
            radiusY = mouthH / 2f,
            color = color,
            coreWidth = 4f,
            glowIntensity = glowIntensity,
        )
        return
    }

    val absSmile = kotlin.math.abs(smile).coerceIn(0.05f, 1f)
    val mouthWidth = width * (0.5f + absSmile * 0.35f)
    val curve = 12f + absSmile * 50f
    val coreWidth = 4f + absSmile * 2.5f

    val path = Path().apply {
        moveTo(-mouthWidth / 2f, y)
        if (smile >= 0f) {
            quadraticTo(0f, y + curve, mouthWidth / 2f, y)
        } else {
            quadraticTo(0f, y - curve, mouthWidth / 2f, y)
        }
    }

    drawNeonPath(
        path = path,
        color = color,
        coreWidth = coreWidth,
        glowIntensity = glowIntensity,
    )
}

private fun DrawScope.drawNeonEllipse(
    center: Offset,
    radiusX: Float,
    radiusY: Float,
    color: Color,
    coreWidth: Float,
    glowIntensity: Float,
) {
    val layers = glowLayers(glowIntensity)
    for ((w, alpha) in layers) {
        drawOval(
            color = color.copy(alpha = alpha),
            topLeft = Offset(center.x - radiusX, center.y - radiusY),
            size = Size(radiusX * 2f, radiusY * 2f),
            style = Stroke(width = w + coreWidth),
        )
    }
    drawOval(
        color = color,
        topLeft = Offset(center.x - radiusX, center.y - radiusY),
        size = Size(radiusX * 2f, radiusY * 2f),
        style = Stroke(width = coreWidth),
    )
    drawOval(
        color = Color.White.copy(alpha = 0.55f * glowIntensity),
        topLeft = Offset(center.x - radiusX, center.y - radiusY),
        size = Size(radiusX * 2f, radiusY * 2f),
        style = Stroke(width = coreWidth * 0.35f),
    )
}

private fun DrawScope.drawNeonFilledCircle(
    center: Offset,
    radius: Float,
    color: Color,
    glowIntensity: Float,
) {
    val layers = glowLayers(glowIntensity, filled = true)
    for ((expand, alpha) in layers) {
        drawCircle(
            color = color.copy(alpha = alpha * 0.6f),
            radius = radius + expand,
            center = center,
        )
    }
    drawCircle(color = color, radius = radius, center = center)
    drawCircle(
        color = Color.White.copy(alpha = 0.75f * glowIntensity),
        radius = radius * 0.35f,
        center = center + Offset(-radius * 0.25f, -radius * 0.25f),
    )
}

private fun DrawScope.drawNeonPath(
    path: Path,
    color: Color,
    coreWidth: Float,
    glowIntensity: Float,
) {
    val layers = glowLayers(glowIntensity)
    for ((w, alpha) in layers) {
        drawPath(
            path = path,
            color = color.copy(alpha = alpha),
            style = Stroke(width = w + coreWidth, cap = StrokeCap.Round),
        )
    }
    drawPath(
        path = path,
        color = color,
        style = Stroke(width = coreWidth, cap = StrokeCap.Round),
    )
    drawPath(
        path = path,
        color = Color.White.copy(alpha = 0.55f * glowIntensity),
        style = Stroke(width = coreWidth * 0.3f, cap = StrokeCap.Round),
    )
}

private fun glowLayers(
    intensity: Float,
    filled: Boolean = false,
): List<Pair<Float, Float>> {
    return if (filled) {
        listOf(
            18f to 0.06f * intensity,
            12f to 0.12f * intensity,
            6f to 0.22f * intensity,
        )
    } else {
        listOf(
            20f to 0.07f * intensity,
            14f to 0.14f * intensity,
            8f to 0.28f * intensity,
        )
    }
}
