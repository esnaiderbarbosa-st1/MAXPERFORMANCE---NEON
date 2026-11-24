package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AppMode
import com.example.ui.theme.Primary

import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.ui.text.style.TextOverflow

@Composable
fun AppModeSegmentedToggle(
    currentMode: AppMode,
    onModeSelected: (AppMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val isBodybuilding = currentMode == AppMode.BODYBUILDING
    val isRunning = currentMode == AppMode.RUNNING

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50.dp))
            .background(com.example.ui.theme.RunColors.surface)
            .border(1.dp, com.example.ui.theme.RunColors.border, RoundedCornerShape(50.dp))
            .padding(2.dp)
            .testTag("app_mode_toggle")
    ) {
        Row(
            modifier = Modifier.height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // Musculação Button
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(50.dp))
                    .background(if (isBodybuilding) com.example.ui.theme.RunColors.border else Color.Transparent)
                    .clickable {
                        if (!isBodybuilding) {
                            com.example.utils.AudioSynth.playTickSound()
                            onModeSelected(AppMode.BODYBUILDING)
                        }
                    }
                    .padding(horizontal = 9.dp, vertical = 6.dp)
                    .testTag("toggle_mode_bodybuilding"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Musculação",
                    fontSize = 11.sp,
                    fontWeight = if (isBodybuilding) FontWeight.Bold else FontWeight.Medium,
                    color = if (isBodybuilding) Color.White else com.example.ui.theme.RunColors.textSecondary,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Indicador Circular de 7dp
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(if (isBodybuilding) Primary else com.example.ui.theme.RunColors.brand)
            )

            // Corrida Button
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(50.dp))
                    .background(if (isRunning) com.example.ui.theme.RunColors.border else Color.Transparent)
                    .clickable {
                        if (!isRunning) {
                            com.example.utils.AudioSynth.playTickSound()
                            onModeSelected(AppMode.RUNNING)
                        }
                    }
                    .padding(horizontal = 9.dp, vertical = 6.dp)
                    .testTag("toggle_mode_running"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Corrida",
                    fontSize = 11.sp,
                    fontWeight = if (isRunning) FontWeight.Bold else FontWeight.Medium,
                    color = if (isRunning) Color.White else com.example.ui.theme.RunColors.textSecondary,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
