package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.HeroCarouselItem
import com.example.data.model.WorkoutExercise
import com.example.utils.AudioSynth
import com.example.utils.JsonUtils

/**
 * =========================================================================
 * HERO WORKOUT PREVIEW BOTTOM SHEET (UX-HERO-PEEK)
 * Modal expansível para "Espiar" os exercícios, séries, repetições
 * e estrutura guiada de qualquer card do Hero Card na tela inicial.
 * =========================================================================
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeroWorkoutPreviewBottomSheet(
    item: HeroCarouselItem,
    onDismiss: () -> Unit,
    onStartWorkout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isRunning = item.isRunning || item.workout.id.contains("RUNNING")
    val accentColor = if (isRunning) Color(0xFF00E5FF) else Color(0xFF00B0FF)
    val accentCyan = Color(0xFF00E5FF)

    val exercises = remember(item.workout.exercisesJson) {
        if (!isRunning) {
            JsonUtils.parseWorkoutExercises(item.workout.exercisesJson)
        } else {
            emptyList()
        }
    }

    val totalSets = remember(exercises) {
        exercises.sumOf { it.sets.size.coerceAtLeast(1) }
    }

    val displayTitle = item.title.ifBlank {
        if (isRunning) "Módulo de Corrida & VO2 Max" else item.workout.name.ifBlank { "Treino de Musculação" }
    }

    val displaySubtitle = item.subtitle.ifBlank {
        if (isRunning) {
            "Cardio, Resistência & Capacidade Aeróbica"
        } else {
            val categories = exercises
                .map { it.category }
                .filter { it.isNotBlank() && !it.equals("Geral", ignoreCase = true) }
                .distinct()
            if (categories.isNotEmpty()) {
                categories.joinToString(", ")
            } else {
                "Corpo Inteiro (Multiarticular)"
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF0D0F14),
        contentColor = Color.White,
        scrimColor = Color.Black.copy(alpha = 0.72f),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 8.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFF00B0FF).copy(alpha = 0.35f))
            )
        },
        modifier = modifier.testTag("hero_workout_preview_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // CABEÇALHO DESAFOGADO DO SHEET (Badge + Título + Subtítulo + Pílulas Métricas)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Badge Tag
                    Surface(
                        color = accentColor.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, accentCyan.copy(alpha = 0.40f)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = item.badgeLabel.ifBlank { if (isRunning) "SESSÃO AERÓBICA" else "PLANO DE TREINO" },
                            color = accentCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    // Botão Fechar no topo
                    IconButton(
                        onClick = {
                            AudioSynth.playTickSound()
                            onDismiss()
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Fechar",
                            tint = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Text(
                    text = displayTitle,
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-0.3).sp
                )

                Text(
                    text = displaySubtitle,
                    color = Color(0xFF94A3B8),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )

                // Row de Pílulas com Estatísticas
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    // Duração
                    Surface(
                        color = Color(0xFF151922),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.25f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Timer,
                                contentDescription = null,
                                tint = accentCyan,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = item.durationText,
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (!isRunning) {
                        // Total de Exercícios
                        Surface(
                            color = Color(0xFF151922),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, accentColor.copy(alpha = 0.25f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FitnessCenter,
                                    contentDescription = null,
                                    tint = accentCyan,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = "${exercises.size} exercícios",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Total de Séries
                        Surface(
                            color = Color(0xFF151922),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, accentColor.copy(alpha = 0.25f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Repeat,
                                    contentDescription = null,
                                    tint = accentCyan,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = "$totalSets séries",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = Color(0xFF00B0FF).copy(alpha = 0.14f))

            // CONTEÚDO PRINCIPAL (Lista de Exercícios ou Visão Geral da Corrida)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .heightIn(max = 390.dp)
            ) {
                if (isRunning) {
                    // Estrutura Guiada do Módulo de Corrida
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "ESTRUTURA DA SESSÃO AERÓBICA",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        )

                        HeroRunningPhaseCard(
                            title = "Aquecimento & Mobilidade",
                            duration = "5 min",
                            description = "Caminhada acelerada ou trote leve para elevação gradual de FC e fluxo sanguíneo.",
                            icon = Icons.Default.DirectionsWalk,
                            accentColor = accentColor
                        )

                        HeroRunningPhaseCard(
                            title = "Bloco Principal (Pace / Zonas)",
                            duration = "20-30 min",
                            description = "Corrida contínua ou intervalada guiada por voz, GPS e métricas em tempo real.",
                            icon = Icons.Default.DirectionsRun,
                            accentColor = accentColor
                        )

                        HeroRunningPhaseCard(
                            title = "Volta à Calma & Recuperação",
                            duration = "5 min",
                            description = "Desaceleração progressiva para estabilização cardíaca e redução do lactato.",
                            icon = Icons.Default.Bedtime,
                            accentColor = accentColor
                        )
                    }
                } else if (exercises.isEmpty()) {
                    // Estado para treino sem exercícios listados no JSON
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FitnessCenter,
                            contentDescription = null,
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(36.dp)
                        )
                        Text(
                            text = "Treino estruturado com sequenciamento pré-configurado.",
                            color = Color(0xFF94A3B8),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                } else {
                    // Lista de Exercícios numerada sem truncamento prematuro
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        itemsIndexed(exercises) { index, exercise ->
                            HeroExerciseItemCard(
                                index = index + 1,
                                exercise = exercise,
                                accentColor = accentColor
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = Color(0xFF00B0FF).copy(alpha = 0.14f))

            // RODAPÉ FIXO COM BOTÕES DE AÇÃO
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = {
                        AudioSynth.playTickSound()
                        onDismiss()
                    },
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Color(0xFF00B0FF).copy(alpha = 0.30f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color(0xFF151922),
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Text(
                        text = "Fechar",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Button(
                    onClick = {
                        AudioSynth.playTickSound()
                        onDismiss()
                        onStartWorkout()
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent,
                        contentColor = Color(0xFF06101E)
                    ),
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier
                        .weight(1.4f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(Color(0xFF00B0FF), Color(0xFF00E5FF))
                            )
                        )
                        .testTag("btn_peek_start_now")
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isRunning) Icons.Default.DirectionsRun else Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color(0xFF06101E),
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = if (isRunning) "ABRIR CORRIDA" else "INICIAR AGORA",
                            color = Color(0xFF06101E),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroExerciseItemCard(
    index: Int,
    exercise: WorkoutExercise,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF151922),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.22f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Badge Circular Sólido VIBRANTE com Número do Exercício
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(Color(0xFF00B0FF), Color(0xFF00E5FF))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$index",
                    color = Color(0xFF06101E),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black
                )
            }

            // Informações do Exercício (Permite até 2 linhas sem truncamento prematuro)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = exercise.name,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                val setsCount = exercise.sets.size.coerceAtLeast(1)
                val firstReps = exercise.sets.firstOrNull()?.reps ?: 10
                val categoryText = exercise.category.takeIf { it.isNotBlank() && !it.equals("Geral", ignoreCase = true) } ?: "Musculação"

                Text(
                    text = "$categoryText • $setsCount x $firstReps reps",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Tempo de Descanso Compacto
            Surface(
                color = Color(0xFF0D0F14),
                border = BorderStroke(1.dp, accentColor.copy(alpha = 0.20f)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = null,
                        tint = Color(0xFF00E5FF),
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = "${exercise.restTimeSeconds}s",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroRunningPhaseCard(
    title: String,
    duration: String,
    description: String,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF151922),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.22f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(accentColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(18.dp)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        color = accentColor.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = duration,
                            color = accentColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(
                    text = description,
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal
                )
            }
        }
    }
}
