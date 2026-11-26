@file:OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)

package com.example.ui.screens

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.MediaStore
import android.speech.tts.TextToSpeech
import android.widget.Toast
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import android.graphics.Typeface
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import coil.compose.AsyncImage
import com.example.ui.components.AppModeSegmentedToggle
import com.example.ui.components.WorkoutStepByStepExplainerDialog
import com.example.ui.viewmodel.AppScreen
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.net.Uri
import android.provider.Settings
import com.example.services.RunTracker
import com.example.services.RunTrackerStatus
import com.example.services.RunningTrackingService
import com.example.data.model.RunningSession
import com.example.data.model.RecordedKmSplit
import com.example.data.model.deduplicatedRunningSessions
import com.example.data.model.toJsonString
import com.example.data.model.toRecordedKmSplits
import com.example.data.model.toHrTimelineList
import com.example.data.model.toRoutePointItems
import com.example.data.model.safeCoerceIn
import com.example.data.model.safeRoundToInt
import com.example.engine.RunningBlock
import com.example.engine.RunningBlockType
import com.example.engine.RunningProgramManager
import com.example.engine.RunningWorkoutPlan
import com.example.ui.theme.Surface
import com.example.ui.viewmodel.MainViewModel
import com.example.utils.AudioSynth
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

import com.example.ui.theme.RunColors
import com.example.services.HeartRateBleManager
import com.example.services.HrConnectionState

private val START_TEXT_COLOR = Color.White

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun RunningModuleScreen(viewModel: MainViewModel) {
    val userProfile by viewModel.userProfile.collectAsState()
    val rawRunningHistory by viewModel.runningSessions.collectAsState()
    val runningHistory = remember(rawRunningHistory) {
        rawRunningHistory.deduplicatedRunningSessions().sortedByDescending { it.date }
    }
    val runningShoes by viewModel.runningShoes.collectAsState()
    val officialRaces by viewModel.officialRaces.collectAsState()
    val gymWorkoutSessions by viewModel.gymWorkoutSessions.collectAsState()
    val upcomingRaces by viewModel.upcomingRaces.collectAsState()
    var showHybridCoachModal by rememberSaveable { mutableStateOf(false) }
    var showOfficialRacesVaultDialog by remember { mutableStateOf(false) }
    var showManualOfficialRaceDialog by remember { mutableStateOf(false) }
    var sessionToSaveAsOfficialRace by remember { mutableStateOf<RunningSession?>(null) }
    val context = LocalContext.current

    // GPS & RunTracker State
    val runTrackState by RunTracker.state.collectAsState()
    var showGpsStatusDialog by remember { mutableStateOf(false) }
    var measuredDistanceMeters by rememberSaveable { mutableIntStateOf(0) }

    fun startOutdoorService(sessionId: String? = null) {
        if (!RunTracker.hasLocationPermission(context)) {
            return
        }
        runCatching {
            RunTracker.start(context)
        }
        try {
            val intent = Intent(context, RunningTrackingService::class.java).apply {
                action = RunningTrackingService.ACTION_START
                sessionId?.takeIf { it.isNotBlank() }?.let {
                    putExtra(RunningTrackingService.EXTRA_SESSION_ID, it)
                }
            }
            ContextCompat.startForegroundService(context, intent)
        } catch (e: Throwable) {
            com.example.utils.CrashLog.record(e, "RunningModuleScreen.startOutdoorService", context)
        }
    }

    fun stopOutdoorService() {
        runCatching {
            context.stopService(Intent(context, RunningTrackingService::class.java))
        }
        RunningTrackingService.stopServiceAndClearNotification(context)
    }

    // TTS preguiçoso (lazy) e isolado: NUNCA instancia TextToSpeech ao abrir a aba ou trocar de tela
    // (evitando o "duck" mudo de 0.5s no Spotify). Só é criado se uma sessão ativa realmente precisar falar.
    var preWorkoutTts by remember { mutableStateOf<TextToSpeech?>(null) }
    var pendingPreWorkoutSpeech by remember { mutableStateOf<String?>(null) }
    DisposableEffect(context) {
        onDispose {
            val activeTts = preWorkoutTts
            if (activeTts != null) {
                runCatching {
                    activeTts.stop()
                    activeTts.shutdown()
                }
                preWorkoutTts = null
                if (com.example.MainActivity.isRunningSessionActive) {
                    AudioSynth.abandonAudioFocus(context)
                }
            }
        }
    }

    fun speakPreWorkoutAlert(message: String) {
        if (!com.example.MainActivity.isRunningSessionActive || !viewModel.coachVoiceEnabled.value || message.isBlank()) return
        val engine = preWorkoutTts
        if (engine != null) {
            runCatching {
                AudioSynth.requestAudioFocus(context)
                val status = engine.speak(message, TextToSpeech.QUEUE_FLUSH, null, "pre_workout_gps_${System.currentTimeMillis()}")
                if (status != TextToSpeech.SUCCESS) {
                    AudioSynth.abandonAudioFocus(context)
                }
            }.onFailure {
                AudioSynth.abandonAudioFocus(context)
            }
        } else {
            pendingPreWorkoutSpeech = message
            runCatching {
                var ttsInstance: TextToSpeech? = null
                ttsInstance = TextToSpeech(context.applicationContext) { status ->
                    if (status == TextToSpeech.SUCCESS) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            runCatching {
                                ttsInstance?.setAudioAttributes(
                                    android.media.AudioAttributes.Builder()
                                        .setUsage(android.media.AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                                        .build()
                                )
                            }
                        }
                        runCatching {
                            ttsInstance?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                                override fun onStart(utteranceId: String?) {}
                                override fun onDone(utteranceId: String?) {
                                    AudioSynth.abandonAudioFocus(context)
                                }
                                @Deprecated("Deprecated in Java")
                                override fun onError(utteranceId: String?) {
                                    AudioSynth.abandonAudioFocus(context)
                                }
                                override fun onError(utteranceId: String?, errorCode: Int) {
                                    AudioSynth.abandonAudioFocus(context)
                                }
                                override fun onStop(utteranceId: String?, interrupted: Boolean) {
                                    if (!interrupted) {
                                        AudioSynth.abandonAudioFocus(context)
                                    }
                                }
                            })
                        }
                        runCatching { ttsInstance?.language = Locale.forLanguageTag("pt-BR") }
                        preWorkoutTts = ttsInstance
                        pendingPreWorkoutSpeech?.let { msg ->
                            pendingPreWorkoutSpeech = null
                            if (com.example.MainActivity.isRunningSessionActive) {
                                runCatching {
                                    AudioSynth.requestAudioFocus(context)
                                    val speakStatus = ttsInstance?.speak(msg, TextToSpeech.QUEUE_FLUSH, null, "pre_workout_gps_alert")
                                    if (speakStatus != TextToSpeech.SUCCESS) {
                                        AudioSynth.abandonAudioFocus(context)
                                    }
                                }.onFailure {
                                    AudioSynth.abandonAudioFocus(context)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Onboarding & Anamnese Fisiológica / RX-PRO Library State
    var anamnesisVersion by remember { mutableIntStateOf(0) }
    val anamnesisProfile = remember(context, anamnesisVersion, userProfile.baselinePaceMinPerKm) {
        com.example.ui.components.RunnerAnamnesisManager.loadProfile(context, userProfile.baselinePaceMinPerKm)
    }
    val runnerCalibration = remember(anamnesisProfile, userProfile.idade, runningHistory, userProfile.baselinePaceMinPerKm, userProfile.fcMaxCalibradaBpm) {
        com.example.ui.components.RunnerAnamnesisManager.calculateCalibration(
            profile = anamnesisProfile,
            athleteAge = userProfile.idade,
            history = runningHistory,
            baselinePaceMinPerKm = userProfile.baselinePaceMinPerKm,
            calibratedMaxHrBpm = userProfile.fcMaxCalibradaBpm
        )
    }
    var showAnamnesisModal by rememberSaveable { mutableStateOf(false) }
    var showRxWorkoutLibraryModal by rememberSaveable { mutableStateOf(false) }
    var selectedRxWorkoutCode by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedRxWorkout = remember(selectedRxWorkoutCode) {
        com.example.ui.components.RunnerAnamnesisManager.RX_PRO_WORKOUT_CATALOG.firstOrNull { it.code == selectedRxWorkoutCode }
    }

    val needsPaceOnboarding = userProfile.baselinePaceMinPerKm == null && !anamnesisProfile.isConfigured
    var hasDismissedPaceOnboarding by remember { mutableStateOf(false) }
    var showPaceOnboardingSheet by remember { mutableStateOf(false) }

    LaunchedEffect(anamnesisProfile.isConfigured, needsPaceOnboarding) {
        if (viewModel.isRunningHudActive.value) return@LaunchedEffect
        if (!anamnesisProfile.isConfigured && !com.example.ui.components.RunnerAnamnesisManager.hasAutoShownOnce(context)) {
            com.example.ui.components.RunnerAnamnesisManager.markAutoShownOnce(context)
            hasDismissedPaceOnboarding = true
            showAnamnesisModal = true
        } else if (needsPaceOnboarding && !hasDismissedPaceOnboarding) {
            showPaceOnboardingSheet = true
        }
    }

    // Bluetooth & Heart Rate State
    val bleManager = remember { HeartRateBleManager.getInstance(context) }
    val hrConnectionState by bleManager.connectionState.collectAsState()
    val currentBpm by bleManager.currentBpm.collectAsState()
    val discoveredDevices by bleManager.discoveredDevices.collectAsState()
    val isScanningBle by bleManager.isScanning.collectAsState()
    val connectedDeviceName by bleManager.connectedDeviceName.collectAsState()
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
        // REGRA ABSOLUTA: JAMAIS inicia corrida, serviço ou treino aqui!
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
            com.example.MainActivity.markIntentionalExternalNavigation()
            blePermissionLauncher.launch(perms.toTypedArray())
        } else {
            showHrPairingSheet = true
            bleManager.startScan()
        }
    }

    // Active session state — Padrão obrigatório: Ar Livre (isIndoor = false / OUTDOOR) e GPS Habilitado (isGpsEnabled = true)
    var isHUDActive by rememberSaveable { mutableStateOf(false) }
    var activePlan by rememberSaveable { mutableStateOf<RunningWorkoutPlan?>(null) }
    var selectedFreeRaceMode by rememberSaveable { mutableStateOf<com.example.engine.FreeRunRaceMode?>(null) }
    var isGentleModeSession by rememberSaveable { mutableStateOf(false) }
    var isIndoor by rememberSaveable { mutableStateOf(false) }
    var isGpsEnabled by rememberSaveable { mutableStateOf(true) }
    var selectedEnvironment by rememberSaveable { mutableStateOf("OUTDOOR") } // "OUTDOOR" or "TREADMILL"
    var showEnvironmentSheet by remember { mutableStateOf(false) }
    var showSystemGpsDisabledDialog by remember { mutableStateOf(false) }
    var pendingAutoStartAfterGpsSettings by rememberSaveable { mutableStateOf(false) }
    var showSummaryDialog by rememberSaveable { mutableStateOf(false) }
    var wasSessionAutoSaved by rememberSaveable { mutableStateOf(false) }
    var showRecoverCompletedWorkoutDialog by rememberSaveable { mutableStateOf(false) }
    var autoSavedSessionId by rememberSaveable { mutableStateOf<String?>(null) }
    var autoSavedSessionTimestamp by rememberSaveable { mutableLongStateOf(0L) }
    var showShoesBottomSheet by remember { mutableStateOf(false) }
    var showReminderConfigDialog by remember { mutableStateOf(false) }
    var isDailyReminderEnabled by remember {
        mutableStateOf(com.example.services.RunningDailyReminderManager.isReminderEnabled(context))
    }
    var dailyReminderHour by remember {
        mutableIntStateOf(com.example.services.RunningDailyReminderManager.getReminderTime(context).first)
    }
    var dailyReminderMinute by remember {
        mutableIntStateOf(com.example.services.RunningDailyReminderManager.getReminderTime(context).second)
    }
    var completedDurationSec by rememberSaveable { mutableIntStateOf(0) }
    var completedSkippedIndices by remember { mutableStateOf(setOf<Int>()) }
    var selectedDayNumberForSession by rememberSaveable { mutableIntStateOf(1) }
    val coachVoiceEnabled by viewModel.coachVoiceEnabled.collectAsState()
    val runHudFaceStyle by viewModel.runHudFaceStyle.collectAsState()

    var completedMainRunDurationSec by rememberSaveable { mutableIntStateOf(0) }
    var completedMainRunDistanceMeters by rememberSaveable { mutableIntStateOf(0) }
    var completedRecordedSplits by remember { mutableStateOf<List<RecordedKmSplit>>(emptyList()) }
    var completedHrTimelineStr by rememberSaveable { mutableStateOf("") }
    var completedRoutePointsJson by rememberSaveable { mutableStateOf<String?>(null) }
    val activePreRunCheckIn by viewModel.activePreRunCheckIn.collectAsState()
    var showPreRunCheckInDialog by rememberSaveable { mutableStateOf(false) }
    var pendingStartEnv by rememberSaveable { mutableStateOf("OUTDOOR") }
    var pendingStartGentle by rememberSaveable { mutableStateOf(false) }
    var latestRpeCr10 by remember { mutableStateOf<Int?>(null) }
    var latestSrpeLoadUa by remember { mutableStateOf<Int?>(null) }
    var latestExecutionAudit by remember { mutableStateOf<com.example.data.model.SessionExecutionAudit?>(null) }

    // Launcher exclusivo para o diálogo informativo de status do GPS - NUNCA inicia o treino nem o serviço
    val gpsDialogPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            isGpsEnabled = true
            Toast.makeText(context, "Permissão de GPS ativada", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Permissão de GPS negada", Toast.LENGTH_SHORT).show()
        }
        // REGRA ABSOLUTA: JAMAIS inicia corrida ou dispara o Foreground Service!
    }

    // Função centralizada de Auto-Start imediato de GPS no clique de "INICIAR"
    fun startOutdoorWorkoutNowWithSystemCheck() {
        isIndoor = false
        isGpsEnabled = true
        selectedEnvironment = "OUTDOOR"
        if (!RunTracker.isSystemGpsEnabled(context)) {
            speakPreWorkoutAlert("Atenção: ative a localização do celular para registrar a distância")
            showSystemGpsDisabledDialog = true
            return
        }
        autoSavedSessionId = UUID.randomUUID().toString()
        autoSavedSessionTimestamp = System.currentTimeMillis()
        RunTracker.clearActiveWorkoutCheckpoint(context)
        RunTracker.reset()
        startOutdoorService(autoSavedSessionId)
        isHUDActive = true
    }

    var hasRequestedActivityRecognitionOnce by rememberSaveable { mutableStateOf(false) }

    fun buildRunStartPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACTIVITY_RECOGNITION
            )
        } else {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        }
    }

    fun needsActivityRecognitionRequest(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            !hasRequestedActivityRecognitionOnce &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACTIVITY_RECOGNITION
            ) != PackageManager.PERMISSION_GRANTED
    }

    // Launcher acionado automaticamente quando o usuário clica em INICIAR e a permissão ainda não foi concedida
    val workoutStartLocationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val granted = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
                RunTracker.hasLocationPermission(context)
        if (selectedEnvironment == "OUTDOOR") {
            if (granted) {
                isGpsEnabled = true
                startOutdoorWorkoutNowWithSystemCheck()
            } else {
                Toast.makeText(context, "Sem permissão de GPS • Treino em modo cronômetro", Toast.LENGTH_SHORT).show()
                autoSavedSessionId = UUID.randomUUID().toString()
                autoSavedSessionTimestamp = System.currentTimeMillis()
                isHUDActive = true
            }
        } else {
            autoSavedSessionId = UUID.randomUUID().toString()
            autoSavedSessionTimestamp = System.currentTimeMillis()
            isHUDActive = true
        }
    }

    fun executeWorkoutStartWithAutoGps(env: String = selectedEnvironment, gentle: Boolean = isGentleModeSession) {
        selectedEnvironment = env
        isIndoor = (env == "TREADMILL")
        isGpsEnabled = (env == "OUTDOOR")
        isGentleModeSession = gentle
        showEnvironmentSheet = false
        latestRpeCr10 = null
        latestSrpeLoadUa = null
        latestExecutionAudit = null
        autoSavedSessionId = UUID.randomUUID().toString()
        autoSavedSessionTimestamp = System.currentTimeMillis()
        RunTracker.clearActiveWorkoutCheckpoint(context)
        RunTracker.reset()

        if (gentle) {
            activePlan = RunningProgramManager.getGentleWalkPlan()
        }

        if (env == "OUTDOOR") {
            if (RunTracker.hasLocationPermission(context) && !needsActivityRecognitionRequest()) {
                startOutdoorWorkoutNowWithSystemCheck()
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    hasRequestedActivityRecognitionOnce = true
                }
                com.example.MainActivity.markIntentionalExternalNavigation()
                workoutStartLocationPermissionLauncher.launch(buildRunStartPermissions())
            }
        } else {
            if (needsActivityRecognitionRequest()) {
                hasRequestedActivityRecognitionOnce = true
                com.example.MainActivity.markIntentionalExternalNavigation()
                workoutStartLocationPermissionLauncher.launch(arrayOf(Manifest.permission.ACTIVITY_RECOGNITION))
            } else {
                isHUDActive = true
            }
        }
    }

    fun triggerWorkoutStartWithAutoGps(
        env: String = selectedEnvironment,
        gentle: Boolean = isGentleModeSession,
        freeRaceMode: com.example.engine.FreeRunRaceMode? = selectedFreeRaceMode,
        sessionType: String? = activePlan?.sessionType,
        plan: RunningWorkoutPlan? = activePlan,
        programId: String? = viewModel.selectedRunningProgramId.value
    ) {
        pendingStartEnv = env
        pendingStartGentle = gentle
        showEnvironmentSheet = false
        val effectiveSessionType = sessionType ?: plan?.sessionType
        val effectiveDisplayTitle = plan?.let { planRef ->
            RunningProgramManager.getDisplayTitleForSessionType(
                sessionType = effectiveSessionType,
                isGentleMode = gentle,
                weekNumber = planRef.weekNumber,
                dayNumber = selectedDayNumberForSession
            )
        }
        if (!RunningProgramManager.shouldShowPreRunAnamnesis(
                freeRaceMode = freeRaceMode,
                sessionType = effectiveSessionType,
                planTitle = plan?.title,
                displayTitle = effectiveDisplayTitle,
                weekNumber = plan?.weekNumber,
                programId = programId
            )
        ) {
            showPreRunCheckInDialog = false
            showAnamnesisModal = false
            showPaceOnboardingSheet = false
            viewModel.setActivePreRunCheckIn(null)
            executeWorkoutStartWithAutoGps(env = env, gentle = gentle)
        } else {
            showPreRunCheckInDialog = true
        }
    }

    // Quando o usuário volta das Configurações de Localização do Android após ativar o GPS, inicia automaticamente
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, pendingAutoStartAfterGpsSettings, activePlan) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                if (pendingAutoStartAfterGpsSettings && activePlan != null && RunTracker.isSystemGpsEnabled(context)) {
                    pendingAutoStartAfterGpsSettings = false
                    showSystemGpsDisabledDialog = false
                    if (autoSavedSessionId.isNullOrBlank()) {
                        autoSavedSessionId = UUID.randomUUID().toString()
                        autoSavedSessionTimestamp = System.currentTimeMillis()
                    }
                    RunTracker.reset()
                    startOutdoorService(autoSavedSessionId)
                    isHUDActive = true
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(isHUDActive) {
        viewModel.isRunningHudActive.value = isHUDActive
        if (isHUDActive) {
            runCatching {
                bleManager.resetSessionStats()
                bleManager.startTimelineRecording()
            }
            if (selectedEnvironment == "OUTDOOR") {
                if (RunTracker.hasLocationPermission(context) && !RunningTrackingService.isServiceRunning) {
                    startOutdoorService(autoSavedSessionId)
                }
            }
        } else {
            runCatching { bleManager.stopTimelineRecording() }
            stopOutdoorService()
        }
    }

    LaunchedEffect(Unit) {
        // Garante que a modalidade padrão nasça sempre como Ar Livre (isIndoor = false) e GPS ativo (isGpsEnabled = true)
        if (!isHUDActive && !showSummaryDialog) {
            isIndoor = false
            isGpsEnabled = true
            selectedEnvironment = "OUTDOOR"
            stopOutdoorService()
            // Se o app foi fechado/reiniciado após Pausar ou Parar (pendingSummarySave == true),
            // restaura automaticamente o RunningSummaryDialog com 100% dos dados, mapa GPS e parciais intactos!
            RunTracker.recoverUnfinishedWorkoutCheckpointIfNeeded(
                context = context,
                onRestorePendingSummary = { snapshot ->
                    val currentProgId = viewModel.selectedRunningProgramId.value
                    val restoredPlan = runCatching {
                        val freeRaceMode = com.example.engine.FreeRunRaceMode.fromSessionType(snapshot.sessionType)
                        when {
                            snapshot.isGentleMode -> RunningProgramManager.getGentleWalkPlan()
                            freeRaceMode != null -> RunningProgramManager.getFreeOrRacePlan(freeRaceMode)
                                .copy(weekNumber = 0, sessionType = snapshot.sessionType)
                            snapshot.weekNumber <= 0 -> RunningProgramManager.getFreeOrRacePlan(
                                com.example.engine.FreeRunRaceMode.FREE_RUN
                            ).copy(weekNumber = 0, sessionType = snapshot.sessionType)
                            RunningProgramManager.isTrack400mSessionType(snapshot.sessionType) ->
                                RunningProgramManager.getTrack400mPlan(weekNumber = snapshot.weekNumber)
                                    .copy(sessionType = snapshot.sessionType)
                            else -> RunningProgramManager.getPlanForProgram(
                                currentProgId,
                                snapshot.weekNumber,
                                snapshot.dayNumber
                            ).copy(sessionType = snapshot.sessionType)
                        }
                    }.getOrElse {
                        RunningProgramManager.getPlanForProgram("road_to_5k", snapshot.weekNumber, snapshot.dayNumber)
                    }
                    activePlan = restoredPlan
                    autoSavedSessionId = snapshot.sessionId
                    autoSavedSessionTimestamp = snapshot.startTimestampMs
                    selectedDayNumberForSession = snapshot.dayNumber
                    selectedEnvironment = snapshot.environment
                    isIndoor = (snapshot.environment == "TREADMILL")
                    isGpsEnabled = (snapshot.environment == "OUTDOOR")
                    isGentleModeSession = snapshot.isGentleMode
                    completedDurationSec = snapshot.elapsedSec
                    measuredDistanceMeters = snapshot.distanceMeters
                    completedMainRunDurationSec = snapshot.mainRunDurationSec
                    completedMainRunDistanceMeters = snapshot.mainRunDistanceMeters
                    completedRecordedSplits = snapshot.kmSplitsJson.toRecordedKmSplits()
                    completedHrTimelineStr = snapshot.hrTimeline ?: ""
                    completedRoutePointsJson = snapshot.routePointsJson
                    completedSkippedIndices = snapshot.skippedIndices
                    wasSessionAutoSaved = false
                    showSummaryDialog = true
                    Toast.makeText(
                        context,
                        "Treino recuperado intacto! Confirme os dados abaixo para salvar.",
                        Toast.LENGTH_LONG
                    ).show()
                },
                onRecovered = { recoveredSession ->
                    viewModel.saveRunningSession(recoveredSession)
                    Toast.makeText(
                        context,
                        "Treino anterior recuperado e salvo no Histórico!",
                        Toast.LENGTH_LONG
                    ).show()
                }
            )
        }
    }

    val currentProgress = remember(runningHistory) {
        RunningProgramManager.calculateCurrentProgress(runningHistory)
    }
    val manualPlanOverride by viewModel.manualPlanDayOverride.collectAsState()
    val effectivePlanWeek = manualPlanOverride?.first ?: currentProgress.weekNumber
    val effectivePlanDay = manualPlanOverride?.second ?: currentProgress.dayNumber

    var selectedWeek by rememberSaveable(effectivePlanWeek) { mutableIntStateOf(effectivePlanWeek) }
    var selectedDay by rememberSaveable(effectivePlanDay) { mutableIntStateOf(effectivePlanDay) }
    var recentSessionToDelete by remember { mutableStateOf<RunningSession?>(null) }

    if (isHUDActive && activePlan != null) {
        val defaultShoeNameForHud = remember(runningShoes) {
            val activeShoe = runningShoes.firstOrNull { it.isDefault && !it.isRetired }
                ?: runningShoes.firstOrNull { !it.isRetired }
            activeShoe?.let { "${it.brand} ${it.name}" } ?: "Olympikus Corre 4"
        }
        RunningHUD(
            plan = activePlan!!,
            dayNumber = selectedDayNumberForSession,
            sessionIdForCheckpoint = autoSavedSessionId ?: "auto_checkpoint_session",
            sessionStartTimestampMs = autoSavedSessionTimestamp.takeIf { it > 0L } ?: System.currentTimeMillis(),
            defaultShoeName = defaultShoeNameForHud,
            environment = selectedEnvironment,
            baselinePaceMinPerKm = userProfile.baselinePaceMinPerKm,
            athleteAge = userProfile.idade.takeIf { it in 10..100 },
            calibratedMaxHrBpm = userProfile.fcMaxCalibradaBpm,
            calibratedLt1Bpm = userProfile.limiarLt1Bpm,
            calibratedLt2Bpm = userProfile.limiarLt2Bpm,
            isGentleMode = isGentleModeSession,
            coachVoiceEnabled = coachVoiceEnabled,
            hudFaceStyle = runHudFaceStyle,
            onEnvironmentChanged = { newEnv ->
                selectedEnvironment = newEnv
                isIndoor = (newEnv == "TREADMILL")
                isGpsEnabled = (newEnv == "OUTDOOR")
                if (newEnv == "OUTDOOR") {
                    if (RunTracker.hasLocationPermission(context) && !needsActivityRecognitionRequest()) {
                        startOutdoorService(autoSavedSessionId)
                    } else {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            hasRequestedActivityRecognitionOnce = true
                        }
                        com.example.MainActivity.markIntentionalExternalNavigation()
                        workoutStartLocationPermissionLauncher.launch(buildRunStartPermissions())
                    }
                } else {
                    stopOutdoorService()
                }
            },
            onFinishSession = { duration, skippedIndices, mainDur, mainDist, recSplits, hrStr ->
                val rawGpsMeters = runTrackState.distanceMeters
                val fallbackDistMeters = RunTracker.estimateFallbackDistanceMeters(
                    plan = activePlan,
                    durationSec = duration,
                    skippedIndices = skippedIndices,
                    baselinePaceMinPerKm = userProfile.baselinePaceMinPerKm
                )
                // Proíbe invenção de distância em modo AR LIVRE (OUTDOOR): se o GPS marcou 0m, exibe 0,00 km honestamente!
                val effectiveInitialDistMeters = if (selectedEnvironment == "OUTDOOR") {
                    rawGpsMeters
                } else {
                    when {
                        rawGpsMeters >= 50 -> rawGpsMeters
                        duration >= 60 -> fallbackDistMeters
                        else -> rawGpsMeters
                    }
                }
                val effectiveInitialMainDist = if (selectedEnvironment == "OUTDOOR" && rawGpsMeters <= 0) {
                    0
                } else {
                    mainDist
                }

                val finalSplitsForSummary = if (effectiveInitialDistMeters > 0) recSplits else emptyList()
                val routeJson = if (RunTracker.recordedPoints.isNotEmpty() && effectiveInitialDistMeters > 0) {
                    RunTracker.getRoutePointsJson()
                } else null
                val blockLogsJson = RunTracker.getBlockUnintendedWalkJson()

                completedDurationSec = duration
                completedSkippedIndices = skippedIndices
                completedMainRunDurationSec = mainDur
                completedMainRunDistanceMeters = effectiveInitialMainDist
                completedRecordedSplits = finalSplitsForSummary
                completedHrTimelineStr = hrStr
                completedRoutePointsJson = routeJson
                measuredDistanceMeters = effectiveInitialDistMeters

                // TRAVA DE SEGURANÇA DA PLANILHA & ORDEM SEGURA DE DESLIGAMENTO:
                // 1) Persiste IMEDIATAMENTE o snapshot completo em disco com pendingSummarySave = true ANTES de parar o serviço!
                // 2) JAMAIS apaga o checkpoint aqui; a limpeza ocorre exclusivamente após confirmar "Salvar" ou "Descartar Treino".
                wasSessionAutoSaved = false
                runCatching {
                    RunTracker.saveActiveWorkoutCheckpoint(
                        context = context,
                        sessionId = autoSavedSessionId ?: UUID.randomUUID().toString().also { autoSavedSessionId = it },
                        startTimestampMs = autoSavedSessionTimestamp.takeIf { it > 0L } ?: System.currentTimeMillis(),
                        weekNumber = activePlan?.weekNumber ?: 1,
                        dayNumber = selectedDayNumberForSession,
                        sessionType = activePlan?.sessionType ?: "INTERVAL",
                        environment = selectedEnvironment,
                        isGentleMode = isGentleModeSession,
                        shoeName = defaultShoeNameForHud,
                        elapsedSec = duration.coerceAtLeast(1),
                        distanceMeters = effectiveInitialDistMeters,
                        fallbackEstimatedMeters = fallbackDistMeters,
                        mainRunDurationSec = mainDur,
                        mainRunDistanceMeters = effectiveInitialMainDist,
                        routePointsJson = routeJson,
                        kmSplitsJson = if (finalSplitsForSummary.isNotEmpty()) finalSplitsForSummary.toJsonString() else null,
                        hrTimeline = hrStr,
                        workoutBlockLogsJson = blockLogsJson,
                        skippedIndices = skippedIndices,
                        pendingSummarySave = true,
                        forceImmediateSave = true
                    )
                }

                runCatching { stopOutdoorService() }
                runCatching { RunTracker.stop() }
                isHUDActive = false
                showSummaryDialog = true
            },
            onCancelSession = {
                stopOutdoorService()
                RunTracker.clearActiveWorkoutCheckpoint(context)
                RunTracker.reset()
                autoSavedSessionId = null
                isHUDActive = false
                activePlan = null
                isGentleModeSession = false
            }
        )
    } else {
        com.example.ui.components.ProvideRunFontScale {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(RunColors.bg)
        ) {
            val currentAppMode by viewModel.appMode.collectAsState()
            val selectedProgramId by viewModel.selectedRunningProgramId.collectAsState()
            val showProgramsCatalog by viewModel.showRunningProgramsCatalog.collectAsState()
            val fatigueAdvisoryState by viewModel.fatigueAdvisoryState.collectAsState()
            val currentProgramInfo = remember(selectedProgramId) {
                RunningProgramManager.getProgramById(selectedProgramId)
            }

            // selectedWeek, selectedDay e selectedFreeRaceMode já estão elevados no escopo principal
            var includeWarmupWithStrides by rememberSaveable { mutableStateOf(false) }
            var isTrack400mSelected by rememberSaveable(selectedProgramId) {
                mutableStateOf(selectedProgramId == "fartlek_intervals" || selectedProgramId == "tiros_10x400m")
            }
            LaunchedEffect(selectedProgramId) {
                if (selectedProgramId == "free_regenerative") {
                    selectedRxWorkoutCode = null
                    selectedFreeRaceMode = null
                }
            }
            val activeSelectedPlan = remember(
                selectedProgramId,
                selectedWeek,
                selectedDay,
                isTrack400mSelected,
                includeWarmupWithStrides,
                selectedRxWorkout,
                runnerCalibration
            ) {
                val basePlan = when {
                    selectedRxWorkout != null -> {
                        selectedRxWorkout.toRunningWorkoutPlan(runnerCalibration)
                    }
                    selectedProgramId == "tiros_10x400m" -> {
                        RunningProgramManager.getPlanForProgram("tiros_10x400m", selectedWeek, selectedDay)
                    }
                    isTrack400mSelected -> {
                        RunningProgramManager.getTrack400mPlan(weekNumber = selectedWeek)
                    }
                    else -> {
                        RunningProgramManager.getPlanForProgram(selectedProgramId, selectedWeek, selectedDay)
                    }
                }
                RunningProgramManager.applyWarmupWithStrides(basePlan, includeWarmupWithStrides)
            }


            val condensedFontFamily = androidx.compose.ui.text.font.FontFamily(
                android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.NORMAL)
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 0.dp, bottom = 72.dp)
            ) {
                // A. Top bar (~56dp)
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Toggle de Modo: MUSCULAÇÃO / CORRIDA
                        AppModeSegmentedToggle(
                            currentMode = currentAppMode,
                            onModeSelected = { viewModel.setAppMode(it) }
                        )

                        Spacer(modifier = Modifier.width(6.dp))

                        // Direita: GPS + Monitor Cardíaco BLE + Lembrete + Coach
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.wrapContentWidth(unbounded = false)
                        ) {
                            val hasGpsPerm = RunTracker.hasLocationPermission(context)
                            val isSysGpsOn = RunTracker.isSystemGpsEnabled(context)
                            val (gpsIconTint, gpsDescription) = when {
                                !hasGpsPerm -> Pair(RunColors.alert, "GPS Sem Permissão")
                                !isSysGpsOn -> Pair(RunColors.alert, "Localização Desligada")
                                runTrackState.status == RunTrackerStatus.WEAK -> Pair(RunColors.alert, "GPS Sinal Fraco")
                                runTrackState.status == RunTrackerStatus.SEARCHING -> Pair(RunColors.brandText, "Buscando GPS")
                                runTrackState.status == RunTrackerStatus.GOOD -> Pair(RunColors.phaseWalk, "GPS OK")
                                else -> Pair(RunColors.phaseWalk, "GPS Ativo (Auto-Start)")
                            }

                            // GPS Icon (Apenas status informativo; o auto-start já ocorre ao clicar em START)
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF1A1A22))
                                    .border(1.dp, gpsIconTint.copy(alpha = 0.35f), CircleShape)
                                    .clickable { showGpsStatusDialog = true },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.GpsFixed,
                                    contentDescription = gpsDescription,
                                    tint = gpsIconTint,
                                    modifier = Modifier.size(15.dp)
                                )
                            }

                            // Monitor Cardíaco BLE (Abre Modal Fullscreen de Frequência Cardíaca e Zonas)
                            if (hrConnectionState == HrConnectionState.CONNECTED && currentBpm > 0) {
                                Surface(
                                    onClick = {
                                        AudioSynth.playTickSound()
                                        showHrPairingSheet = true
                                    },
                                    color = Color(0xFF1A1A22),
                                    shape = RoundedCornerShape(16.dp),
                                    border = BorderStroke(1.dp, Color(0xFFFF4D4D).copy(alpha = 0.45f)),
                                    modifier = Modifier
                                        .height(32.dp)
                                        .clickable {
                                            AudioSynth.playTickSound()
                                            showHrPairingSheet = true
                                        }
                                        .testTag("top_bar_hr_badge")
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                                        modifier = Modifier.padding(horizontal = 7.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Favorite,
                                            contentDescription = "Monitor cardíaco conectado (Abrir Zonas)",
                                            tint = Color(0xFFFF4D4D),
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = "$currentBpm",
                                            color = Color.White,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF1A1A22))
                                        .border(1.dp, Color.White.copy(alpha = 0.08f), CircleShape)
                                        .clickable {
                                            AudioSynth.playTickSound()
                                            showHrPairingSheet = true
                                        }
                                        .testTag("top_bar_hr_icon"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FavoriteBorder,
                                        contentDescription = "Abrir Monitor Cardíaco em Tela Cheia",
                                        tint = if (hrConnectionState == HrConnectionState.CONNECTING) Color(0xFFFFB300) else Color(0xFF8E8E93),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            // Lembrete Diário de Corrida
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF1A1A22))
                                    .border(
                                        1.dp,
                                        if (isDailyReminderEnabled) RunColors.brand.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.08f),
                                        CircleShape
                                    )
                                    .clickable {
                                        AudioSynth.playTickSound()
                                        showReminderConfigDialog = true
                                    }
                                    .testTag("running_daily_reminder_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isDailyReminderEnabled) Icons.Default.NotificationsActive else Icons.Default.NotificationsOff,
                                    contentDescription = "Lembrete diário de corrida",
                                    tint = if (isDailyReminderEnabled) RunColors.brandText else RunColors.textSecondary,
                                    modifier = Modifier.size(15.dp)
                                )
                            }

                            // Coach Híbrido & Polimento TAF (Abre Modal de Carga Híbrida + Voz do Coach)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .height(32.dp)
                                    .wrapContentWidth(unbounded = false)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color(0xFF1A1A22))
                                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                                    .clickable {
                                        AudioSynth.playTickSound()
                                        showHybridCoachModal = true
                                    }
                                    .testTag("top_bar_hybrid_coach_button")
                                    .padding(horizontal = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Text(
                                    text = "Coach",
                                    color = Color.White,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(if (coachVoiceEnabled) RunColors.brand else Color.DarkGray)
                                )
                            }
                        }
                    }
                }

                // B. Card do treino do dia (com Banner Proativo de Supercompensação integrado)
                item {
                    val warmupBlock = activeSelectedPlan.blocks.firstOrNull { it.type == RunningBlockType.WARMUP }
                    val cooldownBlock = activeSelectedPlan.blocks.lastOrNull { it.type == RunningBlockType.COOL_DOWN }
                    val middleBlocks = activeSelectedPlan.blocks.filter {
                        it.type == RunningBlockType.RUN || it.type == RunningBlockType.WALK
                    }
                    val warmupMins = (warmupBlock?.durationSec ?: 300) / 60
                    val cooldownMins = (cooldownBlock?.durationSec ?: 300) / 60
                    val middleTotalSec = middleBlocks.sumOf { it.durationSec }
                    val middleMinsLabel = RunningProgramManager.formatDurationMin(middleTotalSec)

                    val middleSummaryText = remember(middleBlocks) {
                        RunningProgramManager.formatMiddleBlocksSummary(middleBlocks)
                    }

                    LaunchedEffect(selectedWeek, selectedDay, currentProgramInfo.title, middleSummaryText, runningHistory) {
                        com.example.services.RunningDailyReminderManager.syncWorkoutContextAndSchedule(
                            context = context,
                            weekNumber = selectedWeek,
                            dayNumber = selectedDay,
                            programTitle = currentProgramInfo.title,
                            workoutSummary = middleSummaryText,
                            sessions = runningHistory
                        )
                    }

                    val isDayCompleted = remember(runningHistory, selectedWeek, selectedDay) {
                        runningHistory.any { it.weekNumber == selectedWeek && it.dayNumber == selectedDay && !it.isGentleMode }
                    }

                    if (fatigueAdvisoryState.isTriggered) {
                        com.example.ui.components.SmartFatigueModeSwitcher(
                            advisory = fatigueAdvisoryState,
                            onAcceptRest = {
                                AudioSynth.playTickSound()
                                viewModel.acceptFatigueRestDay()
                            },
                            onOverrideTrain = {
                                AudioSynth.playTickSound()
                                viewModel.overrideFatigueAndTrain()
                            },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                    }

                    var showWorkoutStepByStepExplainer by rememberSaveable { mutableStateOf(false) }
                    val hapticFeedback = LocalHapticFeedback.current

                    if (showWorkoutStepByStepExplainer) {
                        val planForExplainer = selectedFreeRaceMode?.let {
                            RunningProgramManager.getFreeOrRacePlan(it)
                        } ?: activeSelectedPlan
                        WorkoutStepByStepExplainerDialog(
                            plan = planForExplainer,
                            weekNumber = selectedWeek,
                            dayNumber = selectedDay,
                            programTitle = currentProgramInfo.title,
                            freeRaceMode = selectedFreeRaceMode,
                            isTrack400mMode = isTrack400mSelected,
                            onDismiss = {
                                showWorkoutStepByStepExplainer = false
                                AudioSynth.playTickSound()
                            },
                            onStartWorkoutNow = {
                                showWorkoutStepByStepExplainer = false
                                val chosenFreeRace = selectedFreeRaceMode
                                if (chosenFreeRace != null) {
                                    val freePlan = RunningProgramManager.getFreeOrRacePlan(chosenFreeRace)
                                    activePlan = freePlan
                                    selectedDayNumberForSession = 1
                                    isIndoor = false
                                    isGpsEnabled = true
                                    isGentleModeSession = false
                                    selectedEnvironment = "OUTDOOR"
                                    showPreRunCheckInDialog = false
                                    viewModel.setActivePreRunCheckIn(null)
                                    AudioSynth.playTickSound()
                                    triggerWorkoutStartWithAutoGps(
                                        env = "OUTDOOR",
                                        gentle = false,
                                        freeRaceMode = chosenFreeRace,
                                        sessionType = freePlan.sessionType,
                                        plan = freePlan,
                                        programId = selectedProgramId
                                    )
                                } else {
                                    activePlan = activeSelectedPlan
                                    selectedDayNumberForSession = selectedDay
                                    AudioSynth.playTickSound()
                                    val targetEnv = if (isIndoor) "TREADMILL" else "OUTDOOR"
                                    triggerWorkoutStartWithAutoGps(
                                        env = targetEnv,
                                        gentle = isGentleModeSession,
                                        freeRaceMode = null,
                                        sessionType = activeSelectedPlan.sessionType,
                                        plan = activeSelectedPlan,
                                        programId = selectedProgramId
                                    )
                                }
                            },
                            onOpenRecoveryDialog = {
                                showWorkoutStepByStepExplainer = false
                                showRecoverCompletedWorkoutDialog = true
                                AudioSynth.playTickSound()
                            }
                        )
                    }

                    Surface(
                        color = RunColors.surface,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(
                            width = if (fatigueAdvisoryState.isRestDayAccepted || selectedFreeRaceMode != null) 1.5.dp else 1.dp,
                            color = when {
                                fatigueAdvisoryState.isRestDayAccepted -> Color(0xFF10B981).copy(alpha = 0.7f)
                                selectedFreeRaceMode != null -> Color(0xFF00E5FF).copy(alpha = 0.75f)
                                else -> RunColors.border
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .combinedClickable(
                                onClick = {},
                                onLongClick = {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                    AudioSynth.playTickSound()
                                    showWorkoutStepByStepExplainer = true
                                }
                            )
                            .testTag("card_unified_running_session")
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp)
                        ) {
                            // Topo limpo com apenas "Ver etapas ⓘ" centralizado e em tom neutro
                            val activeMode = selectedFreeRaceMode
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Ver etapas ⓘ",
                                    color = RunColors.textSecondary,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            AudioSynth.playTickSound()
                                            showWorkoutStepByStepExplainer = true
                                        }
                                        .padding(horizontal = 10.dp, vertical = 2.dp)
                                        .testTag("btn_open_workout_explainer")
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            if (activeMode == null) {
                                if (isTrack400mSelected) {
                                    // MODO INTERVALADO DE PISTA 400M (Laps por Distância + Descanso com Disparo Manual)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color(0xFF0D0F14), RoundedCornerShape(10.dp))
                                            .padding(vertical = 10.dp, horizontal = 12.dp),
                                        horizontalArrangement = Arrangement.SpaceEvenly,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(
                                                text = "400 m",
                                                color = Color(0xFF00E676),
                                                fontSize = 18.sp,
                                                fontWeight = FontWeight.Black,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                            Text(
                                                text = "Distância da Lap",
                                                color = RunColors.textSecondary,
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        }

                                        VerticalDivider(
                                            modifier = Modifier.height(30.dp),
                                            thickness = 1.dp,
                                            color = RunColors.border
                                        )

                                        Column(
                                            modifier = Modifier.weight(1.25f),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(
                                                text = "2:00 (Manual)",
                                                color = Color(0xFF00E5FF),
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Black,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                            Text(
                                                text = "Descanso / Largada",
                                                color = RunColors.textSecondary,
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        }
                                    }
                                } else {
                                    // 3 Colunas sem corte de texto: Aquecimento | Treino | Desaquec.
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        // Coluna 1: Aquecimento
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(
                                                text = "$warmupMins Min",
                                                color = Color.White,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                            Text(
                                                text = "Aquecimento",
                                                color = RunColors.textSecondary,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium,
                                                textAlign = TextAlign.Center,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        }

                                        VerticalDivider(
                                            modifier = Modifier.height(30.dp),
                                            thickness = 1.dp,
                                            color = RunColors.border
                                        )

                                        // Coluna 2: Treino Principal
                                        Column(
                                            modifier = Modifier
                                                .weight(1.75f)
                                                .padding(horizontal = 4.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(
                                                text = "$middleMinsLabel Min",
                                                color = Color.White,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                            Text(
                                                text = middleSummaryText,
                                                color = RunColors.brandText,
                                                fontSize = 10.5.sp,
                                                lineHeight = 13.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                textAlign = TextAlign.Center,
                                                maxLines = 2
                                            )
                                        }

                                        VerticalDivider(
                                            modifier = Modifier.height(30.dp),
                                            thickness = 1.dp,
                                            color = RunColors.border
                                        )

                                        // Coluna 3: Desaquec. (rótulo enxuto para nunca cortar)
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(
                                                text = "$cooldownMins Min",
                                                color = Color.White.copy(alpha = 0.6f),
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                            Text(
                                                text = "Desaquec.",
                                                color = RunColors.textSecondary.copy(alpha = 0.6f),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium,
                                                textAlign = TextAlign.Center,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    // Barra segmentada de 6dp com pequenos espaços (2dp) entre as fases
                                    val totalPlanDuration = activeSelectedPlan.blocks.sumOf { it.durationSec.toLong() }.toFloat().coerceAtLeast(1f)
                                    androidx.compose.foundation.Canvas(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(6.dp)
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(RunColors.bg)
                                    ) {
                                        val canvasWidth = size.width
                                        val canvasHeight = size.height
                                        var currentX = 0f
                                        val gapPx = 2.dp.toPx()

                                        activeSelectedPlan.blocks.forEach { block ->
                                            val blockColor = when (block.type) {
                                                RunningBlockType.WARMUP -> RunColors.phaseWarmup
                                                RunningBlockType.RUN -> RunColors.phaseRun
                                                RunningBlockType.WALK -> RunColors.phaseWalk
                                                RunningBlockType.COOL_DOWN -> RunColors.phaseCooldown
                                            }
                                            val rawWidth = (block.durationSec.toFloat() / totalPlanDuration) * canvasWidth
                                            val blockWidth = (rawWidth - gapPx).coerceAtLeast(1f)
                                            drawRect(
                                                color = blockColor,
                                                topLeft = androidx.compose.ui.geometry.Offset(x = currentX, y = 0f),
                                                size = androidx.compose.ui.geometry.Size(width = blockWidth, height = canvasHeight)
                                            )
                                            currentX += rawWidth
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    // Navegador de dias com alvos de toque >= 48.dp e fonte >= 14.sp SemiBold
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(48.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .clickable(enabled = selectedWeek > 1 || selectedDay > 1) {
                                                    if (selectedDay > 1) {
                                                        selectedDay--
                                                    } else if (selectedWeek > 1) {
                                                        selectedWeek--
                                                        selectedDay = 3
                                                    }
                                                    viewModel.setManualPlanDayOverride(selectedWeek, selectedDay)
                                                    AudioSynth.playTickSound()
                                                }
                                                .testTag("btn_plan_prev_day"),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "<",
                                                color = if (selectedWeek > 1 || selectedDay > 1) Color.White else Color.DarkGray,
                                                fontSize = 20.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                                        ) {
                                            if (isDayCompleted || fatigueAdvisoryState.isRestDayAccepted) {
                                                Icon(
                                                    imageVector = Icons.Default.CheckCircle,
                                                    contentDescription = "Dia Concluído / Protegido",
                                                    tint = if (fatigueAdvisoryState.isRestDayAccepted) Color(0xFF10B981) else RunColors.phaseWalk,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                            Text(
                                                text = "Semana $selectedWeek · Dia $selectedDay de 3",
                                                color = Color.White.copy(alpha = 0.9f),
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }

                                        Box(
                                            modifier = Modifier
                                                .size(48.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .clickable(enabled = selectedWeek < 10 || selectedDay < 3) {
                                                    if (selectedDay < 3) {
                                                        selectedDay++
                                                    } else if (selectedWeek < 10) {
                                                        selectedWeek++
                                                        selectedDay = 1
                                                    }
                                                    viewModel.setManualPlanDayOverride(selectedWeek, selectedDay)
                                                    AudioSynth.playTickSound()
                                                }
                                                .testTag("btn_plan_next_day"),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = ">",
                                                color = if (selectedWeek < 10 || selectedDay < 3) Color.White else Color.DarkGray,
                                                fontSize = 20.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            } else {
                                // MODO ULTRA-MINIMALISTA: LIVRE | 5K | 10K
                                val targetMeters = activeMode.targetDistanceMeters
                                val refPaceMinPerKm = userProfile.baselinePaceMinPerKm ?: 6.0f
                                val refPaceTotalSec = (refPaceMinPerKm * 60f).roundToInt().coerceAtLeast(180)
                                val refPaceFormatted = String.format(
                                    Locale.US,
                                    "%d'%02d\"/km",
                                    refPaceTotalSec / 60,
                                    refPaceTotalSec % 60
                                )
                                val predictedTotalSec = if (targetMeters != null) {
                                    ((targetMeters / 1000f) * refPaceMinPerKm * 60f).roundToInt()
                                } else null
                                val predictedStr = predictedTotalSec?.let { totalS ->
                                    val h = totalS / 3600
                                    val m = (totalS % 3600) / 60
                                    val s = totalS % 60
                                    if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
                                    else String.format(Locale.US, "%02d:%02d", m, s)
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFF0D0F14), RoundedCornerShape(10.dp))
                                        .padding(vertical = 12.dp, horizontal = 14.dp),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Coluna 1: Distância Alvo ("Livre", "5,00 km" ou "10,00 km")
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = if (targetMeters != null) {
                                                String.format(Locale.US, "%.2f km", targetMeters / 1000f).replace('.', ',')
                                            } else {
                                                "Livre"
                                            },
                                            color = Color.White,
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Black,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                        Text(
                                            text = "Distância Alvo",
                                            color = RunColors.textSecondary,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }

                                    VerticalDivider(
                                        modifier = Modifier.height(32.dp),
                                        thickness = 1.dp,
                                        color = RunColors.border
                                    )

                                    // Coluna 2: Pace de Referência (Livre) ou Projeção Estimada de Tempo (5K / 10K)
                                    Column(
                                        modifier = Modifier.weight(1.25f),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = if (predictedStr != null) "~$predictedStr" else refPaceFormatted,
                                            color = if (predictedStr != null) Color(0xFFFFD600) else Color(0xFF00E676),
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Black,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                        Text(
                                            text = if (predictedStr != null) "Projeção Estimada" else "Pace de Referência",
                                            color = RunColors.brandText,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // C, D, E & F. Chip Resumo + Música, Botão START, Métricas Globais e Atividades Recentes
                item {
                    var showRunConfigDialog by rememberSaveable { mutableStateOf(false) }

                    val totalDistMeters = runningHistory.sumOf { it.distanceMeters }
                    val totalKm = totalDistMeters / 1000f
                    val totalKmDisplay = if (runningHistory.isEmpty()) "0,0" else String.format(Locale.US, "%.1f", totalKm).replace('.', ',')

                    val totalSec = runningHistory.sumOf { it.durationSec }
                    val hrs = totalSec / 3600
                    val mins = (totalSec % 3600) / 60
                    val secs = totalSec % 60
                    val totalTimeDisplay = if (runningHistory.isEmpty()) "00:00:00" else String.format("%02d:%02d:%02d", hrs, mins, secs)

                    val modalitySummaryLabel = when {
                        selectedFreeRaceMode == com.example.engine.FreeRunRaceMode.FREE_RUN -> "Corrida Livre"
                        selectedFreeRaceMode == com.example.engine.FreeRunRaceMode.RACE_5K -> "Meta 5K"
                        selectedFreeRaceMode == com.example.engine.FreeRunRaceMode.RACE_10K -> "Meta 10K"
                        selectedRxWorkout != null -> selectedRxWorkout.code
                        isTrack400mSelected -> "Pista 400m"
                        else -> "Planilha"
                    }
                    val isTrack400mCalibrationOn = runTrackState.isTrack400mCalibrationEnabled || isTrack400mSelected
                    val envSummaryLabel = when {
                        isGentleModeSession -> "Modo Leve"
                        isIndoor || selectedEnvironment == "TREADMILL" -> "Esteira"
                        isTrack400mCalibrationOn -> "Pista 400m"
                        else -> "Ar Livre"
                    }
                    val warmupSuffix = if (includeWarmupWithStrides && selectedFreeRaceMode == null) " • +Aquec." else ""

                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(RunColors.bg),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        val screenWidth = maxWidth
                        val buttonDiameter = (screenWidth * 0.62f).coerceIn(196.dp, 290.dp)

                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // (+) à esquerda do pill Resumo Clicável + Ícone de Música + (-) à direita na mesma linha
                                val currentFontScale = com.example.ui.components.rememberRunUiFontScale()
                                val canIncreaseFont = currentFontScale < 1.34f
                                val canDecreaseFont = currentFontScale > 1.01f

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Botão Circular (+) para aumentar a fonte (mesmo padrão visual do botão de música)
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(CircleShape)
                                            .background(RunColors.surface.copy(alpha = 0.9f))
                                            .border(1.dp, RunColors.border, CircleShape)
                                            .clickable {
                                                AudioSynth.playTickSound()
                                                com.example.ui.components.RunFontScaleManager.stepScale(context, +1)
                                            }
                                            .testTag("btn_run_font_scale_increase"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = "Aumentar fonte",
                                            tint = if (canIncreaseFont) Color.White else Color.White.copy(alpha = 0.35f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    Surface(
                                        onClick = {
                                            showRunConfigDialog = true
                                            AudioSynth.playTickSound()
                                        },
                                        color = RunColors.surface.copy(alpha = 0.95f),
                                        shape = RoundedCornerShape(22.dp),
                                        border = BorderStroke(1.dp, RunColors.brandText.copy(alpha = 0.45f)),
                                        modifier = Modifier
                                            .weight(1f, fill = false)
                                            .heightIn(min = 44.dp)
                                            .testTag("btn_open_run_config_modal")
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                                        ) {
                                            Text(
                                                text = "$modalitySummaryLabel • $envSummaryLabel$warmupSuffix • Alterar",
                                                color = Color.White,
                                                fontSize = 13.5.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )
                                            Icon(
                                                imageVector = Icons.Default.KeyboardArrowDown,
                                                contentDescription = null,
                                                tint = RunColors.brandText,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }

                                    // Botão Circular Compacto de Música / Spotify na mesma linha
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(CircleShape)
                                            .background(RunColors.surface.copy(alpha = 0.9f))
                                            .border(1.dp, RunColors.border, CircleShape)
                                            .clickable {
                                                AudioSynth.playTickSound()
                                                com.example.MainActivity.openMusicOrSpotifyPlayer(context)
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.MusicNote,
                                            contentDescription = "Música de Corrida",
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    // Botão Circular (-) para diminuir a fonte (à direita do ícone da música)
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(CircleShape)
                                            .background(RunColors.surface.copy(alpha = 0.9f))
                                            .border(1.dp, RunColors.border, CircleShape)
                                            .clickable {
                                                AudioSynth.playTickSound()
                                                com.example.ui.components.RunFontScaleManager.stepScale(context, -1)
                                            }
                                            .testTag("btn_run_font_scale_decrease"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Remove,
                                            contentDescription = "Diminuir fonte",
                                            tint = if (canDecreaseFont) Color.White else Color.White.copy(alpha = 0.35f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                // Toggle Rápido "Pista 400m (TAF)" na tela Início (calibragem anti-overshoot de curva + aferição de volta 400m)
                                Surface(
                                    onClick = {
                                        AudioSynth.playTickSound()
                                        val nextTrackMode = !isTrack400mCalibrationOn
                                        RunTracker.setTrack400mCalibrationMode(nextTrackMode)
                                        if (nextTrackMode) {
                                            isIndoor = false
                                            isGpsEnabled = true
                                            selectedEnvironment = "OUTDOOR"
                                        }
                                    },
                                    color = if (isTrack400mCalibrationOn) Color(0xFF00E676).copy(alpha = 0.16f) else Color(0xFF151922),
                                    shape = RoundedCornerShape(18.dp),
                                    border = BorderStroke(
                                        width = if (isTrack400mCalibrationOn) 1.4.dp else 1.dp,
                                        color = if (isTrack400mCalibrationOn) Color(0xFF00E676) else Color(0xFF262D3D)
                                    ),
                                    modifier = Modifier
                                        .heightIn(min = 34.dp)
                                        .testTag("quick_toggle_pista_400m_taf")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Flag,
                                            contentDescription = null,
                                            tint = if (isTrack400mCalibrationOn) Color(0xFF00E676) else RunColors.textSecondary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = if (isTrack400mCalibrationOn) {
                                                "Pista 400m (TAF) • Ativo"
                                            } else {
                                                "Pista 400m (TAF)"
                                            },
                                            color = if (isTrack400mCalibrationOn) Color(0xFF00E676) else Color(0xFFB0B8C4),
                                            fontSize = 12.sp,
                                            fontWeight = if (isTrack400mCalibrationOn) FontWeight.Black else FontWeight.SemiBold,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }

                                if (showRunConfigDialog) {
                                    androidx.compose.ui.window.Dialog(
                                        onDismissRequest = { showRunConfigDialog = false },
                                        properties = androidx.compose.ui.window.DialogProperties(
                                            usePlatformDefaultWidth = false
                                        )
                                    ) {
                                        com.example.ui.components.ProvideRunFontScale {
                                            Surface(
                                                shape = RoundedCornerShape(22.dp),
                                                color = Color(0xFF0D0F14),
                                                border = BorderStroke(1.dp, Color(0xFF262D3D)),
                                                modifier = Modifier
                                                    .fillMaxWidth(0.94f)
                                                    .heightIn(max = 680.dp)
                                                    .testTag("dialog_run_config")
                                            ) {
                                                Column(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .verticalScroll(rememberScrollState())
                                                        .padding(18.dp),
                                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                                ) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(
                                                            text = "Configuração da Corrida",
                                                            color = Color.White,
                                                            fontSize = 18.sp,
                                                            fontWeight = FontWeight.Black,
                                                            modifier = Modifier.weight(1f)
                                                        )
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        com.example.ui.components.RunFontScaleToggleButton(compact = false)
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        IconButton(
                                                            onClick = { showRunConfigDialog = false },
                                                            modifier = Modifier.size(40.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.Close,
                                                                contentDescription = "Fechar",
                                                                tint = RunColors.textSecondary,
                                                                modifier = Modifier.size(20.dp)
                                                            )
                                                        }
                                                    }

                                                    // 1. Modalidade do Treino (FlowRow adaptativo com alvos >= 48.dp e fonte >= 14.sp SemiBold)
                                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                                        Text(
                                                            text = "1. MODALIDADE DO TREINO",
                                                            color = RunColors.textSecondary,
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            letterSpacing = 0.6.sp
                                                        )

                                                        val isPlanSelected = selectedFreeRaceMode == null && !isTrack400mSelected && selectedRxWorkout == null
                                                        val isFreeSelected = selectedFreeRaceMode == com.example.engine.FreeRunRaceMode.FREE_RUN
                                                        val is5kSelected = selectedFreeRaceMode == com.example.engine.FreeRunRaceMode.RACE_5K
                                                        val is10kSelected = selectedFreeRaceMode == com.example.engine.FreeRunRaceMode.RACE_10K
                                                        val isTrackSelected = selectedFreeRaceMode == null && isTrack400mSelected

                                                        FlowRow(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                                        ) {
                                                            // Planilha
                                                            Surface(
                                                                onClick = {
                                                                    selectedFreeRaceMode = null
                                                                    isTrack400mSelected = false
                                                                    selectedRxWorkoutCode = null
                                                                    AudioSynth.playTickSound()
                                                                },
                                                                color = if (isPlanSelected) RunColors.brand.copy(alpha = 0.24f) else Color(0xFF151922),
                                                                shape = RoundedCornerShape(12.dp),
                                                                border = BorderStroke(
                                                                    width = if (isPlanSelected) 1.5.dp else 1.dp,
                                                                    color = if (isPlanSelected) RunColors.brandText else Color(0xFF232A38)
                                                                ),
                                                                modifier = Modifier
                                                                    .heightIn(min = 48.dp)
                                                                    .testTag("pill_mode_planilha")
                                                            ) {
                                                                Box(
                                                                    contentAlignment = Alignment.Center,
                                                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                                                                ) {
                                                                    Text(
                                                                        text = "Planilha",
                                                                        color = if (isPlanSelected) Color.White else Color(0xFFB0B8C4),
                                                                        fontSize = 14.sp,
                                                                        fontWeight = if (isPlanSelected) FontWeight.Black else FontWeight.SemiBold
                                                                    )
                                                                }
                                                            }

                                                            // Corrida Livre
                                                            Surface(
                                                                onClick = {
                                                                    selectedFreeRaceMode = com.example.engine.FreeRunRaceMode.FREE_RUN
                                                                    isTrack400mSelected = false
                                                                    selectedRxWorkoutCode = null
                                                                    isIndoor = false
                                                                    isGpsEnabled = true
                                                                    isGentleModeSession = false
                                                                    selectedEnvironment = "OUTDOOR"
                                                                    activePlan = RunningProgramManager.getFreeOrRacePlan(com.example.engine.FreeRunRaceMode.FREE_RUN)
                                                                    showPreRunCheckInDialog = false
                                                                    viewModel.setActivePreRunCheckIn(null)
                                                                    showRunConfigDialog = false
                                                                    AudioSynth.playTickSound()
                                                                },
                                                                color = if (isFreeSelected) Color(0xFF00E676).copy(alpha = 0.20f) else Color(0xFF151922),
                                                                shape = RoundedCornerShape(12.dp),
                                                                border = BorderStroke(
                                                                    width = if (isFreeSelected) 1.5.dp else 1.dp,
                                                                    color = if (isFreeSelected) Color(0xFF00E676) else Color(0xFF232A38)
                                                                ),
                                                                modifier = Modifier
                                                                    .heightIn(min = 48.dp)
                                                                    .testTag("pill_mode_free_run")
                                                            ) {
                                                                Box(
                                                                    contentAlignment = Alignment.Center,
                                                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                                                                ) {
                                                                    Text(
                                                                        text = "Corrida Livre",
                                                                        color = if (isFreeSelected) Color.White else Color(0xFFB0B8C4),
                                                                        fontSize = 14.sp,
                                                                        fontWeight = if (isFreeSelected) FontWeight.Black else FontWeight.SemiBold
                                                                    )
                                                                }
                                                            }

                                                            // Meta 5K
                                                            Surface(
                                                                onClick = {
                                                                    selectedFreeRaceMode = com.example.engine.FreeRunRaceMode.RACE_5K
                                                                    isTrack400mSelected = false
                                                                    selectedRxWorkoutCode = null
                                                                    isIndoor = false
                                                                    isGpsEnabled = true
                                                                    isGentleModeSession = false
                                                                    selectedEnvironment = "OUTDOOR"
                                                                    activePlan = RunningProgramManager.getFreeOrRacePlan(com.example.engine.FreeRunRaceMode.RACE_5K)
                                                                    showPreRunCheckInDialog = false
                                                                    viewModel.setActivePreRunCheckIn(null)
                                                                    showRunConfigDialog = false
                                                                    AudioSynth.playTickSound()
                                                                },
                                                                color = if (is5kSelected) Color(0xFF00B0FF).copy(alpha = 0.24f) else Color(0xFF151922),
                                                                shape = RoundedCornerShape(12.dp),
                                                                border = BorderStroke(
                                                                    width = if (is5kSelected) 1.5.dp else 1.dp,
                                                                    color = if (is5kSelected) Color(0xFF00E5FF) else Color(0xFF232A38)
                                                                ),
                                                                modifier = Modifier
                                                                    .heightIn(min = 48.dp)
                                                                    .testTag("pill_mode_race_5k")
                                                            ) {
                                                                Box(
                                                                    contentAlignment = Alignment.Center,
                                                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                                                                ) {
                                                                    Text(
                                                                        text = "Meta 5K",
                                                                        color = if (is5kSelected) Color.White else Color(0xFFB0B8C4),
                                                                        fontSize = 14.sp,
                                                                        fontWeight = if (is5kSelected) FontWeight.Black else FontWeight.SemiBold
                                                                    )
                                                                }
                                                            }

                                                            // Meta 10K
                                                            Surface(
                                                                onClick = {
                                                                    selectedFreeRaceMode = com.example.engine.FreeRunRaceMode.RACE_10K
                                                                    isTrack400mSelected = false
                                                                    selectedRxWorkoutCode = null
                                                                    isIndoor = false
                                                                    isGpsEnabled = true
                                                                    isGentleModeSession = false
                                                                    selectedEnvironment = "OUTDOOR"
                                                                    activePlan = RunningProgramManager.getFreeOrRacePlan(com.example.engine.FreeRunRaceMode.RACE_10K)
                                                                    showPreRunCheckInDialog = false
                                                                    viewModel.setActivePreRunCheckIn(null)
                                                                    showRunConfigDialog = false
                                                                    AudioSynth.playTickSound()
                                                                },
                                                                color = if (is10kSelected) Color(0xFF00B0FF).copy(alpha = 0.24f) else Color(0xFF151922),
                                                                shape = RoundedCornerShape(12.dp),
                                                                border = BorderStroke(
                                                                    width = if (is10kSelected) 1.5.dp else 1.dp,
                                                                    color = if (is10kSelected) Color(0xFF00E5FF) else Color(0xFF232A38)
                                                                ),
                                                                modifier = Modifier
                                                                    .heightIn(min = 48.dp)
                                                                    .testTag("pill_mode_race_10k")
                                                            ) {
                                                                Box(
                                                                    contentAlignment = Alignment.Center,
                                                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                                                                ) {
                                                                    Text(
                                                                        text = "Meta 10K",
                                                                        color = if (is10kSelected) Color.White else Color(0xFFB0B8C4),
                                                                        fontSize = 14.sp,
                                                                        fontWeight = if (is10kSelected) FontWeight.Black else FontWeight.SemiBold
                                                                    )
                                                                }
                                                            }

                                                            // Pista 400m
                                                            Surface(
                                                                onClick = {
                                                                    selectedFreeRaceMode = null
                                                                    isTrack400mSelected = true
                                                                    RunTracker.setTrack400mCalibrationMode(true)
                                                                    selectedRxWorkoutCode = null
                                                                    isIndoor = false
                                                                    isGpsEnabled = true
                                                                    isGentleModeSession = false
                                                                    selectedEnvironment = "OUTDOOR"
                                                                    AudioSynth.playTickSound()
                                                                },
                                                                color = if (isTrackSelected) Color(0xFF00E676).copy(alpha = 0.22f) else Color(0xFF151922),
                                                                shape = RoundedCornerShape(12.dp),
                                                                border = BorderStroke(
                                                                    width = if (isTrackSelected) 1.5.dp else 1.dp,
                                                                    color = if (isTrackSelected) Color(0xFF00E676) else Color(0xFF232A38)
                                                                ),
                                                                modifier = Modifier
                                                                    .heightIn(min = 48.dp)
                                                                    .testTag("toggle_track_400m_mode")
                                                            ) {
                                                                Box(
                                                                    contentAlignment = Alignment.Center,
                                                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                                                                ) {
                                                                    Text(
                                                                        text = "Pista 400m",
                                                                        color = if (isTrackSelected) Color.White else Color(0xFFB0B8C4),
                                                                        fontSize = 14.sp,
                                                                        fontWeight = if (isTrackSelected) FontWeight.Black else FontWeight.SemiBold
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }

                                                    // 1A-2. Catálogo RX-PRO & Run to Maintain + Recalibração de Anamnese dentro do modal "Alterar ▾"
                                                    com.example.ui.components.RunConfigModalRxAndAnamnesisSection(
                                                        selectedRxWorkout = selectedRxWorkout,
                                                        isRunToMaintainActive = selectedProgramId == "run_to_maintain" || anamnesisProfile.goal == com.example.ui.components.RunnerGoalOption.GOAL_MAINTAIN,
                                                        calibration = runnerCalibration,
                                                        onOpenRxLibraryModal = {
                                                            showRunConfigDialog = false
                                                            showRxWorkoutLibraryModal = true
                                                        },
                                                        onClearRxWorkout = {
                                                            selectedRxWorkoutCode = null
                                                        },
                                                        onActivateRunToMaintain = {
                                                            selectedFreeRaceMode = null
                                                            isTrack400mSelected = false
                                                            selectedRxWorkoutCode = null
                                                            viewModel.selectRunningProgram("run_to_maintain")
                                                            val updated = anamnesisProfile.copy(
                                                                goal = com.example.ui.components.RunnerGoalOption.GOAL_MAINTAIN,
                                                                availableDaysPerWeek = 3,
                                                                isConfigured = true
                                                            )
                                                            com.example.ui.components.RunnerAnamnesisManager.saveProfile(context, updated)
                                                            anamnesisVersion++
                                                            Toast.makeText(
                                                                context,
                                                                "Modo Run to Maintain ativado (3x/semana integrado à Musculação)",
                                                                Toast.LENGTH_SHORT
                                                            ).show()
                                                        },
                                                        onOpenAnamnesisModal = {
                                                            showRunConfigDialog = false
                                                            showAnamnesisModal = true
                                                        }
                                                    )

                                                    // 1B. Seletor de Semana (1..10) e Dia (1..3) quando em modo Planilha
                                                    if (selectedFreeRaceMode == null && !isTrack400mSelected && selectedRxWorkout == null) {
                                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                            Text(
                                                                text = "SEMANA E DIA DA PLANILHA",
                                                                color = RunColors.textSecondary,
                                                                fontSize = 12.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                letterSpacing = 0.6.sp
                                                            )
                                                            FlowRow(
                                                                modifier = Modifier.fillMaxWidth(),
                                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                                            ) {
                                                                (1..10).forEach { w ->
                                                                    val isSelected = selectedWeek == w
                                                                    Surface(
                                                                        onClick = {
                                                                            selectedWeek = w
                                                                            AudioSynth.playTickSound()
                                                                        },
                                                                        color = if (isSelected) RunColors.brand.copy(alpha = 0.25f) else Color(0xFF151922),
                                                                        shape = RoundedCornerShape(10.dp),
                                                                        border = BorderStroke(
                                                                            width = if (isSelected) 1.5.dp else 1.dp,
                                                                            color = if (isSelected) RunColors.brandText else Color(0xFF232A38)
                                                                        ),
                                                                        modifier = Modifier.heightIn(min = 46.dp)
                                                                    ) {
                                                                        Box(
                                                                            contentAlignment = Alignment.Center,
                                                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                                                        ) {
                                                                            Text(
                                                                                text = "Sem $w",
                                                                                color = if (isSelected) Color.White else Color(0xFFB0B8C4),
                                                                                fontSize = 14.sp,
                                                                                fontWeight = if (isSelected) FontWeight.Black else FontWeight.SemiBold
                                                                            )
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                            FlowRow(
                                                                modifier = Modifier.fillMaxWidth(),
                                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                                            ) {
                                                                (1..3).forEach { d ->
                                                                    val isSelected = selectedDay == d
                                                                    Surface(
                                                                        onClick = {
                                                                            selectedDay = d
                                                                            AudioSynth.playTickSound()
                                                                        },
                                                                        color = if (isSelected) RunColors.brand.copy(alpha = 0.25f) else Color(0xFF151922),
                                                                        shape = RoundedCornerShape(10.dp),
                                                                        border = BorderStroke(
                                                                            width = if (isSelected) 1.5.dp else 1.dp,
                                                                            color = if (isSelected) RunColors.brandText else Color(0xFF232A38)
                                                                        ),
                                                                        modifier = Modifier.heightIn(min = 46.dp)
                                                                    ) {
                                                                        Box(
                                                                            contentAlignment = Alignment.Center,
                                                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                                                        ) {
                                                                            Text(
                                                                                text = "Dia $d",
                                                                                color = if (isSelected) Color.White else Color(0xFFB0B8C4),
                                                                                fontSize = 14.sp,
                                                                                fontWeight = if (isSelected) FontWeight.Black else FontWeight.SemiBold
                                                                            )
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }

                                                    // 2. Ambiente e Intensidade (FlowRow adaptativo com alvos >= 48.dp e fonte >= 14.sp SemiBold)
                                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                                        Text(
                                                            text = "2. AMBIENTE E INTENSIDADE",
                                                            color = RunColors.textSecondary,
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            letterSpacing = 0.6.sp
                                                        )

                                                        val isOutdoorSelected = !isGentleModeSession && !isIndoor && selectedEnvironment == "OUTDOOR" && !isTrack400mCalibrationOn
                                                        val isTrack400mEnvSelected = !isGentleModeSession && !isIndoor && selectedEnvironment == "OUTDOOR" && isTrack400mCalibrationOn
                                                        val isTreadmillSelected = !isGentleModeSession && (isIndoor || selectedEnvironment == "TREADMILL")

                                                        FlowRow(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                                        ) {
                                                            // Ar Livre (GPS)
                                                            Surface(
                                                                onClick = {
                                                                    isIndoor = false
                                                                    isGpsEnabled = true
                                                                    isGentleModeSession = false
                                                                    selectedEnvironment = "OUTDOOR"
                                                                    RunTracker.setTrack400mCalibrationMode(false)
                                                                    AudioSynth.playTickSound()
                                                                },
                                                                color = if (isOutdoorSelected) RunColors.brand.copy(alpha = 0.24f) else Color(0xFF151922),
                                                                shape = RoundedCornerShape(12.dp),
                                                                border = BorderStroke(
                                                                    width = if (isOutdoorSelected) 1.5.dp else 1.dp,
                                                                    color = if (isOutdoorSelected) RunColors.brandText else Color(0xFF232A38)
                                                                ),
                                                                modifier = Modifier.heightIn(min = 48.dp)
                                                            ) {
                                                                Box(
                                                                    contentAlignment = Alignment.Center,
                                                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                                                                ) {
                                                                    Text(
                                                                        text = "Ar Livre (GPS)",
                                                                        color = if (isOutdoorSelected) Color.White else Color(0xFFB0B8C4),
                                                                        fontSize = 14.sp,
                                                                        fontWeight = if (isOutdoorSelected) FontWeight.Bold else FontWeight.SemiBold
                                                                    )
                                                                }
                                                            }

                                                            // Pista 400m (TAF)
                                                            Surface(
                                                                onClick = {
                                                                    isIndoor = false
                                                                    isGpsEnabled = true
                                                                    isGentleModeSession = false
                                                                    selectedEnvironment = "OUTDOOR"
                                                                    RunTracker.setTrack400mCalibrationMode(true)
                                                                    AudioSynth.playTickSound()
                                                                },
                                                                color = if (isTrack400mEnvSelected) Color(0xFF00E676).copy(alpha = 0.22f) else Color(0xFF151922),
                                                                shape = RoundedCornerShape(12.dp),
                                                                border = BorderStroke(
                                                                    width = if (isTrack400mEnvSelected) 1.5.dp else 1.dp,
                                                                    color = if (isTrack400mEnvSelected) Color(0xFF00E676) else Color(0xFF232A38)
                                                                ),
                                                                modifier = Modifier
                                                                    .heightIn(min = 48.dp)
                                                                    .testTag("modal_toggle_pista_400m_taf")
                                                            ) {
                                                                Box(
                                                                    contentAlignment = Alignment.Center,
                                                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                                                                ) {
                                                                    Text(
                                                                        text = "Pista 400m (TAF)",
                                                                        color = if (isTrack400mEnvSelected) Color.White else Color(0xFFB0B8C4),
                                                                        fontSize = 14.sp,
                                                                        fontWeight = if (isTrack400mEnvSelected) FontWeight.Black else FontWeight.SemiBold
                                                                    )
                                                                }
                                                            }

                                                            // Esteira (Indoor)
                                                            Surface(
                                                                onClick = {
                                                                    isIndoor = true
                                                                    isGpsEnabled = false
                                                                    isGentleModeSession = false
                                                                    selectedEnvironment = "TREADMILL"
                                                                    RunTracker.setTrack400mCalibrationMode(false)
                                                                    AudioSynth.playTickSound()
                                                                },
                                                                color = if (isTreadmillSelected) RunColors.brand.copy(alpha = 0.24f) else Color(0xFF151922),
                                                                shape = RoundedCornerShape(12.dp),
                                                                border = BorderStroke(
                                                                    width = if (isTreadmillSelected) 1.5.dp else 1.dp,
                                                                    color = if (isTreadmillSelected) RunColors.brandText else Color(0xFF232A38)
                                                                ),
                                                                modifier = Modifier.heightIn(min = 48.dp)
                                                            ) {
                                                                Box(
                                                                    contentAlignment = Alignment.Center,
                                                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                                                                ) {
                                                                    Text(
                                                                        text = "Esteira (Indoor)",
                                                                        color = if (isTreadmillSelected) Color.White else Color(0xFFB0B8C4),
                                                                        fontSize = 14.sp,
                                                                        fontWeight = if (isTreadmillSelected) FontWeight.Bold else FontWeight.SemiBold
                                                                    )
                                                                }
                                                            }

                                                            // Modo Leve (Recuperação)
                                                            Surface(
                                                                onClick = {
                                                                    isGentleModeSession = true
                                                                    isIndoor = false
                                                                    isGpsEnabled = true
                                                                    selectedEnvironment = "OUTDOOR"
                                                                    AudioSynth.playTickSound()
                                                                },
                                                                color = if (isGentleModeSession) RunColors.phaseWalk.copy(alpha = 0.22f) else Color(0xFF151922),
                                                                shape = RoundedCornerShape(12.dp),
                                                                border = BorderStroke(
                                                                    width = if (isGentleModeSession) 1.5.dp else 1.dp,
                                                                    color = if (isGentleModeSession) RunColors.phaseWalk else Color(0xFF232A38)
                                                                ),
                                                                modifier = Modifier.heightIn(min = 48.dp)
                                                            ) {
                                                                Box(
                                                                    contentAlignment = Alignment.Center,
                                                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                                                                ) {
                                                                    Text(
                                                                        text = "Modo Leve (Recuperação)",
                                                                        color = if (isGentleModeSession) RunColors.phaseWalk else Color(0xFFB0B8C4),
                                                                        fontSize = 14.sp,
                                                                        fontWeight = if (isGentleModeSession) FontWeight.Bold else FontWeight.SemiBold
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }

                                                    // 3. Preparação
                                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                        Text(
                                                            text = "3. PREPARAÇÃO",
                                                            color = RunColors.textSecondary,
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            letterSpacing = 0.6.sp
                                                        )
                                                        Row(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .heightIn(min = 50.dp)
                                                                .clip(RoundedCornerShape(12.dp))
                                                                .background(Color(0xFF151922))
                                                                .clickable {
                                                                    includeWarmupWithStrides = !includeWarmupWithStrides
                                                                    AudioSynth.playTickSound()
                                                                }
                                                                .padding(horizontal = 14.dp, vertical = 8.dp)
                                                                .testTag("toggle_warmup_4x20s_strides"),
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Text(
                                                                text = "Incluir Aquecimento + educativos (5 min)",
                                                                color = if (includeWarmupWithStrides) Color.White else RunColors.textSecondary,
                                                                fontSize = 14.sp,
                                                                fontWeight = FontWeight.SemiBold,
                                                                maxLines = 2,
                                                                overflow = TextOverflow.Ellipsis,
                                                                modifier = Modifier.weight(1f)
                                                            )
                                                            Spacer(modifier = Modifier.width(8.dp))
                                                            Switch(
                                                                checked = includeWarmupWithStrides,
                                                                onCheckedChange = {
                                                                    includeWarmupWithStrides = it
                                                                    AudioSynth.playTickSound()
                                                                },
                                                                colors = SwitchDefaults.colors(
                                                                    checkedThumbColor = Color.White,
                                                                    checkedTrackColor = RunColors.brand,
                                                                    uncheckedThumbColor = Color.Gray,
                                                                    uncheckedTrackColor = Color(0xFF1F2430)
                                                                )
                                                            )
                                                        }
                                                    }

                                                    Button(
                                                        onClick = {
                                                            showRunConfigDialog = false
                                                            AudioSynth.playTickSound()
                                                        },
                                                        shape = RoundedCornerShape(14.dp),
                                                        colors = ButtonDefaults.buttonColors(
                                                            containerColor = RunColors.brand,
                                                            contentColor = Color.White
                                                        ),
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .heightIn(min = 50.dp)
                                                            .testTag("btn_close_run_config_modal")
                                                    ) {
                                                        Text(
                                                            text = "Concluir",
                                                            fontSize = 15.sp,
                                                            fontWeight = FontWeight.Black
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // C. Botão START + Glow com 2 Anéis Chapados Estáticos (AUTO-START GPS IMEDIATO)
                                Box(
                                    modifier = Modifier.size(buttonDiameter + 32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    // Anel Glow 1 (Externo)
                                    Box(
                                        modifier = Modifier
                                            .size(buttonDiameter + 32.dp)
                                            .clip(CircleShape)
                                            .background(RunColors.brand.copy(alpha = 0.12f))
                                    )

                                    // Anel Glow 2 (Interno)
                                    Box(
                                        modifier = Modifier
                                            .size(buttonDiameter + 16.dp)
                                            .clip(CircleShape)
                                            .background(RunColors.brand.copy(alpha = 0.22f))
                                    )

                                    // Botão Círculo Principal START (Zero Cliques Extras: Inicia direto com GPS Automático em Ar Livre!)
                                    Box(
                                        modifier = Modifier
                                            .size(buttonDiameter)
                                            .clip(CircleShape)
                                            .background(
                                                Brush.verticalGradient(
                                                    colors = listOf(Color(0xFF3B82F6), RunColors.brand)
                                                )
                                            )
                                            .border(2.dp, Color.White.copy(alpha = 0.3f), CircleShape)
                                            .clickable {
                                                val chosenFreeRace = selectedFreeRaceMode
                                                if (chosenFreeRace != null) {
                                                    val freePlan = RunningProgramManager.getFreeOrRacePlan(chosenFreeRace)
                                                    activePlan = freePlan
                                                    selectedDayNumberForSession = 1
                                                    isIndoor = false
                                                    isGpsEnabled = true
                                                    isGentleModeSession = false
                                                    selectedEnvironment = "OUTDOOR"
                                                    showPreRunCheckInDialog = false
                                                    viewModel.setActivePreRunCheckIn(null)
                                                    AudioSynth.playTickSound()
                                                    triggerWorkoutStartWithAutoGps(
                                                        env = "OUTDOOR",
                                                        gentle = false,
                                                        freeRaceMode = chosenFreeRace,
                                                        sessionType = freePlan.sessionType,
                                                        plan = freePlan,
                                                        programId = selectedProgramId
                                                    )
                                                } else {
                                                    activePlan = activeSelectedPlan
                                                    selectedDayNumberForSession = selectedDay
                                                    AudioSynth.playTickSound()
                                                    val targetEnv = if (isIndoor) "TREADMILL" else "OUTDOOR"
                                                    triggerWorkoutStartWithAutoGps(
                                                        env = targetEnv,
                                                        gentle = isGentleModeSession,
                                                        freeRaceMode = null,
                                                        sessionType = activeSelectedPlan.sessionType,
                                                        plan = activeSelectedPlan,
                                                        programId = selectedProgramId
                                                    )
                                                }
                                            }
                                            .testTag("btn_iniciar_corrida"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Text(
                                                text = "START",
                                                color = START_TEXT_COLOR,
                                                fontSize = 36.sp,
                                                lineHeight = 36.sp,
                                                fontFamily = condensedFontFamily,
                                                fontWeight = FontWeight.Medium,
                                                fontStyle = FontStyle.Italic,
                                                letterSpacing = 1.5.sp,
                                                style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum")
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = when {
                                                    selectedFreeRaceMode != null -> selectedFreeRaceMode!!.startBadgeLabel
                                                    selectedRxWorkout != null -> selectedRxWorkout.code.take(10)
                                                    isTrack400mSelected -> "400M"
                                                    else -> "W$selectedWeek D$selectedDay"
                                                },
                                                color = START_TEXT_COLOR,
                                                fontSize = when {
                                                    selectedRxWorkout != null -> 22.sp
                                                    selectedFreeRaceMode != null || isTrack400mSelected -> 28.sp
                                                    else -> 36.sp
                                                },
                                                lineHeight = 34.sp,
                                                fontFamily = condensedFontFamily,
                                                fontWeight = FontWeight.Medium,
                                                fontStyle = FontStyle.Italic,
                                                letterSpacing = 1.5.sp,
                                                maxLines = 1,
                                                softWrap = false,
                                                style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum")
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(18.dp))

                                // D. Métricas Globais (2 colunas: Total km | Tempo total — sem cortar o último dígito de HH:MM:SS)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Total km
                                    Column(
                                        modifier = Modifier.weight(0.85f),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = "TOTAL KM",
                                            color = RunColors.textSecondary,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            letterSpacing = 0.5.sp,
                                            textAlign = TextAlign.Center,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = totalKmDisplay,
                                            color = Color.White,
                                            fontSize = 30.sp,
                                            lineHeight = 32.sp,
                                            fontFamily = condensedFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            style = androidx.compose.ui.text.TextStyle(
                                                fontFeatureSettings = "tnum"
                                            ),
                                            textAlign = TextAlign.Center,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }

                                    // Tempo total (8 caracteres HH:MM:SS completos sem corte)
                                    Column(
                                        modifier = Modifier.weight(1.15f),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = "TEMPO TOTAL",
                                            color = RunColors.textSecondary,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            letterSpacing = 0.5.sp,
                                            textAlign = TextAlign.Center,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = totalTimeDisplay,
                                            color = Color.White,
                                            fontSize = 30.sp,
                                            lineHeight = 32.sp,
                                            fontFamily = condensedFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            style = androidx.compose.ui.text.TextStyle(
                                                fontFeatureSettings = "tnum"
                                            ),
                                            textAlign = TextAlign.Center,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(18.dp))

                                // E. Atividades recentes (Tela de Início limpa, sem Provas & Evolução)
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Atividades recentes",
                                            color = Color.White,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(end = 8.dp)
                                        )

                                        Surface(
                                            onClick = {
                                                AudioSynth.playTickSound()
                                                showRecoverCompletedWorkoutDialog = true
                                            },
                                            color = Color(0xFF151922),
                                            shape = RoundedCornerShape(8.dp),
                                            border = BorderStroke(1.dp, RunColors.brandText.copy(alpha = 0.40f)),
                                            modifier = Modifier.testTag("btn_open_recover_workout_dialog")
                                        ) {
                                            Text(
                                                text = "+ Registrar treino",
                                                color = RunColors.brandText,
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                softWrap = false,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                            )
                                        }
                                    }

                                    if (runningHistory.isNotEmpty()) {
                                        Column(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            runningHistory.take(5).forEach { session ->
                                                val dateFormatted = remember(session.date) {
                                                    SimpleDateFormat("dd.MM.yy", Locale.getDefault()).format(Date(session.date))
                                                }
                                                val titleLine = remember(session) {
                                                    RunningProgramManager.getDisplayTitleForSession(session)
                                                }
                                                val badgeLabel = remember(session) {
                                                    RunningProgramManager.getBadgeLabelForSession(session)
                                                }
                                                val badgeAccentColor = when (badgeLabel) {
                                                    "LIVRE" -> Color(0xFF00E5FF)
                                                    "PISTA" -> Color(0xFF00E676)
                                                    "LEVE" -> RunColors.phaseWalk
                                                    else -> RunColors.brandText
                                                }

                                                val distKm = session.distanceMeters / 1000f
                                                val kmText = String.format(Locale.US, "%.2f km", distKm).replace('.', ',')
                                                val hours = session.durationSec / 3600
                                                val minutes = (session.durationSec % 3600) / 60
                                                val seconds = session.durationSec % 60
                                                val durText = if (hours > 0) {
                                                    String.format("%02d:%02d:%02d", hours, minutes, seconds)
                                                } else {
                                                    String.format("%02d:%02d", minutes, seconds)
                                                }
                                                val paceText = if (session.avgPaceSecPerKm > 0) {
                                                    val pMin = session.avgPaceSecPerKm / 60
                                                    val pSec = session.avgPaceSecPerKm % 60
                                                    String.format("%d:%02d/km", pMin, pSec)
                                                } else {
                                                    "--:--"
                                                }

                                                Surface(
                                                    color = RunColors.surface,
                                                    shape = RoundedCornerShape(12.dp),
                                                    border = BorderStroke(1.dp, RunColors.border),
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable {
                                                            viewModel.selectedDetailSession.value = session
                                                            viewModel.selectedShareSession.value = session
                                                            AudioSynth.playTickSound()
                                                            viewModel.navigateToScreen(AppScreen.RUNNING_DETAIL)
                                                        }
                                                ) {
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(horizontal = 14.dp, vertical = 12.dp),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Row(
                                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            modifier = Modifier.weight(1f)
                                                        ) {
                                                            // Badge Inteligente (LIVRE / PISTA / LEVE / 5K / 10K)
                                                            Box(
                                                                modifier = Modifier
                                                                    .clip(RoundedCornerShape(6.dp))
                                                                    .background(badgeAccentColor.copy(alpha = 0.15f))
                                                                    .border(1.dp, badgeAccentColor.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
                                                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                                            ) {
                                                                Text(
                                                                    text = badgeLabel,
                                                                    color = badgeAccentColor,
                                                                    fontSize = 11.sp,
                                                                    fontWeight = FontWeight.Bold,
                                                                    maxLines = 1,
                                                                    softWrap = false
                                                                )
                                                            }

                                                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                                Text(
                                                                    text = titleLine,
                                                                    color = Color.White,
                                                                    fontSize = 14.sp,
                                                                    fontWeight = FontWeight.Bold,
                                                                    maxLines = 1,
                                                                    overflow = TextOverflow.Ellipsis
                                                                )
                                                                Text(
                                                                    text = "$kmText · $durText · $paceText · $dateFormatted",
                                                                    color = RunColors.textSecondary,
                                                                    fontSize = 12.sp,
                                                                    maxLines = 1,
                                                                    overflow = TextOverflow.Ellipsis
                                                                )
                                                            }
                                                        }

                                                        val isAlreadyOfficial = officialRaces.any { it.linkedRunSessionId == session.id }
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                        ) {
                                                            if (isAlreadyOfficial) {
                                                                Surface(
                                                                    color = Color(0xFF00E676).copy(alpha = 0.16f),
                                                                    shape = RoundedCornerShape(8.dp),
                                                                    border = BorderStroke(1.dp, Color(0xFF00E676).copy(alpha = 0.6f))
                                                                ) {
                                                                    Row(
                                                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                                                        verticalAlignment = Alignment.CenterVertically,
                                                                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                                                                    ) {
                                                                        Icon(
                                                                            imageVector = Icons.Default.EmojiEvents,
                                                                            contentDescription = null,
                                                                            tint = Color(0xFF00E676),
                                                                            modifier = Modifier.size(11.dp)
                                                                        )
                                                                        Text(
                                                                            text = "PROVA",
                                                                            color = Color(0xFF00E676),
                                                                            fontSize = 9.5.sp,
                                                                            fontWeight = FontWeight.Black,
                                                                            maxLines = 1,
                                                                            softWrap = false
                                                                        )
                                                                    }
                                                                }
                                                            }
                                                            IconButton(
                                                                onClick = {
                                                                    AudioSynth.playTickSound()
                                                                    recentSessionToDelete = session
                                                                },
                                                                modifier = Modifier
                                                                    .size(32.dp)
                                                                    .testTag("btn_delete_recent_run_${session.id}")
                                                            ) {
                                                                Icon(
                                                                    imageVector = Icons.Default.DeleteOutline,
                                                                    contentDescription = "Excluir treino",
                                                                    tint = RunColors.textSecondary.copy(alpha = 0.75f),
                                                                    modifier = Modifier.size(17.dp)
                                                                )
                                                            }
                                                            Icon(
                                                                imageVector = Icons.Default.ChevronRight,
                                                                contentDescription = "Ver detalhe",
                                                                tint = RunColors.textSecondary,
                                                                modifier = Modifier.size(20.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 16.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "Nenhuma atividade ainda",
                                                color = RunColors.textSecondary,
                                                fontSize = 13.sp,
                                                textAlign = TextAlign.Center
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

    // Bottom Sheet: Seletor de Ambiente (Ao Ar Livre vs Na Esteira vs Modo Leve)
    if (showEnvironmentSheet && activePlan != null) {
        RunningEnvironmentBottomSheet(
            onConfirm = { env, gentle ->
                triggerWorkoutStartWithAutoGps(
                    env = env,
                    gentle = gentle,
                    freeRaceMode = selectedFreeRaceMode,
                    sessionType = activePlan?.sessionType,
                    plan = activePlan,
                    programId = viewModel.selectedRunningProgramId.value
                )
            },
            onDismiss = { showEnvironmentSheet = false }
        )
    }

    // Alerta Imediato quando a Localização/GPS do Sistema está Desligada na Barra do Android
    if (showSystemGpsDisabledDialog) {
        AlertDialog(
            onDismissRequest = { showSystemGpsDisabledDialog = false },
            containerColor = Color(0xFF151922),
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.GpsFixed,
                        contentDescription = null,
                        tint = Color(0xFFFF5252)
                    )
                    Text(
                        text = "Localização do Celular Desligada",
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 17.sp
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(
                        color = Color(0xFFFF5252).copy(alpha = 0.14f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFFFF5252).copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Atenção: ative a localização do celular para registrar a distância e o pace da sua corrida.",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                    Text(
                        text = "Ao ativar o GPS na tela de configurações do Android e voltar ao app, seu treino iniciará automaticamente.",
                        color = RunColors.textSecondary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        AudioSynth.playTickSound()
                        pendingAutoStartAfterGpsSettings = true
                        com.example.MainActivity.markIntentionalExternalNavigation(30_000L)
                        runCatching {
                            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        }.onFailure {
                            Toast.makeText(context, "Ative a localização na barra superior do Android", Toast.LENGTH_LONG).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00B0FF)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "ATIVAR LOCALIZAÇÃO DO CELULAR",
                        color = Color(0xFF06101E),
                        fontWeight = FontWeight.Black,
                        fontSize = 12.5.sp
                    )
                }
            },
            dismissButton = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            showSystemGpsDisabledDialog = false
                            pendingAutoStartAfterGpsSettings = false
                        }
                    ) {
                        Text("Cancelar", color = RunColors.textSecondary, fontSize = 12.sp)
                    }
                    TextButton(
                        onClick = {
                            showSystemGpsDisabledDialog = false
                            pendingAutoStartAfterGpsSettings = false
                            isHUDActive = true
                        }
                    ) {
                        Text("Iniciar sem GPS", color = Color(0xFF00E5FF), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        )
    }

    val paceCalibrationMode by viewModel.paceCalibrationMode.collectAsState()
    val lastPaceCalibrationTimestamp by viewModel.lastPaceCalibrationTimestamp.collectAsState()
    val runningPaceAnalysis = remember(
        runningHistory,
        userProfile.idade,
        userProfile.baselinePaceMinPerKm,
        lastPaceCalibrationTimestamp,
        paceCalibrationMode
    ) {
        RunningProgramManager.analyzeRealPaceCalibration(
            history = runningHistory,
            userAge = userProfile.idade,
            currentBaselinePaceMinPerKm = userProfile.baselinePaceMinPerKm,
            lastCalibrationTimestamp = lastPaceCalibrationTimestamp,
            calibrationMode = paceCalibrationMode
        )
    }

    // Bottom Sheet: Calibração de Ritmo (5 km)
    if (showPaceOnboardingSheet) {
        RunningPaceOnboardingBottomSheet(
            onConfirm = { baselinePace ->
                viewModel.saveRunnerBaselinePace(baselinePace)
                hasDismissedPaceOnboarding = true
                showPaceOnboardingSheet = false
            },
            onDismiss = {
                hasDismissedPaceOnboarding = true
                showPaceOnboardingSheet = false
            },
            currentBaselinePaceMinPerKm = userProfile.baselinePaceMinPerKm,
            paceAnalysis = runningPaceAnalysis,
            isAutoCalibrationEnabled = paceCalibrationMode == com.example.engine.PaceCalibrationMode.AUTO_14_DAYS,
            onToggleAutoCalibration = { enabled ->
                viewModel.setPaceAutoCalibrationEnabled(enabled)
            }
        )
    }

    // Modal: Anamnese e Calibração Fisiológica (VAM, LT2, Zonas Z1-Z5 e Projeção de Prova)
    if (showAnamnesisModal) {
        com.example.ui.components.RunnerAnamnesisModal(
            initialProfile = anamnesisProfile,
            athleteAge = userProfile.idade,
            runningHistory = runningHistory,
            baselinePaceMinPerKm = userProfile.baselinePaceMinPerKm,
            calibratedMaxHrBpm = userProfile.fcMaxCalibradaBpm,
            onSaveProfile = { savedProfile, newBaselinePaceMin, recommendedProgramId ->
                com.example.ui.components.RunnerAnamnesisManager.saveProfile(context, savedProfile)
                viewModel.saveRunnerBaselinePace(newBaselinePaceMin)
                if (savedProfile.goal == com.example.ui.components.RunnerGoalOption.GOAL_MAINTAIN) {
                    viewModel.selectRunningProgram("run_to_maintain")
                } else {
                    viewModel.selectRunningProgram(recommendedProgramId)
                }
                anamnesisVersion++
                showAnamnesisModal = false
                Toast.makeText(
                    context,
                    "Perfil calibrado! Limiar LT2 atualizado.",
                    Toast.LENGTH_LONG
                ).show()
            },
            onDismiss = {
                showAnamnesisModal = false
            }
        )
    }

    // Modal: Biblioteca Oficial de Treinos Estruturados (RX-PRO)
    if (showRxWorkoutLibraryModal) {
        com.example.ui.components.RxProWorkoutLibraryModal(
            calibration = runnerCalibration,
            selectedRxCode = selectedRxWorkoutCode,
            onSelectRxWorkout = { rxWorkout ->
                selectedFreeRaceMode = null
                selectedRxWorkoutCode = rxWorkout.code
                showRxWorkoutLibraryModal = false
                Toast.makeText(
                    context,
                    "Treino ${rxWorkout.code} carregado no painel! Toque em START para iniciar.",
                    Toast.LENGTH_SHORT
                ).show()
            },
            onSelectStandardProgramCatalog = {
                showRxWorkoutLibraryModal = false
                viewModel.showRunningProgramsCatalog.value = true
            },
            onDismiss = {
                showRxWorkoutLibraryModal = false
            }
        )
    }

    // Bottom Sheet: Gestão de Revezamento de Tênis
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

    // Modal do Botão "Coach" (Topo da Tela): Consolidação de Carga Híbrida & Polimento TAF + Voz do Coach
    if (showHybridCoachModal) {
        val hybridCoachSummary = remember(runningHistory, gymWorkoutSessions, upcomingRaces, activePreRunCheckIn) {
            com.example.engine.HybridLoadCoachEngine.evaluateHybridState(
                runningSessions = runningHistory,
                gymSessions = gymWorkoutSessions,
                upcomingRaces = upcomingRaces,
                activePreRunCheckIn = activePreRunCheckIn
            )
        }
        com.example.ui.components.HybridCoachModalDialog(
            summary = hybridCoachSummary,
            coachVoiceEnabled = coachVoiceEnabled,
            onToggleCoachVoice = { newVoice ->
                viewModel.setCoachVoiceEnabled(newVoice)
                AudioSynth.playTickSound()
                val msg = if (newVoice) "Orientação de voz do Coach ativada" else "Voz do Coach desativada"
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            },
            onQuickSetTafDay = { dayOfNov ->
                val cal = java.util.Calendar.getInstance().apply {
                    timeInMillis = hybridCoachSummary.targetRaceEvent.raceDateMillis
                    set(java.util.Calendar.YEAR, 2026)
                    set(java.util.Calendar.MONTH, java.util.Calendar.NOVEMBER)
                    set(java.util.Calendar.DAY_OF_MONTH, dayOfNov)
                }
                val updatedEvent = hybridCoachSummary.targetRaceEvent.copy(
                    raceDateMillis = cal.timeInMillis
                )
                viewModel.saveUpcomingRaceEvent(updatedEvent)
                AudioSynth.playTickSound()
                Toast.makeText(
                    context,
                    "Data do ${updatedEvent.raceName} ajustada para $dayOfNov/11/2026!",
                    Toast.LENGTH_SHORT
                ).show()
            },
            onOpenPlanningTafTab = {
                viewModel.runningPlanningSubTab.value = 1
                viewModel.navigateToScreen(com.example.ui.viewmodel.AppScreen.WORKOUTS)
            },
            onDismiss = { showHybridCoachModal = false }
        )
    }

    // Modal: Configuração do Lembrete Diário de Corrida (Regra 8)
    if (showReminderConfigDialog) {
        val (previewTitle, previewBody) = remember(showReminderConfigDialog, runningHistory) {
            com.example.services.RunningDailyReminderManager.buildContextualReminderContent(context)
        }
        val alreadyRanToday = remember(runningHistory, showReminderConfigDialog) {
            com.example.services.RunningDailyReminderManager.hasRunToday(context, runningHistory)
        }
        val availableHours = listOf(6, 7, 8, 12, 17, 18, 19, 20)

        AlertDialog(
            onDismissRequest = { showReminderConfigDialog = false },
            containerColor = RunColors.surface,
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.NotificationsActive,
                        contentDescription = null,
                        tint = RunColors.brandText
                    )
                    Text(
                        text = "Lembrete Diário de Corrida",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Notificação Diária Contextualizada",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Avisa no horário escolhido se você ainda não correu hoje",
                                color = RunColors.textSecondary,
                                fontSize = 11.sp
                            )
                        }
                        Switch(
                            checked = isDailyReminderEnabled,
                            onCheckedChange = { enabled ->
                                isDailyReminderEnabled = enabled
                                com.example.services.RunningDailyReminderManager.setReminderConfig(
                                    context = context,
                                    enabled = enabled,
                                    hourOfDay = dailyReminderHour,
                                    minute = dailyReminderMinute
                                )
                            }
                        )
                    }

                    Text(
                        text = "Horário do lembrete:",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        availableHours.take(4).forEach { h ->
                            val isSelected = dailyReminderHour == h
                            Surface(
                                onClick = {
                                    dailyReminderHour = h
                                    dailyReminderMinute = 0
                                    com.example.services.RunningDailyReminderManager.setReminderConfig(
                                        context = context,
                                        enabled = isDailyReminderEnabled,
                                        hourOfDay = h,
                                        minute = 0
                                    )
                                    AudioSynth.playTickSound()
                                },
                                color = if (isSelected) RunColors.brand else Color(0xFF14161D),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, if (isSelected) RunColors.brandText else RunColors.border),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = String.format(Locale.US, "%02d:00", h),
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        availableHours.drop(4).forEach { h ->
                            val isSelected = dailyReminderHour == h
                            Surface(
                                onClick = {
                                    dailyReminderHour = h
                                    dailyReminderMinute = 0
                                    com.example.services.RunningDailyReminderManager.setReminderConfig(
                                        context = context,
                                        enabled = isDailyReminderEnabled,
                                        hourOfDay = h,
                                        minute = 0
                                    )
                                    AudioSynth.playTickSound()
                                },
                                color = if (isSelected) RunColors.brand else Color(0xFF14161D),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, if (isSelected) RunColors.brandText else RunColors.border),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = String.format(Locale.US, "%02d:00", h),
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            }
                        }
                    }

                    Surface(
                        color = Color(0xFF14161D),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, RunColors.border),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = previewTitle,
                                color = RunColors.brandText,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = previewBody,
                                color = Color.LightGray,
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                            Text(
                                text = if (alreadyRanToday) "✓ Corrida de hoje já registrada (lembrete agendado para amanhã)" else "• Corrida de hoje ainda pendente",
                                color = if (alreadyRanToday) RunColors.phaseWalk else RunColors.phaseWarmup,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            AudioSynth.playTickSound()
                            val sent = com.example.services.RunningDailyReminderManager.dispatchReminderNotificationIfNoRunToday(
                                context = context,
                                forcePreview = true
                            )
                            Toast.makeText(
                                context,
                                if (sent) "Notificação de lembrete enviada!" else "Ative a permissão de notificações do sistema",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        border = BorderStroke(1.dp, RunColors.brand),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Testar Notificação Agora", color = RunColors.brandText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showReminderConfigDialog = false }) {
                    Text("Concluir", color = RunColors.brandText, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // Modal Largo com Scroll Completo: Catálogo de Programas e Treinos de Corrida (Coach)
    val showCatalogSheet by viewModel.showRunningProgramsCatalog.collectAsState()
    val currentCatalogProgramId by viewModel.selectedRunningProgramId.collectAsState()
    if (showCatalogSheet) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { viewModel.showRunningProgramsCatalog.value = false },
            properties = androidx.compose.ui.window.DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = true
            )
        ) {
            com.example.ui.components.ProvideRunFontScale {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.94f)
                    .heightIn(max = 680.dp)
                    .navigationBarsPadding()
                    .padding(bottom = 48.dp),
                shape = RoundedCornerShape(24.dp),
                color = RunColors.surface,
                border = BorderStroke(1.dp, RunColors.border),
                shadowElevation = 12.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp, start = 18.dp, end = 18.dp, bottom = 14.dp)
                ) {
                    // Cabeçalho Fixo no Topo: Título + Subtítulo com lineHeight ajustado + Seletor de Fonte + Botão Circular [ X ]
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Programas e Treinos de Corrida",
                                color = Color.White,
                                fontSize = 20.sp,
                                lineHeight = 24.sp,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                text = "Escolha o protocolo ou treino do Coach para aplicar ao card principal:",
                                color = RunColors.textSecondary,
                                fontSize = 13.5.sp,
                                lineHeight = 18.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            com.example.ui.components.RunFontScaleToggleButton(compact = true)
                            IconButton(
                                onClick = {
                                    AudioSynth.playTickSound()
                                    viewModel.showRunningProgramsCatalog.value = false
                                },
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(Color.White.copy(alpha = 0.08f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Fechar Programas e Treinos",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Lista Rolável Completa de Programas
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        items(
                            items = RunningProgramManager.ALL_RUNNING_PROGRAMS,
                            key = { it.id }
                        ) { prog ->
                            val isSelected = prog.id == currentCatalogProgramId
                            Surface(
                                onClick = {
                                    if (prog.id == "free_regenerative") {
                                        showPreRunCheckInDialog = false
                                        viewModel.setActivePreRunCheckIn(null)
                                    } else {
                                        selectedFreeRaceMode = null
                                    }
                                    viewModel.selectRunningProgram(prog.id)
                                    viewModel.showRunningProgramsCatalog.value = false
                                    AudioSynth.playTickSound()
                                },
                                color = if (isSelected) RunColors.brand.copy(alpha = 0.14f) else Color(0xFF14161D),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) RunColors.brand else RunColors.border
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("running_program_item_${prog.id}")
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            color = RunColors.brand.copy(alpha = 0.18f),
                                            shape = RoundedCornerShape(6.dp),
                                            modifier = Modifier.weight(1f, fill = false)
                                        ) {
                                            Text(
                                                text = prog.badge,
                                                color = RunColors.brandText,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Black,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = prog.durationLabel,
                                            color = RunColors.textSecondary,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                    Text(
                                        text = prog.title,
                                        color = Color.White,
                                        fontSize = 15.sp,
                                        lineHeight = 20.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                    Text(
                                        text = prog.subtitle,
                                        color = RunColors.textSecondary,
                                        fontSize = 12.sp,
                                        lineHeight = 16.sp
                                    )
                                }
                            }
                        }
                    }

                    // Rodapé Fixo com Botão Fechar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = {
                                AudioSynth.playTickSound()
                                viewModel.showRunningProgramsCatalog.value = false
                            },
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text("Fechar", color = RunColors.brandText, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            }
        }
    }

    // Modal de Status do GPS
    if (showGpsStatusDialog) {
        val (gpsTint, gpsLabelText) = when (runTrackState.status) {
            RunTrackerStatus.GOOD -> Pair(RunColors.phaseWalk, "GPS OK")
            RunTrackerStatus.SEARCHING -> Pair(RunColors.phaseWarmup, "Buscando Sinal…")
            RunTrackerStatus.WEAK -> Pair(RunColors.phaseWarmup, "Sinal Fraco")
            RunTrackerStatus.NO_PERMISSION -> Pair(RunColors.alert, "Sem Permissão")
            RunTrackerStatus.IDLE -> {
                val hasPerm = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
                if (hasPerm) Pair(RunColors.phaseWalk, "GPS Pronto") else Pair(RunColors.textSecondary, "GPS Desativado")
            }
        }

        val hasPerm = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        AlertDialog(
            onDismissRequest = { showGpsStatusDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.GpsFixed,
                        contentDescription = null,
                        tint = RunColors.brandText
                    )
                    Text("Medição de GPS", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(
                        color = gpsTint.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Status Atual: $gpsLabelText",
                            color = gpsTint,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(10.dp)
                        )
                    }

                    Text(
                        text = if (hasPerm) {
                            val accStr = if (runTrackState.accuracyMeters > 0f) String.format(Locale.US, " (Precisão ~%.1fm)", runTrackState.accuracyMeters) else ""
                            "Sinal de localização ativo$accStr.\n\nO GPS mede distância e ritmo (pace) em tempo real durante corridas ao ar livre. O rastreamento funciona com a tela bloqueada via Notificação Fixa."
                        } else {
                            "A permissão de localização é necessária para medir a distância real e o pace da sua corrida ao ar livre."
                        },
                        color = Color.LightGray,
                        fontSize = 13.sp
                    )
                }
            },
            confirmButton = {
                if (!hasPerm) {
                    Button(
                        onClick = {
                            showGpsStatusDialog = false
                            gpsDialogPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = RunColors.brand)
                    ) {
                        Text("Permitir GPS", color = Color.White)
                    }
                } else {
                    TextButton(onClick = { showGpsStatusDialog = false }) {
                        Text("OK", color = RunColors.brandText)
                    }
                }
            },
            dismissButton = {
                if (!hasPerm) {
                    TextButton(onClick = {
                        showGpsStatusDialog = false
                        try {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            // Ignore
                        }
                    }) {
                        Text("Configurações", color = RunColors.textSecondary)
                    }
                }
            },
            containerColor = RunColors.surface
        )
    }

    // Modal Rápido Pré-Corrida: "Prontidão Expressa" (apenas treinos estruturados; bloqueado em Corrida Livre/Prova)
    if (showPreRunCheckInDialog) {
        val planRef = activePlan
        val preRunTitle = remember(planRef, selectedDayNumberForSession, isGentleModeSession) {
            if (planRef != null) {
                RunningProgramManager.getDisplayTitleForSessionType(
                    sessionType = planRef.sessionType,
                    isGentleMode = isGentleModeSession,
                    weekNumber = planRef.weekNumber,
                    dayNumber = selectedDayNumberForSession
                )
            } else {
                "Sessão de Corrida"
            }
        }
        val canShowPreRunDialog = RunningProgramManager.shouldShowPreRunAnamnesis(
            freeRaceMode = selectedFreeRaceMode,
            sessionType = planRef?.sessionType,
            planTitle = planRef?.title,
            displayTitle = preRunTitle,
            weekNumber = planRef?.weekNumber,
            programId = currentCatalogProgramId
        )
        if (canShowPreRunDialog) {
            PreRunExpressReadinessDialog(
                workoutTitle = preRunTitle,
                onSkipAndStart = { skippedCheckIn ->
                    viewModel.setActivePreRunCheckIn(skippedCheckIn)
                    showPreRunCheckInDialog = false
                    executeWorkoutStartWithAutoGps(env = pendingStartEnv, gentle = pendingStartGentle)
                },
                onConfirmAndStart = { completedCheckIn ->
                    viewModel.setActivePreRunCheckIn(completedCheckIn)
                    showPreRunCheckInDialog = false
                    executeWorkoutStartWithAutoGps(env = pendingStartEnv, gentle = pendingStartGentle)
                },
                onDismiss = {
                    showPreRunCheckInDialog = false
                }
            )
        } else {
            LaunchedEffect(Unit) {
                showPreRunCheckInDialog = false
            }
        }
    }

    // Modal de Resumo Pós-Corrida
    if (showSummaryDialog && activePlan != null) {
        var isSavingRun by remember { mutableStateOf(false) }
        val completedHrSamples = remember(completedHrTimelineStr) {
            completedHrTimelineStr.toHrTimelineList().filter { it in 45..230 }
        }
        val summaryAvgHr = bleManager.getAverageBpm()?.takeIf { it > 0 }
            ?: completedHrSamples.takeIf { it.isNotEmpty() }?.average()?.safeRoundToInt()
            ?: completedRecordedSplits.mapNotNull { it.avgHr?.takeIf { v -> v > 0 } }.takeIf { it.isNotEmpty() }?.average()?.safeRoundToInt()
        val summaryMaxHr = bleManager.getMaxBpm()?.takeIf { it > 0 }
            ?: completedHrSamples.maxOrNull()
            ?: summaryAvgHr

        RunningSummaryDialog(
            weekNumber = activePlan!!.weekNumber,
            dayNumber = selectedDayNumberForSession,
            sessionType = activePlan!!.sessionType,
            durationSec = completedDurationSec,
            environment = selectedEnvironment,
            isGentleMode = isGentleModeSession,
            measuredDistanceMeters = measuredDistanceMeters,
            plan = activePlan,
            skippedIndices = completedSkippedIndices,
            availableShoes = runningShoes,
            userWeightKg = com.example.data.model.resolveValidWeightKgOrNull(userProfile),
            userProfile = userProfile,
            mainRunDurationSec = completedMainRunDurationSec,
            mainRunDistanceMeters = completedMainRunDistanceMeters,
            avgHr = summaryAvgHr,
            maxHr = summaryMaxHr,
            hrTimeline = completedHrTimelineStr.ifBlank { bleManager.getHrTimelineString() },
            recordedSplits = completedRecordedSplits,
            wasAutoSaved = wasSessionAutoSaved,
            athleteAge = userProfile.idade,
            baselinePaceMinPerKm = userProfile.baselinePaceMinPerKm,
            allRunningSessions = runningHistory,
            onApplyNewBaselinePace = { newPaceMin ->
                anamnesisVersion++
                viewModel.saveRunnerBaselinePace(newPaceMin)
                Toast.makeText(context, "Planilha e VAM atualizadas com sucesso!", Toast.LENGTH_SHORT).show()
            },
            onSaveAsOfficialRace = { record ->
                viewModel.saveOfficialRaceRecord(record)
                Toast.makeText(context, "Prova oficial salva: ${record.raceName}!", Toast.LENGTH_SHORT).show()
            },
            onSaveScientificTelemetry = { rpeCr10Val, sRpeUaVal, auditVal ->
                latestRpeCr10 = rpeCr10Val
                latestSrpeLoadUa = sRpeUaVal
                latestExecutionAudit = auditVal
            },
            onSave = { distanceMeters, rpe, notes, sessionEnv, painResponse, shoeName ->
                if (isSavingRun) return@RunningSummaryDialog
                isSavingRun = true
                val effectiveMainDist = if (completedMainRunDistanceMeters > 0) {
                    completedMainRunDistanceMeters
                } else if (distanceMeters > 0 && completedMainRunDurationSec in 1 until completedDurationSec) {
                    val warmupCooldownSec = (completedDurationSec - completedMainRunDurationSec).coerceAtLeast(0)
                    val warmupCooldownMeters = ((warmupCooldownSec * (5000.0 / 3600.0)).toInt()).coerceAtMost((distanceMeters * 0.45).toInt())
                    (distanceMeters - warmupCooldownMeters).coerceAtLeast(1)
                } else 0

                val hasMainRunIsolation = effectiveMainDist > 0 && completedMainRunDurationSec > 0 &&
                        (completedMainRunDurationSec < completedDurationSec || effectiveMainDist < distanceMeters)

                val mainOnlyAvgPace = if (hasMainRunIsolation && effectiveMainDist > 0) {
                    ((completedMainRunDurationSec.toDouble() / effectiveMainDist.toDouble()) * 1000.0).safeRoundToInt()
                } else if (distanceMeters > 0) {
                    ((completedDurationSec * 1000.0) / distanceMeters.toDouble()).safeRoundToInt()
                } else {
                    0
                }
                val globalAvgPace = if (distanceMeters > 0) {
                    ((completedDurationSec * 1000.0) / distanceMeters.toDouble()).safeRoundToInt()
                } else {
                    mainOnlyAvgPace
                }

                val finalSplitsJson = if (completedRecordedSplits.isNotEmpty()) {
                    completedRecordedSplits.toJsonString()
                } else null

                val finalHrTimeline = completedHrTimelineStr.ifBlank { bleManager.getHrTimelineString() }.ifBlank { null }
                val parsedHrSamples = finalHrTimeline.toHrTimelineList().filter { it in 45..230 }
                val resolvedAvgHr = bleManager.getAverageBpm()?.takeIf { it > 0 }
                    ?: parsedHrSamples.takeIf { it.isNotEmpty() }?.average()?.safeRoundToInt()
                    ?: completedRecordedSplits.mapNotNull { it.avgHr?.takeIf { v -> v > 0 } }.takeIf { it.isNotEmpty() }?.average()?.safeRoundToInt()
                val resolvedMaxHr = bleManager.getMaxBpm()?.takeIf { it > 0 }
                    ?: parsedHrSamples.maxOrNull()
                    ?: resolvedAvgHr
                val finalRoutePointsJson = completedRoutePointsJson?.takeIf { it.isNotBlank() }
                    ?: if (RunTracker.recordedPoints.isNotEmpty()) RunTracker.getRoutePointsJson() else null

                val effectiveDistToSave = if (distanceMeters > 0) distanceMeters else measuredDistanceMeters
                val resolvedRpeCr10 = latestRpeCr10 ?: rpe.takeIf { it in 1..10 }
                val resolvedSrpeUa = latestSrpeLoadUa ?: com.example.data.model.calculateSrpeLoadUa(completedDurationSec, resolvedRpeCr10)
                val resolvedHrProvenance = com.example.data.model.resolveHrMetricProvenance(
                    connectedDeviceName = connectedDeviceName,
                    hasHrData = (resolvedAvgHr ?: 0) > 0 || (resolvedMaxHr ?: 0) > 0 || !finalHrTimeline.isNullOrBlank()
                )
                val session = RunningSession(
                    id = autoSavedSessionId ?: UUID.randomUUID().toString(),
                    date = autoSavedSessionTimestamp.takeIf { it > 0L } ?: System.currentTimeMillis(),
                    weekNumber = activePlan?.weekNumber ?: 1,
                    dayNumber = selectedDayNumberForSession,
                    sessionType = activePlan?.sessionType ?: "Corrida",
                    durationSec = completedDurationSec,
                    distanceMeters = effectiveDistToSave,
                    avgPaceSecPerKm = globalAvgPace,
                    rpe = rpe.takeIf { it in 1..10 } ?: 0,
                    notes = notes,
                    environment = sessionEnv,
                    painResponse = painResponse,
                    isGentleMode = isGentleModeSession,
                    shoeName = shoeName,
                    avgHr = resolvedAvgHr,
                    maxHr = resolvedMaxHr,
                    mainRunDurationSec = if (hasMainRunIsolation) completedMainRunDurationSec else null,
                    mainRunDistanceMeters = if (hasMainRunIsolation) effectiveMainDist else null,
                    mainRunAvgPaceSecPerKm = if (hasMainRunIsolation) mainOnlyAvgPace else null,
                    kmSplitsJson = finalSplitsJson,
                    hrTimeline = finalHrTimeline,
                    routePointsJson = finalRoutePointsJson,
                    preRunCheckIn = activePreRunCheckIn,
                    rpeCr10 = resolvedRpeCr10,
                    sRpeLoadUa = resolvedSrpeUa,
                    executionAudit = latestExecutionAudit,
                    hrProvenance = resolvedHrProvenance
                ).let { draft ->
                    runCatching {
                        val intervals = com.example.data.model.buildExecutedIntervalsForSession(
                            session = draft,
                            liveBlockUnintendedWalkSec = RunTracker.getBlockUnintendedWalkMap()
                        )
                        val withIntervals = draft.copy(executedIntervals = intervals)
                        val adv = com.example.services.ai.AiPhysiologistAnalyzer.computeAdvancedRunningMetrics(withIntervals)
                        withIntervals.copy(advancedMetrics = adv)
                    }.getOrDefault(draft)
                }
                viewModel.saveRunningSession(session)
                RunTracker.persistSessionDirectlyToDb(context, session)
                RunTracker.clearActiveWorkoutCheckpoint(context)
                runCatching {
                    com.example.ui.components.RunRoutePhotoManager.migratePhotosBetweenSessionsIfNeeded(
                        context = context,
                        fromSessionId = "auto_checkpoint_session",
                        toSessionId = session.id
                    )
                    com.example.ui.components.RunRoutePhotoManager.recalibratePhotoProgressForFinalDistance(
                        context = context,
                        sessionId = session.id,
                        finalDistanceMeters = effectiveDistToSave,
                        routeItems = finalRoutePointsJson.toRoutePointItems()
                    )
                }
                com.example.services.RunningDailyReminderManager.markRunCompletedToday(context)
                RunTracker.reset()
                autoSavedSessionId = null
                completedRoutePointsJson = null
                viewModel.setActivePreRunCheckIn(null)
                showSummaryDialog = false
                activePlan = null
                isGentleModeSession = false
                viewModel.selectRunningDetailSession(session)
                viewModel.navigateToScreen(AppScreen.RUNNING_DETAIL)
            },
            onSaveAndShare = { distanceMeters, rpe, notes, sessionEnv, painResponse, shoeName ->
                if (isSavingRun) return@RunningSummaryDialog
                isSavingRun = true
                val effectiveMainDist = if (completedMainRunDistanceMeters > 0) {
                    completedMainRunDistanceMeters
                } else if (distanceMeters > 0 && completedMainRunDurationSec in 1 until completedDurationSec) {
                    val warmupCooldownSec = (completedDurationSec - completedMainRunDurationSec).coerceAtLeast(0)
                    val warmupCooldownMeters = ((warmupCooldownSec * (5000.0 / 3600.0)).toInt()).coerceAtMost((distanceMeters * 0.45).toInt())
                    (distanceMeters - warmupCooldownMeters).coerceAtLeast(1)
                } else 0

                val hasMainRunIsolation = effectiveMainDist > 0 && completedMainRunDurationSec > 0 &&
                        (completedMainRunDurationSec < completedDurationSec || effectiveMainDist < distanceMeters)

                val mainOnlyAvgPace = if (hasMainRunIsolation && effectiveMainDist > 0) {
                    ((completedMainRunDurationSec.toDouble() / effectiveMainDist.toDouble()) * 1000.0).safeRoundToInt()
                } else if (distanceMeters > 0) {
                    ((completedDurationSec * 1000.0) / distanceMeters.toDouble()).safeRoundToInt()
                } else {
                    0
                }
                val globalAvgPace = if (distanceMeters > 0) {
                    ((completedDurationSec * 1000.0) / distanceMeters.toDouble()).safeRoundToInt()
                } else {
                    mainOnlyAvgPace
                }

                val finalSplitsJson = if (completedRecordedSplits.isNotEmpty()) {
                    completedRecordedSplits.toJsonString()
                } else null

                val finalHrTimeline = completedHrTimelineStr.ifBlank { bleManager.getHrTimelineString() }.ifBlank { null }
                val parsedHrSamples = finalHrTimeline.toHrTimelineList().filter { it in 45..230 }
                val resolvedAvgHr = bleManager.getAverageBpm()?.takeIf { it > 0 }
                    ?: parsedHrSamples.takeIf { it.isNotEmpty() }?.average()?.safeRoundToInt()
                    ?: completedRecordedSplits.mapNotNull { it.avgHr?.takeIf { v -> v > 0 } }.takeIf { it.isNotEmpty() }?.average()?.safeRoundToInt()
                val resolvedMaxHr = bleManager.getMaxBpm()?.takeIf { it > 0 }
                    ?: parsedHrSamples.maxOrNull()
                    ?: resolvedAvgHr
                val finalRoutePointsJson = completedRoutePointsJson?.takeIf { it.isNotBlank() }
                    ?: if (RunTracker.recordedPoints.isNotEmpty()) RunTracker.getRoutePointsJson() else null

                val effectiveDistToSave = if (distanceMeters > 0) distanceMeters else measuredDistanceMeters
                val resolvedRpeCr10 = latestRpeCr10 ?: rpe.takeIf { it in 1..10 }
                val resolvedSrpeUa = latestSrpeLoadUa ?: com.example.data.model.calculateSrpeLoadUa(completedDurationSec, resolvedRpeCr10)
                val resolvedHrProvenance = com.example.data.model.resolveHrMetricProvenance(
                    connectedDeviceName = connectedDeviceName,
                    hasHrData = (resolvedAvgHr ?: 0) > 0 || (resolvedMaxHr ?: 0) > 0 || !finalHrTimeline.isNullOrBlank()
                )
                val session = RunningSession(
                    id = autoSavedSessionId ?: UUID.randomUUID().toString(),
                    date = autoSavedSessionTimestamp.takeIf { it > 0L } ?: System.currentTimeMillis(),
                    weekNumber = activePlan?.weekNumber ?: 1,
                    dayNumber = selectedDayNumberForSession,
                    sessionType = activePlan?.sessionType ?: "Corrida",
                    durationSec = completedDurationSec,
                    distanceMeters = effectiveDistToSave,
                    avgPaceSecPerKm = globalAvgPace,
                    rpe = rpe.takeIf { it in 1..10 } ?: 0,
                    notes = notes,
                    environment = sessionEnv,
                    painResponse = painResponse,
                    isGentleMode = isGentleModeSession,
                    shoeName = shoeName,
                    avgHr = resolvedAvgHr,
                    maxHr = resolvedMaxHr,
                    mainRunDurationSec = if (hasMainRunIsolation) completedMainRunDurationSec else null,
                    mainRunDistanceMeters = if (hasMainRunIsolation) effectiveMainDist else null,
                    mainRunAvgPaceSecPerKm = if (hasMainRunIsolation) mainOnlyAvgPace else null,
                    kmSplitsJson = finalSplitsJson,
                    hrTimeline = finalHrTimeline,
                    routePointsJson = finalRoutePointsJson,
                    preRunCheckIn = activePreRunCheckIn,
                    rpeCr10 = resolvedRpeCr10,
                    sRpeLoadUa = resolvedSrpeUa,
                    executionAudit = latestExecutionAudit,
                    hrProvenance = resolvedHrProvenance
                ).let { draft ->
                    runCatching {
                        val intervals = com.example.data.model.buildExecutedIntervalsForSession(
                            session = draft,
                            liveBlockUnintendedWalkSec = RunTracker.getBlockUnintendedWalkMap()
                        )
                        val withIntervals = draft.copy(executedIntervals = intervals)
                        val adv = com.example.services.ai.AiPhysiologistAnalyzer.computeAdvancedRunningMetrics(withIntervals)
                        withIntervals.copy(advancedMetrics = adv)
                    }.getOrDefault(draft)
                }
                viewModel.saveRunningSession(session)
                RunTracker.persistSessionDirectlyToDb(context, session)
                RunTracker.clearActiveWorkoutCheckpoint(context)
                runCatching {
                    com.example.ui.components.RunRoutePhotoManager.migratePhotosBetweenSessionsIfNeeded(
                        context = context,
                        fromSessionId = "auto_checkpoint_session",
                        toSessionId = session.id
                    )
                    com.example.ui.components.RunRoutePhotoManager.recalibratePhotoProgressForFinalDistance(
                        context = context,
                        sessionId = session.id,
                        finalDistanceMeters = effectiveDistToSave,
                        routeItems = finalRoutePointsJson.toRoutePointItems()
                    )
                }
                com.example.services.RunningDailyReminderManager.markRunCompletedToday(context)
                viewModel.selectedShareSession.value = session
                com.example.utils.AudioSynth.playTickSound()
                viewModel.navigateToScreen(AppScreen.SHARE_CARD)
                RunTracker.reset()
                autoSavedSessionId = null
                completedRoutePointsJson = null
                viewModel.setActivePreRunCheckIn(null)
                showSummaryDialog = false
                activePlan = null
                isGentleModeSession = false
            },
            onDiscard = {
                autoSavedSessionId?.let { id ->
                    if (runningHistory.any { it.id == id }) {
                        viewModel.deleteRunningSession(id)
                    }
                }
                RunTracker.clearActiveWorkoutCheckpoint(context)
                RunTracker.reset()
                autoSavedSessionId = null
                completedRoutePointsJson = null
                viewModel.setActivePreRunCheckIn(null)
                showSummaryDialog = false
                activePlan = null
                isGentleModeSession = false
                Toast.makeText(
                    context,
                    "Treino descartado • Planilha mantida em Semana $selectedWeek · Dia $selectedDay",
                    Toast.LENGTH_SHORT
                ).show()
            },
            onDismiss = {
                autoSavedSessionId?.let { id ->
                    if (runningHistory.any { it.id == id }) {
                        viewModel.deleteRunningSession(id)
                    }
                }
                RunTracker.clearActiveWorkoutCheckpoint(context)
                RunTracker.reset()
                autoSavedSessionId = null
                completedRoutePointsJson = null
                viewModel.setActivePreRunCheckIn(null)
                showSummaryDialog = false
                activePlan = null
                isGentleModeSession = false
            }
        )
    }

    // Diálogo de confirmação para excluir treino direto da lista de "Atividades recentes" (restaurando o dia da planilha)
    recentSessionToDelete?.let { sessionToDelete ->
        AlertDialog(
            onDismissRequest = { recentSessionToDelete = null },
            containerColor = RunColors.surface,
            title = {
                Text(
                    text = "Excluir treino?",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                val titleStr = RunningProgramManager.getDisplayTitleForSession(sessionToDelete)
                Text(
                    text = "Deseja remover \"$titleStr\" do Histórico? Se este treino pertencia a um dia da planilha, esse dia voltará a ficar pendente como sua sessão atual.",
                    color = RunColors.textSecondary,
                    fontSize = 13.5.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteRunningSession(sessionToDelete.id)
                        recentSessionToDelete = null
                        Toast.makeText(
                            context,
                            "Treino excluído • Dia da planilha restaurado!",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RunColors.alert)
                ) {
                    Text("Excluir", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { recentSessionToDelete = null }) {
                    Text("Cancelar", color = RunColors.textSecondary)
                }
            }
        )
    }

    // Modal de Registro / Recuperação de Treino Realizado da Planilha (ex: W1D3)
    if (showRecoverCompletedWorkoutDialog) {
        val currentProgId by viewModel.selectedRunningProgramId.collectAsState()
        val defaultShoeForRecover = remember(runningShoes) {
            val activeShoe = runningShoes.firstOrNull { it.isDefault && !it.isRetired }
                ?: runningShoes.firstOrNull { !it.isRetired }
            activeShoe?.let { "${it.brand} ${it.name}" } ?: "Olympikus Corre 4"
        }
        com.example.ui.components.RecoverCompletedWorkoutDialog(
            programId = currentProgId,
            initialWeek = currentProgress.weekNumber,
            initialDay = currentProgress.dayNumber,
            baselinePaceMinPerKm = userProfile.baselinePaceMinPerKm,
            defaultShoeName = defaultShoeForRecover,
            onDismiss = {
                showRecoverCompletedWorkoutDialog = false
                AudioSynth.playTickSound()
            },
            onConfirmRegister = { weekNum, dayNum, sessType, durSec, distM, env, shoe ->
                val avgPace = if (distM > 0) ((durSec * 1000) / distM) else 0
                val recoveredSession = RunningSession(
                    id = UUID.randomUUID().toString(),
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
                RunTracker.persistSessionDirectlyToDb(context, recoveredSession)
                com.example.services.RunningDailyReminderManager.markRunCompletedToday(context)
                showRecoverCompletedWorkoutDialog = false
                AudioSynth.playTickSound()
                Toast.makeText(
                    context,
                    "Semana $weekNum · Dia $dayNum salvo no Histórico!",
                    Toast.LENGTH_SHORT
                ).show()
            }
        )
    }

    // Modal de Pareamento BLE
    if (showHrPairingSheet) {
        ModalBottomSheet(
            onDismissRequest = { 
                showHrPairingSheet = false
                bleManager.stopScan()
            },
            containerColor = Color(0xFF1A1A22),
            dragHandle = { BottomSheetDefaults.DragHandle(color = Color.Gray) }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(16.dp)
                    .padding(bottom = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Monitor Cardíaco BLE",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(24.dp))

                if (hrConnectionState == HrConnectionState.CONNECTED) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(RunColors.surface)
                            .padding(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = null,
                            tint = Color(0xFFFF4D4D),
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = if (currentBpm > 0) "$currentBpm BPM" else "-- BPM",
                            color = Color.White,
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "Conectado a ${connectedDeviceName ?: "Sensor"}",
                            color = RunColors.phaseWalk,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        Button(
                            onClick = { bleManager.disconnect() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C2C38)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("DESCONECTAR", fontWeight = FontWeight.Bold)
                        }
                    }
                } else if (hrConnectionState == HrConnectionState.CONNECTING) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        CircularProgressIndicator(color = RunColors.brand, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Conectando a $connectedDeviceName...", color = Color.White)
                    }
                } else {
                    Text(
                        text = if (isScanningBle) "Procurando sensores BLE..." else "Toque para buscar sua cinta cardíaca",
                        color = RunColors.textSecondary,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    if (isScanningBle) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().height(2.dp),
                            color = RunColors.brand,
                            trackColor = Color.DarkGray
                        )
                    } else {
                        Button(
                            onClick = { bleManager.startScan() },
                            colors = ButtonDefaults.buttonColors(containerColor = RunColors.brand),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.AutoMirrored.Filled.BluetoothSearching, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("BUSCAR CINTA BLUETOOTH", fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(discoveredDevices) { device ->
                            Surface(
                                onClick = { bleManager.connect(device.address) },
                                color = RunColors.surface,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, RunColors.border)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(device.name, color = Color.White, fontWeight = FontWeight.Bold)
                                        Text(device.address, color = RunColors.textSecondary, fontSize = 11.sp)
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.SignalCellularAlt, null, tint = RunColors.phaseWalk, modifier = Modifier.size(14.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("${device.rssi} dBm", color = RunColors.textSecondary, fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                    
                    if (!isScanningBle && discoveredDevices.isEmpty()) {
                        Text(
                            "Nenhum sensor encontrado.",
                            color = RunColors.textSecondary,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                
                if (com.example.BuildConfig.DEBUG) {
                    TextButton(
                        onClick = { 
                            bleManager.startSimulation() 
                            showHrPairingSheet = false
                        }
                    ) {
                        Text(
                            "Simular XOSS X2 PRO (Modo Teste)",
                            color = RunColors.textSecondary,
                            fontSize = 12.sp,
                            textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = com.example.utils.AppBuildInfo.compactSignature,
                    color = Color(0xFF757580),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }

    // Modal de Troféus & Provas Oficiais (PRs + Modo VS + Histórico de Provas)
    if (showOfficialRacesVaultDialog) {
        com.example.ui.components.OfficialRacesVaultDialog(
            officialRaces = officialRaces,
            runningSessions = runningHistory,
            runningShoes = runningShoes,
            userProfile = userProfile,
            onSaveRace = { record -> viewModel.saveOfficialRaceRecord(record) },
            onDeleteRace = { raceId -> viewModel.deleteOfficialRaceRecord(raceId) },
            onOpenLinkedSession = { targetSession ->
                showOfficialRacesVaultDialog = false
                viewModel.selectRunningDetailSession(targetSession)
                viewModel.navigateToScreen(AppScreen.RUNNING_DETAIL)
            },
            onDismiss = { showOfficialRacesVaultDialog = false }
        )
    }

    // Modal de Conversão 1-Toque ou Cadastro Manual de Prova Oficial
    if (showManualOfficialRaceDialog || sessionToSaveAsOfficialRace != null) {
        val targetSess = sessionToSaveAsOfficialRace
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
                showManualOfficialRaceDialog = false
                sessionToSaveAsOfficialRace = null
                Toast.makeText(
                    context,
                    "Prova oficial salva: ${record.raceName}!",
                    Toast.LENGTH_SHORT
                ).show()
            },
            onDismiss = {
                showManualOfficialRaceDialog = false
                sessionToSaveAsOfficialRace = null
            }
        )
    }
}

@Composable
fun RunningSessionHistoryItem(
    session: RunningSession,
    runningShoes: List<com.example.data.model.RunningShoe> = emptyList(),
    isSavedAsOfficialRace: Boolean = false,
    onSaveAsOfficialRaceClick: (() -> Unit)? = null,
    onSessionClick: (() -> Unit)? = null,
    onShareClick: (() -> Unit)? = null,
    onEditShoeClick: (() -> Unit)? = null,
    onDeleteClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val ptBr = Locale.forLanguageTag("pt-BR")

    val dateStr = remember(session.date) {
        SimpleDateFormat("dd.MM.yy HH:mm", ptBr).format(Date(session.date))
    }

    // Separação entre Treino Principal (destaque) e Sessão Total (com Aquecimento/Desaquecimento)
    val mainDistMeters = (session.mainRunDistanceMeters?.takeIf { it > 0 } ?: session.distanceMeters).coerceAtLeast(0)
    val mainDurSec = (session.mainRunDurationSec?.takeIf { it > 0 } ?: session.durationSec).coerceAtLeast(0)
    val mainPaceSec = session.mainRunAvgPaceSecPerKm?.takeIf { it > 0 }
        ?: if (mainDistMeters > 0 && mainDurSec > 0) ((mainDurSec.toDouble() / mainDistMeters.toDouble()) * 1000.0).toInt()
        else session.avgPaceSecPerKm

    val mainDistKm = mainDistMeters / 1000f
    val mainMinutes = mainDurSec / 60
    val mainSeconds = mainDurSec % 60
    val mainDurationText = String.format(Locale.US, "%02d:%02d min", mainMinutes, mainSeconds)
    val mainPaceText = if (mainPaceSec > 0) {
        String.format(Locale.US, "%d'%02d\"/km", mainPaceSec / 60, mainPaceSec % 60)
    } else {
        "--:--"
    }

    val totalDistKm = session.distanceMeters / 1000f
    val totalMinutes = session.durationSec / 60
    val totalSeconds = session.durationSec % 60
    val totalDurationText = String.format(Locale.US, "%02d:%02d min", totalMinutes, totalSeconds)
    val totalPaceSec = if (session.distanceMeters > 0 && session.durationSec > 0) {
        ((session.durationSec.toDouble() / session.distanceMeters.toDouble()) * 1000.0).toInt()
    } else session.avgPaceSecPerKm
    val totalPaceText = if (totalPaceSec > 0) {
        String.format(Locale.US, "%d'%02d\"/km", totalPaceSec / 60, totalPaceSec % 60)
    } else {
        "--:--"
    }

    val matchedShoe = remember(session.shoeName, runningShoes) {
        val raw = session.shoeName?.trim()
        if (raw.isNullOrBlank()) null
        else runningShoes.firstOrNull { shoe ->
            shoe.id.equals(raw, ignoreCase = true) ||
            shoe.name.equals(raw, ignoreCase = true) ||
            shoe.fullDisplayName().equals(raw, ignoreCase = true) ||
            (shoe.name.length >= 3 && (raw.contains(shoe.name, ignoreCase = true) || shoe.name.contains(raw, ignoreCase = true)))
        }
    }

    val shoeDisplayText = remember(session.shoeName, matchedShoe) {
        if (matchedShoe != null) {
            String.format(Locale.US, "%s (%.1f km)", matchedShoe.fullDisplayName(), matchedShoe.totalDistanceKm)
        } else if (!session.shoeName.isNullOrBlank()) {
            session.shoeName
        } else {
            "Definir tênis"
        }
    }

    val dayTitle = RunningProgramManager.getDisplayTitleForSession(session)
    val badgeLabel = RunningProgramManager.getBadgeLabelForSession(session)

    val envLabel = if (session.isGentleMode) "MODO LEVE"
                   else if (session.environment == "TREADMILL") "$badgeLabel • ESTEIRA"
                   else "$badgeLabel • AR LIVRE"

    val envBg = when (badgeLabel) {
        "LIVRE" -> Color(0xFF00E5FF).copy(alpha = 0.15f)
        "PISTA" -> Color(0xFF00E676).copy(alpha = 0.15f)
        "LEVE" -> RunColors.phaseWalk.copy(alpha = 0.15f)
        else -> RunColors.brand.copy(alpha = 0.15f)
    }

    val envTextColor = when (badgeLabel) {
        "LIVRE" -> Color(0xFF00E5FF)
        "PISTA" -> Color(0xFF00E676)
        "LEVE" -> RunColors.phaseWalk
        else -> RunColors.brandText
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onSessionClick != null) Modifier.clickable { onSessionClick() } else Modifier
            ),
        colors = CardDefaults.cardColors(containerColor = RunColors.surface),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, RunColors.border)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Linha 1: "Semana N · Dia D" + Tag Ambiente + Data "dd.mm.aa HH:mm"
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Text(
                        text = dayTitle,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    Surface(
                        color = envBg,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = envLabel,
                            color = envTextColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                Text(
                    text = dateStr,
                    color = RunColors.textSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    softWrap = false
                )
            }

            // Linha 2 (EM DESTAQUE): Dados do Treino Principal
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = RunColors.brand.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "PRINCIPAL",
                        color = RunColors.brandText,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
                Text(
                    text = String.format(ptBr, "%.2f km", mainDistKm),
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    softWrap = false
                )
                Text("•", color = RunColors.textSecondary, fontSize = 11.sp)
                Text(
                    text = mainDurationText,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false
                )
                Text("•", color = RunColors.textSecondary, fontSize = 11.sp)
                Text(
                    text = mainPaceText,
                    color = RunColors.brandText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false
                )
            }

            // Linha 2.5 (SUBTÍTULO MENOR): Dados Totais com Aquecimento/Desaquecimento
            Text(
                text = String.format(
                    ptBr,
                    "Sessão Total (c/ Aq. + Desaq.): %.2f km • %s • Pace %s",
                    totalDistKm,
                    totalDurationText,
                    totalPaceText
                ),
                color = RunColors.textSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                softWrap = false,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )

            // Linha 3 (Rodapé do Card): Tênis (com KM atualizado) + Ações
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = Color(0xFF14161D),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(0.5.dp, RunColors.border),
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .padding(end = 8.dp)
                        .then(
                            if (onEditShoeClick != null) Modifier.clickable { onEditShoeClick() } else Modifier
                        )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.DirectionsRun,
                            contentDescription = null,
                            tint = RunColors.brandText,
                            modifier = Modifier.size(11.dp)
                        )
                        Text(
                            text = shoeDisplayText,
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isSavedAsOfficialRace) {
                        Surface(
                            color = Color(0xFF00E676).copy(alpha = 0.16f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color(0xFF00E676).copy(alpha = 0.6f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.EmojiEvents,
                                    contentDescription = null,
                                    tint = Color(0xFF00E676),
                                    modifier = Modifier.size(11.dp)
                                )
                                Text(
                                    text = "PROVA",
                                    color = Color(0xFF00E676),
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Black,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = {
                            val bitmap = generateCardBitmap(
                                context = context,
                                session = session,
                                photoUri = null,
                                templateResId = com.example.R.drawable.template_1
                            )
                            saveCardToGallery(context, bitmap)
                        },
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(RunColors.surface)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileDownload,
                            contentDescription = "Salvar PNG",
                            tint = RunColors.brandText,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    if (onShareClick != null) {
                        IconButton(
                            onClick = onShareClick,
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(RunColors.brand.copy(alpha = 0.2f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Compartilhar Card",
                                tint = RunColors.brand,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    if (onDeleteClick != null) {
                        IconButton(
                            onClick = onDeleteClick,
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF14161D))
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = "Excluir registro de treino",
                                tint = RunColors.textSecondary.copy(alpha = 0.75f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HoldToStopButton(
    onConfirm: () -> Unit,
    onInteraction: () -> Unit,
    modifier: Modifier = Modifier,
    holdDurationMs: Long = 1500L
) {
    val context = LocalContext.current
    var isHolding by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var showHint by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    var holdJob by remember { mutableStateOf<Job?>(null) }
    var hintJob by remember { mutableStateOf<Job?>(null) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .semantics {
                    contentDescription = "Segure para parar o treino"
                    onLongClick("Parar treino") {
                        onConfirm()
                        true
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            onInteraction()
                            hintJob?.cancel()
                            showHint = false

                            isHolding = true
                            progress = 0f
                            val startTime = SystemClock.elapsedRealtime()
                            holdJob?.cancel()
                            holdJob = scope.launch {
                                while (isActive) {
                                    val elapsed = SystemClock.elapsedRealtime() - startTime
                                    progress = (elapsed.toFloat() / holdDurationMs).coerceIn(0f, 1f)
                                    if (elapsed >= holdDurationMs) {
                                        isHolding = false
                                        progress = 1f
                                        try {
                                            val vibrator = ContextCompat.getSystemService(context, Vibrator::class.java)
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                                vibrator?.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
                                            } else {
                                                @Suppress("DEPRECATION")
                                                vibrator?.vibrate(100)
                                            }
                                        } catch (_: Exception) {}
                                        AudioSynth.playTickSound()
                                        onConfirm()
                                        break
                                    }
                                    delay(16L)
                                }
                            }
                            try {
                                awaitRelease()
                            } finally {
                                val pressDuration = SystemClock.elapsedRealtime() - startTime
                                holdJob?.cancel()
                                holdJob = null
                                isHolding = false
                                val completed = progress >= 1f
                                progress = 0f

                                if (!completed && pressDuration < 300L) {
                                    hintJob?.cancel()
                                    hintJob = scope.launch {
                                        showHint = true
                                        delay(2000L)
                                        showHint = false
                                    }
                                }
                            }
                        }
                    )
                }
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = CircleShape,
                color = RunColors.surface,
                border = BorderStroke(1.5.dp, RunColors.border)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "Segure para parar o treino",
                        tint = RunColors.alert,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            if (isHolding && progress > 0f) {
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxSize(),
                    color = RunColors.alert,
                    strokeWidth = 3.dp,
                    trackColor = Color.Transparent
                )
            }
        }

        Text(
            text = if (showHint) "Segure para encerrar o treino" else "Parar",
            color = if (showHint) RunColors.alert else Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Visible
        )
    }
}

@Composable
fun RunningHUD(
    plan: RunningWorkoutPlan,
    dayNumber: Int = 1,
    sessionIdForCheckpoint: String = "auto_checkpoint_session",
    sessionStartTimestampMs: Long = 0L,
    defaultShoeName: String = "Olympikus Corre 4",
    environment: String = "OUTDOOR",
    baselinePaceMinPerKm: Float? = null,
    athleteAge: Int? = null,
    calibratedMaxHrBpm: Int? = null,
    calibratedLt1Bpm: Int? = null,
    calibratedLt2Bpm: Int? = null,
    isGentleMode: Boolean = false,
    coachVoiceEnabled: Boolean = true,
    hudFaceStyle: com.example.data.model.RunHudFaceStyle = com.example.data.model.RunHudFaceStyle.CLEAN_PRO,
    onEnvironmentChanged: (String) -> Unit = {},
    onFinishSession: (
        durationSec: Int,
        skippedIndices: Set<Int>,
        mainRunDurationSec: Int,
        mainRunDistanceMeters: Int,
        recordedSplits: List<RecordedKmSplit>,
        hrTimelineStr: String
    ) -> Unit,
    onCancelSession: () -> Unit
) {
    val context = LocalContext.current
    val bleManager = remember { HeartRateBleManager.getInstance(context) }

    var mainRunDurationSeconds by rememberSaveable { mutableIntStateOf(0) }
    var mainRunDistanceMeters by rememberSaveable { mutableIntStateOf(0) }
    var lastTrackedGpsMeters by rememberSaveable { mutableIntStateOf(0) }
    var savedSplitsJson by rememberSaveable { mutableStateOf("") }
    val liveRecordedSplits = remember {
        mutableStateListOf<RecordedKmSplit>().apply {
            if (savedSplitsJson.isNotBlank()) {
                addAll(savedSplitsJson.toRecordedKmSplits())
            }
        }
    }
    var lastSplitDistanceMeters by rememberSaveable { mutableIntStateOf(0) }
    var lastSplitElapsedSec by rememberSaveable { mutableIntStateOf(0) }
    val splitHrSamples = remember { mutableListOf<Int>() }

    // Escudo de Foco em 3 Camadas (Modo Munhequeira / Anti-Câmera Motorola)
    val activity = remember(context) {
        context as? android.app.Activity ?: (context as? android.content.ContextWrapper)?.baseContext as? android.app.Activity
    }
    var isWristLockActive by rememberSaveable { mutableStateOf(true) } // Habilitado por padrão na corrida
    var isScreenPinned by rememberSaveable { mutableStateOf(false) }
    var isSystemGpsProviderOn by remember { mutableStateOf(RunTracker.isSystemGpsEnabled(context)) }
    var hasAnnouncedSystemGpsOffInHud by rememberSaveable { mutableStateOf(false) }
    var isLiveMapEnabled by rememberSaveable { mutableStateOf(false) }
    var isLiveMapCompactCard by rememberSaveable { mutableStateOf(true) }
    var liveMapLayerStyle by rememberSaveable { mutableStateOf(com.example.ui.components.LiveRunMapLayerStyle.SATELLITE) }
    var isLiveMap3d by rememberSaveable { mutableStateOf(false) }
    var liveMapRecenterTrigger by remember { mutableIntStateOf(0) }
    var hasUserPannedLiveMap by remember { mutableStateOf(false) }
    val hudScope = rememberCoroutineScope()

    LaunchedEffect(isWristLockActive) {
        com.example.MainActivity.isWristGuardEnabled = isWristLockActive
    }

    // Camada 1 (Flags de Janela de Alta Prioridade) + Camada 2 (Auto-Recuperação de Foco em 450ms)
    val hudLifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(hudLifecycleOwner, activity) {
        com.example.MainActivity.isWristGuardEnabled = isWristLockActive
        com.example.MainActivity.updateRunningSessionWindowShield(activity, true)

        val lifecycleObserver = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE ||
                event == androidx.lifecycle.Lifecycle.Event.ON_STOP
            ) {
                if (com.example.MainActivity.isRunningSessionActive &&
                    com.example.MainActivity.isWristGuardEnabled &&
                    !com.example.MainActivity.isWithinIntentionalExternalNav()
                ) {
                    hudScope.launch {
                        delay(450L)
                        runCatching {
                            if (com.example.MainActivity.isRunningSessionActive &&
                                com.example.MainActivity.isWristGuardEnabled &&
                                !com.example.MainActivity.isAppInForeground &&
                                !com.example.MainActivity.isWithinIntentionalExternalNav()
                            ) {
                                val frontIntent = Intent(context, com.example.MainActivity::class.java).apply {
                                    addFlags(
                                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                                        Intent.FLAG_ACTIVITY_NEW_TASK
                                    )
                                }
                                context.startActivity(frontIntent)
                            }
                        }
                    }
                }
            } else if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                isSystemGpsProviderOn = RunTracker.isSystemGpsEnabled(context)
                com.example.MainActivity.updateRunningSessionWindowShield(activity, true)
                if (environment == "OUTDOOR" &&
                    isSystemGpsProviderOn &&
                    RunTracker.hasLocationPermission(context) &&
                    !RunTracker.isCurrentlyPaused() &&
                    !RunningTrackingService.userRequestedStopFromNotification.value
                ) {
                    runCatching { RunTracker.resumeTrackingIfStopped(context) }
                }
                runCatching {
                    val isMusicActiveNow = (context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager)?.isMusicActive == true
                    RunTracker.updateSystemMusicPlayingState(isMusicActiveNow)
                    com.example.services.MediaMetadataListenerService.refreshMediaSessions(context)
                }
            }
        }
        hudLifecycleOwner.lifecycle.addObserver(lifecycleObserver)

        onDispose {
            runCatching { hudLifecycleOwner.lifecycle.removeObserver(lifecycleObserver) }
            com.example.MainActivity.updateRunningSessionWindowShield(activity, false)
        }
    }

    val blocks = plan.blocks
    var skippedBlockIndices by rememberSaveable { mutableStateOf(setOf<Int>()) }
    val freeRaceMode = remember(plan.sessionType) {
        com.example.engine.FreeRunRaceMode.fromSessionType(plan.sessionType)
    }
    val isTrack400mMode = remember(plan.sessionType) {
        RunningProgramManager.isTrack400mSessionType(plan.sessionType)
    }
    val isFreeOrRaceMode = freeRaceMode != null
    val raceTargetMeters = freeRaceMode?.targetDistanceMeters
    var hasAnnouncedFinal500mSprint by rememberSaveable { mutableStateOf(false) }
    var hasAnnouncedRaceTargetReached by rememberSaveable { mutableStateOf(false) }

    // Estados do Modo Intervalado de Pista 400m (Laps por Distância + Descanso com Disparo Manual)
    var trackLapNumber by rememberSaveable { mutableIntStateOf(1) }
    var isTrackInRestState by rememberSaveable { mutableStateOf(false) }
    var isTrackRestPaused by rememberSaveable { mutableStateOf(false) }
    var trackLapStartGpsMeters by rememberSaveable { mutableIntStateOf(0) }
    var trackLapElapsedSec by rememberSaveable { mutableIntStateOf(0) }
    var trackRestElapsedSec by rememberSaveable { mutableIntStateOf(0) }
    var trackTargetRestSec by rememberSaveable { mutableIntStateOf(120) } // 2:00 sugerido de recuperação (ajustável +30s)
    var showHudFullscreenHrModal by remember { mutableStateOf(false) }

    val activeWorkoutDurationSec = remember(blocks, skippedBlockIndices) {
        blocks.indices.filter { it !in skippedBlockIndices }.sumOf { blocks[it].durationSec }
    }

    var accumulatedElapsedMs by rememberSaveable { mutableLongStateOf(0L) }
    var lastTickRealtime by rememberSaveable { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    var isPaused by rememberSaveable { mutableStateOf(false) }
    var showExitConfirmationDialog by rememberSaveable { mutableStateOf(false) }

    var lastAnnouncedBlockIndex by rememberSaveable { mutableIntStateOf(-1) }
    var lastAnnouncedSecLeft by rememberSaveable { mutableIntStateOf(-1) }
    var hasFinishedSession by rememberSaveable { mutableStateOf(false) }

    val runTrackState by RunTracker.state.collectAsState()
    val mediaTrackInfo by RunTracker.mediaTrackInfo.collectAsState()
    var showMediaMetadataPermissionDialog by rememberSaveable { mutableStateOf(false) }

    // Escuta MediaSession ativa (YouTube Music, Spotify, Deezer, Samsung Music) + AudioPlaybackCallback + Broadcasts OEM
    DisposableEffect(context) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
        runCatching {
            RunTracker.updateSystemMusicPlayingState(audioManager?.isMusicActive == true)
            com.example.services.MediaMetadataListenerService.refreshMediaSessions(context)
        }

        val playbackCallback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            object : android.media.AudioManager.AudioPlaybackCallback() {
                override fun onPlaybackConfigChanged(configs: MutableList<android.media.AudioPlaybackConfiguration>?) {
                    super.onPlaybackConfigChanged(configs)
                    val activeNow = audioManager?.isMusicActive == true
                    RunTracker.updateSystemMusicPlayingState(activeNow)
                    com.example.services.MediaMetadataListenerService.refreshMediaSessions(context)
                }
            }.also { cb ->
                runCatching { audioManager?.registerAudioPlaybackCallback(cb, null) }
            }
        } else null

        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent == null) return
                val extras = intent.extras
                val track = intent.getStringExtra("track")
                    ?: intent.getStringExtra("title")
                    ?: intent.getStringExtra("song")
                    ?: intent.getStringExtra("android.media.metadata.TITLE")
                    ?: extras?.getCharSequence("track")?.toString()
                    ?: extras?.getCharSequence("title")?.toString()
                val artist = intent.getStringExtra("artist")
                    ?: intent.getStringExtra("author")
                    ?: intent.getStringExtra("albumArtist")
                    ?: intent.getStringExtra("android.media.metadata.ARTIST")
                    ?: extras?.getCharSequence("artist")?.toString()
                val isPlaying = when {
                    intent.hasExtra("playing") -> intent.getBooleanExtra("playing", false)
                    intent.hasExtra("playstate") -> intent.getBooleanExtra("playstate", false)
                    intent.hasExtra("isPlaying") -> intent.getBooleanExtra("isPlaying", false)
                    else -> audioManager?.isMusicActive
                }
                RunTracker.updateMediaTrackInfo(track, artist, isPlaying)
                com.example.services.MediaMetadataListenerService.refreshMediaSessions(context)
            }
        }
        val filter = android.content.IntentFilter().apply {
            addAction("com.android.music.metachanged")
            addAction("com.android.music.playstatechanged")
            addAction("com.android.music.playbackcomplete")
            addAction("com.android.music.queuechanged")
            addAction("com.spotify.music.metadatachanged")
            addAction("com.spotify.music.playbackstatechanged")
            addAction("com.sec.android.app.music.metachanged")
            addAction("com.sec.android.app.music.playstatechanged")
            addAction("com.samsung.sec.android.MusicPlayer.metachanged")
            addAction("com.google.android.music.metachanged")
            addAction("com.google.android.music.playstatechanged")
            addAction("com.google.android.apps.youtube.music.metachanged")
            addAction("deezer.android.app.metachanged")
            addAction("com.amazon.mp3.metachanged")
            addAction("com.miui.player.metachanged")
        }
        runCatching {
            ContextCompat.registerReceiver(
                context,
                receiver,
                filter,
                ContextCompat.RECEIVER_EXPORTED
            )
        }
        onDispose {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && playbackCallback != null) {
                runCatching { audioManager?.unregisterAudioPlaybackCallback(playbackCallback) }
            }
            runCatching { context.unregisterReceiver(receiver) }
        }
    }

    // Câmera ao Vivo durante a Corrida (Estilo Relive): captura foto sem pausar o treino e anexa ao ponto GPS e Km atuais
    // Persiste o caminho da captura via rememberSaveable + SharedPreferences para sobreviver a recomposições e recriação ao retornar da câmera
    var pendingCameraCapturePath by rememberSaveable(sessionIdForCheckpoint) {
        mutableStateOf(
            runCatching {
                com.example.ui.components.RunRoutePhotoManager.loadPendingLiveCameraCapturePath(context, sessionIdForCheckpoint)
            }.getOrNull()
        )
    }
    var livePhotoCount by remember(sessionIdForCheckpoint) {
        mutableIntStateOf(
            runCatching {
                com.example.ui.components.RunRoutePhotoManager.loadPhotosForSession(context, sessionIdForCheckpoint).size
            }.getOrDefault(0)
        )
    }
    val liveRunCameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { captured ->
        val resolvedPath = pendingCameraCapturePath
            ?: com.example.ui.components.RunRoutePhotoManager.loadPendingLiveCameraCapturePath(context, sessionIdForCheckpoint)
        val file = resolvedPath?.let { java.io.File(it) }
        if (captured && file != null && file.exists() && file.length() > 0L) {
            val lastGps = RunTracker.recordedPoints.lastOrNull()
            val estTotalDistM = maxOf(
                (activeWorkoutDurationSec.coerceAtLeast(900) / 60) * 145,
                runTrackState.distanceMeters + 300,
                1500
            )
            val updatedPins = com.example.ui.components.RunRoutePhotoManager.addLiveCameraPhotoToSession(
                context = context,
                sessionId = sessionIdForCheckpoint,
                capturedUri = android.net.Uri.fromFile(file),
                currentLat = lastGps?.latitude ?: 0.0,
                currentLon = lastGps?.longitude ?: 0.0,
                currentDistanceMeters = runTrackState.distanceMeters,
                estimatedTotalDistanceMeters = estTotalDistM,
                currentPaceSecPerKm = runTrackState.currentPaceSecPerKm
            )
            livePhotoCount = updatedPins.size
            pendingCameraCapturePath = null
            com.example.ui.components.RunRoutePhotoManager.clearPendingLiveCameraCapturePath(context, sessionIdForCheckpoint)
            val kmNow = String.format(Locale("pt", "BR"), "%.2f", runTrackState.distanceMeters / 1000f)
            Toast.makeText(
                context,
                "Foto salva no Km $kmNow do percurso! Ela aparecerá no voo Cine 3D.",
                Toast.LENGTH_SHORT
            ).show()
        } else {
            if (!captured && file != null && file.exists() && file.length() == 0L) {
                runCatching { file.delete() }
            }
            pendingCameraCapturePath = null
            com.example.ui.components.RunRoutePhotoManager.clearPendingLiveCameraCapturePath(context, sessionIdForCheckpoint)
        }
    }

    fun releaseScreenPinningIfNeeded() {
        if (isScreenPinned || com.example.MainActivity.isScreenPinningActive) {
            runCatching { activity?.stopLockTask() }
            isScreenPinned = false
            com.example.MainActivity.isScreenPinningActive = false
        }
        com.example.MainActivity.updateRunningSessionWindowShield(activity, false)
    }

    fun finishSessionWithMetrics(elapsedSec: Int) {
        runCatching { releaseScreenPinningIfNeeded() }
        runCatching {
            val currentGpsM = runTrackState.distanceMeters
            val remainingDist = currentGpsM - lastSplitDistanceMeters
            val remainingSec = (elapsedSec - lastSplitElapsedSec).coerceAtLeast(1)
            if (remainingDist >= 30) {
                val fracLabel = String.format(Locale.US, "%.2f km", remainingDist / 1000.0)
                val pace = ((remainingSec.toDouble() / remainingDist.toDouble()) * 1000.0).safeRoundToInt()
                val avgBpm = if (splitHrSamples.isNotEmpty()) splitHrSamples.average().safeRoundToInt() else bleManager.currentBpm.value.takeIf { it > 0 }
                liveRecordedSplits.add(
                    RecordedKmSplit(
                        splitNumber = liveRecordedSplits.size + 1,
                        splitLabel = fracLabel,
                        distanceMeters = remainingDist,
                        durationSec = remainingSec,
                        paceSecPerKm = pace,
                        avgHr = avgBpm
                    )
                )
            }
        }
        onFinishSession(
            elapsedSec.coerceAtLeast(1),
            skippedBlockIndices,
            mainRunDurationSeconds,
            mainRunDistanceMeters,
            liveRecordedSplits.toList(),
            runCatching { bleManager.getHrTimelineString() }.getOrDefault("")
        )
    }

    fun calculateBlockInfo(elapsedSec: Int): Pair<Int, Int> {
        if (isGentleMode || isFreeOrRaceMode || isTrack400mMode || blocks.isEmpty()) {
            return Pair(0, elapsedSec)
        }
        var acc = 0
        var foundIdx = -1
        var secLeft = 0
        for (i in blocks.indices) {
            if (i in skippedBlockIndices) continue
            val bSec = blocks[i].durationSec
            if (elapsedSec < acc + bSec) {
                foundIdx = i
                secLeft = (acc + bSec) - elapsedSec
                break
            }
            acc += bSec
        }
        return if (foundIdx == -1) {
            val lastActive = blocks.indices.lastOrNull { it !in skippedBlockIndices } ?: (blocks.size - 1).coerceAtLeast(0)
            Pair(lastActive, 0)
        } else {
            Pair(foundIdx, secLeft)
        }
    }

    // Computação do tempo decorrido baseado no relógio real do sistema (SystemClock.elapsedRealtime)
    // Usando derivedStateOf para recompor a UI apenas a cada 1 segundo inteiro (evitando recomposição a 4Hz do loop de 250ms)
    val totalElapsedSeconds by remember(isPaused) {
        derivedStateOf {
            val currentRealtime = SystemClock.elapsedRealtime()
            val totalElapsedMs = accumulatedElapsedMs + if (!isPaused) (currentRealtime - lastTickRealtime).coerceAtLeast(0L) else 0L
            (totalElapsedMs / 1000L).toInt()
        }
    }

    val (currentBlockIndex, secondsLeftInBlock) = remember(totalElapsedSeconds, blocks, skippedBlockIndices, isGentleMode, isFreeOrRaceMode) {
        calculateBlockInfo(totalElapsedSeconds)
    }

    val currentBlock = blocks.getOrNull(currentBlockIndex) ?: blocks.lastOrNull() ?: RunningBlock(RunningBlockType.RUN, 60, "Treino")

    val notificationStopRequested by RunningTrackingService.userRequestedStopFromNotification.collectAsState()

    LaunchedEffect(notificationStopRequested) {
        if (notificationStopRequested && !hasFinishedSession) {
            RunningTrackingService.clearUserRequestedStopFlag()
            hasFinishedSession = true
            finishSessionWithMetrics(totalElapsedSeconds)
        }
    }

    LaunchedEffect(isPaused, environment, isGentleMode) {
        if (isPaused) {
            RunTracker.pause()
        } else {
            RunTracker.resume()
            if (environment == "OUTDOOR" &&
                RunTracker.hasLocationPermission(context) &&
                !RunningTrackingService.userRequestedStopFromNotification.value
            ) {
                runCatching { RunTracker.resumeTrackingIfStopped(context) }
            }
        }
    }

    // Target Speed calculation for treadmill using calibrated baseline pace
    val targetPaceMinutes = remember(plan.weekNumber, baselinePaceMinPerKm, isGentleMode) {
        if (isGentleMode) {
            13.3f // ~4.5 km/h para caminhada leve
        } else {
            RunningProgramManager.calculateTargetPaceForWeek(plan.weekNumber, baselinePaceMinPerKm)
        }
    }
    val targetSpeedKmh = remember(targetPaceMinutes) {
        String.format(Locale.US, "%.1f", 60f / targetPaceMinutes.coerceAtLeast(1.0f))
    }

    // Haptic & Sound trigger helper (Sino de Boxe real R.raw.sino_boxe na virada de bloco/tiro)
    fun triggerBlockTransitionFeedback() {
        try {
            com.example.utils.MaxSoundManager.playBoxingBell(context)
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

            vibrator?.let { v ->
                if (v.hasVibrator()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        v.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        @Suppress("DEPRECATION")
                        v.vibrate(500)
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore if vibration permissions or hardware unavailable
        }
    }

    // Haptic reminder for pause (two short pulses ~120ms, pause ~120ms, NO tick sound)
    fun triggerPauseReminderFeedback() {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

            vibrator?.let { v ->
                if (v.hasVibrator()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 120, 120, 120), -1))
                    } else {
                        @Suppress("DEPRECATION")
                        v.vibrate(longArrayOf(0, 120, 120, 120), -1)
                    }
                }
            }
        } catch (_: Exception) {}
    }

    // Haptic feedback for slow pace (2 long pulses: ~400ms each)
    fun triggerSlowPaceFeedback() {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

            vibrator?.let { v ->
                if (v.hasVibrator()) {
                    val pattern = longArrayOf(0, 400, 200, 400)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        v.vibrate(VibrationEffect.createWaveform(pattern, -1))
                    } else {
                        @Suppress("DEPRECATION")
                        v.vibrate(pattern, -1)
                    }
                }
            }
        } catch (_: Exception) {}
    }

    // Haptic feedback for fast pace (3 short quick pulses: ~100ms each)
    fun triggerFastPaceFeedback() {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

            vibrator?.let { v ->
                if (v.hasVibrator()) {
                    val pattern = longArrayOf(0, 100, 100, 100, 100, 100)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        v.vibrate(VibrationEffect.createWaveform(pattern, -1))
                    } else {
                        @Suppress("DEPRECATION")
                        v.vibrate(pattern, -1)
                    }
                }
            }
        } catch (_: Exception) {}
    }

    // TextToSpeech setup for Coach Voice com inicialização preguiçosa (lazy) e UtteranceProgressListener para liberar Audio Ducking (Regra 7)
    // Só instancia o motor TTS e solicita AudioFocus quando isRunningSessionActive == true e no exato milissegundo em que uma frase real precisar ser falada.
    var ttsEngine by remember { mutableStateOf<TextToSpeech?>(null) }
    var isInitializingTts by remember { mutableStateOf(false) }
    var lastCriticalSpeechRealtimeMs by remember { mutableLongStateOf(0L) }
    var pendingDeferredSplitAnnouncement by remember { mutableStateOf<String?>(null) }

    DisposableEffect(context) {
        com.example.MainActivity.isRunningSessionActive = true
        onDispose {
            try {
                ttsEngine?.stop()
                ttsEngine?.shutdown()
            } catch (_: Exception) {}
            ttsEngine = null
            isInitializingTts = false
            AudioSynth.abandonAudioFocus(context)
        }
    }

    // Watchdog periódico dedicado (Regra 7): restaura o foco de áudio da música
    // caso tts.isSpeaking seja false por mais de 500ms (exclusivo durante sessão de corrida ativa)
    LaunchedEffect(Unit) {
        while (isActive) {
            delay(200L)
            if (com.example.MainActivity.isRunningSessionActive) {
                val speaking = try {
                    ttsEngine?.isSpeaking == true
                } catch (_: Exception) {
                    false
                }
                AudioSynth.checkDuckingWatchdog(context, isTtsSpeaking = speaking)
            }
        }
    }

    /**
     * Regra 1 & Regra 4: Hierarquia Estrita, Isolamento Exclusivo do MAX RUN e Inicialização Lazy de TTS.
     * - SÓ pode instanciar TTS ou disparar AudioFocus quando `MainActivity.isRunningSessionActive == true`.
     * - Inicializa o TTS apenas no exato milissegundo em que a primeira frase real precisar ser falada.
     */
    fun speakCoach(
        message: String,
        isHighPriority: Boolean = true
    ) {
        if (!com.example.MainActivity.isRunningSessionActive || !coachVoiceEnabled || message.isBlank()) return
        val engine = ttsEngine
        if (engine != null) {
            try {
                if (isHighPriority) {
                    lastCriticalSpeechRealtimeMs = SystemClock.elapsedRealtime()
                    AudioSynth.requestAudioFocus(context)
                    val speakStatus = engine.speak(
                        message,
                        TextToSpeech.QUEUE_FLUSH,
                        null,
                        "running_coach_crit_${System.currentTimeMillis()}"
                    )
                    if (speakStatus != TextToSpeech.SUCCESS) {
                        AudioSynth.abandonAudioFocus(context)
                    }
                } else {
                    AudioSynth.requestAudioFocus(context)
                    val speakStatus = engine.speak(
                        message,
                        TextToSpeech.QUEUE_ADD,
                        null,
                        "running_coach_sec_${System.currentTimeMillis()}"
                    )
                    if (speakStatus != TextToSpeech.SUCCESS) {
                        AudioSynth.abandonAudioFocus(context)
                    }
                }
            } catch (_: Exception) {
                AudioSynth.abandonAudioFocus(context)
            }
            return
        }

        if (!isInitializingTts) {
            isInitializingTts = true
            runCatching {
                var tts: TextToSpeech? = null
                tts = TextToSpeech(context.applicationContext) { status ->
                    isInitializingTts = false
                    runCatching {
                        if (status == TextToSpeech.SUCCESS && com.example.MainActivity.isRunningSessionActive) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                tts?.setAudioAttributes(
                                    android.media.AudioAttributes.Builder()
                                        .setUsage(android.media.AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                                        .build()
                                )
                            }
                            tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                                override fun onStart(utteranceId: String?) {}
                                override fun onDone(utteranceId: String?) {
                                    AudioSynth.abandonAudioFocus(context)
                                }
                                @Deprecated("Deprecated in Java")
                                override fun onError(utteranceId: String?) {
                                    AudioSynth.abandonAudioFocus(context)
                                }
                                override fun onError(utteranceId: String?, errorCode: Int) {
                                    AudioSynth.abandonAudioFocus(context)
                                }
                                override fun onStop(utteranceId: String?, interrupted: Boolean) {
                                    if (!interrupted) {
                                        AudioSynth.abandonAudioFocus(context)
                                    }
                                }
                            })
                            val ptBr = Locale.forLanguageTag("pt-BR")
                            val result = tts?.setLanguage(ptBr)
                            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                                tts?.language = Locale.US
                            }
                            ttsEngine = tts
                            if (com.example.MainActivity.isRunningSessionActive && coachVoiceEnabled) {
                                try {
                                    lastCriticalSpeechRealtimeMs = SystemClock.elapsedRealtime()
                                    AudioSynth.requestAudioFocus(context)
                                    val speakStatus = tts?.speak(
                                        message,
                                        if (isHighPriority) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD,
                                        null,
                                        "running_coach_init_${System.currentTimeMillis()}"
                                    )
                                    if (speakStatus != TextToSpeech.SUCCESS) {
                                        AudioSynth.abandonAudioFocus(context)
                                    }
                                } catch (_: Exception) {
                                    AudioSynth.abandonAudioFocus(context)
                                }
                            }
                        } else {
                            runCatching { tts?.shutdown() }
                        }
                    }.onFailure {
                        AudioSynth.abandonAudioFocus(context)
                    }
                }
            }.onFailure {
                isInitializingTts = false
            }
        }
    }

    // Watchdog contínuo de GPS no HUD: garante que o motor GPS esteja ativo do segundo 0 (aquecimento + tiros)
    // e monitora se o usuário desligou a localização na barra do Android
    LaunchedEffect(environment, isGentleMode, isPaused, ttsEngine) {
        if (environment == "OUTDOOR" && !isPaused) {
            while (isActive) {
                val sysGps = RunTracker.isSystemGpsEnabled(context)
                isSystemGpsProviderOn = sysGps
                if (!sysGps) {
                    if (!hasAnnouncedSystemGpsOffInHud && ttsEngine != null) {
                        hasAnnouncedSystemGpsOffInHud = true
                        speakCoach(
                            "Atenção: ative a localização do celular para registrar a distância",
                            isHighPriority = true
                        )
                    }
                } else {
                    hasAnnouncedSystemGpsOffInHud = false
                    if (RunTracker.hasLocationPermission(context) &&
                        !RunTracker.isCurrentlyTracking() &&
                        !RunTracker.isCurrentlyPaused() &&
                        !RunningTrackingService.userRequestedStopFromNotification.value
                    ) {
                        runCatching { RunTracker.resumeTrackingIfStopped(context) }
                    }
                }
                delay(2000L)
            }
        }
    }

    var lastPaceAlertRealtime by remember { mutableLongStateOf(0L) }
    var paceOutOfZoneSinceRealtimeMs by remember { mutableLongStateOf(0L) }
    var highHrInFirstHalfSinceRealtimeMs by remember { mutableLongStateOf(0L) }
    var recoveryHighHrSinceRealtimeMs by remember { mutableLongStateOf(0L) }
    var lastPraisedBlockIndex by rememberSaveable { mutableIntStateOf(-1) }
    var currentBlockStartElapsedSec by remember { mutableIntStateOf(0) }
    var hasEnteredMainWorkoutPhase by rememberSaveable { mutableStateOf(false) }
    var mainWorkoutStartDistanceMeters by rememberSaveable { mutableIntStateOf(0) }
    var mainWorkoutStartTimeSec by rememberSaveable { mutableIntStateOf(0) }
    var lastHalfKmCheckpointIndex by rememberSaveable { mutableIntStateOf(-1) } // 0 -> 500m, 1 -> 1500m, 2 -> 2500m...
    var lastDistanceOrTimeMilestoneSec by rememberSaveable { mutableIntStateOf(0) }
    var lastThreeMinCheckpointSlot by rememberSaveable { mutableIntStateOf(0) }

    // (A) Cronômetro por relógio real (Ticker loop 250ms)
    LaunchedEffect(isPaused, isGentleMode) {
        if (!isPaused) {
            lastTickRealtime = SystemClock.elapsedRealtime()
            var secondAccumulatorMs = 0L
            while (!isPaused) {
                delay(250L)
                val now = SystemClock.elapsedRealtime()
                val delta = (now - lastTickRealtime).coerceAtLeast(0L)
                lastTickRealtime = now
                accumulatedElapsedMs += delta
                secondAccumulatorMs += delta

                val elapsedSec = (accumulatedElapsedMs / 1000L).toInt()

                // Watchdog de Audio Ducking também verificado a cada tick
                val isCurrentlySpeaking = try { ttsEngine?.isSpeaking == true } catch (_: Exception) { false }
                AudioSynth.checkDuckingWatchdog(context, isTtsSpeaking = isCurrentlySpeaking)

                // Identifica se estamos em fase de corrida ativa/principal (exclui Aquecimento e Desaquecimento)
                val (activeBlockIdx, secLeft) = calculateBlockInfo(elapsedSec)
                val activeBlock = blocks.getOrNull(activeBlockIdx) ?: blocks.lastOrNull()
                val activeBlockTypeName = when {
                    isTrack400mMode -> if (isTrackInRestState) "WALK" else "RUN"
                    isFreeOrRaceMode -> "RUN"
                    else -> activeBlock?.type?.name ?: if (isGentleMode) "WALK" else "RUN"
                }
                val isWarmupOrCooldownPhase = !isGentleMode && !isFreeOrRaceMode && !isTrack400mMode && RunTracker.isWarmupOrCooldown(activeBlockTypeName)
                val isMainRunPhase = if (isTrack400mMode) !isTrackInRestState else (isGentleMode || isFreeOrRaceMode || !isWarmupOrCooldownPhase)
                RunTracker.updateCurrentPhase(
                    blockTypeName = activeBlockTypeName,
                    blockIdx = if (isFreeOrRaceMode || isTrack400mMode) liveRecordedSplits.size else activeBlockIdx
                )

                val currentGpsM = runTrackState.distanceMeters

                // 0. Verifica transição de bloco ANTES do gatilho de 1 km para garantir isolamento imediato entre Aquecimento e Treino Principal
                if (!isGentleMode && !isFreeOrRaceMode && !isTrack400mMode) {
                    if (activeWorkoutDurationSec > 0 && elapsedSec >= activeWorkoutDurationSec) {
                        if (!hasFinishedSession) {
                            hasFinishedSession = true
                            showExitConfirmationDialog = false
                            val endedOnMainWithoutCooldown = activeBlock?.type != RunningBlockType.COOL_DOWN
                            triggerBlockTransitionFeedback()
                            if (endedOnMainWithoutCooldown) {
                                speakCoach(RunTracker.buildCoolDownTransitionAnnouncement(5), isHighPriority = true)
                            } else {
                                speakCoach("Treino concluído! Excelente!", isHighPriority = true)
                            }
                            finishSessionWithMetrics(elapsedSec)
                        }
                        break
                    }

                    if (activeBlockIdx != lastAnnouncedBlockIndex) {
                        lastAnnouncedBlockIndex = activeBlockIdx
                        currentBlockStartElapsedSec = elapsedSec
                        paceOutOfZoneSinceRealtimeMs = 0L
                        highHrInFirstHalfSinceRealtimeMs = 0L
                        recoveryHighHrSinceRealtimeMs = 0L
                        if (activeBlockIdx in blocks.indices) {
                            val nextB = blocks[activeBlockIdx]
                            // No exato instante em que o treino transita do Aquecimento (WARMUP) para o 1º bloco do Treino Principal (RUN/WALK),
                            // registra o marco zero do bloco principal (mainWorkoutStartDistanceMeters e mainWorkoutStartTimeSec)
                            // para que o 1º km do Áudio Coach meça exclusivamente 1,00 km inteiro dentro do Treino Principal!
                            if (nextB.type != RunningBlockType.WARMUP &&
                                nextB.type != RunningBlockType.COOL_DOWN &&
                                !hasEnteredMainWorkoutPhase
                            ) {
                                hasEnteredMainWorkoutPhase = true
                                mainWorkoutStartDistanceMeters = currentGpsM
                                mainWorkoutStartTimeSec = elapsedSec
                                mainRunDistanceMeters = 0
                                mainRunDurationSeconds = 0
                                lastTrackedGpsMeters = currentGpsM
                                lastSplitDistanceMeters = currentGpsM
                                lastSplitElapsedSec = elapsedSec
                                splitHrSamples.clear()
                                liveRecordedSplits.clear()
                                savedSplitsJson = "[]"
                                RunTracker.resetMainBlockPaceWindow()
                            }
                            val coolDownAnnouncement = RunTracker.onBlockTransition(
                                newBlockTypeName = nextB.type.name,
                                isTransitionFromLastMainToCoolDown = nextB.type == RunningBlockType.COOL_DOWN,
                                coolDownDurationSeconds = nextB.durationSec,
                                blockIdx = activeBlockIdx
                            )
                            val nextLabel = when (nextB.type) {
                                RunningBlockType.RUN -> "Correr!"
                                RunningBlockType.WALK -> "Caminhar!"
                                RunningBlockType.COOL_DOWN -> {
                                    coolDownAnnouncement ?: RunTracker.buildCoolDownTransitionAnnouncement(5)
                                }
                                RunningBlockType.WARMUP -> "Aquecimento!"
                            }
                            triggerBlockTransitionFeedback()
                            speakCoach(nextLabel, isHighPriority = true)
                        }
                    }
                }

                val secondsInCurrentBlock = (elapsedSec - currentBlockStartElapsedSec).coerceAtLeast(0)
                val isInTransitionShieldWindow = !isGentleMode && !isFreeOrRaceMode && !isTrack400mMode && RunTracker.isWithinBlockTransitionWindow(
                    blockElapsedSeconds = secondsInCurrentBlock,
                    blockRemainingSeconds = secLeft
                )

                // Incrementa duração da corrida ativa e contadores de Lap 400m / Descanso
                if (secondAccumulatorMs >= 1000L) {
                    val elapsedWholeSeconds = (secondAccumulatorMs / 1000L).toInt().coerceAtLeast(1)
                    secondAccumulatorMs %= 1000L
                    if (isMainRunPhase) {
                        mainRunDurationSeconds += elapsedWholeSeconds
                    }
                    if (isTrack400mMode) {
                        if (isTrackInRestState) {
                            if (!isTrackRestPaused) {
                                trackRestElapsedSec += elapsedWholeSeconds
                            }
                        } else {
                            trackLapElapsedSec += elapsedWholeSeconds
                        }
                    }
                    val curBpm = bleManager.currentBpm.value
                    if (curBpm > 0) {
                        splitHrSamples.add(curBpm)
                    }
                    runCatching {
                        val isSysMusicActive = (context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager)?.isMusicActive == true
                        RunTracker.updateSystemMusicPlayingState(isSysMusicActive)
                        if (isSysMusicActive && mediaTrackInfo.track.isBlank()) {
                            com.example.services.MediaMetadataListenerService.refreshMediaSessions(context)
                        }
                    }
                    if (elapsedSec >= 60 && elapsedSec % 5 == 0) {
                        val estFallback = RunTracker.estimateFallbackDistanceMeters(
                            plan = plan,
                            durationSec = elapsedSec,
                            skippedIndices = skippedBlockIndices,
                            baselinePaceMinPerKm = baselinePaceMinPerKm
                        )
                        RunTracker.saveActiveWorkoutCheckpoint(
                            context = context,
                            sessionId = sessionIdForCheckpoint,
                            startTimestampMs = sessionStartTimestampMs.takeIf { it > 0L } ?: System.currentTimeMillis(),
                            weekNumber = plan.weekNumber,
                            dayNumber = dayNumber,
                            sessionType = plan.sessionType,
                            environment = environment,
                            isGentleMode = isGentleMode,
                            shoeName = defaultShoeName,
                            elapsedSec = elapsedSec,
                            distanceMeters = runTrackState.distanceMeters,
                            fallbackEstimatedMeters = estFallback,
                            mainRunDurationSec = mainRunDurationSeconds,
                            mainRunDistanceMeters = mainRunDistanceMeters,
                            routePointsJson = if (RunTracker.recordedPoints.isNotEmpty()) RunTracker.getRoutePointsJson() else null,
                            kmSplitsJson = savedSplitsJson,
                            hrTimeline = runCatching { bleManager.getHrTimelineString() }.getOrDefault(""),
                            workoutBlockLogsJson = RunTracker.getBlockUnintendedWalkJson(),
                            skippedIndices = skippedBlockIndices,
                            pendingSummarySave = false,
                            forceImmediateSave = false
                        )
                    }
                }

                // Incrementa metros reais percorridos na fase principal (Work/Run)
                if (currentGpsM > lastTrackedGpsMeters) {
                    val deltaM = currentGpsM - lastTrackedGpsMeters
                    lastTrackedGpsMeters = currentGpsM
                    if (isMainRunPhase) {
                        mainRunDistanceMeters += deltaM
                    }
                }

                // MODO PISTA 400M: Verifica se atingiu exatamente 400 metros na Lap atual
                if (isTrack400mMode && !isTrackInRestState) {
                    val lapDistM = (currentGpsM - trackLapStartGpsMeters).coerceAtLeast(0)
                    if (lapDistM >= 400) {
                        val completedLapNum = trackLapNumber
                        val lapDurSec = trackLapElapsedSec.coerceAtLeast(1)
                        val lapPaceSecPerKm = ((lapDurSec * 1000.0) / 400.0).roundToInt()
                        val avgBpm = if (splitHrSamples.isNotEmpty()) splitHrSamples.average().toInt() else bleManager.currentBpm.value.takeIf { it > 0 }
                        liveRecordedSplits.add(
                            RecordedKmSplit(
                                splitNumber = completedLapNum,
                                splitLabel = "LAP $completedLapNum (400m)",
                                distanceMeters = 400,
                                durationSec = lapDurSec,
                                paceSecPerKm = lapPaceSecPerKm,
                                avgHr = avgBpm,
                                phaseTag = "LAP_400M|REST:0"
                            )
                        )
                        savedSplitsJson = liveRecordedSplits.toJsonString()
                        lastSplitDistanceMeters = currentGpsM
                        lastSplitElapsedSec = elapsedSec
                        splitHrSamples.clear()

                        isTrackInRestState = true
                        trackRestElapsedSec = 0
                        triggerBlockTransitionFeedback()
                        speakCoach(
                            RunTracker.buildTrack400mLapAnnouncement(completedLapNum, lapDurSec),
                            isHighPriority = true
                        )
                    }
                }

                // Gravação real de splits km a km (1.0 km, 2.0 km, etc.):
                // Em treinos estruturados com aquecimento, mede EXCLUSIVAMENTE a distância percorrida dentro do Treino Principal
                // (cada 1.000m completos a partir de mainWorkoutStartDistanceMeters / lastSplitDistanceMeters), nunca misturando
                // metros do aquecimento com o tempo do 1º tiro!
                val canRecordFullKmSplits = !isTrack400mMode && (
                    isFreeOrRaceMode || isGentleMode || (hasEnteredMainWorkoutPhase && !isWarmupOrCooldownPhase)
                )
                val nextKmTarget = if (isFreeOrRaceMode || isGentleMode) {
                    ((lastSplitDistanceMeters / 1000) + 1) * 1000
                } else {
                    mainWorkoutStartDistanceMeters + ((liveRecordedSplits.size + 1) * 1000)
                }
                if (canRecordFullKmSplits && currentGpsM >= nextKmTarget) {
                    val splitIdx = liveRecordedSplits.size + 1
                    val deltaDist = (currentGpsM - lastSplitDistanceMeters).coerceAtLeast(1)
                    val deltaTime = (elapsedSec - lastSplitElapsedSec).coerceAtLeast(1)
                    val rawPace = ((deltaTime.toDouble() / deltaDist.toDouble()) * 1000.0).roundToInt()
                    val mainWorkoutElapsedSec = (elapsedSec - mainWorkoutStartTimeSec).coerceAtLeast(1)
                    val mainWorkoutDistM = (currentGpsM - mainWorkoutStartDistanceMeters).coerceAtLeast(1)
                    val mainAvgPaceSec = ((mainWorkoutElapsedSec * 1000.0) / mainWorkoutDistM.toDouble()).roundToInt().coerceAtLeast(170)
                    // Trava de sanidade física: nunca registra ou anuncia parcial de 1 km mais rápida que 2'50"/km (170s/km)
                    val pace = if (rawPace < 170) maxOf(170, mainAvgPaceSec) else rawPace
                    val avgBpm = if (splitHrSamples.isNotEmpty()) splitHrSamples.average().toInt() else bleManager.currentBpm.value.takeIf { it > 0 }
                    liveRecordedSplits.add(
                        RecordedKmSplit(
                            splitNumber = splitIdx,
                            splitLabel = "${splitIdx},0 km",
                            distanceMeters = deltaDist.coerceAtLeast(1000),
                            durationSec = deltaTime,
                            paceSecPerKm = pace,
                            avgHr = avgBpm,
                            phaseTag = activeBlockTypeName
                        )
                    )
                    savedSplitsJson = liveRecordedSplits.toJsonString()
                    lastSplitDistanceMeters = currentGpsM
                    lastSplitElapsedSec = elapsedSec
                    lastDistanceOrTimeMilestoneSec = elapsedSec
                    splitHrSamples.clear()

                    // Anúncio de Voz (TTS) a cada 1.0 km concluído
                    if (isFreeOrRaceMode) {
                        val avgPaceSec = if (currentGpsM > 0) ((elapsedSec * 1000L) / currentGpsM).toInt().coerceAtLeast(170) else pace
                        val liveHr = bleManager.currentBpm.value.takeIf { it > 0 } ?: avgBpm
                        val splitMsg = RunTracker.buildFreeOrRaceKmSplitAnnouncement(
                            kmNumber = splitIdx,
                            splitPaceSecPerKm = pace,
                            globalAvgPaceSecPerKm = avgPaceSec,
                            totalElapsedSec = elapsedSec,
                            currentBpm = liveHr
                        )
                        if (isCurrentlySpeaking || (now - lastCriticalSpeechRealtimeMs < 2500L)) {
                            pendingDeferredSplitAnnouncement = splitMsg
                        } else {
                            speakCoach(splitMsg, isHighPriority = false)
                        }
                    } else if (!isWarmupOrCooldownPhase) {
                        val splitMsg = RunTracker.buildKmSplitAnnouncement(splitIdx, pace, mainAvgPaceSec)
                        if (isInTransitionShieldWindow || isCurrentlySpeaking || (now - lastCriticalSpeechRealtimeMs < 2500L)) {
                            pendingDeferredSplitAnnouncement = splitMsg
                        } else {
                            speakCoach(splitMsg, isHighPriority = false)
                        }
                    }
                }

                // Checkpoints intermediários de Voz na Corrida Livre / Modo Leve
                if ((isFreeOrRaceMode || isGentleMode) && !isTrack400mMode) {
                    val nextHalfKmIndex = lastHalfKmCheckpointIndex + 1
                    val nextHalfKmTargetMeters = (nextHalfKmIndex * 1000) + 500
                    val isFinal500mSprintMark = raceTargetMeters != null && nextHalfKmTargetMeters == (raceTargetMeters - 500)

                    if (currentGpsM >= nextHalfKmTargetMeters && currentGpsM < nextHalfKmTargetMeters + 350) {
                        lastHalfKmCheckpointIndex = nextHalfKmIndex
                        lastDistanceOrTimeMilestoneSec = elapsedSec
                        if (!isFinal500mSprintMark) {
                            val avgPaceSec = if (currentGpsM > 0) ((elapsedSec * 1000L) / currentGpsM).toInt() else (runTrackState.currentPaceSecPerKm ?: 0)
                            val recentPace = runTrackState.currentPaceSecPerKm?.takeIf { it > 0 } ?: avgPaceSec
                            val liveHr = bleManager.currentBpm.value.takeIf { it > 0 }
                            val halfKmMsg = RunTracker.buildFreeRunHalfKmOrTimeCheckpointAnnouncement(
                                distanceMeters = nextHalfKmTargetMeters,
                                recentPaceSecPerKm = recentPace,
                                sessionAvgPaceSecPerKm = avgPaceSec,
                                currentBpm = liveHr,
                                isGentleMode = isGentleMode,
                                isTimeTriggered = false
                            )
                            if (isCurrentlySpeaking || (now - lastCriticalSpeechRealtimeMs < 2500L)) {
                                pendingDeferredSplitAnnouncement = halfKmMsg
                            } else {
                                speakCoach(halfKmMsg, isHighPriority = false)
                            }
                        }
                    } else if (elapsedSec >= 180) {
                        val currentThreeMinSlot = elapsedSec / 180
                        if (currentThreeMinSlot > lastThreeMinCheckpointSlot) {
                            lastThreeMinCheckpointSlot = currentThreeMinSlot
                            val secSinceLastMilestone = elapsedSec - lastDistanceOrTimeMilestoneSec
                            val avgSpeedMps = if (elapsedSec > 0 && currentGpsM > 0) {
                                currentGpsM.toFloat() / elapsedSec.toFloat()
                            } else 0f
                            val metersToNextFullKm = (nextKmTarget - currentGpsM).coerceAtLeast(0)
                            val estSecToNextFullKm = if (avgSpeedMps > 0.4f) {
                                (metersToNextFullKm / avgSpeedMps).roundToInt()
                            } else 999

                            if (secSinceLastMilestone >= 90 && estSecToNextFullKm >= 60) {
                                lastDistanceOrTimeMilestoneSec = elapsedSec
                                val avgPaceSec = if (currentGpsM > 0) ((elapsedSec * 1000L) / currentGpsM).toInt() else (runTrackState.currentPaceSecPerKm ?: 600)
                                val recentPace = runTrackState.currentPaceSecPerKm?.takeIf { it > 0 } ?: avgPaceSec
                                val liveHr = bleManager.currentBpm.value.takeIf { it > 0 }
                                val timeCheckpointMsg = RunTracker.buildFreeRunHalfKmOrTimeCheckpointAnnouncement(
                                    distanceMeters = currentGpsM,
                                    recentPaceSecPerKm = recentPace,
                                    sessionAvgPaceSecPerKm = avgPaceSec,
                                    currentBpm = liveHr,
                                    isGentleMode = isGentleMode,
                                    isTimeTriggered = true,
                                    elapsedMinutes = (currentThreeMinSlot * 3)
                                )
                                if (isCurrentlySpeaking || (now - lastCriticalSpeechRealtimeMs < 2500L)) {
                                    pendingDeferredSplitAnnouncement = timeCheckpointMsg
                                } else {
                                    speakCoach(timeCheckpointMsg, isHighPriority = false)
                                }
                            }
                        }
                    }
                }

                // Alerta de Reta Final de Prova (5K / 10K)
                if (isFreeOrRaceMode && raceTargetMeters != null) {
                    val final500mThreshold = (raceTargetMeters - 500).coerceAtLeast(500)
                    if (!hasAnnouncedFinal500mSprint && currentGpsM in final500mThreshold until raceTargetMeters) {
                        hasAnnouncedFinal500mSprint = true
                        triggerBlockTransitionFeedback()
                        speakCoach(
                            RunTracker.buildRaceFinal500mAnnouncement(raceTargetMeters),
                            isHighPriority = true
                        )
                    }
                    if (!hasAnnouncedRaceTargetReached && currentGpsM >= raceTargetMeters) {
                        hasAnnouncedRaceTargetReached = true
                        triggerBlockTransitionFeedback()
                        speakCoach(
                            RunTracker.buildRaceFinishLineAnnouncement(raceTargetMeters, elapsedSec),
                            isHighPriority = true
                        )
                    }
                }

                // Dispara anúncio de split adiado assim que sair da janela de 10s pós-transição
                if (pendingDeferredSplitAnnouncement != null &&
                    !isInTransitionShieldWindow &&
                    !isCurrentlySpeaking &&
                    (now - lastCriticalSpeechRealtimeMs >= 2500L)
                ) {
                    val deferredMsg = pendingDeferredSplitAnnouncement
                    pendingDeferredSplitAnnouncement = null
                    if (deferredMsg != null && !isWarmupOrCooldownPhase) {
                        speakCoach(deferredMsg, isHighPriority = false)
                    }
                }

                if (!isGentleMode && !isFreeOrRaceMode && !isTrack400mMode) {
                    // Contagem regressiva 5, 4, 3, 2, 1 (Prioridade Máxima - Regra 4), TICs secos em 3, 2, 1 e aviso "Tic-Tic" aos 10s
                    if (secLeft != lastAnnouncedSecLeft) {
                        lastAnnouncedSecLeft = secLeft
                        if (secLeft == 10) {
                            RunTracker.triggerTenSecondWarning(
                                context = context,
                                blockIdx = activeBlockIdx,
                                blockRemainingSeconds = secLeft
                            )
                        } else if (secLeft in 1..5) {
                            if (secLeft in 1..3) {
                                com.example.utils.MaxSoundManager.playRestCountdownTick(context)
                            }
                            speakCoach("$secLeft", isHighPriority = true)
                        }
                    }

                    // ÁUDIO COACH RESPEITANDO A ENERGIA DO ATLETA (FIM DO "DESACELERE" PODANDO O RITMO):
                    // 1) Em Blocos de Estímulo / Tiro (RUN): PROIBIDO mandar "Desacelere um pouco" por correr mais rápido que o pace alvo!
                    //    Se estiver mais rápido ou dentro do alvo, elogia o esforço ("Excelente ritmo, mantendo forte no alvo!") ou mantém silêncio focado.
                    //    Só emite alerta de cautela por esforço excessivo se estiver na 1ª metade do treino e FC real > 185 bpm (> 96% FCmáx) por > 45s seguidos.
                    // 2) Em Blocos de Rodagem Leve / Recuperação (Z2, EASY, RECOVERY, WALK): usa a Frequência Cardíaca como juiz principal (só pede cautela se BPM > 160 bpm).
                    val updatedSecondsInBlock = (elapsedSec - currentBlockStartElapsedSec).coerceAtLeast(0)
                    val isPaceGracePeriodActive = RunTracker.isInPaceGracePeriod(
                        totalElapsedSeconds = elapsedSec,
                        blockElapsedSeconds = updatedSecondsInBlock,
                        blockTypeName = activeBlockTypeName
                    )
                    val isPaceBlockedByTransitionWindow = RunTracker.isWithinBlockTransitionWindow(
                        blockElapsedSeconds = updatedSecondsInBlock,
                        blockRemainingSeconds = secLeft
                    )

                    val currentPace = runTrackState.currentPaceSecPerKm
                    val liveBpm = bleManager.currentBpm.value
                    val highHrVoiceAlertThresholdBpm = com.example.data.model.calculateHighHrVoiceAlertThresholdBpm(
                        calibratedMaxHrBpm = calibratedMaxHrBpm,
                        ageYears = athleteAge
                    )
                    val toleranceMs = RunTracker.PACE_OUT_OF_ZONE_TOLERANCE_SECONDS * 1000L
                    val isEasyOrZ2Session = plan.sessionType.uppercase() in listOf("Z2", "EASY", "RECOVERY", "REGENERATIVE")

                    if (!isWarmupOrCooldownPhase &&
                        !isPaceGracePeriodActive &&
                        !isPaceBlockedByTransitionWindow &&
                        activeBlock?.type == RunningBlockType.RUN &&
                        !isEasyOrZ2Session
                    ) {
                        // A) Guarda de Segurança Cardiovascular na 1ª metade do treino: FC > 95% da FCmáx efetiva (calibrada ou por idade) por > 45s contínuos
                        val isFirstHalfOfWorkout = activeWorkoutDurationSec > 0 && elapsedSec < (activeWorkoutDurationSec / 2)
                        if (isFirstHalfOfWorkout && liveBpm > highHrVoiceAlertThresholdBpm) {
                            if (highHrInFirstHalfSinceRealtimeMs == 0L) {
                                highHrInFirstHalfSinceRealtimeMs = now
                            }
                            if (!isCurrentlySpeaking &&
                                (now - highHrInFirstHalfSinceRealtimeMs) >= toleranceMs &&
                                (now - lastPaceAlertRealtime) >= toleranceMs
                            ) {
                                lastPaceAlertRealtime = now
                                highHrInFirstHalfSinceRealtimeMs = now
                                speakCoach("Atenção ao pulso elevado, respire fundo para sustentar até o final", isHighPriority = false)
                            }
                        } else {
                            highHrInFirstHalfSinceRealtimeMs = 0L
                        }

                        // B) Controle de Pace no Tiro: avisa apenas se cair abaixo do ritmo mínimo ("Acelere um pouco") ou elogia se estiver forte no alvo
                        if (currentPace != null && currentPace > 0) {
                            val calibratedTargetSec = (targetPaceMinutes * 60f).toInt()
                            val (minP, maxP) = if (baselinePaceMinPerKm != null) {
                                Pair((calibratedTargetSec - 25).coerceAtLeast(150), calibratedTargetSec + 25)
                            } else {
                                Pair(activeBlock.minPaceSecPerKm, activeBlock.maxPaceSecPerKm)
                            }
                            val isTooSlow = maxP != null && currentPace > maxP + 10
                            val isStrongOnTarget = minP != null && currentPace < minP - 10
                            if (isTooSlow) {
                                if (paceOutOfZoneSinceRealtimeMs == 0L) {
                                    paceOutOfZoneSinceRealtimeMs = now
                                }
                                val continuousOutOfZoneMs = now - paceOutOfZoneSinceRealtimeMs
                                if (!isCurrentlySpeaking &&
                                    continuousOutOfZoneMs >= toleranceMs &&
                                    now - lastPaceAlertRealtime >= toleranceMs
                                ) {
                                    lastPaceAlertRealtime = now
                                    paceOutOfZoneSinceRealtimeMs = now
                                    triggerSlowPaceFeedback()
                                    speakCoach("Acelere um pouco", isHighPriority = false)
                                }
                            } else if (isStrongOnTarget && (liveBpm <= 0 || liveBpm <= highHrVoiceAlertThresholdBpm)) {
                                if (paceOutOfZoneSinceRealtimeMs == 0L) {
                                    paceOutOfZoneSinceRealtimeMs = now
                                }
                                val continuousStrongMs = now - paceOutOfZoneSinceRealtimeMs
                                if (!isCurrentlySpeaking &&
                                    lastPraisedBlockIndex != activeBlockIdx &&
                                    continuousStrongMs >= toleranceMs &&
                                    now - lastPaceAlertRealtime >= toleranceMs
                                ) {
                                    lastPraisedBlockIndex = activeBlockIdx
                                    lastPaceAlertRealtime = now
                                    paceOutOfZoneSinceRealtimeMs = now
                                    speakCoach("Excelente ritmo, mantendo forte no alvo!", isHighPriority = false)
                                }
                            } else {
                                paceOutOfZoneSinceRealtimeMs = 0L
                            }
                        } else {
                            paceOutOfZoneSinceRealtimeMs = 0L
                        }
                    } else if (!isWarmupOrCooldownPhase &&
                        !isPaceBlockedByTransitionWindow &&
                        updatedSecondsInBlock >= 20 &&
                        (activeBlock?.type == RunningBlockType.WALK || isEasyOrZ2Session)
                    ) {
                        // Em Blocos de Rodagem Leve / Recuperação (Z2, EASY, RECOVERY, WALK):
                        // Usa a Frequência Cardíaca como juiz principal: se FC <= 160 bpm, NÃO pede para desacelerar.
                        // Só pede para aliviar o passo se o BPM não estiver baixando ou passar do teto leve (> 160 bpm) por > 45s.
                        paceOutOfZoneSinceRealtimeMs = 0L
                        highHrInFirstHalfSinceRealtimeMs = 0L
                        if (liveBpm > 160) {
                            if (recoveryHighHrSinceRealtimeMs == 0L) {
                                recoveryHighHrSinceRealtimeMs = now
                            }
                            if (!isCurrentlySpeaking &&
                                (now - recoveryHighHrSinceRealtimeMs) >= toleranceMs &&
                                (now - lastPaceAlertRealtime) >= toleranceMs
                            ) {
                                lastPaceAlertRealtime = now
                                recoveryHighHrSinceRealtimeMs = now
                                speakCoach("Alivie levemente o passo para recuperar os batimentos", isHighPriority = false)
                            }
                        } else {
                            recoveryHighHrSinceRealtimeMs = 0L
                        }
                    } else {
                        paceOutOfZoneSinceRealtimeMs = 0L
                        highHrInFirstHalfSinceRealtimeMs = 0L
                        recoveryHighHrSinceRealtimeMs = 0L
                    }
                }
            }
        }
    }

    // Lembrete periódico de pausa (a cada 60s contínuos em pausa: voz + vibração)
    LaunchedEffect(isPaused) {
        if (isPaused) {
            var pauseDurationMs = 0L
            while (isPaused) {
                kotlinx.coroutines.delay(1000L)
                pauseDurationMs += 1000L
                if (pauseDurationMs >= 60000L) {
                    pauseDurationMs = 0L
                    triggerPauseReminderFeedback()
                    if (coachVoiceEnabled) {
                        speakCoach("Treino pausado")
                    }
                }
            }
        }
    }

    var isUnlocked by rememberSaveable { mutableStateOf(false) } // ESTADO TRAVADO por padrão!
    var lastInteractionTime by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }

    // Trava automaticamente após 5 s sem interação
    LaunchedEffect(isUnlocked, lastInteractionTime) {
        if (isUnlocked) {
            delay(5000L)
            isUnlocked = false
        }
    }

    // Trava de segurança: intercepta SEMPRE o botão Voltar do sistema durante o treino
    val hudContext = LocalContext.current
    BackHandler(enabled = true) {
        if (isUnlocked) {
            // Destravado: exibe diálogo de confirmação para encerrar
            showExitConfirmationDialog = true
        } else {
            // Travado: bloqueia saída acidental e emite feedback auditivo e visual
            AudioSynth.playTickSound(hudContext)
            android.widget.Toast.makeText(
                hudContext,
                "Tela bloqueada contra toques acidentais. Toque no cadeado para destravar.",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    val stateColor = when {
        isGentleMode -> RunColors.phaseWalk
        isTrack400mMode && isTrackInRestState -> Color(0xFF00E5FF)
        isTrack400mMode -> Color(0xFF00E676)
        isFreeOrRaceMode && hasAnnouncedRaceTargetReached -> Color(0xFFFFD600)
        isFreeOrRaceMode -> Color(0xFF00E676)
        else -> when (currentBlock.type) {
            RunningBlockType.RUN -> RunColors.phaseRun
            RunningBlockType.WARMUP -> RunColors.phaseWarmup
            RunningBlockType.COOL_DOWN -> RunColors.phaseCooldown
            RunningBlockType.WALK -> RunColors.phaseWalk
        }
    }

    val nextBlock = blocks.getOrNull(currentBlockIndex + 1)
    val nextBlockText = remember(isGentleMode, isTrack400mMode, isTrackInRestState, trackLapNumber, isFreeOrRaceMode, freeRaceMode, hasAnnouncedRaceTargetReached, nextBlock) {
        when {
            isGentleMode -> ""
            isTrack400mMode && isTrackInRestState -> "Posicione-se na largada e toque em INICIAR LAP ${trackLapNumber + 1}"
            isTrack400mMode -> ""
            isFreeOrRaceMode && hasAnnouncedRaceTargetReached -> "Meta atingida! Segure Parar ao cruzar o pórtico"
            isFreeOrRaceMode -> ""
            nextBlock != null -> "Próximo: ${nextBlock.label} (${nextBlock.durationSec / 60}:${String.format("%02d", nextBlock.durationSec % 60)})"
            else -> "Último bloco"
        }
    }

    val liveRecordedGpsPoints = remember(runTrackState.recordedPointsCount, totalElapsedSeconds) {
        RunTracker.recordedPoints
    }

    val launchLiveCameraCapture: () -> Unit = {
        AudioSynth.playTickSound()
        runCatching {
            com.example.MainActivity.markIntentionalExternalNavigation(60_000L)
            val dir = java.io.File(context.filesDir, "run_route_photos")
            if (!dir.exists()) dir.mkdirs()
            val photoFile = java.io.File(
                dir,
                "live_cam_${sessionIdForCheckpoint}_${System.currentTimeMillis()}.jpg"
            )
            pendingCameraCapturePath = photoFile.absolutePath
            com.example.ui.components.RunRoutePhotoManager.savePendingLiveCameraCapturePath(
                context = context,
                sessionId = sessionIdForCheckpoint,
                filePath = photoFile.absolutePath
            )
            val photoUri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                photoFile
            )
            liveRunCameraLauncher.launch(photoUri)
        }.onFailure {
            pendingCameraCapturePath = null
            com.example.ui.components.RunRoutePhotoManager.clearPendingLiveCameraCapturePath(context, sessionIdForCheckpoint)
            Toast.makeText(
                context,
                "Não foi possível abrir a câmera agora.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(RunColors.bg)
    ) {
        // 0. Modo Mapa em Tempo Real ao Fundo (Estilo Strava — Opcional ON/OFF, Custo Zero)
        if (isLiveMapEnabled) {
            com.example.ui.components.LiveRunningMapBackground(
                currentLat = runTrackState.currentLatitude,
                currentLon = runTrackState.currentLongitude,
                currentBearingDegrees = runTrackState.currentBearingDegrees,
                recordedPoints = liveRecordedGpsPoints,
                layerStyle = liveMapLayerStyle,
                is3dMode = isLiveMap3d,
                recenterTrigger = liveMapRecenterTrigger,
                onUserPanDetected = { hasUserPannedLiveMap = true },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Overlay de Brilho nas Bordas da Tela (Screen Edge Glow) para feedback de Pace
        val targetPaceSecPerKm = targetPaceMinutes * 60f
        ScreenEdgeGlow(
            blockType = currentBlock.type,
            environment = environment,
            currentPaceSecPerKm = runTrackState.currentPaceSecPerKm,
            targetPaceSecPerKm = targetPaceSecPerKm,
            isGentleMode = isGentleMode,
            isFreeRunMode = isFreeOrRaceMode
        )

        val isStructuredPlanWorkout = !isFreeOrRaceMode && !isGentleMode && !isTrack400mMode
        val displayedDistanceMeters = if (isStructuredPlanWorkout && hasEnteredMainWorkoutPhase) {
            mainRunDistanceMeters
        } else {
            runTrackState.distanceMeters
        }
        val distanceLabelText = when {
            isStructuredPlanWorkout && hasEnteredMainWorkoutPhase -> "DISTÂNCIA PRINCIPAL"
            isStructuredPlanWorkout && currentBlock.type == RunningBlockType.WARMUP -> "DISTÂNCIA AQUEC."
            else -> "DISTÂNCIA TOTAL"
        }
        val distStr = remember(displayedDistanceMeters) {
            String.format(Locale.US, "%.2f km", displayedDistanceMeters / 1000f)
        }
        val isolatedBlockAvgPace = if (isStructuredPlanWorkout && hasEnteredMainWorkoutPhase && mainRunDistanceMeters >= 20 && mainRunDurationSeconds > 0) {
            ((mainRunDurationSeconds * 1000) / mainRunDistanceMeters)
        } else null
        val effectiveHudPace = runTrackState.currentPaceSecPerKm ?: isolatedBlockAvgPace
        val paceStr = remember(effectiveHudPace) {
            if (effectiveHudPace != null && effectiveHudPace in 120..1800) {
                val pMin = effectiveHudPace / 60
                val pSec = effectiveHudPace % 60
                String.format(Locale.US, "%d'%02d\"", pMin, pSec)
            } else {
                "--'--"
            }
        }
        val badgeText = when {
            isTrack400mMode && isTrackInRestState -> "AGUARDANDO LARGADA DA PRÓXIMA LAP"
            isTrack400mMode -> "LAP $trackLapNumber • TIRO 400M"
            isFreeOrRaceMode && hasAnnouncedRaceTargetReached -> "META CONCLUÍDA"
            isFreeOrRaceMode && raceTargetMeters != null &&
                runTrackState.distanceMeters >= (raceTargetMeters - 500) -> "SPRINT FINAL 500M!"
            isFreeOrRaceMode && raceTargetMeters != null -> "RITMO DE PROVA"
            isFreeOrRaceMode -> "EM CURSO"
            else -> when (currentBlock.type) {
                RunningBlockType.WARMUP -> "AQUECIMENTO"
                RunningBlockType.COOL_DOWN -> "DESAQUECIMENTO"
                RunningBlockType.RUN -> "CORRIDA"
                RunningBlockType.WALK -> "CAMINHADA"
            }
        }
        val formattedBlockTimer = remember(secondsLeftInBlock, isFreeOrRaceMode, isTrack400mMode, isTrackInRestState, trackLapElapsedSec, trackRestElapsedSec) {
            when {
                isTrack400mMode && isTrackInRestState -> {
                    val mins = trackRestElapsedSec / 60
                    val secs = trackRestElapsedSec % 60
                    String.format(Locale.US, "%02d:%02d", mins, secs)
                }
                isTrack400mMode -> {
                    val mins = trackLapElapsedSec / 60
                    val secs = trackLapElapsedSec % 60
                    String.format(Locale.US, "%02d:%02d", mins, secs)
                }
                isFreeOrRaceMode -> {
                    val hrs = secondsLeftInBlock / 3600
                    val mins = (secondsLeftInBlock % 3600) / 60
                    val secs = secondsLeftInBlock % 60
                    String.format(Locale.US, "%02d:%02d:%02d", hrs, mins, secs)
                }
                else -> {
                    val mins = secondsLeftInBlock / 60
                    val secs = secondsLeftInBlock % 60
                    String.format("%02d:%02d", mins, secs)
                }
            }
        }
        val hrState by bleManager.connectionState.collectAsState()
        val hrBpm by bleManager.currentBpm.collectAsState()
        val isHrLive = hrBpm > 0
        val hrZoneHeartColor = when {
            !isHrLive -> Color(0xFFFF4D4D)
            hrBpm < 115 -> Color(0xFF38BDF8)
            hrBpm < 135 -> Color(0xFF00E676)
            hrBpm < 153 -> Color(0xFFFFD600)
            hrBpm < 170 -> Color(0xFFFF9100)
            else -> Color(0xFFFF1744)
        }
        val gpsStatusText = remember(runTrackState.status, isHrLive, hrState) {
            val gpsPart = when (runTrackState.status) {
                RunTrackerStatus.GOOD -> "GPS OK"
                RunTrackerStatus.SEARCHING -> "Buscando GPS..."
                RunTrackerStatus.WEAK -> "GPS fraco"
                RunTrackerStatus.NO_PERMISSION -> "Sem permissão GPS"
                else -> "GPS inativo"
            }
            val hrPart = when {
                isHrLive -> " • BLE OK"
                hrState == HrConnectionState.CONNECTING -> " • BLE conectando..."
                else -> ""
            }
            gpsPart + hrPart
        }

        val isTrack400mActiveInHud = (runTrackState.isTrack400mCalibrationEnabled || isTrack400mMode) && environment == "OUTDOOR"
        val lapSnapCooldownSec = remember(totalElapsedSeconds, runTrackState.distanceMeters, runTrackState.trackCurrentLapNumber) {
            RunTracker.getManualLapSnapCooldownRemainingSec()
        }
        val handleManualSnapLap400m: () -> Unit = {
            val effectiveSecForPace = if (isStructuredPlanWorkout && hasEnteredMainWorkoutPhase) {
                mainRunDurationSeconds.coerceAtLeast(1)
            } else {
                totalElapsedSeconds.coerceAtLeast(1)
            }
            val snappedMeters = RunTracker.snapDistanceToTrack400mMultiple(
                elapsedSec = effectiveSecForPace,
                currentDisplayedMeters = displayedDistanceMeters
            )
            if (snappedMeters != null) {
                AudioSynth.playTickSound()
                if (isStructuredPlanWorkout && hasEnteredMainWorkoutPhase) {
                    mainRunDistanceMeters = snappedMeters
                    lastTrackedGpsMeters = RunTracker.state.value.distanceMeters
                }
                val completedLaps = snappedMeters / 400
                Toast.makeText(
                    context,
                    "Volta $completedLaps aferida: ${snappedMeters}m (Pace calibrado)",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        if (isLiveMapEnabled) {
            // MODO MAPA AO VIVO EM TELA CHEIA COM MINI-HUD COMPACTO (SEM FUNDO ESCURO COBRINDO O MAPA)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Topo Flutuante Compacto: Status + Alternância Satélite/Ruas + 2D/3D + Centralizar + Aviso GPS + Próximo Bloco
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        color = Color(0xE60A101A),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
                        shadowElevation = 8.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = badgeText,
                                        color = stateColor,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Black,
                                        maxLines = 1
                                    )
                                    if (isHrLive) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Favorite,
                                                contentDescription = null,
                                                tint = hrZoneHeartColor,
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Text(
                                                text = "$hrBpm bpm",
                                                color = Color.White,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = gpsStatusText,
                                    color = if (runTrackState.status == RunTrackerStatus.GOOD) RunColors.phaseWalk else RunColors.phaseWarmup,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Surface(
                                    onClick = {
                                        AudioSynth.playTickSound()
                                        liveMapLayerStyle = if (liveMapLayerStyle == com.example.ui.components.LiveRunMapLayerStyle.STREET_LIGHT) {
                                            com.example.ui.components.LiveRunMapLayerStyle.SATELLITE
                                        } else {
                                            com.example.ui.components.LiveRunMapLayerStyle.STREET_LIGHT
                                        }
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color(0xFF152238),
                                    border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.75f)),
                                    modifier = Modifier
                                        .height(36.dp)
                                        .testTag("btn_live_map_layers")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        com.example.ui.components.VectorMapLayersIcon(
                                            modifier = Modifier.size(16.dp),
                                            tint = Color(0xFF00E5FF)
                                        )
                                        Text(
                                            text = if (liveMapLayerStyle == com.example.ui.components.LiveRunMapLayerStyle.STREET_LIGHT) "Ruas" else "Satélite",
                                            color = Color.White,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Black
                                        )
                                    }
                                }

                                Surface(
                                    onClick = {
                                        AudioSynth.playTickSound()
                                        isLiveMap3d = !isLiveMap3d
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isLiveMap3d) Color(0xFF082F49) else Color(0xFF151922),
                                    border = BorderStroke(
                                        1.dp,
                                        if (isLiveMap3d) Color(0xFF00E676) else Color(0xFF475569)
                                    ),
                                    modifier = Modifier
                                        .height(36.dp)
                                        .testTag("btn_live_map_3d_toggle")
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.padding(horizontal = 9.dp)
                                    ) {
                                        Text(
                                            text = if (isLiveMap3d) "3D" else "2D",
                                            color = if (isLiveMap3d) Color(0xFF00E676) else Color.White,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Black
                                        )
                                    }
                                }

                                Surface(
                                    onClick = {
                                        AudioSynth.playTickSound()
                                        hasUserPannedLiveMap = false
                                        liveMapRecenterTrigger++
                                    },
                                    shape = CircleShape,
                                    color = if (hasUserPannedLiveMap) Color(0xFF082F49) else Color(0xFF151922),
                                    border = BorderStroke(
                                        1.dp,
                                        if (hasUserPannedLiveMap) Color(0xFFFFD600) else Color(0xFF38BDF8).copy(alpha = 0.65f)
                                    ),
                                    modifier = Modifier
                                        .size(36.dp)
                                        .testTag("btn_live_map_recenter")
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                        com.example.ui.components.VectorCenterTargetIcon(
                                            modifier = Modifier.size(18.dp),
                                            tint = if (hasUserPannedLiveMap) Color(0xFFFFD600) else Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (liveRecordedGpsPoints.size < 2) {
                        Surface(
                            color = Color(0xE60F172A),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.5f)),
                            shadowElevation = 6.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("live_map_waiting_gps_banner")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                com.example.ui.components.VectorMapWithPinIcon(
                                    modifier = Modifier.size(15.dp),
                                    tint = Color(0xFF38BDF8),
                                    pinColor = Color(0xFF00E676)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Aguardando deslocamento GPS para desenhar o traçado...",
                                    color = Color(0xFFE2E8F0),
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    HudNextBlockIndicatorPill(
                        blocks = blocks,
                        currentBlockIndex = currentBlockIndex,
                        skippedBlockIndices = skippedBlockIndices,
                        isStructuredWorkout = isStructuredPlanWorkout
                    )
                }

                // Área central 100% livre para visualização e interação com o Mapa ao Vivo
                Spacer(modifier = Modifier.weight(1f))

                // Mini-HUD Flutuante Compacto no Rodapé: Tempo, Distância, Pace Atual e Botão Voltar para Métricas / Fechar Mapa
                Surface(
                    color = Color(0xF00B111C),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.2.dp, stateColor.copy(alpha = 0.55f)),
                    shadowElevation = 12.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("strava_live_map_bottom_card")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        HudTrack400mLiveCalibrationCard(
                            isEnabled = isTrack400mActiveInHud,
                            displayedDistanceMeters = displayedDistanceMeters,
                            cooldownRemainingSec = lapSnapCooldownSec,
                            onSnapLap400m = handleManualSnapLap400m
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = formattedBlockTimer,
                                    color = Color.White,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = if (isFreeOrRaceMode) "TEMPO" else "TEMPO BLOCO",
                                    color = RunColors.textSecondary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(32.dp)
                                    .background(RunColors.border)
                            )

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = distStr,
                                    color = RunColors.brandText,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = "DISTÂNCIA",
                                    color = RunColors.textSecondary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(32.dp)
                                    .background(RunColors.border)
                            )

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = paceStr,
                                    color = Color(0xFF00E676),
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = "PACE ATUAL",
                                    color = RunColors.textSecondary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Button(
                            onClick = {
                                AudioSynth.playTickSound()
                                isLiveMapEnabled = false
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .testTag("btn_live_map_close"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF152238),
                                contentColor = Color.White
                            ),
                            border = BorderStroke(1.2.dp, Color(0xFF00E5FF).copy(alpha = 0.75f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Voltar para Métricas / Fechar Mapa",
                                tint = Color(0xFF00E5FF),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "VOLTAR PARA MÉTRICAS / FECHAR MAPA",
                                color = Color.White,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // a) & b) Cabeçalho de Status/Pílulas e Painel de Fase (Pista 400m / Prova / Barra de Blocos)
                RunningHudHeaderAndPhaseSection(
                    plan = plan,
                    environment = environment,
                    isLiveMapEnabled = isLiveMapEnabled,
                    isTrack400mMode = isTrack400mMode,
                    isFreeOrRaceMode = isFreeOrRaceMode,
                    isGentleMode = isGentleMode,
                    freeRaceModeLabel = freeRaceMode?.hudHeaderLabel,
                    raceTargetMeters = raceTargetMeters,
                    hasAnnouncedRaceTargetReached = hasAnnouncedRaceTargetReached,
                    isSystemGpsProviderOn = isSystemGpsProviderOn,
                    runTrackState = runTrackState,
                    trackLapNumber = trackLapNumber,
                    isTrackInRestState = isTrackInRestState,
                    isTrackRestPaused = isTrackRestPaused,
                    trackLapStartGpsMeters = trackLapStartGpsMeters,
                    trackTargetRestSec = trackTargetRestSec,
                    liveRecordedSplits = liveRecordedSplits,
                    blocks = blocks,
                    currentBlockIndex = currentBlockIndex,
                    skippedBlockIndices = skippedBlockIndices,
                    totalElapsedSeconds = totalElapsedSeconds,
                    baselinePaceMinPerKm = baselinePaceMinPerKm,
                    onEnvironmentChanged = onEnvironmentChanged,
                    onToggleTrackRestPause = {
                        AudioSynth.playTickSound()
                        isTrackRestPaused = !isTrackRestPaused
                    },
                    onAdd30sTrackRest = {
                        AudioSynth.playTickSound()
                        trackTargetRestSec += 30
                    },
                    onStartNextLap400m = {
                        AudioSynth.playTickSound()
                        if (liveRecordedSplits.isNotEmpty()) {
                            val lastIdx = liveRecordedSplits.lastIndex
                            val lastSplit = liveRecordedSplits[lastIdx]
                            liveRecordedSplits[lastIdx] = lastSplit.copy(
                                phaseTag = "LAP_400M|REST:$trackRestElapsedSec"
                            )
                            savedSplitsJson = liveRecordedSplits.toJsonString()
                        }
                        trackLapNumber++
                        trackLapStartGpsMeters = runTrackState.distanceMeters
                        trackLapElapsedSec = 0
                        trackRestElapsedSec = 0
                        isTrackRestPaused = false
                        trackTargetRestSec = 120
                        isTrackInRestState = false
                        triggerBlockTransitionFeedback()
                        speakCoach(
                            RunTracker.buildTrack400mStartAnnouncement(trackLapNumber),
                            isHighPriority = true
                        )
                    },
                    onManualFinishLap400m = {
                        AudioSynth.playTickSound()
                        val completedLapNum = trackLapNumber
                        val lapDurSec = trackLapElapsedSec.coerceAtLeast(1)
                        val lapPaceSecPerKm = ((lapDurSec * 1000.0) / 400.0).roundToInt()
                        val avgBpm = if (splitHrSamples.isNotEmpty()) splitHrSamples.average().toInt() else bleManager.currentBpm.value.takeIf { it > 0 }
                        liveRecordedSplits.add(
                            RecordedKmSplit(
                                splitNumber = completedLapNum,
                                splitLabel = "LAP $completedLapNum (400m)",
                                distanceMeters = 400,
                                durationSec = lapDurSec,
                                paceSecPerKm = lapPaceSecPerKm,
                                avgHr = avgBpm,
                                phaseTag = "LAP_400M|REST:0"
                            )
                        )
                        savedSplitsJson = liveRecordedSplits.toJsonString()
                        lastSplitDistanceMeters = runTrackState.distanceMeters
                        lastSplitElapsedSec = totalElapsedSeconds
                        splitHrSamples.clear()
                        isTrackInRestState = true
                        trackRestElapsedSec = 0
                        triggerBlockTransitionFeedback()
                        speakCoach(
                            RunTracker.buildTrack400mLapAnnouncement(completedLapNum, lapDurSec),
                            isHighPriority = true
                        )
                    }
                )

                // c..g) Painel Central de Métricas
                RunningHudCenterMetricsSection(
                    isLiveMapEnabled = false,
                    isLiveMapCompactCard = false,
                    stateColor = stateColor,
                    badgeText = badgeText,
                    gpsStatusText = gpsStatusText,
                    runTrackerStatus = runTrackState.status,
                    isTrack400mMode = isTrack400mMode,
                    isTrackInRestState = isTrackInRestState,
                    isFreeOrRaceMode = isFreeOrRaceMode,
                    isPaused = isPaused,
                    hudFaceStyle = hudFaceStyle,
                    formattedBlockTimer = formattedBlockTimer,
                    paceStr = paceStr,
                    distStr = distStr,
                    distanceLabelText = distanceLabelText,
                    isHrLive = isHrLive,
                    hrBpm = hrBpm,
                    hrZoneHeartColor = hrZoneHeartColor,
                    mediaIsPlaying = mediaTrackInfo.isPlaying,
                    livePhotoCount = livePhotoCount,
                    isTrack400mActiveInHud = isTrack400mActiveInHud,
                    displayedDistanceMeters = displayedDistanceMeters,
                    lapSnapCooldownSec = lapSnapCooldownSec,
                    blocks = blocks,
                    currentBlockIndex = currentBlockIndex,
                    skippedBlockIndices = skippedBlockIndices,
                    isStructuredPlanWorkout = isStructuredPlanWorkout,
                    onSetLiveMapCompactCard = { compact ->
                        AudioSynth.playTickSound()
                        isLiveMapCompactCard = compact
                    },
                    onSnapLap400m = handleManualSnapLap400m,
                    onOpenFullscreenHrModal = {
                        AudioSynth.playTickSound()
                        showHudFullscreenHrModal = true
                    },
                    onLaunchLiveCamera = launchLiveCameraCapture,
                    modifier = Modifier
                )

                // h) Barra Inferior de Botões / Controles (Player de Música + Pular Bloco + Cadeado / Pausar / Parar)
                RunningHudBottomControlsSection(
                    isLiveMapEnabled = isLiveMapEnabled,
                    isLiveMapCompactCard = isLiveMapCompactCard,
                    mediaTrackDisplayLabel = mediaTrackInfo.displayLabel,
                    mediaTrackHasTitle = mediaTrackInfo.track.isNotBlank(),
                    mediaIsPlaying = mediaTrackInfo.isPlaying,
                    isGentleMode = isGentleMode,
                    isFreeOrRaceMode = isFreeOrRaceMode,
                    currentBlockIndex = currentBlockIndex,
                    blocks = blocks,
                    currentBlockType = currentBlock.type,
                    isUnlocked = isUnlocked,
                    isPaused = isPaused,
                    livePhotoCount = livePhotoCount,
                    onSkipCurrentBlock = {
                        AudioSynth.playTickSound()
                        lastInteractionTime = SystemClock.elapsedRealtime()
                        val now = SystemClock.elapsedRealtime()
                        val newSkipped = skippedBlockIndices + currentBlockIndex
                        var targetEffectiveSec = 0
                        var foundNext = false
                        for (i in blocks.indices) {
                            if (i in newSkipped) continue
                            if (i > currentBlockIndex) {
                                foundNext = true
                                break
                            }
                            targetEffectiveSec += blocks[i].durationSec
                        }
                        if (!foundNext) {
                            targetEffectiveSec = activeWorkoutDurationSec
                        }
                        accumulatedElapsedMs = targetEffectiveSec * 1000L
                        lastTickRealtime = now
                        skippedBlockIndices = newSkipped
                    },
                    onLaunchLiveCamera = launchLiveCameraCapture,
                    onUnlock = {
                        AudioSynth.playTickSound()
                        isUnlocked = true
                        lastInteractionTime = SystemClock.elapsedRealtime()
                    },
                    onLock = {
                        AudioSynth.playTickSound()
                        isUnlocked = false
                    },
                    onToggleLiveMap = {
                        AudioSynth.playTickSound()
                        val turningOn = !isLiveMapEnabled
                        isLiveMapEnabled = turningOn
                        if (turningOn) {
                            isLiveMapCompactCard = true
                            hasUserPannedLiveMap = false
                            liveMapRecenterTrigger++
                        }
                    },
                    onTogglePause = {
                        AudioSynth.playTickSound()
                        lastInteractionTime = SystemClock.elapsedRealtime()
                        if (!isPaused) {
                            val now = SystemClock.elapsedRealtime()
                            accumulatedElapsedMs += (now - lastTickRealtime).coerceAtLeast(0L)
                            isPaused = true
                            val pausedElapsedSec = (accumulatedElapsedMs / 1000L).toInt().coerceAtLeast(1)
                            val estFallback = RunTracker.estimateFallbackDistanceMeters(
                                plan = plan,
                                durationSec = pausedElapsedSec,
                                skippedIndices = skippedBlockIndices,
                                baselinePaceMinPerKm = baselinePaceMinPerKm
                            )
                            runCatching {
                                RunTracker.saveActiveWorkoutCheckpoint(
                                    context = context,
                                    sessionId = sessionIdForCheckpoint,
                                    startTimestampMs = sessionStartTimestampMs.takeIf { it > 0L } ?: System.currentTimeMillis(),
                                    weekNumber = plan.weekNumber,
                                    dayNumber = dayNumber,
                                    sessionType = plan.sessionType,
                                    environment = environment,
                                    isGentleMode = isGentleMode,
                                    shoeName = defaultShoeName,
                                    elapsedSec = pausedElapsedSec,
                                    distanceMeters = runTrackState.distanceMeters,
                                    fallbackEstimatedMeters = estFallback,
                                    mainRunDurationSec = mainRunDurationSeconds,
                                    mainRunDistanceMeters = mainRunDistanceMeters,
                                    routePointsJson = if (RunTracker.recordedPoints.isNotEmpty()) RunTracker.getRoutePointsJson() else null,
                                    kmSplitsJson = liveRecordedSplits.toJsonString(),
                                    hrTimeline = runCatching { bleManager.getHrTimelineString() }.getOrDefault(""),
                                    workoutBlockLogsJson = RunTracker.getBlockUnintendedWalkJson(),
                                    skippedIndices = skippedBlockIndices,
                                    pendingSummarySave = true,
                                    forceImmediateSave = true
                                )
                            }
                        } else {
                            lastTickRealtime = SystemClock.elapsedRealtime()
                            isPaused = false
                        }
                    },
                    onConfirmFinish = {
                        finishSessionWithMetrics(totalElapsedSeconds)
                    },
                    onUserInteraction = {
                        lastInteractionTime = SystemClock.elapsedRealtime()
                    }
                )
            }
        }
    }

    // (C) Diálogos de Confirmação, Monitor Cardíaco em Tela Cheia e Permissão de Mídia
    RunningHudDialogsSection(
        showExitConfirmationDialog = showExitConfirmationDialog,
        showHudFullscreenHrModal = showHudFullscreenHrModal,
        showMediaMetadataPermissionDialog = showMediaMetadataPermissionDialog,
        totalElapsedSeconds = totalElapsedSeconds,
        bleManager = bleManager,
        athleteAge = athleteAge,
        calibratedMaxHrBpm = calibratedMaxHrBpm,
        calibratedLt1Bpm = calibratedLt1Bpm,
        calibratedLt2Bpm = calibratedLt2Bpm,
        onDismissExitDialog = { showExitConfirmationDialog = false },
        onConfirmFinishFromExitDialog = {
            showExitConfirmationDialog = false
            finishSessionWithMetrics(totalElapsedSeconds)
        },
        onDiscardFromExitDialog = {
            showExitConfirmationDialog = false
            AudioSynth.playTickSound()
            releaseScreenPinningIfNeeded()
            onCancelSession()
        },
        onDismissFullscreenHrModal = { showHudFullscreenHrModal = false },
        onDismissMediaPermissionDialog = { showMediaMetadataPermissionDialog = false }
    )
}

@Composable
private fun RunningHudLiveMapFloatingControls(
    liveMapLayerStyle: com.example.ui.components.LiveRunMapLayerStyle,
    isLiveMap3d: Boolean,
    hasUserPannedLiveMap: Boolean,
    onCycleLayerStyle: () -> Unit,
    onToggle3d: () -> Unit,
    onRecenter: () -> Unit,
    onCloseMap: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.End
    ) {
        Surface(
            onClick = onCycleLayerStyle,
            shape = CircleShape,
            color = Color(0xE60F172A),
            border = BorderStroke(1.2.dp, Color(0xFF00E5FF).copy(alpha = 0.7f)),
            shadowElevation = 6.dp,
            modifier = Modifier
                .size(44.dp)
                .testTag("btn_live_map_layers")
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                com.example.ui.components.VectorMapLayersIcon(
                    modifier = Modifier.size(20.dp),
                    tint = Color(0xFF00E5FF)
                )
            }
        }

        Surface(
            onClick = onToggle3d,
            shape = CircleShape,
            color = if (isLiveMap3d) Color(0xE6082F49) else Color(0xE60F172A),
            border = BorderStroke(
                1.2.dp,
                if (isLiveMap3d) Color(0xFF00E676) else Color(0xFF475569)
            ),
            shadowElevation = 6.dp,
            modifier = Modifier
                .size(44.dp)
                .testTag("btn_live_map_3d_toggle")
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Text(
                    text = if (isLiveMap3d) "3D" else "2D",
                    color = if (isLiveMap3d) Color(0xFF00E676) else Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }

        Surface(
            onClick = onRecenter,
            shape = CircleShape,
            color = if (hasUserPannedLiveMap) Color(0xE6082F49) else Color(0xE60F172A),
            border = BorderStroke(
                1.2.dp,
                if (hasUserPannedLiveMap) Color(0xFFFFD600) else Color(0xFF38BDF8).copy(alpha = 0.65f)
            ),
            shadowElevation = 6.dp,
            modifier = Modifier
                .size(44.dp)
                .testTag("btn_live_map_recenter")
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                com.example.ui.components.VectorCenterTargetIcon(
                    modifier = Modifier.size(20.dp),
                    tint = if (hasUserPannedLiveMap) Color(0xFFFFD600) else Color.White
                )
            }
        }

        Surface(
            onClick = onCloseMap,
            shape = CircleShape,
            color = Color(0xE60F172A),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.28f)),
            shadowElevation = 6.dp,
            modifier = Modifier
                .size(40.dp)
                .testTag("btn_live_map_close")
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Desligar Mapa ao Vivo",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun RunningHudHeaderAndPhaseSection(
    plan: RunningWorkoutPlan,
    environment: String,
    isLiveMapEnabled: Boolean,
    isTrack400mMode: Boolean,
    isFreeOrRaceMode: Boolean,
    isGentleMode: Boolean,
    freeRaceModeLabel: String?,
    raceTargetMeters: Int?,
    hasAnnouncedRaceTargetReached: Boolean,
    isSystemGpsProviderOn: Boolean,
    runTrackState: com.example.services.RunTrackState,
    trackLapNumber: Int,
    isTrackInRestState: Boolean,
    isTrackRestPaused: Boolean,
    trackLapStartGpsMeters: Int,
    trackTargetRestSec: Int,
    liveRecordedSplits: List<RecordedKmSplit>,
    blocks: List<RunningBlock>,
    currentBlockIndex: Int,
    skippedBlockIndices: Set<Int>,
    totalElapsedSeconds: Int,
    baselinePaceMinPerKm: Float?,
    onEnvironmentChanged: (String) -> Unit,
    onToggleTrackRestPause: () -> Unit,
    onAdd30sTrackRest: () -> Unit,
    onStartNextLap400m: () -> Unit,
    onManualFinishLap400m: () -> Unit
) {
    val context = LocalContext.current
    Column(
        modifier = if (isLiveMapEnabled) {
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xD90A101A))
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp)
        } else {
            Modifier.fillMaxWidth()
        },
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = when {
                    isTrack400mMode -> "INTERVALADO • PISTA 400M"
                    isFreeOrRaceMode -> freeRaceModeLabel ?: "CORRIDA LIVRE"
                    isGentleMode -> "MODO LEVE"
                    else -> "SEMANA ${plan.weekNumber}"
                },
                color = if (isFreeOrRaceMode && hasAnnouncedRaceTargetReached) Color(0xFFFFD600) else RunColors.brandText,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    color = if (environment == "TREADMILL") RunColors.phaseWarmup.copy(alpha = 0.2f) else RunColors.brand.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.clickable {
                        val nextEnv = if (environment == "TREADMILL") "OUTDOOR" else "TREADMILL"
                        onEnvironmentChanged(nextEnv)
                        AudioSynth.playTickSound()
                    }
                ) {
                    Text(
                        text = if (environment == "TREADMILL") "ESTEIRA ⇄" else "AR LIVRE ⇄",
                        color = if (environment == "TREADMILL") RunColors.phaseWarmup else RunColors.brandText,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }

                if (environment == "OUTDOOR") {
                    val isTrackCalibOn = runTrackState.isTrack400mCalibrationEnabled || isTrack400mMode
                    Surface(
                        color = if (isTrackCalibOn) Color(0xFF00E676).copy(alpha = 0.22f) else Color(0xFF151922),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(
                            1.dp,
                            if (isTrackCalibOn) Color(0xFF00E676) else Color(0xFF262D3D)
                        ),
                        modifier = Modifier
                            .clickable {
                                AudioSynth.playTickSound()
                                RunTracker.setTrack400mCalibrationMode(!runTrackState.isTrack400mCalibrationEnabled)
                            }
                            .testTag("hud_top_toggle_pista_400m")
                    ) {
                        Text(
                            text = if (isTrackCalibOn) "PISTA 400M ✓" else "PISTA 400M",
                            color = if (isTrackCalibOn) Color(0xFF00E676) else RunColors.textSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            when {
                isTrack400mMode -> {
                    Text(
                        text = "LAP $trackLapNumber • ${liveRecordedSplits.size} concl.",
                        color = Color(0xFF00E676),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black
                    )
                }
                raceTargetMeters != null -> {
                    val curKm = String.format(Locale.US, "%.2f", runTrackState.distanceMeters / 1000f).replace('.', ',')
                    val targetKm = String.format(Locale.US, "%.2f", raceTargetMeters / 1000f).replace('.', ',')
                    Text(
                        text = "$curKm / $targetKm km",
                        color = if (hasAnnouncedRaceTargetReached) Color(0xFFFFD600) else Color(0xFF00E5FF),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black
                    )
                }
                isFreeOrRaceMode || isGentleMode -> {
                    Text(
                        text = "Livre",
                        color = RunColors.textSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                else -> {
                    Text(
                        text = "Bloco ${currentBlockIndex + 1}/${blocks.size}",
                        color = RunColors.textSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        if (environment == "OUTDOOR" && !isSystemGpsProviderOn) {
            Surface(
                onClick = {
                    AudioSynth.playTickSound()
                    com.example.MainActivity.markIntentionalExternalNavigation(30_000L)
                    runCatching {
                        val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    }
                },
                color = Color(0xFFFF5252).copy(alpha = 0.18f),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, Color(0xFFFF5252)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "LOCALIZAÇÃO DO CELULAR DESLIGADA • TOQUE PARA ATIVAR",
                        color = Color(0xFFFF5252),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = Color(0xFFFF5252),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        if (isTrack400mMode) {
            val currentLapDistM = if (isTrackInRestState) {
                400
            } else {
                (runTrackState.distanceMeters - trackLapStartGpsMeters).coerceIn(0, 400)
            }
            val lapProgress = (currentLapDistM / 400f).coerceIn(0f, 1f)

            Surface(
                color = Color(0xFF151922),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(
                    1.5.dp,
                    if (isTrackInRestState) Color(0xFF00E5FF) else Color(0xFF00E676).copy(alpha = 0.65f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("hud_track_400m_panel")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isTrackInRestState) {
                                "LAP $trackLapNumber CONCLUÍDA • DESCANSO ATIVO"
                            } else {
                                "LAP $trackLapNumber EM ANDAMENTO: $currentLapDistM m / 400 m"
                            },
                            color = if (isTrackInRestState) Color(0xFF00E5FF) else Color(0xFF00E676),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = if (isTrackInRestState) {
                                val tgtM = trackTargetRestSec / 60
                                val tgtS = trackTargetRestSec % 60
                                String.format(Locale.US, "Alvo: %02d:%02d", tgtM, tgtS)
                            } else {
                                "${(lapProgress * 100).roundToInt()}%"
                            },
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(Color(0xFF0D0F14))
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

                    if (isTrackInRestState) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = onToggleTrackRestPause,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                                    .testTag("btn_pause_resume_track_rest"),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.5f)),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Icon(
                                    imageVector = if (isTrackRestPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                    contentDescription = null,
                                    tint = Color(0xFF00E5FF),
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isTrackRestPaused) "RETOMAR DESCANSO" else "PAUSAR DESCANSO",
                                    color = Color(0xFF00E5FF),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    maxLines = 1
                                )
                            }

                            OutlinedButton(
                                onClick = onAdd30sTrackRest,
                                modifier = Modifier
                                    .height(38.dp)
                                    .testTag("btn_add_30s_track_rest"),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, Color(0xFFFFD600).copy(alpha = 0.55f)),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                            ) {
                                Text(
                                    text = "+30s",
                                    color = Color(0xFFFFD600),
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }

                        Button(
                            onClick = onStartNextLap400m,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("btn_iniciar_proxima_lap_400m"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676))
                        ) {
                            Text(
                                text = "INICIAR LAP ${trackLapNumber + 1} (400M)",
                                color = Color(0xFF06101E),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.8.sp
                            )
                        }
                    } else {
                        OutlinedButton(
                            onClick = onManualFinishLap400m,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(36.dp)
                                .testTag("btn_fechar_lap_400m_manual"),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.45f))
                        ) {
                            Text(
                                text = "FECHAR LAP $trackLapNumber (400M) NA LINHA",
                                color = Color(0xFF00E5FF),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }
            }

            if (liveRecordedSplits.isNotEmpty()) {
                Track400mLapsTableCard(
                    splits = liveRecordedSplits.toList(),
                    title = "LAPS DE 400M REALIZADAS"
                )
            }
        } else if (isFreeOrRaceMode) {
            val currentDistM = runTrackState.distanceMeters
            val globalAvgPaceSec = if (currentDistM >= 25 && totalElapsedSeconds > 0) {
                ((totalElapsedSeconds * 1000L) / currentDistM).toInt()
            } else {
                runTrackState.currentPaceSecPerKm
            }
            val globalAvgPaceStr = if (globalAvgPaceSec != null && globalAvgPaceSec in 120..1800) {
                String.format(Locale.US, "%d'%02d\"/km", globalAvgPaceSec / 60, globalAvgPaceSec % 60)
            } else {
                "--'--\"/km"
            }

            Surface(
                color = Color(0xFF151922),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(
                    1.dp,
                    if (hasAnnouncedRaceTargetReached) Color(0xFFFFD600) else Color(0xFF00E5FF).copy(alpha = 0.45f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("hud_free_or_race_panel")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (raceTargetMeters != null) {
                        val progressFraction = (currentDistM.toFloat() / raceTargetMeters.toFloat()).coerceIn(0f, 1f)
                        val pctInt = ((currentDistM.toFloat() / raceTargetMeters.toFloat()) * 100f).roundToInt()
                        val curKmStr = String.format(Locale.US, "%.2f", currentDistM / 1000f).replace('.', ',')
                        val targetKmStr = String.format(Locale.US, "%.2f", raceTargetMeters / 1000f).replace('.', ',')
                        val raceShortLabel = if (raceTargetMeters == 5000) "5K" else "10K"
                        val projectedFinishSec = if (globalAvgPaceSec != null && globalAvgPaceSec in 120..1800) {
                            ((raceTargetMeters / 1000.0) * globalAvgPaceSec).roundToInt()
                        } else {
                            val refSec = ((baselinePaceMinPerKm ?: 6.0f) * 60f).roundToInt()
                            ((raceTargetMeters / 1000.0) * refSec).roundToInt()
                        }
                        val projHrs = projectedFinishSec / 3600
                        val projMins = (projectedFinishSec % 3600) / 60
                        val projSecs = projectedFinishSec % 60
                        val projTimeStr = if (projHrs > 0) {
                            String.format(Locale.US, "%d:%02d:%02d", projHrs, projMins, projSecs)
                        } else {
                            String.format(Locale.US, "%02d:%02d", projMins, projSecs)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "$curKmStr / $targetKmStr km • $pctInt%",
                                color = if (hasAnnouncedRaceTargetReached) Color(0xFFFFD600) else Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black,
                                maxLines = 1,
                                softWrap = false
                            )
                            Text(
                                text = "Projeção $raceShortLabel: $projTimeStr",
                                color = Color(0xFFFFD600),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black,
                                maxLines = 1,
                                softWrap = false
                            )
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(10.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(Color(0xFF0D0F14))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(progressFraction)
                                    .fillMaxHeight()
                                    .background(
                                        if (hasAnnouncedRaceTargetReached) {
                                            Brush.horizontalGradient(listOf(Color(0xFFFFD600), Color(0xFF00E676)))
                                        } else {
                                            Brush.horizontalGradient(listOf(Color(0xFF00B0FF), Color(0xFF00E676)))
                                        }
                                    )
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Ritmo Médio Global: $globalAvgPaceStr",
                            color = Color(0xFF00E5FF),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                        Text(
                            text = "Parciais 1K: ${liveRecordedSplits.size}",
                            color = Color(0xFF00E676),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        } else if (!isGentleMode) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(RunColors.surface)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    blocks.forEachIndexed { index, b ->
                        val isCurrent = index == currentBlockIndex
                        val isPast = index < currentBlockIndex
                        val isSkipped = index in skippedBlockIndices
                        val isCompleted = isPast || isSkipped
                        val blockColor = when (b.type) {
                            RunningBlockType.WARMUP -> RunColors.phaseWarmup
                            RunningBlockType.RUN -> RunColors.phaseRun
                            RunningBlockType.WALK -> RunColors.phaseWalk
                            RunningBlockType.COOL_DOWN -> RunColors.phaseCooldown
                        }
                        val alpha = if (isCurrent) 1f else if (isCompleted) 0.15f else 0.45f
                        Box(
                            modifier = Modifier
                                .weight(b.durationSec.toFloat().coerceAtLeast(30f))
                                .fillMaxHeight()
                                .background(blockColor.copy(alpha = alpha))
                                .then(
                                    if (isCurrent) Modifier.border(1.5.dp, Color.White, RoundedCornerShape(3.dp))
                                    else if (isCompleted) Modifier.border(0.5.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(3.dp))
                                    else Modifier.border(0.5.dp, Color.Black.copy(alpha = 0.3f), RoundedCornerShape(3.dp))
                                )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RunningHudCenterMetricsSection(
    isLiveMapEnabled: Boolean,
    isLiveMapCompactCard: Boolean,
    stateColor: Color,
    badgeText: String,
    gpsStatusText: String,
    runTrackerStatus: RunTrackerStatus,
    isTrack400mMode: Boolean,
    isTrackInRestState: Boolean,
    isFreeOrRaceMode: Boolean,
    isPaused: Boolean,
    hudFaceStyle: com.example.data.model.RunHudFaceStyle,
    formattedBlockTimer: String,
    paceStr: String,
    distStr: String,
    distanceLabelText: String,
    isHrLive: Boolean,
    hrBpm: Int,
    hrZoneHeartColor: Color,
    mediaIsPlaying: Boolean,
    livePhotoCount: Int,
    isTrack400mActiveInHud: Boolean,
    displayedDistanceMeters: Int,
    lapSnapCooldownSec: Int,
    blocks: List<RunningBlock>,
    currentBlockIndex: Int,
    skippedBlockIndices: Set<Int>,
    isStructuredPlanWorkout: Boolean,
    onSetLiveMapCompactCard: (Boolean) -> Unit,
    onSnapLap400m: () -> Unit,
    onOpenFullscreenHrModal: () -> Unit,
    onLaunchLiveCamera: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    if (isLiveMapEnabled && isLiveMapCompactCard) {
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.Bottom
        ) {
            Spacer(modifier = Modifier.weight(1f))

            Surface(
                color = Color(0xEC0B111C),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.2.dp, stateColor.copy(alpha = 0.45f)),
                shadowElevation = 10.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
                    .testTag("strava_live_map_bottom_card")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
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
                            Text(
                                text = badgeText,
                                color = stateColor,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Black,
                                maxLines = 1
                            )
                            Text(
                                text = "• $gpsStatusText",
                                color = if (runTrackerStatus == RunTrackerStatus.GOOD) RunColors.phaseWalk else RunColors.phaseWarmup,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }

                        Surface(
                            onClick = { onSetLiveMapCompactCard(false) },
                            color = Color(0xFF151E2E),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.55f)),
                            modifier = Modifier.testTag("btn_expand_hud_from_map")
                        ) {
                            Text(
                                text = "⤢ Expandir",
                                color = Color(0xFF00E5FF),
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    HudTrack400mLiveCalibrationCard(
                        isEnabled = isTrack400mActiveInHud,
                        displayedDistanceMeters = displayedDistanceMeters,
                        cooldownRemainingSec = lapSnapCooldownSec,
                        onSnapLap400m = onSnapLap400m
                    )

                    HudNextBlockIndicatorPill(
                        blocks = blocks,
                        currentBlockIndex = currentBlockIndex,
                        skippedBlockIndices = skippedBlockIndices,
                        isStructuredWorkout = isStructuredPlanWorkout
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                            Text(
                                text = formattedBlockTimer,
                                color = Color.White,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = if (isFreeOrRaceMode) "TEMPO" else "TEMPO BLOCO",
                                color = RunColors.textSecondary,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Box(modifier = Modifier.width(1.dp).height(30.dp).background(RunColors.border))

                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                            Text(
                                text = paceStr,
                                color = Color(0xFF00E676),
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "RITMO / KM",
                                color = RunColors.textSecondary,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Box(modifier = Modifier.width(1.dp).height(30.dp).background(RunColors.border))

                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                            Text(
                                text = distStr,
                                color = RunColors.brandText,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "DISTÂNCIA",
                                color = RunColors.textSecondary,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            onClick = onOpenFullscreenHrModal,
                            color = if (isHrLive) hrZoneHeartColor.copy(alpha = 0.16f) else Color(0xFF151922),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, if (isHrLive) hrZoneHeartColor.copy(alpha = 0.55f) else Color(0xFF262D3D))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = if (isHrLive) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = "Batimentos",
                                    tint = hrZoneHeartColor,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = if (isHrLive) "$hrBpm" else "--",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        Surface(
                            color = Color(0xFF151922),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color(0xFF262D3D))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = {
                                        AudioSynth.playTickSound()
                                        com.example.MainActivity.dispatchMediaKeyEvent(context, android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS)
                                    },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(Icons.Default.SkipPrevious, contentDescription = "Anterior", tint = Color.White, modifier = Modifier.size(17.dp))
                                }
                                IconButton(
                                    onClick = {
                                        AudioSynth.playTickSound()
                                        com.example.MainActivity.dispatchMediaKeyEvent(context, android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                                    },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(
                                        imageVector = if (mediaIsPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = "Play/Pause",
                                        tint = Color(0xFF00E676),
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        AudioSynth.playTickSound()
                                        com.example.MainActivity.dispatchMediaKeyEvent(context, android.view.KeyEvent.KEYCODE_MEDIA_NEXT)
                                    },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(Icons.Default.SkipNext, contentDescription = "Próxima", tint = Color.White, modifier = Modifier.size(17.dp))
                                }
                                IconButton(
                                    onClick = {
                                        AudioSynth.playTickSound()
                                        com.example.MainActivity.openMusicOrSpotifyPlayer(context)
                                    },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(Icons.Default.MusicNote, contentDescription = "Abrir Música", tint = Color(0xFF00E5FF), modifier = Modifier.size(16.dp))
                                }
                            }
                        }

                        Surface(
                            onClick = onLaunchLiveCamera,
                            color = if (livePhotoCount > 0) Color(0xFF271A07) else Color(0xFF151922),
                            shape = if (livePhotoCount > 0) RoundedCornerShape(20.dp) else CircleShape,
                            border = BorderStroke(
                                1.2.dp,
                                if (livePhotoCount > 0) Color(0xFFFBBF24).copy(alpha = 0.85f) else Color.White.copy(alpha = 0.72f)
                            ),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = if (livePhotoCount > 0) 10.dp else 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                com.example.ui.components.VectorCameraIcon(
                                    modifier = Modifier.size(17.dp),
                                    tint = Color.White
                                )
                                if (livePhotoCount > 0) {
                                    Text(
                                        text = "$livePhotoCount",
                                        color = Color(0xFFFBBF24),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    } else {
        Column(
            modifier = if (isLiveMapEnabled) {
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xD90A101A))
                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(18.dp))
                    .padding(12.dp)
            } else {
                Modifier.fillMaxWidth()
            },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (isLiveMapEnabled) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Surface(
                        onClick = { onSetLiveMapCompactCard(true) },
                        color = Color(0xFF151E2E),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.55f)),
                        modifier = Modifier.testTag("btn_collapse_hud_to_map_card")
                    ) {
                        Text(
                            text = "⤡ Recolher p/ Mapa",
                            color = Color(0xFF00E5FF),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            Text(
                text = badgeText,
                color = stateColor,
                fontSize = if (isTrack400mMode && isTrackInRestState) 16.sp else 22.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.2.sp,
                maxLines = 1,
                softWrap = false
            )

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                RunHudStyledMetricValue(
                    text = formattedBlockTimer,
                    faceStyle = hudFaceStyle,
                    fontSize = if (isFreeOrRaceMode) 52.sp else 62.sp,
                    defaultColor = Color.White.copy(alpha = if (isPaused) 0.5f else 1f),
                    retroColor = Color(0xFF00E676).copy(alpha = if (isPaused) 0.5f else 1f),
                    isPrimaryChrono = true,
                    modifier = Modifier.testTag("hud_main_timer_display")
                )
                if (isPaused) {
                    Surface(
                        color = RunColors.brandText,
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Text(
                            text = "PAUSADO",
                            color = Color.Black,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            Surface(
                onClick = onOpenFullscreenHrModal,
                color = if (isHrLive) {
                    hrZoneHeartColor.copy(alpha = 0.14f)
                } else {
                    Color(0xFF151922)
                },
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(
                    1.5.dp,
                    if (isHrLive) hrZoneHeartColor.copy(alpha = 0.55f) else Color(0xFF262D3D)
                ),
                modifier = Modifier
                    .clickable(onClick = onOpenFullscreenHrModal)
                    .testTag("hud_bpm_card")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (isHrLive) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Abrir Monitor Cardíaco em Tela Cheia",
                        tint = hrZoneHeartColor,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = if (isHrLive) "$hrBpm bpm" else "-- bpm",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            Surface(
                color = RunColors.surface,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, RunColors.border)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        RunHudStyledMetricValue(
                            text = distStr,
                            faceStyle = hudFaceStyle,
                            fontSize = if (hudFaceStyle == com.example.data.model.RunHudFaceStyle.CLEAN_PRO) 38.sp else 32.sp,
                            defaultColor = RunColors.brandText,
                            retroColor = Color(0xFF00E676),
                            isPrimaryChrono = false,
                            modifier = Modifier.testTag("hud_total_distance_display")
                        )
                        Text(distanceLabelText, color = RunColors.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(48.dp)
                            .background(RunColors.border)
                    )

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        RunHudStyledMetricValue(
                            text = paceStr,
                            faceStyle = hudFaceStyle,
                            fontSize = if (hudFaceStyle == com.example.data.model.RunHudFaceStyle.CLEAN_PRO) 38.sp else 32.sp,
                            defaultColor = Color.White,
                            retroColor = Color(0xFFFFD600),
                            isPrimaryChrono = false,
                            modifier = Modifier.testTag("hud_current_pace_display")
                        )
                        Text(
                            text = if (isFreeOrRaceMode) "RITMO ATUAL" else "PACE DO BLOCO",
                            color = RunColors.textSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            HudTrack400mLiveCalibrationCard(
                isEnabled = isTrack400mActiveInHud,
                displayedDistanceMeters = displayedDistanceMeters,
                cooldownRemainingSec = lapSnapCooldownSec,
                onSnapLap400m = onSnapLap400m
            )

            HudNextBlockIndicatorPill(
                blocks = blocks,
                currentBlockIndex = currentBlockIndex,
                skippedBlockIndices = skippedBlockIndices,
                isStructuredWorkout = isStructuredPlanWorkout
            )

            Surface(
                color = RunColors.surface,
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = gpsStatusText,
                    color = if (runTrackerStatus == RunTrackerStatus.GOOD) RunColors.phaseWalk else RunColors.phaseWarmup,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun RunningHudBottomControlsSection(
    isLiveMapEnabled: Boolean,
    isLiveMapCompactCard: Boolean,
    mediaTrackDisplayLabel: String,
    mediaTrackHasTitle: Boolean,
    mediaIsPlaying: Boolean,
    isGentleMode: Boolean,
    isFreeOrRaceMode: Boolean,
    currentBlockIndex: Int,
    blocks: List<RunningBlock>,
    currentBlockType: RunningBlockType,
    isUnlocked: Boolean,
    isPaused: Boolean,
    livePhotoCount: Int,
    onSkipCurrentBlock: () -> Unit,
    onLaunchLiveCamera: () -> Unit,
    onUnlock: () -> Unit,
    onLock: () -> Unit,
    onToggleLiveMap: () -> Unit,
    onTogglePause: () -> Unit,
    onConfirmFinish: () -> Unit,
    onUserInteraction: () -> Unit
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (!isLiveMapEnabled || !isLiveMapCompactCard) {
            Surface(
                color = Color(0xFF151922),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Color(0xFF262D3D)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("hud_media_controls_bar")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                AudioSynth.playTickSound()
                                com.example.MainActivity.openMusicOrSpotifyPlayer(context)
                            }
                            .padding(end = 6.dp, top = 2.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = "Abrir Player de Música",
                            tint = Color(0xFF00E5FF),
                            modifier = Modifier.size(17.dp)
                        )
                        Text(
                            text = mediaTrackDisplayLabel,
                            color = when {
                                mediaTrackHasTitle -> Color.White
                                mediaIsPlaying -> Color(0xFF00E5FF)
                                else -> RunColors.textSecondary
                            },
                            fontSize = 12.sp,
                            fontWeight = if (mediaTrackHasTitle || mediaIsPlaying) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier
                                .weight(1f)
                                .basicMarquee()
                                .testTag("hud_media_track_title")
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        IconButton(
                            onClick = {
                                AudioSynth.playTickSound()
                                com.example.MainActivity.dispatchMediaKeyEvent(
                                    context,
                                    android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS
                                )
                            },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipPrevious,
                                contentDescription = "Faixa Anterior",
                                tint = Color.White,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                        IconButton(
                            onClick = {
                                AudioSynth.playTickSound()
                                com.example.MainActivity.dispatchMediaKeyEvent(
                                    context,
                                    android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                                )
                            },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = if (mediaIsPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play / Pause Música",
                                tint = Color(0xFF00E676),
                                modifier = Modifier.size(21.dp)
                            )
                        }
                        IconButton(
                            onClick = {
                                AudioSynth.playTickSound()
                                com.example.MainActivity.dispatchMediaKeyEvent(
                                    context,
                                    android.view.KeyEvent.KEYCODE_MEDIA_NEXT
                                )
                            },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = "Próxima Faixa",
                                tint = Color.White,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                }
            }
        }

        val canShowSkipBlockPill = !isGentleMode &&
            !isFreeOrRaceMode &&
            currentBlockIndex in blocks.indices &&
            (currentBlockType == RunningBlockType.WARMUP || isUnlocked)

        if (canShowSkipBlockPill) {
            Surface(
                onClick = onSkipCurrentBlock,
                color = Color(0xFF151922),
                shape = RoundedCornerShape(50),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
                modifier = Modifier
                    .heightIn(min = 38.dp)
                    .testTag("hud_skip_block_pill")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Pular etapa do treino",
                        tint = RunColors.brandText,
                        modifier = Modifier.size(17.dp)
                    )
                    Text(
                        text = if (currentBlockType == RunningBlockType.WARMUP) "Pular aquecimento" else "Pular bloco",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }

        if (!isUnlocked) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    onClick = onLaunchLiveCamera,
                    color = if (livePhotoCount > 0) Color(0xFF271A07) else Color(0xFF151922),
                    shape = if (livePhotoCount > 0) RoundedCornerShape(24.dp) else CircleShape,
                    border = BorderStroke(
                        1.2.dp,
                        if (livePhotoCount > 0) Color(0xFFFBBF24).copy(alpha = 0.85f) else Color.White.copy(alpha = 0.72f)
                    ),
                    modifier = Modifier
                        .height(48.dp)
                        .widthIn(min = 48.dp)
                        .testTag("hud_live_camera_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = if (livePhotoCount > 0) 12.dp else 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        com.example.ui.components.VectorCameraIcon(
                            modifier = Modifier.size(21.dp),
                            tint = Color.White
                        )
                        if (livePhotoCount > 0) {
                            Text(
                                text = "$livePhotoCount",
                                color = Color(0xFFFBBF24),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Black,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }

                OutlinedButton(
                    onClick = onUnlock,
                    modifier = Modifier.size(64.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = RunColors.surface,
                        contentColor = Color.White
                    ),
                    border = BorderStroke(1.5.dp, RunColors.border),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Destravar controles do treino",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Surface(
                        onClick = onToggleLiveMap,
                        color = if (isLiveMapEnabled) Color(0xFF0C273D) else Color(0xFF151922),
                        shape = CircleShape,
                        border = BorderStroke(
                            1.2.dp,
                            if (isLiveMapEnabled) Color(0xFF00E676).copy(alpha = 0.85f) else Color(0xFF00E5FF).copy(alpha = 0.8f)
                        ),
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("hud_toggle_live_map_button")
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            com.example.ui.components.VectorMapWithPinIcon(
                                modifier = Modifier.size(22.dp),
                                tint = Color.White,
                                pinColor = if (isLiveMapEnabled) Color(0xFF00E676) else Color(0xFF00E5FF)
                            )
                        }
                    }
                    Text(
                        text = "Mapa",
                        color = Color(0xFF00E5FF),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    OutlinedButton(
                        onClick = onLock,
                        modifier = Modifier.size(64.dp),
                        shape = CircleShape,
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = RunColors.surface,
                            contentColor = Color.White
                        ),
                        border = BorderStroke(1.5.dp, RunColors.border),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LockOpen,
                            contentDescription = "Travar controles do treino",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Text(
                        text = "Travar",
                        color = RunColors.textSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Surface(
                        onClick = onToggleLiveMap,
                        modifier = Modifier
                            .size(64.dp)
                            .testTag("hud_toggle_live_map_button_unlocked"),
                        shape = CircleShape,
                        color = if (isLiveMapEnabled) Color(0xFF0C273D) else RunColors.surface,
                        border = BorderStroke(1.5.dp, Color(0xFF00E5FF).copy(alpha = 0.75f))
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            com.example.ui.components.VectorMapWithPinIcon(
                                modifier = Modifier.size(26.dp),
                                tint = Color.White,
                                pinColor = Color(0xFF00E5FF)
                            )
                        }
                    }
                    Text(
                        text = "Mapa",
                        color = Color(0xFF00E5FF),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Surface(
                        onClick = onTogglePause,
                        modifier = Modifier.size(64.dp),
                        shape = CircleShape,
                        color = RunColors.surface,
                        border = BorderStroke(1.5.dp, RunColors.border)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Icon(
                                imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                contentDescription = if (isPaused) "Retomar treino" else "Pausar treino",
                                tint = RunColors.alert,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                    Text(
                        text = if (isPaused) "Retomar" else "Pausar",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }

                HoldToStopButton(
                    onConfirm = onConfirmFinish,
                    onInteraction = onUserInteraction
                )
            }
        }
    }
}

@Composable
private fun RunningHudDialogsSection(
    showExitConfirmationDialog: Boolean,
    showHudFullscreenHrModal: Boolean,
    showMediaMetadataPermissionDialog: Boolean,
    totalElapsedSeconds: Int,
    bleManager: HeartRateBleManager,
    athleteAge: Int? = null,
    calibratedMaxHrBpm: Int? = null,
    calibratedLt1Bpm: Int? = null,
    calibratedLt2Bpm: Int? = null,
    onDismissExitDialog: () -> Unit,
    onConfirmFinishFromExitDialog: () -> Unit,
    onDiscardFromExitDialog: () -> Unit,
    onDismissFullscreenHrModal: () -> Unit,
    onDismissMediaPermissionDialog: () -> Unit
) {
    val context = LocalContext.current

    if (showExitConfirmationDialog) {
        val formattedElapsed = String.format(
            Locale.US,
            "%02d:%02d",
            totalElapsedSeconds / 60,
            totalElapsedSeconds % 60
        )

        AlertDialog(
            onDismissRequest = onDismissExitDialog,
            title = {
                Text(
                    text = "Encerrar treino?",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        text = "Tempo decorrido: $formattedElapsed\n\nDeseja encerrar a sessão atual?",
                        color = RunColors.textSecondary,
                        fontSize = 14.sp
                    )
                    HoldToStopButton(
                        onConfirm = onConfirmFinishFromExitDialog,
                        onInteraction = {}
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onDiscardFromExitDialog,
                        border = BorderStroke(1.dp, RunColors.alert)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Descartar treino",
                            tint = RunColors.alert,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "DESCARTAR TREINO",
                            color = RunColors.alert,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    TextButton(
                        onClick = {
                            onDismissExitDialog()
                            AudioSynth.playTickSound()
                        }
                    ) {
                        Text(
                            text = "CONTINUAR",
                            color = RunColors.brandText,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            },
            containerColor = RunColors.surface
        )
    }

    if (showHudFullscreenHrModal) {
        val hudLiveBpm by bleManager.currentBpm.collectAsState()
        val hudHrSamples = remember(hudLiveBpm, totalElapsedSeconds) {
            bleManager.getHrTimelineString().toHrTimelineList()
        }
        FullscreenHeartRateModal(
            currentBpm = hudLiveBpm,
            avgBpm = bleManager.getAverageBpm(),
            maxBpm = bleManager.getMaxBpm(),
            athleteAge = athleteAge,
            calibratedMaxHrBpm = calibratedMaxHrBpm,
            calibratedLt1Bpm = calibratedLt1Bpm,
            calibratedLt2Bpm = calibratedLt2Bpm,
            hrSamples = hudHrSamples,
            durationSeconds = totalElapsedSeconds,
            onDismiss = onDismissFullscreenHrModal
        )
    }

    if (showMediaMetadataPermissionDialog) {
        AlertDialog(
            onDismissRequest = onDismissMediaPermissionDialog,
            title = {
                Text(
                    text = "Exibir nome da música no HUD",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Text(
                    text = "Para o MAX ler automaticamente o título da faixa e o artista do YouTube Music, Spotify ou Deezer em tempo real, ative a permissão de leitura de mídia/notificações para o app (basta fazer 1 única vez).",
                    color = RunColors.textSecondary,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDismissMediaPermissionDialog()
                        AudioSynth.playTickSound()
                        com.example.MainActivity.openNotificationListenerSettings(context)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RunColors.brand,
                        contentColor = Color.White
                    )
                ) {
                    Text("Ativar agora (1x)", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        onDismissMediaPermissionDialog()
                        AudioSynth.playTickSound()
                        com.example.MainActivity.openMusicOrSpotifyPlayer(context)
                    }
                ) {
                    Text("Abrir Player", color = RunColors.brandText, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = RunColors.surface
        )
    }
}

/**
 * Componente de Dígitos Rolantes estilo Hodômetro Clássico Anos 90 (`ODOMETER_90S`).
 * - Quebra a string caractere por caractere (`text.forEachIndexed`).
 * - Separadores fixos (`:`, `'`, `"`, `.`, `,`, `k`, `m`) são renderizados estaticamente sem animar.
 * - Cada dígito numérico (`0..9`) é envolvido em um cilindro individual sutil (`#11151E`, `RoundedCornerShape(4.dp)`)
 *   com borda e sombra interna vertical simulando o tambor mecânico, girando verticalmente via `AnimatedContent`
 *   apenas quando aquele dígito específico muda.
 */
@Composable
fun OdometerRollingText(
    text: String,
    fontSize: androidx.compose.ui.unit.TextUnit,
    digitColor: Color = Color.White,
    separatorColor: Color = digitColor.copy(alpha = 0.78f),
    drumBgColor: Color = Color(0xFF11151E),
    drumBorderColor: Color = Color(0xFF262F42),
    isCompactDrum: Boolean = false,
    modifier: Modifier = Modifier
) {
    val hPad = if (isCompactDrum) 3.dp else 5.dp
    val vPad = if (isCompactDrum) 1.dp else 2.dp

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (isCompactDrum) 1.5.dp else 2.5.dp)
    ) {
        text.forEachIndexed { charIndex, ch ->
            key(charIndex) {
                if (ch.isDigit()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(drumBgColor)
                            .border(0.75.dp, drumBorderColor, RoundedCornerShape(4.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        AnimatedContent(
                            targetState = ch,
                            transitionSpec = {
                                (slideInVertically(
                                    animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing)
                                ) { height -> height } + fadeIn(animationSpec = tween(220))) togetherWith
                                    (slideOutVertically(
                                        animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing)
                                    ) { height -> -height } + fadeOut(animationSpec = tween(220)))
                            },
                            label = "odometer_digit_$charIndex"
                        ) { targetDigit ->
                            Text(
                                text = targetDigit.toString(),
                                color = digitColor,
                                fontSize = fontSize,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = hPad, vertical = vPad)
                            )
                        }

                        // Sombra interna superior e inferior simulando a curvatura do tambor mecânico
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(
                                    Brush.verticalGradient(
                                        0.0f to Color.Black.copy(alpha = 0.52f),
                                        0.22f to Color.Transparent,
                                        0.78f to Color.Transparent,
                                        1.0f to Color.Black.copy(alpha = 0.56f)
                                    )
                                )
                        )
                    }
                } else {
                    val isUnitLetter = ch.isLetter()
                    Text(
                        text = ch.toString(),
                        color = if (isUnitLetter) separatorColor.copy(alpha = 0.85f) else separatorColor,
                        fontSize = if (isUnitLetter) (fontSize.value * 0.56f).sp else (fontSize.value * 0.82f).sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = if (ch == ' ') 1.dp else 0.5.dp)
                    )
                }
            }
        }
    }
}

/**
 * Renderizador unificado das métricas principais do HUD de Corrida conforme o `RunHudFaceStyle` selecionado:
 * - `CLEAN_PRO`: Tipografia monoespaçada moderna de alta precisão e troca instantânea.
 * - `ODOMETER_90S`: Tambores mecânicos individuais com giro vertical (`OdometerRollingText`).
 * - `RETRO_CHRONO`: Estética de cronógrafo digital LCD esportivo dos anos 90 em verde-fósforo (#00E676) / âmbar (#FFD600) sobre matriz escura emoldurada.
 */
@Composable
fun RunHudStyledMetricValue(
    text: String,
    faceStyle: com.example.data.model.RunHudFaceStyle,
    fontSize: androidx.compose.ui.unit.TextUnit,
    defaultColor: Color,
    retroColor: Color = Color(0xFF00E676),
    isPrimaryChrono: Boolean = false,
    modifier: Modifier = Modifier
) {
    // Proteção do Cronômetro Gigante do HUD: evita que o aumento de escala de fonte ("A+") estoure os dígitos na tela
    val currentFontScale = androidx.compose.ui.platform.LocalDensity.current.fontScale.coerceAtLeast(1f)
    val effectiveFontSize = if (isPrimaryChrono && currentFontScale > 1.02f) {
        val maxVisualSp = if (text.length >= 7) 56f else 66f
        val clampedSp = (maxVisualSp / currentFontScale).coerceAtMost(fontSize.value)
        clampedSp.sp
    } else {
        fontSize
    }

    when (faceStyle) {
        com.example.data.model.RunHudFaceStyle.CLEAN_PRO -> {
            Text(
                text = text,
                color = defaultColor,
                fontSize = effectiveFontSize,
                fontWeight = FontWeight.Black,
                fontFamily = if (isPrimaryChrono) FontFamily.Monospace else FontFamily.Default,
                maxLines = 1,
                softWrap = false,
                modifier = modifier
            )
        }
        com.example.data.model.RunHudFaceStyle.ODOMETER_90S -> {
            OdometerRollingText(
                text = text,
                fontSize = if (isPrimaryChrono) (effectiveFontSize.value * 0.86f).sp else (effectiveFontSize.value * 0.84f).sp,
                digitColor = defaultColor,
                isCompactDrum = !isPrimaryChrono,
                modifier = modifier
            )
        }
        com.example.data.model.RunHudFaceStyle.RETRO_CHRONO -> {
            Surface(
                color = Color(0xFF08100C),
                shape = RoundedCornerShape(if (isPrimaryChrono) 12.dp else 8.dp),
                border = BorderStroke(
                    width = if (isPrimaryChrono) 1.5.dp else 1.dp,
                    color = retroColor.copy(alpha = 0.48f)
                ),
                modifier = modifier
            ) {
                Box(
                    modifier = Modifier
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFF0D1913),
                                    Color(0xFF070E0A),
                                    Color(0xFF0D1913)
                                )
                            )
                        )
                        .padding(
                            horizontal = if (isPrimaryChrono) 14.dp else 10.dp,
                            vertical = if (isPrimaryChrono) 6.dp else 4.dp
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    // Camada fantasma sutil de matriz LCD 88:88 ao fundo
                    val ghostLcd = remember(text) {
                        text.map { if (it.isDigit()) '8' else it }.joinToString("")
                    }
                    Text(
                        text = ghostLcd,
                        color = retroColor.copy(alpha = 0.08f),
                        fontSize = if (isPrimaryChrono) (effectiveFontSize.value * 0.88f).sp else (effectiveFontSize.value * 0.84f).sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp,
                        maxLines = 1,
                        softWrap = false
                    )
                    Text(
                        text = text,
                        color = retroColor,
                        fontSize = if (isPrimaryChrono) (effectiveFontSize.value * 0.88f).sp else (effectiveFontSize.value * 0.84f).sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}

/**
 * Card Seletor "Estilo do Mostrador de Corrida (HUD Face)" para a aba Perfil / Configurações.
 * Mantém a Home e o HUD de corrida 100% limpos (zero botões extras no HUD) e exibe mini-previews
 * interativos das 3 opções (`CLEAN_PRO`, `ODOMETER_90S`, `RETRO_CHRONO`).
 */
@Composable
fun RunHudWatchfaceSelectorCard(
    selectedStyle: com.example.data.model.RunHudFaceStyle,
    onSelectStyle: (com.example.data.model.RunHudFaceStyle) -> Unit,
    modifier: Modifier = Modifier
) {
    // Ticker suave para demonstrar os tambores do hodômetro girando ao vivo no mini-preview
    var demoSeconds by remember { mutableIntStateOf(436) } // 07:16
    LaunchedEffect(Unit) {
        while (isActive) {
            delay(1200L)
            demoSeconds = (demoSeconds + 1) % 3600
        }
    }
    val demoTimerText = remember(demoSeconds) {
        val m = demoSeconds / 60
        val s = demoSeconds % 60
        String.format(Locale.US, "%02d:%02d", m, s)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("profile_run_hud_watchface_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161922)),
        border = BorderStroke(1.dp, Color(0xFF262B38))
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
                            .background(Color(0xFF00E5FF), RoundedCornerShape(2.dp))
                    )
                    Text(
                        text = "ESTILO DO MOSTRADOR DE CORRIDA (HUD FACE)",
                        color = Color(0xFFF8FAFC),
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontStyle = FontStyle.Italic,
                        letterSpacing = (-0.3).sp
                    )
                }
                Text(
                    text = "MAX RUN",
                    color = Color(0xFF00E5FF),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = "Personalize o visual do cronômetro, distância e pace durante a corrida:",
                color = Color(0xFF94A3B8),
                fontSize = 11.5.sp,
                lineHeight = 15.sp
            )

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                com.example.data.model.RunHudFaceStyle.entries.forEach { styleOption ->
                    val isSelected = styleOption == selectedStyle
                    val borderColor = when {
                        !isSelected -> Color(0xFF262B38)
                        styleOption == com.example.data.model.RunHudFaceStyle.RETRO_CHRONO -> Color(0xFF00E676)
                        else -> Color(0xFF00E5FF)
                    }
                    val bgColor = when {
                        !isSelected -> Color(0xFF10131A)
                        styleOption == com.example.data.model.RunHudFaceStyle.RETRO_CHRONO -> Color(0xFF00E676).copy(alpha = 0.08f)
                        else -> Color(0xFF00B0FF).copy(alpha = 0.10f)
                    }

                    Surface(
                        onClick = {
                            AudioSynth.playTickSound()
                            onSelectStyle(styleOption)
                        },
                        color = bgColor,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, borderColor),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("hud_face_option_${styleOption.name.lowercase()}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 11.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = null,
                                        colors = RadioButtonDefaults.colors(
                                            selectedColor = borderColor,
                                            unselectedColor = Color(0xFF64748B)
                                        ),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = styleOption.displayTitle,
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = styleOption.subtitle,
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.sp,
                                    lineHeight = 14.sp
                                )
                            }

                            // Mini-Preview Visual Ao Vivo de cada Estilo
                            Box(
                                contentAlignment = Alignment.Center
                            ) {
                                when (styleOption) {
                                    com.example.data.model.RunHudFaceStyle.CLEAN_PRO -> {
                                        Surface(
                                            color = Color(0xFF0D0F14),
                                            shape = RoundedCornerShape(8.dp),
                                            border = BorderStroke(1.dp, Color(0xFF232A38))
                                        ) {
                                            Text(
                                                text = demoTimerText,
                                                color = Color.White,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Black,
                                                fontFamily = FontFamily.Monospace,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                            )
                                        }
                                    }
                                    com.example.data.model.RunHudFaceStyle.ODOMETER_90S -> {
                                        OdometerRollingText(
                                            text = demoTimerText,
                                            fontSize = 14.sp,
                                            digitColor = Color(0xFF00E5FF),
                                            isCompactDrum = true
                                        )
                                    }
                                    com.example.data.model.RunHudFaceStyle.RETRO_CHRONO -> {
                                        RunHudStyledMetricValue(
                                            text = demoTimerText,
                                            faceStyle = com.example.data.model.RunHudFaceStyle.RETRO_CHRONO,
                                            fontSize = 15.sp,
                                            defaultColor = Color(0xFF00E676),
                                            retroColor = Color(0xFF00E676),
                                            isPrimaryChrono = false
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


