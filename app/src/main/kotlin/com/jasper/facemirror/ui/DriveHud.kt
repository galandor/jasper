package com.jasper.facemirror.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jasper.facemirror.openbot.DriveHudState

@Composable
fun DriveHud(
    state: DriveHudState,
    modifier: Modifier = Modifier,
) {
    val line = buildString {
        if (state.recording) append("REC  ")
        if (state.autopilot) append("AUTO  ")
        if (state.gamepad) append("PAD  ")
        if (!state.hasModel) append("no model  ")
        val sonar = state.sonarCm
        if (sonar != null) {
            append(if (sonar == 0) "sonar —  " else "sonar ${sonar}cm  ")
        }
        append(state.status)
    }
    Box(
        modifier = modifier,
        contentAlignment = Alignment.TopStart,
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp)
                .background(Color(0x99000000), RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Text(
                text = line,
                color = if (state.recording) Color(0xFFFF4D6A) else Color(0xFF00F0FF),
                fontSize = 12.sp,
            )
        }
    }
}
