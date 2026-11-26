package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.*
import java.text.SimpleDateFormat
import java.util.Date
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.deduplicatedRunningSessions
import com.example.ui.theme.OnSurfaceDim
import com.example.ui.theme.Primary
import com.example.ui.theme.Surface
import com.example.ui.theme.SurfaceDim
import com.example.ui.viewmodel.AppScreen
import com.example.ui.viewmodel.MainViewModel
import java.util.Locale

@Composable
fun RunningHistoryScreen(viewModel: MainViewModel) {
    androidx.activity.compose.BackHandler {
        viewModel.popBackScreen(fallback = AppScreen.HOME)
    }

    val ptBr = remember { Locale.forLanguageTag("pt-BR") }
    val rawSessions by viewModel.runningSessions.collectAsState()
    val sessions = remember(rawSessions) { rawSessions.deduplicatedRunningSessions() }
    val sortedSessions = remember(sessions) { sessions.sortedByDescending { it.date } }

    val totalDistanceMeters = sessions.sumOf { it.distanceMeters.toLong() }
    val totalDistanceKm = totalDistanceMeters / 1000f
    val totalDurationSec = sessions.sumOf { it.durationSec.toLong() }
    val totalHours = totalDurationSec / 3600
    val totalMinutes = (totalDurationSec % 3600) / 60

    val avgPaceSec = if (totalDistanceMeters > 0) {
        ((totalDurationSec * 1000) / totalDistanceMeters).toInt()
    } else {
        0
    }
    val avgPaceText = if (avgPaceSec > 0) {
        val pMin = avgPaceSec / 60
        val pSec = avgPaceSec % 60
        String.format(Locale.US, "%d'%02d\"/km", pMin, pSec)
    } else {
        "--:--"
    }

    val monthFormat = remember { java.text.SimpleDateFormat("MMMM yyyy", ptBr) }
    val sessionsByMonth = remember(sortedSessions) {
        val linked = java.util.LinkedHashMap<String, MutableList<com.example.data.model.RunningSession>>()
        sortedSessions.forEach { session ->
            val raw = monthFormat.format(java.util.Date(session.date))
            val header = raw.replaceFirstChar { if (it.isLowerCase()) it.titlecase(ptBr) else it.toString() }
            linked.getOrPut(header) { mutableListOf() }.add(session)
        }
        linked
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    val appMode by viewModel.appMode.collectAsState()
    val runningShoes by viewModel.runningShoes.collectAsState()
    val officialRaces by viewModel.officialRaces.collectAsState()
    val gymSessions by viewModel.gymWorkoutSessions.collectAsState()
    val userProfile by viewModel.userProfile.collectAsState()
    val historyTab by viewModel.historyTab.collectAsState()

    androidx.compose.runtime.LaunchedEffect(appMode) {
        if (appMode == com.example.data.model.AppMode.RUNNING && viewModel.historyTab.value != 0) {
            viewModel.historyTab.value = 0
        }
        runCatching {
            com.example.services.RunTracker.recoverUnfinishedWorkoutCheckpointIfNeeded(context)
        }
    }

    var showShoesBottomSheet by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var showRecoverWorkoutDialog by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var showOfficialRacesVault by remember { mutableStateOf(false) }
    var showManualOfficialRaceModal by remember { mutableStateOf(false) }
    var sessionToConvertOfficialRace by remember { mutableStateOf<com.example.data.model.RunningSession?>(null) }
    var selectedGymSessionDetail by remember { mutableStateOf<com.example.data.model.GymWorkoutSession?>(null) }
    var sessionForShoeEdit by remember { mutableStateOf<com.example.data.model.RunningSession?>(null) }
    var sessionToClassify by remember { mutableStateOf<com.example.data.model.RunningSession?>(null) }
    var runningSessionToDelete by remember { mutableStateOf<com.example.data.model.RunningSession?>(null) }
    var gymSessionToDelete by remember { mutableStateOf<com.example.data.model.GymWorkoutSession?>(null) }
    var expandedMobilitySessionIds by remember { mutableStateOf(setOf<String>()) }

    com.example.ui.components.ProvideRunFontScale {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        // Header com Título Completo sem truncamento e ações alinhadas logo abaixo
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (historyTab == 0) "Histórico de Corridas" else "Histórico de Musculação",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                if (historyTab == 0) {
                    com.example.ui.components.RunFontScaleToggleButton(compact = true)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (historyTab == 0) "Registro das sessões e evolução" else "${gymSessions.size} treinos registrados",
                    color = OnSurfaceDim,
                    fontSize = 11.sp,
                    maxLines = 1,
                    softWrap = false,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                )

                // Ações específicas da aba ([ Tênis ] e [ Exportar CSV ] alinhados sem quebrar linha)
                if (historyTab == 0) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = com.example.ui.theme.RunColors.surface,
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, com.example.ui.theme.RunColors.brandText.copy(alpha = 0.45f)),
                            modifier = Modifier.clickable {
                                com.example.utils.AudioSynth.playTickSound()
                                showRecoverWorkoutDialog = true
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "+ Registrar",
                                    color = com.example.ui.theme.RunColors.brandText,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }

                        Surface(
                            color = com.example.ui.theme.RunColors.surface,
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, com.example.ui.theme.RunColors.border),
                            modifier = Modifier.clickable {
                                com.example.utils.AudioSynth.playTickSound()
                                showShoesBottomSheet = true
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "Tênis",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }

                        Surface(
                            color = com.example.ui.theme.RunColors.surface,
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, com.example.ui.theme.RunColors.border),
                            modifier = Modifier.clickable {
                                com.example.utils.AudioSynth.playTickSound()
                                com.example.utils.RunningCsvExporter.exportHistoryToCsv(
                                    context = context,
                                    history = sessions,
                                    userProfile = userProfile,
                                    runningShoes = runningShoes
                                )
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "Exportar CSV",
                                    color = com.example.ui.theme.RunColors.brandText,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }
            }
        }

        // Pill Tabs: [ Corridas ] | [ Musculação ]
        Surface(
            color = Color(0xFF161922),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, Color(0xFF262B38)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Tab Corridas
                Surface(
                    onClick = {
                        viewModel.historyTab.value = 0
                        com.example.utils.AudioSynth.playTickSound()
                    },
                    color = if (historyTab == 0) Color(0xFF0EA5E9) else Color.Transparent,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(vertical = 8.dp)
                    ) {
                        Text(
                            text = "Corridas",
                            color = if (historyTab == 0) Color.White else Color(0xFF94A3B8),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Tab Musculação
                Surface(
                    onClick = {
                        viewModel.historyTab.value = 1
                        com.example.utils.AudioSynth.playTickSound()
                    },
                    color = if (historyTab == 1) Color(0xFF0EA5E9) else Color.Transparent,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(vertical = 8.dp)
                    ) {
                        Text(
                            text = "Musculação",
                            color = if (historyTab == 1) Color.White else Color(0xFF94A3B8),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        if (historyTab == 0) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(top = 6.dp, bottom = 96.dp)
            ) {
                item(key = "run_macro_dossier_card") {
                    com.example.ui.components.PeriodMacroAnalysisEntryCard(
                        runningSessions = sessions,
                        gymSessions = gymSessions,
                        userAgeYears = userProfile.idade.takeIf { it in 10..100 },
                        initiallyExpanded = false
                    )
                }

                item(key = "run_metrics_summary_card") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = com.example.ui.theme.RunColors.surface),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, com.example.ui.theme.RunColors.border)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceAround,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Total Runs
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "${sessions.size}",
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Black
                                )
                                Text(
                                    text = "Sessões",
                                    color = com.example.ui.theme.RunColors.textSecondary,
                                    fontSize = 11.sp
                                )
                            }

                            // Total Distance
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = String.format(ptBr, "%.2f km", totalDistanceKm),
                                    color = com.example.ui.theme.RunColors.brandText,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Black
                                )
                                Text(
                                    text = "Distância",
                                    color = com.example.ui.theme.RunColors.textSecondary,
                                    fontSize = 11.sp
                                )
                            }

                            // Total Time
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                val timeFormatted = if (totalHours > 0) {
                                    "${totalHours}h ${totalMinutes}m"
                                } else {
                                    "${totalMinutes} min"
                                }
                                Text(
                                    text = timeFormatted,
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Black
                                )
                                Text(
                                    text = "Tempo Total",
                                    color = com.example.ui.theme.RunColors.textSecondary,
                                    fontSize = 11.sp
                                )
                            }

                            // Average Pace
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = avgPaceText,
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Black
                                )
                                Text(
                                    text = "Pace Médio",
                                    color = com.example.ui.theme.RunColors.textSecondary,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }

                if (sortedSessions.isEmpty()) {
                    item(key = "run_empty_state") {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp),
                            colors = CardDefaults.cardColors(containerColor = com.example.ui.theme.RunColors.surface),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, com.example.ui.theme.RunColors.border)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = androidx.compose.material.icons.Icons.AutoMirrored.Filled.DirectionsRun,
                                    contentDescription = null,
                                    tint = com.example.ui.theme.RunColors.brand,
                                    modifier = Modifier.size(48.dp)
                                )
                                Text(
                                    text = "Nenhuma corrida registrada",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Inicie seu treino na aba Início para registrar sua primeira sessão!",
                                    color = com.example.ui.theme.RunColors.textSecondary,
                                    fontSize = 13.sp,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    sessionsByMonth.entries.forEach { entry ->
                        val monthHeader = entry.key
                        val monthSessions = entry.value
                        val monthKm = monthSessions.sumOf { it.distanceMeters.toLong() } / 1000f
                        val count = monthSessions.size
                        val countText = if (count == 1) "1 corrida" else "$count corridas"
                        val kmText = String.format(ptBr, "%.2f km", monthKm)

                        item(key = "header_$monthHeader") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp, bottom = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = monthHeader,
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "$countText · $kmText",
                                    color = com.example.ui.theme.RunColors.brandText,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        items(monthSessions, key = { it.id }) { session ->
                            val linkedOfficial = officialRaces.firstOrNull { it.linkedRunSessionId == session.id }
                            RunningSessionHistoryItem(
                                session = session,
                                runningShoes = runningShoes,
                                isSavedAsOfficialRace = linkedOfficial != null,
                                linkedOfficialRaceName = linkedOfficial?.raceName,
                                onSaveAsOfficialRaceClick = null,
                                onClassifySessionClick = {
                                    sessionToClassify = session
                                    com.example.utils.AudioSynth.playTickSound()
                                },
                                onSessionClick = {
                                    viewModel.selectedDetailSession.value = session
                                    viewModel.navigateToScreen(AppScreen.RUNNING_DETAIL)
                                    com.example.utils.AudioSynth.playTickSound()
                                },
                                onShareClick = {
                                    viewModel.selectedShareSession.value = session
                                    viewModel.navigateToScreen(AppScreen.SHARE_CARD)
                                    com.example.utils.AudioSynth.playTickSound()
                                },
                                onEditShoeClick = {
                                    sessionForShoeEdit = session
                                    com.example.utils.AudioSynth.playTickSound()
                                },
                                onDeleteClick = {
                                    runningSessionToDelete = session
                                    com.example.utils.AudioSynth.playTickSound()
                                }
                            )
                        }
                    }
                }
            }
        } else {
            // Metrics Summary Card (Musculação)
            val totalGymVolume = remember(gymSessions) {
                gymSessions.sumOf { it.totalVolumeKg.toDouble() }
            }
            val totalGymSets = remember(gymSessions) {
                gymSessions.sumOf { it.completedSets }
            }
            val totalGymDurationSec = remember(gymSessions) {
                gymSessions.sumOf { it.durationSeconds }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(top = 6.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item(key = "gym_macro_dossier_card") {
                    com.example.ui.components.PeriodMacroAnalysisEntryCard(
                        runningSessions = sessions,
                        gymSessions = gymSessions,
                        userAgeYears = userProfile.idade.takeIf { it in 10..100 },
                        initiallyExpanded = false
                    )
                }

                item(key = "gym_metrics_summary_card") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF161922)),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, Color(0xFF262B38))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceAround,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "${gymSessions.size}",
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Black
                                )
                                Text(
                                    text = "Treinos",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.sp
                                )
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                val volStr = if (totalGymVolume >= 1000.0) {
                                    String.format(ptBr, "%.1f t", totalGymVolume / 1000.0)
                                } else {
                                    String.format(ptBr, "%.0f kg", totalGymVolume)
                                }
                                Text(
                                    text = volStr,
                                    color = Color(0xFF0EA5E9),
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Black,
                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                )
                                Text(
                                    text = "Volume Total",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.sp
                                )
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "$totalGymSets",
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Black
                                )
                                Text(
                                    text = "Séries Feitas",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.sp
                                )
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                val gHours = totalGymDurationSec / 3600
                                val gMins = (totalGymDurationSec % 3600) / 60
                                val gTimeFormatted = if (gHours > 0) "${gHours}h ${gMins}m" else "${gMins}m"
                                Text(
                                    text = gTimeFormatted,
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Black
                                )
                                Text(
                                    text = "Tempo Total",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }

                if (gymSessions.isEmpty()) {
                    item(key = "gym_empty_state") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 48.dp),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FitnessCenter,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.3f),
                                    modifier = Modifier.size(64.dp)
                                )
                                Text(
                                    text = "Nenhum treino de musculação registrado ainda",
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Conclua uma sessão de musculação na aba GYM para visualizar todo o histórico de cargas e relatórios aqui.",
                                    color = Color.White.copy(alpha = 0.4f),
                                    fontSize = 12.sp,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 32.dp)
                                )
                            }
                        }
                    }
                } else {
                    items(gymSessions, key = { it.id }) { session ->
                        val dateFmt = SimpleDateFormat("dd/MM/yyyy • HH:mm", ptBr)
                        val dateStr = dateFmt.format(Date(session.date))
                        val m = session.durationSeconds / 60
                        val s = session.durationSeconds % 60
                        val durStr = String.format(ptBr, "%02d:%02d", m, s)

                        val isMobilitySession = remember(session.workoutName, session.totalVolumeKg, session.exercises) {
                            val nameLower = session.workoutName.lowercase(ptBr)
                            nameLower.contains("mobilidade") ||
                                nameLower.contains("regenerativ") ||
                                nameLower.contains("alongamento") ||
                                nameLower.contains("descanso ativo") ||
                                (session.totalVolumeKg <= 0f && session.exercises.isNotEmpty() && session.exercises.all { ex -> ex.sets.all { it.weight <= 0f } })
                        }

                        if (isMobilitySession) {
                            val isExpanded = expandedMobilitySessionIds.contains(session.id)
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF141821)),
                                border = BorderStroke(1.dp, Color(0xFF00E676).copy(alpha = 0.28f)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        com.example.utils.AudioSynth.playTickSound()
                                        expandedMobilitySessionIds = if (isExpanded) {
                                            expandedMobilitySessionIds - session.id
                                        } else {
                                            expandedMobilitySessionIds + session.id
                                        }
                                    }
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
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
                                            Box(
                                                modifier = Modifier
                                                    .width(3.dp)
                                                    .height(24.dp)
                                                    .background(Color(0xFF00E676), RoundedCornerShape(1.5.dp))
                                            )
                                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                Text(
                                                    text = session.workoutName,
                                                    color = Color.White,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "$dateStr • Duração: $durStr",
                                                    color = Color(0xFF94A3B8),
                                                    fontSize = 11.sp
                                                )
                                            }
                                        }

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Surface(
                                                color = Color(0xFF00E676).copy(alpha = 0.14f),
                                                shape = RoundedCornerShape(6.dp),
                                                border = BorderStroke(0.5.dp, Color(0xFF00E676).copy(alpha = 0.4f))
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.CheckCircle,
                                                        contentDescription = null,
                                                        tint = Color(0xFF00E676),
                                                        modifier = Modifier.size(11.dp)
                                                    )
                                                    Text(
                                                        text = "CONCLUÍDO",
                                                        color = Color(0xFF00E676),
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Black
                                                    )
                                                }
                                            }

                                            Icon(
                                                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                                contentDescription = if (isExpanded) "Recolher exercícios" else "Expandir exercícios",
                                                tint = Color(0xFF94A3B8),
                                                modifier = Modifier.size(18.dp)
                                            )

                                            IconButton(
                                                onClick = {
                                                    com.example.utils.AudioSynth.playTickSound()
                                                    gymSessionToDelete = session
                                                },
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(0xFF1E2430))
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Outlined.Delete,
                                                    contentDescription = "Excluir registro de treino",
                                                    tint = Color(0xFF94A3B8),
                                                    modifier = Modifier.size(15.dp)
                                                )
                                            }
                                        }
                                    }

                                    AnimatedVisibility(visible = isExpanded) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 6.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            HorizontalDivider(
                                                thickness = 0.5.dp,
                                                color = Color(0xFF262B38)
                                            )
                                            if (session.exercises.isEmpty()) {
                                                Text(
                                                    text = "Sessão de mobilidade e recuperação concluída (${session.completedSets} séries).",
                                                    color = Color(0xFF94A3B8),
                                                    fontSize = 11.sp
                                                )
                                            } else {
                                                session.exercises.forEachIndexed { idx, ex ->
                                                    val setsDone = ex.sets.count { it.completed }.coerceAtLeast(ex.sets.size)
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(
                                                            text = "${idx + 1}. ${ex.name}",
                                                            color = Color.White.copy(alpha = 0.9f),
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.Medium,
                                                            modifier = Modifier.weight(1f)
                                                        )
                                                        Text(
                                                            text = "$setsDone ${if (setsDone == 1) "série" else "séries"}",
                                                            color = Color(0xFF00E676),
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.SemiBold
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF161922)),
                                border = BorderStroke(1.dp, Color(0xFF262B38)),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        com.example.utils.AudioSynth.playTickSound()
                                        selectedGymSessionDetail = session
                                    }
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
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
                                            Box(
                                                modifier = Modifier
                                                    .width(3.dp)
                                                    .height(18.dp)
                                                    .background(Color(0xFF0EA5E9), RoundedCornerShape(1.5.dp))
                                            )
                                            Text(
                                                text = session.workoutName,
                                                color = Color.White,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold,
                                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                                maxLines = 1,
                                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                            )
                                        }

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = dateStr,
                                                color = Color(0xFF94A3B8),
                                                fontSize = 11.sp
                                            )

                                            IconButton(
                                                onClick = {
                                                    com.example.utils.AudioSynth.playTickSound()
                                                    gymSessionToDelete = session
                                                },
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(0xFF1E2430))
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Outlined.Delete,
                                                    contentDescription = "Excluir registro de treino",
                                                    tint = Color(0xFF94A3B8),
                                                    modifier = Modifier.size(15.dp)
                                                )
                                            }
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val volStr = if (session.totalVolumeKg >= 1000f) {
                                            String.format(ptBr, "%.1f t", session.totalVolumeKg / 1000f)
                                        } else {
                                            String.format(ptBr, "%.0f kg", session.totalVolumeKg)
                                        }

                                        Text(
                                            text = "Volume: $volStr",
                                            color = Color(0xFF0EA5E9),
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                        )

                                        Text(
                                            text = "Duração: $durStr • ${session.completedSets} séries",
                                            color = Color(0xFF94A3B8),
                                            fontSize = 12.sp
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
    }

    if (runningSessionToDelete != null || gymSessionToDelete != null) {
        AlertDialog(
            onDismissRequest = {
                runningSessionToDelete = null
                gymSessionToDelete = null
            },
            containerColor = Color(0xFF161922),
            title = {
                Text(
                    text = "Excluir Registro de Treino",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            },
            text = {
                Text(
                    text = "Excluir este registro de treino? Suas estatísticas e quilometragem serão atualizadas.",
                    color = Color(0xFFCBD5E1),
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        runningSessionToDelete?.let { runSession ->
                            viewModel.deleteRunningSession(runSession.id)
                        }
                        gymSessionToDelete?.let { gymSession ->
                            viewModel.deleteGymWorkoutSession(gymSession.id)
                        }
                        runningSessionToDelete = null
                        gymSessionToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) {
                    Text("Excluir", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        runningSessionToDelete = null
                        gymSessionToDelete = null
                    }
                ) {
                    Text("Cancelar", color = Color(0xFF94A3B8))
                }
            }
        )
    }

    selectedGymSessionDetail?.let { gymSession ->
        com.example.ui.components.GymSessionDetailDialog(
            session = gymSession,
            athleteName = userProfile.nome.ifBlank { "Atleta" },
            athleteWeightKg = userProfile.peso,
            gymName = userProfile.localTreino,
            userProfile = userProfile,
            allGymSessions = gymSessions,
            recentRunningSessions = sessions,
            onApplyLoadAdjustments = { adjustments ->
                viewModel.applyAiLoadAdjustmentsToNextWorkout(gymSession.workoutName, adjustments)
            },
            onDismiss = { selectedGymSessionDetail = null },
            onDelete = {
                viewModel.deleteGymWorkoutSession(gymSession.id)
                selectedGymSessionDetail = null
            }
        )
    }

    if (sessionForShoeEdit != null) {
        RunningShoesBottomSheet(
            shoes = runningShoes,
            onAddShoe = { viewModel.addRunningShoe(it) },
            onUpdateShoe = { viewModel.updateRunningShoe(it) },
            onSetDefault = { viewModel.setDefaultRunningShoe(it) },
            onRetireShoe = { viewModel.retireRunningShoe(it) },
            onDeleteShoe = { viewModel.deleteRunningShoe(it) },
            onDismiss = { sessionForShoeEdit = null },
            selectionTargetSession = sessionForShoeEdit,
            onSelectShoeForSession = { shoeName ->
                sessionForShoeEdit?.let { target ->
                    viewModel.updateSessionShoe(target.id, shoeName)
                    android.widget.Toast.makeText(
                        context,
                        "Tênis atualizado para $shoeName!",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
                sessionForShoeEdit = null
            }
        )
    }

    sessionToClassify?.let { target ->
        val linkedOfficial = remember(officialRaces, target.id) {
            officialRaces.firstOrNull { it.linkedRunSessionId == target.id }
        }
        com.example.ui.components.RunSessionClassificationDialog(
            session = target,
            isLinkedOfficialRace = linkedOfficial != null,
            linkedOfficialRaceName = linkedOfficial?.raceName,
            onDismiss = { sessionToClassify = null },
            onConfirm = { isRace, raceName ->
                viewModel.updateRunningSessionClassification(
                    sessionId = target.id,
                    isRace = isRace,
                    raceName = raceName
                )
                sessionToClassify = null
                val feedback = if (isRace) {
                    if (!raceName.isNullOrBlank()) "Marcada como Prova: $raceName" else "Sessão marcada como Prova"
                } else {
                    "Sessão marcada como Treino"
                }
                android.widget.Toast.makeText(
                    context,
                    feedback,
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        )
    }

    if (showShoesBottomSheet) {
        RunningShoesBottomSheet(
            shoes = runningShoes,
            onAddShoe = { viewModel.addRunningShoe(it) },
            onUpdateShoe = { viewModel.updateRunningShoe(it) },
            onSetDefault = { viewModel.setDefaultRunningShoe(it) },
            onRetireShoe = { viewModel.retireRunningShoe(it) },
            onDeleteShoe = { viewModel.deleteRunningShoe(it) },
            onDismiss = { showShoesBottomSheet = false }
        )
    }

    if (showRecoverWorkoutDialog) {
        val selectedProgId by viewModel.selectedRunningProgramId.collectAsState()
        val currentProgress = remember(sessions) {
            com.example.engine.RunningProgramManager.calculateCurrentProgress(sessions)
        }
        val defaultShoeForRecover = remember(runningShoes) {
            val activeShoe = runningShoes.firstOrNull { it.isDefault && !it.isRetired }
                ?: runningShoes.firstOrNull { !it.isRetired }
            activeShoe?.let { "${it.brand} ${it.name}" } ?: "Olympikus Corre 4"
        }
        com.example.ui.components.RecoverCompletedWorkoutDialog(
            programId = selectedProgId,
            initialWeek = currentProgress.weekNumber,
            initialDay = currentProgress.dayNumber,
            baselinePaceMinPerKm = userProfile.baselinePaceMinPerKm,
            defaultShoeName = defaultShoeForRecover,
            onDismiss = {
                showRecoverWorkoutDialog = false
                com.example.utils.AudioSynth.playTickSound()
            },
            onConfirmRegister = { weekNum, dayNum, sessType, durSec, distM, env, shoe ->
                val avgPace = if (distM > 0) ((durSec * 1000) / distM) else 0
                val recoveredSession = com.example.data.model.RunningSession(
                    id = java.util.UUID.randomUUID().toString(),
                    date = System.currentTimeMillis(),
                    weekNumber = weekNum,
                    dayNumber = dayNum,
                    sessionType = sessType,
                    durationSec = durSec,
                    distanceMeters = distM,
                    avgPaceSecPerKm = avgPace,
                    rpe = 6,
                    notes = "Treino da planilha registrado concluído (S$weekNum·D$dayNum)",
                    environment = env,
                    painResponse = null,
                    isGentleMode = false,
                    shoeName = shoe
                )
                viewModel.saveRunningSession(recoveredSession)
                com.example.services.RunTracker.persistSessionDirectlyToDb(context, recoveredSession)
                com.example.services.RunningDailyReminderManager.markRunCompletedToday(context)
                showRecoverWorkoutDialog = false
                com.example.utils.AudioSynth.playTickSound()
                android.widget.Toast.makeText(
                    context,
                    "Semana $weekNum · Dia $dayNum salvo no Histórico!",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        )
    }

    if (showOfficialRacesVault) {
        com.example.ui.components.OfficialRacesVaultDialog(
            officialRaces = officialRaces,
            runningSessions = sessions,
            runningShoes = runningShoes,
            userProfile = userProfile,
            onSaveRace = { record -> viewModel.saveOfficialRaceRecord(record) },
            onDeleteRace = { raceId -> viewModel.deleteOfficialRaceRecord(raceId) },
            onOpenLinkedSession = { targetSession ->
                showOfficialRacesVault = false
                viewModel.selectedDetailSession.value = targetSession
                viewModel.navigateToScreen(AppScreen.RUNNING_DETAIL)
            },
            onDismiss = { showOfficialRacesVault = false }
        )
    }

    if (showManualOfficialRaceModal || sessionToConvertOfficialRace != null) {
        val targetSess = sessionToConvertOfficialRace
        val existingLinked = remember(officialRaces, targetSess?.id) {
            if (targetSess != null) officialRaces.firstOrNull { it.linkedRunSessionId == targetSess.id } else null
        }
        com.example.ui.components.SaveOfficialRaceDialog(
            initialSession = targetSess,
            existingRecord = existingLinked,
            userProfile = userProfile,
            availableShoes = runningShoes,
            onSave = { record ->
                viewModel.saveOfficialRaceRecord(record)
                showManualOfficialRaceModal = false
                sessionToConvertOfficialRace = null
                android.widget.Toast.makeText(
                    context,
                    "Prova oficial salva: ${record.raceName}!",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            },
            onDismiss = {
                showManualOfficialRaceModal = false
                sessionToConvertOfficialRace = null
            }
        )
    }
}
