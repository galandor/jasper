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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import com.jasper.facemirror.model.DialogPhase
import com.jasper.facemirror.model.FaceExpression
import com.jasper.facemirror.model.FaceState
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private val BackgroundBlack = Color(0xFF000000)
private val NeonCyan = Color(0xFF00F0FF)
private val NeonPink = Color(0xFFFF2DAA)
private val NeonYellow = Color(0xFFFFEA00)
private val NeonPurple = Color(0xFFB388FF)
private val NeonOrange = Color(0xFFFF5722)
private val EyeBlockBlue = Color(0xFF1565FF)
private val EyeBlockHighlight = Color(0xFF64B5FF)
private val EyeFrameColor = Color(0xFFE3F2FD)
private const val BROW_BASE_RAISE = -6f

@Composable
fun NeonFace(
    faceState: FaceState,
    expression: FaceExpression = FaceExpression.NEUTRAL,
    dialogPhase: DialogPhase = DialogPhase.IDLE,
    isSpeaking: Boolean = false,
    lipPulse: Float = 0f,
    modifier: Modifier = Modifier,
) {
    val baseEyeOpen = expression.eyeOpen.coerceIn(0.2f, 1.1f)
    val eyeOpen by animateFloatAsState(
        targetValue = baseEyeOpen,
        animationSpec = spring(stiffness = Spring.StiffnessLow, dampingRatio = 0.72f),
        label = "eyeOpen",
    )
    val smile by animateFloatAsState(
        targetValue = expression.smileAmount,
        animationSpec = spring(stiffness = Spring.StiffnessLow, dampingRatio = 0.72f),
        label = "smile",
    )
    val browInner by animateFloatAsState(
        targetValue = expression.browInnerLift + dialogBrowInnerAdjust(dialogPhase) + BROW_BASE_RAISE,
        animationSpec = spring(stiffness = Spring.StiffnessMedium, dampingRatio = 0.7f),
        label = "browInner",
    )
    val browOuter by animateFloatAsState(
        targetValue = expression.browOuterLift + dialogBrowOuterAdjust(dialogPhase) + BROW_BASE_RAISE,
        animationSpec = spring(stiffness = Spring.StiffnessMedium, dampingRatio = 0.7f),
        label = "browOuter",
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

    val mouthDrive = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(isSpeaking) {
        if (!isSpeaking) {
            mouthDrive.floatValue = 0f
            return@LaunchedEffect
        }
        while (isActive) {
            mouthDrive.floatValue = 0.55f + Random.nextFloat() * 0.4f
            delay(75L + Random.nextLong(35))
            if (!isSpeaking) break
            mouthDrive.floatValue = 0.12f + Random.nextFloat() * 0.18f
            delay(65L + Random.nextLong(30))
        }
        mouthDrive.floatValue = 0f
    }

    val lipPulseAnimated by animateFloatAsState(
        targetValue = lipPulse,
        animationSpec = spring(stiffness = Spring.StiffnessHigh, dampingRatio = 0.4f),
        label = "lipPulse",
    )
    val mouthDriveAnimated by animateFloatAsState(
        targetValue = mouthDrive.floatValue,
        animationSpec = spring(stiffness = Spring.StiffnessHigh, dampingRatio = 0.42f),
        label = "mouthDrive",
    )
    val speakOpen = if (isSpeaking) {
        maxOf(mouthDriveAnimated, lipPulseAnimated)
    } else {
        0f
    }

    val squashXTarget = remember { mutableFloatStateOf(1f) }
    val squashYTarget = remember { mutableFloatStateOf(1f) }
    LaunchedEffect(expression) {
        squashXTarget.floatValue = 1.06f
        squashYTarget.floatValue = 0.9f
        delay(85)
        squashXTarget.floatValue = 1.2f
        squashYTarget.floatValue = 0.78f
        delay(130)
        squashXTarget.floatValue = 0.94f
        squashYTarget.floatValue = 1.08f
        delay(100)
        squashXTarget.floatValue = 1f
        squashYTarget.floatValue = 1f
    }
    val squashX by animateFloatAsState(
        targetValue = squashXTarget.floatValue,
        animationSpec = spring(stiffness = Spring.StiffnessMedium, dampingRatio = 0.48f),
        label = "squashX",
    )
    val squashY by animateFloatAsState(
        targetValue = squashYTarget.floatValue,
        animationSpec = spring(stiffness = Spring.StiffnessMedium, dampingRatio = 0.48f),
        label = "squashY",
    )

    val bounceTarget = remember { mutableFloatStateOf(1f) }
    LaunchedEffect(expression) {
        bounceTarget.floatValue = 1.12f
        delay(110)
        bounceTarget.floatValue = 1f
    }
    val expressionBounce by animateFloatAsState(
        targetValue = bounceTarget.floatValue,
        animationSpec = spring(stiffness = Spring.StiffnessMedium, dampingRatio = 0.5f),
        label = "expressionBounce",
    )

    val glowSpeed = when (expression) {
        FaceExpression.HAPPY, FaceExpression.PLAYFUL -> 900
        FaceExpression.ANGRY -> 700
        FaceExpression.AFRAID -> 500
        FaceExpression.SLEEPY -> 4500
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
    val breathe by infiniteTransition.animateFloat(
        initialValue = 0.99f,
        targetValue = 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(3200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breathe",
    )
    val idleDriftX by infiniteTransition.animateFloat(
        initialValue = -0.035f,
        targetValue = 0.035f,
        animationSpec = infiniteRepeatable(
            animation = tween(5800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "idleDriftX",
    )
    val idleDriftY by infiniteTransition.animateFloat(
        initialValue = -0.025f,
        targetValue = 0.025f,
        animationSpec = infiniteRepeatable(
            animation = tween(7200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "idleDriftY",
    )

    val idlePhase = remember { Animatable(0f) }
    LaunchedEffect(faceState.isDetected) {
        if (!faceState.isDetected) {
            while (isActive) {
                idlePhase.animateTo(
                    targetValue = idlePhase.value + 1f,
                    animationSpec = tween(5000, easing = LinearEasing),
                )
            }
        }
    }

    val blinkTarget = remember { mutableFloatStateOf(1f) }
    LaunchedEffect(faceState.isDetected, expression) {
        if (!faceState.isDetected || expression == FaceExpression.SLEEPY) {
            blinkTarget.floatValue = 1f
            return@LaunchedEffect
        }
        while (isActive && faceState.isDetected && expression != FaceExpression.SLEEPY) {
            val (minDelay, maxDelay) = blinkIntervalFor(expression)
            delay(Random.nextLong(minDelay, maxDelay))
            if (!faceState.isDetected || expression == FaceExpression.SLEEPY) break

            performBlink(blinkTarget)
            if (Random.nextFloat() < 0.18f) {
                delay(160)
                performBlink(blinkTarget, fast = true)
            }
        }
        blinkTarget.floatValue = 1f
    }
    val blinkOpen by animateFloatAsState(
        targetValue = blinkTarget.floatValue,
        animationSpec = spring(stiffness = Spring.StiffnessHigh, dampingRatio = 0.52f),
        label = "blinkOpen",
    )

    val saccadeX = remember { Animatable(0f) }
    val saccadeY = remember { Animatable(0f) }
    LaunchedEffect(faceState.isDetected) {
        if (!faceState.isDetected) {
            saccadeX.snapTo(0f)
            saccadeY.snapTo(0f)
            return@LaunchedEffect
        }
        while (isActive && faceState.isDetected) {
            delay(Random.nextLong(1800, 4200))
            if (!faceState.isDetected) break
            val targetX = Random.nextFloat() * 0.11f - 0.055f
            val targetY = Random.nextFloat() * 0.07f - 0.035f
            saccadeX.animateTo(
                targetX,
                spring(stiffness = Spring.StiffnessHigh, dampingRatio = 0.55f),
            )
            saccadeY.animateTo(
                targetY,
                spring(stiffness = Spring.StiffnessHigh, dampingRatio = 0.55f),
            )
            delay(140L + Random.nextLong(80))
            saccadeX.animateTo(0f, spring(stiffness = Spring.StiffnessMedium, dampingRatio = 0.7f))
            saccadeY.animateTo(0f, spring(stiffness = Spring.StiffnessMedium, dampingRatio = 0.7f))
        }
    }

    val blinkSquash = 1f - (1f - blinkOpen) * 0.45f
    val effectiveEyeOpen = eyeOpen * blinkOpen
    val eyeStretchX = 1f + (1f - blinkOpen) * 0.32f

    val thinkingGazeY = if (dialogPhase == DialogPhase.THINKING) -0.14f else 0f
    val driftX = if (faceState.isDetected) idleDriftX else 0f
    val driftY = if (faceState.isDetected) idleDriftY else 0f

    val gazeX = if (faceState.isDetected) {
        (yaw / 35f) * 0.4f + saccadeX.value + driftX
    } else {
        sin(idlePhase.value * 2f * Math.PI.toFloat()) * 0.2f
    }
    val gazeY = if (faceState.isDetected) {
        (pitch / 25f) * 0.3f + saccadeY.value + driftY + thinkingGazeY
    } else {
        cos(idlePhase.value * 2f * Math.PI.toFloat()) * 0.12f
    }

    val accentColor = when (expression) {
        FaceExpression.HAPPY, FaceExpression.PLAYFUL -> NeonYellow
        FaceExpression.SAD, FaceExpression.OFFENDED -> NeonPurple
        FaceExpression.SURPRISED, FaceExpression.AFRAID -> NeonCyan
        FaceExpression.ANGRY -> NeonOrange
        FaceExpression.SLEEPY -> NeonPink.copy(alpha = 0.55f)
        else -> NeonPink
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundBlack),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val baseScale = size.height * 0.0016f * expressionBounce * breathe
            val cx = size.width / 2f
            val cy = size.height * 0.44f

            withTransform({
                translate(cx, cy)
                scale(
                    baseScale * squashX,
                    baseScale * squashY,
                    pivot = Offset.Zero,
                )
            }) {
                val eyeSpacing = size.width / baseScale * 0.30f
                val eyeSize = size.height / baseScale * 0.17f
                val mouthY = eyeSize * 1.65f
                val leftEye = Offset(-eyeSpacing / 2f, 0f)
                val rightEye = Offset(eyeSpacing / 2f, 0f)

                drawNeonEyebrow(
                    eyeCenter = leftEye,
                    eyeRadius = eyeSize,
                    innerLift = browInner,
                    outerLift = browOuter,
                    color = accentColor,
                    glowIntensity = glowPulse,
                    isLeftEye = true,
                )
                drawNeonEyebrow(
                    eyeCenter = rightEye,
                    eyeRadius = eyeSize,
                    innerLift = browInner,
                    outerLift = browOuter,
                    color = accentColor,
                    glowIntensity = glowPulse,
                    isLeftEye = false,
                )

                val eyeStretch = eyeStretchX * blinkSquash
                drawBlockEye(
                    center = leftEye,
                    baseSize = eyeSize,
                    openAmount = effectiveEyeOpen,
                    stretchX = eyeStretch,
                    gazeX = gazeX,
                    gazeY = gazeY,
                    headYaw = yaw,
                    expression = expression,
                    isLeftEye = true,
                    glowIntensity = glowPulse,
                )
                drawBlockEye(
                    center = rightEye,
                    baseSize = eyeSize,
                    openAmount = effectiveEyeOpen,
                    stretchX = eyeStretch,
                    gazeX = gazeX,
                    gazeY = gazeY,
                    headYaw = yaw,
                    expression = expression,
                    isLeftEye = false,
                    glowIntensity = glowPulse,
                )

                drawNeonMouth(
                    smile = smile,
                    speakOpen = speakOpen,
                    y = mouthY,
                    width = eyeSpacing * 0.9f,
                    color = accentColor,
                    glowIntensity = glowPulse,
                    surprised = expression == FaceExpression.SURPRISED,
                    sleepy = expression == FaceExpression.SLEEPY,
                )
            }
        }
    }
}

private suspend fun performBlink(blinkTarget: MutableFloatState, fast: Boolean = false) {
    val closeMs = if (fast) 45L else 65L
    val openMs = if (fast) 85L else 115L
    blinkTarget.floatValue = 0.04f
    delay(closeMs)
    blinkTarget.floatValue = 1f
    delay(openMs)
}

private fun blinkIntervalFor(expression: FaceExpression): Pair<Long, Long> = when (expression) {
    FaceExpression.NEUTRAL -> 2800L to 6500L
    FaceExpression.HAPPY, FaceExpression.PLAYFUL -> 3500L to 8000L
    FaceExpression.AFRAID, FaceExpression.SURPRISED -> 2200L to 5000L
    FaceExpression.ANGRY -> 4000L to 9000L
    FaceExpression.SAD, FaceExpression.OFFENDED -> 4500L to 10000L
    else -> 3000L to 7000L
}

private fun DrawScope.drawNeonEyebrow(
    eyeCenter: Offset,
    eyeRadius: Float,
    innerLift: Float,
    outerLift: Float,
    color: Color,
    glowIntensity: Float,
    isLeftEye: Boolean,
) {
    val browBaseY = eyeCenter.y - eyeRadius * 0.84f
    val innerX = if (isLeftEye) eyeCenter.x + eyeRadius * 0.38f else eyeCenter.x - eyeRadius * 0.38f
    val outerX = if (isLeftEye) eyeCenter.x - eyeRadius * 0.88f else eyeCenter.x + eyeRadius * 0.88f
    val inner = Offset(innerX, browBaseY + innerLift)
    val outer = Offset(outerX, browBaseY + outerLift)
    val midX = (inner.x + outer.x) / 2f
    val midY = (inner.y + outer.y) / 2f - eyeRadius * 0.12f

    val path = Path().apply {
        moveTo(inner.x, inner.y)
        quadraticTo(midX, midY, outer.x, outer.y)
    }
    drawNeonPath(path, color, 3.8f, glowIntensity)
}

/** Асимметричное сужение рамки: (внутренний край, внешний край), 0..1 */
private fun blockEyeSquish(expression: FaceExpression, isLeftEye: Boolean): Pair<Float, Float> {
    val (inner, outer) = when (expression) {
        FaceExpression.ANGRY -> 0.38f to 0.06f
        FaceExpression.SAD -> 0.08f to 0.34f
        FaceExpression.OFFENDED -> 0.12f to 0.28f
        FaceExpression.HAPPY, FaceExpression.PLAYFUL -> 0.14f to 0.14f
        FaceExpression.AFRAID, FaceExpression.SURPRISED -> 0f to 0f
        FaceExpression.SLEEPY -> 0.22f to 0.22f
        else -> 0.05f to 0.05f
    }
    return if (isLeftEye) inner to outer else outer to inner
}

private fun DrawScope.drawBlockEye(
    center: Offset,
    baseSize: Float,
    openAmount: Float,
    stretchX: Float,
    gazeX: Float,
    gazeY: Float,
    headYaw: Float,
    expression: FaceExpression,
    isLeftEye: Boolean,
    glowIntensity: Float,
) {
    val squish = openAmount.coerceIn(0.06f, 1.15f)
    val (innerSquish, outerSquish) = blockEyeSquish(expression, isLeftEye)

    val frameW = baseSize * 2.05f * stretchX * if (openAmount > 1f) openAmount else 1f
    val frameH = baseSize * 2.35f * squish

    val innerInset = innerSquish * frameW * 0.42f
    val outerInset = outerSquish * frameW * 0.42f

    val left: Float
    val right: Float
    if (isLeftEye) {
        left = center.x - frameW / 2f + outerInset
        right = center.x + frameW / 2f - innerInset
    } else {
        left = center.x - frameW / 2f + innerInset
        right = center.x + frameW / 2f - outerInset
    }
    val top = center.y - frameH / 2f
    val bottom = center.y + frameH / 2f
    val frameWidth = right - left
    val frameHeight = bottom - top

    if (squish < 0.18f) {
        drawRect(
            color = EyeBlockBlue,
            topLeft = Offset(left, center.y - baseSize * 0.06f),
            size = Size(frameWidth, baseSize * 0.12f),
        )
        drawRect(
            color = EyeFrameColor,
            topLeft = Offset(left, center.y - baseSize * 0.06f),
            size = Size(frameWidth, baseSize * 0.12f),
            style = Stroke(width = baseSize * 0.1f),
        )
        return
    }

    val frameStroke = baseSize * 0.11f
    val glowExpand = baseSize * 0.14f * glowIntensity
    drawRect(
        color = EyeBlockBlue.copy(alpha = 0.12f * glowIntensity),
        topLeft = Offset(left - glowExpand, top - glowExpand),
        size = Size(frameWidth + glowExpand * 2f, frameHeight + glowExpand * 2f),
    )
    drawRect(
        color = EyeFrameColor,
        topLeft = Offset(left, top),
        size = Size(frameWidth, frameHeight),
        style = Stroke(width = frameStroke),
    )
    drawRect(
        color = EyeFrameColor.copy(alpha = 0.35f),
        topLeft = Offset(left + frameStroke * 0.4f, top + frameStroke * 0.4f),
        size = Size(frameWidth - frameStroke * 0.8f, frameHeight - frameStroke * 0.8f),
        style = Stroke(width = frameStroke * 0.35f),
    )

    val padding = baseSize * 0.16f
    val innerLeft = left + padding
    val innerTop = top + padding
    val innerWidth = frameWidth - padding * 2f
    val innerHeight = frameHeight - padding * 2f

    val blockW = innerWidth * 0.68f
    val blockH = innerHeight * 0.68f * squish.coerceAtMost(1f)
    val travelX = innerWidth * 0.26f
    val travelY = innerHeight * 0.26f
    val blockCenterX = innerLeft + innerWidth / 2f +
        gazeX * travelX + (headYaw / 35f) * travelX * 0.4f
    val blockCenterY = innerTop + innerHeight / 2f + gazeY * travelY

    val blockLeft = blockCenterX - blockW / 2f
    val blockTop = blockCenterY - blockH / 2f

    drawRect(
        color = EyeBlockBlue,
        topLeft = Offset(blockLeft, blockTop),
        size = Size(blockW, blockH),
    )
    val highlightW = blockW * 0.28f
    val highlightH = blockH * 0.22f
    drawRect(
        color = EyeBlockHighlight.copy(alpha = 0.55f),
        topLeft = Offset(blockLeft + blockW * 0.08f, blockTop + blockH * 0.1f),
        size = Size(highlightW, highlightH),
    )
    drawRect(
        color = Color.White.copy(alpha = 0.2f),
        topLeft = Offset(blockLeft, blockTop),
        size = Size(blockW, blockH),
        style = Stroke(width = baseSize * 0.04f),
    )
}

private fun DrawScope.drawNeonMouth(
    smile: Float,
    speakOpen: Float,
    y: Float,
    width: Float,
    color: Color,
    glowIntensity: Float,
    surprised: Boolean = false,
    sleepy: Boolean = false,
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

    if (sleepy) {
        val mouthW = width * 0.22f
        drawNeonPath(
            path = Path().apply {
                moveTo(-mouthW / 2f, y)
                lineTo(mouthW / 2f, y)
            },
            color = color,
            coreWidth = 3f,
            glowIntensity = glowIntensity * 0.7f,
        )
        return
    }

    if (speakOpen > 0.04f) {
        val openH = width * (0.1f + speakOpen * 0.2f)
        val openW = width * (0.12f + speakOpen * 0.26f)
        val centerY = y + openH * 0.2f

        drawNeonEllipse(
            center = Offset(0f, centerY),
            radiusX = openW / 2f,
            radiusY = openH / 2f,
            color = color,
            coreWidth = 4f + speakOpen * 2.5f,
            glowIntensity = glowIntensity,
        )

        val upperLip = Path().apply {
            moveTo(-openW * 0.52f, y - openH * 0.05f)
            quadraticTo(0f, y - openH * 0.45f, openW * 0.52f, y - openH * 0.05f)
        }
        drawNeonPath(upperLip, color, 3.5f, glowIntensity * 0.9f)

        if (smile > 0.1f) {
            val cornerLift = 4f + smile * 10f
            val leftCorner = Path().apply {
                moveTo(-openW * 0.5f, y)
                quadraticTo(-openW * 0.65f, y + cornerLift, -openW * 0.75f, y + cornerLift * 0.5f)
            }
            val rightCorner = Path().apply {
                moveTo(openW * 0.5f, y)
                quadraticTo(openW * 0.65f, y + cornerLift, openW * 0.75f, y + cornerLift * 0.5f)
            }
            drawNeonPath(leftCorner, color, 2.5f, glowIntensity * 0.75f)
            drawNeonPath(rightCorner, color, 2.5f, glowIntensity * 0.75f)
        }
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

    drawNeonPath(path, color, coreWidth, glowIntensity)
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

private fun dialogBrowInnerAdjust(phase: DialogPhase): Float = when (phase) {
    DialogPhase.LISTENING -> -5f
    DialogPhase.THINKING -> -7f
    else -> 0f
}

private fun dialogBrowOuterAdjust(phase: DialogPhase): Float = when (phase) {
    DialogPhase.LISTENING -> -4f
    else -> 0f
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
