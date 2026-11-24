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
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Motor de Áudio Exclusivo e Isolado para a Musculação ("MAX GYM / Performance").
 *
 * Regras arquiteturais estritas:
 * 1. Separação Física Total: Nenhuma classe, estado ou requisição de foco é compartilhada com o módulo de Corrida (RunTracker / RunAudioCoach).
 * 2. Zero AudioFocus durante a contagem regressiva (60s... 1s): NUNCA solicita ou mantém AudioFocus aberto durante a execução da série ou durante a contagem do descanso.
 * 3. No exato segundo final (00:00), usa `AudioAttributes.USAGE_MEDIA` (`AudioManager.STREAM_MUSIC`) em 100% de volume para sair no mesmo canal do fone/Spotify mesmo no modo Vibrar/Silencioso, solicita `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK` APENAS durante os ~800ms do alerta e chama `abandonAudioFocusRequest` imediatamente ao terminar (com fallback garantido em 1200ms).
 * 4. Caso haja aviso de voz na musculação, o TTS é inicializado de forma preguiçosa (lazy) apenas no exato milissegundo da fala, solicita foco transiente e executa `abandonAudioFocusRequest` imediatamente no `onDone` (com timeout de segurança de 2 segundos).
 */
object GymAudioEngine {
    private const val TAG = "GymAudioEngine"
    private const val PREFS_NAME = "max_prefs"
    private const val REST_FINISHED_DEBOUNCE_MS = 1200L
    private const val ALERT_FOCUS_SAFETY_TIMEOUT_MS = 1200L
    private const val VOICE_FOCUS_SAFETY_TIMEOUT_MS = 2000L
    private const val BELL_SEEK_MS = 450
    private const val BELL_PLAY_DURATION_MS = 4000L
    private const val BELL_FADEOUT_MS = 500L

    @Volatile
    private var lastRestFinishedRealtimeMs: Long = 0L

    @Volatile
    private var lastCountdownBeepSecond: Int = -1

    @Volatile
    private var lastCountdownBeepRealtimeMs: Long = 0L

    @Volatile
    private var activeGymAudioTrack: AudioTrack? = null

    private val bellLock = Any()

    @Volatile
    private var activeBellMediaPlayer: MediaPlayer? = null

    @Volatile
    private var bellFadeJob: Job? = null

    @Volatile
    private var gymToneGenerator: ToneGenerator? = null

    @Volatile
    private var gymTts: TextToSpeech? = null

    @Volatile
    private var isGymTtsReady: Boolean = false

    @Volatile
    private var pendingVoiceMessage: String? = null

    @Volatile
    private var activeAlertFocusRequest: AudioFocusRequest? = null

    @Volatile
    private var activeVoiceFocusRequest: AudioFocusRequest? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Volatile
    private var voiceFocusWatchdogJob: Job? = null

    @Volatile
    private var alertFocusReleaseRunnable: Runnable? = null

    @Volatile
    private var trackReleaseRunnable: Runnable? = null

    private val gymFocusChangeListener = AudioManager.OnAudioFocusChangeListener { _ -> }

    /**
     * Dispara um bipe curto de contagem regressiva (3, 2, 1) diretamente do Foreground Service,
     * funcionando mesmo com a tela 100% bloqueada/apagada, sem solicitar AudioFocus longo.
     */
    fun playRestCountdownBeep(
        context: Context,
        secondRemaining: Int,
        soundEnabledOverride: Boolean? = null
    ) {
        if (secondRemaining !in 1..3) return
        val now = SystemClock.elapsedRealtime()
        if (lastCountdownBeepSecond == secondRemaining && now - lastCountdownBeepRealtimeMs < 1500L) {
            return
        }
        lastCountdownBeepSecond = secondRemaining
        lastCountdownBeepRealtimeMs = now

        val appCtx = context.applicationContext ?: context
        val prefs = runCatching {
            appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }.getOrNull()

        val isEnabled = soundEnabledOverride ?: (prefs?.getBoolean("rest_sound_enabled", true) ?: true)
        if (!isEnabled) return
        val option = prefs?.getString("rest_sound_option", "default") ?: "default"
        if (option == "vibrate") return

        Thread {
            try {
                val sampleRate = 44100
                val durationSec = 0.085f
                val numSamples = (sampleRate * durationSec).toInt()
                val buffer = ShortArray(numSamples)
                val freq = 980.0
                val gain = 32767.0 * 0.72
                for (i in 0 until numSamples) {
                    val t = i.toDouble() / sampleRate
                    val attack = (t / 0.004).coerceAtMost(1.0)
                    val env = kotlin.math.exp(-16.0 * t) * attack
                    val wave = kotlin.math.sin(2.0 * kotlin.math.PI * freq * t) * env
                    buffer[i] = (wave * gain).coerceIn(-32768.0, 32767.0).toInt().toShort()
                }
                val minBuf = AudioTrack.getMinBufferSize(
                    sampleRate,
                    android.media.AudioFormat.CHANNEL_OUT_MONO,
                    android.media.AudioFormat.ENCODING_PCM_16BIT
                ).coerceAtLeast(buffer.size * 2)

                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        android.media.AudioFormat.Builder()
                            .setEncoding(android.media.AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(android.media.AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(minBuf)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                track.write(buffer, 0, buffer.size)
                track.play()
                mainHandler.postDelayed({
                    runCatching {
                        track.stop()
                        track.release()
                    }
                }, 140L)
            } catch (e: Exception) {
                Log.w(TAG, "Countdown beep synthesis failed", e)
            }
        }.start()
    }

    /**
     * Dispara exclusivamente o alerta sonoro forte (~800ms) no exato segundo final em que o descanso zera (00:00).
     * Usa `AudioAttributes.USAGE_MEDIA` (`AudioManager.STREAM_MUSIC`) com volume 100% para tocar mesmo no silencioso,
     * solicita `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK` apenas durante o alerta (~800ms) e libera imediatamente ao terminar
     * (com timeout garantido de 1200ms).
     */
    fun playRestFinishedAlert(
        context: Context,
        soundOptionOverride: String? = null,
        soundEnabledOverride: Boolean? = null,
        bypassDebounce: Boolean = false
    ) {
        val now = SystemClock.elapsedRealtime()
        if (!bypassDebounce && now - lastRestFinishedRealtimeMs < REST_FINISHED_DEBOUNCE_MS) {
            return
        }
        lastRestFinishedRealtimeMs = now
        lastCountdownBeepSecond = -1

        val appCtx = context.applicationContext ?: context
        val prefs = runCatching {
            appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }.getOrNull()

        val isEnabled = soundEnabledOverride ?: (prefs?.getBoolean("rest_sound_enabled", true) ?: true)
        if (!isEnabled) return

        val option = soundOptionOverride ?: (prefs?.getString("rest_sound_option", "default") ?: "default")
        vibrateRestCompletionWaveform(appCtx)
        if (option == "vibrate") return

        when (option) {
            "bipe" -> playMediaStreamTripleBeep880To1760(appCtx)
            else -> playRestBell(appCtx)
        }
    }

    /**
     * Prévia curta (~800ms) ao selecionar o som nas configurações da Musculação.
     */
    fun previewRestSound(context: Context, option: String) {
        playRestFinishedAlert(
            context = context,
            soundOptionOverride = option,
            soundEnabledOverride = true,
            bypassDebounce = true
        )
    }

    /**
     * Alerta curto (~800ms) de conclusão de treino de musculação.
     */
    fun playWorkoutFinishedAlert(context: Context) {
        playRestFinishedAlert(
            context = context,
            soundOptionOverride = "sino",
            soundEnabledOverride = true,
            bypassDebounce = true
        )
    }

    /**
     * Reproduz o gongo real (`R.raw.sino_boxe`) via `MediaPlayer` em `USAGE_MEDIA` + `CONTENT_TYPE_SONIFICATION`
     * (mesmos atributos do tic-tac), sem `requestAudioFocus`, volume 1.0, pulando ~450 ms de silêncio inicial
     * e encerrando após ~4 s com fade-out nos últimos ~500 ms. Em falha, faz fallback para o triplo bipe.
     */
    private fun playRestBell(context: Context) {
        val appCtx = context.applicationContext ?: context
        stopActiveTrackOnly(appCtx)
        stopAndReleaseBellPlayer()

        val afd = runCatching {
            appCtx.resources.openRawResourceFd(com.example.R.raw.sino_boxe)
        }.onFailure { err ->
            Log.w(TAG, "Failed to open R.raw.sino_boxe, falling back to triple beep", err)
        }.getOrNull()

        if (afd == null) {
            Log.w(TAG, "R.raw.sino_boxe descriptor is null, falling back to triple beep")
            playMediaStreamTripleBeep880To1760(appCtx)
            return
        }

        val mp = MediaPlayer()
        synchronized(bellLock) {
            activeBellMediaPlayer = mp
        }

        try {
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            runCatching { afd.close() }
            mp.setVolume(1f, 1f)

            mp.setOnCompletionListener { player ->
                releaseBellPlayerIfCurrent(player)
            }
            mp.setOnErrorListener { player, what, extra ->
                Log.w(TAG, "MediaPlayer sino_boxe error: what=$what extra=$extra, falling back to triple beep")
                releaseBellPlayerIfCurrent(player)
                playMediaStreamTripleBeep880To1760(appCtx)
                true
            }
            mp.setOnPreparedListener { player ->
                val isCurrent = synchronized(bellLock) { activeBellMediaPlayer === player }
                if (!isCurrent) {
                    runCatching { player.release() }
                    return@setOnPreparedListener
                }
                runCatching {
                    player.seekTo(BELL_SEEK_MS)
                    player.setVolume(1f, 1f)
                    player.start()
                    val fadeJob = engineScope.launch {
                        delay(BELL_PLAY_DURATION_MS - BELL_FADEOUT_MS)
                        val steps = 10
                        val stepDelayMs = BELL_FADEOUT_MS / steps
                        for (step in 1..steps) {
                            val stillCurrent = synchronized(bellLock) { activeBellMediaPlayer === player }
                            if (!stillCurrent) return@launch
                            val vol = ((steps - step).toFloat() / steps.toFloat()).coerceIn(0f, 1f)
                            runCatching { player.setVolume(vol, vol) }
                            delay(stepDelayMs)
                        }
                        releaseBellPlayerIfCurrent(player)
                    }
                    synchronized(bellLock) {
                        if (activeBellMediaPlayer === player) {
                            bellFadeJob = fadeJob
                        } else {
                            fadeJob.cancel()
                        }
                    }
                }.onFailure { err ->
                    Log.w(TAG, "Failed to start MediaPlayer sino_boxe, falling back to triple beep", err)
                    releaseBellPlayerIfCurrent(player)
                    playMediaStreamTripleBeep880To1760(appCtx)
                }
            }
            mp.prepareAsync()
        } catch (t: Throwable) {
            runCatching { afd.close() }
            Log.w(TAG, "MediaPlayer sino_boxe setup failed, falling back to triple beep", t)
            releaseBellPlayerIfCurrent(mp)
            playMediaStreamTripleBeep880To1760(appCtx)
        }
    }

    private fun releaseBellPlayerIfCurrent(player: MediaPlayer) {
        synchronized(bellLock) {
            if (activeBellMediaPlayer === player) {
                bellFadeJob?.cancel()
                bellFadeJob = null
                activeBellMediaPlayer = null
            }
        }
        runCatching {
            if (player.isPlaying) {
                player.stop()
            }
        }
        runCatching { player.release() }
    }

    private fun stopAndReleaseBellPlayer() {
        val player = synchronized(bellLock) {
            bellFadeJob?.cancel()
            bellFadeJob = null
            val current = activeBellMediaPlayer
            activeBellMediaPlayer = null
            current
        }
        if (player != null) {
            runCatching {
                if (player.isPlaying) {
                    player.stop()
                }
            }
            runCatching { player.release() }
        }
    }

    /**
     * Bipe Triplo Forte Ascendente (880Hz -> 1320Hz -> 1760Hz) em `AudioAttributes.USAGE_MEDIA` (`STREAM_MUSIC`)
     * com volume em 100%, duração ~780ms e liberação imediata de `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`.
     */
    private fun playMediaStreamTripleBeep880To1760(context: Context) {
        stopAndReleaseBellPlayer()
        stopActiveTrackOnly(context)
        Thread {
            try {
                val sampleRate = 44100
                val durationSec = 0.78f
                val numSamples = (sampleRate * durationSec).toInt()
                val buffer = ShortArray(numSamples)
                val gain = 32767.0 * 0.98 // 100% de volume em USAGE_MEDIA

                for (i in 0 until numSamples) {
                    val t = i.toDouble() / sampleRate
                    val rawWave = when {
                        t < 0.20 -> {
                            // 1º Bipe: 880 Hz + harmônico 1760 Hz
                            val attack = (t / 0.005).coerceAtMost(1.0)
                            val env = kotlin.math.exp(-2.2 * t) * attack
                            (0.75 * kotlin.math.sin(2.0 * kotlin.math.PI * 880.0 * t) +
                                0.25 * kotlin.math.sin(2.0 * kotlin.math.PI * 1760.0 * t)) * env
                        }
                        t < 0.26 -> 0.0
                        t < 0.46 -> {
                            // 2º Bipe: 1320 Hz + harmônico 2640 Hz
                            val t2 = t - 0.26
                            val attack = (t2 / 0.005).coerceAtMost(1.0)
                            val env = kotlin.math.exp(-2.2 * t2) * attack
                            (0.75 * kotlin.math.sin(2.0 * kotlin.math.PI * 1320.0 * t2) +
                                0.25 * kotlin.math.sin(2.0 * kotlin.math.PI * 2640.0 * t2)) * env
                        }
                        t < 0.52 -> 0.0
                        else -> {
                            // 3º Bipe Forte: 1760 Hz + harmônico 3520 Hz
                            val t3 = t - 0.52
                            val attack = (t3 / 0.005).coerceAtMost(1.0)
                            val env = kotlin.math.exp(-2.6 * t3) * attack
                            (0.78 * kotlin.math.sin(2.0 * kotlin.math.PI * 1760.0 * t3) +
                                0.22 * kotlin.math.sin(2.0 * kotlin.math.PI * 3520.0 * t3)) * env
                        }
                    }
                    val sample = kotlin.math.tanh(rawWave * 1.45) * gain
                    buffer[i] = sample.coerceIn(-32768.0, 32767.0).toInt().toShort()
                }

                playMediaBufferWithTransientDuck(context, buffer, durationMs = 800L)
            } catch (e: Exception) {
                Log.w(TAG, "Gym triple beep synthesis failed, using fallback STREAM_MUSIC tone", e)
                playFallbackMediaTone(context)
            }
        }.start()
    }

    /**
     * Sino Duplo de Boxe + Harmônico Metálico (880Hz -> 1320Hz -> 1760Hz) em `AudioAttributes.USAGE_MEDIA`
     * (`STREAM_MUSIC`) com volume em 100%, duração ~800ms e liberação imediata de `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`.
     */
    private fun playMediaStreamDoubleBellAndChime(context: Context) {
        stopActiveTrackOnly(context)
        Thread {
            try {
                val sampleRate = 44100
                val durationSec = 0.80f
                val numSamples = (sampleRate * durationSec).toInt()
                val buffer = ShortArray(numSamples)

                val fSub = 440.0
                val f0 = 880.0
                val f1 = 1320.0
                val f2 = 1760.0
                val f3 = 2640.0
                val f4 = 3520.0

                val strike1Time = 0.004
                val strike2Time = 0.230
                val strike3Time = 0.460

                val gain = 32767.0 * 0.98 // 100% de volume em USAGE_MEDIA
                var noiseSeed = 0x5A3C9E17L

                fun synthStrike(localT: Double, baseFreqScale: Double, intensity: Double): Double {
                    if (localT < 0.0) return 0.0
                    val attack = (localT / 0.002).coerceAtMost(1.0)
                    val envSub = kotlin.math.exp(-4.0 * localT)
                    val env0 = kotlin.math.exp(-4.6 * localT)
                    val env1 = kotlin.math.exp(-6.2 * localT)
                    val env2 = kotlin.math.exp(-8.2 * localT)
                    val env3 = kotlin.math.exp(-11.0 * localT)
                    val env4 = kotlin.math.exp(-14.5 * localT)

                    val sSub = 0.16 * kotlin.math.sin(2.0 * kotlin.math.PI * (fSub * baseFreqScale) * localT) * envSub
                    val s0 = 0.44 * kotlin.math.sin(2.0 * kotlin.math.PI * (f0 * baseFreqScale) * localT) * env0
                    val s1 = 0.28 * kotlin.math.sin(2.0 * kotlin.math.PI * (f1 * baseFreqScale) * localT) * env1
                    val s2 = 0.20 * kotlin.math.sin(2.0 * kotlin.math.PI * (f2 * baseFreqScale) * localT) * env2
                    val s3 = 0.12 * kotlin.math.sin(2.0 * kotlin.math.PI * (f3 * baseFreqScale) * localT) * env3
                    val s4 = 0.08 * kotlin.math.sin(2.0 * kotlin.math.PI * (f4 * baseFreqScale) * localT) * env4

                    val strikeEnv = kotlin.math.exp(-140.0 * localT)
                    noiseSeed = (noiseSeed * 1664525L + 1013904223L) and 0xFFFFFFFFL
                    val whiteNoise = ((noiseSeed.toInt() and 0xFFFF) / 32768.0) - 1.0
                    val clang = (whiteNoise * 0.26 + kotlin.math.sin(2.0 * kotlin.math.PI * 4850.0 * localT) * 0.30) * strikeEnv

                    return (sSub + s0 + s1 + s2 + s3 + s4 + clang) * attack * intensity
                }

                for (i in 0 until numSamples) {
                    val t = i.toDouble() / sampleRate
                    val w1 = if (t >= strike1Time) {
                        val damp = if (t >= strike2Time) 0.55 else 1.0
                        synthStrike(t - strike1Time, 1.0, 0.95 * damp)
                    } else 0.0
                    val w2 = if (t >= strike2Time) {
                        val damp = if (t >= strike3Time) 0.60 else 1.0
                        synthStrike(t - strike2Time, 1.15, 1.05 * damp)
                    } else 0.0
                    val w3 = if (t >= strike3Time) {
                        synthStrike(t - strike3Time, 1.30, 1.12)
                    } else 0.0
                    val tailFade = if (t > durationSec - 0.04f) {
                        ((durationSec - t) / 0.04).coerceIn(0.0, 1.0)
                    } else 1.0
                    val sample = kotlin.math.tanh((w1 + w2 + w3) * 1.48) * gain * tailFade
                    buffer[i] = sample.coerceIn(-32768.0, 32767.0).toInt().toShort()
                }

                playMediaBufferWithTransientDuck(context, buffer, durationMs = 820L)
            } catch (e: Exception) {
                Log.w(TAG, "Gym double bell synthesis failed, using fallback STREAM_MUSIC tone", e)
                playFallbackMediaTone(context)
            }
        }.start()
    }

    private fun playMediaBufferWithTransientDuck(context: Context, buffer: ShortArray, durationMs: Long) {
        val appCtx = context.applicationContext ?: context
        requestTransientAlertFocusWithSafety(appCtx)

        try {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()

            val audioFormat = android.media.AudioFormat.Builder()
                .setSampleRate(44100)
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

            activeGymAudioTrack = audioTrack
            audioTrack.write(buffer, 0, buffer.size)
            runCatching {
                audioTrack.setVolume(1.0f) // 100% de volume em USAGE_MEDIA (STREAM_MUSIC)
                audioTrack.notificationMarkerPosition = buffer.size
                audioTrack.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
                    override fun onMarkerReached(track: AudioTrack?) {
                        if (activeGymAudioTrack === audioTrack) {
                            activeGymAudioTrack = null
                        }
                        try {
                            audioTrack.stop()
                        } catch (_: Exception) {
                        } finally {
                            runCatching { audioTrack.release() }
                            abandonAlertFocusImmediate(appCtx)
                        }
                    }

                    override fun onPeriodicNotification(track: AudioTrack?) {}
                }, mainHandler)
            }
            audioTrack.play()

            val cleanup = Runnable {
                try {
                    if (activeGymAudioTrack === audioTrack) {
                        activeGymAudioTrack = null
                    }
                    runCatching {
                        audioTrack.stop()
                        audioTrack.release()
                    }
                } finally {
                    abandonAlertFocusImmediate(appCtx)
                }
            }
            trackReleaseRunnable?.let { mainHandler.removeCallbacks(it) }
            trackReleaseRunnable = cleanup
            mainHandler.postDelayed(cleanup, durationMs.coerceAtMost(ALERT_FOCUS_SAFETY_TIMEOUT_MS))
        } catch (e: Exception) {
            Log.w(TAG, "AudioTrack USAGE_MEDIA playback failed, falling back to ToneGenerator STREAM_MUSIC", e)
            playFallbackMediaTone(appCtx)
        }
    }

    private fun playFallbackMediaTone(context: Context) {
        val appCtx = context.applicationContext ?: context
        requestTransientAlertFocusWithSafety(appCtx)
        try {
            val tg = gymToneGenerator ?: ToneGenerator(AudioManager.STREAM_MUSIC, 100).also {
                gymToneGenerator = it
            }
            tg.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 650)
        } catch (e: Exception) {
            Log.w(TAG, "ToneGenerator fallback failed", e)
        } finally {
            mainHandler.postDelayed({
                abandonAlertFocusImmediate(appCtx)
            }, 850L)
        }
    }

    private fun requestTransientAlertFocusWithSafety(context: Context) {
        runCatching {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setWillPauseWhenDucked(false)
                    .setOnAudioFocusChangeListener(gymFocusChangeListener, mainHandler)
                    .build()
                activeAlertFocusRequest = req
                audioManager.requestAudioFocus(req)
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(
                    gymFocusChangeListener,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                )
            }

            alertFocusReleaseRunnable?.let { mainHandler.removeCallbacks(it) }
            val safetyRunnable = Runnable {
                abandonAlertFocusImmediate(context)
            }
            alertFocusReleaseRunnable = safetyRunnable
            mainHandler.postDelayed(safetyRunnable, ALERT_FOCUS_SAFETY_TIMEOUT_MS)
        }
    }

    fun abandonAlertFocusImmediate(context: Context?) {
        alertFocusReleaseRunnable?.let { mainHandler.removeCallbacks(it) }
        alertFocusReleaseRunnable = null
        val ctx = context ?: return
        runCatching {
            val audioManager = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                activeAlertFocusRequest?.let { req ->
                    runCatching { audioManager.abandonAudioFocusRequest(req) }
                }
            }
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(gymFocusChangeListener)
            activeAlertFocusRequest = null
        }
    }

    private fun vibrateRestCompletionWaveform(context: Context) {
        runCatching {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(
                        VibrationEffect.createWaveform(
                            longArrayOf(0L, 260L, 110L, 320L),
                            intArrayOf(0, 255, 0, 255),
                            -1
                        )
                    )
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(longArrayOf(0L, 260L, 110L, 320L), -1)
                }
            }
        }
    }

    /**
     * Caso haja aviso de voz na musculação, inicializa o TTS de forma preguiçosa (lazy) apenas no exato
     * milissegundo em que a frase real precisar ser falada, solicita foco transiente e executa
     * abandonAudioFocusRequest imediatamente no onDone (com timeout de segurança de 2 segundos).
     */
    fun speakGymVoiceAlert(context: Context, message: String) {
        if (message.isBlank()) return
        val appCtx = context.applicationContext ?: context
        val engine = gymTts
        if (engine != null && isGymTtsReady) {
            requestTransientVoiceFocusWith2sSafety(appCtx)
            runCatching {
                engine.speak(
                    message,
                    TextToSpeech.QUEUE_FLUSH,
                    null,
                    "gym_voice_${System.currentTimeMillis()}"
                )
            }.onFailure {
                abandonVoiceFocusImmediate(appCtx)
            }
            return
        }

        pendingVoiceMessage = message
        if (gymTts == null) {
            runCatching {
                var createdTts: TextToSpeech? = null
                createdTts = TextToSpeech(appCtx) { status ->
                    if (status == TextToSpeech.SUCCESS) {
                        runCatching {
                            createdTts?.setAudioAttributes(
                                AudioAttributes.Builder()
                                    .setUsage(AudioAttributes.USAGE_MEDIA)
                                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                    .build()
                            )
                            createdTts?.language = Locale.forLanguageTag("pt-BR")
                            createdTts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                                override fun onStart(utteranceId: String?) {}
                                override fun onDone(utteranceId: String?) {
                                    abandonVoiceFocusImmediate(appCtx)
                                }
                                @Deprecated("Deprecated in Java")
                                override fun onError(utteranceId: String?) {
                                    abandonVoiceFocusImmediate(appCtx)
                                }
                                override fun onError(utteranceId: String?, errorCode: Int) {
                                    abandonVoiceFocusImmediate(appCtx)
                                }
                                override fun onStop(utteranceId: String?, interrupted: Boolean) {
                                    abandonVoiceFocusImmediate(appCtx)
                                }
                            })
                        }
                        isGymTtsReady = true
                        val msg = pendingVoiceMessage
                        pendingVoiceMessage = null
                        if (!msg.isNullOrBlank()) {
                            requestTransientVoiceFocusWith2sSafety(appCtx)
                            runCatching {
                                createdTts?.speak(
                                    msg,
                                    TextToSpeech.QUEUE_FLUSH,
                                    null,
                                    "gym_voice_${System.currentTimeMillis()}"
                                )
                            }.onFailure {
                                abandonVoiceFocusImmediate(appCtx)
                            }
                        }
                    } else {
                        pendingVoiceMessage = null
                    }
                }
                gymTts = createdTts
            }
        }
    }

    private fun requestTransientVoiceFocusWith2sSafety(context: Context) {
        runCatching {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setWillPauseWhenDucked(false)
                    .setOnAudioFocusChangeListener(gymFocusChangeListener, mainHandler)
                    .build()
                activeVoiceFocusRequest = req
                audioManager.requestAudioFocus(req)
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(
                    gymFocusChangeListener,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                )
            }
            voiceFocusWatchdogJob?.cancel()
            voiceFocusWatchdogJob = engineScope.launch {
                delay(VOICE_FOCUS_SAFETY_TIMEOUT_MS)
                abandonVoiceFocusImmediate(context)
            }
        }
    }

    fun abandonVoiceFocusImmediate(context: Context?) {
        voiceFocusWatchdogJob?.cancel()
        voiceFocusWatchdogJob = null
        val ctx = context ?: return
        runCatching {
            val audioManager = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                activeVoiceFocusRequest?.let { req ->
                    runCatching { audioManager.abandonAudioFocusRequest(req) }
                }
            }
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(gymFocusChangeListener)
            activeVoiceFocusRequest = null
        }
    }

    private fun stopActiveTrackOnly(context: Context? = null) {
        trackReleaseRunnable?.let { mainHandler.removeCallbacks(it) }
        trackReleaseRunnable = null
        val track = activeGymAudioTrack
        activeGymAudioTrack = null
        if (track != null) {
            runCatching {
                if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    track.stop()
                }
            }
            runCatching { track.release() }
        }
        abandonAlertFocusImmediate(context)
    }

    /**
     * Cancela imediatamente qualquer som ou foco da musculação ao concluir/fechar o treino ou pular descanso.
     */
    fun stopAllAndRelease(context: Context? = null) {
        pendingVoiceMessage = null
        stopAndReleaseBellPlayer()
        stopActiveTrackOnly(context)
        runCatching { gymToneGenerator?.stopTone() }
        runCatching { gymTts?.stop() }
        abandonAlertFocusImmediate(context)
        abandonVoiceFocusImmediate(context)
    }
}
