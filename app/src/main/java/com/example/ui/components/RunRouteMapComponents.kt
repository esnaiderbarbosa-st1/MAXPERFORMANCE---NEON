package com.example.ui.components

import android.graphics.PointF
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.engine.FreeRunRaceMode
import com.example.engine.RunningBlockType
import com.example.engine.RunningProgramManager
import com.example.engine.RunningWorkoutPlan
import com.example.utils.AudioSynth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Gerenciador Persistido de Escala Dinâmica de Fonte do MAX RUN (`runUiFontScale`).
 * Níveis suportados:
 * - `1.00f` (Normal — Padrão 100%)
 * - `1.20f` (Grande — +20% / 120%)
 * - `1.35f` (Extra Grande — +35% / 135%)
 */
object RunFontScaleManager {
    private const val PREFS_NAME = "max_run_ui_prefs"
    private const val KEY_FONT_SCALE = "run_ui_font_scale"

    val LEVELS = listOf(1.0f, 1.20f, 1.35f)

    private val _scaleFlow = MutableStateFlow(1.0f)
    @Volatile
    private var isInitialized = false

    fun getFlow(context: android.content.Context): StateFlow<Float> {
        if (!isInitialized) {
            synchronized(this) {
                if (!isInitialized) {
                    val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
                    val saved = prefs.getFloat(KEY_FONT_SCALE, 1.0f)
                    _scaleFlow.value = normalizeLevel(saved)
                    isInitialized = true
                }
            }
        }
        return _scaleFlow
    }

    fun normalizeLevel(raw: Float): Float {
        return when {
            raw >= 1.30f -> 1.35f
            raw >= 1.12f -> 1.20f
            else -> 1.0f
        }
    }

    fun setScale(context: android.content.Context, targetScale: Float): Float {
        val normalized = normalizeLevel(targetScale)
        _scaleFlow.value = normalized
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
            .edit()
            .putFloat(KEY_FONT_SCALE, normalized)
            .apply()
        return normalized
    }

    fun cycleScale(context: android.content.Context): Float {
        getFlow(context)
        val current = _scaleFlow.value
        val next = when {
            current < 1.12f -> 1.20f
            current < 1.30f -> 1.35f
            else -> 1.0f
        }
        return setScale(context, next)
    }

    fun stepScale(context: android.content.Context, stepDelta: Int): Float {
        getFlow(context)
        val currentIdx = LEVELS.indexOfFirst { kotlin.math.abs(it - _scaleFlow.value) < 0.05f }.coerceAtLeast(0)
        val nextIdx = (currentIdx + stepDelta).coerceIn(0, LEVELS.lastIndex)
        return setScale(context, LEVELS[nextIdx])
    }

    fun formatPercentLabel(scale: Float): String = "${(scale * 100f).roundToInt()}%"
}

private val LocalBaseRunFontScale = compositionLocalOf<Float?> { null }

@Composable
fun rememberRunUiFontScale(): Float {
    val context = LocalContext.current
    val flow = remember(context) { RunFontScaleManager.getFlow(context) }
    val scale by flow.collectAsState()
    return scale
}

/**
 * Wrapper que aplica a escala dinâmica de fonte (`runUiFontScale`) via `LocalDensity`
 * sem acumular multiplicações caso haja `ProvideRunFontScale` aninhados.
 */
@Composable
fun ProvideRunFontScale(
    scale: Float = rememberRunUiFontScale(),
    content: @Composable () -> Unit
) {
    val currentDensity = LocalDensity.current
    val baseFontScale = LocalBaseRunFontScale.current ?: currentDensity.fontScale
    val effectiveFontScale = (baseFontScale * scale).coerceIn(0.85f, 1.65f)
    val scaledDensity = remember(currentDensity.density, effectiveFontScale) {
        Density(
            density = currentDensity.density,
            fontScale = effectiveFontScale
        )
    }
    CompositionLocalProvider(
        LocalBaseRunFontScale provides baseFontScale,
        LocalDensity provides scaledDensity,
        content = content
    )
}

/**
 * Seletor rápido de tamanho de fonte ("Aa +" / "A- | 120% | A+") para cabeçalhos de modais e barra inicial do MAX RUN.
 */
@Composable
fun RunFontScaleToggleButton(
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val context = LocalContext.current
    val currentScale = rememberRunUiFontScale()
    val pctText = RunFontScaleManager.formatPercentLabel(currentScale)
    val isBoosted = currentScale > 1.01f
    val accentColor = if (isBoosted) Color(0xFF00E5FF) else Color(0xFFCBD5E1)

    // Mantém o próprio botão de controle em escala base 1.0f para nunca quebrar o cabeçalho
    ProvideRunFontScale(scale = 1.0f) {
        if (compact) {
            Surface(
                onClick = {
                    AudioSynth.playTickSound()
                    RunFontScaleManager.cycleScale(context)
                },
                color = if (isBoosted) Color(0xFF00E5FF).copy(alpha = 0.16f) else Color(0xFF151922),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(
                    1.dp,
                    if (isBoosted) Color(0xFF00E5FF) else Color(0xFF2E3646)
                ),
                modifier = modifier
                    .height(36.dp)
                    .testTag("btn_run_font_scale_compact")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Aa+",
                        color = accentColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        softWrap = false
                    )
                    Text(
                        text = pctText,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        } else {
            Surface(
                color = Color(0xFF151922),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(
                    1.dp,
                    if (isBoosted) Color(0xFF00E5FF).copy(alpha = 0.7f) else Color(0xFF2E3646)
                ),
                modifier = modifier
                    .height(36.dp)
                    .testTag("btn_run_font_scale_pill")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                AudioSynth.playTickSound()
                                if (currentScale <= 1.01f) {
                                    RunFontScaleManager.setScale(context, 1.35f)
                                } else {
                                    RunFontScaleManager.stepScale(context, -1)
                                }
                            }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                            .testTag("btn_run_font_scale_minus"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "A-",
                            color = if (currentScale > 1.01f) Color.White else Color(0xFF94A3B8),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isBoosted) Color(0xFF00E5FF).copy(alpha = 0.16f) else Color.Transparent)
                            .clickable {
                                AudioSynth.playTickSound()
                                RunFontScaleManager.cycleScale(context)
                            }
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = pctText,
                            color = accentColor,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                AudioSynth.playTickSound()
                                if (currentScale >= 1.34f) {
                                    RunFontScaleManager.setScale(context, 1.0f)
                                } else {
                                    RunFontScaleManager.stepScale(context, +1)
                                }
                            }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                            .testTag("btn_run_font_scale_plus"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "A+",
                            color = Color(0xFF00E5FF),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }
    }
}

/**
 * Motor de Deslocamento Lateral de Trajeto ("Lane Offset" / Directional Perpendicular Polyline Offset)
 * para percursos de corrida de Ida e Volta ou Voltas Repetidas.
 */
object RunRouteLaneOffsetEngine {

    val WalkColor = Color(0xFF0088FF)
    val SprintColor = Color(0xFF2ECC71)
    val BestSprintColor = Color(0xFFFFD600)
    val WarmupColor = Color(0xFF94A3B8)
    val ContrastBorderColor = Color(0xFF0D0F14).copy(alpha = 0.75f)

    const val WALK_COLOR_HEX = 0xFF0088FF.toInt()
    const val SPRINT_COLOR_HEX = 0xFF2ECC71.toInt()
    const val BEST_SPRINT_COLOR_HEX = 0xFFFFD600.toInt()
    const val WARMUP_COLOR_HEX = 0xFF94A3B8.toInt()
    const val CONTRAST_BORDER_HEX = 0xBF0D0F14.toInt() // #0D0F14 com alpha 0.75f (191/255)

    fun applyDirectionalLaneOffset(
        rawPoints: List<Offset>,
        gpsCoords: List<Pair<Double, Double>> = emptyList(),
        offsetPx: Float,
        corridorThresholdMeters: Double = 12.0
    ): List<Offset> {
        if (rawPoints.size < 2 || offsetPx <= 0.05f) return rawPoints

        val n = rawPoints.size
        val segCount = n - 1
        val hasGps = gpsCoords.size == n

        // 1. Vetores unitários de direção (dx, dy) e normal à direita (nx = -dy, ny = dx) por segmento
        val dxArr = FloatArray(segCount)
        val dyArr = FloatArray(segCount)
        val segLenPx = FloatArray(segCount)
        val cumLenPx = FloatArray(n)
        val cumMeters = DoubleArray(n)

        var lastValidDx = 1f
        var lastValidDy = 0f

        for (i in 0 until segCount) {
            val p1 = rawPoints[i]
            val p2 = rawPoints[i + 1]
            val rawDx = p2.x - p1.x
            val rawDy = p2.y - p1.y
            val len = hypot(rawDx, rawDy)
            segLenPx[i] = len
            cumLenPx[i + 1] = cumLenPx[i] + len

            if (hasGps) {
                val (lat1, lon1) = gpsCoords[i]
                val (lat2, lon2) = gpsCoords[i + 1]
                cumMeters[i + 1] = cumMeters[i] + haversineMeters(lat1, lon1, lat2, lon2)
            }

            if (len > 0.04f) {
                val uDx = rawDx / len
                val uDy = rawDy / len
                dxArr[i] = uDx
                dyArr[i] = uDy
                lastValidDx = uDx
                lastValidDy = uDy
            } else {
                dxArr[i] = lastValidDx
                dyArr[i] = lastValidDy
            }
        }

        // Back-propagate first valid tangent if segment 0 had zero length
        for (i in segCount - 2 downTo 0) {
            if (segLenPx[i] <= 0.04f) {
                dxArr[i] = dxArr[i + 1]
                dyArr[i] = dyArr[i + 1]
            }
        }

        // Suavização leve de tangentes em micro-segmentos ruidosos de GPS
        val smoothDx = FloatArray(segCount)
        val smoothDy = FloatArray(segCount)
        for (i in 0 until segCount) {
            val prevIdx = max(0, i - 1)
            val nextIdx = min(segCount - 1, i + 1)
            val dotPrevNext = dxArr[prevIdx] * dxArr[nextIdx] + dyArr[prevIdx] * dyArr[nextIdx]
            // Preserva curvas acentuadas e U-turns, suavizando apenas oscilações colineares
            if (dotPrevNext > 0.35f) {
                val sx = dxArr[prevIdx] * 0.22f + dxArr[i] * 0.56f + dxArr[nextIdx] * 0.22f
                val sy = dyArr[prevIdx] * 0.22f + dyArr[i] * 0.56f + dyArr[nextIdx] * 0.22f
                val sLen = hypot(sx, sy)
                if (sLen > 0.001f) {
                    smoothDx[i] = sx / sLen
                    smoothDy[i] = sy / sLen
                } else {
                    smoothDx[i] = dxArr[i]
                    smoothDy[i] = dyArr[i]
                }
            } else {
                smoothDx[i] = dxArr[i]
                smoothDy[i] = dyArr[i]
            }
        }

        // Vetor normal perpendicular à direita: nx = -dy, ny = dx
        val nxArr = FloatArray(segCount) { i -> -smoothDy[i] }
        val nyArr = FloatArray(segCount) { i -> smoothDx[i] }

        // 2. Detecção de sobreposição espacial (Ida vs. Volta com dot < -0.2 e Voltas Repetidas com dot >= -0.2)
        val midX = FloatArray(segCount) { i -> (rawPoints[i].x + rawPoints[i + 1].x) * 0.5f }
        val midY = FloatArray(segCount) { i -> (rawPoints[i].y + rawPoints[i + 1].y) * 0.5f }
        val midLat = DoubleArray(if (hasGps) segCount else 0) { i -> (gpsCoords[i].first + gpsCoords[i + 1].first) * 0.5 }
        val midLon = DoubleArray(if (hasGps) segCount else 0) { i -> (gpsCoords[i].second + gpsCoords[i + 1].second) * 0.5 }

        val proximityPx = max(offsetPx * 3.8f, 11f)
        val proximityPxSq = proximityPx * proximityPx
        val minLoopPx = max(offsetPx * 8.5f, 34f)
        val minLoopMeters = 28.0

        var hasAnyOverlap = false
        val sameDirLaneIndex = IntArray(segCount)

        for (i in 0 until segCount) {
            var sameDirectionPasses = 0
            var lastMatchedPassDistPx = -1e9f
            var lastMatchedPassMeters = -1e9

            for (j in 0 until i) {
                val pathDiffPx = cumLenPx[i] - cumLenPx[j]
                if (pathDiffPx < minLoopPx) continue
                if (hasGps && (cumMeters[i] - cumMeters[j]) < minLoopMeters) continue

                val dScreenX = midX[i] - midX[j]
                val dScreenY = midY[i] - midY[j]
                val distSqPx = dScreenX * dScreenX + dScreenY * dScreenY

                val isInCorridor = if (hasGps) {
                    val dLat = kotlin.math.abs(midLat[i] - midLat[j])
                    val dLon = kotlin.math.abs(midLon[i] - midLon[j])
                    val withinMeters = if (dLat < 0.00025 && dLon < 0.00025) {
                        haversineMeters(midLat[i], midLon[i], midLat[j], midLon[j]) <= corridorThresholdMeters
                    } else false
                    withinMeters || distSqPx <= proximityPxSq
                } else {
                    distSqPx <= proximityPxSq
                }

                if (isInCorridor) {
                    hasAnyOverlap = true
                    val dot = smoothDx[i] * smoothDx[j] + smoothDy[i] * smoothDy[j]
                    if (dot >= -0.2f) {
                        val isNewDistinctPass = if (hasGps) {
                            (cumMeters[j] - lastMatchedPassMeters) >= minLoopMeters
                        } else {
                            (cumLenPx[j] - lastMatchedPassDistPx) >= minLoopPx
                        }
                        if (isNewDistinctPass) {
                            sameDirectionPasses++
                            lastMatchedPassDistPx = cumLenPx[j]
                            if (hasGps) lastMatchedPassMeters = cumMeters[j]
                        }
                    }
                }
            }
            sameDirLaneIndex[i] = sameDirectionPasses.coerceIn(0, 3)
        }

        val baseLaneFactor = if (hasAnyOverlap) 1.0f else 0.35f
        val rawSegOffset = FloatArray(segCount) { i ->
            val laneIdx = sameDirLaneIndex[i]
            if (laneIdx == 0) {
                baseLaneFactor * offsetPx
            } else {
                (baseLaneFactor + laneIdx * 1.45f) * offsetPx
            }
        }

        val smoothSegOffset = FloatArray(segCount) { i ->
            var sum = 0f
            var count = 0
            for (k in max(0, i - 2)..min(segCount - 1, i + 2)) {
                sum += rawSegOffset[k]
                count++
            }
            sum / count.coerceAtLeast(1)
        }

        // 3. Suavização de Vértices (Miter / Smooth Join): (n_prev + n_next).normalized()
        val vertexNx = FloatArray(n)
        val vertexNy = FloatArray(n)
        val vertexOffset = FloatArray(n)

        vertexNx[0] = nxArr[0]
        vertexNy[0] = nyArr[0]
        vertexOffset[0] = smoothSegOffset[0]

        vertexNx[n - 1] = nxArr[segCount - 1]
        vertexNy[n - 1] = nyArr[segCount - 1]
        vertexOffset[n - 1] = smoothSegOffset[segCount - 1]

        for (v in 1 until n - 1) {
            val nPrevX = nxArr[v - 1]
            val nPrevY = nyArr[v - 1]
            val nNextX = nxArr[v]
            val nNextY = nyArr[v]

            val sumX = nPrevX + nNextX
            val sumY = nPrevY + nNextY
            val sumLen = hypot(sumX, sumY)

            val avgOffset = (smoothSegOffset[v - 1] + smoothSegOffset[v]) * 0.5f

            if (sumLen >= 0.35f) {
                val bx = sumX / sumLen
                val by = sumY / sumLen
                val cosHalf = (bx * nPrevX + by * nPrevY).coerceAtLeast(0.58f)
                val miterScale = (1f / cosHalf).coerceIn(1f, 1.32f)
                vertexNx[v] = bx
                vertexNy[v] = by
                vertexOffset[v] = avgOffset * miterScale
            } else {
                val turnX = sumX + smoothDx[v - 1]
                val turnY = sumY + smoothDy[v - 1]
                val turnLen = hypot(turnX, turnY)
                if (turnLen > 0.001f) {
                    vertexNx[v] = turnX / turnLen
                    vertexNy[v] = turnY / turnLen
                } else {
                    vertexNx[v] = nPrevX
                    vertexNy[v] = nPrevY
                }
                vertexOffset[v] = avgOffset
            }
        }

        return List(n) { v ->
            val prevV = max(0, v - 1)
            val nextV = min(n - 1, v + 1)
            var sx = vertexNx[prevV] * 0.20f + vertexNx[v] * 0.60f + vertexNx[nextV] * 0.20f
            var sy = vertexNy[prevV] * 0.20f + vertexNy[v] * 0.60f + vertexNy[nextV] * 0.20f
            val sLen = hypot(sx, sy)
            if (sLen > 0.001f) {
                sx /= sLen
                sy /= sLen
            } else {
                sx = vertexNx[v]
                sy = vertexNy[v]
            }
            val disp = vertexOffset[v]
            Offset(
                x = rawPoints[v].x + sx * disp,
                y = rawPoints[v].y + sy * disp
            )
        }
    }

    fun applyDirectionalLaneOffsetPointF(
        rawPoints: List<PointF>,
        gpsCoords: List<Pair<Double, Double>> = emptyList(),
        offsetPx: Float,
        corridorThresholdMeters: Double = 12.0
    ): List<PointF> {
        if (rawPoints.size < 2 || offsetPx <= 0.05f) return rawPoints
        val composeOffsets = rawPoints.map { Offset(it.x, it.y) }
        val shifted = applyDirectionalLaneOffset(
            rawPoints = composeOffsets,
            gpsCoords = gpsCoords,
            offsetPx = offsetPx,
            corridorThresholdMeters = corridorThresholdMeters
        )
        return shifted.map { PointF(it.x, it.y) }
    }

    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2.0) * sin(dLat / 2.0) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2.0) * sin(dLon / 2.0)
        val c = 2.0 * atan2(sqrt(a), sqrt((1.0 - a).coerceAtLeast(0.0)))
        return r * c
    }
}

private data class ExplainedWorkoutStep(
    val stepNumber: Int,
    val title: String,
    val durationLabel: String,
    val subtitle: String,
    val indicatorColor: Color,
    val badgeLabel: String
)

private fun formatHumanizedDuration(durationSec: Int): String {
    if (durationSec <= 0) return "0 min"
    val mins = durationSec / 60
    val remSec = durationSec % 60
    return when {
        mins == 0 -> "${remSec}s"
        remSec == 0 -> "$mins min"
        remSec == 30 -> "$mins,5 min"
        else -> "$mins min ${remSec}s"
    }
}

/**
 * Modal de Tela Cheia ("Raio-X da Sessão") com explicação humanizada passo a passo
 * do treino do dia, sem siglas técnicas soltas (como "R" ou "W").
 */
@Composable
fun WorkoutStepByStepExplainerDialog(
    plan: RunningWorkoutPlan,
    weekNumber: Int,
    dayNumber: Int,
    programTitle: String,
    freeRaceMode: FreeRunRaceMode? = null,
    isTrack400mMode: Boolean = false,
    onDismiss: () -> Unit,
    onStartWorkoutNow: () -> Unit,
    onOpenRecoveryDialog: (() -> Unit)? = null
) {
    val totalRunSec = plan.blocks.filter { it.type == RunningBlockType.RUN }.sumOf { it.durationSec }
    val totalWalkSec = plan.blocks.filter { it.type == RunningBlockType.WALK }.sumOf { it.durationSec }
    val totalWarmupCooldownSec = plan.blocks.filter {
        it.type == RunningBlockType.WARMUP || it.type == RunningBlockType.COOL_DOWN
    }.sumOf { it.durationSec }
    val totalDurationSec = plan.blocks.sumOf { it.durationSec }
    val totalRunBlocksCount = plan.blocks.count { it.type == RunningBlockType.RUN }
    val totalWalkBlocksCount = plan.blocks.count { it.type == RunningBlockType.WALK }

    val sessionSubtitle = when {
        freeRaceMode != null -> "${freeRaceMode.fullTitle} • ${freeRaceMode.subtitle}"
        isTrack400mMode -> "Semana $weekNumber · Pista 400m • Disparo Manual"
        else -> "Semana $weekNumber · Dia $dayNumber • ${(totalDurationSec / 60.0).roundToInt()} min totais"
    }

    val friendlyExplanationText = when {
        freeRaceMode == FreeRunRaceMode.FREE_RUN ->
            "Hoje a sessão é de Corrida Livre: você corre no seu próprio ritmo e encerra quando desejar. Comece devagar nos primeiros minutos para aquecer o corpo e mantenha uma respiração confortável."
        freeRaceMode == FreeRunRaceMode.RACE_5K || freeRaceMode == FreeRunRaceMode.RACE_10K ->
            "Sessão de meta de distância (${freeRaceMode.subtitle}). O foco é manter um ritmo constante do início ao fim, evitando largar rápido demais no primeiro quilômetro."
        isTrack400mMode ->
            "Sessão de voltas de 400 metros na pista com descanso controlado. A cada volta concluída você descansa para recuperar o fôlego e dispara a próxima volta manualmente."
        totalRunBlocksCount > 1 && totalWalkBlocksCount > 0 ->
            "Hoje você vai alternar $totalRunBlocksCount blocos de corrida (${formatHumanizedDuration(totalRunSec)} no total) com pausas de caminhada (${formatHumanizedDuration(totalWalkSec)}) para recuperar o fôlego. Não precisa correr no seu limite: mantenha um ritmo em que consiga respirar de forma controlada."
        totalRunBlocksCount == 1 && totalWalkBlocksCount == 0 ->
            "Hoje você fará um bloco contínuo de corrida de ${formatHumanizedDuration(totalRunSec)}, precedido pelo aquecimento e finalizado com desaquecimento leve. Mantenha uma passada solta e constante."
        else ->
            "Siga as orientações de voz a cada etapa. O aplicativo avisará exatamente quando começar a correr e quando caminhar para recuperar."
    }

    val timelineSteps = buildList {
        var runCounter = 0
        var walkCounter = 0
        var stepIdx = 1
        plan.blocks.forEach { block ->
            val durStr = formatHumanizedDuration(block.durationSec)
            when (block.type) {
                RunningBlockType.WARMUP -> {
                    val isStrides = block.durationSec < 60 || block.label.contains("Acelera", ignoreCase = true)
                    add(
                        ExplainedWorkoutStep(
                            stepNumber = stepIdx++,
                            title = if (isStrides) "Educativo • ${block.label} ($durStr)" else "Aquecimento ($durStr)",
                            durationLabel = durStr,
                            subtitle = if (isStrides) {
                                "Aceleração progressiva leve para ativar a musculatura antes do bloco principal."
                            } else {
                                "Caminhada rápida ou trote bem leve para soltar as articulações e elevar os batimentos aos poucos."
                            },
                            indicatorColor = Color(0xFFF59E0B),
                            badgeLabel = "PREPARAÇÃO"
                        )
                    )
                }
                RunningBlockType.RUN -> {
                    runCounter++
                    val titleText = if (totalRunBlocksCount > 1) {
                        "Corrida • Tiro $runCounter ($durStr)"
                    } else {
                        "Corrida Principal ($durStr)"
                    }
                    add(
                        ExplainedWorkoutStep(
                            stepNumber = stepIdx++,
                            title = titleText,
                            durationLabel = durStr,
                            subtitle = "Corra em ritmo constante e confortável (esforço moderado, respiração firme e controlada).",
                            indicatorColor = Color(0xFF2ECC71),
                            badgeLabel = "CORRIDA"
                        )
                    )
                }
                RunningBlockType.WALK -> {
                    walkCounter++
                    add(
                        ExplainedWorkoutStep(
                            stepNumber = stepIdx++,
                            title = "Recuperação • Caminhada ($durStr)",
                            durationLabel = durStr,
                            subtitle = "Caminhe devagar e respire fundo pelo nariz para baixar os batimentos antes do próximo bloco.",
                            indicatorColor = Color(0xFF0088FF),
                            badgeLabel = "CAMINHADA"
                        )
                    )
                }
                RunningBlockType.COOL_DOWN -> {
                    add(
                        ExplainedWorkoutStep(
                            stepNumber = stepIdx++,
                            title = "Desaquecimento ($durStr)",
                            durationLabel = durStr,
                            subtitle = "Caminhada leve final para voltar à calma e iniciar a recuperação muscular.",
                            indicatorColor = Color(0xFF8B5CF6),
                            badgeLabel = "VOLTA À CALMA"
                        )
                    )
                }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = true
        )
    ) {
        ProvideRunFontScale {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(start = 12.dp, end = 12.dp, top = 16.dp, bottom = 48.dp)
                    .testTag("dialog_workout_step_by_step_explainer"),
                shape = RoundedCornerShape(24.dp),
                color = Color(0xFF0D0F14),
                border = BorderStroke(1.dp, Color(0xFF262D3D))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(18.dp)
                ) {
                    // A) Cabeçalho + Seletor Rápido A- / A+
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                        ) {
                            Text(
                                text = programTitle.uppercase(Locale.getDefault()),
                                color = Color(0xFF00E5FF),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Como funciona o treino de hoje",
                                color = Color.White,
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Black
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = sessionSubtitle,
                                color = Color(0xFF94A3B8),
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RunFontScaleToggleButton(compact = false)

                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF151922))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Fechar explicação",
                                    tint = Color(0xFFCBD5E1),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                Spacer(modifier = Modifier.height(12.dp))

                // Conteúdo rolável: Resumo em Português Claro + Caixas + Timeline Passo a Passo
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Card "O que você vai fazer hoje"
                    item {
                        Surface(
                            color = Color(0xFF151922),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, Color(0xFF232A38)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "O QUE VOCÊ VAI FAZER HOJE",
                                    color = Color(0xFF38BDF8),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 0.6.sp
                                )
                                Text(
                                    text = friendlyExplanationText,
                                    color = Color(0xFFE2E8F0),
                                    fontSize = 13.5.sp,
                                    lineHeight = 19.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    // B) Caixas de Resumo Mastigado (Lado a Lado)
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Tempo Total Correndo
                            Surface(
                                color = Color(0xFF2ECC71).copy(alpha = 0.12f),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, Color(0xFF2ECC71).copy(alpha = 0.45f)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = formatHumanizedDuration(totalRunSec),
                                        color = Color(0xFF2ECC71),
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Black,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                    Text(
                                        text = "Total Correndo",
                                        color = Color(0xFFCBD5E1),
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            // Tempo Total Caminhando
                            Surface(
                                color = Color(0xFF00B4D8).copy(alpha = 0.12f),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, Color(0xFF00B4D8).copy(alpha = 0.45f)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = formatHumanizedDuration(totalWalkSec),
                                        color = Color(0xFF00B4D8),
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Black,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                    Text(
                                        text = "Total Caminhando",
                                        color = Color(0xFFCBD5E1),
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            // Aquec. + Desaquec.
                            Surface(
                                color = Color(0xFFF59E0B).copy(alpha = 0.12f),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.40f)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = formatHumanizedDuration(totalWarmupCooldownSec),
                                        color = Color(0xFFFBBF24),
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Black,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                    Text(
                                        text = "Aquec. + Calma",
                                        color = Color(0xFFCBD5E1),
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    // C) Título da Linha do Tempo Passo a Passo
                    item {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "PASSO A PASSO DA SESSÃO (${timelineSteps.size} ETAPAS)",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.6.sp
                        )
                    }

                    itemsIndexed(timelineSteps) { index, step ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFF131720))
                                .border(1.dp, step.indicatorColor.copy(alpha = 0.30f), RoundedCornerShape(14.dp))
                                .padding(12.dp),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Círculo Numerado com a cor semântica da etapa
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(step.indicatorColor.copy(alpha = 0.18f))
                                    .border(1.5.dp, step.indicatorColor, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${index + 1}",
                                    color = step.indicatorColor,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = step.title,
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        color = step.indicatorColor.copy(alpha = 0.16f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = step.badgeLabel,
                                            color = step.indicatorColor,
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Text(
                                    text = step.subtitle,
                                    color = Color(0xFF94A3B8),
                                    fontSize = 12.sp,
                                    lineHeight = 16.5.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (onOpenRecoveryDialog != null) {
                    Surface(
                        onClick = onOpenRecoveryDialog,
                        color = Color(0xFF151922),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.45f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .padding(bottom = 8.dp)
                            .testTag("btn_open_recovery_from_explainer")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF00E5FF),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Já concluiu este treino? Registrar no Histórico",
                                color = Color(0xFF00E5FF),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                // D) Botões Inferiores: "Fechar" e "Iniciar Treino" na mesma altura (52.dp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFF334155)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .testTag("btn_close_workout_explainer")
                    ) {
                        Text(
                            text = "Fechar",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            textAlign = TextAlign.Center
                        )
                    }

                    Button(
                        onClick = onStartWorkoutNow,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF2ECC71),
                            contentColor = Color(0xFF06130B)
                        ),
                        modifier = Modifier
                            .weight(1.15f)
                            .height(52.dp)
                            .testTag("btn_start_from_workout_explainer")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Iniciar Treino",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                            softWrap = false,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
        }
    }
}

/**
 * Modal de Registro / Recuperação de Treino Realizado da Planilha (`RecoverCompletedWorkoutDialog`).
 * Permite marcar rapidamente como concluído um treino guiado (ex: Semana 1 · Dia 3 - W1D3)
 * com duração e distância aproximada pré-preenchidas a partir da planilha, salvando no Histórico
 * e avançando a progressão automaticamente para o próximo treino.
 */
@Composable
fun RecoverCompletedWorkoutDialog(
    programId: String,
    initialWeek: Int,
    initialDay: Int,
    baselinePaceMinPerKm: Float? = null,
    defaultShoeName: String = "Olympikus Corre 4",
    onDismiss: () -> Unit,
    onConfirmRegister: (
        weekNumber: Int,
        dayNumber: Int,
        sessionType: String,
        durationSec: Int,
        distanceMeters: Int,
        environment: String,
        shoeName: String
    ) -> Unit
) {
    var selectedWeek by remember { mutableIntStateOf(initialWeek.coerceIn(1, 10)) }
    var selectedDay by remember { mutableIntStateOf(initialDay.coerceIn(1, 3)) }
    var selectedEnvironment by remember { mutableStateOf("OUTDOOR") }

    val selectedPlan = remember(programId, selectedWeek, selectedDay) {
        RunningProgramManager.getPlanForProgram(programId, selectedWeek, selectedDay)
    }

    val defaultDurationMin = remember(selectedPlan) {
        val totalSec = selectedPlan.blocks.sumOf { it.durationSec }
        (totalSec / 60).coerceAtLeast(15)
    }

    val defaultEstimatedKmStr = remember(selectedPlan, baselinePaceMinPerKm) {
        val totalSec = selectedPlan.blocks.sumOf { it.durationSec }
        val estM = com.example.services.RunTracker.estimateFallbackDistanceMeters(
            plan = selectedPlan,
            durationSec = totalSec,
            skippedIndices = emptySet(),
            baselinePaceMinPerKm = baselinePaceMinPerKm
        )
        String.format(Locale.US, "%.2f", estM / 1000f)
    }

    var durationMinInput by remember(selectedWeek, selectedDay, defaultDurationMin) {
        mutableStateOf(defaultDurationMin.toString())
    }
    var distanceKmInput by remember(selectedWeek, selectedDay, defaultEstimatedKmStr) {
        mutableStateOf(defaultEstimatedKmStr)
    }

    val parsedDurationMin = durationMinInput.toIntOrNull()?.coerceIn(1, 300) ?: defaultDurationMin
    val parsedDistanceKm = distanceKmInput.replace(',', '.').toFloatOrNull()?.coerceIn(0.1f, 100f)
        ?: (defaultEstimatedKmStr.toFloatOrNull() ?: 2.8f)

    @OptIn(ExperimentalLayoutApi::class)
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        ProvideRunFontScale {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(start = 14.dp, end = 14.dp, top = 18.dp, bottom = 48.dp)
                    .testTag("dialog_recover_completed_workout"),
                shape = RoundedCornerShape(22.dp),
                color = Color(0xFF0D0F14),
                border = BorderStroke(1.dp, Color(0xFF262D3D))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                        ) {
                            Text(
                                text = "RECUPERAR / REGISTRAR TREINO",
                                color = Color(0xFF00E5FF),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.8.sp
                            )
                            Text(
                                text = "Registrar treino realizado",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RunFontScaleToggleButton(compact = false)

                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF151922))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Fechar",
                                    tint = Color(0xFFCBD5E1),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    Text(
                        text = "Concluiu um treino da planilha que não apareceu no histórico? Selecione a semana e o dia abaixo para registrá-lo e avançar sua planilha automaticamente.",
                        color = Color(0xFF94A3B8),
                        fontSize = 13.5.sp,
                        lineHeight = 19.sp
                    )

                    // Seletor rápido de Semana (1..10) em FlowRow adaptativo com alvos de toque >= 48.dp e fonte >= 14.sp
                    Text(
                        text = "Semana da Planilha",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        (1..10).forEach { week ->
                            val isSelected = selectedWeek == week
                            Surface(
                                onClick = {
                                    selectedWeek = week
                                    AudioSynth.playTickSound()
                                },
                                color = if (isSelected) Color(0xFF00E5FF).copy(alpha = 0.20f) else Color(0xFF151922),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(
                                    if (isSelected) 1.5.dp else 1.dp,
                                    if (isSelected) Color(0xFF00E5FF) else Color(0xFF262D3D)
                                ),
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .widthIn(min = 56.dp)
                                    .testTag("recover_week_chip_$week")
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                                ) {
                                    Text(
                                        text = "Semana $week",
                                        color = if (isSelected) Color(0xFF00E5FF) else Color(0xFFCBD5E1),
                                        fontSize = 14.sp,
                                        fontWeight = if (isSelected) FontWeight.Black else FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }

                    // Seletor de Dia (Dia 1, Dia 2, Dia 3) em FlowRow com alvos >= 48.dp e fonte >= 14.sp
                    Text(
                        text = "Dia da Semana $selectedWeek",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        maxItemsInEachRow = 3
                    ) {
                        (1..3).forEach { day ->
                            val isSelected = selectedDay == day
                            Surface(
                                onClick = {
                                    selectedDay = day
                                    AudioSynth.playTickSound()
                                },
                                color = if (isSelected) Color(0xFF2ECC71).copy(alpha = 0.20f) else Color(0xFF151922),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(
                                    if (isSelected) 1.5.dp else 1.dp,
                                    if (isSelected) Color(0xFF2ECC71) else Color(0xFF262D3D)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("recover_day_chip_$day")
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 10.dp)
                                ) {
                                    Text(
                                        text = "Dia $day (W${selectedWeek}D$day)",
                                        color = if (isSelected) Color(0xFF2ECC71) else Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = if (isSelected) FontWeight.Black else FontWeight.SemiBold,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }

                    // Resumo do treino selecionado
                    Surface(
                        color = Color(0xFF151922),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFF262D3D)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Semana $selectedWeek · Dia $selectedDay — ${selectedPlan.title}",
                                color = Color(0xFF00E5FF),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = selectedPlan.description,
                                color = Color(0xFF94A3B8),
                                fontSize = 13.sp,
                                lineHeight = 18.sp
                            )
                        }
                    }

                    // Campos de Duração e Distância
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = durationMinInput,
                            onValueChange = { durationMinInput = it.filter { ch -> ch.isDigit() } },
                            label = { Text("Duração (min)", fontSize = 13.sp) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF00E5FF),
                                unfocusedBorderColor = Color(0xFF262D3D),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedLabelColor = Color(0xFF00E5FF),
                                unfocusedLabelColor = Color(0xFF94A3B8)
                            ),
                            modifier = Modifier.weight(1f)
                        )

                        OutlinedTextField(
                            value = distanceKmInput,
                            onValueChange = { distanceKmInput = it },
                            label = { Text("Distância (km)", fontSize = 13.sp) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF00E5FF),
                                unfocusedBorderColor = Color(0xFF262D3D),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedLabelColor = Color(0xFF00E5FF),
                                unfocusedLabelColor = Color(0xFF94A3B8)
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Seletor de Ambiente (Ar Livre / Esteira) em FlowRow com altura >= 48.dp e fonte >= 14.sp
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        maxItemsInEachRow = 2
                    ) {
                        listOf("OUTDOOR" to "Ar Livre", "TREADMILL" to "Esteira").forEach { (envKey, envLabel) ->
                            val isSelected = selectedEnvironment == envKey
                            Surface(
                                onClick = {
                                    selectedEnvironment = envKey
                                    AudioSynth.playTickSound()
                                },
                                color = if (isSelected) Color(0xFF00E5FF).copy(alpha = 0.18f) else Color(0xFF151922),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(
                                    if (isSelected) 1.5.dp else 1.dp,
                                    if (isSelected) Color(0xFF00E5FF) else Color(0xFF262D3D)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(vertical = 12.dp, horizontal = 12.dp)
                                ) {
                                    Text(
                                        text = envLabel,
                                        color = if (isSelected) Color(0xFF00E5FF) else Color(0xFFCBD5E1),
                                        fontSize = 14.sp,
                                        fontWeight = if (isSelected) FontWeight.Black else FontWeight.SemiBold,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color(0xFF334155)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            modifier = Modifier
                                .weight(0.95f)
                                .heightIn(min = 50.dp)
                        ) {
                            Text(
                                text = "Cancelar",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Button(
                            onClick = {
                                val durSec = (parsedDurationMin * 60).coerceAtLeast(60)
                                val distM = (parsedDistanceKm * 1000f).roundToInt().coerceAtLeast(100)
                                onConfirmRegister(
                                    selectedWeek,
                                    selectedDay,
                                    selectedPlan.sessionType,
                                    durSec,
                                    distM,
                                    selectedEnvironment,
                                    defaultShoeName
                                )
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF2ECC71),
                                contentColor = Color(0xFF06130B)
                            ),
                            modifier = Modifier
                                .weight(1.3f)
                                .heightIn(min = 50.dp)
                                .testTag("btn_confirm_recover_workout")
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Salvar no Histórico",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Representa um Pin de Foto Georreferenciada ou posicionada em um marco do percurso
 * para exibição estilo RouteCine / Relive durante o Replay Cine 3D.
 */
data class RunRoutePhotoPin(
    val id: String,
    val uriString: String,
    val progress: Float,
    val lat: Double,
    val lon: Double,
    val distanceKmAtPoint: Float,
    val paceLabelAtPoint: String,
    val timestampMs: Long = 0L
)

/**
 * Matemática de Câmera 3D Flyover:
 * - Interpolação suave de ângulo pelo menor arco (`lerpAngle`) para nunca dar trancos em `-180° / +180°`
 * - Cálculo de azimute suavizado (`computeSmoothedBearingDeg`) olhando ligeiramente à frente na curva
 */
object Cine3DCameraMath {

    fun normalizeAngleDeg(angle: Float): Float {
        var a = angle % 360f
        if (a > 180f) a -= 360f
        if (a < -180f) a += 360f
        return a
    }

    fun lerpAngle(currentAngle: Float, targetAngle: Float, fraction: Float): Float {
        val diff = normalizeAngleDeg(targetAngle - currentAngle)
        return normalizeAngleDeg(currentAngle + diff * fraction.coerceIn(0f, 1f))
    }

    /**
     * Calcula o azimute suavizado da direção do corredor no Canvas (para rotacionar o mapa via `rotationZ`
     * de modo que o sentido de deslocamento aponte suavemente para cima/frente na perspectiva 3D).
     * - Olha para um ponto pelo menos 30 a 40 metros à frente (~35m Look-Ahead, jamais < 12m).
     * - Nos últimos 6% do percurso (`progress > 0.94f`), congela o `targetBearing` no último ângulo estável
     *   eliminando 100% do tremor de chegada.
     */
    fun computeSmoothedBearingDeg(
        screenPoints: List<Offset>,
        progress: Float,
        totalDistanceMeters: Int = 3000
    ): Float {
        if (screenPoints.size < 2) return 0f
        val n = screenPoints.size
        val segLengths = FloatArray(n - 1)
        var totalLen = 0f
        for (i in 0 until n - 1) {
            val dx = screenPoints[i + 1].x - screenPoints[i].x
            val dy = screenPoints[i + 1].y - screenPoints[i].y
            val len = hypot(dx, dy)
            segLengths[i] = len
            totalLen += len
        }
        if (totalLen <= 0.001f) return 0f

        fun pointAtFraction(f: Float): Offset {
            val clamped = f.coerceIn(0f, 1f)
            val target = totalLen * clamped
            var acc = 0f
            for (i in 0 until n - 1) {
                val sl = segLengths[i]
                if (acc + sl >= target && sl > 0.0001f) {
                    val t = ((target - acc) / sl).coerceIn(0f, 1f)
                    return Offset(
                        x = screenPoints[i].x + (screenPoints[i + 1].x - screenPoints[i].x) * t,
                        y = screenPoints[i].y + (screenPoints[i + 1].y - screenPoints[i].y) * t
                    )
                }
                acc += sl
            }
            return screenPoints.last()
        }

        // Congela o cálculo de azimute nos últimos 6% do trajeto (progress > 0.94f) para eliminar 100% da tremedeira final
        val stableProgress = progress.coerceIn(0f, 0.94f)
        val effectiveDistM = totalDistanceMeters.coerceAtLeast(400).toFloat()
        // Fração correspondente a ~35 metros à frente (mínimo 30m..40m, nunca vizinho < 12m)
        val lookAheadFraction = (35f / effectiveDistM).coerceIn(0.035f, 0.12f)
        val lookBehindFraction = (15f / effectiveDistM).coerceIn(0.015f, 0.05f)

        val pBehind = pointAtFraction((stableProgress - lookBehindFraction).coerceAtLeast(0f))
        val pAhead = pointAtFraction((stableProgress + lookAheadFraction).coerceAtMost(0.985f))
        val vx = pAhead.x - pBehind.x
        val vy = pAhead.y - pBehind.y
        if (hypot(vx, vy) < 1.5f) return 0f

        val rawHeadingDeg = Math.toDegrees(atan2(vx.toDouble(), (-vy).toDouble())).toFloat()
        val rawMapRotation = normalizeAngleDeg(-rawHeadingDeg)
        return (rawMapRotation * 0.30f).coerceIn(-34f, 34f)
    }
}

/**
 * Gerenciador de Fotos Georreferenciadas / Marcos Fotográficos do Percurso (`RunRoutePhotoManager`).
 * - Lê EXIF (`TAG_DATETIME_ORIGINAL`, `TAG_DATETIME`, coordenadas GPS) ou `MediaStore.Images.Media.DATE_TAKEN`
 * - Faz cópia otimizada em armazenamento interno do app para nunca perder permissão de leitura da URI
 * - Posiciona automaticamente no ponto exato do trajeto ou distribui em marcos proporcionais (ex: 35%, 70%, 95%)
 * - Persiste em `SharedPreferences` vinculado ao `sessionId`.
 */
object RunRoutePhotoManager {
    private const val PREFS_NAME = "max_run_route_photos_prefs"
    private const val PENDING_CAPTURE_PREFIX = "pending_live_cam_"

    fun savePendingLiveCameraCapturePath(
        context: android.content.Context,
        sessionId: String,
        filePath: String
    ) {
        if (sessionId.isBlank() || filePath.isBlank()) return
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
            .edit()
            .putString("$PENDING_CAPTURE_PREFIX$sessionId", filePath)
            .apply()
    }

    fun loadPendingLiveCameraCapturePath(
        context: android.content.Context,
        sessionId: String
    ): String? {
        if (sessionId.isBlank()) return null
        return context.applicationContext
            .getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
            .getString("$PENDING_CAPTURE_PREFIX$sessionId", null)
            ?.takeIf { it.isNotBlank() }
    }

    fun clearPendingLiveCameraCapturePath(
        context: android.content.Context,
        sessionId: String
    ) {
        if (sessionId.isBlank()) return
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
            .edit()
            .remove("$PENDING_CAPTURE_PREFIX$sessionId")
            .apply()
    }

    fun migratePhotosBetweenSessionsIfNeeded(
        context: android.content.Context,
        fromSessionId: String,
        toSessionId: String
    ) {
        if (fromSessionId.isBlank() || toSessionId.isBlank() || fromSessionId == toSessionId) return
        val sourcePhotos = loadPhotosForSession(context, fromSessionId)
        if (sourcePhotos.isEmpty()) return
        val targetPhotos = loadPhotosForSession(context, toSessionId)
        val merged = (targetPhotos + sourcePhotos)
            .distinctBy { it.id }
            .sortedBy { it.progress }
        savePhotosForSession(context, toSessionId, merged)
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
            .edit()
            .remove("photos_$fromSessionId")
            .remove("$PENDING_CAPTURE_PREFIX$fromSessionId")
            .apply()
    }

    fun loadPhotosForSession(context: android.content.Context, sessionId: String): List<RunRoutePhotoPin> {
        if (sessionId.isBlank()) return emptyList()
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
        val rawJson = prefs.getString("photos_$sessionId", null) ?: return emptyList()
        return runCatching {
            val arr = org.json.JSONArray(rawJson)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    add(
                        RunRoutePhotoPin(
                            id = obj.optString("id", "pin_$i"),
                            uriString = obj.optString("uriString", ""),
                            progress = obj.optDouble("progress", 0.5).toFloat().coerceIn(0.03f, 0.97f),
                            lat = obj.optDouble("lat", 0.0),
                            lon = obj.optDouble("lon", 0.0),
                            distanceKmAtPoint = obj.optDouble("distanceKmAtPoint", 1.0).toFloat(),
                            paceLabelAtPoint = obj.optString("paceLabelAtPoint", "6'30\"/km"),
                            timestampMs = obj.optLong("timestampMs", 0L)
                        )
                    )
                }
            }.filter { it.uriString.isNotBlank() }.sortedBy { it.progress }
        }.getOrDefault(emptyList())
    }

    fun savePhotosForSession(
        context: android.content.Context,
        sessionId: String,
        photos: List<RunRoutePhotoPin>
    ) {
        if (sessionId.isBlank()) return
        val arr = org.json.JSONArray()
        photos.sortedBy { it.progress }.forEach { pin ->
            val obj = org.json.JSONObject().apply {
                put("id", pin.id)
                put("uriString", pin.uriString)
                put("progress", pin.progress.toDouble())
                put("lat", pin.lat)
                put("lon", pin.lon)
                put("distanceKmAtPoint", pin.distanceKmAtPoint.toDouble())
                put("paceLabelAtPoint", pin.paceLabelAtPoint)
                put("timestampMs", pin.timestampMs)
            }
            arr.put(obj)
        }
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
            .edit()
            .putString("photos_$sessionId", arr.toString())
            .apply()
    }

    fun removePhotoFromSession(
        context: android.content.Context,
        sessionId: String,
        photoId: String
    ): List<RunRoutePhotoPin> {
        val current = loadPhotosForSession(context, sessionId)
        val updated = current.filterNot { it.id == photoId }
        savePhotosForSession(context, sessionId, updated)
        return updated
    }

    fun updatePhotoProgressKm(
        context: android.content.Context,
        sessionId: String,
        photoId: String,
        newProgress: Float,
        routeItems: List<com.example.data.model.RoutePointItem>,
        totalDistanceMeters: Int
    ): List<RunRoutePhotoPin> {
        val clamped = newProgress.coerceIn(0.04f, 0.96f)
        val current = loadPhotosForSession(context, sessionId)
        val updated = current.map { pin ->
            if (pin.id == photoId) {
                val (lat, lon, paceLabel) = resolvePointMetadataAtProgress(routeItems, clamped)
                val distKm = ((totalDistanceMeters.coerceAtLeast(100) / 1000f) * clamped).coerceAtLeast(0.05f)
                pin.copy(
                    progress = clamped,
                    lat = lat,
                    lon = lon,
                    distanceKmAtPoint = distKm,
                    paceLabelAtPoint = paceLabel
                )
            } else pin
        }.sortedBy { it.progress }
        savePhotosForSession(context, sessionId, updated)
        return updated
    }

    fun addPhotosToSession(
        context: android.content.Context,
        sessionId: String,
        uris: List<android.net.Uri>,
        routeItems: List<com.example.data.model.RoutePointItem>,
        totalDistanceMeters: Int,
        sessionEndTimestampMs: Long = System.currentTimeMillis(),
        sessionDurationSec: Int = 1800
    ): List<RunRoutePhotoPin> {
        if (sessionId.isBlank() || uris.isEmpty()) return loadPhotosForSession(context, sessionId)

        val existing = loadPhotosForSession(context, sessionId).toMutableList()
        val defaultSlotTargets = listOf(0.35f, 0.70f, 0.92f, 0.20f, 0.52f, 0.82f)
        var fallbackSlotIdx = existing.size

        val sessionStartMs = sessionEndTimestampMs - (sessionDurationSec.coerceAtLeast(60) * 1000L)

        uris.forEachIndexed { idx, uri ->
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }

            // 1. Extrair EXIF (GPS lat/lon ou data/hora original) + MediaStore DATE_TAKEN
            val (exifLatLon, exifTimestampMs) = extractPhotoExifAndTimestamp(context, uri)

            // 2. Copiar versão compacta persistente no diretório interno do app para nunca expirar a URI
            val persistedUriStr = persistPhotoCopyToInternalFiles(context, sessionId, uri) ?: uri.toString()

            // 3. Calcular progresso (0.04f .. 0.96f) ao longo do percurso:
            // Prioridade A: Coordenada GPS EXIF próxima da rota
            var matchedProgress: Float? = null
            if (exifLatLon != null && routeItems.size >= 2) {
                var bestIdx = -1
                var bestDistM = Double.MAX_VALUE
                routeItems.forEachIndexed { ptIdx, item ->
                    val d = haversineM(exifLatLon.first, exifLatLon.second, item.lat, item.lon)
                    if (d < bestDistM) {
                        bestDistM = d
                        bestIdx = ptIdx
                    }
                }
                if (bestIdx >= 0 && bestDistM <= 850.0) {
                    matchedProgress = (bestIdx.toFloat() / (routeItems.size - 1).coerceAtLeast(1))
                        .coerceIn(0.05f, 0.95f)
                }
            }

            // Prioridade B: Timestamp EXIF dentro da janela de tempo da corrida
            if (matchedProgress == null && exifTimestampMs != null && exifTimestampMs > 0L) {
                val toleranceMs = 15 * 60 * 1000L // 15 min de tolerância
                if (exifTimestampMs in (sessionStartMs - toleranceMs)..(sessionEndTimestampMs + toleranceMs)) {
                    val ratio = (exifTimestampMs - sessionStartMs).toFloat() /
                        (sessionDurationSec.coerceAtLeast(60) * 1000f)
                    matchedProgress = ratio.coerceIn(0.06f, 0.95f)
                }
            }

            // Prioridade C: Marcos proporcionais inteligentes (35%, 70%, 92%, etc.) evitando colisão
            val finalProgress = matchedProgress ?: run {
                val baseSlot = defaultSlotTargets[fallbackSlotIdx % defaultSlotTargets.size]
                fallbackSlotIdx++
                var candidate = baseSlot
                while (existing.any { kotlin.math.abs(it.progress - candidate) < 0.06f }) {
                    candidate = ((candidate + 0.14f) % 0.88f).coerceIn(0.12f, 0.94f)
                }
                candidate
            }

            val (lat, lon, paceLabel) = resolvePointMetadataAtProgress(routeItems, finalProgress)
            val distKm = ((totalDistanceMeters.coerceAtLeast(100) / 1000f) * finalProgress).coerceAtLeast(0.05f)

            existing.add(
                RunRoutePhotoPin(
                    id = "photo_${System.currentTimeMillis()}_${idx}_${fallbackSlotIdx}",
                    uriString = persistedUriStr,
                    progress = finalProgress,
                    lat = lat,
                    lon = lon,
                    distanceKmAtPoint = distKm,
                    paceLabelAtPoint = paceLabel,
                    timestampMs = exifTimestampMs ?: System.currentTimeMillis()
                )
            )
        }

        val sorted = existing.sortedBy { it.progress }
        savePhotosForSession(context, sessionId, sorted)
        return sorted
    }

    /**
     * Salva imediatamente uma foto tirada ao vivo com a câmera nativa durante a corrida (estilo Relive),
     * vinculada à coordenada GPS atual `(lat, lon)`, ao `km` exato naquele instante e ao `pace` atual.
     */
    fun addLiveCameraPhotoToSession(
        context: android.content.Context,
        sessionId: String,
        capturedUri: android.net.Uri,
        currentLat: Double,
        currentLon: Double,
        currentDistanceMeters: Int,
        estimatedTotalDistanceMeters: Int,
        currentPaceSecPerKm: Int?
    ): List<RunRoutePhotoPin> {
        if (sessionId.isBlank()) return emptyList()
        val existing = loadPhotosForSession(context, sessionId).toMutableList()
        val persistedUriStr = persistPhotoCopyToInternalFiles(context, sessionId, capturedUri) ?: capturedUri.toString()

        val distKm = (currentDistanceMeters.coerceAtLeast(10) / 1000f)
        val estTotalM = maxOf(estimatedTotalDistanceMeters, currentDistanceMeters + 200, 1000)
        val rawProgress = (currentDistanceMeters.toFloat() / estTotalM.toFloat()).coerceIn(0.05f, 0.94f)

        val paceSec = (currentPaceSecPerKm?.takeIf { it in 150..1200 }) ?: 375
        val paceLabel = String.format(Locale.US, "%d'%02d\"/km", paceSec / 60, paceSec % 60)

        existing.add(
            RunRoutePhotoPin(
                id = "live_photo_${System.currentTimeMillis()}_${existing.size}",
                uriString = persistedUriStr,
                progress = rawProgress,
                lat = currentLat,
                lon = currentLon,
                distanceKmAtPoint = distKm,
                paceLabelAtPoint = paceLabel,
                timestampMs = System.currentTimeMillis()
            )
        )
        val sorted = existing.sortedBy { it.progress }
        savePhotosForSession(context, sessionId, sorted)
        return sorted
    }

    /**
     * Recalibra a posição proporcional (`progress`) das fotos tiradas ao vivo durante a corrida
     * assim que a distância final da sessão é consolidada.
     */
    fun recalibratePhotoProgressForFinalDistance(
        context: android.content.Context,
        sessionId: String,
        finalDistanceMeters: Int,
        routeItems: List<com.example.data.model.RoutePointItem> = emptyList()
    ) {
        if (sessionId.isBlank() || finalDistanceMeters <= 50) return
        val existing = loadPhotosForSession(context, sessionId)
        if (existing.isEmpty()) return
        val totalKm = (finalDistanceMeters / 1000f).coerceAtLeast(0.1f)

        val recalibrated = existing.map { pin ->
            if (pin.id.startsWith("live_photo_")) {
                // Tenta casar pelo ponto GPS real da rota se disponível
                var matchedProg: Float? = null
                if ((pin.lat != 0.0 || pin.lon != 0.0) && routeItems.size >= 2) {
                    var bestIdx = -1
                    var bestDist = Double.MAX_VALUE
                    routeItems.forEachIndexed { idx, item ->
                        val d = haversineM(pin.lat, pin.lon, item.lat, item.lon)
                        if (d < bestDist) {
                            bestDist = d
                            bestIdx = idx
                        }
                    }
                    if (bestIdx >= 0 && bestDist <= 250.0) {
                        matchedProg = (bestIdx.toFloat() / (routeItems.size - 1).coerceAtLeast(1)).coerceIn(0.05f, 0.95f)
                    }
                }
                val finalProg = matchedProg ?: (pin.distanceKmAtPoint / totalKm).coerceIn(0.05f, 0.95f)
                pin.copy(progress = finalProg)
            } else {
                pin
            }
        }.sortedBy { it.progress }

        savePhotosForSession(context, sessionId, recalibrated)
    }

    private fun resolvePointMetadataAtProgress(
        routeItems: List<com.example.data.model.RoutePointItem>,
        progress: Float
    ): Triple<Double, Double, String> {
        if (routeItems.isEmpty()) {
            return Triple(0.0, 0.0, "6'15\"/km")
        }
        val idx = ((routeItems.size - 1) * progress.coerceIn(0f, 1f)).roundToInt()
            .coerceIn(0, routeItems.lastIndex)
        val item = routeItems[idx]
        val spd = item.speed?.takeIf { it > 0.4f } ?: 2.75f
        val paceSec = (1000f / spd).roundToInt().coerceIn(165, 960)
        val pMin = paceSec / 60
        val pSec = paceSec % 60
        val paceStr = String.format(Locale.US, "%d'%02d\"/km", pMin, pSec)
        return Triple(item.lat, item.lon, paceStr)
    }

    private fun extractPhotoExifAndTimestamp(
        context: android.content.Context,
        uri: android.net.Uri
    ): Pair<Pair<Double, Double>?, Long?> {
        var latLon: Pair<Double, Double>? = null
        var timestampMs: Long? = null

        runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val exif = android.media.ExifInterface(stream)
                val latLongArr = FloatArray(2)
                if (exif.getLatLong(latLongArr)) {
                    val lat = latLongArr[0].toDouble()
                    val lon = latLongArr[1].toDouble()
                    if (lat != 0.0 || lon != 0.0) {
                        latLon = Pair(lat, lon)
                    }
                }
                val dtStr = exif.getAttribute(android.media.ExifInterface.TAG_DATETIME_ORIGINAL)
                    ?: exif.getAttribute(android.media.ExifInterface.TAG_DATETIME)
                if (!dtStr.isNullOrBlank()) {
                    val fmt = java.text.SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US)
                    timestampMs = fmt.parse(dtStr)?.time
                }
            }
        }

        if (timestampMs == null) {
            runCatching {
                val proj = arrayOf(android.provider.MediaStore.Images.Media.DATE_TAKEN)
                context.contentResolver.query(uri, proj, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val col = cursor.getColumnIndex(android.provider.MediaStore.Images.Media.DATE_TAKEN)
                        if (col >= 0) {
                            val taken = cursor.getLong(col)
                            if (taken > 0L) timestampMs = taken
                        }
                    }
                }
            }
        }
        return Pair(latLon, timestampMs)
    }

    private fun readExifOrientation(
        context: android.content.Context,
        uri: android.net.Uri
    ): Int {
        return runCatching {
            if (uri.scheme == "file" && !uri.path.isNullOrBlank()) {
                android.media.ExifInterface(uri.path!!).getAttributeInt(
                    android.media.ExifInterface.TAG_ORIENTATION,
                    android.media.ExifInterface.ORIENTATION_NORMAL
                )
            } else {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    android.media.ExifInterface(stream).getAttributeInt(
                        android.media.ExifInterface.TAG_ORIENTATION,
                        android.media.ExifInterface.ORIENTATION_NORMAL
                    )
                } ?: android.media.ExifInterface.ORIENTATION_NORMAL
            }
        }.getOrDefault(android.media.ExifInterface.ORIENTATION_NORMAL)
    }

    fun applyExifOrientationToBitmap(
        source: android.graphics.Bitmap,
        orientation: Int
    ): android.graphics.Bitmap {
        val matrix = android.graphics.Matrix()
        when (orientation) {
            android.media.ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            android.media.ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            android.media.ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            android.media.ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
            android.media.ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
            android.media.ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.preScale(-1f, 1f)
            }
            android.media.ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.preScale(-1f, 1f)
            }
            else -> return source
        }
        return runCatching {
            val rotated = android.graphics.Bitmap.createBitmap(
                source,
                0,
                0,
                source.width,
                source.height,
                matrix,
                true
            )
            if (rotated !== source) {
                runCatching { source.recycle() }
            }
            rotated
        }.getOrDefault(source)
    }

    /**
     * Decodifica um Bitmap de uma URI (file:// ou content://) aplicando automaticamente a correção
     * de orientação EXIF (`ExifInterface.TAG_ORIENTATION`) via `Matrix().postRotate(degrees)`.
     */
    fun decodeUprightBitmap(
        context: android.content.Context,
        uriString: String,
        maxDimension: Int = 1080
    ): android.graphics.Bitmap? {
        if (uriString.isBlank()) return null
        return runCatching {
            val uri = android.net.Uri.parse(uriString)
            val orientation = readExifOrientation(context, uri)

            val rawBoundsOpts = android.graphics.BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            if (uri.scheme == "file" && !uri.path.isNullOrBlank()) {
                android.graphics.BitmapFactory.decodeFile(uri.path!!, rawBoundsOpts)
            } else {
                context.contentResolver.openInputStream(uri)?.use { s ->
                    android.graphics.BitmapFactory.decodeStream(s, null, rawBoundsOpts)
                }
            }
            val maxDim = max(rawBoundsOpts.outWidth, rawBoundsOpts.outHeight)
            var sampleSize = 1
            while (maxDim / sampleSize > maxDimension) {
                sampleSize *= 2
            }
            val decodeOpts = android.graphics.BitmapFactory.Options().apply {
                inSampleSize = sampleSize
            }
            val rawBmp = if (uri.scheme == "file" && !uri.path.isNullOrBlank()) {
                android.graphics.BitmapFactory.decodeFile(uri.path!!, decodeOpts)
            } else {
                context.contentResolver.openInputStream(uri)?.use { s ->
                    android.graphics.BitmapFactory.decodeStream(s, null, decodeOpts)
                }
            } ?: return null

            applyExifOrientationToBitmap(rawBmp, orientation)
        }.getOrNull()
    }

    /**
     * Rotaciona manualmente em +90° uma foto já salva no percurso (caso tenha sido gravada sem EXIF em versão anterior)
     * e atualiza a URI da foto na sessão.
     */
    fun rotateSavedPhotoBy90Degrees(
        context: android.content.Context,
        sessionId: String,
        photoId: String
    ): List<RunRoutePhotoPin> {
        val current = loadPhotosForSession(context, sessionId)
        val target = current.find { it.id == photoId } ?: return current
        val bmp = decodeUprightBitmap(context, target.uriString, 1080) ?: return current
        var rotated: android.graphics.Bitmap? = null
        try {
            val matrix = android.graphics.Matrix().apply { postRotate(90f) }
            rotated = runCatching {
                android.graphics.Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
            }.getOrNull() ?: return current

            val dir = java.io.File(context.filesDir, "run_route_photos")
            if (!dir.exists()) dir.mkdirs()
            val outFile = java.io.File(dir, "route_${sessionId}_${System.currentTimeMillis()}_rot.jpg")
            runCatching {
                java.io.FileOutputStream(outFile).use { fos ->
                    rotated.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, fos)
                }
            }
            val newUriStr = android.net.Uri.fromFile(outFile).toString()
            val updated = current.map { pin ->
                if (pin.id == photoId) pin.copy(uriString = newUriStr) else pin
            }
            savePhotosForSession(context, sessionId, updated)
            return updated
        } finally {
            runCatching {
                if (rotated != null && rotated !== bmp && !rotated.isRecycled) {
                    rotated.recycle()
                }
            }
            runCatching {
                if (!bmp.isRecycled) {
                    bmp.recycle()
                }
            }
        }
    }

    private fun persistPhotoCopyToInternalFiles(
        context: android.content.Context,
        sessionId: String,
        sourceUri: android.net.Uri
    ): String? {
        return runCatching {
            val dir = java.io.File(context.filesDir, "run_route_photos")
            if (!dir.exists()) dir.mkdirs()

            val orientation = readExifOrientation(context, sourceUri)

            val rawBoundsOpts = android.graphics.BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            if (sourceUri.scheme == "file" && !sourceUri.path.isNullOrBlank()) {
                android.graphics.BitmapFactory.decodeFile(sourceUri.path!!, rawBoundsOpts)
            } else {
                context.contentResolver.openInputStream(sourceUri)?.use { s ->
                    android.graphics.BitmapFactory.decodeStream(s, null, rawBoundsOpts)
                }
            }
            val maxDim = max(rawBoundsOpts.outWidth, rawBoundsOpts.outHeight)
            var sampleSize = 1
            while (maxDim / sampleSize > 1080) {
                sampleSize *= 2
            }
            val decodeOpts = android.graphics.BitmapFactory.Options().apply {
                inSampleSize = sampleSize
            }
            val rawBmp = if (sourceUri.scheme == "file" && !sourceUri.path.isNullOrBlank()) {
                android.graphics.BitmapFactory.decodeFile(sourceUri.path!!, decodeOpts)
            } else {
                context.contentResolver.openInputStream(sourceUri)?.use { s ->
                    android.graphics.BitmapFactory.decodeStream(s, null, decodeOpts)
                }
            } ?: return null

            var uprightBmp: android.graphics.Bitmap? = null
            try {
                uprightBmp = applyExifOrientationToBitmap(rawBmp, orientation)

                val outFile = java.io.File(dir, "route_${sessionId}_${System.currentTimeMillis()}.jpg")
                java.io.FileOutputStream(outFile).use { fos ->
                    uprightBmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, fos)
                }
                runCatching {
                    val outExif = android.media.ExifInterface(outFile.absolutePath)
                    outExif.setAttribute(
                        android.media.ExifInterface.TAG_ORIENTATION,
                        android.media.ExifInterface.ORIENTATION_NORMAL.toString()
                    )
                    outExif.saveAttributes()
                }
                android.net.Uri.fromFile(outFile).toString()
            } finally {
                runCatching {
                    if (uprightBmp != null && uprightBmp !== rawBmp && !uprightBmp.isRecycled) {
                        uprightBmp.recycle()
                    }
                }
                runCatching {
                    if (!rawBmp.isRecycled) {
                        rawBmp.recycle()
                    }
                }
            }
        }.getOrNull()
    }

    private fun haversineM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2.0) * sin(dLat / 2.0) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2.0) * sin(dLon / 2.0)
        val c = 2.0 * atan2(sqrt(a), sqrt((1.0 - a).coerceAtLeast(0.0)))
        return r * c
    }
}

