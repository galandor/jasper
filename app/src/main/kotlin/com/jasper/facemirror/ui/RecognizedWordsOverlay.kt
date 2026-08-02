package com.jasper.facemirror.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jasper.facemirror.model.SpeechState
import kotlinx.coroutines.delay

private val NeonCyan = Color(0xFF00F0FF)
private val NeonPink = Color(0xFFFF2DAA)
private val DimText = Color(0xFF00F0FF).copy(alpha = 0.35f)

@Composable
fun RecognizedWordsOverlay(
    speechState: SpeechState,
    modifier: Modifier = Modifier,
) {
    var visibleFinalText by remember { mutableStateOf("") }
    var fadeOut by remember { mutableStateOf(false) }

    LaunchedEffect(speechState.recognizedText) {
        if (speechState.recognizedText.isNotBlank()) {
            visibleFinalText = speechState.recognizedText
            fadeOut = false
            delay(3500)
            fadeOut = true
            delay(800)
            visibleFinalText = ""
            fadeOut = false
        }
    }

    val finalAlpha by animateFloatAsState(
        targetValue = if (fadeOut) 0f else 1f,
        animationSpec = tween(700),
        label = "finalAlpha",
    )

    val displayText = speechState.partialText.ifBlank { visibleFinalText }
    val isPartial = speechState.partialText.isNotBlank()

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (displayText.isNotBlank()) {
                Text(
                    text = displayText,
                    modifier = Modifier.fillMaxWidth(),
                    style = neonTextStyle(
                        color = if (isPartial) NeonPink else NeonCyan,
                        alpha = if (isPartial) 1f else finalAlpha,
                    ),
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (speechState.history.size > 1) {
                val previous = speechState.history.dropLast(1).takeLast(2).reversed()
                previous.forEach { phrase ->
                    Text(
                        text = phrase,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                        style = neonTextStyle(color = DimText, alpha = 0.7f),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private fun neonTextStyle(color: Color, alpha: Float = 1f): TextStyle {
    val c = color.copy(alpha = color.alpha * alpha)
    return TextStyle(
        color = c,
        fontSize = 22.sp,
        fontWeight = FontWeight.Medium,
        shadow = Shadow(
            color = c.copy(alpha = 0.9f),
            blurRadius = 24f,
        ),
    )
}
