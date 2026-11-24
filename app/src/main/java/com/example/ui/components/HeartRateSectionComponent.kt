package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.*
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.RecordedKmSplit
import com.example.data.model.RoutePointItem
import com.example.data.model.RunPhysiologyDossier
import com.example.data.model.calculateEffectiveMaxHr
import com.example.data.model.computeCoherent5ZoneDistribution
import com.example.data.model.extractValidHrCurveSamples
import com.example.data.model.hasHrCurve
import com.example.data.model.hasHrZones
import com.example.data.model.hrCurveUnavailableMessage
import com.example.ui.theme.RunColors
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

data class HrZoneDefinition(
    val name: String,
    val rangeText: String,
    val minBpm: Int,
    val maxBpm: Int,
    val color: Color
)

/**
 * 6 Zonas Fisiológicas de Esforço (inspiradas no Haylou Fun):
 * 1. Recuperação (< 98 bpm) - Azul Suave (#60A5FA)
 * 2. Aquecimento (98–109 bpm) - Ciano (#38BDF8)
 * 3. Queima de Gordura (110–130 bpm) - Verde (#22C55E)
 * 4. Aeróbico (131–145 bpm) - Amarelo (#FACC15)
 * 5. Anaeróbico (146–160 bpm) - Laranja (#FF8A00)
 * 6. Limite (> 160 bpm) - Vermelho-Alaranjado (#FF453A)
 */
val HR_ZONES = listOf(
    HrZoneDefinition("Recuperação", "< 98 bpm", 0, 97, Color(0xFF60A5FA)),
    HrZoneDefinition("Aquecimento", "98–109 bpm", 98, 109, Color(0xFF38BDF8)),
    HrZoneDefinition("Queima de Gordura", "110–130 bpm", 110, 130, Color(0xFF22C55E)),
    HrZoneDefinition("Aeróbico", "131–145 bpm", 131, 145, Color(0xFFFACC15)),
    HrZoneDefinition("Anaeróbico", "146–160 bpm", 146, 160, Color(0xFFFF8A00)),
    HrZoneDefinition("Limite", "> 160 bpm", 161, 240, Color(0xFFFF453A))
)

/**
 * Retorna (corDeFundo, corDoTexto) para badges de zona, garantindo alto contraste
 * especialmente na zona "Aeróbico" (amarelo claro com texto escuro #8D4E00 / #795548).
 */
fun getZoneBadgeColors(zone: HrZoneDefinition, isLightMode: Boolean): Pair<Color, Color> {
    return if (zone.name == "Aeróbico") {
        val bg = if (isLightMode) Color(0xFFFEF08A) else Color(0xFFFFF59D)
        val fg = Color(0xFF795548)
        bg to fg
    } else {
        val bg = zone.color.copy(alpha = if (isLightMode) 0.16f else 0.20f)
        val fg = zone.color
        bg to fg
    }
}

fun getHrZoneForBpm(bpm: Int): HrZoneDefinition {
    return when {
        bpm < 98 -> HR_ZONES[0]
        bpm <= 109 -> HR_ZONES[1]
        bpm <= 130 -> HR_ZONES[2]
        bpm <= 145 -> HR_ZONES[3]
        bpm <= 160 -> HR_ZONES[4]
        else -> HR_ZONES[5]
    }
}

fun getHrColorForBpm(bpm: Float): Color {
    return when {
        bpm < 98f -> Color(0xFF60A5FA)
        bpm <= 109f -> Color(0xFF38BDF8)
        bpm <= 130f -> Color(0xFF22C55E)
        bpm <= 145f -> Color(0xFFFACC15)
        bpm <= 160f -> Color(0xFFFF8A00)
        else -> Color(0xFFFF453A)
    }
}

/**
 * Formata os marcos da régua de tempo horizontal (Eixo X).
 */
fun buildTimeAxisLabels(durationSec: Int, tickCount: Int = 6): List<String> {
    val safeDuration = durationSec.coerceAtLeast(60)
    val totalMinutes = safeDuration / 60f
    if (tickCount <= 2) {
        val endMin = totalMinutes.roundToInt().coerceAtLeast(1)
        return listOf("0", "${endMin}min")
    }
    return (0 until tickCount).map { idx ->
        val fraction = idx.toFloat() / (tickCount - 1).coerceAtLeast(1)
        val secAtTick = (safeDuration * fraction).roundToInt()
        if (idx == 0) {
            "0m"
        } else if (totalMinutes >= 5f) {
            val minRounded = (secAtTick / 60f).roundToInt()
            "${minRounded}m"
        } else {
            val m = secAtTick / 60
            val s = secAtTick % 60
            String.format(Locale.US, "%d:%02d", m, s)
        }
    }
}

/**
 * Calcula a queda média de BPM nas caminhadas/recuperações entre tiros.
 */
private fun calculateAverageWalkRecoveryDropBpm(
    samples: List<Int>,
    splits: List<RecordedKmSplit>
): Int? {
    // 1. Tenta via splits de corrida vs caminhada se disponíveis
    val runHrs = splits.filter { it.phaseTag == "RUN" || it.phaseTag == "PR" }.mapNotNull { it.avgHr }
    val walkHrs = splits.filter { it.phaseTag == "WALK" }.mapNotNull { it.avgHr }
    if (runHrs.isNotEmpty() && walkHrs.isNotEmpty()) {
        val drop = (runHrs.average() - walkHrs.average()).roundToInt()
        if (drop > 2) return drop
    }

    // 2. Detecta picos locais seguidos de vales de recuperação na curva de amostras
    if (samples.size < 6) return null
    val drops = mutableListOf<Int>()
    val window = (samples.size / 8).coerceIn(3, 12)
    var i = 2
    while (i < samples.size - 2) {
        val current = samples[i]
        val isLocalPeak = current >= samples[i - 1] && current >= samples[i + 1] && current >= 132
        if (isLocalPeak) {
            val endSearch = (i + window).coerceAtMost(samples.lastIndex)
            var minAfter = current
            for (j in (i + 1)..endSearch) {
                if (samples[j] < minAfter) minAfter = samples[j]
            }
            val drop = current - minAfter
            if (drop >= 6) {
                drops.add(drop)
                i += (window / 2).coerceAtLeast(2)
                continue
            }
        }
        i++
    }
    return if (drops.isNotEmpty()) {
        drops.average().roundToInt().coerceIn(8, 35)
    } else {
        val maxVal = samples.maxOrNull() ?: 170
        val avgVal = samples.average().roundToInt()
        ((maxVal - avgVal) * 0.65f).roundToInt().coerceIn(10, 26)
    }
}

/**
 * Resolve a Fase/Bloco da Corrida e o Pace Instantâneo para um dado progresso (0f..1f) no gráfico.
 */
private fun resolvePhaseAndPaceAtFraction(
    fraction: Float,
    bpmAtPoint: Int,
    avgHr: Int,
    avgPaceSecPerKm: Int,
    routePointItems: List<RoutePointItem>,
    splits: List<RecordedKmSplit>
): Pair<String, String> {
    val clamped = fraction.coerceIn(0f, 1f)

    // 1. Tenta extrair de routePointItems (contém phase, blockIndex e speed real)
    if (routePointItems.isNotEmpty()) {
        val idx = (clamped * (routePointItems.size - 1)).roundToInt().coerceIn(0, routePointItems.lastIndex)
        val pt = routePointItems[idx]
        // Conta qual o número do Tiro ou Caminhada até este ponto
        var sprintNum = 1
        var walkNum = 1
        var lastPhase = ""
        for (k in 0..idx) {
            val p = routePointItems[k].phase
            if (p != lastPhase) {
                if ((p == "RUN" || p == "PR") && lastPhase.isNotEmpty() && lastPhase != "RUN" && lastPhase != "PR") {
                    sprintNum++
                } else if (p == "WALK" && lastPhase.isNotEmpty() && lastPhase != "WALK") {
                    walkNum++
                }
                lastPhase = p
            }
        }
        val phaseLabel = when (pt.phase.uppercase(Locale.US)) {
            "WARMUP" -> "Aquecimento"
            "COOL_DOWN", "COOLDOWN" -> "Desaquecimento"
            "WALK" -> "Caminhada $walkNum"
            "PR" -> "Tiro $sprintNum (PR ⚡)"
            else -> if (sprintNum > 1 || routePointItems.any { it.phase == "WALK" }) "Tiro $sprintNum" else "Corrida Principal"
        }

        val speedMps = pt.speed ?: 0f
        val paceSec = if (speedMps > 0.6f) {
            (1000f / speedMps).roundToInt().coerceIn(150, 950)
        } else if (pt.phase.equals("WALK", ignoreCase = true)) {
            570 // ~9'30"/km caminhada
        } else {
            val basePace = if (avgPaceSecPerKm > 0) avgPaceSecPerKm else 340
            val hrRatio = (avgHr.toFloat() / bpmAtPoint.coerceAtLeast(90).toFloat()).coerceIn(0.78f, 1.25f)
            (basePace * hrRatio).roundToInt().coerceIn(175, 780)
        }
        val pMin = paceSec / 60
        val pSec = paceSec % 60
        return phaseLabel to String.format(Locale.US, "%d'%02d\"/km", pMin, pSec)
    }

    // 2. Fallback via splits se disponíveis
    if (splits.isNotEmpty()) {
        val totalSplitDur = splits.sumOf { it.durationSec }.coerceAtLeast(1)
        val targetSec = (clamped * totalSplitDur).roundToInt()
        var acc = 0
        var runCount = 0
        var walkCount = 0
        for (sp in splits) {
            val isWalk = sp.phaseTag == "WALK" || sp.splitLabel.contains("Caminh", ignoreCase = true)
            val isWarm = sp.phaseTag == "WARMUP" || sp.splitLabel.contains("Aquec", ignoreCase = true)
            val isCool = sp.phaseTag == "COOL_DOWN" || sp.splitLabel.contains("Desaquec", ignoreCase = true)
            if (!isWalk && !isWarm && !isCool) runCount++
            if (isWalk) walkCount++
            acc += sp.durationSec
            if (targetSec <= acc || sp == splits.last()) {
                val label = when {
                    isWarm -> "Aquecimento"
                    isCool -> "Desaquecimento"
                    isWalk -> "Caminhada ${walkCount.coerceAtLeast(1)}"
                    else -> "Tiro ${runCount.coerceAtLeast(1)}"
                }
                val pSec = sp.paceSecPerKm.takeIf { it > 0 } ?: avgPaceSecPerKm.takeIf { it > 0 } ?: 330
                return label to String.format(Locale.US, "%d'%02d\"/km", pSec / 60, pSec % 60)
            }
        }
    }

    // 3. Fallback fisiológico baseado na janela temporal e FC
    val phaseLabel = when {
        clamped < 0.10f -> "Aquecimento"
        clamped > 0.92f -> "Desaquecimento"
        bpmAtPoint < avgHr - 8 -> "Caminhada / Recup."
        else -> {
            val estBlock = ((clamped - 0.10f) / 0.14f).toInt().coerceAtLeast(0) + 1
            "Tiro $estBlock"
        }
    }
    val basePace = if (avgPaceSecPerKm > 0) avgPaceSecPerKm else 330
    val hrFactor = (avgHr.toFloat() / bpmAtPoint.coerceAtLeast(90).toFloat()).coerceIn(0.80f, 1.30f)
    val estPaceSec = (basePace * hrFactor).roundToInt().coerceIn(180, 750)
    return phaseLabel to String.format(Locale.US, "%d'%02d\"/km", estPaceSec / 60, estPaceSec % 60)
}

@Composable
private fun HaylouHeartRateCanvas(
    samples: List<Int>,
    isLightMode: Boolean,
    activeIdx: Int?,
    strokeWidthDp: Float = 2.4f
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas
        val topPad = 8.dp.toPx()
        val bottomPad = 6.dp.toPx()
        val plotH = (h - topPad - bottomPad).coerceAtLeast(1f)

        val minBpmScale = 80f
        val maxBpmScale = 180f
        val rangeBpm = maxBpmScale - minBpmScale
        val maxCanvasY = (h - 2f).coerceAtLeast(2f)

        fun bpmToY(bpm: Float): Float {
            val ratio = (bpm - minBpmScale) / rangeBpm
            return (topPad + plotH * (1f - ratio)).coerceIn(2f, maxCanvasY)
        }

        val dottedEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f), 0f)
        val gridColor = if (isLightMode) Color(0xFFCBD5E1) else Color.White.copy(alpha = 0.16f)
        val baseAxisColor = if (isLightMode) Color(0xFF94A3B8) else Color.White.copy(alpha = 0.30f)

        val dottedBpms = listOf(180f, 155f, 130f, 105f)
        dottedBpms.forEach { guide ->
            val y = bpmToY(guide)
            drawLine(
                color = gridColor,
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = 1.dp.toPx(),
                pathEffect = dottedEffect
            )
        }

        val y80 = bpmToY(80f)
        drawLine(
            color = baseAxisColor,
            start = Offset(0f, y80),
            end = Offset(w, y80),
            strokeWidth = 1.dp.toPx()
        )

        if (samples.size >= 2) {
            val stepX = w / (samples.size - 1).coerceAtLeast(1)
            val linePath = Path()

            val pts = samples.mapIndexed { idx, bpm ->
                Offset(x = idx * stepX, y = bpmToY(bpm.toFloat()))
            }

            linePath.moveTo(pts.first().x, pts.first().y)
            for (i in 0 until pts.size - 1) {
                val p0 = pts[i]
                val p1 = pts[i + 1]
                val midX = (p0.x + p1.x) / 2f
                val midY = (p0.y + p1.y) / 2f
                if (i == 0) {
                    linePath.lineTo(midX, midY)
                } else {
                    linePath.quadraticTo(p0.x, p0.y, midX, midY)
                }
            }
            linePath.lineTo(pts.last().x, pts.last().y)

            val stop158 = (bpmToY(158f) / h).coerceIn(0.05f, 0.90f)
            val stop142 = (bpmToY(142f) / h).coerceIn(stop158 + 0.03f, 0.93f)
            val stop125 = (bpmToY(125f) / h).coerceIn(stop142 + 0.03f, 0.96f)
            val stop105 = (bpmToY(105f) / h).coerceIn(stop125 + 0.03f, 0.99f)

            val zoneStrokeBrush = Brush.verticalGradient(
                0.0f to Color(0xFFFF453A),
                stop158 to Color(0xFFFF5252),
                stop142 to Color(0xFFFF8A00),
                stop125 to Color(0xFFFACC15),
                stop105 to Color(0xFF22C55E),
                1.0f to Color(0xFF38BDF8)
            )

            drawPath(
                path = linePath,
                brush = zoneStrokeBrush,
                style = Stroke(
                    width = strokeWidthDp.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )

            if (activeIdx != null && activeIdx in pts.indices) {
                val scrubPt = pts[activeIdx]
                val scrubBpm = samples[activeIdx].toFloat()
                val dotColor = getHrColorForBpm(scrubBpm)

                drawLine(
                    color = if (isLightMode) Color(0xFF334155) else Color.White.copy(alpha = 0.85f),
                    start = Offset(scrubPt.x, topPad),
                    end = Offset(scrubPt.x, y80),
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect = dottedEffect
                )
                drawCircle(
                    color = dotColor.copy(alpha = 0.28f),
                    radius = 10.dp.toPx(),
                    center = scrubPt
                )
                drawCircle(
                    color = Color.White,
                    radius = 5.5.dp.toPx(),
                    center = scrubPt
                )
                drawCircle(
                    color = dotColor,
                    radius = 3.8.dp.toPx(),
                    center = scrubPt
                )
            }
        }
    }
}

/**
 * Sobrecarga direta para abrir a Tela Cheia de Análise Cardíaca da Sessão a partir do Resumo Pós-Treino
 * ou Dossiê do Histórico (`RunPhysiologyDossier`).
 */
@Composable
fun FullscreenSessionHeartRateModal(
    dossier: RunPhysiologyDossier,
    athleteAge: Int?,
    hrSamples: List<Int> = emptyList(),
    routePointItems: List<RoutePointItem> = emptyList(),
    splits: List<RecordedKmSplit> = emptyList(),
    weekNumber: Int = 1,
    dayNumber: Int = 1,
    isLightMode: Boolean = false,
    onDismiss: () -> Unit
) {
    val validAge = athleteAge?.takeIf { it in 10..100 }
    val effectiveSamples = remember(hrSamples, dossier.telemetrySamples) {
        val raw = hrSamples.filter { it in 45..240 }
        if (hasHrCurve(raw)) {
            raw
        } else {
            extractValidHrCurveSamples(dossier)
        }
    }
    val calibratedMaxHr = remember(validAge, dossier.maxHeartRateBpm, dossier.effectiveMaxHeartRateBpm) {
        val fromDossier = dossier.effectiveMaxHeartRateBpm.takeIf { it > 0 }
        fromDossier ?: calculateEffectiveMaxHr(
            ageYears = validAge,
            sessionMaxHrBpm = dossier.maxHeartRateBpm.takeIf { it > 0 }
        )
    }

    FullscreenHeartRateAnalysisDialog(
        avgHr = dossier.avgHeartRateBpm,
        maxHr = dossier.maxHeartRateBpm,
        effectiveSamples = effectiveSamples,
        durationSec = dossier.durationSeconds,
        distanceMeters = (dossier.distanceKm * 1000f).roundToInt(),
        weekNumber = weekNumber,
        dayNumber = dayNumber,
        avgPaceSecPerKm = dossier.avgPaceSecPerKm,
        routePointItems = routePointItems,
        splits = splits,
        zoneStats = emptyList(),
        isLightMode = isLightMode,
        athleteAge = validAge,
        calibratedMaxHrOverride = calibratedMaxHr.takeIf { it > 0 },
        onDismiss = onDismiss
    )
}

/**
 * MODAL / TELA DEDICADA EM TELA CHEIA: "ANÁLISE CARDÍACA DA SESSÃO"
 * Exclusivo para o Resumo Pós-Treino e Dossiê Histórico (separado do Monitor Ao Vivo BLE).
 * Possui cabeçalho fixo com botão [ X ], scroll vertical com navigationBarsPadding(),
 * 3 KPIs (Média Sessão | Pico Sessão | FC Máx Calibrada) + Recuperação, Gráfico Interativo
 * com scrubbing e distribuição real de tempo/percentual nas 5 Zonas (Z1–Z5).
 */
@Composable
fun FullscreenHeartRateAnalysisDialog(
    avgHr: Int,
    maxHr: Int,
    effectiveSamples: List<Int>,
    durationSec: Int,
    distanceMeters: Int,
    weekNumber: Int,
    dayNumber: Int,
    avgPaceSecPerKm: Int,
    routePointItems: List<RoutePointItem>,
    splits: List<RecordedKmSplit>,
    zoneStats: List<Triple<HrZoneDefinition, Float, Int>> = emptyList(),
    isLightMode: Boolean,
    athleteAge: Int?,
    calibratedMaxHrOverride: Int? = null,
    onDismiss: () -> Unit
) {
    val validAge = athleteAge?.takeIf { it in 10..100 }
    val ptBr = remember { Locale("pt", "BR") }
    val bgColor = if (isLightMode) Color(0xFFF8FAFC) else Color(0xFF0D0F14)
    val cardColor = if (isLightMode) Color.White else Color(0xFF151922)
    val cardBorder = if (isLightMode) Color(0xFFE2E8F0) else Color(0xFF232A3B)
    val primaryText = if (isLightMode) Color(0xFF0F172A) else Color(0xFFF8FAFC)
    val secondaryText = if (isLightMode) Color(0xFF64748B) else Color(0xFF94A3B8)

    val calibratedMaxHr = remember(calibratedMaxHrOverride, validAge, maxHr) {
        val overrideVal = calibratedMaxHrOverride?.takeIf { it > 0 }
        overrideVal ?: calculateEffectiveMaxHr(
            ageYears = validAge,
            sessionMaxHrBpm = maxHr.takeIf { it > 0 },
            historicalPeakHrBpm = maxHr.takeIf { it > 0 }
        )
    }

    val coherent5Zones = remember(effectiveSamples, avgHr, maxHr, calibratedMaxHr, durationSec) {
        if (effectiveSamples.size >= 5 && calibratedMaxHr > 0) {
            computeCoherent5ZoneDistribution(
                hrSamples = effectiveSamples,
                avgBpm = avgHr,
                maxBpm = maxHr,
                effectiveMaxHr = calibratedMaxHr,
                durationSeconds = durationSec.coerceAtLeast(60)
            )
        } else {
            emptyList()
        }
    }

    val hasCurve = remember(effectiveSamples) { hasHrCurve(effectiveSamples) }
    val hasZones = remember(coherent5Zones) { hasHrZones(coherent5Zones) }

    val recoveryDropBpm = remember(effectiveSamples, splits) {
        calculateAverageWalkRecoveryDropBpm(effectiveSamples, splits)
    }

    var scrubbedIndex by remember(effectiveSamples) {
        mutableStateOf<Int?>(if (effectiveSamples.isNotEmpty()) effectiveSamples.size / 2 else null)
    }
    var plotWidthPx by remember { mutableFloatStateOf(1f) }

    val xAxisTicks = remember(durationSec) {
        val totalMin = (durationSec / 60f).coerceAtLeast(1f)
        val maxM = totalMin.roundToInt().coerceAtLeast(1)
        val tickCount = 7
        (0 until tickCount).map { idx ->
            if (idx == 0) {
                "0m"
            } else if (idx == tickCount - 1) {
                "${maxM}m"
            } else {
                val frac = idx.toFloat() / (tickCount - 1).toFloat()
                val m = (maxM * frac).roundToInt()
                "${m}m"
            }
        }.distinct()
    }

    val distKmText = remember(distanceMeters) {
        if (distanceMeters > 0) {
            String.format(ptBr, "%.2f km", distanceMeters / 1000f)
        } else {
            "-- km"
        }
    }
    val durationFormatted = remember(durationSec) {
        val m = durationSec / 60
        val s = durationSec % 60
        String.format(Locale.US, "%02d:%02d", m, s)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .background(bgColor),
            color = bgColor
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .navigationBarsPadding()
            ) {
                // BARRA SUPERIOR FIXA COM TÍTULO E BOTÃO FECHAR [ X ] NO TOPO DIREITO
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 12.dp)
                    ) {
                        Text(
                            text = "ANÁLISE CARDÍACA DA SESSÃO",
                            color = Color(0xFF00E5FF),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.8.sp
                        )
                        Text(
                            text = "Semana $weekNumber • Dia $dayNumber  |  $distKmText  |  $durationFormatted",
                            color = secondaryText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(42.dp)
                            .background(cardColor, CircleShape)
                            .testTag("btn_close_session_hr_modal")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Fechar Análise Cardíaca da Sessão",
                            tint = primaryText
                        )
                    }
                }

                HorizontalDivider(thickness = 0.5.dp, color = cardBorder)

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .padding(bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 1. CARD SUPERIOR: MÉDIA SESSÃO | PICO SESSÃO | FC MÁX CALIBRADA + RECUPERAÇÃO 1MIN
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = cardColor),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, cardBorder)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = "MÉDIA SESSÃO",
                                        color = secondaryText,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = if (avgHr > 0) "$avgHr bpm" else "-- bpm",
                                        color = Color(0xFF00E5FF),
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Black,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .width(1.dp)
                                        .height(36.dp)
                                        .background(cardBorder)
                                )

                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = "PICO SESSÃO",
                                        color = secondaryText,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = if (maxHr > 0) "$maxHr bpm" else "-- bpm",
                                        color = Color(0xFFFFD600),
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Black,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .width(1.dp)
                                        .height(36.dp)
                                        .background(cardBorder)
                                )

                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = "FC MÁX CALIBRADA",
                                        color = secondaryText,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1
                                    )
                                    val showMissingAge = calibratedMaxHr <= 0 && validAge == null
                                    Text(
                                        text = when {
                                            calibratedMaxHr > 0 && (avgHr > 0 || maxHr > 0) -> "$calibratedMaxHr bpm"
                                            showMissingAge -> "idade não informada"
                                            else -> "-- bpm"
                                        },
                                        color = Color(0xFF00E676),
                                        fontSize = if (showMissingAge) 11.sp else 20.sp,
                                        fontWeight = FontWeight.Black,
                                        textAlign = TextAlign.Center,
                                        maxLines = if (showMissingAge) 2 else 1,
                                        softWrap = showMissingAge
                                    )
                                }
                            }

                            HorizontalDivider(thickness = 0.5.dp, color = cardBorder)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Recuperação Cardíaca (Caminhadas / 1min)",
                                    color = secondaryText,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = if (hasCurve && recoveryDropBpm != null) "-$recoveryDropBpm bpm" else "--",
                                    color = Color(0xFF00E676),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }
                    }

                    // 2. GRÁFICO INTERATIVO DA CURVA DE BPM COM SCRUBBING
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = cardColor),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, cardBorder)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (!hasCurve) {
                                Text(
                                    text = hrCurveUnavailableMessage(avgHr, maxHr),
                                    color = secondaryText,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                )
                            } else {
                                val activeIdx = scrubbedIndex?.coerceIn(0, (effectiveSamples.size - 1).coerceAtLeast(0))
                                if (activeIdx != null) {
                                    val fraction = activeIdx.toFloat() / (effectiveSamples.size - 1).coerceAtLeast(1)
                                    val activeBpm = effectiveSamples[activeIdx]
                                    val activeZone = getHrZoneForBpm(activeBpm)
                                    val (badgeBg, badgeFg) = getZoneBadgeColors(activeZone, isLightMode)
                                    val activeTimeSec = (fraction * durationSec.coerceAtLeast(1)).roundToInt()
                                    val mm = activeTimeSec / 60
                                    val ss = activeTimeSec % 60
                                    val timeFormatted = String.format(Locale.US, "%02d:%02d", mm, ss)
                                    val (phaseBlockLabel, instantPaceLabel) = remember(
                                        activeIdx,
                                        activeBpm,
                                        avgHr,
                                        avgPaceSecPerKm,
                                        routePointItems,
                                        splits
                                    ) {
                                        resolvePhaseAndPaceAtFraction(
                                            fraction = fraction,
                                            bpmAtPoint = activeBpm,
                                            avgHr = avgHr,
                                            avgPaceSecPerKm = avgPaceSecPerKm,
                                            routePointItems = routePointItems,
                                            splits = splits
                                        )
                                    }

                                    Surface(
                                        color = if (isLightMode) Color(0xFFF1F5F9) else Color(0xFF0D0F14),
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(1.dp, activeZone.color.copy(alpha = 0.65f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(10.dp)
                                                            .background(activeZone.color, CircleShape)
                                                    )
                                                    Text(
                                                        text = "Minuto $timeFormatted • $activeBpm bpm",
                                                        color = primaryText,
                                                        fontSize = 14.sp,
                                                        fontWeight = FontWeight.Black
                                                    )
                                                }
                                                Surface(
                                                    color = badgeBg,
                                                    shape = RoundedCornerShape(6.dp)
                                                ) {
                                                    Text(
                                                        text = activeZone.name,
                                                        color = badgeFg,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        maxLines = 1,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }

                                            Text(
                                                text = "Bloco: $phaseBlockLabel • Pace: $instantPaceLabel",
                                                color = secondaryText,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                softWrap = false,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }

                                val yAxisReservedWidth = 30.dp
                                val yAxisSpacerWidth = 8.dp
                                val totalRightAxisPadding = yAxisReservedWidth + yAxisSpacerWidth

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(260.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .onSizeChanged { size ->
                                                plotWidthPx = size.width.toFloat().coerceAtLeast(1f)
                                            }
                                            .pointerInput(effectiveSamples) {
                                                detectTapGestures { offset ->
                                                    if (effectiveSamples.isNotEmpty()) {
                                                        val ratio = (offset.x / plotWidthPx).coerceIn(0f, 1f)
                                                        scrubbedIndex = (ratio * (effectiveSamples.size - 1)).roundToInt()
                                                            .coerceIn(0, effectiveSamples.lastIndex)
                                                    }
                                                }
                                            }
                                            .pointerInput(effectiveSamples) {
                                                detectHorizontalDragGestures(
                                                    onDragStart = { offset ->
                                                        if (effectiveSamples.isNotEmpty()) {
                                                            val ratio = (offset.x / plotWidthPx).coerceIn(0f, 1f)
                                                            scrubbedIndex = (ratio * (effectiveSamples.size - 1)).roundToInt()
                                                                .coerceIn(0, effectiveSamples.lastIndex)
                                                        }
                                                    },
                                                    onHorizontalDrag = { change, _ ->
                                                        change.consume()
                                                        if (effectiveSamples.isNotEmpty()) {
                                                            val ratio = (change.position.x / plotWidthPx).coerceIn(0f, 1f)
                                                            scrubbedIndex = (ratio * (effectiveSamples.size - 1)).roundToInt()
                                                                .coerceIn(0, effectiveSamples.lastIndex)
                                                        }
                                                    }
                                                )
                                            }
                                    ) {
                                        HaylouHeartRateCanvas(
                                            samples = effectiveSamples,
                                            isLightMode = isLightMode,
                                            activeIdx = activeIdx,
                                            strokeWidthDp = 2.8f
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(yAxisSpacerWidth))

                                    Column(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .width(yAxisReservedWidth),
                                        verticalArrangement = Arrangement.SpaceBetween,
                                        horizontalAlignment = Alignment.End
                                    ) {
                                        Text("180", color = secondaryText, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                        Text("155", color = secondaryText, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                        Text("130", color = secondaryText, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                        Text("105", color = secondaryText, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                        Text("80", color = secondaryText, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                    }
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(end = totalRightAxisPadding),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    xAxisTicks.forEachIndexed { idx, tick ->
                                        Text(
                                            text = tick,
                                            color = secondaryText,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            textAlign = when (idx) {
                                                0 -> TextAlign.Start
                                                xAxisTicks.lastIndex -> TextAlign.End
                                                else -> TextAlign.Center
                                            }
                                        )
                                    }
                                }

                                Text(
                                    text = "Deslize o dedo sobre o gráfico para inspecionar batimentos, bloco e pace segundo a segundo.",
                                    color = secondaryText,
                                    fontSize = 11.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    // 3. DISTRIBUIÇÃO REAL DE TEMPO E PERCENTUAL NAS 5 ZONAS (Z1 A Z5)
                    if (hasCurve || hasZones) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = cardColor),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, cardBorder)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "DISTRIBUIÇÃO NAS 5 ZONAS (Z1 – Z5)",
                                        color = primaryText,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Black,
                                        maxLines = 1
                                    )
                                    if (hasZones) {
                                        Text(
                                            text = "% (MM:SS)",
                                            color = secondaryText,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }

                                if (!hasZones) {
                                    Text(
                                        text = "Zonas indisponíveis",
                                        color = secondaryText,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                    )
                                } else coherent5Zones.forEach { zItem ->
                                    val zColor = Color(zItem.colorHex)
                                    val pctInt = zItem.percentage.roundToInt().coerceIn(0, 100)
                                    val progressFraction = if (pctInt <= 0) 0f else (pctInt / 100f).coerceIn(0f, 1f)
                                    val rangeLabel = if (zItem.zoneIndex == 1) {
                                        "≤ ${zItem.maxBpm} bpm (<62% FCmáx)"
                                    } else if (zItem.zoneIndex == 5) {
                                        "≥ ${zItem.minBpm} bpm (>92% FCmáx)"
                                    } else {
                                        "${zItem.minBpm}–${zItem.maxBpm} bpm"
                                    }

                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color(0xFF0D0F14), RoundedCornerShape(10.dp))
                                            .padding(horizontal = 12.dp, vertical = 9.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .padding(end = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Surface(
                                                    color = zColor.copy(alpha = 0.22f),
                                                    shape = RoundedCornerShape(6.dp)
                                                ) {
                                                    Text(
                                                        text = "Z${zItem.zoneIndex}",
                                                        color = zColor,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Black,
                                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                                    )
                                                }
                                                Column {
                                                    Text(
                                                        text = zItem.label,
                                                        color = primaryText,
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Text(
                                                        text = rangeLabel,
                                                        color = secondaryText,
                                                        fontSize = 10.sp,
                                                        maxLines = 1
                                                    )
                                                }
                                            }

                                            Text(
                                                text = "${pctInt}% (${zItem.formattedDuration})",
                                                color = if (pctInt > 0) primaryText else secondaryText,
                                                fontSize = 12.sp,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Black,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        }

                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(7.dp)
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(if (isLightMode) Color(0xFFE2E8F0) else Color(0xFF1E2433))
                                        ) {
                                            if (progressFraction > 0f) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxHeight()
                                                        .fillMaxWidth(progressFraction)
                                                        .background(zColor, RoundedCornerShape(4.dp))
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}
