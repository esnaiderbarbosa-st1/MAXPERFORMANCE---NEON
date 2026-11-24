package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.model.GpsTrackPoint
import com.example.data.model.RoutePointItem
import com.example.utils.AudioSynth
import com.example.utils.TileCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.*

enum class MapTileStyle(val displayName: String, val urlTemplate: String) {
    DARK("Satélite", "https://mt1.google.com/vt/lyrs=s&x={x}&y={y}&z={z}"),
    SATELLITE("Satélite", "https://mt1.google.com/vt/lyrs=s&x={x}&y={y}&z={z}"),
    LIGHT("Satélite", "https://mt1.google.com/vt/lyrs=s&x={x}&y={y}&z={z}"),
    VOYAGER("Satélite", "https://mt1.google.com/vt/lyrs=s&x={x}&y={y}&z={z}"),
    OSM("Satélite", "https://mt1.google.com/vt/lyrs=s&x={x}&y={y}&z={z}")
}

private data class FinishConfettiSeed(
    val startXFraction: Float,
    val startYFraction: Float,
    val vx: Float,
    val vy: Float,
    val sizePx: Float,
    val color: Color,
    val spinSpeed: Float,
    val phaseOffset: Float
)

/**
 * Legenda horizontal limpa logo abaixo do mapa:
 * 🔵 Caminhada  🟢 Corrida  🟡 Melhor Sprint (PR)
 */
@Composable
fun RouteSprintLegendBar(
    modifier: Modifier = Modifier,
    showWarmupLegend: Boolean = true
) {
    Surface(
        color = Color(0xFF151922),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showWarmupLegend) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF94A3B8))
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Aquec.",
                        color = Color(0xFFCBD5E1),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(RunRouteLaneOffsetEngine.WalkColor)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Caminhada",
                    color = RunRouteLaneOffsetEngine.WalkColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(RunRouteLaneOffsetEngine.SprintColor)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Corrida",
                    color = RunRouteLaneOffsetEngine.SprintColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(RunRouteLaneOffsetEngine.BestSprintColor)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Melhor Sprint",
                    color = RunRouteLaneOffsetEngine.BestSprintColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

/**
 * Componente de Mapa Real com Rota Estática 100% visível no carregamento e Animação sob demanda (Modo "Cine 3D Flyover"),
 * Fotos Georreferenciadas nos Pontos do Percurso com Pausa de 1,6s + Card Pop-up Animado,
 * Placar Superior Dinâmico ("Live Flyover HUD"), Bandeiras de Largada / Km / Chegada e Confetes na Chegada.
 */
@Composable
fun AnimatedRouteMapView(
    routePoints: List<Pair<Double, Double>>,
    modifier: Modifier = Modifier,
    routePointItems: List<RoutePointItem> = emptyList(),
    allowSimulationFallback: Boolean = false,
    isInteractiveReplay: Boolean = true,
    initialStyle: MapTileStyle = MapTileStyle.SATELLITE,
    externalStyle: MapTileStyle? = null,
    onStyleChange: ((MapTileStyle) -> Unit)? = null,
    distanceMeters: Int = 3000,
    enableGestures: Boolean = true,
    externalColorByPace: Boolean? = null,
    externalReplayTrigger: Int = 0,
    externalResetTrigger: Int = 0,
    showFullscreenIcon: Boolean = false,
    onFullscreenClick: (() -> Unit)? = null,
    sessionId: String = "",
    sessionTitle: String = "Corrida Livre",
    sessionEndTimestampMs: Long = System.currentTimeMillis(),
    sessionDurationSec: Int = 1800,
    avgPaceSecPerKm: Int = 390,
    avgHrBpm: Int = 0,
    hrTimelineList: List<Int> = emptyList(),
    externalCine3dEnabled: Boolean? = null,
    onCine3dChange: ((Boolean) -> Unit)? = null,
    externalPhotoReloadTrigger: Int = 0,
    onReplayLiveMetricsChange: ((isReplaying: Boolean, liveDistKmStr: String, livePaceStr: String, liveBpmStr: String) -> Unit)? = null
) {
    val context = LocalContext.current
    val ptBr = remember { Locale("pt", "BR") }
    remember(context) {
        TileCache.init(context)
        true
    }
    val currentStyle = MapTileStyle.SATELLITE
    val providerKey = if (currentStyle == MapTileStyle.SATELLITE) "satellite" else currentStyle.name.lowercase(Locale.US)

    var localReplayTrigger by remember { mutableIntStateOf(0) }
    val replayTrigger = localReplayTrigger + externalReplayTrigger
    var localColorByPace by remember { mutableStateOf(true) }
    val colorByPace = externalColorByPace ?: localColorByPace

    // Modo "🎥 Cine 3D" ativado por padrão ao clicar em Replay
    var localCine3dEnabled by remember { mutableStateOf(true) }
    val isCine3dEnabled = externalCine3dEnabled ?: localCine3dEnabled
    val setCine3dEnabled: (Boolean) -> Unit = { enabled ->
        localCine3dEnabled = enabled
        onCine3dChange?.invoke(enabled)
    }

    // Sanitização retroativa de saltos de largada a frio (Cold-Start GPS / Cell-Tower Jump)
    val sanitizedInputItems = remember(routePointItems) {
        com.example.data.model.sanitizeColdStartOutliers(routePointItems)
    }
    val sanitizedInputPairs = remember(routePoints) {
        com.example.data.model.sanitizeColdStartCoordinatePairs(routePoints)
    }

    val effectiveItems = remember(sanitizedInputItems, sanitizedInputPairs, distanceMeters, allowSimulationFallback) {
        when {
            sanitizedInputItems.size >= 2 -> {
                val hasPrAlready = sanitizedInputItems.any { it.isPrSegment || it.phase.equals("PR", ignoreCase = true) }
                if (hasPrAlready) {
                    sanitizedInputItems
                } else {
                    val n = sanitizedInputItems.size
                    val speeds = FloatArray(n) { idx ->
                        val pt = sanitizedInputItems[idx]
                        pt.speed?.takeIf { it > 0.3f } ?: 2.9f
                    }
                    val medianSpd = speeds.sorted()[n / 2].coerceAtLeast(1.8f)
                    val windowLen = (n / 5).coerceIn(2, 25)
                    var bestStart = (n / 2).coerceAtMost((n - windowLen).coerceAtLeast(0))
                    var bestAvgSpd = -1f
                    for (s in 0..(n - windowLen).coerceAtLeast(0)) {
                        var sum = 0f
                        for (k in s until (s + windowLen).coerceAtMost(n)) sum += speeds[k]
                        val avg = sum / windowLen
                        if (avg > bestAvgSpd) {
                            bestAvgSpd = avg
                            bestStart = s
                        }
                    }
                    val bestEnd = (bestStart + windowLen - 1).coerceAtMost(n - 1)
                    sanitizedInputItems.mapIndexed { idx, pt ->
                        val isPr = idx in bestStart..bestEnd
                        val phase = when {
                            isPr -> "PR"
                            pt.phase.uppercase(Locale.US) in listOf("WALK", "WARMUP", "COOL_DOWN") -> pt.phase.uppercase(Locale.US)
                            speeds[idx] >= 1.85f && speeds[idx] >= medianSpd * 0.78f -> "RUN"
                            else -> "WALK"
                        }
                        pt.copy(phase = phase, isPrSegment = isPr)
                    }
                }
            }
            sanitizedInputPairs.size >= 2 -> sanitizedInputPairs.mapIndexed { idx, (lat, lon) ->
                val ratio = idx.toDouble() / (sanitizedInputPairs.size - 1).coerceAtLeast(1)
                val isPr = ratio in 0.52..0.68
                val phase = when {
                    isPr -> "PR"
                    ratio < 0.10 -> "WARMUP"
                    ratio > 0.92 -> "COOL_DOWN"
                    (idx / 4) % 2 == 0 -> "RUN"
                    else -> "WALK"
                }
                RoutePointItem(
                    lat = lat,
                    lon = lon,
                    timestamp = idx * 1000L,
                    speed = if (phase == "RUN" || phase == "PR") 3.1f else 1.5f,
                    phase = phase,
                    blockIndex = idx / 4,
                    isPrSegment = isPr
                )
            }
            allowSimulationFallback -> {
                val simPts = com.example.utils.MapTileHelper.getAvenueRoutePoints(distanceMeters)
                simPts.mapIndexed { idx, (lat, lon) ->
                    val ratio = idx.toDouble() / (simPts.size - 1).coerceAtLeast(1)
                    val isPr = ratio in 0.50..0.64
                    val phase = when {
                        isPr -> "PR"
                        ratio < 0.12 -> "WARMUP"
                        ratio > 0.88 -> "COOL_DOWN"
                        (idx / 4) % 2 == 0 -> "RUN"
                        else -> "WALK"
                    }
                    RoutePointItem(
                        lat = lat,
                        lon = lon,
                        timestamp = idx * 1000L,
                        speed = if (phase == "RUN" || phase == "PR") 3.2f else 1.5f,
                        phase = phase,
                        blockIndex = idx / 4,
                        isPrSegment = isPr
                    )
                }
            }
            else -> emptyList()
        }
    }

    val effectivePoints = remember(effectiveItems) {
        effectiveItems.map { Pair(it.lat, it.lon) }
    }

    val routeCacheKey = remember(effectivePoints, distanceMeters) {
        effectivePoints.hashCode() * 31 + distanceMeters
    }

    val resolvedSessionId = remember(sessionId, routeCacheKey) {
        sessionId.ifBlank { "session_$routeCacheKey" }
    }

    // Fotos Georreferenciadas / Marcos no Percurso
    var localPhotoReloadTick by remember { mutableIntStateOf(0) }
    val routePhotos = remember(resolvedSessionId, localPhotoReloadTick, externalPhotoReloadTrigger) {
        RunRoutePhotoManager.loadPhotosForSession(context, resolvedSessionId)
    }
    var activePopupPhoto by remember(routeCacheKey) { mutableStateOf<RunRoutePhotoPin?>(null) }
    var isManualPhotoPreview by remember(routeCacheKey) { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { pickedUris ->
        if (pickedUris.isNotEmpty()) {
            val updated = RunRoutePhotoManager.addPhotosToSession(
                context = context,
                sessionId = resolvedSessionId,
                uris = pickedUris,
                routeItems = effectiveItems,
                totalDistanceMeters = distanceMeters,
                sessionEndTimestampMs = sessionEndTimestampMs,
                sessionDurationSec = sessionDurationSec
            )
            localPhotoReloadTick++
            activePopupPhoto = updated.lastOrNull()
            isManualPhotoPreview = true
            Toast.makeText(
                context,
                "${pickedUris.size} foto(s) adicionada(s) ao percurso! Toque em Replay para o voo Cine 3D.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // Estado inicial 100% visível (estático) ao abrir a tela; animação ocorre sob demanda
    val animationProgress = remember(routeCacheKey) {
        Animatable(1f)
    }
    var isAnimating by remember(routeCacheKey) { mutableStateOf(false) }
    var hasAnimatedOnce by remember(routeCacheKey) { mutableStateOf(false) }
    var lastHandledReplayTrigger by remember(routeCacheKey) { mutableIntStateOf(replayTrigger) }

    // Estado de Explosão de Confetes na Chegada (>= 98%)
    var showFinishConfetti by remember(routeCacheKey) { mutableStateOf(false) }
    val confettiAnimProgress = remember(routeCacheKey) { Animatable(0f) }

    var userScale by remember { mutableFloatStateOf(1f) }
    var userOffsetX by remember { mutableFloatStateOf(0f) }
    var userOffsetY by remember { mutableFloatStateOf(0f) }

    // Pausa o Replay suavemente por 1,6s em cada Pin de Foto quando |replayProgress - photoPoint.progress| < 0.025f
    LaunchedEffect(replayTrigger) {
        if (replayTrigger > lastHandledReplayTrigger && effectivePoints.size >= 2) {
            lastHandledReplayTrigger = replayTrigger
            isAnimating = true
            isManualPhotoPreview = false
            activePopupPhoto = null
            showFinishConfetti = false
            if (externalCine3dEnabled == null) {
                localCine3dEnabled = true
            }
            userScale = 1f
            userOffsetX = 0f
            userOffsetY = 0f
            try {
                animationProgress.snapTo(0f)
                val sortedPins = RunRoutePhotoManager.loadPhotosForSession(context, resolvedSessionId)
                    .sortedBy { it.progress }
                val totalDurationMs = 15000

                if (sortedPins.isEmpty()) {
                    animationProgress.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(
                            durationMillis = totalDurationMs,
                            easing = CubicBezierEasing(0.22f, 0.08f, 0.18f, 1.0f)
                        )
                    )
                } else {
                    var currentPos = 0f
                    for (pin in sortedPins) {
                        val targetStop = pin.progress.coerceIn(0.04f, 0.96f)
                        if (targetStop > currentPos + 0.01f) {
                            val segDur = ((targetStop - currentPos) * totalDurationMs).roundToInt().coerceAtLeast(450)
                            animationProgress.animateTo(
                                targetValue = targetStop,
                                animationSpec = tween(durationMillis = segDur, easing = LinearEasing)
                            )
                            currentPos = targetStop
                        }
                        // Pausa suavemente o avanço do replay por 1,6 segundos exibindo o Card Pop-up RouteCine
                        activePopupPhoto = pin
                        AudioSynth.playTickSound()
                        delay(1600L)
                        activePopupPhoto = null
                        delay(160L)
                    }
                    if (currentPos < 1f) {
                        val remDur = ((1f - currentPos) * totalDurationMs).roundToInt().coerceAtLeast(600)
                        animationProgress.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(durationMillis = remDur, easing = LinearEasing)
                        )
                    }
                }
            } finally {
                activePopupPhoto = null
                animationProgress.snapTo(1f)
                hasAnimatedOnce = true
                isAnimating = false
            }
        }
    }

    // Dispara Explosão de Confetes ao cruzar 98% do percurso durante o Replay
    val currentProgressVal = animationProgress.value.coerceIn(0f, 1f)
    LaunchedEffect(isAnimating, currentProgressVal >= 0.98f) {
        if (isAnimating && currentProgressVal >= 0.98f && !showFinishConfetti) {
            showFinishConfetti = true
            confettiAnimProgress.snapTo(0f)
            confettiAnimProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 2000, easing = LinearEasing)
            )
            showFinishConfetti = false
        }
    }

    LaunchedEffect(externalResetTrigger) {
        if (externalResetTrigger > 0) {
            userScale = 1f
            userOffsetX = 0f
            userOffsetY = 0f
            activePopupPhoto = null
        }
    }

    // Animação contínua do Halo Pulsante do Cursor Vivo
    val infiniteCursorTransition = rememberInfiniteTransition(label = "cine_cursor_halo")
    val cursorPulseScale by infiniteCursorTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.38f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 780, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cursor_pulse_scale"
    )
    val cursorPulseAlpha by infiniteCursorTransition.animateFloat(
        initialValue = 0.48f,
        targetValue = 0.16f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 780, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cursor_pulse_alpha"
    )

    // Transição suave de entrada/saída da perspectiva 3D Flyover (48° de inclinação e zoom 1.38x)
    // Congela/retorna ao enquadramento cheio nos últimos 6% (> 0.94f) para eliminar qualquer tremor no final
    val is3dActiveNow = isCine3dEnabled && isAnimating && currentProgressVal < 0.94f
    val cine3dFactor by animateFloatAsState(
        targetValue = if (is3dActiveNow) 1f else 0f,
        animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
        label = "cine_3d_factor"
    )

    val loadedTiles = remember { mutableStateMapOf<String, ImageBitmap>() }

    val boxModifier = if (onFullscreenClick != null && !enableGestures) {
        modifier.clickable {
            AudioSynth.playTickSound()
            onFullscreenClick()
        }
    } else {
        modifier
    }

    Box(
        modifier = boxModifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF0D0F14))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(20.dp))
    ) {
        if (effectivePoints.size < 2) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Navigation,
                        contentDescription = null,
                        tint = Color(0xFF64748B),
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        text = "Sem pontos de rota GPS gravados nesta sessão",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Corridas ao ar livre com sinal de satélite ativo exibem o traçado real por Sprints aqui.",
                        color = Color(0xFF64748B),
                        fontSize = 10.sp
                    )
                }
            }
        } else {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val densityObj = LocalDensity.current
                val widthPx = with(densityObj) { maxWidth.toPx() }
                val heightPx = with(densityObj) { maxHeight.toPx() }

                val mapProjection = remember(effectivePoints, widthPx, heightPx) {
                    calculateMapProjection(effectivePoints, widthPx, heightPx)
                }

                // Pré-calcula os pontos projetados e deslocados em faixas para alimentar o cálculo de azimute 3D e os pins
                val precomputedScreenPoints = remember(effectivePoints, mapProjection, densityObj.density) {
                    val bounds = mapProjection.bounds
                    val zoom = mapProjection.zoom
                    val rawPts = effectivePoints.map { (lat, lon) ->
                        val worldX = lonToWorldX(lon, zoom)
                        val worldY = latToWorldY(lat, zoom)
                        Offset(x = (worldX - bounds.minWorldX).toFloat(), y = (worldY - bounds.minWorldY).toFloat())
                    }
                    val laneScale = (1f / mapProjection.fitScale.coerceAtLeast(1f)).coerceIn(0.45f, 1f)
                    RunRouteLaneOffsetEngine.applyDirectionalLaneOffset(
                        rawPoints = rawPts,
                        gpsCoords = effectivePoints,
                        offsetPx = 3.5f * densityObj.density * laneScale
                    )
                }

                // Interpolação contínua de azimute (smoothedBearing) com look-ahead de 35m e trava nos últimos 6% (> 0.94f)
                var smoothedBearing by remember(routeCacheKey) { mutableFloatStateOf(0f) }
                val frozenOrLiveProgress = currentProgressVal.coerceAtMost(0.94f)
                val targetBearing = remember(precomputedScreenPoints, frozenOrLiveProgress, isCine3dEnabled, isAnimating, distanceMeters) {
                    if (isCine3dEnabled && isAnimating) {
                        Cine3DCameraMath.computeSmoothedBearingDeg(
                            screenPoints = precomputedScreenPoints,
                            progress = frozenOrLiveProgress,
                            totalDistanceMeters = distanceMeters
                        )
                    } else 0f
                }
                LaunchedEffect(targetBearing, is3dActiveNow, currentProgressVal > 0.94f) {
                    smoothedBearing = when {
                        isCine3dEnabled && isAnimating && currentProgressVal > 0.94f -> smoothedBearing
                        is3dActiveNow -> Cine3DCameraMath.lerpAngle(smoothedBearing, targetBearing, 0.18f)
                        else -> Cine3DCameraMath.lerpAngle(smoothedBearing, 0f, 0.25f)
                    }
                }

                // Pré-carrega sincronamente da LruCache em RAM para evitar qualquer piscada no frame 0
                remember(mapProjection, providerKey) {
                    for ((tileX, tileY, zoom) in mapProjection.visibleTiles) {
                        val tileKey = TileCache.buildKey(providerKey, zoom, tileX, tileY)
                        if (!loadedTiles.containsKey(tileKey)) {
                            TileCache.getMemoryImageBitmap(tileKey)?.let { memImg ->
                                loadedTiles[tileKey] = memImg
                            }
                        }
                    }
                    true
                }

                LaunchedEffect(mapProjection, currentStyle) {
                    val missingTiles = mapProjection.visibleTiles.filter { (tileX, tileY, zoom) ->
                        val tileKey = TileCache.buildKey(providerKey, zoom, tileX, tileY)
                        val memHit = TileCache.getMemoryImageBitmap(tileKey)
                        if (memHit != null) {
                            if (!loadedTiles.containsKey(tileKey)) {
                                loadedTiles[tileKey] = memHit
                            }
                            false
                        } else {
                            !loadedTiles.containsKey(tileKey)
                        }
                    }

                    if (missingTiles.isNotEmpty()) {
                        withContext(Dispatchers.IO) {
                            missingTiles.map { (tileX, tileY, zoom) ->
                                async {
                                    val tileKey = TileCache.buildKey(providerKey, zoom, tileX, tileY)
                                    val bmp = TileCache.getOrLoadTileBitmap(
                                        context = context,
                                        provider = providerKey,
                                        zoom = zoom,
                                        tileX = tileX,
                                        tileY = tileY
                                    ) {
                                        com.example.utils.MapTileHelper.fetchTileNetworkOnly(
                                            urlTemplate = currentStyle.urlTemplate,
                                            zoom = zoom,
                                            tileX = tileX,
                                            tileY = tileY,
                                            isSatellite = currentStyle == MapTileStyle.SATELLITE
                                        )
                                    }
                                    if (bmp != null && !bmp.isRecycled) {
                                        val imgBmp = TileCache.getMemoryImageBitmap(tileKey)
                                            ?: runCatching { bmp.asImageBitmap() }.getOrNull()
                                        if (imgBmp != null) {
                                            withContext(Dispatchers.Main) {
                                                loadedTiles[tileKey] = imgBmp
                                            }
                                        }
                                    }
                                }
                            }.awaitAll()
                        }
                    }
                }

                // Container do mapa de satélite com inclinação de perspectiva 3D ("Cine 3D Flyover"):
                // rotationX = 48f, rotationZ = smoothedBearing, scaleX = 1.38f, scaleY = 1.38f, cameraDistance = 16f * density
                val baseCanvasModifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        if (cine3dFactor > 0.001f) {
                            rotationX = 48f * cine3dFactor
                            rotationZ = smoothedBearing * cine3dFactor
                            scaleX = 1f + (0.38f * cine3dFactor)
                            scaleY = 1f + (0.38f * cine3dFactor)
                            cameraDistance = 16f * density
                        }
                    }

                val canvasModifier = if (enableGestures) {
                    baseCanvasModifier.pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            userScale = (userScale * zoom).coerceIn(0.85f, 6.5f)
                            if (abs(userScale - 1f) < 0.03f && zoom < 1f) {
                                userOffsetX = 0f
                                userOffsetY = 0f
                            } else {
                                userOffsetX += pan.x
                                userOffsetY += pan.y
                            }
                        }
                    }
                } else {
                    baseCanvasModifier
                }

                Canvas(modifier = canvasModifier) {
                    val bounds = mapProjection.bounds
                    val baseScale = (mapProjection.fitScale * userScale).coerceIn(0.8f, 12f)
                    val canvasCenter = Offset(size.width / 2f, size.height / 2f)
                    val screenPoints = precomputedScreenPoints

                    val progress = currentProgressVal
                    val (_, currentHeadPoint) = getSubPathAtProgress(screenPoints, progress)

                    // Câmera Dinâmica Estilo Relive / RouteCine:
                    // Durante o replay (0.02f..0.92f) aplica zoom dinâmico (1.55f) acompanhando suavemente a bolinha
                    // e nos últimos 8% (0.92f..1.0f) abre o zoom suavemente de volta para o enquadramento total sem tremer.
                    val isManualPanOrZoom = abs(userScale - 1f) > 0.02f || abs(userOffsetX) > 2f || abs(userOffsetY) > 2f
                    val followZoomPeak = 1.55f
                    val cameraBoost: Float
                    val focusPoint: Offset

                    if (!isManualPanOrZoom && currentHeadPoint != null && isAnimating && progress < 0.999f) {
                        when {
                            progress <= 0.05f -> {
                                val rampIn = (progress / 0.05f).coerceIn(0f, 1f)
                                val smoothIn = rampIn * rampIn * (3f - 2f * rampIn)
                                cameraBoost = 1.25f + (followZoomPeak - 1.25f) * smoothIn
                                focusPoint = currentHeadPoint
                            }
                            progress <= 0.92f -> {
                                cameraBoost = followZoomPeak
                                focusPoint = currentHeadPoint
                            }
                            else -> {
                                val t = ((progress - 0.92f) / 0.08f).coerceIn(0f, 1f)
                                val smoothOut = t * t * (3f - 2f * t)
                                cameraBoost = followZoomPeak + (1.0f - followZoomPeak) * smoothOut
                                focusPoint = Offset(
                                    x = currentHeadPoint.x + (canvasCenter.x - currentHeadPoint.x) * smoothOut,
                                    y = currentHeadPoint.y + (canvasCenter.y - currentHeadPoint.y) * smoothOut
                                )
                            }
                        }
                    } else {
                        cameraBoost = 1.0f
                        focusPoint = canvasCenter
                    }

                    val effectiveScale = (baseScale * cameraBoost).coerceIn(0.8f, 14f)
                    val camTranslateX = -(focusPoint.x - canvasCenter.x) * effectiveScale
                    val camTranslateY = -(focusPoint.y - canvasCenter.y) * effectiveScale

                    withTransform({
                        translate(userOffsetX + camTranslateX, userOffsetY + camTranslateY)
                        scale(effectiveScale, effectiveScale, pivot = canvasCenter)
                    }) {
                        var tilesDrawn = 0
                        for ((tileX, tileY, tileZoom) in mapProjection.visibleTiles) {
                            val tileKey = TileCache.buildKey(providerKey, tileZoom, tileX, tileY)
                            val imgBmp = loadedTiles[tileKey] ?: TileCache.getMemoryImageBitmap(tileKey)
                            if (imgBmp != null) {
                                val tileOriginX = (tileX * 256.0 - bounds.minWorldX).toFloat()
                                val tileOriginY = (tileY * 256.0 - bounds.minWorldY).toFloat()
                                drawImage(
                                    image = imgBmp,
                                    dstOffset = IntOffset(tileOriginX.roundToInt(), tileOriginY.roundToInt()),
                                    dstSize = IntSize(256, 256)
                                )
                                tilesDrawn++
                            }
                        }

                        // Película de contraste sutil para destacar o traçado neon sobre Satélite
                        if (tilesDrawn > 0) {
                            val overlayColor = when (currentStyle) {
                                MapTileStyle.SATELLITE -> Color(0x2E04070D)
                                MapTileStyle.DARK -> Color(0x40070B14)
                                else -> Color.Transparent
                            }
                            if (overlayColor != Color.Transparent) {
                                drawRect(
                                    color = overlayColor,
                                    topLeft = Offset(-size.width, -size.height),
                                    size = Size(size.width * 3f, size.height * 3f)
                                )
                            }
                        }

                        // Fallback visual elegante da grade escura quando offline sem cache
                        if (tilesDrawn == 0) {
                            drawRect(
                                color = Color(0xFF0D0F14),
                                topLeft = Offset(-size.width, -size.height),
                                size = Size(size.width * 3f, size.height * 3f)
                            )
                            val gridStep = 40f
                            var x = -size.width
                            while (x < size.width * 2f) {
                                drawLine(color = Color(0xFF151922), start = Offset(x, -size.height), end = Offset(x, size.height * 2f), strokeWidth = 1.2f)
                                x += gridStep
                            }
                            var y = -size.height
                            while (y < size.height * 2f) {
                                drawLine(color = Color(0xFF151922), start = Offset(-size.width, y), end = Offset(size.width * 2f, y), strokeWidth = 1.2f)
                                y += gridStep
                            }
                        }

                        if (screenPoints.size >= 2) {
                            val strokeScale = (1f / (mapProjection.fitScale * cameraBoost.pow(0.45f)).coerceAtLeast(1f)).coerceIn(0.42f, 1f)

                            val segLengths = FloatArray(screenPoints.size - 1)
                            var totalRouteLen = 0f
                            for (i in 0 until screenPoints.size - 1) {
                                val dx = screenPoints[i + 1].x - screenPoints[i].x
                                val dy = screenPoints[i + 1].y - screenPoints[i].y
                                val len = sqrt(dx * dx + dy * dy)
                                segLengths[i] = len
                                totalRouteLen += len
                            }

                            val targetDrawLen = if (progress >= 0.999f) totalRouteLen else totalRouteLen * progress
                            var accumulatedLen = 0f
                            var activeHeadColor = RunRouteLaneOffsetEngine.SprintColor
                            var liveCursorPoint: Offset? = currentHeadPoint

                            data class ProgressiveColoredSegment(
                                val p1: Offset,
                                val p2: Offset,
                                val semanticColor: Color,
                                val glowColor: Color,
                                val mainStrokeWidth: Float,
                                val isPr: Boolean,
                                val freshTrailGlow: Float
                            )

                            val visibleSegments = ArrayList<ProgressiveColoredSegment>(screenPoints.size)

                            if (totalRouteLen > 0.001f && progress > 0f) {
                                for (i in 0 until screenPoints.size - 1) {
                                    if (progress < 0.999f && accumulatedLen >= targetDrawLen) break

                                    val segLen = segLengths[i]
                                    if (segLen <= 0.0005f) {
                                        continue
                                    }

                                    val segStartLen = accumulatedLen
                                    val remainLen = (targetDrawLen - segStartLen).coerceAtLeast(0f)
                                    val segFraction = if (progress >= 0.999f) {
                                        1f
                                    } else {
                                        (remainLen / segLen).coerceIn(0f, 1f)
                                    }
                                    accumulatedLen += segLen

                                    if (segFraction <= 0f) break

                                    val p1 = screenPoints[i]
                                    val fullP2 = screenPoints[i + 1]
                                    val p2 = if (segFraction < 1f) {
                                        Offset(
                                            x = p1.x + (fullP2.x - p1.x) * segFraction,
                                            y = p1.y + (fullP2.y - p1.y) * segFraction
                                        )
                                    } else {
                                        fullP2
                                    }
                                    liveCursorPoint = p2

                                    val item = effectiveItems.getOrNull(i + 1) ?: effectiveItems.getOrNull(i)
                                    val phase = item?.phase?.uppercase(Locale.US) ?: "RUN"
                                    val isPr = item?.isPrSegment == true || phase == "PR"

                                    val semanticColor: Color
                                    val glowColor: Color
                                    val baseWidth: Float

                                    if (colorByPace) {
                                        when {
                                            isPr -> {
                                                semanticColor = RunRouteLaneOffsetEngine.BestSprintColor
                                                glowColor = Color(0xFFFFCA28)
                                                baseWidth = 7.6f
                                            }
                                            phase == "WARMUP" || phase == "COOL_DOWN" -> {
                                                semanticColor = RunRouteLaneOffsetEngine.WarmupColor
                                                glowColor = RunRouteLaneOffsetEngine.WarmupColor
                                                baseWidth = 6.0f
                                            }
                                            phase == "WALK" -> {
                                                semanticColor = RunRouteLaneOffsetEngine.WalkColor
                                                glowColor = Color(0xFF00B0FF)
                                                baseWidth = 6.2f
                                            }
                                            else -> {
                                                semanticColor = RunRouteLaneOffsetEngine.SprintColor
                                                glowColor = RunRouteLaneOffsetEngine.SprintColor
                                                baseWidth = 6.8f
                                            }
                                        }
                                    } else {
                                        semanticColor = Color(0xFF00E5FF)
                                        glowColor = Color(0xFF00B0FF)
                                        baseWidth = 6.8f
                                    }

                                    activeHeadColor = semanticColor

                                    val distBehindHead = (targetDrawLen - (segStartLen + segLen * segFraction)).coerceAtLeast(0f)
                                    val tailWindow = (totalRouteLen * 0.09f).coerceAtLeast(1f)
                                    val freshTrailGlow = if (progress < 0.995f && distBehindHead < tailWindow) {
                                        (1f - (distBehindHead / tailWindow)).coerceIn(0f, 1f)
                                    } else 0f

                                    visibleSegments.add(
                                        ProgressiveColoredSegment(
                                            p1 = p1,
                                            p2 = p2,
                                            semanticColor = semanticColor,
                                            glowColor = glowColor,
                                            mainStrokeWidth = baseWidth,
                                            isPr = isPr,
                                            freshTrailGlow = freshTrailGlow
                                        )
                                    )

                                    if (segFraction < 1f) break
                                }
                            }

                            if (visibleSegments.isNotEmpty()) {
                                val borderExtraPx = 2.5.dp.toPx() * strokeScale

                                data class ContinuousPhaseSubPath(
                                    val path: Path,
                                    val semanticColor: Color,
                                    val glowColor: Color,
                                    val mainStrokeWidth: Float,
                                    val isPr: Boolean
                                )

                                val groupedSubPaths = ArrayList<ContinuousPhaseSubPath>()
                                var idx = 0
                                while (idx < visibleSegments.size) {
                                    val firstSeg = visibleSegments[idx]
                                    val subPath = Path().apply {
                                        moveTo(firstSeg.p1.x, firstSeg.p1.y)
                                        lineTo(firstSeg.p2.x, firstSeg.p2.y)
                                    }
                                    var j = idx + 1
                                    while (j < visibleSegments.size &&
                                        visibleSegments[j].semanticColor == firstSeg.semanticColor &&
                                        visibleSegments[j].isPr == firstSeg.isPr
                                    ) {
                                        subPath.lineTo(visibleSegments[j].p2.x, visibleSegments[j].p2.y)
                                        j++
                                    }
                                    groupedSubPaths.add(
                                        ContinuousPhaseSubPath(
                                            path = subPath,
                                            semanticColor = firstSeg.semanticColor,
                                            glowColor = firstSeg.glowColor,
                                            mainStrokeWidth = firstSeg.mainStrokeWidth,
                                            isPr = firstSeg.isPr
                                        )
                                    )
                                    idx = j
                                }

                                for (group in groupedSubPaths) {
                                    if (group.isPr) {
                                        drawPath(
                                            path = group.path,
                                            color = group.glowColor.copy(alpha = 0.28f),
                                            style = Stroke(
                                                width = 13.5f * strokeScale,
                                                cap = StrokeCap.Round,
                                                join = StrokeJoin.Round
                                            )
                                        )
                                    }
                                }
                                if (progress < 0.995f) {
                                    for (seg in visibleSegments) {
                                        if (seg.freshTrailGlow > 0.04f) {
                                            drawLine(
                                                color = seg.glowColor.copy(alpha = 0.28f * seg.freshTrailGlow),
                                                start = seg.p1,
                                                end = seg.p2,
                                                strokeWidth = 12.5f * strokeScale,
                                                cap = StrokeCap.Round
                                            )
                                        }
                                    }
                                }

                                // 1ª PASSADA (Fundo / Contorno Escuro Contínuo):
                                val fullBorderPath = Path().apply {
                                    moveTo(visibleSegments.first().p1.x, visibleSegments.first().p1.y)
                                    for (seg in visibleSegments) {
                                        lineTo(seg.p2.x, seg.p2.y)
                                    }
                                }
                                drawPath(
                                    path = fullBorderPath,
                                    color = RunRouteLaneOffsetEngine.ContrastBorderColor,
                                    style = Stroke(
                                        width = (6.8f * strokeScale) + borderExtraPx,
                                        cap = StrokeCap.Round,
                                        join = StrokeJoin.Round
                                    )
                                )
                                for (group in groupedSubPaths) {
                                    if (group.isPr) {
                                        drawPath(
                                            path = group.path,
                                            color = RunRouteLaneOffsetEngine.ContrastBorderColor,
                                            style = Stroke(
                                                width = (group.mainStrokeWidth * strokeScale) + borderExtraPx,
                                                cap = StrokeCap.Round,
                                                join = StrokeJoin.Round
                                            )
                                        )
                                    }
                                }

                                // 2ª PASSADA (Cores Limpas e Contínuas por Cima):
                                for (group in groupedSubPaths) {
                                    drawPath(
                                        path = group.path,
                                        color = group.semanticColor,
                                        style = Stroke(
                                            width = group.mainStrokeWidth * strokeScale,
                                            cap = StrokeCap.Round,
                                            join = StrokeJoin.Round
                                        )
                                    )
                                }
                            }

                            val markerScale = strokeScale.coerceIn(0.52f, 1f)
                            val startPt = screenPoints.first()
                            drawCircle(color = Color.White, radius = 8.5f * markerScale, center = startPt)
                            drawCircle(color = Color(0xFF00E676), radius = 6f * markerScale, center = startPt)

                            // Bandeiras / Etiquetas flutuantes no mapa para Largada (0.0 km), cada Km 1, Km 2... e Chegada
                            val totalKmFloat = (distanceMeters.coerceAtLeast(100) / 1000f)
                            val fullKmsCount = floor(totalKmFloat).toInt().coerceIn(0, 42)
                            val nativeCanvas = drawContext.canvas.nativeCanvas
                            val pillPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
                            val textPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                                typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                                textAlign = android.graphics.Paint.Align.CENTER
                            }

                            fun drawFloatingRouteFlag(
                                anchorPt: Offset,
                                label: String,
                                bgHex: Int,
                                borderHex: Int,
                                textHex: Int,
                                offsetYFactor: Float = -20f
                            ) {
                                val fontPx = 18f * markerScale
                                textPaint.textSize = fontPx
                                val textW = textPaint.measureText(label)
                                val padH = 10f * markerScale
                                val boxW = textW + padH * 2f
                                val boxH = 26f * markerScale
                                val cx = anchorPt.x
                                val cy = anchorPt.y + offsetYFactor * markerScale
                                val rect = android.graphics.RectF(cx - boxW / 2f, cy - boxH / 2f, cx + boxW / 2f, cy + boxH / 2f)

                                pillPaint.style = android.graphics.Paint.Style.FILL
                                pillPaint.color = bgHex
                                nativeCanvas.drawRoundRect(rect, 8f * markerScale, 8f * markerScale, pillPaint)

                                pillPaint.style = android.graphics.Paint.Style.STROKE
                                pillPaint.strokeWidth = 1.8f * markerScale
                                pillPaint.color = borderHex
                                nativeCanvas.drawRoundRect(rect, 8f * markerScale, 8f * markerScale, pillPaint)

                                textPaint.color = textHex
                                nativeCanvas.drawText(label, cx, cy + (fontPx * 0.34f), textPaint)
                            }

                            // Etiqueta de Largada (0.0 km)
                            drawFloatingRouteFlag(
                                anchorPt = startPt,
                                label = "Largada (0.0 km)",
                                bgHex = 0xE6062E18.toInt(),
                                borderHex = 0xFF00E676.toInt(),
                                textHex = 0xFFFFFFFF.toInt(),
                                offsetYFactor = -22f
                            )

                            // Marcos de cada Km 1, Km 2...
                            for (km in 1..fullKmsCount) {
                                val kmProgress = (km.toFloat() / totalKmFloat).coerceIn(0.05f, 0.94f)
                                if (progress >= kmProgress) {
                                    val (_, kmPt) = getSubPathAtProgress(screenPoints, kmProgress)
                                    if (kmPt != null) {
                                        drawCircle(color = Color.White, radius = 6.2f * markerScale, center = kmPt)
                                        drawCircle(color = Color(0xFF00E5FF), radius = 4.2f * markerScale, center = kmPt)
                                        drawFloatingRouteFlag(
                                            anchorPt = kmPt,
                                            label = "Km $km",
                                            bgHex = 0xE60F172A.toInt(),
                                            borderHex = 0xFF00E5FF.toInt(),
                                            textHex = 0xFF00E5FF.toInt(),
                                            offsetYFactor = -18f
                                        )
                                    }
                                }
                            }

                            // Pins de Fotos Georreferenciadas no percurso
                            for (photoPin in routePhotos) {
                                val (_, pinPt) = getSubPathAtProgress(screenPoints, photoPin.progress)
                                if (pinPt != null) {
                                    val isCurrentPopup = activePopupPhoto?.id == photoPin.id
                                    val pinRadius = (if (isCurrentPopup) 14f else 10.5f) * markerScale
                                    drawCircle(
                                        color = Color(0xFFF59E0B).copy(alpha = if (isCurrentPopup) 0.55f else 0.30f),
                                        radius = pinRadius * 1.7f,
                                        center = pinPt
                                    )
                                    drawCircle(
                                        color = Color.White,
                                        radius = pinRadius,
                                        center = pinPt
                                    )
                                    drawCircle(
                                        color = Color(0xFFF59E0B),
                                        radius = pinRadius * 0.76f,
                                        center = pinPt
                                    )
                                    // Desenha lente minimalista de câmera dentro do pin âmbar
                                    drawCircle(
                                        color = Color.White,
                                        radius = pinRadius * 0.34f,
                                        center = pinPt,
                                        style = Stroke(width = (1.6f * markerScale).coerceAtLeast(1.1f))
                                    )
                                    val kmPinText = String.format(ptBr, "Foto Km %.1f", photoPin.distanceKmAtPoint)
                                    drawFloatingRouteFlag(
                                        anchorPt = pinPt,
                                        label = kmPinText,
                                        bgHex = 0xEE1E1608.toInt(),
                                        borderHex = 0xFFF59E0B.toInt(),
                                        textHex = 0xFFFDE68A.toInt(),
                                        offsetYFactor = 22f
                                    )
                                }
                            }

                            // Marcador e bandeira de Chegada quando o traçado completa o percurso (>= 98%)
                            if (progress >= 0.98f) {
                                val endPt = screenPoints.last()
                                drawCircle(color = Color(0xFFEF4444).copy(alpha = 0.32f), radius = 14f * markerScale, center = endPt)
                                drawCircle(color = Color.White, radius = 8.5f * markerScale, center = endPt)
                                drawCircle(color = Color(0xFFEF4444), radius = 6f * markerScale, center = endPt)
                                drawFloatingRouteFlag(
                                    anchorPt = endPt,
                                    label = "Chegada",
                                    bgHex = 0xE63B0A0A.toInt(),
                                    borderHex = 0xFFEF4444.toInt(),
                                    textHex = 0xFFFFFFFF.toInt(),
                                    offsetYFactor = 22f
                                )
                            }

                            // Cursor luminoso animado com halo pulsante na ponta ativa do traçado
                            val headPt = liveCursorPoint ?: currentHeadPoint
                            if (headPt != null && progress < 0.995f) {
                                drawCircle(
                                    color = activeHeadColor.copy(alpha = cursorPulseAlpha),
                                    radius = 28f * markerScale * cursorPulseScale,
                                    center = headPt
                                )
                                drawCircle(
                                    color = activeHeadColor.copy(alpha = 0.62f),
                                    radius = 15.5f * markerScale,
                                    center = headPt
                                )
                                drawCircle(
                                    color = Color.White,
                                    radius = 8.5f * markerScale,
                                    center = headPt
                                )
                                drawCircle(
                                    color = activeHeadColor,
                                    radius = 5.2f * markerScale,
                                    center = headPt
                                )
                            }
                        }
                    }
                }

                // Cálculo ao vivo de Distância (0,00 km -> distância final), Pace e BPM sincronizados com replayProgress
                val liveKm = ((distanceMeters.coerceAtLeast(100) / 1000f) * currentProgressVal).coerceAtLeast(0f)
                val liveIdx = ((effectiveItems.size - 1) * currentProgressVal).roundToInt()
                    .coerceIn(0, (effectiveItems.size - 1).coerceAtLeast(0))
                val currentItem = effectiveItems.getOrNull(liveIdx)
                val liveSpeed = currentItem?.speed?.takeIf { it > 0.45f }
                val livePaceSec = if (liveSpeed != null) {
                    (1000f / liveSpeed).roundToInt().coerceIn(160, 960)
                } else {
                    avgPaceSecPerKm.takeIf { it > 0 } ?: 390
                }
                val pMin = livePaceSec / 60
                val pSec = livePaceSec % 60
                val livePaceStr = String.format(Locale.US, "%d'%02d\"/km", pMin, pSec)

                val liveBpm = when {
                    hrTimelineList.isNotEmpty() -> {
                        val hIdx = ((hrTimelineList.size - 1) * currentProgressVal).roundToInt()
                            .coerceIn(0, hrTimelineList.lastIndex)
                        hrTimelineList[hIdx]
                    }
                    avgHrBpm > 0 -> {
                        val phaseBoost = if (currentItem?.isPrSegment == true) 11 else if (currentItem?.phase == "WALK") -9 else 3
                        (avgHrBpm + phaseBoost).coerceIn(95, 205)
                    }
                    else -> {
                        if (currentItem?.isPrSegment == true) 168 else if (currentItem?.phase == "WALK") 136 else 156
                    }
                }

                LaunchedEffect(isAnimating, currentProgressVal, liveKm, livePaceStr, liveBpm) {
                    val formattedLiveDist = String.format(ptBr, "%.2f km", liveKm)
                    val formattedLivePace = String.format(Locale.US, "%d'%02d\" /km", pMin, pSec)
                    val formattedLiveBpm = "$liveBpm bpm"
                    onReplayLiveMetricsChange?.invoke(isAnimating, formattedLiveDist, formattedLivePace, formattedLiveBpm)
                }

                // 3. PLACAR SUPERIOR DINÂMICO ("LIVE FLYOVER HUD" #CC0D1117) DURANTE O REPLAY
                AnimatedVisibility(
                    visible = isAnimating,
                    enter = fadeIn() + scaleIn(initialScale = 0.92f),
                    exit = fadeOut(),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = if (!isInteractiveReplay) 64.dp else 10.dp, start = 12.dp, end = 12.dp)
                ) {
                    Surface(
                        color = Color(0xCC0D1117),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.45f)),
                        shadowElevation = 8.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = sessionTitle,
                                    color = Color(0xFF94A3B8),
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (isCine3dEnabled) {
                                    Text(
                                        text = "• 🎥 CINE 3D",
                                        color = Color(0xFF00E5FF),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                            }
                            Text(
                                text = String.format(ptBr, "%.2f km • %s • %d bpm", liveKm, livePaceStr, liveBpm),
                                color = Color.White,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Black,
                                fontStyle = FontStyle.Italic,
                                maxLines = 1
                            )
                        }
                    }
                }

                // 2. CARD POP-UP ELEGANTE ESTILO ROUTECINE QUANDO O REPLAY PASSA POR UM PIN DE FOTO (OU TOQUE MANUAL)
                AnimatedVisibility(
                    visible = activePopupPhoto != null,
                    enter = scaleIn(initialScale = 0.76f, animationSpec = tween(260, easing = FastOutSlowInEasing)) + fadeIn(tween(220)),
                    exit = scaleOut(targetScale = 0.84f, animationSpec = tween(200)) + fadeOut(tween(180)),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(12.dp)
                ) {
                    activePopupPhoto?.let { photoPin ->
                        Surface(
                            color = Color(0xF20D1117),
                            shape = RoundedCornerShape(20.dp),
                            border = BorderStroke(1.8.dp, Color(0xFF00E5FF)),
                            shadowElevation = 14.dp
                        ) {
                            Column(
                                modifier = Modifier
                                    .widthIn(max = 220.dp)
                                    .padding(10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = String.format(
                                            ptBr,
                                            "📍 Km %.1f • %s",
                                            photoPin.distanceKmAtPoint,
                                            photoPin.paceLabelAtPoint
                                        ),
                                        color = Color.White,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Black,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (isManualPhotoPreview) {
                                        IconButton(
                                            onClick = {
                                                activePopupPhoto = null
                                                isManualPhotoPreview = false
                                            },
                                            modifier = Modifier.size(22.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Fechar foto",
                                                tint = Color(0xFFCBD5E1),
                                                modifier = Modifier.size(15.dp)
                                            )
                                        }
                                    }
                                }

                                val uprightPhotoBitmap = remember(photoPin.uriString) {
                                    RunRoutePhotoManager.decodeUprightBitmap(context, photoPin.uriString, 720)
                                }
                                if (uprightPhotoBitmap != null) {
                                    androidx.compose.foundation.Image(
                                        bitmap = uprightPhotoBitmap.asImageBitmap(),
                                        contentDescription = "Foto do percurso no Km ${photoPin.distanceKmAtPoint}",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(if (enableGestures) 168.dp else 124.dp)
                                            .clip(RoundedCornerShape(16.dp))
                                            .border(1.5.dp, Color(0xFFF59E0B), RoundedCornerShape(16.dp))
                                    )
                                } else {
                                    AsyncImage(
                                        model = photoPin.uriString,
                                        contentDescription = "Foto do percurso no Km ${photoPin.distanceKmAtPoint}",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(if (enableGestures) 168.dp else 124.dp)
                                            .clip(RoundedCornerShape(16.dp))
                                            .border(1.5.dp, Color(0xFFF59E0B), RoundedCornerShape(16.dp))
                                    )
                                }

                                if (isManualPhotoPreview) {
                                    val totalKm = (distanceMeters.coerceAtLeast(200) / 1000f)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        TextButton(
                                            onClick = {
                                                val nextProg = ((photoPin.progress + 0.18f) % 0.90f).coerceIn(0.10f, 0.94f)
                                                val updated = RunRoutePhotoManager.updatePhotoProgressKm(
                                                    context = context,
                                                    sessionId = resolvedSessionId,
                                                    photoId = photoPin.id,
                                                    newProgress = nextProg,
                                                    routeItems = effectiveItems,
                                                    totalDistanceMeters = distanceMeters
                                                )
                                                localPhotoReloadTick++
                                                activePopupPhoto = updated.find { it.id == photoPin.id }
                                            },
                                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                            modifier = Modifier.height(28.dp)
                                        ) {
                                            Text(
                                                text = String.format(ptBr, "Km (%.1f/%.1f)", photoPin.distanceKmAtPoint, totalKm),
                                                color = Color(0xFF00E5FF),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        TextButton(
                                            onClick = {
                                                AudioSynth.playTickSound()
                                                val updated = RunRoutePhotoManager.rotateSavedPhotoBy90Degrees(
                                                    context = context,
                                                    sessionId = resolvedSessionId,
                                                    photoId = photoPin.id
                                                )
                                                localPhotoReloadTick++
                                                activePopupPhoto = updated.find { it.id == photoPin.id }
                                            },
                                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                            modifier = Modifier.height(28.dp)
                                        ) {
                                            Text(
                                                text = "↻ Girar",
                                                color = Color(0xFFFBBF24),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                RunRoutePhotoManager.removePhotoFromSession(
                                                    context = context,
                                                    sessionId = resolvedSessionId,
                                                    photoId = photoPin.id
                                                )
                                                localPhotoReloadTick++
                                                activePopupPhoto = null
                                                isManualPhotoPreview = false
                                            },
                                            modifier = Modifier.size(26.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Remover foto",
                                                tint = Color(0xFFEF4444),
                                                modifier = Modifier.size(15.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 3.2 EXPLOSÃO DE CONFETES ESTRITAMENTE DENTRO DO BOX DO MAPA (nunca sobre os botões inferiores)
                if (showFinishConfetti) {
                    val confettiSeeds = remember {
                        val palette = listOf(
                            Color(0xFF00E676),
                            Color(0xFF00E5FF),
                            Color(0xFFFFD600),
                            Color(0xFFFF4081),
                            Color(0xFFA855F7),
                            Color(0xFFFF6D00)
                        )
                        val rnd = java.util.Random(75L)
                        List(58) { idx ->
                            FinishConfettiSeed(
                                startXFraction = 0.08f + rnd.nextFloat() * 0.84f,
                                startYFraction = -0.05f - (rnd.nextFloat() * 0.25f),
                                vx = (rnd.nextFloat() - 0.5f) * 220f,
                                vy = 190f + rnd.nextFloat() * 240f,
                                sizePx = 10f + rnd.nextFloat() * 11f,
                                color = palette[idx % palette.size],
                                spinSpeed = (rnd.nextFloat() - 0.5f) * 720f,
                                phaseOffset = rnd.nextFloat() * 6.28f
                            )
                        }
                    }
                    val t = confettiAnimProgress.value
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = if (!isInteractiveReplay) 210.dp else 44.dp)
                            .clip(RoundedCornerShape(18.dp))
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val alphaFade = (1f - ((t - 0.72f) / 0.28f).coerceIn(0f, 1f)).coerceIn(0f, 1f)
                            for (seed in confettiSeeds) {
                                val startX = size.width * seed.startXFraction
                                val startY = size.height * seed.startYFraction
                                val sway = sin((t * 9f) + seed.phaseOffset) * 24f
                                val px = startX + seed.vx * t + sway
                                val py = startY + (seed.vy * t) + (0.5f * 320f * t * t)
                                if (py in -20f..size.height) {
                                    withTransform({
                                        rotate(degrees = seed.spinSpeed * t, pivot = Offset(px, py))
                                    }) {
                                        drawRect(
                                            color = seed.color.copy(alpha = alphaFade),
                                            topLeft = Offset(px - seed.sizePx / 2f, py - seed.sizePx * 0.32f),
                                            size = Size(seed.sizePx, seed.sizePx * 0.64f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Controles no Canto Inferior Esquerdo: [ 🎥 Cine 3D ] e [ 📷 + Fotos ]
                if (isInteractiveReplay) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Toggle Rápido: "🎥 Cine 3D"
                        Surface(
                            onClick = {
                                AudioSynth.playTickSound()
                                setCine3dEnabled(!isCine3dEnabled)
                            },
                            color = if (isCine3dEnabled) Color(0xD9082F49) else Color(0xD90F172A),
                            shape = RoundedCornerShape(50),
                            border = BorderStroke(
                                1.dp,
                                if (isCine3dEnabled) Color(0xFF00E5FF) else Color(0xFF475569)
                            ),
                            modifier = Modifier.testTag("btn_toggle_cine_3d")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "🎥 Cine 3D",
                                    color = if (isCine3dEnabled) Color(0xFF00E5FF) else Color(0xFFCBD5E1),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }

                        // Botão: Ícone Vetorial de Câmera + Fotos
                        Surface(
                            onClick = {
                                AudioSynth.playTickSound()
                                photoPickerLauncher.launch("image/*")
                            },
                            color = if (routePhotos.isNotEmpty()) Color(0xD9271A07) else Color(0xD90F172A),
                            shape = RoundedCornerShape(50),
                            border = BorderStroke(
                                1.dp,
                                if (routePhotos.isNotEmpty()) Color(0xFFF59E0B) else Color(0xFF38BDF8).copy(alpha = 0.55f)
                            ),
                            modifier = Modifier.testTag("btn_add_route_photos")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                VectorCameraIcon(
                                    modifier = Modifier.size(15.dp),
                                    tint = if (routePhotos.isNotEmpty()) Color(0xFFFBBF24) else Color.White
                                )
                                Text(
                                    text = if (routePhotos.isEmpty()) "+ Fotos" else "Fotos (${routePhotos.size})",
                                    color = if (routePhotos.isNotEmpty()) Color(0xFFFBBF24) else Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }

                // Controles em overlay no canto inferior direito do mapa: [ ▶ Animar / ↻ Replay ] e [ Tela Cheia ]
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isInteractiveReplay && !isAnimating) {
                        Surface(
                            onClick = {
                                localCine3dEnabled = true
                                localReplayTrigger++
                                AudioSynth.playTickSound()
                            },
                            color = Color(0xD90F172A),
                            shape = RoundedCornerShape(50),
                            border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.65f)),
                            shadowElevation = 6.dp,
                            modifier = Modifier.testTag("btn_replay_route_map")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = if (hasAnimatedOnce) Icons.Rounded.Replay else Icons.Rounded.PlayArrow,
                                    contentDescription = if (hasAnimatedOnce) "Replay Trajeto" else "Animar Trajeto",
                                    tint = Color(0xFF00E5FF),
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = if (hasAnimatedOnce) "Replay" else "Animar",
                                    color = Color.White,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    if (showFullscreenIcon || onFullscreenClick != null) {
                        Surface(
                            onClick = {
                                AudioSynth.playTickSound()
                                onFullscreenClick?.invoke()
                            },
                            color = Color(0xDD00E5FF),
                            shape = CircleShape,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Icon(
                                    imageVector = Icons.Default.Fullscreen,
                                    contentDescription = "Expandir Mapa em Tela Cheia",
                                    tint = Color.Black,
                                    modifier = Modifier.size(20.dp)
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
 * Modal em Tela Quase Cheia (Full Screen Inspector) permitindo zoom de pinça, arraste livre,
 * Modo "🎥 Cine 3D", adição de Fotos no Percurso ("📷 + Fotos"), Placar Dinâmico e Confetes na Chegada.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullscreenMapDialog(
    routePoints: List<Pair<Double, Double>>,
    distKmStr: String,
    paceStr: String,
    bpmStr: String,
    routePointItems: List<RoutePointItem> = emptyList(),
    allowSimulationFallback: Boolean = false,
    initialStyle: MapTileStyle = MapTileStyle.DARK,
    externalStyle: MapTileStyle? = null,
    onStyleChange: ((MapTileStyle) -> Unit)? = null,
    distanceMeters: Int = 3000,
    sessionId: String = "",
    sessionTitle: String = "Corrida Livre",
    sessionEndTimestampMs: Long = System.currentTimeMillis(),
    sessionDurationSec: Int = 1800,
    avgPaceSecPerKm: Int = 390,
    avgHrBpm: Int = 0,
    hrTimelineList: List<Int> = emptyList(),
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false,
            decorFitsSystemWindows = true
        )
    ) {
        var colorByPace by remember { mutableStateOf(true) }
        var isCine3dEnabled by remember { mutableStateOf(true) }
        var replayTrigger by remember { mutableIntStateOf(0) }
        var resetTrigger by remember { mutableIntStateOf(0) }
        var photoReloadTrigger by remember { mutableIntStateOf(0) }
        var internalDialogStyle by remember(initialStyle, externalStyle) {
            mutableStateOf(externalStyle ?: initialStyle)
        }
        val activeStyle = externalStyle ?: internalDialogStyle
        val setDialogStyle: (MapTileStyle) -> Unit = { style ->
            internalDialogStyle = style
            onStyleChange?.invoke(style)
        }

        val resolvedSessionId = remember(sessionId, routePoints, distanceMeters) {
            sessionId.ifBlank { "session_${routePoints.hashCode() * 31 + distanceMeters}" }
        }
        val dialogRoutePhotos = remember(resolvedSessionId, photoReloadTrigger) {
            RunRoutePhotoManager.loadPhotosForSession(context, resolvedSessionId)
        }

        // Resolução automática de Cidade - UF via Geocoder no primeiro ponto GPS (com fallback imediato "Rio Branco - AC")
        val firstGpsPoint = remember(routePoints, routePointItems) {
            routePoints.firstOrNull()
                ?: routePointItems.firstOrNull()?.let { Pair(it.lat, it.lon) }
        }
        var cityStateLabel by remember { mutableStateOf("Rio Branco - AC") }
        LaunchedEffect(firstGpsPoint) {
            if (firstGpsPoint != null && (firstGpsPoint.first != 0.0 || firstGpsPoint.second != 0.0)) {
                withContext(Dispatchers.IO) {
                    runCatching {
                        val geocoder = android.location.Geocoder(context, Locale("pt", "BR"))
                        @Suppress("DEPRECATION")
                        val addresses = geocoder.getFromLocation(firstGpsPoint.first, firstGpsPoint.second, 1)
                        val addr = addresses?.firstOrNull()
                        if (addr != null) {
                            val city = addr.subAdminArea?.takeIf { it.isNotBlank() }
                                ?: addr.locality?.takeIf { it.isNotBlank() }
                            val rawAdmin = addr.adminArea?.trim().orEmpty()
                            val uf = when {
                                rawAdmin.length == 2 -> rawAdmin.uppercase(Locale.US)
                                rawAdmin.equals("Acre", ignoreCase = true) -> "AC"
                                rawAdmin.equals("São Paulo", ignoreCase = true) -> "SP"
                                rawAdmin.equals("Rio de Janeiro", ignoreCase = true) -> "RJ"
                                rawAdmin.equals("Minas Gerais", ignoreCase = true) -> "MG"
                                rawAdmin.equals("Paraná", ignoreCase = true) -> "PR"
                                rawAdmin.equals("Santa Catarina", ignoreCase = true) -> "SC"
                                rawAdmin.equals("Rio Grande do Sul", ignoreCase = true) -> "RS"
                                rawAdmin.equals("Distrito Federal", ignoreCase = true) -> "DF"
                                rawAdmin.equals("Goiás", ignoreCase = true) -> "GO"
                                rawAdmin.equals("Bahia", ignoreCase = true) -> "BA"
                                rawAdmin.equals("Amazonas", ignoreCase = true) -> "AM"
                                rawAdmin.equals("Rondônia", ignoreCase = true) -> "RO"
                                rawAdmin.isNotBlank() -> rawAdmin.take(2).uppercase(Locale.US)
                                else -> "AC"
                            }
                            if (!city.isNullOrBlank()) {
                                cityStateLabel = "$city - $uf"
                            }
                        }
                    }
                }
            }
        }

        // Data e Horário limpos do treino para o cabeçalho social
        val formattedDateTimeSubtitle = remember(sessionEndTimestampMs) {
            val ptBr = Locale("pt", "BR")
            val fmt = java.text.SimpleDateFormat("EEEE, dd MMM yyyy • HH:mm", ptBr)
            fmt.format(java.util.Date(sessionEndTimestampMs)).replaceFirstChar { it.uppercase(ptBr) }
        }

        // Estado das métricas ao vivo durante o Replay (Distância subindo de 0,00 km até o final + Pace + BPM)
        var isReplayRunningInDialog by remember { mutableStateOf(false) }
        var liveDialogDistStr by remember(distKmStr) { mutableStateOf(distKmStr) }
        var liveDialogPaceStr by remember(paceStr) { mutableStateOf(paceStr) }
        var liveDialogBpmStr by remember(bpmStr) { mutableStateOf(bpmStr) }

        val dialogPhotoPicker = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetMultipleContents()
        ) { uris ->
            if (uris.isNotEmpty()) {
                RunRoutePhotoManager.addPhotosToSession(
                    context = context,
                    sessionId = resolvedSessionId,
                    uris = uris,
                    routeItems = routePointItems,
                    totalDistanceMeters = distanceMeters,
                    sessionEndTimestampMs = sessionEndTimestampMs,
                    sessionDurationSec = sessionDurationSec
                )
                photoReloadTrigger++
                Toast.makeText(
                    context,
                    "${uris.size} foto(s) vinculada(s) ao trajeto! Toque em Replay para o voo Cine 3D.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.78f))
                .systemBarsPadding()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(),
                shape = RoundedCornerShape(24.dp),
                color = Color(0xFF0D0F14),
                border = BorderStroke(1.5.dp, Color(0xFF00E5FF).copy(alpha = 0.35f))
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // Mapa em Tela Quase Cheia com gestos de Pinch (zoom de pinça) e Pan livres + Modo Cine 3D Flyover
                    AnimatedRouteMapView(
                        routePoints = routePoints,
                        routePointItems = routePointItems,
                        allowSimulationFallback = allowSimulationFallback,
                        modifier = Modifier.fillMaxSize(),
                        isInteractiveReplay = false,
                        initialStyle = initialStyle,
                        externalStyle = activeStyle,
                        onStyleChange = setDialogStyle,
                        distanceMeters = distanceMeters,
                        enableGestures = true,
                        externalColorByPace = colorByPace,
                        externalReplayTrigger = replayTrigger,
                        externalResetTrigger = resetTrigger,
                        sessionId = resolvedSessionId,
                        sessionTitle = sessionTitle,
                        sessionEndTimestampMs = sessionEndTimestampMs,
                        sessionDurationSec = sessionDurationSec,
                        avgPaceSecPerKm = avgPaceSecPerKm,
                        avgHrBpm = avgHrBpm,
                        hrTimelineList = hrTimelineList,
                        externalCine3dEnabled = isCine3dEnabled,
                        onCine3dChange = { isCine3dEnabled = it },
                        externalPhotoReloadTrigger = photoReloadTrigger,
                        onReplayLiveMetricsChange = { isRunning, liveDist, livePace, liveBpm ->
                            isReplayRunningInDialog = isRunning
                            if (isRunning) {
                                liveDialogDistStr = liveDist
                                liveDialogPaceStr = livePace
                                liveDialogBpmStr = liveBpm
                            } else {
                                liveDialogDistStr = distKmStr
                                liveDialogPaceStr = paceStr
                                liveDialogBpmStr = bpmStr
                            }
                        }
                    )

                    // Botão Fechar [✕] no Canto Superior Direito
                    IconButton(
                        onClick = {
                            AudioSynth.playTickSound()
                            onDismiss()
                        },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .statusBarsPadding()
                            .padding(14.dp)
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Color(0xDD0F172A))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Fechar Mapa",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Novo Cabeçalho Social e Limpo no Topo Esquerdo (Localização Cidade - UF + Data e Horário do Treino)
                    Surface(
                        color = Color(0xDD0F172A),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .statusBarsPadding()
                            .padding(start = 14.dp, top = 14.dp, end = 64.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = if (sessionTitle.isNotBlank()) {
                                        "📍 $cityStateLabel • $sessionTitle"
                                    } else {
                                        "📍 $cityStateLabel"
                                    },
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Black,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = formattedDateTimeSubtitle,
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Painel Flutuante do Rodapé (Sem barra de legendas poluída + Folga de 48.dp acima da barra de navegação do Android)
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 48.dp)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Métricas Principais da Sessão (com Distância, Pace e BPM subindo ao vivo durante o Replay!)
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xF2141722)),
                            border = BorderStroke(
                                1.dp,
                                if (isReplayRunningInDialog) Color(0xFF00E5FF).copy(alpha = 0.65f) else Color(0xFF262C3D)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceAround,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = if (isReplayRunningInDialog) liveDialogDistStr else distKmStr,
                                        color = Color.White,
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontStyle = FontStyle.Italic
                                    )
                                    Text(text = "Distância", color = Color(0xFF94A3B8), fontSize = 10.sp)
                                }
                                Box(modifier = Modifier.width(1.dp).height(24.dp).background(Color(0xFF262C3D)))
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = if (isReplayRunningInDialog) liveDialogPaceStr else paceStr,
                                        color = Color(0xFF00E676),
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontStyle = FontStyle.Italic
                                    )
                                    Text(text = "Ritmo", color = Color(0xFF94A3B8), fontSize = 10.sp)
                                }
                                Box(modifier = Modifier.width(1.dp).height(24.dp).background(Color(0xFF262C3D)))
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = if (isReplayRunningInDialog) liveDialogBpmStr else bpmStr,
                                        color = Color(0xFFFFD700),
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontStyle = FontStyle.Italic
                                    )
                                    Text(text = "Cardíaco / PR", color = Color(0xFF94A3B8), fontSize = 10.sp)
                                }
                            }
                        }

                        // Linha 1 de Controles Rápidos: [ 🎥 Cine 3D ] | [ 📷 + Fotos / 📷 Fotos (X) ] | [ Cor: Sprints ]
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                onClick = {
                                    isCine3dEnabled = !isCine3dEnabled
                                    AudioSynth.playTickSound()
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isCine3dEnabled) Color(0xFF00E5FF).copy(alpha = 0.22f) else Color(0xF2141722),
                                border = BorderStroke(1.dp, if (isCine3dEnabled) Color(0xFF00E5FF) else Color(0xFF262C3D)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 10.dp)) {
                                    Text(
                                        text = "🎥 Cine 3D",
                                        color = if (isCine3dEnabled) Color(0xFF00E5FF) else Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Black,
                                        maxLines = 1
                                    )
                                }
                            }

                            Surface(
                                onClick = {
                                    AudioSynth.playTickSound()
                                    dialogPhotoPicker.launch("image/*")
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFFF59E0B).copy(alpha = 0.18f),
                                border = BorderStroke(1.dp, Color(0xFFF59E0B)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
                                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp)
                                ) {
                                    VectorCameraIcon(
                                        modifier = Modifier.size(15.dp),
                                        tint = Color(0xFFFBBF24)
                                    )
                                    Text(
                                        text = if (dialogRoutePhotos.isEmpty()) "+ Fotos" else "Fotos (${dialogRoutePhotos.size})",
                                        color = Color(0xFFFBBF24),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                }
                            }

                            Surface(
                                onClick = {
                                    colorByPace = !colorByPace
                                    AudioSynth.playTickSound()
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = if (colorByPace) Color(0xFF00E676).copy(alpha = 0.25f) else Color(0xF2141722),
                                border = BorderStroke(1.dp, if (colorByPace) Color(0xFF00E676) else Color(0xFF262C3D)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 10.dp)) {
                                    Text(
                                        text = if (colorByPace) "Cor: Sprints" else "Cor: Única",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                }
                            }
                        }

                        // Linha 2 de Controles: [ ▶ Animar / ↻ Replay Cine 3D ] e [ Enquadrar ]
                        val exportCoroutineScope = rememberCoroutineScope()
                        var isExporting3dVideo by remember { mutableStateOf(false) }
                        var export3dVideoProgress by remember { mutableFloatStateOf(0f) }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                onClick = {
                                    isCine3dEnabled = true
                                    replayTrigger++
                                    AudioSynth.playTickSound()
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFF00E5FF),
                                modifier = Modifier.weight(1.3f)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                    modifier = Modifier.padding(vertical = 10.dp)
                                ) {
                                    Icon(
                                        if (replayTrigger > 0) Icons.Rounded.Replay else Icons.Rounded.PlayArrow,
                                        contentDescription = null,
                                        tint = Color.Black,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        if (replayTrigger > 0) "Replay Cine 3D" else "Animar Cine 3D",
                                        color = Color.Black,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                            }

                            Surface(
                                onClick = {
                                    resetTrigger++
                                    AudioSynth.playTickSound()
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xF2141722),
                                border = BorderStroke(1.dp, Color(0xFF262C3D)),
                                modifier = Modifier.weight(0.9f)
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 10.dp)) {
                                    Text("Enquadrar", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // Linha 3: Botão de Destaque [ 🎬 Baixar / Compartilhar Vídeo 3D (MP4 para Reels/Status) ]
                        Surface(
                            onClick = {
                                if (!isExporting3dVideo) {
                                    AudioSynth.playTickSound()
                                    isExporting3dVideo = true
                                    export3dVideoProgress = 0.02f
                                    exportCoroutineScope.launch {
                                        try {
                                            com.example.utils.Route3dVideoExporter.exportAndShareRoute3dVideo(
                                                context = context,
                                                sessionId = resolvedSessionId,
                                                sessionTitle = sessionTitle,
                                                cityStateLabel = cityStateLabel,
                                                sessionEndTimestampMs = sessionEndTimestampMs,
                                                distanceMeters = distanceMeters,
                                                avgPaceSecPerKm = avgPaceSecPerKm,
                                                avgHrBpm = avgHrBpm,
                                                hrTimelineList = hrTimelineList,
                                                routePoints = routePoints,
                                                routePointItems = routePointItems,
                                                mapStyle = activeStyle,
                                                colorByPace = colorByPace,
                                                onProgress = { prog ->
                                                    export3dVideoProgress = prog
                                                }
                                            )
                                        } finally {
                                            isExporting3dVideo = false
                                        }
                                    }
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isExporting3dVideo) Color(0xFF1E293B) else Color(0xFF00E676),
                            border = BorderStroke(
                                1.dp,
                                if (isExporting3dVideo) Color(0xFF00E5FF) else Color(0xFF00E676)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("btn_export_3d_route_video")
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Share,
                                        contentDescription = "Baixar / Compartilhar Vídeo 3D",
                                        tint = if (isExporting3dVideo) Color(0xFF00E5FF) else Color(0xFF06101E),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = if (isExporting3dVideo) {
                                            "Gerando Vídeo 3D MP4 (${(export3dVideoProgress * 100).toInt()}%)..."
                                        } else {
                                            "🎬 Baixar / Compartilhar Vídeo 3D (MP4)"
                                        },
                                        color = if (isExporting3dVideo) Color.White else Color(0xFF06101E),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Black,
                                        maxLines = 1
                                    )
                                }
                                if (isExporting3dVideo) {
                                    LinearProgressIndicator(
                                        progress = { export3dVideoProgress.coerceIn(0f, 1f) },
                                        color = Color(0xFF00E5FF),
                                        trackColor = Color.White.copy(alpha = 0.15f),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(4.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Sobrecarga conveniente aceitando `List<GpsTrackPoint>` diretamente do Room.
 */
@JvmName("AnimatedRouteMapViewGpsTrackPoints")
@Composable
fun AnimatedRouteMapView(
    gpsTrackPoints: List<GpsTrackPoint>,
    modifier: Modifier = Modifier,
    isInteractiveReplay: Boolean = true,
    initialStyle: MapTileStyle = MapTileStyle.DARK
) {
    val routePairList = remember(gpsTrackPoints) {
        gpsTrackPoints.map { Pair(it.latitude, it.longitude) }
    }
    AnimatedRouteMapView(
        routePoints = routePairList,
        modifier = modifier,
        isInteractiveReplay = isInteractiveReplay,
        initialStyle = initialStyle
    )
}

// ==================== CÁLCULOS MERCATOR E HELPERS ====================

private data class MapBounds(
    val minWorldX: Double,
    val maxWorldX: Double,
    val minWorldY: Double,
    val maxWorldY: Double
)

private data class TileCoord(val tileX: Int, val tileY: Int, val zoom: Int)

private data class MapProjection(
    val bounds: MapBounds,
    val zoom: Int,
    val visibleTiles: List<TileCoord>,
    val fitScale: Float = 1f
)

private fun calculateMapProjection(
    points: List<Pair<Double, Double>>,
    widthPx: Float,
    heightPx: Float
): MapProjection {
    if (points.isEmpty()) {
        return MapProjection(MapBounds(0.0, 0.0, 0.0, 0.0), 14, emptyList(), 1f)
    }

    val minLat = points.minOf { it.first }
    val maxLat = points.maxOf { it.first }
    val minLon = points.minOf { it.second }
    val maxLon = points.maxOf { it.second }

    // Determina o zoom ótimo e Auto-BoundingBox justo (~82% do quadro ocupado pelo traçado)
    var zoom = 18
    while (zoom > 10) {
        val minX = lonToWorldX(minLon, zoom)
        val maxX = lonToWorldX(maxLon, zoom)
        val minY = latToWorldY(maxLat, zoom)
        val maxY = latToWorldY(minLat, zoom)

        val spanX = abs(maxX - minX)
        val spanY = abs(maxY - minY)

        if (spanX <= widthPx * 0.84f && spanY <= heightPx * 0.84f) {
            break
        }
        zoom--
    }

    val minX = lonToWorldX(minLon, zoom)
    val maxX = lonToWorldX(maxLon, zoom)
    val minY = latToWorldY(maxLat, zoom)
    val maxY = latToWorldY(minLat, zoom)
    val spanX = abs(maxX - minX)
    val spanY = abs(maxY - minY)

    val fitScale = if (spanX > 4.0 || spanY > 4.0) {
        val scaleX = if (spanX > 1.0) (widthPx * 0.82) / spanX else 2.6
        val scaleY = if (spanY > 1.0) (heightPx * 0.82) / spanY else 2.6
        minOf(scaleX, scaleY).toFloat().coerceIn(1.0f, 2.6f)
    } else {
        1.25f
    }

    val centerWorldX = (minX + maxX) / 2.0
    val centerWorldY = (minY + maxY) / 2.0

    val minWorldX = centerWorldX - (widthPx / 2.0)
    val maxWorldX = centerWorldX + (widthPx / 2.0)
    val minWorldY = centerWorldY - (heightPx / 2.0)
    val maxWorldY = centerWorldY + (heightPx / 2.0)

    val bounds = MapBounds(minWorldX, maxWorldX, minWorldY, maxWorldY)

    // Margem expandida (±2 tiles) para garantir cobertura total de satélite mesmo durante inclinação 3D (48°) e rotação de azimute
    val maxTileIndex = (1 shl zoom) - 1
    val halfWorldW = (widthPx / 2.0) / fitScale.coerceAtLeast(1f)
    val halfWorldH = (heightPx / 2.0) / fitScale.coerceAtLeast(1f)
    val startTileX = (floor((centerWorldX - halfWorldW) / 256.0).toInt() - 2).coerceIn(0, maxTileIndex)
    val endTileX = (floor((centerWorldX + halfWorldW) / 256.0).toInt() + 2).coerceIn(0, maxTileIndex)
    val startTileY = (floor((centerWorldY - halfWorldH) / 256.0).toInt() - 2).coerceIn(0, maxTileIndex)
    val endTileY = (floor((centerWorldY + halfWorldH) / 256.0).toInt() + 2).coerceIn(0, maxTileIndex)

    val visibleTiles = mutableListOf<TileCoord>()
    for (tx in startTileX..endTileX) {
        for (ty in startTileY..endTileY) {
            visibleTiles.add(TileCoord(tx, ty, zoom))
        }
    }

    return MapProjection(bounds, zoom, visibleTiles, fitScale)
}

private fun lonToWorldX(lon: Double, zoom: Int): Double {
    return ((lon + 180.0) / 360.0) * (256.0 * (1 shl zoom))
}

private fun latToWorldY(lat: Double, zoom: Int): Double {
    val sinLat = sin(Math.toRadians(lat)).coerceIn(-0.9999, 0.9999)
    return (0.5 - (ln((1.0 + sinLat) / (1.0 - sinLat)) / (4.0 * PI))) * (256.0 * (1 shl zoom))
}

private fun calculateHaversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6371000.0 // Raio da Terra em metros
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return r * c
}

private fun getSubPathAtProgress(points: List<Offset>, progress: Float): Pair<Path, Offset?> {
    val path = Path()
    if (points.isEmpty()) return Pair(path, null)
    if (points.size == 1 || progress <= 0f) {
        path.moveTo(points.first().x, points.first().y)
        return Pair(path, points.first())
    }

    path.moveTo(points.first().x, points.first().y)
    if (progress >= 1f) {
        for (i in 1 until points.size) {
            path.lineTo(points[i].x, points[i].y)
        }
        return Pair(path, points.last())
    }

    // Calcula comprimento acumulado
    val segmentLengths = FloatArray(points.size - 1)
    var totalLength = 0f
    for (i in 0 until points.size - 1) {
        val dx = points[i + 1].x - points[i].x
        val dy = points[i + 1].y - points[i].y
        val len = sqrt(dx * dx + dy * dy)
        segmentLengths[i] = len
        totalLength += len
    }

    val targetLength = totalLength * progress
    var accumulated = 0f
    var headOffset = points.first()

    for (i in 0 until points.size - 1) {
        val segLen = segmentLengths[i]
        if (accumulated + segLen >= targetLength) {
            val remain = targetLength - accumulated
            val fraction = if (segLen > 0) remain / segLen else 0f
            val startPt = points[i]
            val endPt = points[i + 1]
            headOffset = Offset(
                x = startPt.x + (endPt.x - startPt.x) * fraction,
                y = startPt.y + (endPt.y - startPt.y) * fraction
            )
            path.lineTo(headOffset.x, headOffset.y)
            break
        } else {
            path.lineTo(points[i + 1].x, points[i + 1].y)
            accumulated += segLen
            headOffset = points[i + 1]
        }
    }

    return Pair(path, headOffset)
}

private fun downloadTileBitmap(urlStr: String): Bitmap? {
    return try {
        val url = URL(urlStr)
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 4000
        conn.readTimeout = 4000
        conn.setRequestProperty("User-Agent", "MaxPerformanceRun/3.0")
        conn.doInput = true
        conn.connect()
        val inputStream = conn.inputStream
        val bitmap = BitmapFactory.decodeStream(inputStream)
        inputStream.close()
        conn.disconnect()
        bitmap
    } catch (e: Exception) {
        null
    }
}

// =============================================================================================
// ÍCONES VETORIAIS MINIMALISTAS (CÂMERA, CAMADAS DE MAPA E CENTRALIZAÇÃO) + MAPA AO VIVO STRAVA
// =============================================================================================

/**
 * Ícone vetorial minimalista e esportivo de câmera fotográfica:
 * - Corpo retangular com cantos suavemente arredondados (`RoundRect`)
 * - Trapézio/prisma centralizado no topo (visor superior)
 * - Lente circular central com anel vazado + núcleo sutil
 * - Ponto do flash no canto superior esquerdo do corpo da câmera
 */
@Composable
fun VectorCameraIcon(
    modifier: Modifier = Modifier.size(18.dp),
    tint: Color = Color.White
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val strokeW = (w * 0.095f).coerceAtLeast(1.6f)

        // Corpo retangular arredondado da câmera
        val bodyLeft = w * 0.10f
        val bodyRight = w * 0.90f
        val bodyTop = h * 0.28f
        val bodyBottom = h * 0.84f
        val cornerRadius = w * 0.15f

        drawRoundRect(
            color = tint,
            topLeft = Offset(bodyLeft, bodyTop),
            size = Size(bodyRight - bodyLeft, bodyBottom - bodyTop),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius, cornerRadius),
            style = Stroke(width = strokeW)
        )

        // Trapézio/prisma centralizado no topo (visor superior)
        val prismPath = Path().apply {
            moveTo(w * 0.33f, bodyTop)
            lineTo(w * 0.40f, h * 0.15f)
            lineTo(w * 0.60f, h * 0.15f)
            lineTo(w * 0.67f, bodyTop)
        }
        drawPath(
            path = prismPath,
            color = tint,
            style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        // Lente circular central com anel vazado + reflexo interno
        val lensCenter = Offset(w * 0.50f, (bodyTop + bodyBottom) * 0.52f)
        val lensOuterRadius = (bodyBottom - bodyTop) * 0.29f
        drawCircle(
            color = tint,
            radius = lensOuterRadius,
            center = lensCenter,
            style = Stroke(width = strokeW)
        )
        drawCircle(
            color = tint.copy(alpha = 0.45f),
            radius = lensOuterRadius * 0.36f,
            center = lensCenter
        )

        // Ponto do flash no canto superior esquerdo do corpo
        drawCircle(
            color = tint,
            radius = strokeW * 0.62f,
            center = Offset(bodyLeft + (bodyRight - bodyLeft) * 0.20f, bodyTop + (bodyBottom - bodyTop) * 0.24f)
        )
    }
}

/**
 * Ícone vetorial minimalista de Camadas de Mapa (losangos empilhados estilo Strava).
 */
@Composable
fun VectorMapLayersIcon(
    modifier: Modifier = Modifier.size(18.dp),
    tint: Color = Color.White
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val strokeW = (w * 0.095f).coerceAtLeast(1.6f)

        val topDiamond = Path().apply {
            moveTo(w * 0.50f, h * 0.16f)
            lineTo(w * 0.88f, h * 0.38f)
            lineTo(w * 0.50f, h * 0.58f)
            lineTo(w * 0.12f, h * 0.38f)
            close()
        }
        drawPath(
            path = topDiamond,
            color = tint,
            style = Stroke(width = strokeW, join = StrokeJoin.Round)
        )

        val midLayer = Path().apply {
            moveTo(w * 0.14f, h * 0.55f)
            lineTo(w * 0.50f, h * 0.74f)
            lineTo(w * 0.86f, h * 0.55f)
        }
        drawPath(
            path = midLayer,
            color = tint,
            style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        val bottomLayer = Path().apply {
            moveTo(w * 0.14f, h * 0.70f)
            lineTo(w * 0.50f, h * 0.89f)
            lineTo(w * 0.86f, h * 0.70f)
        }
        drawPath(
            path = bottomLayer,
            color = tint.copy(alpha = 0.75f),
            style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

/**
 * Ícone Vetorial de Mapa Dobrado com Pino de Localização (`VectorMapWithPinIcon`):
 * - 3 painéis verticais de um mapa dobrado em zigue-zague (três trapézios verticais conectados pelas dobras) na base;
 * - Pino de GPS clássico (gota invertida com furo central) cravado no centro superior do mapa.
 */
@Composable
fun VectorMapWithPinIcon(
    modifier: Modifier = Modifier.size(20.dp),
    tint: Color = Color.White,
    pinColor: Color = tint
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val strokeW = (w * 0.088f).coerceAtLeast(1.5f)

        // 1. Base: 3 painéis verticais de mapa dobrado em zigue-zague
        val x0 = w * 0.08f
        val x1 = w * 0.35f
        val x2 = w * 0.65f
        val x3 = w * 0.92f

        val topY0 = h * 0.44f
        val topY1 = h * 0.54f
        val topY2 = h * 0.42f
        val topY3 = h * 0.52f

        val botY0 = h * 0.84f
        val botY1 = h * 0.93f
        val botY2 = h * 0.81f
        val botY3 = h * 0.91f

        val foldedMapOutline = Path().apply {
            moveTo(x0, topY0)
            // Deixa abertura sutil no centro onde o pino crava
            lineTo(x1, topY1)
            lineTo(w * 0.39f, h * 0.50f)
            moveTo(w * 0.61f, h * 0.44f)
            lineTo(x2, topY2)
            lineTo(x3, topY3)
            lineTo(x3, botY3)
            lineTo(x2, botY2)
            lineTo(x1, botY1)
            lineTo(x0, botY0)
            close()
        }
        drawPath(
            path = foldedMapOutline,
            color = tint.copy(alpha = 0.92f),
            style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        // Linhas verticais das duas dobras internas do mapa
        drawLine(
            color = tint.copy(alpha = 0.72f),
            start = Offset(x1, topY1),
            end = Offset(x1, botY1),
            strokeWidth = strokeW * 0.85f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = tint.copy(alpha = 0.72f),
            start = Offset(x2, topY2),
            end = Offset(x2, botY2),
            strokeWidth = strokeW * 0.85f,
            cap = StrokeCap.Round
        )

        // 2. Pino de GPS clássico (gota invertida com furo central) cravado no centro superior
        val pinCenterX = w * 0.50f
        val pinHeadCenterY = h * 0.25f
        val pinHeadR = w * 0.19f
        val pinTipY = h * 0.61f

        val pinPath = Path().apply {
            moveTo(pinCenterX, pinTipY)
            cubicTo(
                pinCenterX - pinHeadR * 0.65f, pinHeadCenterY + pinHeadR * 1.15f,
                pinCenterX - pinHeadR, pinHeadCenterY + pinHeadR * 0.45f,
                pinCenterX - pinHeadR, pinHeadCenterY
            )
            arcTo(
                rect = androidx.compose.ui.geometry.Rect(
                    left = pinCenterX - pinHeadR,
                    top = pinHeadCenterY - pinHeadR,
                    right = pinCenterX + pinHeadR,
                    bottom = pinHeadCenterY + pinHeadR
                ),
                startAngleDegrees = 180f,
                sweepAngleDegrees = 180f,
                forceMoveTo = false
            )
            cubicTo(
                pinCenterX + pinHeadR, pinHeadCenterY + pinHeadR * 0.45f,
                pinCenterX + pinHeadR * 0.65f, pinHeadCenterY + pinHeadR * 1.15f,
                pinCenterX, pinTipY
            )
            close()
        }

        drawPath(
            path = pinPath,
            color = pinColor.copy(alpha = 0.24f)
        )
        drawPath(
            path = pinPath,
            color = pinColor,
            style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        // Furo central do pino de GPS
        drawCircle(
            color = pinColor,
            radius = pinHeadR * 0.36f,
            center = Offset(pinCenterX, pinHeadCenterY)
        )
    }
}

/**
 * Ícone vetorial minimalista de Centralizar GPS (`◎` Mira/Alvo estilo Strava).
 */
@Composable
fun VectorCenterTargetIcon(
    modifier: Modifier = Modifier.size(18.dp),
    tint: Color = Color.White
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val c = Offset(w / 2f, h / 2f)
        val r = minOf(w, h) * 0.30f
        val strokeW = (w * 0.095f).coerceAtLeast(1.6f)

        drawCircle(
            color = tint,
            radius = r,
            center = c,
            style = Stroke(width = strokeW)
        )
        drawCircle(
            color = tint,
            radius = r * 0.36f,
            center = c
        )
        // 4 hastes cardeais da mira GPS
        drawLine(tint, Offset(c.x, h * 0.08f), Offset(c.x, c.y - r), strokeWidth = strokeW, cap = StrokeCap.Round)
        drawLine(tint, Offset(c.x, c.y + r), Offset(c.x, h * 0.92f), strokeWidth = strokeW, cap = StrokeCap.Round)
        drawLine(tint, Offset(w * 0.08f, c.y), Offset(c.x - r, c.y), strokeWidth = strokeW, cap = StrokeCap.Round)
        drawLine(tint, Offset(c.x + r, c.y), Offset(w * 0.92f, c.y), strokeWidth = strokeW, cap = StrokeCap.Round)
    }
}

/**
 * Estilos de camada para o Mapa em Tempo Real no HUD de Corrida (Custo Zero • Slippy Tiles).
 */
enum class LiveRunMapLayerStyle(
    val displayName: String,
    val providerKey: String,
    val urlTemplate: String,
    val isSatellite: Boolean
) {
    SATELLITE(
        displayName = "Satélite",
        providerKey = "live_google_hybrid",
        urlTemplate = "https://mt1.google.com/vt/lyrs=y&x={x}&y={y}&z={z}",
        isSatellite = true
    ),
    STREET_LIGHT(
        displayName = "Mapa de Ruas",
        providerKey = "live_google_streets",
        urlTemplate = "https://mt1.google.com/vt/lyrs=m&x={x}&y={y}&z={z}",
        isSatellite = false
    ),
    SPORT_DARK(
        displayName = "Satélite Puro",
        providerKey = "satellite",
        urlTemplate = "https://mt1.google.com/vt/lyrs=s&x={x}&y={y}&z={z}",
        isSatellite = true
    );

    fun nextLayer(): LiveRunMapLayerStyle = when (this) {
        SATELLITE -> STREET_LIGHT
        STREET_LIGHT -> SPORT_DARK
        SPORT_DARK -> SATELLITE
    }
}

/**
 * Motor de Mapa em Tempo Real ao Fundo durante a Corrida (Estilo Strava — 0 KB APK / Custo Zero):
 * - Renderiza blocos 256x256 com cache em memória (`TileCache`) centrados na coordenada GPS ao vivo;
 * - Caso ainda não haja pontos gravados na sessão (teste parado/indoor), usa a última localização conhecida do aparelho (ou Rio Branco - AC como fallback inicial);
 * - Desenha a polilinha colorida (azul/ciano/verde) de todos os pontos já percorridos na sessão;
 * - Desenha o Ponto Azul/Ciano Pulsante com Cone de Direção (Azimute/Bearing) na posição exata do corredor;
 * - Suporta perspectiva 3D (`48°` de inclinação acompanhando o azimute da corrida) vs 2D plano e gestos de arraste/zoom.
 */
@Composable
fun LiveRunningMapBackground(
    currentLat: Double?,
    currentLon: Double?,
    currentBearingDegrees: Float,
    recordedPoints: List<com.example.services.RunTracker.RecordedGpsPoint>,
    layerStyle: LiveRunMapLayerStyle,
    is3dMode: Boolean,
    recenterTrigger: Int,
    onUserPanDetected: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    remember(context) {
        TileCache.init(context)
        true
    }

    // Fallback de localização real do aparelho quando o usuário abre o mapa ainda parado (sem pontos em recordedPoints)
    val lastKnownDeviceCoords = remember(context, currentLat == null && recordedPoints.isEmpty()) {
        if (currentLat != null || recordedPoints.isNotEmpty()) {
            null
        } else {
            runCatching {
                val lm = context.getSystemService(android.content.Context.LOCATION_SERVICE) as? android.location.LocationManager
                val providers = listOf(
                    android.location.LocationManager.GPS_PROVIDER,
                    android.location.LocationManager.NETWORK_PROVIDER,
                    android.location.LocationManager.PASSIVE_PROVIDER
                )
                providers.mapNotNull { provider ->
                    runCatching {
                        @Suppress("MissingPermission")
                        lm?.getLastKnownLocation(provider)
                    }.getOrNull()
                }.filter { loc ->
                    !loc.latitude.isNaN() && !loc.longitude.isNaN() &&
                        (abs(loc.latitude) > 0.0001 || abs(loc.longitude) > 0.0001)
                }.maxByOrNull { it.time }?.let { Pair(it.latitude, it.longitude) }
            }.getOrNull()
        }
    }

    // Coordenada ativa: GPS atual -> último ponto gravado -> última localização do aparelho -> centro de Rio Branco - AC
    val activeLat = currentLat
        ?: recordedPoints.lastOrNull()?.latitude
        ?: lastKnownDeviceCoords?.first
        ?: -9.97499
    val activeLon = currentLon
        ?: recordedPoints.lastOrNull()?.longitude
        ?: lastKnownDeviceCoords?.second
        ?: -67.82430

    var userPanOffset by remember { mutableStateOf(Offset.Zero) }
    var userZoomScale by remember { mutableFloatStateOf(1f) }
    val baseHiDpiScale = remember(density) { (density / 1.45f).coerceIn(1.65f, 2.35f) }
    val effectiveScale = (baseHiDpiScale * userZoomScale).coerceIn(1.1f, 6.5f)

    LaunchedEffect(recenterTrigger) {
        if (recenterTrigger > 0) {
            userPanOffset = Offset.Zero
            userZoomScale = 1f
        }
    }

    val tiltFactor by animateFloatAsState(
        targetValue = if (is3dMode) 1f else 0f,
        animationSpec = tween(durationMillis = 550, easing = FastOutSlowInEasing),
        label = "live_map_3d_tilt"
    )

    var smoothedBearing by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(currentBearingDegrees, is3dMode) {
        val target = if (is3dMode) -currentBearingDegrees else 0f
        smoothedBearing = Cine3DCameraMath.lerpAngle(smoothedBearing, target, 0.25f)
    }

    val pulseTransition = rememberInfiniteTransition(label = "live_runner_pulse")
    val haloScale by pulseTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.65f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "live_halo_scale"
    )
    val haloAlpha by pulseTransition.animateFloat(
        initialValue = 0.50f,
        targetValue = 0.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "live_halo_alpha"
    )

    val loadedTiles = remember { mutableStateMapOf<String, ImageBitmap>() }
    val zoom = 17

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0B1520))
    ) {
        val centerWorldX = lonToWorldX(activeLon, zoom)
        val centerWorldY = latToWorldY(activeLat, zoom)
        val panWorldDX = -userPanOffset.x / effectiveScale
        val panWorldDY = -userPanOffset.y / effectiveScale
        val centerTileX = floor((centerWorldX + panWorldDX) / 256.0).toInt()
        val centerTileY = floor((centerWorldY + panWorldDY) / 256.0).toInt()
        val maxTileIndex = (1 shl zoom) - 1

        val visibleTiles = remember(centerTileX, centerTileY, zoom) {
            val list = ArrayList<TileCoord>(35)
            for (dx in -2..2) {
                for (dy in -3..3) {
                    val tx = (centerTileX + dx).coerceIn(0, maxTileIndex)
                    val ty = (centerTileY + dy).coerceIn(0, maxTileIndex)
                    list.add(TileCoord(tx, ty, zoom))
                }
            }
            list
        }

        // Pré-carrega da memória RAM se disponível
        remember(visibleTiles, layerStyle) {
            for ((tx, ty, z) in visibleTiles) {
                val key = TileCache.buildKey(layerStyle.providerKey, z, tx, ty)
                if (!loadedTiles.containsKey(key)) {
                    TileCache.getMemoryImageBitmap(key)?.let { loadedTiles[key] = it }
                }
            }
            true
        }

        LaunchedEffect(visibleTiles, layerStyle) {
            val missing = visibleTiles.filter { (tx, ty, z) ->
                val key = TileCache.buildKey(layerStyle.providerKey, z, tx, ty)
                val mem = TileCache.getMemoryImageBitmap(key)
                if (mem != null) {
                    loadedTiles[key] = mem
                    false
                } else {
                    !loadedTiles.containsKey(key)
                }
            }
            if (missing.isNotEmpty()) {
                withContext(Dispatchers.IO) {
                    missing.map { (tx, ty, z) ->
                        async {
                            val key = TileCache.buildKey(layerStyle.providerKey, z, tx, ty)
                            val bmp = TileCache.getOrLoadTileBitmap(
                                context = context,
                                provider = layerStyle.providerKey,
                                zoom = z,
                                tileX = tx,
                                tileY = ty
                            ) {
                                com.example.utils.MapTileHelper.fetchTileNetworkOnly(
                                    urlTemplate = layerStyle.urlTemplate,
                                    zoom = z,
                                    tileX = tx,
                                    tileY = ty,
                                    isSatellite = layerStyle.isSatellite
                                )
                            }
                            if (bmp != null && !bmp.isRecycled) {
                                val img = TileCache.getMemoryImageBitmap(key)
                                    ?: runCatching { bmp.asImageBitmap() }.getOrNull()
                                if (img != null) {
                                    withContext(Dispatchers.Main) {
                                        loadedTiles[key] = img
                                    }
                                }
                            }
                        }
                    }.awaitAll()
                }
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    if (tiltFactor > 0.001f) {
                        rotationX = 48f * tiltFactor
                        rotationZ = smoothedBearing * tiltFactor
                        scaleX = 1f + (0.34f * tiltFactor)
                        scaleY = 1f + (0.34f * tiltFactor)
                        cameraDistance = 16f * density
                    }
                }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, gestureZoom, _ ->
                        userZoomScale = (userZoomScale * gestureZoom).coerceIn(0.65f, 3.8f)
                        if (abs(pan.x) > 1.5f || abs(pan.y) > 1.5f) {
                            userPanOffset += pan
                            onUserPanDetected()
                        }
                    }
                }
        ) {
            // Posiciona o corredor no centro visual da área útil do mapa (46% da altura)
            val anchorScreenCenter = Offset(size.width * 0.50f, size.height * 0.46f)

            withTransform({
                translate(userPanOffset.x, userPanOffset.y)
                scale(effectiveScale, effectiveScale, pivot = anchorScreenCenter)
            }) {
                var drawnCount = 0
                for ((tx, ty, z) in visibleTiles) {
                    val key = TileCache.buildKey(layerStyle.providerKey, z, tx, ty)
                    val img = loadedTiles[key] ?: TileCache.getMemoryImageBitmap(key)
                    if (img != null) {
                        val tileScreenX = anchorScreenCenter.x + (tx * 256.0 - centerWorldX).toFloat()
                        val tileScreenY = anchorScreenCenter.y + (ty * 256.0 - centerWorldY).toFloat()
                        drawImage(
                            image = img,
                            dstOffset = IntOffset(tileScreenX.roundToInt(), tileScreenY.roundToInt()),
                            dstSize = IntSize(257, 257)
                        )
                        drawnCount++
                    }
                }

                // Fallback de grade caso esteja sem internet e sem cache local na região
                if (drawnCount == 0) {
                    val gridStep = 44f
                    var gx = -size.width
                    while (gx < size.width * 2f) {
                        drawLine(
                            color = Color(0xFF1E293B),
                            start = Offset(gx, -size.height),
                            end = Offset(gx, size.height * 2f),
                            strokeWidth = 1.2f
                        )
                        gx += gridStep
                    }
                    var gy = -size.height
                    while (gy < size.height * 2f) {
                        drawLine(
                            color = Color(0xFF1E293B),
                            start = Offset(-size.width, gy),
                            end = Offset(size.width * 2f, gy),
                            strokeWidth = 1.2f
                        )
                        gy += gridStep
                    }
                } else if (layerStyle.isSatellite) {
                    // Película sutil para destacar o traçado neon sobre imagens de satélite
                    drawRect(
                        color = Color(0x1E040810),
                        topLeft = Offset(-size.width, -size.height),
                        size = Size(size.width * 3f, size.height * 3f)
                    )
                }

                // 1. Polilinha colorida de todos os pontos GPS já percorridos na sessão atual
                if (recordedPoints.size >= 2) {
                    val screenPts = recordedPoints.map { pt ->
                        val wx = lonToWorldX(pt.longitude, zoom)
                        val wy = latToWorldY(pt.latitude, zoom)
                        Offset(
                            x = anchorScreenCenter.x + (wx - centerWorldX).toFloat(),
                            y = anchorScreenCenter.y + (wy - centerWorldY).toFloat()
                        )
                    }

                    // Halo escuro de contraste sob todo o rastro percorrido
                    val fullTrailPath = Path().apply {
                        moveTo(screenPts.first().x, screenPts.first().y)
                        for (i in 1 until screenPts.size) {
                            lineTo(screenPts[i].x, screenPts[i].y)
                        }
                    }
                    drawPath(
                        path = fullTrailPath,
                        color = Color.Black.copy(alpha = 0.55f),
                        style = Stroke(width = 11f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )

                    // Segmentos coloridos por fase (Aquecimento, Caminhada Azul/Ciano, Corrida Verde Neon)
                    for (i in 0 until screenPts.size - 1) {
                        val p1 = screenPts[i]
                        val p2 = screenPts[i + 1]
                        val phase = recordedPoints[i + 1].phase.uppercase(Locale.US)
                        val segColor = when (phase) {
                            "RUN" -> Color(0xFF00E676)
                            "WALK" -> Color(0xFF00E5FF)
                            "WARMUP" -> Color(0xFF38BDF8)
                            else -> Color(0xFF00E676)
                        }
                        drawLine(
                            color = segColor.copy(alpha = 0.32f),
                            start = p1,
                            end = p2,
                            strokeWidth = 12f,
                            cap = StrokeCap.Round
                        )
                        drawLine(
                            color = segColor,
                            start = p1,
                            end = p2,
                            strokeWidth = 6.2f,
                            cap = StrokeCap.Round
                        )
                    }

                    // Marcador de Largada no 1º ponto
                    val startPt = screenPts.first()
                    drawCircle(color = Color.White, radius = 7.5f, center = startPt)
                    drawCircle(color = Color(0xFF10B981), radius = 5.2f, center = startPt)
                }

                // 2. Ponto Azul/Ciano Pulsante com Cone de Direção (Azimute/Bearing) na posição exata do corredor
                val coneRadius = 46f
                withTransform({
                    rotate(degrees = currentBearingDegrees - 90f, pivot = anchorScreenCenter)
                }) {
                    val conePath = Path().apply {
                        moveTo(anchorScreenCenter.x, anchorScreenCenter.y)
                        lineTo(
                            anchorScreenCenter.x + coneRadius,
                            anchorScreenCenter.y - coneRadius * 0.48f
                        )
                        lineTo(
                            anchorScreenCenter.x + coneRadius * 1.12f,
                            anchorScreenCenter.y
                        )
                        lineTo(
                            anchorScreenCenter.x + coneRadius,
                            anchorScreenCenter.y + coneRadius * 0.48f
                        )
                        close()
                    }
                    drawPath(
                        path = conePath,
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF00E5FF).copy(alpha = 0.52f),
                                Color(0xFF0284C7).copy(alpha = 0.0f)
                            ),
                            center = anchorScreenCenter,
                            radius = coneRadius * 1.15f
                        )
                    )
                }

                // Halo pulsante externo + Anel branco + Ponto Azul/Ciano vivo
                drawCircle(
                    color = Color(0xFF00E5FF).copy(alpha = haloAlpha),
                    radius = 18f * haloScale,
                    center = anchorScreenCenter
                )
                drawCircle(
                    color = Color.White,
                    radius = 9.5f,
                    center = anchorScreenCenter
                )
                drawCircle(
                    color = Color(0xFF0284C7),
                    radius = 7.0f,
                    center = anchorScreenCenter
                )
                drawCircle(
                    color = Color(0xFF38BDF8),
                    radius = 3.2f,
                    center = Offset(anchorScreenCenter.x - 1.5f, anchorScreenCenter.y - 1.5f)
                )
            }
        }
    }
}

