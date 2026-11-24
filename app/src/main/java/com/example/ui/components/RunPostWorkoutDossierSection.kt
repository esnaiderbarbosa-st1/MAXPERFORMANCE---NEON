package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.KmSplit
import com.example.data.model.RunPhysiologyDossier
import com.example.data.model.RunTelemetrySample
import com.example.data.model.RunningSession
import com.example.data.model.SprintAnalytics
import com.example.data.model.formatPaceMinSecQuote
import com.example.data.model.hasHrCurve
import com.example.data.model.hasHrZones
import com.example.data.model.hrCurveUnavailableMessage
import com.example.data.model.safeCoerceIn
import com.example.data.model.safeRoundToInt
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val DossierCardDark = Color(0xFF151922)
private val DossierCardBorderDark = Color(0xFF232A38)
private val DossierCyan = Color(0xFF00E5FF)
private val DossierBlue = Color(0xFF00B0FF)
private val DossierGreen = Color(0xFF00E676)
private val DossierRed = Color(0xFFFF5252)
private val DossierAmber = Color(0xFFFFCA28)
private val DossierOrange = Color(0xFFFF9800)
private val DossierTextSecondaryDark = Color(0xFF94A3B8)

/**
 * BLOCO 1 — CABEÇALHO MESTRE + GRADE 2x3 DE MÉTRICAS PRINCIPAIS (ETAPA 1).
 * - Selo "MAX RUN | Corrida ao ar livre"
 * - Distância em destaque (32.sp Black) + nome do atleta (Esnaider Barbosa) e data/hora formatada
 * - Grade 2x3: Tempo (HH:MM:SS), Calorias (Ativas), Total de kcal (Ativas + Basal), Média de ritmo, FC média, Passos.
 */
@Composable
fun RunDossierMasterHeaderAnd2x3Grid(
    dossier: RunPhysiologyDossier,
    session: RunningSession,
    sprintAnalytics: SprintAnalytics,
    isLightMode: Boolean = false,
    prevSession: RunningSession? = null,
    nextSession: RunningSession? = null,
    onSelectSession: ((RunningSession) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val ptBr = remember { Locale("pt", "BR") }
    val shortDateFormat = remember { SimpleDateFormat("dd/MM", ptBr) }

    val cardBg = if (isLightMode) Color.White else DossierCardDark
    val cardBorder = if (isLightMode) Color(0xFFE2E8F0) else DossierCardBorderDark
    val primaryText = if (isLightMode) Color(0xFF0F172A) else Color.White
    val secondaryText = if (isLightMode) Color(0xFF64748B) else DossierTextSecondaryDark
    val accentCyan = if (isLightMode) Color(0xFF0288D1) else DossierCyan
    val accentBlue = if (isLightMode) Color(0xFF0288D1) else DossierBlue

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Topo com Selo "MAX RUN | Corrida ao ar livre" + Navegação Rápida de Sessão
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = accentBlue.copy(alpha = 0.14f),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, accentCyan.copy(alpha = 0.45f)),
                modifier = Modifier.weight(1f, fill = false)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DirectionsRun,
                        contentDescription = null,
                        tint = accentCyan,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = dossier.badgeTitle,
                        color = accentCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (onSelectSession != null) {
                Spacer(modifier = Modifier.width(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val hasPrev = prevSession != null
                    val hasNext = nextSession != null

                    Surface(
                        onClick = { prevSession?.let(onSelectSession) },
                        enabled = hasPrev,
                        color = if (hasPrev) accentBlue.copy(alpha = 0.12f) else Color.Transparent,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(
                            1.dp,
                            if (hasPrev) accentBlue.copy(alpha = 0.35f) else cardBorder.copy(alpha = 0.45f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ChevronLeft,
                                contentDescription = "Corrida anterior",
                                tint = if (hasPrev) accentCyan else secondaryText.copy(alpha = 0.32f),
                                modifier = Modifier.size(14.dp)
                            )
                            if (prevSession != null) {
                                Text(
                                    text = shortDateFormat.format(Date(prevSession.date)),
                                    color = accentCyan,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }

                    Surface(
                        color = accentBlue.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, accentBlue.copy(alpha = 0.4f))
                    ) {
                        val navBadgeText = if (com.example.engine.RunningProgramManager.isEffectiveFreeRun(session) || session.weekNumber <= 0) {
                            com.example.engine.RunningProgramManager.getBadgeLabelForSession(session)
                        } else {
                            "S${session.weekNumber} • D${session.dayNumber}"
                        }
                        Text(
                            text = navBadgeText,
                            color = accentCyan,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }

                    Surface(
                        onClick = { nextSession?.let(onSelectSession) },
                        enabled = hasNext,
                        color = if (hasNext) accentBlue.copy(alpha = 0.12f) else Color.Transparent,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(
                            1.dp,
                            if (hasNext) accentBlue.copy(alpha = 0.35f) else cardBorder.copy(alpha = 0.45f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            if (nextSession != null) {
                                Text(
                                    text = shortDateFormat.format(Date(nextSession.date)),
                                    color = accentCyan,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = "Próxima corrida",
                                tint = if (hasNext) accentCyan else secondaryText.copy(alpha = 0.32f),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }

        // 2. Bloco Principal de Distância (32.sp Black) + Atleta (Esnaider Barbosa) + Data/Hora
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = String.format(ptBr, "%.2f", dossier.distanceKm),
                        color = primaryText,
                        fontSize = 32.sp,
                        lineHeight = 36.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        softWrap = false
                    )
                    Text(
                        text = "km",
                        color = accentCyan,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(bottom = 3.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = dossier.athleteName,
                        color = primaryText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = dossier.formattedDateTime,
                        color = secondaryText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (dossier.hasMainRunIsolation) {
                val totalDistStr = String.format(ptBr, "%.2f km", dossier.totalDistanceWithWarmupKm)
                val totalM = dossier.totalDurationWithWarmupSeconds / 60
                val totalS = dossier.totalDurationWithWarmupSeconds % 60
                val totalTimeStr = String.format(Locale.US, "%02d:%02d", totalM, totalS)
                val warmupCalStr = if (dossier.isWeightMissingForCalories) "Peso não informado" else "${dossier.totalCaloriesWithWarmupKcal} kcal"
                val warmupStepsStr = if (dossier.totalStepsWithWarmup > 0) "${dossier.totalStepsWithWarmup} passos" else "-- passos"
                Text(
                    text = "Total c/ Aq.+Desaq.: $totalDistStr ($totalTimeStr • $warmupCalStr • $warmupStepsStr)",
                    color = secondaryText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (sprintAnalytics.structureSummary.isNotBlank()) {
                Text(
                    text = sprintAnalytics.structureSummary,
                    color = accentCyan.copy(alpha = 0.9f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // 3. Grade 2x3 de Métricas Principais no Card #151922
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("run_dossier_2x3_grid_card"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = cardBg),
            border = BorderStroke(1.dp, cardBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Linha 1: Tempo (HH:MM:SS) | Calorias (Ativas) | Total kcal (Ativas + Basal)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    DossierGridMetricCell(
                        value = dossier.formattedDurationHhMmSs,
                        label = "Tempo",
                        valueColor = primaryText,
                        labelColor = secondaryText,
                        modifier = Modifier.weight(1f)
                    )
                    DossierGridMetricCell(
                        value = if (dossier.isWeightMissingForCalories) "Peso não informado" else "${dossier.activeCaloriesKcal} kcal",
                        label = "Calorias",
                        valueColor = DossierGreen,
                        labelColor = secondaryText,
                        modifier = Modifier.weight(1f)
                    )
                    DossierGridMetricCell(
                        value = if (dossier.isWeightMissingForCalories) "Peso não informado" else "${dossier.totalCaloriesKcal} kcal",
                        label = "Total kcal",
                        valueColor = accentCyan,
                        labelColor = secondaryText,
                        modifier = Modifier.weight(1f)
                    )
                }

                HorizontalDivider(color = cardBorder, thickness = 1.dp)

                // Linha 2: Ritmo médio (M'SS") | FC média (XXX bpm) | Passos (XXXX)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    DossierGridMetricCell(
                        value = dossier.formattedAvgPace,
                        label = "Ritmo médio",
                        valueColor = primaryText,
                        labelColor = secondaryText,
                        modifier = Modifier.weight(1f)
                    )
                    DossierGridMetricCell(
                        value = if (dossier.avgHeartRateBpm > 0) "${dossier.avgHeartRateBpm} bpm" else "-- bpm",
                        label = "FC média",
                        valueColor = DossierRed,
                        labelColor = secondaryText,
                        modifier = Modifier.weight(1f)
                    )
                    DossierGridMetricCell(
                        value = if (dossier.totalSteps > 0) String.format(ptBr, "%,d", dossier.totalSteps) else "--",
                        label = "Passos",
                        valueColor = primaryText,
                        labelColor = secondaryText,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun DossierGridMetricCell(
    value: String,
    label: String,
    valueColor: Color,
    labelColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = value,
            color = valueColor,
            fontSize = if (value.length >= 8) 14.5.sp else 16.sp,
            fontWeight = FontWeight.Black,
            fontStyle = FontStyle.Italic,
            letterSpacing = (-0.5).sp,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = label,
            color = labelColor,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * BLOCO 2 — CARD DE FREQUÊNCIA CARDÍACA (CURVA CONTÍNUA + BARRAS DAS 5 ZONAS CARDÍACAS).
 * - Cabeçalho com ícone de coração vermelho (#FF5252), título "Frequência cardíaca (BPM)"
 * - Resumo duplo: BPM Médio vs. BPM Máximo (e badge discreto caso estimado por biometria/pace)
 * - Gráfico de Linha Contínua (Canvas) com linha tracejada da FC Média e eixo X temporal
 * - 5 Barras Horizontais: Leve (#29B6F6), Intensa (#00E676), Aeróbica (#FFCA28), Anaeróbica (#FF7043), VO2 máximo (#FF5252)
 */
@Composable
fun RunDossierHeartRateCard(
    dossier: RunPhysiologyDossier,
    isLightMode: Boolean = false,
    onOpenDetailedModal: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val cardBg = if (isLightMode) Color.White else DossierCardDark
    val cardBorder = if (isLightMode) Color(0xFFE2E8F0) else DossierCardBorderDark
    val primaryText = if (isLightMode) Color(0xFF0F172A) else Color.White
    val secondaryText = if (isLightMode) Color(0xFF64748B) else DossierTextSecondaryDark

    val samples = dossier.telemetrySamples
    val hrSamples = remember(samples) { samples.filter { it.heartRateBpm in 45..240 } }
    val hasCurve = remember(hrSamples) { hasHrCurve(hrSamples.map { it.heartRateBpm }) }
    val hasZones = remember(dossier.hrZones5) { hasHrZones(dossier) }
    val hasSummaryHr = dossier.avgHeartRateBpm > 0 || dossier.maxHeartRateBpm > 0
    var selectedSampleIdx by remember(hrSamples) { mutableStateOf<Int?>(null) }
    var chartWidthPx by remember { mutableFloatStateOf(1f) }

    val xLabels = remember(dossier.durationSeconds) {
        buildFourTimeMarkers(dossier.durationSeconds)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onOpenDetailedModal != null) {
                    Modifier.clickable { onOpenDetailedModal() }
                } else {
                    Modifier
                }
            )
            .testTag("run_dossier_hr_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(1.dp, cardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Cabeçalho
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
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(DossierRed.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = "Frequência cardíaca",
                            tint = DossierRed,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Text(
                        text = "Frequência cardíaca (BPM)",
                        color = primaryText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (hasSummaryHr) {
                        Surface(
                            color = DossierGreen.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(0.5.dp, DossierGreen.copy(alpha = 0.4f))
                        ) {
                            val hrBadgeLabel = dossier.dataSourceLabel.substringAfter(
                                "GPS + ",
                                dossier.dataSourceLabel.substringAfter("• ", "FC registrada")
                            )
                            Text(
                                text = hrBadgeLabel,
                                color = DossierGreen,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    if (onOpenDetailedModal != null) {
                        Icon(
                            imageVector = Icons.Default.OpenInFull,
                            contentDescription = "Ampliar análise cardíaca",
                            tint = secondaryText,
                            modifier = Modifier
                                .size(16.dp)
                                .clickable { onOpenDetailedModal() }
                        )
                    }
                }
            }

            // 2. Resumo Duplo: BPM Médio vs. BPM Máximo
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = if (dossier.avgHeartRateBpm > 0) "${dossier.avgHeartRateBpm}" else "--",
                                color = primaryText,
                                fontSize = 26.sp,
                                lineHeight = 28.sp,
                                fontWeight = FontWeight.Black,
                                maxLines = 1,
                                softWrap = false
                            )
                            Text(
                                text = "bpm",
                                color = secondaryText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(bottom = 3.dp)
                            )
                        }
                        Text(
                            text = "BPM Médio",
                            color = secondaryText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = if (dossier.maxHeartRateBpm > 0) "${dossier.maxHeartRateBpm}" else "--",
                                color = DossierRed,
                                fontSize = 26.sp,
                                lineHeight = 28.sp,
                                fontWeight = FontWeight.Black,
                                maxLines = 1,
                                softWrap = false
                            )
                            Text(
                                text = "bpm",
                                color = secondaryText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(bottom = 3.dp)
                            )
                        }
                        Text(
                            text = "BPM Máximo",
                            color = secondaryText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                // Tooltip ao tocar/arrastar no gráfico
                val activeSample = selectedSampleIdx?.let { hrSamples.getOrNull(it) }
                if (activeSample != null) {
                    val m = activeSample.elapsedSeconds / 60
                    val s = activeSample.elapsedSeconds % 60
                    Surface(
                        color = DossierRed.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, DossierRed.copy(alpha = 0.45f))
                    ) {
                        Text(
                            text = String.format(Locale.US, "%02d:%02d • %d bpm", m, s, activeSample.heartRateBpm),
                            color = primaryText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // 3. Gráfico de Linha Contínua (Canvas) com Linha Tracejada da FC Média
            if (!hasCurve) {
                Text(
                    text = hrCurveUnavailableMessage(dossier.avgHeartRateBpm, dossier.maxHeartRateBpm),
                    color = secondaryText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                )
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val minSampleBpm = hrSamples.minOf { it.heartRateBpm }
                    val maxSampleBpm = hrSamples.maxOf { it.heartRateBpm }
                    val minBpmAxis = minOf(85, ((minSampleBpm - 5) / 10) * 10).coerceAtLeast(40)
                    val maxBpmAxis = maxOf(minBpmAxis + 30, ((maxOf(dossier.maxHeartRateBpm, maxSampleBpm) + 9) / 10) * 10)
                    val midBpmAxis = (minBpmAxis + maxBpmAxis) / 2

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(148.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .onSizeChanged { chartWidthPx = it.width.toFloat().coerceAtLeast(1f) }
                                .pointerInput(hrSamples) {
                                    detectTapGestures { offset ->
                                        if (hrSamples.size >= 2) {
                                            val frac = (offset.x / chartWidthPx.coerceAtLeast(1f)).safeCoerceIn(0f, 1f)
                                            selectedSampleIdx = (frac * (hrSamples.size - 1)).safeRoundToInt().safeCoerceIn(0, hrSamples.lastIndex)
                                        }
                                    }
                                }
                                .pointerInput(hrSamples) {
                                    detectHorizontalDragGestures(
                                        onDragEnd = { selectedSampleIdx = null },
                                        onDragCancel = { selectedSampleIdx = null }
                                    ) { change, _ ->
                                        change.consume()
                                        if (hrSamples.size >= 2) {
                                            val frac = (change.position.x / chartWidthPx.coerceAtLeast(1f)).safeCoerceIn(0f, 1f)
                                            selectedSampleIdx = (frac * (hrSamples.size - 1)).safeRoundToInt().safeCoerceIn(0, hrSamples.lastIndex)
                                        }
                                    }
                                }
                        ) {
                            ContinuousHrLineCanvas(
                                samples = hrSamples,
                                avgHr = dossier.avgHeartRateBpm,
                                minBpmAxis = minBpmAxis.toFloat(),
                                maxBpmAxis = maxBpmAxis.toFloat(),
                                selectedIdx = selectedSampleIdx,
                                isLightMode = isLightMode
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Eixo Y compacto à direita
                        Column(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(30.dp),
                            verticalArrangement = Arrangement.SpaceBetween,
                            horizontalAlignment = Alignment.End
                        ) {
                            Text(
                                text = "$maxBpmAxis",
                                color = secondaryText,
                                fontSize = 10.sp,
                                maxLines = 1,
                                softWrap = false
                            )
                            Text(
                                text = if (dossier.avgHeartRateBpm > 0) "${dossier.avgHeartRateBpm}" else "--",
                                color = DossierRed.copy(alpha = 0.9f),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false
                            )
                            Text(
                                text = "$midBpmAxis",
                                color = secondaryText,
                                fontSize = 10.sp,
                                maxLines = 1,
                                softWrap = false
                            )
                            Text(
                                text = "$minBpmAxis",
                                color = secondaryText,
                                fontSize = 10.sp,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }

                    // Marcações de tempo no Eixo X
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 38.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        xLabels.forEach { tick ->
                            Text(
                                text = tick,
                                color = secondaryText,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }

            if (hasCurve || hasZones) {
                HorizontalDivider(color = cardBorder, thickness = 1.dp)

                // 4. Barras Horizontais das 5 Zonas Cardíacas
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
                            text = "Zonas de Frequência Cardíaca (5 Zonas)",
                            color = secondaryText,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (hasZones) {
                            Text(
                                text = "% (mm:ss)",
                                color = secondaryText,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }

                    if (!hasZones) {
                        Text(
                            text = "Zonas indisponíveis",
                            color = secondaryText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        )
                    } else dossier.hrZones5.forEach { zoneItem ->
                        val rawColor = Color(zoneItem.colorHex)
                        val barColor = if (isLightMode && zoneItem.colorHex == 0xFFFFCA28L) {
                            Color(0xFFD97706)
                        } else {
                            rawColor
                        }
                        val pctInt = zoneItem.percentage.safeRoundToInt().safeCoerceIn(0, 100)
                        val fraction = if (pctInt <= 0) 0f else (pctInt / 100f).safeCoerceIn(0f, 1f)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(barColor, CircleShape)
                            )

                            Text(
                                text = "Z${zoneItem.zoneIndex} ${zoneItem.label}",
                                color = primaryText,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.width(96.dp)
                            )

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (isLightMode) Color(0xFFE2E8F0) else Color(0xFF1F2533))
                            ) {
                                if (fraction > 0f) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .fillMaxWidth(fraction)
                                            .background(barColor, RoundedCornerShape(4.dp))
                                    )
                                }
                            }

                            Text(
                                text = "${pctInt}% (${zoneItem.formattedDuration})",
                                color = primaryText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.End,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.width(82.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ContinuousHrLineCanvas(
    samples: List<RunTelemetrySample>,
    avgHr: Int,
    minBpmAxis: Float,
    maxBpmAxis: Float,
    selectedIdx: Int?,
    isLightMode: Boolean
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val topPad = 8.dp.toPx()
        val bottomPad = 6.dp.toPx()
        val plotH = (h - topPad - bottomPad).coerceAtLeast(1f)
        val range = (maxBpmAxis - minBpmAxis).coerceAtLeast(20f)

        fun bpmToY(bpm: Float): Float {
            val norm = ((bpm - minBpmAxis) / range).safeCoerceIn(0f, 1f)
            return topPad + plotH * (1f - norm)
        }

        val gridColor = if (isLightMode) Color(0xFFCBD5E1) else Color.White.copy(alpha = 0.08f)
        val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)

        // Linhas horizontais de grade
        listOf(0f, 0.5f, 1f).forEach { frac ->
            val y = topPad + plotH * frac
            drawLine(
                color = gridColor,
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = 1.dp.toPx()
            )
        }

        // Linha guia horizontal tracejada da FC Média (apenas quando há FC real medida)
        if (avgHr > 0) {
            val avgY = bpmToY(avgHr.toFloat())
            drawLine(
                color = DossierRed.copy(alpha = 0.65f),
                start = Offset(0f, avgY),
                end = Offset(w, avgY),
                strokeWidth = 1.2.dp.toPx(),
                pathEffect = dashEffect
            )
        }

        val validHrSamples = samples.filter { it.heartRateBpm > 0 }
        if (validHrSamples.size >= 2) {
            val stepX = w / (validHrSamples.size - 1).coerceAtLeast(1)
            val points = validHrSamples.mapIndexed { idx, s ->
                Offset(idx * stepX, bpmToY(s.heartRateBpm.toFloat()))
            }
            val firstPt = points.firstOrNull() ?: Offset.Zero
            val lastPt = points.lastOrNull() ?: Offset.Zero

            val linePath = Path().apply {
                moveTo(firstPt.x, firstPt.y)
                for (i in 0 until points.size - 1) {
                    val p0 = points[i]
                    val p1 = points[i + 1]
                    val midX = (p0.x + p1.x) / 2f
                    val midY = (p0.y + p1.y) / 2f
                    if (i == 0) {
                        lineTo(midX, midY)
                    } else {
                        quadraticTo(p0.x, p0.y, midX, midY)
                    }
                }
                lineTo(lastPt.x, lastPt.y)
            }

            val fillPath = Path().apply {
                addPath(linePath)
                lineTo(lastPt.x, topPad + plotH)
                lineTo(firstPt.x, topPad + plotH)
                close()
            }

            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        DossierRed.copy(alpha = 0.30f),
                        DossierRed.copy(alpha = 0.03f)
                    ),
                    startY = topPad,
                    endY = topPad + plotH
                )
            )

            drawPath(
                path = linePath,
                color = DossierRed,
                style = Stroke(
                    width = 2.4.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )

            if (selectedIdx != null && selectedIdx in points.indices) {
                val pt = points[selectedIdx]
                drawLine(
                    color = Color.White.copy(alpha = 0.75f),
                    start = Offset(pt.x, topPad),
                    end = Offset(pt.x, topPad + plotH),
                    strokeWidth = 1.2.dp.toPx(),
                    pathEffect = dashEffect
                )
                drawCircle(
                    color = DossierRed.copy(alpha = 0.30f),
                    radius = 8.dp.toPx(),
                    center = pt
                )
                drawCircle(
                    color = Color.White,
                    radius = 4.5.dp.toPx(),
                    center = pt
                )
                drawCircle(
                    color = DossierRed,
                    radius = 3.dp.toPx(),
                    center = pt
                )
            }
        }
    }
}

/**
 * BLOCO 3 — CARD DE RITMO (GRÁFICO DE ÁREA DOS TIROS/CAMINHADAS).
 * - Cabeçalho com ícone + título "Ritmo" e resumo duplo: Média de ritmo (M'SS") vs. Ritmo máx (M'SS" - pico mais rápido).
 * - Gráfico de Área em Canvas: curva de pace invertida (ritmos mais rápidos no topo) preenchida com Brush.verticalGradient
 *   em Verde/Ciano (#00E676 / #00E5FF), evidenciando claramente os blocos de corrida/tiros (picos) vs. caminhadas (vales),
 *   com linha média tracejada horizontal.
 */
@Composable
fun RunDossierPaceAreaChartCard(
    dossier: RunPhysiologyDossier,
    isLightMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    val cardBg = if (isLightMode) Color.White else DossierCardDark
    val cardBorder = if (isLightMode) Color(0xFFE2E8F0) else DossierCardBorderDark
    val primaryText = if (isLightMode) Color(0xFF0F172A) else Color.White
    val secondaryText = if (isLightMode) Color(0xFF64748B) else DossierTextSecondaryDark

    val samples = dossier.telemetrySamples
    val paceSamples = remember(samples) { samples.filter { it.paceSecPerKm > 0f } }
    var selectedSampleIdx by remember(paceSamples) { mutableStateOf<Int?>(null) }
    var chartWidthPx by remember { mutableFloatStateOf(1f) }

    val xLabels = remember(dossier.durationSeconds) {
        buildFourTimeMarkers(dossier.durationSeconds)
    }

    val fastestPaceSec = remember(paceSamples, dossier.maxPaceSecPerKm) {
        val sampleMin = paceSamples.minOfOrNull { it.paceSecPerKm.safeRoundToInt() }
        val rawFastest = listOfNotNull(
            dossier.maxPaceSecPerKm.takeIf { it > 0 },
            sampleMin?.takeIf { it > 0 }
        ).minOrNull() ?: 0
        if (rawFastest > 0) rawFastest.safeCoerceIn(140, 3600) else 0
    }
    val slowestPaceSec = remember(paceSamples, dossier.avgPaceSecPerKm, fastestPaceSec) {
        if (fastestPaceSec <= 0) {
            0
        } else {
            val sampleMax = paceSamples.maxOfOrNull { it.paceSecPerKm.safeRoundToInt() } ?: fastestPaceSec
            val avgBound = dossier.avgPaceSecPerKm.takeIf { it > 0 }?.let { (it * 1.25f).safeRoundToInt() } ?: sampleMax
            maxOf(sampleMax, avgBound, fastestPaceSec + 30).safeCoerceIn(fastestPaceSec + 30, maxOf(fastestPaceSec + 30, 3600))
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("run_dossier_pace_area_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(1.dp, cardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Cabeçalho
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
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(DossierGreen.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = "Ritmo",
                            tint = DossierGreen,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Text(
                        text = "Ritmo",
                        color = primaryText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                }

                val bTitleUpper = dossier.badgeTitle.uppercase()
                val isContinuousDossier = !dossier.hasMainRunIsolation ||
                    bTitleUpper.contains("LIVRE") ||
                    bTitleUpper.contains("5K") ||
                    bTitleUpper.contains("10K") ||
                    bTitleUpper.contains("MODO LEVE") ||
                    bTitleUpper.contains("CAMINHADA")
                Surface(
                    color = DossierCyan.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(0.5.dp, DossierCyan.copy(alpha = 0.38f))
                ) {
                    Text(
                        text = if (isContinuousDossier) "Consistência entre Parciais" else "Tiros (Picos) vs. Recup. (Vales)",
                        color = DossierCyan,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
            }

            // 2. Resumo Duplo: Média de ritmo (M'SS") vs. Ritmo máx (M'SS" - pico mais rápido)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = dossier.formattedAvgPace,
                                color = primaryText,
                                fontSize = 26.sp,
                                lineHeight = 28.sp,
                                fontWeight = FontWeight.Black,
                                maxLines = 1,
                                softWrap = false
                            )
                            Text(
                                text = "/km",
                                color = secondaryText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(bottom = 3.dp)
                            )
                        }
                        Text(
                            text = "Média de ritmo",
                            color = secondaryText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = dossier.formattedMaxPace,
                                color = DossierGreen,
                                fontSize = 26.sp,
                                lineHeight = 28.sp,
                                fontWeight = FontWeight.Black,
                                maxLines = 1,
                                softWrap = false
                            )
                            Text(
                                text = "/km",
                                color = secondaryText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(bottom = 3.dp)
                            )
                        }
                        Text(
                            text = "Ritmo máx",
                            color = secondaryText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                val activeSample = selectedSampleIdx?.let { paceSamples.getOrNull(it) }
                if (activeSample != null) {
                    val m = activeSample.elapsedSeconds / 60
                    val s = activeSample.elapsedSeconds % 60
                    val pFormatted = formatPaceMinSecQuote(activeSample.paceSecPerKm.safeRoundToInt())
                    Surface(
                        color = DossierGreen.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, DossierGreen.copy(alpha = 0.45f))
                    ) {
                        Text(
                            text = String.format(Locale.US, "%02d:%02d • %s", m, s, pFormatted),
                            color = primaryText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // 3. Gráfico de Área Invertido (Ritmos mais rápidos ficam mais altos no topo)
            if (paceSamples.size < 2 || fastestPaceSec <= 0 || slowestPaceSec <= 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(104.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isLightMode) Color(0xFFF1F5F9) else Color(0xFF10141D)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Sem dados de ritmo",
                        color = secondaryText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(148.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .onSizeChanged { chartWidthPx = it.width.toFloat().coerceAtLeast(1f) }
                                .pointerInput(paceSamples) {
                                    detectTapGestures { offset ->
                                        if (paceSamples.size >= 2) {
                                            val frac = (offset.x / chartWidthPx.coerceAtLeast(1f)).safeCoerceIn(0f, 1f)
                                            selectedSampleIdx = (frac * (paceSamples.size - 1)).safeRoundToInt().safeCoerceIn(0, paceSamples.lastIndex)
                                        }
                                    }
                                }
                                .pointerInput(paceSamples) {
                                    detectHorizontalDragGestures(
                                        onDragEnd = { selectedSampleIdx = null },
                                        onDragCancel = { selectedSampleIdx = null }
                                    ) { change, _ ->
                                        change.consume()
                                        if (paceSamples.size >= 2) {
                                            val frac = (change.position.x / chartWidthPx.coerceAtLeast(1f)).safeCoerceIn(0f, 1f)
                                            selectedSampleIdx = (frac * (paceSamples.size - 1)).safeRoundToInt().safeCoerceIn(0, paceSamples.lastIndex)
                                        }
                                    }
                                }
                        ) {
                            InvertedPaceAreaCanvas(
                                samples = paceSamples,
                                avgPaceSecPerKm = dossier.avgPaceSecPerKm.toFloat(),
                                fastestPaceSec = fastestPaceSec.toFloat(),
                                slowestPaceSec = slowestPaceSec.toFloat(),
                                selectedIdx = selectedSampleIdx,
                                isLightMode = isLightMode
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Eixo Y invertido à direita (Topo = Ritmo máx mais rápido; Base = Caminhada mais lenta)
                        Column(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(42.dp),
                            verticalArrangement = Arrangement.SpaceBetween,
                            horizontalAlignment = Alignment.End
                        ) {
                            Text(
                                text = formatPaceMinSecQuote(fastestPaceSec),
                                color = DossierGreen,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false
                            )
                            Text(
                                text = dossier.formattedAvgPace,
                                color = DossierCyan,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false
                            )
                            Text(
                                text = formatPaceMinSecQuote(slowestPaceSec),
                                color = secondaryText,
                                fontSize = 10.sp,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }

                    // Marcações de tempo no Eixo X
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 50.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        xLabels.forEach { tick ->
                            Text(
                                text = tick,
                                color = secondaryText,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InvertedPaceAreaCanvas(
    samples: List<RunTelemetrySample>,
    avgPaceSecPerKm: Float,
    fastestPaceSec: Float,
    slowestPaceSec: Float,
    selectedIdx: Int?,
    isLightMode: Boolean
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val topPad = 8.dp.toPx()
        val bottomPad = 6.dp.toPx()
        val plotH = (h - topPad - bottomPad).coerceAtLeast(1f)

        val topFastBound = (fastestPaceSec - 15f).coerceAtLeast(135f)
        val bottomSlowBound = (slowestPaceSec + 25f).coerceAtLeast(topFastBound + 60f)
        val paceRange = (bottomSlowBound - topFastBound).coerceAtLeast(30f)

        // Invertido: menor paceSecPerKm (mais rápido) -> Y menor (mais alto no topo do Canvas)
        fun paceToY(paceSec: Float): Float {
            val clamped = paceSec.safeCoerceIn(topFastBound, bottomSlowBound)
            val ratioFromFast = (clamped - topFastBound) / paceRange
            return topPad + plotH * ratioFromFast
        }

        val gridColor = if (isLightMode) Color(0xFFCBD5E1) else Color.White.copy(alpha = 0.08f)
        val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)

        listOf(0f, 0.5f, 1f).forEach { frac ->
            val y = topPad + plotH * frac
            drawLine(
                color = gridColor,
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = 1.dp.toPx()
            )
        }

        // Linha média tracejada horizontal
        val avgY = paceToY(avgPaceSecPerKm)
        drawLine(
            color = DossierCyan.copy(alpha = 0.72f),
            start = Offset(0f, avgY),
            end = Offset(w, avgY),
            strokeWidth = 1.3.dp.toPx(),
            pathEffect = dashEffect
        )

        if (samples.size >= 2) {
            val stepX = w / (samples.size - 1).coerceAtLeast(1)
            val points = samples.mapIndexed { idx, s ->
                Offset(idx * stepX, paceToY(s.paceSecPerKm))
            }
            val firstPt = points.firstOrNull() ?: Offset.Zero
            val lastPt = points.lastOrNull() ?: Offset.Zero

            val curvePath = Path().apply {
                moveTo(firstPt.x, firstPt.y)
                for (i in 0 until points.size - 1) {
                    val p0 = points[i]
                    val p1 = points[i + 1]
                    val midX = (p0.x + p1.x) / 2f
                    val midY = (p0.y + p1.y) / 2f
                    if (i == 0) {
                        lineTo(midX, midY)
                    } else {
                        quadraticTo(p0.x, p0.y, midX, midY)
                    }
                }
                lineTo(lastPt.x, lastPt.y)
            }

            val areaPath = Path().apply {
                addPath(curvePath)
                lineTo(lastPt.x, topPad + plotH)
                lineTo(firstPt.x, topPad + plotH)
                close()
            }

            drawPath(
                path = areaPath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        DossierGreen.copy(alpha = 0.48f),
                        DossierCyan.copy(alpha = 0.22f),
                        DossierCyan.copy(alpha = 0.02f)
                    ),
                    startY = topPad,
                    endY = topPad + plotH
                )
            )

            drawPath(
                path = curvePath,
                brush = Brush.verticalGradient(
                    colors = listOf(DossierGreen, DossierCyan),
                    startY = topPad,
                    endY = topPad + plotH
                ),
                style = Stroke(
                    width = 2.4.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )

            if (selectedIdx != null && selectedIdx in points.indices) {
                val pt = points[selectedIdx]
                drawLine(
                    color = Color.White.copy(alpha = 0.75f),
                    start = Offset(pt.x, topPad),
                    end = Offset(pt.x, topPad + plotH),
                    strokeWidth = 1.2.dp.toPx(),
                    pathEffect = dashEffect
                )
                drawCircle(
                    color = DossierGreen.copy(alpha = 0.30f),
                    radius = 8.dp.toPx(),
                    center = pt
                )
                drawCircle(
                    color = Color.White,
                    radius = 4.5.dp.toPx(),
                    center = pt
                )
                drawCircle(
                    color = DossierGreen,
                    radius = 3.dp.toPx(),
                    center = pt
                )
            }
        }
    }
}

/**
 * BLOCO 4 — CARD DE RITMO POR KM (BARRAS DE PARCIAIS / SPLITS).
 * - Cabeçalho "Ritmo por km" com colunas Km e Ritmo.
 * - Barra horizontal preenchida para cada quilômetro cheio ("1", "2", etc.) e para a fração final ("<2", "<3", etc., ex: 0.57 km),
 *   exibindo o pace dentro da barra e destacando visualmente o km mais rápido.
 */
@Composable
fun RunDossierPacePerKmCard(
    dossier: RunPhysiologyDossier,
    isLightMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    val cardBg = if (isLightMode) Color.White else DossierCardDark
    val cardBorder = if (isLightMode) Color(0xFFE2E8F0) else DossierCardBorderDark
    val primaryText = if (isLightMode) Color(0xFF0F172A) else Color.White
    val secondaryText = if (isLightMode) Color(0xFF64748B) else DossierTextSecondaryDark

    val splits = dossier.kmSplits
    val fastestPace = remember(splits) {
        splits.minOfOrNull { it.paceSecPerKm }?.coerceAtLeast(1) ?: 360
    }
    val slowestPace = remember(splits) {
        splits.maxOfOrNull { it.paceSecPerKm }?.coerceAtLeast(fastestPace + 30) ?: (fastestPace + 60)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("run_dossier_pace_per_km_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(1.dp, cardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Cabeçalho
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
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(DossierBlue.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = "Ritmo por km",
                            tint = DossierCyan,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Text(
                        text = "Ritmo por km",
                        color = primaryText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                }

                if (splits.any { it.isFastest }) {
                    Surface(
                        color = DossierGreen.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(0.5.dp, DossierGreen.copy(alpha = 0.45f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.EmojiEvents,
                                contentDescription = null,
                                tint = DossierGreen,
                                modifier = Modifier.size(11.dp)
                            )
                            Text(
                                text = "Melhor km em destaque",
                                color = DossierGreen,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }

            // Cabeçalho das Colunas: Km | Ritmo
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Km",
                    color = secondaryText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.width(42.dp)
                )
                Text(
                    text = "Ritmo",
                    color = secondaryText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "Parcial",
                    color = secondaryText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.width(64.dp)
                )
            }

            HorizontalDivider(color = cardBorder, thickness = 1.dp)

            // Linhas de Barras Horizontais por Km
            if (splits.isEmpty()) {
                Text(
                    text = "Parciais indisponíveis",
                    color = secondaryText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                )
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    splits.forEach { split ->
                        KmSplitHorizontalBarRow(
                            split = split,
                            fastestPaceSec = fastestPace,
                            slowestPaceSec = slowestPace,
                            primaryText = primaryText,
                            secondaryText = secondaryText,
                            isLightMode = isLightMode
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun KmSplitHorizontalBarRow(
    split: KmSplit,
    fastestPaceSec: Int,
    slowestPaceSec: Int,
    primaryText: Color,
    secondaryText: Color,
    isLightMode: Boolean
) {
    // Quanto mais rápido o pace (menor paceSecPerKm), maior o preenchimento da barra (0.52f .. 1.0f)
    val span = (slowestPaceSec - fastestPaceSec).coerceAtLeast(25).toFloat()
    val speedNorm = (1f - ((split.paceSecPerKm - fastestPaceSec).toFloat() / span)).safeCoerceIn(0f, 1f)
    val fillFraction = (0.55f + speedNorm * 0.45f).safeCoerceIn(0.55f, 1.0f)

    val barBrush = if (split.isFastest) {
        Brush.horizontalGradient(
            colors = listOf(
                DossierGreen.copy(alpha = 0.88f),
                DossierCyan.copy(alpha = 0.85f)
            )
        )
    } else {
        Brush.horizontalGradient(
            colors = listOf(
                DossierBlue.copy(alpha = 0.72f),
                DossierCyan.copy(alpha = 0.52f)
            )
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Coluna Km ("1", "2", "<3")
        Text(
            text = split.displayLabel,
            color = if (split.isFastest) DossierGreen else primaryText,
            fontSize = 13.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.width(34.dp)
        )

        // Barra horizontal preenchida com o Pace dentro da barra
        Box(
            modifier = Modifier
                .weight(1f)
                .height(28.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (isLightMode) Color(0xFFE2E8F0) else Color(0xFF1F2533))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fillFraction)
                    .clip(RoundedCornerShape(8.dp))
                    .background(barBrush)
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${split.formattedPace}/km",
                        color = Color(0xFF081018),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        softWrap = false
                    )
                    if (split.isFastest) {
                        Text(
                            text = "★ PR",
                            color = Color(0xFF081018),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }

        // Coluna direita: distância da fração (ex: "0,57 km" quando <2/<3) ou tempo mm:ss
        val rightLabel = if (split.isPartialLastKm) {
            split.formattedDistanceSubLabel
        } else {
            String.format(Locale.US, "%02d:%02d", split.durationSec / 60, split.durationSec % 60)
        }
        Text(
            text = rightLabel,
            color = if (split.isPartialLastKm) DossierCyan else secondaryText,
            fontSize = 11.sp,
            fontWeight = if (split.isPartialLastKm) FontWeight.Bold else FontWeight.SemiBold,
            textAlign = TextAlign.End,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.width(56.dp)
        )
    }
}

private fun buildFourTimeMarkers(totalDurationSec: Int): List<String> {
    val safeSec = totalDurationSec.coerceAtLeast(60)
    return listOf(0f, 0.33f, 0.66f, 1.0f).map { frac ->
        val sec = (safeSec * frac).safeRoundToInt()
        val m = sec / 60
        val s = sec % 60
        String.format(Locale.US, "%02d:%02d", m, s)
    }
}

/**
 * BLOCO 5 — CARD DE CADÊNCIA (ETAPA 2: MÉDIA DE PPM + PPM MÁXIMO + DISPERSÃO/SEGMENTOS EM CANVAS).
 * - Cabeçalho com ícone amarelo/laranja (#FFCA28 / #FF9800), título "Cadência" e resumo duplo: Média de PPM vs. PPM máximo.
 * - Gráfico de dispersão/segmentos em Canvas (CadenceScatterLineCanvas): renderiza os patamares de cadência ao longo do tempo
 *   em laranja/âmbar (#FF9800 / #FFB300), mostrando nitidamente a separação biomecânica entre os blocos de corrida (~165–175 PPM)
 *   e caminhada (~110–125 PPM), com linha média tracejada de cadência e tooltip interativa.
 */
@Composable
fun RunDossierCadenceCard(
    dossier: RunPhysiologyDossier,
    isLightMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    val cardBg = if (isLightMode) Color.White else DossierCardDark
    val cardBorder = if (isLightMode) Color(0xFFE2E8F0) else DossierCardBorderDark
    val primaryText = if (isLightMode) Color(0xFF0F172A) else Color.White
    val secondaryText = if (isLightMode) Color(0xFF64748B) else DossierTextSecondaryDark

    val accentAmber = if (isLightMode) Color(0xFFD97706) else DossierAmber
    val accentOrange = if (isLightMode) Color(0xFFEA580C) else DossierOrange

    val samples = dossier.telemetrySamples
    val cadenceSamples = remember(samples) { samples.filter { it.cadenceSpm > 0 } }
    var selectedSampleIdx by remember(cadenceSamples) { mutableStateOf<Int?>(null) }
    var chartWidthPx by remember { mutableFloatStateOf(1f) }

    val xLabels = remember(dossier.durationSeconds) {
        buildFourTimeMarkers(dossier.durationSeconds)
    }

    val minCadenceAxis = remember(cadenceSamples) {
        val rawMin = cadenceSamples.minOfOrNull { it.cadenceSpm } ?: 0
        if (rawMin > 0) ((rawMin - 10) / 5 * 5).safeCoerceIn(70, 160) else 0
    }
    val maxCadenceAxis = remember(cadenceSamples, dossier.maxCadenceSpm, minCadenceAxis) {
        if (minCadenceAxis <= 0) {
            0
        } else {
            val rawMax = maxOf(dossier.maxCadenceSpm, cadenceSamples.maxOfOrNull { it.cadenceSpm } ?: minCadenceAxis)
            (((rawMax + 10) / 5) * 5).safeCoerceIn(minCadenceAxis + 30, maxOf(minCadenceAxis + 30, 230))
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("run_dossier_cadence_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(1.dp, cardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Cabeçalho
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
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(accentOrange.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.DirectionsRun,
                            contentDescription = "Cadência",
                            tint = accentAmber,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Text(
                        text = "Cadência",
                        color = primaryText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                }

                Surface(
                    color = accentOrange.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(0.5.dp, accentAmber.copy(alpha = 0.45f))
                ) {
                    Text(
                        text = "Corrida (~165-175) vs. Caminhada",
                        color = accentAmber,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
            }

            // 2. Resumo Duplo: Média de PPM vs. PPM máximo
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = if (dossier.avgCadenceSpm > 0) "${dossier.avgCadenceSpm}" else "--",
                                color = primaryText,
                                fontSize = 26.sp,
                                lineHeight = 28.sp,
                                fontWeight = FontWeight.Black,
                                maxLines = 1,
                                softWrap = false
                            )
                            Text(
                                text = "ppm",
                                color = secondaryText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(bottom = 3.dp)
                            )
                        }
                        Text(
                            text = "Média de PPM",
                            color = secondaryText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = if (dossier.maxCadenceSpm > 0) "${dossier.maxCadenceSpm}" else "--",
                                color = accentAmber,
                                fontSize = 26.sp,
                                lineHeight = 28.sp,
                                fontWeight = FontWeight.Black,
                                maxLines = 1,
                                softWrap = false
                            )
                            Text(
                                text = "ppm",
                                color = secondaryText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(bottom = 3.dp)
                            )
                        }
                        Text(
                            text = "PPM máximo",
                            color = secondaryText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                val activeSample = selectedSampleIdx?.let { cadenceSamples.getOrNull(it) }
                if (activeSample != null) {
                    val m = activeSample.elapsedSeconds / 60
                    val s = activeSample.elapsedSeconds % 60
                    Surface(
                        color = accentOrange.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, accentAmber.copy(alpha = 0.55f))
                    ) {
                        Text(
                            text = String.format(Locale.US, "%02d:%02d • %d ppm", m, s, activeSample.cadenceSpm),
                            color = primaryText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // 3. Gráfico de Dispersão / Segmentos de Cadência
            if (cadenceSamples.size < 2 || minCadenceAxis <= 0 || maxCadenceAxis <= 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isLightMode) Color(0xFFF1F5F9) else Color(0xFF10141D)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Sem dados de cadência",
                        color = secondaryText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(144.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .onSizeChanged { chartWidthPx = it.width.toFloat().coerceAtLeast(1f) }
                                .pointerInput(cadenceSamples) {
                                    detectTapGestures { offset ->
                                        if (cadenceSamples.size >= 2) {
                                            val frac = (offset.x / chartWidthPx.coerceAtLeast(1f)).safeCoerceIn(0f, 1f)
                                            selectedSampleIdx = (frac * (cadenceSamples.size - 1)).safeRoundToInt().safeCoerceIn(0, cadenceSamples.lastIndex)
                                        }
                                    }
                                }
                                .pointerInput(cadenceSamples) {
                                    detectHorizontalDragGestures(
                                        onDragEnd = { selectedSampleIdx = null },
                                        onDragCancel = { selectedSampleIdx = null }
                                    ) { change, _ ->
                                        change.consume()
                                        if (cadenceSamples.size >= 2) {
                                            val frac = (change.position.x / chartWidthPx.coerceAtLeast(1f)).safeCoerceIn(0f, 1f)
                                            selectedSampleIdx = (frac * (cadenceSamples.size - 1)).safeRoundToInt().safeCoerceIn(0, cadenceSamples.lastIndex)
                                        }
                                    }
                                }
                        ) {
                            CadenceScatterLineCanvas(
                                samples = cadenceSamples,
                                avgCadenceSpm = dossier.avgCadenceSpm.toFloat(),
                                minCadenceAxis = minCadenceAxis.toFloat(),
                                maxCadenceAxis = maxCadenceAxis.toFloat(),
                                selectedIdx = selectedSampleIdx,
                                isLightMode = isLightMode
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Eixo Y compacto à direita
                        Column(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(34.dp),
                            verticalArrangement = Arrangement.SpaceBetween,
                            horizontalAlignment = Alignment.End
                        ) {
                            Text(
                                text = "$maxCadenceAxis",
                                color = accentAmber,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false
                            )
                            Text(
                                text = if (dossier.avgCadenceSpm > 0) "${dossier.avgCadenceSpm}" else "--",
                                color = accentOrange,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false
                            )
                            Text(
                                text = "$minCadenceAxis",
                                color = secondaryText,
                                fontSize = 10.sp,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }

                    // Marcações de tempo no Eixo X
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 42.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        xLabels.forEach { tick ->
                            Text(
                                text = tick,
                                color = secondaryText,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CadenceScatterLineCanvas(
    samples: List<RunTelemetrySample>,
    avgCadenceSpm: Float,
    minCadenceAxis: Float,
    maxCadenceAxis: Float,
    selectedIdx: Int?,
    isLightMode: Boolean
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val topPad = 8.dp.toPx()
        val bottomPad = 6.dp.toPx()
        val plotH = (h - topPad - bottomPad).coerceAtLeast(1f)
        val range = (maxCadenceAxis - minCadenceAxis).coerceAtLeast(30f)

        fun spmToY(spm: Float): Float {
            val norm = ((spm - minCadenceAxis) / range).safeCoerceIn(0f, 1f)
            return topPad + plotH * (1f - norm)
        }

        val gridColor = if (isLightMode) Color(0xFFCBD5E1) else Color.White.copy(alpha = 0.08f)
        val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)

        listOf(0f, 0.5f, 1f).forEach { frac ->
            val y = topPad + plotH * frac
            drawLine(
                color = gridColor,
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = 1.dp.toPx()
            )
        }

        // Linha média tracejada horizontal de cadência
        val avgY = spmToY(avgCadenceSpm)
        drawLine(
            color = DossierOrange.copy(alpha = 0.72f),
            start = Offset(0f, avgY),
            end = Offset(w, avgY),
            strokeWidth = 1.3.dp.toPx(),
            pathEffect = dashEffect
        )

        if (samples.size >= 2) {
            val stepX = w / (samples.size - 1).coerceAtLeast(1)
            val points = samples.mapIndexed { idx, s ->
                Offset(idx * stepX, spmToY(s.cadenceSpm.toFloat()))
            }

            // Conecta apenas segmentos dentro do mesmo patamar biomecânico (diferença <= 18 PPM)
            for (i in 0 until points.size - 1) {
                val s0 = samples[i]
                val s1 = samples[i + 1]
                val p0 = points[i]
                val p1 = points[i + 1]
                val isSamePlateau = kotlin.math.abs(s0.cadenceSpm - s1.cadenceSpm) <= 18
                val segColor = if (s0.cadenceSpm >= 145) DossierAmber else DossierOrange.copy(alpha = 0.78f)
                if (isSamePlateau) {
                    drawLine(
                        color = segColor,
                        start = p0,
                        end = p1,
                        strokeWidth = 2.6.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                } else {
                    // Transição vertical sutil entre caminhada e corrida
                    drawLine(
                        color = DossierOrange.copy(alpha = 0.24f),
                        start = p0,
                        end = p1,
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = dashEffect
                    )
                }
            }

            // Pontos de dispersão (Scatter) destacando cada amostra nos patamares
            points.forEachIndexed { idx, pt ->
                val spm = samples[idx].cadenceSpm
                val dotColor = if (spm >= 145) DossierAmber else DossierOrange
                drawCircle(
                    color = dotColor,
                    radius = if (spm >= 145) 3.1.dp.toPx() else 2.5.dp.toPx(),
                    center = pt
                )
            }

            if (selectedIdx != null && selectedIdx in points.indices) {
                val pt = points[selectedIdx]
                drawLine(
                    color = Color.White.copy(alpha = 0.75f),
                    start = Offset(pt.x, topPad),
                    end = Offset(pt.x, topPad + plotH),
                    strokeWidth = 1.2.dp.toPx(),
                    pathEffect = dashEffect
                )
                drawCircle(
                    color = DossierAmber.copy(alpha = 0.32f),
                    radius = 8.dp.toPx(),
                    center = pt
                )
                drawCircle(
                    color = Color.White,
                    radius = 4.5.dp.toPx(),
                    center = pt
                )
                drawCircle(
                    color = DossierOrange,
                    radius = 3.dp.toPx(),
                    center = pt
                )
            }
        }
    }
}

/**
 * BLOCO 6 — CARD DE PASSADA EM CM (ETAPA 2: PASSADA MÉDIA + PASSADA MÁXIMA + CURVA EM CANVAS).
 * - Cabeçalho com ícone amarelo/âmbar, título "Passada (cm)" e resumo duplo: Passada média vs. Passada máxima.
 * - Gráfico contínuo/segmentado em Canvas (StrideLengthCanvas): curva em amarelo/laranja (#FFCA28 / #FFA726)
 *   evidenciando a abertura de passada nos blocos de tiro vs. recuperação, com linha guia da média e marcações de tempo no eixo X.
 */
@Composable
fun RunDossierStrideLengthCard(
    dossier: RunPhysiologyDossier,
    isLightMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    val cardBg = if (isLightMode) Color.White else DossierCardDark
    val cardBorder = if (isLightMode) Color(0xFFE2E8F0) else DossierCardBorderDark
    val primaryText = if (isLightMode) Color(0xFF0F172A) else Color.White
    val secondaryText = if (isLightMode) Color(0xFF64748B) else DossierTextSecondaryDark

    val accentAmber = if (isLightMode) Color(0xFFD97706) else DossierAmber
    val accentOrange = if (isLightMode) Color(0xFFEA580C) else DossierOrange

    val samples = dossier.telemetrySamples
    val strideSamples = remember(samples) { samples.filter { it.strideLengthCm > 0 } }
    var selectedSampleIdx by remember(strideSamples) { mutableStateOf<Int?>(null) }
    var chartWidthPx by remember { mutableFloatStateOf(1f) }

    val xLabels = remember(dossier.durationSeconds) {
        buildFourTimeMarkers(dossier.durationSeconds)
    }

    val minStrideAxis = remember(strideSamples) {
        val rawMin = strideSamples.minOfOrNull { it.strideLengthCm } ?: 0
        if (rawMin > 0) ((rawMin - 8) / 5 * 5).safeCoerceIn(35, 110) else 0
    }
    val maxStrideAxis = remember(strideSamples, dossier.maxStrideLengthCm, minStrideAxis) {
        if (minStrideAxis <= 0) {
            0
        } else {
            val rawMax = maxOf(dossier.maxStrideLengthCm, strideSamples.maxOfOrNull { it.strideLengthCm } ?: minStrideAxis)
            (((rawMax + 8) / 5) * 5).safeCoerceIn(minStrideAxis + 25, maxOf(minStrideAxis + 25, 220))
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("run_dossier_stride_length_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(1.dp, cardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Cabeçalho
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
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(accentAmber.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.OpenInFull,
                            contentDescription = "Passada (cm)",
                            tint = accentAmber,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    Text(
                        text = "Passada (cm)",
                        color = primaryText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                }

                Surface(
                    color = accentAmber.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(0.5.dp, accentAmber.copy(alpha = 0.45f))
                ) {
                    Text(
                        text = "Amplitude Biomecânica",
                        color = accentAmber,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
            }

            // 2. Resumo Duplo: Passada média (cm) vs. Passada máxima (cm)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = if (dossier.avgStrideLengthCm > 0) "${dossier.avgStrideLengthCm}" else "--",
                                color = primaryText,
                                fontSize = 26.sp,
                                lineHeight = 28.sp,
                                fontWeight = FontWeight.Black,
                                maxLines = 1,
                                softWrap = false
                            )
                            Text(
                                text = "cm",
                                color = secondaryText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(bottom = 3.dp)
                            )
                        }
                        Text(
                            text = "Passada média",
                            color = secondaryText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = if (dossier.maxStrideLengthCm > 0) "${dossier.maxStrideLengthCm}" else "--",
                                color = accentAmber,
                                fontSize = 26.sp,
                                lineHeight = 28.sp,
                                fontWeight = FontWeight.Black,
                                maxLines = 1,
                                softWrap = false
                            )
                            Text(
                                text = "cm",
                                color = secondaryText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(bottom = 3.dp)
                            )
                        }
                        Text(
                            text = "Passada máxima",
                            color = secondaryText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                val activeSample = selectedSampleIdx?.let { strideSamples.getOrNull(it) }
                if (activeSample != null) {
                    val m = activeSample.elapsedSeconds / 60
                    val s = activeSample.elapsedSeconds % 60
                    Surface(
                        color = accentAmber.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, accentAmber.copy(alpha = 0.55f))
                    ) {
                        Text(
                            text = String.format(Locale.US, "%02d:%02d • %d cm", m, s, activeSample.strideLengthCm),
                            color = primaryText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // 3. Gráfico de Curva de Passada em Canvas
            if (strideSamples.size < 2 || minStrideAxis <= 0 || maxStrideAxis <= 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isLightMode) Color(0xFFF1F5F9) else Color(0xFF10141D)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Sem dados de passada",
                        color = secondaryText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(144.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .onSizeChanged { chartWidthPx = it.width.toFloat().coerceAtLeast(1f) }
                                .pointerInput(strideSamples) {
                                    detectTapGestures { offset ->
                                        if (strideSamples.size >= 2) {
                                            val frac = (offset.x / chartWidthPx.coerceAtLeast(1f)).safeCoerceIn(0f, 1f)
                                            selectedSampleIdx = (frac * (strideSamples.size - 1)).safeRoundToInt().safeCoerceIn(0, strideSamples.lastIndex)
                                        }
                                    }
                                }
                                .pointerInput(strideSamples) {
                                    detectHorizontalDragGestures(
                                        onDragEnd = { selectedSampleIdx = null },
                                        onDragCancel = { selectedSampleIdx = null }
                                    ) { change, _ ->
                                        change.consume()
                                        if (strideSamples.size >= 2) {
                                            val frac = (change.position.x / chartWidthPx.coerceAtLeast(1f)).safeCoerceIn(0f, 1f)
                                            selectedSampleIdx = (frac * (strideSamples.size - 1)).safeRoundToInt().safeCoerceIn(0, strideSamples.lastIndex)
                                        }
                                    }
                                }
                        ) {
                            StrideLengthCanvas(
                                samples = strideSamples,
                                avgStrideCm = dossier.avgStrideLengthCm.toFloat(),
                                minStrideAxis = minStrideAxis.toFloat(),
                                maxStrideAxis = maxStrideAxis.toFloat(),
                                selectedIdx = selectedSampleIdx,
                                isLightMode = isLightMode
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Eixo Y compacto à direita
                        Column(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(34.dp),
                            verticalArrangement = Arrangement.SpaceBetween,
                            horizontalAlignment = Alignment.End
                        ) {
                            Text(
                                text = "$maxStrideAxis",
                                color = accentAmber,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false
                            )
                            Text(
                                text = if (dossier.avgStrideLengthCm > 0) "${dossier.avgStrideLengthCm}" else "--",
                                color = accentOrange,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false
                            )
                            Text(
                                text = "$minStrideAxis",
                                color = secondaryText,
                                fontSize = 10.sp,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }

                    // Marcações de tempo no Eixo X
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 42.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        xLabels.forEach { tick ->
                            Text(
                                text = tick,
                                color = secondaryText,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StrideLengthCanvas(
    samples: List<RunTelemetrySample>,
    avgStrideCm: Float,
    minStrideAxis: Float,
    maxStrideAxis: Float,
    selectedIdx: Int?,
    isLightMode: Boolean
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val topPad = 8.dp.toPx()
        val bottomPad = 6.dp.toPx()
        val plotH = (h - topPad - bottomPad).coerceAtLeast(1f)
        val range = (maxStrideAxis - minStrideAxis).coerceAtLeast(20f)

        fun cmToY(cm: Float): Float {
            val norm = ((cm - minStrideAxis) / range).safeCoerceIn(0f, 1f)
            return topPad + plotH * (1f - norm)
        }

        val gridColor = if (isLightMode) Color(0xFFCBD5E1) else Color.White.copy(alpha = 0.08f)
        val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)

        listOf(0f, 0.5f, 1f).forEach { frac ->
            val y = topPad + plotH * frac
            drawLine(
                color = gridColor,
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = 1.dp.toPx()
            )
        }

        // Linha guia horizontal da passada média
        val avgY = cmToY(avgStrideCm)
        drawLine(
            color = DossierOrange.copy(alpha = 0.72f),
            start = Offset(0f, avgY),
            end = Offset(w, avgY),
            strokeWidth = 1.3.dp.toPx(),
            pathEffect = dashEffect
        )

        if (samples.size >= 2) {
            val stepX = w / (samples.size - 1).coerceAtLeast(1)
            val points = samples.mapIndexed { idx, s ->
                Offset(idx * stepX, cmToY(s.strideLengthCm.toFloat()))
            }
            val firstPt = points.firstOrNull() ?: Offset.Zero
            val lastPt = points.lastOrNull() ?: Offset.Zero

            val linePath = Path().apply {
                moveTo(firstPt.x, firstPt.y)
                for (i in 0 until points.size - 1) {
                    val p0 = points[i]
                    val p1 = points[i + 1]
                    val midX = (p0.x + p1.x) / 2f
                    val midY = (p0.y + p1.y) / 2f
                    if (i == 0) {
                        lineTo(midX, midY)
                    } else {
                        quadraticTo(p0.x, p0.y, midX, midY)
                    }
                }
                lineTo(lastPt.x, lastPt.y)
            }

            val areaPath = Path().apply {
                addPath(linePath)
                lineTo(lastPt.x, topPad + plotH)
                lineTo(firstPt.x, topPad + plotH)
                close()
            }

            drawPath(
                path = areaPath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        DossierAmber.copy(alpha = 0.36f),
                        DossierOrange.copy(alpha = 0.14f),
                        DossierOrange.copy(alpha = 0.02f)
                    ),
                    startY = topPad,
                    endY = topPad + plotH
                )
            )

            drawPath(
                path = linePath,
                brush = Brush.verticalGradient(
                    colors = listOf(DossierAmber, DossierOrange),
                    startY = topPad,
                    endY = topPad + plotH
                ),
                style = Stroke(
                    width = 2.4.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )

            if (selectedIdx != null && selectedIdx in points.indices) {
                val pt = points[selectedIdx]
                drawLine(
                    color = Color.White.copy(alpha = 0.75f),
                    start = Offset(pt.x, topPad),
                    end = Offset(pt.x, topPad + plotH),
                    strokeWidth = 1.2.dp.toPx(),
                    pathEffect = dashEffect
                )
                drawCircle(
                    color = DossierAmber.copy(alpha = 0.30f),
                    radius = 8.dp.toPx(),
                    center = pt
                )
                drawCircle(
                    color = Color.White,
                    radius = 4.5.dp.toPx(),
                    center = pt
                )
                drawCircle(
                    color = DossierAmber,
                    radius = 3.dp.toPx(),
                    center = pt
                )
            }
        }
    }
}

/**
 * BLOCO 7 — CARD DE EFEITO DO TREINO & FONTE DE DADOS (ETAPA 2: VITALIDADE + JANELA 48H FULL BODY + ENGINE SOURCE).
 * - Seção Efeito do Treino: ícone ciano/verde (#00E5FF / #00E676), título "Efeito do treino", valor em destaque "+X"
 *   seguido de "Pontuação de Vitalidade", badge explicativa do efeito fisiológico + integração com o motor híbrido (Full Body).
 * - Seção Fonte de Dados: card/linha compacta "Fonte de dados" com ícone de dispositivo/GPS e identificação do motor
 *   (MAX RUN Telemetry v4.5 + status de sensor BLE/GPS).
 */
@Composable
fun RunDossierTrainingEffectAndSourceCard(
    dossier: RunPhysiologyDossier,
    isLightMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    val cardBg = if (isLightMode) Color.White else DossierCardDark
    val cardBorder = if (isLightMode) Color(0xFFE2E8F0) else DossierCardBorderDark
    val primaryText = if (isLightMode) Color(0xFF0F172A) else Color.White
    val secondaryText = if (isLightMode) Color(0xFF64748B) else DossierTextSecondaryDark
    val accentCyan = if (isLightMode) Color(0xFF0288D1) else DossierCyan

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Card 0: Fisiologia & Substrato Energético (LT1 / LT2, Carboidrato vs. Gordura, Lactato e Janela de Fadiga)
        RunDossierEnduranceSubstrateLt1Lt2Card(
            dossier = dossier,
            isLightMode = isLightMode
        )

        // Card 1: Efeito do Treino (Vitalidade + Sinergia Híbrida 48h)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("run_dossier_training_effect_card"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = cardBg),
            border = BorderStroke(1.dp, cardBorder)
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
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(DossierGreen.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = "Efeito do treino",
                                tint = DossierGreen,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Text(
                            text = "Efeito do treino",
                            color = primaryText,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    Surface(
                        color = accentCyan.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(0.5.dp, accentCyan.copy(alpha = 0.45f))
                    ) {
                        Text(
                            text = dossier.trainingEffectTitle,
                            color = accentCyan,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }

                // Destaque: +X Pontuação de Vitalidade
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (dossier.vitalityScoreGain > 0) "+${dossier.vitalityScoreGain}" else "--",
                        color = if (dossier.vitalityScoreGain > 0) DossierGreen else secondaryText,
                        fontSize = 30.sp,
                        lineHeight = 32.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        softWrap = false
                    )
                    Text(
                        text = "Pontuação de Vitalidade",
                        color = primaryText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }

                // Integração com o Motor Híbrido (Janela 48h Full Body)
                Surface(
                    color = if (isLightMode) Color(0xFFF1F5F9) else Color(0xFF1B212E),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, cardBorder)
                ) {
                    Text(
                        text = dossier.hybridRecoveryImpact,
                        color = secondaryText,
                        fontSize = 11.5.sp,
                        lineHeight = 16.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }

                // Índice de Consistência Anti-Quebra (Comparação 1º Bloco/Tiro vs. Último Bloco/Tiro)
                val hasConsistencyMetrics = dossier.firstIntervalPaceSecPerKm > 0 && dossier.lastIntervalPaceSecPerKm > 0
                val isFadeAlert = hasConsistencyMetrics && dossier.paceFadePct > 10.0f
                val consistencyAccent = when {
                    !hasConsistencyMetrics -> secondaryText
                    isFadeAlert -> Color(0xFFFFB300)
                    else -> DossierGreen
                }
                Surface(
                    color = consistencyAccent.copy(alpha = 0.10f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, consistencyAccent.copy(alpha = 0.40f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("run_dossier_anti_fade_consistency_box")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text = "ÍNDICE DE CONSISTÊNCIA ANTI-QUEBRA • ${dossier.consistencyVerdictTitle.uppercase()}",
                            color = consistencyAccent,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = dossier.consistencyVerdictDetail,
                            color = primaryText,
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // Card 2: Fonte de Dados (Engine & Sensores)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("run_dossier_data_source_card"),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = cardBg),
            border = BorderStroke(1.dp, cardBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(accentCyan.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = "Fonte de dados",
                            tint = accentCyan,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Text(
                        text = "Fonte de dados",
                        color = primaryText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Text(
                    text = dossier.dataSourceLabel,
                    color = secondaryText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * PAINEL CIENTÍFICO: "FISIOLOGIA & SUBSTRATO ENERGÉTICO (LT1 / LT2)"
 * - Barra Dupla de Combustível da Sessão: Carboidrato / Glicogênio (#FF6B00) vs. Gordura (#00B4D8)
 * - Fronteiras Fisiológicas LT1 (137 bpm • 72% FCmáx) e LT2 (162 bpm • 85% FCmáx) calibradas para FCmáx 190 bpm
 * - Lactato Sanguíneo Estimado (mmol/L), Tempo até a Fadiga e Classificação Joe Friel / Jack Daniels
 * - Impacto direto na reposição de glicogênio muscular para o próximo treino do MAX GYM.
 */
@Composable
fun RunDossierEnduranceSubstrateLt1Lt2Card(
    dossier: RunPhysiologyDossier,
    isLightMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    val cardBg = if (isLightMode) Color.White else DossierCardDark
    val cardBorder = if (isLightMode) Color(0xFFE2E8F0) else DossierCardBorderDark
    val primaryText = if (isLightMode) Color(0xFF0F172A) else Color.White
    val secondaryText = if (isLightMode) Color(0xFF64748B) else DossierTextSecondaryDark

    val carbOrange = Color(0xFFFF6B00)
    val fatBlue = Color(0xFF00B4D8)

    val profile = remember(dossier) {
        com.example.data.model.calculateEndurancePhysiologyProfile(dossier)
    }

    val hasValidHrForSubstrate = dossier.avgHeartRateBpm > 0 && profile.isAvailable
    if (!hasValidHrForSubstrate) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .testTag("run_dossier_endurance_substrate_lt1_lt2_card"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = cardBg),
            border = BorderStroke(1.dp, cardBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "FISIOLOGIA & SUBSTRATO ENERGÉTICO (LT1 / LT2)",
                    color = primaryText,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.4.sp
                )
                Text(
                    text = if (dossier.avgHeartRateBpm <= 0 && dossier.maxHeartRateBpm <= 0) {
                        "Sem dados de FC para calcular limiares (LT1/LT2) e substrato energético."
                    } else {
                        "Dados insuficientes: informe a idade ou calibração de FCmáx no perfil para calcular limiares (LT1/LT2) e zonas."
                    },
                    color = secondaryText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        return
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("run_dossier_endurance_substrate_lt1_lt2_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(1.dp, carbOrange.copy(alpha = 0.42f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Cabeçalho com Selo LT1 / LT2
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
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(carbOrange.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = null,
                            tint = carbOrange,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "FISIOLOGIA & SUBSTRATO ENERGÉTICO (LT1 / LT2)",
                            color = primaryText,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.4.sp
                        )
                        Text(
                            text = "FCmáx Calibrada ${profile.calibratedMaxHrBpm} bpm • LT1 ${profile.lt1Bpm} bpm • LT2 ${profile.lt2Bpm} bpm",
                            color = secondaryText,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Badge de Predominância Fisiológica (ex: Fronteira LT2 Limiar ➔ VO2máx)
            Surface(
                color = carbOrange.copy(alpha = 0.12f),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, carbOrange.copy(alpha = 0.38f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = profile.predominantZoneBadge,
                        color = carbOrange,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = profile.frielDanielsClassification,
                        color = primaryText,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Barra Dupla de Combustível da Sessão: Carboidrato (Glicogênio #FF6B00) vs. Gordura (#00B4D8)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val carbDisplayText = when {
                    !profile.hasZoneDistribution -> "--"
                    profile.hasCalorieEstimate -> "${profile.carbOxidationPct}% (~${profile.carbGramsEstimated}g)"
                    else -> "${profile.carbOxidationPct}%"
                }
                val fatDisplayText = when {
                    !profile.hasZoneDistribution -> "--"
                    profile.hasCalorieEstimate -> "${profile.fatOxidationPct}% (~${profile.fatGramsEstimated}g)"
                    else -> "${profile.fatOxidationPct}%"
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "CARBOIDRATO (GLICOGÊNIO)",
                            color = carbOrange,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = carbDisplayText,
                            color = primaryText,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "GORDURA (LIPÍDIOS)",
                            color = fatBlue,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = fatDisplayText,
                            color = primaryText,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }

                if (profile.hasZoneDistribution) {
                    // Barra Horizontal Bicolor (Laranja #FF6B00 vs. Azul #00B4D8)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(12.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF1E2430))
                    ) {
                        val carbWeight = (profile.carbOxidationPct / 100f).coerceIn(0.05f, 0.95f)
                        val fatWeight = (1f - carbWeight).coerceIn(0.05f, 0.95f)
                        Box(
                            modifier = Modifier
                                .weight(carbWeight)
                                .fillMaxHeight()
                                .background(carbOrange)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Box(
                            modifier = Modifier
                                .weight(fatWeight)
                                .fillMaxHeight()
                                .background(fatBlue)
                        )
                    }
                } else {
                    Text(
                        text = "Dados insuficientes (< 6 amostras de FC) para distribuição de zonas e substrato.",
                        color = secondaryText,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Grade de 3 Pilares Fisiológicos: Fronteiras LT1/LT2, Lactato Sanguíneo Estimado e Janela de Fadiga
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (isLightMode) Color(0xFFF8FAFC) else Color(0xFF10141D), RoundedCornerShape(10.dp))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Domínio Predominante:",
                        color = secondaryText,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = profile.thresholdStatusTitle,
                        color = primaryText,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                HorizontalDivider(color = cardBorder.copy(alpha = 0.6f), thickness = 0.5.dp)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Lactato Sanguíneo Est.:",
                        color = secondaryText,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "${profile.estimatedLactateRange} • ${profile.rpeRangeLabel}",
                        color = Color(0xFFFFD600),
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                HorizontalDivider(color = cardBorder.copy(alpha = 0.6f), thickness = 0.5.dp)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Janela até a Fadiga:",
                        color = secondaryText,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = profile.timeToFatigueWindow,
                        color = DossierCyan,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(1f).padding(start = 8.dp)
                    )
                }

                HorizontalDivider(color = cardBorder.copy(alpha = 0.6f), thickness = 0.5.dp)

                // Distribuição nas 3 Zonas de Limiar (< LT1 | LT1-LT2 | > LT2)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Z1 (< LT1 <=${profile.lt1Bpm})", color = secondaryText, fontSize = 9.sp)
                        Text(
                            text = if (profile.hasZoneDistribution) "${profile.belowLt1Pct}%" else "--",
                            color = fatBlue,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Z2 (LT1–LT2 ${profile.lt1Bpm + 1}–${profile.lt2Bpm - 1})", color = secondaryText, fontSize = 9.sp)
                        Text(
                            text = if (profile.hasZoneDistribution) "${profile.betweenLt1Lt2Pct}%" else "--",
                            color = DossierGreen,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Z3 (> LT2 >=${profile.lt2Bpm})", color = secondaryText, fontSize = 9.sp)
                        Text(
                            text = if (profile.hasZoneDistribution) "${profile.aboveLt2Pct}%" else "--",
                            color = carbOrange,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }

            // Explicação do Impacto na Reposição Muscular para o Próximo Treino do MAX GYM
            Surface(
                color = DossierBlue.copy(alpha = 0.10f),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, DossierBlue.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = profile.maxGymGlycogenImpact,
                    color = primaryText,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
    }
}


