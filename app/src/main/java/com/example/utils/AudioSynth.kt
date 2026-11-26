package com.example.utils

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object AudioSynth {
    private const val TAG = "AudioSynth"
    private const val SAFETY_WATCHDOG_TIMEOUT_MS = 4000L
    private const val PREFS_NAME = "max_prefs"
    private const val BOXING_BELL_DEBOUNCE_MS = 1200L

    @Volatile
    private var lastBoxingBellRealtimeMs: Long = 0L

    @Volatile
    private var cachedAppContext: Context? = null

    @Volatile
    private var isFocusRequested: Boolean = false
    @Volatile
    private var lastFocusRequest: AudioFocusRequest? = null
    @Volatile
    private var lastFocusListener: AudioManager.OnAudioFocusChangeListener? = null
    private val activeFocusRequests = java.util.concurrent.CopyOnWriteArrayList<AudioFocusRequest>()
    @Volatile
    private var ttsSilentSinceRealtimeMs: Long = 0L
    @Volatile
    private var lastToneOrTrackRequestMs: Long = 0L
    @Volatile
    private var isTtsActiveFlag: Boolean = false

    private val mainHandler = Handler(Looper.getMainLooper())
    private val watchdogScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    @Volatile
    private var safetyWatchdogJob: Job? = null

    private val sharedFocusChangeListener = AudioManager.OnAudioFocusChangeListener { _ ->
        // No-op listener required by OEM AudioManagers to properly track and release transient ducking focus
    }

    val isFocusHeld: Boolean
        get() = isFocusRequested || lastFocusRequest != null || activeFocusRequests.isNotEmpty() || lastFocusListener != null

    @Volatile
    var alertVolumeFactor: Float = 1.0f
        private set

    @Volatile
    private var toneGenerator: ToneGenerator? = null

    @Volatile
    private var toneGeneratorVolume: Int = -1

    @Volatile
    private var activeAudioTrack: AudioTrack? = null

    fun cacheContext(context: Context?) {
        if (context != null && cachedAppContext == null) {
            cachedAppContext = context.applicationContext ?: context
        }
    }

    fun setVolumeFactor(volume: Float) {
        val clamped = volume.coerceIn(0.0f, 1.0f)
        alertVolumeFactor = clamped
        val newToneVol = (clamped * 100).toInt().coerceIn(0, 100)
        if (toneGenerator != null && kotlin.math.abs(newToneVol - toneGeneratorVolume) >= 5) {
            try {
                toneGenerator?.release()
            } catch (_: Exception) {}
            toneGenerator = null
            toneGeneratorVolume = -1
        }
    }

    /**
     * Safety Watchdog de 4 segundos: toda vez que o foco de áudio for solicitado,
     * agenda automaticamente um delay(4000) para forçar a liberação do foco caso
     * ele ainda esteja retido sem fala/som ativo, garantindo que o player de música
     * (Spotify, etc.) nunca fique preso no volume baixo.
     */
    private fun armSafetyWatchdog(context: Context?) {
        val appCtx = context?.applicationContext ?: context ?: cachedAppContext ?: return
        safetyWatchdogJob?.cancel()
        safetyWatchdogJob = watchdogScope.launch {
            delay(SAFETY_WATCHDOG_TIMEOUT_MS)
            val now = android.os.SystemClock.elapsedRealtime()
            val elapsedSinceLastRequest = now - lastToneOrTrackRequestMs
            if (isFocusHeld && !isTtsActiveFlag && activeAudioTrack == null && elapsedSinceLastRequest >= (SAFETY_WATCHDOG_TIMEOUT_MS - 150L)) {
                Log.d(TAG, "Safety Watchdog (4000ms): Forçando liberação de AudioFocus para restaurar volume da música")
                abandonAudioFocus(appCtx)
            }
        }
    }

    /**
     * Solicita foco de áudio transitório com permissão de ducking (abaixar a música de fundo)
     * EXCLUSIVAMENTE quando uma sessão de corrida (MAX RUN) estiver efetivamente ativa.
     * Na Home ou na Musculação (MAX GYM), é estritamente bloqueado para impedir "audio ducks" fantasmas.
     */
    fun requestAudioFocus(context: Context) {
        if (!com.example.MainActivity.isRunningSessionActive && !com.example.services.RunTracker.isCurrentlyTracking()) {
            return
        }
        try {
            val appCtx = context.applicationContext ?: context
            cachedAppContext = appCtx
            val audioManager = appCtx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            ttsSilentSinceRealtimeMs = 0L
            lastToneOrTrackRequestMs = android.os.SystemClock.elapsedRealtime()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val focusRequest = lastFocusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setWillPauseWhenDucked(false)
                    .setAcceptsDelayedFocusGain(false)
                    .setOnAudioFocusChangeListener(sharedFocusChangeListener, mainHandler)
                    .build()
                val grantResult = audioManager.requestAudioFocus(focusRequest)
                if (grantResult == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                    lastFocusRequest = focusRequest
                    if (!activeFocusRequests.contains(focusRequest)) {
                        activeFocusRequests.add(focusRequest)
                    }
                    lastFocusListener = sharedFocusChangeListener
                    isFocusRequested = true
                    armSafetyWatchdog(appCtx)
                }
            } else {
                @Suppress("DEPRECATION")
                val grantResult = audioManager.requestAudioFocus(
                    sharedFocusChangeListener,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                )
                if (grantResult == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                    lastFocusListener = sharedFocusChangeListener
                    isFocusRequested = true
                    armSafetyWatchdog(appCtx)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Erro ao solicitar AudioFocus no AudioSynth", e)
        }
    }

    fun abandonAudioFocus(context: Context? = null) {
        try {
            val appCtx = context?.applicationContext ?: context ?: cachedAppContext ?: return
            val audioManager = appCtx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            safetyWatchdogJob?.cancel()
            safetyWatchdogJob = null
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                for (req in activeFocusRequests) {
                    try {
                        audioManager.abandonAudioFocusRequest(req)
                    } catch (_: Exception) {}
                }
                activeFocusRequests.clear()
                lastFocusRequest?.let {
                    try {
                        audioManager.abandonAudioFocusRequest(it)
                    } catch (_: Exception) {}
                }
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(lastFocusListener ?: sharedFocusChangeListener)
            }
            lastFocusRequest = null
            lastFocusListener = null
            isFocusRequested = false
            ttsSilentSinceRealtimeMs = 0L
        } catch (e: Exception) {
            Log.w(TAG, "Erro ao abandonar AudioFocus no AudioSynth", e)
        }
    }

    /**
     * Regra 7: Watchdog periódico que restaura o foco de áudio da música caso
     * tts.isSpeaking seja false por mais de 500ms (ou 4000ms como teto máximo de segurança).
     */
    fun checkDuckingWatchdog(context: Context, isTtsSpeaking: Boolean) {
        cacheContext(context)
        isTtsActiveFlag = isTtsSpeaking
        val now = android.os.SystemClock.elapsedRealtime()
        if (isTtsSpeaking || activeAudioTrack != null) {
            ttsSilentSinceRealtimeMs = 0L
            return
        }
        if (!isFocusHeld) {
            ttsSilentSinceRealtimeMs = 0L
            return
        }
        if (ttsSilentSinceRealtimeMs == 0L) {
            ttsSilentSinceRealtimeMs = now
        } else if (
            (now - ttsSilentSinceRealtimeMs >= 500L && now - lastToneOrTrackRequestMs >= 500L) ||
            (now - lastToneOrTrackRequestMs >= SAFETY_WATCHDOG_TIMEOUT_MS)
        ) {
            abandonAudioFocus(context)
            ttsSilentSinceRealtimeMs = 0L
        }
    }

    private fun ensureTone(): ToneGenerator? {
        val targetVol = (alertVolumeFactor * 75).toInt().coerceIn(0, 100)
        if (targetVol <= 0) return null
        toneGenerator?.let {
            if (toneGeneratorVolume == targetVol) return it
            try { it.release() } catch (_: Exception) {}
            toneGenerator = null
        }
        return try {
            ToneGenerator(AudioManager.STREAM_NOTIFICATION, targetVol).also {
                toneGenerator = it
                toneGeneratorVolume = targetVol
            }
        } catch (e: RuntimeException) {
            Log.w(TAG, "STREAM_NOTIFICATION falhou no ToneGenerator, tentando STREAM_SYSTEM", e)
            try {
                ToneGenerator(AudioManager.STREAM_SYSTEM, targetVol).also {
                    toneGenerator = it
                    toneGeneratorVolume = targetVol
                }
            } catch (e2: Exception) {
                Log.w(TAG, "ToneGenerator indisponível neste dispositivo; sons ficarão mudos", e2)
                null
            }
        }
    }

    @Volatile
    private var pendingFocusAbandonRunnable: Runnable? = null

    @Volatile
    private var testSequenceRunnables = java.util.concurrent.CopyOnWriteArrayList<Runnable>()

    private fun cancelPendingFocusAbandon() {
        pendingFocusAbandonRunnable?.let { mainHandler.removeCallbacks(it) }
        pendingFocusAbandonRunnable = null
    }

    private fun scheduleFocusAbandon(context: Context?, delayMs: Long) {
        val appCtx = context?.applicationContext ?: context ?: cachedAppContext ?: return
        cancelPendingFocusAbandon()
        val runnable = Runnable {
            pendingFocusAbandonRunnable = null
            abandonAudioFocus(appCtx)
        }
        pendingFocusAbandonRunnable = runnable
        mainHandler.postDelayed(runnable, delayMs)
        armSafetyWatchdog(appCtx)
    }

    fun cancelTestSequence() {
        for (r in testSequenceRunnables) {
            mainHandler.removeCallbacks(r)
        }
        testSequenceRunnables.clear()
    }

    fun stopActiveTrack() {
        val track = activeAudioTrack
        activeAudioTrack = null
        if (track != null) {
            try {
                if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    track.stop()
                }
            } catch (_: Exception) {}
            try {
                track.release()
            } catch (_: Exception) {}
        }
    }

    /**
     * Feedback sonoro curto de interface (clique): NUNCA solicita AudioFocus para jamais causar "duck" no Spotify.
     */
    fun playTickSound(context: Context? = null) {
        if (alertVolumeFactor <= 0.01f) return
        context?.let { cacheContext(it) }
        runCatching {
            ensureTone()?.startTone(ToneGenerator.TONE_PROP_BEEP, 45)
        }
    }

    /**
     * Toque seco, cristalino e de alta presença (cronógrafo mecânico) para a contagem regressiva final 3 - 2 - 1 da CORRIDA (MAX RUN):
     * - Sintetizado via AudioTrack PCM 16-bit (44.100 Hz) em USAGE_MEDIA + CONTENT_TYPE_MUSIC
     * - Pré-silêncio de 30ms após requestAudioFocus (garante que fones Bluetooth não engulam o ataque)
     * - Pulso seco de 70ms (1600 Hz fundamental + clique percussivo 3200 Hz) em 100% full-scale
     * - Vibração hática curta (25ms / EFFECT_TICK) sincronizada.
     */
    fun playClockCountdownTick(
        context: Context? = null,
        volumeFactor: Float = alertVolumeFactor,
        triggerHaptic: Boolean = true
    ) {
        if (!com.example.MainActivity.isRunningSessionActive && !com.example.services.RunTracker.isCurrentlyTracking()) {
            return
        }
        if (triggerHaptic && context != null) {
            vibrateShortTick(context, durationMs = 25L)
        }
        if (volumeFactor <= 0.01f) return
        cancelPendingFocusAbandon()
        stopActiveTrack()
        context?.let { requestAudioFocus(it) }
        Thread {
            try {
                val sampleRate = 44100
                val durationSec = 0.110f
                val numSamples = (sampleRate * durationSec).toInt()
                val buffer = ShortArray(numSamples)
                val effectiveVol = volumeFactor.coerceIn(0.85f, 1.0f)
                val fullScale = 32767.0 * effectiveVol

                val pulseStart = 0.030
                val pulseDuration = 0.070
                val pulseEnd = pulseStart + pulseDuration // 0.100s
                val fadeInSec = 0.004
                val fadeOutSec = 0.008

                for (i in 0 until numSamples) {
                    val t = i.toDouble() / sampleRate
                    val wave = if (t in pulseStart..pulseEnd) {
                        val localT = t - pulseStart
                        val fadeIn = (localT / fadeInSec).coerceIn(0.0, 1.0)
                        val fadeOut = ((pulseDuration - localT) / fadeOutSec).coerceIn(0.0, 1.0)
                        val env = kotlin.math.min(fadeIn, fadeOut) * kotlin.math.exp(-8.5 * localT)
                        val clickEnv = kotlin.math.exp(-48.0 * localT)
                        val body = 0.78 * kotlin.math.sin(2.0 * kotlin.math.PI * 1600.0 * localT) * env
                        val click = 0.34 * kotlin.math.sin(2.0 * kotlin.math.PI * 3200.0 * localT) * clickEnv * fadeIn
                        kotlin.math.tanh((body + click) * 1.35)
                    } else {
                        0.0
                    }
                    val sample = (wave * fullScale).coerceIn(-32768.0, 32767.0).toInt().toShort()
                    buffer[i] = sample
                }

                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()

                val audioFormat = android.media.AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(android.media.AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(android.media.AudioFormat.CHANNEL_OUT_MONO)
                    .build()

                val audioTrack = AudioTrack(
                    audioAttributes,
                    audioFormat,
                    buffer.size * 2,
                    AudioTrack.MODE_STATIC,
                    AudioManager.AUDIO_SESSION_ID_GENERATE
                )

                val effCtx = context ?: cachedAppContext
                activeAudioTrack = audioTrack
                audioTrack.write(buffer, 0, buffer.size)
                try {
                    audioTrack.setVolume(1.0f)
                    audioTrack.notificationMarkerPosition = numSamples
                    audioTrack.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
                        override fun onMarkerReached(track: AudioTrack?) {
                            if (activeAudioTrack === audioTrack) activeAudioTrack = null
                            try {
                                audioTrack.stop()
                                audioTrack.release()
                            } catch (_: Exception) {}
                            abandonAudioFocus(effCtx)
                        }
                        override fun onPeriodicNotification(track: AudioTrack?) {}
                    }, mainHandler)
                } catch (_: Exception) {}
                audioTrack.play()

                mainHandler.postDelayed({
                    if (activeAudioTrack === audioTrack) {
                        activeAudioTrack = null
                    }
                    try {
                        audioTrack.stop()
                        audioTrack.release()
                    } catch (_: Exception) {}
                }, 115L)
                scheduleFocusAbandon(effCtx, 125L)
            } catch (e: Exception) {
                Log.w(TAG, "AudioTrack 3-2-1 countdown tick failed, falling back to tone", e)
                ensureTone()?.startTone(ToneGenerator.TONE_PROP_BEEP, 55)
                scheduleFocusAbandon(context ?: cachedAppContext, 70L)
            }
        }.start()
    }

    /**
     * Chime curto de interface: NUNCA solicita AudioFocus para jamais causar "duck" no Spotify.
     */
    fun playChimeSound(context: Context? = null) {
        if (alertVolumeFactor <= 0.01f) return
        context?.let { cacheContext(it) }
        runCatching {
            ensureTone()?.startTone(ToneGenerator.TONE_PROP_ACK, 120)
        }
    }

    /**
     * Vibração curta e seca (25ms–30ms / EFFECT_TICK) para os marcos de 10s e 3, 2, 1.
     */
    fun vibrateShortTick(context: Context?, durationMs: Long = 30L) {
        if (context == null) return
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? android.os.VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
            }
            if (vibrator != null && vibrator.hasVibrator()) {
                when {
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                        try {
                            vibrator.vibrate(android.os.VibrationEffect.createPredefined(android.os.VibrationEffect.EFFECT_TICK))
                        } catch (_: Exception) {
                            vibrator.vibrate(android.os.VibrationEffect.createOneShot(durationMs, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                        }
                    }
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> {
                        vibrator.vibrate(android.os.VibrationEffect.createOneShot(durationMs, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                    }
                    else -> {
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(durationMs)
                    }
                }
            }
        } catch (_: Exception) {}
    }

    /**
     * Som de preparação de 10 segundos ("Tic-Tic" estilo cronógrafo/metrônomo) sintetizado via AudioTrack
     * no canal USAGE_MEDIA + CONTENT_TYPE_MUSIC (mesmo stream da música para que fones Bluetooth não cortem o início):
     * - Pré-silêncio de 40ms após requestAudioFocus (estabiliza o ducking sem cortar a onda sonora)
     * - 1º pulso: 1400 Hz por 55ms com envelope suave (5ms fade-in/out) e amplitude 95%
     * - Intervalo: 50ms de silêncio
     * - 2º pulso: 1800 Hz por 65ms com envelope suave (5ms fade-in/out) e amplitude 100%
     * - Liberação imediata do AudioFocus logo após os ~220ms para retorno suave da música.
     */
    fun playTenSecondWarningTick(
        context: Context? = null,
        volumeFactor: Float = alertVolumeFactor,
        triggerHaptic: Boolean = true
    ) {
        if (triggerHaptic && context != null) {
            vibrateShortTick(context, durationMs = 30L)
        }
        if (volumeFactor <= 0.01f) return
        cancelPendingFocusAbandon()
        stopActiveTrack()
        val effCtx = context ?: cachedAppContext
        effCtx?.let { requestAudioFocus(it) }
        Thread {
            try {
                val sampleRate = 44100
                val durationSec = 0.220f
                val numSamples = (sampleRate * durationSec).toInt()
                val buffer = ShortArray(numSamples)
                val effectiveVol = volumeFactor.coerceIn(0.85f, 1.0f)
                val fullScale = 32767.0 * effectiveVol

                val preSilenceEnd = 0.040
                val pulse1Start = 0.040
                val pulse1Duration = 0.055
                val pulse1End = pulse1Start + pulse1Duration // 0.095
                val gapEnd = pulse1End + 0.050 // 0.145
                val pulse2Start = gapEnd
                val pulse2Duration = 0.065
                val pulse2End = pulse2Start + pulse2Duration // 0.210
                val fadeSec = 0.005

                for (i in 0 until numSamples) {
                    val t = i.toDouble() / sampleRate
                    val wave = when {
                        t < preSilenceEnd -> 0.0
                        t < pulse1End -> {
                            val localT = t - pulse1Start
                            val fadeIn = (localT / fadeSec).coerceIn(0.0, 1.0)
                            val fadeOut = ((pulse1Duration - localT) / fadeSec).coerceIn(0.0, 1.0)
                            val env = kotlin.math.min(fadeIn, fadeOut)
                            kotlin.math.sin(2.0 * kotlin.math.PI * 1400.0 * localT) * 0.95 * env
                        }
                        t < gapEnd -> 0.0
                        t < pulse2End -> {
                            val localT = t - pulse2Start
                            val fadeIn = (localT / fadeSec).coerceIn(0.0, 1.0)
                            val fadeOut = ((pulse2Duration - localT) / fadeSec).coerceIn(0.0, 1.0)
                            val env = kotlin.math.min(fadeIn, fadeOut)
                            kotlin.math.sin(2.0 * kotlin.math.PI * 1800.0 * localT) * 1.00 * env
                        }
                        else -> 0.0
                    }
                    val sample = (wave * fullScale).coerceIn(-32768.0, 32767.0).toInt().toShort()
                    buffer[i] = sample
                }

                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()

                val audioFormat = android.media.AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(android.media.AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(android.media.AudioFormat.CHANNEL_OUT_MONO)
                    .build()

                val audioTrack = AudioTrack(
                    audioAttributes,
                    audioFormat,
                    buffer.size * 2,
                    AudioTrack.MODE_STATIC,
                    AudioManager.AUDIO_SESSION_ID_GENERATE
                )

                activeAudioTrack = audioTrack
                audioTrack.write(buffer, 0, buffer.size)
                try {
                    audioTrack.setVolume(1.0f)
                    audioTrack.notificationMarkerPosition = numSamples
                    audioTrack.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
                        override fun onMarkerReached(track: AudioTrack?) {
                            if (activeAudioTrack === audioTrack) activeAudioTrack = null
                            try {
                                audioTrack.stop()
                                audioTrack.release()
                            } catch (_: Exception) {}
                            abandonAudioFocus(effCtx)
                        }
                        override fun onPeriodicNotification(track: AudioTrack?) {}
                    }, mainHandler)
                } catch (_: Exception) {}
                audioTrack.play()

                mainHandler.postDelayed({
                    if (activeAudioTrack === audioTrack) {
                        activeAudioTrack = null
                    }
                    try {
                        audioTrack.stop()
                        audioTrack.release()
                    } catch (_: Exception) {}
                }, 225L)
                scheduleFocusAbandon(effCtx, 235L)
            } catch (e: Exception) {
                Log.w(TAG, "AudioTrack 10s warning tick failed, releasing focus", e)
                scheduleFocusAbandon(effCtx, 50L)
            }
        }.start()
    }

    /**
     * Som de Bipe Eletrônico Ascendente (opção secundária "bipe"):
     * Triplo bipe esportivo via AudioTrack USAGE_MEDIA + CONTENT_TYPE_MUSIC.
     */
    fun playDefaultRestAlertSound(context: Context? = null, volumeFactor: Float = alertVolumeFactor) {
        if (volumeFactor <= 0.01f) return
        cancelPendingFocusAbandon()
        stopActiveTrack()
        val effCtx = context ?: cachedAppContext
        effCtx?.let { requestAudioFocus(it) }
        Thread {
            try {
                val sampleRate = 44100
                val durationSec = 0.84f
                val numSamples = (sampleRate * durationSec).toInt()
                val buffer = ShortArray(numSamples)
                val effectiveVol = volumeFactor.coerceIn(0.85f, 1.0f)
                val gain = (effectiveVol * 32767.0 * 0.99)
                val preSilence = 0.035

                for (i in 0 until numSamples) {
                    val rawT = i.toDouble() / sampleRate
                    val t = rawT - preSilence
                    val rawWave = when {
                        t < 0.0 -> 0.0
                        t < 0.20 -> {
                            val attack = (t / 0.004).coerceAtMost(1.0)
                            val env = kotlin.math.exp(-2.2 * t) * attack
                            val wave = 0.65 * kotlin.math.sin(2.0 * kotlin.math.PI * 1046.5 * t) +
                                0.25 * kotlin.math.sin(2.0 * kotlin.math.PI * 2093.0 * t) +
                                0.10 * kotlin.math.sin(2.0 * kotlin.math.PI * 3139.5 * t)
                            wave * env
                        }
                        t < 0.26 -> 0.0
                        t < 0.46 -> {
                            val t2 = t - 0.26
                            val attack = (t2 / 0.004).coerceAtMost(1.0)
                            val env = kotlin.math.exp(-2.2 * t2) * attack
                            val wave = 0.65 * kotlin.math.sin(2.0 * kotlin.math.PI * 1318.5 * t2) +
                                0.25 * kotlin.math.sin(2.0 * kotlin.math.PI * 2637.0 * t2) +
                                0.10 * kotlin.math.sin(2.0 * kotlin.math.PI * 3955.5 * t2)
                            wave * env
                        }
                        t < 0.52 -> 0.0
                        else -> {
                            val t3 = t - 0.52
                            val attack = (t3 / 0.004).coerceAtMost(1.0)
                            val env = kotlin.math.exp(-2.5 * t3) * attack
                            val wave = 0.65 * kotlin.math.sin(2.0 * kotlin.math.PI * 1568.0 * t3) +
                                0.25 * kotlin.math.sin(2.0 * kotlin.math.PI * 3136.0 * t3) +
                                0.10 * kotlin.math.sin(2.0 * kotlin.math.PI * 4704.0 * t3)
                            wave * env
                        }
                    }
                    val saturated = kotlin.math.tanh(rawWave * 1.45)
                    val sample = saturated * gain
                    buffer[i] = sample.coerceIn(-32768.0, 32767.0).toInt().toShort()
                }

                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()

                val audioFormat = android.media.AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(android.media.AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(android.media.AudioFormat.CHANNEL_OUT_MONO)
                    .build()

                val audioTrack = AudioTrack(
                    audioAttributes,
                    audioFormat,
                    buffer.size * 2,
                    AudioTrack.MODE_STATIC,
                    AudioManager.AUDIO_SESSION_ID_GENERATE
                )

                activeAudioTrack = audioTrack
                audioTrack.write(buffer, 0, buffer.size)
                try {
                    audioTrack.setVolume(1.0f)
                    audioTrack.notificationMarkerPosition = numSamples
                    audioTrack.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
                        override fun onMarkerReached(track: AudioTrack?) {
                            if (activeAudioTrack === audioTrack) activeAudioTrack = null
                            try {
                                audioTrack.stop()
                                audioTrack.release()
                            } catch (_: Exception) {}
                            abandonAudioFocus(effCtx)
                        }
                        override fun onPeriodicNotification(track: AudioTrack?) {}
                    }, mainHandler)
                } catch (_: Exception) {}
                audioTrack.play()

                mainHandler.postDelayed({
                    if (activeAudioTrack === audioTrack) {
                        activeAudioTrack = null
                    }
                    try {
                        audioTrack.stop()
                        audioTrack.release()
                    } catch (_: Exception) {}
                }, 860L)
                scheduleFocusAbandon(effCtx, 880L)
            } catch (e: Exception) {
                Log.w(TAG, "AudioTrack default rest alert failed, falling back to tone", e)
                ensureTone()?.startTone(ToneGenerator.TONE_PROP_ACK, 300)
                scheduleFocusAbandon(effCtx, 320L)
            }
        }.start()
    }

    /**
     * SINO DE BOXE DUPLO AUTÊNTICO ("Bell Boxe Ring Double" — "BLÉÉÉNG-BLÉÉÉNG!"):
     * - Sintetizado via AudioTrack PCM 16-bit (44.100 Hz) em USAGE_MEDIA + CONTENT_TYPE_MUSIC
     * - Pré-silêncio de 35ms após requestAudioFocus (impede corte do ataque no Bluetooth)
     * - 1ª Martelada em t = 0.035s ("BLÉÉÉNG-"): Impacto percussivo de aço no gongo de bronze + parciais inarmônicas
     * - 2ª Martelada em t = 0.315s (280ms após a primeira — "-BLÉÉÉNG!"): Re-excitação completa do impacto do martelo
     *   somada à ressonância vibrante da 1ª batida e cauda metálica longa até 1.85s.
     */
    fun playBoxingBellSound(context: Context? = null, volumeFactor: Float = alertVolumeFactor) {
        if (volumeFactor <= 0.01f) return
        cancelPendingFocusAbandon()
        stopActiveTrack()
        val effCtx = context ?: cachedAppContext
        effCtx?.let { requestAudioFocus(it) }
        Thread {
            try {
                val sampleRate = 44100
                val durationSec = 1.85f
                val numSamples = (sampleRate * durationSec).toInt()
                val buffer = ShortArray(numSamples)

                // Modos inarmônicos reais de Gongo/Sino de Boxe de Bronze Fundido
                val fSub = 293.6
                val f0 = 587.3
                val f1 = 1188.0
                val f2 = 1764.0
                val f3 = 2495.0
                val f4 = 3390.0
                val f5 = 4380.0
                val f6 = 5460.0

                val strike1Time = 0.035 // 1ª Martelada ("BLÉÉÉNG-")
                val strike2Time = 0.315 // 2ª Martelada 280ms depois ("-BLÉÉÉNG!")

                val effectiveVol = volumeFactor.coerceIn(0.85f, 1.0f)
                val gain = (effectiveVol * 32767.0 * 0.99)
                var noiseSeed = 0x5A3C9E17L

                fun synthesizeGongStrike(localT: Double, intensity: Double, isSecondStrike: Boolean): Double {
                    if (localT < 0.0) return 0.0
                    val attack = (localT / 0.0020).coerceAtMost(1.0) // Ataque percussivo de 2.0ms
                    val decayScale = if (isSecondStrike) 1.0 else 1.25

                    val envSub = kotlin.math.exp(-1.35 * decayScale * localT)
                    val env0 = kotlin.math.exp(-1.75 * decayScale * localT)
                    val env1 = kotlin.math.exp(-2.65 * decayScale * localT)
                    val env2 = kotlin.math.exp(-3.80 * decayScale * localT)
                    val env3 = kotlin.math.exp(-5.40 * decayScale * localT)
                    val env4 = kotlin.math.exp(-8.20 * decayScale * localT)
                    val env5 = kotlin.math.exp(-11.8 * decayScale * localT)
                    val env6 = kotlin.math.exp(-16.5 * decayScale * localT)

                    val beat0 = 1.0 + 0.09 * kotlin.math.sin(2.0 * kotlin.math.PI * 5.4 * localT)
                    val beat1 = 1.0 + 0.07 * kotlin.math.cos(2.0 * kotlin.math.PI * 8.8 * localT)

                    val sSub = 0.15 * kotlin.math.sin(2.0 * kotlin.math.PI * fSub * localT) * envSub
                    val s0 = 0.40 * kotlin.math.sin(2.0 * kotlin.math.PI * f0 * localT) * env0 * beat0
                    val s1 = 0.26 * kotlin.math.sin(2.0 * kotlin.math.PI * f1 * localT) * env1 * beat1
                    val s2 = 0.17 * kotlin.math.sin(2.0 * kotlin.math.PI * f2 * localT) * env2
                    val s3 = 0.12 * kotlin.math.sin(2.0 * kotlin.math.PI * f3 * localT) * env3
                    val s4 = 0.08 * kotlin.math.sin(2.0 * kotlin.math.PI * f4 * localT) * env4
                    val s5 = 0.06 * kotlin.math.sin(2.0 * kotlin.math.PI * f5 * localT) * env5
                    val s6 = 0.04 * kotlin.math.sin(2.0 * kotlin.math.PI * f6 * localT) * env6

                    // Transiente metálico agudo da batida do martelo de aço no anel do gongo
                    val strikeEnv = kotlin.math.exp(-135.0 * localT)
                    noiseSeed = (noiseSeed * 1664525L + 1013904223L) and 0xFFFFFFFFL
                    val whiteNoise = ((noiseSeed.toInt() and 0xFFFF) / 32768.0) - 1.0
                    val hammerClang = (
                        whiteNoise * 0.30 +
                        kotlin.math.sin(2.0 * kotlin.math.PI * 4850.0 * localT) * 0.34 +
                        kotlin.math.sin(2.0 * kotlin.math.PI * 6180.0 * localT) * 0.22
                    ) * strikeEnv

                    return (sSub + s0 + s1 + s2 + s3 + s4 + s5 + s6 + hammerClang) * attack * intensity
                }

                for (i in 0 until numSamples) {
                    val t = i.toDouble() / sampleRate
                    val wave1 = if (t >= strike1Time) {
                        // A 1ª batida soa cheia e, quando a 2ª martelada entra em 0.315s, mantém 55% da ressonância de fundo
                        val dampingAfterSecond = if (t >= strike2Time) 0.55 else 1.0
                        synthesizeGongStrike(t - strike1Time, intensity = 0.96 * dampingAfterSecond, isSecondStrike = false)
                    } else 0.0

                    val wave2 = if (t >= strike2Time) {
                        // A 2ª batida ("-BLÉÉÉNG!") re-excita o gongo com impacto total e sustenta o decay longo
                        synthesizeGongStrike(t - strike2Time, intensity = 1.08, isSecondStrike = true)
                    } else 0.0

                    val combined = wave1 + wave2
                    val saturated = kotlin.math.tanh(combined * 1.48)
                    val sample = saturated * gain
                    buffer[i] = sample.coerceIn(-32768.0, 32767.0).toInt().toShort()
                }

                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()

                val audioFormat = android.media.AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(android.media.AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(android.media.AudioFormat.CHANNEL_OUT_MONO)
                    .build()

                val audioTrack = AudioTrack(
                    audioAttributes,
                    audioFormat,
                    buffer.size * 2,
                    AudioTrack.MODE_STATIC,
                    AudioManager.AUDIO_SESSION_ID_GENERATE
                )

                activeAudioTrack = audioTrack
                audioTrack.write(buffer, 0, buffer.size)
                try {
                    audioTrack.setVolume(1.0f)
                    audioTrack.notificationMarkerPosition = numSamples
                    audioTrack.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
                        override fun onMarkerReached(track: AudioTrack?) {
                            if (activeAudioTrack === audioTrack) activeAudioTrack = null
                            try {
                                audioTrack.stop()
                                audioTrack.release()
                            } catch (_: Exception) {}
                            abandonAudioFocus(effCtx)
                        }
                        override fun onPeriodicNotification(track: AudioTrack?) {}
                    }, mainHandler)
                } catch (_: Exception) {}
                audioTrack.play()

                mainHandler.postDelayed({
                    if (activeAudioTrack === audioTrack) {
                        activeAudioTrack = null
                    }
                    try {
                        audioTrack.stop()
                        audioTrack.release()
                    } catch (_: Exception) {}
                }, 1880L)
                scheduleFocusAbandon(effCtx, 1910L)
            } catch (e: Exception) {
                Log.w(TAG, "AudioTrack double boxing bell failed, falling back to tone", e)
                ensureTone()?.startTone(ToneGenerator.TONE_PROP_BEEP2, 400)
                scheduleFocusAbandon(effCtx, 420L)
            }
        }.start()
    }

    /**
     * Reproduz o som real de sino de boxe (`R.raw.sino_boxe`) via `MediaPlayer` com
     * `USAGE_ASSISTANCE_SONIFICATION` / `CONTENT_TYPE_SONIFICATION`, sem solicitar AudioFocus,
     * para tocar por cima da música (Spotify/YouTube) sem pausar o player do usuário.
     * Respeita mute/volume de alertas do app e aplica debounce para evitar disparo duplicado.
     */
    fun playBoxingBell(
        context: Context,
        volumeOverride: Float? = null,
        bypassDebounce: Boolean = false,
        ignoreMutePrefs: Boolean = false
    ) {
        runCatching {
            val appCtx = context.applicationContext ?: context
            cachedAppContext = appCtx

            val now = SystemClock.elapsedRealtime()
            if (!bypassDebounce && now - lastBoxingBellRealtimeMs < BOXING_BELL_DEBOUNCE_MS) {
                return
            }

            val prefs = runCatching {
                appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            }.getOrNull()

            if (!ignoreMutePrefs) {
                val isEnabled = prefs?.getBoolean("rest_sound_enabled", true) ?: true
                if (!isEnabled) return
                val option = prefs?.getString("rest_sound_option", "default") ?: "default"
                if (option == "vibrate") return
            }

            val effectiveVol = (
                volumeOverride
                    ?: if (prefs != null && prefs.contains("audio_alert_volume")) {
                        prefs.getFloat("audio_alert_volume", alertVolumeFactor)
                    } else {
                        alertVolumeFactor
                    }
            ).coerceIn(0.0f, 1.0f)

            if (effectiveVol <= 0.01f) return

            lastBoxingBellRealtimeMs = now

            val afd = appCtx.resources.openRawResourceFd(com.example.R.raw.sino_boxe) ?: return
            val mp = MediaPlayer()
            try {
                mp.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                runCatching { afd.close() }
                mp.setVolume(effectiveVol, effectiveVol)
                mp.setOnCompletionListener { player ->
                    runCatching { player.release() }
                }
                mp.setOnErrorListener { player, what, extra ->
                    Log.w(TAG, "MediaPlayer sino_boxe error: what=$what extra=$extra")
                    runCatching { player.release() }
                    true
                }
                mp.setOnPreparedListener { player ->
                    runCatching { player.start() }.onFailure { err ->
                        Log.w(TAG, "Failed to start MediaPlayer sino_boxe", err)
                        runCatching { player.release() }
                    }
                }
                mp.prepareAsync()
            } catch (t: Throwable) {
                runCatching { afd.close() }
                runCatching { mp.release() }
                throw t
            }
        }.onFailure { err ->
            Log.w(TAG, "playBoxingBell failed safely", err)
        }
    }

    /**
     * Dispara a sequência completa de validação:
     * 3s (TIC) -> 2s (TIC) -> 1s (TIC) -> 0s (Sino de Boxe real R.raw.sino_boxe)
     */
    fun playCountdownAndDoubleBellTestSequence(
        context: Context,
        volumeFactor: Float = alertVolumeFactor
    ) {
        cancelTestSequence()
        cancelPendingFocusAbandon()
        val appCtx = context.applicationContext ?: context
        cachedAppContext = appCtx

        // 3s -> TIC imediato
        playClockCountdownTick(appCtx, volumeFactor = volumeFactor, triggerHaptic = true)

        // 2s -> TIC em 1000ms
        val r2 = Runnable {
            playClockCountdownTick(appCtx, volumeFactor = volumeFactor, triggerHaptic = true)
        }
        // 1s -> TIC em 2000ms
        val r1 = Runnable {
            playClockCountdownTick(appCtx, volumeFactor = volumeFactor, triggerHaptic = true)
        }
        // 0s -> Sino de Boxe real (R.raw.sino_boxe) em 3000ms
        val r0 = Runnable {
            playBoxingBell(appCtx, volumeOverride = volumeFactor, bypassDebounce = true, ignoreMutePrefs = true)
        }

        testSequenceRunnables.add(r2)
        testSequenceRunnables.add(r1)
        testSequenceRunnables.add(r0)

        mainHandler.postDelayed(r2, 1000L)
        mainHandler.postDelayed(r1, 2000L)
        mainHandler.postDelayed(r0, 3000L)
    }

    fun playTempoCompleteSound(context: Context? = null) {
        val effCtx = context ?: cachedAppContext
        if (effCtx != null) {
            playBoxingBell(effCtx)
        } else {
            playBoxingBellSound(null, alertVolumeFactor)
        }
    }

    fun playSyntheticFallback(context: Context? = null) {
        playTickSound(context ?: cachedAppContext)
    }

    fun release(context: Context? = null) {
        cancelTestSequence()
        cancelPendingFocusAbandon()
        mainHandler.removeCallbacksAndMessages(null)
        stopActiveTrack()
        try {
            toneGenerator?.stopTone()
        } catch (_: Exception) {}
        try {
            toneGenerator?.release()
        } catch (_: Exception) {}
        toneGenerator = null
        toneGeneratorVolume = -1
        abandonAudioFocus(context ?: cachedAppContext)
    }
}
