@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.example.ui.screens

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.RunningShoe
import com.example.data.model.safeCoerceIn
import com.example.data.model.safeRoundToInt
import com.example.data.model.toHrTimelineList
import com.example.data.model.toJsonString
import com.example.data.model.toRecordedKmSplits
import com.example.engine.RunningBlockType
import com.example.engine.RunningProgramManager
import com.example.engine.RunningWorkoutPlan
import com.example.ui.theme.RunColors
import com.example.utils.AudioSynth
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Overlay de iluminação nas bordas da tela (Screen Edge Glow) para feedback visual de Pace em tempo real.
 * Não bloqueia toques na UI (desenhado via Canvas limpo no Box de fundo).
 */
@Composable
fun ScreenEdgeGlow(
    blockType: RunningBlockType,
    environment: String,
    currentPaceSecPerKm: Int?,
    targetPaceSecPerKm: Float,
    isGentleMode: Boolean = false,
    isFreeRunMode: Boolean = false,
    currentCadenceSpm: Int? = null,
    currentBpm: Int? = null,
    modifier: Modifier = Modifier
) {
    val (baseColor, _, isLowOpacity) = remember(
        blockType,
        environment,
        currentPaceSecPerKm,
        targetPaceSecPerKm,
        isGentleMode,
        isFreeRunMode,
        currentCadenceSpm,
        currentBpm
    ) {
        val isWalkOrWarmupCooldownBlock = blockType == RunningBlockType.WALK ||
            blockType == RunningBlockType.WARMUP ||
            blockType == RunningBlockType.COOL_DOWN
        val isFreeRunWalkingPace = isFreeRunMode && (
            (currentPaceSecPerKm != null && currentPaceSecPerKm > 510) ||
            (currentCadenceSpm != null && currentCadenceSpm in 1..134)
        )

        if (isGentleMode || isWalkOrWarmupCooldownBlock || isFreeRunWalkingPace) {
            // Sem alerta vermelho punitivo em caminhadas, Modo Leve, aquecimento/desaquecimento ou Corrida Livre em ritmo de caminhada (> 8'30"/km)
            val calmColor = when {
                isGentleMode || isFreeRunWalkingPace || blockType == RunningBlockType.WALK -> Color(0xFF00B4D8) // Azul/Ciano calmo
                blockType == RunningBlockType.WARMUP -> Color(0xFF2ECC71) // Verde Suave
                blockType == RunningBlockType.COOL_DOWN -> Color(0xFF00B4D8) // Azul/Ciano calmo
                else -> Color(0xFF00B4D8)
            }
            Triple(calmColor, false, true)
        } else if (isFreeRunMode) {
            // Corrida Livre em ritmo de corrida (<= 8'30"/km): brilho Verde Suave (#2ECC71) ou Ciano calmo sem alerta punitivo
            val freeRunColor = if (currentPaceSecPerKm != null && currentPaceSecPerKm in 1..510) {
                Color(0xFF2ECC71)
            } else {
                Color(0xFF00B4D8)
            }
            Triple(freeRunColor, false, true)
        } else if (currentBpm != null && currentBpm > 0) {
            // Reação Dinâmica baseada nas Zonas de Frequência Cardíaca (BLE) apenas em blocos RUN da planilha/prova
            val hrColor = when {
                currentBpm < 125 -> Color(0xFF00B4D8) // Azul/Ciano: Zona Leve
                currentBpm in 125..149 -> Color(0xFF2ECC71) // Verde Suave: Aeróbico Ideal
                currentBpm in 150..169 -> Color(0xFFFFB300) // Amarelo/Laranja: Limiar / Ritmo Forte
                else -> Color(0xFFFF1744) // Vermelho/Magenta: Esforço Máximo / Tiro
            }
            Triple(hrColor, true, false)
        } else if (environment == "TREADMILL") {
            val phaseColor = when (blockType) {
                RunningBlockType.RUN -> RunColors.phaseRun
                RunningBlockType.WALK -> Color(0xFF00B4D8)
                RunningBlockType.WARMUP -> Color(0xFF2ECC71)
                RunningBlockType.COOL_DOWN -> Color(0xFF00B4D8)
            }
            Triple(phaseColor, false, true)
        } else {
            // Fase RUN da planilha ao ar livre com GPS (Pace Zone)
            if (currentPaceSecPerKm == null || currentPaceSecPerKm <= 0 || targetPaceSecPerKm <= 0f) {
                Triple(Color(0xFF00B4D8), false, true)
            } else {
                val currentPaceFloat = currentPaceSecPerKm.toFloat()
                val paceColor = when {
                    currentPaceFloat <= targetPaceSecPerKm -> Color(0xFF00E676) // Verde: Ideal/Acelerado
                    else -> {
                        val delayPercent = ((currentPaceFloat - targetPaceSecPerKm) / targetPaceSecPerKm) * 100f
                        when {
                            delayPercent <= 10f -> Color(0xFF00E676) // Verde: Tolerância leve
                            delayPercent <= 25f -> Color(0xFFFFB300) // Laranja: Moderado
                            else -> Color(0xFFFF1744) // Vermelho: Lento no tiro
                        }
                    }
                }
                Triple(paceColor, true, false)
            }
        }
    }

    // Animação de pulsação suave em loop (1500ms tween)
    val infiniteTransition = rememberInfiniteTransition(label = "edge_glow_pulse_transition")
    val animatedAlpha by infiniteTransition.animateFloat(
        initialValue = if (isLowOpacity) 0.12f else 0.28f,
        targetValue = if (isLowOpacity) 0.22f else 0.58f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "edge_glow_alpha"
    )

    androidx.compose.foundation.Canvas(
        modifier = modifier.fillMaxSize()
    ) {
        val w = size.width
        val h = size.height
        val glowThickness = w * 0.18f

        val effectiveColor = baseColor.copy(alpha = animatedAlpha)

        // Bordas com gradiente (centro permanece 100% transparente)
        // Borda Superior
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(effectiveColor, Color.Transparent),
                startY = 0f,
                endY = glowThickness
            ),
            topLeft = androidx.compose.ui.geometry.Offset(0f, 0f),
            size = androidx.compose.ui.geometry.Size(w, glowThickness)
        )

        // Borda Inferior
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Transparent, effectiveColor),
                startY = h - glowThickness,
                endY = h
            ),
            topLeft = androidx.compose.ui.geometry.Offset(0f, h - glowThickness),
            size = androidx.compose.ui.geometry.Size(w, glowThickness)
        )

        // Borda Esquerda
        drawRect(
            brush = Brush.horizontalGradient(
                colors = listOf(effectiveColor, Color.Transparent),
                startX = 0f,
                endX = glowThickness
            ),
            topLeft = androidx.compose.ui.geometry.Offset(0f, 0f),
            size = androidx.compose.ui.geometry.Size(glowThickness, h)
        )

        // Borda Direita
        drawRect(
            brush = Brush.horizontalGradient(
                colors = listOf(Color.Transparent, effectiveColor),
                startX = w - glowThickness,
                endX = w
            ),
            topLeft = androidx.compose.ui.geometry.Offset(w - glowThickness, 0f),
            size = androidx.compose.ui.geometry.Size(glowThickness, h)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RunningEnvironmentBottomSheet(
    onConfirm: (environment: String, isGentleMode: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedEnv by remember { mutableStateOf("OUTDOOR") } // "OUTDOOR" or "TREADMILL"
    var isGentleChosen by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = RunColors.surface,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // Cabeçalho: Título + Botão Fechar (X)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Onde você vai correr hoje?",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Selecione o ambiente para calibrarmos o GPS ou a velocidade da esteira.",
                        color = RunColors.textSecondary,
                        fontSize = 13.sp
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Fechar",
                        tint = Color.White.copy(alpha = 0.7f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Lista Rolável / Empilhada das Opções de Ambiente
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Opção A: Ao Ar Livre
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 64.dp)
                        .clickable {
                            selectedEnv = "OUTDOOR"
                            isGentleChosen = false
                            AudioSynth.playTickSound()
                        },
                    colors = CardDefaults.cardColors(
                        containerColor = if (!isGentleChosen && selectedEnv == "OUTDOOR") RunColors.brand.copy(alpha = 0.18f) else RunColors.surface
                    ),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(
                        width = if (!isGentleChosen && selectedEnv == "OUTDOOR") 2.dp else 1.dp,
                        color = if (!isGentleChosen && selectedEnv == "OUTDOOR") RunColors.brand else RunColors.border
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(if (!isGentleChosen && selectedEnv == "OUTDOOR") RunColors.brand else RunColors.border),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.DirectionsRun,
                                contentDescription = "Ao Ar Livre",
                                tint = if (!isGentleChosen && selectedEnv == "OUTDOOR") RunColors.brandText else Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Ao Ar Livre",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Usa GPS • Mostra Pace em tempo real (min/km)",
                                color = if (!isGentleChosen && selectedEnv == "OUTDOOR") RunColors.brandText else RunColors.textSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        RadioButton(
                            selected = !isGentleChosen && selectedEnv == "OUTDOOR",
                            onClick = {
                                selectedEnv = "OUTDOOR"
                                isGentleChosen = false
                                AudioSynth.playTickSound()
                            },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = RunColors.brand,
                                unselectedColor = Color.Gray
                            )
                        )
                    }
                }

                // Opção B: Na Esteira
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 64.dp)
                        .clickable {
                            selectedEnv = "TREADMILL"
                            isGentleChosen = false
                            AudioSynth.playTickSound()
                        },
                    colors = CardDefaults.cardColors(
                        containerColor = if (!isGentleChosen && selectedEnv == "TREADMILL") RunColors.phaseWarmup.copy(alpha = 0.18f) else RunColors.surface
                    ),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(
                        width = if (!isGentleChosen && selectedEnv == "TREADMILL") 2.dp else 1.dp,
                        color = if (!isGentleChosen && selectedEnv == "TREADMILL") RunColors.phaseWarmup else RunColors.border
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(if (!isGentleChosen && selectedEnv == "TREADMILL") RunColors.phaseWarmup else RunColors.border),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.FitnessCenter,
                                contentDescription = "Na Esteira",
                                tint = if (!isGentleChosen && selectedEnv == "TREADMILL") Color.Black else Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Na Esteira",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Sem GPS • Velocidade Alvo (km/h) + Inclinação 1%",
                                color = if (!isGentleChosen && selectedEnv == "TREADMILL") RunColors.phaseWarmup else RunColors.textSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        RadioButton(
                            selected = !isGentleChosen && selectedEnv == "TREADMILL",
                            onClick = {
                                selectedEnv = "TREADMILL"
                                isGentleChosen = false
                                AudioSynth.playTickSound()
                            },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = RunColors.phaseWarmup,
                                unselectedColor = Color.Gray
                            )
                        )
                    }
                }

                // Opção C: Modo Leve
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 64.dp)
                        .clickable {
                            selectedEnv = "TREADMILL"
                            isGentleChosen = true
                            AudioSynth.playTickSound()
                        },
                    colors = CardDefaults.cardColors(
                        containerColor = if (isGentleChosen) RunColors.phaseWalk.copy(alpha = 0.18f) else RunColors.surface
                    ),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(
                        width = if (isGentleChosen) 2.dp else 1.dp,
                        color = if (isGentleChosen) RunColors.phaseWalk else RunColors.border
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(if (isGentleChosen) RunColors.phaseWalk else RunColors.border),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.DirectionsWalk,
                                contentDescription = "Modo Leve",
                                tint = if (isGentleChosen) Color.Black else Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Modo Leve",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Caminhada regenerativa • Sem GPS • No seu ritmo",
                                color = if (isGentleChosen) RunColors.phaseWalk else RunColors.textSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        RadioButton(
                            selected = isGentleChosen,
                            onClick = {
                                selectedEnv = "TREADMILL"
                                isGentleChosen = true
                                AudioSynth.playTickSound()
                            },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = RunColors.phaseWalk,
                                unselectedColor = Color.Gray
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Botão FIXO no rodapé
            Button(
                onClick = {
                    AudioSynth.playTickSound()
                    onConfirm(
                        if (isGentleChosen) "TREADMILL" else selectedEnv,
                        isGentleChosen
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isGentleChosen) RunColors.phaseWalk else if (selectedEnv == "TREADMILL") RunColors.phaseWarmup else RunColors.brand
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    text = "CONFIRMAR E INICIAR",
                    color = if (isGentleChosen || selectedEnv == "TREADMILL") Color.Black else Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 15.sp,
                    letterSpacing = 0.5.sp
                )
            }

            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PreRunExpressReadinessDialog(
    workoutTitle: String,
    onSkipAndStart: (com.example.data.model.PreRunCheckIn) -> Unit,
    onConfirmAndStart: (com.example.data.model.PreRunCheckIn) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedSleep by remember { mutableStateOf(com.example.data.model.ReadinessLevel.NORMAL) }
    var selectedDisposition by remember { mutableStateOf(com.example.data.model.ReadinessLevel.NORMAL) }
    var selectedPainRegions by remember { mutableStateOf(setOf<com.example.data.model.PainBodyRegion>()) }
    var painIntensityCr10 by remember { mutableIntStateOf(3) }
    var altersGaitMechanics by remember { mutableStateOf(false) }

    val painOptions = remember {
        listOf(
            null to "Nenhuma",
            com.example.data.model.PainBodyRegion.CALF to "Panturrilha",
            com.example.data.model.PainBodyRegion.HAMSTRING to "Posterior",
            com.example.data.model.PainBodyRegion.QUADRICEPS to "Quadríceps",
            com.example.data.model.PainBodyRegion.SHIN to "Canela",
            com.example.data.model.PainBodyRegion.KNEE to "Joelho",
            com.example.data.model.PainBodyRegion.LOWER_BACK to "Lombar"
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF151922),
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = null,
                            tint = RunColors.brandText,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Prontidão Expressa",
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Surface(
                        color = RunColors.brand.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "PRÉ-CORRIDA",
                            color = RunColors.brandText,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                }
                Text(
                    text = workoutTitle,
                    color = RunColors.textSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 1. SONO
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Qualidade do Sono",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val sleepItems = listOf(
                            com.example.data.model.ReadinessLevel.LOW to "Ruim",
                            com.example.data.model.ReadinessLevel.NORMAL to "Normal",
                            com.example.data.model.ReadinessLevel.HIGH to "Ótimo"
                        )
                        sleepItems.forEach { (level, label) ->
                            val isSel = selectedSleep == level
                            val accent = when (level) {
                                com.example.data.model.ReadinessLevel.LOW -> Color(0xFFFF5252)
                                com.example.data.model.ReadinessLevel.NORMAL -> RunColors.brandText
                                com.example.data.model.ReadinessLevel.HIGH -> Color(0xFF00E676)
                            }
                            Surface(
                                onClick = {
                                    selectedSleep = level
                                    AudioSynth.playTickSound()
                                },
                                color = if (isSel) accent.copy(alpha = 0.2f) else Color(0xFF1E2430),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(if (isSel) 1.5.dp else 1.dp, if (isSel) accent else RunColors.border),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 42.dp)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = label,
                                        color = if (isSel) accent else Color.White,
                                        fontSize = 12.5.sp,
                                        fontWeight = if (isSel) FontWeight.Black else FontWeight.SemiBold,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }

                // 2. DISPOSIÇÃO
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Disposição Física / Energia",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val dispItems = listOf(
                            com.example.data.model.ReadinessLevel.LOW to "Baixa",
                            com.example.data.model.ReadinessLevel.NORMAL to "Normal",
                            com.example.data.model.ReadinessLevel.HIGH to "Alta"
                        )
                        dispItems.forEach { (level, label) ->
                            val isSel = selectedDisposition == level
                            val accent = when (level) {
                                com.example.data.model.ReadinessLevel.LOW -> Color(0xFFFFB300)
                                com.example.data.model.ReadinessLevel.NORMAL -> RunColors.brandText
                                com.example.data.model.ReadinessLevel.HIGH -> Color(0xFF00E676)
                            }
                            Surface(
                                onClick = {
                                    selectedDisposition = level
                                    AudioSynth.playTickSound()
                                },
                                color = if (isSel) accent.copy(alpha = 0.2f) else Color(0xFF1E2430),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(if (isSel) 1.5.dp else 1.dp, if (isSel) accent else RunColors.border),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 42.dp)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = label,
                                        color = if (isSel) accent else Color.White,
                                        fontSize = 12.5.sp,
                                        fontWeight = if (isSel) FontWeight.Black else FontWeight.SemiBold,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }

                // 3. DOR OU FADIGA LOCALIZADA
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Dor ou Fadiga Localizada",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        painOptions.forEach { (region, label) ->
                            val isSel = if (region == null) {
                                selectedPainRegions.isEmpty()
                            } else {
                                region in selectedPainRegions
                            }
                            val accent = if (region == null) Color(0xFF00E676) else Color(0xFFFFB300)
                            Surface(
                                onClick = {
                                    AudioSynth.playTickSound()
                                    if (region == null) {
                                        selectedPainRegions = emptySet()
                                        altersGaitMechanics = false
                                    } else {
                                        selectedPainRegions = if (region in selectedPainRegions) {
                                            selectedPainRegions - region
                                        } else {
                                            selectedPainRegions + region
                                        }
                                    }
                                },
                                color = if (isSel) accent.copy(alpha = 0.2f) else Color(0xFF1E2430),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(if (isSel) 1.5.dp else 1.dp, if (isSel) accent else RunColors.border)
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSel) accent else Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSel) FontWeight.Black else FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                                )
                            }
                        }
                    }
                }

                // 4. EXPANSÃO CONDICIONAL DE DOR
                if (selectedPainRegions.isNotEmpty()) {
                    Surface(
                        color = Color(0xFF1E2430),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFFFFB300).copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth()
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
                                Text(
                                    text = "Intensidade da Dor (1 a 10)",
                                    color = Color.White,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Surface(
                                    color = Color(0xFFFFB300).copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "$painIntensityCr10 / 10",
                                        color = Color(0xFFFFB300),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Black,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }

                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                (1..10).forEach { lvl ->
                                    val isLvlSel = painIntensityCr10 == lvl
                                    val lvlColor = when {
                                        lvl <= 3 -> Color(0xFF2ECC71)
                                        lvl <= 6 -> Color(0xFFFFB300)
                                        else -> Color(0xFFFF5252)
                                    }
                                    Surface(
                                        onClick = {
                                            painIntensityCr10 = lvl
                                            AudioSynth.playTickSound()
                                        },
                                        color = if (isLvlSel) lvlColor.copy(alpha = 0.25f) else Color(0xFF151922),
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(if (isLvlSel) 1.5.dp else 1.dp, if (isLvlSel) lvlColor else RunColors.border)
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                        ) {
                                            Text(
                                                text = "$lvl",
                                                color = if (isLvlSel) lvlColor else Color.White,
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        altersGaitMechanics = !altersGaitMechanics
                                        AudioSynth.playTickSound()
                                    }
                                    .padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Altera a passada?",
                                        color = Color.White,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = if (altersGaitMechanics) "Sim (mancando / compensação mecânica)" else "Não (mecânica preservada)",
                                        color = if (altersGaitMechanics) Color(0xFFFF5252) else RunColors.textSecondary,
                                        fontSize = 11.sp
                                    )
                                }
                                Switch(
                                    checked = altersGaitMechanics,
                                    onCheckedChange = {
                                        altersGaitMechanics = it
                                        AudioSynth.playTickSound()
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = Color(0xFFFF5252),
                                        uncheckedThumbColor = Color.LightGray,
                                        uncheckedTrackColor = Color(0xFF151922)
                                    )
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = {
                        AudioSynth.playTickSound()
                        onSkipAndStart(
                            com.example.data.model.PreRunCheckIn(
                                status = com.example.data.model.CheckInStatus.SKIPPED,
                                timestampMillis = System.currentTimeMillis()
                            )
                        )
                    },
                    modifier = Modifier
                        .weight(0.85f)
                        .heightIn(min = 48.dp)
                        .testTag("btn_prerun_checkin_skip"),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, RunColors.border),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = "Pular",
                        color = RunColors.textSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }

                Button(
                    onClick = {
                        AudioSynth.playTickSound()
                        val entries = selectedPainRegions.map { reg ->
                            com.example.data.model.LocalizedPainEntry(
                                region = reg,
                                intensityCr10 = painIntensityCr10.coerceIn(1, 10),
                                altersGaitMechanics = altersGaitMechanics
                            )
                        }
                        onConfirmAndStart(
                            com.example.data.model.PreRunCheckIn(
                                status = com.example.data.model.CheckInStatus.COMPLETED,
                                timestampMillis = System.currentTimeMillis(),
                                sleepQuality = selectedSleep,
                                disposition = selectedDisposition,
                                localizedPains = entries
                            )
                        )
                    },
                    modifier = Modifier
                        .weight(1.35f)
                        .heightIn(min = 48.dp)
                        .testTag("btn_prerun_checkin_confirm"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RunColors.brand,
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = "Confirmar e Iniciar",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        dismissButton = null
    )
}

@Composable
fun RunningSummaryDialog(
    weekNumber: Int,
    dayNumber: Int = 1,
    sessionType: String,
    durationSec: Int,
    environment: String = "OUTDOOR",
    isGentleMode: Boolean = false,
    measuredDistanceMeters: Int = 0,
    plan: RunningWorkoutPlan? = null,
    skippedIndices: Set<Int> = emptySet(),
    availableShoes: List<com.example.data.model.RunningShoe> = emptyList(),
    userWeightKg: Float? = null,
    userProfile: com.example.data.model.UserProfileData? = null,
    mainRunDurationSec: Int = 0,
    mainRunDistanceMeters: Int = 0,
    avgHr: Int? = null,
    maxHr: Int? = null,
    hrTimeline: String = "",
    recordedSplits: List<com.example.data.model.RecordedKmSplit> = emptyList(),
    wasAutoSaved: Boolean = measuredDistanceMeters >= 50,
    athleteAge: Int = 40,
    baselinePaceMinPerKm: Float? = null,
    allRunningSessions: List<com.example.data.model.RunningSession> = emptyList(),
    onApplyNewBaselinePace: ((Float) -> Unit)? = null,
    onSaveAsOfficialRace: ((com.example.data.model.OfficialRaceRecord) -> Unit)? = null,
    onSaveScientificTelemetry: ((rpeCr10: Int?, sRpeLoadUa: Int?, executionAudit: com.example.data.model.SessionExecutionAudit?) -> Unit)? = null,
    onSave: (distanceMeters: Int, rpe: Int, notes: String?, environment: String, painResponse: String?, shoeName: String?) -> Unit,
    onSaveAndShare: ((distanceMeters: Int, rpe: Int, notes: String?, environment: String, painResponse: String?, shoeName: String?) -> Unit)? = null,
    onDiscard: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    var showOfficialRaceConversionDialog by remember { mutableStateOf(false) }
    var hasMarkedAsOfficialRace by remember { mutableStateOf(false) }
    var selectedDeviationReasons by remember { mutableStateOf(setOf<com.example.data.model.InterruptionReason>()) }
    var showDeviationReasonsPanel by remember { mutableStateOf(false) }
    // Cálculo da sugestão de distância do plano apenas para modo ESTEIRA (TREADMILL)
    val suggestedDistKmStr = remember(plan, skippedIndices, durationSec) {
        runCatching {
            if (plan == null) {
                if (durationSec >= 60) {
                    val estM = com.example.services.RunTracker.estimateFallbackDistanceMeters(null, durationSec, skippedIndices, null)
                    String.format(Locale.US, "%.2f", estM / 1000f)
                } else "0.00"
            } else {
                var remSec = durationSec
                var totalSuggestedKm = 0.0
                for (i in plan.blocks.indices) {
                    if (i in skippedIndices) continue
                    val block = plan.blocks[i]
                    val timeInBlockSec = minOf(remSec, block.durationSec)
                    if (timeInBlockSec <= 0) break
                    remSec -= timeInBlockSec

                    val blockSpeed = if (block.type == RunningBlockType.RUN) {
                        val targetPaceMin = RunningProgramManager.calculateTargetPaceForWeek(plan.weekNumber, null).coerceAtLeast(3.0f)
                        60f / targetPaceMin
                    } else {
                        5.0f // Caminhada / Aquecimento / Desaquecimento
                    }
                    totalSuggestedKm += (blockSpeed / 3600.0) * timeInBlockSec
                }
                String.format(Locale.US, "%.2f", totalSuggestedKm)
            }
        }.getOrDefault("0.00")
    }

    // Distância inicial:
    // Em modo AR LIVRE (OUTDOOR), NUNCA inventa distância da planilha se o GPS registrou zero movimento real!
    var distanceKmInput by remember(measuredDistanceMeters, environment, suggestedDistKmStr) {
        mutableStateOf(
            if (measuredDistanceMeters > 0) {
                String.format(Locale.US, "%.2f", measuredDistanceMeters / 1000f)
            } else if (environment == "TREADMILL" && durationSec >= 60 && suggestedDistKmStr != "0.00") {
                suggestedDistKmStr
            } else {
                ""
            }
        )
    }
    var treadmillSpeedInput by remember { mutableStateOf("") }
    var selectedRpe by remember { mutableIntStateOf(6) }
    var rpeTouchedManually by remember { mutableStateOf(false) }
    var selectedPainResponse by remember { mutableStateOf<String?>(null) } // "PIOROU", "IGUAL", "MELHOROU" ou "SEM_DOR"
    var notesInput by remember { mutableStateOf("") }
    var saveWithoutDistance by remember(measuredDistanceMeters, environment) {
        mutableStateOf(environment == "OUTDOOR" && measuredDistanceMeters <= 0)
    }
    var confirmedUnusualPace by remember { mutableStateOf(false) }

    var selectedShoe by remember(availableShoes) {
        mutableStateOf(
            availableShoes.firstOrNull { it.isDefault && !it.isRetired }
                ?: availableShoes.firstOrNull { !it.isRetired }
        )
    }

    var showCustomShoeInput by remember { mutableStateOf(false) }
    var customShoeName by remember { mutableStateOf("") }
    
    val defaultShoesList = remember(availableShoes) {
        if (availableShoes.isNotEmpty()) {
            availableShoes.filter { !it.isRetired }.map { "${it.brand} ${it.name}" }
        } else {
            listOf("Olympikus Corre 4", "Nike Pegasus 40", "On Cloudmonster")
        }
    }
    
    var selectedShoeName by remember {
        mutableStateOf(
            selectedShoe?.let { "${it.brand} ${it.name}" } ?: defaultShoesList.firstOrNull() ?: "Olympikus Corre 4"
        )
    }

    val distKmFloat = distanceKmInput.replace(',', '.').toFloatOrNull() ?: 0f
    val distMeters = (distKmFloat * 1000).toInt()
    val speedKmhFloat = treadmillSpeedInput.replace(',', '.').toFloatOrNull()

    val calculatedPaceText = if (distMeters > 0 && durationSec > 0) {
        val paceSecPerKm = (durationSec * 1000) / distMeters
        val pMin = paceSecPerKm / 60
        val pSec = paceSecPerKm % 60
        String.format("%d'%02d\" / km", pMin, pSec)
    } else if (speedKmhFloat != null && speedKmhFloat > 0f) {
        val paceMinFloat = 60f / speedKmhFloat
        val pMin = paceMinFloat.toInt()
        val pSec = ((paceMinFloat - pMin) * 60).toInt()
        String.format("%d'%02d\" / km", pMin, pSec)
    } else {
        "--:-- / km"
    }

    // Checagem de Pace Incomum (< 3'00"/km ou > 20'00"/km)
    val paceSecPerKm = if (distMeters > 0 && durationSec > 0) (durationSec * 1000) / distMeters else 0
    val isUnusualPace = distMeters > 0 && durationSec > 0 && (paceSecPerKm < 180 || paceSecPerKm > 1200)

    // Checagem de Treino Incompleto (< 80% do planejado ativo) - ignora Corrida Livre / Prova 5K / 10K / Modo Leve
    val isFreeOrRaceSession = RunningProgramManager.isFreeOrRaceSessionType(sessionType)
    val activePlannedSec = plan?.blocks?.indices?.filter { it !in skippedIndices }?.sumOf { plan.blocks[it].durationSec } ?: 0
    val isWorkoutIncomplete = !isGentleMode && !isFreeOrRaceSession && activePlannedSec > 0 && durationSec < (0.8f * activePlannedSec).toInt()
    val executedMin = durationSec / 60
    val plannedMin = (activePlannedSec + 59) / 60

    val mins = durationSec / 60
    val secs = durationSec % 60
    val totalTimeText = String.format("%02d:%02d min", mins, secs)

    val isDistanceValidToSave = distMeters > 0 || (speedKmhFloat != null && speedKmhFloat > 0f) || saveWithoutDistance

    com.example.ui.components.ProvideRunFontScale {
    AlertDialog(
        onDismissRequest = { /* Bloqueado contra fechamento acidental; exige clique explícito em Salvar ou Descartar */ },
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = true,
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        ),
        modifier = Modifier
            .fillMaxWidth(0.94f)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(top = 12.dp, bottom = 28.dp),
        title = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = when {
                            isFreeOrRaceSession -> RunningProgramManager.getDisplayTitleForSessionType(sessionType, isGentleMode, weekNumber, dayNumber)
                            isGentleMode -> "Resumo - Modo Leve"
                            else -> "Resumo da Sessão"
                        },
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    com.example.ui.components.RunFontScaleToggleButton(compact = true)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = RunColors.brand.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = if (environment == "TREADMILL") "ESTEIRA" else "AR LIVRE",
                            color = RunColors.brandText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = when {
                        isFreeOrRaceSession -> RunningProgramManager.getDisplayTitleForSessionType(sessionType, isGentleMode, weekNumber, dayNumber)
                        isGentleMode -> "Caminhada Regenerativa • Zero Fricção"
                        else -> "Semana $weekNumber · Dia $dayNumber"
                    },
                    color = RunColors.brandText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )

                if (wasAutoSaved && (distMeters >= 50 || measuredDistanceMeters >= 50)) {
                    Surface(
                        color = Color(0xFF2ECC71).copy(alpha = 0.14f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color(0xFF2ECC71).copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Revise a distância e o esforço abaixo e toque em Salvar para confirmar no Histórico.",
                            color = Color(0xFF2ECC71),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                } else if (distMeters < 50 && measuredDistanceMeters < 50) {
                    Surface(
                        color = RunColors.alert.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, RunColors.alert.copy(alpha = 0.55f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("summary_no_displacement_warning")
                    ) {
                        Text(
                            text = "Treino sem deslocamento detectado (0,00 km) — toque em Descartar Treino para ignorar sem avançar a planilha",
                            color = RunColors.alert,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                        )
                    }
                }

                if (isWorkoutIncomplete) {
                    Surface(
                        color = RunColors.phaseWarmup.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, RunColors.phaseWarmup.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "AVISO: Treino incompleto ($executedMin de $plannedMin min)",
                            color = RunColors.phaseWarmup,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }

                val hasMainRunIsolation = (mainRunDistanceMeters > 0 && mainRunDurationSec > 0 &&
                        (mainRunDurationSec < durationSec || mainRunDistanceMeters < distMeters)) ||
                        (plan?.blocks?.any { it.type == RunningBlockType.WARMUP || it.type == RunningBlockType.COOL_DOWN } == true && distMeters > 0)

                val effectiveMainRunDuration = if (mainRunDurationSec > 0) {
                    mainRunDurationSec
                } else if (plan != null && hasMainRunIsolation) {
                    plan.blocks.filterIndexed { idx, b -> idx !in skippedIndices && b.type != RunningBlockType.WARMUP && b.type != RunningBlockType.COOL_DOWN }.sumOf { it.durationSec }
                } else {
                    durationSec
                }

                val effectiveMainRunDistance = if (mainRunDistanceMeters > 0) {
                    mainRunDistanceMeters
                } else if (hasMainRunIsolation && distMeters > 0 && durationSec > effectiveMainRunDuration) {
                    val warmupCooldownSec = (durationSec - effectiveMainRunDuration).coerceAtLeast(0)
                    // Desconta distância estimada de caminhada no aquecimento/desaquecimento (~5.0 km/h = 1.389 m/s), limitada a 45% da distância total
                    val warmupCooldownMeters = ((warmupCooldownSec * (5000.0 / 3600.0)).toInt()).coerceAtMost((distMeters * 0.45).toInt())
                    (distMeters - warmupCooldownMeters).coerceAtLeast(1)
                } else {
                    distMeters
                }

                val mainRunPaceSec = if (effectiveMainRunDistance > 0 && effectiveMainRunDuration > 0) {
                    ((effectiveMainRunDuration.toDouble() / effectiveMainRunDistance.toDouble()) * 1000.0).toInt()
                } else 0

                val mainRunPaceText = if (mainRunPaceSec > 0) {
                    String.format(Locale.US, "%d'%02d\" / km", mainRunPaceSec / 60, mainRunPaceSec % 60)
                } else {
                    calculatedPaceText
                }

                if (hasMainRunIsolation) {
                    // Destaque da Corrida Principal (Sem Aquecimento/Desaquecimento)
                    Surface(
                        color = Color(0xFF161B26),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.5.dp, Color(0xFF00E5FF).copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
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
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.DirectionsRun,
                                        contentDescription = null,
                                        tint = Color(0xFF00E5FF),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Corrida Principal (Pace Oficial)",
                                        color = Color(0xFF00E5FF),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Surface(
                                    color = Color(0xFF00E5FF).copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "WORK PHASE",
                                        color = Color(0xFF00E5FF),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("Distância Real", color = RunColors.textSecondary, fontSize = 11.sp)
                                    Text(
                                        text = String.format(Locale.US, "%.2f km", effectiveMainRunDistance / 1000f),
                                        color = Color.White,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Column {
                                    Text("Tempo Ativo", color = RunColors.textSecondary, fontSize = 11.sp)
                                    val m = effectiveMainRunDuration / 60
                                    val s = effectiveMainRunDuration % 60
                                    Text(
                                        text = String.format(Locale.US, "%02d:%02d", m, s),
                                        color = Color.White,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Column {
                                    Text("Pace Médio", color = RunColors.textSecondary, fontSize = 11.sp)
                                    Text(
                                        text = mainRunPaceText,
                                        color = Color(0xFF00E5FF),
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                            }
                        }
                    }

                    // Pílula secundária obrigatória: "Total c/ Aquec. e Desaquec.: X,XX km · MM:SS"
                    Surface(
                        color = Color(0xFF222634),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Timer,
                                contentDescription = null,
                                tint = Color(0xFFB0B3C1),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Total c/ Aquec. e Desaquec.: ${String.format(Locale.US, "%.2f km", distMeters / 1000f)} · $totalTimeText",
                                color = Color(0xFFB0B3C1),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Tempo Total", color = RunColors.textSecondary, fontSize = 11.sp)
                            Text(totalTimeText, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }

                        Column {
                            Text(
                                if (environment == "TREADMILL") "Pace Equivalente" else "Pace Calculado",
                                color = RunColors.textSecondary,
                                fontSize = 11.sp
                            )
                            Text(calculatedPaceText, color = RunColors.brandText, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Dossiê Biomecânico & Fisiológico (Etapa 1: Grade 2x3 + FC 5 Zonas + Ritmo Área + Ritmo por Km)
                val (previewSession, previewSprintAnalytics, previewDossier) = remember(
                    weekNumber,
                    dayNumber,
                    durationSec,
                    distMeters,
                    measuredDistanceMeters,
                    effectiveMainRunDuration,
                    effectiveMainRunDistance,
                    avgHr,
                    maxHr,
                    hrTimeline,
                    recordedSplits,
                    environment
                ) {
                    val effectiveDist = if (distMeters > 0) distMeters else measuredDistanceMeters
                    val previewPace = if (effectiveDist > 0 && durationSec > 0) (durationSec * 1000) / effectiveDist else 0
                    val previewMainPace = if (effectiveMainRunDistance > 0 && effectiveMainRunDuration > 0) {
                        (effectiveMainRunDuration * 1000) / effectiveMainRunDistance
                    } else null
                    val splitsEncoded = if (recordedSplits.isNotEmpty()) {
                        recordedSplits.toJsonString()
                    } else null
                    val sess = com.example.data.model.RunningSession(
                        id = "preview_session",
                        date = System.currentTimeMillis(),
                        weekNumber = weekNumber,
                        dayNumber = dayNumber,
                        durationSec = durationSec.coerceAtLeast(1),
                        distanceMeters = effectiveDist,
                        avgPaceSecPerKm = previewPace,
                        rpe = selectedRpe,
                        notes = notesInput,
                        environment = environment,
                        painResponse = selectedPainResponse,
                        isGentleMode = isGentleMode,
                        shoeName = selectedShoeName,
                        avgHr = avgHr,
                        maxHr = maxHr,
                        mainRunDurationSec = if (hasMainRunIsolation) effectiveMainRunDuration else null,
                        mainRunDistanceMeters = if (hasMainRunIsolation) effectiveMainRunDistance else null,
                        mainRunAvgPaceSecPerKm = if (hasMainRunIsolation) previewMainPace else null,
                        kmSplitsJson = splitsEncoded,
                        hrTimeline = hrTimeline,
                        routePointsJson = null
                    )
                    val sAnalytics = runCatching {
                        com.example.data.model.calculateSprintAnalytics(sess, emptyList())
                    }.getOrElse {
                        com.example.data.model.SprintAnalytics(
                            structureSummary = "Sessão de Corrida",
                            headerIdentification = "Sessão de Corrida",
                            mainDistMeters = sess.distanceMeters,
                            mainDurationSec = sess.durationSec,
                            mainPaceSecPerKm = sess.avgPaceSecPerKm,
                            totalDistMeters = sess.distanceMeters,
                            totalDurationSec = sess.durationSec,
                            totalPaceSecPerKm = sess.avgPaceSecPerKm,
                            avgSprintPaceSecPerKm = sess.avgPaceSecPerKm,
                            bestSprintPaceSecPerKm = sess.avgPaceSecPerKm,
                            runningTimeSec = sess.durationSec,
                            walkingTimeSec = 0,
                            avgCadenceSpm = 156,
                            elevationGainMeters = 0,
                            isContinuousRunMode = true
                        )
                    }
                    val effectiveSummaryProfile = userProfile ?: userWeightKg?.takeIf { it > 0f }?.let {
                        com.example.data.model.UserProfileData(peso = it, weightKg = it)
                    }
                    val doss = runCatching {
                        com.example.data.model.buildRunPhysiologyDossier(
                            session = sess,
                            userProfile = effectiveSummaryProfile,
                            gpsPoints = emptyList()
                        )
                    }.getOrElse {
                        val safeDur = sess.durationSec.coerceAtLeast(1)
                        val safeDistKm = (sess.distanceMeters.coerceAtLeast(0)) / 1000f
                        val safePace = sess.avgPaceSecPerKm.coerceAtLeast(0)
                        val formattedPace = com.example.data.model.formatPaceMinSecQuote(safePace)
                        val computedActiveKcal = com.example.data.model.calculateRunActiveCaloriesKcal(
                            distanceMeters = sess.distanceMeters,
                            userProfile = effectiveSummaryProfile,
                            weightKgOverride = userWeightKg
                        )
                        val missingWeight = computedActiveKcal == null && sess.distanceMeters > 0
                        com.example.data.model.RunPhysiologyDossier(
                            badgeTitle = "SESSÃO DE CORRIDA",
                            athleteName = "Atleta",
                            formattedDateTime = "Hoje",
                            distanceKm = safeDistKm,
                            totalDistanceWithWarmupKm = safeDistKm,
                            hasMainRunIsolation = false,
                            durationSeconds = safeDur,
                            totalDurationWithWarmupSeconds = safeDur,
                            formattedDurationHhMmSs = com.example.data.model.formatSecondsToHhMmSs(safeDur),
                            activeCaloriesKcal = computedActiveKcal ?: 0,
                            basalCaloriesKcal = 0,
                            totalCaloriesKcal = computedActiveKcal ?: 0,
                            isWeightMissingForCalories = missingWeight,
                            avgPaceSecPerKm = safePace,
                            maxPaceSecPerKm = safePace,
                            formattedAvgPace = formattedPace,
                            formattedMaxPace = formattedPace,
                            avgHeartRateBpm = (sess.avgHr ?: 0).coerceAtLeast(0),
                            maxHeartRateBpm = (sess.maxHr ?: 0).coerceAtLeast(0),
                            effectiveMaxHeartRateBpm = 190,
                            isHrEstimated = sess.avgHr == null || sess.avgHr <= 0,
                            totalSteps = 0,
                            avgCadenceSpm = 0,
                            maxCadenceSpm = 0,
                            avgStrideLengthCm = 0,
                            maxStrideLengthCm = 0,
                            telemetrySamples = emptyList(),
                            hrZones5 = emptyList(),
                            kmSplits = emptyList()
                        )
                    }
                    Triple(sess, sAnalytics, doss)
                }

                com.example.ui.components.RunDossierMasterHeaderAnd2x3Grid(
                    dossier = previewDossier,
                    session = previewSession,
                    sprintAnalytics = previewSprintAnalytics,
                    isLightMode = false
                )

                com.example.ui.components.RunnaPaceEvolutionBanner(
                    session = previewSession,
                    sprintAnalytics = previewSprintAnalytics,
                    allRunningSessions = allRunningSessions,
                    athleteAge = athleteAge,
                    baselinePaceMinPerKm = baselinePaceMinPerKm,
                    onApplyNewBaselinePace = { newPaceMin ->
                        onApplyNewBaselinePace?.invoke(newPaceMin)
                    }
                )

                var showSummaryHrModal by remember { mutableStateOf(false) }

                val track400mLapsInSummary = remember(previewSession.kmSplitsJson) {
                    previewSession.kmSplitsJson.toRecordedKmSplits().filter { isTrack400mLapSplit(it) }
                }
                if (track400mLapsInSummary.isNotEmpty() || RunningProgramManager.isTrack400mSessionType(sessionType)) {
                    Track400mLapsTableCard(
                        splits = if (track400mLapsInSummary.isNotEmpty()) track400mLapsInSummary else previewSession.kmSplitsJson.toRecordedKmSplits(),
                        title = "TABELA DE LAPS DE PISTA (400M)"
                    )
                }

                com.example.ui.components.RunDossierHeartRateCard(
                    dossier = previewDossier,
                    isLightMode = false,
                    onOpenDetailedModal = {
                        AudioSynth.playTickSound()
                        showSummaryHrModal = true
                    }
                )

                if (showSummaryHrModal) {
                    val hrTimelineList = remember(hrTimeline) { hrTimeline.toHrTimelineList() }
                    com.example.ui.components.FullscreenSessionHeartRateModal(
                        dossier = previewDossier,
                        athleteAge = (userProfile?.idade ?: athleteAge).takeIf { it in 10..100 },
                        hrSamples = hrTimelineList,
                        splits = previewSession.kmSplitsJson.toRecordedKmSplits(),
                        weekNumber = previewSession.weekNumber,
                        dayNumber = previewSession.dayNumber,
                        isLightMode = false,
                        onDismiss = { showSummaryHrModal = false }
                    )
                }

                com.example.ui.components.RunDossierPaceAreaChartCard(
                    dossier = previewDossier,
                    isLightMode = false
                )

                com.example.ui.components.RunDossierPacePerKmCard(
                    dossier = previewDossier,
                    isLightMode = false
                )

                com.example.ui.components.RunDossierCadenceCard(
                    dossier = previewDossier,
                    isLightMode = false
                )

                com.example.ui.components.RunDossierStrideLengthCard(
                    dossier = previewDossier,
                    isLightMode = false
                )

                com.example.ui.components.RunDossierTrainingEffectAndSourceCard(
                    dossier = previewDossier,
                    isLightMode = false
                )

                // Seção de Métricas e Parciais (somente Ar Livre com GPS)
                var selectedSplitInterval by remember { mutableStateOf(1.0f) }
                val metricsResult = remember(plan, skippedIndices, durationSec, distMeters, selectedSplitInterval, userWeightKg) {
                    val safeDistDouble = if (distMeters > 0) distMeters.toDouble() else measuredDistanceMeters.coerceAtLeast(0).toDouble()
                    runCatching {
                        com.example.services.WorkoutMetricsCalculator.calculateMetrics(
                            plan = plan,
                            skippedIndices = skippedIndices,
                            totalElapsedSec = durationSec.coerceAtLeast(1),
                            totalMeasuredDistanceMeters = safeDistDouble,
                            splitIntervalKm = selectedSplitInterval,
                            userWeightKg = userWeightKg
                        )
                    }.getOrElse {
                        com.example.services.WorkoutMetricsResult(
                            evolutionDurationSec = durationSec.coerceAtLeast(0),
                            evolutionDistanceMeters = safeDistDouble,
                            evolutionAvgPaceSecPerKm = if (safeDistDouble > 1.0 && durationSec > 0) ((durationSec * 1000.0) / safeDistDouble).toInt() else null,
                            cargaDurationSec = durationSec.coerceAtLeast(0),
                            cargaDistanceMeters = safeDistDouble,
                            cargaCaloriesKcal = 0,
                            recuperationAvgPaceSecPerKm = null,
                            blockSummaries = emptyList(),
                            partialSplits = emptyList()
                        )
                    }
                }

                com.example.ui.components.WorkoutMetricsAndSplitsSection(
                    metrics = metricsResult,
                    isOutdoorGps = environment == "OUTDOOR" && (distMeters > 0 || measuredDistanceMeters > 0),
                    selectedSplitInterval = selectedSplitInterval,
                    onSplitIntervalChanged = { selectedSplitInterval = it }
                )

                // Seletor de Tênis de Corrida
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Tênis Utilizado:",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = selectedShoeName,
                            color = RunColors.brandText,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Chips de Seleção de Tênis com FlowRow adaptativo e alvos de toque >= 48.dp
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        defaultShoesList.take(3).forEach { shoeLabel ->
                            val isSelected = selectedShoeName.equals(shoeLabel, ignoreCase = true) && !showCustomShoeInput
                            OutlinedButton(
                                onClick = {
                                    selectedShoeName = shoeLabel
                                    showCustomShoeInput = false
                                    AudioSynth.playTickSound()
                                },
                                modifier = Modifier.heightIn(min = 48.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (isSelected) RunColors.brand.copy(alpha = 0.22f) else Color.Transparent,
                                    contentColor = if (isSelected) RunColors.brandText else Color.White
                                ),
                                border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, if (isSelected) RunColors.brand else RunColors.border),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    text = shoeLabel,
                                    fontSize = 14.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    // Botão/Campo para Adicionar Novo Tênis
                    if (showCustomShoeInput) {
                        OutlinedTextField(
                            value = customShoeName,
                            onValueChange = {
                                customShoeName = it
                                selectedShoeName = it.ifBlank { "Olympikus Corre 4" }
                            },
                            label = { Text("Nome do novo tênis (ex: Asics Novablast)", color = RunColors.textSecondary) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = RunColors.brand,
                                unfocusedBorderColor = RunColors.border,
                                focusedLabelColor = RunColors.brand,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )
                    } else {
                        Surface(
                            onClick = {
                                showCustomShoeInput = true
                                AudioSynth.playTickSound()
                            },
                            color = Color.White.copy(alpha = 0.05f),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.CenterStart,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    text = "Digitar/adicionar outro modelo de tênis",
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                // Campo de Distância
                OutlinedTextField(
                    value = distanceKmInput,
                    onValueChange = {
                        distanceKmInput = it
                        saveWithoutDistance = false
                        confirmedUnusualPace = false
                    },
                    label = {
                        Text(
                            text = if (environment == "TREADMILL") "Distância marcada na esteira (km)" else "Distância percorrida (km)",
                            color = RunColors.textSecondary
                        )
                    },
                    placeholder = { Text("Ex: 3.2", color = RunColors.textSecondary.copy(alpha = 0.6f)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = RunColors.brand,
                        unfocusedBorderColor = RunColors.border,
                        focusedLabelColor = RunColors.brand,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )

                // Chip de Sugestão de Distância do Plano (Apenas Esteira / Modo Leve)
                if (environment == "TREADMILL" || isGentleMode) {
                    Surface(
                        onClick = {
                            distanceKmInput = suggestedDistKmStr
                            saveWithoutDistance = false
                            confirmedUnusualPace = false
                            AudioSynth.playTickSound()
                        },
                        color = RunColors.brand.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, RunColors.brand.copy(alpha = 0.6f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                            Text(
                                text = "Sugestão do plano: $suggestedDistKmStr km · usar",
                                color = RunColors.brandText,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Estimativa baseada no tempo real executado na esteira",
                                color = RunColors.textSecondary,
                                fontSize = 10.sp
                            )
                        }
                    }
                }

                // Opção explícita de salvar sem distância
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = saveWithoutDistance,
                        onCheckedChange = { checked ->
                            saveWithoutDistance = checked
                            if (checked) {
                                distanceKmInput = ""
                                confirmedUnusualPace = false
                            }
                            AudioSynth.playTickSound()
                        },
                        colors = CheckboxDefaults.colors(
                            checkedColor = RunColors.brand,
                            checkmarkColor = Color.Black
                        )
                    )
                    Text(
                        text = "Salvar sem distância (0.0 km)",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Banner de Alerta para Pace Incomum (< 3'00"/km ou > 20'00"/km)
                if (isUnusualPace && !saveWithoutDistance) {
                    Surface(
                        color = RunColors.alert.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, RunColors.alert.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "ATENÇÃO: Pace incomum ($calculatedPaceText). Confira a distância.",
                            color = RunColors.alert,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }

                // Campo opcional de velocidade média na esteira
                if (environment == "TREADMILL") {
                    OutlinedTextField(
                        value = treadmillSpeedInput,
                        onValueChange = {
                            treadmillSpeedInput = it
                            confirmedUnusualPace = false
                        },
                        label = { Text("Velocidade média (km/h) - opcional", color = RunColors.textSecondary, maxLines = 1) },
                        placeholder = { Text("Ex: 8.5", color = RunColors.textSecondary.copy(alpha = 0.6f)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = RunColors.brand,
                            unfocusedBorderColor = RunColors.border,
                            focusedLabelColor = RunColors.brand,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                }

                if (isGentleMode) {
                    Text(
                        text = "Como a dor ficou após o treino?",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val painOptions = listOf(
                            Triple("PIOROU", "Piorou", RunColors.alert),
                            Triple("IGUAL", "Permaneceu", RunColors.phaseWarmup),
                            Triple("MELHOROU", "Melhorou", RunColors.phaseWalk)
                        )
                        painOptions.forEach { (key, label, accent) ->
                            val isSelected = selectedPainResponse == key
                            OutlinedButton(
                                onClick = {
                                    selectedPainResponse = key
                                    AudioSynth.playTickSound()
                                },
                                modifier = Modifier.heightIn(min = 48.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (isSelected) accent.copy(alpha = 0.22f) else Color.Transparent,
                                    contentColor = if (isSelected) accent else Color.White
                                ),
                                border = BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) accent else RunColors.border),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 14.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold
                                )
                            }
                        }
                    }
                } else {
                    val liveSrpeUa = remember(durationSec, selectedRpe) {
                        com.example.data.model.calculateSrpeLoadUa(durationSec, selectedRpe) ?: 0
                    }
                    Surface(
                        color = Color(0xFF161B26),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, RunColors.brand.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
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
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "ESCALA BORG CR10 (0 A 10)",
                                        color = RunColors.textSecondary,
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = 0.5.sp
                                    )
                                    Text(
                                        text = com.example.data.model.borgCr10Label(selectedRpe),
                                        color = Color.White,
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Surface(
                                    color = RunColors.brand.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, RunColors.brand)
                                ) {
                                    Text(
                                        text = "Carga Interna (sRPE): $liveSrpeUa UA",
                                        color = RunColors.brandText,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Black,
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                                    )
                                }
                            }

                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                (0..10).forEach { rpeVal ->
                                    val isSelected = selectedRpe == rpeVal
                                    val chipColor = when {
                                        rpeVal <= 2 -> Color(0xFF2ECC71)
                                        rpeVal <= 4 -> Color(0xFF00E5FF)
                                        rpeVal <= 6 -> RunColors.brandText
                                        rpeVal <= 8 -> Color(0xFFFFB300)
                                        else -> RunColors.alert
                                    }
                                    Surface(
                                        onClick = {
                                            selectedRpe = rpeVal
                                            rpeTouchedManually = true
                                            AudioSynth.playTickSound()
                                        },
                                        color = if (isSelected) chipColor.copy(alpha = 0.24f) else Color(0xFF1E2430),
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(
                                            width = if (isSelected) 1.5.dp else 1.dp,
                                            color = if (isSelected) chipColor else RunColors.border
                                        )
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)
                                        ) {
                                            Text(
                                                text = "$rpeVal",
                                                color = if (isSelected) chipColor else Color.White,
                                                fontSize = 12.sp,
                                                fontWeight = if (isSelected) FontWeight.Black else FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Quadro Automático "Planejado vs. Executado" + Motivo do Desvio / Intercorrência
                val plannedRunSec = remember(plan, durationSec, effectiveMainRunDuration) {
                    val rawPlanRun = plan?.blocks?.filter { it.type == RunningBlockType.RUN }?.sumOf { it.durationSec } ?: 0
                    if (rawPlanRun in 1..3599) rawPlanRun else effectiveMainRunDuration.coerceAtLeast(durationSec)
                }
                val plannedWalkSec = remember(plan) {
                    plan?.blocks?.filter { it.type == RunningBlockType.WALK }?.sumOf { it.durationSec } ?: 0
                }
                val actualRunSec = remember(previewSprintAnalytics, effectiveMainRunDuration) {
                    previewSprintAnalytics.runningTimeSec.takeIf { it > 0 } ?: effectiveMainRunDuration
                }
                val actualWalkSec = remember(previewSprintAnalytics) {
                    previewSprintAnalytics.walkingTimeSec.coerceAtLeast(0)
                }
                val unplannedWalkSec = remember(actualWalkSec, plannedWalkSec, plannedRunSec, actualRunSec) {
                    val extraWalk = (actualWalkSec - plannedWalkSec).coerceAtLeast(0)
                    val runDeficit = (plannedRunSec - actualRunSec).coerceAtLeast(0)
                    maxOf(extraWalk, minOf(actualWalkSec, runDeficit))
                }
                val hasAutomaticDeviation = unplannedWalkSec > 20 || (plannedRunSec >= 60 && actualRunSec < (plannedRunSec * 0.90).toInt())

                Surface(
                    color = Color(0xFF161B26),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(
                        1.dp,
                        if (hasAutomaticDeviation) Color(0xFFFFB300).copy(alpha = 0.55f) else Color.White.copy(alpha = 0.14f)
                    ),
                    modifier = Modifier.fillMaxWidth()
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
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = if (hasAutomaticDeviation) Color(0xFFFFB300) else Color(0xFF00E676),
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "AUDITORIA: PLANEJADO VS. EXECUTADO",
                                    color = Color.White,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Black,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (!hasAutomaticDeviation) {
                                TextButton(
                                    onClick = {
                                        showDeviationReasonsPanel = !showDeviationReasonsPanel
                                        AudioSynth.playTickSound()
                                    },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                    modifier = Modifier.height(26.dp)
                                ) {
                                    Text(
                                        text = if (showDeviationReasonsPanel) "Ocultar" else "+ Intercorrência",
                                        color = RunColors.brandText,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        val prescMin = ((plannedRunSec + 30) / 60)
                        val execRunMin = ((actualRunSec + 30) / 60)
                        val execWalkMin = ((actualWalkSec + 30) / 60)
                        Text(
                            text = "Prescrito: $prescMin min corrida | Executado: $execRunMin min corrida ($execWalkMin min caminhada)",
                            color = Color(0xFFD0D7E2),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        if (hasAutomaticDeviation || showDeviationReasonsPanel || selectedDeviationReasons.isNotEmpty()) {
                            Text(
                                text = "Motivo do Desvio / Intercorrência (múltipla escolha):",
                                color = Color(0xFFFFB300),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            val reasonChoices = listOf(
                                com.example.data.model.InterruptionReason.CARDIO_RESPIRATORY_FATIGUE to "Falta de fôlego / condicionamento",
                                com.example.data.model.InterruptionReason.MUSCULAR_FATIGUE_OR_PAIN to "Fadiga ou dor muscular localizada",
                                com.example.data.model.InterruptionReason.GI_DISTRESS to "Desconforto gastrointestinal / cólica",
                                com.example.data.model.InterruptionReason.HEAT_HUMIDITY_STRESS to "Calor / umidade excessiva",
                                com.example.data.model.InterruptionReason.EXTERNAL_STOP to "Pausa externa (semáforo / rua)"
                            )
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                reasonChoices.forEach { (reason, label) ->
                                    val isReasonSel = reason in selectedDeviationReasons
                                    Surface(
                                        onClick = {
                                            AudioSynth.playTickSound()
                                            selectedDeviationReasons = if (isReasonSel) {
                                                selectedDeviationReasons - reason
                                            } else {
                                                selectedDeviationReasons + reason
                                            }
                                        },
                                        color = if (isReasonSel) Color(0xFFFFB300).copy(alpha = 0.22f) else Color(0xFF1E2430),
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(
                                            if (isReasonSel) 1.5.dp else 1.dp,
                                            if (isReasonSel) Color(0xFFFFB300) else RunColors.border
                                        )
                                    ) {
                                        Text(
                                            text = label,
                                            color = if (isReasonSel) Color(0xFFFFB300) else Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = if (isReasonSel) FontWeight.Bold else FontWeight.Medium,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = notesInput,
                    onValueChange = { notesInput = it },
                    label = { Text("Anotações (opcional)", color = RunColors.textSecondary) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = RunColors.brand,
                        unfocusedBorderColor = RunColors.border,
                        focusedLabelColor = RunColors.brand,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )

                // Botão de Conversão em 1 Toque: "Marcar como Prova Oficial"
                if (onSaveAsOfficialRace != null) {
                    Surface(
                        onClick = {
                            AudioSynth.playTickSound()
                            showOfficialRaceConversionDialog = true
                        },
                        color = if (hasMarkedAsOfficialRace) Color(0xFF00E676).copy(alpha = 0.16f) else Color(0xFFFFD600).copy(alpha = 0.15f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(
                            1.2.dp,
                            if (hasMarkedAsOfficialRace) Color(0xFF00E676) else Color(0xFFFFD600).copy(alpha = 0.65f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("summary_mark_official_race_btn")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 11.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.EmojiEvents,
                                    contentDescription = null,
                                    tint = if (hasMarkedAsOfficialRace) Color(0xFF00E676) else Color(0xFFFFD600),
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = if (hasMarkedAsOfficialRace) {
                                        "Prova Oficial Salva em Planejamento!"
                                    } else {
                                        "Marcar como Prova Oficial (5K/10K/TAF)"
                                    },
                                    color = if (hasMarkedAsOfficialRace) Color(0xFF00E676) else Color(0xFFFFD600),
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Black,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Text(
                                text = if (hasMarkedAsOfficialRace) "Editar" else "Salvar",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }

                    if (showOfficialRaceConversionDialog) {
                        com.example.ui.components.SaveOfficialRaceDialog(
                            initialSession = previewSession.copy(
                                shoeName = selectedShoeName,
                                notes = notesInput
                            ),
                            availableShoes = availableShoes,
                            onSave = { record ->
                                onSaveAsOfficialRace(record)
                                hasMarkedAsOfficialRace = true
                                showOfficialRaceConversionDialog = false
                            },
                            onDismiss = { showOfficialRaceConversionDialog = false }
                        )
                    }
                }
            }
        },
        confirmButton = {
            val isFreeRunOrGentle = isGentleMode ||
                RunningProgramManager.isFreeOrRaceSessionType(sessionType) ||
                weekNumber <= 0
            val plannedRunSecForSave = remember(plan, durationSec, isFreeRunOrGentle) {
                if (isFreeRunOrGentle) {
                    0
                } else {
                    val raw = plan?.blocks?.filter { it.type == RunningBlockType.RUN }?.sumOf { it.durationSec } ?: 0
                    if (raw in 1..3599) raw else durationSec
                }
            }
            val plannedWalkSecForSave = remember(plan, isFreeRunOrGentle) {
                if (isFreeRunOrGentle) 0 else plan?.blocks?.filter { it.type == RunningBlockType.WALK }?.sumOf { it.durationSec } ?: 0
            }
            fun emitScientificTelemetry(notesValue: String?) {
                runCatching {
                    val rpeCr10Val = when {
                        isGentleMode -> 2
                        rpeTouchedManually -> selectedRpe.coerceIn(1, 10)
                        else -> null
                    }
                    val sRpeUaVal = com.example.data.model.calculateSrpeLoadUa(durationSec, rpeCr10Val)
                    if (isFreeRunOrGentle) {
                        onSaveScientificTelemetry?.invoke(rpeCr10Val, sRpeUaVal, null)
                        return@runCatching
                    }
                    val liveRouteJson = com.example.services.RunTracker.recordedPoints
                        .takeIf { it.isNotEmpty() }
                        ?.let { com.example.services.RunTracker.getRoutePointsJson() }
                    val sAnalytics = runCatching {
                        com.example.data.model.calculateSprintAnalytics(
                            com.example.data.model.RunningSession(
                                weekNumber = weekNumber,
                                dayNumber = dayNumber,
                                sessionType = sessionType,
                                durationSec = durationSec,
                                distanceMeters = if (distMeters > 0) distMeters else measuredDistanceMeters,
                                mainRunDurationSec = mainRunDurationSec.takeIf { it > 0 },
                                mainRunDistanceMeters = mainRunDistanceMeters.takeIf { it > 0 },
                                kmSplitsJson = if (recordedSplits.isNotEmpty()) recordedSplits.toJsonString() else null,
                                routePointsJson = liveRouteJson
                            ),
                            emptyList()
                        )
                    }.getOrNull()
                    val actRunSec = (sAnalytics?.runningTimeSec?.takeIf { it > 0 }
                        ?: mainRunDurationSec.takeIf { it > 0 }
                        ?: durationSec).coerceIn(0, durationSec)
                    val trackedUnintendedWalk = com.example.services.RunTracker.getTotalUnintendedWalkSec().coerceAtLeast(0)
                    val actWalkSec = (sAnalytics?.walkingTimeSec ?: 0).coerceAtLeast(0)
                    val extraWalk = (actWalkSec - plannedWalkSecForSave).coerceAtLeast(0)
                    val runDeficit = (plannedRunSecForSave - actRunSec).coerceAtLeast(0)
                    val unplWalkSec = maxOf(trackedUnintendedWalk, extraWalk, minOf(actWalkSec, runDeficit))
                        .coerceIn(0, durationSec)
                    val skippedRunBlocks = skippedIndices.count { idx ->
                        plan?.blocks?.getOrNull(idx)?.type == RunningBlockType.RUN
                    }
                    val audit = com.example.data.model.SessionExecutionAudit(
                        prescribedStructureLabel = sAnalytics?.structureSummary?.ifBlank { sessionType } ?: sessionType,
                        plannedWorkDurationSec = plannedRunSecForSave,
                        actualWorkRunningSec = actRunSec,
                        unplannedWalkDurationSec = unplWalkSec,
                        interruptedIntervalsCount = skippedRunBlocks,
                        reportedDeviationReasons = selectedDeviationReasons.toList(),
                        athleteDeviationNotes = notesValue
                    )
                    onSaveScientificTelemetry?.invoke(rpeCr10Val, sRpeUaVal, audit)
                }
            }
            var isSavingRun by remember { mutableStateOf(false) }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (onSaveAndShare != null) {
                    Button(
                        enabled = isDistanceValidToSave && !isSavingRun,
                        onClick = {
                            if (isSavingRun) return@Button
                            if (isUnusualPace && !saveWithoutDistance && !confirmedUnusualPace) {
                                confirmedUnusualPace = true
                                AudioSynth.playTickSound()
                                return@Button
                            }
                            isSavingRun = true
                            val finalDistMeters = if (saveWithoutDistance) {
                                0
                            } else if (distMeters > 0) {
                                distMeters
                            } else if (speedKmhFloat != null && speedKmhFloat > 0f) {
                                ((speedKmhFloat / 3600f) * durationSec * 1000f).safeRoundToInt()
                            } else {
                                0
                            }
                            val formattedNotes = if (environment == "TREADMILL" && speedKmhFloat != null && speedKmhFloat > 0f) {
                                val speedNote = "Velocidade na esteira: ${speedKmhFloat} km/h (Inclinação 1%)"
                                if (notesInput.isNotBlank()) "$speedNote | $notesInput" else speedNote
                            } else {
                                notesInput.ifBlank { null }
                            }
                            emitScientificTelemetry(formattedNotes)
                            onSaveAndShare(
                                finalDistMeters,
                                when {
                                    isGentleMode -> 2
                                    rpeTouchedManually -> selectedRpe.safeCoerceIn(1, 10)
                                    else -> 0
                                },
                                formattedNotes,
                                environment,
                                selectedPainResponse,
                                selectedShoeName
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 50.dp),
                        shape = RoundedCornerShape(24.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = RunColors.brand,
                            disabledContainerColor = RunColors.surface
                        )
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Compartilhar Conquista",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "COMPARTILHAR CONQUISTA",
                                color = Color.White,
                                fontWeight = FontWeight.Black,
                                fontSize = 13.5.sp,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Botão 1: DESCARTAR TREINO (não salva no histórico e não avança a planilha)
                    OutlinedButton(
                        enabled = !isSavingRun,
                        onClick = {
                            if (isSavingRun) return@OutlinedButton
                            AudioSynth.playTickSound()
                            (onDiscard ?: onDismiss)()
                        },
                        modifier = Modifier
                            .weight(1.05f)
                            .heightIn(min = 48.dp)
                            .testTag("btn_summary_discard_workout"),
                        shape = RoundedCornerShape(24.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                        border = BorderStroke(1.2.dp, RunColors.alert.copy(alpha = 0.75f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = RunColors.alert.copy(alpha = 0.12f),
                            contentColor = RunColors.alert
                        )
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Descartar Treino",
                                tint = RunColors.alert,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Descartar Treino",
                                color = RunColors.alert,
                                fontWeight = FontWeight.Black,
                                fontSize = 12.sp,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Botão 2: SALVAR / SALVAR MESMO ASSIM (principal)
                    val isZeroDisplacementManualSave = !wasAutoSaved && distMeters < 50 && measuredDistanceMeters < 50
                    Button(
                        enabled = isDistanceValidToSave && !isSavingRun,
                        onClick = {
                            if (isSavingRun) return@Button
                            if (isUnusualPace && !confirmedUnusualPace) {
                                confirmedUnusualPace = true
                                AudioSynth.playTickSound()
                                return@Button
                            }
                            isSavingRun = true
                            val finalDistMeters = if (saveWithoutDistance && distMeters <= 0) {
                                0
                            } else if (distMeters > 0) {
                                distMeters
                            } else if (speedKmhFloat != null && speedKmhFloat > 0f) {
                                ((speedKmhFloat / 3600f) * durationSec * 1000f).safeRoundToInt()
                            } else {
                                0
                            }
                            val formattedNotes = if (environment == "TREADMILL" && speedKmhFloat != null && speedKmhFloat > 0f) {
                                val speedNote = "Velocidade na esteira: ${speedKmhFloat} km/h (Inclinação 1%)"
                                if (notesInput.isNotBlank()) "$speedNote | $notesInput" else speedNote
                            } else {
                                notesInput.ifBlank { null }
                            }
                            emitScientificTelemetry(formattedNotes)
                            onSave(
                                finalDistMeters,
                                when {
                                    isGentleMode -> 2
                                    rpeTouchedManually -> selectedRpe.safeCoerceIn(1, 10)
                                    else -> 0
                                },
                                formattedNotes,
                                environment,
                                selectedPainResponse,
                                selectedShoeName
                            )
                        },
                        modifier = Modifier
                            .weight(if (isZeroDisplacementManualSave) 1.45f else 1.05f)
                            .heightIn(min = 48.dp)
                            .testTag("btn_summary_save"),
                        shape = RoundedCornerShape(24.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isUnusualPace && !confirmedUnusualPace) RunColors.phaseWarmup else RunColors.brand,
                            disabledContainerColor = RunColors.surface
                        )
                    ) {
                        Text(
                            text = when {
                                isSavingRun -> "SALVANDO..."
                                isUnusualPace && !confirmedUnusualPace -> "CONFIRMAR"
                                isZeroDisplacementManualSave -> "Salvar mesmo assim"
                                else -> "SALVAR"
                            },
                            color = if (isDistanceValidToSave && !isSavingRun) Color.White else RunColors.textSecondary,
                            fontWeight = FontWeight.Black,
                            fontSize = if (isZeroDisplacementManualSave) 12.5.sp else 13.5.sp,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
        },
        dismissButton = null,
        containerColor = RunColors.surface
    )
    }
}

/**
 * Identifica se um `RecordedKmSplit` pertence a uma volta de Pista 400m (`phaseTag` contendo `"LAP_400M"`).
 */
fun isTrack400mLapSplit(split: com.example.data.model.RecordedKmSplit): Boolean {
    val tag = split.phaseTag.orEmpty().uppercase(Locale.US)
    return tag.startsWith("LAP_400M") || split.splitLabel.uppercase(Locale.US).startsWith("LAP ")
}

/**
 * Extrai o tempo de descanso (em segundos) codificado no `phaseTag` de uma Lap de 400m (`"LAP_400M|REST:125"`).
 */
fun parseTrack400mRestSeconds(phaseTag: String?): Int? {
    if (phaseTag.isNullOrBlank()) return null
    val restPrefix = "REST:"
    val idx = phaseTag.indexOf(restPrefix, ignoreCase = true)
    if (idx < 0) return null
    val raw = phaseTag.substring(idx + restPrefix.length).takeWhile { it.isDigit() }
    return raw.toIntOrNull()
}

/**
 * Card de Tabela Clínica das Voltas de 400m (`Laps de 400m`), exibindo:
 * - Lap # (com destaque Dourado `#FFD600` para a melhor volta PR)
 * - Tempo do Tiro (400m)
 * - Pace Equivalente (`/km`)
 * - Tempo de Descanso de cada volta
 */
@Composable
fun Track400mLapsTableCard(
    splits: List<com.example.data.model.RecordedKmSplit>,
    title: String = "PARCIAIS • LAPS DE PISTA (400M)",
    modifier: Modifier = Modifier
) {
    val bestLapNumber = remember(splits) {
        splits.filter { it.durationSec > 0 }.minByOrNull { it.durationSec }?.splitNumber
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF151922)),
        border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.28f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
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
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = null,
                        tint = Color(0xFF00E5FF),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = title,
                        color = Color(0xFF00E5FF),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.8.sp
                    )
                }
                Text(
                    text = "${splits.size} ${if (splits.size == 1) "volta" else "voltas"}",
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            if (splits.isEmpty()) {
                Text(
                    text = "Nenhuma Lap de 400m concluída ainda. Corra 400m para registrar a Lap 1.",
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp
                )
            } else {
                // Cabeçalho da Tabela
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0D0F14), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "LAP (400M)",
                        color = Color(0xFF94A3B8),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1.1f)
                    )
                    Text(
                        text = "TIRO",
                        color = Color(0xFF94A3B8),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(0.95f)
                    )
                    Text(
                        text = "PACE EQ.",
                        color = Color(0xFF94A3B8),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1.0f)
                    )
                    Text(
                        text = "DESCANSO",
                        color = Color(0xFF94A3B8),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.End,
                        modifier = Modifier.weight(0.95f)
                    )
                }

                splits.forEachIndexed { index, split ->
                    val isPr = splits.size > 1 && split.splitNumber == bestLapNumber
                    val rowBg = if (index % 2 == 0) Color(0xFF151922) else Color(0xFF1A1F2B)
                    val accentColor = if (isPr) Color(0xFFFFD600) else Color(0xFF00E676)
                    val lapMin = split.durationSec / 60
                    val lapSec = split.durationSec % 60
                    val lapTimeStr = String.format(Locale.US, "%02d:%02d", lapMin, lapSec)
                    val paceMin = split.paceSecPerKm / 60
                    val paceSec = split.paceSecPerKm % 60
                    val paceStr = if (split.paceSecPerKm > 0) {
                        String.format(Locale.US, "%d'%02d\"/km", paceMin, paceSec)
                    } else "--:--"
                    val restSec = parseTrack400mRestSeconds(split.phaseTag)
                    val restStr = if (restSec != null && restSec > 0) {
                        String.format(Locale.US, "%02d:%02d", restSec / 60, restSec % 60)
                    } else if (index == splits.lastIndex) {
                        "Final"
                    } else {
                        "--:--"
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(rowBg, RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1.1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "LAP ${split.splitNumber}",
                                color = if (isPr) Color(0xFFFFD600) else Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black,
                                maxLines = 1,
                                softWrap = false
                            )
                            if (isPr) {
                                Surface(
                                    color = Color(0xFFFFD600).copy(alpha = 0.18f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "PR",
                                        color = Color(0xFFFFD600),
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Black,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }

                        Text(
                            text = lapTimeStr,
                            color = accentColor,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.weight(0.95f)
                        )

                        Text(
                            text = paceStr,
                            color = Color(0xFF00E5FF),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.weight(1.0f)
                        )

                        Text(
                            text = restStr,
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = androidx.compose.ui.text.style.TextAlign.End,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.weight(0.95f)
                        )
                    }
                    if (index < splits.lastIndex) {
                        HorizontalDivider(
                            thickness = 0.5.dp,
                            color = Color(0xFF262D3D)
                        )
                    }
                }
            }
        }
    }
}

/**
 * MODAL FULLSCREEN DE FREQUÊNCIA CARDÍACA E ZONAS DE ESFORÇO (`FullscreenHeartRateModal`):
 * Abre ao clicar tanto no Card de BPM no HUD ativo de corrida quanto no Ícone/Badge de Coração
 * no topo da tela principal do MAX RUN (ou no Dossiê Pós-Treino).
 * Exibe:
 * - BPM atual em destaque gigante
 * - Zona Cardíaca atual (`Z1` a `Z5`)
 * - `% da FCmáx calibrada` (`maxOf(220 - age, maxBpm, currentBpm)`)
 * - Pico (`maxBpm`) e Média (`avgBpm`) da sessão
 * - Gráfico/Barras das 5 zonas cardíacas (`Z1` a `Z5`)
 * - Controles de conexão, busca e simulação da cinta BLE (`HeartRateBleManager`)
 */
@Composable
fun FullscreenHeartRateModal(
    currentBpm: Int,
    avgBpm: Int?,
    maxBpm: Int?,
    athleteAge: Int?,
    calibratedMaxHrBpm: Int? = null,
    calibratedLt1Bpm: Int? = null,
    calibratedLt2Bpm: Int? = null,
    hrSamples: List<Int> = emptyList(),
    durationSeconds: Int = 0,
    onRequestBleScan: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val bleManager = remember(context) { com.example.services.HeartRateBleManager.getInstance(context) }
    val bleState by bleManager.connectionState.collectAsState()
    val liveBleBpm by bleManager.currentBpm.collectAsState()
    val connectedDeviceName by bleManager.connectedDeviceName.collectAsState()
    val discoveredDevices by bleManager.discoveredDevices.collectAsState()
    val isSmartwatchBridgeActive by bleManager.isSmartwatchBridgeActive.collectAsState()
    val linkedSmartwatchName by bleManager.linkedSmartwatchName.collectAsState()
    val linkedSmartwatchAddress by bleManager.linkedSmartwatchAddress.collectAsState()
    val smartwatchDailyMetrics by bleManager.smartwatchDailyMetrics.collectAsState()
    val isUsingSmartwatchBridgeBpm by bleManager.isUsingSmartwatchBridgeBpm.collectAsState()
    val gattStatusMessage by bleManager.gattStatusMessage.collectAsState()
    val activeGattTargetAddress by bleManager.activeGattTargetAddress.collectAsState()
    val isUuid180dClosedOnWatch by bleManager.isUuid180dClosedOnWatch.collectAsState()
    val hcCoroutineScope = rememberCoroutineScope()
    var hcStatusNotice by remember { mutableStateOf<String?>(null) }

    val hcPermissionLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract()
    ) {
        bleManager.syncSmartwatchHealthConnectMetrics()
        hcStatusNotice = "Sincronização com o Health Connect atualizada."
    }

    val triggerHealthConnectSync: () -> Unit = {
        AudioSynth.playTickSound()
        hcCoroutineScope.launch {
            runCatching {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                    hcStatusNotice = "Health Connect indisponível neste Android (requer Android 8.0+)."
                    return@launch
                }
                if (!bleManager.isHealthConnectAvailable()) {
                    val sdkStatus = runCatching {
                        HealthConnectClient.getSdkStatus(context)
                    }.getOrDefault(HealthConnectClient.SDK_UNAVAILABLE)
                    hcStatusNotice = when (sdkStatus) {
                        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED ->
                            "Health Connect requer instalação/atualização (status SDK: $sdkStatus)."
                        HealthConnectClient.SDK_UNAVAILABLE ->
                            "Health Connect indisponível neste aparelho (status SDK: $sdkStatus)."
                        else ->
                            "Health Connect indisponível (status SDK: $sdkStatus)."
                    }
                    return@launch
                }
                if (bleManager.hasHealthConnectPermissions()) {
                    hcStatusNotice = "Sincronizando dados reais do Health Connect..."
                    bleManager.syncSmartwatchHealthConnectMetrics()
                } else {
                    hcStatusNotice = "Solicitando permissões oficiais do Health Connect..."
                    hcPermissionLauncher.launch(bleManager.getHealthConnectRequiredPermissions())
                }
            }.onFailure { e ->
                hcStatusNotice = "Falha ao acessar Health Connect: ${e.message ?: "erro desconhecido"}"
            }
        }
    }

    LaunchedEffect(Unit) {
        bleManager.refreshBondedAndSystemConnectedDevices()
    }

    val displayBpm = when {
        liveBleBpm > 0 && bleManager.hasFreshRealBleHeartRate() -> liveBleBpm
        currentBpm > 0 && bleManager.hasFreshRealBleHeartRate() -> currentBpm
        else -> 0
    }

    val effectiveSamples = remember(hrSamples, displayBpm) {
        val valid = hrSamples.filter { it in 40..240 }
        if (valid.isNotEmpty()) valid else if (displayBpm in 40..240) listOf(displayBpm) else emptyList()
    }

    val sessionPeak = maxOf(maxBpm ?: 0, effectiveSamples.maxOrNull() ?: 0, displayBpm)
    val sessionAvg = avgBpm ?: if (effectiveSamples.isNotEmpty()) effectiveSamples.average().toInt() else displayBpm

    val validAge = athleteAge?.takeIf { it in 10..100 }
    val theoreticalMaxHr = validAge?.let { (220 - it.coerceIn(15, 95)).coerceAtLeast(150) }
    val calibratedMaxHr = remember(validAge, sessionPeak, calibratedMaxHrBpm) {
        com.example.data.model.calculateEffectiveMaxHr(
            ageYears = validAge,
            sessionMaxHrBpm = if (sessionPeak > 0) sessionPeak else null,
            calibratedMaxHrBpm = calibratedMaxHrBpm
        )
    }
    val isAutoCalibrated = theoreticalMaxHr != null && calibratedMaxHr > theoreticalMaxHr

    val enduranceSnap = remember(displayBpm, sessionAvg, calibratedMaxHr, calibratedLt1Bpm, calibratedLt2Bpm) {
        val refBpm = if (displayBpm > 0) displayBpm else sessionAvg
        com.example.data.model.calculateLiveBpmEnduranceSnapshot(
            currentBpm = refBpm,
            calibratedMaxHr = calibratedMaxHr,
            calibratedLt1Bpm = calibratedLt1Bpm,
            calibratedLt2Bpm = calibratedLt2Bpm
        )
    }

    val pctMaxHr = if (displayBpm > 0 && calibratedMaxHr > 0) {
        ((displayBpm.toFloat() / calibratedMaxHr.toFloat()) * 100f).toInt().coerceIn(30, 100)
    } else 0

    val activeZoneIndex = when {
        displayBpm <= 0 || calibratedMaxHr <= 0 -> -1
        pctMaxHr < 60 -> 0 // Z1
        pctMaxHr < 70 -> 1 // Z2
        pctMaxHr < 80 -> 2 // Z3
        pctMaxHr < 90 -> 3 // Z4
        else -> 4          // Z5
    }

    val estimatedDurationSec = remember(durationSeconds, effectiveSamples.size) {
        if (durationSeconds > 0) durationSeconds else (effectiveSamples.size * 5).coerceAtLeast(60)
    }

    val zoneDistributions = remember(effectiveSamples, sessionAvg, sessionPeak, calibratedMaxHr, estimatedDurationSec) {
        if (calibratedMaxHr > 0) {
            com.example.data.model.computeCoherent5ZoneDistribution(
                hrSamples = effectiveSamples,
                avgBpm = if (sessionAvg > 0) sessionAvg else null,
                maxBpm = if (sessionPeak > 0) sessionPeak else null,
                effectiveMaxHr = calibratedMaxHr,
                durationSeconds = estimatedDurationSec
            )
        } else {
            emptyList()
        }
    }

    data class HrZoneMeta(
        val zoneCode: String,
        val title: String,
        val subtitle: String,
        val minPct: Int,
        val maxPct: Int,
        val color: Color
    )

    val lt1Label = if (enduranceSnap.lt1Bpm > 0) "${enduranceSnap.lt1Bpm} bpm" else "--"
    val lt2Label = if (enduranceSnap.lt2Bpm > 0) "${enduranceSnap.lt2Bpm} bpm" else "--"
    val zonesMeta = listOf(
        HrZoneMeta("Z1", "Recuperação Ativa (< LT1)", "Substrato: 80% Gordura • Todo o dia", 50, 60, Color(0xFF00B0FF)),
        HrZoneMeta("Z2", "Queima de Gordura (<= LT1 $lt1Label)", "Substrato: 70% Gordura / 30% CHO • +150 min", 60, 72, Color(0xFF00E5FF)),
        HrZoneMeta("Z3", "Aeróbico / Tempo (LT1–LT2)", "Substrato: 75% Glicogênio • 60 a 150 min", 72, 80, Color(0xFF00E676)),
        HrZoneMeta("Z4", "Limiar Anaeróbico (Próximo LT2 $lt2Label)", "Substrato: 85% Glicogênio • 45 a 75 min", 80, 86, Color(0xFFFFB300)),
        HrZoneMeta("Z5", "VO2máx / Acima de LT2 (> $lt2Label)", "Substrato: 96% Glicogênio • Fadiga 2 a 7 min", 86, 100, Color(0xFFFF5252))
    )

    val currentZone = zonesMeta.getOrNull(activeZoneIndex)
    val mainAccentColor = currentZone?.color ?: Color(0xFF00E5FF)

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0D0F14))
                .statusBarsPadding()
                .navigationBarsPadding(),
            color = Color(0xFF0D0F14)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Cabeçalho Superior Fixo com Título e Botão [ X ] de Fechar no Topo Direito
                Surface(
                    color = Color(0xFF151922),
                    shadowElevation = 6.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "MONITOR CARDÍACO AO VIVO",
                                color = Color(0xFF00E5FF),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp
                            )
                            val headerMaxHrLabel = if (calibratedMaxHr > 0) "$calibratedMaxHr bpm" else "dados insuficientes"
                            val headerLtLabel = if (enduranceSnap.isAvailable && enduranceSnap.lt1Bpm > 0 && enduranceSnap.lt2Bpm > 0) {
                                "LT1: ${enduranceSnap.lt1Bpm} bpm • LT2: ${enduranceSnap.lt2Bpm} bpm"
                            } else {
                                "LT1: -- • LT2: --"
                            }
                            Text(
                                text = "FCmáx Calibrada: $headerMaxHrLabel • $headerLtLabel",
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        IconButton(
                            onClick = {
                                AudioSynth.playTickSound()
                                onDismiss()
                            },
                            modifier = Modifier
                                .size(40.dp)
                                .background(Color.White.copy(alpha = 0.08f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Fechar Monitor Cardíaco",
                                tint = Color.White
                            )
                        }
                    }
                }

                // Corpo Rolável com Preenchimento Inferior Seguro (48.dp acima da barra de navegação)
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 18.dp, vertical = 14.dp)
                        .padding(bottom = 48.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Card Principal: BPM Gigante + Zona Atual + % FCmáx
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF151922)),
                        border = BorderStroke(1.5.dp, mainAccentColor.copy(alpha = 0.45f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 20.dp, horizontal = 18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                color = mainAccentColor.copy(alpha = 0.16f),
                                shape = RoundedCornerShape(50),
                                border = BorderStroke(1.dp, mainAccentColor.copy(alpha = 0.4f))
                            ) {
                                val topStatusText = when {
                                    currentZone != null && isUsingSmartwatchBridgeBpm ->
                                        "${currentZone.zoneCode} • PONTE ${(linkedSmartwatchName ?: "SMARTWATCH").uppercase(Locale.getDefault())}"
                                    currentZone != null ->
                                        "${currentZone.zoneCode} • ${currentZone.title.uppercase(Locale.getDefault())}"
                                    else ->
                                        "AGUARDANDO SINAL CARDÍACO (BLE)"
                                }
                                Text(
                                    text = topStatusText,
                                    color = mainAccentColor,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 0.6.sp,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.Bottom,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = if (displayBpm > 0) "$displayBpm" else "--",
                                    color = Color.White,
                                    fontSize = 72.sp,
                                    fontWeight = FontWeight.Black
                                )
                                Column(
                                    modifier = Modifier.padding(bottom = 12.dp)
                                ) {
                                    Text(
                                        text = "♥ BPM",
                                        color = mainAccentColor,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                    Text(
                                        text = if (pctMaxHr > 0) "$pctMaxHr% FCmáx" else "--% FCmáx",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // Linha de Métricas da Sessão: Média Sessão, Pico Sessão e FC Máx Calibrada
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF0D0F14), RoundedCornerShape(12.dp))
                                    .padding(vertical = 10.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "MÉDIA SESSÃO",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = if (sessionAvg > 0) "$sessionAvg bpm" else "-- bpm",
                                        color = Color(0xFF00E5FF),
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Black,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .width(1.dp)
                                        .height(28.dp)
                                        .background(Color(0xFF262D3D))
                                )
                                Column(
                                    modifier = Modifier.weight(1f),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "PICO SESSÃO",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = if (sessionPeak > 0) "$sessionPeak bpm" else "-- bpm",
                                        color = Color(0xFFFFD600),
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Black,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .width(1.dp)
                                        .height(28.dp)
                                        .background(Color(0xFF262D3D))
                                )
                                Column(
                                    modifier = Modifier.weight(1f),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "FC MÁX CALIB.",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = if (calibratedMaxHr > 0) "$calibratedMaxHr bpm" else "idade não informada",
                                        color = Color(0xFF00E676),
                                        fontSize = if (calibratedMaxHr > 0) 15.sp else 10.sp,
                                        fontWeight = FontWeight.Black,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        maxLines = if (calibratedMaxHr > 0) 1 else 2,
                                        softWrap = calibratedMaxHr <= 0
                                    )
                                }
                            }
                        }
                    }

                    // Card Fisiológico de Resistência: LT1 / LT2, Substrato Energético (Gordura vs. Glicogênio) e Janela de Fadiga
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF151922)),
                        border = BorderStroke(1.dp, Color(0xFFFF6B00).copy(alpha = 0.35f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "FRONTEIRAS LT1 / LT2 & COMBUSTÍVEL",
                                    color = Color.White,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 0.5.sp
                                )
                                Surface(
                                    color = Color(0xFF0D0F14),
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
                                ) {
                                    Text(
                                        text = enduranceSnap.zone3ModelLabel,
                                        color = Color(0xFF00E5FF),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Black,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            // Barra Dual-Fuel ao vivo: Carboidrato (#FF6B00) vs Gordura (#00B4D8)
                            val hasFuelData = enduranceSnap.isAvailable && (enduranceSnap.carbPct > 0 || enduranceSnap.fatPct > 0)
                            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (hasFuelData) "CHO (Glicogênio): ${enduranceSnap.carbPct}%" else "CHO (Glicogênio): --",
                                        color = Color(0xFFFF6B00),
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Black,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                    Text(
                                        text = if (hasFuelData) "Gordura (Lipídios): ${enduranceSnap.fatPct}%" else "Gordura (Lipídios): --",
                                        color = Color(0xFF00B4D8),
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Black,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                                if (hasFuelData) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(10.dp)
                                            .clip(RoundedCornerShape(5.dp))
                                            .background(Color(0xFF0D0F14))
                                    ) {
                                        val cWeight = enduranceSnap.carbPct.coerceIn(5, 98) / 100f
                                        val fWeight = (1f - cWeight).coerceAtLeast(0.02f)
                                        Box(
                                            modifier = Modifier
                                                .weight(cWeight)
                                                .fillMaxHeight()
                                                .background(Color(0xFFFF6B00))
                                        )
                                        Box(
                                            modifier = Modifier
                                                .weight(fWeight)
                                                .fillMaxHeight()
                                                .background(Color(0xFF00B4D8))
                                        )
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF0D0F14), RoundedCornerShape(10.dp))
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier.weight(1.05f),
                                    horizontalAlignment = Alignment.Start
                                ) {
                                    Text(
                                        text = "LIMIARES (LT1/LT2)",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = if (enduranceSnap.isAvailable && enduranceSnap.lt1Bpm > 0 && enduranceSnap.lt2Bpm > 0) {
                                            "${enduranceSnap.lt1Bpm} / ${enduranceSnap.lt2Bpm} bpm"
                                        } else {
                                            "--"
                                        },
                                        color = Color.White,
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Black,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .width(1.dp)
                                        .height(24.dp)
                                        .background(Color(0xFF262D3D))
                                )
                                Column(
                                    modifier = Modifier.weight(0.95f),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "LACTATO EST.",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = enduranceSnap.lactateEstimateText,
                                        color = Color(0xFFFFB300),
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Black,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .width(1.dp)
                                        .height(24.dp)
                                        .background(Color(0xFF262D3D))
                                )
                                Column(
                                    modifier = Modifier.weight(1f),
                                    horizontalAlignment = Alignment.End
                                ) {
                                    Text(
                                        text = "JANELA FADIGA",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = enduranceSnap.timeToFatigueText,
                                        color = Color(0xFF00E676),
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Black,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    // Card das 5 Zonas de Frequência Cardíaca (Barras Proporcionais Reais 0% a 100%)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF151922)),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "ZONAS DE ESFORÇO CARDÍACO (Z1 – Z5)",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 0.5.sp
                                )
                                Text(
                                    text = "Limiares calibrados: LT1 ${enduranceSnap.lt1Bpm} bpm | LT2 ${enduranceSnap.lt2Bpm} bpm",
                                    color = Color(0xFF00E5FF),
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            zonesMeta.forEachIndexed { idx, zone ->
                                val isCurrentZone = idx == activeZoneIndex
                                val distItem = zoneDistributions.getOrNull(idx)
                                val pctInt = (distItem?.percentage ?: 0f).toInt().coerceIn(0, 100)
                                val formattedDur = distItem?.formattedDuration ?: "00:00"
                                val minBpm = distItem?.minBpm ?: ((calibratedMaxHr * zone.minPct) / 100f).toInt()
                                val maxBpmZone = distItem?.maxBpm ?: ((calibratedMaxHr * zone.maxPct) / 100f).toInt()
                                val barProgress = (pctInt / 100f).coerceIn(0f, 1f)

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            if (isCurrentZone) zone.color.copy(alpha = 0.12f) else Color(0xFF0D0F14),
                                            RoundedCornerShape(10.dp)
                                        )
                                        .padding(horizontal = 12.dp, vertical = 9.dp),
                                    verticalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Surface(
                                                color = zone.color.copy(alpha = 0.22f),
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text(
                                                    text = zone.zoneCode,
                                                    color = zone.color,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Black,
                                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                                )
                                            }
                                            Text(
                                                text = zone.title,
                                                color = if (isCurrentZone) Color.White else Color(0xFFE2E8F0),
                                                fontSize = 12.sp,
                                                fontWeight = if (isCurrentZone) FontWeight.Black else FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )
                                            if (isCurrentZone) {
                                                Surface(
                                                    color = zone.color.copy(alpha = 0.22f),
                                                    shape = RoundedCornerShape(4.dp)
                                                ) {
                                                    Text(
                                                        text = "ATUAL",
                                                        color = zone.color,
                                                        fontSize = 8.5.sp,
                                                        fontWeight = FontWeight.Black,
                                                        maxLines = 1,
                                                        softWrap = false,
                                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(6.dp))

                                        Text(
                                            text = "${pctInt}% ($formattedDur)",
                                            color = if (isCurrentZone || pctInt > 0) zone.color else Color(0xFF64748B),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Black,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }

                                    Text(
                                        text = "$minBpm – $maxBpmZone bpm (${zone.minPct}–${zone.maxPct}%) • ${zone.subtitle}",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 10.5.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(6.dp)
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(Color(0xFF1E2430))
                                    ) {
                                        if (barProgress > 0f) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth(barProgress)
                                                    .fillMaxHeight()
                                                    .clip(RoundedCornerShape(3.dp))
                                                    .background(zone.color)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Card de Controles de Conexão BLE (Cinta Peitoral GEHOD H500 + Smartwatch Redmi Watch 5 Health Connect)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF151922)),
                        border = BorderStroke(1.dp, Color(0xFF00B0FF).copy(alpha = 0.25f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "SENSORES CARDÍACOS & SMARTWATCH (DUAL-MODE)",
                                    color = Color(0xFF00B0FF),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 0.5.sp
                                )
                                Text(
                                    text = when {
                                        !gattStatusMessage.isNullOrBlank() -> gattStatusMessage!!
                                        bleState == com.example.services.HrConnectionState.CONNECTED ->
                                            "Sinal Ativo: ${connectedDeviceName ?: "Monitor BLE"} ($displayBpm bpm)"
                                        bleState == com.example.services.HrConnectionState.SCANNING ->
                                            "Buscando cintas BLE e smartwatches pareados..."
                                        bleState == com.example.services.HrConnectionState.CONNECTING ->
                                            "Conectando GATT (${connectedDeviceName ?: "sensor"})..."
                                        isSmartwatchBridgeActive ->
                                            if (displayBpm > 0) "Sinal Ativo: $displayBpm bpm" else "Aguardando transmissão 180D do relógio • Toque em CONECTAR AGORA"
                                        else ->
                                            "Cinta BLE desconectada (toque para buscar GEHOD H500)"
                                    },
                                    color = when {
                                        isUuid180dClosedOnWatch -> Color(0xFFFFB300)
                                        bleState == com.example.services.HrConnectionState.CONNECTED || (isSmartwatchBridgeActive && displayBpm > 0) ->
                                            Color(0xFF00E676)
                                        bleState == com.example.services.HrConnectionState.CONNECTING ->
                                            Color(0xFF00E5FF)
                                        else ->
                                            Color(0xFF94A3B8)
                                    },
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Button(
                                onClick = triggerHealthConnectSync,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00838F)),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .testTag("btn_connect_sync_health_connect_modal")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Sync,
                                    contentDescription = "Conectar ou sincronizar Health Connect",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "CONECTAR / SINCRONIZAR HEALTH CONNECT",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            if (!hcStatusNotice.isNullOrBlank()) {
                                Surface(
                                    color = Color(0xFF00E5FF).copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.35f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = hcStatusNotice!!,
                                        color = Color(0xFF80DEEA),
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                                    )
                                }
                            }

                            if (isSmartwatchBridgeActive) {
                                val liveWatchGattStatus = when {
                                    bleState == com.example.services.HrConnectionState.CONNECTING &&
                                        (activeGattTargetAddress.isNullOrBlank() || activeGattTargetAddress.equals(linkedSmartwatchAddress, ignoreCase = true)) ->
                                        gattStatusMessage ?: "Conectando GATT..."
                                    displayBpm > 0 ->
                                        "Sinal Ativo: $displayBpm bpm"
                                    isUuid180dClosedOnWatch ->
                                        gattStatusMessage ?: "Conectado via Bluetooth, aguardando você ativar 'Compartilhar FC' no relógio (UUID 180D fechado)"
                                    !gattStatusMessage.isNullOrBlank() ->
                                        gattStatusMessage!!
                                    else ->
                                        "Aguardando transmissão 180D do relógio"
                                }
                                Surface(
                                    onClick = {
                                        AudioSynth.playTickSound()
                                        val mac = linkedSmartwatchAddress
                                        if (!mac.isNullOrBlank()) {
                                            bleManager.connectDirectByMac(mac, linkedSmartwatchName, isSmartwatch = true)
                                        } else {
                                            val match = discoveredDevices.firstOrNull {
                                                it.category == com.example.services.WearableDeviceCategory.SMARTWATCH_BRIDGE
                                            }
                                            if (match != null) {
                                                bleManager.connectDirectByMac(match.address, match.name, isSmartwatch = true)
                                            }
                                        }
                                    },
                                    color = Color(0xFF00E676).copy(alpha = 0.1f),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, Color(0xFF00E676).copy(alpha = 0.35f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(
                                                modifier = Modifier.weight(1f),
                                                verticalArrangement = Arrangement.spacedBy(2.dp)
                                            ) {
                                                Text(
                                                    text = "${linkedSmartwatchName ?: "Redmi Watch 5"} • VINCULADO",
                                                    color = Color.White,
                                                    fontSize = 12.5.sp,
                                                    fontWeight = FontWeight.Black,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = liveWatchGattStatus,
                                                    color = when {
                                                        displayBpm > 0 -> Color(0xFF00E676)
                                                        isUuid180dClosedOnWatch -> Color(0xFFFFB300)
                                                        bleState == com.example.services.HrConnectionState.CONNECTING -> Color(0xFF00E5FF)
                                                        else -> Color(0xFF94A3B8)
                                                    },
                                                    fontSize = 10.5.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Surface(
                                                    onClick = {
                                                        AudioSynth.playTickSound()
                                                        val mac = linkedSmartwatchAddress
                                                        if (!mac.isNullOrBlank()) {
                                                            bleManager.connectDirectByMac(mac, linkedSmartwatchName, isSmartwatch = true)
                                                        } else {
                                                            val match = discoveredDevices.firstOrNull {
                                                                it.category == com.example.services.WearableDeviceCategory.SMARTWATCH_BRIDGE
                                                            }
                                                            if (match != null) {
                                                                bleManager.connectDirectByMac(match.address, match.name, isSmartwatch = true)
                                                            }
                                                        }
                                                    },
                                                    color = Color(0xFF00E676).copy(alpha = 0.2f),
                                                    shape = RoundedCornerShape(8.dp),
                                                    border = BorderStroke(1.dp, Color(0xFF00E676).copy(alpha = 0.65f))
                                                ) {
                                                    Text(
                                                        text = if (bleState == com.example.services.HrConnectionState.CONNECTING) "CONECTANDO..." else "CONECTAR AGORA",
                                                        color = Color(0xFF00E676),
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Black,
                                                        maxLines = 1,
                                                        softWrap = false,
                                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                                    )
                                                }
                                                Surface(
                                                    onClick = triggerHealthConnectSync,
                                                    color = Color(0xFF00E5FF).copy(alpha = 0.14f),
                                                    shape = RoundedCornerShape(8.dp),
                                                    border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.4f))
                                                ) {
                                                    Text(
                                                        text = "SYNC",
                                                        color = Color(0xFF00E5FF),
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Black,
                                                        maxLines = 1,
                                                        softWrap = false,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                                    )
                                                }
                                            }
                                        }

                                        if (displayBpm <= 0) {
                                            Surface(
                                                color = Color(0xFFFFB300).copy(alpha = 0.12f),
                                                shape = RoundedCornerShape(8.dp),
                                                border = BorderStroke(1.dp, Color(0xFFFFB300).copy(alpha = 0.4f)),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text(
                                                    text = if (isUuid180dClosedOnWatch && !gattStatusMessage.isNullOrBlank()) {
                                                        gattStatusMessage!!
                                                    } else {
                                                        "Dica Redmi/Xiaomi/Haylou: Ative 'Compartilhar FC / Broadcast HR' nas configurações do relógio e toque em CONECTAR AGORA para iniciar a leitura GATT 0x180D."
                                                    },
                                                    color = Color(0xFFFFE082),
                                                    fontSize = 10.5.sp,
                                                    lineHeight = 14.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            val filteredDiscoveredDevices = remember(
                                discoveredDevices,
                                connectedDeviceName
                            ) {
                                discoveredDevices.filterNot { dev ->
                                    !connectedDeviceName.isNullOrBlank() && dev.name.equals(connectedDeviceName, ignoreCase = true)
                                }
                            }

                            if (filteredDiscoveredDevices.isNotEmpty()) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    filteredDiscoveredDevices.forEach { dev ->
                                        val isWatch = dev.category == com.example.services.WearableDeviceCategory.SMARTWATCH_BRIDGE
                                        val isThisWatchLinked = isWatch && isSmartwatchBridgeActive && (
                                            (!linkedSmartwatchAddress.isNullOrBlank() && linkedSmartwatchAddress.equals(dev.address, ignoreCase = true)) ||
                                            (!linkedSmartwatchName.isNullOrBlank() && linkedSmartwatchName.equals(dev.name, ignoreCase = true))
                                        )
                                        val isConnectingThisDevice = bleState == com.example.services.HrConnectionState.CONNECTING &&
                                            activeGattTargetAddress.equals(dev.address, ignoreCase = true)
                                        val isConnectedThisDevice = bleState == com.example.services.HrConnectionState.CONNECTED &&
                                            (activeGattTargetAddress.equals(dev.address, ignoreCase = true) ||
                                                connectedDeviceName.equals(dev.name, ignoreCase = true))
                                        val deviceLiveGattLine = when {
                                            isConnectingThisDevice -> gattStatusMessage ?: "Conectando GATT..."
                                            isConnectedThisDevice && displayBpm > 0 -> "Sinal Ativo: $displayBpm bpm"
                                            isThisWatchLinked && displayBpm > 0 -> "Sinal Ativo: $displayBpm bpm"
                                            isThisWatchLinked && isUuid180dClosedOnWatch ->
                                                gattStatusMessage ?: "Conectado via Bluetooth, aguardando você ativar 'Compartilhar FC' no relógio (UUID 180D fechado)"
                                            isThisWatchLinked ->
                                                gattStatusMessage ?: "Aguardando transmissão 180D do relógio"
                                            dev.isBondedOrSystemConnected ->
                                                "Pareado/Conectado (${dev.address}) • Toque para conectar GATT direto"
                                            else ->
                                                "${dev.address} • Sinal ${dev.rssi} dBm"
                                        }
                                        Surface(
                                            onClick = {
                                                AudioSynth.playTickSound()
                                                bleManager.connectDirectByMac(
                                                    macAddress = dev.address,
                                                    deviceName = dev.name,
                                                    isSmartwatch = isWatch
                                                )
                                            },
                                            color = Color(0xFF0D0F14),
                                            shape = RoundedCornerShape(10.dp),
                                            border = BorderStroke(
                                                1.dp,
                                                if (isWatch) Color(0xFF00E676).copy(alpha = 0.4f) else Color(0xFF00E5FF).copy(alpha = 0.35f)
                                            ),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(
                                                    modifier = Modifier.weight(1f),
                                                    verticalArrangement = Arrangement.spacedBy(3.dp)
                                                ) {
                                                    Row(
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Surface(
                                                            color = if (isWatch) Color(0xFF00E5FF).copy(alpha = 0.16f) else Color(0xFF00E676).copy(alpha = 0.16f),
                                                            shape = RoundedCornerShape(4.dp)
                                                        ) {
                                                            Text(
                                                                text = if (isWatch) "SMARTWATCH" else "CINTA BLE",
                                                                color = if (isWatch) Color(0xFF00E5FF) else Color(0xFF00E676),
                                                                fontSize = 8.5.sp,
                                                                fontWeight = FontWeight.Black,
                                                                softWrap = false,
                                                                maxLines = 1,
                                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                            )
                                                        }
                                                        if (isThisWatchLinked) {
                                                            Surface(
                                                                color = Color(0xFF00E676).copy(alpha = 0.16f),
                                                                shape = RoundedCornerShape(4.dp)
                                                            ) {
                                                                Text(
                                                                    text = "VINCULADO",
                                                                    color = Color(0xFF00E676),
                                                                    fontSize = 8.5.sp,
                                                                    fontWeight = FontWeight.Black,
                                                                    softWrap = false,
                                                                    maxLines = 1,
                                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                                )
                                                            }
                                                        }
                                                    }
                                                    Text(
                                                        text = dev.name,
                                                        color = Color.White,
                                                        fontSize = 12.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Text(
                                                        text = deviceLiveGattLine,
                                                        color = when {
                                                            (isConnectedThisDevice || isThisWatchLinked) && displayBpm > 0 -> Color(0xFF00E676)
                                                            isThisWatchLinked && isUuid180dClosedOnWatch -> Color(0xFFFFB300)
                                                            isConnectingThisDevice -> Color(0xFF00E5FF)
                                                            else -> Color(0xFF94A3B8)
                                                        },
                                                        fontSize = 10.5.sp,
                                                        maxLines = 2,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                                Surface(
                                                    onClick = {
                                                        AudioSynth.playTickSound()
                                                        bleManager.connectDirectByMac(
                                                            macAddress = dev.address,
                                                            deviceName = dev.name,
                                                            isSmartwatch = isWatch
                                                        )
                                                    },
                                                    color = if (isThisWatchLinked) Color(0xFF00E676).copy(alpha = 0.18f) else Color(0xFF00E5FF).copy(alpha = 0.14f),
                                                    shape = RoundedCornerShape(8.dp),
                                                    border = BorderStroke(
                                                        1.dp,
                                                        if (isThisWatchLinked) Color(0xFF00E676).copy(alpha = 0.6f) else Color(0xFF00E5FF).copy(alpha = 0.45f)
                                                    )
                                                ) {
                                                    Text(
                                                        text = when {
                                                            isConnectingThisDevice -> "CONECTANDO..."
                                                            isThisWatchLinked -> "CONECTAR AGORA"
                                                            isWatch -> "VINCULAR"
                                                            else -> "CONECTAR"
                                                        },
                                                        color = if (isThisWatchLinked || isWatch) Color(0xFF00E676) else Color(0xFF00E5FF),
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Black,
                                                        maxLines = 1,
                                                        softWrap = false,
                                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                if (bleState == com.example.services.HrConnectionState.CONNECTED) {
                                    OutlinedButton(
                                        onClick = {
                                            AudioSynth.playTickSound()
                                            bleManager.disconnect()
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(44.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp),
                                        border = BorderStroke(1.dp, Color(0xFFFF5252).copy(alpha = 0.6f))
                                    ) {
                                        Text(
                                            text = "DESCONECTAR CINTA",
                                            color = Color(0xFFFF5252),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Black,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                } else {
                                    Button(
                                        onClick = {
                                            AudioSynth.playTickSound()
                                            if (onRequestBleScan != null) {
                                                onRequestBleScan()
                                            } else {
                                                bleManager.startScan()
                                            }
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(44.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00B0FF))
                                    ) {
                                        Text(
                                            text = if (bleState == com.example.services.HrConnectionState.SCANNING) "BUSCANDO..." else "BUSCAR SENSORES",
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Black,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    if (com.example.BuildConfig.DEBUG) {
                                        OutlinedButton(
                                            onClick = {
                                                AudioSynth.playTickSound()
                                                bleManager.startSimulation()
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(44.dp),
                                            shape = RoundedCornerShape(12.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp),
                                            border = BorderStroke(1.dp, Color(0xFF00E676).copy(alpha = 0.5f))
                                        ) {
                                            Text(
                                                text = "SIMULAR SINAL",
                                                color = Color(0xFF00E676),
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

                    Button(
                        onClick = {
                            AudioSynth.playTickSound()
                            onDismiss()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B))
                    ) {
                        Text(
                            text = "VOLTAR AO TREINO",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }
        }
    }
}

/**
 * Formata a duração exata de um bloco do HUD para leitura rápida em movimento:
 * - Segundos exatos em minutos: "8 min", "5 min"
 * - Minutos e segundos quebrados: "2m 30s", "1m 15s"
 * - Apenas segundos (< 60s): "45s", "30s"
 */
fun formatHudNextBlockDuration(durationSec: Int): String {
    val safeSec = durationSec.coerceAtLeast(0)
    val mins = safeSec / 60
    val secs = safeSec % 60
    return when {
        mins > 0 && secs == 0 -> "$mins min"
        mins > 0 && secs > 0 -> "${mins}m ${secs}s"
        else -> "${secs}s"
    }
}

/**
 * Pílula compacta de alta legibilidade exibida no HUD de Corrida logo acima de "GPS OK • BLE OK"
 * durante treinos estruturados/intervalados.
 * Não é exibida em Corrida Livre (mantendo a tela limpa).
 */
@Composable
fun HudNextBlockIndicatorPill(
    blocks: List<com.example.engine.RunningBlock>,
    currentBlockIndex: Int,
    skippedBlockIndices: Set<Int>,
    isStructuredWorkout: Boolean,
    modifier: Modifier = Modifier
) {
    if (!isStructuredWorkout || blocks.size <= 1) return

    val nextBlock = remember(blocks, currentBlockIndex, skippedBlockIndices) {
        blocks
            .drop(currentBlockIndex + 1)
            .withIndex()
            .firstOrNull { (offset, _) -> (currentBlockIndex + 1 + offset) !in skippedBlockIndices }
            ?.value
    }

    val (pillText, accentColor, iconVector) = remember(nextBlock) {
        if (nextBlock == null) {
            Triple(
                "ÚLTIMO BLOCO • RETA FINAL",
                Color(0xFFFFB300),
                Icons.Default.FastForward
            )
        } else {
            val durationLabel = formatHudNextBlockDuration(nextBlock.durationSec)
            when (nextBlock.type) {
                RunningBlockType.RUN -> Triple(
                    "PRÓXIMO BLOCO: CORRIDA • $durationLabel",
                    Color(0xFF00E5FF),
                    Icons.AutoMirrored.Filled.DirectionsRun
                )
                RunningBlockType.WALK -> Triple(
                    "PRÓXIMO BLOCO: CAMINHADA • $durationLabel",
                    Color(0xFF00E676),
                    Icons.Default.FastForward
                )
                RunningBlockType.COOL_DOWN -> Triple(
                    "PRÓXIMO BLOCO: DESAQUECIMENTO • $durationLabel",
                    Color(0xFFFFB300),
                    Icons.Default.FastForward
                )
                RunningBlockType.WARMUP -> Triple(
                    "PRÓXIMO BLOCO: AQUECIMENTO • $durationLabel",
                    Color(0xFFFFB300),
                    Icons.Default.FastForward
                )
            }
        }
    }

    Surface(
        color = accentColor.copy(alpha = 0.12f),
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.45f)),
        modifier = modifier.testTag("hud_next_block_indicator_pill")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = iconVector,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = pillText,
                color = accentColor,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.3.sp,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Barra compacta de Modo Pista 400m (TAF) para o HUD de Corrida:
 * - Exibe o contador de voltas ao vivo: "VOLTA X • Y m na volta atual (400m)"
 * - Oferece o botão compacto "+1 Volta (400m)" com trava anti-toque duplo de 45 segundos
 *   para aferir a distância acumulada para o múltiplo exato de 400m (laps * 400.0) ao cruzar a linha.
 */
@Composable
fun HudTrack400mLiveCalibrationCard(
    isEnabled: Boolean,
    displayedDistanceMeters: Int,
    cooldownRemainingSec: Int,
    onSnapLap400m: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!isEnabled) return

    val safeMeters = displayedDistanceMeters.coerceAtLeast(0)
    val currentLapNumber = (safeMeters / 400) + 1
    val metersInCurrentLap = safeMeters % 400
    val lapProgress = (metersInCurrentLap / 400f).coerceIn(0f, 1f)
    val isButtonLocked = cooldownRemainingSec > 0

    Surface(
        color = Color(0xFF101824),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.2.dp, Color(0xFF00E676).copy(alpha = 0.55f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("hud_track_400m_live_calibration_bar")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Flag,
                        contentDescription = null,
                        tint = Color(0xFF00E676),
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = "VOLTA $currentLapNumber • $metersInCurrentLap m na volta atual (400m)",
                        color = Color.White,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("hud_track_400m_lap_counter_text")
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Surface(
                    onClick = {
                        if (!isButtonLocked) {
                            onSnapLap400m()
                        }
                    },
                    enabled = !isButtonLocked,
                    color = if (isButtonLocked) {
                        Color(0xFF1B2433)
                    } else {
                        Color(0xFF00E676)
                    },
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(
                        1.dp,
                        if (isButtonLocked) Color(0xFF334155) else Color(0xFF00E676)
                    ),
                    modifier = Modifier
                        .heightIn(min = 32.dp)
                        .testTag("btn_hud_snap_lap_400m")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (!isButtonLocked) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = Color(0xFF06101E),
                                modifier = Modifier.size(13.dp)
                            )
                        }
                        Text(
                            text = if (isButtonLocked) {
                                "Aferido (${cooldownRemainingSec}s)"
                            } else {
                                "+1 Volta (400m)"
                            },
                            color = if (isButtonLocked) Color(0xFF94A3B8) else Color(0xFF06101E),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }

            // Barra de progresso sutil da volta de 400m
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFF0B0F17))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(lapProgress)
                        .fillMaxHeight()
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFF00B0FF), Color(0xFF00E676))
                            )
                        )
                )
            }
        }
    }
}



