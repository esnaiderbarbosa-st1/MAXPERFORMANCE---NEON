package com.example.utils

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.SystemClock

object MaxSoundManager {

    private const val PREFS_NAME = "max_prefs"
    private const val REST_DEBOUNCE_MS = 1400L
    private const val COUNTDOWN_TICK_DEBOUNCE_MS = 250L
    private const val TEN_SECOND_TICK_DEBOUNCE_MS = 850L

    @Volatile
    private var lastRestFinishedRealtimeMs: Long = 0L

    @Volatile
    private var lastCountdownTickRealtimeMs: Long = 0L

    @Volatile
    private var lastTenSecondTickRealtimeMs: Long = 0L

    fun isHeadsetOrBluetoothConnected(context: Context): Boolean {
        return try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { device ->
                    device.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                    device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                    device.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                    device.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                    device.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                    device.type == AudioDeviceInfo.TYPE_USB_HEADSET
                }
            } else {
                @Suppress("DEPRECATION")
                audioManager.isBluetoothA2dpOn || audioManager.isWiredHeadsetOn
            }
        } catch (e: Exception) {
            false
        }
    }

    fun init(context: Context) {
        try {
            AudioSynth.cacheContext(context)
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            if (prefs.contains("audio_alert_volume")) {
                val savedVolume = prefs.getFloat("audio_alert_volume", 0.85f)
                AudioSynth.setVolumeFactor(savedVolume)
            }
        } catch (_: Exception) {}
    }

    fun playWorkoutStarted(context: Context) {
        // Sem roubar AudioFocus na inicialização do treino de musculação
        AudioSynth.playChimeSound(context)
    }

    /**
     * Exclusivo para MAX RUN (Corrida): Toca o duplo pulso de preparação ("Tic-Tic", 1400 Hz -> 1800 Hz)
     * no marco de 10 segundos restantes de um bloco de corrida.
     * Na Musculação (MAX GYM), é estritamente silencioso para não causar ducks no Spotify.
     */
    fun playTenSecondWarningTick(
        context: Context,
        soundOptionOverride: String? = null,
        soundEnabledOverride: Boolean? = null
    ) {
        if (!com.example.MainActivity.isRunningSessionActive && !com.example.services.RunTracker.isCurrentlyTracking()) {
            return
        }
        val now = SystemClock.elapsedRealtime()
        if (now - lastTenSecondTickRealtimeMs < TEN_SECOND_TICK_DEBOUNCE_MS) {
            return
        }
        lastTenSecondTickRealtimeMs = now

        val prefs = try {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        } catch (_: Exception) {
            null
        }

        val isEnabled = soundEnabledOverride ?: (prefs?.getBoolean("rest_sound_enabled", true) ?: true)
        if (!isEnabled) return

        AudioSynth.vibrateShortTick(context)

        val option = soundOptionOverride ?: (prefs?.getString("rest_sound_option", "default") ?: "default")
        if (option == "vibrate") return

        val volume = if (prefs != null && prefs.contains("audio_alert_volume")) {
            prefs.getFloat("audio_alert_volume", AudioSynth.alertVolumeFactor)
        } else {
            AudioSynth.alertVolumeFactor
        }

        AudioSynth.playTenSecondWarningTick(
            context = context,
            volumeFactor = volume,
            triggerHaptic = false
        )
    }

    /**
     * Exclusivo para MAX RUN (Corrida): Toca o "TIC" seco nos últimos 3 segundos (3s, 2s, 1s) da transição de bloco da corrida.
     * Na Musculação (MAX GYM), é estritamente silencioso durante a contagem normal para manter o Spotify em 100% do volume.
     */
    fun playRestCountdownTick(
        context: Context,
        soundOptionOverride: String? = null,
        soundEnabledOverride: Boolean? = null
    ) {
        if (!com.example.MainActivity.isRunningSessionActive && !com.example.services.RunTracker.isCurrentlyTracking()) {
            return
        }
        val now = SystemClock.elapsedRealtime()
        if (now - lastCountdownTickRealtimeMs < COUNTDOWN_TICK_DEBOUNCE_MS) {
            return
        }
        lastCountdownTickRealtimeMs = now

        val prefs = try {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        } catch (_: Exception) {
            null
        }

        val isEnabled = soundEnabledOverride ?: (prefs?.getBoolean("rest_sound_enabled", true) ?: true)
        if (!isEnabled) return

        AudioSynth.vibrateShortTick(context, durationMs = 25L)

        val option = soundOptionOverride ?: (prefs?.getString("rest_sound_option", "default") ?: "default")
        if (option == "vibrate") return

        val volume = if (prefs != null && prefs.contains("audio_alert_volume")) {
            prefs.getFloat("audio_alert_volume", AudioSynth.alertVolumeFactor)
        } else {
            AudioSynth.alertVolumeFactor
        }

        AudioSynth.playClockCountdownTick(
            context = context,
            volumeFactor = volume,
            triggerHaptic = false
        )
    }

    /**
     * Reproduz o sino de boxe real (`R.raw.sino_boxe`) sobrepondo a música do usuário sem pausá-la,
     * respeitando as preferências de volume/mute de alertas e aplicando debounce anti-duplicação.
     */
    fun playBoxingBell(
        context: Context,
        bypassDebounce: Boolean = false,
        ignoreMutePrefs: Boolean = false
    ) {
        AudioSynth.playBoxingBell(
            context = context,
            bypassDebounce = bypassDebounce,
            ignoreMutePrefs = ignoreMutePrefs
        )
    }

    /**
     * Se uma sessão de corrida (MAX RUN) estiver ativa, usa o sino de transição de bloco da corrida.
     * Caso contrário (MAX GYM / Musculação), delega para o motor isolado `GymAudioEngine` (USAGE_ASSISTANCE_SONIFICATION,
     * sem requestAudioFocus) no exato segundo final (00:00).
     */
    fun playRestFinished(
        context: Context,
        soundOptionOverride: String? = null,
        soundEnabledOverride: Boolean? = null
    ) {
        if (!com.example.MainActivity.isRunningSessionActive && !com.example.services.RunTracker.isCurrentlyTracking()) {
            GymAudioEngine.playRestFinishedAlert(
                context = context,
                soundOptionOverride = soundOptionOverride,
                soundEnabledOverride = soundEnabledOverride
            )
            return
        }

        val now = SystemClock.elapsedRealtime()
        if (now - lastRestFinishedRealtimeMs < REST_DEBOUNCE_MS) {
            return
        }
        lastRestFinishedRealtimeMs = now

        val prefs = try {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        } catch (_: Exception) {
            null
        }

        val isEnabled = soundEnabledOverride ?: (prefs?.getBoolean("rest_sound_enabled", true) ?: true)
        if (!isEnabled) return

        val option = soundOptionOverride ?: (prefs?.getString("rest_sound_option", "default") ?: "default")
        if (option == "vibrate") return

        val volume = if (prefs != null && prefs.contains("audio_alert_volume")) {
            prefs.getFloat("audio_alert_volume", AudioSynth.alertVolumeFactor)
        } else {
            AudioSynth.alertVolumeFactor
        }

        when (option) {
            "bipe" -> AudioSynth.playDefaultRestAlertSound(context, volume)
            else -> AudioSynth.playBoxingBell(
                context = context,
                volumeOverride = volume,
                bypassDebounce = true,
                ignoreMutePrefs = true
            )
        }
    }

    /**
     * Toca uma prévia imediata (< 800ms) usando o GymAudioEngine (sem roubar AudioFocus).
     */
    fun previewRestSound(context: Context, option: String) {
        GymAudioEngine.previewRestSound(context, option)
    }

    /**
     * Dispara o teste de alerta sonoro de fim de descanso nas configurações usando o GymAudioEngine.
     */
    fun playTestCountdownAndDoubleBellSequence(context: Context) {
        GymAudioEngine.previewRestSound(context, "sino")
    }

    fun playWorkoutFinished(context: Context) {
        GymAudioEngine.playWorkoutFinishedAlert(context)
    }

    fun stopAllSounds(context: Context? = null) {
        GymAudioEngine.stopAllAndRelease(context)
        if (!com.example.MainActivity.isRunningSessionActive && !com.example.services.RunTracker.isCurrentlyTracking()) {
            AudioSynth.release(context)
        }
    }
}
