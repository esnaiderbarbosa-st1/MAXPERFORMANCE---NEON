package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.focus.onFocusChanged
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.constants.TemplatesAndExercises
import com.example.data.model.Exercise
import com.example.data.model.PeriodizedWorkoutCardData
import com.example.data.model.CalendarDaySchedule
import com.example.data.model.SetType
import com.example.data.model.Workout
import com.example.data.model.WorkoutExercise
import com.example.data.model.WorkoutSet
import com.example.ui.theme.*
import java.util.Locale

/**
 * 6. CABEÇALHO FLOW (Minimalist Header for Active Workout)
 * Showing ONLY:
 * - Back button
 * - Workout Name
 * - Progress indicator ("Exercício X de Y")
 * - Finish/Pause action button
 */
@Composable
fun FlowStateHeader(
    workoutName: String,
    currentIndex: Int,
    totalExercises: Int,
    completedSetsCount: Int,
    onBackClick: () -> Unit,
    onFinishClick: () -> Unit,
    currentBpm: Int = 0,
    onHrClick: (() -> Unit)? = null,
    fontScale: Float = 1.0f,
    onDecreaseFontScale: (() -> Unit)? = null,
    onIncreaseFontScale: (() -> Unit)? = null,
    onResetFontScale: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    DisposableEffect(Unit) {
        onDispose {
            com.example.utils.GymAudioEngine.stopAllAndRelease(context)
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "hr_pulse_header")
    val hrPulseScale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 560, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "hr_pulse_scale"
    )

    val currentDensity = androidx.compose.ui.platform.LocalDensity.current
    CompositionLocalProvider(
        androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(
            density = currentDensity.density,
            fontScale = 1.0f
        )
    ) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Linha principal: Botão Voltar + Pílulas Simétricas Alinhadas (35.dp de altura cada) + Controle [ A- | 100% | A+ ]
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBackClick,
                modifier = Modifier
                    .size(35.dp)
                    .background(Color(0xFF151922), CircleShape)
                    .border(1.dp, Color.White.copy(alpha = 0.08f), CircleShape)
                    .testTag("flow_header_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Voltar",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }

            // 1. Pílula do Exercício: EX 1/7 em Ciano/Azul (#00B0FF / #00E5FF)
            Surface(
                color = Color(0xFF00B0FF).copy(alpha = 0.14f),
                shape = RoundedCornerShape(50),
                border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.42f)),
                modifier = Modifier.height(35.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "EX ${currentIndex + 1}/${totalExercises.coerceAtLeast(1)}",
                        color = Color(0xFF00E5FF),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.3.sp,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // 2. Pílula do Monitor Cardíaco (Clicável In-Workout)
            val isHrConnected = currentBpm > 0
            Surface(
                onClick = { onHrClick?.invoke() },
                color = if (isHrConnected) Color(0xFFFF5252).copy(alpha = 0.14f) else Color(0xFF151922),
                shape = RoundedCornerShape(50),
                border = BorderStroke(
                    1.dp,
                    if (isHrConnected) Color(0xFFFF5252).copy(alpha = 0.48f) else Color(0xFF00B0FF).copy(alpha = 0.30f)
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(35.dp)
                    .testTag("flow_header_hr_pill")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = if (isHrConnected) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = if (isHrConnected) "Frequência Cardíaca" else "Conectar Monitor Cardíaco",
                        tint = Color(0xFFFF5252),
                        modifier = Modifier
                            .size(13.dp)
                            .graphicsLayer {
                                if (isHrConnected) {
                                    scaleX = hrPulseScale
                                    scaleY = hrPulseScale
                                }
                            }
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isHrConnected) "$currentBpm BPM" else "-- bpm",
                        color = if (isHrConnected) Color.White else Color(0xFFE2E8F0),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // 3. Controle Compacto de Zoom de Fonte [ A- | 100% | A+ ]
            if (onDecreaseFontScale != null && onIncreaseFontScale != null) {
                val pct = kotlin.math.round(fontScale * 100f).toInt()
                Surface(
                    color = Color(0xFF151922),
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.35f)),
                    modifier = Modifier
                        .height(35.dp)
                        .testTag("gym_font_scale_pill")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxHeight()
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "A-",
                            color = if (fontScale > 0.86f) Color.White else Color.White.copy(alpha = 0.35f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable(enabled = fontScale > 0.86f) {
                                    com.example.utils.AudioSynth.playTickSound()
                                    onDecreaseFontScale()
                                }
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                                .testTag("gym_font_scale_decrease")
                        )
                        Text(
                            text = "$pct%",
                            color = Color(0xFF00E5FF),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable {
                                    com.example.utils.AudioSynth.playTickSound()
                                    onResetFontScale?.invoke()
                                }
                                .padding(horizontal = 3.dp, vertical = 4.dp)
                        )
                        Text(
                            text = "A+",
                            color = if (fontScale < 1.39f) Color(0xFF00E5FF) else Color.White.copy(alpha = 0.35f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable(enabled = fontScale < 1.39f) {
                                    com.example.utils.AudioSynth.playTickSound()
                                    onIncreaseFontScale()
                                }
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                                .testTag("gym_font_scale_increase")
                        )
                    }
                }
            }

            // 4. Pílula de Encerrar (Simétrica, borda #FF5252 e fundo #FF5252 com alpha 0.14f)
            Surface(
                onClick = onFinishClick,
                color = Color(0xFFFF5252).copy(alpha = 0.14f),
                shape = RoundedCornerShape(50),
                border = BorderStroke(1.dp, Color(0xFFFF5252).copy(alpha = 0.55f)),
                modifier = Modifier
                    .height(35.dp)
                    .testTag("flow_header_finish_button")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.StopCircle,
                        contentDescription = null,
                        tint = Color(0xFFFF5252),
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Encerrar",
                        color = Color(0xFFFF5252),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
    }
}

/**
 * 1. CARD DE AQUECIMENTO (Warmup Block Card)
 * Rendered as a separate distinct block ABOVE current exercise card if warmup block is present.
 */
@Composable
fun FlowWarmupCard(
    warmupExerciseName: String,
    setsInfo: String,
    onSkipWarmup: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isDismissed by remember { mutableStateOf(false) }

    if (isDismissed) return

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0E1D2F)),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Color(0xFF00B0FF).copy(alpha = 0.6f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF00B0FF))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocalFireDepartment,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "AQUECIMENTO",
                                color = Color.Black,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }

                    Text(
                        text = warmupExerciseName,
                        color = Color.White,
                        fontSize = if (warmupExerciseName.length > 26) 12.sp else 13.sp,
                        lineHeight = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        softWrap = true,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Surface(
                    onClick = {
                        isDismissed = true
                        onSkipWarmup()
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, Color(0xFF29B6F6).copy(alpha = 0.4f)),
                    modifier = Modifier.testTag("skip_warmup_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Ignorar",
                            color = Color(0xFF29B6F6),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Ignorar Aquecimento",
                            tint = Color(0xFF29B6F6),
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }

            Text(
                text = setsInfo,
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * 1 & 4. CARD DO EXERCÍCIO ATUAL (Protagonist Current Exercise Card)
 * Shows:
 * - Image thumbnail/hero
 * - Exercise Name
 * - Concise Metrics: Séries, Repetições, Carga, Descanso
 * - Button `...` on top-right to trigger Exercise Replacement
 */
@Composable
fun FlowCurrentExerciseCard(
    currentExercise: WorkoutExercise,
    onOpenReplacementSheet: () -> Unit,
    modifier: Modifier = Modifier
) {
    val maxOrange = Color(0xFF00B0FF)
    var showImageDetailDialog by remember { mutableStateOf(false) }

    val topWeight = remember(currentExercise) {
        currentExercise.sets.maxOfOrNull { it.weight } ?: 0f
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = WarmCardBottom),
        border = warmSpecularRimBorder()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    // Fundo Warm Obsidian (#1C130E -> #140D09)
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(WarmCardTop, WarmCardBottom)
                        )
                    )
                    // Ambient Copper Glow radial no topo (MaxOrange 0.14f)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                maxOrange.copy(alpha = 0.14f),
                                Color.Transparent
                            ),
                            center = Offset(size.width * 0.45f, 0f),
                            radius = size.width * 0.75f
                        ),
                        radius = size.width * 0.75f,
                        center = Offset(size.width * 0.45f, 0f)
                    )
                }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Miniatura 3D/Soft-Glow à esquerda (68.dp x 68.dp, RoundedCornerShape(16.dp))
                ExerciseIllustrationThumbnail(
                    exerciseName = currentExercise.name,
                    muscleGroup = currentExercise.category,
                    cornerRadius = 16.dp,
                    showZoomBadge = true,
                    equipment = currentExercise.equipment,
                    notes = currentExercise.notes,
                    illustrationId = currentExercise.illustrationId,
                    onClick = {
                        showImageDetailDialog = true
                    },
                    modifier = Modifier.size(68.dp)
                )

                // Coluna de informações à direita (no mesmo card)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val context = androidx.compose.ui.platform.LocalContext.current
                    val eqConfigVer by com.example.utils.WeightCalculator.equipmentConfigVersion.collectAsState()
                    val cleanEquipmentBadge = remember(currentExercise.name, currentExercise.equipment, eqConfigVer) {
                        com.example.utils.WeightCalculator.formatCleanEquipmentBadgeLabel(
                            context = context,
                            exerciseName = currentExercise.name,
                            fallbackEquipment = currentExercise.equipment
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            val dynamicFontSize = if (currentExercise.name.length > 30) 12.5.sp else if (currentExercise.name.length > 22) 13.5.sp else 15.sp
                            val dynamicLineHeight = if (currentExercise.name.length > 22) 16.sp else 18.sp
                            Text(
                                text = currentExercise.name,
                                color = Color.White,
                                fontSize = dynamicFontSize,
                                lineHeight = dynamicLineHeight,
                                fontWeight = FontWeight.Black,
                                maxLines = 2,
                                softWrap = true,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        IconButton(
                            onClick = onOpenReplacementSheet,
                            modifier = Modifier
                                .size(26.dp)
                                .testTag("exercise_replacement_trigger_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Opções do Exercício",
                                tint = WarmSandText,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // Meta prescrita em alto contraste (14.5sp SemiBold)
                    val setsCount = currentExercise.sets.size.coerceAtLeast(1)
                    val repsSummary = remember(currentExercise.sets) {
                        val distinctReps = currentExercise.sets.map { it.reps }.distinct()
                        when {
                            distinctReps.size == 1 -> "${distinctReps.first()} reps"
                            distinctReps.isNotEmpty() -> "${distinctReps.minOrNull()}-${distinctReps.maxOrNull()} reps"
                            else -> "8-12 reps"
                        }
                    }
                    val weightSummary = remember(topWeight, currentExercise.name, currentExercise.equipment) {
                        when {
                            topWeight <= 0f -> "Peso Corporal"
                            topWeight % 1f == 0f -> "${topWeight.toInt()} kg"
                            else -> "${String.format(java.util.Locale.US, "%.1f", topWeight).replace('.', ',')} kg"
                        }
                    }
                    Text(
                        text = "$setsCount séries • $repsSummary • $weightSummary • RPE 8",
                        color = Color(0xFFF1F5F9),
                        fontSize = 13.5.sp,
                        lineHeight = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        softWrap = true,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("active_exercise_prescribed_target")
                    )

                    // Linha inline: Tag do Grupo Muscular + Categoria Limpa do Equipamento + GymEquipmentVisualizer
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            color = Color(0xFF00B0FF).copy(alpha = 0.16f),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(0.5.dp, Color(0xFF29B6F6).copy(alpha = 0.45f))
                        ) {
                            Text(
                                text = currentExercise.category.uppercase(),
                                color = Color(0xFF29B6F6),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        Surface(
                            color = Color(0xFF151922),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(0.5.dp, Color(0xFF00E5FF).copy(alpha = 0.35f))
                        ) {
                            Text(
                                text = cleanEquipmentBadge,
                                color = Color(0xFF00E5FF),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        Box(modifier = Modifier.height(26.dp)) {
                            GymEquipmentVisualizer(
                                weight = topWeight,
                                equipment = currentExercise.equipment,
                                exerciseName = currentExercise.name
                            )
                        }
                    }
                }
            }
        }
    }

    if (showImageDetailDialog) {
        ExerciseIllustrationDetailDialog(
            exerciseName = currentExercise.name,
            muscleGroup = currentExercise.category,
            equipment = currentExercise.equipment,
            notes = currentExercise.notes,
            illustrationId = currentExercise.illustrationId,
            onDismiss = { showImageDetailDialog = false }
        )
    }
}

/**
 * 2. PRÓXIMO EXERCÍCIO CARD ("A seguir" Card)
 * Positioned below current exercise. Shows miniature, name, and equipment.
 */
@Composable
fun FlowNextExerciseCard(
    nextExercise: WorkoutExercise?,
    modifier: Modifier = Modifier
) {
    if (nextExercise == null) {
        // Last exercise in workout feedback card
        Card(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF192419)),
            border = BorderStroke(1.dp, Color(0xFF00E676).copy(alpha = 0.5f)),
            shape = RoundedCornerShape(14.dp)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFF00E676),
                    modifier = Modifier.size(24.dp)
                )
                Column {
                    Text(
                        text = "ÚLTIMO EXERCÍCIO DA SESSÃO!",
                        color = Color(0xFF00E676),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "Dê o seu máximo para finalizar com chave de ouro.",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 11.sp
                    )
                }
            }
        }
        return
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = WarmCardBottom),
        border = warmSpecularRimBorder(),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.SkipNext,
                    contentDescription = null,
                    tint = Primary,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "A SEGUIR",
                    color = Primary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ExerciseIllustrationThumbnail(
                    exerciseName = nextExercise.name,
                    muscleGroup = nextExercise.category,
                    cornerRadius = 12.dp,
                    showZoomBadge = true,
                    equipment = nextExercise.equipment,
                    notes = nextExercise.notes,
                    illustrationId = nextExercise.illustrationId,
                    enableDetailModalOnTap = true,
                    modifier = Modifier.size(48.dp)
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = nextExercise.name,
                        color = Color.White,
                        fontSize = 14.sp,
                        lineHeight = 17.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Aparelho: ${nextExercise.equipment} • ${nextExercise.category}",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

/**
 * 5. DESCANSO FLOW (Rest Overlay Mode)
 * Shows when rest timer is active:
 * - Large countdown
 * - Next Exercise Preview
 * - +15s, -15s, Pular Descanso
 */
@Composable
fun FlowRestOverlay(
    restRemaining: Int,
    restTotal: Int,
    nextExercise: WorkoutExercise?,
    onModifyRest: (Int) -> Unit,
    onSkipRest: () -> Unit,
    currentBpm: Int = 0,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var livePhysicalRemaining by remember(restRemaining) {
        val physical = com.example.services.WorkoutServiceState.computeRemainingRestSeconds()
        mutableIntStateOf(if (com.example.services.WorkoutServiceState.restEndElapsedRealtime > 0L) physical else restRemaining)
    }

    // Recálculo instantâneo no exato milissegundo em que a tela é desbloqueada (ON_RESUME)
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                val justFinished = com.example.services.WorkoutServiceState.recalculateNowFromPhysicalClock()
                livePhysicalRemaining = com.example.services.WorkoutServiceState.restTimeRemaining.value
                if (justFinished) {
                    com.example.utils.GymAudioEngine.playRestFinishedAlert(context)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Sincronização contínua com SystemClock.elapsedRealtime()
    LaunchedEffect(restRemaining) {
        while (com.example.services.WorkoutServiceState.isRestTimerActive.value &&
            com.example.services.WorkoutServiceState.restEndElapsedRealtime > 0L
        ) {
            livePhysicalRemaining = com.example.services.WorkoutServiceState.computeRemainingRestSeconds()
            kotlinx.coroutines.delay(200L)
        }
    }

    val effectiveRemaining = if (com.example.services.WorkoutServiceState.restEndElapsedRealtime > 0L) {
        livePhysicalRemaining
    } else {
        restRemaining
    }

    val isFlashing = effectiveRemaining <= 5
    val infiniteTransition = rememberInfiniteTransition(label = "flashing_rest")
    val flashingColor = if (isFlashing) {
        infiniteTransition.animateColor(
            initialValue = Color(0xFFFF1744),
            targetValue = Color.White.copy(alpha = 0.3f),
            animationSpec = infiniteRepeatable(
                animation = tween(400, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "color"
        ).value
    } else {
        Primary
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1E)),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (isFlashing) Color(0xFFFF1744) else Color.White.copy(alpha = 0.08f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 3. Timer Principal (à esquerda dos pills) + BPM ao vivo de recuperação
            Row(
                modifier = Modifier.padding(end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Timer,
                    contentDescription = "Descanso",
                    tint = Primary,
                    modifier = Modifier.size(20.dp)
                )
                val remMins = (effectiveRemaining / 60).coerceAtLeast(0)
                val remSecs = (effectiveRemaining % 60).coerceAtLeast(0)
                Text(
                    text = String.format(Locale.US, "%d:%02d", remMins, remSecs),
                    color = flashingColor,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            // 2. Pills do Timer (largura igual, altura fixa 48dp, padding 8dp horiz / 12dp vert)
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { onModifyRest(-15) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2A2E)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Text(
                        text = "-15s",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Button(
                    onClick = { onModifyRest(15) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2A2E)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Text(
                        text = "+15s",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Button(
                    onClick = onSkipRest,
                    colors = ButtonDefaults.buttonColors(containerColor = Primary),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Text(
                        text = "PULAR",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

/**
 * 4. SUBSTITUIÇÃO (Exercise Substitution BottomSheet)
 * Features:
 * - Top section "RECOMENDADOS": Up to 8 exercises matching biomechanics/category/equipment
 * - Tabs: Músculos, Equipamentos, Todos
 * - Direct 1-tap substitution
 */
@OptIn(
    ExperimentalMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)
@Composable
fun FlowSubstitutionBottomSheet(
    currentExercise: WorkoutExercise,
    allExercisesList: List<Exercise>,
    onSelectExercise: (Exercise) -> Unit,
    onCreateAndReplaceCustomExercise: ((name: String, equipmentCategory: String, useLbs: Boolean) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    val bringIntoViewRequester = remember {
        androidx.compose.foundation.relocation.BringIntoViewRequester()
    }

    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(1) } // 0 = Todos, 1 = Músculos, 2 = Equipamentos
    var showCreateEquipmentForm by remember { mutableStateOf(false) }
    var customExerciseName by remember { mutableStateOf("") }
    var selectedEquipmentCategory by remember { mutableStateOf("Polia / Torre de Placas") }
    var selectedUseLbs by remember { mutableStateOf(false) }

    LaunchedEffect(showCreateEquipmentForm) {
        if (showCreateEquipmentForm) {
            runCatching { sheetState.expand() }
            kotlinx.coroutines.delay(80)
            runCatching { scrollState.animateScrollTo(0) }
            runCatching { bringIntoViewRequester.bringIntoView() }
        }
    }

    val muscleGroupEquivalences = remember {
        mapOf(
            "peito" to listOf("peito", "peitoral"),
            "costas" to listOf("costas", "dorsais", "dorsal", "lombar"),
            "ombros" to listOf("ombros", "ombro", "deltoides", "deltoide", "trapézio", "trapezio"),
            "braços" to listOf("braços", "bracos", "bíceps", "biceps", "tríceps", "triceps", "antebraço", "antebraco"),
            "bíceps" to listOf("bíceps", "biceps", "braços", "bracos"),
            "tríceps" to listOf("tríceps", "triceps", "braços", "bracos"),
            "pernas" to listOf("pernas", "perna", "quadríceps", "quadriceps", "isquiotibiais", "panturrilha", "panturrilhas", "glúteo", "gluteo", "glúteos", "gluteos", "coxa"),
            "glúteos" to listOf("glúteos", "gluteos", "glúteo", "gluteo", "pernas", "perna"),
            "abdômen" to listOf("abdômen", "abdomen", "abdominal", "core")
        )
    }

    // Calculate smart recommendations (up to 8 exercises), prioritizing the athlete's field-tested machines
    val recommendedList = remember(currentExercise, allExercisesList) {
        val currentCat = currentExercise.category.lowercase(Locale.ROOT)
        allExercisesList.asSequence()
            .filter { !it.name.equals(currentExercise.name, ignoreCase = true) }
            .filter { ex ->
                ex.category.lowercase(Locale.ROOT) == currentCat ||
                ex.name.lowercase(Locale.ROOT).contains(currentCat) ||
                (currentCat.contains("peito") && (ex.category.lowercase(Locale.ROOT).contains("peito") || ex.name.lowercase(Locale.ROOT).contains("supino"))) ||
                ((currentCat.contains("costas") || currentCat.contains("dorsal")) && (ex.category.lowercase(Locale.ROOT).contains("costas") || ex.name.lowercase(Locale.ROOT).contains("puxada") || ex.name.lowercase(Locale.ROOT).contains("remada"))) ||
                ((currentCat.contains("perna") || currentCat.contains("quadr") || currentCat.contains("posterior")) && (ex.category.lowercase(Locale.ROOT).contains("perna") || ex.name.lowercase(Locale.ROOT).contains("cadeira") || ex.name.lowercase(Locale.ROOT).contains("agachamento")))
            }
            .sortedByDescending { ex ->
                val n = ex.name.lowercase(Locale.ROOT)
                when {
                    n.contains("supino declinado máquina total health") || n.contains("puxada alta máquina anilhas") -> 100
                    n.contains("puxada alta na polia") || n.contains("cadeira extensora") || n.contains("cadeira flexora") -> 80
                    else -> 0
                }
            }
            .take(8)
            .toList()
    }

    val filteredList = remember(searchQuery, selectedTab, allExercisesList, currentExercise, muscleGroupEquivalences) {
        var base = allExercisesList.filter { it.name != currentExercise.name }

        if (searchQuery.isNotBlank()) {
            base = base.filter { ex ->
                com.example.utils.FuzzySearchUtils.matches(searchQuery, ex.name) ||
                com.example.utils.FuzzySearchUtils.matches(searchQuery, ex.category) ||
                com.example.utils.FuzzySearchUtils.matches(searchQuery, ex.equipment ?: "")
            }
        } else {
            when (selectedTab) {
                1 -> { // Músculos com equivalência biomecânica
                    val currentCat = currentExercise.category.lowercase(Locale.ROOT).trim()
                    val targetGroup = muscleGroupEquivalences.entries.firstOrNull { entry ->
                        entry.key == currentCat || entry.value.any { it.equals(currentCat, ignoreCase = true) }
                    }?.value ?: listOf(currentCat)

                    base = base.filter { candidate ->
                        val candidateCat = candidate.category.lowercase(Locale.ROOT).trim()
                        candidateCat == currentCat || targetGroup.any { it.equals(candidateCat, ignoreCase = true) }
                    }
                }
                2 -> { // Equipamentos
                    val eq = currentExercise.equipment.lowercase(Locale.ROOT)
                    base = base.filter { (it.equipment ?: "").lowercase(Locale.ROOT) == eq }
                }
            }
        }
        base
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Surface,
        scrimColor = Color.Black.copy(alpha = 0.7f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                . navigationBarsPadding()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Sheet Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "SUBSTITUIR EXERCÍCIO",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = "Substituindo: ${currentExercise.name}",
                        color = Primary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Fechar", tint = Color.White)
                }
            }

            // Expandable Section: + Cadastrar Novo Equipamento / Exercício
            if (onCreateAndReplaceCustomExercise != null) {
                Surface(
                    color = Color(0xFF151922),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(
                        1.dp,
                        if (showCreateEquipmentForm) Color(0xFF00E5FF) else Color(0xFF00B0FF).copy(alpha = 0.45f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .bringIntoViewRequester(bringIntoViewRequester)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min)
                                .clickable {
                                    showCreateEquipmentForm = !showCreateEquipmentForm
                                    com.example.utils.AudioSynth.playTickSound()
                                }
                                .testTag("toggle_create_custom_equipment_btn"),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = if (showCreateEquipmentForm) Icons.Default.Close else Icons.Default.Add,
                                    contentDescription = "Cadastrar Novo Equipamento",
                                    tint = Color(0xFF00E5FF),
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "+ Cadastrar Novo Equipamento / Exercício",
                                    color = Color(0xFF00E5FF),
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Black,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Surface(
                                color = Color(0xFF00B0FF).copy(alpha = 0.16f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = currentExercise.category.uppercase(),
                                    color = Color(0xFF00B0FF),
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        if (showCreateEquipmentForm) {
                            // 1. Nome do Exercício / Máquina
                            OutlinedTextField(
                                value = customExerciseName,
                                onValueChange = { customExerciseName = it },
                                placeholder = {
                                    Text(
                                        "Ex: Rosca Bíceps Máquina Matrix",
                                        color = OnSurfaceDim,
                                        fontSize = 12.sp
                                    )
                                },
                                label = {
                                    Text(
                                        "1. Nome do Exercício / Máquina",
                                        color = Color(0xFF00B0FF),
                                        fontSize = 11.sp
                                    )
                                },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF00E5FF),
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onFocusChanged { focusState ->
                                        if (focusState.isFocused) {
                                            coroutineScope.launch {
                                                kotlinx.coroutines.delay(120)
                                                runCatching { sheetState.expand() }
                                                runCatching { bringIntoViewRequester.bringIntoView() }
                                            }
                                        }
                                    }
                                    .testTag("custom_equipment_name_input")
                            )

                            // 2. Categoria do Equipamento
                            Text(
                                text = "2. Categoria do Equipamento",
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            val categories = com.example.utils.WeightCalculator.ALL_EQUIPMENT_CATEGORIES
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                categories.chunked(2).forEach { rowCats ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(IntrinsicSize.Min),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        rowCats.forEach { cat ->
                                            val isSelected = selectedEquipmentCategory == cat
                                            Surface(
                                                color = if (isSelected) Color(0xFF00B0FF).copy(alpha = 0.22f) else Color(0xFF0D0F14),
                                                shape = RoundedCornerShape(8.dp),
                                                border = BorderStroke(
                                                    1.dp,
                                                    if (isSelected) Color(0xFF00E5FF) else Color.White.copy(alpha = 0.12f)
                                                ),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .fillMaxHeight()
                                                    .clickable {
                                                        selectedEquipmentCategory = cat
                                                        com.example.utils.AudioSynth.playTickSound()
                                                    }
                                            ) {
                                                Box(
                                                    contentAlignment = Alignment.Center,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp)
                                                ) {
                                                    Text(
                                                        text = cat,
                                                        color = if (isSelected) Color(0xFF00E5FF) else Color.White.copy(alpha = 0.8f),
                                                        fontSize = 10.5.sp,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                        maxLines = 1,
                                                        softWrap = false,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // 3. Unidade Nativa Padrão
                            Text(
                                text = "3. Unidade Nativa Padrão",
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(IntrinsicSize.Min),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(
                                    false to "Quilos (KG)",
                                    true to "Placas em Libras (LBS)"
                                ).forEach { (isLbsOption, label) ->
                                    val isSelected = selectedUseLbs == isLbsOption
                                    Surface(
                                        color = if (isSelected) Color(0xFF00E676).copy(alpha = 0.18f) else Color(0xFF0D0F14),
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(
                                            1.dp,
                                            if (isSelected) Color(0xFF00E676) else Color.White.copy(alpha = 0.12f)
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .clickable {
                                                selectedUseLbs = isLbsOption
                                                com.example.utils.AudioSynth.playTickSound()
                                            }
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                                        ) {
                                            Text(
                                                text = label,
                                                color = if (isSelected) Color(0xFF00E676) else Color.White.copy(alpha = 0.8f),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                softWrap = false,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }

                            // Botão Salvar e Substituir Agora
                            Button(
                                onClick = {
                                    val clean = customExerciseName.trim()
                                    if (clean.isNotEmpty()) {
                                        onCreateAndReplaceCustomExercise(
                                            clean,
                                            selectedEquipmentCategory,
                                            selectedUseLbs
                                        )
                                    }
                                },
                                enabled = customExerciseName.trim().isNotEmpty(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF00B0FF),
                                    disabledContainerColor = Color.White.copy(alpha = 0.12f)
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(42.dp)
                                    .testTag("save_and_replace_custom_equipment_btn")
                            ) {
                                Text(
                                    text = "SALVAR E SUBSTITUIR AGORA",
                                    color = if (customExerciseName.trim().isNotEmpty()) Color(0xFF0D0F14) else Color.White.copy(alpha = 0.4f),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Black,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }
            }

            // Search input field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Buscar exercício por nome...", color = OnSurfaceDim, fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = Primary) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, null, tint = Color.White)
                        }
                    }
                },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Primary,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("replacement_search_field")
            )

            // Section 1: RECOMENDADOS (Horizontal Quick Chips - 8 items matching biomechanics)
            if (searchQuery.isEmpty() && recommendedList.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "Recomendados",
                            tint = Color(0xFF29B6F6),
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "RECOMENDADOS (MESMO ESTÍMULO)",
                            color = Color(0xFF29B6F6),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp
                        )
                    }

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(recommendedList) { exercise ->
                            Card(
                                modifier = Modifier
                                    .width(150.dp)
                                    .clickable { onSelectExercise(exercise) },
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E22)),
                                border = BorderStroke(1.dp, Primary.copy(alpha = 0.4f)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = exercise.name,
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = exercise.category,
                                        color = Primary,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Filter Tabs
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf("Todos", "Músculos", "Equipamentos").forEachIndexed { idx, tabTitle ->
                    val isSelected = selectedTab == idx
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedTab = idx },
                        label = { Text(tabTitle, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Primary,
                            selectedLabelColor = Color.Black,
                            containerColor = Color.White.copy(alpha = 0.05f),
                            labelColor = Color.White
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Exercise List
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(filteredList) { exercise ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectExercise(exercise) },
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1B1E)),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ExerciseIllustrationThumbnail(
                                exerciseName = exercise.name,
                                muscleGroup = exercise.category,
                                cornerRadius = 12.dp,
                                modifier = Modifier.size(44.dp)
                            )

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = exercise.name,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${exercise.category} • ${exercise.equipment ?: "Outros"}",
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontSize = 11.sp
                                )
                            }

                            Icon(
                                imageVector = Icons.Default.SwapHoriz,
                                contentDescription = "Substituir",
                                tint = Primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * HOTFIX-HOME-003 — FLOW HERO CARD FOR HOME SCREEN
 * Implements the new UX hierarchy:
 * 1. Muscle Group as primary focal point (largest font, highest contrast)
 * 2. Status Badge (🟢 Pronto / 🟡 Gerando / 🔵 Em andamento / 🏁 Concluído)
 * 3. Plan Name & Day on secondary lines
 * 4. Actual exercise count, total sets, and estimated time
 * 5. Entire card clickable with 100ms scale feedback
 * 6. Discrete "▶ Continuar" CTA on bottom right
 * 7. Clean loading / skeleton states
 */
/**
 * SPRINT UX-001 — HOME INTELIGENTE (MAX PERFORMANCE 3.0)
 * 1. Premium Hero Card with background image, dark gradient overlay, muscle group focus, recovery badge & Spotify-like CTA
 * 2. "Por Que Este Treino?" Decision explanation in human language
 * 3. "Como Seu Corpo Está Hoje" Body Status with recovery bar & metrics
 * 4. "O Max Percebeu" AI Coach Insights
 * 5. "Preparação para IA" Quick Action Chips
 */

@Composable
fun FlowHomeBentoRow3Cards(
    gymName: String,
    onGymClick: () -> Unit,
    readinessLevel: String = "Alta",
    currentBpm: Int = 0,
    fatigueAdvisory: com.example.services.recommendation.FatigueAdvisoryState? = null,
    onFatigueClick: (() -> Unit)? = null,
    hybridReadinessReport: com.example.ui.components.HybridReadinessReport? = null,
    onSwapToRegenerativeZ2: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showReadinessInfoDialog by remember { mutableStateOf(false) }

    if (showReadinessInfoDialog) {
        if (hybridReadinessReport != null) {
            HybridReadinessExecutiveDialog(
                report = hybridReadinessReport,
                onDismiss = { showReadinessInfoDialog = false },
                onSwapToRegenerativeZ2 = {
                    onSwapToRegenerativeZ2?.invoke()
                }
            )
        } else {
            AlertDialog(
                onDismissRequest = { showReadinessInfoDialog = false },
                icon = {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = Primary,
                        modifier = Modifier.size(28.dp)
                    )
                },
                title = {
                    Text(
                        text = "Raio-X de Prontidão Híbrida",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        textAlign = TextAlign.Center
                    )
                },
                text = {
                    Text(
                        text = "Calculado cruzando o impacto de membros inferiores na musculação (48h) e o desgaste cardiovascular em Z4/Z5 na corrida (48h).",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        textAlign = TextAlign.Start
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = { showReadinessInfoDialog = false }
                    ) {
                        Text(
                            text = "Entendi",
                            color = Primary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                },
                containerColor = Color(0xFF1E1E24),
                shape = RoundedCornerShape(18.dp)
            )
        }
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val isFatigueTriggered = fatigueAdvisory?.isTriggered == true
        val effectiveReadiness = when {
            hybridReadinessReport != null -> hybridReadinessReport.homeBadgeLabel
            isFatigueTriggered || readinessLevel.contains("Moderada", ignoreCase = true) -> "Moderada"
            else -> readinessLevel
        }

        // Cores de destaque contextuais sincronizadas com o estado de prontidão/fadiga
        val readinessColor = when {
            hybridReadinessReport != null -> hybridReadinessReport.accentColor
            isFatigueTriggered -> Color(0xFFFFB300)
            effectiveReadiness.lowercase().contains("moderada") || effectiveReadiness.lowercase() in listOf("média", "media") -> Color(0xFFFFB300)
            effectiveReadiness.lowercase().contains("atenção") || effectiveReadiness.lowercase() == "baixa" -> Color(0xFFFF5252)
            else -> Color(0xFF00E676) // Alta
        }

        // 1. CARD PRONTIDÃO
        Card(
            modifier = Modifier
                .weight(1f)
                .height(72.dp)
                .clickable { showReadinessInfoDialog = true }
                .testTag("bento_readiness_card"),
            colors = CardDefaults.cardColors(containerColor = WarmCardBottom),
            border = if (isFatigueTriggered || (hybridReadinessReport != null && hybridReadinessReport.scorePct < 90)) {
                BorderStroke(1.dp, readinessColor.copy(alpha = 0.45f))
            } else {
                warmSpecularRimBorder()
            },
            shape = RoundedCornerShape(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawBehind {
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(WarmCardTop, WarmCardBottom)
                            )
                        )
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    MaxOrange.copy(alpha = 0.14f),
                                    Color.Transparent
                                ),
                                center = Offset(size.width * 0.5f, 0f),
                                radius = size.width * 0.9f
                            ),
                            radius = size.width * 0.9f,
                            center = Offset(size.width * 0.5f, 0f)
                        )
                    }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.TrendingUp,
                                contentDescription = null,
                                tint = if (isFatigueTriggered) Color(0xFFFFB300) else CopperRimLight.copy(alpha = 0.75f),
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = "Prontidão",
                                color = WarmSandText,
                                fontSize = 10.sp,
                                lineHeight = 12.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (currentBpm > 0) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Favorite,
                                    contentDescription = null,
                                    tint = Color(0xFFFF4D4D),
                                    modifier = Modifier.size(10.dp)
                                )
                                Text(
                                    text = "$currentBpm",
                                    color = Color(0xFFFF4D4D),
                                    fontSize = 10.sp,
                                    lineHeight = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Text(
                        text = effectiveReadiness,
                        color = readinessColor,
                        fontSize = if (effectiveReadiness.length > 9) 12.5.sp else 14.5.sp,
                        lineHeight = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = when {
                            currentBpm > 0 -> "$currentBpm BPM ao vivo"
                            hybridReadinessReport != null -> "Raio-X Híbrido 48h"
                            isFatigueTriggered -> "Atenção ao SNC"
                            else -> "Histórico real"
                        },
                        color = if (currentBpm > 0) Color(0xFFFF4D4D) else WarmSandText.copy(alpha = 0.75f),
                        fontSize = 9.sp,
                        lineHeight = 11.sp,
                        fontWeight = if (currentBpm > 0) FontWeight.Medium else FontWeight.Normal,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // 2. CARD ACADEMIA
        val fullGym = gymName.ifBlank { "Smart Fit - Unidade Centro" }
        val (gymMain, gymSub) = remember(fullGym) {
            if (fullGym.contains("-")) {
                val p = fullGym.split("-")
                p[0].trim() to p.subList(1, p.size).joinToString("-").trim()
            } else if (fullGym.contains("–")) {
                val p = fullGym.split("–")
                p[0].trim() to p.subList(1, p.size).joinToString("–").trim()
            } else {
                fullGym to "Unidade Centro"
            }
        }

        Card(
            modifier = Modifier
                .weight(1f)
                .height(72.dp)
                .clickable { onGymClick() },
            colors = CardDefaults.cardColors(containerColor = WarmCardBottom),
            border = warmSpecularRimBorder(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawBehind {
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(WarmCardTop, WarmCardBottom)
                            )
                        )
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    MaxOrange.copy(alpha = 0.14f),
                                    Color.Transparent
                                ),
                                center = Offset(size.width * 0.5f, 0f),
                                radius = size.width * 0.9f
                            ),
                            radius = size.width * 0.9f,
                            center = Offset(size.width * 0.5f, 0f)
                        )
                    }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FitnessCenter,
                            contentDescription = null,
                            tint = CopperRimLight.copy(alpha = 0.75f),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Academia",
                            color = WarmSandText,
                            fontSize = 10.sp,
                            lineHeight = 12.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Text(
                        text = gymMain.ifBlank { "Smart Fit" },
                        color = Color.White,
                        fontSize = 15.sp,
                        lineHeight = 18.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = if (gymSub.isNotBlank()) gymSub else "Unidade Centro",
                        color = WarmSandText.copy(alpha = 0.75f),
                        fontSize = 9.sp,
                        lineHeight = 11.sp,
                        fontWeight = FontWeight.Normal,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // 3. CARD FADIGA
        val fatigueValue = when {
            fatigueAdvisory?.isRestDayAccepted == true -> "Descanso"
            isFatigueTriggered -> "Alta (${fatigueAdvisory?.consecutiveDays ?: 3}d)"
            hybridReadinessReport != null -> hybridReadinessReport.homeFatigueLabel
            effectiveReadiness.lowercase() == "baixa" -> "Alta"
            effectiveReadiness.lowercase() in listOf("média", "media", "moderada") -> "Média"
            else -> "Baixa"
        }
        val fatigueColor = when {
            fatigueAdvisory?.isRestDayAccepted == true -> Color(0xFF10B981)
            isFatigueTriggered -> Color(0xFFFFB300)
            fatigueValue.lowercase() == "baixa" -> Color(0xFF00E676)
            fatigueValue.lowercase() in listOf("média", "media", "moderada") -> Color(0xFFFFB300)
            else -> Color(0xFFFF5252)
        }
        val fatigueSub = when {
            fatigueAdvisory?.isRestDayAccepted == true -> "Ofensiva mantida"
            isFatigueTriggered -> "Recuperar SNC"
            hybridReadinessReport?.hasLowerBodySession48h == true -> "Pernas (${hybridReadinessReport.hoursSinceLowerBody ?: 12}h)"
            hybridReadinessReport?.isHighCardioWear48h == true -> "Z4/Z5 recente"
            fatigueValue.lowercase() == "baixa" -> "Pronto p/ treinar"
            fatigueValue.lowercase() in listOf("média", "media", "moderada") -> "Carga moderada"
            else -> "Descansar hoje"
        }

        Card(
            modifier = Modifier
                .weight(1f)
                .height(72.dp)
                .clickable {
                    showReadinessInfoDialog = true
                    onFatigueClick?.invoke()
                }
                .testTag("bento_fatigue_card"),
            colors = CardDefaults.cardColors(containerColor = WarmCardBottom),
            border = if (fatigueAdvisory?.isRestDayAccepted == true) {
                BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.55f))
            } else if (isFatigueTriggered || (hybridReadinessReport != null && hybridReadinessReport.scorePct < 90)) {
                BorderStroke(1.dp, fatigueColor.copy(alpha = 0.55f))
            } else {
                warmSpecularRimBorder()
            },
            shape = RoundedCornerShape(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawBehind {
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(WarmCardTop, WarmCardBottom)
                            )
                        )
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    MaxOrange.copy(alpha = 0.14f),
                                    Color.Transparent
                                ),
                                center = Offset(size.width * 0.5f, 0f),
                                radius = size.width * 0.9f
                            ),
                            radius = size.width * 0.9f,
                            center = Offset(size.width * 0.5f, 0f)
                        )
                    }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocalFireDepartment,
                            contentDescription = null,
                            tint = if (isFatigueTriggered) Color(0xFFFFB300) else CopperRimLight.copy(alpha = 0.75f),
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "Fadiga",
                            color = WarmSandText,
                            fontSize = 10.sp,
                            lineHeight = 12.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Text(
                        text = fatigueValue,
                        color = fatigueColor,
                        fontSize = 14.5.sp,
                        lineHeight = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = if (currentBpm > 0) "Sensor BLE ativo" else fatigueSub,
                        color = if (currentBpm > 0) Color(0xFF00E676) else WarmSandText.copy(alpha = 0.75f),
                        fontSize = 9.sp,
                        lineHeight = 11.sp,
                        fontWeight = FontWeight.Normal,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun FlowHomeHeroCard(
    workoutName: String,
    exercisesList: List<WorkoutExercise>,
    isSessionActive: Boolean,
    isWorkoutCompletedToday: Boolean,
    isLoading: Boolean,
    onClick: () -> Unit,
    onSwapClick: () -> Unit = {},
    onPeekClick: (() -> Unit)? = null,
    activeGymName: String = "academia ativa",
    lastStimulusDays: Int? = null,
    weeklyProgress: Pair<Int, Int> = Pair(1, 4),
    recoveryPercent: Float = 1.0f,
    badgeLabel: String = "PRÓXIMO TREINO",
    customTitle: String? = null,
    customSubtitle: String? = null,
    customDurationText: String? = null,
    advanceGymLabel: String? = null,
    onAdvanceGymClick: (() -> Unit)? = null,
    fatigueAdvisory: com.example.services.recommendation.FatigueAdvisoryState? = null,
    onAcceptRestClick: () -> Unit = {},
    onOverrideTrainClick: () -> Unit = {},
    onResetFatigueClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1.0f,
        animationSpec = tween(durationMillis = 100, easing = FastOutSlowInEasing),
        label = "hero_card_scale"
    )

    val isRunningHero = workoutName.lowercase().contains("corrida") ||
        (customTitle?.lowercase()?.contains("corrida") == true)

    val parsedData = remember(workoutName, exercisesList, customDurationText) {
        val parts = workoutName.split("-", "–", ":").map { it.trim() }
        val planName = if (parts.isNotEmpty()) parts[0] else workoutName
        val dayName = if (parts.size >= 2) parts[1] else "Dia A"
        val rawMuscleGroup = if (parts.size >= 3) parts[2] else ""

        val muscleGroup = if (rawMuscleGroup.isNotBlank()) {
            rawMuscleGroup.replace("/", ", ").replace(" + ", ", ").replace(" e ", ", ")
        } else if (exercisesList.isNotEmpty()) {
            val categories = exercisesList
                .map { it.category }
                .filter { it.isNotBlank() && !it.equals("Geral", ignoreCase = true) }
                .distinct()
            if (categories.isNotEmpty()) {
                categories.take(3).joinToString(", ")
            } else {
                "Peitoral, Ombros, Tríceps"
            }
        } else {
            "Peitoral, Ombros, Tríceps"
        }

        val actualExerciseCount = exercisesList.size
        val totalSets = exercisesList.sumOf { it.sets.size.coerceAtLeast(1) }
        
        // Duração realista calibrada: ~2.5 min por série + 4 min setup (ex: 22 séries = 59 min)
        val durationMins = if (actualExerciseCount > 0) {
            (totalSets * 2.5f + 4f).toInt().coerceAtLeast(25)
        } else {
            45
        }

        val isRunningWorkout = workoutName.lowercase().contains("corrida")
        val isMobilityWorkout = workoutName.lowercase().contains("mobilidade") || workoutName.lowercase().contains("descanso ativo")
        val imageUrl = when {
            isRunningWorkout ->
                "https://images.unsplash.com/photo-1552674605-db6ffd4facb5?q=80&w=800&auto=format&fit=crop"
            workoutName.lowercase().contains("costas") ->
                "https://images.unsplash.com/photo-1526506118085-60ce8714f8c5?q=80&w=800&auto=format&fit=crop"
            workoutName.lowercase().contains("perna") ->
                "https://images.unsplash.com/photo-1574680096145-d05b474e2155?q=80&w=800&auto=format&fit=crop"
            else ->
                "https://images.unsplash.com/photo-1581009146145-b5ef050c2e1e?q=80&w=800&auto=format&fit=crop"
        }

        val resolvedPlanName = if (isRunningWorkout) {
            "Módulo de Corrida"
        } else if (isMobilityWorkout) {
            "Mobilidade Regenerativa"
        } else if (workoutName.contains("Full Body", ignoreCase = true)) {
            workoutName
        } else if (planName.isNotBlank() && !planName.equals("Upper A", ignoreCase = true)) {
            planName
        } else {
            workoutName.ifBlank { "Treino do Dia" }
        }
        val resolvedDayName = if (isRunningWorkout) "Sessão Aeróbica" else dayName
        val resolvedMuscleGroup = when {
            isRunningWorkout -> "Cardio, Resistência & VO2 Max"
            isMobilityWorkout -> "Recuperação do SNC e Articulações"
            else -> muscleGroup
        }

        WorkoutHeroParsed(
            planName = resolvedPlanName,
            dayName = resolvedDayName,
            muscleGroup = resolvedMuscleGroup,
            actualExerciseCount = actualExerciseCount,
            totalSets = totalSets,
            timeRangeStr = customDurationText?.takeIf { it.isNotBlank() } ?: when {
                isMobilityWorkout -> "15 min"
                isRunningWorkout -> "35-45 min"
                else -> "$durationMins min"
            },
            imageUrl = imageUrl
        )
    }

    val totalSetsCount = remember(exercisesList) {
        exercisesList.sumOf { it.sets.size.coerceAtLeast(1) }
    }
    val volumeLabel = remember(totalSetsCount, isRunningHero, advanceGymLabel) {
        when {
            advanceGymLabel != null -> "Recuperação 48h"
            isRunningHero -> "Condicionamento"
            totalSetsCount > 22 -> "Volume Alto"
            totalSetsCount >= 12 -> "Volume Moderado"
            totalSetsCount > 0 -> "Volume Leve"
            else -> "Volume Moderado"
        }
    }

    val displayName = remember(customTitle, parsedData.planName, workoutName) {
        if (!customTitle.isNullOrBlank()) {
            customTitle.uppercase()
        } else {
            when {
                parsedData.planName.contains("Full Body", ignoreCase = true) || workoutName.contains("Full Body", ignoreCase = true) -> {
                    val name = if (parsedData.planName.contains("Full Body", ignoreCase = true)) parsedData.planName else workoutName
                    name.uppercase()
                }
                parsedData.planName.contains("Upper", ignoreCase = true) -> "SUPERIOR"
                parsedData.planName.contains("Push", ignoreCase = true) -> "PEITO + TRÍCEPS"
                parsedData.planName.contains("Pull", ignoreCase = true) -> "COSTAS + BÍCEPS"
                parsedData.planName.contains("Leg", ignoreCase = true) -> "PERNAS COMPLETO"
                parsedData.planName.contains("Cardio", ignoreCase = true) -> "CARDIO & RESISTÊNCIA"
                else -> parsedData.planName.uppercase().ifBlank { workoutName.uppercase() }
            }
        }
    }

    val subtitleText = remember(customSubtitle, parsedData.muscleGroup, workoutName) {
        if (!customSubtitle.isNullOrBlank()) {
            customSubtitle
        } else {
            val raw = parsedData.muscleGroup
            if (raw.isNotBlank() && !raw.contains("Upper", ignoreCase = true)) {
                raw
            } else if (workoutName.contains("Full Body", ignoreCase = true)) {
                "Corpo Inteiro (Multiarticular)"
            } else {
                "Peitoral, Ombros, Tríceps"
            }
        }
    }

    val (completedWorkouts, totalWorkouts) = weeklyProgress
    val progressFraction = if (totalWorkouts > 0) {
        (completedWorkouts.toFloat() / totalWorkouts.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val isFatigueTriggered = fatigueAdvisory?.isTriggered == true
    val wearScore = fatigueAdvisory?.accumulatedWearScore?.takeIf { it > 0 } ?: 84
    val recoveryText = when {
        isFatigueTriggered -> "Desgaste $wearScore%"
        recoveryPercent >= 0.85f -> "100% Pronto"
        recoveryPercent >= 0.50f -> "Pronto"
        else -> "Fadiga Alta"
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(20.dp))
            .clickable { onClick() }
            .testTag("personal_trainer_hero_card"),
        colors = CardDefaults.cardColors(containerColor = WarmCardBottom),
        border = if (fatigueAdvisory?.isRestDayAccepted == true) {
            BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.45f))
        } else {
            warmSpecularRimBorder()
        },
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(WarmCardTop, WarmCardBottom)
                        )
                    )
                    // Ambient Copper Glow sutil no Hero Card
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                MaxOrange.copy(alpha = 0.14f),
                                Color.Transparent
                            ),
                            center = Offset(size.width * 0.5f, size.height * 0.25f),
                            radius = size.width * 0.85f
                        ),
                        radius = size.width * 0.85f,
                        center = Offset(size.width * 0.5f, size.height * 0.25f)
                    )
                }
        ) {
            // A. ÁREA SUPERIOR IMERSIVA (IMAGEM ANATÔMICA/FITNESS + OVERLAY + BADGE + TÍTULOS + BADGES)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(165.dp)
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                    .background(WarmCardTop)
            ) {
                // Imagem de fundo
                AsyncImage(
                    model = parsedData.imageUrl,
                    contentDescription = "Ilustração do Treino",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Overlay escuro sobre a imagem + Ambient Cyan Glow no topo
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF0D0F14).copy(alpha = 0.48f))
                        .drawBehind {
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        MaxOrange.copy(alpha = 0.14f),
                                        Color.Transparent
                                    ),
                                    center = Offset(size.width * 0.4f, 0f),
                                    radius = size.width * 0.75f
                                ),
                                radius = size.width * 0.75f,
                                center = Offset(size.width * 0.4f, 0f)
                            )
                        }
                )

                // Gradiente inferior profundo para legibilidade perfeita dos títulos
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color(0xFF0D0F14).copy(alpha = 0.35f),
                                    Color(0xFF11151D).copy(alpha = 0.88f),
                                    WarmCardTop
                                ),
                                startY = 30f
                            )
                        )
                )

                // Conteúdo posicionado sobre a imagem
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 11.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Topo: Badge PRÓXIMO TREINO / PLANO FIXADO, Botão Espiar e Badge Recuperado (sem truncar)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val isMobilityHeroTop = workoutName.lowercase().contains("mobilidade") || workoutName.lowercase().contains("descanso ativo")
                        val compactBadgeLabel = if (badgeLabel.startsWith("INTERCALAÇÃO HÍBRIDA", ignoreCase = true) && badgeLabel.length > 30) {
                            "INTERCALAÇÃO HÍBRIDA • CORRIDA"
                        } else {
                            badgeLabel
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .padding(end = 6.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    when {
                                        isMobilityHeroTop -> Brush.verticalGradient(listOf(Color(0xFF10B981), Color(0xFF059669)))
                                        else -> copperVolumeGradientBrush()
                                    }
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = compactBadgeLabel,
                                color = Color.Black,
                                fontSize = 9.5.sp,
                                lineHeight = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.2.sp,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (onPeekClick != null) {
                                Surface(
                                    onClick = {
                                        com.example.utils.AudioSynth.playTickSound()
                                        onPeekClick()
                                    },
                                    color = WarmPillContainer.copy(alpha = 0.88f),
                                    border = warmSpecularRimBorder(topAlpha = 0.32f, bottomAlpha = 0.08f),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.testTag("btn_hero_peek_workout")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Visibility,
                                            contentDescription = "Espiar exercícios",
                                            tint = WarmSandText,
                                            modifier = Modifier.size(11.dp)
                                        )
                                        Text(
                                            text = "Espiar",
                                            color = Color.White,
                                            fontSize = 9.5.sp,
                                            lineHeight = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }

                            // Indicador de Recuperação / Carga Acumulada sincronizado com o estado de fadiga
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (isFatigueTriggered) Color(0xFF00E5FF).copy(alpha = 0.18f)
                                        else WarmPillContainer.copy(alpha = 0.78f)
                                    )
                                    .border(
                                        1.dp,
                                        if (isFatigueTriggered) Color(0xFF00E5FF).copy(alpha = 0.55f)
                                        else CopperRimLight.copy(alpha = 0.28f),
                                        RoundedCornerShape(12.dp)
                                    )
                                    .padding(horizontal = 7.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = recoveryText,
                                    color = if (isFatigueTriggered) Color(0xFF00E5FF) else Color.White,
                                    fontSize = 9.5.sp,
                                    lineHeight = 11.sp,
                                    fontWeight = if (isFatigueTriggered) FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    // Parte Inferior sobre a imagem: Títulos e Badges
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Título grande
                        val dynamicTitleSize = if (displayName.length > 25) 18.sp else 23.sp
                        Text(
                            text = displayName,
                            color = Color.White,
                            fontSize = dynamicTitleSize,
                            fontWeight = FontWeight.Black,
                            letterSpacing = (-0.5).sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        // Subtítulo em Slate Suave (#94A3B8) - até 2 linhas para exibir a intercalação híbrida
                        Text(
                            text = subtitleText,
                            color = WarmSandText,
                            fontSize = 12.5.sp,
                            lineHeight = 16.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        // Badges em pílulas Dark Neutras (#1E2430)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(WarmPillContainer.copy(alpha = 0.85f))
                                    .border(0.8.dp, CopperRimLight.copy(alpha = 0.22f), RoundedCornerShape(12.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = parsedData.timeRangeStr,
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    lineHeight = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(WarmPillContainer.copy(alpha = 0.85f))
                                    .border(0.8.dp, CopperRimLight.copy(alpha = 0.22f), RoundedCornerShape(12.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = volumeLabel,
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    lineHeight = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            val exercisePillText = when {
                                isRunningHero || parsedData.planName.contains("Corrida", ignoreCase = true) || workoutName.contains("Corrida", ignoreCase = true) -> "Treino Guiado GPS"
                                workoutName.lowercase().contains("mobilidade") || workoutName.lowercase().contains("descanso ativo") -> "Sessão Guiada"
                                parsedData.actualExerciseCount <= 0 -> "Treino Guiado GPS"
                                else -> "${parsedData.actualExerciseCount} exercícios"
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(WarmPillContainer.copy(alpha = 0.85f))
                                    .border(0.8.dp, CopperRimLight.copy(alpha = 0.22f), RoundedCornerShape(12.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = exercisePillText,
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    lineHeight = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            // B. SEÇÃO INFERIOR DO CARD (DARK NEUTRO & ACENTO AZUL/CIANO)
            val isRunningHeroBottom = isRunningHero
            val isMobilityHero = workoutName.lowercase().contains("mobilidade") || workoutName.lowercase().contains("descanso ativo")
            val heroButtonText = when {
                isSessionActive -> "Retomar treino"
                isMobilityHero -> "Iniciar mobilidade (15m)"
                isRunningHeroBottom -> "IR PARA CORRIDA DE HOJE"
                else -> "Iniciar treino"
            }
            val heroButtonIcon = if (isRunningHeroBottom) Icons.Default.DirectionsRun else Icons.Default.PlayArrow
            val heroButtonContainerColor = when {
                isMobilityHero -> Color(0xFF10B981)
                isRunningHeroBottom -> Color(0xFF00E5FF)
                else -> Primary
            }

            if (isRunningHeroBottom && advanceGymLabel != null && onAdvanceGymClick != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Progresso semanal: $completedWorkouts/$totalWorkouts treinos completados",
                            color = WarmSandText,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        LinearProgressIndicator(
                            progress = { progressFraction },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = Color(0xFF00E5FF),
                            trackColor = WarmPillContainer
                        )
                    }

                    // Botão Principal do Hero Card: [ IR PARA CORRIDA DE HOJE ]
                    Button(
                        onClick = onClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp)
                            .testTag("btn_hero_continuar"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF00B0FF),
                            contentColor = Color.Black
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.DirectionsRun,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "IR PARA CORRIDA DE HOJE",
                            color = Color.Black,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.4.sp,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Opção secundária discreta abaixo: [ Antecipar Full Body A para hoje ] + [ Trocar treino ]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            onClick = onAdvanceGymClick,
                            color = WarmPillContainer,
                            border = BorderStroke(1.dp, Color(0xFF29B6F6).copy(alpha = 0.35f)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .testTag("btn_hero_advance_gym")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FitnessCenter,
                                    contentDescription = null,
                                    tint = Color(0xFF29B6F6),
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = advanceGymLabel,
                                    color = Color(0xFF29B6F6),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Surface(
                            onClick = onSwapClick,
                            color = WarmPillContainer,
                            border = warmSpecularRimBorder(topAlpha = 0.30f, bottomAlpha = 0.06f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxHeight()
                                .testTag("btn_hero_trocar_treino")
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Trocar treino",
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 11.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Coluna esquerda: Progresso Semanal + Ações secundárias ("Trocar treino" + label de ação)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Text(
                                text = "Progresso semanal: $completedWorkouts/$totalWorkouts treinos completados",
                                color = WarmSandText,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            LinearProgressIndicator(
                                progress = { progressFraction },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp)),
                                color = if (fatigueAdvisory?.isRestDayAccepted == true) Color(0xFF10B981) else Primary,
                                trackColor = WarmPillContainer
                            )
                        }

                        Row(
                            modifier = Modifier.height(IntrinsicSize.Min),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                onClick = onSwapClick,
                                color = WarmPillContainer,
                                border = warmSpecularRimBorder(topAlpha = 0.30f, bottomAlpha = 0.06f),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .testTag("btn_hero_trocar_treino")
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .padding(horizontal = 10.dp, vertical = 5.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Trocar treino",
                                        color = Color.White.copy(alpha = 0.92f),
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .clickable { onClick() }
                                    .padding(vertical = 5.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = heroButtonText,
                                    color = heroButtonContainerColor,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    // Canto Inferior Direito: Botão Circular de Ação Rápida
                    val playButtonBrush = if (isMobilityHero) {
                        Brush.verticalGradient(listOf(Color(0xFF34D399), Color(0xFF059669)))
                    } else {
                        copperVolumeGradientBrush()
                    }
                    val playButtonBorder = if (isMobilityHero) {
                        BorderStroke(1.dp, Color(0xFF6EE7B7).copy(alpha = 0.55f))
                    } else {
                        copperVolumeHighlightBorder(1.dp)
                    }

                    Surface(
                        onClick = onClick,
                        shape = CircleShape,
                        color = Color.Transparent,
                        border = playButtonBorder,
                        shadowElevation = 6.dp,
                        modifier = Modifier
                            .size(54.dp)
                            .testTag("btn_hero_continuar")
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxSize()
                                .background(playButtonBrush, CircleShape)
                        ) {
                            Icon(
                                imageVector = heroButtonIcon,
                                contentDescription = heroButtonText,
                                tint = Color.Black,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * PARTE 1: LÓGICA DINÂMICA DE IMAGENS
 * Retorna o ID do recurso drawable da anatomia com brilho baseado no grupo muscular.
 */
@Composable
fun getCategoryModel(title: String, fallbackUrl: String): Any {
    val context = androidx.compose.ui.platform.LocalContext.current
    val key = title.lowercase()
        .replace("ç", "c")
        .replace("ã", "a")
        .replace("á", "a")
        .replace("é", "e")
        .replace("í", "i")
        .replace("ó", "o")
        .replace("ú", "u")

    val resName = when {
        key.contains("peito") -> "ex_supino_reto"
        key.contains("costas") -> "ex_puxada_alta"
        key.contains("perna") -> "ex_agachamento_livre"
        key.contains("ombro") -> "ex_desenvolvimento_ombros"
        key.contains("braco") || key.contains("biceps") || key.contains("triceps") -> "ex_rosca_direta"
        key.contains("recomendad") -> "ex_agachamento_livre"
        else -> ""
    }

    if (resName.isNotEmpty()) {
        val resId = context.resources.getIdentifier(resName, "drawable", context.packageName)
        if (resId != 0) return resId
    }

    return fallbackUrl
}

@Composable
fun getAnatomyModelForMuscle(muscleGroup: String): Any {
    val context = androidx.compose.ui.platform.LocalContext.current
    val resId = resolveBiomechanicDrawableResId(
        context = context,
        exerciseName = muscleGroup,
        muscleGroup = muscleGroup
    )
    return if (resId != 0) resId else com.example.R.drawable.ex_agachamento_livre
}

/**
 * 3D Male Anatomical Model Canvas component.
 * Renders a dark metallic male torso with glowing neon orange muscle fibers matching Image 1.
 */
@Composable
fun Anatomy3DHumanModelCanvas(
    muscleGroup: String,
    modifier: Modifier = Modifier
) {
    val neonOrange = Color(0xFF00B0FF)
    val brightOrange = Color(0xFF00E5FF)
    val glowingYellow = Color(0xFF80F2FF)
    val darkCharcoal = Color(0xFF22222A)
    val darkBody = Color(0xFF1B1B22)
    val darkOutline = Color(0xFF383842)

    val key = muscleGroup.lowercase()
    val isLegs = key.contains("perna") || key.contains("coxa") || key.contains("quadriceps") || key.contains("panturrilha")
    val isArms = key.contains("braço") || key.contains("braco") || key.contains("biceps") || key.contains("triceps") || key.contains("antebraço")
    val isChest = key.contains("peito") || key.contains("peitoral")
    val isGlutes = key.contains("glut") || key.contains("bumbum") || key.contains("gluteo")
    val isBackTrapsShoulders = key.contains("costa") && (key.contains("trap") || key.contains("ombro"))
    val isTrapsShoulders = (key.contains("trap") || key.contains("trapezio")) && key.contains("ombro")
    val isShouldersOnly = key.contains("ombro") || key.contains("deltoide")
    val isBackNoShoulders = key.contains("costa")

    androidx.compose.foundation.Canvas(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF0C0C0F))
    ) {
        val w = size.width
        val h = size.height

        // Background glow
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    neonOrange.copy(alpha = 0.28f),
                    Color(0xFF0C0C0F)
                ),
                center = Offset(w * 0.5f, h * 0.45f),
                radius = w * 0.75f
            )
        )

        when {
            // 1. PERNAS (LEGS) - Image 1
            isLegs -> {
                // Thighs and calves dark base
                val leftLeg = Path().apply {
                    moveTo(w * 0.48f, h * 0.15f)
                    cubicTo(w * 0.35f, h * 0.20f, w * 0.25f, h * 0.40f, w * 0.32f, h * 0.60f)
                    lineTo(w * 0.33f, h * 0.85f)
                    lineTo(w * 0.40f, h * 0.95f)
                    lineTo(w * 0.47f, h * 0.85f)
                    lineTo(w * 0.48f, h * 0.15f)
                    close()
                }
                val rightLeg = Path().apply {
                    moveTo(w * 0.52f, h * 0.15f)
                    cubicTo(w * 0.65f, h * 0.20f, w * 0.75f, h * 0.40f, w * 0.68f, h * 0.60f)
                    lineTo(w * 0.67f, h * 0.85f)
                    lineTo(w * 0.60f, h * 0.95f)
                    lineTo(w * 0.53f, h * 0.85f)
                    lineTo(w * 0.52f, h * 0.15f)
                    close()
                }
                drawPath(leftLeg, color = darkCharcoal)
                drawPath(rightLeg, color = darkCharcoal)
                drawPath(leftLeg, color = darkOutline, style = Stroke(width = 1.5f))
                drawPath(rightLeg, color = darkOutline, style = Stroke(width = 1.5f))

                // Glowing quads
                val legBrush = Brush.verticalGradient(listOf(glowingYellow, brightOrange, neonOrange))
                val leftQuad = Path().apply {
                    moveTo(w * 0.46f, h * 0.20f)
                    cubicTo(w * 0.32f, h * 0.26f, w * 0.30f, h * 0.48f, w * 0.44f, h * 0.54f)
                    close()
                }
                val rightQuad = Path().apply {
                    moveTo(w * 0.54f, h * 0.20f)
                    cubicTo(w * 0.68f, h * 0.26f, w * 0.70f, h * 0.48f, w * 0.56f, h * 0.54f)
                    close()
                }
                drawPath(leftQuad, brush = legBrush)
                drawPath(rightQuad, brush = legBrush)
                drawPath(leftQuad, color = glowingYellow, style = Stroke(width = 2f))
                drawPath(rightQuad, color = glowingYellow, style = Stroke(width = 2f))

                // Calves glow
                val leftCalf = Path().apply {
                    moveTo(w * 0.33f, h * 0.62f)
                    cubicTo(w * 0.28f, h * 0.72f, w * 0.33f, h * 0.82f, w * 0.40f, h * 0.80f)
                    close()
                }
                val rightCalf = Path().apply {
                    moveTo(w * 0.67f, h * 0.62f)
                    cubicTo(w * 0.72f, h * 0.72f, w * 0.67f, h * 0.82f, w * 0.60f, h * 0.80f)
                    close()
                }
                drawPath(leftCalf, brush = legBrush)
                drawPath(rightCalf, brush = legBrush)
            }

            // 2. BRAÇOS (ARMS) - Image 2
            isArms -> {
                val armBody = Path().apply {
                    moveTo(w * 0.70f, h * 0.10f)
                    lineTo(w * 0.30f, h * 0.22f)
                    cubicTo(w * 0.10f, h * 0.38f, w * 0.15f, h * 0.58f, w * 0.25f, h * 0.75f)
                    lineTo(w * 0.50f, h * 0.85f)
                    lineTo(w * 0.85f, h * 0.55f)
                    close()
                }
                drawPath(armBody, color = darkCharcoal)

                val armBrush = Brush.horizontalGradient(listOf(glowingYellow, brightOrange, neonOrange))
                val bicep = Path().apply {
                    moveTo(w * 0.25f, h * 0.35f)
                    cubicTo(w * 0.15f, h * 0.45f, w * 0.30f, h * 0.65f, w * 0.55f, h * 0.58f)
                    cubicTo(w * 0.65f, h * 0.45f, w * 0.45f, h * 0.30f, w * 0.25f, h * 0.35f)
                    close()
                }
                drawPath(bicep, brush = armBrush)
                drawPath(bicep, color = glowingYellow, style = Stroke(width = 2.5f))

                for (i in 1..4) {
                    val y = h * (0.38f + i * 0.05f)
                    drawLine(Color.White.copy(alpha = 0.8f), Offset(w * 0.25f, y), Offset(w * 0.52f, y - h * 0.04f), strokeWidth = 2f)
                }
            }

            // 3. GLÚTEOS (GLUTES) - Image 7
            isGlutes -> {
                val hipBody = Path().apply {
                    moveTo(w * 0.35f, h * 0.15f)
                    lineTo(w * 0.65f, h * 0.15f)
                    lineTo(w * 0.80f, h * 0.75f)
                    lineTo(w * 0.20f, h * 0.75f)
                    close()
                }
                drawPath(hipBody, color = darkCharcoal)

                val gluteBrush = Brush.radialGradient(
                    colors = listOf(glowingYellow, brightOrange, neonOrange),
                    center = Offset(w * 0.5f, h * 0.45f),
                    radius = w * 0.4f
                )
                val leftGlute = Path().apply {
                    moveTo(w * 0.48f, h * 0.22f)
                    cubicTo(w * 0.22f, h * 0.24f, w * 0.18f, h * 0.60f, w * 0.48f, h * 0.68f)
                    close()
                }
                val rightGlute = Path().apply {
                    moveTo(w * 0.52f, h * 0.22f)
                    cubicTo(w * 0.78f, h * 0.24f, w * 0.82f, h * 0.60f, w * 0.52f, h * 0.68f)
                    close()
                }
                drawPath(leftGlute, brush = gluteBrush)
                drawPath(rightGlute, brush = gluteBrush)
                drawPath(leftGlute, color = glowingYellow, style = Stroke(width = 2.5f))
                drawPath(rightGlute, color = glowingYellow, style = Stroke(width = 2.5f))
            }

            // 4, 5, 8. COSTAS / TRAPÉZIO (BACK / TRAPS) - Images 4, 5, 8
            isBackTrapsShoulders || isTrapsShoulders || isBackNoShoulders -> {
                // Head
                val headPath = Path().apply { addOval(Rect(Offset(w * 0.5f, h * 0.12f), size = Size(w * 0.22f, h * 0.16f))) }
                drawPath(headPath, color = darkCharcoal)

                // Rear Torso frame
                val backTorso = Path().apply {
                    moveTo(w * 0.39f, h * 0.24f)
                    lineTo(w * 0.22f, h * 0.28f)
                    lineTo(w * 0.12f, h * 0.52f)
                    lineTo(w * 0.30f, h * 0.88f)
                    lineTo(w * 0.70f, h * 0.88f)
                    lineTo(w * 0.88f, h * 0.52f)
                    lineTo(w * 0.78f, h * 0.28f)
                    lineTo(w * 0.61f, h * 0.24f)
                    close()
                }
                drawPath(backTorso, color = darkCharcoal)
                drawPath(backTorso, color = darkOutline, style = Stroke(width = 1.5f))

                val fireBrush = Brush.verticalGradient(listOf(glowingYellow, brightOrange, neonOrange))

                // Trapezius V-shape
                val traps = Path().apply {
                    moveTo(w * 0.50f, h * 0.22f)
                    lineTo(w * 0.32f, h * 0.28f)
                    lineTo(w * 0.50f, h * 0.55f)
                    lineTo(w * 0.68f, h * 0.28f)
                    close()
                }
                drawPath(traps, brush = fireBrush)
                drawPath(traps, color = glowingYellow, style = Stroke(width = 2f))

                // Lats (Latissimus Dorsi wings)
                if (isBackTrapsShoulders || isBackNoShoulders) {
                    val leftLat = Path().apply {
                        moveTo(w * 0.48f, h * 0.38f)
                        cubicTo(w * 0.25f, h * 0.40f, w * 0.15f, h * 0.60f, w * 0.36f, h * 0.82f)
                        lineTo(w * 0.48f, h * 0.75f)
                        close()
                    }
                    val rightLat = Path().apply {
                        moveTo(w * 0.52f, h * 0.38f)
                        cubicTo(w * 0.75f, h * 0.40f, w * 0.85f, h * 0.60f, w * 0.64f, h * 0.82f)
                        lineTo(w * 0.52f, h * 0.75f)
                        close()
                    }
                    drawPath(leftLat, brush = fireBrush)
                    drawPath(rightLat, brush = fireBrush)
                    drawPath(leftLat, color = glowingYellow, style = Stroke(width = 2f))
                    drawPath(rightLat, color = glowingYellow, style = Stroke(width = 2f))
                }

                // Shoulders / Rear Delts
                if (isBackTrapsShoulders || isTrapsShoulders) {
                    val leftRearDelt = Path().apply {
                        moveTo(w * 0.22f, h * 0.28f)
                        cubicTo(w * 0.10f, h * 0.32f, w * 0.10f, h * 0.44f, w * 0.24f, h * 0.45f)
                        close()
                    }
                    val rightRearDelt = Path().apply {
                        moveTo(w * 0.78f, h * 0.28f)
                        cubicTo(w * 0.90f, h * 0.32f, w * 0.90f, h * 0.44f, w * 0.76f, h * 0.45f)
                        close()
                    }
                    drawPath(leftRearDelt, brush = fireBrush)
                    drawPath(rightRearDelt, brush = fireBrush)
                }
            }

            // 6. SOMENTE OMBROS (SHOULDERS ONLY) - Image 6
            isShouldersOnly -> {
                // Head & Neck
                val headPath = Path().apply { addOval(Rect(Offset(w * 0.5f, h * 0.15f), size = Size(w * 0.24f, h * 0.18f))) }
                drawPath(headPath, color = darkCharcoal)

                // Torso Frame
                val torso = Path().apply {
                    moveTo(w * 0.40f, h * 0.28f)
                    lineTo(w * 0.20f, h * 0.32f)
                    lineTo(w * 0.10f, h * 0.52f)
                    lineTo(w * 0.28f, h * 0.88f)
                    lineTo(w * 0.72f, h * 0.88f)
                    lineTo(w * 0.90f, h * 0.52f)
                    lineTo(w * 0.80f, h * 0.32f)
                    lineTo(w * 0.60f, h * 0.28f)
                    close()
                }
                drawPath(torso, color = darkCharcoal)

                val shoulderBrush = Brush.verticalGradient(listOf(glowingYellow, brightOrange, neonOrange))
                val leftDelt = Path().apply {
                    moveTo(w * 0.32f, h * 0.30f)
                    cubicTo(w * 0.10f, h * 0.32f, w * 0.05f, h * 0.55f, w * 0.25f, h * 0.58f)
                    cubicTo(w * 0.30f, h * 0.48f, w * 0.34f, h * 0.38f, w * 0.32f, h * 0.30f)
                    close()
                }
                val rightDelt = Path().apply {
                    moveTo(w * 0.68f, h * 0.30f)
                    cubicTo(w * 0.90f, h * 0.32f, w * 0.95f, h * 0.55f, w * 0.75f, h * 0.58f)
                    cubicTo(w * 0.70f, h * 0.48f, w * 0.66f, h * 0.38f, w * 0.68f, h * 0.30f)
                    close()
                }
                drawPath(leftDelt, brush = shoulderBrush)
                drawPath(rightDelt, brush = shoulderBrush)
                drawPath(leftDelt, color = glowingYellow, style = Stroke(width = 2.5f))
                drawPath(rightDelt, color = glowingYellow, style = Stroke(width = 2.5f))
            }

            // 3, DEFAULT: PEITO (CHEST) - Image 3
            else -> {
                // Head & Neck
                val headPath = Path().apply { addOval(Rect(Offset(w * 0.5f, h * 0.15f), size = Size(w * 0.26f, h * 0.20f))) }
                drawPath(headPath, color = darkCharcoal)
                drawPath(headPath, color = darkOutline, style = Stroke(width = 1.5f))

                val neckPath = Path().apply {
                    moveTo(w * 0.43f, h * 0.23f)
                    lineTo(w * 0.57f, h * 0.23f)
                    lineTo(w * 0.61f, h * 0.31f)
                    lineTo(w * 0.39f, h * 0.31f)
                    close()
                }
                drawPath(neckPath, color = darkBody)

                val torsoPath = Path().apply {
                    moveTo(w * 0.39f, h * 0.31f)
                    lineTo(w * 0.22f, h * 0.34f)
                    cubicTo(w * 0.10f, h * 0.35f, w * 0.05f, h * 0.44f, w * 0.04f, h * 0.55f)
                    lineTo(w * 0.14f, h * 0.95f)
                    lineTo(w * 0.32f, h * 0.95f)
                    lineTo(w * 0.35f, h * 1.0f)
                    lineTo(w * 0.65f, h * 1.0f)
                    lineTo(w * 0.68f, h * 0.95f)
                    lineTo(w * 0.86f, h * 0.95f)
                    lineTo(w * 0.96f, h * 0.55f)
                    cubicTo(w * 0.95f, h * 0.44f, w * 0.90f, h * 0.35f, w * 0.78f, h * 0.34f)
                    lineTo(w * 0.61f, h * 0.31f)
                    close()
                }
                drawPath(torsoPath, color = darkCharcoal)
                drawPath(torsoPath, color = darkOutline, style = Stroke(width = 2f))

                // Abs contours
                val absColor = Color(0xFF16161C)
                drawLine(Color(0xFF2E2E38), Offset(w * 0.5f, h * 0.31f), Offset(w * 0.5f, h * 0.88f), strokeWidth = 3f)
                drawLine(absColor, Offset(w * 0.35f, h * 0.59f), Offset(w * 0.65f, h * 0.59f), strokeWidth = 2f)
                drawLine(absColor, Offset(w * 0.37f, h * 0.69f), Offset(w * 0.63f, h * 0.69f), strokeWidth = 2f)
                drawLine(absColor, Offset(w * 0.39f, h * 0.79f), Offset(w * 0.61f, h * 0.79f), strokeWidth = 2f)

                val leftPec = Path().apply {
                    moveTo(w * 0.49f, h * 0.35f)
                    cubicTo(w * 0.34f, h * 0.34f, w * 0.18f, h * 0.38f, w * 0.14f, h * 0.46f)
                    cubicTo(w * 0.16f, h * 0.55f, w * 0.33f, h * 0.57f, w * 0.49f, h * 0.54f)
                    close()
                }
                val rightPec = Path().apply {
                    moveTo(w * 0.51f, h * 0.35f)
                    cubicTo(w * 0.66f, h * 0.34f, w * 0.82f, h * 0.38f, w * 0.86f, h * 0.46f)
                    cubicTo(w * 0.84f, h * 0.55f, w * 0.67f, h * 0.57f, w * 0.51f, h * 0.54f)
                    close()
                }

                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(neonOrange.copy(alpha = 0.85f), brightOrange.copy(alpha = 0.35f), Color.Transparent),
                        center = Offset(w * 0.5f, h * 0.45f),
                        radius = w * 0.45f
                    ),
                    radius = w * 0.45f,
                    center = Offset(w * 0.5f, h * 0.45f)
                )

                val pecBrush = Brush.verticalGradient(
                    colors = listOf(glowingYellow, brightOrange, neonOrange),
                    startY = h * 0.34f,
                    endY = h * 0.57f
                )
                drawPath(leftPec, brush = pecBrush)
                drawPath(rightPec, brush = pecBrush)
                drawPath(leftPec, color = glowingYellow, style = Stroke(width = 2.5f))
                drawPath(rightPec, color = glowingYellow, style = Stroke(width = 2.5f))

                for (i in 1..4) {
                    val factor = i / 5f
                    val y1 = h * (0.36f + factor * 0.15f)
                    val y2 = h * (0.38f + factor * 0.13f)
                    drawLine(Color.White.copy(alpha = 0.9f), Offset(w * 0.49f, y1), Offset(w * (0.49f - factor * 0.30f), y2), strokeWidth = 2.5f)
                    drawLine(Color.White.copy(alpha = 0.9f), Offset(w * 0.51f, y1), Offset(w * (0.51f + factor * 0.30f), y2), strokeWidth = 2.5f)
                }
            }
        }
    }
}

/**
 * 2. EXPLICAÇÃO DA DECISÃO ("POR QUE ESTE TREINO?")
 * Human-centered language explaining why this workout is recommended today.
 */
@Composable
fun FlowWhyThisWorkoutCard(
    workoutName: String = "Upper A",
    muscleGroup: String = "peito",
    reasonsList: List<String> = emptyList(),
    onDetailsClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val defaultReasons = listOf(
        "Costas e bíceps estão sem estímulo direto há 6 dias, no pico ideal de recuperação.",
        "Volume semanal equilibrado em supercompensação para ganho de massa.",
        "Equipamentos verificados e disponíveis no perfil da academia ativa.",
        "Foco do dia otimizado para hipertrofia com intensidade progressiva."
    )
    val allReasons = if (reasonsList.isNotEmpty()) reasonsList else defaultReasons
    val displayReasons = allReasons.take(3)
    val hasMoreReasons = allReasons.size > 3

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = WarmCardBottom),
        border = warmSpecularRimBorder(),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header estilo Pinterest: limpo, tipografia nítida, ação direta
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(Primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "?",
                            color = Color.Black,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                    Text(
                        text = "POR QUE ESTE TREINO?",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }

                if (hasMoreReasons) {
                    Text(
                        text = "Ver detalhes >",
                        color = Primary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onDetailsClick() }
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }

            // Lista de razões: máximo 3, texto completo em até 2 linhas, sem truncamento feio
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                displayReasons.forEach { reasonText ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF242428)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Primary,
                                modifier = Modifier.size(11.dp)
                            )
                        }

                        Text(
                            text = reasonText,
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Normal,
                            lineHeight = 18.sp,
                            maxLines = 2,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

/**
 * EXPLORAR OUTROS TREINOS ("O QUE VOCÊ QUER TREINAR HOJE?")
 * Image cards matching reference layout
 */
@Composable
fun FlowExploreWorkoutsSection(
    onCategoryClick: (String) -> Unit,
    onViewLibraryClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "CONTINUE TREINANDO",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.8.sp
            )

            Text(
                text = "Ver biblioteca >",
                color = Primary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { onViewLibraryClick() }
            )
        }

        val categories = listOf(
            Triple("Recomendado", "Para você", "https://images.unsplash.com/photo-1534438327276-14e5300c3a48?q=80&w=400&auto=format&fit=crop"),
            Triple("Peito", "Hipertrofia", "https://images.unsplash.com/photo-1571019614242-c5c5dee9f50b?q=80&w=400&auto=format&fit=crop"),
            Triple("Costas", "Força", "https://images.unsplash.com/photo-1526506118085-60ce8714f8c5?q=80&w=400&auto=format&fit=crop"),
            Triple("Pernas", "Volume", "https://images.unsplash.com/photo-1574680096145-d05b474e2155?q=80&w=400&auto=format&fit=crop"),
            Triple("Ombros", "Definição", "https://images.unsplash.com/photo-1581009146145-b5ef050c2e1e?q=80&w=400&auto=format&fit=crop"),
            Triple("Braços", "Isolado", "https://images.unsplash.com/photo-1583454110551-21f2fa2afe61?q=80&w=400&auto=format&fit=crop")
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 2.dp)
        ) {
            items(categories) { (title, subtitle, imgUrl) ->
                val isRecomendado = title == "Recomendado"
                
                Card(
                    modifier = Modifier
                        .width(112.dp)
                        .height(130.dp)
                        .clickable {
                            onCategoryClick(title)
                            com.example.utils.AudioSynth.playTickSound()
                        },
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF18181C)),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(
                        if (isRecomendado) 1.5.dp else 1.dp,
                        if (isRecomendado) Primary else Color.White.copy(alpha = 0.08f)
                    )
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        AsyncImage(
                            model = getCategoryModel(title, imgUrl),
                            contentDescription = title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            Color.Black.copy(alpha = 0.75f),
                                            Color.Black.copy(alpha = 0.95f)
                                        )
                                    )
                                )
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            if (isRecomendado) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = null,
                                    tint = Primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            } else {
                                Spacer(modifier = Modifier.size(16.dp))
                            }

                            Column {
                                Text(
                                    text = title,
                                    color = Color.White,
                                    fontSize = if (title.length > 9) 11.sp else 12.5.sp,
                                    fontWeight = FontWeight.Black,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = subtitle,
                                    color = if (isRecomendado) Primary else Color.White.copy(alpha = 0.6f),
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
    }
}

/**
 * 3. STATUS CORPORAL ("COMO SEU CORPO ESTÁ HOJE")
 */
@Composable
fun FlowBodyStatusCard(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = WarmCardBottom),
        border = warmSpecularRimBorder(),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = null,
                        tint = Color(0xFFFF5252),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "COMO SEU CORPO ESTÁ HOJE",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.8.sp
                    )
                }

                Text(
                    text = "Prontidão Alta",
                    color = Color(0xFF00E676),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Horizontal Pill Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val pills = listOf(
                    Triple("Energia", "Alta", Icons.Default.Bolt),
                    Triple("Motivação", "Alta", Icons.Default.Psychology),
                    Triple("Dor muscular", "Baixa", Icons.Default.FitnessCenter),
                    Triple("Stress", "Baixo", Icons.Default.SentimentSatisfied)
                )

                pills.forEach { (label, value, icon) ->
                    Surface(
                        modifier = Modifier.weight(1f),
                        color = Color.White.copy(alpha = 0.04f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f))
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = Primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = label,
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                            Text(
                                text = value,
                                color = Color(0xFF00E676),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }

                // Plus FAB button
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Adicionar registro",
                        tint = Color.Black,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun BodyStatusPill(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = Color.White.copy(alpha = 0.04f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                color = OnSurfaceDim,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = value,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * 4. INSIGHTS DO MAX ("O MAX PERCEBEU")
 */
@Composable
fun FlowMaxInsightsCard(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = WarmCardBottom),
        border = warmSpecularRimBorder(),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = Primary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "O MAX PERCEBEU",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.8.sp
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                InsightItem(
                    prefix = "↑",
                    text = "Sua carga média aumentou 7% nas últimas semanas.",
                    prefixColor = Primary
                )
                InsightItem(
                    prefix = "↓",
                    text = "Seu ombro recebeu pouco estímulo recentemente.",
                    prefixColor = Color(0xFFFFB74D)
                )
                InsightItem(
                    prefix = "✓",
                    text = "Hoje é um ótimo dia para tentar a progressão de carga.",
                    prefixColor = Color(0xFF00E676)
                )
            }
        }
    }
}

@Composable
private fun InsightItem(prefix: String, text: String, prefixColor: Color) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = prefix,
            color = prefixColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.Black
        )
        Text(
            text = text,
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * 8. PREPARAÇÃO PARA IA (Quick Action Chips)
 */
@Composable
fun FlowAiQuickActionsRow(
    onAskMax: () -> Unit,
    onImportWorkout: () -> Unit,
    onSmartSuggest: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "AÇÕES RÁPIDAS COM IA",
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.8.sp,
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                QuickActionChip(
                    icon = Icons.Default.Chat,
                    label = "Perguntar ao Max",
                    onClick = onAskMax
                )
            }
            item {
                QuickActionChip(
                    icon = Icons.Default.FileDownload,
                    label = "Importar treino",
                    onClick = onImportWorkout
                )
            }
            item {
                QuickActionChip(
                    icon = Icons.Default.AutoAwesome,
                    label = "Sugestão inteligente",
                    onClick = onSmartSuggest
                )
            }
        }
    }
}

@Composable
private fun QuickActionChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.clickable { onClick() },
        color = Color(0xFF18181B),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Primary,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = label,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private data class WorkoutHeroParsed(
    val planName: String,
    val dayName: String,
    val muscleGroup: String,
    val actualExerciseCount: Int,
    val totalSets: Int,
    val timeRangeStr: String,
    val imageUrl: String
)

/**
 * Carrossel horizontal de seleção de treinos da periodização ativa.
 * Exibe apenas dados concretos (Nome, Grupos Musculares, Duração e Sequência).
 */
@Composable
fun PeriodizedWorkoutCarousel(
    workoutSequence: List<PeriodizedWorkoutCardData>,
    onSelectWorkout: (Workout) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
    ) {
        items(workoutSequence) { cardData ->
            PeriodizedWorkoutCard(
                cardData = cardData,
                onStartClick = { onSelectWorkout(cardData.workout) }
            )
        }
    }
}

/**
 * Card individual do carrossel da periodização.
 * Design escuro, limpo, com borda sutil alinhado ao Hero Card.
 */
@Composable
fun PeriodizedWorkoutCard(
    cardData: PeriodizedWorkoutCardData,
    onStartClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .width(250.dp)
            .height(280.dp)
            .testTag("periodized_card_${cardData.title}"),
        colors = CardDefaults.cardColors(containerColor = WarmCardBottom),
        border = warmSpecularRimBorder(),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Tag de Sequência / Posição (ex: "Sugerido Hoje", "Próximo (Amanhã)", "Em 2 dias")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val isToday = cardData.sequenceTag.contains("Hoje", ignoreCase = true)
                    Surface(
                        color = if (isToday) Primary.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.08f),
                        border = BorderStroke(
                            1.dp,
                            if (isToday) Primary.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.12f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = cardData.sequenceTag,
                            color = if (isToday) Primary else Color(0xFFC0C0C8),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                        )
                    }

                    Icon(
                        imageVector = Icons.Default.FitnessCenter,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.35f),
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Nome do Treino (ex: "Treino B")
                Text(
                    text = cardData.title,
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-0.3).sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Grupos Musculares Concretos (ex: "Costas e Bíceps")
                Text(
                    text = cardData.muscleGroups,
                    color = Color(0xFFB0B0B8),
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    lineHeight = 18.sp,
                    overflow = TextOverflow.Ellipsis
                )

                // Badges de duração e quantidade de exercícios
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = cardData.durationText,
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    if (cardData.exerciseCount > 0) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.White.copy(alpha = 0.08f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "${cardData.exerciseCount} exercícios",
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // Ação: Botão claro "INICIAR"
            Button(
                onClick = onStartClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Primary,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .testTag("btn_iniciar_${cardData.title}")
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "INICIAR",
                        color = Color.Black,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }
    }
}




