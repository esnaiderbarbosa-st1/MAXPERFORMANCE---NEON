package com.example.services

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import com.example.MainActivity
import java.util.Locale

object WorkoutServiceState {
    val workoutDurationSeconds = kotlinx.coroutines.flow.MutableStateFlow(0L)
    val isRestTimerActive = kotlinx.coroutines.flow.MutableStateFlow(false)
    val restTimeRemaining = kotlinx.coroutines.flow.MutableStateFlow(0)
    val restTimeTotal = kotlinx.coroutines.flow.MutableStateFlow(60)
    val isTempoTimerActive = kotlinx.coroutines.flow.MutableStateFlow(false)
    val tempoTimeRemaining = kotlinx.coroutines.flow.MutableStateFlow(0)
    val tempoTimeTotal = kotlinx.coroutines.flow.MutableStateFlow(0)

    @Volatile
    var isWorkoutClockRunning: Boolean = false

    @Volatile
    var workoutClockBaseDurationSeconds: Long = 0L

    @Volatile
    var workoutClockAnchorElapsedRealtime: Long = 0L

    @Volatile
    var workoutStartElapsedRealtime: Long = 0L

    @Volatile
    var workoutStartWallClockMillis: Long = 0L

    @Volatile
    var restEndElapsedRealtime: Long = 0L

    @Volatile
    var restEndWallClockMillis: Long = 0L

    @Volatile
    var tempoEndElapsedRealtime: Long = 0L

    @Volatile
    var lastCountdownBeepSec: Int = -1

    fun startOrSyncWorkoutClock(
        initialElapsedSec: Long = 0L,
        nowElapsed: Long = SystemClock.elapsedRealtime(),
        nowWall: Long = System.currentTimeMillis()
    ) {
        val safeInitial = initialElapsedSec.coerceAtLeast(0L)
        workoutClockBaseDurationSeconds = safeInitial
        workoutClockAnchorElapsedRealtime = nowElapsed
        isWorkoutClockRunning = true
        workoutStartElapsedRealtime = nowElapsed - (safeInitial * 1000L)
        workoutStartWallClockMillis = nowWall - (safeInitial * 1000L)
        workoutDurationSeconds.value = safeInitial
    }

    fun clearWorkoutClock() {
        isWorkoutClockRunning = false
        workoutClockBaseDurationSeconds = 0L
        workoutClockAnchorElapsedRealtime = 0L
        workoutStartElapsedRealtime = 0L
        workoutStartWallClockMillis = 0L
    }

    fun startRestClock(durationSec: Int) {
        val safeSec = durationSec.coerceAtLeast(0)
        val nowElapsed = SystemClock.elapsedRealtime()
        val nowWall = System.currentTimeMillis()
        restTimeTotal.value = safeSec
        if (safeSec > 0) {
            restEndElapsedRealtime = nowElapsed + (safeSec * 1000L)
            restEndWallClockMillis = nowWall + (safeSec * 1000L)
            restTimeRemaining.value = safeSec
            isRestTimerActive.value = true
            lastCountdownBeepSec = -1
        } else {
            restEndElapsedRealtime = 0L
            restEndWallClockMillis = 0L
            restTimeRemaining.value = 0
            isRestTimerActive.value = false
            lastCountdownBeepSec = -1
        }
    }

    fun modifyRestClock(deltaSeconds: Int) {
        val nowElapsed = SystemClock.elapsedRealtime()
        val nowWall = System.currentTimeMillis()
        val currentRemainingMs = if (isRestTimerActive.value && restEndElapsedRealtime > nowElapsed) {
            restEndElapsedRealtime - nowElapsed
        } else {
            restTimeRemaining.value.coerceAtLeast(0) * 1000L
        }
        val newRemainingMs = (currentRemainingMs + deltaSeconds * 1000L).coerceAtLeast(0L)
        val newRemainingSec = kotlin.math.max(0, ((newRemainingMs + 999L) / 1000L).toInt())
        if (newRemainingSec > 0) {
            restEndElapsedRealtime = nowElapsed + newRemainingMs
            restEndWallClockMillis = nowWall + newRemainingMs
            restTimeRemaining.value = newRemainingSec
            if (newRemainingSec > restTimeTotal.value) {
                restTimeTotal.value = newRemainingSec
            }
            isRestTimerActive.value = true
            if (newRemainingSec > 3) {
                lastCountdownBeepSec = -1
            }
        } else {
            restEndElapsedRealtime = 0L
            restEndWallClockMillis = 0L
            restTimeRemaining.value = 0
            isRestTimerActive.value = false
            lastCountdownBeepSec = -1
        }
    }

    fun startTempoClock(durationSec: Int) {
        val safeSec = durationSec.coerceAtLeast(0)
        tempoTimeTotal.value = safeSec
        if (safeSec > 0) {
            tempoEndElapsedRealtime = SystemClock.elapsedRealtime() + (safeSec * 1000L)
            tempoTimeRemaining.value = safeSec
            isTempoTimerActive.value = true
        } else {
            tempoEndElapsedRealtime = 0L
            tempoTimeRemaining.value = 0
            isTempoTimerActive.value = false
        }
    }

    fun computeRemainingRestSeconds(nowElapsed: Long = SystemClock.elapsedRealtime()): Int {
        if (!isRestTimerActive.value || restEndElapsedRealtime <= 0L) return 0
        return kotlin.math.max(0, ((restEndElapsedRealtime - nowElapsed + 999L) / 1000L).toInt())
    }

    fun computeWorkoutDurationSeconds(nowElapsed: Long = SystemClock.elapsedRealtime()): Long {
        if (!isWorkoutClockRunning) return workoutDurationSeconds.value
        val deltaSinceAnchorSec = if (nowElapsed >= workoutClockAnchorElapsedRealtime) {
            (nowElapsed - workoutClockAnchorElapsedRealtime) / 1000L
        } else {
            0L
        }
        return kotlin.math.max(0L, workoutClockBaseDurationSeconds + deltaSinceAnchorSec)
    }

    /**
     * Recalcula instantaneamente os contadores de descanso e de treino a partir do relógio físico
     * (`SystemClock.elapsedRealtime()`). Usado tanto pelo ForegroundService quanto no `ON_RESUME` ao desbloquear a tela.
     * Retorna `true` se o descanso acabou exatamente nesta transição para 0.
     */
    fun recalculateNowFromPhysicalClock(nowElapsed: Long = SystemClock.elapsedRealtime()): Boolean {
        if (isWorkoutClockRunning) {
            workoutDurationSeconds.value = computeWorkoutDurationSeconds(nowElapsed)
        }
        var restJustCompleted = false
        if (isRestTimerActive.value) {
            val rem = computeRemainingRestSeconds(nowElapsed)
            restTimeRemaining.value = rem
            if (rem <= 0) {
                isRestTimerActive.value = false
                restEndElapsedRealtime = 0L
                restEndWallClockMillis = 0L
                lastCountdownBeepSec = -1
                restJustCompleted = true
            }
        }
        if (isTempoTimerActive.value) {
            val remTempo = if (tempoEndElapsedRealtime > 0L) {
                kotlin.math.max(0, ((tempoEndElapsedRealtime - nowElapsed + 999L) / 1000L).toInt())
            } else 0
            tempoTimeRemaining.value = remTempo
            if (remTempo <= 0) {
                isTempoTimerActive.value = false
                tempoEndElapsedRealtime = 0L
            }
        }
        return restJustCompleted
    }

    fun resetTimers() {
        isRestTimerActive.value = false
        restTimeRemaining.value = 0
        restEndElapsedRealtime = 0L
        restEndWallClockMillis = 0L
        lastCountdownBeepSec = -1
        isTempoTimerActive.value = false
        tempoTimeRemaining.value = 0
        tempoEndElapsedRealtime = 0L
    }
}

class WorkoutForegroundService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var handler: Handler? = null
    private var countdownRunnable: Runnable? = null
    
    private var workoutName = "Treino Ativo"
    
    private var isRestActive: Boolean
        get() = WorkoutServiceState.isRestTimerActive.value
        set(value) { WorkoutServiceState.isRestTimerActive.value = value }
        
    private var restTimeRemaining: Int
        get() = WorkoutServiceState.restTimeRemaining.value
        set(value) { WorkoutServiceState.restTimeRemaining.value = value }
        
    private var restTimeTotal: Int
        get() = WorkoutServiceState.restTimeTotal.value
        set(value) { WorkoutServiceState.restTimeTotal.value = value }
        
    private var workoutDurationSeconds: Long
        get() = WorkoutServiceState.workoutDurationSeconds.value
        set(value) { WorkoutServiceState.workoutDurationSeconds.value = value }

    private var isTempoActive: Boolean
        get() = WorkoutServiceState.isTempoTimerActive.value
        set(value) { WorkoutServiceState.isTempoTimerActive.value = value }

    private var tempoTimeRemaining: Int
        get() = WorkoutServiceState.tempoTimeRemaining.value
        set(value) { WorkoutServiceState.tempoTimeRemaining.value = value }
    
    // Configurable choices (defaults)
    private var soundOption = "default"
    private var soundEnabled = true

    companion object {
        @Volatile
        var isServiceRunning = false
        @Volatile
        var isWorkoutActive = false

        private const val TAG = "WorkoutForeground"
        private const val CHANNEL_ID = "workout_channel_id"
        private const val REST_CHANNEL_ID = "rest_channel_v3"
        const val NOTIFICATION_ID = 9912
        const val REST_ALERT_NOTIFICATION_ID = 9913
        private const val ALARM_TRIGGER_ACTION = "com.example.ACTION_ALARM_TRIGGER"
        
        // Actions
        const val ACTION_START_WORKOUT = "com.example.START_WORKOUT"
        const val ACTION_STOP_WORKOUT = "com.example.STOP_WORKOUT"
        const val ACTION_START_REST = "com.example.START_REST"
        const val ACTION_MODIFY_REST = "com.example.MODIFY_REST"
        const val ACTION_SKIP_REST = "com.example.SKIP_REST"
        const val ACTION_PAUSE_REST_30S = "com.example.PAUSE_REST_30S"
        const val ACTION_UPDATE_SOUND_PREFS = "com.example.UPDATE_SOUND_PREFS"
        
        // Extras
        const val EXTRA_WORKOUT_NAME = "workout_name"
        const val EXTRA_REST_DURATION = "rest_duration"
        const val EXTRA_SOUND_OPTION = "sound_option"
        const val EXTRA_SOUND_ENABLED = "sound_enabled"

        /**
         * Encerra imediatamente o WorkoutForegroundService e remove qualquer notificação residual (9912/9913).
         */
        fun stopServiceAndClearNotifications(context: Context) {
            isWorkoutActive = false
            isServiceRunning = false
            WorkoutServiceState.clearWorkoutClock()
            WorkoutServiceState.resetTimers()
            runCatching {
                context.stopService(Intent(context, WorkoutForegroundService::class.java))
            }
            runCatching {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                nm?.cancel(NOTIFICATION_ID)
                nm?.cancel(REST_ALERT_NOTIFICATION_ID)
            }
        }
    }

    private val alarmReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ALARM_TRIGGER_ACTION) {
                Log.d(TAG, "Alarm triggered! Playing sound.")
                triggerRestCompleteAlarm()
            }
        }
    }

    private fun promoteToForegroundImmediately() {
        createNotificationChannels()
        val notification = buildWorkoutNotification()
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        }.onFailure { e ->
            runCatching { startForeground(NOTIFICATION_ID, notification) }
            com.example.utils.CrashLog.record(e, "WorkoutForegroundService.promoteToForegroundImmediately", this)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        promoteToForegroundImmediately()
        isServiceRunning = true
        Log.d(TAG, "Service created")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(alarmReceiver, IntentFilter(ALARM_TRIGGER_ACTION), Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(alarmReceiver, IntentFilter(ALARM_TRIGGER_ACTION))
        }
        
        handler = Handler(Looper.getMainLooper())
        startLocalTicker()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Blindagem Android 14/15 (SDK 34/35): SEMPRE chama startForeground() no topo de onStartCommand
        // para QUALQUER intent/action antes de qualquer return ou stopSelf()
        promoteToForegroundImmediately()

        if (intent == null || intent.action == ACTION_STOP_WORKOUT) {
            Log.d(TAG, "Workout stopped or null intent - cancelling timers, removing foreground notification and stopping service")
            stopSelfAndClearNotifications()
            return START_NOT_STICKY
        }

        try {
            when (intent.action) {
                ACTION_START_WORKOUT -> {
                    isServiceRunning = true
                    isWorkoutActive = true
                    workoutName = intent.getStringExtra(EXTRA_WORKOUT_NAME) ?: "Treino Ativo"
                    if (!WorkoutServiceState.isWorkoutClockRunning) {
                        WorkoutServiceState.startOrSyncWorkoutClock(workoutDurationSeconds)
                    }
                    soundOption = intent.getStringExtra(EXTRA_SOUND_OPTION) ?: "default"
                    soundEnabled = intent.getBooleanExtra(EXTRA_SOUND_ENABLED, true)
                    
                    acquireWakeLock(4 * 60 * 60 * 1000L) // 4 hours max PARTIAL_WAKE_LOCK
                    promoteToForegroundImmediately()
                    Log.d(TAG, "Workout started: $workoutName")
                }
                ACTION_START_REST -> {
                    isServiceRunning = true
                    isWorkoutActive = true
                    if (!WorkoutServiceState.isWorkoutClockRunning) {
                        WorkoutServiceState.startOrSyncWorkoutClock(workoutDurationSeconds)
                    }
                    val duration = intent.getIntExtra(EXTRA_REST_DURATION, 60)
                    // Se o ViewModel ainda não definiu restEndElapsedRealtime ou a diferença for grande, sincroniza:
                    val currentRem = WorkoutServiceState.computeRemainingRestSeconds()
                    if (!isRestActive || kotlin.math.abs(currentRem - duration) > 2) {
                        WorkoutServiceState.startRestClock(duration)
                    }
                    Log.d(TAG, "Rest started: $duration seconds (endElapsed=${WorkoutServiceState.restEndElapsedRealtime})")
                    
                    acquireWakeLock(4 * 60 * 60 * 1000L) // Mantém PARTIAL_WAKE_LOCK ativo (4h timeout seguro)
                    promoteToForegroundImmediately()
                    scheduleAlarm(restTimeRemaining.coerceAtLeast(1))
                    updateNotification()
                }
                ACTION_MODIFY_REST -> {
                    if (!isWorkoutActive) {
                        stopSelfAndClearNotifications()
                        return START_NOT_STICKY
                    }
                    val rem = WorkoutServiceState.computeRemainingRestSeconds()
                    restTimeRemaining = rem
                    if (isRestActive && rem > 0) {
                        acquireWakeLock(4 * 60 * 60 * 1000L)
                        scheduleAlarm(rem)
                    } else {
                        cancelAlarm()
                    }
                    updateNotification()
                }
                ACTION_SKIP_REST -> {
                    cancelAlarm()
                    stopAlarmPlayback()
                    WorkoutServiceState.resetTimers()
                    if (isWorkoutActive) {
                        updateNotification()
                    } else {
                        stopSelfAndClearNotifications()
                    }
                }
                ACTION_PAUSE_REST_30S -> {
                    if (!isWorkoutActive) {
                        stopSelfAndClearNotifications()
                        return START_NOT_STICKY
                    }
                    stopAlarmPlayback()
                    WorkoutServiceState.modifyRestClock(30)
                    acquireWakeLock(4 * 60 * 60 * 1000L)
                    scheduleAlarm(restTimeRemaining.coerceAtLeast(1))
                    updateNotification()
                    Log.d(TAG, "Alarm paused. Added 30s of rest.")
                }
                ACTION_UPDATE_SOUND_PREFS -> {
                    soundOption = intent.getStringExtra(EXTRA_SOUND_OPTION) ?: soundOption
                    soundEnabled = intent.getBooleanExtra(EXTRA_SOUND_ENABLED, soundEnabled)
                }
            }
        } catch (e: Exception) {
            com.example.utils.CrashLog.record(e, "WorkoutForegroundService.onStartCommand", this)
            Log.e(TAG, "Error handling action in onStartCommand: ${intent.action}", e)
        }
        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.d(TAG, "onTaskRemoved - killing timers, foreground notification and service")
        stopSelfAndClearNotifications()
    }

    private fun stopSelfAndClearNotifications() {
        isServiceRunning = false
        isWorkoutActive = false
        WorkoutServiceState.clearWorkoutClock()
        WorkoutServiceState.resetTimers()
        try {
            countdownRunnable?.let { handler?.removeCallbacks(it) }
            handler?.removeCallbacksAndMessages(null)
        } catch (_: Exception) {}
        cancelAlarm()
        stopAlarmPlayback()
        releaseWakeLock()
        try {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        } catch (e: Exception) {
            com.example.utils.CrashLog.record(e, "WorkoutForegroundService.stopForeground", this)
        }
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.cancel(NOTIFICATION_ID)
            nm?.cancel(REST_ALERT_NOTIFICATION_ID)
        } catch (_: Exception) {}
        stopSelf()
    }

    override fun onDestroy() {
        isServiceRunning = false
        isWorkoutActive = false
        WorkoutServiceState.clearWorkoutClock()
        WorkoutServiceState.resetTimers()
        Log.d(TAG, "Service being destroyed - cleaning up resources safely")
        
        try {
            countdownRunnable?.let { handler?.removeCallbacks(it) }
            handler?.removeCallbacksAndMessages(null)
        } catch (e: Exception) {
            com.example.utils.CrashLog.record(e, "WorkoutForegroundService.removeCallbacksAndMessages", this)
        }

        try {
            unregisterReceiver(alarmReceiver)
        } catch (_: Exception) {
            // Already unregistered
        }
        
        cancelAlarm()
        stopAlarmPlayback()
        try {
            com.example.utils.GymAudioEngine.stopAllAndRelease(this)
        } catch (e: Exception) {
            com.example.utils.CrashLog.record(e, "WorkoutForegroundService.stopAllSounds", this)
        }
        releaseWakeLock()

        try {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        } catch (_: Exception) {}
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.cancel(NOTIFICATION_ID)
            nm?.cancel(REST_ALERT_NOTIFICATION_ID)
        } catch (_: Exception) {}
        
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // --- LOCAL TICKER ENGINE BASEADO EM RELÓGIO FÍSICO (SystemClock.elapsedRealtime()) ---
    // Imune ao bloqueio de tela / CPU suspension, mantendo PARTIAL_WAKE_LOCK e tocando bipes 3, 2, 1 e alerta 00:00
    private fun startLocalTicker() {
        var lastNotifiedWorkoutSec = -1L
        var lastNotifiedRestSec = -1
        countdownRunnable = object : Runnable {
            override fun run() {
                if (!isServiceRunning || !isWorkoutActive) {
                    return
                }
                val nowElapsed = SystemClock.elapsedRealtime()
                if (wakeLock?.isHeld != true) {
                    acquireWakeLock(4 * 60 * 60 * 1000L)
                }

                // 1. Tempo total do treino por diferença de timestamp real
                if (!WorkoutServiceState.isWorkoutClockRunning) {
                    WorkoutServiceState.startOrSyncWorkoutClock(workoutDurationSeconds)
                }
                workoutDurationSeconds = WorkoutServiceState.computeWorkoutDurationSeconds(nowElapsed)

                // 2. Cronômetro de descanso por diferença de timestamp real:
                // max(0, ((restEndElapsedRealtime - SystemClock.elapsedRealtime() + 999) / 1000).toInt())
                if (isRestActive) {
                    val remaining = WorkoutServiceState.computeRemainingRestSeconds(nowElapsed)
                    restTimeRemaining = remaining

                    if (remaining in 1..3 && WorkoutServiceState.lastCountdownBeepSec != remaining) {
                        WorkoutServiceState.lastCountdownBeepSec = remaining
                        com.example.utils.GymAudioEngine.playRestCountdownBeep(
                            context = this@WorkoutForegroundService,
                            secondRemaining = remaining,
                            soundEnabledOverride = soundEnabled
                        )
                    }

                    if (remaining <= 0) {
                        Log.d(TAG, "Rest timer completed via physical clock (00:00). Triggering alarm.")
                        triggerRestCompleteAlarm()
                    } else if (remaining != lastNotifiedRestSec) {
                        lastNotifiedRestSec = remaining
                        updateNotification()
                    }
                } else if (workoutDurationSeconds != lastNotifiedWorkoutSec) {
                    lastNotifiedWorkoutSec = workoutDurationSeconds
                    updateNotification()
                }

                // 3. Cronômetro de cadência/tempo de execução por diferença de timestamp real
                if (isTempoActive) {
                    val remTempo = if (WorkoutServiceState.tempoEndElapsedRealtime > 0L) {
                        kotlin.math.max(0, ((WorkoutServiceState.tempoEndElapsedRealtime - nowElapsed + 999L) / 1000L).toInt())
                    } else 0
                    tempoTimeRemaining = remTempo
                    if (remTempo <= 0) {
                        Log.d(TAG, "Tempo timer completed in physical ticker (00:00). Triggering alarm.")
                        isTempoActive = false
                        WorkoutServiceState.tempoEndElapsedRealtime = 0L
                        com.example.utils.GymAudioEngine.playRestFinishedAlert(this@WorkoutForegroundService)
                        updateNotification()
                    }
                }
                
                // Automatic stop if inactive (safety mechanism - 4 hours max workout)
                if (workoutDurationSeconds >= 14400) {
                    Log.w(TAG, "Workout reached 4 hour limit. Stopping foreground service.")
                    stopSelfAndClearNotifications()
                    return
                }
                
                if (isServiceRunning && isWorkoutActive) {
                    handler?.postDelayed(this, 250L)
                }
            }
        }
        handler?.post(countdownRunnable!!)
    }

    // --- ALARM SCHEDULING (Exact timing, Doze mode immune) ---
    private fun scheduleAlarm(seconds: Int) {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(ALARM_TRIGGER_ACTION).apply {
            setPackage(packageName)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            this, 102, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        
        val triggerTime = System.currentTimeMillis() + (seconds * 1000)
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        pendingIntent
                    )
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        pendingIntent
                    )
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            }
            Log.d(TAG, "Scheduled exact/inexact alarm in $seconds seconds")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule exact alarm, using standard set", e)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        pendingIntent
                    )
                } else {
                    alarmManager.set(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        pendingIntent
                    )
                }
            } catch (ex: Exception) {
                Log.e(TAG, "Failed to schedule fallback alarm", ex)
            }
        }
    }

    private fun cancelAlarm() {
        try {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(ALARM_TRIGGER_ACTION).apply {
                setPackage(packageName)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                this, 102, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            alarmManager.cancel(pendingIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cancel alarm safely", e)
        }
    }

    // --- ALARM PLAYBACK & WAKELOCK (Screen wake and high-priority alerts) ---
    private fun triggerRestCompleteAlarm() {
        Log.d(TAG, "REST_TIMER_FINISHED")
        if (!isRestActive && restTimeRemaining <= 0 && WorkoutServiceState.restEndElapsedRealtime <= 0L) {
            return
        }
        WorkoutServiceState.resetTimers()
        cancelAlarm()

        // START_FOREGROUND_SERVICE
        Log.d(TAG, "START_FOREGROUND_SERVICE: Service is active and handling rest complete alarm.")

        // 1. PLAY SOUND & VIBRATION VIA DEDICATED GYM AUDIO ENGINE (00:00 ONLY, USAGE_MEDIA 100%)
        try {
            com.example.utils.GymAudioEngine.playRestFinishedAlert(context = this)
        } catch (e: Exception) {
            Log.e(TAG, "PLAY_SOUND: Error playing rest finished sound via GymAudioEngine", e)
        }

        // 3. WAKE SCREEN
        Log.d(TAG, "WAKE_SCREEN")
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!pm.isInteractive) {
                @Suppress("DEPRECATION")
                val alarmWakeLock = pm.newWakeLock(
                    PowerManager.FULL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
                    "WorkoutApp:AlarmWakeLock"
                )
                alarmWakeLock.acquire(3000)
                Log.d(TAG, "WAKE_SCREEN: Screen woken up successfully.")
            }
        } catch (e: Throwable) {
            Log.e(TAG, "WAKE_SCREEN: Failed to acquire wake lock safely", e)
        }

        // 4. SHOW NOTIFICATION
        Log.d(TAG, "SHOW_NOTIFICATION")
        try {
            updateNotification()
        } catch (e: Exception) {
            Log.e(TAG, "SHOW_NOTIFICATION: Failed to update notification safely", e)
        }
        
        val inForeground = androidx.lifecycle.ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)
        if (!inForeground) {
            Log.d(TAG, "SHOW_NOTIFICATION: App is in background, showing high priority alarm notification.")
            try {
                showRestCompleteHighPriorityNotification()
                Log.d(TAG, "SHOW_NOTIFICATION: High-priority notification displayed.")
            } catch (e: Exception) {
                Log.e(TAG, "SHOW_NOTIFICATION: Failed to show high priority notification safely", e)
            }
        } else {
            Log.d(TAG, "SHOW_NOTIFICATION: App is in foreground, high priority alarm notification suppressed.")
        }
    }

    private fun stopAlarmPlayback() {
        try {
            com.example.utils.GymAudioEngine.stopAllAndRelease(this)
        } catch (e: Exception) {
            // Already stopped
        }
    }

    // --- WAKELOCK MANAGEMENT ---
    private fun acquireWakeLock(timeoutMs: Long) {
        try {
            if (wakeLock == null) {
                val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WorkoutApp:ForegroundCpuLock")
            }
            if (wakeLock?.isHeld == false) {
                wakeLock?.acquire(timeoutMs)
                Log.d(TAG, "WakeLock acquired/refreshed with timeout: $timeoutMs ms")
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to acquire wake lock safely", e)
        }
    }

    private fun releaseWakeLock() {
        runCatching {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        }.onFailure { e ->
            Log.e(TAG, "Failed to release wake lock safely", e)
        }
        wakeLock = null
    }

    // --- NOTIFICATION BUILDERS ---
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            // Channel 1: Active Workout
            val workoutChannel = NotificationChannel(
                CHANNEL_ID,
                "Monitor de Treino Ativo",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Mantém o cronômetro do treino correndo e calcula o descanso."
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(workoutChannel)
            
            // Channel 2: High Priority Rest Alerts
            val restChannel = NotificationChannel(
                REST_CHANNEL_ID,
                "Alertas de Fim de Descanso",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifica com som quando o tempo de descanso acaba."
                enableVibration(true)
                setBypassDnd(true)
                setSound(null, null) // Prevents OS default notification sound
            }
            notificationManager.createNotificationChannel(restChannel)
        }
    }

    private fun buildWorkoutNotification(): Notification {
        // Tap intent to open MainActivity
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this, 101, openIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Stop workout action
        val stopIntent = Intent(this, WorkoutForegroundService::class.java).apply {
            action = ACTION_STOP_WORKOUT
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 201, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Status text
        val formattedTime = String.format(
            Locale.getDefault(), "%02d:%02d",
            workoutDurationSeconds / 60, workoutDurationSeconds % 60
        )
        
        val contentText = if (isRestActive) {
            val mins = restTimeRemaining / 60
            val secs = restTimeRemaining % 60
            "Tempo de Descanso: " + String.format(Locale.getDefault(), "%d:%02d", mins, secs)
        } else {
            "Treino em Execução • $formattedTime"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(workoutName)
            .setContentText(contentText)
            .setOngoing(true)
            .setContentIntent(openPendingIntent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Encerrar Sessão", stopPendingIntent)
            .setColor(0xFF00B0FF.toInt()) // MAX PERFORMANCE Primary Color (Blue)
            .build()
    }

    private fun showRestCompleteHighPriorityNotification() {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this, 103, openIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Pause/Snooze Action (adds 30s)
        val snoozeIntent = Intent(this, WorkoutForegroundService::class.java).apply {
            action = ACTION_PAUSE_REST_30S
        }
        val snoozePendingIntent = PendingIntent.getService(
            this, 202, snoozeIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, REST_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Descanso Concluído!")
            .setContentText("Hora de voltar ao treino e iniciar a próxima série! 💪")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(openPendingIntent)
            .setAutoCancel(true)
            .addAction(android.R.drawable.ic_media_play, "Voltar ao Treino", openPendingIntent)
            .addAction(android.R.drawable.ic_media_pause, "+30s de Descanso", snoozePendingIntent)
            .setColor(0xFF00B0FF.toInt())
            .build()

        val notificationManager = NotificationManagerCompat.from(this)
        try {
            notificationManager.notify(REST_ALERT_NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            com.example.utils.CrashLog.record(e, "WorkoutForegroundService.highPriorityNotification", this)
            Log.e(TAG, "Notification permission missing for high priority notification", e)
        }
    }

    private fun updateNotification() {
        if (!isServiceRunning || !isWorkoutActive) return
        val notification = buildWorkoutNotification()
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        }.onFailure { e ->
            runCatching { startForeground(NOTIFICATION_ID, notification) }
            runCatching {
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                notificationManager?.notify(NOTIFICATION_ID, notification)
            }.onFailure { ex ->
                com.example.utils.CrashLog.record(ex, "WorkoutForegroundService.updateNotification.notifyFallback", this)
                Log.e(TAG, "Failed to update notification", ex)
            }
        }
    }
}
