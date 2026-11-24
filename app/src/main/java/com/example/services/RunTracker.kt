package com.example.services

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import com.example.data.model.safeRoundToInt
import com.example.data.model.toHrTimelineList
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class RunTrackerStatus {
    IDLE,
    SEARCHING,
    GOOD,
    WEAK,
    NO_PERMISSION
}

data class RunTrackState(
    val distanceMeters: Int = 0,
    val currentPaceSecPerKm: Int? = null,
    val status: RunTrackerStatus = RunTrackerStatus.IDLE,
    val accuracyMeters: Float = 0f,
    val currentLatitude: Double? = null,
    val currentLongitude: Double? = null,
    val currentBearingDegrees: Float = 0f,
    val recordedPointsCount: Int = 0,
    val stepsCount: Int = 0,
    val isTrack400mCalibrationEnabled: Boolean = false,
    val trackCurrentLapNumber: Int = 1,
    val trackMetersInCurrentLap: Int = 0
)

data class TrackCurveSegmentResult(
    val isAccepted: Boolean,
    val dampedDistanceMeters: Double,
    val smoothedLatitude: Double,
    val smoothedLongitude: Double,
    val segmentBearingDegrees: Float,
    val deltaBearingPerSec: Float,
    val isCurveDamped: Boolean
)

data class MediaTrackInfo(
    val track: String = "",
    val artist: String = "",
    val isPlaying: Boolean = false,
    val sourcePackage: String = ""
) {
    val displayLabel: String
        get() = when {
            track.isNotBlank() && artist.isNotBlank() -> "$track — $artist"
            track.isNotBlank() -> track
            artist.isNotBlank() -> artist
            isPlaying -> "Tocando agora • Toque aqui p/ exibir nome da faixa"
            else -> "Toque ▶ para controlar sua música"
        }
}

object RunTracker {
    private val _state = MutableStateFlow(RunTrackState())
    val state: StateFlow<RunTrackState> = _state.asStateFlow()

    private val _mediaTrackInfo = MutableStateFlow(MediaTrackInfo())
    val mediaTrackInfo: StateFlow<MediaTrackInfo> = _mediaTrackInfo.asStateFlow()

    fun updateMediaTrackInfo(
        track: String?,
        artist: String?,
        isPlaying: Boolean? = null,
        sourcePackage: String? = null
    ) {
        _mediaTrackInfo.update { current ->
            val cleanTrack = track?.trim()?.takeIf { it.isNotEmpty() } ?: current.track
            val cleanArtist = artist?.trim()?.takeIf { it.isNotEmpty() } ?: current.artist
            val cleanPkg = sourcePackage?.trim()?.takeIf { it.isNotEmpty() } ?: current.sourcePackage
            current.copy(
                track = cleanTrack,
                artist = cleanArtist,
                isPlaying = isPlaying ?: current.isPlaying,
                sourcePackage = cleanPkg
            )
        }
    }

    fun updateSystemMusicPlayingState(isPlaying: Boolean) {
        _mediaTrackInfo.update { current ->
            if (current.isPlaying == isPlaying) current else current.copy(isPlaying = isPlaying)
        }
    }

    data class RecordedGpsPoint(
        val latitude: Double,
        val longitude: Double,
        val timestamp: Long,
        val speed: Float? = null,
        val altitude: Double? = null,
        val accuracy: Float? = null,
        val phase: String = "WARMUP",
        val blockIndex: Int = 0
    )

    private val _recordedPoints = mutableListOf<RecordedGpsPoint>()
    val recordedPoints: List<RecordedGpsPoint>
        get() = synchronized(_recordedPoints) { _recordedPoints.toList() }

    fun getRoutePointsJson(): String {
        val points = recordedPoints
        if (points.isEmpty()) return ""
        val jsonArray = org.json.JSONArray()
        for (pt in points) {
            val obj = org.json.JSONObject()
            obj.put("lat", pt.latitude)
            obj.put("lon", pt.longitude)
            obj.put("latitude", pt.latitude)
            obj.put("longitude", pt.longitude)
            obj.put("timestamp", pt.timestamp)
            if (pt.speed != null) obj.put("speed", pt.speed.toDouble())
            if (pt.altitude != null) obj.put("altitude", pt.altitude)
            if (pt.accuracy != null) obj.put("accuracy", pt.accuracy.toDouble())
            obj.put("phase", pt.phase)
            obj.put("blockIndex", pt.blockIndex)
            jsonArray.put(obj)
        }
        return jsonArray.toString()
    }

    // --- CONFIGURAÇÃO DO COACH DE ÁUDIO (CORRIDA) ---
    const val PACE_GRACE_PERIOD_SECONDS = 15
    const val BLOCK_TRANSITION_SHIELD_SECONDS = 10
    const val BPM_VOICE_ALERTS_ENABLED = false

    private var sessionStartTimeMs: Long = 0L
    private var currentBlockEntryTimeMs: Long = 0L
    private var currentBlockType: String = "WARMUP"
    private var currentBlockIndex: Int = 0
    @Volatile
    private var lastTenSecondWarningBlockIndex: Int = -1

    // --- MOTOR DE INTERVALOS & FILTRO MULTIVARIADO ANTI-OSCILAÇÃO DE GPS (PACOTE 2) ---
    const val UNINTENDED_WALK_MAX_SPEED_WITH_CADENCE_MPS = 1.95
    const val UNINTENDED_WALK_MAX_CADENCE_SPM = 138
    const val UNINTENDED_WALK_CADENCE_DROP_FACTOR = 0.82
    const val UNINTENDED_WALK_MIN_DURATION_WITH_CADENCE_SEC = 8
    const val UNINTENDED_WALK_MAX_SPEED_NO_CADENCE_MPS = 1.85
    const val UNINTENDED_WALK_MIN_DURATION_NO_CADENCE_SEC = 15

    private val rollingSpeedWindowMps = ArrayDeque<Double>()
    private val stepTimestampsMs = ArrayDeque<Long>()
    private var consecutiveLowSpeedWithCadenceSec: Int = 0
    private var consecutiveLowSpeedNoCadenceSec: Int = 0
    private var countedStreakWithCadence: Boolean = false
    private var countedStreakNoCadence: Boolean = false
    private val unintendedWalkByBlock = mutableMapOf<Int, Int>()
    private val runCadenceSamplesSpm = mutableListOf<Int>()

    fun getBlockUnintendedWalkMap(): Map<Int, Int> = synchronized(unintendedWalkByBlock) {
        unintendedWalkByBlock.toMap()
    }

    fun getTotalUnintendedWalkSec(): Int = synchronized(unintendedWalkByBlock) {
        unintendedWalkByBlock.values.sum()
    }

    /**
     * Avalia a cada 1 segundo (dentro de blocos RUN_WORK) se há caminhada não-intencional real
     * usando o filtro multivariado Anti-Oscilação de GPS:
     * - Com cadência disponível: >= 8s consecutivos com velocidade média móvel < 1.95 m/s (pace > 8'32"/km)
     *   E cadência < 138 spm (ou queda > 18% da cadência média de corrida da sessão).
     * - Sem leitura de cadência no momento: exige janela sustentada de >= 15s com velocidade < 1.85 m/s.
     */
    fun evaluateMultivariateUnintendedWalkTick(dtSec: Int = 1) {
        if (!isTracking || isPaused || currentBlockType != "RUN") {
            consecutiveLowSpeedWithCadenceSec = 0
            consecutiveLowSpeedNoCadenceSec = 0
            countedStreakWithCadence = false
            countedStreakNoCadence = false
            return
        }
        val latestSpeedMps: Double? = synchronized(rollingSpeedWindowMps) {
            if (rollingSpeedWindowMps.isEmpty()) null else rollingSpeedWindowMps.average()
        }
        if (latestSpeedMps == null) return

        val nowMs = System.currentTimeMillis()
        val currentCadenceSpm: Int? = synchronized(stepTimestampsMs) {
            while (stepTimestampsMs.isNotEmpty() && nowMs - stepTimestampsMs.first() > 12_000L) {
                stepTimestampsMs.removeFirst()
            }
            if (stepTimestampsMs.size >= 4) {
                val windowSec = ((nowMs - stepTimestampsMs.first()) / 1000.0).coerceIn(3.0, 12.0)
                ((stepTimestampsMs.size / windowSec) * 60.0).toInt().takeIf { it in 60..230 }
            } else null
        }

        if (currentCadenceSpm != null && latestSpeedMps >= 2.15 && currentCadenceSpm >= 142) {
            synchronized(runCadenceSamplesSpm) {
                if (runCadenceSamplesSpm.size < 600) runCadenceSamplesSpm.add(currentCadenceSpm)
            }
        }
        val sessionAvgRunCadSpm: Int? = synchronized(runCadenceSamplesSpm) {
            if (runCadenceSamplesSpm.size >= 5) runCadenceSamplesSpm.average().toInt() else null
        }
        val cadenceDropLimit = sessionAvgRunCadSpm?.let { (it * UNINTENDED_WALK_CADENCE_DROP_FACTOR).toInt() } ?: UNINTENDED_WALK_MAX_CADENCE_SPM

        val step = dtSec.coerceAtLeast(1)
        if (currentCadenceSpm != null) {
            consecutiveLowSpeedNoCadenceSec = 0
            countedStreakNoCadence = false
            val isCadenceWalk = currentCadenceSpm < UNINTENDED_WALK_MAX_CADENCE_SPM || currentCadenceSpm < cadenceDropLimit
            if (latestSpeedMps < UNINTENDED_WALK_MAX_SPEED_WITH_CADENCE_MPS && isCadenceWalk) {
                consecutiveLowSpeedWithCadenceSec += step
                if (consecutiveLowSpeedWithCadenceSec >= UNINTENDED_WALK_MIN_DURATION_WITH_CADENCE_SEC) {
                    val addSec = if (!countedStreakWithCadence) {
                        countedStreakWithCadence = true
                        consecutiveLowSpeedWithCadenceSec
                    } else step
                    synchronized(unintendedWalkByBlock) {
                        unintendedWalkByBlock[currentBlockIndex] =
                            (unintendedWalkByBlock[currentBlockIndex] ?: 0) + addSec
                    }
                }
            } else {
                consecutiveLowSpeedWithCadenceSec = 0
                countedStreakWithCadence = false
            }
        } else {
            consecutiveLowSpeedWithCadenceSec = 0
            countedStreakWithCadence = false
            if (latestSpeedMps < UNINTENDED_WALK_MAX_SPEED_NO_CADENCE_MPS) {
                consecutiveLowSpeedNoCadenceSec += step
                if (consecutiveLowSpeedNoCadenceSec >= UNINTENDED_WALK_MIN_DURATION_NO_CADENCE_SEC) {
                    val addSec = if (!countedStreakNoCadence) {
                        countedStreakNoCadence = true
                        consecutiveLowSpeedNoCadenceSec
                    } else step
                    synchronized(unintendedWalkByBlock) {
                        unintendedWalkByBlock[currentBlockIndex] =
                            (unintendedWalkByBlock[currentBlockIndex] ?: 0) + addSec
                    }
                }
            } else {
                consecutiveLowSpeedNoCadenceSec = 0
                countedStreakNoCadence = false
            }
        }
    }

    /**
     * Dispara o alerta sonoro curto e audível ("Tic-Tic", 1400Hz -> 1800Hz via AudioTrack USAGE_MEDIA + CONTENT_TYPE_MUSIC)
     * acompanhado de vibração seca (30ms / EFFECT_TICK) exatamente quando faltam 10 segundos para o fim do bloco atual,
     * com trava de disparo único por bloco e EXCLUSIVAMENTE quando uma sessão de corrida (MAX RUN) estiver ativa.
     */
    fun triggerTenSecondWarning(
        context: Context,
        blockIdx: Int = currentBlockIndex,
        blockRemainingSeconds: Int
    ): Boolean {
        if (!com.example.MainActivity.isRunningSessionActive && !isTracking) return false
        if (blockRemainingSeconds != 10) return false
        if (lastTenSecondWarningBlockIndex == blockIdx) return false
        lastTenSecondWarningBlockIndex = blockIdx
        com.example.utils.MaxSoundManager.playTenSecondWarningTick(context)
        return true
    }

    /**
     * Regra 1: Alertas vocais baseados em Frequência Cardíaca (BPM) estão desativados na corrida.
     * O guia de voz ativo é exclusivamente o Bloco e o Pace (BPM permanece apenas visual na tela).
     */
    fun shouldTriggerBpmVoiceAlert(currentBpm: Int, maxSafeBpm: Int = 190): Boolean = false

    fun isWarmupOrCooldown(blockTypeName: String = currentBlockType): Boolean {
        return when (blockTypeName.uppercase()) {
            "WARM_UP", "WARMUP", "COOL_DOWN", "COOLDOWN" -> true
            else -> false
        }
    }

    /**
     * Regra 4: Bloqueia/adia qualquer anúncio secundário de pace ou distância nos 10 segundos
     * anteriores e nos 10 segundos posteriores a uma mudança de bloco.
     */
    fun isWithinBlockTransitionWindow(
        blockElapsedSeconds: Int,
        blockRemainingSeconds: Int
    ): Boolean {
        return blockElapsedSeconds < BLOCK_TRANSITION_SHIELD_SECONDS ||
                blockRemainingSeconds <= BLOCK_TRANSITION_SHIELD_SECONDS
    }

    fun updateCurrentPhase(blockTypeName: String, blockIdx: Int = 0) {
        val normalized = when (blockTypeName.uppercase()) {
            "WARM_UP", "WARMUP" -> "WARMUP"
            "COOL_DOWN", "COOLDOWN" -> "COOL_DOWN"
            "WALK", "RECOVERY" -> "WALK"
            else -> "RUN"
        }
        currentBlockType = normalized
        currentBlockIndex = blockIdx
    }

    /**
     * Regra 2 e 3: Verifica se o corredor está dentro da janela de estabilização (Grace Period de 15s)
     * no início da corrida ou imediatamente após cada transição de bloco, ou em Aquecimento/Desaquecimento/Caminhada,
     * bloqueando alertas vocais de cobrança de pace ("Acelere!" / "Desacelere").
     */
    fun isInPaceGracePeriod(
        totalElapsedSeconds: Int,
        blockElapsedSeconds: Int,
        blockTypeName: String = currentBlockType
    ): Boolean {
        if (isWarmupOrCooldown(blockTypeName)) return true
        val isActiveRunBlock = blockTypeName.uppercase() in listOf("RUN", "INTERVAL", "SPRINT")
        if (!isActiveRunBlock) return true
        return totalElapsedSeconds < PACE_GRACE_PERIOD_SECONDS ||
                blockElapsedSeconds < PACE_GRACE_PERIOD_SECONDS
    }

    /**
     * Registra transição de bloco e retorna o anúncio de voz caso seja a transição
     * do último bloco principal para o desaquecimento (COOL_DOWN).
     */
    fun onBlockTransition(
        newBlockTypeName: String,
        isTransitionFromLastMainToCoolDown: Boolean,
        coolDownDurationSeconds: Int,
        blockIdx: Int = currentBlockIndex
    ): String? {
        updateCurrentPhase(newBlockTypeName, blockIdx)
        currentBlockEntryTimeMs = System.currentTimeMillis()
        lastTenSecondWarningBlockIndex = -1
        consecutiveLowSpeedWithCadenceSec = 0
        consecutiveLowSpeedNoCadenceSec = 0
        countedStreakWithCadence = false
        countedStreakNoCadence = false
        if (isTransitionFromLastMainToCoolDown && currentBlockType == "COOL_DOWN") {
            val coolDownMinutes = (coolDownDurationSeconds / 60).coerceAtLeast(1)
            return buildCoolDownTransitionAnnouncement(coolDownMinutes)
        }
        return null
    }

    /**
     * Regra 5: Anúncio obrigatório ao zerar o último bloco principal e entrar no desaquecimento.
     */
    fun buildCoolDownTransitionAnnouncement(coolDownMinutes: Int = 5): String {
        val mins = if (coolDownMinutes > 0) coolDownMinutes else 5
        return "Treino encerrado! Realize o desaquecimento ou alongue por $mins minutos."
    }

    /**
     * Regra 6: A cada quilômetro inteiro completado (1.0 km, 2.0 km...) no treino principal, anuncia:
     * "X quilômetros, tempo parcial: Y minutos e Z segundos por quilômetro."
     * Inclui trava de sanidade física: nunca anuncia parcial de 1 km mais rápida que 2'50"/km (170s/km).
     */
    fun buildKmSplitAnnouncement(
        kmNumber: Int,
        splitDurationSec: Int,
        avgPaceSecPerKm: Int = splitDurationSec
    ): String {
        val safeSplitSec = if (splitDurationSec < 170) {
            avgPaceSecPerKm.coerceIn(170, 900)
        } else {
            splitDurationSec.coerceIn(170, 1200)
        }
        val splitMin = safeSplitSec / 60
        val splitSec = safeSplitSec % 60
        return "$kmNumber quilômetros, tempo parcial: $splitMin minutos e $splitSec segundos por quilômetro."
    }

    /**
     * Anúncio completo de parcial a cada 1,0 km para o modo Corrida Livre / Prova (5K • 10K • Livre):
     * - Quilômetro concluído
     * - Pace da última parcial de 1 km (com trava de sanidade física >= 2'50"/km = 170s/km)
     * - Pace médio global e Tempo total decorrido
     * - Frequência Cardíaca atual (se conectado ao monitor BLE)
     */
    fun buildFreeOrRaceKmSplitAnnouncement(
        kmNumber: Int,
        splitPaceSecPerKm: Int,
        globalAvgPaceSecPerKm: Int,
        totalElapsedSec: Int,
        currentBpm: Int? = null
    ): String {
        val safeGlobalSec = globalAvgPaceSecPerKm.coerceAtLeast(170)
        val safeSplitSec = if (splitPaceSecPerKm < 170) safeGlobalSec else splitPaceSecPerKm
        val splitMin = safeSplitSec / 60
        val splitSec = safeSplitSec % 60
        val globalMin = safeGlobalSec / 60
        val globalSec = safeGlobalSec % 60
        val totalMin = (totalElapsedSec.coerceAtLeast(1)) / 60
        val totalSec = (totalElapsedSec.coerceAtLeast(1)) % 60

        val sb = StringBuilder()
        sb.append("Quilômetro $kmNumber concluído. ")
        sb.append("Ritmo da parcial: $splitMin minutos e $splitSec segundos por quilômetro. ")
        sb.append("Ritmo médio global: $globalMin minutos e $globalSec segundos. ")
        sb.append("Tempo total: $totalMin minutos e $totalSec segundos.")
        if (currentBpm != null && currentBpm > 40) {
            sb.append(" Frequência cardíaca: $currentBpm batimentos por minuto.")
        }
        return sb.toString()
    }

    /**
     * Checkpoint curto e diagnóstico de meio-quilômetro (500m, 1500m, 2500m...) ou a cada 3 minutos (180s)
     * para o modo Corrida Livre / Modo Leve:
     * - Se em ritmo de corrida (<= 8'30"/km = 510s/km e !isGentleMode), compara o pace recente com a média da sessão:
     *   * Melhorando/firme: "Quinhentos metros. Ritmo firme em [pace], batimentos em [bpm]."
     *   * Caindo (> 15s/km mais lento que a média): "Meio quilômetro. Ritmo caiu para [pace]. Ajuste a postura e a respiração."
     * - Se em ritmo de caminhada (> 8'30"/km ou Modo Leve), fala sem cobrar velocidade:
     *   * "Quinhentos metros concluídos. Ritmo constante, batimentos em [bpm]."
     */
    const val FREE_RUN_WALKING_PACE_THRESHOLD_SEC_PER_KM = 510

    fun buildFreeRunHalfKmOrTimeCheckpointAnnouncement(
        distanceMeters: Int,
        recentPaceSecPerKm: Int,
        sessionAvgPaceSecPerKm: Int,
        currentBpm: Int? = null,
        isGentleMode: Boolean = false,
        isTimeTriggered: Boolean = false,
        elapsedMinutes: Int = 3
    ): String {
        val effectivePace = if (recentPaceSecPerKm > 0) recentPaceSecPerKm else sessionAvgPaceSecPerKm
        val isWalkingPace = isGentleMode || effectivePace > FREE_RUN_WALKING_PACE_THRESHOLD_SEC_PER_KM || effectivePace <= 0
        val paceMin = (effectivePace.coerceAtLeast(1)) / 60
        val paceSec = (effectivePace.coerceAtLeast(1)) % 60
        val paceSpoken = "$paceMin minutos e $paceSec segundos"

        val milestoneLabel = when {
            isTimeTriggered -> "$elapsedMinutes minutos"
            distanceMeters >= 1500 -> {
                val wholeKm = distanceMeters / 1000
                "$wholeKm quilômetros e meio"
            }
            else -> "Quinhentos metros"
        }

        return if (isWalkingPace) {
            val baseLabel = if (isTimeTriggered) "$elapsedMinutes minutos concluídos" else if (distanceMeters >= 1500) "$milestoneLabel concluídos" else "Quinhentos metros concluídos"
            if (currentBpm != null && currentBpm > 40) {
                "$baseLabel. Ritmo constante, batimentos em $currentBpm."
            } else {
                "$baseLabel. Ritmo constante."
            }
        } else {
            val isPaceDropping = sessionAvgPaceSecPerKm > 0 && (effectivePace - sessionAvgPaceSecPerKm) > 15
            if (isPaceDropping) {
                val dropLabel = if (isTimeTriggered) milestoneLabel else if (distanceMeters >= 1500) milestoneLabel else "Meio quilômetro"
                "$dropLabel. Ritmo caiu para $paceSpoken. Ajuste a postura e a respiração."
            } else {
                if (currentBpm != null && currentBpm > 40) {
                    "$milestoneLabel. Ritmo firme em $paceSpoken, batimentos em $currentBpm."
                } else {
                    "$milestoneLabel. Ritmo firme em $paceSpoken."
                }
            }
        }
    }

    /**
     * Anúncio motivacional quando faltam exatamente 500 metros para a meta da Prova (ex: 4,50 km na Prova 5K ou 9,50 km na Prova 10K).
     */
    fun buildRaceFinal500mAnnouncement(targetDistanceMeters: Int): String {
        val kmTarget = (targetDistanceMeters / 1000).coerceAtLeast(1)
        return "Atenção! Últimos 500 metros para fechar a prova de $kmTarget quilômetros! Hora do sprint final, entregue tudo até a linha de chegada!"
    }

    /**
     * Anúncio especial ao atingir a distância exata da Prova (5,00 km ou 10,00 km), mantendo o cronômetro rodando.
     */
    fun buildRaceFinishLineAnnouncement(targetDistanceMeters: Int, totalElapsedSec: Int): String {
        val kmTarget = (targetDistanceMeters / 1000).coerceAtLeast(1)
        val mins = totalElapsedSec / 60
        val secs = totalElapsedSec % 60
        return "Meta atingida! Meta de $kmTarget quilômetros concluída em $mins minutos e $secs segundos! Excelente performance! O cronômetro segue livre até você encerrar."
    }

    /**
     * Anúncio obrigatório ao concluir exatamente 400 metros na volta atual do modo Intervalado de Pista 400m:
     * "Lap X concluída em X minutos e Y segundos. Descanso iniciado. Toque em Iniciar Lap quando estiver posicionado na largada."
     */
    fun buildTrack400mLapAnnouncement(lapNumber: Int, lapDurationSec: Int): String {
        val mins = lapDurationSec / 60
        val secs = lapDurationSec % 60
        val timeSpoken = if (mins > 0) {
            "$mins minutos e $secs segundos"
        } else {
            "$secs segundos"
        }
        return "Lap $lapNumber concluída em $timeSpoken. Descanso iniciado. Toque em Iniciar Lap quando estiver posicionado na largada."
    }

    /**
     * Janela de tolerância contínua de estabilidade de ritmo (40 a 45 segundos):
     * O Coach de voz só emite alerta ("Acelere um pouco" / "Desacelere um pouco")
     * se o pace permanecer continuamente fora da faixa alvo por mais de 45 segundos.
     */
    const val PACE_OUT_OF_ZONE_TOLERANCE_SECONDS = 45

    /**
     * Anúncio ao disparar manualmente o próximo tiro de 400m na pista.
     */
    fun buildTrack400mStartAnnouncement(lapNumber: Int): String {
        return "Lap $lapNumber iniciada! Tiro de 400 metros, vai!"
    }

    /**
     * Isolamento estrito entre Aquecimento (Warmup) e Bloco Principal:
     * Limpa a janela móvel de velocidades do aquecimento para que o cálculo de pace
     * do 1º bloco principal comece 100% zerado e alinhado ao cronômetro físico do atleta.
     */
    fun resetMainBlockPaceWindow() {
        recentPaceSamples.clear()
        _state.update { it.copy(currentPaceSecPerKm = null) }
    }

    // --- FILTRO ANTI-OVERSHOOT DE CURVA (PISTA 400M / TAF) & ANTI-JITTER ---
    const val TRACK_CURVE_DAMPING_FACTOR = 0.948f
    const val NON_TRACK_DISTANCE_DAMPING_FACTOR = 1.0
    const val TRACK_LAP_SNAP_COOLDOWN_MS = 45_000L
    const val ANTI_JITTER_MIN_SPEED_MPS = 0.7f
    const val ANTI_JITTER_MIN_DISTANCE_METERS = 1.8f
    const val ANTI_JITTER_ACCURACY_FACTOR = 0.22f

    @Volatile
    var isTrack400mCalibrationEnabled: Boolean = false
        private set

    private var lastSegmentBearingDegrees: Float? = null
    private var lastSnappedLapCount: Int = 0
    private var lastManualLapSnapRealtimeMs: Long = 0L

    fun setTrack400mCalibrationMode(enabled: Boolean) {
        isTrack400mCalibrationEnabled = enabled
        _state.update { current ->
            val distM = current.distanceMeters.coerceAtLeast(0)
            current.copy(
                isTrack400mCalibrationEnabled = enabled,
                trackCurrentLapNumber = (distM / 400) + 1,
                trackMetersInCurrentLap = distM % 400
            )
        }
    }

    fun getManualLapSnapCooldownRemainingSec(nowRealtimeMs: Long = SystemClock.elapsedRealtime()): Int {
        val lastSnap = lastManualLapSnapRealtimeMs
        if (lastSnap <= 0L) return 0
        val elapsedMs = (nowRealtimeMs - lastSnap).coerceAtLeast(0L)
        if (elapsedMs >= TRACK_LAP_SNAP_COOLDOWN_MS) return 0
        return ((TRACK_LAP_SNAP_COOLDOWN_MS - elapsedMs + 999L) / 1000L).toInt().coerceAtLeast(0)
    }

    /**
     * Aferição manual de volta na linha de 400m ("+1 Volta (400m)"):
     * - Trava anti-toque duplo de 45 segundos (SystemClock.elapsedRealtime()).
     * - Ajusta instantaneamente a distância acumulada para o múltiplo exato de 400m (laps * 400.0),
     *   corrigindo tanto submedição quanto sobremedição (overshoot radial de curva) ao cruzar a linha!
     * - Recalibra o pace em tempo real com precisão de pista oficial.
     */
    fun snapDistanceToTrack400mMultiple(
        elapsedSec: Int,
        currentDisplayedMeters: Int? = null,
        nowRealtimeMs: Long = SystemClock.elapsedRealtime()
    ): Int? {
        if (getManualLapSnapCooldownRemainingSec(nowRealtimeMs) > 0) {
            return null
        }
        val baseDisplayedM = (currentDisplayedMeters ?: accumulatedDistanceMeters.toInt()).coerceAtLeast(0)
        val nearestLap = kotlin.math.round(baseDisplayedM / 400.0).toInt().coerceAtLeast(1)
        val targetLaps = maxOf(lastSnappedLapCount + 1, nearestLap)
        val snappedTargetMeters = targetLaps * 400

        val deltaAdjustment = snappedTargetMeters - baseDisplayedM
        accumulatedDistanceMeters = (accumulatedDistanceMeters + deltaAdjustment).coerceAtLeast(snappedTargetMeters.toDouble())
        lastSnappedLapCount = targetLaps
        lastManualLapSnapRealtimeMs = nowRealtimeMs

        val calibratedPaceSecPerKm = if (elapsedSec > 0 && snappedTargetMeters >= 400) {
            ((elapsedSec * 1000.0) / snappedTargetMeters.toDouble()).toInt().takeIf { it in 120..1800 }
        } else null

        if (calibratedPaceSecPerKm != null && calibratedPaceSecPerKm > 0) {
            val calibratedSpeedMps = 1000.0 / calibratedPaceSecPerKm.toDouble()
            recentPaceSamples.clear()
            recentPaceSamples.add(PaceWindowSample(distanceMeters = calibratedSpeedMps, durationSec = 1.0))
        }

        val newTotalMeters = accumulatedDistanceMeters.toInt()
        _state.update { current ->
            current.copy(
                distanceMeters = newTotalMeters,
                currentPaceSecPerKm = calibratedPaceSecPerKm ?: current.currentPaceSecPerKm,
                isTrack400mCalibrationEnabled = true,
                trackCurrentLapNumber = targetLaps + 1,
                trackMetersInCurrentLap = 0
            )
        }
        return snappedTargetMeters
    }

    fun computeBearingDegrees(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val dLon = Math.toRadians(lon2 - lon1)
        val lat1R = Math.toRadians(lat1)
        val lat2R = Math.toRadians(lat2)
        val y = kotlin.math.sin(dLon) * kotlin.math.cos(lat2R)
        val x = kotlin.math.cos(lat1R) * kotlin.math.sin(lat2R) -
            kotlin.math.sin(lat1R) * kotlin.math.cos(lat2R) * kotlin.math.cos(dLon)
        return ((Math.toDegrees(kotlin.math.atan2(y, x)).toFloat() + 360f) % 360f)
    }

    /**
     * Filtro Anti-Overshoot de Curva (Track Curve Damping) e Anti-Jitter:
     * 1. Ignora micro-deslocamentos de ruído quando `speed < 0.7 m/s` ou `deltaDistance < max(1.8f, accuracy * 0.22f)`.
     * 2. Calcula a variação angular (`deltaBearing`) entre segmentos consecutivos.
     *    Quando o atleta está em curva contínua (`deltaBearing` entre 8° e 45°/s, típico das curvas de pista de 400m)
     *    ou com `accuracy > 6.0m`, aplica média ponderada suave nas coordenadas e fator de amortecimento de arco
     *    (`0.948f` em curvas / Modo Pista 400m), eliminando os 20-25m extras por volta que o GPS joga para fora da raia.
     */
    fun evaluateTrackCurveAndJitterFilter(
        prevLat: Double,
        prevLon: Double,
        newLat: Double,
        newLon: Double,
        dtSec: Double,
        accuracyMeters: Float,
        speedMps: Float?,
        previousBearingDeg: Float?,
        track400mActive: Boolean = isTrack400mCalibrationEnabled
    ): TrackCurveSegmentResult {
        val rawDist = GpsEvaluator.distanceBetweenMeters(prevLat, prevLon, newLat, newLon)
        val safeDtSec = dtSec.coerceAtLeast(0.5)
        val impliedSpeedMps = (rawDist / safeDtSec).toFloat()
        val effectiveSpeedMps = if (speedMps != null && speedMps > 0f) {
            speedMps
        } else {
            impliedSpeedMps
        }

        val minRequiredDelta = maxOf(ANTI_JITTER_MIN_DISTANCE_METERS, accuracyMeters * ANTI_JITTER_ACCURACY_FACTOR)
        val segmentBearing = computeBearingDegrees(prevLat, prevLon, newLat, newLon)

        if (effectiveSpeedMps < ANTI_JITTER_MIN_SPEED_MPS || rawDist < minRequiredDelta.toDouble()) {
            return TrackCurveSegmentResult(
                isAccepted = false,
                dampedDistanceMeters = 0.0,
                smoothedLatitude = prevLat,
                smoothedLongitude = prevLon,
                segmentBearingDegrees = previousBearingDeg ?: segmentBearing,
                deltaBearingPerSec = 0f,
                isCurveDamped = false
            )
        }

        val rawDeltaBearing = if (previousBearingDeg != null) {
            kotlin.math.abs(((segmentBearing - previousBearingDeg) + 540f) % 360f - 180f)
        } else {
            0f
        }
        val deltaBearingPerSec = if (previousBearingDeg != null) {
            (rawDeltaBearing / safeDtSec).toFloat()
        } else {
            0f
        }

        val isContinuousTrackCurve = previousBearingDeg != null &&
            (deltaBearingPerSec in 8.0f..45.0f || rawDeltaBearing in 8.0f..55.0f)
        val isModerateOrNoisyAccuracy = accuracyMeters > 6.0f

        val shouldApplyCurveDamping = isContinuousTrackCurve ||
            (track400mActive && isModerateOrNoisyAccuracy)

        val coordSmoothWeight = when {
            shouldApplyCurveDamping -> 0.78
            isModerateOrNoisyAccuracy -> 0.85
            else -> 1.0
        }
        val smoothedLat = prevLat + (newLat - prevLat) * coordSmoothWeight
        val smoothedLon = prevLon + (newLon - prevLon) * coordSmoothWeight

        val distanceDampingFactor = if (!track400mActive) {
            NON_TRACK_DISTANCE_DAMPING_FACTOR
        } else {
            when {
                shouldApplyCurveDamping -> TRACK_CURVE_DAMPING_FACTOR.toDouble()
                isModerateOrNoisyAccuracy -> 0.968
                else -> 1.0
            }
        }
        val dampedDist = rawDist * distanceDampingFactor

        return TrackCurveSegmentResult(
            isAccepted = true,
            dampedDistanceMeters = dampedDist,
            smoothedLatitude = smoothedLat,
            smoothedLongitude = smoothedLon,
            segmentBearingDegrees = segmentBearing,
            deltaBearingPerSec = deltaBearingPerSec,
            isCurveDamped = track400mActive && shouldApplyCurveDamping
        )
    }

    private var fusedLocationClient: FusedLocationProviderClient? = null
    private var systemLocationManager: android.location.LocationManager? = null
    private var sensorManager: android.hardware.SensorManager? = null
    const val MAX_STEP_DELTA_PER_EVENT = 40
    private const val MAX_STEP_EVENT_INTERVAL_MS = 12_000L

    private var stepCounterBaseline: Int? = null
    private var accumulatedRealSteps: Int = 0
    private var lastStepEventMs: Long? = null
    private var anchorPoint: GpsPoint? = null
    private var accumulatedDistanceMeters: Double = 0.0
    private var lastValidNanos: Long? = null
    private var lastProcessedLocationNanos: Long = 0L
    @Volatile
    private var lastFusedLocationMs: Long = 0L
    private var sessionStartRealtimeNanos: Long = 0L
    private var lastAccuracyMeters: Float = 0f
    private var isTracking = false
    private var isPaused = false

    private val recentPaceSamples = mutableListOf<PaceWindowSample>()

    private val hardwareStepListener = object : android.hardware.SensorEventListener {
        override fun onSensorChanged(event: android.hardware.SensorEvent?) {
            if (!isTracking || isPaused || event == null) return
            if (event.sensor?.type == android.hardware.Sensor.TYPE_STEP_COUNTER && event.values.isNotEmpty()) {
                val rawSteps = event.values[0].toInt()
                if (rawSteps > 0) {
                    val base = stepCounterBaseline
                    if (base == null) {
                        stepCounterBaseline = rawSteps
                    } else if (rawSteps >= base) {
                        val newSteps = (rawSteps - base).coerceAtLeast(0)
                        val delta = (newSteps - accumulatedRealSteps).coerceIn(0, MAX_STEP_DELTA_PER_EVENT)
                        accumulatedRealSteps = newSteps
                        if (delta > 0) {
                            val nowMs = System.currentTimeMillis()
                            val prevMs = lastStepEventMs ?: nowMs
                            val intervalMs = (nowMs - prevMs).coerceIn(0L, MAX_STEP_EVENT_INTERVAL_MS)
                            val startMs = nowMs - intervalMs
                            synchronized(stepTimestampsMs) {
                                for (i in 1..delta) {
                                    val stepTs = startMs + (intervalMs * i) / delta
                                    stepTimestampsMs.addLast(stepTs)
                                }
                                while (stepTimestampsMs.size > 60) stepTimestampsMs.removeFirst()
                            }
                            lastStepEventMs = nowMs
                        }
                        _state.update { it.copy(stepsCount = accumulatedRealSteps) }
                    }
                }
            }
        }

        override fun onAccuracyChanged(sensor: android.hardware.Sensor?, accuracy: Int) {}
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var watchdogJob: Job? = null

    fun hasLocationPermission(context: Context): Boolean {
        return runCatching {
            val fine = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            val coarse = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            fine || coarse
        }.getOrDefault(false)
    }

    /**
     * Verifica se o provedor de localização/GPS do sistema Android está ativo na barra de status.
     */
    fun isSystemGpsEnabled(context: Context): Boolean {
        return runCatching {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as? android.location.LocationManager
                ?: return@runCatching true
            lm.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)
        }.getOrDefault(true)
    }

    fun isCurrentlyTracking(): Boolean = isTracking && !isPaused

    fun isCurrentlyPaused(): Boolean = isPaused

    private const val FUSED_PRIORITY_WINDOW_MS = 3_000L

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            if (!isTracking || isPaused) return

            val locations = result.locations
            if (locations.isEmpty()) return

            for (location in locations) {
                if (!(location.latitude == 0.0 && location.longitude == 0.0) &&
                    location.hasAccuracy() && location.accuracy > 0f
                ) {
                    lastFusedLocationMs = SystemClock.elapsedRealtime()
                }
                processNewLocation(location)
            }
        }
    }

    private val hardwareGpsListener = object : android.location.LocationListener {
        override fun onLocationChanged(location: Location) {
            if (!isTracking || isPaused) return
            // Evita zig-zag de GPS duplo: ignora GPS_PROVIDER enquanto o FusedLocationProvider entregou ponto válido há < 3s
            if (lastFusedLocationMs > 0L && (SystemClock.elapsedRealtime() - lastFusedLocationMs) < FUSED_PRIORITY_WINDOW_MS) {
                return
            }
            processNewLocation(location)
        }

        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    private fun processNewLocation(location: Location) {
        if (location.latitude == 0.0 && location.longitude == 0.0) return

        // Bloqueia coordenadas de triangulação de rede celular ou sem métrica real de precisão
        if (!location.hasAccuracy() || location.accuracy <= 0f) return
        if (location.provider?.equals(android.location.LocationManager.NETWORK_PROVIDER, ignoreCase = true) == true &&
            location.accuracy > 18.0f
        ) {
            return
        }

        val currentNanos = SystemClock.elapsedRealtimeNanos()
        val locNanos = if (location.elapsedRealtimeNanos != 0L) location.elapsedRealtimeNanos else currentNanos

        // 1. Rejeita qualquer Location cuja idade seja > 5 segundos antes do início da sessão ou antes do instante atual
        if (sessionStartRealtimeNanos > 0L) {
            val ageBeforeSessionSec = (sessionStartRealtimeNanos - locNanos) / 1_000_000_000.0
            if (ageBeforeSessionSec > GpsEvaluator.COLD_START_MAX_LOCATION_AGE_SEC) {
                return
            }
        }
        val ageFromNowSec = (currentNanos - locNanos) / 1_000_000_000.0
        if (ageFromNowSec > GpsEvaluator.COLD_START_MAX_LOCATION_AGE_SEC) {
            return
        }

        // 2. Nos primeiros 30 segundos de treino (ou enquanto o ponto zero não travou), exige accuracy <= 22.0f metros
        val isWithinFirst30Seconds = sessionStartRealtimeNanos <= 0L ||
            (currentNanos - sessionStartRealtimeNanos) <= GpsEvaluator.COLD_START_WINDOW_NANOS
        if ((isWithinFirst30Seconds || anchorPoint == null) && location.accuracy > GpsEvaluator.COLD_START_MAX_ACCURACY_METERS) {
            _state.update { current ->
                current.copy(
                    status = RunTrackerStatus.SEARCHING,
                    accuracyMeters = location.accuracy,
                    currentLatitude = current.currentLatitude ?: location.latitude.takeIf { location.accuracy <= 65f },
                    currentLongitude = current.currentLongitude ?: location.longitude.takeIf { location.accuracy <= 65f }
                )
            }
            return
        }

        // Deduplicação contra entrega dupla entre FusedLocation e LocationManager
        if (lastProcessedLocationNanos > 0L && kotlin.math.abs(locNanos - lastProcessedLocationNanos) < 350_000_000L) {
            return
        }
        lastProcessedLocationNanos = locNanos

        val gpsPoint = GpsPoint(
            latitude = location.latitude,
            longitude = location.longitude,
            accuracyMeters = location.accuracy,
            elapsedRealtimeNanos = locNanos,
            speedMps = if (location.hasSpeed() && location.speed >= 0f) location.speed else null
        )

        val currentRecordedCount = synchronized(_recordedPoints) { _recordedPoints.size }
        val prevAnchor = anchorPoint

        val evaluation = GpsEvaluator.evaluatePoint(
            anchor = prevAnchor,
            newPoint = gpsPoint,
            currentNanos = currentNanos,
            isPaused = isPaused,
            sessionStartNanos = sessionStartRealtimeNanos,
            recordedPointsCount = currentRecordedCount
        )

        var newPace: Int? = _state.value.currentPaceSecPerKm

        when (evaluation) {
            is EvaluationResult.Accepted -> {
                if (prevAnchor == null) {
                    // Primeiro ponto âncora
                    anchorPoint = evaluation.newAnchor
                    lastValidNanos = currentNanos
                    lastAccuracyMeters = gpsPoint.accuracyMeters
                    synchronized(_recordedPoints) {
                        _recordedPoints.add(
                            RecordedGpsPoint(
                                latitude = location.latitude,
                                longitude = location.longitude,
                                timestamp = if (location.time > 0) location.time else System.currentTimeMillis(),
                                speed = evaluation.newAnchor.speedMps ?: gpsPoint.speedMps,
                                altitude = if (location.hasAltitude()) location.altitude else null,
                                accuracy = location.accuracy,
                                phase = currentBlockType,
                                blockIndex = currentBlockIndex
                            )
                        )
                    }
                } else {
                    val dtSegmentSec = if (locNanos > prevAnchor.elapsedRealtimeNanos) {
                        (locNanos - prevAnchor.elapsedRealtimeNanos) / 1_000_000_000.0
                    } else 1.0

                    val curveFilter = evaluateTrackCurveAndJitterFilter(
                        prevLat = prevAnchor.latitude,
                        prevLon = prevAnchor.longitude,
                        newLat = location.latitude,
                        newLon = location.longitude,
                        dtSec = dtSegmentSec,
                        accuracyMeters = location.accuracy,
                        speedMps = gpsPoint.speedMps,
                        previousBearingDeg = lastSegmentBearingDegrees,
                        track400mActive = isTrack400mCalibrationEnabled
                    )

                    if (curveFilter.isAccepted) {
                        accumulatedDistanceMeters += curveFilter.dampedDistanceMeters
                        anchorPoint = evaluation.newAnchor
                        lastSegmentBearingDegrees = curveFilter.segmentBearingDegrees
                        lastValidNanos = currentNanos
                        lastAccuracyMeters = gpsPoint.accuracyMeters

                        val dampedEffectiveSpeed = if (dtSegmentSec > 0.0) {
                            (curveFilter.dampedDistanceMeters / dtSegmentSec).toFloat()
                        } else {
                            evaluation.newAnchor.speedMps ?: gpsPoint.speedMps
                        }
                        if (dampedEffectiveSpeed != null && dampedEffectiveSpeed >= 0f) {
                            synchronized(rollingSpeedWindowMps) {
                                rollingSpeedWindowMps.addLast(dampedEffectiveSpeed.toDouble())
                                while (rollingSpeedWindowMps.size > 8) {
                                    rollingSpeedWindowMps.removeFirst()
                                }
                            }
                        }
                        newPace = GpsEvaluator.calculatePaceSecPerKm(
                            deltaDistanceMeters = curveFilter.dampedDistanceMeters,
                            deltaTimeSec = dtSegmentSec,
                            recentSamples = recentPaceSamples
                        ) ?: newPace

                        synchronized(_recordedPoints) {
                            // Verifica se este é o 2º ou 3º ponto gravado e houve salto em relação ao 1º ponto gravado
                            if (_recordedPoints.size in 1..2) {
                                val firstPt = _recordedPoints.first()
                                val jumpDist = GpsEvaluator.distanceBetweenMeters(
                                    firstPt.latitude, firstPt.longitude,
                                    location.latitude, location.longitude
                                )
                                val dtSec = (((if (location.time > 0) location.time else System.currentTimeMillis()) - firstPt.timestamp) / 1000.0).coerceAtLeast(0.5)
                                val impliedSpd = jumpDist / dtSec
                                val firstAcc = firstPt.accuracy ?: 50f
                                val isColdJump = (jumpDist > GpsEvaluator.COLD_START_JUMP_DISTANCE_METERS && impliedSpd > GpsEvaluator.COLD_START_JUMP_SPEED_MPS) ||
                                    (firstAcc > 40f && location.accuracy < 12f)
                                if (isColdJump) {
                                    _recordedPoints.clear()
                                    accumulatedDistanceMeters = 0.0
                                    lastSegmentBearingDegrees = null
                                }
                            }

                            _recordedPoints.add(
                                RecordedGpsPoint(
                                    latitude = curveFilter.smoothedLatitude,
                                    longitude = curveFilter.smoothedLongitude,
                                    timestamp = if (location.time > 0) location.time else System.currentTimeMillis(),
                                    speed = dampedEffectiveSpeed,
                                    altitude = if (location.hasAltitude()) location.altitude else null,
                                    accuracy = location.accuracy,
                                    phase = currentBlockType,
                                    blockIndex = currentBlockIndex
                                )
                            )
                        }
                    } else {
                        // Micro-deslocamento de ruído (speed < 0.7 m/s ou deltaDistance < max(1.8f, accuracy * 0.22f)):
                        // mantém o status do sinal vivo mas não soma distância espúria!
                        lastValidNanos = currentNanos
                        lastAccuracyMeters = gpsPoint.accuracyMeters
                    }
                }
            }
            is EvaluationResult.ColdStartAnchorReset -> {
                // Descarta o 1º ponto fantasma ("Cold-Start Anchor") e redefine o ponto zero da rota na via real
                accumulatedDistanceMeters = 0.0
                anchorPoint = evaluation.newAnchor
                lastSegmentBearingDegrees = null
                lastValidNanos = currentNanos
                lastAccuracyMeters = gpsPoint.accuracyMeters
                recentPaceSamples.clear()

                synchronized(_recordedPoints) {
                    _recordedPoints.clear()
                    _recordedPoints.add(
                        RecordedGpsPoint(
                            latitude = location.latitude,
                            longitude = location.longitude,
                            timestamp = if (location.time > 0) location.time else System.currentTimeMillis(),
                            speed = evaluation.newAnchor.speedMps ?: gpsPoint.speedMps,
                            altitude = if (location.hasAltitude()) location.altitude else null,
                            accuracy = location.accuracy,
                            phase = currentBlockType,
                            blockIndex = currentBlockIndex
                        )
                    )
                }
            }
            is EvaluationResult.Rejected -> {
                // Ignora micro-deslocamentos abaixo do filtro anti-jitter
                lastValidNanos = lastValidNanos ?: currentNanos
            }
        }

        val currentStatus = GpsEvaluator.determineStatus(
            lastValidNanos = lastValidNanos,
            lastAccuracyMeters = lastAccuracyMeters,
            currentNanos = currentNanos,
            isTracking = isTracking,
            hasPermission = true
        )

        val updatedPtsSnapshot = synchronized(_recordedPoints) { _recordedPoints.toList() }
        val resolvedBearing = when {
            location.hasBearing() && location.bearing != 0f -> location.bearing
            lastSegmentBearingDegrees != null -> lastSegmentBearingDegrees!!
            updatedPtsSnapshot.size >= 2 -> {
                val p1 = updatedPtsSnapshot[updatedPtsSnapshot.size - 2]
                val p2 = updatedPtsSnapshot.last()
                computeBearingDegrees(p1.latitude, p1.longitude, p2.latitude, p2.longitude)
            }
            else -> _state.value.currentBearingDegrees
        }

        val newDistMetersInt = accumulatedDistanceMeters.toInt()
        _state.update { current ->
            current.copy(
                distanceMeters = newDistMetersInt,
                currentPaceSecPerKm = newPace,
                status = currentStatus,
                accuracyMeters = gpsPoint.accuracyMeters,
                currentLatitude = location.latitude,
                currentLongitude = location.longitude,
                currentBearingDegrees = resolvedBearing,
                recordedPointsCount = updatedPtsSnapshot.size,
                isTrack400mCalibrationEnabled = isTrack400mCalibrationEnabled,
                trackCurrentLapNumber = (newDistMetersInt / 400) + 1,
                trackMetersInCurrentLap = newDistMetersInt % 400
            )
        }
    }

    private fun startWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = scope.launch {
            while (isActive && isTracking) {
                delay(GpsConfig.UPDATE_INTERVAL_MS)
                if (isTracking && !isPaused) {
                    val currentNanos = SystemClock.elapsedRealtimeNanos()
                     val status = GpsEvaluator.determineStatus(
                        lastValidNanos = lastValidNanos,
                        lastAccuracyMeters = lastAccuracyMeters,
                        currentNanos = currentNanos,
                        isTracking = isTracking,
                        hasPermission = true
                    )
                    val tickSec = (GpsConfig.UPDATE_INTERVAL_MS / 1000L).toInt().coerceAtLeast(1)
                    evaluateMultivariateUnintendedWalkTick(tickSec)
                    _state.update { it.copy(status = status) }
                }
            }
        }
    }

    @Suppress("MissingPermission")
    fun resumeTrackingIfStopped(context: Context) {
        if (isPaused) return
        start(context, resetMetrics = false)
    }

    @Suppress("MissingPermission")
    fun start(context: Context, resetMetrics: Boolean = true) {
        if (!hasLocationPermission(context)) {
            _state.update { it.copy(status = RunTrackerStatus.NO_PERMISSION) }
            return
        }

        if (isTracking) {
            if (resetMetrics) {
                isPaused = false
            }
            return
        }

        if (resetMetrics) {
            // Reset tracking variables for a brand new session
            accumulatedDistanceMeters = 0.0
            stepCounterBaseline = null
            accumulatedRealSteps = 0
            lastStepEventMs = null
            anchorPoint = null
            lastSegmentBearingDegrees = null
            lastSnappedLapCount = 0
            lastManualLapSnapRealtimeMs = 0L
            lastValidNanos = null
            lastProcessedLocationNanos = 0L
            lastFusedLocationMs = 0L
            sessionStartRealtimeNanos = SystemClock.elapsedRealtimeNanos()
            lastAccuracyMeters = 0f
            recentPaceSamples.clear()
            synchronized(rollingSpeedWindowMps) { rollingSpeedWindowMps.clear() }
            synchronized(stepTimestampsMs) { stepTimestampsMs.clear() }
            synchronized(unintendedWalkByBlock) { unintendedWalkByBlock.clear() }
            synchronized(runCadenceSamplesSpm) { runCadenceSamplesSpm.clear() }
            consecutiveLowSpeedWithCadenceSec = 0
            consecutiveLowSpeedNoCadenceSec = 0
            countedStreakWithCadence = false
            countedStreakNoCadence = false
            sessionStartTimeMs = System.currentTimeMillis()
            currentBlockEntryTimeMs = sessionStartTimeMs
            currentBlockType = "WARMUP"
            currentBlockIndex = 0
            lastTenSecondWarningBlockIndex = -1
            synchronized(_recordedPoints) { _recordedPoints.clear() }
            runCatching { com.example.utils.StepCadenceTracker.reset() }
            _state.value = RunTrackState(
                status = RunTrackerStatus.SEARCHING,
                isTrack400mCalibrationEnabled = isTrack400mCalibrationEnabled
            )
        } else {
            // Retomada não-destrutiva: preserva distância acumulada, passos e pontos já gravados
            anchorPoint = null
            lastSegmentBearingDegrees = null
            lastStepEventMs = null
            lastFusedLocationMs = 0L
            recentPaceSamples.clear()
            if (sessionStartRealtimeNanos <= 0L) {
                sessionStartRealtimeNanos = SystemClock.elapsedRealtimeNanos()
            }
            if (sessionStartTimeMs <= 0L) {
                sessionStartTimeMs = System.currentTimeMillis()
                currentBlockEntryTimeMs = sessionStartTimeMs
            }
            _state.update { it.copy(status = RunTrackerStatus.SEARCHING) }
        }

        val appCtx = context.applicationContext

        runCatching {
            fusedLocationClient = LocationServices.getFusedLocationProviderClient(appCtx)
            systemLocationManager = appCtx.getSystemService(Context.LOCATION_SERVICE) as? android.location.LocationManager
            sensorManager = appCtx.getSystemService(Context.SENSOR_SERVICE) as? android.hardware.SensorManager

            val locationRequest = LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                GpsConfig.UPDATE_INTERVAL_MS
            ).apply {
                setMinUpdateIntervalMillis(GpsConfig.MIN_UPDATE_INTERVAL_MS)
                setMinUpdateDistanceMeters(0f) // Filtering is handled in code
                setWaitForAccurateLocation(true) // Exige fix real de satélite, nunca triangulação grosseira na largada
            }.build()

            isTracking = true
            isPaused = false
            // JAMAIS utiliza fusedLocationClient.lastLocation / getLastKnownLocation como ponto zero da rota!
            fusedLocationClient?.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
            // Registro redundante no GPS_PROVIDER nativo (hardware) para garantir captura de satélite em aparelhos Motorola/OEM
            runCatching {
                val lm = systemLocationManager
                if (lm != null && lm.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)) {
                    lm.requestLocationUpdates(
                        android.location.LocationManager.GPS_PROVIDER,
                        GpsConfig.MIN_UPDATE_INTERVAL_MS,
                        0f,
                        hardwareGpsListener,
                        Looper.getMainLooper()
                    )
                }
            }
            // Registra sensor físico de passos (TYPE_STEP_COUNTER) se disponível
            runCatching {
                val sm = sensorManager
                val stepSensor = sm?.getDefaultSensor(android.hardware.Sensor.TYPE_STEP_COUNTER)
                if (sm != null && stepSensor != null) {
                    sm.registerListener(hardwareStepListener, stepSensor, android.hardware.SensorManager.SENSOR_DELAY_UI)
                }
            }
            // Inicia StepCadenceTracker (TYPE_STEP_DETECTOR) para Sensor Fusion anti-drift
            runCatching { com.example.utils.StepCadenceTracker.start(appCtx) }
            startWatchdog()
        }.onFailure { e ->
            isTracking = false
            isPaused = false
            if (e is SecurityException) {
                _state.update { it.copy(status = RunTrackerStatus.NO_PERMISSION) }
            }
        }
    }

    fun pause() {
        isPaused = true
    }

    fun resume() {
        isPaused = false
        anchorPoint = null
        lastSegmentBearingDegrees = null
        lastStepEventMs = null
        recentPaceSamples.clear()
    }

    fun stop() {
        if (!isTracking) return
        watchdogJob?.cancel()
        watchdogJob = null
        runCatching {
            fusedLocationClient?.removeLocationUpdates(locationCallback)
        }
        runCatching {
            systemLocationManager?.removeUpdates(hardwareGpsListener)
        }
        runCatching {
            sensorManager?.unregisterListener(hardwareStepListener)
        }
        runCatching { com.example.utils.StepCadenceTracker.stop() }
        isTracking = false
        isPaused = false
        anchorPoint = null
        lastSegmentBearingDegrees = null
        lastFusedLocationMs = 0L
        recentPaceSamples.clear()
        runCatching { com.example.utils.AudioSynth.abandonAudioFocus() }
        _state.update { it.copy(status = RunTrackerStatus.IDLE) }
    }

    fun reset() {
        stop()
        runCatching { com.example.utils.StepCadenceTracker.reset() }
        accumulatedDistanceMeters = 0.0
        stepCounterBaseline = null
        accumulatedRealSteps = 0
        lastStepEventMs = null
        anchorPoint = null
        lastSegmentBearingDegrees = null
        lastSnappedLapCount = 0
        lastManualLapSnapRealtimeMs = 0L
        lastValidNanos = null
        lastProcessedLocationNanos = 0L
        lastFusedLocationMs = 0L
        sessionStartRealtimeNanos = 0L
        lastAccuracyMeters = 0f
        recentPaceSamples.clear()
        synchronized(rollingSpeedWindowMps) { rollingSpeedWindowMps.clear() }
        synchronized(stepTimestampsMs) { stepTimestampsMs.clear() }
        synchronized(unintendedWalkByBlock) { unintendedWalkByBlock.clear() }
        synchronized(runCadenceSamplesSpm) { runCadenceSamplesSpm.clear() }
        consecutiveLowSpeedWithCadenceSec = 0
        consecutiveLowSpeedNoCadenceSec = 0
        countedStreakWithCadence = false
        countedStreakNoCadence = false
        sessionStartTimeMs = 0L
        currentBlockEntryTimeMs = 0L
        currentBlockType = "WARMUP"
        currentBlockIndex = 0
        lastTenSecondWarningBlockIndex = -1
        synchronized(_recordedPoints) { _recordedPoints.clear() }
        _state.value = RunTrackState(isTrack400mCalibrationEnabled = isTrack400mCalibrationEnabled)
    }

    /**
     * Integridade real de GPS: jamais inventa distância baseada em tempo quando o GPS registrou 0m.
     */
    fun estimateFallbackDistanceMeters(
        plan: com.example.engine.RunningWorkoutPlan?,
        durationSec: Int,
        skippedIndices: Set<Int> = emptySet(),
        baselinePaceMinPerKm: Float? = null
    ): Int = 0

    fun getBlockUnintendedWalkJson(): String {
        val map = getBlockUnintendedWalkMap()
        if (map.isEmpty()) return "{}"
        return runCatching {
            val obj = org.json.JSONObject()
            for ((k, v) in map) {
                obj.put(k.toString(), v)
            }
            obj.toString()
        }.getOrDefault("{}")
    }

    fun restoreBlockUnintendedWalkFromJson(json: String?) {
        if (json.isNullOrBlank()) return
        runCatching {
            val obj = org.json.JSONObject(json)
            synchronized(unintendedWalkByBlock) {
                unintendedWalkByBlock.clear()
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val idx = key.toIntOrNull() ?: continue
                    val sec = obj.optInt(key, 0)
                    if (sec > 0) {
                        unintendedWalkByBlock[idx] = sec
                    }
                }
            }
        }
    }

    data class ActiveWorkoutCheckpointSnapshot(
        val sessionId: String,
        val startTimestampMs: Long,
        val weekNumber: Int,
        val dayNumber: Int,
        val sessionType: String,
        val environment: String,
        val isGentleMode: Boolean,
        val shoeName: String,
        val elapsedSec: Int,
        val distanceMeters: Int,
        val mainRunDurationSec: Int,
        val mainRunDistanceMeters: Int,
        val routePointsJson: String?,
        val kmSplitsJson: String?,
        val hrTimeline: String?,
        val workoutBlockLogsJson: String?,
        val skippedIndices: Set<Int>,
        val pendingSummarySave: Boolean
    )

    /**
     * Persiste atomicamente a sessão de corrida diretamente no banco Room (`OnConflictStrategy.REPLACE`)
     * e marca a conclusão diária imediatamente, sem depender de confirmação manual no modal.
     */
    fun persistSessionDirectlyToDb(
        context: Context,
        session: com.example.data.model.RunningSession
    ) {
        val appCtx = context.applicationContext
        scope.launch {
            runCatching {
                val db = com.example.data.database.AppDatabase.getDatabase(appCtx)
                db.runningSessionDao().insertRunningSession(session)
                RunningDailyReminderManager.markRunCompletedToday(appCtx)
                clearActiveWorkoutCheckpoint(appCtx)
            }.onFailure { e ->
                android.util.Log.e("RunTracker", "Falha ao persistir sessão diretamente no banco: ${e.message}", e)
            }
        }
    }

    private const val CHECKPOINT_PREFS = "max_run_active_checkpoint_prefs"
    private val checkpointGeneration = java.util.concurrent.atomic.AtomicLong(0L)

    /**
     * Salva um checkpoint completo do treino em SharedPreferences fora da Main Thread (`Dispatchers.IO` + `apply()`),
     * evitando travamento da UI em rotas longas e prevenindo race conditions com `clearActiveWorkoutCheckpoint`.
     */
    fun saveActiveWorkoutCheckpoint(
        context: Context,
        sessionId: String,
        startTimestampMs: Long,
        weekNumber: Int,
        dayNumber: Int,
        sessionType: String,
        environment: String,
        isGentleMode: Boolean,
        shoeName: String?,
        elapsedSec: Int,
        distanceMeters: Int,
        fallbackEstimatedMeters: Int,
        mainRunDurationSec: Int = 0,
        mainRunDistanceMeters: Int = 0,
        routePointsJson: String? = null,
        kmSplitsJson: String? = null,
        hrTimeline: String? = null,
        workoutBlockLogsJson: String? = null,
        skippedIndices: Set<Int> = emptySet(),
        pendingSummarySave: Boolean = false,
        forceImmediateSave: Boolean = false
    ) {
        if (elapsedSec <= 0) return
        if (!forceImmediateSave && !pendingSummarySave) {
            if (elapsedSec < 60) return
            // Guarda Anti-Treino Vazio no Auto-Save periódico: em modo AR LIVRE (OUTDOOR), só salva se houver deslocamento >= 50m
            if (environment.equals("OUTDOOR", ignoreCase = true) && distanceMeters < 50) return
        }
        val resolvedCheckpointMeters = distanceMeters.coerceAtLeast(0)
        if (!forceImmediateSave && !pendingSummarySave && resolvedCheckpointMeters < 50) return

        val appCtx = context.applicationContext
        val generation = checkpointGeneration.incrementAndGet()
        val skippedCopy = skippedIndices.toSet()

        scope.launch(Dispatchers.IO) {
            runCatching {
                if (checkpointGeneration.get() != generation) return@launch
                val resolvedRouteJson = routePointsJson?.takeIf { it.isNotBlank() }
                    ?: if (recordedPoints.isNotEmpty() && resolvedCheckpointMeters > 0) getRoutePointsJson() else null
                val resolvedBlockLogsJson = workoutBlockLogsJson?.takeIf { it.isNotBlank() }
                    ?: getBlockUnintendedWalkJson()
                val skippedCsv = skippedCopy.joinToString(",")

                if (checkpointGeneration.get() != generation) return@launch

                appCtx.getSharedPreferences(CHECKPOINT_PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean("has_active_checkpoint", true)
                    .putString("session_id", sessionId)
                    .putLong("start_timestamp_ms", startTimestampMs)
                    .putInt("week_number", weekNumber)
                    .putInt("day_number", dayNumber)
                    .putString("session_type", sessionType)
                    .putString("environment", environment)
                    .putBoolean("is_gentle_mode", isGentleMode)
                    .putString("shoe_name", shoeName ?: "")
                    .putInt("elapsed_sec", elapsedSec)
                    .putInt("distance_meters", resolvedCheckpointMeters)
                    .putInt("main_run_duration_sec", mainRunDurationSec.coerceAtLeast(0))
                    .putInt("main_run_distance_meters", mainRunDistanceMeters.coerceAtLeast(0))
                    .putString("route_points_json", resolvedRouteJson)
                    .putString("km_splits_json", kmSplitsJson)
                    .putString("hr_timeline", hrTimeline)
                    .putString("workout_block_logs_json", resolvedBlockLogsJson)
                    .putString("skipped_indices_csv", skippedCsv)
                    .putBoolean("pending_summary_save", pendingSummarySave)
                    .apply()
            }
        }
    }

    fun clearActiveWorkoutCheckpoint(context: Context) {
        checkpointGeneration.incrementAndGet()
        runCatching {
            context.applicationContext
                .getSharedPreferences(CHECKPOINT_PREFS, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .apply()
        }
    }

    /**
     * Verifica se havia um treino em estado de pré-salvamento (`pendingSummarySave == true`, após Pausar ou Parar)
     * ou interrompido em segundo plano, restaurando automaticamente o `RunningSummaryDialog` com todos os dados,
     * mapa GPS e parciais intactos para o atleta salvar sem perder 1 metro sequer.
     */
    fun recoverUnfinishedWorkoutCheckpointIfNeeded(
        context: Context,
        onRestorePendingSummary: ((ActiveWorkoutCheckpointSnapshot) -> Unit)? = null,
        onRecovered: ((com.example.data.model.RunningSession) -> Unit)? = null
    ) {
        val appCtx = context.applicationContext
        val prefs = appCtx.getSharedPreferences(CHECKPOINT_PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean("has_active_checkpoint", false)) return

        val sessionId = prefs.getString("session_id", null) ?: return
        val elapsedSec = prefs.getInt("elapsed_sec", 0)
        val distMeters = prefs.getInt("distance_meters", 0)
        val pendingSummarySave = prefs.getBoolean("pending_summary_save", false)

        if (!pendingSummarySave && (elapsedSec < 60 || distMeters < 50)) {
            clearActiveWorkoutCheckpoint(appCtx)
            return
        }
        if (elapsedSec <= 0) {
            clearActiveWorkoutCheckpoint(appCtx)
            return
        }

        val startTimestampMs = prefs.getLong("start_timestamp_ms", System.currentTimeMillis())
        val weekNumber = prefs.getInt("week_number", 1)
        val dayNumber = prefs.getInt("day_number", 1)
        val sessionType = prefs.getString("session_type", "INTERVAL") ?: "INTERVAL"
        val environment = prefs.getString("environment", "OUTDOOR") ?: "OUTDOOR"
        val isGentleMode = prefs.getBoolean("is_gentle_mode", false)
        val shoeName = prefs.getString("shoe_name", "") ?: ""
        val mainRunDurationSec = prefs.getInt("main_run_duration_sec", 0)
        val mainRunDistanceMeters = prefs.getInt("main_run_distance_meters", 0)
        val routePointsJson = prefs.getString("route_points_json", null)?.takeIf { it.isNotBlank() }
        val kmSplitsJson = prefs.getString("km_splits_json", null)?.takeIf { it.isNotBlank() && it != "[]" }
        val hrTimeline = prefs.getString("hr_timeline", null)?.takeIf { it.isNotBlank() }
        val workoutBlockLogsJson = prefs.getString("workout_block_logs_json", null)?.takeIf { it.isNotBlank() }
        val skippedIndices = prefs.getString("skipped_indices_csv", "")
            ?.split(",")
            ?.mapNotNull { it.trim().toIntOrNull() }
            ?.toSet()
            ?: emptySet()

        restoreBlockUnintendedWalkFromJson(workoutBlockLogsJson)

        val snapshot = ActiveWorkoutCheckpointSnapshot(
            sessionId = sessionId,
            startTimestampMs = startTimestampMs,
            weekNumber = weekNumber,
            dayNumber = dayNumber,
            sessionType = sessionType,
            environment = environment,
            isGentleMode = isGentleMode,
            shoeName = shoeName,
            elapsedSec = elapsedSec,
            distanceMeters = distMeters,
            mainRunDurationSec = mainRunDurationSec,
            mainRunDistanceMeters = mainRunDistanceMeters,
            routePointsJson = routePointsJson,
            kmSplitsJson = kmSplitsJson,
            hrTimeline = hrTimeline,
            workoutBlockLogsJson = workoutBlockLogsJson,
            skippedIndices = skippedIndices,
            pendingSummarySave = pendingSummarySave
        )

        // Se o atleta havia pausado ou parado o treino (pendingSummarySave == true) ou se a tela suporta restaurar o Resumo Pós-Corrida,
        // abre o RunningSummaryDialog com 100% dos dados intactos e MANTÉM o checkpoint no disco até que o atleta clique em Salvar ou Descartar!
        if (onRestorePendingSummary != null && (pendingSummarySave || distMeters >= 50)) {
            onRestorePendingSummary.invoke(snapshot)
            return
        }

        val avgPace = if (distMeters > 0) ((elapsedSec * 1000) / distMeters) else 0
        val hasMainIsolation = mainRunDistanceMeters > 0 && mainRunDurationSec > 0 &&
            (mainRunDurationSec < elapsedSec || mainRunDistanceMeters < distMeters)
        val mainAvgPace = if (hasMainIsolation) ((mainRunDurationSec * 1000) / mainRunDistanceMeters) else null
        val parsedHrSamples = hrTimeline.toHrTimelineList().filter { it in 45..230 }
        val avgHr = parsedHrSamples.takeIf { it.isNotEmpty() }?.average()?.safeRoundToInt()
        val maxHr = parsedHrSamples.maxOrNull() ?: avgHr

        val recoveredSession = com.example.data.model.RunningSession(
            id = sessionId,
            date = startTimestampMs,
            weekNumber = weekNumber,
            dayNumber = dayNumber,
            sessionType = sessionType,
            durationSec = elapsedSec,
            distanceMeters = distMeters,
            avgPaceSecPerKm = avgPace,
            rpe = 0,
            notes = "Salvo automaticamente (Auto-Save)",
            environment = environment,
            painResponse = null,
            isGentleMode = isGentleMode,
            shoeName = shoeName,
            avgHr = avgHr,
            maxHr = maxHr,
            mainRunDurationSec = if (hasMainIsolation) mainRunDurationSec else null,
            mainRunDistanceMeters = if (hasMainIsolation) mainRunDistanceMeters else null,
            mainRunAvgPaceSecPerKm = mainAvgPace,
            kmSplitsJson = kmSplitsJson,
            hrTimeline = hrTimeline,
            routePointsJson = routePointsJson
        )
        persistSessionDirectlyToDb(appCtx, recoveredSession)
        onRecovered?.invoke(recoveredSession)
    }
}
