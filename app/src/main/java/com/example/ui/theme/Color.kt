package com.example.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// --- PALETA OFICIAL DARK NEUTRO & ACENTO AZUL/CIANO (#00B0FF / #00E5FF) ---
val BackgroundStart = Color(0xFF0D0F14)     // Dark Neutro Profundo (Background Geral)
val BackgroundEnd = Color(0xFF11151D)       // Dark Neutro Secundário
val GraphiteSurface = Color(0xFF161922)     // Superfície de Cards
val WarmCardTop = Color(0xFF161922)         // Card Gradient Top
val WarmCardBottom = Color(0xFF11151D)      // Card Gradient Bottom
val SecondaryElement = Color(0xFF1E2430)    // Pílulas e Containers Secundários
val WarmPillContainer = Color(0xFF1E2430)   // Pílulas Dark Neutras
val BorderColor = Color(0xFF262B38)         // Borda Slate Escura

// --- PALETA DE DESTAQUE & AZUL/CIANO ILUMINADO ---
val MaxOrange = Color(0xFF00B0FF)           // Azul Oficial Max Performance (#00B0FF)
val Primary = Color(0xFF00B0FF)             // Azul Vibrante (#00B0FF)
val CopperRimLight = Color(0xFF00E5FF)      // Ciano Iluminado para Rim Lighting (#00E5FF)
val CopperVolumeTop = Color(0xFF00E5FF)     // Topo do Degradê de Volume (Botões/Badges)
val CopperVolumeBottom = Color(0xFF00B0FF)  // Base do Degradê de Volume (Botões/Badges)
val Secondary = Color(0xFF00E5FF)           // Ciano (#00E5FF)
val Error = Color(0xFFFF4444)               // Vermelho alerta
val Success = Color(0xFF34C759)             // Verde sucesso
val PremiumColor = Color(0xFFAF52DE)        // Roxo premium

// --- SISTEMA DE TEXTOS NEUTROS ---
val TextPrimary = Color(0xFFFFFFFF)
val TextSecondary = Color(0xFF94A3B8)       // Slate Suave
val WarmSandText = Color(0xFF94A3B8)        // Slate Suave
val TextTertiary = Color(0xFF64748B)        // Slate Escuro
val TextDisabled = Color(0xFF475569)

// --- BACKWARDS COMPATIBILITY MAPS ---
val OnPrimary = Color(0xFFFFFFFF)
val PrimaryContainer = Color(0xE600B0FF)

// Surfaces mapped to Dark Neutral
val Surface = Color(0xFF161922)
val SurfaceDim = Color(0xFF0D0F14)
val SurfaceBright = Color(0xFF1E2430)
val OnSurface = Color(0xFFFFFFFF)
val OnSurfaceDim = Color(0xFF94A3B8)
val OnSurfaceVariant = Color(0xFF94A3B8)
val Outline = Color(0xFF262B38)
val OutlineVariant = Color(0xFF1E2430)

val Warning = Color(0xFF38BDF8)
val Info = Color(0xFF00E5FF)

// Muscle Group Colors
val MusclePeito = Color(0xFF00B0FF)
val MuscleCostas = Color(0xFF00E5FF)
val MusclePernas = Color(0xFF34C759)
val MuscleOmbros = Color(0xFFAF52DE)
val MuscleBiceps = Color(0xFF00B0FF)
val MuscleTriceps = Color(0xFF38BDF8)
val MuscleTrapezio = Color(0xFFAF52DE)
val MuscleAbdominais = Color(0xFF30D158)

/**
 * Borda Especular (Rim Lighting): vai de Ciano Iluminado (#00E5FF com 0.28f alpha)
 * no topo esquerdo para quase transparente (0.04f alpha) na base direita.
 */
fun warmSpecularRimBorder(width: Dp = 1.dp, topAlpha: Float = 0.28f, bottomAlpha: Float = 0.04f): BorderStroke {
    return BorderStroke(
        width = width,
        brush = Brush.linearGradient(
            colors = listOf(
                CopperRimLight.copy(alpha = topAlpha),
                CopperRimLight.copy(alpha = (topAlpha + bottomAlpha) * 0.45f),
                CopperRimLight.copy(alpha = bottomAlpha)
            )
        )
    )
}

fun specularRimBorder(width: Dp = 1.dp, topAlpha: Float = 0.28f, bottomAlpha: Float = 0.04f): BorderStroke {
    return warmSpecularRimBorder(width = width, topAlpha = topAlpha, bottomAlpha = bottomAlpha)
}

/**
 * Degradê vertical de volume para botões de destaque e badges ativos (#00E5FF -> #00B0FF).
 */
fun copperVolumeGradientBrush(): Brush {
    return Brush.verticalGradient(
        colors = listOf(CopperVolumeTop, CopperVolumeBottom)
    )
}

/**
 * Borda iluminada no topo para botões e badges com volume.
 */
fun copperVolumeHighlightBorder(width: Dp = 1.dp): BorderStroke {
    return BorderStroke(
        width = width,
        brush = Brush.verticalGradient(
            colors = listOf(
                Color(0xFF80F2FF).copy(alpha = 0.65f),
                CopperVolumeBottom.copy(alpha = 0.18f)
            )
        )
    )
}
