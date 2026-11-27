package com.example

import android.os.Bundle
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Warning
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.ui.platform.testTag
import com.example.data.model.AppMode
import com.example.data.model.UserProfileData
import com.example.ui.screens.*
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.AppScreen
import com.example.ui.viewmodel.MainViewModel
import com.example.ui.viewmodel.ProfileGate
import androidx.compose.runtime.CompositionLocalProvider
import com.example.ui.adaptive.*
import com.example.utils.AppBackupManager
import com.example.utils.AudioSynth
import com.example.utils.customViewForScrollCapture
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    
    companion object {
        @Volatile
        var isAppInForeground: Boolean = false

        @Volatile
        var isRunningSessionActive: Boolean = false

        @Volatile
        var isWristGuardEnabled: Boolean = true

        @Volatile
        var isScreenPinningActive: Boolean = false

        @Volatile
        var intentionalExternalNavUntilElapsedMs: Long = 0L

        @Volatile
        var intentionalExternalNavStartedAtMs: Long = 0L

        /**
         * Registra uma navegação externa intencional do usuário (ex: abrir Configurações de GPS,
         * diálogo de permissão, Spotify/player de música ou compartilhamento) para que a Trava de Pulso
         * não puxe o foco de volta durante a ação voluntária (60s para música/Spotify).
         */
        fun markIntentionalExternalNavigation(durationMs: Long = 60_000L) {
            val now = android.os.SystemClock.elapsedRealtime()
            intentionalExternalNavStartedAtMs = now
            intentionalExternalNavUntilElapsedMs = now + durationMs
        }

        fun isWithinIntentionalExternalNav(): Boolean {
            return android.os.SystemClock.elapsedRealtime() <= intentionalExternalNavUntilElapsedMs
        }

        /**
         * Envia comando de mídia nativo (Anterior, Play/Pause, Próxima) priorizando o Spotify (com.spotify.music)
         * sem acionar o YouTube Music por engano. Se o botão Play for acionado com o Spotify fechado na memória,
         * tenta iniciar a reprodução e abre o Spotify automaticamente caso o áudio não inicie.
         */
        fun dispatchMediaKeyEvent(context: android.content.Context, keyCode: Int) {
            runCatching {
                val audioManager = context.getSystemService(android.content.Context.AUDIO_SERVICE) as? android.media.AudioManager
                val mediaInfoBefore = com.example.services.RunTracker.mediaTrackInfo.value
                val wasPlayingBefore = (audioManager?.isMusicActive == true) || mediaInfoBefore.isPlaying
                val isPlayAttempt = keyCode == android.view.KeyEvent.KEYCODE_MEDIA_PLAY ||
                    (keyCode == android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE && !wasPlayingBefore)

                val handledBySession = com.example.services.MediaMetadataListenerService.dispatchCommandToPreferredController(
                    context = context,
                    keyCode = keyCode
                )

                val spotifyInstalled = runCatching {
                    context.packageManager.getLaunchIntentForPackage("com.spotify.music") != null
                }.getOrDefault(false)

                val otherPlayerActivelyPlaying = wasPlayingBefore &&
                    mediaInfoBefore.sourcePackage.isNotBlank() &&
                    !mediaInfoBefore.sourcePackage.equals("com.spotify.music", ignoreCase = true)

                if (!handledBySession) {
                    val eventTime = android.os.SystemClock.uptimeMillis()
                    val downEvent = android.view.KeyEvent(
                        eventTime,
                        eventTime,
                        android.view.KeyEvent.ACTION_DOWN,
                        keyCode,
                        0
                    )
                    val upEvent = android.view.KeyEvent(
                        eventTime,
                        eventTime,
                        android.view.KeyEvent.ACTION_UP,
                        keyCode,
                        0
                    )

                    if (spotifyInstalled && !otherPlayerActivelyPlaying) {
                        // Direciona o MEDIA_BUTTON exclusivamente para o pacote do Spotify para não acordar o YouTube Music
                        runCatching {
                            val downIntent = android.content.Intent(android.content.Intent.ACTION_MEDIA_BUTTON).apply {
                                setPackage("com.spotify.music")
                                putExtra(android.content.Intent.EXTRA_KEY_EVENT, downEvent)
                            }
                            val upIntent = android.content.Intent(android.content.Intent.ACTION_MEDIA_BUTTON).apply {
                                setPackage("com.spotify.music")
                                putExtra(android.content.Intent.EXTRA_KEY_EVENT, upEvent)
                            }
                            context.sendBroadcast(downIntent)
                            context.sendBroadcast(upIntent)
                        }
                        // Se já havia música tocando (ex: usuário pausando/pulando faixa sem permissão de notificação ativa),
                        // despacha também via AudioManager para controlar o stream ativo.
                        if (wasPlayingBefore) {
                            audioManager?.dispatchMediaKeyEvent(downEvent)
                            audioManager?.dispatchMediaKeyEvent(upEvent)
                        }
                    } else {
                        audioManager?.dispatchMediaKeyEvent(downEvent)
                        audioManager?.dispatchMediaKeyEvent(upEvent)
                    }
                }

                val appCtx = context.applicationContext
                val handler = android.os.Handler(android.os.Looper.getMainLooper())
                handler.postDelayed({
                    val activeNow = (appCtx.getSystemService(android.content.Context.AUDIO_SERVICE) as? android.media.AudioManager)?.isMusicActive == true
                    com.example.services.RunTracker.updateSystemMusicPlayingState(activeNow)
                    com.example.services.MediaMetadataListenerService.refreshMediaSessions(appCtx)
                }, 350L)
                handler.postDelayed({
                    val activeNow = (appCtx.getSystemService(android.content.Context.AUDIO_SERVICE) as? android.media.AudioManager)?.isMusicActive == true
                    com.example.services.RunTracker.updateSystemMusicPlayingState(activeNow)
                    com.example.services.MediaMetadataListenerService.refreshMediaSessions(appCtx)

                    val isPlayingAfter = activeNow || com.example.services.RunTracker.mediaTrackInfo.value.isPlaying
                    if (isPlayAttempt && !isPlayingAfter) {
                        // Se o Spotify estava fechado na memória e não iniciou o áudio em background, abre o Spotify direto
                        openMusicOrSpotifyPlayer(context)
                    }
                }, 650L)
            }
        }

        /**
         * Abre a tela de Acesso a Notificações do Android (suspendendo a trava de pulso por 60s)
         * para permitir a leitura nativa de título e artista do Spotify / Deezer / YouTube Music via MediaSession.
         */
        fun openNotificationListenerSettings(context: android.content.Context) {
            markIntentionalExternalNavigation(60_000L)
            runCatching {
                val intent = android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }.onFailure {
                android.widget.Toast.makeText(
                    context,
                    "Abra Configurações > Acesso a notificações e ative o MAX",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
        }

        /**
         * Abre prioritariamente o pacote do Spotify (com.spotify.music) ao tocar no player do HUD,
         * evitando abrir o YouTube Music por engano. Se o Spotify não estiver instalado, usa fallback
         * inteligente para a sessão ativa ou seletor de música do sistema.
         */
        fun openMusicOrSpotifyPlayer(context: android.content.Context) {
            markIntentionalExternalNavigation(60_000L)
            try {
                val pm = context.packageManager
                val mediaInfo = com.example.services.RunTracker.mediaTrackInfo.value
                val activePkg = mediaInfo.sourcePackage
                val otherPlayerActivelyPlaying = mediaInfo.isPlaying &&
                    activePkg.isNotBlank() &&
                    !activePkg.equals("com.spotify.music", ignoreCase = true)

                val candidatePackages = buildList {
                    if (otherPlayerActivelyPlaying) {
                        add(activePkg)
                    }
                    // Força prioridade máxima para o pacote oficial do Spotify
                    add("com.spotify.music")
                    if (activePkg.isNotBlank()) add(activePkg)
                    add("deezer.android.app")
                    add("com.sec.android.app.music")
                    add("com.amazon.mp3")
                    add("com.apple.android.music")
                    add("com.google.android.apps.youtube.music")
                }.distinct()

                for (pkg in candidatePackages) {
                    val launchIntent = pm.getLaunchIntentForPackage(pkg)?.apply {
                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    if (launchIntent != null) {
                        context.startActivity(launchIntent)
                        return
                    }
                }

                val musicIntent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
                    addCategory(android.content.Intent.CATEGORY_APP_MUSIC)
                    flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(musicIntent)
            } catch (e: Exception) {
                try {
                    val mediaIntent = android.content.Intent(android.provider.MediaStore.INTENT_ACTION_MUSIC_PLAYER).apply {
                        flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(mediaIntent)
                } catch (e2: Exception) {
                    android.widget.Toast.makeText(
                        context,
                        "Nenhum player de música encontrado no dispositivo",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        /**
         * Camada 1 — Flags de Janela de Alta Prioridade enquanto a corrida estiver ativa:
         * FLAG_KEEP_SCREEN_ON + setShowWhenLocked(true) + setTurnScreenOn(true).
         */
        fun updateRunningSessionWindowShield(activity: android.app.Activity?, active: Boolean) {
            isRunningSessionActive = active
            if (activity == null) return
            runCatching {
                if (active) {
                    activity.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                        activity.setShowWhenLocked(true)
                        activity.setTurnScreenOn(true)
                    } else {
                        @Suppress("DEPRECATION")
                        activity.window?.addFlags(
                            android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                            android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                        )
                    }
                } else {
                    activity.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                        activity.setShowWhenLocked(false)
                        activity.setTurnScreenOn(false)
                    } else {
                        @Suppress("DEPRECATION")
                        activity.window?.clearFlags(
                            android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                            android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                        )
                    }
                    if (isScreenPinningActive) {
                        runCatching { activity.stopLockTask() }
                        isScreenPinningActive = false
                    }
                }
            }
        }

        /**
         * Sanity Check Anti-Notificação Zumbi:
         * Caso o app esteja aberto (ou na tela de resumo pós-treino) sem uma sessão de musculação
         * ou corrida efetivamente em execução, encerra qualquer serviço órfão e remove as notificações.
         */
        fun cancelOrphanNotifications(
            context: android.content.Context,
            isWorkoutActive: Boolean,
            isRunningActive: Boolean
        ) {
            runCatching {
                if (!isWorkoutActive) {
                    com.example.services.WorkoutForegroundService.stopServiceAndClearNotifications(context)
                }
                if (!isRunningActive) {
                    com.example.services.RunningTrackingService.stopServiceAndClearNotification(context)
                }
                if (!isWorkoutActive && !isRunningActive) {
                    val nm = context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
                    nm?.cancel(com.example.services.WorkoutForegroundService.NOTIFICATION_ID)
                    nm?.cancel(com.example.services.WorkoutForegroundService.REST_ALERT_NOTIFICATION_ID)
                    nm?.cancel(com.example.services.RunningTrackingService.NOTIFICATION_ID)
                }
            }
        }
    }

    private var lastHardwareKeyInterceptMs: Long = 0L
    private val wristGuardHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val wristGuardRestoreRunnable = Runnable {
        runCatching {
            if (isRunningSessionActive &&
                isWristGuardEnabled &&
                !isAppInForeground &&
                !isWithinIntentionalExternalNav() &&
                !isFinishing &&
                !isDestroyed
            ) {
                val restoreIntent = android.content.Intent(applicationContext, MainActivity::class.java).apply {
                    addFlags(
                        android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                        android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                    )
                }
                applicationContext.startActivity(restoreIntent)
            }
        }
    }

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (isRunningSessionActive && isWristGuardEnabled) {
            val code = event.keyCode
            // Bloqueia disparo físico de câmera/foco na munhequeira
            if (code == android.view.KeyEvent.KEYCODE_CAMERA ||
                code == android.view.KeyEvent.KEYCODE_FOCUS
            ) {
                return true
            }
            // Bloqueia duplo clique rápido de botão de volume que alguns aparelhos usam como atalho de câmera
            if (code == android.view.KeyEvent.KEYCODE_VOLUME_DOWN ||
                code == android.view.KeyEvent.KEYCODE_VOLUME_UP
            ) {
                if (event.action == android.view.KeyEvent.ACTION_DOWN) {
                    val now = android.os.SystemClock.elapsedRealtime()
                    if (now - lastHardwareKeyInterceptMs in 1..320 || event.repeatCount > 0) {
                        return true
                    }
                    lastHardwareKeyInterceptMs = now
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onResume() {
        super.onResume()
        isAppInForeground = true
        wristGuardHandler.removeCallbacks(wristGuardRestoreRunnable)
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - intentionalExternalNavStartedAtMs > 2_500L) {
            intentionalExternalNavUntilElapsedMs = 0L
        }
        if (isRunningSessionActive) {
            updateRunningSessionWindowShield(this, true)
        }
        cancelOrphanNotifications(
            context = this,
            isWorkoutActive = viewModel.isGymWorkoutActivelyRunning(),
            isRunningActive = isRunningSessionActive || viewModel.isRunningHudActive.value
        )
        if (viewModel.isInitialSessionRecoveryDone.value && !viewModel.isSessionRecoveryCompleted()) {
            viewModel.checkActiveSessions()
        }
        runCatching {
            val activeNow = (getSystemService(android.content.Context.AUDIO_SERVICE) as? android.media.AudioManager)?.isMusicActive == true
            com.example.services.RunTracker.updateSystemMusicPlayingState(activeNow)
            com.example.services.MediaMetadataListenerService.refreshMediaSessions(this)
        }
    }

    override fun onPause() {
        isAppInForeground = false
        scheduleWristGuardFocusRecoveryIfNeeded()
        super.onPause()
    }

    override fun onStop() {
        scheduleWristGuardFocusRecoveryIfNeeded()
        super.onStop()
    }

    override fun onDestroy() {
        wristGuardHandler.removeCallbacks(wristGuardRestoreRunnable)
        super.onDestroy()
    }

    private fun scheduleWristGuardFocusRecoveryIfNeeded() {
        runCatching {
            if (isRunningSessionActive &&
                isWristGuardEnabled &&
                !isWithinIntentionalExternalNav() &&
                !isChangingConfigurations &&
                !isFinishing
            ) {
                wristGuardHandler.removeCallbacks(wristGuardRestoreRunnable)
                wristGuardHandler.postDelayed(wristGuardRestoreRunnable, 450L)
            }
        }
    }
    
    private val viewModel: MainViewModel by viewModels()

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                Log.w("MainActivity", "Usuário negou permissão de notificação — alarme de descanso não vai aparecer.")
            }
        }

    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        // Blindagem Anti-Crash ao Reabrir: limpa arquivos de diagnóstico antigos e estados semi-abertos
        // sem nunca bloquear o atleta na tela de CrashReportActivity ao abrir o app.
        try {
            val crashFile = java.io.File(filesDir, "last_crash.txt")
            if (crashFile.exists()) {
                val content = runCatching { crashFile.readText() }.getOrDefault("")
                if (content.isNotBlank()) {
                    android.util.Log.w("MAX_CRASH_RECOVERY", "Recovered from previous exception:\n$content")
                }
                runCatching { crashFile.delete() }
            }
        } catch (_: Exception) {}
        
        try {
            // Initialize Core Telemetry Engine
            com.example.services.telemetry.Telemetry.initialize(applicationContext)
        } catch (e: Exception) {
            com.example.utils.CrashLog.record(e, "MainActivity.initTelemetry")
            android.util.Log.w("MAX_PERFORMANCE_DEBUG", "Failed to initialize Telemetry safely", e)
        }
        
        try {
            // Initialize MaxSoundManager for audio sprint
            com.example.utils.MaxSoundManager.init(applicationContext)
        } catch (e: Exception) {
            com.example.utils.CrashLog.record(e, "MainActivity.initMaxSoundManager")
            android.util.Log.w("MAX_PERFORMANCE_DEBUG", "Failed to initialize MaxSoundManager safely", e)
        }
        
        // Edge-to-Edge full immersion layout com ícones claros sobre fundo escuro
        enableEdgeToEdge(
            statusBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        
        ensureNotificationPermission()
        
        setContent {
            MyApplicationTheme {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF0D0F14)),
                    contentAlignment = Alignment.TopCenter
                ) {
                    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
                    val currentDensity = androidx.compose.ui.platform.LocalDensity.current
                    val screenWidthDp = configuration.screenWidthDp.takeIf { it > 0 } ?: maxWidth.value.toInt()
                    val isTablet = screenWidthDp >= 600
                    val targetScale = if (screenWidthDp < 600) {
                        (screenWidthDp / 411f).coerceIn(0.82f, 1.15f)
                    } else {
                        1.0f
                    }
                    val adaptedDensity = remember(currentDensity.density, targetScale) {
                        androidx.compose.ui.unit.Density(
                            density = currentDensity.density * targetScale,
                            fontScale = 1.0f
                        )
                    }

                    CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides adaptedDensity) {
                        androidx.compose.foundation.layout.Box(
                            modifier = if (isTablet) {
                                Modifier
                                    .fillMaxHeight()
                                    .widthIn(max = 840.dp)
                                    .fillMaxWidth()
                            } else {
                                Modifier.fillMaxSize()
                            }
                        ) {
                            val profileGate by viewModel.profileGate.collectAsState()
                            when (profileGate) {
                                ProfileGate.Loading -> {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color(0xFF0D0F14))
                                            .statusBarsPadding()
                                            .navigationBarsPadding()
                                            .testTag("profile_gate_loading"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(color = Color(0xFF00B0FF))
                                    }
                                }
                                ProfileGate.SetupNeeded -> {
                                    val setupContext = androidx.compose.ui.platform.LocalContext.current
                                    val setupScope = rememberCoroutineScope()
                                    var isRestoringBackup by remember { mutableStateOf(false) }
                                    var backupStatusMessage by remember { mutableStateOf<String?>(null) }
                                    var backupStatusIsError by remember { mutableStateOf(false) }

                                    val restoreBackupLauncher = rememberLauncherForActivityResult(
                                        contract = ActivityResultContracts.OpenDocument()
                                    ) { uri ->
                                        if (uri == null) return@rememberLauncherForActivityResult
                                        isRestoringBackup = true
                                        backupStatusMessage = "Validando e restaurando backup..."
                                        backupStatusIsError = false
                                        setupScope.launch {
                                            val result = AppBackupManager.importBackupFromUri(
                                                context = setupContext,
                                                uri = uri,
                                                onUpcomingRacesRestored = { viewModel.reloadUpcomingRacesFromPrefs() }
                                            )
                                            isRestoringBackup = false
                                            result.onSuccess { summary ->
                                                if (summary.hasUserProfile) {
                                                    backupStatusMessage = "Backup restaurado: ${summary.toHumanReadableSummary()}"
                                                    backupStatusIsError = false
                                                    android.widget.Toast.makeText(
                                                        setupContext,
                                                        "Backup restaurado com sucesso!",
                                                        android.widget.Toast.LENGTH_LONG
                                                    ).show()
                                                } else {
                                                    backupStatusMessage = "Dados restaurados (${summary.toHumanReadableSummary()}), mas sem perfil salvo. Preencha seus dados acima para continuar."
                                                    backupStatusIsError = false
                                                    android.widget.Toast.makeText(
                                                        setupContext,
                                                        "Dados restaurados! Complete seu perfil para continuar.",
                                                        android.widget.Toast.LENGTH_LONG
                                                    ).show()
                                                }
                                            }.onFailure { err ->
                                                val msg = "Falha ao restaurar backup: ${err.message ?: "arquivo inválido"}"
                                                backupStatusMessage = msg
                                                backupStatusIsError = true
                                                android.widget.Toast.makeText(
                                                    setupContext,
                                                    msg,
                                                    android.widget.Toast.LENGTH_LONG
                                                ).show()
                                            }
                                        }
                                    }

                                    ProfileSetupScreen(
                                        onCompleteSetup = { name, gender, age, heightCm, weightKg, weeklyGoal ->
                                            viewModel.completeInitialProfileSetup(
                                                name = name,
                                                gender = gender,
                                                age = age,
                                                heightCm = heightCm,
                                                weightKg = weightKg,
                                                weeklyGoal = weeklyGoal
                                            )
                                        },
                                        footerContent = {
                                            HorizontalDivider(
                                                color = Color(0xFF262B38),
                                                thickness = 1.dp
                                            )

                                            OutlinedButton(
                                                onClick = {
                                                    AudioSynth.playTickSound()
                                                    restoreBackupLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                                                },
                                                enabled = !isRestoringBackup,
                                                shape = RoundedCornerShape(12.dp),
                                                border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.7f)),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .heightIn(min = 48.dp)
                                                    .testTag("btn_setup_restore_backup")
                                            ) {
                                                if (isRestoringBackup) {
                                                    CircularProgressIndicator(
                                                        color = Color(0xFF10B981),
                                                        strokeWidth = 2.dp,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                } else {
                                                    Icon(
                                                        imageVector = Icons.Default.UploadFile,
                                                        contentDescription = "Restaurar de backup em JSON",
                                                        tint = Color(0xFF10B981),
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = if (isRestoringBackup) "Restaurando backup..." else "Restaurar de backup (.json)",
                                                    color = Color(0xFF10B981),
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }

                                            if (!backupStatusMessage.isNullOrBlank()) {
                                                Text(
                                                    text = backupStatusMessage!!,
                                                    color = if (backupStatusIsError) Color(0xFFEF4444) else Color(0xFF10B981),
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .testTag("text_setup_backup_status")
                                                )
                                            }

                                            if (com.example.BuildConfig.DEBUG) {
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Surface(
                                                    color = Color(0xFF161922),
                                                    shape = RoundedCornerShape(14.dp),
                                                    border = BorderStroke(1.dp, Color(0xFF262B38)),
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .testTag("setup_debug_presets_section")
                                                ) {
                                                    Column(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(12.dp),
                                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                                    ) {
                                                        Text(
                                                            text = "Atalhos DEV (DEBUG)",
                                                            color = Color(0xFF94A3B8),
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                        ) {
                                                            OutlinedButton(
                                                                onClick = {
                                                                    viewModel.loadPresetProfile(UserProfileData.PRESET_ESNAIDER)
                                                                    android.widget.Toast.makeText(
                                                                        setupContext,
                                                                        "Preset DEBUG carregado: Esnaider",
                                                                        android.widget.Toast.LENGTH_SHORT
                                                                    ).show()
                                                                },
                                                                enabled = !isRestoringBackup,
                                                                shape = RoundedCornerShape(10.dp),
                                                                border = BorderStroke(1.dp, Color(0xFF00B0FF).copy(alpha = 0.55f)),
                                                                modifier = Modifier
                                                                    .weight(1f)
                                                                    .heightIn(min = 48.dp)
                                                                    .testTag("btn_setup_debug_esnaider")
                                                            ) {
                                                                Text(
                                                                    text = "Esnaider",
                                                                    color = Color(0xFF00B0FF),
                                                                    fontSize = 12.sp,
                                                                    fontWeight = FontWeight.Bold
                                                                )
                                                            }
                                                            OutlinedButton(
                                                                onClick = {
                                                                    viewModel.loadPresetProfile(UserProfileData.PRESET_ALYNE)
                                                                    android.widget.Toast.makeText(
                                                                        setupContext,
                                                                        "Preset DEBUG carregado: Alyne",
                                                                        android.widget.Toast.LENGTH_SHORT
                                                                    ).show()
                                                                },
                                                                enabled = !isRestoringBackup,
                                                                shape = RoundedCornerShape(10.dp),
                                                                border = BorderStroke(1.dp, Color(0xFF00B0FF).copy(alpha = 0.55f)),
                                                                modifier = Modifier
                                                                    .weight(1f)
                                                                    .heightIn(min = 48.dp)
                                                                    .testTag("btn_setup_debug_alyne")
                                                            ) {
                                                                Text(
                                                                    text = "Alyne",
                                                                    color = Color(0xFF00B0FF),
                                                                    fontSize = 12.sp,
                                                                    fontWeight = FontWeight.Bold
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    )
                                }
                                ProfileGate.Ready -> {
                            val activeScreen by viewModel.activeScreen.collectAsState()
                    val sessionPrepStatus by viewModel.sessionPrepStatus.collectAsState()
                    val levelSuggestion by viewModel.showLevelUpSuggestion.collectAsState()
                    val workoutError by viewModel.workoutErrorMessage.collectAsState()
                    val showActiveSessionPrompt by viewModel.showActiveSessionPrompt.collectAsState()
                    val activeSessions by viewModel.activeSessionsList.collectAsState()
                    val isConflict by viewModel.isMultipleActiveSessionsConflict.collectAsState()
                    val sessionToDiscardConfirm by viewModel.sessionToDiscardConfirm.collectAsState()
                    val showNoSetsDialog by viewModel.showNoSetsDialog.collectAsState()
                    val sessionArchivedNotification by viewModel.sessionArchivedNotification.collectAsState()

                    val context = androidx.compose.ui.platform.LocalContext.current
                    LaunchedEffect(sessionArchivedNotification) {
                        sessionArchivedNotification?.let { msg ->
                            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
                            viewModel.clearSessionArchivedNotification()
                        }
                    }
                    val activity = context as? android.app.Activity ?: (context as? android.content.ContextWrapper)?.baseContext as? android.app.Activity
                    val isRunningHudActive by viewModel.isRunningHudActive.collectAsState()
                    val activeWorkoutState by viewModel.activeWorkout.collectAsState()
                    val workoutFlowState by viewModel.workoutFlowState.collectAsState()
                    val isPreSessionActive by viewModel.isPreSessionActive.collectAsState()
                    LaunchedEffect(isRunningHudActive) {
                        updateRunningSessionWindowShield(activity, isRunningHudActive)
                    }
                    LaunchedEffect(activeScreen, isRunningHudActive, activeWorkoutState, workoutFlowState, isPreSessionActive) {
                        val gymRunningNow = activeScreen == AppScreen.PLAYER &&
                            activeWorkoutState != null &&
                            workoutFlowState == com.example.ui.viewmodel.WorkoutFlowState.Running &&
                            !isPreSessionActive
                        val runRunningNow = isRunningHudActive || isRunningSessionActive
                        if (!gymRunningNow || !runRunningNow) {
                            cancelOrphanNotifications(
                                context = context,
                                isWorkoutActive = gymRunningNow,
                                isRunningActive = runRunningNow
                            )
                        }
                    }
                    androidx.activity.compose.BackHandler(enabled = activeScreen == AppScreen.PLAYER || isRunningHudActive) {
                        if (isRunningHudActive && isWristGuardEnabled) {
                            android.widget.Toast.makeText(
                                context,
                                "Pulso Travado (Anti-Câmera ativo). Destrave no cadeado para sair.",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            markIntentionalExternalNavigation()
                            activity?.moveTaskToBack(true)
                        }
                    }
                    androidx.activity.compose.BackHandler(enabled = com.example.BuildConfig.DEBUG && activeScreen == AppScreen.BETA_LAB) {
                        viewModel.activeScreen.value = AppScreen.PROFILE
                    }
                    androidx.activity.compose.BackHandler(enabled = activeScreen == AppScreen.RUNNING_HISTORY) {
                        viewModel.popBackScreen(fallback = AppScreen.HOME)
                    }
                    androidx.activity.compose.BackHandler(enabled = activeScreen == AppScreen.SHARE_CARD) {
                        viewModel.popBackScreen(fallback = AppScreen.HOME)
                    }
                    androidx.activity.compose.BackHandler(enabled = activeScreen == AppScreen.GYM) {
                        viewModel.popBackScreen(fallback = AppScreen.HOME)
                    }

                // Diálogo: 1 sessão ativa encontrada
                if (showActiveSessionPrompt && !isConflict) {
                    val singleSession = activeSessions.firstOrNull()
                    AlertDialog(
                        onDismissRequest = { /* Bloquear fechamento sem ação explícita */ },
                        title = {
                            Text(
                                text = "Treino em Andamento",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                        },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "Você possui um treino em andamento.",
                                    color = Color.LightGray,
                                    fontSize = 14.sp
                                )
                                if (singleSession != null) {
                                    Text(
                                        text = "Rotina: ${singleSession.workoutName}",
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 15.sp
                                    )
                                    val formattedTime = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                                        .format(Date(singleSession.startTime))
                                    Text(
                                        text = "Iniciado em: $formattedTime",
                                        color = Color.Gray,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    viewModel.restoreActiveSession()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00B0FF))
                            ) {
                                Text("Retomar", color = Color(0xFF06101E), fontWeight = FontWeight.Bold)
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = {
                                    if (singleSession != null) {
                                        viewModel.requestDiscardSession(singleSession)
                                    } else {
                                        viewModel.requestDiscardCurrentActiveSession()
                                    }
                                }
                            ) {
                                Text("Descartar", color = Color(0xFFFF5252), fontWeight = FontWeight.Bold)
                            }
                        },
                        containerColor = Color(0xFF1E1E24)
                    )
                }

                // Diálogo: Estado inconsistente com múltiplas sessões ativas (resolução individual permitida)
                if (showActiveSessionPrompt && isConflict) {
                    AlertDialog(
                        onDismissRequest = { /* Bloquear fechamento sem resolução */ },
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFFFB300))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Múltiplos Treinos Ativos",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )
                            }
                        },
                        text = {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "Foram detectadas múltiplas sessões ativas (${activeSessions.size}). Selecione uma ação para cada treino individualmente antes de iniciar um novo:",
                                    color = Color.LightGray,
                                    fontSize = 13.sp
                                )
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 260.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(activeSessions) { sessionItem ->
                                        Card(
                                            colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A32)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(10.dp)) {
                                                Text(
                                                    text = sessionItem.workoutName,
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 14.sp
                                                )
                                                val formattedTime = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
                                                    .format(Date(sessionItem.startTime))
                                                Text(
                                                    text = "Iniciado em: $formattedTime",
                                                    color = Color.Gray,
                                                    fontSize = 11.sp
                                                )
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.End,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    TextButton(
                                                        onClick = { viewModel.requestDiscardSession(sessionItem) }
                                                    ) {
                                                        Text("Descartar", color = Color(0xFFFF5252), fontSize = 12.sp)
                                                    }
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Button(
                                                        onClick = { viewModel.restoreActiveSession(sessionItem.id) },
                                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00B0FF)),
                                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                                    ) {
                                                        Text("Retomar", color = Color(0xFF06101E), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        },
                        confirmButton = {},
                        containerColor = Color(0xFF1E1E24)
                    )
                }

                // Diálogo de confirmação de descarte de sessão (obrigatório: registro mantido por 30 dias)
                if (sessionToDiscardConfirm != null) {
                    AlertDialog(
                        onDismissRequest = { viewModel.cancelDiscardConfirmation() },
                        title = {
                            Text(
                                text = "Descartar treino em andamento?",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp
                            )
                        },
                        text = {
                            Text(
                                text = "Descartar este treino em andamento? As informações não concluídas serão mantidas apenas como registro descartado por 30 dias.",
                                color = Color.LightGray,
                                fontSize = 14.sp
                            )
                        },
                        confirmButton = {
                            Button(
                                onClick = { viewModel.executeDiscardSession() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                            ) {
                                Text("Descartar", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = { viewModel.cancelDiscardConfirmation() }
                            ) {
                                Text("Cancelar", color = Color.White)
                            }
                        },
                        containerColor = Color(0xFF221E1E)
                    )
                }

                // Diálogo: Tentativa de encerramento sem nenhuma série realizada
                if (showNoSetsDialog) {
                    AlertDialog(
                        onDismissRequest = { viewModel.dismissNoSetsDialog() },
                        title = {
                            Text(
                                text = "Concluir ou Encerrar Treino?",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp
                            )
                        },
                        text = {
                            Text(
                                text = "Nenhuma série individual foi marcada. Deseja concluir e registrar todas as séries no Diário ou encerrar sem salvar?",
                                color = Color.LightGray,
                                fontSize = 14.sp
                            )
                        },
                        confirmButton = {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(
                                    onClick = { viewModel.endWorkoutAsNotPerformed() }
                                ) {
                                    Text("Encerrar sem salvar", color = Color.LightGray, fontWeight = FontWeight.Bold)
                                }
                                Button(
                                    onClick = {
                                        viewModel.dismissNoSetsDialog()
                                        viewModel.finishActiveWorkout(isEarly = false, autoCompleteIfEmpty = true)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00B0FF))
                                ) {
                                    Text("Concluir e Salvar", color = Color(0xFF06101E), fontWeight = FontWeight.Bold)
                                }
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = { viewModel.dismissNoSetsDialog() }
                            ) {
                                Text("Continuar Treinando", color = Color(0xFF00B0FF), fontWeight = FontWeight.Bold)
                            }
                        },
                        containerColor = Color(0xFF1E1E24)
                    )
                }

                if (workoutError != null) {
                    AlertDialog(
                        onDismissRequest = { viewModel.clearWorkoutError() },
                        title = {
                            Text(
                                text = "Erro ao Iniciar Treino",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                        },
                        text = {
                            Text(
                                text = workoutError!!,
                                color = Color.LightGray,
                                fontSize = 14.sp
                            )
                        },
                        confirmButton = {
                            Button(
                                onClick = { viewModel.clearWorkoutError() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00B0FF))
                            ) {
                                Text("OK", color = Color(0xFF06101E), fontWeight = FontWeight.Bold)
                            }
                        },
                        containerColor = Color(0xFF1E1E24)
                    )
                }

                if (levelSuggestion != null) {
                    val suggestion = levelSuggestion!!
                    AlertDialog(
                        onDismissRequest = { viewModel.declineLevelUpSuggestion() },
                        title = {
                            Text(
                                text = "Que tal subir para o nível ${suggestion.newLevel.uppercase()}?",
                                color = Color.White,
                                fontWeight = FontWeight.Black,
                                fontSize = 18.sp
                            )
                        },
                        text = {
                            Text(
                                text = "${suggestion.reason}\n\nSubir de nível atualizará suas sugestões de treino no MAX PERFORMANCE.",
                                color = Color.LightGray,
                                fontSize = 14.sp
                            )
                        },
                        confirmButton = {
                            Button(
                                onClick = { viewModel.acceptLevelUpSuggestion(suggestion.newLevel) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF00E676) // Bright green
                                )
                            ) {
                                Text(
                                    text = "SUBIR NÍVEL",
                                    color = Color.Black,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = { viewModel.declineLevelUpSuggestion() }
                            ) {
                                Text(
                                    text = "DEPOIS",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        },
                        containerColor = Color(0xFF1E1E24)
                    )
                }

                val workoutWarning by viewModel.workoutWarningState.collectAsState()

                if (workoutWarning != null) {
                    val warning = workoutWarning!!
                    val daysText = when (warning.daysAgo) {
                        0 -> "hoje (${warning.lastTrainedDateStr})"
                        1 -> "ontem (${warning.lastTrainedDateStr})"
                        else -> "há ${warning.daysAgo} dias (${warning.lastTrainedDateStr})"
                    }
                    val targetClean = warning.targetWorkoutName.ifBlank {
                        com.example.services.recommendation.engine001.TrainingEngine001.extractCleanWorkoutTitle(
                            warning.workout.name,
                            warning.workout.id
                        )
                    }
                    val messageText = warning.customMessage ?: if (warning.isFullBodyWarning) {
                        "Você realizou o treino ${warning.lastWorkoutName.ifBlank { warning.muscleName }} $daysText. " +
                            "Para recuperação ideal pós-Full Body (48h), recomendamos realizar sua Corrida ou descansar hoje."
                    } else {
                        "Você treinou ${warning.muscleName} $daysText. Considere intercalar com Corrida ou descansar hoje."
                    }

                    AlertDialog(
                        onDismissRequest = { viewModel.workoutWarningState.value = null },
                        title = {
                            Text(
                                text = "Alerta de Recuperação Muscular",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                        },
                        text = {
                            Text(
                                text = messageText,
                                color = Color.LightGray,
                                fontSize = 14.sp,
                                lineHeight = 20.sp
                            )
                        },
                        dismissButton = {
                            OutlinedButton(
                                onClick = {
                                    viewModel.workoutWarningState.value = null
                                    viewModel.setAppMode(com.example.data.model.AppMode.RUNNING)
                                },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF00E5FF)),
                                border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.45f))
                            ) {
                                Text("Ir para Corrida / Descansar", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    viewModel.startWorkoutSession(warning.workout, forceStart = true)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00B0FF))
                            ) {
                                Text(
                                    text = "Treinar $targetClean mesmo assim",
                                    color = Color.Black,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 12.sp
                                )
                            }
                        },
                        containerColor = Color(0xFF161922)
                    )
                }

                val currentUser by viewModel.currentUser.collectAsState()
                val windowSizeInfo = WindowSizeManager.rememberWindowSize()
                CompositionLocalProvider(LocalWindowSizeInfo provides windowSizeInfo) {
                    val user = currentUser
                    if (user != null && user.nivelAcesso.uppercase() == "SUPERUSER") {
                        AdminPanelScreen(viewModel = viewModel)
                    } else {
                        AdaptiveNavigation(viewModel = viewModel) { innerPadding ->
                            Scaffold(
                                modifier = Modifier.fillMaxSize(),
                                containerColor = Color.Transparent,
                                topBar = {}
                            ) { scaffoldPadding ->
                                val finalModifier = Modifier.padding(
                                    top = scaffoldPadding.calculateTopPadding(),
                                    bottom = if (windowSizeInfo.widthSizeClass == WindowSize.COMPACT) {
                                        innerPadding.calculateBottomPadding()
                                    } else {
                                        scaffoldPadding.calculateBottomPadding()
                                    },
                                    start = scaffoldPadding.calculateLeftPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
                                    end = scaffoldPadding.calculateRightPadding(androidx.compose.ui.unit.LayoutDirection.Ltr)
                                )
                                ScaffoldContent(
                                    screen = activeScreen,
                                    viewModel = viewModel,
                                    modifier = finalModifier
                                )
                            }
                        }
                    }

                    // Session Preparation Overlay for instant UX feedback
                    com.example.ui.components.SessionPrepOverlay(prepStatus = sessionPrepStatus)
                        }
                                }
                            }
                    }
                }
            }
        }
    }
}

} // Close MainActivity class

@Composable
fun ScaffoldContent(screen: AppScreen, viewModel: MainViewModel, modifier: Modifier = Modifier) {
    val appMode by viewModel.appMode.collectAsState()
    // Wrap active screen with padding modifier so it respects bar heights and uses the premium MaxBackground
    com.example.ui.components.MaxBackground(modifier = modifier.customViewForScrollCapture()) {
        AnimatedContent(
            targetState = screen,
            transitionSpec = {
                (fadeIn(animationSpec = tween(300, easing = FastOutSlowInEasing)) + slideInVertically(
                    initialOffsetY = { it / 20 },
                    animationSpec = tween(300, easing = FastOutSlowInEasing)
                )).togetherWith(
                    fadeOut(animationSpec = tween(200, easing = FastOutSlowInEasing))
                )
            },
            label = "AppScreenTransition"
        ) { targetScreen ->
            when (targetScreen) {
                AppScreen.HOME -> {
                    if (appMode == AppMode.BODYBUILDING) {
                        HomeScreen(viewModel = viewModel)
                    } else {
                        RunningModuleScreen(viewModel = viewModel)
                    }
                }
                AppScreen.RUNNING_HISTORY -> RunningHistoryScreen(viewModel = viewModel)
                AppScreen.WORKOUTS -> WorkoutScreen(viewModel = viewModel)
                AppScreen.PLAYER -> PlayerScreen(viewModel = viewModel)
                AppScreen.LIBRARY -> LibraryScreen(viewModel = viewModel)
                AppScreen.PROFILE -> ProfileScreen(viewModel = viewModel)
                AppScreen.CARDIO -> CardioScreen(viewModel = viewModel)
                AppScreen.SOCIAL, AppScreen.CLASSROOM -> HomeScreen(viewModel = viewModel)
                AppScreen.CREATOR -> WorkoutScreen(viewModel = viewModel) // handoff internally handled
                AppScreen.CONCLUIDO -> CompletedWorkoutScreen(viewModel = viewModel)
                AppScreen.DIARIO -> RunningHistoryScreen(viewModel = viewModel)
                AppScreen.BETA_LAB -> if (com.example.BuildConfig.DEBUG) {
                    com.example.ui.screens.BetaLabScreen(viewModel = viewModel)
                } else {
                    HomeScreen(viewModel = viewModel)
                }
                AppScreen.SHARE_CARD -> com.example.ui.screens.ShareCardEditorScreen(viewModel = viewModel)
                AppScreen.RUNNING_DETAIL -> com.example.ui.screens.RunningSessionDetailScreen(viewModel = viewModel)
                AppScreen.GYM -> com.example.ui.components.RunningPlanningScreen(viewModel = viewModel)
                AppScreen.APP_SETTINGS -> com.example.ui.screens.AppSettingsScreen(viewModel = viewModel)
            }
        }
    }
}

