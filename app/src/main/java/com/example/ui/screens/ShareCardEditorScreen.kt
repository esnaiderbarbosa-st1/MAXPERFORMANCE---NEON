package com.example.ui.screens

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.Density
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.content.FileProvider
import com.example.data.model.GpsTrackPoint
import com.example.data.model.RoutePointItem
import com.example.data.model.RunningSession
import com.example.data.model.calculateSprintAnalytics
import com.example.data.model.isClassifiedAsRace
import com.example.data.model.isSimulatedOrTestSession
import com.example.data.model.resolveSessionRoutePointsWithPhases
import com.example.data.model.toGpsTrackPoints
import com.example.ui.viewmodel.MainViewModel
import com.example.ui.viewmodel.AppScreen
import com.example.ui.theme.RunColors
import com.example.ui.theme.OnSurfaceDim
import com.example.utils.AudioSynth
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import coil.compose.AsyncImage
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

// ATENCAO: Devem ser mantidas exatamente iguais aos parametros de exportacao (export width 1080, height 1920, fator 3.8)
private const val EXPORT_WIDTH = 1080f
private const val EXPORT_HEIGHT = 1920f
private const val POSITION_SCALE_FACTOR = 3.8f

data class BlockTransform(
    val x: Float = 0f,
    val y: Float = 0f,
    val scale: Float = 1f,
    val fontScale: Float = 1f
)

enum class ProceduralCardTheme(val title: String) {
    CYBER_NEON("Cyber Neon"),
    SUNSET_RUN("Sunset Run"),
    MIDNIGHT_METRO("Midnight Metro"),
    CARBON_GOLD("Carbon & Gold")
}

val cardTextShadow = Shadow(
    color = Color.Black,
    offset = Offset(3f, 3f),
    blurRadius = 6f
)

private object ShareCardPrefs {
    const val PREFS_NAME = "share_card_positions_v7"
    const val MIGRATION_KEY = "positions_migrated_v8"

    fun saveTransform(context: Context, blockId: String, transform: BlockTransform) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putFloat("${blockId}_x", transform.x)
            .putFloat("${blockId}_y", transform.y)
            .putFloat("${blockId}_scale", transform.scale)
            .putFloat("block_${blockId}_font_scale", transform.fontScale)
            .apply()
    }

    fun loadTransform(context: Context, blockId: String, defaultTransform: BlockTransform): BlockTransform {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val migrated = prefs.getBoolean(MIGRATION_KEY, false)
        if (!migrated) {
            val editor = prefs.edit()
            val blocks = listOf("header", "hero", "metrics", "route", "shoe", "progress", "quote")
            for (id in blocks) {
                editor.remove("${id}_x")
                editor.remove("${id}_y")
                editor.remove("${id}_scale")
            }
            editor.putBoolean(MIGRATION_KEY, true)
            editor.apply()
        }

        val fontScale = prefs.getFloat("block_${blockId}_font_scale", defaultTransform.fontScale)
        if (!prefs.contains("${blockId}_x")) {
            return defaultTransform.copy(fontScale = fontScale)
        }
        val x = prefs.getFloat("${blockId}_x", defaultTransform.x)
        val y = prefs.getFloat("${blockId}_y", defaultTransform.y)
        val scale = prefs.getFloat("${blockId}_scale", defaultTransform.scale)

        val maxAllowedX = (EXPORT_WIDTH / 2f) / POSITION_SCALE_FACTOR
        val maxAllowedY = (EXPORT_HEIGHT / 2f) / POSITION_SCALE_FACTOR
        if (x < -maxAllowedX || x > maxAllowedX || y < -maxAllowedY || y > maxAllowedY) {
            return defaultTransform.copy(fontScale = fontScale)
        }
        return BlockTransform(x, y, scale, fontScale)
    }

    fun saveFontScale(context: Context, blockId: String, scale: Float) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putFloat("block_${blockId}_font_scale", scale).apply()
    }

    fun loadFontScale(context: Context, blockId: String, defaultScale: Float = 1f): Float {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getFloat("block_${blockId}_font_scale", defaultScale)
    }

    fun saveTextColor(context: Context, blockId: String, colorInt: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt("block_${blockId}_color", colorInt).apply()
    }

    fun loadTextColor(context: Context, blockId: String, defaultColorInt: Int): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt("block_${blockId}_color", defaultColorInt)
    }

    fun saveVisibility(context: Context, blockKey: String, visible: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(blockKey, visible).apply()
    }

    fun loadVisibility(context: Context, blockKey: String, defaultVisible: Boolean = true): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(blockKey, defaultVisible)
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply()
    }
}

@Composable
fun ShareCardEditorScreen(viewModel: MainViewModel) {
    androidx.activity.compose.BackHandler {
        viewModel.popBackScreen(fallback = AppScreen.RUNNING_DETAIL)
    }

    val context = LocalContext.current
    val sessions by viewModel.runningSessions.collectAsState()
    val selectedSessionState by viewModel.selectedShareSession.collectAsState()

    val demoSession = remember {
        RunningSession(
            id = "demo_fallback_session_5k",
            date = System.currentTimeMillis(),
            weekNumber = 1,
            dayNumber = 1,
            sessionType = "VOLUME",
            durationSec = 1650,
            distanceMeters = 5000,
            avgPaceSecPerKm = 330,
            rpe = 6,
            environment = "OUTDOOR",
            shoeName = "Nike Pegasus 40"
        )
    }

    val demoGpsPoints = remember {
        listOf(
            GpsTrackPoint("demo_fallback_session_5k", 1000L, 1000L, -23.5874, -46.6576),
            GpsTrackPoint("demo_fallback_session_5k", 2000L, 2000L, -23.5862, -46.6560),
            GpsTrackPoint("demo_fallback_session_5k", 3000L, 3000L, -23.5850, -46.6540),
            GpsTrackPoint("demo_fallback_session_5k", 4000L, 4000L, -23.5835, -46.6528),
            GpsTrackPoint("demo_fallback_session_5k", 5000L, 5000L, -23.5820, -46.6535),
            GpsTrackPoint("demo_fallback_session_5k", 6000L, 6000L, -23.5812, -46.6552),
            GpsTrackPoint("demo_fallback_session_5k", 7000L, 7000L, -23.5822, -46.6575),
            GpsTrackPoint("demo_fallback_session_5k", 8000L, 8000L, -23.5838, -46.6590),
            GpsTrackPoint("demo_fallback_session_5k", 9000L, 9000L, -23.5855, -46.6598),
            GpsTrackPoint("demo_fallback_session_5k", 10000L, 10000L, -23.5870, -46.6588),
            GpsTrackPoint("demo_fallback_session_5k", 11000L, 11000L, -23.5874, -46.6576)
        )
    }

    val runningShoes by viewModel.runningShoes.collectAsState()
    val isUsingDemo = selectedSessionState == null && sessions.isEmpty()
    val session = remember(selectedSessionState, sessions) {
        val targetId = selectedSessionState?.id
        if (targetId != null) {
            sessions.find { it.id == targetId } ?: selectedSessionState ?: demoSession
        } else {
            sessions.firstOrNull() ?: demoSession
        }
    }

    val realGpsPoints by viewModel.getGpsPointsForSession(session.id).collectAsState(initial = emptyList())
    val effectiveRealPoints = remember(realGpsPoints, session.routePointsJson) {
        if (realGpsPoints.isNotEmpty()) {
            realGpsPoints
        } else {
            session.routePointsJson.toGpsTrackPoints(session.id)
        }
    }
    val gpsPoints = if (isUsingDemo) demoGpsPoints else effectiveRealPoints

    val templateDrawables = remember {
        listOf(
            com.example.R.drawable.template_1,
            com.example.R.drawable.template_2,
            com.example.R.drawable.template_3,
            com.example.R.drawable.template_4,
            com.example.R.drawable.template_5,
            com.example.R.drawable.template_6,
            com.example.R.drawable.template_7,
            com.example.R.drawable.template_8
        )
    }

    var selectedPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var selectedTheme by remember { mutableStateOf<ProceduralCardTheme?>(null) }
    var selectedTemplateResId by remember { mutableStateOf<Int?>(com.example.R.drawable.template_1) }
    var customQuote by remember { mutableStateOf("Superando limites a cada KM!") }
    var showQuoteEditDialog by remember { mutableStateOf(false) }

    var bgScaleMode by remember { mutableStateOf(0) }
    var isFullscreen by remember { mutableStateOf(false) }
    var selectedBlockId by remember { mutableStateOf("hero") }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            selectedPhotoUri = uri
            selectedTheme = null
            selectedTemplateResId = null
            AudioSynth.playTickSound()
        }
    }

    val defaultHeader = remember { BlockTransform(x = 0f, y = -155f, scale = 1f, fontScale = 1f) }
    val defaultHero = remember { BlockTransform(x = 0f, y = -110f, scale = 1f, fontScale = 1f) }
    val defaultMetrics = remember { BlockTransform(x = 0f, y = -72f, scale = 1f, fontScale = 1f) }
    val defaultRoute = remember { BlockTransform(x = 0f, y = -5f, scale = 1f, fontScale = 1f) }
    val defaultShoe = remember { BlockTransform(x = 0f, y = 68f, scale = 1f, fontScale = 1f) }
    val defaultProgress = remember { BlockTransform(x = 0f, y = 102f, scale = 1f, fontScale = 1f) }
    val defaultQuote = remember { BlockTransform(x = 0f, y = 138f, scale = 1f, fontScale = 1f) }

    var headerTransform by remember { mutableStateOf(ShareCardPrefs.loadTransform(context, "header", defaultHeader)) }
    var heroTransform by remember { mutableStateOf(ShareCardPrefs.loadTransform(context, "hero", defaultHero)) }
    var metricsTransform by remember { mutableStateOf(ShareCardPrefs.loadTransform(context, "metrics", defaultMetrics)) }
    var routeTransform by remember { mutableStateOf(ShareCardPrefs.loadTransform(context, "route", defaultRoute)) }
    var shoeTransform by remember { mutableStateOf(ShareCardPrefs.loadTransform(context, "shoe", defaultShoe)) }
    var progressTransform by remember { mutableStateOf(ShareCardPrefs.loadTransform(context, "progress", defaultProgress)) }
    var quoteTransform by remember { mutableStateOf(ShareCardPrefs.loadTransform(context, "quote", defaultQuote)) }

    // Text colors per block
    var headerTextColor by remember { mutableStateOf(Color(ShareCardPrefs.loadTextColor(context, "header", AndroidColor.WHITE))) }
    var heroTextColor by remember { mutableStateOf(Color(ShareCardPrefs.loadTextColor(context, "hero", AndroidColor.WHITE))) }
    var metricsTextColor by remember { mutableStateOf(Color(ShareCardPrefs.loadTextColor(context, "metrics", AndroidColor.WHITE))) }
    var routeTextColor by remember { mutableStateOf(Color(ShareCardPrefs.loadTextColor(context, "route", AndroidColor.WHITE))) }
    var shoeTextColor by remember { mutableStateOf(Color(ShareCardPrefs.loadTextColor(context, "shoe", AndroidColor.WHITE))) }
    var progressTextColor by remember { mutableStateOf(Color(ShareCardPrefs.loadTextColor(context, "progress", AndroidColor.WHITE))) }
    var quoteTextColor by remember { mutableStateOf(Color(ShareCardPrefs.loadTextColor(context, "quote", AndroidColor.WHITE))) }

    fun getBlockTextColor(blockId: String): Color = when (blockId) {
        "header" -> headerTextColor
        "hero" -> heroTextColor
        "metrics" -> metricsTextColor
        "route" -> routeTextColor
        "shoe" -> shoeTextColor
        "progress" -> progressTextColor
        "quote" -> quoteTextColor
        else -> Color.White
    }

    fun updateSelectedBlockTextColor(color: Color) {
        when (selectedBlockId) {
            "header" -> { headerTextColor = color; ShareCardPrefs.saveTextColor(context, "header", color.toArgb()) }
            "hero" -> { heroTextColor = color; ShareCardPrefs.saveTextColor(context, "hero", color.toArgb()) }
            "metrics" -> { metricsTextColor = color; ShareCardPrefs.saveTextColor(context, "metrics", color.toArgb()) }
            "route" -> { routeTextColor = color; ShareCardPrefs.saveTextColor(context, "route", color.toArgb()) }
            "shoe" -> { shoeTextColor = color; ShareCardPrefs.saveTextColor(context, "shoe", color.toArgb()) }
            "progress" -> { progressTextColor = color; ShareCardPrefs.saveTextColor(context, "progress", color.toArgb()) }
            "quote" -> { quoteTextColor = color; ShareCardPrefs.saveTextColor(context, "quote", color.toArgb()) }
        }
        AudioSynth.playTickSound()
    }

    var blockVisibility by remember {
        mutableStateOf(
            mapOf(
                "block_header_visible" to ShareCardPrefs.loadVisibility(context, "block_header_visible", true),
                "block_hero_visible" to ShareCardPrefs.loadVisibility(context, "block_hero_visible", true),
                "block_metrics_visible" to ShareCardPrefs.loadVisibility(context, "block_metrics_visible", true),
                "block_route_visible" to ShareCardPrefs.loadVisibility(context, "block_route_visible", true),
                "block_shoe_visible" to ShareCardPrefs.loadVisibility(context, "block_shoe_visible", true),
                "block_progress_visible" to ShareCardPrefs.loadVisibility(context, "block_progress_visible", true),
                "block_quote_visible" to ShareCardPrefs.loadVisibility(context, "block_quote_visible", true)
            )
        )
    }

    fun isBlockVisible(blockKey: String): Boolean = blockVisibility[blockKey] != false

    fun updateHeaderVisibility(visible: Boolean) {
        blockVisibility = blockVisibility.toMutableMap().apply { put("block_header_visible", visible) }
        ShareCardPrefs.saveVisibility(context, "block_header_visible", visible)
        AudioSynth.playTickSound()
    }

    fun updateHeroVisibility(visible: Boolean) {
        blockVisibility = blockVisibility.toMutableMap().apply { put("block_hero_visible", visible) }
        ShareCardPrefs.saveVisibility(context, "block_hero_visible", visible)
        AudioSynth.playTickSound()
    }

    fun updateMetricsVisibility(visible: Boolean) {
        blockVisibility = blockVisibility.toMutableMap().apply { put("block_metrics_visible", visible) }
        ShareCardPrefs.saveVisibility(context, "block_metrics_visible", visible)
        AudioSynth.playTickSound()
    }

    fun updateRouteVisibility(visible: Boolean) {
        blockVisibility = blockVisibility.toMutableMap().apply { put("block_route_visible", visible) }
        ShareCardPrefs.saveVisibility(context, "block_route_visible", visible)
        AudioSynth.playTickSound()
    }

    fun updateShoeVisibility(visible: Boolean) {
        blockVisibility = blockVisibility.toMutableMap().apply { put("block_shoe_visible", visible) }
        ShareCardPrefs.saveVisibility(context, "block_shoe_visible", visible)
        AudioSynth.playTickSound()
    }

    fun updateProgressVisibility(visible: Boolean) {
        blockVisibility = blockVisibility.toMutableMap().apply { put("block_progress_visible", visible) }
        ShareCardPrefs.saveVisibility(context, "block_progress_visible", visible)
        AudioSynth.playTickSound()
    }

    fun updateQuoteVisibility(visible: Boolean) {
        blockVisibility = blockVisibility.toMutableMap().apply { put("block_quote_visible", visible) }
        ShareCardPrefs.saveVisibility(context, "block_quote_visible", visible)
        AudioSynth.playTickSound()
    }

    fun updateHeader(t: BlockTransform) { headerTransform = t; ShareCardPrefs.saveTransform(context, "header", t) }
    fun updateHero(t: BlockTransform) { heroTransform = t; ShareCardPrefs.saveTransform(context, "hero", t) }
    fun updateMetrics(t: BlockTransform) { metricsTransform = t; ShareCardPrefs.saveTransform(context, "metrics", t) }
    fun updateRoute(t: BlockTransform) { routeTransform = t; ShareCardPrefs.saveTransform(context, "route", t) }
    fun updateShoe(t: BlockTransform) { shoeTransform = t; ShareCardPrefs.saveTransform(context, "shoe", t) }
    fun updateProgress(t: BlockTransform) { progressTransform = t; ShareCardPrefs.saveTransform(context, "progress", t) }
    fun updateQuote(t: BlockTransform) { quoteTransform = t; ShareCardPrefs.saveTransform(context, "quote", t) }

    fun updateHeaderFontScale(newScale: Float) {
        val updated = headerTransform.copy(fontScale = newScale)
        headerTransform = updated
        ShareCardPrefs.saveTransform(context, "header", updated)
        ShareCardPrefs.saveFontScale(context, "header", newScale)
        AudioSynth.playTickSound()
    }

    fun updateHeroFontScale(newScale: Float) {
        val updated = heroTransform.copy(fontScale = newScale)
        heroTransform = updated
        ShareCardPrefs.saveTransform(context, "hero", updated)
        ShareCardPrefs.saveFontScale(context, "hero", newScale)
        AudioSynth.playTickSound()
    }

    fun updateMetricsFontScale(newScale: Float) {
        val updated = metricsTransform.copy(fontScale = newScale)
        metricsTransform = updated
        ShareCardPrefs.saveTransform(context, "metrics", updated)
        ShareCardPrefs.saveFontScale(context, "metrics", newScale)
        AudioSynth.playTickSound()
    }

    fun updateRouteFontScale(newScale: Float) {
        val updated = routeTransform.copy(fontScale = newScale)
        routeTransform = updated
        ShareCardPrefs.saveTransform(context, "route", updated)
        ShareCardPrefs.saveFontScale(context, "route", newScale)
        AudioSynth.playTickSound()
    }

    fun updateShoeFontScale(newScale: Float) {
        val updated = shoeTransform.copy(fontScale = newScale)
        shoeTransform = updated
        ShareCardPrefs.saveTransform(context, "shoe", updated)
        ShareCardPrefs.saveFontScale(context, "shoe", newScale)
        AudioSynth.playTickSound()
    }

    fun updateProgressFontScale(newScale: Float) {
        val updated = progressTransform.copy(fontScale = newScale)
        progressTransform = updated
        ShareCardPrefs.saveTransform(context, "progress", updated)
        ShareCardPrefs.saveFontScale(context, "progress", newScale)
        AudioSynth.playTickSound()
    }

    fun updateQuoteFontScale(newScale: Float) {
        val updated = quoteTransform.copy(fontScale = newScale)
        quoteTransform = updated
        ShareCardPrefs.saveTransform(context, "quote", updated)
        ShareCardPrefs.saveFontScale(context, "quote", newScale)
        AudioSynth.playTickSound()
    }

    fun adjustSelectedFontScale(delta: Float) {
        val currentScale = when (selectedBlockId) {
            "header" -> headerTransform.fontScale
            "hero" -> heroTransform.fontScale
            "metrics" -> metricsTransform.fontScale
            "route" -> routeTransform.fontScale
            "shoe" -> shoeTransform.fontScale
            "progress" -> progressTransform.fontScale
            "quote" -> quoteTransform.fontScale
            else -> 1.0f
        }
        val newScale = (currentScale + delta).coerceIn(0.6f, 2.5f)
        when (selectedBlockId) {
            "header" -> updateHeaderFontScale(newScale)
            "hero" -> updateHeroFontScale(newScale)
            "metrics" -> updateMetricsFontScale(newScale)
            "route" -> updateRouteFontScale(newScale)
            "shoe" -> updateShoeFontScale(newScale)
            "progress" -> updateProgressFontScale(newScale)
            "quote" -> updateQuoteFontScale(newScale)
        }
    }

    val selectedBlockName = when (selectedBlockId) {
        "header" -> "Topo"
        "hero" -> "Resumo"
        "metrics" -> "Pace"
        "route" -> "Rota"
        "shoe" -> "Tenis"
        "progress" -> "Semana"
        "quote" -> "Frase"
        else -> "Bloco"
    }

    fun resetPositions() {
        headerTransform = defaultHeader.copy(fontScale = 1f)
        heroTransform = defaultHero.copy(fontScale = 1f)
        metricsTransform = defaultMetrics.copy(fontScale = 1f)
        routeTransform = defaultRoute.copy(fontScale = 1f)
        shoeTransform = defaultShoe.copy(fontScale = 1f)
        progressTransform = defaultProgress.copy(fontScale = 1f)
        quoteTransform = defaultQuote.copy(fontScale = 1f)
        headerTextColor = Color.White
        heroTextColor = Color.White
        metricsTextColor = Color.White
        routeTextColor = Color.White
        shoeTextColor = Color.White
        progressTextColor = Color.White
        quoteTextColor = Color.White
        blockVisibility = mapOf(
            "block_header_visible" to true,
            "block_hero_visible" to true,
            "block_metrics_visible" to true,
            "block_route_visible" to true,
            "block_shoe_visible" to true,
            "block_progress_visible" to true,
            "block_quote_visible" to true
        )
        ShareCardPrefs.clear(context)
        AudioSynth.playTickSound()
    }

    val ptBrLocale = remember { Locale("pt", "BR") }
    val distKm = session.distanceMeters / 1000f
    val mins = session.durationSec / 60
    val secs = session.durationSec % 60
    val timeStr = String.format(Locale.US, "%02d:%02d", mins, secs)

    val hasIsolatedMainRun = (session.mainRunDurationSec ?: 0) > 0 && (session.mainRunDistanceMeters ?: 0) > 0
    val effectiveMainRunDuration = if (hasIsolatedMainRun) session.mainRunDurationSec!! else session.durationSec
    val effectiveMainRunDist = if (hasIsolatedMainRun) session.mainRunDistanceMeters!! else session.distanceMeters

    val paceSec = if (effectiveMainRunDist > 0) kotlin.math.round((effectiveMainRunDuration.toDouble() * 1000.0) / effectiveMainRunDist.toDouble()).toInt() else 0
    val pMin = paceSec / 60
    val pSec = paceSec % 60
    val paceStr = if (paceSec > 0) String.format(Locale.US, "%d'%02d\"", pMin, pSec) else "--:--"

    val sprintAnalytics = remember(session, gpsPoints) {
        calculateSprintAnalytics(session, gpsPoints)
    }
    val physiologyDossier = remember(session, gpsPoints) {
        com.example.data.model.buildRunPhysiologyDossier(session, null, gpsPoints)
    }
    val sprintPaceFormatted = sprintAnalytics.avgSprintPaceText
    val routePointItems = remember(session, gpsPoints) {
        resolveSessionRoutePointsWithPhases(session, gpsPoints)
    }
    val workoutStructureSummary = sprintAnalytics.structureSummary

    val shoeNameText = remember(session.shoeName, runningShoes) {
        val raw = session.shoeName?.trim()
        if (raw.isNullOrBlank()) {
            "Nenhum"
        } else {
            val matched = runningShoes.firstOrNull { viewModel.shoeMatches(it, raw) }
            if (matched != null) {
                String.format(Locale.US, "%s (%.1f km)", matched.fullDisplayName(), matched.totalDistanceKm)
            } else {
                raw
            }
        }
    }

    @Composable
    fun CardPreviewArea(modifier: Modifier = Modifier) {
        BoxWithConstraints(
            modifier = modifier
                .aspectRatio(9f / 16f)
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFF0A0A0F))
                .border(1.5.dp, Color(0xFF32323D), RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.TopStart
        ) {
            val previewScale = (constraints.maxWidth.toFloat() / 1080f).coerceAtLeast(0.01f)

            CompositionLocalProvider(LocalDensity provides Density(density = previewScale, fontScale = 1f)) {
                Box(
                    modifier = Modifier.fillMaxSize()
                ) {
                    val bgModifier = when (bgScaleMode) {
                        1 -> Modifier.fillMaxSize().graphicsLayer { scaleX = 0.88f; scaleY = 0.88f }
                        2 -> Modifier.fillMaxSize().graphicsLayer { scaleX = 1.15f; scaleY = 1.15f }
                        else -> Modifier.fillMaxSize()
                    }
                    val currentScale = if (bgScaleMode == 1) ContentScale.Fit else ContentScale.Crop

                    if (selectedPhotoUri != null) {
                        AsyncImage(
                            model = selectedPhotoUri,
                            contentDescription = "Foto da Galeria",
                            contentScale = currentScale,
                            modifier = bgModifier
                        )
                    } else if (selectedTheme != null) {
                        ProceduralBackgroundComposable(
                            theme = selectedTheme!!,
                            modifier = bgModifier
                        )
                    } else if (selectedTemplateResId != null) {
                        AsyncImage(
                            model = selectedTemplateResId!!,
                            contentDescription = "Template Selecionado",
                            contentScale = currentScale,
                            modifier = bgModifier
                        )
                    } else {
                        Box(
                            modifier = bgModifier
                                .background(
                                    Brush.radialGradient(
                                        colors = listOf(
                                            RunColors.brand.copy(alpha = 0.55f),
                                            Color(0xFF141416).copy(alpha = 0.85f),
                                            Color(0xFF141416)
                                        ),
                                        radius = 900f
                                    )
                                )
                        )
                    }

                    // Dark layer equal to export: black with alpha 102/255 (0.40f)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 102f / 255f))
                    )

                    // 0. Header Block
                    if (isBlockVisible("block_header_visible")) {
                        DraggableCardBlock(
                            blockId = "header",
                            selectedBlockId = selectedBlockId,
                            onSelect = { selectedBlockId = it },
                            transform = headerTransform,
                            previewScale = previewScale,
                            onTransformChange = { updateHeader(it) }
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "MAX PERFORMANCE",
                                    color = getBlockTextColor("header"),
                                    fontSize = (32f * headerTransform.fontScale).sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false,
                                    style = TextStyle(
                                        fontFeatureSettings = "tnum",
                                        shadow = cardTextShadow
                                    )
                                )
                                val headerSubtitleText = if (session.isClassifiedAsRace()) {
                                    val displayTitle = com.example.engine.RunningProgramManager.getDisplayTitleForSession(session).uppercase(Locale("pt", "BR"))
                                    if (displayTitle.startsWith("PROVA")) "$displayTitle • MAX RUN" else "PROVA • $displayTitle"
                                } else if (com.example.engine.RunningProgramManager.isEffectiveFreeRun(session)) {
                                    "CORRIDA LIVRE • MAX RUN"
                                } else {
                                    "SEMANA ${session.weekNumber} • DIA ${session.dayNumber} • ${if (session.isGentleMode) "MODO LEVE" else "COUCH TO 5K"}"
                                }
                                Text(
                                    text = headerSubtitleText,
                                    color = getBlockTextColor("header").copy(alpha = 0.9f),
                                    fontSize = (15f * headerTransform.fontScale).sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.06.em,
                                    maxLines = 1,
                                    softWrap = false,
                                    style = TextStyle(
                                        fontFeatureSettings = "tnum",
                                        shadow = cardTextShadow
                                    ),
                                    modifier = Modifier.widthIn(max = 1000.dp)
                                )
                            }
                        }
                    }

                    // 1. Hero Block
                    if (isBlockVisible("block_hero_visible")) {
                        DraggableCardBlock(
                            blockId = "hero",
                            selectedBlockId = selectedBlockId,
                            onSelect = { selectedBlockId = it },
                            transform = heroTransform,
                            previewScale = previewScale,
                            onTransformChange = { updateHero(it) }
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(max = 1000.dp)) {
                                Text(
                                    text = String.format(ptBrLocale, "%.2f KM   •   %s MIN", distKm, timeStr),
                                    color = getBlockTextColor("hero"),
                                    fontSize = (38f * heroTransform.fontScale).sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false,
                                    style = TextStyle(
                                        fontFeatureSettings = "tnum",
                                        shadow = cardTextShadow
                                    )
                                )
                                Text(
                                    text = workoutStructureSummary.uppercase(ptBrLocale),
                                    color = getBlockTextColor("hero").copy(alpha = 0.9f),
                                    fontSize = (15f * heroTransform.fontScale).sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.06.em,
                                    maxLines = 1,
                                    softWrap = false,
                                    style = TextStyle(
                                        fontFeatureSettings = "tnum",
                                        shadow = cardTextShadow
                                    )
                                )
                            }
                        }
                    }

                    // 2. Metrics Block
                    if (isBlockVisible("block_metrics_visible")) {
                        DraggableCardBlock(
                            blockId = "metrics",
                            selectedBlockId = selectedBlockId,
                            onSelect = { selectedBlockId = it },
                            transform = metricsTransform,
                            previewScale = previewScale,
                            onTransformChange = { updateMetrics(it) }
                        ) {
                            val hrText = if (session.avgHr != null && session.avgHr > 0) {
                                "   •   ${session.avgHr} BPM"
                            } else ""

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.widthIn(max = 1000.dp)
                            ) {
                                Text(
                                    text = "PACE $paceStr   •   TIROS $sprintPaceFormatted$hrText",
                                    color = getBlockTextColor("metrics"),
                                    fontSize = (28f * metricsTransform.fontScale).sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false,
                                    style = TextStyle(
                                        fontFeatureSettings = "tnum",
                                        shadow = cardTextShadow
                                    )
                                )
                                val cadStr = if (physiologyDossier.avgCadenceSpm > 0) "${physiologyDossier.avgCadenceSpm}/${if (physiologyDossier.maxCadenceSpm > 0) physiologyDossier.maxCadenceSpm else "--"} PPM" else "--"
                                val strideStr = if (physiologyDossier.avgStrideLengthCm > 0) "${physiologyDossier.avgStrideLengthCm}/${if (physiologyDossier.maxStrideLengthCm > 0) physiologyDossier.maxStrideLengthCm else "--"} CM" else "--"
                                val kcalStr = if (physiologyDossier.isWeightMissingForCalories) "-- KCAL" else "${physiologyDossier.totalCaloriesKcal} KCAL"
                                val vitStr = if (physiologyDossier.vitalityScoreGain > 0) "+${physiologyDossier.vitalityScoreGain} VITALIDADE" else "--"
                                Text(
                                    text = "CAD $cadStr • PASSADA $strideStr • $kcalStr • $vitStr",
                                    color = getBlockTextColor("metrics").copy(alpha = 0.92f),
                                    fontSize = (13.5f * metricsTransform.fontScale).sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false,
                                    style = TextStyle(
                                        fontFeatureSettings = "tnum",
                                        shadow = cardTextShadow
                                    )
                                )
                            }
                        }
                    }

                    // 3. Route Block (Pintado por Sprints + Trecho PR Dourado)
                    if (isBlockVisible("block_route_visible")) {
                        DraggableCardBlock(
                            blockId = "route",
                            selectedBlockId = selectedBlockId,
                            onSelect = { selectedBlockId = it },
                            transform = routeTransform,
                            previewScale = previewScale,
                            onTransformChange = { updateRoute(it) }
                        ) {
                            Box(
                                modifier = Modifier
                                    .widthIn(max = 1000.dp)
                                    .width((360f * routeTransform.fontScale).dp)
                                    .height((220f * routeTransform.fontScale).dp)
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(Color.Black.copy(alpha = 110f / 255f))
                                    .border(2.5.dp, getBlockTextColor("route").copy(alpha = 0.7f), RoundedCornerShape(24.dp))
                                    .padding(14.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val normPoints = getNormalizedRoutePoints(
                                        points = gpsPoints,
                                        targetWidth = size.width,
                                        targetHeight = size.height - (22f * routeTransform.fontScale),
                                        padding = 22f * routeTransform.fontScale,
                                        routePointItems = routePointItems,
                                        allowSimulationFallback = isUsingDemo || session.isSimulatedOrTestSession()
                                    )
                                    if (normPoints.size >= 2) {
                                        val dashEffect = PathEffect.dashPathEffect(floatArrayOf(12f * routeTransform.fontScale, 8f * routeTransform.fontScale), 0f)
                                        val borderExtraPx = 2.5.dp.toPx() * routeTransform.fontScale

                                        data class CardPhasePathGroup(
                                            val path: Path,
                                            val phase: String,
                                            val color: Color,
                                            val mainWidth: Float,
                                            val isWarmup: Boolean
                                        )

                                        val phaseGroups = ArrayList<CardPhasePathGroup>()
                                        var idx = 0
                                        while (idx < normPoints.size - 1) {
                                            val segPhase = normPoints[idx + 1].phase.uppercase(Locale.US)
                                            val segPath = Path().apply {
                                                moveTo(normPoints[idx].x, normPoints[idx].y)
                                            }
                                            var j = idx + 1
                                            while (j < normPoints.size && normPoints[j].phase.uppercase(Locale.US) == segPhase) {
                                                segPath.lineTo(normPoints[j].x, normPoints[j].y)
                                                j++
                                            }
                                            val segColor = when (segPhase) {
                                                "PR" -> com.example.ui.components.RunRouteLaneOffsetEngine.BestSprintColor
                                                "RUN" -> com.example.ui.components.RunRouteLaneOffsetEngine.SprintColor
                                                "WALK" -> com.example.ui.components.RunRouteLaneOffsetEngine.WalkColor
                                                else -> com.example.ui.components.RunRouteLaneOffsetEngine.WarmupColor
                                            }
                                            val isWarmup = segPhase == "WARMUP" || segPhase == "COOL_DOWN"
                                            val mainWidth = (if (segPhase == "PR") 7.0f else 5.5f) * routeTransform.fontScale
                                            phaseGroups.add(CardPhasePathGroup(segPath, segPhase, segColor, mainWidth, isWarmup))
                                            idx = j - 1
                                        }

                                        // Glow externo suave
                                        for (group in phaseGroups) {
                                            drawPath(
                                                path = group.path,
                                                color = group.color.copy(alpha = 0.25f),
                                                style = Stroke(
                                                    width = (if (group.phase == "PR") 15f else 11f) * routeTransform.fontScale,
                                                    cap = StrokeCap.Round,
                                                    join = StrokeJoin.Round
                                                )
                                            )
                                        }

                                        // 1ª PASSADA: Borda escura contínua (#0D0F14, alpha = 0.75f, strokeWidth + 2.5.dp)
                                        val fullBorderPath = Path().apply {
                                            moveTo(normPoints.first().x, normPoints.first().y)
                                            for (k in 1 until normPoints.size) {
                                                lineTo(normPoints[k].x, normPoints[k].y)
                                            }
                                        }
                                        drawPath(
                                            path = fullBorderPath,
                                            color = com.example.ui.components.RunRouteLaneOffsetEngine.ContrastBorderColor,
                                            style = Stroke(
                                                width = (5.5f * routeTransform.fontScale) + borderExtraPx,
                                                cap = StrokeCap.Round,
                                                join = StrokeJoin.Round
                                            )
                                        )
                                        for (group in phaseGroups) {
                                            if (group.phase == "PR") {
                                                drawPath(
                                                    path = group.path,
                                                    color = com.example.ui.components.RunRouteLaneOffsetEngine.ContrastBorderColor,
                                                    style = Stroke(
                                                        width = group.mainWidth + borderExtraPx,
                                                        cap = StrokeCap.Round,
                                                        join = StrokeJoin.Round
                                                    )
                                                )
                                            }
                                        }

                                        // 2ª PASSADA: Sub-Paths coloridos contínuos por cima
                                        for (group in phaseGroups) {
                                            drawPath(
                                                path = group.path,
                                                color = group.color,
                                                style = Stroke(
                                                    width = group.mainWidth,
                                                    cap = StrokeCap.Round,
                                                    join = StrokeJoin.Round,
                                                    pathEffect = if (group.isWarmup) dashEffect else null
                                                )
                                            )
                                        }
                                        val start = normPoints.first()
                                        drawCircle(com.example.ui.components.RunRouteLaneOffsetEngine.SprintColor, radius = 7f * routeTransform.fontScale, center = Offset(start.x, start.y))
                                        val end = normPoints.last()
                                        drawCircle(com.example.ui.components.RunRouteLaneOffsetEngine.WalkColor, radius = 7f * routeTransform.fontScale, center = Offset(end.x, end.y))
                                    }
                                }
                                Text(
                                    text = "● Caminhada (Azul)   ● Corrida (Verde)   ★ Melhor Sprint (PR)",
                                    color = getBlockTextColor("route"),
                                    fontSize = (11f * routeTransform.fontScale).sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(bottom = 4.dp),
                                    style = TextStyle(
                                        fontFeatureSettings = "tnum",
                                        shadow = cardTextShadow
                                    )
                                )
                            }
                        }
                    }

                    // 4. Shoe Block
                    if (isBlockVisible("block_shoe_visible")) {
                        DraggableCardBlock(
                            blockId = "shoe",
                            selectedBlockId = selectedBlockId,
                            onSelect = { selectedBlockId = it },
                            transform = shoeTransform,
                            previewScale = previewScale,
                            onTransformChange = { updateShoe(it) }
                        ) {
                            Text(
                                text = "TENIS: $shoeNameText",
                                color = getBlockTextColor("shoe"),
                                fontSize = (28f * shoeTransform.fontScale).sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false,
                                style = TextStyle(
                                    fontFeatureSettings = "tnum",
                                    shadow = cardTextShadow
                                ),
                                modifier = Modifier.widthIn(max = 1000.dp)
                            )
                        }
                    }

                    // 5. Progress Block
                    if (isBlockVisible("block_progress_visible")) {
                        DraggableCardBlock(
                            blockId = "progress",
                            selectedBlockId = selectedBlockId,
                            onSelect = { selectedBlockId = it },
                            transform = progressTransform,
                            previewScale = previewScale,
                            onTransformChange = { updateProgress(it) }
                        ) {
                            val progressLabelText = if (session.isClassifiedAsRace()) {
                                "PROVA • ${com.example.engine.RunningProgramManager.getDisplayTitleForSession(session).uppercase(Locale("pt", "BR"))}   •   $workoutStructureSummary"
                            } else if (com.example.engine.RunningProgramManager.isEffectiveFreeRun(session)) {
                                "CORRIDA LIVRE   •   $workoutStructureSummary"
                            } else {
                                "SEMANA ${session.weekNumber} • DIA ${session.dayNumber}   •   $workoutStructureSummary"
                            }
                            Text(
                                text = progressLabelText,
                                color = getBlockTextColor("progress"),
                                fontSize = (24f * progressTransform.fontScale).sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false,
                                style = TextStyle(
                                    fontFeatureSettings = "tnum",
                                    shadow = cardTextShadow
                                ),
                                modifier = Modifier.widthIn(max = 1000.dp)
                            )
                        }
                    }

                    // 6. Quote Block
                    if (isBlockVisible("block_quote_visible")) {
                        DraggableCardBlock(
                            blockId = "quote",
                            selectedBlockId = selectedBlockId,
                            onSelect = { selectedBlockId = it },
                            transform = quoteTransform,
                            previewScale = previewScale,
                            onTransformChange = { updateQuote(it) }
                        ) {
                            val quoteTextToDisplay = if (customQuote.isNotBlank()) "\"$customQuote\"" else "\"Sua frase motivacional\""
                            Text(
                                text = quoteTextToDisplay,
                                color = getBlockTextColor("quote"),
                                fontSize = (28f * quoteTransform.fontScale).sp,
                                fontWeight = FontWeight.Bold,
                                fontStyle = FontStyle.Italic,
                                maxLines = 1,
                                softWrap = false,
                                style = TextStyle(
                                    fontFeatureSettings = "tnum",
                                    shadow = cardTextShadow
                                ),
                                modifier = Modifier.widthIn(max = 1000.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (isFullscreen) {
        // FULLSCREEN EDIT MODE
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0A0A0F))
                .systemBarsPadding()
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 1. Top Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.Black.copy(alpha = 0.85f))
                        .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(14.dp))
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { isFullscreen = false; AudioSynth.playTickSound() },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Sair Tela Cheia",
                            tint = Color.White
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val bgModeLabel = when (bgScaleMode) {
                            0 -> "Preencher"
                            1 -> "Sem Corte"
                            else -> "Zoom 115%"
                        }
                        Button(
                            onClick = {
                                bgScaleMode = (bgScaleMode + 1) % 3
                                AudioSynth.playTickSound()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (bgScaleMode != 0) Color(0xFFFFEA00) else Color(0xFF2C2C38)
                            ),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text(
                                text = bgModeLabel,
                                color = if (bgScaleMode != 0) Color.Black else Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }

                        Button(
                            onClick = {
                                exportCardAsPdf(
                                    context = context,
                                    session = session,
                                    photoUri = selectedPhotoUri,
                                    proceduralTheme = selectedTheme,
                                    templateResId = selectedTemplateResId,
                                    bgScaleMode = bgScaleMode,
                                    headerTransform = headerTransform,
                                    heroTransform = heroTransform,
                                    metricsTransform = metricsTransform,
                                    routeTransform = routeTransform,
                                    shoeTransform = shoeTransform,
                                    progressTransform = progressTransform,
                                    quoteTransform = quoteTransform,
                                    customQuoteText = customQuote,
                                    blockVisibility = blockVisibility,
                                    gpsPoints = gpsPoints,
                                    headerColor = headerTextColor.toArgb(),
                                    heroColor = heroTextColor.toArgb(),
                                    metricsColor = metricsTextColor.toArgb(),
                                    routeColor = routeTextColor.toArgb(),
                                    shoeColor = shoeTextColor.toArgb(),
                                    progressColor = progressTextColor.toArgb(),
                                    quoteColor = quoteTextColor.toArgb()
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C2C38)),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(imageVector = Icons.Default.PictureAsPdf, contentDescription = null, tint = Color(0xFFFFEA00), modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text("PDF", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                shareCardAsBitmap(
                                    context = context,
                                    session = session,
                                    photoUri = selectedPhotoUri,
                                    proceduralTheme = selectedTheme,
                                    templateResId = selectedTemplateResId,
                                    bgScaleMode = bgScaleMode,
                                    headerTransform = headerTransform,
                                    heroTransform = heroTransform,
                                    metricsTransform = metricsTransform,
                                    routeTransform = routeTransform,
                                    shoeTransform = shoeTransform,
                                    progressTransform = progressTransform,
                                    quoteTransform = quoteTransform,
                                    customQuoteText = customQuote,
                                    blockVisibility = blockVisibility,
                                    gpsPoints = gpsPoints,
                                    headerColor = headerTextColor.toArgb(),
                                    heroColor = heroTextColor.toArgb(),
                                    metricsColor = metricsTextColor.toArgb(),
                                    routeColor = routeTextColor.toArgb(),
                                    shoeColor = shoeTextColor.toArgb(),
                                    progressColor = progressTextColor.toArgb(),
                                    quoteColor = quoteTextColor.toArgb()
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = RunColors.brand),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Share, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text("Share", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // 2. Card Preview in Middle
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CardPreviewArea(
                        modifier = Modifier.fillMaxHeight()
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // 3. Bottom Bar with A-, Block Name, A+, and Color Selector
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    color = Color.Black.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { resetPositions() },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Resetar",
                                tint = Color(0xFFFFEA00),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Button(
                            onClick = { adjustSelectedFontScale(-0.12f) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C2C38)),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("A-", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.White.copy(alpha = 0.1f))
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = selectedBlockName,
                                color = Color(0xFFFFEA00),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }

                        Button(
                            onClick = { adjustSelectedFontScale(0.12f) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C2C38)),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("A+", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        // Quick Text Color Selector (4 options: White, Neon Yellow, Tech Cyan, Dark Graphite)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val colorOptions = listOf(
                                Color.White to "Branco",
                                Color(0xFFFFEA00) to "Amarelo",
                                Color(0xFF00E5FF) to "Ciano",
                                Color(0xFF0F0F12) to "Grafite"
                            )
                            val activeColor = getBlockTextColor(selectedBlockId)

                            for ((col, desc) in colorOptions) {
                                val isSelectedColor = activeColor == col
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .background(col)
                                        .border(
                                            width = if (isSelectedColor) 2.dp else 1.dp,
                                            color = if (isSelectedColor) Color.White else Color.Gray,
                                            shape = CircleShape
                                        )
                                        .clickable { updateSelectedBlockTextColor(col) }
                                )
                            }
                        }

                        TextButton(
                            onClick = {
                                selectedBlockId = "quote"
                                showQuoteEditDialog = true
                            },
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            Text("Frase", color = Color(0xFF69F0AE), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    } else {
        // STANDARD EDIT SCREEN LAYOUT
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF141416))
                .statusBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    IconButton(
                        onClick = {
                            viewModel.popBackScreen(fallback = AppScreen.RUNNING_DETAIL)
                            AudioSynth.playTickSound()
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.08f))
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Voltar",
                            tint = Color.White
                        )
                    }

                    Text(
                        text = "Personalizar Share Card",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val bgModeLabel = when (bgScaleMode) {
                        0 -> "Preencher"
                        1 -> "Sem Corte"
                        else -> "Zoom 115%"
                    }
                    val bgModeIcon = when (bgScaleMode) {
                        0 -> Icons.Default.Crop
                        1 -> Icons.Default.FitScreen
                        else -> Icons.Default.AspectRatio
                    }
                    IconButton(
                        onClick = {
                            bgScaleMode = (bgScaleMode + 1) % 3
                            AudioSynth.playTickSound()
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(if (bgScaleMode != 0) Color(0xFFFFEA00).copy(alpha = 0.2f) else Color.White.copy(alpha = 0.08f))
                    ) {
                        Icon(
                            imageVector = bgModeIcon,
                            contentDescription = bgModeLabel,
                            tint = if (bgScaleMode != 0) Color(0xFFFFEA00) else Color.White
                        )
                    }

                    IconButton(
                        onClick = {
                            isFullscreen = true
                            AudioSynth.playTickSound()
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.08f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Fullscreen,
                            contentDescription = "Modo Tela Cheia",
                            tint = Color.White
                        )
                    }

                    IconButton(
                        onClick = { resetPositions() },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.08f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Resetar Posições",
                            tint = Color(0xFFFFEA00)
                        )
                    }
                }
            }

            Text(
                text = "Arraste os blocos, toque para selecionar e ajuste fonte/cor abaixo.",
                color = OnSurfaceDim,
                fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                CardPreviewArea(
                    modifier = Modifier.fillMaxHeight()
                )
            }

            // Elementos do Card (Visibilidade Ligar/Desligar)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "Elementos do Card:",
                    color = Color(0xFF9E9EA8),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 0.dp)
                ) {
                    val itemsList = listOf(
                        Triple("Topo", "block_header_visible", { v: Boolean -> updateHeaderVisibility(v) }),
                        Triple("Resumo", "block_hero_visible", { v: Boolean -> updateHeroVisibility(v) }),
                        Triple("Pace", "block_metrics_visible", { v: Boolean -> updateMetricsVisibility(v) }),
                        Triple("Rota", "block_route_visible", { v: Boolean -> updateRouteVisibility(v) }),
                        Triple("Tênis", "block_shoe_visible", { v: Boolean -> updateShoeVisibility(v) }),
                        Triple("Semana", "block_progress_visible", { v: Boolean -> updateProgressVisibility(v) }),
                        Triple("Frase", "block_quote_visible", { v: Boolean -> updateQuoteVisibility(v) })
                    )
                    items(itemsList.size) { index ->
                        val (label, key, setter) = itemsList[index]
                        val visible = isBlockVisible(key)
                        Surface(
                            onClick = { setter(!visible) },
                            color = if (visible) Color(0xFF2C2C38) else Color(0xFF16161C),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, if (visible) Color(0xFFFFEA00).copy(alpha = 0.5f) else Color.White.copy(alpha = 0.1f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = if (visible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = null,
                                    tint = if (visible) Color(0xFFFFEA00) else Color.Gray,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = label,
                                    color = if (visible) Color.White else Color.Gray,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // LazyRow Seletor de Templates
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Text(
                    text = "Fundo do Card:",
                    color = Color(0xFF9E9EA8),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                )

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    item {
                        val isCustomPhotoSelected = selectedPhotoUri != null
                        Box(
                            modifier = Modifier
                                .size(56.dp, 72.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF26262E))
                                .border(
                                    width = 2.dp,
                                    color = if (isCustomPhotoSelected) Color(0xFFFFEA00) else Color(0xFF383842),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable {
                                    photoPickerLauncher.launch(
                                        androidx.activity.result.PickVisualMediaRequest(
                                            ActivityResultContracts.PickVisualMedia.ImageOnly
                                        )
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.PhotoLibrary,
                                    contentDescription = "Galeria",
                                    tint = if (isCustomPhotoSelected) Color(0xFFFFEA00) else Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Fotos",
                                    color = if (isCustomPhotoSelected) Color(0xFFFFEA00) else Color(0xFFAAAAAF),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    items(templateDrawables.size) { index ->
                        val resId = templateDrawables[index]
                        val isSelected = selectedTemplateResId == resId && selectedPhotoUri == null && selectedTheme == null
                        Box(
                            modifier = Modifier
                                .size(56.dp, 72.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF1E1E24))
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) Color(0xFFFFEA00) else Color(0xFF282832),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable {
                                    selectedTemplateResId = resId
                                    selectedPhotoUri = null
                                    selectedTheme = null
                                    AudioSynth.playTickSound()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = resId),
                                contentDescription = "Template ${index + 1}",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(11.dp))
                            )
                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.25f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFFFEA00)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = Color.Black,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Toolbar de Ações Inferior (Standard Screen)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                color = Color(0xFF1E1E24),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { adjustSelectedFontScale(-0.12f) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C2C38)),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text("A-", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.White.copy(alpha = 0.1f))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = selectedBlockName,
                            color = Color(0xFFFFEA00),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Button(
                        onClick = { adjustSelectedFontScale(0.12f) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C2C38)),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text("A+", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            exportCardAsPdf(
                                context = context,
                                session = session,
                                photoUri = selectedPhotoUri,
                                proceduralTheme = selectedTheme,
                                templateResId = selectedTemplateResId,
                                bgScaleMode = bgScaleMode,
                                headerTransform = headerTransform,
                                heroTransform = heroTransform,
                                metricsTransform = metricsTransform,
                                routeTransform = routeTransform,
                                shoeTransform = shoeTransform,
                                progressTransform = progressTransform,
                                quoteTransform = quoteTransform,
                                customQuoteText = customQuote,
                                blockVisibility = blockVisibility,
                                gpsPoints = gpsPoints,
                                headerColor = headerTextColor.toArgb(),
                                heroColor = heroTextColor.toArgb(),
                                metricsColor = metricsTextColor.toArgb(),
                                routeColor = routeTextColor.toArgb(),
                                shoeColor = shoeTextColor.toArgb(),
                                progressColor = progressTextColor.toArgb(),
                                quoteColor = quoteTextColor.toArgb()
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C2C38)),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(imageVector = Icons.Default.PictureAsPdf, contentDescription = null, tint = Color(0xFFFFEA00), modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("PDF", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            val bitmap = generateCardBitmap(
                                context = context,
                                session = session,
                                photoUri = selectedPhotoUri,
                                proceduralTheme = selectedTheme,
                                templateResId = selectedTemplateResId,
                                bgScaleMode = bgScaleMode,
                                headerTransform = headerTransform,
                                heroTransform = heroTransform,
                                metricsTransform = metricsTransform,
                                routeTransform = routeTransform,
                                shoeTransform = shoeTransform,
                                progressTransform = progressTransform,
                                quoteTransform = quoteTransform,
                                customQuoteText = customQuote,
                                blockVisibility = blockVisibility,
                                gpsPoints = gpsPoints,
                                headerColor = headerTextColor.toArgb(),
                                heroColor = heroTextColor.toArgb(),
                                metricsColor = metricsTextColor.toArgb(),
                                routeColor = routeTextColor.toArgb(),
                                shoeColor = shoeTextColor.toArgb(),
                                progressColor = progressTextColor.toArgb(),
                                quoteColor = quoteTextColor.toArgb()
                            )
                            saveCardToGallery(context, bitmap)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C2C38)),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(imageVector = Icons.Default.FileDownload, contentDescription = null, tint = Color(0xFFFFEA00), modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("PNG", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (showQuoteEditDialog) {
        AlertDialog(
            onDismissRequest = { showQuoteEditDialog = false },
            title = { Text("Editar Frase", color = Color.White) },
            text = {
                OutlinedTextField(
                    value = customQuote,
                    onValueChange = { customQuote = it },
                    label = { Text("Sua frase motivacional", color = Color.Gray) },
                    singleLine = false,
                    maxLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = RunColors.brand,
                        unfocusedBorderColor = Color.Gray
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showQuoteEditDialog = false
                        AudioSynth.playTickSound()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RunColors.brand)
                ) {
                    Text("SALVAR", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = Color(0xFF1E1E24)
        )
    }
}

@Composable
private fun ProceduralBackgroundComposable(
    theme: ProceduralCardTheme,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val nativeCanvas = drawContext.canvas.nativeCanvas
        drawProceduralBackgroundBitmap(nativeCanvas, size.width.toInt(), size.height.toInt(), theme)
    }
}

@Composable
private fun GpsNeonRoutePreviewComposable(
    points: List<GpsTrackPoint>,
    scale: Float,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.35f))
            .border(1.dp, textColor.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
            .padding(6.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val normPoints = getNormalizedRoutePoints(
                points = points,
                targetWidth = size.width,
                targetHeight = size.height,
                padding = 16f * scale
            )

            if (normPoints.size >= 2) {
                val path = Path().apply {
                    moveTo(normPoints.first().x, normPoints.first().y)
                    for (i in 1 until normPoints.size) {
                        lineTo(normPoints[i].x, normPoints[i].y)
                    }
                }

                drawPath(
                    path = path,
                    color = textColor.copy(alpha = 0.40f),
                    style = Stroke(
                        width = (8.dp * scale).toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )

                drawPath(
                    path = path,
                    brush = Brush.linearGradient(
                        colors = listOf(textColor, Color(0xFF00FF66), Color(0xFFFFEA00))
                    ),
                    style = Stroke(
                        width = (2.5.dp * scale).toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )

                val start = normPoints.first()
                drawCircle(
                    color = Color(0xFF00FF66).copy(alpha = 0.4f),
                    radius = (7.dp * scale).toPx(),
                    center = Offset(start.x, start.y)
                )
                drawCircle(
                    color = Color(0xFF00FF66),
                    radius = (3.5.dp * scale).toPx(),
                    center = Offset(start.x, start.y)
                )

                val end = normPoints.last()
                drawCircle(
                    color = Color(0xFF00E5FF).copy(alpha = 0.4f),
                    radius = (7.dp * scale).toPx(),
                    center = Offset(end.x, end.y)
                )
                drawCircle(
                    color = Color(0xFF00E5FF),
                    radius = (3.5.dp * scale).toPx(),
                    center = Offset(end.x, end.y)
                )
            }
        }

        Text(
            text = if (points.size >= 2) "ROTA GPS NEON" else "ROTA DA SESSÃO",
            color = textColor,
            fontSize = (8.sp * scale).value.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 1.dp),
            style = TextStyle(
                fontFeatureSettings = "tnum",
                shadow = cardTextShadow
            )
        )
    }
}

@Composable
private fun DraggableCardBlock(
    blockId: String,
    selectedBlockId: String,
    onSelect: (String) -> Unit,
    transform: BlockTransform,
    previewScale: Float = 1f,
    onTransformChange: (BlockTransform) -> Unit,
    content: @Composable () -> Unit
) {
    val currentTransform by rememberUpdatedState(transform)
    val currentOnTransformChange by rememberUpdatedState(onTransformChange)
    val currentOnSelect by rememberUpdatedState(onSelect)
    val isSelected = blockId == selectedBlockId
    val density = LocalDensity.current.density

    val translationXPx = currentTransform.x * POSITION_SCALE_FACTOR
    val translationYPx = currentTransform.y * POSITION_SCALE_FACTOR

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .wrapContentSize(Alignment.Center, unbounded = true)
                .graphicsLayer {
                    translationX = translationXPx * density
                    translationY = translationYPx * density
                    scaleX = currentTransform.scale
                    scaleY = currentTransform.scale
                }
                .pointerInput(blockId) {
                    coroutineScope {
                        launch {
                            detectTapGestures(
                                onTap = { currentOnSelect(blockId) },
                                onPress = { currentOnSelect(blockId) }
                            )
                        }
                        launch {
                            detectTransformGestures { _, pan, zoom, _ ->
                                currentOnSelect(blockId)
                                val newFontScale = (currentTransform.fontScale * zoom).coerceIn(0.6f, 2.5f)
                                val dx = (pan.x * currentTransform.scale) / (POSITION_SCALE_FACTOR * density)
                                val dy = (pan.y * currentTransform.scale) / (POSITION_SCALE_FACTOR * density)
                                val maxAllowedX = (EXPORT_WIDTH / 2f) / POSITION_SCALE_FACTOR
                                val maxAllowedY = (EXPORT_HEIGHT / 2f) / POSITION_SCALE_FACTOR
                                val newX = (currentTransform.x + dx).coerceIn(-maxAllowedX, maxAllowedX)
                                val newY = (currentTransform.y + dy).coerceIn(-maxAllowedY, maxAllowedY)
                                currentOnTransformChange(
                                    currentTransform.copy(
                                        x = newX,
                                        y = newY,
                                        fontScale = newFontScale
                                    )
                                )
                            }
                        }
                    }
                }
                .then(
                    if (isSelected) {
                        Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .border(3.dp, Color.White.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.12f))
                    } else {
                        Modifier
                    }
                )
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }
}

private data class NormalizedPoint(val x: Float, val y: Float, val phase: String = "RUN")

private fun getNormalizedRoutePoints(
    points: List<GpsTrackPoint>,
    targetWidth: Float,
    targetHeight: Float,
    padding: Float = 20f,
    routePointItems: List<RoutePointItem> = emptyList(),
    allowSimulationFallback: Boolean = false
): List<NormalizedPoint> {
    val resolvedItems: List<RoutePointItem> = when {
        routePointItems.size >= 2 -> routePointItems
        points.size >= 2 -> points.mapIndexed { idx, pt ->
            val ratio = idx.toFloat() / (points.size - 1).coerceAtLeast(1)
            val ph = when {
                ratio < 0.08f -> "WARMUP"
                ratio > 0.92f -> "COOL_DOWN"
                idx in (points.size * 2 / 5)..(points.size * 3 / 5) -> "PR"
                (idx / 3) % 2 == 0 -> "RUN"
                else -> "WALK"
            }
            RoutePointItem(lat = pt.latitude, lon = pt.longitude, timestamp = pt.timestampMs, phase = ph)
        }
        allowSimulationFallback -> {
            val avePts = com.example.utils.MapTileHelper.getAvenueRoutePoints(3000)
            avePts.mapIndexed { idx, (lat, lon) ->
                val ratio = idx.toFloat() / (avePts.size - 1).coerceAtLeast(1)
                val ph = when {
                    ratio < 0.10f -> "WARMUP"
                    ratio > 0.90f -> "COOL_DOWN"
                    ratio in 0.48f..0.64f -> "PR"
                    ((ratio * 6f).toInt() % 2 == 0) -> "RUN"
                    else -> "WALK"
                }
                RoutePointItem(lat = lat, lon = lon, timestamp = idx * 1000L, phase = ph)
            }
        }
        else -> emptyList()
    }

    if (resolvedItems.size < 2) return emptyList()

    val minLat = resolvedItems.minOf { it.lat }
    val maxLat = resolvedItems.maxOf { it.lat }
    val minLng = resolvedItems.minOf { it.lon }
    val maxLng = resolvedItems.maxOf { it.lon }

    val deltaLat = (maxLat - minLat).coerceAtLeast(0.00001)
    val deltaLng = (maxLng - minLng).coerceAtLeast(0.00001)

    val availableW = (targetWidth - (padding * 2)).coerceAtLeast(10f)
    val availableH = (targetHeight - (padding * 2)).coerceAtLeast(10f)

    val scaleX = availableW / deltaLng
    val scaleY = availableH / deltaLat
    val scale = minOf(scaleX, scaleY).toFloat()

    val drawWidth = (deltaLng * scale).toFloat()
    val drawHeight = (deltaLat * scale).toFloat()

    val offsetX = padding + (availableW - drawWidth) / 2f
    val offsetY = padding + (availableH - drawHeight) / 2f

    val rawOffsets = resolvedItems.map { pt ->
        val normX = offsetX + ((pt.lon - minLng) * scale).toFloat()
        val normY = offsetY + ((maxLat - pt.lat) * scale).toFloat()
        Offset(normX, normY)
    }
    val gpsPairs = resolvedItems.map { Pair(it.lat, it.lon) }
    val laneOffsetPx = (3.5f * (targetWidth / 360f).coerceIn(0.65f, 3.2f))
    val shiftedOffsets = com.example.ui.components.RunRouteLaneOffsetEngine.applyDirectionalLaneOffset(
        rawPoints = rawOffsets,
        gpsCoords = gpsPairs,
        offsetPx = laneOffsetPx
    )

    return resolvedItems.mapIndexed { index, pt ->
        val shifted = shiftedOffsets[index]
        NormalizedPoint(shifted.x, shifted.y, pt.phase)
    }
}

private fun drawBlockGpsRoute(
    canvas: Canvas,
    points: List<GpsTrackPoint>,
    transform: BlockTransform,
    centerX: Float,
    centerY: Float,
    textColor: Int = AndroidColor.WHITE,
    session: RunningSession? = null
) {
    canvas.save()
    val blockX = centerX + (transform.x * 3.8f)
    val blockY = centerY + (transform.y * 3.8f)

    canvas.translate(blockX, blockY)
    canvas.scale(transform.scale, transform.scale)

    val boxW = 360f * transform.fontScale
    val boxH = 220f * transform.fontScale

    val bgRect = RectF(-boxW / 2f, -boxH / 2f, boxW / 2f, boxH / 2f)
    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.argb(115, 0, 0, 0)
    }
    canvas.drawRoundRect(bgRect, 24f, 24f, bgPaint)

    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = textColor
        alpha = 180
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
    }
    canvas.drawRoundRect(bgRect, 24f, 24f, borderPaint)

    val routeItems = if (session != null) {
        resolveSessionRoutePointsWithPhases(session, points)
    } else emptyList()

    val normPoints = getNormalizedRoutePoints(
        points = points,
        targetWidth = boxW,
        targetHeight = boxH - (24f * transform.fontScale),
        padding = 24f * transform.fontScale,
        routePointItems = routeItems,
        allowSimulationFallback = session?.isSimulatedOrTestSession() ?: false
    )

    if (normPoints.size >= 2) {
        val originX = -boxW / 2f
        val originY = -boxH / 2f
        val dashEffect = android.graphics.DashPathEffect(
            floatArrayOf(12f * transform.fontScale, 8f * transform.fontScale),
            0f
        )
        val borderExtraPx = 2.5f * transform.fontScale

        data class ExportPhasePathGroup(
            val path: android.graphics.Path,
            val phase: String,
            val colorHex: Int,
            val coreStrokeW: Float,
            val isWarmup: Boolean
        )

        val exportGroups = ArrayList<ExportPhasePathGroup>()
        var idx = 0
        while (idx < normPoints.size - 1) {
            val segPhase = normPoints[idx + 1].phase.uppercase(Locale.US)
            val segPath = android.graphics.Path().apply {
                moveTo(originX + normPoints[idx].x, originY + normPoints[idx].y)
            }
            var j = idx + 1
            while (j < normPoints.size && normPoints[j].phase.uppercase(Locale.US) == segPhase) {
                segPath.lineTo(originX + normPoints[j].x, originY + normPoints[j].y)
                j++
            }

            val segColor = when (segPhase) {
                "PR" -> com.example.ui.components.RunRouteLaneOffsetEngine.BEST_SPRINT_COLOR_HEX
                "RUN" -> com.example.ui.components.RunRouteLaneOffsetEngine.SPRINT_COLOR_HEX
                "WALK" -> com.example.ui.components.RunRouteLaneOffsetEngine.WALK_COLOR_HEX
                else -> com.example.ui.components.RunRouteLaneOffsetEngine.WARMUP_COLOR_HEX
            }
            val isWarmup = segPhase == "WARMUP" || segPhase == "COOL_DOWN"
            val coreStrokeW = (if (segPhase == "PR") 7.0f else 5.5f) * transform.fontScale
            exportGroups.add(ExportPhasePathGroup(segPath, segPhase, segColor, coreStrokeW, isWarmup))
            idx = j - 1
        }

        for (group in exportGroups) {
            val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = group.colorHex
                alpha = 64
                style = Paint.Style.STROKE
                strokeWidth = (if (group.phase == "PR") 15f else 11f) * transform.fontScale
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            canvas.drawPath(group.path, glowPaint)
        }

        // 1ª PASSADA: Borda escura contínua (#0D0F14, alpha = 0.75f, strokeWidth + 2.5.dp)
        val fullBorderPath = android.graphics.Path().apply {
            moveTo(originX + normPoints.first().x, originY + normPoints.first().y)
            for (k in 1 until normPoints.size) {
                lineTo(originX + normPoints[k].x, originY + normPoints[k].y)
            }
        }
        val contrastPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = com.example.ui.components.RunRouteLaneOffsetEngine.CONTRAST_BORDER_HEX
            style = Paint.Style.STROKE
            strokeWidth = (5.5f * transform.fontScale) + borderExtraPx
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        canvas.drawPath(fullBorderPath, contrastPaint)
        for (group in exportGroups) {
            if (group.phase == "PR") {
                contrastPaint.strokeWidth = group.coreStrokeW + borderExtraPx
                canvas.drawPath(group.path, contrastPaint)
            }
        }

        // 2ª PASSADA: Sub-Paths coloridos contínuos por cima
        for (group in exportGroups) {
            val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = group.colorHex
                style = Paint.Style.STROKE
                strokeWidth = group.coreStrokeW
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                pathEffect = if (group.isWarmup) dashEffect else null
            }
            canvas.drawPath(group.path, corePaint)
        }

        val startX = originX + normPoints.first().x
        val startY = originY + normPoints.first().y
        val startHalo = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AndroidColor.argb(100, 46, 204, 113) }
        canvas.drawCircle(startX, startY, 13f * transform.fontScale, startHalo)
        val startDot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = com.example.ui.components.RunRouteLaneOffsetEngine.SPRINT_COLOR_HEX }
        canvas.drawCircle(startX, startY, 6.5f * transform.fontScale, startDot)

        val endX = originX + normPoints.last().x
        val endY = originY + normPoints.last().y
        val endHalo = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AndroidColor.argb(100, 0, 136, 255) }
        canvas.drawCircle(endX, endY, 13f * transform.fontScale, endHalo)
        val endDot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = com.example.ui.components.RunRouteLaneOffsetEngine.WALK_COLOR_HEX }
        canvas.drawCircle(endX, endY, 6.5f * transform.fontScale, endDot)
    }

    val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = textColor
        textSize = 11.5f * transform.fontScale
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
        fontFeatureSettings = "tnum"
        setShadowLayer(6f, 2f, 2f, AndroidColor.BLACK)
    }
    canvas.drawText(
        "● Caminhada (Azul)   ● Corrida (Verde)   ★ Melhor Sprint (PR)",
        0f,
        (boxH / 2f) - (10f * transform.fontScale),
        labelPaint
    )

    canvas.restore()
}

fun renderCardContentToCanvas(
    canvas: Canvas,
    width: Int,
    height: Int,
    context: Context,
    session: RunningSession,
    photoUri: Uri?,
    proceduralTheme: ProceduralCardTheme?,
    templateResId: Int?,
    bgScaleMode: Int,
    headerTransform: BlockTransform,
    heroTransform: BlockTransform,
    metricsTransform: BlockTransform,
    routeTransform: BlockTransform,
    shoeTransform: BlockTransform,
    progressTransform: BlockTransform,
    quoteTransform: BlockTransform,
    customQuoteText: String,
    blockVisibility: Map<String, Boolean>,
    gpsPoints: List<GpsTrackPoint>,
    headerColor: Int = AndroidColor.WHITE,
    heroColor: Int = AndroidColor.WHITE,
    metricsColor: Int = AndroidColor.WHITE,
    routeColor: Int = AndroidColor.WHITE,
    shoeColor: Int = AndroidColor.WHITE,
    progressColor: Int = AndroidColor.WHITE,
    quoteColor: Int = AndroidColor.WHITE
) {
    canvas.drawColor(AndroidColor.parseColor("#0A0A0F"))

    var bgBitmap: Bitmap? = null
    if (photoUri != null) {
        val inputStream = context.contentResolver.openInputStream(photoUri)
        bgBitmap = BitmapFactory.decodeStream(inputStream)
        inputStream?.close()
    } else if (proceduralTheme != null) {
        drawProceduralBackgroundBitmap(canvas, width, height, proceduralTheme)
    } else if (templateResId != null) {
        bgBitmap = BitmapFactory.decodeResource(context.resources, templateResId)
    }

    if (bgBitmap != null) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val bw = bgBitmap.width
        val bh = bgBitmap.height
        val targetAspect = width.toFloat() / height.toFloat()
        val bitmapAspect = bw.toFloat() / bh.toFloat()
        val srcRect = if (bitmapAspect > targetAspect) {
            val cropW = (bh * targetAspect).toInt()
            val left = (bw - cropW) / 2
            Rect(left, 0, left + cropW, bh)
        } else {
            val cropH = (bw / targetAspect).toInt()
            val top = (bh - cropH) / 2
            Rect(0, top, bw, top + cropH)
        }
        val dstRect = Rect(0, 0, width, height)
        canvas.drawBitmap(bgBitmap, srcRect, dstRect, paint)
    } else if (photoUri == null && proceduralTheme == null && templateResId == null) {
        canvas.drawColor(AndroidColor.parseColor("#141416"))
        val radialGradient = android.graphics.RadialGradient(
            width / 2f,
            height / 2f,
            width * 0.7f,
            intArrayOf(
                RunColors.brand.toArgb(),
                AndroidColor.parseColor("#80141416"),
                AndroidColor.parseColor("#141416")
            ),
            floatArrayOf(0f, 0.6f, 1f),
            android.graphics.Shader.TileMode.CLAMP
        )
        val auraPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = radialGradient
        }
        canvas.drawCircle(width / 2f, height / 2f, width * 0.7f, auraPaint)
    }

    val dimPaint = Paint().apply {
        color = AndroidColor.argb(102, 0, 0, 0)
    }
    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dimPaint)

    val centerX = width / 2f
    val centerY = height / 2f

    fun drawBlockText(
        title: String,
        subtitle: String?,
        transform: BlockTransform,
        textColor: Int,
        fontScale: Float = transform.fontScale,
        baseTitleSize: Float = 36f,
        baseSubSize: Float = 22f
    ) {
        canvas.save()
        val blockX = centerX + (transform.x * 3.8f)
        val blockY = centerY + (transform.y * 3.8f)

        canvas.translate(blockX, blockY)
        canvas.scale(transform.scale, transform.scale)

        val scaledTitleSize = baseTitleSize * fontScale
        val scaledSubSize = baseSubSize * fontScale

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColor
            textSize = scaledTitleSize
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            fontFeatureSettings = "tnum"
            setShadowLayer(10f, 3f, 3f, AndroidColor.BLACK)
        }
        val titleY = if (subtitle != null) (-10f * fontScale) else (12f * fontScale)
        canvas.drawText(title, 0f, titleY, textPaint)

        if (subtitle != null) {
            val subTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = textColor
                textSize = scaledSubSize
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
                letterSpacing = 0.08f
                fontFeatureSettings = "tnum"
                setShadowLayer(8f, 2f, 2f, AndroidColor.BLACK)
            }
            val subtitleY = titleY + (scaledTitleSize * 0.82f)
            canvas.drawText(subtitle, 0f, subtitleY, subTextPaint)
        }

        canvas.restore()
    }

    val ptBr = Locale("pt", "BR")
    val sprintAnalytics = calculateSprintAnalytics(session, gpsPoints)
    val workoutStructureSummary = sprintAnalytics.structureSummary

    // 0. Header Block
    if (blockVisibility["block_header_visible"] != false) {
        val headerSubtitleBitmap = if (session.isClassifiedAsRace()) {
            val displayTitle = com.example.engine.RunningProgramManager.getDisplayTitleForSession(session).uppercase(ptBr)
            if (displayTitle.startsWith("PROVA")) "$displayTitle • MAX RUN" else "PROVA • $displayTitle"
        } else if (com.example.engine.RunningProgramManager.isEffectiveFreeRun(session)) {
            "CORRIDA LIVRE • MAX RUN"
        } else {
            "SEMANA ${session.weekNumber} • DIA ${session.dayNumber} • ${if (session.isGentleMode) "MODO LEVE" else "COUCH TO 5K"}"
        }
        drawBlockText(
            title = "MAX PERFORMANCE",
            subtitle = headerSubtitleBitmap,
            transform = headerTransform,
            textColor = headerColor,
            fontScale = headerTransform.fontScale,
            baseTitleSize = 32f,
            baseSubSize = 15f
        )
    }

    val distKm = session.distanceMeters / 1000f
    val mins = session.durationSec / 60
    val secs = session.durationSec % 60
    val timeStr = String.format(Locale.US, "%02d:%02d", mins, secs)

    val hasIsolatedMainRun = (session.mainRunDurationSec ?: 0) > 0 && (session.mainRunDistanceMeters ?: 0) > 0
    val effectiveMainRunDuration = if (hasIsolatedMainRun) session.mainRunDurationSec!! else session.durationSec
    val effectiveMainRunDist = if (hasIsolatedMainRun) session.mainRunDistanceMeters!! else session.distanceMeters

    val paceSec = if (effectiveMainRunDist > 0) kotlin.math.round((effectiveMainRunDuration.toDouble() * 1000.0) / effectiveMainRunDist.toDouble()).toInt() else 0
    val pMin = paceSec / 60
    val pSec = paceSec % 60
    val paceStr = if (paceSec > 0) String.format(Locale.US, "%d'%02d\"", pMin, pSec) else "--:--"

    val sprintPaceFormatted = sprintAnalytics.avgSprintPaceText

    val shoeNameText = if (!session.shoeName.isNullOrBlank()) session.shoeName else "Nenhum"

    // 1. Hero Block
    if (blockVisibility["block_hero_visible"] != false) {
        drawBlockText(
            title = String.format(ptBr, "%.2f KM   •   %s MIN", distKm, timeStr),
            subtitle = workoutStructureSummary.uppercase(ptBr),
            transform = heroTransform,
            textColor = heroColor,
            fontScale = heroTransform.fontScale,
            baseTitleSize = 38f,
            baseSubSize = 15f
        )
    }

    // 2. Metrics Block
    if (blockVisibility["block_metrics_visible"] != false) {
        val dossier = com.example.data.model.buildRunPhysiologyDossier(session, null, gpsPoints)
        val hrText = if (session.avgHr != null && session.avgHr > 0) {
            "   •   ${session.avgHr} BPM"
        } else ""

        val cadStr = if (dossier.avgCadenceSpm > 0) "${dossier.avgCadenceSpm}/${if (dossier.maxCadenceSpm > 0) dossier.maxCadenceSpm else "--"} PPM" else "--"
        val strideStr = if (dossier.avgStrideLengthCm > 0) "${dossier.avgStrideLengthCm}/${if (dossier.maxStrideLengthCm > 0) dossier.maxStrideLengthCm else "--"} CM" else "--"
        val kcalStr = if (dossier.isWeightMissingForCalories) "-- KCAL" else "${dossier.totalCaloriesKcal} KCAL"
        val vitStr = if (dossier.vitalityScoreGain > 0) "+${dossier.vitalityScoreGain} VITALIDADE" else "--"

        drawBlockText(
            title = "PACE $paceStr   •   TIROS $sprintPaceFormatted$hrText",
            subtitle = "CAD $cadStr • PASSADA $strideStr • $kcalStr • $vitStr",
            transform = metricsTransform,
            textColor = metricsColor,
            fontScale = metricsTransform.fontScale,
            baseTitleSize = 28f,
            baseSubSize = 13.5f
        )
    }

    // 3. GPS Route Block
    if (blockVisibility["block_route_visible"] != false) {
        drawBlockGpsRoute(
            canvas = canvas,
            points = gpsPoints,
            transform = routeTransform,
            centerX = centerX,
            centerY = centerY,
            textColor = routeColor,
            session = session
        )
    }

    // 4. Shoe Block
    if (blockVisibility["block_shoe_visible"] != false) {
        drawBlockText(
            title = "TENIS: $shoeNameText",
            subtitle = null,
            transform = shoeTransform,
            textColor = shoeColor,
            fontScale = shoeTransform.fontScale,
            baseTitleSize = 28f
        )
    }

    // 5. Progress Block
    if (blockVisibility["block_progress_visible"] != false) {
        val progressBitmapTitle = if (session.isClassifiedAsRace()) {
            "PROVA • ${com.example.engine.RunningProgramManager.getDisplayTitleForSession(session).uppercase(ptBr)}   •   $workoutStructureSummary"
        } else if (com.example.engine.RunningProgramManager.isEffectiveFreeRun(session)) {
            "CORRIDA LIVRE   •   $workoutStructureSummary"
        } else {
            "SEMANA ${session.weekNumber} • DIA ${session.dayNumber}   •   $workoutStructureSummary"
        }
        drawBlockText(
            title = progressBitmapTitle,
            subtitle = null,
            transform = progressTransform,
            textColor = progressColor,
            fontScale = progressTransform.fontScale,
            baseTitleSize = 24f
        )
    }

    // 6. Quote Block
    if (blockVisibility["block_quote_visible"] != false && customQuoteText.isNotBlank()) {
        drawBlockText(
            title = "\"$customQuoteText\"",
            subtitle = null,
            transform = quoteTransform,
            textColor = quoteColor,
            fontScale = quoteTransform.fontScale,
            baseTitleSize = 28f
        )
    }
}

fun generateCardBitmap(
    context: Context,
    session: RunningSession,
    photoUri: Uri?,
    proceduralTheme: ProceduralCardTheme? = ProceduralCardTheme.CYBER_NEON,
    templateResId: Int? = null,
    bgScaleMode: Int = 0,
    headerTransform: BlockTransform = BlockTransform(0f, -155f, 1f, 1f),
    heroTransform: BlockTransform = BlockTransform(0f, -110f, 1f, 1f),
    metricsTransform: BlockTransform = BlockTransform(0f, -72f, 1f, 1f),
    routeTransform: BlockTransform = BlockTransform(0f, -5f, 1f, 1f),
    shoeTransform: BlockTransform = BlockTransform(0f, 68f, 1f, 1f),
    progressTransform: BlockTransform = BlockTransform(0f, 102f, 1f, 1f),
    quoteTransform: BlockTransform = BlockTransform(0f, 138f, 1f, 1f),
    customQuoteText: String = "",
    blockVisibility: Map<String, Boolean> = mapOf(
        "block_header_visible" to true,
        "block_hero_visible" to true,
        "block_metrics_visible" to true,
        "block_route_visible" to true,
        "block_shoe_visible" to true,
        "block_progress_visible" to true,
        "block_quote_visible" to true
    ),
    gpsPoints: List<GpsTrackPoint> = emptyList(),
    headerColor: Int = AndroidColor.WHITE,
    heroColor: Int = AndroidColor.WHITE,
    metricsColor: Int = AndroidColor.WHITE,
    routeColor: Int = AndroidColor.WHITE,
    shoeColor: Int = AndroidColor.WHITE,
    progressColor: Int = AndroidColor.WHITE,
    quoteColor: Int = AndroidColor.WHITE
): Bitmap {
    val width = 1080
    val height = 1920
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    renderCardContentToCanvas(
        canvas = canvas,
        width = width,
        height = height,
        context = context,
        session = session,
        photoUri = photoUri,
        proceduralTheme = proceduralTheme,
        templateResId = templateResId,
        bgScaleMode = bgScaleMode,
        headerTransform = headerTransform,
        heroTransform = heroTransform,
        metricsTransform = metricsTransform,
        routeTransform = routeTransform,
        shoeTransform = shoeTransform,
        progressTransform = progressTransform,
        quoteTransform = quoteTransform,
        customQuoteText = customQuoteText,
        blockVisibility = blockVisibility,
        gpsPoints = gpsPoints,
        headerColor = headerColor,
        heroColor = heroColor,
        metricsColor = metricsColor,
        routeColor = routeColor,
        shoeColor = shoeColor,
        progressColor = progressColor,
        quoteColor = quoteColor
    )

    return bitmap
}

fun saveCardToGallery(context: Context, bitmap: Bitmap): Boolean {
    return try {
        val filename = "share_card_${System.currentTimeMillis()}.png"
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, com.example.utils.AppExportPaths.PICTURES_RELATIVE_PATH)
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            ?: return false

        resolver.openOutputStream(uri)?.use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentValues.clear()
            contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, contentValues, null, null)
        }

        Toast.makeText(context, "Card salvo na Galeria!", Toast.LENGTH_SHORT).show()
        AudioSynth.playTickSound()
        true
    } catch (e: Exception) {
        Toast.makeText(context, "Erro ao salvar imagem: ${e.message}", Toast.LENGTH_SHORT).show()
        false
    }
}

fun shareCardAsBitmap(
    context: Context,
    session: RunningSession,
    photoUri: Uri?,
    proceduralTheme: ProceduralCardTheme? = ProceduralCardTheme.CYBER_NEON,
    templateResId: Int? = null,
    bgScaleMode: Int = 0,
    headerTransform: BlockTransform,
    heroTransform: BlockTransform,
    metricsTransform: BlockTransform,
    routeTransform: BlockTransform = BlockTransform(0f, -5f, 1f, 1f),
    shoeTransform: BlockTransform,
    progressTransform: BlockTransform,
    quoteTransform: BlockTransform,
    customQuoteText: String,
    blockVisibility: Map<String, Boolean> = mapOf(
        "block_header_visible" to true,
        "block_hero_visible" to true,
        "block_metrics_visible" to true,
        "block_route_visible" to true,
        "block_shoe_visible" to true,
        "block_progress_visible" to true,
        "block_quote_visible" to true
    ),
    gpsPoints: List<GpsTrackPoint> = emptyList(),
    headerColor: Int = AndroidColor.WHITE,
    heroColor: Int = AndroidColor.WHITE,
    metricsColor: Int = AndroidColor.WHITE,
    routeColor: Int = AndroidColor.WHITE,
    shoeColor: Int = AndroidColor.WHITE,
    progressColor: Int = AndroidColor.WHITE,
    quoteColor: Int = AndroidColor.WHITE
) {
    try {
        val bitmap = generateCardBitmap(
            context, session, photoUri, proceduralTheme, templateResId, bgScaleMode,
            headerTransform, heroTransform, metricsTransform, routeTransform, shoeTransform,
            progressTransform, quoteTransform, customQuoteText,
            blockVisibility, gpsPoints,
            headerColor, heroColor, metricsColor, routeColor, shoeColor, progressColor, quoteColor
        )

        val cachePath = File(context.cacheDir, "images")
        cachePath.mkdirs()
        val file = File(cachePath, "share_card_${System.currentTimeMillis()}.png")
        val stream = FileOutputStream(file)
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        stream.close()

        val contentUri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, contentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(Intent.createChooser(shareIntent, "Compartilhar treino MAX"))
        AudioSynth.playTickSound()

    } catch (e: Exception) {
        Toast.makeText(context, "Erro ao gerar imagem de compartilhamento: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

fun exportCardAsPdf(
    context: Context,
    session: RunningSession,
    photoUri: Uri?,
    proceduralTheme: ProceduralCardTheme? = ProceduralCardTheme.CYBER_NEON,
    templateResId: Int? = null,
    bgScaleMode: Int = 0,
    headerTransform: BlockTransform = BlockTransform(0f, -155f, 1f, 1f),
    heroTransform: BlockTransform = BlockTransform(0f, -110f, 1f, 1f),
    metricsTransform: BlockTransform = BlockTransform(0f, -72f, 1f, 1f),
    routeTransform: BlockTransform = BlockTransform(0f, -5f, 1f, 1f),
    shoeTransform: BlockTransform = BlockTransform(0f, 68f, 1f, 1f),
    progressTransform: BlockTransform = BlockTransform(0f, 102f, 1f, 1f),
    quoteTransform: BlockTransform = BlockTransform(0f, 138f, 1f, 1f),
    customQuoteText: String = "",
    blockVisibility: Map<String, Boolean> = mapOf(
        "block_header_visible" to true,
        "block_hero_visible" to true,
        "block_metrics_visible" to true,
        "block_route_visible" to true,
        "block_shoe_visible" to true,
        "block_progress_visible" to true,
        "block_quote_visible" to true
    ),
    gpsPoints: List<GpsTrackPoint> = emptyList(),
    headerColor: Int = AndroidColor.WHITE,
    heroColor: Int = AndroidColor.WHITE,
    metricsColor: Int = AndroidColor.WHITE,
    routeColor: Int = AndroidColor.WHITE,
    shoeColor: Int = AndroidColor.WHITE,
    progressColor: Int = AndroidColor.WHITE,
    quoteColor: Int = AndroidColor.WHITE
): File? {
    return try {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(1080, 1920, 1).create()
        val page = pdfDocument.startPage(pageInfo)

        renderCardContentToCanvas(
            canvas = page.canvas,
            width = 1080,
            height = 1920,
            context = context,
            session = session,
            photoUri = photoUri,
            proceduralTheme = proceduralTheme,
            templateResId = templateResId,
            bgScaleMode = bgScaleMode,
            headerTransform = headerTransform,
            heroTransform = heroTransform,
            metricsTransform = metricsTransform,
            routeTransform = routeTransform,
            shoeTransform = shoeTransform,
            progressTransform = progressTransform,
            quoteTransform = quoteTransform,
            customQuoteText = customQuoteText,
            blockVisibility = blockVisibility,
            gpsPoints = gpsPoints,
            headerColor = headerColor,
            heroColor = heroColor,
            metricsColor = metricsColor,
            routeColor = routeColor,
            shoeColor = shoeColor,
            progressColor = progressColor,
            quoteColor = quoteColor
        )

        pdfDocument.finishPage(page)

        val filename = "share_card_${System.currentTimeMillis()}.pdf"
        val cachePath = File(context.cacheDir, "documents")
        cachePath.mkdirs()
        val file = File(cachePath, filename)
        FileOutputStream(file).use { out ->
            pdfDocument.writeTo(out)
        }
        pdfDocument.close()

        savePdfToDownloads(context, file)
        file
    } catch (e: Exception) {
        Toast.makeText(context, "Erro ao gerar PDF vetorial: ${e.message}", Toast.LENGTH_SHORT).show()
        null
    }
}

fun savePdfToDownloads(context: Context, sourceFile: File): Boolean {
    return try {
        val filename = "share_card_${System.currentTimeMillis()}.pdf"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, filename)
                put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                put(MediaStore.Downloads.RELATIVE_PATH, com.example.utils.AppExportPaths.DOWNLOADS_RELATIVE_PATH)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            if (uri != null) {
                resolver.openOutputStream(uri)?.use { output ->
                    sourceFile.inputStream().use { input ->
                        input.copyTo(output)
                    }
                }
                contentValues.clear()
                contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
                Toast.makeText(context, "PDF vetorial salvo em Downloads!", Toast.LENGTH_SHORT).show()
                AudioSynth.playTickSound()
                true
            } else {
                Toast.makeText(context, "PDF salvo com sucesso!", Toast.LENGTH_SHORT).show()
                true
            }
        } else {
            Toast.makeText(context, "PDF salvo com sucesso!", Toast.LENGTH_SHORT).show()
            true
        }
    } catch (e: Exception) {
        Toast.makeText(context, "Erro ao salvar PDF em Downloads: ${e.message}", Toast.LENGTH_SHORT).show()
        false
    }
}

private fun drawProceduralBackgroundBitmap(canvas: Canvas, width: Int, height: Int, theme: ProceduralCardTheme) {
    val w = width.toFloat()
    val h = height.toFloat()

    when (theme) {
        ProceduralCardTheme.CYBER_NEON -> {
            val bgShader = android.graphics.LinearGradient(
                0f, 0f, 0f, h,
                intArrayOf(
                    AndroidColor.parseColor("#12121A"),
                    AndroidColor.parseColor("#0B0B10"),
                    AndroidColor.parseColor("#050508")
                ),
                floatArrayOf(0f, 0.5f, 1f),
                android.graphics.Shader.TileMode.CLAMP
            )
            canvas.drawRect(0f, 0f, w, h, Paint().apply { shader = bgShader })

            val neonGlow = android.graphics.RadialGradient(
                w * 0.5f, h * 0.3f, w * 0.7f,
                AndroidColor.argb(70, 0, 212, 255),
                AndroidColor.TRANSPARENT,
                android.graphics.Shader.TileMode.CLAMP
            )
            canvas.drawCircle(w * 0.5f, h * 0.3f, w * 0.7f, Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = neonGlow })

            val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = AndroidColor.argb(15, 0, 212, 255)
                strokeWidth = 2f
            }
            var y = 0f
            while (y < h) {
                canvas.drawLine(0f, y, w, y, linePaint)
                y += 60f
            }
        }

        ProceduralCardTheme.SUNSET_RUN -> {
            val bgShader = android.graphics.LinearGradient(
                0f, 0f, 0f, h,
                intArrayOf(
                    AndroidColor.parseColor("#2A121A"),
                    AndroidColor.parseColor("#180A10"),
                    AndroidColor.parseColor("#0A0508")
                ),
                floatArrayOf(0f, 0.5f, 1f),
                android.graphics.Shader.TileMode.CLAMP
            )
            canvas.drawRect(0f, 0f, w, h, Paint().apply { shader = bgShader })

            val sunGlow = android.graphics.RadialGradient(
                w * 0.5f, h * 0.45f, w * 0.6f,
                AndroidColor.argb(90, 255, 107, 53),
                AndroidColor.TRANSPARENT,
                android.graphics.Shader.TileMode.CLAMP
            )
            canvas.drawCircle(w * 0.5f, h * 0.45f, w * 0.6f, Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = sunGlow })
        }

        ProceduralCardTheme.MIDNIGHT_METRO -> {
            val bgShader = android.graphics.LinearGradient(
                0f, 0f, 0f, h,
                intArrayOf(
                    AndroidColor.parseColor("#0F141C"),
                    AndroidColor.parseColor("#080A0E"),
                    AndroidColor.parseColor("#040507")
                ),
                floatArrayOf(0f, 0.5f, 1f),
                android.graphics.Shader.TileMode.CLAMP
            )
            canvas.drawRect(0f, 0f, w, h, Paint().apply { shader = bgShader })

            val pulse = android.graphics.RadialGradient(
                w * 0.5f, h * 0.5f, w * 0.5f,
                AndroidColor.argb(60, 0, 255, 102),
                AndroidColor.TRANSPARENT,
                android.graphics.Shader.TileMode.CLAMP
            )
            canvas.drawCircle(w * 0.5f, h * 0.5f, w * 0.5f, Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = pulse })
        }

        ProceduralCardTheme.CARBON_GOLD -> {
            val bgShader = android.graphics.LinearGradient(
                0f, 0f, 0f, h,
                intArrayOf(
                    AndroidColor.parseColor("#17171E"),
                    AndroidColor.parseColor("#0E0E13"),
                    AndroidColor.parseColor("#060608")
                ),
                floatArrayOf(0f, 0.5f, 1f),
                android.graphics.Shader.TileMode.CLAMP
            )
            canvas.drawRect(0f, 0f, w, h, Paint().apply { shader = bgShader })

            val goldAura = android.graphics.RadialGradient(
                w * 0.5f, h * 0.35f, w * 0.8f,
                AndroidColor.argb(55, 255, 234, 0),
                AndroidColor.TRANSPARENT,
                android.graphics.Shader.TileMode.CLAMP
            )
            canvas.drawCircle(w * 0.5f, h * 0.35f, w * 0.8f, Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = goldAura })

            val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = AndroidColor.argb(20, 255, 255, 255)
            }
            val dotStep = 50f
            var dx = 25f
            while (dx < w) {
                var dy = 25f
                while (dy < h) {
                    canvas.drawCircle(dx, dy, 3f, dotPaint)
                    dy += dotStep
                }
                dx += dotStep
            }
        }
    }
}
