package com.example.ui.components

import com.example.utils.customViewForScrollCapture

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Shader
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.ui.platform.testTag
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.ui.theme.*
import java.util.Locale

// Cache generator for the tileable background noise pattern (max 3% opacity)
object NoiseCache {
    private var cachedBitmap: ImageBitmap? = null

    fun get(width: Int = 128, height: Int = 128, intensity: Float = 0.03f): ImageBitmap {
        cachedBitmap?.let { return it }
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val random = java.util.Random()
        for (x in 0 until width) {
            for (y in 0 until height) {
                val noiseValue = random.nextInt(256)
                // Restrict opacity to maximum of 3%
                val alpha = (noiseValue * intensity).toInt().coerceIn(0, 255)
                bitmap.setPixel(x, y, android.graphics.Color.argb(alpha, 255, 255, 255))
            }
        }
        val imgBitmap = bitmap.asImageBitmap()
        cachedBitmap = imgBitmap
        return imgBitmap
    }
}

/**
 * Box with beautiful vertical gradient of the graphite palette and subtle noise layer.
 */
@Composable
fun MaxBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val noiseBitmap = remember { NoiseCache.get() }
    val gradientBrush = remember {
        Brush.verticalGradient(
            colors = listOf(BackgroundStart, BackgroundEnd)
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .customViewForScrollCapture()
            .background(gradientBrush)
            .drawBehind {
                // Difusão sutil de brilho cobre no topo da tela (Warm Obsidian & Copper Glass UI)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            MaxOrange.copy(alpha = 0.09f),
                            Color.Transparent
                        ),
                        center = androidx.compose.ui.geometry.Offset(size.width * 0.5f, 0f),
                        radius = size.width * 0.95f
                    ),
                    radius = size.width * 0.95f,
                    center = androidx.compose.ui.geometry.Offset(size.width * 0.5f, 0f)
                )

                val paint = Paint().asFrameworkPaint().apply {
                    shader = BitmapShader(
                        noiseBitmap.asAndroidBitmap(),
                        Shader.TileMode.REPEAT,
                        Shader.TileMode.REPEAT
                    )
                }
                drawIntoCanvas { canvas ->
                    canvas.nativeCanvas.drawRect(0f, 0f, size.width, size.height, paint)
                }
            },
        content = content
    )
}

/**
 * Surface with Warm Obsidian & Copper Glassmorphism, Ambient Copper Glow, and Specular Rim Lighting.
 */
@Composable
fun MaxCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    isGlassmorphism: Boolean = false,
    shape: RoundedCornerShape = RoundedCornerShape(16.dp),
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val elevation = if (isPressed) 8.dp else 4.dp

    val backgroundBrush = if (isGlassmorphism) {
        Brush.verticalGradient(
            colors = listOf(
                WarmPillContainer.copy(alpha = 0.88f),
                WarmCardBottom.copy(alpha = 0.92f)
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                WarmCardTop,
                WarmCardBottom
            )
        )
    }

    // Borda Especular (Rim Lighting): #FF8A3D (0.28f alpha) no topo esquerdo -> (0.04f alpha) na base direita
    val borderStroke = remember {
        BorderStroke(
            1.dp,
            Brush.linearGradient(
                colors = listOf(
                    CopperRimLight.copy(alpha = 0.28f),
                    CopperRimLight.copy(alpha = 0.12f),
                    CopperRimLight.copy(alpha = 0.04f)
                )
            )
        )
    }

    Surface(
        modifier = modifier
            .shadow(
                elevation = if (isGlassmorphism) 8.dp else elevation,
                shape = shape,
                clip = false
            )
            .clip(shape)
            .drawBehind {
                drawRect(brush = backgroundBrush)
                // Ambient Copper Glow radial sutil no topo
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            MaxOrange.copy(alpha = 0.14f),
                            Color.Transparent
                        ),
                        center = androidx.compose.ui.geometry.Offset(size.width * 0.35f, 0f),
                        radius = size.width * 0.85f
                    ),
                    radius = size.width * 0.85f,
                    center = androidx.compose.ui.geometry.Offset(size.width * 0.35f, 0f)
                )
            }
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = androidx.compose.foundation.LocalIndication.current,
                        onClick = onClick
                    )
                } else Modifier
            ),
        color = Color.Transparent,
        border = borderStroke,
        shape = shape,
        content = content
    )
}

/**
 * Button with subtle neon glow effect and states of press scale feedback.
 */
@Composable
fun MaxButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPrimary: Boolean = true,
    enabled: Boolean = true,
    shape: RoundedCornerShape = RoundedCornerShape(12.dp),
    content: @Composable RowScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale = if (isPressed) 0.96f else 1.0f

    Button(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isPrimary) Primary else SecondaryElement,
            contentColor = if (isPrimary) Color.Black else Color.White,
            disabledContainerColor = TextDisabled,
            disabledContentColor = TextTertiary
        ),
        border = if (!isPrimary && enabled) BorderStroke(1.dp, BorderColor) else null,
        shape = shape,
        modifier = modifier
            .scale(scale)
            .then(
                if (isPrimary && enabled) {
                    Modifier.drawBehind {
                        val glowRadius = size.height * 1.5f
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Primary.copy(alpha = 0.35f),
                                    Color.Transparent
                                ),
                                center = center,
                                radius = glowRadius
                            ),
                            radius = glowRadius,
                            center = center
                        )
                    }
                } else Modifier
            ),
        content = content
    )
}

/**
 * Glowing spotlight effect for highlighted elements.
 */
@Composable
fun MaxGlowEffect(
    modifier: Modifier = Modifier,
    glowColor: Color = Primary,
    opacity: Float = 0.3f,
    glowRadiusFraction: Float = 1.3f,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier.drawBehind {
            val glowRadius = size.minDimension * glowRadiusFraction
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        glowColor.copy(alpha = opacity),
                        Color.Transparent
                    ),
                    center = center,
                    radius = glowRadius
                ),
                radius = glowRadius,
                center = center
            )
        },
        contentAlignment = Alignment.Center,
        content = { content() }
    )
}

/**
 * SPRINT UX-002 — HOTFIX-UX-014: Equipment Placeholders & Illustration Confidence Resolution
 */
enum class EquipmentCategory(val displayName: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    DUMBBELL("HALTER", Icons.Default.FitnessCenter),
    BARBELL("BARRA", Icons.Default.FitnessCenter),
    MACHINE("MÁQUINA", Icons.Default.Info),
    CABLE("POLIA", Icons.Default.MoreVert),
    SMITH("SMITH", Icons.Default.Info),
    BODYWEIGHT("CORPORAL", Icons.Default.CheckCircle),
    PLATE_LOADED("ANILHAS", Icons.Default.FitnessCenter),
    BENCH("BANCO", Icons.Default.Info),
    GENERIC("EQUIPAMENTO", Icons.Default.FitnessCenter)
}

fun parseEquipmentCategory(equipmentStr: String?, exerciseName: String?): EquipmentCategory {
    val inferred = com.example.utils.WeightCalculator.inferEquipment(exerciseName ?: "", equipmentStr)
    val eq = inferred.lowercase(Locale.ROOT)

    return when {
        eq.contains("smith") -> EquipmentCategory.SMITH
        eq.contains("polia") || eq.contains("cabo") -> EquipmentCategory.CABLE
        eq.contains("halter") || eq.contains("dumbbell") -> EquipmentCategory.DUMBBELL
        eq.contains("barra") || eq.contains("barbell") -> EquipmentCategory.BARBELL
        eq.contains("máquina") || eq.contains("maquina") -> EquipmentCategory.MACHINE
        eq.contains("anilha") || eq.contains("plate") -> EquipmentCategory.PLATE_LOADED
        eq.contains("corporal") || eq.contains("bodyweight") -> EquipmentCategory.BODYWEIGHT
        eq.contains("banco") -> EquipmentCategory.BENCH
        else -> EquipmentCategory.BARBELL
    }
}

@Composable
fun EquipmentCategoryCanvasIcon(
    equipmentCategory: EquipmentCategory,
    size: Dp,
    tint: Color = Primary,
    modifier: Modifier = Modifier
) {
    androidx.compose.foundation.Canvas(
        modifier = modifier.size(size)
    ) {
        val w = this.size.width
        val h = this.size.height
        val cy = h * 0.5f

        when (equipmentCategory) {
            EquipmentCategory.BARBELL, EquipmentCategory.SMITH, EquipmentCategory.PLATE_LOADED -> {
                // Guide rails if Smith
                if (equipmentCategory == EquipmentCategory.SMITH) {
                    val railStroke = (w * 0.04f).coerceAtLeast(1.5f)
                    drawLine(
                        color = tint.copy(alpha = 0.35f),
                        start = androidx.compose.ui.geometry.Offset(w * 0.14f, h * 0.10f),
                        end = androidx.compose.ui.geometry.Offset(w * 0.14f, h * 0.90f),
                        strokeWidth = railStroke
                    )
                    drawLine(
                        color = tint.copy(alpha = 0.35f),
                        start = androidx.compose.ui.geometry.Offset(w * 0.86f, h * 0.10f),
                        end = androidx.compose.ui.geometry.Offset(w * 0.86f, h * 0.90f),
                        strokeWidth = railStroke
                    )
                }

                // 1. Long, thin Olympic barbell shaft spanning edge-to-edge
                val shaftStroke = (h * 0.075f).coerceAtLeast(2f)
                drawLine(
                    color = tint,
                    start = androidx.compose.ui.geometry.Offset(w * 0.02f, cy),
                    end = androidx.compose.ui.geometry.Offset(w * 0.98f, cy),
                    strokeWidth = shaftStroke,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                )

                // 2. Left & right Olympic plates at the outer ends (leaving a wide central shaft)
                val plateW = (w * 0.065f).coerceAtLeast(2.2f)
                val tallPlateH = h * 0.68f
                val midPlateH = h * 0.48f

                // Left inner tall plate
                drawRoundRect(
                    color = tint,
                    topLeft = androidx.compose.ui.geometry.Offset(w * 0.15f, cy - tallPlateH / 2f),
                    size = androidx.compose.ui.geometry.Size(plateW, tallPlateH),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(plateW * 0.4f)
                )
                // Left outer medium plate
                drawRoundRect(
                    color = tint.copy(alpha = 0.85f),
                    topLeft = androidx.compose.ui.geometry.Offset(w * 0.07f, cy - midPlateH / 2f),
                    size = androidx.compose.ui.geometry.Size(plateW, midPlateH),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(plateW * 0.4f)
                )

                // Right inner tall plate
                drawRoundRect(
                    color = tint,
                    topLeft = androidx.compose.ui.geometry.Offset(w * 0.85f - plateW, cy - tallPlateH / 2f),
                    size = androidx.compose.ui.geometry.Size(plateW, tallPlateH),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(plateW * 0.4f)
                )
                // Right outer medium plate
                drawRoundRect(
                    color = tint.copy(alpha = 0.85f),
                    topLeft = androidx.compose.ui.geometry.Offset(w * 0.93f - plateW, cy - midPlateH / 2f),
                    size = androidx.compose.ui.geometry.Size(plateW, midPlateH),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(plateW * 0.4f)
                )
            }

            EquipmentCategory.DUMBBELL -> {
                // Short compact dumbbell with thick dual heads and short central handle
                val handleStroke = (h * 0.12f).coerceAtLeast(2.5f)
                drawLine(
                    color = tint.copy(alpha = 0.9f),
                    start = androidx.compose.ui.geometry.Offset(w * 0.32f, cy),
                    end = androidx.compose.ui.geometry.Offset(w * 0.68f, cy),
                    strokeWidth = handleStroke,
                    cap = androidx.compose.ui.graphics.StrokeCap.Butt
                )
                val headW = (w * 0.12f).coerceAtLeast(3f)
                val innerHeadH = h * 0.60f
                val outerHeadH = h * 0.48f

                // Left heads
                drawRoundRect(
                    color = tint,
                    topLeft = androidx.compose.ui.geometry.Offset(w * 0.22f, cy - innerHeadH / 2f),
                    size = androidx.compose.ui.geometry.Size(headW, innerHeadH),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(headW * 0.35f)
                )
                drawRoundRect(
                    color = tint.copy(alpha = 0.85f),
                    topLeft = androidx.compose.ui.geometry.Offset(w * 0.11f, cy - outerHeadH / 2f),
                    size = androidx.compose.ui.geometry.Size(headW * 0.85f, outerHeadH),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(headW * 0.35f)
                )
                // Right heads
                drawRoundRect(
                    color = tint,
                    topLeft = androidx.compose.ui.geometry.Offset(w * 0.78f - headW, cy - innerHeadH / 2f),
                    size = androidx.compose.ui.geometry.Size(headW, innerHeadH),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(headW * 0.35f)
                )
                drawRoundRect(
                    color = tint.copy(alpha = 0.85f),
                    topLeft = androidx.compose.ui.geometry.Offset(w * 0.89f - headW * 0.85f, cy - outerHeadH / 2f),
                    size = androidx.compose.ui.geometry.Size(headW * 0.85f, outerHeadH),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(headW * 0.35f)
                )
            }

            EquipmentCategory.MACHINE, EquipmentCategory.CABLE, EquipmentCategory.BENCH -> {
                // Pulley wheel at top + vertical cable + 3 weight stack plates
                val stroke = (w * 0.075f).coerceAtLeast(2f)
                val pulleyRadius = w * 0.13f
                val pulleyCenter = androidx.compose.ui.geometry.Offset(w * 0.5f, h * 0.22f)
                drawCircle(
                    color = tint,
                    radius = pulleyRadius,
                    center = pulleyCenter,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke)
                )
                // Cable line down into stack
                drawLine(
                    color = tint,
                    start = androidx.compose.ui.geometry.Offset(w * 0.5f, h * 0.35f),
                    end = androidx.compose.ui.geometry.Offset(w * 0.5f, h * 0.86f),
                    strokeWidth = stroke * 0.85f
                )
                // 3 horizontal weight plates
                val plateW = w * 0.56f
                val plateH = h * 0.11f
                val leftX = (w - plateW) / 2f
                listOf(h * 0.46f, h * 0.61f, h * 0.76f).forEachIndexed { idx, topY ->
                    drawRoundRect(
                        color = if (idx < 2) tint else tint.copy(alpha = 0.55f),
                        topLeft = androidx.compose.ui.geometry.Offset(leftX, topY),
                        size = androidx.compose.ui.geometry.Size(plateW, plateH),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(plateH * 0.4f)
                    )
                }
            }

            EquipmentCategory.BODYWEIGHT, EquipmentCategory.GENERIC -> {
                // Calisthenics pull-up bar frame + hanging athlete silhouette
                val stroke = (w * 0.08f).coerceAtLeast(2f)
                // Left & right posts
                drawLine(
                    color = tint.copy(alpha = 0.7f),
                    start = androidx.compose.ui.geometry.Offset(w * 0.16f, h * 0.16f),
                    end = androidx.compose.ui.geometry.Offset(w * 0.16f, h * 0.88f),
                    strokeWidth = stroke,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                )
                drawLine(
                    color = tint.copy(alpha = 0.7f),
                    start = androidx.compose.ui.geometry.Offset(w * 0.84f, h * 0.16f),
                    end = androidx.compose.ui.geometry.Offset(w * 0.84f, h * 0.88f),
                    strokeWidth = stroke,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                )
                // Top horizontal pull-up bar
                drawLine(
                    color = tint,
                    start = androidx.compose.ui.geometry.Offset(w * 0.10f, h * 0.18f),
                    end = androidx.compose.ui.geometry.Offset(w * 0.90f, h * 0.18f),
                    strokeWidth = stroke * 1.15f,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                )
                // Head & arms in V-pull-up position
                drawCircle(
                    color = tint,
                    radius = w * 0.09f,
                    center = androidx.compose.ui.geometry.Offset(w * 0.5f, h * 0.42f)
                )
                drawLine(
                    color = tint,
                    start = androidx.compose.ui.geometry.Offset(w * 0.32f, h * 0.18f),
                    end = androidx.compose.ui.geometry.Offset(w * 0.5f, h * 0.56f),
                    strokeWidth = stroke * 0.9f,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                )
                drawLine(
                    color = tint,
                    start = androidx.compose.ui.geometry.Offset(w * 0.68f, h * 0.18f),
                    end = androidx.compose.ui.geometry.Offset(w * 0.5f, h * 0.56f),
                    strokeWidth = stroke * 0.9f,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                )
                drawLine(
                    color = tint,
                    start = androidx.compose.ui.geometry.Offset(w * 0.5f, h * 0.54f),
                    end = androidx.compose.ui.geometry.Offset(w * 0.5f, h * 0.84f),
                    strokeWidth = stroke,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                )
            }
        }
    }
}

@Composable
fun EquipmentPlaceholder(
    equipmentCategory: EquipmentCategory,
    size: Dp = 52.dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(12.dp))
            .background(WarmCardTop)
            .border(1.dp, CopperRimLight.copy(alpha = 0.35f), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            EquipmentCategoryCanvasIcon(
                equipmentCategory = equipmentCategory,
                size = (size.value * 0.46f).dp,
                tint = Primary
            )
            if (size >= 40.dp) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = equipmentCategory.displayName,
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = (size.value * 0.16f).sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.5.sp,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * Square exercise thumbnail placeholder/image with rounded corners, confidence resolution, and equipment fallback.
 * SPRINT UX-002 Resolution Order:
 * 1. Exact illustrationId match (ex_<illustrationId>.png / drawable)
 * 2. Validated biomechanical alias from IllustrationAliases.kt (Confidence >= 70%)
 * 3. Equipment Placeholder (never muscle group placeholder!)
 * 4. Generic Placeholder
 */
@Composable
fun ExerciseThumbnail(
    category: String,
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 52.dp,
    illustrationId: String? = null,
    equipment: String? = null,
    notes: String = "",
    showZoomBadge: Boolean = true,
    enableDetailModalOnTap: Boolean = true
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val illustrationType = remember(name, category) {
        resolveExerciseIllustrationType(name, category)
    }
    val resolvedResId = remember(name, category, illustrationId) {
        resolveBiomechanicDrawableResId(
            context = context,
            exerciseName = name,
            muscleGroup = category,
            illustrationType = illustrationType,
            explicitIllustrationId = illustrationId
        )
    }
    val equipmentCategory = remember(equipment, name) { parseEquipmentCategory(equipment, name) }

    androidx.compose.runtime.LaunchedEffect(illustrationType.primaryDrawableKey, resolvedResId) {
        if (resolvedResId == 0) {
            com.example.utils.IllustrationMetricsManager.logMissingIllustration(
                exerciseName = name,
                illustrationId = illustrationId ?: illustrationType.primaryDrawableKey,
                equipment = equipmentCategory.name
            )
        }
    }

    ExerciseIllustrationThumbnail(
        exerciseName = name,
        muscleGroup = category,
        cornerRadius = if (size >= 56.dp) 16.dp else 12.dp,
        showZoomBadge = showZoomBadge,
        equipment = equipment ?: "",
        notes = notes,
        illustrationId = illustrationId,
        enableDetailModalOnTap = enableDetailModalOnTap,
        modifier = modifier.size(size)
    )
}

/**
 * Standalone Preparation Stage Card for Warm-up before main workout with Swipe-to-Skip.
 * Sprint UX-005 Redesign:
 * Phase 1: 🔥 PREPARAÇÃO (Prepare seu corpo para o primeiro exercício)
 * - Estimated time, exercise count, muscle focus
 * - Target first exercise preview with thumbnail, 1 set x reps, load
 * - Primary Action: [ INICIAR AQUECIMENTO ]
 * - Swipe to Skip gesture & "Pular aquecimento" button
 */
@Composable
fun WarmUpPreparationCard(
    warmupExercise: com.example.data.model.WorkoutExercise,
    targetExerciseName: String = "",
    estimatedTimeMins: Int = 45,
    totalExercisesCount: Int = 4,
    muscleGroupFocus: String = "Geral",
    onStartWarmUp: () -> Unit = {},
    onSkip: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var isSkipped by remember { mutableStateOf(false) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    val animatedOffsetX by animateFloatAsState(
        targetValue = if (isSkipped) -1000f else offsetX,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "warmup_swipe_anim"
    )

    AnimatedVisibility(
        visible = !isSkipped,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically() + slideOutHorizontally(targetOffsetX = { -it })
    ) {
        val firstSet = warmupExercise.sets.firstOrNull()
        val repsText = if (firstSet != null) "${firstSet.reps} reps" else "20 reps"
        val weightText = if (firstSet != null) {
            val w = firstSet.weight
            if (w % 1f == 0f) "${w.toInt()} kg" else "$w kg"
        } else "20 kg"
        val setsCount = warmupExercise.sets.size.coerceAtLeast(1)
        val displayTargetName = targetExerciseName.ifBlank { warmupExercise.name }

        Card(
            modifier = modifier
                .fillMaxWidth()
                .offset { IntOffset(animatedOffsetX.toInt(), 0) }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (offsetX < -90.dp.toPx()) {
                                isSkipped = true
                                com.example.utils.AudioSynth.playTickSound()
                                onSkip()
                            } else {
                                offsetX = 0f
                            }
                        },
                        onDragCancel = { offsetX = 0f },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            if (dragAmount < 0 || offsetX < 0) {
                                offsetX = (offsetX + dragAmount).coerceAtMost(20f)
                            }
                        }
                    )
                }
                .testTag("warmup_preparation_stage_card"),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF151922)),
            border = BorderStroke(1.5.dp, Color(0xFF00B0FF).copy(alpha = 0.55f)),
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. HEADER ROW: BADGE + SUBTITLE + SKIP BUTTON
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        brush = Brush.horizontalGradient(
                                            colors = listOf(Color(0xFF00B0FF), Color(0xFF00E5FF))
                                        )
                                    )
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                                    .testTag("badge_preparacao")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Bolt,
                                        contentDescription = null,
                                        tint = Color(0xFF06101E),
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = "PREPARAÇÃO",
                                        color = Color(0xFF06101E),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = 0.8.sp
                                    )
                                }
                            }
                        }

                        Text(
                            text = "Prepare seu corpo para o primeiro exercício.",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 18.sp
                        )
                    }

                    // Pular Aquecimento Action Surface
                    Surface(
                        onClick = {
                            isSkipped = true
                            com.example.utils.AudioSynth.playTickSound()
                            onSkip()
                        },
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xFF00B0FF).copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.40f)),
                        modifier = Modifier.testTag("pular_aquecimento_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Pular aquecimento",
                                color = Color(0xFF00E5FF),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "Pular aquecimento",
                                tint = Color(0xFF00E5FF),
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }

                // 2. SESSION METRICS SUMMARY CHIPS
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Hoje seu treino possui:",
                        color = OnSurfaceDim,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Estimated Time
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color.White.copy(alpha = 0.05f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Timer,
                                        contentDescription = null,
                                        tint = Primary,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text("$estimatedTimeMins min", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                                Text("Tempo estim.", color = OnSurfaceDim, fontSize = 9.sp)
                            }
                        }

                        // Exercises Count
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color.White.copy(alpha = 0.05f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FitnessCenter,
                                        contentDescription = null,
                                        tint = Primary,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text("$totalExercisesCount exs", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                                Text("Exercícios", color = OnSurfaceDim, fontSize = 9.sp)
                            }
                        }

                        // Muscle Group Focus
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color.White.copy(alpha = 0.05f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AccessibilityNew,
                                        contentDescription = null,
                                        tint = Primary,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(muscleGroupFocus, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Text("Foco principal", color = OnSurfaceDim, fontSize = 9.sp)
                            }
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color(0xFF00B0FF).copy(alpha = 0.14f))
                )

                // 3. TARGET EXERCISE PREVIEW
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Primeiro exercício",
                        color = Color(0xFF00E5FF),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp
                    )

                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0D0F14)),
                        border = BorderStroke(1.dp, Color(0xFF00B0FF).copy(alpha = 0.28f)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            ExerciseThumbnail(
                                category = warmupExercise.category,
                                name = displayTargetName,
                                size = 52.dp,
                                illustrationId = warmupExercise.illustrationId,
                                equipment = warmupExercise.equipment,
                                notes = warmupExercise.notes
                            )

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = displayTargetName.uppercase(),
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Black,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Color(0xFF00B0FF).copy(alpha = 0.18f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "Aquecimento",
                                            color = Color(0xFF00E5FF),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    Text(
                                        text = "$setsCount série  ×  $repsText  •  $weightText",
                                        color = OnSurfaceDim,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }

                // 4. PRIMARY BUTTON: [ INICIAR AQUECIMENTO ]
                Button(
                    onClick = {
                        com.example.utils.AudioSynth.playTickSound()
                        onStartWarmUp()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent,
                        contentColor = Color(0xFF06101E)
                    ),
                    contentPadding = PaddingValues(0.dp),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(Color(0xFF00B0FF), Color(0xFF00E5FF))
                            )
                        )
                        .testTag("iniciar_aquecimento_button")
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color(0xFF06101E),
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "INICIAR AQUECIMENTO",
                            color = Color(0xFF06101E),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp
                        )
                    }
                }

                // 5. SWIPE HINT
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.35f),
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Deslize para pular aquecimento",
                        color = Color.White.copy(alpha = 0.35f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

/**
 * Exercise list item card with distinct visual state styling and square thumbnail.
 * Structure: [Thumbnail] | Nome do exercício | Séries × Repetições | Carga
 * Visual States:
 * - Preparação
 * - ▶ Exercício Atual
 * - Próximos Exercícios
 * - Exercícios Concluídos
 */
@Composable
fun ExerciseCardWithState(
    exercise: com.example.data.model.WorkoutExercise,
    index: Int,
    isCurrent: Boolean,
    isCompleted: Boolean,
    isUpcoming: Boolean,
    modifier: Modifier = Modifier,
    adjustmentText: String? = null,
    onClick: (() -> Unit)? = null,
    onOverflowClick: (() -> Unit)? = null
) {
    val completedSetsCount = exercise.sets.count { it.completed }
    val totalSetsCount = exercise.sets.size
    val isFullyCompleted = totalSetsCount > 0 && completedSetsCount == totalSetsCount

    val isWarmup = exercise.blockType.equals("Aquecimento", ignoreCase = true) || exercise.name.contains("Aquecimento", ignoreCase = true)

    val containerColor = when {
        isWarmup -> Color(0xFF151922)
        isFullyCompleted -> Color(0xFF141614)
        isCurrent -> Primary.copy(alpha = 0.08f)
        else -> Surface
    }

    val borderColor = when {
        isWarmup -> Color(0xFF00B0FF).copy(alpha = 0.45f)
        isFullyCompleted -> Success.copy(alpha = 0.35f)
        isCurrent -> Primary
        else -> Color.White.copy(alpha = 0.06f)
    }

    val borderWidth = if (isCurrent || isWarmup) 1.5.dp else 1.dp

    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(borderWidth, borderColor),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .testTag("exercise_card_item_${exercise.exerciseId}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // [Thumbnail] on left with lupinha (zoom badge & modal on tap)
            ExerciseThumbnail(
                category = exercise.category,
                name = exercise.name,
                size = 52.dp,
                illustrationId = exercise.illustrationId,
                equipment = exercise.equipment,
                notes = exercise.notes
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                // Title and State Badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val dynamicFontSize = if (exercise.name.length > 28) 11.5.sp else if (exercise.name.length > 20) 12.sp else 13.sp
                    val dynamicLineHeight = if (exercise.name.length > 20) 15.sp else 16.sp
                    Text(
                        text = exercise.name.uppercase(),
                        color = if (isCompleted) Color.White.copy(alpha = 0.7f) else Color.White,
                        fontSize = dynamicFontSize,
                        lineHeight = dynamicLineHeight,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.3.sp,
                        maxLines = 2,
                        softWrap = true,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        when {
                            isWarmup -> {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFF00B0FF).copy(alpha = 0.18f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "PREPARAÇÃO",
                                        color = Color(0xFF00E5FF),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                            }
                            isFullyCompleted || isCompleted -> {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Success.copy(alpha = 0.15f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Icon(
                                        imageVector = androidx.compose.material.icons.Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = Success,
                                        modifier = Modifier.size(10.dp)
                                    )
                                    Text(
                                        text = "CONCLUÍDO",
                                        color = Success,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            completedSetsCount > 0 -> {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Success.copy(alpha = 0.2f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "✓ $completedSetsCount/$totalSetsCount SÉRIES",
                                        color = Success,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            isCurrent -> {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Primary)
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "EM EXECUÇÃO",
                                        color = Color.Black,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                            }
                            else -> {
                                Text(
                                    text = "#$index",
                                    color = OnSurfaceDim.copy(alpha = 0.6f),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        if (onOverflowClick != null) {
                            IconButton(
                                onClick = onOverflowClick,
                                modifier = Modifier
                                    .size(28.dp)
                                    .testTag("exercise_overflow_button_${exercise.exerciseId}")
                            ) {
                                Icon(
                                    imageVector = androidx.compose.material.icons.Icons.Default.MoreVert,
                                    contentDescription = "Opções do exercício",
                                    tint = Color.White.copy(alpha = 0.8f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                val firstSet = exercise.sets.firstOrNull()
                val repsText = if (firstSet != null) "${firstSet.reps} reps" else "10 reps"
                val eqCat = parseEquipmentCategory(exercise.equipment, exercise.name)
                val weightText = if (firstSet != null) {
                    val w = firstSet.weight
                    val formatted = if (w % 1f == 0f) "${w.toInt()} kg" else "$w kg"
                    if (eqCat == EquipmentCategory.DUMBBELL && w > 0f) "$formatted (cada)" else formatted
                } else "0 kg"

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${exercise.sets.size} séries • $repsText • RPE 8",
                        color = if (isCompleted) OnSurfaceDim.copy(alpha = 0.65f) else Color(0xFFE2E8F0),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                    )

                    Text(
                        text = weightText,
                        color = if (isCompleted) Primary.copy(alpha = 0.7f) else Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        softWrap = false
                    )
                }

                if (adjustmentText != null && !isCompleted) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.Default.Info,
                            contentDescription = null,
                            tint = Primary,
                            modifier = Modifier.size(10.dp)
                        )
                        Text(
                            text = adjustmentText,
                            color = Primary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

/**
 * Build #52 (v5.2) — Smart Mode Switcher (Seletor Inteligente por Pílulas / Chips).
 * Substitui o antigo card gigante de alerta por uma barra de seleção horizontal minimalista
 * com 1 linha sutil de contexto em âmbar suave (12sp), suporte a dispensar (Swipe ou botão ✕)
 * e duas pílulas interativas:
 * - Chip 1: [ Descanso Ativo • 15m ] (com destaque âmbar "Sugerido")
 * - Chip 2: [ Manter Treino do Dia ]
 */
@Composable
fun SmartFatigueModeSwitcher(
    advisory: com.example.services.recommendation.FatigueAdvisoryState,
    onAcceptRest: () -> Unit,
    onOverrideTrain: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val todayKey = androidx.compose.runtime.remember {
        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
    }
    val prefs = androidx.compose.runtime.remember(context) {
        context.getSharedPreferences("fatigue_banner_prefs", android.content.Context.MODE_PRIVATE)
    }
    var isDismissedToday by androidx.compose.runtime.remember(todayKey) {
        androidx.compose.runtime.mutableStateOf(prefs.getString("dismissed_fatigue_date", "") == todayKey)
    }
    var dragOffsetX by androidx.compose.runtime.remember { mutableFloatStateOf(0f) }

    val dismissBanner = {
        isDismissedToday = true
        prefs.edit().putString("dismissed_fatigue_date", todayKey).apply()
    }

    val isRestActive = advisory.isRestDayAccepted
    val isTrainActive = !isRestActive
    val daysCount = advisory.consecutiveDays.coerceAtLeast(3)

    AnimatedVisibility(
        visible = !isDismissedToday,
        exit = fadeOut() + shrinkVertically()
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .offset { androidx.compose.ui.unit.IntOffset(dragOffsetX.toInt(), 0) }
                .pointerInput(todayKey) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (kotlin.math.abs(dragOffsetX) > 100f) {
                                dismissBanner()
                            } else {
                                dragOffsetX = 0f
                            }
                        },
                        onDragCancel = {
                            dragOffsetX = 0f
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            dragOffsetX += dragAmount
                        }
                    )
                }
                .testTag("proactive_fatigue_advisory_banner"),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Linha sutil de contexto (12sp, cor âmbar suave) + Botão Fechar (✕) à direita
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Bolt,
                    contentDescription = null,
                    tint = Color(0xFFFFCA28),
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = "$daysCount dias fortes seguidos (${advisory.sequenceFormatted}) • Supercompensação sugerida",
                    color = Color(0xFFFFCA28).copy(alpha = 0.92f),
                    fontSize = 12.sp,
                    lineHeight = 15.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { dismissBanner() },
                    modifier = Modifier
                        .size(22.dp)
                        .testTag("btn_dismiss_fatigue_banner")
                ) {
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.Default.Close,
                        contentDescription = "Dispensar aviso de sobrecarga",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            // Seletor Compacto de Modo do Dia (2 Pílulas / Chips sem truncamento)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Chip 1: [ Descanso • 15m ] (Destaque com borda/indicador âmbar de "Sugerido")
                Surface(
                    onClick = onAcceptRest,
                    shape = RoundedCornerShape(50),
                    color = if (isRestActive) {
                        Color(0xFFFFB300).copy(alpha = 0.22f)
                    } else {
                        Color(0xFF1F1A12)
                    },
                    border = BorderStroke(
                        width = if (isRestActive) 1.5.dp else 1.dp,
                        color = if (isRestActive) Color(0xFFFFB300) else Color(0xFFFFB300).copy(alpha = 0.65f)
                    ),
                    modifier = Modifier
                        .weight(1.06f)
                        .height(38.dp)
                        .testTag("btn_accept_rest_mobility")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (isRestActive) Color(0xFF10B981) else Color(0xFFFFB300))
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "Descanso • 15m",
                            color = if (isRestActive) Color.White else Color(0xFFFFE082),
                            fontSize = 11.sp,
                            fontWeight = if (isRestActive) FontWeight.Bold else FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Surface(
                            color = Color(0xFFFFB300).copy(alpha = if (isRestActive) 0.28f else 0.18f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "Sugerido",
                                color = Color(0xFFFFCA28),
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }

                // Chip 2: [ Manter Treino ]
                Surface(
                    onClick = onOverrideTrain,
                    shape = RoundedCornerShape(50),
                    color = if (isTrainActive) {
                        WarmPillContainer
                    } else {
                        WarmCardTop
                    },
                    border = BorderStroke(
                        width = if (isTrainActive) 1.2.dp else 1.dp,
                        brush = Brush.linearGradient(
                            colors = if (isTrainActive) {
                                listOf(
                                    CopperRimLight.copy(alpha = 0.45f),
                                    CopperRimLight.copy(alpha = 0.14f)
                                )
                            } else {
                                listOf(
                                    CopperRimLight.copy(alpha = 0.24f),
                                    CopperRimLight.copy(alpha = 0.04f)
                                )
                            }
                        )
                    ),
                    modifier = Modifier
                        .weight(0.94f)
                        .height(38.dp)
                        .testTag("btn_override_train_anyway")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        if (isTrainActive) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(Primary)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                        }
                        Text(
                            text = "Manter Treino",
                            color = if (isTrainActive) Color.White else WarmSandText,
                            fontSize = 11.sp,
                            fontWeight = if (isTrainActive) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DedicatedRecoveryHeroCard(
    advisory: com.example.services.recommendation.FatigueAdvisoryState,
    onAcceptRest: () -> Unit,
    onOverrideTrain: () -> Unit,
    modifier: Modifier = Modifier
) {
    SmartFatigueModeSwitcher(
        advisory = advisory,
        onAcceptRest = onAcceptRest,
        onOverrideTrain = onOverrideTrain,
        modifier = modifier
    )
}

@Composable
fun ProactiveFatigueAdvisoryBanner(
    advisory: com.example.services.recommendation.FatigueAdvisoryState,
    onAcceptRest: () -> Unit,
    onOverrideTrain: () -> Unit,
    onResetSimulation: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (!advisory.isTriggered) return
    SmartFatigueModeSwitcher(
        advisory = advisory,
        onAcceptRest = onAcceptRest,
        onOverrideTrain = onOverrideTrain,
        modifier = modifier
    )
}


