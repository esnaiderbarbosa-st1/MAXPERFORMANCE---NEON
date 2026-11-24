package com.example.ui.screens

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.utils.AudioSynth
import kotlinx.coroutines.delay

private const val TAG = "SplashAnimation"

/**
 * Função utilitária para reproduzir o efeito sonoro de swoosh/raio.
 * Tenta reproduzir a partir de res/raw/swoosh_sound (.wav/.mp3),
 * com tratamento de exceção seguro para falha silenciosa ou fallback sintético.
 */
fun playSwooshSound(context: Context) {
    try {
        val resourceId = context.resources.getIdentifier("swoosh_sound", "raw", context.packageName)
        if (resourceId != 0) {
            val mediaPlayer = MediaPlayer.create(context, resourceId)
            mediaPlayer?.start()
            mediaPlayer?.setOnCompletionListener { mp ->
                try {
                    mp.release()
                } catch (_: Exception) {}
            }
        } else {
            AudioSynth.playBoxingBellSound(context)
        }
    } catch (e: Exception) {
        Log.w(TAG, "Falha ao reproduzir swoosh_sound, executando fallback", e)
        try {
            AudioSynth.playBoxingBellSound(context)
        } catch (_: Exception) {}
    }
}

/**
 * Componente das 3 linhas de velocidade horizontais à esquerda do texto do logo.
 */
@Composable
fun SpeedLines(color: Color) {
    Column(
        modifier = Modifier.width(80.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Traço superior (curto)
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color)
        )
        // Traço do meio (médio)
        Box(
            modifier = Modifier
                .width(60.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color)
        )
        // Traço inferior (longo)
        Box(
            modifier = Modifier
                .width(80.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color)
        )
    }
}

/**
 * Composable redesenhado da Splash Screen "MAX PERFORMANCE" em estilo Fitness 22.
 *
 * Timeline da Animação:
 * - Fase 1 (0ms - 800ms): Logo entra da esquerda (-600dp, cinza) até o centro (0dp, azul elétrico)
 * - Fase 2 (800ms - 1500ms): Pausa no centro e dispara efeito sonoro aos 800ms
 * - Fase 3 (1500ms - 2000ms): Acelera para a direita (600dp) exibindo um rastro completo
 * - Fase 4 (2000ms): Finaliza a animação e navega para a MainActivity
 */
@Composable
fun AnimatedSplashContent(onAnimationComplete: () -> Unit) {
    val context = LocalContext.current
    var animationPhase by remember { mutableIntStateOf(1) }

    val xOffset by animateDpAsState(
        targetValue = when (animationPhase) {
            1 -> (-600).dp
            2 -> 0.dp
            3 -> 600.dp
            else -> 600.dp
        },
        animationSpec = when (animationPhase) {
            1 -> tween(durationMillis = 900, easing = FastOutSlowInEasing)
            2 -> tween(durationMillis = 700) // Pausa no centro
            3 -> tween(durationMillis = 450, easing = FastOutLinearInEasing) // Saída acelerada
            else -> tween(0)
        },
        label = "splash_x_offset"
    )

    val brandColor by animateColorAsState(
        targetValue = if (animationPhase >= 2) Color(0xFF2979FF) else Color.Gray.copy(alpha = 0.4f),
        animationSpec = tween(durationMillis = 400),
        label = "splash_brand_color"
    )

    LaunchedEffect(Unit) {
        animationPhase = 2
        delay(800L)
        playSwooshSound(context)
        delay(700L)
        animationPhase = 3
        delay(500L)
        onAnimationComplete()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0D12)),
        contentAlignment = Alignment.Center
    ) {
        // RASTRO DE VELOCIDADE (Fase 3 de saída rápida)
        if (animationPhase == 3) {
            Row(
                modifier = Modifier.offset(x = xOffset - 50.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SpeedLines(color = brandColor.copy(alpha = 0.3f))
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "MAX PERFORMANCE",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                    fontStyle = FontStyle.Italic,
                    letterSpacing = (-1).sp,
                    color = brandColor.copy(alpha = 0.3f)
                )
            }
        }

        // LOGO PRINCIPAL (Traços de velocidade + Texto)
        Row(
            modifier = Modifier.offset(x = xOffset),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SpeedLines(color = brandColor)
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = "MAX PERFORMANCE",
                fontSize = 32.sp,
                fontWeight = FontWeight.Black,
                fontStyle = FontStyle.Italic,
                letterSpacing = (-1).sp,
                color = brandColor
            )
        }

        // Rodapé discreto com versão e build
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 28.dp)
        ) {
            Text(
                text = com.example.utils.AppBuildInfo.splashSignature,
                color = Color.White.copy(alpha = 0.35f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.5.sp
            )
        }
    }
}
