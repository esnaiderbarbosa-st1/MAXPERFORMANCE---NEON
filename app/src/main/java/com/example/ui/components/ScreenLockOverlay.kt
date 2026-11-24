package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun ScreenLockOverlay(
    isLocked: Boolean,
    onUnlocked: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var holdProgress by remember { mutableFloatStateOf(0f) }
    var isHolding by remember { mutableStateOf(false) }

    LaunchedEffect(isHolding) {
        if (isHolding) {
            val startTime = System.currentTimeMillis()
            val duration = 2000L // 2 segundos para liberar
            while (isHolding && isActive) {
                val elapsed = System.currentTimeMillis() - startTime
                holdProgress = (elapsed.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                if (elapsed >= duration) {
                    onUnlocked()
                    isHolding = false
                    holdProgress = 0f
                    break
                }
                delay(16L)
            }
        } else {
            holdProgress = 0f
        }
    }

    Box(modifier = modifier) {
        content()

        if (isLocked) {
            // Dim translucido (~31% opacidade) sem consumir toques
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x501C1C1E))
            )

            // Indicador "BLOQUEADA" no topo
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xCC1C1C1E),
                border = BorderStroke(1.dp, Color(0x33FFFFFF)),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp)
                    .windowInsetsPadding(WindowInsets.statusBars)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Tela bloqueada",
                        tint = Color(0xFFFFD740),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "BLOQUEADA",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            }

            // Barra inferior de desbloqueio (única área interativa)
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = Color(0xE62C2C2E),
                border = BorderStroke(1.dp, Color(0x33FFFFFF)),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .heightIn(min = 64.dp)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                isHolding = true
                                tryAwaitRelease()
                                isHolding = false
                            }
                        )
                    }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(40.dp)
                    ) {
                        if (holdProgress > 0f) {
                            CircularProgressIndicator(
                                progress = { holdProgress },
                                modifier = Modifier.size(40.dp),
                                color = Color(0xFFFFD740),
                                strokeWidth = 3.dp,
                                trackColor = Color(0xFF3A3A3C)
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Cadeado",
                            tint = Color(0xFFFFD740),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column(
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "SEGURE 2s PARA DESBLOQUEAR",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Toques em outras áreas são ignorados",
                            color = Color(0xFFAEAEB2),
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}
