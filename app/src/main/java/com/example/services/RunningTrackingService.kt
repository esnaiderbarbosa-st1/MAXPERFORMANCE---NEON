package com.example.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.MainActivity
import com.example.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import com.example.data.database.AppDatabase
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID

class RunningTrackingService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    private var stateCollectJob: Job? = null
    private var lastNotificationText: String? = null
    private var runningWakeLock: PowerManager.WakeLock? = null

    // DAO e GpsPointRecorder para gravação de coordenadas GPS
    private val gpsTrackPointDao by lazy {
        AppDatabase.getDatabase(applicationContext).gpsTrackPointDao()
    }
    private var recorder: GpsPointRecorder? = null
    private var currentSessionId: String = UUID.randomUUID().toString()

    companion object {
        const val CHANNEL_ID = "running_tracking_channel"
        const val NOTIFICATION_ID = 2001
        const val ACTION_START = "ACTION_START_TRACKING"
        const val ACTION_STOP = "ACTION_STOP_TRACKING"
        const val ACTION_STOP_SERVICE = "ACTION_STOP_SERVICE"
        const val EXTRA_SESSION_ID = "EXTRA_SESSION_ID"

        @Volatile
        var isServiceRunning: Boolean = false
            private set

        private val _userRequestedStopFromNotification = MutableStateFlow(false)
        val userRequestedStopFromNotification: StateFlow<Boolean> = _userRequestedStopFromNotification.asStateFlow()

        fun clearUserRequestedStopFlag() {
            _userRequestedStopFromNotification.value = false
        }

        fun getBlockUnintendedWalkMap(): Map<Int, Int> = RunTracker.getBlockUnintendedWalkMap()

        /**
         * Encerra imediatamente o RunningTrackingService, o RunTracker e remove a notificação (2001).
         */
        fun stopServiceAndClearNotification(context: Context) {
            isServiceRunning = false
            runCatching { RunTracker.stop() }
            runCatching {
                context.stopService(Intent(context, RunningTrackingService::class.java))
            }
            runCatching {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                nm?.cancel(NOTIFICATION_ID)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // Se o usuário fechar o aplicativo da lista de apps recentes, encerra o serviço e remove a notificação
        stopTrackingAndSelf()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Blindagem Android 14/15 (SDK 34/35): SEMPRE chama startForeground() imediatamente no topo
        // de onStartCommand para QUALQUER intent/action antes de qualquer return ou stopSelf()
        createNotificationChannel()
        val promotedOk = startForegroundServiceInternal()

        if (intent == null || intent.action == ACTION_STOP || intent.action == ACTION_STOP_SERVICE || !promotedOk) {
            if (intent?.action == ACTION_STOP_SERVICE) {
                _userRequestedStopFromNotification.value = true
            }
            stopTrackingAndSelf()
            return START_NOT_STICKY
        }

        _userRequestedStopFromNotification.value = false
        acquireRunningWakeLock(4 * 60 * 60 * 1000L) // 4 horas PARTIAL_WAKE_LOCK para manter CPU ativa com tela apagada

        val hasLocationPerm = RunTracker.hasLocationPermission(this)

        if (hasLocationPerm) {
            runCatching {
                val incomingSessionId = intent.getStringExtra(EXTRA_SESSION_ID)?.takeIf { it.isNotBlank() } ?: currentSessionId
                val isSameSession = incomingSessionId == currentSessionId &&
                    (recorder != null || RunTracker.isCurrentlyTracking() || RunTracker.state.value.distanceMeters > 0)
                currentSessionId = incomingSessionId

                if (isSameSession) {
                    RunTracker.resumeTrackingIfStopped(applicationContext)
                } else if (!RunTracker.isCurrentlyPaused()) {
                    val shouldReset = !RunTracker.isCurrentlyTracking() && RunTracker.state.value.distanceMeters == 0
                    RunTracker.start(applicationContext, resetMetrics = shouldReset)
                }
                ensureStateCollector()
                if (recorder == null || recorder?.sessionId != currentSessionId) {
                    recorder?.stop()
                    recorder = GpsPointRecorder(gpsTrackPointDao, currentSessionId)
                    recorder?.start(this)
                }
            }.onFailure { e ->
                com.example.utils.CrashLog.record(e, "RunningTrackingService.onStartCommand.RunTracker", this)
            }
        }

        return START_NOT_STICKY
    }

    private fun acquireRunningWakeLock(timeoutMs: Long) {
        try {
            if (runningWakeLock == null) {
                val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
                runningWakeLock = pm.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "WorkoutApp:RunningGpsLock"
                )
            }
            if (runningWakeLock?.isHeld == false) {
                runningWakeLock?.acquire(timeoutMs)
            }
        } catch (e: Throwable) {
            com.example.utils.CrashLog.record(e, "RunningTrackingService.acquireRunningWakeLock", this)
            android.util.Log.e("RunningTrackingService", "Failed to acquire wake lock", e)
        }
    }

    private fun releaseRunningWakeLock() {
        runCatching {
            if (runningWakeLock?.isHeld == true) {
                runningWakeLock?.release()
            }
        }.onFailure { e ->
            com.example.utils.CrashLog.record(e, "RunningTrackingService.releaseRunningWakeLock", this)
            android.util.Log.e("RunningTrackingService", "Failed to release wake lock", e)
        }
        runningWakeLock = null
    }

    private fun startForegroundServiceInternal(): Boolean {
        val hasLocationPerm = RunTracker.hasLocationPermission(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && !hasLocationPerm) {
            isServiceRunning = false
            android.util.Log.w("RunningTrackingService", "Permissão de localização ausente no Android 14+; abortando startForeground(LOCATION).")
            return false
        }

        isServiceRunning = true
        val initialContent = lastNotificationText ?: "0,00 km · Pace --'--\""
        val notification = buildNotification(initialContent)
        lastNotificationText = initialContent

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && hasLocationPerm) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            true
        } catch (e: SecurityException) {
            com.example.utils.CrashLog.record(e, "RunningTrackingService.startForeground.SecurityException", this)
            val fallbackOk = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                runCatching { startForeground(NOTIFICATION_ID, notification) }.isSuccess
            } else {
                false
            }
            if (!fallbackOk) isServiceRunning = false
            fallbackOk
        } catch (e: Throwable) {
            com.example.utils.CrashLog.record(e, "RunningTrackingService.startForeground.Exception", this)
            val fallbackOk = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                runCatching { startForeground(NOTIFICATION_ID, notification) }.isSuccess
            } else {
                false
            }
            if (!fallbackOk) isServiceRunning = false
            fallbackOk
        }
    }

    private fun ensureStateCollector() {
        if (stateCollectJob?.isActive == true) return

        stateCollectJob = serviceScope.launch {
            RunTracker.state.collectLatest { trackState ->
                if (!isServiceRunning) return@collectLatest
                val distKm = trackState.distanceMeters / 1000f
                val paceText = if (trackState.currentPaceSecPerKm != null) {
                    val pMin = trackState.currentPaceSecPerKm / 60
                    val pSec = trackState.currentPaceSecPerKm % 60
                    String.format(Locale.forLanguageTag("pt-BR"), "%d'%02d\"", pMin, pSec)
                } else {
                    "--'--\""
                }

                val content = String.format(Locale.forLanguageTag("pt-BR"), "%.2f km · Pace %s", distKm, paceText)

                // Only update notification when content text actually changes
                if (content != lastNotificationText && isServiceRunning) {
                    lastNotificationText = content
                    runCatching {
                        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                        notificationManager?.notify(NOTIFICATION_ID, buildNotification(content))
                    }
                }
            }
        }
    }

    private fun buildNotification(contentText: String): android.app.Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, RunningTrackingService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Treino de Corrida ao Ar Livre")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_run_notification)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "ENCERRAR",
                stopPendingIntent
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Monitoramento de Corrida (GPS)",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notificação persistente para medição de distância e ritmo durante a corrida ao ar livre"
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun stopTrackingAndSelf() {
        isServiceRunning = false
        stateCollectJob?.cancel()
        stateCollectJob = null
        releaseRunningWakeLock()
        recorder?.stop()
        recorder = null
        RunTracker.stop()
        try {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        } catch (_: Exception) {}
        try {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(NOTIFICATION_ID)
        } catch (e: Exception) {
            android.util.Log.e("RunningTrackingService", "Erro ao cancelar notificação", e)
        }
        stopSelf()
    }

    override fun onDestroy() {
        isServiceRunning = false
        stateCollectJob?.cancel()
        stateCollectJob = null
        serviceScope.cancel()
        releaseRunningWakeLock()
        recorder?.stop()
        recorder = null
        RunTracker.stop()
        try {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        } catch (_: Exception) {}
        try {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(NOTIFICATION_ID)
        } catch (e: Exception) {
            android.util.Log.e("RunningTrackingService", "Erro ao cancelar notificação", e)
        }
        super.onDestroy()
    }
}
