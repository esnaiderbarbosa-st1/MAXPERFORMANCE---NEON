package com.example.ui.screens

import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.health.connect.client.PermissionController
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.RecordedKmSplit
import com.example.data.model.SplitSelectorMode
import com.example.data.model.buildRunPhysiologyDossier
import com.example.data.model.buildSplitsForMode
import com.example.data.model.calculateSprintAnalytics
import com.example.data.model.isSimulatedOrTestSession
import com.example.data.model.resolveSessionRoutePointsWithPhases
import com.example.data.model.safeCoerceIn
import com.example.data.model.safeRoundToInt
import com.example.data.model.toRecordedKmSplits
import com.example.data.model.toHrTimelineList
import com.example.engine.RunningProgramManager
import com.example.services.HeartRateBleManager
import com.example.ui.components.AnimatedRouteMapView
import com.example.ui.components.MapTileStyle
import com.example.ui.components.RouteSprintLegendBar
import com.example.ui.components.RunDossierCadenceCard
import com.example.ui.components.RunDossierHeartRateCard
import com.example.ui.components.RunDossierMasterHeaderAnd2x3Grid
import com.example.ui.components.RunDossierPaceAreaChartCard
import com.example.ui.components.RunDossierPacePerKmCard
import com.example.ui.components.RunDossierStrideLengthCard
import com.example.ui.components.RunDossierTrainingEffectAndSourceCard
import com.example.ui.viewmodel.AppScreen
import com.example.ui.viewmodel.MainViewModel
import com.example.utils.AudioSynth
import com.example.utils.RunningCsvExporter
import com.example.utils.SessionReportBitmapGenerator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RunningSessionDetailScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val ptBr = remember { Locale.forLanguageTag("pt-BR") }

    val prefs = remember { context.getSharedPreferences("running_report_prefs", Context.MODE_PRIVATE) }
    var isLightReport by remember { mutableStateOf(prefs.getBoolean("pref_light_report", false)) }

    val session by viewModel.selectedDetailSession.collectAsState()
    val runningShoes by viewModel.runningShoes.collectAsState()
    val officialRaces by viewModel.officialRaces.collectAsState()
    val userProfile by viewModel.userProfile.collectAsState()
    val allRunningSessions by viewModel.runningSessions.collectAsState()
    val allGymSessions by viewModel.gymWorkoutSessions.collectAsState()

    var showShoeDialog by remember { mutableStateOf(false) }
    var showSaveOfficialRaceDialog by remember { mutableStateOf(false) }
    var showOfficialRacesVaultDialog by remember { mutableStateOf(false) }
    var showSyncWatchDialog by remember { mutableStateOf(false) }

    BackHandler {
        viewModel.popBackScreen(fallback = AppScreen.RUNNING_HISTORY)
    }

    if (session == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(if (isLightReport) Color(0xFFF8FAFC) else Color(0xFF0D0F14))
                .statusBarsPadding()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "Sessão de corrida não encontrada.",
                color = if (isLightReport) Color(0xFF0F172A) else Color.White,
                fontSize = 16.sp
            )
        }
        return
    }

    val currentSession = session!!

    // Paleta Dinâmica Modo Claro / Escuro (Fundo #0D0F14 e Cards #151922 no Modo Escuro)
    val screenBg = if (isLightReport) Color(0xFFF8FAFC) else Color(0xFF0D0F14)
    val cardBg = if (isLightReport) Color.White else Color(0xFF151922)
    val cardBorder = if (isLightReport) Color(0xFFE2E8F0) else Color(0xFF242A38)
    val primaryText = if (isLightReport) Color(0xFF0F172A) else Color.White
    val secondaryText = if (isLightReport) Color(0xFF64748B) else Color(0xFF94A3B8)
    val brandAccent = if (isLightReport) Color(0xFF0284C7) else Color(0xFF00E5FF)

    val dateStr = remember(currentSession.date) {
        SimpleDateFormat("dd/MM/yyyy • HH:mm", ptBr).format(Date(currentSession.date))
    }

    // Isola Aquecimento e Desaquecimento do Cálculo de Pace Principal
    val hasMainRunIsolation = currentSession.mainRunDistanceMeters != null &&
            currentSession.mainRunDistanceMeters > 0 &&
            currentSession.mainRunDurationSec != null &&
            currentSession.mainRunDurationSec > 0 &&
            (currentSession.mainRunDurationSec < currentSession.durationSec || currentSession.mainRunDistanceMeters < currentSession.distanceMeters)

    val distKm = if (hasMainRunIsolation) {
        currentSession.mainRunDistanceMeters!! / 1000f
    } else {
        currentSession.distanceMeters / 1000f
    }

    val effectiveDurationSec = if (hasMainRunIsolation) {
        currentSession.mainRunDurationSec!!
    } else {
        currentSession.durationSec
    }

    val minutes = effectiveDurationSec / 60
    val seconds = effectiveDurationSec % 60
    val durationText = String.format(Locale.US, "%02d:%02d", minutes, seconds)

    val effectivePaceSec = if (hasMainRunIsolation && currentSession.mainRunAvgPaceSecPerKm != null && currentSession.mainRunAvgPaceSecPerKm > 0) {
        currentSession.mainRunAvgPaceSecPerKm
    } else if (currentSession.avgPaceSecPerKm > 0) {
        currentSession.avgPaceSecPerKm
    } else if (distKm > 0f && effectiveDurationSec > 0) {
        (effectiveDurationSec / distKm).safeRoundToInt()
    } else {
        0
    }

    val paceText = if (effectivePaceSec > 0) {
        val pMin = effectivePaceSec / 60
        val pSec = effectivePaceSec % 60
        String.format(Locale.US, "%d'%02d\"/km", pMin, pSec)
    } else {
        "--:--"
    }

    val caloriesKcal = remember(currentSession.distanceMeters, userProfile) {
        com.example.data.model.calculateRunActiveCaloriesKcal(
            distanceMeters = currentSession.distanceMeters,
            userProfile = userProfile
        )
    }

    var selectedSplitMode by remember { mutableStateOf(SplitSelectorMode.ONE_KM_MAIN) }
    var showFullscreenMap by remember(currentSession.id) { mutableStateOf(false) }
    var selectedMapStyle by remember {
        mutableStateOf(MapTileStyle.SATELLITE)
    }
    val gpsPointsFlow = remember(currentSession.id) {
        viewModel.getGpsPointsForSession(currentSession.id)
    }
    val gpsPoints by gpsPointsFlow.collectAsState(initial = emptyList())

    val chronologicalSessions = remember(allRunningSessions, currentSession) {
        val listWithCurrent = if (allRunningSessions.any { it.id == currentSession.id }) {
            allRunningSessions
        } else {
            allRunningSessions + currentSession
        }
        listWithCurrent.sortedBy { it.date }
    }
    val currentSessionIndex = remember(chronologicalSessions, currentSession.id) {
        chronologicalSessions.indexOfFirst { it.id == currentSession.id }
    }
    val prevSession = remember(chronologicalSessions, currentSessionIndex) {
        if (currentSessionIndex > 0) chronologicalSessions[currentSessionIndex - 1] else null
    }
    val nextSession = remember(chronologicalSessions, currentSessionIndex) {
        if (currentSessionIndex in 0 until chronologicalSessions.lastIndex) chronologicalSessions[currentSessionIndex + 1] else null
    }
    val shortDateFormat = remember { SimpleDateFormat("dd/MM", ptBr) }

    val sprintAnalytics = remember(currentSession, gpsPoints) {
        runCatching {
            calculateSprintAnalytics(currentSession, gpsPoints)
        }.getOrElse {
            com.example.data.model.SprintAnalytics(
                structureSummary = "Sessão de Corrida",
                headerIdentification = "Sessão de Corrida",
                mainDistMeters = currentSession.distanceMeters,
                mainDurationSec = currentSession.durationSec,
                mainPaceSecPerKm = currentSession.avgPaceSecPerKm,
                totalDistMeters = currentSession.distanceMeters,
                totalDurationSec = currentSession.durationSec,
                totalPaceSecPerKm = currentSession.avgPaceSecPerKm,
                avgSprintPaceSecPerKm = currentSession.avgPaceSecPerKm,
                bestSprintPaceSecPerKm = currentSession.avgPaceSecPerKm,
                runningTimeSec = currentSession.durationSec,
                walkingTimeSec = 0,
                avgCadenceSpm = 156,
                elevationGainMeters = 0,
                isContinuousRunMode = true
            )
        }
    }

    val routePointItems = remember(currentSession, gpsPoints) {
        runCatching {
            resolveSessionRoutePointsWithPhases(currentSession, gpsPoints)
        }.getOrDefault(emptyList())
    }

    val routeCoords = remember(routePointItems) {
        routePointItems.map { Pair(it.lat, it.lon) }
    }

    val displaySplits: List<RecordedKmSplit> = remember(currentSession, selectedSplitMode, gpsPoints) {
        runCatching {
            buildSplitsForMode(
                session = currentSession,
                mode = selectedSplitMode,
                gpsPoints = gpsPoints
            )
        }.getOrDefault(emptyList())
    }

    // Dossiê Biomecânico & Fisiológico (Etapa 1: Telemetria + 2x3 + FC 5 Zonas + Ritmo Área + Ritmo por Km)
    val physiologyDossier = remember(currentSession, userProfile, gpsPoints) {
        runCatching {
            buildRunPhysiologyDossier(
                session = currentSession,
                userProfile = userProfile,
                gpsPoints = gpsPoints
            )
        }.getOrElse {
            buildRunPhysiologyDossier(
                session = currentSession.copy(avgHr = null, maxHr = null, hrTimeline = null, kmSplitsJson = null),
                userProfile = userProfile,
                gpsPoints = emptyList()
            )
        }
    }

    val currentShoeName = currentSession.shoeName?.ifBlank { null } ?: "Olympikus Corre 4"
    val matchedShoe = remember(currentShoeName, runningShoes) {
        runningShoes.firstOrNull { shoe -> viewModel.shoeMatches(shoe, currentShoeName) }
    }
    val currentShoeDisplay = remember(currentShoeName, matchedShoe) {
        if (matchedShoe != null) {
            String.format(Locale.US, "%s • %.1f / %.0f km acumulados", matchedShoe.fullDisplayName(), matchedShoe.totalDistanceKm, matchedShoe.maxDistanceKm)
        } else {
            currentShoeName
        }
    }
    val coroutineScope = rememberCoroutineScope()
    var isGeneratingLongScreenshot by remember { mutableStateOf(false) }

    fun triggerLongScreenshot() {
        if (isGeneratingLongScreenshot) return
        isGeneratingLongScreenshot = true
        coroutineScope.launch {
            try {
                Toast.makeText(context, "Renderizando Share Card MAX RUN...", Toast.LENGTH_SHORT).show()
                SessionReportBitmapGenerator.shareFullSessionReport(
                    context = context,
                    session = currentSession,
                    isLightReport = isLightReport,
                    userProfile = userProfile,
                    gpsPoints = gpsPoints,
                    recordedSplits = displaySplits,
                    splitSelectorMode = selectedSplitMode,
                    runningShoes = runningShoes,
                    mapTileStyle = selectedMapStyle
                )
            } finally {
                isGeneratingLongScreenshot = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(screenBg)
            .statusBarsPadding()
    ) {
        // --- 1. BARRA SUPERIOR DEDICADA COM NAVEGAÇÃO ENTRE CORRIDAS E ALTERNADOR CLARO/ESCURO ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = {
                    AudioSynth.playTickSound()
                    viewModel.popBackScreen(fallback = AppScreen.RUNNING_HISTORY)
                }
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Voltar",
                    tint = primaryText
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Relatório da Corrida",
                    color = primaryText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
                if (chronologicalSessions.isNotEmpty() && currentSessionIndex >= 0) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val hasPrev = prevSession != null
                        val hasNext = nextSession != null
                        if (hasPrev) {
                            Icon(
                                imageVector = Icons.Default.ChevronLeft,
                                contentDescription = "Corrida anterior",
                                tint = brandAccent,
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable {
                                        prevSession?.let { target ->
                                            AudioSynth.playTickSound()
                                            viewModel.selectRunningDetailSession(target)
                                        }
                                    }
                            )
                        }
                        Text(
                            text = "${currentSessionIndex + 1} de ${chronologicalSessions.size} • S${currentSession.weekNumber}D${currentSession.dayNumber}",
                            color = secondaryText,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false
                        )
                        if (hasNext) {
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = "Próxima corrida",
                                tint = brandAccent,
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable {
                                        nextSession?.let { target ->
                                            AudioSynth.playTickSound()
                                            viewModel.selectRunningDetailSession(target)
                                        }
                                    }
                            )
                        }
                    }
                }
            }

            // Alternador de Modo Claro / Escuro (Pílula Minimalista Sol/Lua)
            Surface(
                onClick = {
                    isLightReport = !isLightReport
                    prefs.edit().putBoolean("pref_light_report", isLightReport).apply()
                    AudioSynth.playTickSound()
                },
                shape = RoundedCornerShape(50),
                color = if (isLightReport) Color(0xFFE2E8F0) else Color(0xFF1E293B),
                border = BorderStroke(1.dp, if (isLightReport) Color(0xFFCBD5E1) else Color(0xFF334155)),
                modifier = Modifier.size(width = 76.dp, height = 34.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(3.dp),
                    contentAlignment = if (isLightReport) Alignment.CenterStart else Alignment.CenterEnd
                ) {
                    Box(
                        modifier = Modifier
                            .width(34.dp)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(50))
                            .background(if (isLightReport) Color.White else Color(0xFF334155))
                    )
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        Icon(
                            imageVector = Icons.Default.WbSunny,
                            contentDescription = "Modo Claro",
                            tint = if (isLightReport) Color(0xFFD97706) else Color(0xFF94A3B8),
                            modifier = Modifier.size(16.dp)
                        )
                        Icon(
                            imageVector = Icons.Default.NightsStay,
                            contentDescription = "Modo Escuro",
                            tint = if (!isLightReport) Color(0xFFFFD600) else Color(0xFF64748B),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // --- CONTEÚDO PRINCIPAL EM ROLAGEM ÚNICA CONTÍNUA ---
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // ==========================================
            // BLOCO 1 — CABEÇALHO MESTRE + GRADE 2x3 DE MÉTRICAS PRINCIPAIS (MI FITNESS / GARMIN)
            // ==========================================
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RunDossierMasterHeaderAnd2x3Grid(
                        dossier = physiologyDossier,
                        session = currentSession,
                        sprintAnalytics = sprintAnalytics,
                        isLightMode = isLightReport,
                        prevSession = prevSession,
                        nextSession = nextSession,
                        onSelectSession = { target ->
                            AudioSynth.playTickSound()
                            viewModel.selectRunningDetailSession(target)
                        }
                    )

                    if (hasMainRunIsolation) {
                        val totalDistStr = String.format(ptBr, "%.2f km", currentSession.distanceMeters / 1000f)
                        val totalM = currentSession.durationSec / 60
                        val totalS = currentSession.durationSec % 60
                        val totalTimeStr = String.format(Locale.US, "%02d:%02d", totalM, totalS)

                        Surface(
                            color = if (isLightReport) Color(0xFFF1F5F9) else Color(0xFF151922),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, cardBorder)
                        ) {
                            Text(
                                text = "Treino Principal Isolado: ${String.format(ptBr, "%.2f km", distKm)} ($durationText • $paceText) | Total c/ Aquec.+Desaquec.: $totalDistStr ($totalTimeStr)",
                                color = secondaryText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }

                    // Botão de Conversão em 1 Toque: "Salvar em Provas Oficiais" + "Comparar (VS)"
                    val linkedOfficialRace = remember(officialRaces, currentSession.id) {
                        officialRaces.firstOrNull { it.linkedRunSessionId == currentSession.id }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            onClick = {
                                AudioSynth.playTickSound()
                                showSaveOfficialRaceDialog = true
                            },
                            color = if (linkedOfficialRace != null) Color(0xFF00E676).copy(alpha = 0.16f) else Color(0xFFFFD600).copy(alpha = 0.16f),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(
                                1.2.dp,
                                if (linkedOfficialRace != null) Color(0xFF00E676) else Color(0xFFFFD600).copy(alpha = 0.65f)
                            ),
                            modifier = Modifier.weight(1.3f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.EmojiEvents,
                                    contentDescription = null,
                                    tint = if (linkedOfficialRace != null) Color(0xFF00E676) else Color(0xFFFFD600),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = if (linkedOfficialRace != null) {
                                        "Prova Salva (${linkedOfficialRace.raceName})"
                                    } else {
                                        "Salvar em Provas Oficiais"
                                    },
                                    color = if (linkedOfficialRace != null) Color(0xFF00E676) else Color(0xFFFFD600),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Surface(
                            onClick = {
                                AudioSynth.playTickSound()
                                viewModel.openRunningPlanningTab(subTab = 1)
                            },
                            color = cardBg,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, brandAccent.copy(alpha = 0.55f)),
                            modifier = Modifier.weight(0.95f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.CompareArrows,
                                    contentDescription = null,
                                    tint = brandAccent,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "Comparar (VS)",
                                    color = brandAccent,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    // Botão de Ação: "Sincronizar Relógio (Mi Fitness)" (Icons.Default.Watch / Icons.Default.Edit)
                    val isWatchSynced = currentSession.hrProvenance?.source == com.example.data.model.DataSourceType.OPTICAL_PPG_WATCH ||
                        currentSession.advancedMetrics?.watchSyncedSteps != null
                    Surface(
                        onClick = {
                            AudioSynth.playTickSound()
                            showSyncWatchDialog = true
                        },
                        color = if (isWatchSynced) Color(0xFF00E676).copy(alpha = 0.14f) else Color(0xFF00B0FF).copy(alpha = 0.14f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(
                            1.2.dp,
                            if (isWatchSynced) Color(0xFF00E676).copy(alpha = 0.65f) else Color(0xFF00E5FF).copy(alpha = 0.55f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("btn_sync_watch_mi_fitness")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Watch,
                                contentDescription = null,
                                tint = if (isWatchSynced) Color(0xFF00E676) else Color(0xFF00E5FF),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isWatchSynced) {
                                    "Sincronizar Relógio (Mi Fitness) • Oficial Ativo"
                                } else {
                                    "Sincronizar Relógio (Mi Fitness)"
                                },
                                color = if (isWatchSynced) Color(0xFF00E676) else Color(0xFF00E5FF),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Black,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = null,
                                tint = if (isWatchSynced) Color(0xFF00E676) else Color(0xFF00E5FF),
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }
            }

            // ==========================================
            // MAPA INTERATIVO PINTADO POR SPRINTS + LEGENDA HORIZONTAL
            // ==========================================
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = cardBg),
                        border = BorderStroke(1.dp, cardBorder)
                    ) {
                        key(currentSession.id) {
                            val inlineHrTimeline = remember(currentSession.hrTimeline) {
                                currentSession.hrTimeline.toHrTimelineList().filter { it in 45..230 }
                            }
                            val inlineAvgHr = currentSession.avgHr?.takeIf { it > 0 }
                                ?: inlineHrTimeline.takeIf { it.isNotEmpty() }?.average()?.safeRoundToInt()
                                ?: physiologyDossier.avgHeartRateBpm.takeIf { it > 0 }
                                ?: 0
                            val inlineTitle = remember(currentSession.sessionType, currentSession.isGentleMode, currentSession.weekNumber, currentSession.dayNumber) {
                                RunningProgramManager.getDisplayTitleForSessionType(
                                    sessionType = currentSession.sessionType,
                                    isGentleMode = currentSession.isGentleMode,
                                    weekNumber = currentSession.weekNumber,
                                    dayNumber = currentSession.dayNumber
                                )
                            }
                            AnimatedRouteMapView(
                                routePoints = routeCoords,
                                routePointItems = routePointItems,
                                allowSimulationFallback = currentSession.isSimulatedOrTestSession(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(275.dp)
                                    .clip(RoundedCornerShape(16.dp)),
                                isInteractiveReplay = true,
                                enableGestures = false,
                                initialStyle = MapTileStyle.SATELLITE,
                                externalStyle = MapTileStyle.SATELLITE,
                                onStyleChange = { selectedMapStyle = MapTileStyle.SATELLITE },
                                distanceMeters = currentSession.distanceMeters,
                                showFullscreenIcon = true,
                                onFullscreenClick = { showFullscreenMap = true },
                                sessionId = currentSession.id,
                                sessionTitle = inlineTitle,
                                sessionEndTimestampMs = currentSession.date,
                                sessionDurationSec = effectiveDurationSec,
                                avgPaceSecPerKm = effectivePaceSec,
                                avgHrBpm = inlineAvgHr,
                                hrTimelineList = inlineHrTimeline
                            )
                        }
                    }

                    RouteSprintLegendBar()
                }
            }

            // ==========================================
            // BLOCO 2 — CARD DE FREQUÊNCIA CARDÍACA (CURVA CONTÍNUA + 5 BARRAS DE ZONAS)
            // ==========================================
            item {
                var showDetailHrModal by remember { mutableStateOf(false) }
                RunDossierHeartRateCard(
                    dossier = physiologyDossier,
                    isLightMode = isLightReport,
                    onOpenDetailedModal = {
                        AudioSynth.playTickSound()
                        showDetailHrModal = true
                    }
                )
                if (showDetailHrModal) {
                    com.example.ui.components.FullscreenSessionHeartRateModal(
                        dossier = physiologyDossier,
                        athleteAge = userProfile.idade.takeIf { it in 10..100 },
                        hrSamples = currentSession.hrTimeline.toHrTimelineList(),
                        splits = currentSession.kmSplitsJson.toRecordedKmSplits(),
                        weekNumber = currentSession.weekNumber,
                        dayNumber = currentSession.dayNumber,
                        isLightMode = isLightReport,
                        onDismiss = { showDetailHrModal = false }
                    )
                }
            }

            // ==========================================
            // TABELA DE LAPS DE PISTA 400M (SE SESSÃO DE PISTA 400M)
            // ==========================================
            item {
                val track400mSplits = remember(currentSession.kmSplitsJson) {
                    currentSession.kmSplitsJson.toRecordedKmSplits().filter { isTrack400mLapSplit(it) }
                }
                if (track400mSplits.isNotEmpty() || com.example.engine.RunningProgramManager.isTrack400mSessionType(currentSession.sessionType)) {
                    Track400mLapsTableCard(
                        splits = if (track400mSplits.isNotEmpty()) track400mSplits else currentSession.kmSplitsJson.toRecordedKmSplits(),
                        title = "DOSSIÊ DE PISTA • LAPS DE 400M"
                    )
                }
            }

            // ==========================================
            // BLOCO 3 — CARD DE RITMO (GRÁFICO DE ÁREA DOS TIROS/CAMINHADA)
            // ==========================================
            item {
                RunDossierPaceAreaChartCard(
                    dossier = physiologyDossier,
                    isLightMode = isLightReport
                )
            }

            // ==========================================
            // BLOCO 4 — CARD DE RITMO POR KM (BARRAS DE PARCIAIS / SPLITS)
            // ==========================================
            item {
                RunDossierPacePerKmCard(
                    dossier = physiologyDossier,
                    isLightMode = isLightReport
                )
            }

            // ==========================================
            // BLOCO 5 — CARD DE CADÊNCIA (MÉDIA DE PPM vs. PPM MÁXIMO + DISPERSÃO/SEGMENTOS)
            // ==========================================
            item {
                RunDossierCadenceCard(
                    dossier = physiologyDossier,
                    isLightMode = isLightReport
                )
            }

            // ==========================================
            // BLOCO 6 — CARD DE PASSADA EM CM (PASSADA MÉDIA vs. MÁXIMA + CURVA BIOMECÂNICA)
            // ==========================================
            item {
                RunDossierStrideLengthCard(
                    dossier = physiologyDossier,
                    isLightMode = isLightReport
                )
            }

            // ==========================================
            // BLOCO 7 — CARD DE EFEITO DO TREINO (VITALIDADE / HÍBRIDO) & FONTE DE DADOS
            // ==========================================
            item {
                RunDossierTrainingEffectAndSourceCard(
                    dossier = physiologyDossier,
                    isLightMode = isLightReport
                )
            }

            // ==========================================
            // BLOCO 7.5 — PRONTIDÃO, CARGA INTERNA (sRPE) & AUDITORIA DE EXECUÇÃO (PACOTE 1)
            // ==========================================
            if (currentSession.preRunCheckIn != null ||
                currentSession.sRpeLoadUa != null ||
                currentSession.rpeCr10 != null ||
                currentSession.executionAudit != null ||
                currentSession.hrProvenance != null
            ) {
                item {
                    val checkIn = currentSession.preRunCheckIn
                    val audit = currentSession.executionAudit
                    val rpeVal = currentSession.rpeCr10 ?: currentSession.rpe.takeIf { it > 0 }
                    val sRpeUa = currentSession.sRpeLoadUa ?: com.example.data.model.calculateSrpeLoadUa(currentSession.durationSec, rpeVal)
                    val hrProv = currentSession.hrProvenance

                    val nonMetabolicReasons = buildList {
                        checkIn?.localizedPains?.forEach { p ->
                            val gaitTxt = if (p.altersGaitMechanics) ", altera passada" else ""
                            add("Dor pré-corrida em ${p.region.label} (${p.intensityCr10}/10$gaitTxt)")
                        }
                        audit?.reportedDeviationReasons?.filter {
                            it == com.example.data.model.InterruptionReason.GI_DISTRESS ||
                                it == com.example.data.model.InterruptionReason.EXTERNAL_STOP ||
                                it == com.example.data.model.InterruptionReason.MUSCULAR_FATIGUE_OR_PAIN ||
                                it == com.example.data.model.InterruptionReason.HEAT_HUMIDITY_STRESS
                        }?.forEach { r ->
                            add(r.label)
                        }
                    }

                    val hasConservativeVerdict = (checkIn?.localizedPains?.isNotEmpty() == true) ||
                        (audit?.reportedDeviationReasons?.any {
                            it == com.example.data.model.InterruptionReason.GI_DISTRESS ||
                                it == com.example.data.model.InterruptionReason.EXTERNAL_STOP
                        } == true)

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("card_scientific_execution_audit"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = cardBg),
                        border = BorderStroke(1.dp, cardBorder)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
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
                                    Box(
                                        modifier = Modifier
                                            .width(4.dp)
                                            .height(16.dp)
                                            .background(brandAccent, RoundedCornerShape(2.dp))
                                    )
                                    Text(
                                        text = "PRONTIDÃO, CARGA INTERNA & AUDITORIA DE EXECUÇÃO",
                                        color = primaryText,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Black,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (sRpeUa != null) {
                                    Surface(
                                        color = brandAccent.copy(alpha = 0.16f),
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(1.dp, brandAccent.copy(alpha = 0.55f))
                                    ) {
                                        Text(
                                            text = "sRPE: $sRpeUa UA",
                                            color = brandAccent,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Black,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }

                            // 1. Check-in Pré-Corrida
                            val checkInSummary = when (checkIn?.status) {
                                com.example.data.model.CheckInStatus.COMPLETED -> {
                                    val sleepStr = checkIn.sleepQuality?.label ?: "Não inf."
                                    val dispStr = checkIn.disposition?.label ?: "Não inf."
                                    val painsStr = if (checkIn.localizedPains.isEmpty()) {
                                        "Sem dor localizada"
                                    } else {
                                        checkIn.localizedPains.joinToString(", ") { p ->
                                            val gait = if (p.altersGaitMechanics) " • altera passada" else ""
                                            "${p.region.label} (${p.intensityCr10}/10$gait)"
                                        }
                                    }
                                    "Sono: $sleepStr | Disposição: $dispStr | Dor: $painsStr"
                                }
                                com.example.data.model.CheckInStatus.SKIPPED ->
                                    "Check-in pré-corrida pulado pelo atleta (sem inferência automática de normalidade)"
                                else ->
                                    "Não solicitado (sessão anterior à auditoria pré-corrida)"
                            }

                            Surface(
                                color = if (isLightReport) Color(0xFFF3F6FA) else Color(0xFF151922),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, cardBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "PRONTIDÃO PRÉ-CORRIDA",
                                        color = secondaryText,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = checkInSummary,
                                        color = primaryText,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            // 2. Carga Interna (RPE CR10 & sRPE) + Proveniência
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    color = if (isLightReport) Color(0xFFF3F6FA) else Color(0xFF151922),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, cardBorder),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Text(
                                            text = "CARGA INTERNA (BORG CR10)",
                                            color = secondaryText,
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = if (rpeVal != null) {
                                                "${com.example.data.model.borgCr10Label(rpeVal)} • ${sRpeUa ?: 0} UA"
                                            } else {
                                                "Não informada"
                                            },
                                            color = primaryText,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                if (hrProv != null) {
                                    Surface(
                                        color = if (isLightReport) Color(0xFFF3F6FA) else Color(0xFF151922),
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(1.dp, cardBorder),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(10.dp),
                                            verticalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            Text(
                                                text = "PROVENIÊNCIA DE FC",
                                                color = secondaryText,
                                                fontSize = 9.5.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "${hrProv.source.label} (Confiança: ${hrProv.confidence.label})",
                                                color = primaryText,
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }

                            // 3. Auditoria Planejado vs. Executado
                            if (audit != null) {
                                Surface(
                                    color = if (isLightReport) Color(0xFFF3F6FA) else Color(0xFF151922),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, cardBorder),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = "AUDITORIA PLANEJADO VS. EXECUTADO",
                                            color = secondaryText,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        val isFreeRunDetail = com.example.engine.RunningProgramManager.isEffectiveFreeRun(currentSession) ||
                                            currentSession.sessionType.equals("FREE_RUN", ignoreCase = true) ||
                                            currentSession.isGentleMode
                                        val auditSummaryText = if (isFreeRunDetail) {
                                            "Sessão livre (sem prescrição fixa)"
                                        } else {
                                            val pMin = (audit.plannedWorkDurationSec / 60.0).safeRoundToInt()
                                            val aMin = (audit.actualWorkRunningSec.coerceIn(0, currentSession.durationSec) / 60.0).safeRoundToInt()
                                            val wMin = (audit.unplannedWalkDurationSec.coerceIn(0, currentSession.durationSec) / 60.0).safeRoundToInt()
                                            "Prescrito: $pMin min corrida | Executado: $aMin min corrida (${wMin} min caminhada não planejada)"
                                        }
                                        Text(
                                            text = auditSummaryText,
                                            color = primaryText,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        if (audit.reportedDeviationReasons.isNotEmpty()) {
                                            Text(
                                                text = "Intercorrências reportadas: ${audit.reportedDeviationReasons.joinToString(", ") { it.label }}",
                                                color = Color(0xFFFFB300),
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }

                            // 4. Veredito Fisiológico Conservador Automático
                            if (hasConservativeVerdict) {
                                val reasonsListed = nonMetabolicReasons.ifEmpty { listOf("Intercorrência periférica/externa") }.joinToString("; ")
                                val loadText = sRpeUa ?: com.example.data.model.calculateSrpeLoadUa(currentSession.durationSec, rpeVal ?: 6) ?: 0
                                Surface(
                                    color = Color(0xFFFFB300).copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, Color(0xFFFFB300).copy(alpha = 0.55f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = "VEREDITO FISIOLÓGICO CONSERVADOR",
                                            color = Color(0xFFFFB300),
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Black
                                        )
                                        Text(
                                            text = "• Não há evidência suficiente de perda de condicionamento cardiorrespiratório.\n" +
                                                "• A execução foi impactada por fatores não-metabólicos/periféricos reportados ($reasonsListed).\n" +
                                                "• A sessão gerou carga interna de $loadText UA e não deve ser usada isoladamente para medir evolução de ritmo.\n" +
                                                "• A próxima sessão depende da recuperação muscular e clínica.",
                                            color = primaryText,
                                            fontSize = 11.5.sp,
                                            lineHeight = 16.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ==========================================
            // BLOCO 7.6 — TELEMETRIA DE INTERVALOS & EFICIÊNCIA FISIOLÓGICA (PACOTE 2)
            // ==========================================
            item {
                val resolvedIntervals = remember(currentSession, gpsPoints) {
                    runCatching {
                        com.example.data.model.buildExecutedIntervalsForSession(currentSession, gpsPoints)
                    }.getOrDefault(emptyList())
                }
                val resolvedAdvMetrics = remember(currentSession, gpsPoints) {
                    runCatching {
                        currentSession.advancedMetrics
                            ?: com.example.services.ai.AiPhysiologistAnalyzer.computeAdvancedRunningMetrics(currentSession, gpsPoints)
                    }.getOrElse {
                        com.example.data.model.AdvancedRunningMetrics()
                    }
                }
                var isIntervalTelemetryExpanded by remember { mutableStateOf(true) }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("card_interval_telemetry_efficiency"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    border = BorderStroke(1.dp, cardBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    AudioSynth.playTickSound()
                                    isIntervalTelemetryExpanded = !isIntervalTelemetryExpanded
                                }
                                .testTag("toggle_interval_telemetry_card"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(4.dp)
                                        .height(16.dp)
                                        .background(Color(0xFF00E676), RoundedCornerShape(2.dp))
                                )
                                Text(
                                    text = "Telemetria de Intervalos & Eficiência Fisiológica",
                                    color = primaryText,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Black,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Icon(
                                imageVector = if (isIntervalTelemetryExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (isIntervalTelemetryExpanded) "Recolher" else "Expandir",
                                tint = secondaryText
                            )
                        }

                        if (isIntervalTelemetryExpanded) {
                            // 1. Tabela/Lista de Blocos Executados (executedIntervals)
                            if (resolvedIntervals.isNotEmpty()) {
                                Surface(
                                    color = if (isLightReport) Color(0xFFF3F6FA) else Color(0xFF151922),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, cardBorder),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = "BLOCOS EXECUTADOS (${resolvedIntervals.size})",
                                            color = secondaryText,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        var previousWorkEndHr: Int? = null
                                        resolvedIntervals.forEach { block ->
                                            val durMin = block.actualDurationSec / 60
                                            val durSec = block.actualDurationSec % 60
                                            val durFormatted = String.format(Locale.US, "%d:%02d", durMin, durSec)
                                            val isWork = block.type == com.example.data.model.IntervalBlockType.RUN_WORK ||
                                                block.type == com.example.data.model.IntervalBlockType.UNSTRUCTURED

                                            if (isWork) {
                                                val pMin = block.avgPaceSecPerKm / 60
                                                val pSec = block.avgPaceSecPerKm % 60
                                                val paceFormatted = if (block.avgPaceSecPerKm > 0) {
                                                    String.format(Locale.US, "%d'%02d\"/km", pMin, pSec)
                                                } else "--'--\"/km"
                                                val hrPart = if (block.avgHrBpm != null && block.maxHrBpm != null) {
                                                    " • FC ${block.avgHrBpm}/${block.maxHrBpm} bpm"
                                                } else if (block.endHrBpm != null) {
                                                    " • FC ${block.endHrBpm} bpm"
                                                } else ""
                                                val cadPart = block.avgCadenceSpm?.let { " • $it spm" } ?: ""
                                                val walkAlertPart = if (block.unintendedWalkSec >= 8) {
                                                    " • ${block.unintendedWalkSec}s caminhada não-intencional"
                                                } else ""
                                                previousWorkEndHr = block.endHrBpm ?: block.maxHrBpm ?: block.avgHrBpm

                                                Surface(
                                                    color = brandAccent.copy(alpha = 0.10f),
                                                    shape = RoundedCornerShape(8.dp),
                                                    border = BorderStroke(1.dp, brandAccent.copy(alpha = 0.35f)),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Text(
                                                        text = "${block.blockLabel} ($durFormatted) • Pace $paceFormatted$hrPart$cadPart$walkAlertPart",
                                                        color = primaryText,
                                                        fontSize = 11.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                                    )
                                                }
                                            } else {
                                                val endHr = block.endHrBpm ?: block.avgHrBpm
                                                val deltaBpm = block.hrRecoveryDeltaBpm
                                                    ?: if (previousWorkEndHr != null && endHr != null && previousWorkEndHr!! > endHr) {
                                                        previousWorkEndHr!! - endHr
                                                    } else null
                                                val recText = buildString {
                                                    append("${block.blockLabel} ($durFormatted)")
                                                    if (endHr != null) {
                                                        append(" • FC final $endHr bpm")
                                                        if (deltaBpm != null && deltaBpm > 0) {
                                                            if (previousWorkEndHr != null) {
                                                                append(" (Queda: -$deltaBpm bpm | $previousWorkEndHr -> $endHr bpm)")
                                                            } else {
                                                                append(" (Queda: -$deltaBpm bpm)")
                                                            }
                                                        }
                                                    } else {
                                                        val pMin = block.avgPaceSecPerKm / 60
                                                        val pSec = block.avgPaceSecPerKm % 60
                                                        append(String.format(Locale.US, " • Pace %d'%02d\"/km", pMin, pSec))
                                                    }
                                                }
                                                Surface(
                                                    color = Color(0xFF00E676).copy(alpha = 0.08f),
                                                    shape = RoundedCornerShape(8.dp),
                                                    border = BorderStroke(1.dp, Color(0xFF00E676).copy(alpha = 0.30f)),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Text(
                                                        text = recText,
                                                        color = primaryText,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // 2. Resumo de Consistência e Indicadores (advancedMetrics)
                            Surface(
                                color = if (isLightReport) Color(0xFFF3F6FA) else Color(0xFF151922),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, cardBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "INDICADORES DE EFICIÊNCIA & CONSISTÊNCIA",
                                        color = secondaryText,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )

                                    // Eficiência Cardíaca (EF)
                                    val efVal = resolvedAdvMetrics.efficiencyFactorMeterPerBeat
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(
                                            text = if (efVal != null) {
                                                "Eficiência Cardíaca (EF): ${String.format(ptBr, "%.2f", efVal)} m/bat"
                                            } else {
                                                "Eficiência Cardíaca (EF): Requer FC válida (>80 bpm) e >= 5 min correndo"
                                            },
                                            color = primaryText,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Comparar apenas entre sessões de mesma estrutura, terreno e clima similar",
                                            color = secondaryText,
                                            fontSize = 10.5.sp,
                                            fontStyle = FontStyle.Italic
                                        )
                                    }

                                    // Deriva Cardíaca (Pa:Hr) com Gatekeeper Estrito
                                    val driftPct = resolvedAdvMetrics.cardiacDriftPct
                                    val isDriftApplicable = resolvedAdvMetrics.isCardiacDriftApplicable && driftPct != null
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        if (isDriftApplicable && driftPct != null) {
                                            val sign = if (driftPct >= 0.0) "+" else ""
                                            val driftColor = if (driftPct > 5.0) Color(0xFFFFB300) else Color(0xFF00E676)
                                            Text(
                                                text = "Deriva Cardíaca (Pa:Hr): $sign${String.format(ptBr, "%.1f", driftPct)}%",
                                                color = driftColor,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            if (driftPct > 5.0) {
                                                Text(
                                                    text = "Deriva cardíaca de +${String.format(ptBr, "%.1f", driftPct)}% na 2ª metade (referência de atenção: >5%). Pode refletir influência de temperatura/umidade, hidratação ou fadiga acumulada.",
                                                    color = Color(0xFFFFB300),
                                                    fontSize = 11.sp,
                                                    lineHeight = 15.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                        } else {
                                            val note = resolvedAdvMetrics.cardiacDriftGatekeeperNote
                                                ?: "Não aplicável a treino intervalado/fracionado ou menor que 20 min contínuos"
                                            Text(
                                                text = "Deriva Cardíaca (Pa:Hr): $note",
                                                color = secondaryText,
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }

                                    // Consistência dos Tiros (se houver múltiplos tiros)
                                    if (resolvedAdvMetrics.workIntervalsCount >= 2) {
                                        val spreadPct = resolvedAdvMetrics.workSpeedSpreadMaxMinPct ?: 0.0
                                        val dropPct = resolvedAdvMetrics.workSpeedDropFirstToLastPct ?: 0.0
                                        val dropSign = if (dropPct >= 0.0) "+" else ""
                                        Text(
                                            text = "Consistência dos Tiros: Mais rápido vs. Mais lento (Δ ${String.format(ptBr, "%.1f", spreadPct)}%) | 1º vs. Último tiro (Δ $dropSign${String.format(ptBr, "%.1f", dropPct)}%)",
                                            color = primaryText,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ==========================================
            // BLOCO E — TABELA DE SPLITS E PARCIAIS COM SELETOR DESTRAVADO (3 MODOS)
            // ==========================================
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
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
                                        .width(4.dp)
                                        .height(16.dp)
                                        .background(brandAccent, RoundedCornerShape(2.dp))
                                )
                                Text(
                                    text = "SPLITS E PARCIAIS (${selectedSplitMode.title.uppercase(ptBr)})",
                                    color = primaryText,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontStyle = FontStyle.Italic,
                                    letterSpacing = (-0.4).sp
                                )
                            }
                        }

                        // Seletor Destravado de Distâncias nos Splits: Por Bloco/Sprint | 500 m | 1 km (Principal)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            SplitSelectorMode.entries.forEach { mode ->
                                val isSelected = selectedSplitMode == mode
                                Surface(
                                    onClick = {
                                        selectedSplitMode = mode
                                        AudioSynth.playTickSound()
                                    },
                                    color = if (isSelected) brandAccent.copy(alpha = 0.22f) else Color.Transparent,
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, if (isSelected) brandAccent else cardBorder),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp)
                                    ) {
                                        Text(
                                            text = mode.title,
                                            color = if (isSelected) brandAccent else secondaryText,
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Black else FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }

                        // Cabeçalho da Tabela de Parciais (PACE e TEMPO da parcial alinhados sem erro de 1s)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (selectedSplitMode == SplitSelectorMode.BY_BLOCK) "BLOCO" else "PARCIAL",
                                color = secondaryText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(92.dp)
                            )
                            Text("PACE", color = secondaryText, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(68.dp))
                            Text("RITMO", color = secondaryText, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Text("TEMPO", color = secondaryText, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, modifier = Modifier.width(56.dp))
                            Text("BPM", color = secondaryText, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, modifier = Modifier.width(42.dp))
                        }

                        HorizontalDivider(color = cardBorder, thickness = 1.dp)

                        // Linhas da Tabela
                        if (displaySplits.isEmpty()) {
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
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                for (split in displaySplits) {
                                    StravaSplitRow(
                                        label = split.splitLabel,
                                        paceSec = split.paceSecPerKm,
                                        durationSec = split.durationSec,
                                        avgHr = split.avgHr,
                                        phaseTag = split.phaseTag,
                                        basePaceSec = effectivePaceSec,
                                        primaryText = primaryText,
                                        secondaryText = secondaryText,
                                        brandAccent = brandAccent,
                                        isLight = isLightReport
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ==========================================
            // BLOCO E1.4 — AUTO-REGULAÇÃO DA PLANILHA E VAM (ESTILO RUNNA)
            // ==========================================
            item {
                com.example.ui.components.RunnaPaceEvolutionBanner(
                    session = currentSession,
                    sprintAnalytics = sprintAnalytics,
                    allRunningSessions = allRunningSessions,
                    athleteAge = userProfile.idade,
                    baselinePaceMinPerKm = userProfile.baselinePaceMinPerKm,
                    calibratedMaxHrBpm = userProfile.fcMaxCalibradaBpm,
                    onApplyNewBaselinePace = { newPaceMin ->
                        viewModel.saveRunnerBaselinePace(newPaceMin)
                        android.widget.Toast.makeText(
                            context,
                            "Planilha e VAM atualizadas com sucesso!",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                )
            }

            // ==========================================
            // BLOCO E1.5 — TREINADOR FISIOLOGISTA IA PÓS-TREINO (MAX RUN + CRUZAMENTO HÍBRIDO 48H)
            // ==========================================
            item {
                com.example.ui.components.AiPhysiologistCard(
                    sessionId = currentSession.id,
                    isRunningMode = true,
                    initialCachedReport = currentSession.aiCoachReport,
                    defaultHasHrSensor = (currentSession.avgHr != null && currentSession.avgHr > 0) ||
                        !currentSession.hrTimeline.isNullOrBlank(),
                    isLightMode = isLightReport,
                    onGenerateAnalysis = { checkIn, forceCloud ->
                        val result = com.example.services.ai.AiPhysiologistAnalyzer.analyzeRunningSession(
                            context = context,
                            session = currentSession,
                            allRunningSessions = allRunningSessions,
                            recentGymSessions = allGymSessions,
                            userProfile = userProfile,
                            sprintAnalytics = sprintAnalytics,
                            splits = displaySplits,
                            gpsPoints = gpsPoints,
                            checkIn = checkIn,
                            forceCloudRegenerate = forceCloud
                        )
                        viewModel.saveRunningSessionAiReport(currentSession.id, result.reportText)
                        result
                    }
                )
            }

            // ==========================================
            // BLOCO E2 — EXIBIÇÃO E SELEÇÃO DE EQUIPAMENTO (TÊNIS UTILIZADO) NO FINAL DA PÁGINA
            // ==========================================
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    border = BorderStroke(1.dp, cardBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showShoeDialog = true
                                AudioSynth.playTickSound()
                            }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(brandAccent.copy(alpha = 0.18f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.DirectionsRun,
                                    contentDescription = null,
                                    tint = brandAccent,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "EQUIPAMENTO (TÊNIS UTILIZADO)",
                                    color = secondaryText,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = currentShoeDisplay,
                                    color = primaryText,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Surface(
                            color = brandAccent.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "ALTERAR",
                                color = brandAccent,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }

            // ==========================================
            // BLOCO F — BOTÕES DE EXPORTAÇÃO E COMPARTILHAMENTO
            // ==========================================
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Botão Principal de Print Longo em Imagem Única
                    Button(
                        onClick = { triggerLongScreenshot() },
                        enabled = !isGeneratingLongScreenshot,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = brandAccent),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (isGeneratingLongScreenshot) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    "Renderizando Mapa Real e Print Longo...",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            } else {
                                Icon(Icons.Default.Download, contentDescription = null, tint = Color.White)
                                Text(
                                    "SALVAR PRINT LONGO",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Ações Secundárias: Stories e Dados Brutos
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                AudioSynth.playTickSound()
                                viewModel.selectedShareSession.value = currentSession
                                viewModel.navigateToScreen(AppScreen.SHARE_CARD)
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, cardBorder),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = primaryText)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text("CRIAR CARD", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                AudioSynth.playTickSound()
                                RunningCsvExporter.exportHistoryToCsv(
                                    context = context,
                                    history = listOf(currentSession),
                                    userProfile = userProfile,
                                    runningShoes = runningShoes
                                )
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, cardBorder),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = primaryText)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text("PLANILHA CSV", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal / BottomSheet de Minha Garagem (Escolha e Gestão de Tênis)
    if (showShoeDialog) {
        RunningShoesBottomSheet(
            shoes = runningShoes,
            onAddShoe = { shoe -> viewModel.addRunningShoe(shoe) },
            onUpdateShoe = { shoe -> viewModel.updateRunningShoe(shoe) },
            onSetDefault = { shoeId -> viewModel.setDefaultRunningShoe(shoeId) },
            onRetireShoe = { shoeId -> viewModel.retireRunningShoe(shoeId) },
            onDeleteShoe = { shoeId -> viewModel.deleteRunningShoe(shoeId) },
            onDismiss = { showShoeDialog = false },
            selectionTargetSession = currentSession,
            onSelectShoeForSession = { shoeName ->
                viewModel.updateSessionShoe(currentSession.id, shoeName)
                Toast.makeText(context, "Tênis vinculado: $shoeName", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (showSaveOfficialRaceDialog) {
        val existingLinked = remember(officialRaces, currentSession.id) {
            officialRaces.firstOrNull { it.linkedRunSessionId == currentSession.id }
        }
        com.example.ui.components.SaveOfficialRaceDialog(
            initialSession = currentSession,
            existingRecord = existingLinked,
            userProfile = userProfile,
            availableShoes = runningShoes,
            onSave = { record ->
                viewModel.saveOfficialRaceRecord(record)
                showSaveOfficialRaceDialog = false
                Toast.makeText(
                    context,
                    "Salva em Provas & Evolução: ${record.raceName}!",
                    Toast.LENGTH_SHORT
                ).show()
            },
            onDelete = { raceId ->
                viewModel.deleteOfficialRaceRecord(raceId)
                showSaveOfficialRaceDialog = false
                Toast.makeText(
                    context,
                    "Removida de Provas Oficiais",
                    Toast.LENGTH_SHORT
                ).show()
            },
            onDismiss = { showSaveOfficialRaceDialog = false }
        )
    }

    if (showOfficialRacesVaultDialog) {
        com.example.ui.components.OfficialRacesVaultDialog(
            officialRaces = officialRaces,
            runningSessions = allRunningSessions,
            runningShoes = runningShoes,
            userProfile = userProfile,
            onSaveRace = { record -> viewModel.saveOfficialRaceRecord(record) },
            onDeleteRace = { raceId -> viewModel.deleteOfficialRaceRecord(raceId) },
            onOpenLinkedSession = { targetSession ->
                showOfficialRacesVaultDialog = false
                viewModel.selectRunningDetailSession(targetSession)
            },
            onDismiss = { showOfficialRacesVaultDialog = false }
        )
    }

    if (showSyncWatchDialog) {
        SyncWatchMetricsModalDialog(
            session = currentSession,
            dossier = physiologyDossier,
            onDismiss = { showSyncWatchDialog = false },
            onHealthConnectSync = { updatedSession ->
                viewModel.saveRunningSession(updatedSession)
                viewModel.selectRunningDetailSession(updatedSession)
            },
            onSave = { distKmVal, avgHrVal, maxHrVal, stepsVal, cadVal, strideVal, activeKcalVal, totalKcalVal ->
                viewModel.syncWatchMetricsForRunningSession(
                    sessionId = currentSession.id,
                    officialDistanceKm = distKmVal,
                    avgHrBpm = avgHrVal,
                    maxHrBpm = maxHrVal,
                    totalSteps = stepsVal,
                    avgCadenceSpm = cadVal,
                    avgStrideCm = strideVal,
                    activeCaloriesKcal = activeKcalVal,
                    totalCaloriesKcal = totalKcalVal
                )
                showSyncWatchDialog = false
                Toast.makeText(
                    context,
                    "Métricas do relógio (Mi Fitness) sincronizadas com sucesso!",
                    Toast.LENGTH_SHORT
                ).show()
            }
        )
    }

    if (showFullscreenMap) {
        val routeCoords = remember(routePointItems) {
            routePointItems.map { Pair(it.lat, it.lon) }
        }
        val timelineHrValues = remember(currentSession.hrTimeline) {
            currentSession.hrTimeline.toHrTimelineList().filter { it in 45..230 }
        }
        val splitHrValues = remember(currentSession.kmSplitsJson) {
            currentSession.kmSplitsJson.toRecordedKmSplits().mapNotNull { it.avgHr?.takeIf { hr -> hr > 0 } }
        }
        val resolvedSessionBpm = currentSession.avgHr?.takeIf { it > 0 }
            ?: currentSession.maxHr?.takeIf { it > 0 }
            ?: timelineHrValues.takeIf { it.isNotEmpty() }?.average()?.safeRoundToInt()
            ?: splitHrValues.takeIf { it.isNotEmpty() }?.average()?.safeRoundToInt()
            ?: physiologyDossier.avgHeartRateBpm.takeIf { it > 0 }
            ?: physiologyDossier.maxHeartRateBpm.takeIf { it > 0 }
            ?: 0
        val fullscreenTitle = remember(currentSession.sessionType, currentSession.isGentleMode, currentSession.weekNumber, currentSession.dayNumber) {
            RunningProgramManager.getDisplayTitleForSessionType(
                sessionType = currentSession.sessionType,
                isGentleMode = currentSession.isGentleMode,
                weekNumber = currentSession.weekNumber,
                dayNumber = currentSession.dayNumber
            )
        }
        com.example.ui.components.FullscreenMapDialog(
            routePoints = routeCoords,
            routePointItems = routePointItems,
            allowSimulationFallback = currentSession.isSimulatedOrTestSession(),
            distKmStr = String.format(ptBr, "%.2f km", distKm),
            paceStr = paceText,
            bpmStr = if (resolvedSessionBpm > 0) "$resolvedSessionBpm bpm" else "-- bpm",
            initialStyle = MapTileStyle.SATELLITE,
            externalStyle = MapTileStyle.SATELLITE,
            onStyleChange = { selectedMapStyle = MapTileStyle.SATELLITE },
            distanceMeters = currentSession.distanceMeters,
            onDismiss = { showFullscreenMap = false },
            sessionId = currentSession.id,
            sessionTitle = fullscreenTitle,
            sessionEndTimestampMs = currentSession.date,
            sessionDurationSec = effectiveDurationSec,
            avgPaceSecPerKm = effectivePaceSec,
            avgHrBpm = resolvedSessionBpm,
            hrTimelineList = timelineHrValues
        )
    }
}

@Composable
fun StravaSplitRow(
    label: String,
    paceSec: Int,
    durationSec: Int,
    avgHr: Int?,
    basePaceSec: Int,
    primaryText: Color,
    secondaryText: Color,
    brandAccent: Color,
    isLight: Boolean,
    phaseTag: String? = null
) {
    val pMin = paceSec / 60
    val pSec = paceSec % 60
    val paceFormatted = if (paceSec > 0) String.format(Locale.US, "%d'%02d\"", pMin, pSec) else "--:--"

    val tMin = durationSec / 60
    val tSec = durationSec % 60
    val durFormatted = String.format(Locale.US, "%02d:%02d", tMin, tSec)

    val validBase = if (basePaceSec > 0) basePaceSec else 450
    val ratio = if (paceSec > 0) (validBase.toFloat() / paceSec.toFloat()).safeCoerceIn(0.4f, 1.4f) else 0.8f

    val prColor = if (isLight) Color(0xFFD97706) else Color(0xFFFFD700)
    val runColor = if (isLight) Color(0xFF059669) else Color(0xFF00E676)
    val walkColor = if (isLight) Color(0xFF0284C7) else Color(0xFF00B0FF)

    val barColor = when (phaseTag?.uppercase(Locale.US)) {
        "PR" -> prColor
        "RUN" -> runColor
        "WALK" -> walkColor
        "WARMUP", "COOL_DOWN" -> Color(0xFF94A3B8)
        else -> if (ratio >= 1.05f) runColor else if (ratio >= 0.95f) brandAccent else walkColor
    }

    val labelColor = when (phaseTag?.uppercase(Locale.US)) {
        "PR" -> prColor
        "WALK" -> walkColor
        "WARMUP", "COOL_DOWN" -> secondaryText
        else -> primaryText
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Bloco / Km
        Text(
            text = label,
            color = labelColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontStyle = FontStyle.Italic,
            letterSpacing = (-0.4).sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(92.dp)
        )

        // Pace Real
        Text(
            text = paceFormatted,
            color = primaryText,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            fontStyle = FontStyle.Italic,
            letterSpacing = (-0.5).sp,
            modifier = Modifier.width(64.dp)
        )

        // Barra Proporcional à Velocidade / Fase
        Box(
            modifier = Modifier
                .weight(1f)
                .height(6.dp)
                .padding(end = 8.dp)
                .clip(CircleShape)
                .background(if (isLight) Color(0xFFE2E8F0) else Color(0xFF222634))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth((ratio / 1.4f).safeCoerceIn(0.18f, 1f))
                    .background(barColor, CircleShape)
            )
        }

        // Tempo Parcial Exato
        Text(
            text = durFormatted,
            color = secondaryText,
            fontSize = 12.sp,
            fontWeight = FontWeight.Normal,
            textAlign = TextAlign.End,
            modifier = Modifier.width(50.dp)
        )

        // BPM
        Text(
            text = avgHr?.toString() ?: "--",
            color = primaryText,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontStyle = FontStyle.Italic,
            letterSpacing = (-0.5).sp,
            textAlign = TextAlign.End,
            modifier = Modifier.width(42.dp)
        )
    }
}

@Composable
private fun SyncWatchMetricsModalDialog(
    session: com.example.data.model.RunningSession,
    dossier: com.example.data.model.RunPhysiologyDossier,
    onDismiss: () -> Unit,
    onHealthConnectSync: (com.example.data.model.RunningSession) -> Unit,
    onSave: (
        officialDistanceKm: Float,
        avgHrBpm: Int?,
        maxHrBpm: Int?,
        totalSteps: Int?,
        avgCadenceSpm: Int?,
        avgStrideCm: Int?,
        activeCaloriesKcal: Int?,
        totalCaloriesKcal: Int?
    ) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val bleManager = remember { HeartRateBleManager.getInstance(context) }

    val adv = session.advancedMetrics
    var distanceKmInput by remember(session.id) {
        mutableStateOf(String.format(Locale.US, "%.2f", dossier.distanceKm))
    }
    var avgHrInput by remember(session.id) {
        val v = session.avgHr?.takeIf { it > 0 } ?: dossier.avgHeartRateBpm.takeIf { it > 0 }
        mutableStateOf(v?.toString() ?: "")
    }
    var maxHrInput by remember(session.id) {
        val v = session.maxHr?.takeIf { it > 0 } ?: dossier.maxHeartRateBpm.takeIf { it > 0 }
        mutableStateOf(v?.toString() ?: "")
    }
    var stepsInput by remember(session.id) {
        val v = adv?.watchSyncedSteps ?: dossier.totalSteps.takeIf { it > 0 }
        mutableStateOf(v?.toString() ?: "")
    }
    var cadenceInput by remember(session.id) {
        val v = adv?.watchSyncedCadenceSpm ?: dossier.avgCadenceSpm.takeIf { it > 0 }
        mutableStateOf(v?.toString() ?: "")
    }
    var strideInput by remember(session.id) {
        val v = adv?.watchSyncedStrideCm ?: dossier.avgStrideLengthCm.takeIf { it > 0 }
        mutableStateOf(v?.toString() ?: "")
    }
    var activeKcalInput by remember(session.id) {
        val v = adv?.watchSyncedActiveKcal ?: dossier.activeCaloriesKcal.takeIf { it > 0 }
        mutableStateOf(v?.toString() ?: "")
    }
    var totalKcalInput by remember(session.id) {
        val v = adv?.watchSyncedTotalKcal ?: dossier.totalCaloriesKcal.takeIf { it > 0 }
        mutableStateOf(v?.toString() ?: "")
    }

    var isPullingHc by remember { mutableStateOf(false) }
    var hcFeedbackMessage by remember { mutableStateOf<String?>(null) }
    var hcFeedbackIsSuccess by remember { mutableStateOf(false) }

    suspend fun performHealthConnectWindowPull() {
        isPullingHc = true
        hcFeedbackMessage = null
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                hcFeedbackIsSuccess = false
                hcFeedbackMessage = "Health Connect requer Android 8.0 ou superior."
                return
            }
            if (!bleManager.isHealthConnectAvailable()) {
                hcFeedbackIsSuccess = false
                hcFeedbackMessage = "Health Connect não está disponível neste aparelho."
                return
            }
            val startMs = session.date
            val endMs = session.date + (session.durationSec.coerceAtLeast(60) * 1000L)
            val snap = runCatching {
                bleManager.fetchWorkoutWindowFromHealthConnect(startMs, endMs)
            }.getOrNull()

            if (snap != null) {
                if (snap.avgHr > 0) avgHrInput = snap.avgHr.toString()
                if (snap.maxHr > 0) maxHrInput = snap.maxHr.toString()
                if (snap.steps > 0) stepsInput = snap.steps.toString()
                if (snap.activeCalories > 0) activeKcalInput = snap.activeCalories.toString()

                val updatedAdvanced = (session.advancedMetrics ?: com.example.data.model.AdvancedRunningMetrics()).copy(
                    watchSyncedSteps = snap.steps.takeIf { it > 0 } ?: session.advancedMetrics?.watchSyncedSteps,
                    watchSyncedActiveKcal = snap.activeCalories.takeIf { it > 0 } ?: session.advancedMetrics?.watchSyncedActiveKcal
                )
                val updatedSession = session.copy(
                    avgHr = snap.avgHr.takeIf { it > 0 } ?: session.avgHr,
                    maxHr = snap.maxHr.takeIf { it > 0 } ?: session.maxHr,
                    hrTimeline = snap.hrTimeline.takeIf { it.isNotBlank() } ?: session.hrTimeline,
                    hrProvenance = com.example.data.model.MetricProvenance(
                        source = com.example.data.model.DataSourceType.OPTICAL_PPG_WATCH,
                        deviceName = "HEALTH_CONNECT_MI_FITNESS",
                        confidence = com.example.data.model.DataConfidenceLevel.HIGH
                    ),
                    advancedMetrics = updatedAdvanced
                )
                onHealthConnectSync(updatedSession)
                hcFeedbackIsSuccess = true
                hcFeedbackMessage = if (snap.sampleCount > 0) {
                    "✓ Sincronizado: ${snap.avgHr} bpm méd / ${snap.maxHr} máx (${snap.sampleCount} amostras)"
                } else {
                    "✓ Sincronizado: ${snap.steps} passos • ${snap.activeCalories} kcal ativas"
                }
            } else {
                hcFeedbackIsSuccess = false
                hcFeedbackMessage = "Nenhum registro encontrado no Health Connect para esta janela de horário"
            }
        } finally {
            isPullingHc = false
        }
    }

    val hcPermissionLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract()
    ) {
        scope.launch {
            if (bleManager.hasHealthConnectPermissions()) {
                performHealthConnectWindowPull()
            } else {
                hcFeedbackIsSuccess = false
                hcFeedbackMessage = "Permissões do Health Connect não concedidas."
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF131720),
        shape = RoundedCornerShape(18.dp),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Watch,
                    contentDescription = null,
                    tint = Color(0xFF00E5FF),
                    modifier = Modifier.size(22.dp)
                )
                Column {
                    Text(
                        text = "Sincronizar Relógio (Mi Fitness)",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = "Ajuste oficial PPG/IMU • Mapa GPS preservado",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        if (isPullingHc) return@Button
                        scope.launch {
                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                                hcFeedbackIsSuccess = false
                                hcFeedbackMessage = "Health Connect requer Android 8.0 ou superior."
                                return@launch
                            }
                            if (!bleManager.isHealthConnectAvailable()) {
                                hcFeedbackIsSuccess = false
                                hcFeedbackMessage = "Health Connect não está disponível neste aparelho."
                                return@launch
                            }
                            val hasPerms = runCatching { bleManager.hasHealthConnectPermissions() }.getOrDefault(false)
                            if (!hasPerms) {
                                runCatching {
                                    com.example.MainActivity.markIntentionalExternalNavigation()
                                    hcPermissionLauncher.launch(bleManager.getHealthConnectRequiredPermissions())
                                }.onFailure {
                                    hcFeedbackIsSuccess = false
                                    hcFeedbackMessage = "Não foi possível abrir as permissões do Health Connect."
                                }
                            } else {
                                performHealthConnectWindowPull()
                            }
                        }
                    },
                    enabled = !isPullingHc,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF00C853),
                        disabledContainerColor = Color(0xFF1E293B)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("btn_pull_hc_workout_window")
                ) {
                    if (isPullingHc) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "CONSULTANDO HEALTH CONNECT...",
                            color = Color.White,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Black
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Puxar dados do Health Connect",
                            tint = Color(0xFF041C0F),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "PUXAR DO HEALTH CONNECT (JANELA DO TREINO)",
                            color = Color(0xFF041C0F),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                if (hcFeedbackMessage != null) {
                    Surface(
                        color = if (hcFeedbackIsSuccess) Color(0xFF064E3B) else Color(0xFF3F1D2B),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(
                            1.dp,
                            if (hcFeedbackIsSuccess) Color(0xFF10B981) else Color(0xFFF43F5E)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("hc_workout_sync_feedback")
                    ) {
                        Text(
                            text = hcFeedbackMessage.orEmpty(),
                            color = Color.White,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }

                OutlinedTextField(
                    value = distanceKmInput,
                    onValueChange = { distanceKmInput = it },
                    label = { Text("Distância Oficial do Relógio (km)", fontSize = 11.sp) },
                    placeholder = { Text("Ex: 4.30", fontSize = 12.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF00E5FF),
                        unfocusedBorderColor = Color(0xFF334155),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedLabelColor = Color(0xFF00E5FF),
                        unfocusedLabelColor = Color(0xFF94A3B8)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = avgHrInput,
                        onValueChange = { avgHrInput = it.filter { ch -> ch.isDigit() } },
                        label = { Text("FC Média (bpm)", fontSize = 10.5.sp) },
                        placeholder = { Text("Ex: 171", fontSize = 12.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFFF5252),
                            unfocusedBorderColor = Color(0xFF334155),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedLabelColor = Color(0xFFFF5252),
                            unfocusedLabelColor = Color(0xFF94A3B8)
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = maxHrInput,
                        onValueChange = { maxHrInput = it.filter { ch -> ch.isDigit() } },
                        label = { Text("FC Máx (bpm)", fontSize = 10.5.sp) },
                        placeholder = { Text("Ex: 191", fontSize = 12.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFFF5252),
                            unfocusedBorderColor = Color(0xFF334155),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedLabelColor = Color(0xFFFF5252),
                            unfocusedLabelColor = Color(0xFF94A3B8)
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = stepsInput,
                        onValueChange = { stepsInput = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Passos Totais", fontSize = 10.5.sp) },
                        placeholder = { Text("Ex: 4170", fontSize = 12.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF00E676),
                            unfocusedBorderColor = Color(0xFF334155),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedLabelColor = Color(0xFF00E676),
                            unfocusedLabelColor = Color(0xFF94A3B8)
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = cadenceInput,
                        onValueChange = { cadenceInput = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Cadência (spm)", fontSize = 10.5.sp) },
                        placeholder = { Text("Ex: 143", fontSize = 12.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF00E676),
                            unfocusedBorderColor = Color(0xFF334155),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedLabelColor = Color(0xFF00E676),
                            unfocusedLabelColor = Color(0xFF94A3B8)
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = strideInput,
                        onValueChange = { strideInput = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Passada (cm)", fontSize = 10.5.sp) },
                        placeholder = { Text("Ex: 84", fontSize = 12.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF00E5FF),
                            unfocusedBorderColor = Color(0xFF334155),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedLabelColor = Color(0xFF00E5FF),
                            unfocusedLabelColor = Color(0xFF94A3B8)
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = activeKcalInput,
                        onValueChange = { activeKcalInput = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Kcal Ativas", fontSize = 10.5.sp) },
                        placeholder = { Text("Ex: 262", fontSize = 12.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFFFB300),
                            unfocusedBorderColor = Color(0xFF334155),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedLabelColor = Color(0xFFFFB300),
                            unfocusedLabelColor = Color(0xFF94A3B8)
                        ),
                        modifier = Modifier.weight(0.95f)
                    )
                    OutlinedTextField(
                        value = totalKcalInput,
                        onValueChange = { totalKcalInput = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Kcal Totais", fontSize = 10.5.sp) },
                        placeholder = { Text("Ex: 298", fontSize = 12.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFFFB300),
                            unfocusedBorderColor = Color(0xFF334155),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedLabelColor = Color(0xFFFFB300),
                            unfocusedLabelColor = Color(0xFF94A3B8)
                        ),
                        modifier = Modifier.weight(0.95f)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsedDistKm = distanceKmInput.replace(',', '.').toFloatOrNull() ?: dossier.distanceKm
                    val parsedAvgHr = avgHrInput.toIntOrNull()
                    val parsedMaxHr = maxHrInput.toIntOrNull()
                    val parsedSteps = stepsInput.toIntOrNull()
                    val parsedCadence = cadenceInput.toIntOrNull()
                    val parsedStride = strideInput.toIntOrNull()
                    val parsedActiveKcal = activeKcalInput.toIntOrNull()
                    val parsedTotalKcal = totalKcalInput.toIntOrNull()

                    onSave(
                        parsedDistKm,
                        parsedAvgHr,
                        parsedMaxHr,
                        parsedSteps,
                        parsedCadence,
                        parsedStride,
                        parsedActiveKcal,
                        parsedTotalKcal
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00B0FF)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    text = "SALVAR & RECALCULAR",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = Color(0xFF94A3B8), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    )
}
