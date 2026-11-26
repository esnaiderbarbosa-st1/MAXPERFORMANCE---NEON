package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.example.services.HeartRateBleManager
import com.example.services.HrConnectionState
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.runtime.*
import coil.compose.AsyncImage
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.utils.customViewForScrollCapture
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.constants.*
import com.example.data.model.*
import com.example.ui.theme.*
import com.example.ui.components.*
import com.example.ui.components.WeightAdjustmentBottomSheet
import com.example.ui.viewmodel.AppScreen
import com.example.ui.viewmodel.MainViewModel
import com.example.ui.viewmodel.WorkoutFlowState
import com.example.utils.MaxSoundManager
import java.text.SimpleDateFormat
import com.example.ui.adaptive.*
import java.util.*
import kotlinx.coroutines.delay
import androidx.compose.ui.zIndex
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.launch

fun uriToBase64(context: android.content.Context, uri: android.net.Uri): String? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri)
        val bytes = inputStream?.readBytes()
        inputStream?.close()
        if (bytes != null) {
            android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        } else null
    } catch (e: Exception) {
        null
    }
}

// ==================== SCREEN 1: HOME ====================
@Composable
fun HomeScreen(viewModel: MainViewModel) {
    val workoutsList by viewModel.workouts.collectAsState()
    val historyList by viewModel.history.collectAsState()
    val recoveryList by viewModel.recoveryList.collectAsState()
    val profile by viewModel.userProfile.collectAsState()
    val activeGymProfile by viewModel.activeGymProfile.collectAsState()
    val activeWorkout by viewModel.activeWorkout.collectAsState()
    val workoutFlowState by viewModel.workoutFlowState.collectAsState()
    val homeRecommendationVersion by viewModel.homeRecommendationVersion.collectAsState()
    val hasActiveSession by viewModel.hasActiveSession.collectAsState()
    val runningSessions by viewModel.runningSessions.collectAsState()
    val preferredProtocolId by viewModel.preferredProtocolId.collectAsState()
    val preferredWorkoutId by viewModel.preferredWorkoutId.collectAsState()
    val gymWorkoutSessions by viewModel.gymWorkoutSessions.collectAsState()
    val fatigueAdvisoryState by viewModel.fatigueAdvisoryState.collectAsState()

    var showHomeGymSelectorBottomSheet by remember { mutableStateOf(false) }
    var showExpressBottomSheet by remember { mutableStateOf(false) }
    var showCreationHubBottomSheet by remember { mutableStateOf(false) }
    var selectedGymSessionForShareModal by remember { mutableStateOf<com.example.data.model.GymWorkoutSession?>(null) }
    var showWhyThisWorkoutBottomSheet by remember { mutableStateOf(false) }
    var showSwapWorkoutBottomSheet by remember { mutableStateOf(false) }
    var showDiscardAndStartDialog by remember { mutableStateOf(false) }
    var peekingCarouselItem by remember { mutableStateOf<com.example.data.model.HeroCarouselItem?>(null) }

    val context = LocalContext.current
    val bleManager = remember { HeartRateBleManager.getInstance(context) }
    val hrConnectionState by bleManager.connectionState.collectAsState()
    val currentBpm by bleManager.currentBpm.collectAsState()
    var showHrPairingSheet by remember { mutableStateOf(false) }

    val blePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val allGranted = perms.values.all { it }
        if (allGranted) {
            showHrPairingSheet = true
            bleManager.startScan()
        } else {
            Toast.makeText(context, "Permissões de Bluetooth necessárias para parear a cinta", Toast.LENGTH_LONG).show()
        }
    }

    fun requestBlePermissionsAndOpenSheet() {
        val perms = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            perms.add(Manifest.permission.BLUETOOTH_SCAN)
            perms.add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            perms.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        val needsRequest = perms.any {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needsRequest) {
            blePermissionLauncher.launch(perms.toTypedArray())
        } else {
            showHrPairingSheet = true
            bleManager.startScan()
        }
    }

    val isSessionActive = hasActiveSession || (activeWorkout != null && 
                          workoutFlowState != WorkoutFlowState.Finished && 
                          workoutFlowState != WorkoutFlowState.Abandoned)

    // Time-based greeting (SPRINT UX-007: Concise "Bom dia, Esnaider 👋")
    val hour = remember { java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) }
    val firstName = remember(profile.nome) {
        profile.nome.split(" ").firstOrNull()?.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() } ?: "Atleta"
    }
    val timeGreeting = remember(hour) {
        when (hour) {
            in 5..11 -> "Bom dia"
            in 12..17 -> "Boa tarde"
            else -> "Boa noite"
        }
    }
    val headerGreetingTitle = "$timeGreeting, $firstName"
    val headerGreetingSubtitle = "Pronto para evoluir hoje?"

    val suggestedPlan = remember(workoutsList, historyList, profile.nivelExperiencia, homeRecommendationVersion, preferredProtocolId, preferredWorkoutId, fatigueAdvisoryState) {
        viewModel.getSuggestedPlanWorkout()
    }

    val carouselData = remember(
        workoutsList,
        historyList,
        runningSessions,
        profile.nivelExperiencia,
        homeRecommendationVersion,
        preferredProtocolId,
        preferredWorkoutId,
        fatigueAdvisoryState
    ) {
        viewModel.getHeroCarouselData()
    }
    val carouselItems = carouselData.first
    val targetCarouselPage = carouselData.second

    val heroPagerState = rememberPagerState(
        initialPage = targetCarouselPage.coerceIn(0, (carouselItems.size - 1).coerceAtLeast(0)),
        pageCount = { carouselItems.size.coerceAtLeast(1) }
    )

    LaunchedEffect(targetCarouselPage, homeRecommendationVersion, preferredProtocolId, preferredWorkoutId, fatigueAdvisoryState.decision) {
        if (carouselItems.isNotEmpty() && targetCarouselPage in carouselItems.indices) {
            if (heroPagerState.currentPage != targetCarouselPage) {
                heroPagerState.animateScrollToPage(targetCarouselPage)
            }
        }
    }

    val currentWorkoutName = if (isSessionActive) {
        activeWorkout?.name ?: suggestedPlan.name
    } else {
        suggestedPlan.name
    }

    val isWorkoutCompletedToday = remember(historyList, runningSessions) {
        val todayStart = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        historyList.any { it.date >= todayStart } || runningSessions.any { it.date >= todayStart }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .customViewForScrollCapture()
                .background(Color.Transparent)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 28.dp)
        ) {
            // ================= 1. CABEÇALHO (LOGO + AVATAR + SAUDAÇÃO) =================
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 0.dp, bottom = 0.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("home_header_card"),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Toggle de Modo: MUSCULAÇÃO / CORRIDA
                        val currentAppMode by viewModel.appMode.collectAsState()
                        AppModeSegmentedToggle(
                            currentMode = currentAppMode,
                            onModeSelected = { viewModel.setAppMode(it) }
                        )

                        // Right Controls: Clean, unified 3 elements (BLE HR + Quick Actions Menu + Compact Avatar)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // 1. Pílula/Botão do Monitor Cardíaco BLE
                            if (hrConnectionState == HrConnectionState.CONNECTED && currentBpm > 0) {
                                Surface(
                                    onClick = {
                                        requestBlePermissionsAndOpenSheet()
                                        com.example.utils.AudioSynth.playTickSound()
                                    },
                                    color = Color(0xFF1A1A22),
                                    shape = RoundedCornerShape(17.dp),
                                    border = BorderStroke(1.dp, Color(0xFFFF4D4D).copy(alpha = 0.35f)),
                                    modifier = Modifier
                                        .height(34.dp)
                                        .testTag("btn_header_hr_monitor")
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier.padding(horizontal = 9.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Favorite,
                                            contentDescription = "Monitor cardíaco conectado",
                                            tint = Color(0xFFFF4D4D),
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Text(
                                            text = "$currentBpm",
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF1A1A22))
                                        .border(1.dp, Color.White.copy(alpha = 0.08f), CircleShape)
                                        .clickable {
                                            requestBlePermissionsAndOpenSheet()
                                            com.example.utils.AudioSynth.playTickSound()
                                        }
                                        .testTag("btn_header_hr_monitor"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FavoriteBorder,
                                        contentDescription = "Parear monitor cardíaco BLE",
                                        tint = if (hrConnectionState == HrConnectionState.CONNECTING) Color(0xFFFFB300) else Color(0xFF8E8E93),
                                        modifier = Modifier.size(17.dp)
                                    )
                                }
                            }

                            // 2. Botão de Ação Rápida com Dropdown compacto (Central de Criação, Share Card, Treino Express)
                            var showQuickActionMenu by remember { mutableStateOf(false) }
                            Box {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF1A1A22))
                                        .border(1.dp, Color.White.copy(alpha = 0.08f), CircleShape)
                                        .clickable {
                                            showQuickActionMenu = true
                                            com.example.utils.AudioSynth.playTickSound()
                                        }
                                        .testTag("btn_header_quick_actions"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Ações Rápidas",
                                        tint = Color.White.copy(alpha = 0.9f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                DropdownMenu(
                                    expanded = showQuickActionMenu,
                                    onDismissRequest = { showQuickActionMenu = false },
                                    modifier = Modifier.background(Color(0xFF1E1E26))
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Central de Criação", color = Color.White, fontSize = 13.sp) },
                                        leadingIcon = { Icon(Icons.Default.Add, null, tint = Primary, modifier = Modifier.size(18.dp)) },
                                        onClick = {
                                            showQuickActionMenu = false
                                            showCreationHubBottomSheet = true
                                            com.example.utils.AudioSynth.playTickSound()
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Share Card MAX GYM", color = Color.White, fontSize = 13.sp) },
                                        leadingIcon = { Icon(Icons.Default.Share, null, tint = Color(0xFF0EA5E9), modifier = Modifier.size(18.dp)) },
                                        onClick = {
                                            showQuickActionMenu = false
                                            val latest = gymWorkoutSessions.firstOrNull()
                                            if (latest != null) {
                                                selectedGymSessionForShareModal = latest
                                            } else {
                                                Toast.makeText(context, "Conclua uma sessão de musculação para gerar o Share Card GYM!", Toast.LENGTH_SHORT).show()
                                            }
                                            com.example.utils.AudioSynth.playTickSound()
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Histórico Musculação", color = Color.White, fontSize = 13.sp) },
                                        leadingIcon = { Icon(Icons.Default.History, null, tint = Color(0xFF0EA5E9), modifier = Modifier.size(18.dp)) },
                                        onClick = {
                                            showQuickActionMenu = false
                                            viewModel.historyTab.value = 1
                                            viewModel.activeScreen.value = AppScreen.DIARIO
                                            com.example.utils.AudioSynth.playTickSound()
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Treino Express", color = Color.White, fontSize = 13.sp) },
                                        leadingIcon = { Icon(Icons.Default.Bolt, null, tint = Color(0xFFFFB300), modifier = Modifier.size(18.dp)) },
                                        onClick = {
                                            showQuickActionMenu = false
                                            showExpressBottomSheet = true
                                            com.example.utils.AudioSynth.playTickSound()
                                        }
                                    )
                                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f), modifier = Modifier.padding(vertical = 4.dp))
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 6.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = com.example.utils.AppBuildInfo.compactSignature,
                                            color = Color(0xFF757580),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }

                            // 3. Avatar do Usuário ("E"): compacto (34.dp), elegante com borda sutil
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF1A1A22))
                                    .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape)
                                    .clickable {
                                        viewModel.activeScreen.value = AppScreen.PROFILE
                                        com.example.utils.AudioSynth.playTickSound()
                                    }
                                    .testTag("btn_header_profile"),
                                contentAlignment = Alignment.Center
                            ) {
                                if (profile.foto.isNotEmpty()) {
                                    AsyncImage(
                                        model = profile.foto,
                                        contentDescription = "Perfil",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize().clip(CircleShape)
                                    )
                                } else {
                                    Text(
                                        text = firstName.take(1).uppercase(),
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(1.dp)
                        ) {
                            Text(
                                text = headerGreetingTitle,
                                color = Color.White,
                                fontSize = 23.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = (-0.5).sp
                            )
                            Text(
                                text = headerGreetingSubtitle,
                                color = Color.White.copy(alpha = 0.65f),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // ================= 1.1 CARD: TREINO EM ANDAMENTO (TRAVA DE SESSÃO ÚNICA) =================
            if (isSessionActive && activeWorkout != null) {
                item {
                    val durationSec by viewModel.workoutDurationSeconds.collectAsState()
                    val dMins = durationSec / 60
                    val dSecs = durationSec % 60
                    val durationStr = String.format(java.util.Locale.US, "%02d:%02d", dMins, dSecs)

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("active_gym_session_card"),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF161922)),
                        border = BorderStroke(1.5.dp, Color(0xFF0EA5E9)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
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
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF22C55E))
                                    )
                                    Text(
                                        text = "TREINO EM ANDAMENTO",
                                        color = Color(0xFF0EA5E9),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Black,
                                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                    )
                                }
                                Text(
                                    text = durationStr,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Text(
                                text = activeWorkout?.name ?: "Sessão de Treino",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(IntrinsicSize.Min),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        com.example.utils.AudioSynth.playTickSound()
                                        viewModel.activeScreen.value = AppScreen.PLAYER
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight(),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0EA5E9)),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(
                                        text = "Retomar Treino",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                OutlinedButton(
                                    onClick = {
                                        com.example.utils.AudioSynth.playTickSound()
                                        showDiscardAndStartDialog = true
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight(),
                                    border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f)),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(
                                        text = "Descartar e Iniciar Novo",
                                        color = Color(0xFFEF4444),
                                        fontSize = 11.sp,
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

            // ================= 2. BENTO SUPERIOR (RECUPERAÇÃO | ACADEMIA | FADIGA) =================
            item {
                val selectedRunProgramId by viewModel.selectedRunningProgramId.collectAsState()
                val hybridReadinessReport = remember(
                    historyList,
                    gymWorkoutSessions,
                    runningSessions,
                    profile.idade,
                    profile.fcMaxCalibradaBpm,
                    selectedRunProgramId,
                    fatigueAdvisoryState.consecutiveDays
                ) {
                    com.example.ui.components.HybridReadinessEngine.evaluate(
                        gymHistory = historyList,
                        gymSessions = gymWorkoutSessions,
                        runningHistory = runningSessions,
                        athleteAge = profile.idade,
                        selectedRunningProgramId = selectedRunProgramId,
                        consecutiveDays = fatigueAdvisoryState.consecutiveDays,
                        calibratedMaxHrBpm = profile.fcMaxCalibradaBpm
                    )
                }

                FlowHomeBentoRow3Cards(
                    gymName = activeGymProfile?.name ?: "Smart Fit",
                    readinessLevel = hybridReadinessReport.homeBadgeLabel,
                    currentBpm = if (hrConnectionState == HrConnectionState.CONNECTED) currentBpm else 0,
                    fatigueAdvisory = fatigueAdvisoryState,
                    hybridReadinessReport = hybridReadinessReport,
                    onSwapToRegenerativeZ2 = {
                        viewModel.selectRunningProgram("free_regenerative")
                        Toast.makeText(
                            context,
                            "Sessão de hoje trocada por Rodagem Regenerativa Z2 (Proteção de Inferiores)!",
                            Toast.LENGTH_LONG
                        ).show()
                    },
                    onFatigueClick = {
                        viewModel.resetOrSimulateFatigueAdvisory(false)
                        com.example.utils.AudioSynth.playTickSound()
                    },
                    onGymClick = {
                        showHomeGymSelectorBottomSheet = true
                        com.example.utils.AudioSynth.playTickSound()
                    }
                )
            }

            // ================= 3. HERO CARD (CARROSSEL FLUIDO COM HORIZONTAL PAGER) =================
            item {
                val weeklyProgress = remember(historyList, runningSessions, profile.metaSemanal, fatigueAdvisoryState) {
                    viewModel.getWeeklyProgress()
                }

                val avgRecovery = remember(recoveryList) {
                    if (recoveryList.isEmpty()) 1.0f else recoveryList.map { it.recoveryPercent }.average().toFloat()
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (fatigueAdvisoryState.isTriggered) {
                        com.example.ui.components.SmartFatigueModeSwitcher(
                            advisory = fatigueAdvisoryState,
                            onAcceptRest = {
                                com.example.utils.AudioSynth.playTickSound()
                                viewModel.acceptFatigueRestDay()
                            },
                            onOverrideTrain = {
                                com.example.utils.AudioSynth.playTickSound()
                                viewModel.overrideFatigueAndTrain()
                            }
                        )
                    }

                    HorizontalPager(
                        state = heroPagerState,
                        beyondViewportPageCount = 1,
                        contentPadding = PaddingValues(horizontal = 0.dp),
                        pageSpacing = 12.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("hero_carousel_pager")
                    ) { page ->
                        val item = carouselItems.getOrNull(page) ?: return@HorizontalPager

                        val pageOffset = ((heroPagerState.currentPage - page) + heroPagerState.currentPageOffsetFraction)
                            .let { kotlin.math.abs(it) }
                            .coerceIn(0f, 1f)
                        val scale = androidx.compose.ui.util.lerp(1.0f, 0.95f, pageOffset)
                        val alpha = androidx.compose.ui.util.lerp(1.0f, 0.75f, pageOffset)

                        val itemExercisesList = remember(item.workout.exercisesJson) {
                            com.example.utils.JsonUtils.parseWorkoutExercises(item.workout.exercisesJson)
                        }

                        val primaryCategory = remember(itemExercisesList, item.workout.name) {
                            itemExercisesList.map { it.category }
                                .filter { it.isNotBlank() && !it.equals("Geral", ignoreCase = true) }
                                .firstOrNull() ?: if (item.workout.name.lowercase().contains("costas")) "Costas" else "Peito"
                        }

                        val daysSinceLastStimulus = remember(historyList, primaryCategory) {
                            val lastEntry = historyList.firstOrNull { hist ->
                                val exs = com.example.utils.JsonUtils.parseWorkoutExercises(hist.exercisesJson)
                                exs.any { it.category.equals(primaryCategory, ignoreCase = true) && it.sets.any { s -> s.completed } }
                            }
                            if (lastEntry != null) {
                                com.example.services.recommendation.engine001.TrainingEngine001
                                    .calculateCalendarDaysBetween(lastEntry.date)
                                    .coerceAtLeast(1)
                            } else {
                                null
                            }
                        }

                        val currentActiveW = activeWorkout
                        val isThisItemActiveSession = isSessionActive && currentActiveW?.id == item.workout.id

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                    this.alpha = alpha
                                }
                        ) {
                            FlowHomeHeroCard(
                                workoutName = item.workout.name,
                                exercisesList = if (isThisItemActiveSession) {
                                    com.example.utils.JsonUtils.parseWorkoutExercises(currentActiveW.exercisesJson)
                                } else itemExercisesList,
                                isSessionActive = isThisItemActiveSession,
                                isWorkoutCompletedToday = isWorkoutCompletedToday,
                                isLoading = itemExercisesList.isEmpty() && !item.isRunning && !isWorkoutCompletedToday,
                                activeGymName = activeGymProfile?.name ?: "Smart Fit",
                                lastStimulusDays = daysSinceLastStimulus,
                                weeklyProgress = weeklyProgress,
                                recoveryPercent = avgRecovery,
                                badgeLabel = item.badgeLabel,
                                customTitle = item.title,
                                customSubtitle = item.subtitle,
                                customDurationText = item.durationText,
                                advanceGymLabel = item.advanceGymButtonLabel,
                                onAdvanceGymClick = item.nextGymWorkoutToAdvance?.let { advWorkout ->
                                    {
                                        com.example.utils.AudioSynth.playTickSound()
                                        viewModel.startWorkoutSession(advWorkout)
                                    }
                                },
                                fatigueAdvisory = fatigueAdvisoryState,
                                onAcceptRestClick = {
                                    com.example.utils.AudioSynth.playTickSound()
                                    viewModel.acceptFatigueRestDay()
                                },
                                onOverrideTrainClick = {
                                    com.example.utils.AudioSynth.playTickSound()
                                    viewModel.overrideFatigueAndTrain()
                                },
                                onResetFatigueClick = {
                                    com.example.utils.AudioSynth.playTickSound()
                                    viewModel.resetOrSimulateFatigueAdvisory(false)
                                },
                                onClick = {
                                    if (isThisItemActiveSession) {
                                        viewModel.activeScreen.value = AppScreen.PLAYER
                                    } else if (item.isRunning || item.workout.id.contains("RUNNING")) {
                                        viewModel.setAppMode(com.example.data.model.AppMode.RUNNING)
                                    } else {
                                        viewModel.startWorkoutSession(item.workout)
                                    }
                                    com.example.utils.AudioSynth.playTickSound()
                                },
                                onSwapClick = {
                                    showSwapWorkoutBottomSheet = true
                                    com.example.utils.AudioSynth.playTickSound()
                                },
                                onPeekClick = if (item.isRunning && item.nextGymWorkoutToAdvance != null) null else ({
                                    peekingCarouselItem = item
                                })
                            )
                        }
                    }

                    // Indicadores discretos de página (dots/pílulas)
                    if (carouselItems.size > 1) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 2.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            carouselItems.indices.forEach { idx ->
                                val isSelected = heroPagerState.currentPage == idx
                                val dotWidth by animateDpAsState(
                                    targetValue = if (isSelected) 22.dp else 7.dp,
                                    label = "carousel_dot_width"
                                )
                                val dotColor by animateColorAsState(
                                    targetValue = if (isSelected) Primary else Color.White.copy(alpha = 0.25f),
                                    label = "carousel_dot_color"
                                )
                                Box(
                                    modifier = Modifier
                                        .padding(horizontal = 3.dp)
                                        .height(6.dp)
                                        .width(dotWidth)
                                        .clip(CircleShape)
                                        .background(dotColor)
                                )
                            }
                        }
                    }
                }
            }

            // ================= 4. CRONOGRAMA DE PERIODIZAÇÃO (CALENDÁRIO 7 DIAS) =================
            item {
                val calendarSchedule = remember(
                    workoutsList,
                    historyList,
                    runningSessions,
                    profile.nivelExperiencia,
                    profile.metaSemanal,
                    homeRecommendationVersion,
                    fatigueAdvisoryState
                ) {
                    viewModel.getUpcoming7DaysSchedule()
                }

                val activeHeroItem = carouselItems.getOrNull(heroPagerState.currentPage) ?: carouselItems.firstOrNull()

                WeeklyScheduleSection(
                    daysSchedule = calendarSchedule,
                    activeHeroItem = activeHeroItem,
                    fatigueAdvisory = fatigueAdvisoryState,
                    onAcceptRestDay = {
                        com.example.utils.AudioSynth.playTickSound()
                        viewModel.acceptFatigueRestDay()
                    },
                    onOverrideTrain = {
                        com.example.utils.AudioSynth.playTickSound()
                        viewModel.overrideFatigueAndTrain()
                    },
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    if (showHrPairingSheet) {
        com.example.ui.components.BleHeartRatePairingBottomSheet(
            bleManager = bleManager,
            onDismiss = { showHrPairingSheet = false }
        )
    }

    if (showCreationHubBottomSheet) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showCreationHubBottomSheet = false },
            properties = androidx.compose.ui.window.DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.65f))
                    .clickable { showCreationHubBottomSheet = false },
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .padding(vertical = 24.dp)
                        .imePadding()
                        .clickable(enabled = false) {},
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Surface),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Central de Criação",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black
                            )
                            IconButton(onClick = { showCreationHubBottomSheet = false }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Fechar",
                                    tint = OnSurfaceDim
                                )
                            }
                        }

                        // Action 1: Criar Sessão
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("btn_escolher_foco")
                                .clickable {
                                    showCreationHubBottomSheet = false
                                    viewModel.activeScreen.value = AppScreen.WORKOUTS
                                    viewModel.workoutsSubTab.value = 3
                                    com.example.utils.AudioSynth.playTickSound()
                                },
                            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.04f)),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(Primary.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = null,
                                        tint = Primary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = "Novo treino",
                                        color = Color.White,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Monte um treino personalizado do zero",
                                        color = OnSurfaceDim,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }

                        // Action 2: Treino Rápido
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("btn_treino_express")
                                .clickable {
                                    showCreationHubBottomSheet = false
                                    showExpressBottomSheet = true
                                    com.example.utils.AudioSynth.playTickSound()
                                },
                            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.04f)),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(Primary.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Timer,
                                        contentDescription = null,
                                        tint = Primary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = "Treino Rápido",
                                        color = Color.White,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Gere uma sessão rápida focada em tempo",
                                        color = OnSurfaceDim,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }

                        // Action 3: Importar Treino
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showCreationHubBottomSheet = false
                                    viewModel.activeScreen.value = AppScreen.WORKOUTS
                                    viewModel.workoutsSubTab.value = 2
                                    com.example.utils.AudioSynth.playTickSound()
                                },
                            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.04f)),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(Primary.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Download,
                                        contentDescription = null,
                                        tint = Primary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = "Importar Treino",
                                        color = Color.White,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Importe treinos via arquivo ou código",
                                        color = OnSurfaceDim,
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

    if (showHomeGymSelectorBottomSheet) {
        val gymProfiles by viewModel.gymProfiles.collectAsState()
        val currentActiveGymProfile by viewModel.activeGymProfile.collectAsState()
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showHomeGymSelectorBottomSheet = false },
            properties = androidx.compose.ui.window.DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .clickable { showHomeGymSelectorBottomSheet = false },
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .padding(vertical = 24.dp)
                        .imePadding()
                        .clickable(enabled = false) {},
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Surface),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Selecionar Perfil de Academia",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black
                            )
                            IconButton(onClick = { showHomeGymSelectorBottomSheet = false }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Fechar",
                                    tint = OnSurfaceDim
                                )
                            }
                        }

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 250.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            item {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.selectGymProfile(null)
                                            showHomeGymSelectorBottomSheet = false
                                        },
                                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.03f)),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.FitnessCenter,
                                            contentDescription = "Padrão",
                                            tint = OnSurfaceDim,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Column {
                                            Text(
                                                text = "Treino Padrão (Sem Perfil)",
                                                color = Color.White,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "Usar todos os aparelhos normalmente",
                                                color = OnSurfaceDim,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                }
                            }

                            items(gymProfiles) { profile ->
                                val isSelected = currentActiveGymProfile?.id == profile.id
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.selectGymProfile(profile)
                                            showHomeGymSelectorBottomSheet = false
                                        },
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) Primary.copy(alpha = 0.1f) else Color.White.copy(alpha = 0.03f)
                                    ),
                                    border = BorderStroke(
                                        width = 1.dp,
                                        color = if (isSelected) Primary else Color.White.copy(alpha = 0.05f)
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.FitnessCenter,
                                            contentDescription = "Academia",
                                            tint = if (isSelected) Primary else OnSurfaceDim,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = profile.name,
                                                color = Color.White,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            if (profile.latitude != null && profile.longitude != null) {
                                                Text(
                                                    text = "Lat: ${profile.latitude.toString().take(7)}, Lng: ${profile.longitude.toString().take(7)}",
                                                    color = OnSurfaceDim,
                                                    fontSize = 11.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Button(
                            onClick = {
                                showHomeGymSelectorBottomSheet = false
                                viewModel.activeScreen.value = AppScreen.PROFILE
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Primary),
                            modifier = Modifier.fillMaxWidth().testTag("add_gym_from_home_bottom_sheet")
                        ) {
                            Text("Cadastrar Nova Academia", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    if (showExpressBottomSheet) {
        ExpressWorkoutDialog(
            viewModel = viewModel,
            onDismiss = { showExpressBottomSheet = false }
        )
    }

    if (showWhyThisWorkoutBottomSheet) {
        val suggestedWorkout = remember(workoutsList) { viewModel.getSuggestedPlanWorkout() }
        val activeGymProfile by viewModel.activeGymProfile.collectAsState()
        val currentGymName = activeGymProfile?.name?.ifBlank { "Smartfit" } ?: "Smartfit"

        Dialog(
            onDismissRequest = { showWhyThisWorkoutBottomSheet = false },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f))
                    .clickable { showWhyThisWorkoutBottomSheet = false },
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .padding(vertical = 24.dp)
                        .imePadding()
                        .clickable(enabled = false) {},
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Surface),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Header
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
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(Primary.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = Primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = "DETALHES DA RECOMENDAÇÃO",
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = 0.5.sp
                                    )
                                    Text(
                                        text = "Motor de Inteligência de Treino",
                                        color = OnSurfaceDim,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                            IconButton(onClick = { showWhyThisWorkoutBottomSheet = false }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Fechar",
                                    tint = Color.White.copy(alpha = 0.7f)
                                )
                            }
                        }

                        // Target Workout Card
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.04f)),
                            border = BorderStroke(1.dp, Primary.copy(alpha = 0.3f)),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                val rawTargetName = currentWorkoutName.ifBlank { suggestedWorkout.name }
                                val displayTargetName = when {
                                    rawTargetName.contains("Upper", ignoreCase = true) -> "SUPERIOR"
                                    rawTargetName.contains("Push", ignoreCase = true) -> "PEITO + TRÍCEPS"
                                    rawTargetName.contains("Pull", ignoreCase = true) -> "COSTAS + BÍCEPS"
                                    rawTargetName.contains("Leg", ignoreCase = true) -> "PERNAS COMPLETO"
                                    rawTargetName.contains("Cardio", ignoreCase = true) -> "CARDIO & RESISTÊNCIA"
                                    else -> rawTargetName.uppercase()
                                }
                                Text(
                                    text = displayTargetName,
                                    color = Primary,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Black
                                )
                                Text(
                                    text = "Recomendação personalizada para seu momento fisiológico",
                                    color = Color.White.copy(alpha = 0.8f),
                                    fontSize = 12.sp
                                )
                            }
                        }

                        // Justification List
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "FATORES DE DECISÃO DA IA",
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp
                            )

                            val factors = listOf(
                                "Descanso Muscular Ideal" to "Musculatura alvo com intervalo completo (6 dias) para máxima supercompensação.",
                                "Status de Fadiga" to "Fadiga central sob controle, permitindo sessão de alta intensidade e hipertrofia.",
                                "Equilíbrio de Volume" to "Volume de séries ajustado para a semana sem risco de overreaching.",
                                "Adaptação ao Local" to "Equipamentos verificados no perfil ativo da academia ($currentGymName)."
                            )

                            factors.forEach { (title, desc) ->
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = Primary,
                                        modifier = Modifier
                                            .size(18.dp)
                                            .padding(top = 2.dp)
                                    )
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(
                                            text = title,
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = desc,
                                            color = OnSurfaceDim,
                                            fontSize = 11.5.sp,
                                            lineHeight = 15.sp
                                        )
                                    }
                                }
                            }
                        }

                        // Action Buttons
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    showWhyThisWorkoutBottomSheet = false
                                    viewModel.startWorkoutSession(suggestedWorkout)
                                    com.example.utils.AudioSynth.playTickSound()
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = Color.Black,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Iniciar Treino",
                                        color = Color.Black,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            OutlinedButton(
                                onClick = {
                                    showWhyThisWorkoutBottomSheet = false
                                    viewModel.activeScreen.value = AppScreen.WORKOUTS
                                    com.example.utils.AudioSynth.playTickSound()
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SwapHoriz,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Outro Treino",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
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
        }
    }

    if (showSwapWorkoutBottomSheet) {
        val periodizedSequence = remember(workoutsList, historyList, profile.nivelExperiencia, homeRecommendationVersion) {
            viewModel.getPeriodizedWorkoutSequence()
        }
        val activeProtocolId by viewModel.preferredProtocolId.collectAsState()
        val allProtocols = remember { com.example.data.repository.WorkoutProtocolsRepository.ALL_PROTOCOLS }
        var selectedSwapTab by remember { mutableIntStateOf(0) }

        Dialog(
            onDismissRequest = { showSwapWorkoutBottomSheet = false },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.75f))
                    .clickable { showSwapWorkoutBottomSheet = false },
                contentAlignment = Alignment.BottomCenter
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 20.dp)
                        .imePadding()
                        .clickable(enabled = false) {},
                    shape = RoundedCornerShape(26.dp),
                    colors = CardDefaults.cardColors(containerColor = Surface),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Header com título e botão fechar
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SwapHoriz,
                                        contentDescription = null,
                                        tint = Primary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Text(
                                        text = "Trocar Treino / Ficha",
                                        color = Color.White,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.3.sp
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Escolha o treino de hoje ou fixe um protocolo na Home:",
                                    color = OnSurfaceDim,
                                    fontSize = 12.sp
                                )
                            }
                            IconButton(onClick = { showSwapWorkoutBottomSheet = false }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Fechar",
                                    tint = Color.White.copy(alpha = 0.7f)
                                )
                            }
                        }

                        // Banner de Status do Plano Ativo (se houver preferência fixada)
                        if (activeProtocolId != null) {
                            val activeLabel = if (activeProtocolId == "FULL_BODY") "Full Body Híbrido (Alternado com Corrida)" else activeProtocolId ?: ""
                            Surface(
                                color = Primary.copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, Primary.copy(alpha = 0.35f)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PushPin,
                                            contentDescription = null,
                                            tint = Primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = "Plano Ativo: $activeLabel",
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    TextButton(
                                        onClick = {
                                            viewModel.clearPreferredWorkoutAndProtocol()
                                            com.example.utils.AudioSynth.playTickSound()
                                        },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                    ) {
                                        Text(
                                            text = "Restaurar IA",
                                            color = Primary,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        // Seletor de Abas: Ficha Atual | Protocolos Estruturados | Meus Treinos
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(alpha = 0.05f))
                                .padding(3.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf("Ficha Atual", "Protocolos & Mobilidade", "Meus Treinos").forEachIndexed { index, tabName ->
                                val isSelected = selectedSwapTab == index
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isSelected) Primary else Color.Transparent)
                                        .clickable {
                                            selectedSwapTab = index
                                            com.example.utils.AudioSynth.playTickSound()
                                        }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = tabName,
                                        color = if (isSelected) Color.Black else Color.White.copy(alpha = 0.7f),
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Black else FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        when (selectedSwapTab) {
                            0 -> {
                                // Carrossel Horizontal com treinos da periodização
                                PeriodizedWorkoutCarousel(
                                    workoutSequence = periodizedSequence,
                                    onSelectWorkout = { chosenWorkout ->
                                        showSwapWorkoutBottomSheet = false
                                        if (chosenWorkout.id == "WORKOUT_RUNNING_SESSION") {
                                            viewModel.setAppMode(com.example.data.model.AppMode.RUNNING)
                                        } else {
                                            viewModel.startWorkoutSession(chosenWorkout)
                                        }
                                        com.example.utils.AudioSynth.playTickSound()
                                    }
                                )
                            }
                            1 -> {
                                // Lista de Protocolos Estruturados (Full Body A-D + PPL)
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 280.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(allProtocols) { proto ->
                                        val isProtoActive = activeProtocolId == proto.id || (proto.category == "FULL_BODY" && activeProtocolId == "FULL_BODY")
                                        Card(
                                            shape = RoundedCornerShape(14.dp),
                                            colors = CardDefaults.cardColors(containerColor = SurfaceDim),
                                            border = BorderStroke(
                                                1.dp,
                                                if (isProtoActive) Primary else Color.White.copy(alpha = 0.08f)
                                            ),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(12.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        Text(
                                                            text = proto.name,
                                                            color = Color.White,
                                                            fontSize = 14.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                        if (isProtoActive) {
                                                            Text(
                                                                text = "ATIVO",
                                                                color = Primary,
                                                                fontSize = 9.sp,
                                                                fontWeight = FontWeight.Black,
                                                                maxLines = 1,
                                                                softWrap = false
                                                            )
                                                        }
                                                    }
                                                    Text(
                                                        text = "${proto.exercises.size} exercícios • ${proto.frequency}",
                                                        color = OnSurfaceDim,
                                                        fontSize = 11.sp
                                                    )
                                                }

                                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    // Botão Fixar no Hero Card
                                                    OutlinedButton(
                                                        onClick = {
                                                            viewModel.setPreferredProtocol(
                                                                proto.id,
                                                                hybridWithRunning = true
                                                            )
                                                            com.example.utils.AudioSynth.playChimeSound()
                                                            showSwapWorkoutBottomSheet = false
                                                        },
                                                        border = BorderStroke(1.dp, Primary),
                                                        shape = RoundedCornerShape(8.dp),
                                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                                        modifier = Modifier.height(34.dp)
                                                    ) {
                                                        Text("Fixar", color = Primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                    }

                                                    // Botão Iniciar Agora
                                                    Button(
                                                        onClick = {
                                                            showSwapWorkoutBottomSheet = false
                                                            val w = com.example.data.repository.WorkoutProtocolsRepository.convertProtocolToWorkout(proto)
                                                            viewModel.startWorkoutSession(w)
                                                            com.example.utils.AudioSynth.playTickSound()
                                                        },
                                                        colors = ButtonDefaults.buttonColors(containerColor = Primary, contentColor = Color.Black),
                                                        shape = RoundedCornerShape(8.dp),
                                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                                        modifier = Modifier.height(34.dp)
                                                    ) {
                                                        Text("Iniciar", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Black)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            2 -> {
                                // Lista de Treinos Criados pelo Usuário
                                if (workoutsList.isEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 24.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "Nenhum treino personalizado criado ainda.",
                                            color = OnSurfaceDim,
                                            fontSize = 12.sp
                                        )
                                    }
                                } else {
                                    LazyColumn(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 280.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        items(workoutsList) { wrk ->
                                            Card(
                                                shape = RoundedCornerShape(14.dp),
                                                colors = CardDefaults.cardColors(containerColor = SurfaceDim),
                                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(12.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = wrk.name,
                                                            color = Color.White,
                                                            fontSize = 14.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                        Text(
                                                            text = wrk.description.ifBlank { "Treino salvo" },
                                                            color = OnSurfaceDim,
                                                            fontSize = 11.sp,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }

                                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                        OutlinedButton(
                                                            onClick = {
                                                                viewModel.setPreferredWorkout(wrk.id)
                                                                com.example.utils.AudioSynth.playChimeSound()
                                                                showSwapWorkoutBottomSheet = false
                                                            },
                                                            border = BorderStroke(1.dp, Primary),
                                                            shape = RoundedCornerShape(8.dp),
                                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                                            modifier = Modifier.height(34.dp)
                                                        ) {
                                                            Text("Fixar", color = Primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                        }

                                                        Button(
                                                            onClick = {
                                                                showSwapWorkoutBottomSheet = false
                                                                viewModel.startWorkoutSession(wrk)
                                                                com.example.utils.AudioSynth.playTickSound()
                                                            },
                                                            colors = ButtonDefaults.buttonColors(containerColor = Primary, contentColor = Color.Black),
                                                            shape = RoundedCornerShape(8.dp),
                                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                                            modifier = Modifier.height(34.dp)
                                                        ) {
                                                            Text("Iniciar", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Black)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Rodapé com atalho para a biblioteca completa
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = {
                                    showSwapWorkoutBottomSheet = false
                                    viewModel.activeScreen.value = AppScreen.WORKOUTS
                                    com.example.utils.AudioSynth.playTickSound()
                                }
                            ) {
                                Text(
                                    text = "Ver todos os treinos da biblioteca",
                                    color = Color(0xFFB0B0B8),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (peekingCarouselItem != null) {
        val currentPeek = peekingCarouselItem!!
        HeroWorkoutPreviewBottomSheet(
            item = currentPeek,
            onDismiss = { peekingCarouselItem = null },
            onStartWorkout = {
                peekingCarouselItem = null
                if (currentPeek.isRunning || currentPeek.workout.id.contains("RUNNING")) {
                    viewModel.setAppMode(com.example.data.model.AppMode.RUNNING)
                } else {
                    viewModel.startWorkoutSession(currentPeek.workout)
                }
            }
        )
    }

    if (showDiscardAndStartDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardAndStartDialog = false },
            title = { Text("Descartar Treino Atual?", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Você possui uma sessão de musculação em andamento. Deseja descartá-la para iniciar um novo treino?",
                    color = Color.White.copy(alpha = 0.8f)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.cancelActiveWorkoutDirect()
                        showDiscardAndStartDialog = false
                        com.example.utils.AudioSynth.playTickSound()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Descartar", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardAndStartDialog = false }) {
                    Text("Cancelar", color = Color(0xFF94A3B8))
                }
            },
            containerColor = Color(0xFF1E1E26)
        )
    }

    selectedGymSessionForShareModal?.let { session ->
        com.example.ui.components.GymSessionDetailDialog(
            session = session,
            athleteName = profile.nome.ifBlank { "Atleta" },
            athleteWeightKg = profile.peso,
            gymName = profile.localTreino,
            userProfile = profile,
            allGymSessions = gymWorkoutSessions,
            recentRunningSessions = runningSessions,
            onApplyLoadAdjustments = { adjustments ->
                viewModel.applyAiLoadAdjustmentsToNextWorkout(session.workoutName, adjustments)
            },
            onDismiss = { selectedGymSessionForShareModal = null }
        )
    }
}

@Composable
fun ExpressWorkoutDialog(
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    var expressTimeMinutes by remember { mutableStateOf(40f) }
    var expressFocus by remember { mutableStateOf("Full Body") }
    var showWarmupChoiceDialog by remember { mutableStateOf(false) }

    val estimatedWorkout = remember(expressTimeMinutes.toInt(), expressFocus, viewModel.userProfile.collectAsState().value.nivelExperiencia) {
        try {
            val provider = com.example.engine.QuickWorkoutProvider(
                durationMinutes = expressTimeMinutes.toInt(),
                objective = "Hipertrofia",
                gymProfile = viewModel.activeGymProfile.value,
                technique = "Linear",
                userPreferences = com.example.engine.UserPreferences(
                    level = viewModel.userProfile.value.nivelExperiencia,
                    preferredFocus = "Hipertrofia"
                ),
                muscleGroup = expressFocus
            )
            val context = provider.build()
            com.example.engine.WorkoutEngine.generate(context)
        } catch (e: Exception) {
            null
        }
    }
    
    val exercisesCount = remember(estimatedWorkout) {
        estimatedWorkout?.let { workout ->
            try {
                val exercises = com.example.utils.JsonUtils.parseWorkoutExercises(workout.exercisesJson)
                val mainExercises = exercises.filter { it.blockType != "Aquecimento" && !it.name.contains("Aquecimento", ignoreCase = true) }
                if (mainExercises.isNotEmpty()) mainExercises.size else exercises.size
            } catch (e: Exception) {
                3
            }
        } ?: 3
    }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF1C1C1E) // BackgroundStart
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .imePadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                    // Title and Close Button (always visible / sticky header)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "TREINO EXPRESS",
                            color = Primary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black
                        )
                        IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Fechar",
                                tint = OnSurfaceDim,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Description text
                    Text(
                        text = "Ajuste o tempo e o foco. O Sistema Inteligente de Performance prescreverá o volume ideal para hoje.",
                        color = OnSurfaceDim,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 16.sp
                    )

                    // Scrollable content section
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // 1. ANÁLISE DE DENSIDADE FISIOLÓGICA CARD
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.02f)),
                            border = BorderStroke(1.dp, Primary.copy(alpha = 0.15f))
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = "Informações Fisiológicas",
                                        tint = Primary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "ANÁLISE DE DENSIDADE FISIOLÓGICA",
                                        color = Primary,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = 0.5.sp
                                    )
                                }
                                
                                Text(
                                    text = "Com base no tempo de ${expressTimeMinutes.toInt()} min para o foco ${expressFocus.uppercase()}, o Sistema de Prescrição Fisiológica determina:",
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    lineHeight = 15.sp
                                )
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    // Volume Stat
                                    Card(
                                        modifier = Modifier.weight(1f),
                                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.03f)),
                                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(6.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(
                                                text = "VOLUME MÁX",
                                                color = OnSurfaceDim,
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "${(expressTimeMinutes / 3.5f).toInt()} séries",
                                                color = Color.White,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Black
                                            )
                                        }
                                    }
                                    
                                    // Exercises Stat
                                    Card(
                                        modifier = Modifier.weight(1f),
                                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.03f)),
                                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(6.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(
                                                text = "EXERCÍCIOS",
                                                color = OnSurfaceDim,
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            val countStr = exercisesCount.toString().padStart(2, '0')
                                            Text(
                                                text = countStr,
                                                color = Color.White,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Black
                                            )
                                        }
                                    }
                                    
                                    // Cadence Stat
                                    Card(
                                        modifier = Modifier.weight(1f),
                                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.03f)),
                                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(6.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(
                                                text = "CADÊNCIA",
                                                color = OnSurfaceDim,
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "Controlada",
                                                color = Color.White,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Black,
                                                modifier = Modifier.padding(top = 1.dp)
                                            )
                                        }
                                    }
                                }
                                
                                Text(
                                    text = "A prescrição considera 3.5 minutos por série para garantir execução controlada, tempo de setup e o descanso recuperativo metabólico ideal.",
                                    color = OnSurfaceDim,
                                    fontSize = 9.sp,
                                    lineHeight = 12.sp
                                )
                            }
                        }

                        // 2. CONFIGURATION FIELDS
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.02f)),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "DURAÇÃO DO TREINO",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = 0.5.sp
                                    )
                                    Text(
                                        text = "${expressTimeMinutes.toInt()} MINUTOS",
                                        color = Primary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                                Slider(
                                    value = expressTimeMinutes,
                                    onValueChange = { expressTimeMinutes = it },
                                    valueRange = 15f..90f,
                                    steps = 14,
                                    colors = SliderDefaults.colors(
                                        thumbColor = Primary,
                                        activeTrackColor = Primary,
                                        inactiveTrackColor = Color.White.copy(alpha = 0.1f)
                                    ),
                                    modifier = Modifier.fillMaxWidth().testTag("express_time_slider")
                                )
                            }
                        }

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.02f)),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = "FOCO DO TREINO",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 0.5.sp
                                )
                                val focusOptions = listOf("Full Body", "Peito", "Costas", "Pernas", "Glúteos", "Braços")
                                focusOptions.chunked(3).forEach { rowFocusOptions ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        rowFocusOptions.forEach { focus ->
                                            val selected = expressFocus == focus
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(44.dp)
                                                    .clip(RoundedCornerShape(20.dp))
                                                    .background(if (selected) Primary else Color.White.copy(alpha = 0.05f))
                                                    .border(1.dp, if (selected) Primary else Color.White.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
                                                    .clickable {
                                                        expressFocus = focus
                                                        com.example.utils.AudioSynth.playTickSound()
                                                    }
                                                    .testTag("express_focus_chip_$focus"),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = focus.uppercase(),
                                                    color = if (selected) Color.Black else Color.White,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Black
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Bottom CTA button un-nested and placed immediately below the cards inside the scrollable flow
                        Button(
                            onClick = {
                                showWarmupChoiceDialog = true
                                com.example.utils.AudioSynth.playTickSound()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Primary),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("btn_gerar_treino_express")
                        ) {
                            Text(
                                text = "GERAR TREINO",
                                color = Color.Black,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp
                            )
                        }

                        // Navigation safety spacing at the absolute end of the scrollable column
                        Spacer(
                            modifier = Modifier
                                .navigationBarsPadding()
                                .padding(bottom = 24.dp)
                        )
                    }
                }
            }

            if (showWarmupChoiceDialog) {
                AlertDialog(
                    onDismissRequest = { showWarmupChoiceDialog = false },
                    title = {
                        Text(
                            text = "Deseja incluir aquecimento no treino?",
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp
                        )
                    },
                    text = {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = "O aquecimento prepara seus músculos com uma série leve. Você pode pular se já estiver aquecido.",
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 13.sp,
                                lineHeight = 18.sp
                            )
                            
                            Column(
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp)
                            ) {
                                Button(
                                    onClick = {
                                        viewModel.generateAndStartExpressWorkout(
                                            durationMinutes = expressTimeMinutes.toInt(),
                                            objective = "Hipertrofia",
                                            muscleGroup = expressFocus,
                                            technique = "Linear",
                                            includeWarmup = true
                                        )
                                        showWarmupChoiceDialog = false
                                        onDismiss()
                                        com.example.utils.AudioSynth.playTickSound()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Primary),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("btn_warmup_include")
                                ) {
                                    Text(
                                        text = "Sim, incluir",
                                        color = Color.Black,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 14.sp
                                    )
                                }

                                OutlinedButton(
                                    onClick = {
                                        viewModel.generateAndStartExpressWorkout(
                                            durationMinutes = expressTimeMinutes.toInt(),
                                            objective = "Hipertrofia",
                                            muscleGroup = expressFocus,
                                            technique = "Linear",
                                            includeWarmup = false
                                        )
                                        showWarmupChoiceDialog = false
                                        onDismiss()
                                        com.example.utils.AudioSynth.playTickSound()
                                    },
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("btn_warmup_skip")
                                ) {
                                    Text(
                                        text = "Não, pular",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                    },
                    confirmButton = {},
                    containerColor = Color(0xFF1E1E24),
                    shape = RoundedCornerShape(20.dp)
                )
            }
        }
    }




private fun getWorkoutCategory(workoutName: String): String {
    val nameLower = workoutName.lowercase()
    return when {
        nameLower.contains("push") || nameLower.contains("empurrar") || nameLower.contains("peito") || nameLower.contains("supino") -> "EMPURRAR"
        nameLower.contains("pull") || nameLower.contains("puxar") || nameLower.contains("costas") || nameLower.contains("remada") -> "PUXAR"
        nameLower.contains("leg") || nameLower.contains("perna") || nameLower.contains("inferior") || nameLower.contains("agachamento") -> "PERNAS"
        nameLower.contains("ombro") || nameLower.contains("delto") -> "OMBROS"
        nameLower.contains("cardio") || nameLower.contains("aerob") || nameLower.contains("corrida") || nameLower.contains("esteira") -> "CARDIO"
        nameLower.contains("biceps") || nameLower.contains("triceps") || nameLower.contains("braço") || nameLower.contains("arm") -> "BRAÇOS"
        nameLower.contains("full") || nameLower.contains("completo") || nameLower.contains("corpo") -> "CORPO COMPLETO"
        else -> "TREINO"
    }
}

// ==================== SCREEN 2: WORKOUTS LIST & CREATOR ====================
@Composable
fun WorkoutScreen(viewModel: MainViewModel) {
    val gymFontScale by viewModel.gymFontScale.collectAsState()
    val baseDensity = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides androidx.compose.ui.unit.Density(
            density = baseDensity.density,
            fontScale = (baseDensity.fontScale * gymFontScale).coerceIn(0.75f, 1.65f)
        )
    ) {
        WorkoutScreenContent(viewModel = viewModel)
    }
}

@Composable
private fun WorkoutScreenContent(viewModel: MainViewModel) {
    val workoutsList by viewModel.workouts.collectAsState()
    val activeScreenFlow by viewModel.activeScreen.collectAsState()
    val subTabSelected by viewModel.workoutsSubTab.collectAsState()

    var showCreator by remember { mutableStateOf(false) }
    var showExerciseLib by remember { mutableStateOf(false) }

    if (activeScreenFlow == AppScreen.CREATOR || showCreator) {
        WorkoutCreatorScreen(
            viewModel = viewModel,
            onDismiss = { 
                showCreator = false
                viewModel.activeScreen.value = AppScreen.WORKOUTS
            }
        )
        return
    }

    if (showExerciseLib) {
        Scaffold(
            containerColor = Color.Transparent
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    IconButton(
                        onClick = { showExerciseLib = false },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar", tint = Color.White)
                    }
                    Text(
                        text = "Biblioteca de Exercícios",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
                Box(modifier = Modifier.weight(1f)) {
                    LibraryScreen(viewModel = viewModel)
                }
            }
        }
        return
    }

    Scaffold(
        containerColor = Color.Transparent
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val context = LocalContext.current
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Área de Treino e Cardio",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 12.dp)
                )

                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .clickable {
                            viewModel.historyTab.value = 1
                            viewModel.activeScreen.value = AppScreen.DIARIO
                            com.example.utils.AudioSynth.playTickSound()
                        },
                    color = Color(0xFF0EA5E9).copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, Color(0xFF0EA5E9)),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = Color(0xFF0EA5E9),
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Histórico",
                            color = Color(0xFF0EA5E9),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }

            val subTabTitles = listOf("Meus Treinos", "Programas", "Cardio")
            val activeSubTab = when (subTabSelected) {
                1 -> 2 // Cardio
                2 -> 1 // Programas
                4 -> 1 // Programas
                else -> 0 // Meus Treinos
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(SurfaceDim)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                subTabTitles.forEachIndexed { idx, title ->
                    val isSelected = activeSubTab == idx
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) Primary else Color.Transparent)
                            .clickable {
                                viewModel.workoutsSubTab.value = when (idx) {
                                    1 -> 2 // Programas
                                    2 -> 1 // Cardio
                                    else -> 0 // Meus Treinos
                                }
                                com.example.utils.AudioSynth.playTickSound()
                            }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = title,
                            color = if (isSelected) Color.Black else Color.White.copy(alpha = 0.7f),
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            when (activeSubTab) {
                0 -> {
                    val customWorkouts = workoutsList.filter { it.templateId == null }
                    
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 80.dp)
                    ) {
                        item {
                            Text("Seus Treinos Personalizados", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }

                        item {
                            val mobWorkout = remember { viewModel.getMobilityRecoveryWorkout() }
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.startWorkoutSession(mobWorkout)
                                        com.example.utils.AudioSynth.playTickSound()
                                    },
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF141F18)),
                                border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.45f))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color(0xFF10B981).copy(alpha = 0.18f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AccessibilityNew,
                                            contentDescription = null,
                                            tint = Color(0xFF34D399),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Text(
                                            text = "Mobilidade & Recuperação Ativa • 15 min",
                                            color = Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "SNC, Tendões e Articulações • Livre a qualquer momento",
                                            color = OnSurfaceDim,
                                            fontSize = 11.5.sp,
                                            lineHeight = 15.sp,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Button(
                                        onClick = {
                                            viewModel.startWorkoutSession(mobWorkout)
                                            com.example.utils.AudioSynth.playTickSound()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981), contentColor = Color.Black),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                        modifier = Modifier.height(34.dp)
                                    ) {
                                        Text(
                                            text = "Iniciar",
                                            color = Color.Black,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Black,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                            }
                        }

                        if (customWorkouts.isEmpty()) {
                            item {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp)
                                        .clickable {
                                            showCreator = true
                                            com.example.utils.AudioSynth.playTickSound()
                                        }
                                        .testTag("btn_create_custom_workout_card"),
                                    colors = CardDefaults.cardColors(containerColor = Surface),
                                    border = BorderStroke(1.dp, Primary.copy(alpha = 0.25f)),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 32.dp, horizontal = 24.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AddCircle,
                                            contentDescription = "Novo Treino Personalizado",
                                            tint = Primary,
                                            modifier = Modifier.size(48.dp)
                                        )
                                        Spacer(modifier = Modifier.height(14.dp))
                                        Text(
                                            text = "NENHUM TREINO PERSONALIZADO",
                                            color = Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 1.sp,
                                            textAlign = TextAlign.Center
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = "Toque aqui para montar sua própria ficha, ou escolha um plano completo na aba PROGRAMAS.",
                                            color = OnSurfaceDim,
                                            fontSize = 12.sp,
                                            lineHeight = 18.sp,
                                            textAlign = TextAlign.Center
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Surface(
                                            color = Primary.copy(alpha = 0.16f),
                                            border = BorderStroke(1.dp, Primary.copy(alpha = 0.6f)),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Add,
                                                    contentDescription = null,
                                                    tint = Primary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Text(
                                                    text = "Criar Novo Treino Personalizado",
                                                    color = Primary,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    softWrap = false
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            items(customWorkouts) { workout ->
                                val workoutExs = remember(workout.exercisesJson) {
                                    try {
                                        com.example.utils.JsonUtils.parseWorkoutExercises(workout.exercisesJson)
                                    } catch (e: Exception) {
                                        emptyList()
                                    }
                                }
                                val historyList by viewModel.history.collectAsState()
                                val lastEntry = remember(historyList, workout) {
                                    historyList.filter { 
                                        it.workoutId == workout.id || it.workoutName.equals(workout.name, ignoreCase = true) 
                                    }.maxByOrNull { it.date }
                                }
                                val lastExecText = remember(lastEntry) {
                                    if (lastEntry != null) {
                                        val diffDays = java.util.concurrent.TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - lastEntry.date)
                                        when {
                                            diffDays == 0L -> "Hoje"
                                            diffDays == 1L -> "Ontem"
                                            diffDays < 30L -> "Há $diffDays dias"
                                            else -> "Há ${diffDays / 30}m"
                                        }
                                    } else {
                                        "Nunca executado"
                                    }
                                }
                                val estimatedDuration = (workoutExs.size * 11).coerceAtLeast(25)
                                val totalSets = workoutExs.sumOf { it.sets.size }
                                val objectiveText = if (workout.description.isNotBlank()) workout.description else "Hipertrofia & Força"

                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = Surface),
                                    border = BorderStroke(1.dp, Primary.copy(alpha = 0.2f)),
                                    shape = RoundedCornerShape(18.dp)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .background(Primary.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                                        .border(0.5.dp, Primary.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                                ) {
                                                    Text(
                                                        text = getWorkoutCategory(workout.name),
                                                        color = Primary,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Black,
                                                        letterSpacing = 0.5.sp
                                                    )
                                                }

                                                Box(
                                                    modifier = Modifier
                                                        .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(6.dp))
                                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                                ) {
                                                    Text(
                                                        text = "$totalSets SÉRIES TOTAL",
                                                        color = OnSurfaceDim,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }

                                            IconButton(
                                                onClick = { viewModel.deleteSavedWorkout(workout.id) },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(Icons.Default.Delete, "Excluir Treino", tint = Error.copy(alpha = 0.8f), modifier = Modifier.size(18.dp))
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(10.dp))

                                        Text(
                                            text = workout.name,
                                            color = Color.White,
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Black,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )

                                        Spacer(modifier = Modifier.height(4.dp))

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Flag,
                                                contentDescription = null,
                                                tint = Primary,
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Text(
                                                text = "Objetivo: $objectiveText",
                                                color = OnSurfaceDim,
                                                fontSize = 12.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(14.dp))

                                        // Highlight metrics bar
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                                .border(0.5.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                                                .padding(horizontal = 12.dp, vertical = 10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(Icons.Default.Schedule, null, tint = Primary, modifier = Modifier.size(14.dp))
                                                Text(
                                                    text = "$estimatedDuration min",
                                                    color = Color.White,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }

                                            Text("•", color = OnSurfaceDim)

                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(Icons.Default.FitnessCenter, null, tint = Primary, modifier = Modifier.size(14.dp))
                                                Text(
                                                    text = "${workoutExs.size} exercícios",
                                                    color = Color.White,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }

                                            Text("•", color = OnSurfaceDim)

                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(Icons.Default.History, null, tint = OnSurfaceDim, modifier = Modifier.size(14.dp))
                                                Text(
                                                    text = lastExecText,
                                                    color = if (lastEntry != null) Primary else OnSurfaceDim,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(14.dp))

                                        Button(
                                            onClick = { viewModel.startWorkoutSession(workout) },
                                            colors = ButtonDefaults.buttonColors(containerColor = Primary),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.fillMaxWidth().height(46.dp)
                                        ) {
                                            Icon(Icons.Default.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(20.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("INICIAR ESTE TREINO", color = Color.Black, fontWeight = FontWeight.Black, fontSize = 13.sp)
                                        }
                                    }
                                }
                            }

                            item {
                                OutlinedButton(
                                    onClick = {
                                        showCreator = true
                                        com.example.utils.AudioSynth.playTickSound()
                                    },
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(1.dp, Primary.copy(alpha = 0.5f)),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = Primary.copy(alpha = 0.1f),
                                        contentColor = Primary
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("btn_create_custom_workout_card")
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, tint = Primary, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Criar Novo Treino Personalizado",
                                        color = Primary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }
                    }
                }
                1 -> {
                    Box(modifier = Modifier.weight(1f)) {
                        WorkoutProtocolsListScreen(viewModel = viewModel)
                    }
                }
                2 -> {
                    Box(modifier = Modifier.weight(1f)) {
                        CardioScreen(viewModel = viewModel)
                    }
                }
            }
        }
    }
}

// --- WORKOUT CREATOR MODAL SCREEN ---
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WorkoutCreatorScreen(viewModel: MainViewModel, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    val allExercises = TemplatesAndExercises.ALL_EXERCISES
    
    // Track selected exercises for custom workout
    val selectedExs = remember { mutableStateListOf<Exercise>() }
    var searchPrompt by remember { mutableStateOf("") }
    var warningMessage by remember { mutableStateOf<String?>(null) }
    var selectedCreatorMuscle by remember { mutableStateOf<String?>(null) }

    val muscleGroupImages = mapOf(
        "Peito" to "https://images.unsplash.com/photo-1571019614242-c5c5dee9f50b?q=80&w=200&auto=format&fit=crop",
        "Costas" to "https://images.unsplash.com/photo-1598971639058-fab3c3109a00?q=80&w=200&auto=format&fit=crop",
        "Pernas" to "https://images.unsplash.com/photo-1574680096145-d05b474e2155?q=80&w=200&auto=format&fit=crop",
        "Ombros" to "https://images.unsplash.com/photo-1541534741688-6078c6bfb5c5?q=80&w=200&auto=format&fit=crop",
        "Bíceps" to "https://images.unsplash.com/photo-1581009146145-b5ef050c2e1e?q=80&w=200&auto=format&fit=crop",
        "Tríceps" to "https://images.unsplash.com/photo-1534438327276-14e5300c3a48?q=80&w=200&auto=format&fit=crop",
        "Trapézio" to "https://images.unsplash.com/photo-1517838277536-f5f99be501cd?q=80&w=200&auto=format&fit=crop",
        "Abdominais" to "https://images.unsplash.com/photo-1571019613454-1cb2f99b2d8b?q=80&w=200&auto=format&fit=crop"
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 120.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar", tint = Color.White)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text("Criar Novo Treino", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Surface),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Nome do Treino (ex: Super Ombros)") },
                        modifier = Modifier.fillMaxWidth().testTag("creator_workout_name_input"),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary, focusedLabelColor = Primary, focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                    )

                    OutlinedTextField(
                        value = desc,
                        onValueChange = { desc = it },
                        label = { Text("Descrição rápida (objetivo do treino)") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary, focusedLabelColor = Primary, focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                    )
                }
            }
        }

        item {
            Text("Exercícios Escolhidos (${selectedExs.size})", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            if (selectedExs.isEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = Warning,
                        modifier = Modifier.size(14.dp)
                    )
                    Text("Escolha pelo menos 3 exercícios abaixo para validar seu treino.", color = Warning, fontSize = 11.sp)
                }
            } else {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    selectedExs.forEach { ex ->
                        SuggestionChip(
                            onClick = { selectedExs.remove(ex) },
                            label = { Text(ex.name) },
                            icon = { Icon(Icons.Default.Close, null, modifier = Modifier.size(14.dp)) },
                            colors = SuggestionChipDefaults.suggestionChipColors(labelColor = Color.White)
                        )
                    }
                }
            }
        }

        // Exercises Selection Database searchable list
        item {
            Text("Adicionar Exercícios", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = searchPrompt,
                onValueChange = { 
                    searchPrompt = it 
                    if (it.isNotEmpty()) {
                        selectedCreatorMuscle = null
                    }
                },
                placeholder = { 
                    if (selectedCreatorMuscle != null) {
                        Text("Buscar em $selectedCreatorMuscle...")
                    } else {
                        Text("Buscar no catálogo de 170+...")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Search, null, tint = OnSurfaceDim) },
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary, focusedTextColor = Color.White, unfocusedTextColor = Color.White)
            )
        }

        item {
            Text("Inclusão Rápida (Regra Engine 001):", color = Primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            val quickCategories = listOf("Abdômen", "Panturrilha", "Cardio", "Alongamento", "Mobilidade")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(quickCategories) { qCat ->
                    FilterChip(
                        selected = selectedCreatorMuscle == qCat,
                        onClick = { selectedCreatorMuscle = if (selectedCreatorMuscle == qCat) null else qCat },
                        label = { Text("+ $qCat", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Primary,
                            selectedLabelColor = Color.Black,
                            containerColor = Primary.copy(alpha = 0.1f),
                            labelColor = Primary
                        )
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        if (searchPrompt.isEmpty() && selectedCreatorMuscle == null) {
            item {
                Text("Navegar por Grupo Muscular:", color = OnSurfaceDim, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            
            val muscles = listOf("Peito", "Costas", "Pernas", "Ombros", "Bíceps", "Tríceps", "Trapézio", "Abdominais", "Panturrilha", "Cardio", "Alongamento", "Mobilidade")
            muscles.chunked(2).forEach { pair ->
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        pair.forEach { muscle ->
                            val imgUrl = muscleGroupImages[muscle] ?: ""
                            val count = allExercises.filter { it.category.equals(muscle, ignoreCase = true) }.size
                            Card(
                                onClick = { selectedCreatorMuscle = muscle },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(110.dp),
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
                            ) {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    AsyncImage(
                                        model = imgUrl,
                                        contentDescription = null,
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(
                                                androidx.compose.ui.graphics.Brush.verticalGradient(
                                                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                                                )
                                            )
                                    )
                                    Column(
                                        modifier = Modifier
                                            .align(Alignment.BottomStart)
                                            .padding(12.dp)
                                    ) {
                                        Text(
                                            text = muscle,
                                            color = Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Black
                                        )
                                        Text(
                                            text = "$count exercícios",
                                            color = Primary,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                        if (pair.size < 2) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        } else {
            if (selectedCreatorMuscle != null) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { selectedCreatorMuscle = null; searchPrompt = "" },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar", tint = Primary)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "$selectedCreatorMuscle",
                                color = Primary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                        TextButton(onClick = { selectedCreatorMuscle = null; searchPrompt = "" }) {
                            Text("Ver Todos Grupos", color = OnSurfaceDim, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            val filtered = allExercises.filter { ex ->
                if (selectedCreatorMuscle != null) {
                    ex.category.equals(selectedCreatorMuscle, ignoreCase = true) &&
                    (searchPrompt.isEmpty() || ex.name.contains(searchPrompt, ignoreCase = true))
                } else {
                    ex.name.contains(searchPrompt, ignoreCase = true) || ex.category.contains(searchPrompt, ignoreCase = true)
                }
            }

            if (filtered.isEmpty()) {
                item {
                    Text("Nenhum exercício encontrado.", color = OnSurfaceDim, modifier = Modifier.padding(16.dp))
                }
            } else {
                items(filtered) { ex ->
                    val isAdded = selectedExs.any { it.id == ex.id }
                    Card(
                        colors = CardDefaults.cardColors(containerColor = if (isAdded) SurfaceBright else Surface),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (isAdded) {
                                    selectedExs.removeAll { it.id == ex.id }
                                } else {
                                    selectedExs.add(ex)
                                }
                                com.example.utils.AudioSynth.playTickSound()
                            },
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            com.example.ui.components.ExerciseIllustrationThumbnail(
                                exerciseName = ex.name,
                                muscleGroup = ex.category,
                                cornerRadius = 12.dp,
                                equipment = ex.equipment ?: "",
                                modifier = Modifier.size(44.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(ex.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Text("${ex.category} • ${ex.difficulty}", color = OnSurfaceDim, fontSize = 10.sp)
                            }
                            Icon(
                                imageVector = if (isAdded) Icons.Default.CheckCircle else Icons.Default.Add,
                                contentDescription = "Add toggle",
                                tint = if (isAdded) Primary else OnSurfaceDim
                            )
                        }
                    }
                }
            }
        }

        item {
            warningMessage?.let {
                Text(it, color = Error, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    if (name.isEmpty()) {
                        warningMessage = "Por favor, digite um nome para o treino."
                    } else if (selectedExs.size < 3) {
                        warningMessage = "Validação falhou: Selecione no mínimo 3 exercícios!"
                    } else {
                        viewModel.createAndSaveCustomWorkout(name, desc, selectedExs.toList())
                        onDismiss()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("save_custom_workout_button")
            ) {
                Text("SALVAR E VALIDAR PLANILHA", color = Color.White, fontWeight = FontWeight.Black)
            }
        }
    }
}

// ==================== SCREEN 3: PLAYER (ACTIVE WORKOUT) ====================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(viewModel: MainViewModel) {
    val gymFontScale by viewModel.gymFontScale.collectAsState()
    val baseDensity = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides androidx.compose.ui.unit.Density(
            density = baseDensity.density,
            fontScale = (baseDensity.fontScale * gymFontScale).coerceIn(0.75f, 1.65f)
        )
    ) {
        PlayerScreenContent(viewModel = viewModel, gymFontScale = gymFontScale)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerScreenContent(viewModel: MainViewModel, gymFontScale: Float) {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val activity = context as? android.app.Activity ?: (context as? android.content.ContextWrapper)?.baseContext as? android.app.Activity
        try {
            activity?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            android.util.Log.d("MAX_PERFORMANCE_DEBUG", "KEEP_SCREEN_ON flag added")
            com.example.services.telemetry.Telemetry.log(com.example.data.model.TelemetryEventType.SCREEN_KEEP_ON_ENABLED)
        } catch (e: Exception) {
            com.example.services.telemetry.Telemetry.log(com.example.data.model.TelemetryEventType.ERROR, mapOf("error" to (e.message ?: "Unknown error while enabling keep screen on")))
        }
        onDispose {
            try {
                activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                android.util.Log.d("MAX_PERFORMANCE_DEBUG", "KEEP_SCREEN_ON flag cleared")
                com.example.services.telemetry.Telemetry.log(com.example.data.model.TelemetryEventType.SCREEN_KEEP_ON_DISABLED)
            } catch (e: Exception) {
                com.example.services.telemetry.Telemetry.log(com.example.data.model.TelemetryEventType.ERROR, mapOf("error" to (e.message ?: "Unknown error while disabling keep screen on")))
            }
        }
    }

    val activeW by viewModel.activeWorkout.collectAsState()
    val activeExs by viewModel.activeExercises.collectAsState()
    val currentIndex by viewModel.currentExerciseIndex.collectAsState()
    val workoutDurationState = viewModel.workoutDurationSeconds.collectAsState()
    val hasWorkoutDurationStarted by remember { derivedStateOf { workoutDurationState.value > 0L } }
    
    val showGymSavePrompt by viewModel.showGymSavePrompt.collectAsState()
    val showGymRegistrationFromWorkout by viewModel.showGymRegistrationFromWorkout.collectAsState()
    val exercisesToSaveInGym by viewModel.exercisesToSaveInGym.collectAsState()
    
    val activePrCelebration by viewModel.activePrCelebration.collectAsState()
    val activeRpePrompt by viewModel.activeRpePrompt.collectAsState()
    val prSetIds by viewModel.prSetIds.collectAsState()
    val fastSetWarning by viewModel.fastSetWarningMessage.collectAsState()

    // Auto-dismiss fast set warning after 3.5 seconds
    LaunchedEffect(fastSetWarning) {
        if (fastSetWarning != null) {
            delay(3500)
            viewModel.fastSetWarningMessage.value = null
        }
    }

    // Auto-dismiss PR celebration after 3 seconds
    LaunchedEffect(activePrCelebration) {
        if (activePrCelebration != null) {
            delay(3000)
            viewModel.dismissPrCelebration(activePrCelebration!!)
        }
    }

    // Rest timer (00:00 alert is triggered authoritatively by WorkoutForegroundService or MainViewModel.startTicker)
    val restTimeRemaining by viewModel.restTimeRemaining.collectAsState()
    val isRestActive by viewModel.isRestTimerActive.collectAsState()
    val restRemaining = restTimeRemaining
    val restActive = isRestActive
    val restTotal by viewModel.restTimeTotal.collectAsState()

    // TEMPO timer
    val isTempoActive by viewModel.isTempoTimerActive.collectAsState()
    val tempoRemaining by viewModel.tempoTimeRemaining.collectAsState()
    val tempoTotal by viewModel.tempoTimeTotal.collectAsState()

    // BLE Heart Rate Monitor (In-Workout Connection Support)
    val bleManager = remember { com.example.services.HeartRateBleManager.getInstance(context) }
    val hrConnectionState by bleManager.connectionState.collectAsState()
    val currentBpm by bleManager.currentBpm.collectAsState()
    var showHrPairingSheet by remember { mutableStateOf(false) }

    val blePermissionLauncherInWorkout = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        showHrPairingSheet = true
        bleManager.startScan()
    }

    fun requestBlePermissionsAndOpenSheetInWorkout() {
        val perms = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            arrayOf(
                android.Manifest.permission.BLUETOOTH_SCAN,
                android.Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            arrayOf(
                android.Manifest.permission.ACCESS_FINE_LOCATION
            )
        }
        val allGranted = perms.all {
            androidx.core.content.ContextCompat.checkSelfPermission(context, it) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (allGranted) {
            showHrPairingSheet = true
            bleManager.startScan()
        } else {
            blePermissionLauncherInWorkout.launch(perms)
        }
    }

    if (showHrPairingSheet) {
        com.example.ui.components.BleHeartRatePairingBottomSheet(
            bleManager = bleManager,
            onDismiss = { showHrPairingSheet = false }
        )
    }

    val isPreSessionActive by viewModel.isPreSessionActive.collectAsState()
    val customExList by viewModel.customExercises.collectAsState()
    val progressionSuggestions by viewModel.exerciseProgressionSuggestions.collectAsState()

    val combinedExercises = remember(customExList) {
        val defaultList = com.example.constants.TemplatesAndExercises.ALL_EXERCISES
        val customList = customExList.map { entity ->
            com.example.data.model.Exercise(
                id = entity.id,
                name = entity.name,
                category = entity.category,
                description = entity.description,
                isCustom = true
            )
        }
        defaultList + customList
    }

    var replaceExerciseIndex by remember { mutableStateOf<Int?>(null) }
    var exerciseSearchQuery by remember { mutableStateOf("") }

    // For editing weight/reps
    var editingSetIndices by remember { mutableStateOf<Pair<Int, Int>?>(null) } // Pair(exerciseIndex, setIndex)
    var showWeightSheetForSet by remember { mutableStateOf<Pair<Int, Int>?>(null) } // Pair(exerciseIndex, setIndex)
    var editWeightStr by remember { mutableStateOf("") }
    var editRepsStr by remember { mutableStateOf("") }
    var editTargetField by remember { mutableStateOf("") } // "weight" or "reps"

    var activeTab by remember { mutableStateOf(0) } // 0 = Séries, 1 = Instruções, 2 = Análises
    var showFinishConfirmation by remember { mutableStateOf(false) }

    var showRestPicker by remember { mutableStateOf(false) }
    var restPickerExerciseIndex by remember { mutableStateOf<Int?>(null) }
    var showAddExerciseDialog by remember { mutableStateOf(false) }
    var addExerciseSearchQuery by remember { mutableStateOf("") }
    var showDefaultRestPicker by remember { mutableStateOf(false) }
    var showWeightsDialog by remember { mutableStateOf(false) }

    val gymProfiles by viewModel.gymProfiles.collectAsState()
    val activeGymProfile by viewModel.activeGymProfile.collectAsState()
    var nearbyGymProfileToPrompt by remember { mutableStateOf<GymProfile?>(null) }
    var showNearbyGymDialog by remember { mutableStateOf(false) }
    var showGymSelectorBottomSheet by remember { mutableStateOf(false) }

    var newGymNameFromWorkout by remember { mutableStateOf("") }
    var newGymLatStrFromWorkout by remember { mutableStateOf("") }
    var newGymLngStrFromWorkout by remember { mutableStateOf("") }
    var selectedWorkoutGymExercises by remember(exercisesToSaveInGym) { mutableStateOf(emptySet<String>()) }
    var detectLocationAutoFromWorkout by remember { mutableStateOf(false) }
    var showPermissionRationaleFromWorkout by remember { mutableStateOf(false) }
    var onPermissionGrantedFromWorkoutCallback by remember { mutableStateOf<(() -> Unit)?>(null) }
    var onPermissionDeniedFromWorkoutCallback by remember { mutableStateOf<(() -> Unit)?>(null) }

    val locationPermissionLauncherFromWorkout = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            onPermissionGrantedFromWorkoutCallback?.invoke()
        } else {
            onPermissionDeniedFromWorkoutCallback?.invoke()
        }
    }

    val restPickerIndex = restPickerExerciseIndex ?: currentIndex
    if (showRestPicker && activeW != null && restPickerIndex in activeExs.indices) {
        val currentEx = activeExs[restPickerIndex]
        var tempSeconds by remember(restPickerIndex) { mutableStateOf(currentEx.restTimeSeconds.toString()) }
        
        AlertDialog(
            onDismissRequest = { 
                showRestPicker = false 
                restPickerExerciseIndex = null
            },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Definir Tempo de Descanso",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                showRestPicker = false
                                restPickerExerciseIndex = null
                                com.example.utils.AudioSynth.playTickSound()
                            },
                            border = BorderStroke(1.dp, Primary.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text("Voltar", color = Primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        IconButton(
                            onClick = {
                                showRestPicker = false
                                restPickerExerciseIndex = null
                                com.example.utils.AudioSynth.playTickSound()
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Voltar ao Exercício",
                                tint = Color.LightGray,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .imePadding()
                ) {
                    Text(
                        text = "Selecione ou insira o tempo de descanso para o exercício '${currentEx.name}':",
                        color = Color.LightGray,
                        fontSize = 14.sp
                    )
                    
                    // Predefined options in a row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(30, 45, 60, 90, 120, 150, 180).forEach { sec ->
                            Box(
                                modifier = Modifier
                                    .background(
                                        if (tempSeconds == sec.toString()) Primary else Color.White.copy(alpha = 0.05f),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .border(
                                        1.dp,
                                        if (tempSeconds == sec.toString()) Primary else Color.White.copy(alpha = 0.1f),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                        tempSeconds = sec.toString()
                                        com.example.utils.AudioSynth.playTickSound()
                                    }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${sec}s",
                                    color = if (tempSeconds == sec.toString()) Color.Black else Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    
                    // Custom text field
                    OutlinedTextField(
                        value = tempSeconds,
                        onValueChange = { input ->
                            if (input.all { it.isDigit() }) {
                                tempSeconds = input
                            }
                        },
                        label = { Text("Segundos personalizados", color = Color.Gray) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Primary,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val parsed = tempSeconds.toIntOrNull() ?: 60
                        viewModel.updateExerciseRestTime(restPickerIndex, parsed.coerceIn(5, 600))
                        showRestPicker = false
                        restPickerExerciseIndex = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Primary)
                ) {
                    Text("Salvar", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showRestPicker = false 
                    restPickerExerciseIndex = null
                }) {
                    Text("Cancelar", color = Color.White)
                }
            },
            containerColor = Color(0xFF1E1E24)
        )
    }

    if (showDefaultRestPicker) {
        val currentDefaultRest by viewModel.sessionDefaultRestSeconds.collectAsState()
        var tempDefaultSeconds by remember { mutableStateOf(currentDefaultRest.toString()) }
        
        AlertDialog(
            onDismissRequest = { showDefaultRestPicker = false },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Descanso Padrão da Sessão",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { showDefaultRestPicker = false },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Fechar",
                            tint = Color.LightGray,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .imePadding()
                ) {
                    Text(
                        text = "Selecione ou insira o tempo de descanso padrão aplicado a novos exercícios:",
                        color = Color.LightGray,
                        fontSize = 14.sp
                    )
                    
                    // Predefined options in a row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(30, 45, 60, 90, 120).forEach { sec ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(
                                        if (tempDefaultSeconds == sec.toString()) Primary else Color.White.copy(alpha = 0.05f),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .border(
                                        1.dp,
                                        if (tempDefaultSeconds == sec.toString()) Primary else Color.White.copy(alpha = 0.1f),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                        tempDefaultSeconds = sec.toString()
                                        com.example.utils.AudioSynth.playTickSound()
                                    }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${sec}s",
                                    color = if (tempDefaultSeconds == sec.toString()) Color.Black else Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    
                    // Custom text field
                    OutlinedTextField(
                        value = tempDefaultSeconds,
                        onValueChange = { input ->
                            if (input.all { it.isDigit() }) {
                                tempDefaultSeconds = input
                            }
                        },
                        label = { Text("Segundos personalizados", color = Color.Gray) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Primary,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("default_rest_picker_field")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val parsed = tempDefaultSeconds.toIntOrNull() ?: 60
                        viewModel.setSessionDefaultRestSeconds(parsed.coerceIn(5, 600))
                        showDefaultRestPicker = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Primary)
                ) {
                    Text("Salvar", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDefaultRestPicker = false }) {
                    Text("Cancelar", color = Color.White)
                }
            },
            containerColor = Surface
        )
    }

    if (showAddExerciseDialog) {
        var showInlineCreateForm by remember { mutableStateOf(false) }
        var inlineName by remember { mutableStateOf("") }
        var inlineCategory by remember { mutableStateOf("Peito") }

        androidx.compose.ui.window.Dialog(
            onDismissRequest = { 
                showAddExerciseDialog = false 
                showInlineCreateForm = false
                inlineName = ""
            },
            properties = androidx.compose.ui.window.DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.75f))
                    .clickable { 
                        showAddExerciseDialog = false 
                        showInlineCreateForm = false
                        inlineName = ""
                    },
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .padding(vertical = 16.dp)
                        .navigationBarsPadding()
                        .imePadding()
                        .clickable(enabled = false) {},
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Surface),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Header with Title and Close Button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (showInlineCreateForm) "Cadastrar Novo Aparelho" else "Adicionar exercício",
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (showInlineCreateForm) "Cadastre um aparelho personalizado para adicionar ao treino" else "Selecione um exercício da biblioteca para incluir no treino",
                                    color = OnSurfaceDim,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Normal
                                )
                            }
                            IconButton(onClick = { 
                                showAddExerciseDialog = false 
                                showInlineCreateForm = false
                                inlineName = ""
                            }) {
                                Icon(Icons.Default.Close, "Fechar", tint = Color.White)
                            }
                        }

                        HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 1.dp)

                        if (!showInlineCreateForm) {
                            // Button to trigger inline creation form
                            Button(
                                onClick = { 
                                    showInlineCreateForm = true 
                                    com.example.utils.AudioSynth.playTickSound()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Primary.copy(alpha = 0.15f)),
                                border = BorderStroke(1.dp, Primary.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth().testTag("show_inline_create_form_button"),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Cadastrar Novo Aparelho",
                                    tint = Primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("CADASTRAR NOVO APARELHO", color = Primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            OutlinedTextField(
                                value = addExerciseSearchQuery,
                                onValueChange = { addExerciseSearchQuery = it },
                                placeholder = { Text("Buscar por nome ou grupo...", color = OnSurfaceDim, fontSize = 14.sp) },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Primary,
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("add_exercise_search_field")
                            )

                            val customExList = viewModel.customExercises.collectAsState().value
                            val allCombined = remember(customExList) {
                                val defaultList = com.example.constants.TemplatesAndExercises.ALL_EXERCISES
                                val customList = customExList.map { entity ->
                                    com.example.data.model.Exercise(
                                        id = entity.id,
                                        name = entity.name,
                                        category = entity.category,
                                        description = entity.description,
                                        isCustom = true
                                    )
                                }
                                defaultList + customList
                            }

                            val filteredList = remember(allCombined, addExerciseSearchQuery) {
                                if (addExerciseSearchQuery.isBlank()) {
                                    allCombined
                                } else {
                                    allCombined.filter {
                                        it.name.contains(addExerciseSearchQuery, ignoreCase = true) ||
                                        it.category.contains(addExerciseSearchQuery, ignoreCase = true)
                                    }
                                }
                            }

                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 260.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(filteredList) { exercise ->
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = SurfaceDim),
                                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                viewModel.addExerciseToActiveWorkout(exercise)
                                                showAddExerciseDialog = false
                                                addExerciseSearchQuery = ""
                                                com.example.utils.AudioSynth.playTickSound()
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = exercise.name,
                                                    color = Color.White,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Row(
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = exercise.category,
                                                        color = Primary,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    if (exercise.isCustom) {
                                                        Text("•", color = OnSurfaceDim)
                                                        Text(
                                                            text = "Personalizado",
                                                            color = Color(0xFF29B6F6),
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                }
                                            }
                                            Icon(
                                                imageVector = Icons.Default.Add,
                                                contentDescription = "Selecionar",
                                                tint = Primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(onClick = { showAddExerciseDialog = false }) {
                                    Text("FECHAR", color = Primary, fontWeight = FontWeight.Bold)
                                }
                            }
                        } else {
                            // Inline creation form with vertical scroll
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedTextField(
                                    value = inlineName,
                                    onValueChange = { inlineName = it },
                                    placeholder = { Text("Nome do Aparelho / Exercício (ex: Rosca Unilateral)", color = OnSurfaceDim, fontSize = 14.sp) },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Primary,
                                        unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    ),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().testTag("inline_name_field")
                                )

                                Text("Categoria / Grupo Muscular", color = OnSurfaceDim, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    val categories = listOf(
                                        listOf("Peito", "Costas", "Pernas", "Ombros"),
                                        listOf("Bíceps", "Tríceps", "Trapézio", "Abdominais")
                                    )
                                    categories.forEach { rowList ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            rowList.forEach { cat ->
                                                val isSelected = inlineCategory == cat
                                                Box(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .height(36.dp)
                                                        .background(
                                                            color = if (isSelected) Primary else Color.White.copy(alpha = 0.05f),
                                                            shape = RoundedCornerShape(8.dp)
                                                        )
                                                        .border(
                                                            width = 1.dp,
                                                            color = if (isSelected) Primary else Color.White.copy(alpha = 0.12f),
                                                            shape = RoundedCornerShape(8.dp)
                                                        )
                                                        .clickable {
                                                            inlineCategory = cat
                                                            com.example.utils.AudioSynth.playTickSound()
                                                        },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = cat,
                                                        color = if (isSelected) Color.Black else Color.White,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextButton(onClick = { 
                                        showInlineCreateForm = false 
                                        inlineName = ""
                                    }) {
                                        Text("VOLTAR", color = Color.White.copy(alpha = 0.6f))
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Button(
                                        onClick = {
                                            if (inlineName.isNotBlank()) {
                                                val newId = "custom_${java.util.UUID.randomUUID()}"
                                                viewModel.addNewCustomExercise(
                                                    name = inlineName,
                                                    category = inlineCategory,
                                                    description = "Aparelho cadastrado de forma rápida durante o treino.",
                                                    difficulty = "Médio"
                                                )
                                                val newEx = com.example.data.model.Exercise(
                                                    id = newId,
                                                    name = inlineName,
                                                    category = inlineCategory,
                                                    description = "Aparelho cadastrado de forma rápida durante o treino.",
                                                    isCustom = true
                                                )
                                                viewModel.addExerciseToActiveWorkout(newEx)
                                                
                                                showAddExerciseDialog = false
                                                showInlineCreateForm = false
                                                inlineName = ""
                                                addExerciseSearchQuery = ""
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Primary),
                                        enabled = inlineName.isNotBlank()
                                    ) {
                                        Text("SALVAR & ADICIONAR", color = Color.Black, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val activeGymPrompt by viewModel.activeGymPrompt.collectAsState()
    if (false && activeGymPrompt != null) {
        val (exerciseName, load) = activeGymPrompt!!
        AlertDialog(
            onDismissRequest = { viewModel.dismissGymConfigPrompt(exerciseName) },
            title = {
                Text(
                    text = "Confirmar Carga da Máquina",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "A carga correta desta máquina para o exercício \"$exerciseName\" é ${load} kg?",
                    color = Color.LightGray,
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.confirmGymConfig(exerciseName, load) },
                    modifier = Modifier.testTag("gym_config_confirm_button")
                ) {
                    Text("SIM", color = Color(0xFF00FFCC), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.dismissGymConfigPrompt(exerciseName) },
                    modifier = Modifier.testTag("gym_config_dismiss_button")
                ) {
                    Text("NÃO", color = Color.White)
                }
            },
            containerColor = Color(0xFF1E1E1E),
            textContentColor = Color.LightGray
        )
    }

    if (activeW == null || activeExs.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Transparent), contentAlignment = Alignment.Center) {
            Text("Nenhuma sessão de treino ativa.", color = OnSurfaceDim)
        }
        return
    }

    // Replacement Exercise Flow BottomSheet
    replaceExerciseIndex?.let { index ->
        val currentWorkoutEx = activeExs.getOrNull(index)
        if (currentWorkoutEx != null) {
            FlowSubstitutionBottomSheet(
                currentExercise = currentWorkoutEx,
                allExercisesList = combinedExercises,
                onSelectExercise = { selectedEx ->
                    viewModel.replaceExerciseInActiveWorkout(index, selectedEx)
                    replaceExerciseIndex = null
                    exerciseSearchQuery = ""
                    com.example.utils.AudioSynth.playTickSound()
                },
                onCreateAndReplaceCustomExercise = { customName, equipmentCategory, useLbs ->
                    viewModel.createAndReplaceCustomExerciseInActiveWorkout(
                        index = index,
                        name = customName,
                        equipmentCategory = equipmentCategory,
                        useLbs = useLbs
                    )
                    replaceExerciseIndex = null
                    exerciseSearchQuery = ""
                },
                onDismiss = {
                    replaceExerciseIndex = null
                    exerciseSearchQuery = ""
                }
            )
        }
    }

    // Weight Adjustment Visual Bottom Sheet
    showWeightSheetForSet?.let { (exIndex, setIndex) ->
        val ex = activeExs.getOrNull(exIndex)
        val set = ex?.sets?.getOrNull(setIndex)
        if (ex != null && set != null) {
            WeightAdjustmentBottomSheet(
                targetWeight = set.weight.toDouble(),
                equipment = ex.equipment,
                exerciseName = ex.name,
                onConfirm = { finalWeight ->
                    viewModel.setSetMetrics(exIndex, setIndex, set.reps, finalWeight.toFloat())
                    showWeightSheetForSet = null
                },
                onDismiss = {
                    showWeightSheetForSet = null
                }
            )
        } else {
            showWeightSheetForSet = null
        }
    }

    // Weight/Reps Inline Edit Dialog
    editingSetIndices?.let { (exIndex, setIndex) ->
        val ex = activeExs.getOrNull(exIndex)
        val set = ex?.sets?.getOrNull(setIndex)
        if (ex != null && set != null) {
            val focusRequester = remember { FocusRequester() }

            LaunchedEffect(editingSetIndices) {
                focusRequester.requestFocus()
            }
            
            val initialStr = if (editTargetField == "weight") editWeightStr else editRepsStr
            var textFieldValue by remember(editingSetIndices, editTargetField) {
                mutableStateOf(
                    androidx.compose.ui.text.input.TextFieldValue(
                        text = initialStr,
                        selection = androidx.compose.ui.text.TextRange(0, initialStr.length)
                    )
                )
            }
            
            val isAbdominal = ex.category.lowercase() == "abdominais" || ex.category.lowercase() == "abdominal"
            val inferredEq = com.example.utils.WeightCalculator.inferEquipment(ex.name, ex.equipment)
            val weightLabelText = if (isAbdominal) "Tempo (Segundos)"
                else if (inferredEq.equals("Halteres", ignoreCase = true)) "Peso por halter (kg)"
                else if (inferredEq.equals("Barra", ignoreCase = true)) "Peso total com barra (kg)"
                else "Peso (kg)"

            AlertDialog(
                onDismissRequest = { editingSetIndices = null },
                title = { Text(if (editTargetField == "weight") (if (isAbdominal) "Editar Tempo (Segundos)" else "Editar Carga") else "Editar Repetições", color = Color.White, fontWeight = FontWeight.Bold) },
                text = {
                    OutlinedTextField(
                        value = textFieldValue,
                        onValueChange = { newValue ->
                            textFieldValue = newValue
                            if (editTargetField == "weight") {
                                editWeightStr = newValue.text
                            } else {
                                editRepsStr = newValue.text
                            }
                        },
                        label = { Text(if (editTargetField == "weight") weightLabelText else "Repetições") },
                        placeholder = { Text(if (editTargetField == "weight") (if (isAbdominal) "${set.weight.toInt()}" else "${set.weight}") else "${set.reps}", color = Color.White.copy(alpha = 0.4f)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Primary,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                            focusedLabelColor = Primary,
                            unfocusedLabelColor = Color.White,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester)
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (editTargetField == "weight") {
                                var w = editWeightStr.replace(',', '.').toFloatOrNull() ?: set.weight
                                if (inferredEq.equals("Halteres", ignoreCase = true) || ex.equipment.contains("halter", ignoreCase = true)) {
                                    w = com.example.utils.WeightCalculator.roundToAvailableWeight(w, "Halteres")
                                }
                                viewModel.setSetMetrics(exIndex, setIndex, set.reps, w)
                            } else {
                                val r = editRepsStr.toIntOrNull() ?: set.reps
                                viewModel.setSetMetrics(exIndex, setIndex, r, set.weight)
                            }
                            editingSetIndices = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Primary)
                    ) {
                        Text("SALVAR", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { editingSetIndices = null }) {
                        Text("CANCELAR", color = Color.White)
                    }
                },
                containerColor = Surface
            )
        } else {
            editingSetIndices = null
        }
    }

    if (showWeightsDialog && activeW != null) {
        val currentEx = activeExs.getOrNull(currentIndex)
        if (currentEx != null) {
            val context = LocalContext.current
            val ptBr = remember { Locale.forLanguageTag("pt-BR") }
            val topW = currentEx.sets.maxOfOrNull { it.weight } ?: 20f
            var selectedCategory by remember(currentEx.name) {
                mutableStateOf(
                    com.example.utils.WeightCalculator.getSavedEquipmentCategory(
                        context,
                        currentEx.name,
                        currentEx.equipment
                    )
                )
            }
            var useLbsMode by remember(currentEx.name) {
                mutableStateOf(
                    com.example.utils.WeightCalculator.isLbsMode(context, currentEx.name)
                )
            }
            var selectedStepLbs by remember(currentEx.name) {
                mutableStateOf(
                    com.example.utils.WeightCalculator.getSavedStepLbs(context, currentEx.name)
                )
            }
            var customStepLbsInput by remember(currentEx.name) {
                mutableStateOf(com.example.utils.WeightCalculator.formatStepNumber(selectedStepLbs))
            }
            var startingPinLbsInput by remember(currentEx.name) {
                val initLbs = if (useLbsMode && topW > 0f) {
                    kotlin.math.round(topW / com.example.utils.WeightCalculator.LBS_TO_KG).toInt().toString()
                } else ""
                mutableStateOf(initLbs)
            }
            var selectedStepKg by remember(currentEx.name) {
                mutableStateOf(
                    com.example.utils.WeightCalculator.getSavedWeightStep(
                        context,
                        currentEx.name,
                        currentEx.equipment,
                        topW
                    )
                )
            }
            var customStepInput by remember(currentEx.name) {
                mutableStateOf(com.example.utils.WeightCalculator.formatStepNumber(selectedStepKg))
            }
            var selectedTareKg by remember(currentEx.name) {
                mutableStateOf(
                    com.example.utils.WeightCalculator.getSavedTareWeightKg(context, currentEx.name)
                )
            }
            var customTareInput by remember(currentEx.name) {
                mutableStateOf(
                    if (selectedTareKg > 0f) com.example.utils.WeightCalculator.formatStepNumber(selectedTareKg) else ""
                )
            }
            var tareOnlyMode by remember(currentEx.name) {
                mutableStateOf(
                    com.example.utils.WeightCalculator.isTareOnlyMode(context, currentEx.name)
                )
            }

            val supportsTare = selectedCategory.contains("Articulad", ignoreCase = true) ||
                selectedCategory.contains("Anilha", ignoreCase = true) ||
                selectedCategory.contains("Barra", ignoreCase = true) ||
                selectedCategory.contains("Smith", ignoreCase = true) ||
                selectedCategory.contains("Leg Press", ignoreCase = true)
            val isBodyweightCat = selectedCategory.contains("Peso Corporal", ignoreCase = true)

            AlertDialog(
                onDismissRequest = { showWeightsDialog = false },
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = currentEx.name,
                                color = Color.White,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Black
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Calibração do aparelho: categoria, unidade nativa (KG ou Libras) e tara da máquina.",
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                        }
                        IconButton(
                            onClick = {
                                showWeightsDialog = false
                                com.example.utils.AudioSynth.playTickSound()
                            },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Fechar",
                                tint = Color.LightGray,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .imePadding()
                    ) {
                        // 1. CATEGORIAS REAIS DE EQUIPAMENTO (6 OPÇÕES)
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "1. CATEGORIA DO APARELHO",
                                color = Primary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.5.sp
                            )
                            com.example.utils.WeightCalculator.ALL_EQUIPMENT_CATEGORIES.forEach { catOption ->
                                val isSel = selectedCategory.equals(catOption, ignoreCase = true)
                                Surface(
                                    onClick = {
                                        selectedCategory = catOption
                                        if (catOption.contains("Peso Corporal", ignoreCase = true)) {
                                            useLbsMode = false
                                            tareOnlyMode = false
                                        }
                                        val defStep = com.example.utils.WeightCalculator.defaultStepForCategory(catOption, topW)
                                        selectedStepKg = defStep
                                        customStepInput = com.example.utils.WeightCalculator.formatStepNumber(defStep)
                                        com.example.utils.AudioSynth.playTickSound()
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isSel) Primary.copy(alpha = 0.20f) else Color.White.copy(alpha = 0.04f),
                                    border = BorderStroke(
                                        1.dp,
                                        if (isSel) Primary else Color.White.copy(alpha = 0.12f)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 9.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = catOption,
                                            color = if (isSel) Color.White else Color.White.copy(alpha = 0.8f),
                                            fontSize = 12.sp,
                                            fontWeight = if (isSel) FontWeight.Black else FontWeight.Medium
                                        )
                                        if (isSel) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Selecionado",
                                                tint = Primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (!isBodyweightCat) {
                            // 2. UNIDADE NATIVA DA MÁQUINA (KG vs LIBRAS - LBS)
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "2. UNIDADE NATIVA DO APARELHO (KG vs LIBRAS)",
                                    color = Primary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 0.5.sp
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Surface(
                                        onClick = {
                                            useLbsMode = false
                                            com.example.utils.AudioSynth.playTickSound()
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (!useLbsMode) Primary else Color.White.copy(alpha = 0.06f),
                                        border = BorderStroke(1.dp, if (!useLbsMode) Primary else Color.White.copy(alpha = 0.15f)),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = "Quilos (KG)",
                                            color = if (!useLbsMode) Color.Black else Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Black,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(vertical = 9.dp)
                                        )
                                    }
                                    Surface(
                                        onClick = {
                                            useLbsMode = true
                                            com.example.utils.AudioSynth.playTickSound()
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (useLbsMode) Primary else Color.White.copy(alpha = 0.06f),
                                        border = BorderStroke(1.dp, if (useLbsMode) Primary else Color.White.copy(alpha = 0.15f)),
                                        modifier = Modifier.weight(1.2f)
                                    ) {
                                        Text(
                                            text = "Placas em Libras (LBS)",
                                            color = if (useLbsMode) Color.Black else Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Black,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(vertical = 9.dp)
                                        )
                                    }
                                }

                                if (useLbsMode) {
                                    Text(
                                        text = "Salto das placas em Libras (1 lb = 0,4536 kg). Na execução você verá o valor convertido em KG ao lado da placa em LBS:",
                                        color = Color.White.copy(alpha = 0.65f),
                                        fontSize = 11.sp,
                                        lineHeight = 15.sp
                                    )
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        com.example.utils.WeightCalculator.LBS_STEP_PRESETS.forEach { lbsPreset ->
                                            val isPresetSelected = kotlin.math.abs(selectedStepLbs - lbsPreset) < 0.1f
                                            val kgEquiv = String.format(ptBr, "%.1f", lbsPreset * com.example.utils.WeightCalculator.LBS_TO_KG)
                                            Surface(
                                                onClick = {
                                                    selectedStepLbs = lbsPreset
                                                    customStepLbsInput = com.example.utils.WeightCalculator.formatStepNumber(lbsPreset)
                                                    com.example.utils.AudioSynth.playTickSound()
                                                },
                                                shape = RoundedCornerShape(18.dp),
                                                color = if (isPresetSelected) Primary else Color.White.copy(alpha = 0.06f),
                                                border = BorderStroke(1.dp, if (isPresetSelected) Primary else Color.White.copy(alpha = 0.15f))
                                            ) {
                                                Text(
                                                    text = "+${lbsPreset.toInt()} lbs ($kgEquiv kg)",
                                                    color = if (isPresetSelected) Color.Black else Color.White,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Black,
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                                                )
                                            }
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = customStepLbsInput,
                                            onValueChange = { raw ->
                                                customStepLbsInput = raw
                                                val parsed = raw.replace(',', '.').trim().toFloatOrNull()
                                                if (parsed != null && parsed > 0f) {
                                                    selectedStepLbs = parsed
                                                }
                                            },
                                            label = { Text("Salto (lbs)", fontSize = 11.sp) },
                                            placeholder = { Text("Ex: 15", color = Color.White.copy(alpha = 0.3f)) },
                                            singleLine = true,
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedTextColor = Color.White,
                                                unfocusedTextColor = Color.White,
                                                focusedBorderColor = Primary,
                                                unfocusedBorderColor = Color.White.copy(alpha = 0.18f),
                                                focusedLabelColor = Primary,
                                                unfocusedLabelColor = Color.White.copy(alpha = 0.6f)
                                            ),
                                            modifier = Modifier.weight(1f)
                                        )
                                        OutlinedTextField(
                                            value = startingPinLbsInput,
                                            onValueChange = { raw -> startingPinLbsInput = raw },
                                            label = { Text("Placa atual (lbs)", fontSize = 11.sp) },
                                            placeholder = { Text("Ex: 100", color = Color.White.copy(alpha = 0.3f)) },
                                            singleLine = true,
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedTextColor = Color.White,
                                                unfocusedTextColor = Color.White,
                                                focusedBorderColor = Primary,
                                                unfocusedBorderColor = Color.White.copy(alpha = 0.18f),
                                                focusedLabelColor = Primary,
                                                unfocusedLabelColor = Color.White.copy(alpha = 0.6f)
                                            ),
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                } else {
                                    Text(
                                        text = "Toque em um atalho ou digite o salto exato desta máquina na sua academia (ex: 2,5 kg, 5 kg, 10 kg):",
                                        color = Color.White.copy(alpha = 0.65f),
                                        fontSize = 11.sp,
                                        lineHeight = 15.sp
                                    )

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        com.example.utils.WeightCalculator.GRAVL_STEP_PRESETS.forEach { preset ->
                                            val isPresetSelected = kotlin.math.abs(selectedStepKg - preset) < 0.05f
                                            val label = "+${com.example.utils.WeightCalculator.formatStepNumber(preset)} kg"
                                            Surface(
                                                onClick = {
                                                    selectedStepKg = preset
                                                    customStepInput = com.example.utils.WeightCalculator.formatStepNumber(preset)
                                                    com.example.utils.AudioSynth.playTickSound()
                                                },
                                                shape = RoundedCornerShape(18.dp),
                                                color = if (isPresetSelected) Primary else Color.White.copy(alpha = 0.06f),
                                                border = BorderStroke(
                                                    1.dp,
                                                    if (isPresetSelected) Primary else Color.White.copy(alpha = 0.15f)
                                                )
                                            ) {
                                                Text(
                                                    text = label,
                                                    color = if (isPresetSelected) Color.Black else Color.White,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Black,
                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                                                )
                                            }
                                        }
                                    }

                                    OutlinedTextField(
                                        value = customStepInput,
                                        onValueChange = { raw ->
                                            customStepInput = raw
                                            val parsed = raw.replace(',', '.').trim().toFloatOrNull()
                                            if (parsed != null && parsed > 0f) {
                                                selectedStepKg = parsed
                                            }
                                        },
                                        label = { Text("Incremento Customizado (kg)") },
                                        placeholder = { Text("Ex: 2,5 ou 5", color = Color.White.copy(alpha = 0.3f)) },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White,
                                            focusedBorderColor = Primary,
                                            unfocusedBorderColor = Color.White.copy(alpha = 0.18f),
                                            focusedLabelColor = Primary,
                                            unfocusedLabelColor = Color.White.copy(alpha = 0.6f)
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("weight_input")
                                    )
                                }
                            }

                            // 3. TARA / PESO BASE DO BRAÇO DA MÁQUINA
                            if (supportsTare) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = "3. TARA / PESO BASE DO BRAÇO DA MÁQUINA",
                                        color = Primary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = 0.5.sp
                                    )
                                    Text(
                                        text = "Para máquinas articuladas, Hack ou Smith pesadas onde o próprio carro/braço já possui carga inicial (evitando lançar '1 kg' simbólico):",
                                        color = Color.White.copy(alpha = 0.65f),
                                        fontSize = 11.sp,
                                        lineHeight = 15.sp
                                    )
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        com.example.utils.WeightCalculator.TARE_WEIGHT_PRESETS.forEach { tarePreset ->
                                            val isTareSel = kotlin.math.abs(selectedTareKg - tarePreset) < 0.1f
                                            val label = if (tarePreset <= 0f) "Sem Tara" else "${tarePreset.toInt()} kg"
                                            Surface(
                                                onClick = {
                                                    selectedTareKg = tarePreset
                                                    customTareInput = if (tarePreset > 0f) com.example.utils.WeightCalculator.formatStepNumber(tarePreset) else ""
                                                    if (tarePreset <= 0f) tareOnlyMode = false
                                                    com.example.utils.AudioSynth.playTickSound()
                                                },
                                                shape = RoundedCornerShape(18.dp),
                                                color = if (isTareSel) Primary else Color.White.copy(alpha = 0.06f),
                                                border = BorderStroke(1.dp, if (isTareSel) Primary else Color.White.copy(alpha = 0.15f))
                                            ) {
                                                Text(
                                                    text = label,
                                                    color = if (isTareSel) Color.Black else Color.White,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Black,
                                                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp)
                                                )
                                            }
                                        }
                                    }

                                    OutlinedTextField(
                                        value = customTareInput,
                                        onValueChange = { raw ->
                                            customTareInput = raw
                                            val parsed = raw.replace(',', '.').trim().toFloatOrNull()
                                            if (parsed != null && parsed >= 0f) {
                                                selectedTareKg = parsed
                                            } else if (raw.isBlank()) {
                                                selectedTareKg = 0f
                                                tareOnlyMode = false
                                            }
                                        },
                                        label = { Text("Peso Base / Tara do Braço (kg)", fontSize = 11.sp) },
                                        placeholder = { Text("Ex: 15", color = Color.White.copy(alpha = 0.3f)) },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White,
                                            focusedBorderColor = Primary,
                                            unfocusedBorderColor = Color.White.copy(alpha = 0.18f),
                                            focusedLabelColor = Primary,
                                            unfocusedLabelColor = Color.White.copy(alpha = 0.6f)
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    Surface(
                                        onClick = {
                                            tareOnlyMode = !tareOnlyMode
                                            if (tareOnlyMode && selectedTareKg <= 0f) {
                                                selectedTareKg = 15f
                                                customTareInput = "15"
                                            }
                                            com.example.utils.AudioSynth.playTickSound()
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (tareOnlyMode) Primary.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.04f),
                                        border = BorderStroke(1.dp, if (tareOnlyMode) Primary else Color.White.copy(alpha = 0.14f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "Treinando apenas com o peso base da máquina",
                                                    color = Color.White,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = "Preenche as séries com a tara (${com.example.utils.WeightCalculator.formatStepNumber(if (selectedTareKg > 0f) selectedTareKg else 15f)} kg) sem anilhas extras",
                                                    color = Color(0xFF94A3B8),
                                                    fontSize = 10.sp
                                                )
                                            }
                                            Checkbox(
                                                checked = tareOnlyMode,
                                                onCheckedChange = { checked ->
                                                    tareOnlyMode = checked
                                                    if (checked && selectedTareKg <= 0f) {
                                                        selectedTareKg = 15f
                                                        customTareInput = "15"
                                                    }
                                                },
                                                colors = CheckboxDefaults.colors(
                                                    checkedColor = Primary,
                                                    checkmarkColor = Color.Black
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            // Informativo quando Peso Corporal está selecionado
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Primary.copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, Primary.copy(alpha = 0.35f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Modo Peso Corporal ativo: as séries com 0 kg exibirão 'Peso Corporal' na tabela e serão contabilizadas pelas repetições totais no relatório fisiológico.",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val finalStepKg = customStepInput.replace(',', '.').trim().toFloatOrNull()
                                ?.takeIf { it > 0f } ?: selectedStepKg
                            val finalStepLbs = customStepLbsInput.replace(',', '.').trim().toFloatOrNull()
                                ?.takeIf { it > 0f } ?: selectedStepLbs
                            val finalTareKg = customTareInput.replace(',', '.').trim().toFloatOrNull()
                                ?.takeIf { it >= 0f } ?: selectedTareKg

                            com.example.utils.WeightCalculator.saveExerciseEquipmentConfig(
                                context = context,
                                exerciseName = currentEx.name,
                                category = selectedCategory,
                                stepKg = finalStepKg,
                                isCustomStep = true,
                                useLbs = useLbsMode && !isBodyweightCat,
                                stepLbs = finalStepLbs,
                                tareWeightKg = if (supportsTare && !isBodyweightCat) finalTareKg else 0f,
                                tareOnlyMode = supportsTare && !isBodyweightCat && tareOnlyMode
                            )

                            val pinLbsVal = startingPinLbsInput.replace(',', '.').trim().toFloatOrNull()
                            val weightToApply: Float? = when {
                                isBodyweightCat -> 0f
                                supportsTare && tareOnlyMode && finalTareKg > 0f -> finalTareKg
                                useLbsMode && pinLbsVal != null && pinLbsVal > 0f ->
                                    (kotlin.math.round(pinLbsVal * com.example.utils.WeightCalculator.LBS_TO_KG * 10f) / 10f)
                                else -> null
                            }

                            viewModel.updateExerciseEquipmentByName(
                                exerciseName = currentEx.name,
                                equipmentCategory = selectedCategory,
                                applyWeightToUncompletedSets = weightToApply
                            )
                            showWeightsDialog = false
                            com.example.utils.AudioSynth.playTickSound()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Primary),
                        modifier = Modifier.testTag("save_weights_btn")
                    ) {
                        Text("Salvar Calibração", color = Color.Black, fontWeight = FontWeight.Black)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showWeightsDialog = false
                            com.example.utils.AudioSynth.playTickSound()
                        }
                    ) {
                        Text("Cancelar", color = Color.White.copy(alpha = 0.7f))
                    }
                },
                containerColor = Surface
            )
        }
    }

    var showPauseWorkoutDialog by remember { mutableStateOf(false) }

    if (showPauseWorkoutDialog) {
        AlertDialog(
            onDismissRequest = { showPauseWorkoutDialog = false },
            title = {
                Text(
                    text = "Pausar treino?",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Text(
                    text = "Deseja pausar o treino atual para continuar mais tarde? Seu progresso será preservado.",
                    color = Color.LightGray,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPauseWorkoutDialog = false
                        viewModel.pauseActiveSessionAndExit()
                        com.example.utils.AudioSynth.playTickSound()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Primary)
                ) {
                    Text("Sim", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showPauseWorkoutDialog = false
                        com.example.utils.AudioSynth.playTickSound()
                    }
                ) {
                    Text("Não", color = Color.White)
                }
            },
            containerColor = Surface
        )
    }

    val isAnyPanelOpen = showWeightsDialog || showRestPicker || showDefaultRestPicker || (activeTab != 0)
    androidx.activity.compose.BackHandler(enabled = isAnyPanelOpen) {
        when {
            showWeightsDialog -> {
                showWeightsDialog = false
                com.example.utils.AudioSynth.playTickSound()
            }
            showRestPicker -> {
                showRestPicker = false
                restPickerExerciseIndex = null
                com.example.utils.AudioSynth.playTickSound()
            }
            showDefaultRestPicker -> {
                showDefaultRestPicker = false
                com.example.utils.AudioSynth.playTickSound()
            }
            activeTab != 0 -> {
                activeTab = 0
                com.example.utils.AudioSynth.playTickSound()
            }
        }
    }

    androidx.activity.compose.BackHandler(enabled = activeW != null && !isAnyPanelOpen) {
        if (isPreSessionActive) {
            showPauseWorkoutDialog = true
        } else {
            viewModel.isPreSessionActive.value = true
        }
    }

    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val playerLifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    // Recálculo imediato de remainingSeconds e workoutDurationSeconds a partir do relógio físico (SystemClock.elapsedRealtime())
    // no exato milissegundo em que a tela é desbloqueada (ON_RESUME)
    DisposableEffect(playerLifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                viewModel.syncTimersFromPhysicalClockOnResume()
            }
        }
        playerLifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            playerLifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    if (isPreSessionActive) {

        val isRecalculatingSession by viewModel.isRecalculatingSession.collectAsState()
        var showAdjustmentsBottomSheet by remember { mutableStateOf(false) }
        var warmUpSkipped by remember { mutableStateOf(false) }
        var warmUpCompleted by remember { mutableStateOf(false) }
        var isStartingTransition by remember { mutableStateOf(false) }

        val onStartWorkoutWithFeedback: () -> Unit = {
            if (!isStartingTransition) {
                isStartingTransition = true
                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                try {
                    com.example.utils.AudioSynth.playTickSound()
                } catch (e: Exception) {}

                coroutineScope.launch {
                    kotlinx.coroutines.delay(350)
                    val hasLocationPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                        context,
                        android.Manifest.permission.ACCESS_COARSE_LOCATION
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED

                    if (hasLocationPermission && gymProfiles.isNotEmpty()) {
                        viewModel.checkNearbyGymProfile(context) { nearby ->
                            if (nearby != null) {
                                nearbyGymProfileToPrompt = nearby
                                showNearbyGymDialog = true
                                isStartingTransition = false
                            } else {
                                viewModel.confirmAndStartWorkout()
                                isStartingTransition = false
                            }
                        }
                    } else {
                        if (gymProfiles.isNotEmpty()) {
                            showGymSelectorBottomSheet = true
                            isStartingTransition = false
                        } else {
                            viewModel.confirmAndStartWorkout()
                            isStartingTransition = false
                        }
                    }
                }
            }
        }

        var draggingIndex by remember { mutableStateOf<Int?>(null) }
        var dragOffsetY by remember { mutableStateOf(0f) }
        val density = LocalDensity.current.density
        val listState = rememberLazyListState()

        var selectedExerciseForOverflowIndex by remember { mutableStateOf<Int?>(null) }
        var showExerciseActionVideoDialog by remember { mutableStateOf<String?>(null) }
        var showExerciseActionHistoryDialog by remember { mutableStateOf<String?>(null) }
        var showExerciseActionNoteDialog by remember { mutableStateOf<String?>(null) }

        val warmupEx = remember(activeExs) {
            activeExs.find { it.blockType.equals("Aquecimento", ignoreCase = true) || it.name.contains("Aquecimento", ignoreCase = true) }
        }
        val mainExercises = remember(activeExs) {
            val filtered = activeExs.filterNot { it.blockType.equals("Aquecimento", ignoreCase = true) || it.name.contains("Aquecimento", ignoreCase = true) }
            if (filtered.isNotEmpty()) filtered else activeExs
        }

        val localMainExercises = remember { mutableStateListOf<WorkoutExercise>() }
        LaunchedEffect(mainExercises, draggingIndex) {
            if (draggingIndex == null) {
                localMainExercises.clear()
                localMainExercises.addAll(mainExercises)
            }
        }

        fun mainExerciseStableKey(ex: WorkoutExercise): String =
            "sess_ex_${ex.id.ifBlank { ex.sets.firstOrNull()?.id ?: ex.exerciseId }}_${ex.name}"

        val showWarmUpStage = warmupEx != null && !warmUpSkipped && !warmUpCompleted

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0D0F14))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. TOP HEADER BAR
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        showPauseWorkoutDialog = true
                        com.example.utils.AudioSynth.playTickSound()
                    },
                    modifier = Modifier.size(48.dp).testTag("session_adjustment_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Voltar",
                        tint = Color.White
                    )
                }

                Text(
                    text = if (showWarmUpStage) "Preparação" else "Sessão de Hoje",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val pct = kotlin.math.round(gymFontScale * 100f).toInt()
                    Surface(
                        color = Color(0xFF151922),
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.35f)),
                        modifier = Modifier
                            .height(32.dp)
                            .testTag("presession_gym_font_scale_pill")
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
                                color = if (gymFontScale > 0.86f) Color.White else Color.White.copy(alpha = 0.35f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .clickable(enabled = gymFontScale > 0.86f) {
                                        com.example.utils.AudioSynth.playTickSound()
                                        viewModel.adjustGymFontScale(-0.10f)
                                    }
                                    .padding(horizontal = 6.dp, vertical = 4.dp)
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
                                        viewModel.resetGymFontScale()
                                    }
                                    .padding(horizontal = 3.dp, vertical = 4.dp)
                            )
                            Text(
                                text = "A+",
                                color = if (gymFontScale < 1.39f) Color(0xFF00E5FF) else Color.White.copy(alpha = 0.35f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .clickable(enabled = gymFontScale < 1.39f) {
                                        com.example.utils.AudioSynth.playTickSound()
                                        viewModel.adjustGymFontScale(0.10f)
                                    }
                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Button(
                        onClick = { viewModel.cancelPreSession() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp),
                        modifier = Modifier
                            .height(32.dp)
                            .testTag("session_adjustment_end_button")
                    ) {
                        Text(
                            text = "CANCELAR",
                            color = OnSurfaceDim,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // 2. SESSION IDENTITY (WORKOUT NAME & REC ENGINE NOTE)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = activeW?.name?.uppercase() ?: "TREINO RECOMENDADO",
                    color = Color(0xFF00B0FF),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.5.sp
                )
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Success,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Plano de Treino Recomendado",
                        color = Success,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Text(
                    text = "Seu treino foi preparado considerando seu histórico, recuperação e objetivo.",
                    color = OnSurfaceDim,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }

            // 3. SESSION SUMMARY CHIPS
            val totalSets = mainExercises.sumOf { it.sets.size }
            val estimatedTimeMins = (totalSets * 2.5f + 4f).toInt().coerceAtLeast(20)
            val volumeText = if (totalSets > 22) "Volume Alto" else if (totalSets > 10) "Volume Médio" else "Volume Leve"
            val distinctCategories = mainExercises.map { it.category }.distinct()
            val focusText = if (distinctCategories.isNotEmpty()) {
                distinctCategories.take(2).joinToString(" + ") { cat ->
                    cat.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                }
            } else {
                "Geral"
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SessionSummaryChip(text = "$estimatedTimeMins min")
                SessionSummaryChip(text = "${mainExercises.size} exercícios")
                SessionSummaryChip(text = volumeText)
                SessionSummaryChip(text = focusText)
            }

            // 4. MAIN ACTION: INICIAR TREINO BUTTON (Visible after warm-up phase or if skipped/no warmup)
            AnimatedVisibility(
                visible = !showWarmUpStage,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Button(
                    onClick = onStartWorkoutWithFeedback,
                    enabled = !isStartingTransition,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        contentColor = Color(0xFF06101E)
                    ),
                    contentPadding = PaddingValues(0.dp),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(Color(0xFF00B0FF), Color(0xFF00E5FF))
                            )
                        )
                        .testTag("iniciar_treino_main_button")
                ) {
                    if (isStartingTransition) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = Color(0xFF06101E),
                                strokeWidth = 2.5.dp
                            )
                            Text(
                                text = "INICIANDO SESSÃO...",
                                color = Color(0xFF06101E),
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.5.sp,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    } else {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Iniciar Treino",
                                tint = Color(0xFF06101E),
                                modifier = Modifier.size(28.dp)
                            )
                            Text(
                                text = "INICIAR TREINO",
                                color = Color(0xFF06101E),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.5.sp,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // 4.5 REST TIMER OVERLAY (IF REST TIMER IS RUNNING DURING SESSION)
            AnimatedVisibility(
                visible = isRestActive || restRemaining > 0,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                com.example.ui.components.FlowRestOverlay(
                    restRemaining = restRemaining,
                    restTotal = if (restTotal > 0) restTotal else 60,
                    nextExercise = mainExercises.getOrNull(currentIndex),
                    onModifyRest = { delta -> viewModel.modifyRestTimer(delta) },
                    onSkipRest = { viewModel.skipRestTimer() },
                    currentBpm = currentBpm,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                )
            }

            // 5. STAGE CONTENT: WARM-UP PREPARATION CARD OR MAIN EXERCISES LIST
            if (showWarmUpStage && warmupEx != null) {
                AnimatedVisibility(
                    visible = showWarmUpStage,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically() + slideOutHorizontally(targetOffsetX = { -it })
                ) {
                    com.example.ui.components.WarmUpPreparationCard(
                        warmupExercise = warmupEx,
                        targetExerciseName = mainExercises.firstOrNull()?.name ?: warmupEx.name,
                        estimatedTimeMins = estimatedTimeMins,
                        totalExercisesCount = mainExercises.size,
                        muscleGroupFocus = focusText,
                        onStartWarmUp = {
                            warmUpCompleted = true
                        },
                        onSkip = {
                            warmUpSkipped = true
                        }
                    )
                }
            } else {
                AnimatedVisibility(
                    visible = !showWarmUpStage,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .pointerInput(Unit) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { offset ->
                                        val hitItem = listState.layoutInfo.visibleItemsInfo.firstOrNull { item ->
                                            offset.y.toInt() in item.offset..(item.offset + item.size)
                                        }
                                        val hitIdx = if (hitItem != null) {
                                            localMainExercises.indexOfFirst { mainExerciseStableKey(it) == hitItem.key }
                                        } else -1

                                        if (hitIdx != -1) {
                                            draggingIndex = hitIdx
                                            dragOffsetY = 0f
                                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                            try {
                                                com.example.utils.AudioSynth.playTickSound()
                                            } catch (e: Exception) {}
                                        }
                                    },
                                    onDrag = { change, dragAmount ->
                                        val currentIdx = draggingIndex ?: return@detectDragGesturesAfterLongPress
                                        change.consume()
                                        dragOffsetY += dragAmount.y

                                        val currentEx = localMainExercises.getOrNull(currentIdx) ?: return@detectDragGesturesAfterLongPress
                                        val currentKey = mainExerciseStableKey(currentEx)
                                        val visibleItems = listState.layoutInfo.visibleItemsInfo
                                        val currentItemInfo = visibleItems.find { it.key == currentKey } ?: return@detectDragGesturesAfterLongPress
                                        val draggedCenterY = currentItemInfo.offset + (currentItemInfo.size / 2f) + dragOffsetY

                                        // Troca em tempo real 1 por 1 para baixo
                                        if (dragOffsetY > 0f && currentIdx < localMainExercises.lastIndex) {
                                            val nextEx = localMainExercises[currentIdx + 1]
                                            val nextItemInfo = visibleItems.find { it.key == mainExerciseStableKey(nextEx) }
                                            if (nextItemInfo != null) {
                                                val nextCenterY = nextItemInfo.offset + (nextItemInfo.size / 2f)
                                                if (draggedCenterY > nextCenterY) {
                                                    val moved = localMainExercises.removeAt(currentIdx)
                                                    localMainExercises.add(currentIdx + 1, moved)
                                                    val stepDistance = (nextItemInfo.offset + nextItemInfo.size) - (currentItemInfo.offset + currentItemInfo.size)
                                                    dragOffsetY -= stepDistance
                                                    draggingIndex = currentIdx + 1
                                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                                }
                                            }
                                        }
                                        // Troca em tempo real 1 por 1 para cima
                                        else if (dragOffsetY < 0f && currentIdx > 0) {
                                            val prevEx = localMainExercises[currentIdx - 1]
                                            val prevItemInfo = visibleItems.find { it.key == mainExerciseStableKey(prevEx) }
                                            if (prevItemInfo != null) {
                                                val prevCenterY = prevItemInfo.offset + (prevItemInfo.size / 2f)
                                                if (draggedCenterY < prevCenterY) {
                                                    val moved = localMainExercises.removeAt(currentIdx)
                                                    localMainExercises.add(currentIdx - 1, moved)
                                                    val stepDistance = currentItemInfo.offset - prevItemInfo.offset
                                                    dragOffsetY += stepDistance
                                                    draggingIndex = currentIdx - 1
                                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                                }
                                            }
                                        }

                                        // Auto-scroll suave somente nas extremidades e quando houver rolagem disponível
                                        val layoutInfo = listState.layoutInfo
                                        val itemTop = currentItemInfo.offset + dragOffsetY
                                        val itemBottom = itemTop + currentItemInfo.size
                                        val edgeMargin = 48f * density
                                        if (itemTop < layoutInfo.viewportStartOffset + edgeMargin && listState.canScrollBackward) {
                                            coroutineScope.launch { listState.scrollBy(-12f * density) }
                                        } else if (itemBottom > layoutInfo.viewportEndOffset - edgeMargin && listState.canScrollForward) {
                                            coroutineScope.launch { listState.scrollBy(12f * density) }
                                        }
                                    },
                                    onDragEnd = {
                                        if (draggingIndex != null) {
                                            val newFullOrder = buildList {
                                                if (warmupEx != null && mainExercises.size < activeExs.size) {
                                                    add(warmupEx)
                                                }
                                                addAll(localMainExercises)
                                            }
                                            viewModel.commitReorderedSessionExercises(newFullOrder)
                                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                        }
                                        draggingIndex = null
                                        dragOffsetY = 0f
                                    },
                                    onDragCancel = {
                                        draggingIndex = null
                                        dragOffsetY = 0f
                                    }
                                )
                            },
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 12.dp)
                    ) {
                        itemsIndexed(
                            items = localMainExercises,
                            key = { _, ex -> mainExerciseStableKey(ex) }
                        ) { idx, ex ->
                            val isCurrentDragging = draggingIndex == idx
                            val translationY = if (isCurrentDragging) dragOffsetY else 0f
                            val realIdx = activeExs.indexOfFirst { mainExerciseStableKey(it) == mainExerciseStableKey(ex) }.let { if (it != -1) it else idx }

                            val isExCompleted = ex.sets.isNotEmpty() && ex.sets.all { it.completed }
                            val workoutFlowState by viewModel.workoutFlowState.collectAsState()
                            val isSessionActuallyStarted = workoutFlowState == com.example.ui.viewmodel.WorkoutFlowState.Running ||
                                    hasWorkoutDurationStarted ||
                                    localMainExercises.any { itemEx -> itemEx.sets.any { it.completed } }
                            val isExCurrent = isSessionActuallyStarted && (realIdx == currentIndex)

                            com.example.ui.components.ExerciseCardWithState(
                                exercise = ex,
                                index = idx + 1,
                                isCurrent = isExCurrent,
                                isCompleted = isExCompleted,
                                isUpcoming = !isExCompleted && !isExCurrent,
                                adjustmentText = getPhysiologicalAdjustmentText(ex, activeW),
                                onClick = {
                                    if (!isSessionActuallyStarted) {
                                        viewModel.confirmAndStartWorkout()
                                    }
                                    viewModel.currentExerciseIndex.value = realIdx
                                    viewModel.isPreSessionActive.value = false
                                    try {
                                        com.example.utils.AudioSynth.playTickSound()
                                    } catch (e: Exception) {}
                                },
                                onOverflowClick = {
                                    selectedExerciseForOverflowIndex = realIdx
                                    try {
                                        com.example.utils.AudioSynth.playTickSound()
                                    } catch (e: Exception) {}
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(if (!isCurrentDragging) Modifier.animateItem() else Modifier)
                                    .zIndex(if (isCurrentDragging) 100f else 1f)
                                    .graphicsLayer {
                                        this.translationY = translationY
                                        this.scaleX = if (isCurrentDragging) 1.03f else 1f
                                        this.scaleY = if (isCurrentDragging) 1.03f else 1f
                                        this.shadowElevation = if (isCurrentDragging) 16f else 0f
                                    }
                                    .background(
                                        if (isCurrentDragging) Primary.copy(alpha = 0.12f) else Color.Transparent,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                            )
                        }
                        
                        // 6. DISCRETE FOOTER FOR SETTINGS
                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = {
                                    showAdjustmentsBottomSheet = true
                                    com.example.utils.AudioSynth.playTickSound()
                                },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = OnSurfaceDim),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("personalize_session_button")
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = "Personalizar sessão",
                                        tint = OnSurfaceDim,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "PERSONALIZAR SESSÃO",
                                        color = OnSurfaceDim,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.5.sp
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                }
            }
        }

        // Adjustments Bottom Sheet Dialog
        if (showAdjustmentsBottomSheet) {
            Dialog(
                onDismissRequest = { showAdjustmentsBottomSheet = false },
                properties = DialogProperties(
                    usePlatformDefaultWidth = false,
                    decorFitsSystemWindows = false
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.6f))
                        .clickable { showAdjustmentsBottomSheet = false },
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.95f)
                            .navigationBarsPadding()
                            .imePadding()
                            .clickable(enabled = false) {},
                        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                        colors = CardDefaults.cardColors(containerColor = Surface),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            var draggingIndex by remember { mutableStateOf<Int?>(null) }
                            var dragOffsetY by remember { mutableStateOf(0f) }
                            val density = LocalDensity.current.density
                            val bottomSheetListState = rememberLazyListState()
                            val haptic = LocalHapticFeedback.current
                            val coroutineScope = rememberCoroutineScope()

                            val localSheetExercises = remember { mutableStateListOf<WorkoutExercise>() }
                            LaunchedEffect(activeExs, draggingIndex) {
                                if (draggingIndex == null) {
                                    localSheetExercises.clear()
                                    localSheetExercises.addAll(activeExs)
                                }
                            }
                            fun sheetExerciseStableKey(ex: WorkoutExercise): String =
                                "bs_ex_${ex.id.ifBlank { ex.sets.firstOrNull()?.id ?: ex.exerciseId }}_${ex.name}"

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "Ajustes da Sessão",
                                        color = Color.White,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                    Text(
                                        text = "Ajustes opcionais do treino preparado",
                                        color = OnSurfaceDim,
                                        fontSize = 11.sp
                                    )
                                }
                                IconButton(
                                    onClick = { showAdjustmentsBottomSheet = false }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Fechar",
                                        tint = Color.White
                                    )
                                }
                            }

                            LazyColumn(
                                state = bottomSheetListState,
                                modifier = Modifier
                                    .weight(1f)
                                    .pointerInput(Unit) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = { offset ->
                                                val hitItem = bottomSheetListState.layoutInfo.visibleItemsInfo.firstOrNull { item ->
                                                    offset.y.toInt() in item.offset..(item.offset + item.size)
                                                }
                                                val hitIdx = if (hitItem != null) {
                                                    localSheetExercises.indexOfFirst { sheetExerciseStableKey(it) == hitItem.key }
                                                } else -1

                                                if (hitIdx != -1) {
                                                    draggingIndex = hitIdx
                                                    dragOffsetY = 0f
                                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                                    try {
                                                        com.example.utils.AudioSynth.playTickSound()
                                                    } catch (e: Exception) {}
                                                }
                                            },
                                            onDrag = { change, dragAmount ->
                                                val currentIdx = draggingIndex ?: return@detectDragGesturesAfterLongPress
                                                change.consume()
                                                dragOffsetY += dragAmount.y

                                                val currentEx = localSheetExercises.getOrNull(currentIdx) ?: return@detectDragGesturesAfterLongPress
                                                val currentKey = sheetExerciseStableKey(currentEx)
                                                val visibleItems = bottomSheetListState.layoutInfo.visibleItemsInfo
                                                val currentItemInfo = visibleItems.find { it.key == currentKey } ?: return@detectDragGesturesAfterLongPress
                                                val draggedCenterY = currentItemInfo.offset + (currentItemInfo.size / 2f) + dragOffsetY

                                                if (dragOffsetY > 0f && currentIdx < localSheetExercises.lastIndex) {
                                                    val nextEx = localSheetExercises[currentIdx + 1]
                                                    val nextItemInfo = visibleItems.find { it.key == sheetExerciseStableKey(nextEx) }
                                                    if (nextItemInfo != null) {
                                                        val nextCenterY = nextItemInfo.offset + (nextItemInfo.size / 2f)
                                                        if (draggedCenterY > nextCenterY) {
                                                            val moved = localSheetExercises.removeAt(currentIdx)
                                                            localSheetExercises.add(currentIdx + 1, moved)
                                                            val stepDistance = (nextItemInfo.offset + nextItemInfo.size) - (currentItemInfo.offset + currentItemInfo.size)
                                                            dragOffsetY -= stepDistance
                                                            draggingIndex = currentIdx + 1
                                                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                                        }
                                                    }
                                                } else if (dragOffsetY < 0f && currentIdx > 0) {
                                                    val prevEx = localSheetExercises[currentIdx - 1]
                                                    val prevItemInfo = visibleItems.find { it.key == sheetExerciseStableKey(prevEx) }
                                                    if (prevItemInfo != null) {
                                                        val prevCenterY = prevItemInfo.offset + (prevItemInfo.size / 2f)
                                                        if (draggedCenterY < prevCenterY) {
                                                            val moved = localSheetExercises.removeAt(currentIdx)
                                                            localSheetExercises.add(currentIdx - 1, moved)
                                                            val stepDistance = currentItemInfo.offset - prevItemInfo.offset
                                                            dragOffsetY += stepDistance
                                                            draggingIndex = currentIdx - 1
                                                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                                        }
                                                    }
                                                }

                                                val layoutInfo = bottomSheetListState.layoutInfo
                                                val itemTop = currentItemInfo.offset + dragOffsetY
                                                val itemBottom = itemTop + currentItemInfo.size
                                                val edgeMargin = 48f * density
                                                if (itemTop < layoutInfo.viewportStartOffset + edgeMargin && bottomSheetListState.canScrollBackward) {
                                                    coroutineScope.launch { bottomSheetListState.scrollBy(-12f * density) }
                                                } else if (itemBottom > layoutInfo.viewportEndOffset - edgeMargin && bottomSheetListState.canScrollForward) {
                                                    coroutineScope.launch { bottomSheetListState.scrollBy(12f * density) }
                                                }
                                            },
                                            onDragEnd = {
                                                if (draggingIndex != null) {
                                                    viewModel.commitReorderedSessionExercises(localSheetExercises.toList())
                                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                                }
                                                draggingIndex = null
                                                dragOffsetY = 0f
                                            },
                                            onDragCancel = {
                                                draggingIndex = null
                                                dragOffsetY = 0f
                                            }
                                        )
                                    },
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                // 1. PROGRESSION METHOD
                                item {
                                    Text(
                                        text = "Método de Progressão",
                                        color = Primary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    val trainingMethod by viewModel.selectedTrainingMethod.collectAsState()
                                    var methodDropdownExpanded by remember { mutableStateOf(false) }

                                    ExposedDropdownMenuBox(
                                        expanded = methodDropdownExpanded,
                                        onExpandedChange = { methodDropdownExpanded = !methodDropdownExpanded },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        OutlinedTextField(
                                            readOnly = true,
                                            value = trainingMethod.displayName,
                                            onValueChange = {},
                                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = methodDropdownExpanded) },
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = Primary,
                                                unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                                focusedLabelColor = Primary,
                                                unfocusedLabelColor = Color.White.copy(alpha = 0.6f),
                                                focusedTextColor = Color.White,
                                                unfocusedTextColor = Color.White
                                            ),
                                            modifier = Modifier
                                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                                .fillMaxWidth()
                                                .testTag("method_dropdown_field")
                                        )
                                        
                                        ExposedDropdownMenu(
                                            expanded = methodDropdownExpanded,
                                            onDismissRequest = { methodDropdownExpanded = false },
                                            modifier = Modifier.background(Surface)
                                        ) {
                                            com.example.data.model.TrainingMethod.ALL.forEach { method ->
                                                DropdownMenuItem(
                                                    text = { 
                                                        Text(
                                                            text = method.displayName,
                                                            color = if (trainingMethod.id == method.id) Primary else Color.White,
                                                            fontWeight = if (trainingMethod.id == method.id) FontWeight.Bold else FontWeight.Normal
                                                        ) 
                                                    },
                                                    onClick = {
                                                        viewModel.selectedTrainingMethod.value = method
                                                        methodDropdownExpanded = false
                                                        com.example.utils.AudioSynth.playTickSound()
                                                    },
                                                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                                                )
                                            }
                                        }
                                    }
                                }

                                // 2. DEFAULT REST PICKER
                                item {
                                    Text(
                                        text = "Configuração de Descanso",
                                        color = Primary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    val defaultRestSeconds by viewModel.sessionDefaultRestSeconds.collectAsState()
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.03f)),
                                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth().testTag("default_rest_card")
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("Descanso padrão", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                Text("Aplicado a novos exercícios", color = OnSurfaceDim, fontSize = 10.sp)
                                            }
                                            Text(
                                                text = "${defaultRestSeconds}s",
                                                color = Primary,
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Black
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            TextButton(
                                                onClick = { showDefaultRestPicker = true },
                                                modifier = Modifier.testTag("default_rest_change_button")
                                            ) {
                                                Text("ALTERAR", color = Primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }

                                // 3. OPTIMIZE SEQUENCE
                                item {
                                    Text(
                                        text = "Inteligência de Fadiga",
                                        color = Primary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Button(
                                        onClick = {
                                            viewModel.optimizeActiveWorkoutSequence()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Primary.copy(alpha = 0.1f)),
                                        border = BorderStroke(1.dp, Primary.copy(alpha = 0.5f)),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(44.dp)
                                            .testTag("optimize_sequence_button")
                                    ) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Bolt,
                                                contentDescription = "Evitar Fadiga",
                                                tint = Primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                text = "OTIMIZAR SEQUÊNCIA (EVITAR FADIGA)",
                                                color = Primary,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Black,
                                                letterSpacing = 0.5.sp
                                            )
                                        }
                                    }
                                }

                                // 4. ADD EXERCISE
                                item {
                                    Text(
                                        text = "Estrutura do Treino",
                                        color = Primary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Button(
                                        onClick = { showAddExerciseDialog = true },
                                        colors = ButtonDefaults.buttonColors(containerColor = Primary.copy(alpha = 0.08f)),
                                        border = BorderStroke(1.dp, Primary.copy(alpha = 0.3f)),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(44.dp)
                                            .testTag("add_exercise_button")
                                    ) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Add,
                                                contentDescription = "Adicionar Exercício",
                                                tint = Primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                text = "Adicionar exercício",
                                                color = Primary,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }

                                // 5. EXERCISE REORDER AND REPLACE LIST
                                item {
                                    Text(
                                        text = "Exercícios da Sessão (Pressione e arraste para reordenar)",
                                        color = Primary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                }

                                itemsIndexed(
                                    items = localSheetExercises,
                                    key = { _, ex -> sheetExerciseStableKey(ex) }
                                ) { idx, ex ->
                                    val isCurrentDragging = draggingIndex == idx
                                    val translationY = if (isCurrentDragging) dragOffsetY else 0f
                                    
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .then(if (!isCurrentDragging) Modifier.animateItem() else Modifier)
                                            .zIndex(if (isCurrentDragging) 100f else 1f)
                                            .graphicsLayer {
                                                this.translationY = translationY
                                                this.scaleX = if (isCurrentDragging) 1.03f else 1f
                                                this.scaleY = if (isCurrentDragging) 1.03f else 1f
                                                this.shadowElevation = if (isCurrentDragging) 16f else 0f
                                            },
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isCurrentDragging) Primary.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.02f)
                                        ),
                                        border = BorderStroke(1.dp, if (isCurrentDragging) Primary else Color.White.copy(alpha = 0.04f)),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.DragHandle,
                                                    contentDescription = "Arrastar para reordenar",
                                                    tint = if (isCurrentDragging) Primary else Color.White.copy(alpha = 0.4f),
                                                    modifier = Modifier
                                                        .padding(end = 6.dp)
                                                        .size(20.dp)
                                                )

                                                com.example.ui.components.ExerciseThumbnail(
                                                    category = ex.category,
                                                    name = ex.name,
                                                    size = 42.dp,
                                                    modifier = Modifier.padding(end = 10.dp),
                                                    illustrationId = ex.illustrationId,
                                                    equipment = ex.equipment,
                                                    notes = ex.notes
                                                )

                                                Column(modifier = Modifier.weight(1f)) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        val dynamicFontSize = if (ex.name.length > 28) 11.5.sp else if (ex.name.length > 20) 12.sp else 13.sp
                                                        val dynamicLineHeight = if (ex.name.length > 20) 15.sp else 16.sp
                                                        Text(
                                                            text = ex.name.uppercase(),
                                                            color = Color.White,
                                                            fontSize = dynamicFontSize,
                                                            lineHeight = dynamicLineHeight,
                                                            fontWeight = FontWeight.Black,
                                                            maxLines = 2,
                                                            softWrap = true,
                                                            overflow = TextOverflow.Ellipsis,
                                                            modifier = Modifier.weight(1f)
                                                        )

                                                        val isCurrent = idx == currentIndex
                                                        val isCompleted = ex.sets.isNotEmpty() && ex.sets.all { it.completed }
                                                        if (isCurrent) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .clip(RoundedCornerShape(4.dp))
                                                                    .background(Primary)
                                                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                                                            ) {
                                                                Text("ATUAL", color = Color.Black, fontSize = 8.sp, fontWeight = FontWeight.Black)
                                                            }
                                                        } else if (isCompleted) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .clip(RoundedCornerShape(4.dp))
                                                                    .background(Success.copy(alpha = 0.2f))
                                                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                                                            ) {
                                                                Text("✓ FIM", color = Success, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                                            }
                                                        }
                                                    }
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    val firstSet = ex.sets.firstOrNull()
                                                    val repsText = if (firstSet != null) "${firstSet.reps} reps" else "10 reps"
                                                    val weightText = if (firstSet != null) (if (firstSet.weight % 1f == 0f) "${firstSet.weight.toInt()} kg" else "${firstSet.weight} kg") else "0 kg"
                                                    val metaStr = "${ex.sets.size} séries • $repsText • $weightText"
                                                    Row(
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(
                                                            text = metaStr,
                                                            color = OnSurfaceDim,
                                                            fontSize = 11.sp
                                                        )
                                                        Text("•", color = OnSurfaceDim.copy(alpha = 0.4f))
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            modifier = Modifier.clickable {
                                                                restPickerExerciseIndex = idx
                                                                showRestPicker = true
                                                            }
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.Timer,
                                                                contentDescription = "Tempo de descanso",
                                                                tint = Primary,
                                                                modifier = Modifier.size(12.dp)
                                                            )
                                                            Spacer(modifier = Modifier.width(2.dp))
                                                            Text("${ex.restTimeSeconds}s", color = Primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                        }
                                                    }
                                                }
                                            }
                                            
                                            TextButton(
                                                onClick = { replaceExerciseIndex = idx },
                                                modifier = Modifier.height(36.dp)
                                            ) {
                                                Row(
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.SwapHoriz,
                                                        contentDescription = "Substituir",
                                                        tint = Primary,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                    Text("SUBSTITUIR", color = Primary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            Button(
                                onClick = { showAdjustmentsBottomSheet = false },
                                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                            ) {
                                Text("CONCLUIR AJUSTES", color = Color.Black, fontWeight = FontWeight.Black, fontSize = 14.sp)
                            }
                        }
                    }
                }
            }
        }

        // Overflow Menu Bottom Sheet
        selectedExerciseForOverflowIndex?.let { overflowIdx ->
            val overflowEx = activeExs.getOrNull(overflowIdx)
            if (overflowEx != null) {
                Dialog(
                    onDismissRequest = { selectedExerciseForOverflowIndex = null },
                    properties = DialogProperties(
                        usePlatformDefaultWidth = false,
                        decorFitsSystemWindows = false
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.6f))
                            .clickable { selectedExerciseForOverflowIndex = null },
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .clickable(enabled = false) {},
                            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                            colors = CardDefaults.cardColors(containerColor = Surface),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = overflowEx.name.uppercase(),
                                            color = Color.White,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Black,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${overflowEx.category} • ${overflowEx.sets.size} séries",
                                            color = OnSurfaceDim,
                                            fontSize = 12.sp
                                        )
                                    }
                                    IconButton(
                                        onClick = { selectedExerciseForOverflowIndex = null }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Fechar",
                                            tint = Color.White
                                        )
                                    }
                                }

                                HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

                                // 1. Substituir exercício
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable {
                                            val targetIdx = selectedExerciseForOverflowIndex ?: overflowIdx
                                            selectedExerciseForOverflowIndex = null
                                            replaceExerciseIndex = targetIdx
                                        }
                                        .padding(vertical = 12.dp, horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.SwapHoriz, contentDescription = null, tint = Primary, modifier = Modifier.size(20.dp))
                                    Text("Substituir exercício", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                }

                                // 2. Mover para o final
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable {
                                            val targetIdx = selectedExerciseForOverflowIndex ?: overflowIdx
                                            selectedExerciseForOverflowIndex = null
                                            if (targetIdx in activeExs.indices && targetIdx < activeExs.lastIndex) {
                                                viewModel.reorderActiveWorkoutExercises(targetIdx, activeExs.lastIndex)
                                            }
                                        }
                                        .padding(vertical = 12.dp, horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.VerticalAlignBottom, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                                    Text("Mover para o final", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                }

                                // 3. Vídeo e instruções
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable {
                                            val name = overflowEx.name
                                            selectedExerciseForOverflowIndex = null
                                            showExerciseActionVideoDialog = name
                                        }
                                        .padding(vertical = 12.dp, horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.PlayCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                                    Text("Vídeo e instruções", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                }

                                // 4. Histórico do exercício
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable {
                                            val name = overflowEx.name
                                            selectedExerciseForOverflowIndex = null
                                            showExerciseActionHistoryDialog = name
                                        }
                                        .padding(vertical = 12.dp, horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.History, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                                    Text("Histórico do exercício", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                }

                                // 5. Notas
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable {
                                            val name = overflowEx.name
                                            selectedExerciseForOverflowIndex = null
                                            showExerciseActionNoteDialog = name
                                        }
                                        .padding(vertical = 12.dp, horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Edit, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                                    Text("Notas", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                }

                                // 6. Remover do treino
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable {
                                            val targetIdx = selectedExerciseForOverflowIndex ?: overflowIdx
                                            selectedExerciseForOverflowIndex = null
                                            if (targetIdx in activeExs.indices) {
                                                viewModel.removeExerciseFromActiveWorkout(targetIdx)
                                            }
                                        }
                                        .padding(vertical = 12.dp, horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Delete, contentDescription = null, tint = Error, modifier = Modifier.size(20.dp))
                                    Text("Remover do treino", color = Error, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }

        showExerciseActionVideoDialog?.let { exName ->
            AlertDialog(
                onDismissRequest = { showExerciseActionVideoDialog = null },
                title = { Text("Vídeo e Instruções: $exName", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp) },
                text = { Text("Mantenha a postura adequada durante a execução. Execute o movimento de forma controlada.", color = Color.LightGray, fontSize = 13.sp) },
                confirmButton = {
                    Button(onClick = { showExerciseActionVideoDialog = null }, colors = ButtonDefaults.buttonColors(containerColor = Primary)) {
                        Text("ENTENDIDO", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                },
                containerColor = Surface
            )
        }

        showExerciseActionHistoryDialog?.let { exName ->
            AlertDialog(
                onDismissRequest = { showExerciseActionHistoryDialog = null },
                title = { Text("Histórico: $exName", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp) },
                text = { Text("Nenhum registro anterior encontrado nas sessões recentes.", color = Color.LightGray, fontSize = 13.sp) },
                confirmButton = {
                    Button(onClick = { showExerciseActionHistoryDialog = null }, colors = ButtonDefaults.buttonColors(containerColor = Primary)) {
                        Text("FECHAR", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                },
                containerColor = Surface
            )
        }

        showExerciseActionNoteDialog?.let { exName ->
            val initialNote = remember(exName, activeExs) {
                activeExs.firstOrNull { it.name.trim().equals(exName.trim(), ignoreCase = true) }?.notes ?: ""
            }
            var noteText by remember(exName) { mutableStateOf(initialNote) }
            AlertDialog(
                onDismissRequest = { showExerciseActionNoteDialog = null },
                title = { Text("Notas do Exercício: $exName", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp) },
                text = {
                    OutlinedTextField(
                        value = noteText,
                        onValueChange = { noteText = it },
                        placeholder = { Text("Adicionar observações...", color = OnSurfaceDim) },
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary, unfocusedBorderColor = Color.White.copy(alpha = 0.2f), focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.updateExerciseNotesByName(exName, noteText)
                            showExerciseActionNoteDialog = null
                            com.example.utils.AudioSynth.playTickSound()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Primary)
                    ) {
                        Text("SALVAR", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showExerciseActionNoteDialog = null }) {
                        Text("CANCELAR", color = Color.White)
                    }
                },
                containerColor = Surface
            )
        }

        if (showNearbyGymDialog && nearbyGymProfileToPrompt != null) {
            val nearby = nearbyGymProfileToPrompt!!
            AlertDialog(
                onDismissRequest = { showNearbyGymDialog = false },
                title = {
                    Text(
                        text = "Detectamos uma Academia!",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                text = {
                    Text(
                        text = "Parece que você está na ${nearby.name}. Deseja ativar este perfil de treino?",
                        color = Color.LightGray,
                        fontSize = 14.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.selectGymProfile(nearby)
                            viewModel.confirmAndStartWorkout()
                            showNearbyGymDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Primary)
                    ) {
                        Text("SIM", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showNearbyGymDialog = false
                            showGymSelectorBottomSheet = true
                        }
                    ) {
                        Text("TROCAR", color = Primary)
                    }
                },
                containerColor = Surface
            )
        }

        if (showGymSelectorBottomSheet) {
            Dialog(
                onDismissRequest = { showGymSelectorBottomSheet = false },
                properties = DialogProperties(
                    usePlatformDefaultWidth = false,
                    decorFitsSystemWindows = false
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.6f))
                        .clickable { showGymSelectorBottomSheet = false },
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth(0.92f)
                            .padding(vertical = 24.dp)
                            .imePadding()
                            .clickable(enabled = false) {},
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = Surface),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Selecionar Perfil de Academia",
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Black
                                )
                                IconButton(onClick = { showGymSelectorBottomSheet = false }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Fechar",
                                        tint = OnSurfaceDim
                                    )
                                }
                            }

                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 300.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                item {
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                viewModel.selectGymProfile(null)
                                                viewModel.confirmAndStartWorkout()
                                                showGymSelectorBottomSheet = false
                                            },
                                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.03f)),
                                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(16.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.FitnessCenter,
                                                contentDescription = "Padrão",
                                                tint = OnSurfaceDim,
                                                modifier = Modifier.size(24.dp)
                                            )
                                            Column {
                                                Text(
                                                    text = "Treino Padrão (Sem Perfil)",
                                                    color = Color.White,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = "Usar todos os aparelhos normalmente",
                                                    color = OnSurfaceDim,
                                                    fontSize = 11.sp
                                                )
                                            }
                                        }
                                    }
                                }

                                items(gymProfiles) { profile ->
                                    val isSelected = viewModel.activeGymProfile.collectAsState().value?.id == profile.id
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                viewModel.selectGymProfile(profile)
                                                viewModel.confirmAndStartWorkout()
                                                showGymSelectorBottomSheet = false
                                            },
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isSelected) Primary.copy(alpha = 0.1f) else Color.White.copy(alpha = 0.03f)
                                        ),
                                        border = BorderStroke(
                                            width = 1.dp,
                                            color = if (isSelected) Primary else Color.White.copy(alpha = 0.05f)
                                        )
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(16.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.LocationOn,
                                                    contentDescription = "Academia",
                                                    tint = if (isSelected) Primary else OnSurfaceDim,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                                Column {
                                                    Text(
                                                        text = profile.name,
                                                        color = Color.White,
                                                        fontSize = 14.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Text(
                                                        text = "${profile.availableExercisesIds.size} aparelhos disponíveis",
                                                        color = OnSurfaceDim,
                                                        fontSize = 11.sp
                                                    )
                                                }
                                            }
                                            if (isSelected) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = "Selecionado",
                                                    tint = Primary,
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
            }
        }

        if (isRecalculatingSession) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.65f))
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                awaitPointerEvent()
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Surface),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(color = Primary, modifier = Modifier.size(28.dp))
                        Text(
                            text = "Recalculando cargas fisiológicas...",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        if (isStartingTransition) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                awaitPointerEvent()
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Surface),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, Primary.copy(alpha = 0.4f)),
                    elevation = CardDefaults.cardElevation(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 28.dp, vertical = 22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        CircularProgressIndicator(
                            color = Primary,
                            modifier = Modifier.size(36.dp),
                            strokeWidth = 3.dp
                        )
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Iniciando Sessão",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Sincronizando cargas e ativando cronômetro...",
                                color = OnSurfaceDim,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }

        return
    }

    val displayExercises = remember(activeExs) {
        val filtered = activeExs.filterNot { it.blockType.equals("Aquecimento", ignoreCase = true) || it.name.contains("Aquecimento", ignoreCase = true) }
        if (filtered.isNotEmpty()) filtered else activeExs
    }

    val currentEx = displayExercises.getOrNull(currentIndex) ?: displayExercises.firstOrNull()
    if (currentEx == null) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Transparent), contentAlignment = Alignment.Center) {
            Text("Nenhuma sessão de treino ativa.", color = OnSurfaceDim)
        }
        return
    }

    val customExercisesListState by viewModel.customExercises.collectAsState()
    val matchingExercise = remember(currentEx, customExercisesListState) {
        val customMatch = customExercisesListState.find { it.id == currentEx.exerciseId || it.name.equals(currentEx.name, ignoreCase = true) }
        if (customMatch != null) {
            com.example.data.model.Exercise(
                id = customMatch.id,
                name = customMatch.name,
                category = customMatch.category,
                description = customMatch.description,
                difficulty = customMatch.difficulty,
                isCustom = true,
                isUnilateral = customMatch.name.lowercase().contains("unilateral") || customMatch.name.lowercase().contains("alternada") || customMatch.name.lowercase().contains("alternado")
            )
        } else {
            com.example.constants.TemplatesAndExercises.ALL_EXERCISES.find { it.id == currentEx.exerciseId || it.name.equals(currentEx.name, ignoreCase = true) }
        }
    }

    val currentSetIndex = remember(currentEx.sets) {
        val firstUncompleted = currentEx.sets.indexOfFirst { !it.completed }
        if (firstUncompleted != -1) firstUncompleted else (currentEx.sets.size - 1).coerceAtLeast(0)
    }
    val currentSet = currentEx.sets.getOrNull(currentSetIndex)

    val lastSetCompletionMs by viewModel.lastSetCompletionTimeMs.collectAsState()
    var isSetCooldownActive by remember { mutableStateOf(false) }
    var showActiveExerciseNoteDialog by remember { mutableStateOf(false) }
    LaunchedEffect(lastSetCompletionMs) {
        if (lastSetCompletionMs > 0L) {
            val elapsed = System.currentTimeMillis() - lastSetCompletionMs
            if (elapsed in 0..500L) {
                isSetCooldownActive = true
                kotlinx.coroutines.delay(500L - elapsed)
                isSetCooldownActive = false
            }
        }
    }

    val (histMaxWeight, histMaxReps) = remember(currentEx.name) {
        viewModel.getHistoricalMaxForExercise(currentEx.name)
    }
    val lastCompletedSet = remember(viewModel.history, currentEx.name) {
        viewModel.history.value
            .sortedByDescending { it.date }
            .asSequence()
            .mapNotNull { entry ->
                val exercises = try {
                    com.example.utils.JsonUtils.parseWorkoutExercises(entry.exercisesJson)
                } catch (e: Exception) {
                    emptyList()
                }
                exercises.find { it.name.equals(currentEx.name, ignoreCase = true) }
            }
            .flatMap { it.sets }
            .firstOrNull { it.completed }
    }

    val workoutFlowState by viewModel.workoutFlowState.collectAsState()
    var showSessionStartedBanner by remember { mutableStateOf(false) }
    LaunchedEffect(workoutFlowState) {
        if (workoutFlowState == com.example.ui.viewmodel.WorkoutFlowState.Running && workoutDurationState.value < 6) {
            showSessionStartedBanner = true
            kotlinx.coroutines.delay(3500)
            showSessionStartedBanner = false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = showSessionStartedBanner,
            enter = slideInVertically(
                initialOffsetY = { -it },
                animationSpec = tween(400, easing = FastOutSlowInEasing)
            ) + fadeIn(animationSpec = tween(400)),
            exit = slideOutVertically(
                targetOffsetY = { -it },
                animationSpec = tween(350, easing = FastOutSlowInEasing)
            ) + fadeOut(animationSpec = tween(350)),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 12.dp, start = 16.dp, end = 16.dp)
                .zIndex(200f)
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF161922),
                border = specularRimBorder(width = 1.dp, topAlpha = 0.35f, bottomAlpha = 0.08f),
                shadowElevation = 12.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(Primary, shape = CircleShape)
                    )
                    Column {
                        Text(
                            text = "Sessão Ativa: Bom Treino!",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Cronômetro iniciado • Cargas configuradas",
                            color = Color.White.copy(alpha = 0.65f),
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        Column(modifier = Modifier.fillMaxSize()) {
            // FLOW STATE HEADER & HERO CARDS
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                val activeWorkoutObj by viewModel.activeWorkout.collectAsState()
                val completedSetsForHeader = activeExs.flatMap { it.sets }.count { it.completed }

                FlowStateHeader(
                    workoutName = activeWorkoutObj?.name ?: "Treino de Hoje",
                    currentIndex = currentIndex,
                    totalExercises = displayExercises.size,
                    completedSetsCount = completedSetsForHeader,
                    onBackClick = {
                        viewModel.isPreSessionActive.value = true
                        com.example.utils.AudioSynth.playTickSound()
                    },
                    onFinishClick = {
                        viewModel.tryFinishActiveWorkout(onShowPartialConfirmation = { showFinishConfirmation = true })
                    },
                    currentBpm = currentBpm,
                    onHrClick = {
                        com.example.utils.AudioSynth.playTickSound()
                        requestBlePermissionsAndOpenSheetInWorkout()
                    },
                    fontScale = gymFontScale,
                    onDecreaseFontScale = { viewModel.adjustGymFontScale(-0.10f) },
                    onIncreaseFontScale = { viewModel.adjustGymFontScale(0.10f) },
                    onResetFontScale = { viewModel.resetGymFontScale() }
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 1. Warmup Card (if warmup block)
                    if (currentEx.blockType.equals("Aquecimento", ignoreCase = true) || currentEx.name.contains("Aquecimento", ignoreCase = true)) {
                        FlowWarmupCard(
                            warmupExerciseName = currentEx.name,
                            setsInfo = "${currentEx.sets.size} séries de preparação",
                            onSkipWarmup = {
                                if (currentIndex < displayExercises.lastIndex) {
                                    viewModel.currentExerciseIndex.value = currentIndex + 1
                                    com.example.utils.AudioSynth.playTickSound()
                                }
                            }
                        )
                    }

                    // 2. Protagonist Current Exercise Card
                    FlowCurrentExerciseCard(
                        currentExercise = currentEx,
                        onOpenReplacementSheet = {
                            replaceExerciseIndex = currentIndex
                            exerciseSearchQuery = ""
                            com.example.utils.AudioSynth.playTickSound()
                        }
                    )
                }

                // 3 ACTION CHIPS BELOW HERO IMAGE (Dark + Blue/Cyan Oficial)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min)
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // DESCANSO CHIP
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 38.dp)
                            .fillMaxHeight()
                            .clickable {
                                if (showRestPicker) {
                                    showRestPicker = false
                                    restPickerExerciseIndex = null
                                } else {
                                    restPickerExerciseIndex = currentIndex
                                    showRestPicker = true
                                    activeTab = 0
                                    showWeightsDialog = false
                                }
                                com.example.utils.AudioSynth.playTickSound()
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = if (showRestPicker) Primary.copy(alpha = 0.22f) else Color(0xFF1E2430)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        border = if (showRestPicker) BorderStroke(1.dp, Primary) else specularRimBorder(width = 1.dp, topAlpha = 0.26f, bottomAlpha = 0.05f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 4.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Timer, null, tint = Primary, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "DESCANSO",
                                color = if (showRestPicker) Color(0xFF29B6F6) else Color.White,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // ANÁLISES CHIP
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 38.dp)
                            .fillMaxHeight()
                            .clickable {
                                activeTab = if (activeTab == 2) 0 else 2
                                if (activeTab == 2) {
                                    showRestPicker = false
                                    showWeightsDialog = false
                                }
                                com.example.utils.AudioSynth.playTickSound()
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = if (activeTab == 2) Primary.copy(alpha = 0.22f) else Color(0xFF1E2430)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        border = if (activeTab == 2) BorderStroke(1.dp, Primary) else specularRimBorder(width = 1.dp, topAlpha = 0.26f, bottomAlpha = 0.05f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 4.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Analytics, null, tint = Primary, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "ANÁLISES",
                                color = if (activeTab == 2) Color(0xFF29B6F6) else Color.White,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // CARGAS CHIP
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 38.dp)
                            .fillMaxHeight()
                            .clickable {
                                showWeightsDialog = !showWeightsDialog
                                if (showWeightsDialog) {
                                    showRestPicker = false
                                    activeTab = 0
                                }
                                com.example.utils.AudioSynth.playTickSound()
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = if (showWeightsDialog) Primary.copy(alpha = 0.22f) else Color(0xFF1E2430)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        border = if (showWeightsDialog) BorderStroke(1.dp, Primary) else specularRimBorder(width = 1.dp, topAlpha = 0.26f, bottomAlpha = 0.05f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 4.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Tune, null, tint = Primary, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "CARGAS",
                                color = if (showWeightsDialog) Color(0xFF29B6F6) else Color.White,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            val listState = rememberLazyListState()
            val autoCenterActiveSet = true

            // Centralização Automática da Série Ativa usando LazyListState (sempre ativo por padrão)
            LaunchedEffect(currentIndex, currentSetIndex) {
                if (autoCenterActiveSet && activeTab == 0 && currentEx.sets.isNotEmpty()) {
                    val isAbdominalEx = currentEx.category.lowercase() == "abdominais" || currentEx.category.lowercase() == "abdominal"
                    val hasTempoTimer = currentEx.executionType == "TEMPO" || isAbdominalEx
                    // Item 0 (se houver): timer de tempo. Em seguida: header de séries eficazes (item 1) e cabeçalho de colunas (item 2)
                    val baseHeaderItemsCount = if (hasTempoTimer) 3 else 2
                    val targetItemIndex = (baseHeaderItemsCount + currentSetIndex).coerceIn(0, (baseHeaderItemsCount + currentEx.sets.size - 1).coerceAtLeast(0))

                    val viewportHeight = listState.layoutInfo.viewportEndOffset - listState.layoutInfo.viewportStartOffset
                    val scrollOffset = if (viewportHeight > 0) -viewportHeight / 3 else 0
                    try {
                        listState.animateScrollToItem(index = targetItemIndex, scrollOffset = scrollOffset)
                    } catch (_: Exception) {}
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color.Transparent)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(bottom = 140.dp, top = 4.dp)
            ) {






        // TAB 0: SERIES SET TABLE with Inline editing
        if (activeTab == 0) {
            val isAbdominalEx = currentEx.category.lowercase() == "abdominais" || currentEx.category.lowercase() == "abdominal"
            if (currentEx.executionType == "TEMPO" || isAbdominalEx) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceDim),
                        border = BorderStroke(1.dp, Primary.copy(alpha = 0.3f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Timer,
                                    contentDescription = "Tempo",
                                    tint = Primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                val formattedTempoStr = remember(tempoRemaining) {
                                    val mins = tempoRemaining / 60
                                    val secs = tempoRemaining % 60
                                    String.format(Locale.getDefault(), "%02d:%02d", mins, secs)
                                }
                                Text(
                                    text = formattedTempoStr,
                                    color = Color.White,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        viewModel.resetTempoTimer()
                                        com.example.utils.AudioSynth.playTickSound()
                                    },
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Text("Zerar", color = Color.White, fontSize = 11.sp)
                                }

                                Button(
                                    onClick = {
                                        if (isTempoActive) {
                                            viewModel.pauseTempoTimer()
                                        } else {
                                            if (tempoRemaining <= 0) {
                                                viewModel.startTempoTimer(tempoTotal.coerceAtLeast(60))
                                            } else {
                                                viewModel.resumeTempoTimer()
                                            }
                                        }
                                        com.example.utils.AudioSynth.playTickSound()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Primary),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isTempoActive) Icons.Default.Pause else Icons.Default.PlayArrow,
                                            contentDescription = if (isTempoActive) "Pausar" else "Iniciar",
                                            tint = Color.Black,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = if (isTempoActive) "PAUSAR" else "INICIAR",
                                            color = Color.Black,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Black
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // "Séries Eficazes" header em linha única limpa + badge discreto de progressão conservadora sugerida
            item {
                val exProgressionSuggestion = progressionSuggestions[currentEx.name.trim().lowercase()]
                val currentWorkingWeight = currentEx.sets.firstOrNull { !it.completed }?.weight
                    ?: currentEx.sets.firstOrNull()?.weight
                    ?: 0f
                val showProgressionBadge = exProgressionSuggestion != null &&
                    exProgressionSuggestion.progressionApplied &&
                    exProgressionSuggestion.deltaKg > 0f &&
                    currentWorkingWeight > exProgressionSuggestion.previousWeightKg

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Séries Eficazes",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (showProgressionBadge && exProgressionSuggestion != null) {
                        val deltaFormatted = com.example.utils.WeightCalculator.formatStepNumber(exProgressionSuggestion.deltaKg)
                        Surface(
                            color = Success.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(50),
                            border = BorderStroke(1.dp, Success.copy(alpha = 0.42f)),
                            modifier = Modifier.testTag("progression_suggestion_badge")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                                    contentDescription = "Progressão conservadora de carga sugerida",
                                    tint = Success,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = "+$deltaFormatted kg sugerido",
                                    color = Success,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"),
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }
            }

            // Table Header: SÉRIE | CARGA (KG) | REPS | OK
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "SÉRIE",
                        color = Color(0xFF94A3B8),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.2.sp,
                        modifier = Modifier.width(44.dp),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        softWrap = false
                    )

                    Text(
                        text = "CARGA (KG)",
                        color = Color(0xFF94A3B8),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.3.sp,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        softWrap = false
                    )

                    Text(
                        text = "REPS",
                        color = Color(0xFF94A3B8),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.3.sp,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        softWrap = false
                    )

                    Text(
                        text = "OK",
                        color = Color(0xFF94A3B8),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.2.sp,
                        modifier = Modifier.width(40.dp),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        softWrap = false
                    )
                }
                HorizontalDivider(color = Color(0xFF00B0FF).copy(alpha = 0.15f), thickness = 1.dp)
            }

            // The list of compact series sets (48.dp a 52.dp, cabem 4 a 5 séries sem rolagem)
            itemsIndexed(
                items = currentEx.sets,
                key = { _, set -> set.id }
            ) { setIndex, set ->
                val dismissState = rememberSwipeToDismissBoxState(
                    confirmValueChange = { value ->
                        if (value == SwipeToDismissBoxValue.EndToStart) {
                            viewModel.deleteSetFromExercise(currentIndex, setIndex)
                            com.example.utils.AudioSynth.playTickSound()
                            true
                        } else {
                            false
                        }
                    }
                )

                val isActive = setIndex == currentSetIndex
                val isCompleted = set.completed
                val eqConfigVer by com.example.utils.WeightCalculator.equipmentConfigVersion.collectAsState()
                val weightStep = remember(currentEx.name, currentEx.equipment, set.weight, eqConfigVer) {
                    com.example.utils.WeightCalculator.getSavedWeightStep(
                        context = context,
                        exerciseName = currentEx.name,
                        fallbackEquipment = currentEx.equipment,
                        currentWeight = set.weight
                    )
                }
                val formattedWeightStr = remember(set.weight) {
                    com.example.utils.WeightCalculator.formatStepNumber(set.weight)
                }
                val (displayWeightMain, displayWeightUnit) = remember(currentEx.name, currentEx.equipment, set.weight, eqConfigVer) {
                    com.example.utils.WeightCalculator.formatWeightDisplayPairForSet(
                        context = context,
                        exerciseName = currentEx.name,
                        fallbackEquipment = currentEx.equipment,
                        weightKg = set.weight
                    )
                }

                SwipeToDismissBox(
                    state = dismissState,
                    enableDismissFromStartToEnd = false,
                    enableDismissFromEndToStart = true,
                    backgroundContent = {
                        if (dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart) {
                            val color = when (dismissState.targetValue) {
                                SwipeToDismissBoxValue.EndToStart -> Error
                                else -> Color.Transparent
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(vertical = 1.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(color),
                                contentAlignment = Alignment.CenterEnd
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Excluir Série",
                                    tint = Color.White,
                                    modifier = Modifier.padding(end = 16.dp).size(20.dp)
                                )
                            }
                        } else {
                            Box(modifier = Modifier.fillMaxSize())
                        }
                    }
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = if (isActive) 56.dp else 48.dp)
                            .then(
                                if (isCompleted && !isActive) Modifier.alpha(0.65f)
                                else Modifier
                            ),
                        colors = CardDefaults.cardColors(
                            containerColor = when {
                                isActive -> Color(0xFF00B0FF).copy(alpha = 0.14f)
                                isCompleted -> Color(0xFF11151D)
                                else -> Color(0xFF161922)
                            }
                        ),
                        shape = RoundedCornerShape(12.dp),
                        border = when {
                            isActive -> BorderStroke(
                                1.8.dp,
                                Brush.linearGradient(
                                    colors = listOf(Color(0xFF00E5FF), Color(0xFF00B0FF))
                                )
                            )
                            isCompleted -> BorderStroke(1.dp, Color(0xFF00E676).copy(alpha = 0.25f))
                            else -> specularRimBorder(width = 1.dp, topAlpha = 0.22f, bottomAlpha = 0.04f)
                        }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = if (isActive) 8.dp else 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // 1. Badge da Série (S1, S2...) clicável com debounce de 500ms para alternar conclusão da série
                            Box(
                                modifier = Modifier
                                    .size(36.dp, 32.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable(enabled = isCompleted || !isSetCooldownActive) {
                                        viewModel.toggleSetCompletion(currentIndex, setIndex)
                                    }
                                    .testTag("set_badge_box_${setIndex}")
                                    .then(
                                        when {
                                            isActive -> Modifier
                                                .background(
                                                    brush = Brush.verticalGradient(
                                                        colors = listOf(Color(0xFF29B6F6), Color(0xFF00B0FF))
                                                    )
                                                )
                                                .border(
                                                    BorderStroke(
                                                        1.dp,
                                                        Brush.verticalGradient(
                                                            colors = listOf(
                                                                Color(0xFF80D8FF).copy(alpha = 0.7f),
                                                                Color(0xFF0288D1).copy(alpha = 0.25f)
                                                            )
                                                        )
                                                    ),
                                                    RoundedCornerShape(8.dp)
                                                )
                                            isCompleted -> Modifier
                                                .background(Color(0xFF00E676).copy(alpha = 0.18f))
                                                .border(1.dp, Color(0xFF00E676).copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                            else -> Modifier
                                                .background(Color(0xFF1E2430))
                                                .border(specularRimBorder(1.dp, 0.22f, 0.04f), RoundedCornerShape(8.dp))
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "S${set.index}",
                                    color = when {
                                        isActive -> Color.Black
                                        isCompleted -> Color(0xFF00E676)
                                        else -> Color.White
                                    },
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Black,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }

                            // 2. Controle de Peso (- valor +)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 2.dp)
                            ) {
                                // Botão Diminuir Peso
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.08f))
                                        .clickable {
                                            viewModel.updateSetMetrics(currentIndex, setIndex, repsDelta = 0, weightDelta = -weightStep)
                                            com.example.utils.AudioSynth.playTickSound()
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Remove,
                                        contentDescription = "Diminuir Peso",
                                        tint = Color.White,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }

                                // Campo de Valor de Peso Clicável
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 2.dp)
                                        .height(38.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isActive) Color(0xFF00B0FF).copy(alpha = 0.18f) else Color.White.copy(alpha = 0.05f))
                                        .clickable {
                                            editingSetIndices = Pair(currentIndex, setIndex)
                                            editTargetField = "weight"
                                            editWeightStr = formattedWeightStr
                                            com.example.utils.AudioSynth.playTickSound()
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        val isBwZero = displayWeightMain == "Peso Corporal"
                                        val isLbs = displayWeightUnit.startsWith("(")
                                        val weightFontSize = when {
                                            isBwZero -> 11.sp
                                            isLbs -> 16.sp
                                            displayWeightMain.length <= 3 -> 19.5.sp
                                            displayWeightMain.length == 4 -> 17.sp
                                            else -> 15.sp
                                        }
                                        Text(
                                            text = displayWeightMain,
                                            color = if (isBwZero) Color(0xFF29B6F6) else Color.White,
                                            fontSize = weightFontSize,
                                            lineHeight = if (isBwZero) 11.5.sp else androidx.compose.ui.unit.TextUnit.Unspecified,
                                            fontWeight = FontWeight.ExtraBold,
                                            textAlign = TextAlign.Center,
                                            style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"),
                                            maxLines = if (isBwZero) 2 else 1,
                                            softWrap = isBwZero,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        val showWeightUnit = displayWeightUnit.isNotEmpty() &&
                                            (isLbs || displayWeightMain.length < 4)
                                        if (showWeightUnit) {
                                            Spacer(modifier = Modifier.width(2.dp))
                                            Text(
                                                text = displayWeightUnit,
                                                color = Color(0xFF38BDF8),
                                                fontSize = if (isLbs) 10.sp else 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                softWrap = false,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    if (prSetIds.contains(set.id)) {
                                        Icon(
                                            imageVector = Icons.Default.EmojiEvents,
                                            contentDescription = "Recorde Pessoal",
                                            tint = Color(0xFFFFD700),
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(top = 2.dp, end = 2.dp)
                                                .size(10.dp)
                                        )
                                    }
                                }

                                // Botão Aumentar Peso
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.08f))
                                        .clickable {
                                            viewModel.updateSetMetrics(currentIndex, setIndex, repsDelta = 0, weightDelta = weightStep)
                                            com.example.utils.AudioSynth.playTickSound()
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Aumentar Peso",
                                        tint = Color.White,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }

                            // 3. Controle de Repetições (- reps +)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 2.dp)
                            ) {
                                // Botão Diminuir Repetições
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.08f))
                                        .clickable {
                                            viewModel.updateSetMetrics(currentIndex, setIndex, repsDelta = -1, weightDelta = 0f)
                                            com.example.utils.AudioSynth.playTickSound()
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Remove,
                                        contentDescription = "Diminuir Repetições",
                                        tint = Color.White,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }

                                // Campo de Valor de Repetições Clicável
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 2.dp)
                                        .height(38.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isActive) Color(0xFF00B0FF).copy(alpha = 0.18f) else Color.White.copy(alpha = 0.05f))
                                        .clickable {
                                            editingSetIndices = Pair(currentIndex, setIndex)
                                            editTargetField = "reps"
                                            editRepsStr = "${set.reps}"
                                            com.example.utils.AudioSynth.playTickSound()
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        val repsStr = "${set.reps}"
                                        val isEachSide = com.example.utils.WeightCalculator.isEachSideReps(
                                            context,
                                            currentEx.name,
                                            currentEx.equipment
                                        )
                                        val repsFontSize = when {
                                            repsStr.length <= 3 -> 19.5.sp
                                            repsStr.length == 4 -> 17.sp
                                            else -> 15.sp
                                        }
                                        Text(
                                            text = repsStr,
                                            color = Color.White,
                                            fontSize = repsFontSize,
                                            fontWeight = FontWeight.ExtraBold,
                                            style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"),
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        val showRepsUnit = isEachSide || repsStr.length < 4
                                        if (showRepsUnit) {
                                            Spacer(modifier = Modifier.width(2.dp))
                                            Text(
                                                text = if (isEachSide) "reps/lado" else "reps",
                                                color = Color(0xFF94A3B8),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 1,
                                                softWrap = false,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }

                                // Botão Aumentar Repetições
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.08f))
                                        .clickable {
                                            viewModel.updateSetMetrics(currentIndex, setIndex, repsDelta = 1, weightDelta = 0f)
                                            com.example.utils.AudioSynth.playTickSound()
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Aumentar Repetições",
                                        tint = Color.White,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }

                            // 4. Botão Check de concluir série (Destaque ampliado na série ativa pendente)
                            Box(
                                modifier = Modifier
                                    .size(if (isActive) 38.dp else 34.dp)
                                    .clip(CircleShape)
                                    .alpha(if (!isCompleted && isSetCooldownActive) 0.45f else 1f)
                                    .background(
                                        when {
                                            isCompleted -> Color(0xFF00E676)
                                            isActive -> Color(0xFF00B0FF).copy(alpha = 0.22f)
                                            else -> Color.White.copy(alpha = 0.06f)
                                        }
                                    )
                                    .border(
                                        width = if (isActive) 2.dp else if (isCompleted) 1.5.dp else 1.dp,
                                        color = when {
                                            isCompleted -> Color(0xFF00E676)
                                            isActive -> Color(0xFF00E5FF)
                                            else -> Color.White.copy(alpha = 0.15f)
                                        },
                                        shape = CircleShape
                                    )
                                    .clickable(enabled = isCompleted || !isSetCooldownActive) {
                                        viewModel.toggleSetCompletion(currentIndex, setIndex)
                                    }
                                    .testTag("set_toggle_box_${setIndex}"),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = if (isCompleted) "Série Concluída" else "Concluir Série",
                                    tint = if (isCompleted) Color.Black else if (isActive) Color(0xFF00E5FF) else Color.White.copy(alpha = 0.4f),
                                    modifier = Modifier.size(if (isActive) 20.dp else 18.dp)
                                )
                            }
                        }
                    }
                }
            }

            // "+ Adicionar Série" layout row inside the table
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            viewModel.addSetToExercise(currentIndex)
                            com.example.utils.AudioSynth.playTickSound()
                        }
                        .padding(vertical = 14.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Adicionar Série",
                        tint = Primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Adicionar Série",
                        color = Primary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Preview Discreto do Próximo Exercício na Tela de Séries
                val nextExercise = displayExercises.getOrNull(currentIndex + 1)
                Surface(
                    color = Color(0xFF151922),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0xFF00B0FF).copy(alpha = 0.28f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp, bottom = 4.dp)
                        .clickable(enabled = nextExercise != null) {
                            if (nextExercise != null) {
                                viewModel.currentExerciseIndex.value = currentIndex + 1
                                com.example.utils.AudioSynth.playTickSound()
                            }
                        }
                        .testTag("next_exercise_discreet_preview_bar")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "Próximo Exercício",
                                tint = Color(0xFF00E5FF),
                                modifier = Modifier.size(14.dp)
                            )
                            val previewText = if (nextExercise != null) {
                                val nextSetsCount = nextExercise.sets.size.coerceAtLeast(1)
                                val nextReps = nextExercise.sets.firstOrNull()?.reps ?: 10
                                "A SEGUIR (${currentIndex + 2}/${displayExercises.size}): ${nextExercise.name} • ${nextSetsCount}x${nextReps}"
                            } else {
                                "Último exercício da sessão"
                            }
                            Text(
                                text = previewText,
                                color = if (nextExercise != null) Color.White.copy(alpha = 0.92f) else Color.White.copy(alpha = 0.55f),
                                fontSize = 11.5.sp,
                                lineHeight = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2,
                                softWrap = true,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (nextExercise != null) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = Color(0xFF00B0FF).copy(alpha = 0.16f),
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(0.5.dp, Color(0xFF00E5FF).copy(alpha = 0.45f)),
                                modifier = Modifier
                                    .clickable {
                                        viewModel.swapExerciseOrderInActiveWorkout(currentIndex, currentIndex + 1)
                                    }
                                    .testTag("swap_with_next_exercise_btn")
                            ) {
                                Text(
                                    text = "Inverter",
                                    color = Color(0xFF00E5FF),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }
            }



            // PR Card, Last Series, Recommended Rest and Substituir button
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 1. CARDS DE INFORMAÇÃO (BENTO GRID COM TEMA DARK + AZUL/CIANO OFICIAL — ADAPTÁVEL A 140% DE FONTE)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Card 1: Recorde Pessoal (PR)
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 68.dp)
                                .fillMaxHeight()
                                .wrapContentHeight(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF161922)),
                            border = specularRimBorder(width = 1.dp, topAlpha = 0.28f, bottomAlpha = 0.04f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 68.dp)
                                    .background(
                                        brush = Brush.verticalGradient(
                                            colors = listOf(Color(0xFF161922), Color(0xFF11151D))
                                        )
                                    )
                                    .drawBehind {
                                        drawRect(
                                            brush = Brush.radialGradient(
                                                colors = listOf(
                                                    Primary.copy(alpha = 0.14f),
                                                    Color.Transparent
                                                ),
                                                center = Offset(size.width * 0.5f, 0f),
                                                radius = size.width * 0.9f
                                            )
                                        )
                                    }
                                    .padding(horizontal = 6.dp, vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = "RECORDE",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 10.sp,
                                        lineHeight = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.3.sp,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = if (histMaxWeight > 0f) "${histMaxWeight.toInt()} kg x $histMaxReps" else "--",
                                        color = Color.White,
                                        fontSize = 14.5.sp,
                                        lineHeight = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        // Card 2: Última Série
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 68.dp)
                                .fillMaxHeight()
                                .wrapContentHeight(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF161922)),
                            border = specularRimBorder(width = 1.dp, topAlpha = 0.28f, bottomAlpha = 0.04f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 68.dp)
                                    .background(
                                        brush = Brush.verticalGradient(
                                            colors = listOf(Color(0xFF161922), Color(0xFF11151D))
                                        )
                                    )
                                    .drawBehind {
                                        drawRect(
                                            brush = Brush.radialGradient(
                                                colors = listOf(
                                                    Primary.copy(alpha = 0.14f),
                                                    Color.Transparent
                                                ),
                                                center = Offset(size.width * 0.5f, 0f),
                                                radius = size.width * 0.9f
                                            )
                                        )
                                    }
                                    .padding(horizontal = 6.dp, vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = "ÚLTIMA",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 10.sp,
                                        lineHeight = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.3.sp,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = if (lastCompletedSet != null) "${lastCompletedSet.weight.toInt()} kg x ${lastCompletedSet.reps}" else "--",
                                        color = Color.White,
                                        fontSize = 14.5.sp,
                                        lineHeight = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        // Card 3: Descanso Recomendado
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 68.dp)
                                .fillMaxHeight()
                                .wrapContentHeight()
                                .clickable {
                                    restPickerExerciseIndex = currentIndex
                                    showRestPicker = true
                                    com.example.utils.AudioSynth.playTickSound()
                                },
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF161922)),
                            border = specularRimBorder(width = 1.dp, topAlpha = 0.28f, bottomAlpha = 0.04f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 68.dp)
                                    .background(
                                        brush = Brush.verticalGradient(
                                            colors = listOf(Color(0xFF161922), Color(0xFF11151D))
                                        )
                                    )
                                    .drawBehind {
                                        drawRect(
                                            brush = Brush.radialGradient(
                                                colors = listOf(
                                                    Primary.copy(alpha = 0.14f),
                                                    Color.Transparent
                                                ),
                                                center = Offset(size.width * 0.5f, 0f),
                                                radius = size.width * 0.9f
                                            )
                                        )
                                    }
                                    .padding(horizontal = 6.dp, vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = "DESCANSO",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 10.sp,
                                        lineHeight = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.3.sp,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Text(
                                            text = "${currentEx.restTimeSeconds}s",
                                            color = Color.White,
                                            fontSize = 14.5.sp,
                                            lineHeight = 18.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                        Icon(
                                            imageVector = Icons.Default.ArrowDropDown,
                                            contentDescription = "Configurar descanso",
                                            tint = Color(0xFF29B6F6),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Sleek, minimal Substituir & Anotar Feedback Buttons (flexíveis em 140% de fonte)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                replaceExerciseIndex = currentIndex
                                exerciseSearchQuery = ""
                                com.example.utils.AudioSynth.playTickSound()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E2430)),
                            border = specularRimBorder(width = 1.dp, topAlpha = 0.28f, bottomAlpha = 0.04f),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 38.dp)
                                .wrapContentHeight()
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SwapHoriz,
                                    contentDescription = "Substituir exercício",
                                    tint = Primary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "SUBSTITUIR",
                                    color = Color.White,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Button(
                            onClick = {
                                showActiveExerciseNoteDialog = true
                                com.example.utils.AudioSynth.playTickSound()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (currentEx.notes.isNotBlank()) Color(0xFF0F3B4B) else Color(0xFF1E2330)
                            ),
                            border = BorderStroke(1.dp, if (currentEx.notes.isNotBlank()) Color(0xFF0EA5E9) else Color.White.copy(alpha = 0.12f)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                            modifier = Modifier
                                .weight(1.2f)
                                .heightIn(min = 38.dp)
                                .wrapContentHeight()
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.EditNote,
                                    contentDescription = "Anotar feedback",
                                    tint = if (currentEx.notes.isNotBlank()) Color(0xFF0EA5E9) else Color.White,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = if (currentEx.notes.isNotBlank()) "NOTA REGISTRADA" else "ANOTAR FEEDBACK",
                                    color = if (currentEx.notes.isNotBlank()) Color(0xFF38BDF8) else Color(0xFFFBF7F4),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    if (currentEx.notes.isNotBlank()) {
                        Surface(
                            color = Color(0xFF0F1E29),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(0.5.dp, Color(0xFF0EA5E9).copy(alpha = 0.4f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showActiveExerciseNoteDialog = true }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.EditNote,
                                    contentDescription = null,
                                    tint = Color(0xFF00B0FF),
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = currentEx.notes,
                                    color = Color(0xFFE0F2FE),
                                    fontSize = 11.sp,
                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    // Folga extra de 96.dp para garantir que SUBSTITUIR e ANOTAR FEEDBACK fiquem sempre acima da barra REGISTRAR SÉRIE
                    Spacer(modifier = Modifier.height(96.dp))
                }
            }
        }

        // TAB 1: VÍDEO & INSTRUCTIONS
        if (activeTab == 1) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Surface),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("Demonstração da Execução", color = Primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(10.dp))
                        
                        // FUTURISTIC VIDEO PLAYER MOCKUP CARD (GRAVL-STYLE)
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                            colors = CardDefaults.cardColors(containerColor = SurfaceBright),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    drawRect(
                                        brush = Brush.verticalGradient(
                                            colors = listOf(Color.Black.copy(alpha = 0.4f), Color.Black.copy(alpha = 0.85f))
                                        )
                                    )
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .background(Primary.copy(alpha = 0.15f), shape = CircleShape)
                                            .border(1.5.dp, Primary, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayCircle,
                                            contentDescription = "Demonstração de Execução",
                                            tint = Primary,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                    Text("Guia de Execução Técnica", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    Text("Postura, amplitude e controle do movimento", color = OnSurfaceDim, fontSize = 11.sp)
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text("Descrição do Exercício", color = Primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Este exercício foca nos músculos estabilizadores e exige cadência rigorosa (recomenda-se 2s na descida e 1s na subida rápida). Atente-se à postura para poupar articulações.",
                            color = Color.White,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Musculatura Ativada", color = Primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Foco Primário: ${currentEx.category}\nFoco Secundário: Ombros, Estabilizadores",
                            color = OnSurfaceDim,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        // TAB 2: ANALYSIS (Cargas, volume cumulativo)
        if (activeTab == 2) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .clickable {
                            activeTab = 0
                            com.example.utils.AudioSynth.playTickSound()
                        },
                    colors = CardDefaults.cardColors(containerColor = Primary.copy(alpha = 0.15f)),
                    border = BorderStroke(1.dp, Primary.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Primary, modifier = Modifier.size(18.dp))
                            Text("Voltar ao Exercício (Execução de Séries)", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                        Text("SÉRIES ↩", color = Primary, fontSize = 11.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
            item {
                val eqConfigVer by com.example.utils.WeightCalculator.equipmentConfigVersion.collectAsState()
                val refW = currentEx.sets.maxOfOrNull { it.weight } ?: 20f
                val savedCat = remember(currentEx.name, currentEx.equipment, eqConfigVer) {
                    com.example.utils.WeightCalculator.getSavedEquipmentCategory(context, currentEx.name, currentEx.equipment)
                }
                val savedStep = remember(currentEx.name, currentEx.equipment, refW, eqConfigVer) {
                    com.example.utils.WeightCalculator.getSavedWeightStep(context, currentEx.name, currentEx.equipment, refW)
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Surface),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        val completedSets = currentEx.sets.filter { it.completed }
                        val volume = completedSets.sumOf { (it.reps * it.weight).toDouble() }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Volume da Sessão Ativa", color = Primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Surface(
                                onClick = {
                                    showWeightsDialog = true
                                    com.example.utils.AudioSynth.playTickSound()
                                },
                                shape = RoundedCornerShape(10.dp),
                                color = Primary.copy(alpha = 0.16f),
                                border = BorderStroke(1.dp, Primary.copy(alpha = 0.5f))
                            ) {
                                Text(
                                    text = "$savedCat • ${com.example.utils.WeightCalculator.formatStepPillText(savedStep)}",
                                    color = Primary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        
                        if (completedSets.isEmpty()) {
                            Text("Volume nesta Sessão: 0 kg", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Black)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Marque séries como concluídas na aba SÉRIES para computar o volume desta sessão.", color = OnSurfaceDim, fontSize = 11.sp)
                        } else {
                            Text("Volume Acumulado nesta Sessão: ${volume.toInt()} kg", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Black)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Média de Repetições: ${String.format(Locale.getDefault(), "%.1f", completedSets.map { it.reps }.average())} reps", color = OnSurfaceDim, fontSize = 12.sp)
                        }
                    }
                }
            }

            // Week-by-week progress history (at most last 3 weeks performed)
            item {
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Surface),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("Progresso Semanal (Histórico)", color = Primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        val weeklyProgress = viewModel.getWeeklyProgressForExercise(currentEx.name)
                        if (weeklyProgress.isEmpty()) {
                            Text(
                                text = "Nenhum histórico anterior para este exercício ainda. Complete e salve esta sessão para iniciar o registro semanal!",
                                color = OnSurfaceDim,
                                fontSize = 12.sp
                            )
                        } else {
                            val maxVolume = weeklyProgress.map { it.totalVolume }.maxOrNull() ?: 1f
                            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                weeklyProgress.forEach { progress ->
                                    val fraction = if (maxVolume > 0f) progress.totalVolume / maxVolume else 0f
                                    val animatedFraction by animateFloatAsState(
                                        targetValue = fraction,
                                        animationSpec = tween(600, easing = EaseInOutQuad),
                                        label = "bar_growth"
                                    )
                                    Column {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Default.Event,
                                                    contentDescription = "Semana",
                                                    tint = Primary,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Text(progress.weekLabel, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            }
                                            Text("${progress.totalVolume.toInt()} kg", color = Primary, fontSize = 13.sp, fontWeight = FontWeight.Black)
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        
                                        // Gorgeous progress bar representing the volume proportion
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(8.dp)
                                                .background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(4.dp))
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth(animatedFraction.coerceIn(0.01f, 1f))
                                                    .fillMaxHeight()
                                                    .background(
                                                        brush = Brush.horizontalGradient(
                                                            colors = listOf(Primary.copy(alpha = 0.6f), Primary)
                                                        ),
                                                        shape = RoundedCornerShape(4.dp)
                                                    )
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("Carga Máxima: ${progress.maxWeight.toInt()} kg", color = OnSurfaceDim, fontSize = 10.sp)
                                            Text("${progress.totalSets} séries concluídas", color = OnSurfaceDim, fontSize = 10.sp)
                                        }
                                    }
                                }
                                
                                // Progression insight coach tip
                                if (weeklyProgress.size >= 2) {
                                    val last = weeklyProgress.last()
                                    val prev = weeklyProgress[weeklyProgress.size - 2]
                                    val percentChange = if (prev.totalVolume > 0f) {
                                        ((last.totalVolume - prev.totalVolume) / prev.totalVolume) * 100
                                    } else 0f
                                    
                                    Spacer(modifier = Modifier.height(6.dp))
                                    HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
                                    Spacer(modifier = Modifier.height(6.dp))
                                    
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (percentChange >= 0f) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown,
                                            contentDescription = "Tendência",
                                            tint = if (percentChange >= 0f) Success else Error,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = if (percentChange >= 0f) {
                                                "Evolução: +${String.format(Locale.getDefault(), "%.1f", percentChange)}% de volume em relação à semana anterior!"
                                            } else {
                                                "Volume reduziu ${String.format(Locale.getDefault(), "%.1f", percentChange)}% em relação à semana passada. Mantenha a constância!"
                                            },
                                            color = if (percentChange >= 0f) Success else Color.LightGray,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium
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



    val hasPendingSets = currentEx.sets.any { !it.completed }

    Column(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        SurfaceDim.copy(alpha = 0.95f),
                        SurfaceDim
                    )
                )
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (activeTab == 0) {
            // (b) Botão "REGISTRAR SÉRIE" ou FlowRestOverlay
            if (restActive) {
                FlowRestOverlay(
                    restRemaining = restRemaining,
                    restTotal = restTotal,
                    nextExercise = displayExercises.getOrNull(currentIndex + 1),
                    onModifyRest = { delta -> viewModel.modifyRestTimer(delta) },
                    onSkipRest = { viewModel.skipRestTimer() },
                    currentBpm = currentBpm
                )
            } else if (hasPendingSets) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Botão REGISTRAR SÉRIE em Azul/Ciano Oficial (#29B6F6 -> #00B0FF), borda iluminada e debounce de 2.5s
                    Button(
                        onClick = {
                            if (!isSetCooldownActive) {
                                val nextUncompletedSetIndex = currentEx.sets.indexOfFirst { !it.completed }
                                if (nextUncompletedSetIndex != -1) {
                                    viewModel.toggleSetCompletion(currentIndex, nextUncompletedSetIndex)
                                }
                            }
                        },
                        enabled = !isSetCooldownActive,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent
                        ),
                        contentPadding = PaddingValues(0.dp),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .alpha(if (isSetCooldownActive) 0.55f else 1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = if (isSetCooldownActive) {
                                        listOf(Color(0xFF1E3A5F), Color(0xFF13263E))
                                    } else {
                                        listOf(Color(0xFF29B6F6), Color(0xFF00B0FF))
                                    }
                                )
                            )
                            .border(
                                BorderStroke(
                                    1.dp,
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color(0xFF80D8FF).copy(alpha = if (isSetCooldownActive) 0.25f else 0.65f),
                                            Color(0xFF0288D1).copy(alpha = 0.2f)
                                        )
                                    )
                                ),
                                RoundedCornerShape(12.dp)
                            )
                            .testTag("register_set_button")
                    ) {
                        Text(
                            text = if (isSetCooldownActive) "SÉRIE REGISTRADA ✓" else "REGISTRAR SÉRIE",
                            color = if (isSetCooldownActive) Color.White else Color.Black,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp
                        )
                    }

                    val allCompleted = currentEx.sets.isNotEmpty() && currentEx.sets.all { it.completed }
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF1E2430))
                            .border(specularRimBorder(1.dp, 0.28f, 0.05f), RoundedCornerShape(12.dp))
                            .clickable {
                                currentEx.sets.forEachIndexed { sIdx, s ->
                                    if (!s.completed) {
                                        viewModel.toggleSetCompletion(currentIndex, sIdx)
                                    }
                                }
                                com.example.utils.AudioSynth.playTickSound()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.DoneAll,
                            contentDescription = "Concluir Tudo",
                            tint = if (allCompleted) Primary else Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        // (a) Botão "SESSÃO DE HOJE" e "PRÓXIMO EXERCÍCIO" / "CONCLUIR TREINO"
        if (activeTab == 0 && !hasPendingSets) {
            val isLast = currentIndex == activeExs.lastIndex
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = {
                        viewModel.isPreSessionActive.value = true
                        com.example.utils.AudioSynth.playTickSound()
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    border = BorderStroke(1.dp, Primary.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("return_to_session_button")
                ) {
                    Text(
                        text = "SESSÃO DE HOJE",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Button(
                    onClick = {
                        if (isLast) {
                            viewModel.tryFinishActiveWorkout(onShowPartialConfirmation = { showFinishConfirmation = true })
                        } else {
                            viewModel.currentExerciseIndex.value += 1
                            com.example.utils.AudioSynth.playTickSound()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = if (isLast) Success else Primary),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("next_exercise_button")
                ) {
                    Text(
                        text = if (isLast) "CONCLUIR TREINO" else "PRÓXIMO EXERCÍCIO",
                        color = Color.Black,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }

        if (activeTab == 2) {
            Button(
                onClick = {
                    activeTab = 0
                    com.example.utils.AudioSynth.playTickSound()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("return_to_exercise_from_analysis_bottom_button")
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("VOLTAR AO EXERCÍCIO (SÉRIES)", color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Black)
            }
        }
    }

    if (showFinishConfirmation) {
        val completedSetsCount = activeExs.flatMap { it.sets }.count { it.completed }
        val completedExsCount = activeExs.count { ex -> ex.sets.any { s -> s.completed } }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showFinishConfirmation = false },
            title = {
                Text(
                    text = if (completedSetsCount > 0) "Encerrar Treino?" else "Concluir ou Encerrar Treino?",
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 18.sp
                )
            },
            text = {
                Text(
                    text = if (completedSetsCount > 0) {
                        "Você concluiu $completedExsCount de ${activeExs.size} exercícios ($completedSetsCount séries). Se encerrar agora, o treino será encerrado antecipadamente e seu progresso será salvo no Diário."
                    } else {
                        "Você ainda não marcou séries individuais. Deseja concluir e salvar o treino completo no Diário ou abandonar sem salvar?"
                    },
                    color = OnSurfaceDim,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (completedSetsCount == 0) {
                        TextButton(
                            onClick = {
                                showFinishConfirmation = false
                                viewModel.cancelActiveWorkout()
                            }
                        ) {
                            Text("Abandonar", color = Error, fontWeight = FontWeight.Bold)
                        }
                    }
                    Button(
                        onClick = {
                            showFinishConfirmation = false
                            viewModel.finishActiveWorkout(isEarly = completedSetsCount > 0, autoCompleteIfEmpty = true)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = if (completedSetsCount > 0) Error else Primary)
                    ) {
                        Text(
                            text = if (completedSetsCount > 0) "Encerrar e Salvar" else "Concluir e Salvar",
                            color = if (completedSetsCount > 0) Color.White else Color.Black,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showFinishConfirmation = false }
                ) {
                    Text("Continuar", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = Surface
        )
    }

    // Fast Set Warning floating alert (Snackbar-like notification) - Auto-dismiss after 3.5s
    AnimatedVisibility(
        visible = fastSetWarning != null,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        modifier = Modifier.align(Alignment.TopCenter).padding(top = if (activePrCelebration != null) 96.dp else 16.dp).padding(horizontal = 16.dp).zIndex(98f)
    ) {
        fastSetWarning?.let { message ->
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2C2411)), // Dark Amber
                border = BorderStroke(1.5.dp, Color(0xFFFFB300)), // Amber Warning
                shape = RoundedCornerShape(14.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(12.dp, RoundedCornerShape(14.dp))
                    .testTag("fast_set_warning_banner")
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color(0xFFFFB300).copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Alerta",
                            tint = Color(0xFFFFB300),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Text(
                        text = message,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { viewModel.fastSetWarningMessage.value = null },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Fechar",
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }

    // PR Celebration floating notification (top drawer) - Auto-dismiss after 3s
    AnimatedVisibility(
        visible = activePrCelebration != null,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp).padding(horizontal = 16.dp).zIndex(99f)
    ) {
        activePrCelebration?.let { celebration ->
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1B4B)), // Dark Indigo/Midnight Purple
                border = BorderStroke(1.5.dp, Color(0xFFFFD700)), // Elegant Gold Border
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                modifier = Modifier.fillMaxWidth().shadow(12.dp, RoundedCornerShape(16.dp))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .background(Color(0xFFFFD700).copy(alpha = 0.15f), CircleShape)
                            .border(1.dp, Color(0xFFFFD700), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.EmojiEvents,
                            contentDescription = "Recorde Pessoal",
                            tint = Color(0xFFFFD700),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = celebration.exerciseName,
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = celebration.value.uppercase(Locale.getDefault()),
                            color = Color(0xFFFFD700),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black
                        )
                        Spacer(modifier = Modifier.height(1.dp))
                        Text(
                            text = if (celebration.isWeightPR) "CARGA MÁXIMA" else "REPETIÇÕES MÁXIMAS",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }
        }
    }

    // 1. Semi-transparent backdrop (Fades in/out)
    AnimatedVisibility(
        visible = activeRpePrompt != null,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.fillMaxSize().zIndex(99f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null
                ) { /* Block touches to underlying UI */ }
        )
    }

    // 2. RPE Assessment sliding overlay (bottom drawer)
    AnimatedVisibility(
        visible = activeRpePrompt != null,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = Modifier.align(Alignment.BottomCenter).zIndex(100f)
    ) {
        activeRpePrompt?.let { rpePrompt ->
            val formattedWeight = if (rpePrompt.weight % 1 == 0f) "${rpePrompt.weight.toInt()}" else "${rpePrompt.weight}"
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding(), // Ensures it's never cut off by OS bottom navigation
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F0F12)), // Pitch black
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 36.dp, start = 20.dp, end = 20.dp, top = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Drag handle / Pill
                    Box(
                        modifier = Modifier
                            .width(40.dp)
                            .height(4.dp)
                            .background(Color.White.copy(alpha = 0.2f), shape = RoundedCornerShape(2.dp))
                    )
                    
                    Spacer(modifier = Modifier.height(20.dp))
                    
                    // Header Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "PERCEPÇÃO DE ESFORÇO (RPE)",
                            color = Color(0xFF00E5FF),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            letterSpacing = 1.sp,
                            maxLines = 1,
                            softWrap = false
                        )

                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Info",
                            tint = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Exercise Name
                    Text(
                        text = rpePrompt.exerciseName,
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.align(Alignment.Start)
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Description text
                    Text(
                        text = "Como você sentiu a série ${rpePrompt.setIndex + 1} (${rpePrompt.reps} reps x ${formattedWeight}kg)? Isso calibra sua próxima progressão:",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 18.sp,
                        modifier = Modifier.align(Alignment.Start)
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Intensity explanatory cards
                    data class RpeOptionSpec(
                        val value: String,
                        val title: String,
                        val explanation: String,
                        val accentColor: Color
                    )

                    val rpeOptions = listOf(
                        RpeOptionSpec(
                            value = "Fácil",
                            title = "Fácil",
                            explanation = "Sobraram +4 reps • Aumenta carga e repetições",
                            accentColor = Color(0xFF00E676)
                        ),
                        RpeOptionSpec(
                            value = "Moderado",
                            title = "Moderado",
                            explanation = "Sobraram 2 a 3 reps • Aumenta +2 a 3 repetições ou +carga leve",
                            accentColor = Color(0xFF00E5FF)
                        ),
                        RpeOptionSpec(
                            value = "Difícil",
                            title = "Difícil (Ideal)",
                            explanation = "Sobrou 1 rep • Mantém carga calibrada",
                            accentColor = Color(0xFF00B0FF)
                        ),
                        RpeOptionSpec(
                            value = "Máximo",
                            title = "Máximo / Falha",
                            explanation = "0 reps na reserva • Mantém ou reduz carga por fadiga",
                            accentColor = Color(0xFFFF5252)
                        )
                    )

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rpeOptions.forEach { option ->
                            Surface(
                                color = Color(0xFF151922),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, option.accentColor.copy(alpha = 0.45f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.recordRpeForSet(
                                            rpePrompt.exerciseIndex,
                                            rpePrompt.setIndex,
                                            option.value
                                        )
                                        com.example.utils.AudioSynth.playTickSound()
                                    }
                                    .testTag("rpe_option_${option.value.lowercase()}")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(IntrinsicSize.Min)
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Text(
                                            text = option.title,
                                            color = option.accentColor,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Black,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                        Text(
                                            text = option.explanation,
                                            color = Color.White.copy(alpha = 0.88f),
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = option.title,
                                        tint = option.accentColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showActiveExerciseNoteDialog) {
        var noteInput by remember(currentEx.name) { mutableStateOf(currentEx.notes) }
        AlertDialog(
            onDismissRequest = { showActiveExerciseNoteDialog = false },
            title = {
                Text(
                    text = "Anotação / Feedback do Aparelho",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "${currentEx.name} [${currentEx.equipment}]",
                        color = Primary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    OutlinedTextField(
                        value = noteInput,
                        onValueChange = { noteInput = it },
                        placeholder = { Text("Ex: Banco na altura 4, carga suave, ajustar pegada...", color = OnSurfaceDim, fontSize = 12.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Primary,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val realIdx = activeExs.indexOfFirst { it.name.trim().equals(currentEx.name.trim(), ignoreCase = true) }
                            .takeIf { it != -1 } ?: currentIndex
                        viewModel.updateExerciseNotes(realIdx, noteInput)
                        showActiveExerciseNoteDialog = false
                        com.example.utils.AudioSynth.playTickSound()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Primary)
                ) {
                    Text("SALVAR", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showActiveExerciseNoteDialog = false }) {
                    Text("CANCELAR", color = Color.White)
                }
            },
            containerColor = Surface
        )
    }

    if (showGymSavePrompt) {
        val activeGym = activeGymProfile
        androidx.compose.material3.AlertDialog(
            onDismissRequest = {
                viewModel.showGymSavePrompt.value = false
                viewModel.finalizeActiveWorkoutAndNavigateToCompleted()
            },
            title = {
                Text(
                    text = "Treino concluído!",
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 18.sp
                )
            },
            text = {
                Text(
                    text = if (activeGym != null) {
                        "Deseja salvar os aparelhos utilizados hoje no perfil da academia '${activeGym.name}'?"
                    } else {
                        "Deseja salvar os aparelhos utilizados hoje em um perfil de academia?"
                    },
                    color = OnSurfaceDim,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.showGymSavePrompt.value = false
                        if (activeGym != null) {
                            val mergedExercises = (activeGym.availableExercisesIds + exercisesToSaveInGym).distinct()
                            val updatedGym = activeGym.copy(availableExercisesIds = mergedExercises)
                            viewModel.updateGymProfile(updatedGym)
                            viewModel.finalizeActiveWorkoutAndNavigateToCompleted()
                        } else {
                            viewModel.showGymRegistrationFromWorkout.value = true
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Primary),
                    modifier = Modifier.testTag("save_gym_profile_yes")
                ) {
                    Text(
                        text = if (activeGym != null) "Salvar Aparelhos" else "Sim, Salvar Perfil",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        viewModel.showGymSavePrompt.value = false
                        viewModel.finalizeActiveWorkoutAndNavigateToCompleted()
                    },
                    modifier = Modifier.testTag("save_gym_profile_no")
                ) {
                    Text("Não", color = Color.White)
                }
            },
            containerColor = Surface
        )
    }

    if (showPermissionRationaleFromWorkout) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = {
                showPermissionRationaleFromWorkout = false
                detectLocationAutoFromWorkout = false
            },
            title = {
                Text(
                    text = "Permissão de Localização",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Text(
                    text = "O MaxPerformance precisa da sua localização aproximada para sugerir o nome do bairro e detectar a academia automaticamente.",
                    color = OnSurfaceDim,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPermissionRationaleFromWorkout = false
                        locationPermissionLauncherFromWorkout.launch(android.Manifest.permission.ACCESS_COARSE_LOCATION)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Primary)
                ) {
                    Text("Conceder", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showPermissionRationaleFromWorkout = false
                        detectLocationAutoFromWorkout = false
                    }
                ) {
                    Text("Cancelar", color = Color.White)
                }
            },
            containerColor = Surface
        )
    }

    if (showGymRegistrationFromWorkout) {
        var isSavingFromWorkout by remember { mutableStateOf(false) }
        var addGymWarning by remember { mutableStateOf<String?>(null) }

        val triggerLocationDetectionFromWorkout = {
            viewModel.getCurrentLocation(context) { lat, lng ->
                if (lat != null && lng != null) {
                    newGymLatStrFromWorkout = lat.toString()
                    newGymLngStrFromWorkout = lng.toString()
                    viewModel.getNeighborhoodFromLocation(context, lat, lng) { neighborhood ->
                        if (!neighborhood.isNullOrBlank()) {
                            val currentName = newGymNameFromWorkout.trim()
                            val baseName = if (currentName.isEmpty()) "Academia" else currentName
                            if (!baseName.contains(neighborhood)) {
                                newGymNameFromWorkout = "$baseName - $neighborhood"
                            }
                        }
                    }
                }
            }
        }

        val toggleDetectionFromWorkout = {
            val hasLocationPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED

            if (!detectLocationAutoFromWorkout) {
                if (hasLocationPermission) {
                    detectLocationAutoFromWorkout = true
                    triggerLocationDetectionFromWorkout()
                } else {
                    onPermissionGrantedFromWorkoutCallback = {
                        detectLocationAutoFromWorkout = true
                        triggerLocationDetectionFromWorkout()
                    }
                    onPermissionDeniedFromWorkoutCallback = {
                        detectLocationAutoFromWorkout = false
                    }
                    showPermissionRationaleFromWorkout = true
                }
            } else {
                detectLocationAutoFromWorkout = false
            }
        }

        AlertDialog(
            onDismissRequest = {
                viewModel.showGymRegistrationFromWorkout.value = false
                viewModel.finalizeActiveWorkoutAndNavigateToCompleted()
            },
            title = {
                Text(
                    text = "Cadastrar Academia",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = newGymNameFromWorkout,
                        onValueChange = { newGymNameFromWorkout = it },
                        label = { Text("Nome da Academia (ex: SmartFit)") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Primary,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("gym_name_input")
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { toggleDetectionFromWorkout() }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Checkbox(
                            checked = detectLocationAutoFromWorkout,
                            onCheckedChange = { checked -> toggleDetectionFromWorkout() },
                            colors = CheckboxDefaults.colors(checkedColor = Primary, uncheckedColor = OnSurfaceDim)
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.MyLocation,
                                contentDescription = "GPS",
                                tint = if (detectLocationAutoFromWorkout) Primary else OnSurfaceDim,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Detectar localização automaticamente",
                                color = Color.White,
                                fontSize = 13.sp
                            )
                        }
                    }

                    if (addGymWarning != null) {
                        Text(addGymWarning!!, color = Error, fontSize = 11.sp)
                    }

                    Text(
                        text = "Aparelhos / Exercícios Utilizados:",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Info",
                                tint = Primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Nenhum aparelho cadastrado. O app aprenderá com os treinos que você fizer aqui, ou você pode marcar manualmente abaixo.",
                                color = OnSurfaceDim,
                                fontSize = 11.sp,
                                lineHeight = 14.sp
                            )
                        }
                    }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.03f)),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                    ) {
                        LazyColumn(
                            modifier = Modifier.padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(combinedExercises) { exercise ->
                                val isChecked = selectedWorkoutGymExercises.contains(exercise.id)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedWorkoutGymExercises = if (isChecked) {
                                                selectedWorkoutGymExercises - exercise.id
                                            } else {
                                                selectedWorkoutGymExercises + exercise.id
                                            }
                                        }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Checkbox(
                                        checked = isChecked,
                                        onCheckedChange = { checked ->
                                            selectedWorkoutGymExercises = if (checked == true) {
                                                selectedWorkoutGymExercises + exercise.id
                                            } else {
                                                selectedWorkoutGymExercises - exercise.id
                                            }
                                        },
                                        colors = CheckboxDefaults.colors(checkedColor = Primary, uncheckedColor = OnSurfaceDim)
                                    )
                                    Column {
                                        Text(exercise.name, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        Text(exercise.category, color = OnSurfaceDim, fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newGymNameFromWorkout.isBlank()) {
                            addGymWarning = "O nome da academia é obrigatório."
                            return@Button
                        }

                        val lat = if (detectLocationAutoFromWorkout) newGymLatStrFromWorkout.toDoubleOrNull() else null
                        val lng = if (detectLocationAutoFromWorkout) newGymLngStrFromWorkout.toDoubleOrNull() else null

                        viewModel.saveGymProfileFromSession(
                            name = newGymNameFromWorkout,
                            lat = lat,
                            lng = lng,
                            usedExercisesIds = selectedWorkoutGymExercises.toList()
                        )
                        viewModel.showGymRegistrationFromWorkout.value = false
                        viewModel.finalizeActiveWorkoutAndNavigateToCompleted()
                    },
                    enabled = !isSavingFromWorkout && newGymNameFromWorkout.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary),
                    modifier = Modifier.testTag("save_gym_profile_submit")
                ) {
                    Text(
                        text = if (isSavingFromWorkout) "SALVANDO..." else "CADASTRAR",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        viewModel.showGymRegistrationFromWorkout.value = false
                        viewModel.finalizeActiveWorkoutAndNavigateToCompleted()
                    }
                ) {
                    Text("CANCELAR", color = Color.White)
                }
            },
            containerColor = Surface
        )
    }

    // BottomSheet BLE já renderizado no topo do ActiveWorkoutScreen para funcionar tanto no PreSession quanto no Player Ativo
}
}

// ==================== SCREEN 4: EXERCISE LIBRARY & AI COACH ====================
@Composable
fun LibraryScreen(viewModel: MainViewModel) {
    val windowSizeInfo = LocalWindowSizeInfo.current
    val allExercises = TemplatesAndExercises.ALL_EXERCISES
    val favIds by viewModel.favoriteExercisesIds.collectAsState()
    
    // AI states
    val aiResponse by viewModel.aiCoachResponse.collectAsState()
    val isAiLoading by viewModel.isAiLoading.collectAsState()
    val queriesToday by viewModel.aiQueriesToday.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedMuscleGroup by remember { mutableStateOf<String?>(null) }
    var aiCoachPrompt by remember { mutableStateOf("") }
    var showAiCoachModal by remember { mutableStateOf(false) }
    var showCustomExerciseModal by remember { mutableStateOf(false) }
    var showProtocolsView by remember { mutableStateOf(false) }

    if (showProtocolsView) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Transparent)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = { showProtocolsView = false },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar", tint = Color.White)
                }
                Text("Voltar ao Catálogo de Exercícios", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
            Box(modifier = Modifier.weight(1f)) {
                WorkoutProtocolsListScreen(viewModel = viewModel)
            }
        }
        return
    }

    // Custom Exercise fields
    val libContext = LocalContext.current
    var customExName by remember { mutableStateOf("") }
    var customExCategory by remember { mutableStateOf("Peito") }
    var customExDesc by remember { mutableStateOf("") }
    var customExDiff by remember { mutableStateOf("Iniciante") }
    var customExEquipCat by remember { mutableStateOf(com.example.utils.WeightCalculator.ALL_EQUIPMENT_CATEGORIES.first()) }
    var customExStepKg by remember { mutableStateOf(2.0f) }

    ResponsiveDialog(
        visible = showAiCoachModal,
        onDismissRequest = { showAiCoachModal = false }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Coach IA - MAX PERFORMANCE", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                IconButton(onClick = { showAiCoachModal = false }) {
                    Icon(Icons.Default.Close, null, tint = Color.White)
                }
            }
            
            Text("Pergunte algo sobre treino ou peça sugestão de exercícios. Limite diário de 2 consultas.", color = OnSurfaceDim, fontSize = 11.sp)
            Text("Consultas feitas hoje: $queriesToday/2", color = if (queriesToday >= 2) Error else Success, fontSize = 11.sp, fontWeight = FontWeight.Bold)

            OutlinedTextField(
                value = aiCoachPrompt,
                onValueChange = { aiCoachPrompt = it },
                placeholder = { Text("Ex: Monte um bi-set de braço com halteres...") },
                modifier = Modifier.fillMaxWidth().testTag("ai_coach_prompt_input"),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary, focusedTextColor = Color.White, unfocusedTextColor = Color.White)
            )

            Button(
                onClick = { 
                    if (aiCoachPrompt.isNotBlank()) {
                        viewModel.queryAiCoach(aiCoachPrompt)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                enabled = !isAiLoading && aiCoachPrompt.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isAiLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White)
                } else {
                    Text("Consultar Coach IA", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }

            aiResponse?.let {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Primary.copy(alpha = 0.08f))
                            .border(1.dp, Primary.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        Text(it, color = Color.White, fontSize = 13.sp)
                    }
                    
                    Button(
                        onClick = {
                            viewModel.importAiWorkout(it)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Primary),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Download, null, tint = Color.Black, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("EXPORTAR COMO MEU TREINO", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            }
        }
    }

    ResponsiveDialog(
        visible = showCustomExerciseModal,
        onDismissRequest = { showCustomExerciseModal = false }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Adicionar Exercício", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                IconButton(onClick = { showCustomExerciseModal = false }) {
                    Icon(Icons.Default.Close, null, tint = Color.White)
                }
            }

            OutlinedTextField(
                value = customExName,
                onValueChange = { customExName = it },
                label = { Text("Nome do Exercício") },
                modifier = Modifier.fillMaxWidth().testTag("custom_exercise_name_input"),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary, focusedTextColor = Color.White, unfocusedTextColor = Color.White)
            )

            OutlinedTextField(
                value = customExDesc,
                onValueChange = { customExDesc = it },
                label = { Text("Descrição / Instruções") },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary, focusedTextColor = Color.White, unfocusedTextColor = Color.White)
            )

            Text("Grupo Muscular Principal", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            val musclesList = listOf("Peito", "Costas", "Pernas", "Ombros", "Bíceps", "Tríceps", "Trapézio", "Abdominais")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(musclesList) { muscle ->
                    FilterChip(
                        selected = customExCategory == muscle,
                        onClick = { customExCategory = muscle },
                        label = { Text(muscle) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Primary, selectedLabelColor = Color.White)
                    )
                }
            }

            Text("Categoria do Equipamento (Estilo Gravl)", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(com.example.utils.WeightCalculator.ALL_EQUIPMENT_CATEGORIES) { eqCat ->
                    FilterChip(
                        selected = customExEquipCat == eqCat,
                        onClick = {
                            customExEquipCat = eqCat
                            customExStepKg = com.example.utils.WeightCalculator.defaultStepForCategory(eqCat, 20f)
                        },
                        label = { Text(eqCat) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Primary, selectedLabelColor = Color.Black)
                    )
                }
            }

            Text("Incremento Mínimo da Máquina (Δ kg)", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(com.example.utils.WeightCalculator.GRAVL_STEP_PRESETS) { stepPreset ->
                    FilterChip(
                        selected = kotlin.math.abs(customExStepKg - stepPreset) < 0.05f,
                        onClick = { customExStepKg = stepPreset },
                        label = { Text("+${com.example.utils.WeightCalculator.formatStepNumber(stepPreset)} kg") },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Primary, selectedLabelColor = Color.Black)
                    )
                }
            }

            Text("Dificuldade", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            val diffs = listOf("Iniciante", "Intermediário", "Avançado")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                diffs.forEach { d ->
                    FilterChip(
                        selected = customExDiff == d,
                        onClick = { customExDiff = d },
                        label = { Text(d) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Primary, selectedLabelColor = Color.White)
                    )
                }
            }

            Button(
                onClick = {
                    if (customExName.isNotBlank()) {
                        com.example.utils.WeightCalculator.saveExerciseEquipmentConfig(
                            context = libContext,
                            exerciseName = customExName,
                            category = customExEquipCat,
                            stepKg = customExStepKg,
                            isCustomStep = true
                        )
                        viewModel.addNewCustomExercise(customExName, customExCategory, customExDesc, customExDiff)
                        showCustomExerciseModal = false
                        customExName = ""
                        customExDesc = ""
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Cadastrar Exercício", color = Color.White, fontWeight = FontWeight.Black)
            }
        }
    }

    val muscleGroupImages = mapOf(
        "Peito" to "https://images.unsplash.com/photo-1571019614242-c5c5dee9f50b?q=80&w=200&auto=format&fit=crop",
        "Costas" to "https://images.unsplash.com/photo-1598971639058-fab3c3109a00?q=80&w=200&auto=format&fit=crop",
        "Pernas" to "https://images.unsplash.com/photo-1574680096145-d05b474e2155?q=80&w=200&auto=format&fit=crop",
        "Ombros" to "https://images.unsplash.com/photo-1541534741688-6078c6bfb5c5?q=80&w=200&auto=format&fit=crop",
        "Bíceps" to "https://images.unsplash.com/photo-1581009146145-b5ef050c2e1e?q=80&w=200&auto=format&fit=crop",
        "Tríceps" to "https://images.unsplash.com/photo-1534438327276-14e5300c3a48?q=80&w=200&auto=format&fit=crop",
        "Trapézio" to "https://images.unsplash.com/photo-1517838277536-f5f99be501cd?q=80&w=200&auto=format&fit=crop",
        "Abdominais" to "https://images.unsplash.com/photo-1571019613454-1cb2f99b2d8b?q=80&w=200&auto=format&fit=crop"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Catálogo de Exercícios", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
            
            Row {
                IconButton(onClick = { showCustomExerciseModal = true }) {
                    Icon(Icons.Default.Add, "Novo Exercício", tint = Color.White)
                }
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    showProtocolsView = true
                    viewModel.workoutsSubTab.value = 2
                    com.example.utils.AudioSynth.playTickSound()
                },
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Primary.copy(alpha = 0.15f)),
            border = BorderStroke(1.dp, Primary.copy(alpha = 0.35f))
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.FitnessCenter, null, tint = Primary, modifier = Modifier.size(22.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Protocolos Estruturados (Full Body & PPL)",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Acesse 5 fichas validadas de 3x a 5x por semana",
                        color = Color(0xFFB0B0B8),
                        fontSize = 11.sp,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(Icons.Default.ChevronRight, null, tint = Primary, modifier = Modifier.size(18.dp))
            }
        }

        // Search + Filter rows
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { 
                searchQuery = it 
                if (it.isNotEmpty()) {
                    selectedMuscleGroup = null
                }
            },
            placeholder = { 
                if (selectedMuscleGroup != null) {
                    Text("Buscar em $selectedMuscleGroup...")
                } else {
                    Text("Buscar entre 170+ exercícios...")
                }
            },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = OnSurfaceDim) },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary, focusedTextColor = Color.White, unfocusedTextColor = Color.White)
        )

        // If search and muscle are empty, show visual grid
        if (searchQuery.isEmpty() && selectedMuscleGroup == null) {
            Column(modifier = Modifier.fillMaxWidth().weight(1f)) {
                Text(
                    text = "Navegar por Grupo Muscular:",
                    color = OnSurfaceDim,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                val muscles = listOf("Peito", "Costas", "Pernas", "Ombros", "Bíceps", "Tríceps", "Trapézio", "Abdominais")
                ResponsiveGrid(
                    compactCols = 2,
                    mediumCols = 3,
                    expandedCols = 4,
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(muscles.size) { index ->
                        val muscle = muscles[index]
                        val imgUrl = muscleGroupImages[muscle] ?: ""
                        val count = allExercises.filter { it.category.equals(muscle, ignoreCase = true) }.size
                        Card(
                            onClick = { selectedMuscleGroup = muscle },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(110.dp),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                AsyncImage(
                                    model = imgUrl,
                                    contentDescription = null,
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            androidx.compose.ui.graphics.Brush.verticalGradient(
                                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                                            )
                                        )
                                )
                                Column(
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(12.dp)
                                ) {
                                    Text(
                                        text = muscle,
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                    Text(
                                        text = "$count exercícios",
                                        color = Primary,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // List of exercises
            val filteredList = allExercises.filter { ex ->
                val matchesSearch = searchQuery.isEmpty() ||
                        com.example.utils.FuzzySearchUtils.matches(searchQuery, ex.name) ||
                        com.example.utils.FuzzySearchUtils.matches(searchQuery, ex.category) ||
                        com.example.utils.FuzzySearchUtils.matches(searchQuery, ex.equipment ?: "")
                val matchesMuscle = selectedMuscleGroup == null || ex.category.equals(selectedMuscleGroup, ignoreCase = true)
                matchesSearch && matchesMuscle
            }

            if (windowSizeInfo.widthSizeClass == WindowSize.COMPACT) {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    // Header for sub-list if muscle is selected
                    if (selectedMuscleGroup != null) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { selectedMuscleGroup = null; searchQuery = "" },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar", tint = Primary)
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "$selectedMuscleGroup",
                                        color = Primary,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                                TextButton(onClick = { selectedMuscleGroup = null; searchQuery = "" }) {
                                    Text("Ver Todos Grupos", color = OnSurfaceDim, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    if (filteredList.isEmpty()) {
                        item {
                            Text("Nenhum exercício correspondente encontrado.", color = OnSurfaceDim, modifier = Modifier.padding(24.dp))
                        }
                    } else {
                        items(filteredList) { ex ->
                            val isFav = favIds.contains(ex.id)
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Surface),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    com.example.ui.components.ExerciseIllustrationThumbnail(
                                        exerciseName = ex.name,
                                        muscleGroup = ex.category,
                                        cornerRadius = 12.dp,
                                        equipment = ex.equipment ?: "",
                                        notes = ex.description,
                                        showZoomBadge = true,
                                        enableDetailModalOnTap = true,
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(ex.name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text("${ex.category} • Dificuldade: ${ex.difficulty}", color = OnSurfaceDim, fontSize = 11.sp)
                                        if (ex.description.isNotEmpty()) {
                                            Text(ex.description, color = OnSurfaceVariant, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                    
                                    IconButton(onClick = { viewModel.toggleExerciseFavorite(ex.id) }) {
                                        Icon(
                                            imageVector = if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                            contentDescription = "Favorito toggle",
                                            tint = if (isFav) Primary else OnSurfaceDim
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Wide Screen: Grid Layout
                Column(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    if (selectedMuscleGroup != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { selectedMuscleGroup = null; searchQuery = "" },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar", tint = Primary)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "$selectedMuscleGroup",
                                    color = Primary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                            TextButton(onClick = { selectedMuscleGroup = null; searchQuery = "" }) {
                                Text("Ver Todos Grupos", color = OnSurfaceDim, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    if (filteredList.isEmpty()) {
                        Text("Nenhum exercício correspondente encontrado.", color = OnSurfaceDim, modifier = Modifier.padding(24.dp))
                    } else {
                        ResponsiveGrid(
                            compactCols = 2,
                            mediumCols = 2,
                            expandedCols = 3,
                            contentPadding = PaddingValues(bottom = 80.dp)
                        ) {
                            items(filteredList.size) { idx ->
                                val ex = filteredList[idx]
                                val isFav = favIds.contains(ex.id)
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = Surface),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        com.example.ui.components.ExerciseIllustrationThumbnail(
                                            exerciseName = ex.name,
                                            muscleGroup = ex.category,
                                            cornerRadius = 12.dp,
                                            equipment = ex.equipment ?: "",
                                            notes = ex.description,
                                            showZoomBadge = true,
                                            enableDetailModalOnTap = true,
                                            modifier = Modifier.size(48.dp)
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(ex.name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text("${ex.category} • Dificuldade: ${ex.difficulty}", color = OnSurfaceDim, fontSize = 11.sp)
                                            if (ex.description.isNotEmpty()) {
                                                Text(ex.description, color = OnSurfaceVariant, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            }
                                        }
                                        
                                        IconButton(onClick = { viewModel.toggleExerciseFavorite(ex.id) }) {
                                            Icon(
                                                imageVector = if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                                contentDescription = "Favorito toggle",
                                                tint = if (isFav) Primary else OnSurfaceDim
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
}

// ==================== SCREEN 5: CARDIO TRACKING ====================
@Composable
fun CardioScreen(viewModel: MainViewModel) {
    val cardioLogs by viewModel.cardioSessions.collectAsState()

    var cardioSubTab by remember { mutableIntStateOf(0) }
    var selectedType by remember { mutableStateOf("Esteira") }
    var durationStr by remember { mutableStateOf("") }
    var caloriesStr by remember { mutableStateOf("") }
    var showArchivedCardio by remember { mutableStateOf(false) }

    val types = listOf("Esteira", "Bicicleta", "Elíptico", "Corda", "Outros")

    // Smartwatch Scanner States
    var showCardioScanner by remember { mutableStateOf(false) }
    var scannedCardioSession by remember { mutableStateOf<CardioSession?>(null) }
    var isCardioScanned by remember { mutableStateOf(false) }
    var selectedImageUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var scanErrorMessage by remember { mutableStateOf<String?>(null) }
    val isCardioAiLoading by viewModel.isAiLoading.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val galleryLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        selectedImageUri = uri
        scanErrorMessage = null
    }

    var editCardioType by remember { mutableStateOf("Corrida") }
    var editCardioDuration by remember { mutableStateOf("") }
    var editCardioCalories by remember { mutableStateOf("") }
    var editCardioDistance by remember { mutableStateOf("") }
    var editCardioAvgHr by remember { mutableStateOf("") }
    var editCardioSteps by remember { mutableStateOf("") }
    var editCardioPace by remember { mutableStateOf("") }
    var editCardioFatBurn by remember { mutableStateOf("") }
    var editCardioAnaerobic by remember { mutableStateOf("") }
    var editCardioLimit by remember { mutableStateOf("") }
    var editCardioDate by remember { mutableStateOf("") }

    LaunchedEffect(scannedCardioSession) {
        scannedCardioSession?.let {
            editCardioType = it.type
            editCardioDuration = "${it.durationMinutes}"
            editCardioCalories = "${it.calories}"
            editCardioDistance = "${it.distance}"
            editCardioAvgHr = "${it.avgHr}"
            editCardioSteps = "${it.steps}"
            editCardioPace = it.pace
            editCardioFatBurn = "${it.fatBurnZone.toInt()}"
            editCardioAnaerobic = "${it.anaerobicZone.toInt()}"
            editCardioLimit = "${it.limitZone.toInt()}"
            editCardioDate = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(it.date))
        }
    }

    if (showCardioScanner) {
        Dialog(onDismissRequest = { showCardioScanner = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.9f)
                    .padding(8.dp),
                colors = CardDefaults.cardColors(containerColor = Surface),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, Primary.copy(alpha = 0.3f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (!isCardioScanned) "Smartwatch Scan (Haylou)" else "Confirmar Sessão",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = { showCardioScanner = false; isCardioScanned = false; scannedCardioSession = null }) {
                            Icon(Icons.Default.Close, null, tint = Color.White)
                        }
                    }

                    if (!isCardioScanned) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Card(
                                onClick = { galleryLauncher.launch("image/*") },
                                modifier = Modifier
                                    .size(180.dp)
                                    .border(1.dp, if (selectedImageUri != null) Primary else Primary.copy(alpha = 0.4f), RoundedCornerShape(16.dp)),
                                colors = CardDefaults.cardColors(containerColor = SurfaceDim)
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                    if (selectedImageUri != null) {
                                        Box(modifier = Modifier.fillMaxSize()) {
                                            AsyncImage(
                                                model = selectedImageUri,
                                                contentDescription = null,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                            )
                                            IconButton(
                                                onClick = { selectedImageUri = null; scanErrorMessage = null },
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .padding(8.dp)
                                                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                                    .size(32.dp)
                                            ) {
                                                Icon(Icons.Default.Delete, "Remover", tint = Error, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    } else {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.AddPhotoAlternate,
                                                contentDescription = "Selecionar Imagem",
                                                tint = Primary.copy(alpha = 0.6f),
                                                modifier = Modifier.size(48.dp)
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = "Toque para Escolher Print",
                                                color = Color.White,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "(da galeria)",
                                                color = OnSurfaceDim,
                                                fontSize = 10.sp
                                            )
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Selecione o print da tela de resumo do treino do app Haylou Fun / Strava.",
                                color = OnSurfaceDim,
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                            if (selectedImageUri != null) {
                                TextButton(
                                    onClick = { selectedImageUri = null; scanErrorMessage = null },
                                    modifier = Modifier.align(Alignment.CenterHorizontally)
                                ) {
                                    Icon(Icons.Default.Delete, null, tint = Error, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("LIMPAR PRINT SELECIONADO", color = Error, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        if (scanErrorMessage != null) {
                            Text(
                                text = scanErrorMessage ?: "",
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        Button(
                            onClick = {
                                val uri = selectedImageUri
                                if (uri == null) {
                                    scanErrorMessage = "Selecione um print da galeria primeiro!"
                                    return@Button
                                }
                                val base64 = uriToBase64(context, uri)
                                if (base64 == null) {
                                    scanErrorMessage = "Não foi possível converter o print para processamento."
                                    return@Button
                                }
                                scanErrorMessage = null
                                viewModel.scanCardio(base64, { sess ->
                                    scannedCardioSession = sess
                                    isCardioScanned = true
                                }, { err ->
                                    scanErrorMessage = err
                                })
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Primary),
                            enabled = !isCardioAiLoading,
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            if (isCardioAiLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.Black)
                            } else {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.QrCodeScanner, null, tint = Color.Black, modifier = Modifier.size(18.dp))
                                    Text("ANALISAR COM IA", color = Color.Black, fontWeight = FontWeight.Black, fontSize = 13.sp)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedButton(
                            onClick = {
                                scannedCardioSession = CardioSession(
                                    id = "cardio_" + java.util.UUID.randomUUID().toString(),
                                    type = "Corrida",
                                    durationMinutes = 0,
                                    calories = 0,
                                    date = System.currentTimeMillis(),
                                    distance = 0f,
                                    durationSeconds = 0,
                                    avgHr = 0,
                                    steps = 0,
                                    pace = "",
                                    fatBurnZone = 0f,
                                    anaerobicZone = 0f,
                                    limitZone = 0f
                                )
                                isCardioScanned = true
                            },
                            border = BorderStroke(1.dp, Primary),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Text("DIGITAR MANUALMENTE", color = Primary, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                "Métricas extraídas da imagem do relógio. Ajuste se necessário antes de salvar:",
                                color = OnSurfaceDim,
                                fontSize = 11.sp
                            )

                            OutlinedTextField(
                                value = editCardioDate,
                                onValueChange = { editCardioDate = it },
                                label = { Text("Data e Hora da Atividade") },
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary, focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = editCardioType,
                                    onValueChange = { editCardioType = it },
                                    label = { Text("Atividade") },
                                    modifier = Modifier.weight(1f),
                                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary, focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                                )
                                OutlinedTextField(
                                    value = editCardioDuration,
                                    onValueChange = { editCardioDuration = it },
                                    label = { Text("Duração (Min)") },
                                    modifier = Modifier.weight(1f),
                                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary, focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = editCardioDistance,
                                    onValueChange = { editCardioDistance = it },
                                    label = { Text("Distância (km)") },
                                    modifier = Modifier.weight(1f),
                                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary, focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                                )
                                OutlinedTextField(
                                    value = editCardioCalories,
                                    onValueChange = { editCardioCalories = it },
                                    label = { Text("Calorias (kcal)") },
                                    modifier = Modifier.weight(1f),
                                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary, focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = editCardioAvgHr,
                                    onValueChange = { editCardioAvgHr = it },
                                    label = { Text("FC Média (BPM)") },
                                    modifier = Modifier.weight(1f),
                                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary, focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                                )
                                OutlinedTextField(
                                    value = editCardioSteps,
                                    onValueChange = { editCardioSteps = it },
                                    label = { Text("Passos") },
                                    modifier = Modifier.weight(1f),
                                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary, focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = editCardioPace,
                                    onValueChange = { editCardioPace = it },
                                    label = { Text("Pace Médio (min/km)") },
                                    modifier = Modifier.weight(1f),
                                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary, focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                                )
                            }

                            Text("Zonas de Frequência Cardíaca (%)", color = Primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = editCardioFatBurn,
                                    onValueChange = { editCardioFatBurn = it },
                                    label = { Text("Gordura (%)") },
                                    modifier = Modifier.weight(1f),
                                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary, focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                                )
                                OutlinedTextField(
                                    value = editCardioAnaerobic,
                                    onValueChange = { editCardioAnaerobic = it },
                                    label = { Text("Anaeróbico (%)") },
                                    modifier = Modifier.weight(1f),
                                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary, focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                                )
                                OutlinedTextField(
                                    value = editCardioLimit,
                                    onValueChange = { editCardioLimit = it },
                                    label = { Text("Limite (%)") },
                                    modifier = Modifier.weight(1f),
                                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary, focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    isCardioScanned = false
                                    scannedCardioSession = null
                                    selectedImageUri = null
                                },
                                border = BorderStroke(1.dp, Error),
                                modifier = Modifier.weight(1f).height(48.dp)
                            ) {
                                Text("CANCELAR", color = Error, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = {
                                    var actDate = System.currentTimeMillis()
                                    try {
                                        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                                        actDate = sdf.parse(editCardioDate)?.time ?: System.currentTimeMillis()
                                    } catch (e: Exception) {}

                                    val finalSession = CardioSession(
                                        id = scannedCardioSession?.id ?: ("cardio_" + UUID.randomUUID().toString()),
                                        type = editCardioType,
                                        durationMinutes = editCardioDuration.toIntOrNull() ?: 30,
                                        calories = editCardioCalories.toIntOrNull() ?: 250,
                                        date = actDate,
                                        distance = editCardioDistance.toFloatOrNull() ?: 0.0f,
                                        avgHr = editCardioAvgHr.toIntOrNull() ?: 0,
                                        steps = editCardioSteps.toIntOrNull() ?: 0,
                                        pace = editCardioPace,
                                        fatBurnZone = editCardioFatBurn.toFloatOrNull() ?: 0.0f,
                                        anaerobicZone = editCardioAnaerobic.toFloatOrNull() ?: 0.0f,
                                        limitZone = editCardioLimit.toFloatOrNull() ?: 0.0f
                                    )
                                    viewModel.saveCardioSession(finalSession)
                                    showCardioScanner = false
                                    isCardioScanned = false
                                    scannedCardioSession = null
                                    selectedImageUri = null
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                                modifier = Modifier.weight(1f).height(48.dp)
                            ) {
                                Text("SALVAR", color = Color.Black, fontWeight = FontWeight.Black)
                            }
                        }
                    }
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = cardioSubTab,
            containerColor = Surface,
            contentColor = Primary
        ) {
            Tab(
                selected = cardioSubTab == 0,
                onClick = { cardioSubTab = 0 },
                text = { Text("CORRIDA PROGRESSIVA", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
            )
            Tab(
                selected = cardioSubTab == 1,
                onClick = { cardioSubTab = 1 },
                text = { Text("REGISTRO MANUAL", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
            )
        }

        if (cardioSubTab == 0) {
            RunningModuleScreen(viewModel = viewModel)
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Transparent)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 80.dp, top = 8.dp)
            ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Registrar Cardio", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
                Button(
                    onClick = { showCardioScanner = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Primary.copy(alpha = 0.1f)),
                    border = BorderStroke(1.dp, Primary),
                    modifier = Modifier.height(36.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Watch, null, tint = Primary, modifier = Modifier.size(16.dp))
                        Text("ESCANEAR SMARTWATCH", color = Primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Surface),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Selecione a Atividade", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(types) { type ->
                            FilterChip(
                                selected = selectedType == type,
                                onClick = { selectedType = type },
                                label = { Text(type) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Primary, selectedLabelColor = Color.White)
                            )
                        }
                    }

                    OutlinedTextField(
                        value = durationStr,
                        onValueChange = { durationStr = it },
                        label = { Text("Duração (Minutos)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().testTag("cardio_duration_input"),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary, focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                    )

                    OutlinedTextField(
                        value = caloriesStr,
                        onValueChange = { caloriesStr = it },
                        label = { Text("Calorias Estimadas (kcal)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().testTag("cardio_calories_input"),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary, focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                    )

                    Button(
                        onClick = {
                            val duration = durationStr.toIntOrNull() ?: 0
                            val cals = caloriesStr.toIntOrNull() ?: 0
                            if (duration > 0 && cals > 0) {
                                viewModel.logCardioActivity(selectedType, duration, cals)
                                durationStr = ""
                                caloriesStr = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Primary),
                        modifier = Modifier.fillMaxWidth().height(46.dp).testTag("log_cardio_button")
                    ) {
                        Text("LOG DE CARDIO", color = Color.White, fontWeight = FontWeight.Black)
                    }
                }
            }
        }

        val activeCardios = cardioLogs.filter { !it.archived }.sortedByDescending { it.date }
        val archivedCardios = cardioLogs.filter { it.archived }.sortedByDescending { it.date }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Histórico Recente de Cardio", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                if (archivedCardios.isNotEmpty()) {
                    Button(
                        onClick = { showArchivedCardio = !showArchivedCardio },
                        colors = ButtonDefaults.buttonColors(containerColor = if (showArchivedCardio) Primary else Surface),
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text(
                            text = if (showArchivedCardio) "Ver Ativos" else "Cardio Arquivado (${archivedCardios.size})",
                            color = if (showArchivedCardio) Color.Black else Primary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        val displayedCardios = if (showArchivedCardio) archivedCardios else activeCardios

        if (displayedCardios.isEmpty()) {
            item {
                Text("Nenhuma atividade encontrada.", color = OnSurfaceDim, modifier = Modifier.padding(12.dp))
            }
        } else {
            items(displayedCardios) { log ->
                val dateStr = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(log.date))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Surface),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.03f))
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(log.type, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    if (log.distance > 0f || log.avgHr > 0) {
                                        Card(
                                            colors = CardDefaults.cardColors(containerColor = Primary.copy(alpha = 0.15f)),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = "SMARTWATCH",
                                                color = Primary,
                                                fontSize = 7.sp,
                                                fontWeight = FontWeight.Black,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    if (log.archived) {
                                        Card(
                                            colors = CardDefaults.cardColors(containerColor = Color.Gray.copy(alpha = 0.2f)),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = "ARQUIVADO",
                                                color = Color.LightGray,
                                                fontSize = 7.sp,
                                                fontWeight = FontWeight.Black,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                Text(dateStr, color = OnSurfaceDim, fontSize = 10.sp)
                            }
                            
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("Tempo", color = OnSurfaceVariant, fontSize = 9.sp)
                                    Text("${log.durationMinutes} min", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("Gasto", color = OnSurfaceVariant, fontSize = 9.sp)
                                    Text("${log.calories} kcal", color = Primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                IconButton(
                                    onClick = { viewModel.deleteCardioSession(log.id) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Excluir cardio", tint = Error, modifier = Modifier.size(16.dp))
                                }
                            }
                        }

                        if (log.distance > 0f || log.avgHr > 0) {
                            HorizontalDivider(color = Color.White.copy(alpha = 0.03f))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                if (log.distance > 0f) {
                                    Column {
                                        Text("Distância", color = OnSurfaceDim, fontSize = 8.sp)
                                        Text("${log.distance} km", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                if (log.avgHr > 0) {
                                    Column {
                                        Text("FC Média", color = OnSurfaceDim, fontSize = 8.sp)
                                        Text("${log.avgHr} BPM", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                if (log.steps > 0) {
                                    Column {
                                        Text("Passos", color = OnSurfaceDim, fontSize = 8.sp)
                                        Text("${log.steps}", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                if (!log.pace.isNullOrBlank()) {
                                    Column {
                                        Text("Pace", color = OnSurfaceDim, fontSize = 8.sp)
                                        Text(log.pace, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            if (log.fatBurnZone > 0 || log.anaerobicZone > 0 || log.limitZone > 0) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Zonas HR:", color = OnSurfaceVariant, fontSize = 8.sp)
                                    if (log.fatBurnZone > 0) {
                                        Text("Queima: ${log.fatBurnZone.toInt()}%", color = Color.Cyan, fontSize = 8.sp)
                                    }
                                    if (log.anaerobicZone > 0) {
                                        Text("Anaeróbico: ${log.anaerobicZone.toInt()}%", color = Primary, fontSize = 8.sp)
                                    }
                                    if (log.limitZone > 0) {
                                        Text("Limite: ${log.limitZone.toInt()}%", color = Color.Red, fontSize = 8.sp)
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
}
}

@Composable
fun CompletedWorkoutScreen(viewModel: MainViewModel) {
    val summary by viewModel.lastCompletedWorkoutSummary.collectAsState()
    val userProfile by viewModel.userProfile.collectAsState()
    val gymSessions by viewModel.gymWorkoutSessions.collectAsState()
    val runningSessions by viewModel.runningSessions.collectAsState()
    var showGymDetailDialog by remember { mutableStateOf(false) }

    val activeGymSession = remember(summary, gymSessions) {
        gymSessions.firstOrNull() ?: summary?.let { sum ->
            com.example.data.model.GymWorkoutSession(
                id = "session_${System.currentTimeMillis()}",
                date = System.currentTimeMillis(),
                workoutName = sum.workoutName,
                durationSeconds = sum.durationSeconds,
                totalVolumeKg = sum.totalVolume,
                totalSets = sum.totalPlannedSets,
                completedSets = sum.setsCompletedCount,
                exercises = sum.completedExercises
                    .filter { ex -> ex.sets.any { it.completed } }
                    .map { ex ->
                        com.example.data.model.GymExerciseRecord(
                            name = ex.name,
                            equipment = ex.equipment,
                            category = ex.category,
                            sets = ex.sets.filter { it.completed }.map { s ->
                                com.example.data.model.GymSetRecord(
                                    setIndex = s.index,
                                    weight = s.weight,
                                    reps = s.reps,
                                    completed = s.completed
                                )
                            },
                            notes = ex.notes
                        )
                    },
                notes = sum.completedExercises
                    .filter { ex -> ex.sets.any { it.completed } && ex.notes.isNotBlank() }
                    .joinToString(separator = " • ") { "${it.name}: ${it.notes}" }
            )
        }
    }

    val maxHighlights = remember(summary, activeGymSession) {
        val completedExs = summary?.completedExercises?.filter { ex -> ex.sets.any { it.completed } }
        if (completedExs != null && completedExs.isNotEmpty()) {
            completedExs.mapNotNull { ex ->
                val maxSet = ex.sets.filter { it.completed }.maxByOrNull { it.weight }
                if (maxSet != null && maxSet.weight > 0f) {
                    Triple(ex.name, maxSet.weight, maxSet.reps)
                } else null
            }.sortedByDescending { it.second }.take(4)
        } else {
            activeGymSession?.exercises
                ?.filter { ex -> ex.sets.any { it.completed } }
                ?.mapNotNull { ex ->
                    val maxSet = ex.sets.filter { it.completed }.maxByOrNull { it.weight }
                    if (maxSet != null && maxSet.weight > 0f) {
                        Triple(ex.name, maxSet.weight, maxSet.reps)
                    } else null
                }?.sortedByDescending { it.second }?.take(4) ?: emptyList()
        }
    }

    val ptBr = Locale.forLanguageTag("pt-BR")
    val dateTodayStr = remember {
        SimpleDateFormat("dd MMM yyyy", ptBr).format(Date())
    }

    com.example.ui.components.MaxBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState())
                .customViewForScrollCapture(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Header (Date + Title + Location Metadata)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(modifier = Modifier.width(24.dp))
                    Text(
                        text = dateTodayStr,
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Icon(
                        imageVector = Icons.Default.MoreHoriz,
                        contentDescription = "Opções",
                        tint = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = summary?.workoutName ?: "Treino Concluído",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    lineHeight = 26.sp
                )

                // Subtitle Line: Horário + Local
                val durationSec = summary?.durationSeconds ?: 0
                val endTimeMs = System.currentTimeMillis()
                val startTimeMs = endTimeMs - (durationSec * 1000L)
                val timeFormat = SimpleDateFormat("HH:mm", ptBr)
                val startTimeStr = timeFormat.format(Date(startTimeMs))
                val endTimeStr = timeFormat.format(Date(endTimeMs))
                val gymName = userProfile?.localTreino ?: "Academia padrão"

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "$startTimeStr - $endTimeStr  •",
                        color = OnSurfaceDim,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = Primary,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = gymName,
                        color = OnSurfaceDim,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // 2. Metrics Grid Card (2 Columns - Style Gravl)
            Card(
                colors = CardDefaults.cardColors(containerColor = Surface),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    val duration = summary?.durationSeconds ?: 0
                    val hours = duration / 3600
                    val mins = (duration % 3600) / 60
                    val secs = duration % 60
                    val durationStr = if (hours > 0) {
                        String.format(Locale.US, "%d:%02d:%02d", hours, mins, secs)
                    } else {
                        String.format(Locale.US, "%02d:%02d", mins, secs)
                    }

                    val durationMins = duration / 60.0
                    val kcalVal = if (durationMins > 0) (durationMins * 7.5).toInt() else 0

                    val volume = summary?.totalVolume ?: 0f
                    val athleteWeight = (userProfile.peso).coerceAtLeast(35f)
                    val biometricRatio = if (athleteWeight > 0f) volume / athleteWeight else 0f
                    val biometricRatioStr = String.format(ptBr, "%.1fx peso corporal", biometricRatio)

                    val volumeStr = if (volume >= 1000f) {
                        String.format(ptBr, "%.1f t (%,.0f kg)", volume / 1000f, volume)
                    } else {
                        String.format(ptBr, "%.0f kg", volume)
                    }

                    val exCount = summary?.exercisesCompletedCount ?: 0
                    val setsCount = summary?.setsCompletedCount ?: 0

                    // Row 1: Duração | Energia
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Duração", color = OnSurfaceDim, fontSize = 11.sp)
                            Text(durationStr, color = Color(0xFFFFD54F), fontSize = 17.sp, fontWeight = FontWeight.Black)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Energia", color = OnSurfaceDim, fontSize = 11.sp)
                            Text("$kcalVal Kcal", color = Color(0xFFFF5252), fontSize = 17.sp, fontWeight = FontWeight.Black)
                        }
                    }

                    // Row 2: Tempo médio descanso | Tempo mediano descanso
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Tempo médio de descanso", color = OnSurfaceDim, fontSize = 11.sp, maxLines = 1, softWrap = false)
                            Text("1:30", color = Color(0xFF3DDC97), fontSize = 17.sp, fontWeight = FontWeight.Black)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Tempo mediano de descanso", color = OnSurfaceDim, fontSize = 11.sp, maxLines = 1, softWrap = false)
                            Text("1:30", color = Color(0xFFB388FF), fontSize = 17.sp, fontWeight = FontWeight.Black)
                        }
                    }

                    // Row 3: Volume & Carga Biométrica | Registros
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1.3f)) {
                            Text("Volume & Carga Biométrica", color = OnSurfaceDim, fontSize = 11.sp)
                            Text(volumeStr, color = Color(0xFF40C4FF), fontSize = 15.sp, fontWeight = FontWeight.Black)
                            Text(biometricRatioStr, color = Color(0xFF0EA5E9), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Column(modifier = Modifier.weight(0.7f)) {
                            Text("Registros", color = OnSurfaceDim, fontSize = 11.sp)
                            Text("$setsCount séries", color = Color(0xFFB2FF59), fontSize = 16.sp, fontWeight = FontWeight.Black)
                        }
                    }

                    // Row 4: Músculos | Exercícios
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Músculos", color = OnSurfaceDim, fontSize = 11.sp)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FitnessCenter,
                                    contentDescription = null,
                                    tint = Primary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text("Musculação", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Exercícios", color = OnSurfaceDim, fontSize = 11.sp)
                            Text("$exCount", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }
            }

            // 2.5 Card "★ Destaques de Cargas Máximas"
            if (maxHighlights.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Surface),
                    border = BorderStroke(1.dp, Color(0xFFFACC15).copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.EmojiEvents,
                                contentDescription = null,
                                tint = Color(0xFFFACC15),
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Destaques de Cargas Máximas",
                                color = Color(0xFFFACC15),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black,
                                fontStyle = FontStyle.Italic
                            )
                        }

                        HorizontalDivider(color = Color.White.copy(alpha = 0.06f))

                        maxHighlights.forEach { (exName, maxWeight, reps) ->
                            val wStr = if (maxWeight % 1f == 0f) "${maxWeight.toInt()}" else String.format(ptBr, "%.1f", maxWeight)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = exName,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "$wStr kg × $reps reps",
                                    color = Color(0xFFFACC15),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Black,
                                    fontStyle = FontStyle.Italic
                                )
                            }
                        }
                    }
                }
            }

            // 3. Card "✨ Resumo de IA" (Diretriz de Evolução)
            val activeWorkoutVal = viewModel.activeWorkout.value
            val activeExercisesVal = viewModel.activeExercises.value
            val isEarlyVal = summary?.isEarly == true
            val feedbackMessages = if (activeWorkoutVal != null) {
                viewModel.generateWorkoutFeedbackMessage(
                    activeWorkoutVal,
                    activeExercisesVal,
                    summary?.durationSeconds ?: 0,
                    isEarlyVal
                )
            } else {
                listOf("Parabéns pelo treino! Mantenha a consistência para otimizar sua hipertrofia.")
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = Surface),
                border = BorderStroke(1.dp, Primary.copy(alpha = 0.3f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().testTag("card_resumo_ia")
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = Primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Resumo de IA",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.05f))

                    feedbackMessages.forEach { msg ->
                        Text(
                            text = msg,
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        )
                    }
                }
            }

            // 3.5 Card "Treinador Fisiologista IA Pós-Treino" (MAX GYM + Cruzamento 48h + Ajuste de Carga)
            val currentContext = LocalContext.current
            activeGymSession?.let { targetGymSession ->
                com.example.ui.components.AiPhysiologistCard(
                    sessionId = targetGymSession.id,
                    isRunningMode = false,
                    initialCachedReport = targetGymSession.aiCoachReport,
                    initialAdjustmentsJson = targetGymSession.aiLoadAdjustmentsJson,
                    isLightMode = false,
                    onGenerateAnalysis = { checkIn, forceCloud ->
                        val result = com.example.services.ai.AiPhysiologistAnalyzer.analyzeGymSession(
                            context = currentContext,
                            session = targetGymSession,
                            allGymSessions = gymSessions,
                            recentRunningSessions = runningSessions,
                            userProfile = userProfile,
                            checkIn = checkIn,
                            forceCloudRegenerate = forceCloud
                        )
                        val adjJson = com.example.services.ai.GymLoadAdjustment.listToJson(result.loadAdjustments)
                        viewModel.saveGymSessionAiReport(targetGymSession, result.reportText, adjJson)
                        result
                    },
                    onApplyLoadAdjustments = { adjustments ->
                        viewModel.applyAiLoadAdjustmentsToNextWorkout(targetGymSession.workoutName, adjustments)
                    }
                )
            }

            // 4. Seção "Exercícios" (Lista detalhada de exercícios executados com séries)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Exercícios",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black
                )

                val completedExs = (summary?.completedExercises ?: emptyList()).filter { ex ->
                    ex.sets.any { it.completed }
                }
                if (completedExs.isEmpty()) {
                    Text(
                        text = "Nenhum detalhamento individual salvo para esta sessão.",
                        color = OnSurfaceDim,
                        fontSize = 12.sp
                    )
                } else {
                    completedExs.forEach { ex ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            // Miniatura com Check verde
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Surface)
                                    .border(1.dp, Color.White.copy(0.08f), RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FitnessCenter,
                                    contentDescription = null,
                                    tint = Primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                // Badge verde no canto inferior
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .offset(x = 2.dp, y = 2.dp)
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(Success),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Color.Black,
                                        modifier = Modifier.size(11.dp)
                                    )
                                }
                            }

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text(
                                    text = ex.name,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )

                                // Séries executadas (Carga x Reps)
                                val completedSets = ex.sets.filter { it.completed }
                                if (completedSets.isEmpty()) {
                                    Text("Nenhuma série concluída", color = OnSurfaceDim, fontSize = 12.sp)
                                } else {
                                    val isBw = com.example.utils.WeightCalculator.isBodyweightExercise(null, ex.name, ex.equipment)
                                    val isPerDumbbell = com.example.utils.WeightCalculator.isPerDumbbellWeight(null, ex.name, ex.equipment)
                                    completedSets.forEach { set ->
                                        val setLineText = if (isBw && set.weight <= 0.05f) {
                                            "Peso Corporal x ${set.reps} reps"
                                        } else {
                                            val wStr = com.example.utils.WeightCalculator.formatStepNumber(set.weight)
                                            val unitStr = if (isPerDumbbell) "kg (cada halter)" else "kg"
                                            "$wStr $unitStr x ${set.reps} reps"
                                        }
                                        Text(
                                            text = setLineText,
                                            color = OnSurfaceDim,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }

                                if (ex.notes.isNotBlank()) {
                                    Surface(
                                        color = Color(0xFF0F1E29),
                                        shape = RoundedCornerShape(6.dp),
                                        border = BorderStroke(0.5.dp, Color(0xFF0EA5E9).copy(alpha = 0.3f)),
                                        modifier = Modifier.padding(top = 4.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.EditNote,
                                                contentDescription = null,
                                                tint = Color(0xFF00B0FF),
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Text(
                                                text = "Feedback de Campo: ${ex.notes}",
                                                color = Color(0xFFE0F2FE),
                                                fontSize = 11.sp,
                                                fontStyle = FontStyle.Italic
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 4.5 Prévia Visual do Share Card Exclusivo MAX GYM
            val context = LocalContext.current
            if (activeGymSession != null) {
                val gymCardBitmap by produceState<android.graphics.Bitmap?>(
                    initialValue = null,
                    activeGymSession,
                    userProfile.nome
                ) {
                    value = try {
                        com.example.utils.GymShareCardGenerator.generateGymReportBitmap(
                            context = context,
                            session = activeGymSession,
                            athleteName = userProfile.nome.ifBlank { "Atleta" }
                        )
                    } catch (_: Exception) {
                        null
                    }
                }
                val currentGymBitmap = gymCardBitmap
                if (currentGymBitmap != null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Surface),
                        border = BorderStroke(1.dp, Color(0xFF0EA5E9).copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                com.example.utils.AudioSynth.playTickSound()
                                showGymDetailDialog = true
                            }
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "SHARE CARD EXCLUSIVO MAX GYM",
                                    color = Color(0xFF0EA5E9),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    fontStyle = FontStyle.Italic
                                )
                                Text(
                                    text = "Toque para ampliar / compartilhar",
                                    color = OnSurfaceDim,
                                    fontSize = 10.sp
                                )
                            }
                            androidx.compose.foundation.Image(
                                bitmap = currentGymBitmap.asImageBitmap(),
                                contentDescription = "Share Card MAX GYM",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 360.dp)
                                    .clip(RoundedCornerShape(12.dp)),
                                contentScale = ContentScale.Fit
                            )
                        }
                    }
                }
            }

            // 5. Botões de Ação no Rodapé (Share Card, .TXT e Concluído)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Botão 1: COMPARTILHAR SHARE CARD MAX GYM
                Button(
                    onClick = {
                        com.example.utils.AudioSynth.playTickSound()
                        showGymDetailDialog = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0EA5E9)),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("share_summary_button")
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share Card MAX GYM",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "COMPARTILHAR SHARE CARD MAX GYM",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            fontStyle = FontStyle.Italic
                        )
                    }
                }

                // Botões 2 e 3: [ Copiar Relatório .TXT ] e [ Exportar .TXT ]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            com.example.utils.AudioSynth.playTickSound()
                            if (activeGymSession != null) {
                                val reportText = com.example.data.repository.GymHistoryRepository.exportToTextReport(
                                    session = activeGymSession,
                                    athleteName = userProfile.nome.ifBlank { "Atleta" },
                                    athleteWeightKg = userProfile.peso,
                                    gymName = userProfile.localTreino
                                )
                                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                val clip = android.content.ClipData.newPlainText("Relatório MAX GYM", reportText)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Relatório .TXT copiado com sucesso!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        border = BorderStroke(1.dp, Color(0xFF0EA5E9)),
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = null,
                            tint = Color(0xFF0EA5E9),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Copiar Relatório .TXT",
                            color = Color(0xFF0EA5E9),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            com.example.utils.AudioSynth.playTickSound()
                            if (activeGymSession != null) {
                                val reportText = com.example.data.repository.GymHistoryRepository.exportToTextReport(
                                    session = activeGymSession,
                                    athleteName = userProfile.nome.ifBlank { "Atleta" },
                                    athleteWeightKg = userProfile.peso,
                                    gymName = userProfile.localTreino
                                )
                                val sendIntent = android.content.Intent().apply {
                                    action = android.content.Intent.ACTION_SEND
                                    putExtra(android.content.Intent.EXTRA_TEXT, reportText)
                                    type = "text/plain"
                                }
                                val shareIntent = android.content.Intent.createChooser(sendIntent, "Exportar Relatório .TXT")
                                context.startActivity(shareIntent)
                            }
                        },
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileDownload,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Exportar .TXT",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }

                // Botão 4: Concluído
                Button(
                    onClick = {
                        viewModel.lastCompletedWorkoutSummary.value = null
                        viewModel.activeScreen.value = AppScreen.HOME
                        com.example.utils.AudioSynth.playTickSound()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC6FF00)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("finish_summary_button")
                ) {
                    Text(
                        text = "Concluído",
                        color = Color.Black,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }

            Spacer(modifier = Modifier.height(48.dp))
        }
    }

    if (showGymDetailDialog && activeGymSession != null) {
        com.example.ui.components.GymSessionDetailDialog(
            session = activeGymSession,
            athleteName = userProfile.nome.ifBlank { "Atleta" },
            athleteWeightKg = userProfile.peso,
            gymName = userProfile.localTreino,
            userProfile = userProfile,
            allGymSessions = gymSessions,
            recentRunningSessions = runningSessions,
            onApplyLoadAdjustments = { adjustments ->
                viewModel.applyAiLoadAdjustmentsToNextWorkout(activeGymSession.workoutName, adjustments)
            },
            onDismiss = { showGymDetailDialog = false }
        )
    }
}

fun getPhysiologicalAdjustmentText(ex: com.example.data.model.WorkoutExercise, activeWorkout: com.example.data.model.Workout?): String? {
    val firstSet = ex.sets.firstOrNull()
    if (firstSet != null && firstSet.baseWeight != null && firstSet.baseWeight != firstSet.weight) {
        val diff = firstSet.weight - firstSet.baseWeight!!
        return if (diff > 0) {
            "Ajuste fisiológico: Carga otimizada (+${String.format(java.util.Locale.US, "%.1f", diff)}kg) por prioridade na sequência."
        } else {
            "Ajuste fisiológico: Carga ajustada (${String.format(java.util.Locale.US, "%.1f", diff)}kg) devido à fadiga acumulada."
        }
    }
    if (ex.exerciseId.startsWith("low_impact_") || ex.name.contains("Baixo Impacto")) {
        return "Ajuste fisiológico: Exercício substituído por equivalência biomecânica."
    }
    if (ex.exerciseId.startsWith("sub_") || ex.name.contains("Substituto")) {
        return "Ajuste fisiológico: Equipamento indisponível na academia (substituição dinâmica)."
    }
    val desc = activeWorkout?.description ?: ""
    val name = activeWorkout?.name ?: ""
    if (desc.contains("loads reduced") || desc.contains("weight reduced") || name.contains("Hard Floor Intensity") || name.contains("Recovery Return")) {
        return "Ajuste fisiológico: Carga reduzida para recuperação ativa."
    }
    if (desc.contains("real Deload") || desc.contains("volume reduced") || name.contains("Deload")) {
        return "Ajuste fisiológico: Volume de séries reduzido para recuperação ativa."
    }
    if (desc.contains("Hard Ceiling")) {
        return "Ajuste fisiológico: Carga bloqueada (teto de volume fisiológico atingido)."
    }
    return null
}

@Composable
private fun SessionSummaryChip(text: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}



enum class PlateColor(val weight: Float, val color: Color) {
    BLUE_20KG(20.0f, Color(0xFF1976D2)),     // 🔵 Azul = 20kg
    YELLOW_15KG(15.0f, Color(0xFFFBC02D)),   // 🟡 Amarelo = 15kg
    GRAY_10KG(10.0f, Color(0xFF757575)),     // ⚫ Cinza = 10kg
    WHITE_5KG(5.0f, Color(0xFFE0E0E0)),      // ⚪ Branco = 5kg
    GREEN_2_5KG(2.5f, Color(0xFF388E3C))     // 🟢 Verde = 2.5kg
}

fun decomposeWeight(weight: Float): List<PlateColor> {
    val barWeight = 20.0f
    if (weight <= barWeight) return emptyList()
    var remainingPerSide = (weight - barWeight) / 2.0f
    val plates = mutableListOf<PlateColor>()
    val availablePlates = PlateColor.values()
    for (plate in availablePlates) {
        while (remainingPerSide >= plate.weight - 0.01f) {
            plates.add(plate)
            remainingPerSide -= plate.weight
        }
    }
    return plates
}

@Composable
fun BarbellVisual(
    weight: Float,
    modifier: Modifier = Modifier
) {
    val plates = remember(weight) {
        decomposeWeight(weight)
    }

    // Proporção de altura das anilhas estilo Gravl (20dp a 34dp)
    fun plateHeight(plate: PlateColor): androidx.compose.ui.unit.Dp = when (plate) {
        PlateColor.BLUE_20KG -> 34.dp
        PlateColor.YELLOW_15KG -> 31.dp
        PlateColor.GRAY_10KG -> 27.dp
        PlateColor.WHITE_5KG -> 24.dp
        PlateColor.GREEN_2_5KG -> 20.dp
    }

    val plateWidth = 6.5.dp
    val plateSpacing = 1.dp
    val shaftWidth = 7.dp
    val collarWidth = 3.dp
    val minSleeveRemaining = 14.dp
    val platesTotalWidth = if (plates.isEmpty()) 0.dp else (plates.size * 7.5).dp
    val totalWidth = shaftWidth + collarWidth + platesTotalWidth + minSleeveRemaining

    Box(
        modifier = modifier.height(36.dp),
        contentAlignment = Alignment.Center
    ) {
        // 1. Haste metálica contínua da barra (atravessa de ponta a ponta no centro)
        Box(
            modifier = Modifier
                .width(totalWidth)
                .height(2.5.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(Color(0xFF8E8E93))
        )

        // 2. Elementos montados da esquerda para a direita (Shaft -> Collar -> Anilhas -> Manga livre)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            // Eixo interno da barra à esquerda
            Spacer(modifier = Modifier.width(shaftWidth))

            // Anel Batente / Colar metálico da barra (Collar Stop)
            Box(
                modifier = Modifier
                    .width(collarWidth)
                    .height(12.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(Color(0xFF75757A))
                    .border(0.5.dp, Color.Black.copy(alpha = 0.6f), RoundedCornerShape(1.dp))
            )

            // Anilhas carregadas encostadas no colar (da mais pesada para a mais leve)
            if (plates.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(plateSpacing),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    plates.forEach { plate ->
                        Box(
                            modifier = Modifier
                                .width(plateWidth)
                                .height(plateHeight(plate))
                                .clip(RoundedCornerShape(2.dp))
                                .background(plate.color)
                                .border(0.6.dp, Color.Black.copy(alpha = 0.55f), RoundedCornerShape(2.dp))
                        )
                    }
                }
            }

            // Manga livre da barra estendendo-se à direita
            Spacer(modifier = Modifier.width(minSleeveRemaining))
        }
    }
}





