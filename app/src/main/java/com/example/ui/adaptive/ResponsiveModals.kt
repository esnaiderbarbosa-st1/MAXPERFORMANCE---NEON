package com.example.ui.adaptive

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.SurfaceDim

enum class ModalPosition {
    CENTER,
    SIDE
}

/**
 * ResponsiveDialog restricts width based on WindowSize:
 * - COMPACT: 90% of screen width
 * - MEDIUM: 70% of screen width
 * - EXPANDED: 50% of screen width (supports centering or right side drawer style)
 */
@Composable
fun ResponsiveDialog(
    onDismissRequest: () -> Unit,
    visible: Boolean,
    modifier: Modifier = Modifier,
    position: ModalPosition = ModalPosition.CENTER,
    properties: DialogProperties = DialogProperties(usePlatformDefaultWidth = false),
    content: @Composable ColumnScope.() -> Unit
) {
    if (!visible) return

    val windowSizeInfo = LocalWindowSizeInfo.current
    
    val widthFraction = when (windowSizeInfo.widthSizeClass) {
        WindowSize.COMPACT -> 0.90f
        WindowSize.MEDIUM -> 0.70f
        WindowSize.EXPANDED -> 0.50f
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = properties
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismissRequest
                ),
            contentAlignment = if (windowSizeInfo.widthSizeClass == WindowSize.EXPANDED && position == ModalPosition.SIDE) {
                Alignment.CenterEnd
            } else {
                Alignment.Center
            }
        ) {
            Surface(
                modifier = modifier
                    .fillMaxHeight(
                        if (windowSizeInfo.widthSizeClass == WindowSize.EXPANDED && position == ModalPosition.SIDE) 1f else 0.85f
                    )
                    .fillMaxWidth(widthFraction)
                    .padding(
                        if (windowSizeInfo.widthSizeClass == WindowSize.EXPANDED && position == ModalPosition.SIDE) {
                            PaddingValues(start = 16.dp, top = 0.dp, bottom = 0.dp, end = 0.dp)
                        } else {
                            PaddingValues(16.dp)
                        }
                    )
                    .clickable(enabled = false) {}, // prevent click propagation
                shape = if (windowSizeInfo.widthSizeClass == WindowSize.EXPANDED && position == ModalPosition.SIDE) {
                    RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)
                } else {
                    RoundedCornerShape(16.dp)
                },
                color = SurfaceDim,
                tonalElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(responsivePadding())
                ) {
                    content()
                }
            }
        }
    }
}
