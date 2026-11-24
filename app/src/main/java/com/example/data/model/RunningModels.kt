package com.example.data.model

import android.location.Location
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.engine.RunningBlockType
import com.example.engine.RunningProgramManager
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt

fun Double.safeRoundToInt(default: Int = 0): Int =
    if (this.isNaN() || this.isInfinite()) default else this.roundToInt()

fun Float.safeRoundToInt(default: Int = 0): Int =
    if (this.isNaN() || this.isInfinite()) default else this.roundToInt()

fun Int.safeCoerceIn(minVal: Int, maxVal: Int): Int =
    this.coerceIn(minVal, maxOf(minVal, maxVal))

fun Float.safeCoerceIn(minVal: Float, maxVal: Float): Float =
    if (this.isNaN() || this.isInfinite()) minVal else this.coerceIn(minVal, maxOf(minVal, maxVal))

fun Double.safeCoerceIn(minVal: Double, maxVal: Double): Double =
    if (this.isNaN() || this.isInfinite()) minVal else this.coerceIn(minVal, maxOf(minVal, maxVal))

@Entity(tableName = "running_sessions")
data class RunningSession(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val date: Long = System.currentTimeMillis(),
    val weekNumber: Int = 1,
    val dayNumber: Int = 1,
    val sessionType: String = "VOLUME", // "VOLUME", "CONTINUOUS", "SPECIFICITY"
    val durationSec: Int = 0,
    val distanceMeters: Int = 0,
    val avgPaceSecPerKm: Int = 0,
    val rpe: Int = 0, // 0 = não preenchido manualmente; 1-10 = preenchido manualmente pelo usuário
    val notes: String? = null,
    val environment: String = "OUTDOOR", // "OUTDOOR", "TREADMILL"
    val painResponse: String? = null, // "PIOROU", "IGUAL", "MELHOROU", or null
    val isGentleMode: Boolean = false,
    val shoeName: String? = null,
    val avgHr: Int? = null,
    val maxHr: Int? = null,
    val mainRunDurationSec: Int? = null,
    val mainRunDistanceMeters: Int? = null,
    val mainRunAvgPaceSecPerKm: Int? = null,
    val kmSplitsJson: String? = null,
    val hrTimeline: String? = null,
    val routePointsJson: String? = null,
    val aiCoachReport: String? = null,
    // --- PACOTE 1 (EVOLUÇÃO CIENTÍFICA MAX RUN) — 100% RETROCOMPATÍVEL (NULLABLE) ---
    val preRunCheckIn: PreRunCheckIn? = null,
    val rpeCr10: Int? = null,
    val sRpeLoadUa: Int? = null, // round((durationSec / 60.0) * rpeCr10)
    val executionAudit: SessionExecutionAudit? = null,
    val hrProvenance: MetricProvenance? = null,
    // --- PACOTE 2 (MOTOR DE INTERVALOS, HRR, EF & DERIVA CARDÍACA) — 100% RETROCOMPATÍVEL ---
    val executedIntervals: List<ExecutedInterval> = emptyList(),
    val advancedMetrics: AdvancedRunningMetrics? = null
)

typealias RunSession = RunningSession

// --- 1. PROVENIÊNCIA E CONFIABILIDADE DO DADO ---
enum class DataSourceType(val label: String) {
    OPTICAL_PPG_WATCH("Sensor Óptico (Relógio PPG)"),
    CHEST_STRAP_BLE("Cinta Peitoral BLE (ECG)"),
    PHONE_GPS("GPS do Celular"),
    STEP_SENSOR_PEDOMETER("Pedômetro / Acelerômetro"),
    ATHLETE_REPORTED("Relato Direto do Atleta"),
    DERIVED_CALCULATION("Cálculo Derivado")
}

enum class DataConfidenceLevel(val label: String) {
    HIGH("Alta"),
    MODERATE("Moderada"),
    LOW("Baixa"),
    UNVERIFIED("Não Verificada")
}

data class MetricProvenance(
    val source: DataSourceType,
    val deviceName: String? = null,
    val confidence: DataConfidenceLevel = DataConfidenceLevel.MODERATE
)

// --- 2. CHECK-IN PRÉ-CORRIDA (DIFERENCIA PULADO VS. PREENCHIDO) ---
enum class CheckInStatus {
    COMPLETED,      // Atleta respondeu ativamente
    SKIPPED,        // Atleta clicou em "Pular" (NÃO transformar em "Normal")
    NOT_PROMPTED    // Sessão antiga (legado antes desta atualização)
}

enum class ReadinessLevel(val label: String) {
    LOW("Baixa/Ruim"),
    NORMAL("Normal"),
    HIGH("Alta/Ótimo")
}

enum class PainBodyRegion(val label: String) {
    CALF("Panturrilha"),
    HAMSTRING("Posterior de Coxa"),
    QUADRICEPS("Quadríceps"),
    SHIN("Canela"),
    KNEE("Joelho"),
    HIP_GLUTE("Quadril/Glúteo"),
    LOWER_BACK("Lombar"),
    OTHER("Outra região")
}

data class LocalizedPainEntry(
    val region: PainBodyRegion,
    val intensityCr10: Int = 3, // 1 a 10
    val altersGaitMechanics: Boolean = false // "Altera a passada?"
)

data class PreRunCheckIn(
    val status: CheckInStatus = CheckInStatus.NOT_PROMPTED,
    val timestampMillis: Long = System.currentTimeMillis(),
    val sleepQuality: ReadinessLevel? = null,
    val disposition: ReadinessLevel? = null,
    val restingHrBpm: Int? = null,
    val localizedPains: List<LocalizedPainEntry> = emptyList()
)

// --- 3. AUDITORIA DE EXECUÇÃO (PLANEJADO VS. EXECUTADO + MOTIVOS DE QUEBRA) ---
enum class InterruptionReason(val label: String) {
    CARDIO_RESPIRATORY_FATIGUE("Falta de fôlego / condicionamento"),
    MUSCULAR_FATIGUE_OR_PAIN("Fadiga ou dor muscular localizada"),
    GI_DISTRESS("Desconforto gastrointestinal / cólica"),
    HEAT_HUMIDITY_STRESS("Calor / umidade excessiva"),
    EXTERNAL_STOP("Pausa externa (semáforo / obstáculo)"),
    EQUIPMENT_OR_SENSOR("Ajuste de tênis / sensor / GPS")
}

data class SessionExecutionAudit(
    val prescribedStructureLabel: String, // Ex: "5x (3 min Corrida + 1 min Caminhada)"
    val plannedWorkDurationSec: Int,      // Ex: 900s (15 min de corrida prescrita)
    val actualWorkRunningSec: Int,        // Ex: 720s (12 min realmente corridos)
    val unplannedWalkDurationSec: Int,    // Tempo caminhando onde deveria correr
    val interruptedIntervalsCount: Int = 0,
    val reportedDeviationReasons: List<InterruptionReason> = emptyList(),
    val athleteDeviationNotes: String? = null
)

fun calculateSrpeLoadUa(durationSec: Int, rpeCr10: Int?): Int? {
    if (rpeCr10 == null || durationSec <= 0) return null
    return ((durationSec / 60.0) * rpeCr10.coerceIn(0, 10)).roundToInt()
}

fun borgCr10Label(rpeCr10: Int): String = when (rpeCr10.coerceIn(0, 10)) {
    0 -> "0 • Repouso"
    1, 2 -> "$rpeCr10 • Muito leve"
    3, 4 -> "$rpeCr10 • Leve a moderado"
    5, 6 -> "$rpeCr10 • Forte"
    7, 8 -> "$rpeCr10 • Muito forte"
    9 -> "9 • Quase máximo"
    else -> "10 • Máximo"
}

fun resolveHrMetricProvenance(
    connectedDeviceName: String?,
    hasHrData: Boolean
): MetricProvenance? {
    if (!hasHrData) return null
    val dev = connectedDeviceName?.trim()?.takeIf { it.isNotBlank() }
    val lower = dev?.lowercase(Locale.ROOT).orEmpty()
    val isChestStrap = lower.contains("polar") ||
        lower.contains("h10") ||
        lower.contains("h9") ||
        lower.contains("h7") ||
        lower.contains("hrm") ||
        lower.contains("cinta") ||
        lower.contains("chest") ||
        lower.contains("magene") ||
        lower.contains("coospo") ||
        lower.contains("wahoo") ||
        lower.contains("tickr")
    return if (isChestStrap) {
        MetricProvenance(
            source = DataSourceType.CHEST_STRAP_BLE,
            deviceName = dev,
            confidence = DataConfidenceLevel.HIGH
        )
    } else {
        MetricProvenance(
            source = DataSourceType.OPTICAL_PPG_WATCH,
            deviceName = dev ?: "Smartwatch BLE (PPG)",
            confidence = DataConfidenceLevel.MODERATE
        )
    }
}

fun MetricProvenance.toJsonString(): String {
    val obj = org.json.JSONObject()
    obj.put("source", source.name)
    if (!deviceName.isNullOrBlank()) obj.put("deviceName", deviceName)
    obj.put("confidence", confidence.name)
    return obj.toString()
}

fun String?.toMetricProvenance(): MetricProvenance? {
    if (this.isNullOrBlank()) return null
    return try {
        val obj = org.json.JSONObject(this)
        val source = runCatching {
            DataSourceType.valueOf(obj.optString("source", DataSourceType.OPTICAL_PPG_WATCH.name))
        }.getOrDefault(DataSourceType.OPTICAL_PPG_WATCH)
        val confidence = runCatching {
            DataConfidenceLevel.valueOf(obj.optString("confidence", DataConfidenceLevel.MODERATE.name))
        }.getOrDefault(DataConfidenceLevel.MODERATE)
        val deviceName = obj.optString("deviceName", "").takeIf { it.isNotBlank() && it != "null" }
        MetricProvenance(
            source = source,
            deviceName = deviceName,
            confidence = confidence
        )
    } catch (_: Exception) {
        null
    }
}

fun PreRunCheckIn.toJsonString(): String {
    val obj = org.json.JSONObject()
    obj.put("status", status.name)
    obj.put("timestampMillis", timestampMillis)
    sleepQuality?.let { obj.put("sleepQuality", it.name) }
    disposition?.let { obj.put("disposition", it.name) }
    restingHrBpm?.let { obj.put("restingHrBpm", it) }
    val painsArr = org.json.JSONArray()
    localizedPains.forEach { pain ->
        val pObj = org.json.JSONObject()
        pObj.put("region", pain.region.name)
        pObj.put("intensityCr10", pain.intensityCr10)
        pObj.put("altersGaitMechanics", pain.altersGaitMechanics)
        painsArr.put(pObj)
    }
    obj.put("localizedPains", painsArr)
    return obj.toString()
}

fun String?.toPreRunCheckIn(): PreRunCheckIn? {
    if (this.isNullOrBlank()) return null
    return try {
        val obj = org.json.JSONObject(this)
        val status = runCatching {
            CheckInStatus.valueOf(obj.optString("status", CheckInStatus.NOT_PROMPTED.name))
        }.getOrDefault(CheckInStatus.NOT_PROMPTED)
        val timestampMillis = obj.optLong("timestampMillis", 0L)
        val sleepQuality = obj.optString("sleepQuality", "").takeIf { it.isNotBlank() && it != "null" }?.let {
            runCatching { ReadinessLevel.valueOf(it) }.getOrNull()
        }
        val disposition = obj.optString("disposition", "").takeIf { it.isNotBlank() && it != "null" }?.let {
            runCatching { ReadinessLevel.valueOf(it) }.getOrNull()
        }
        val restingHrBpm = if (obj.has("restingHrBpm") && !obj.isNull("restingHrBpm")) {
            obj.optInt("restingHrBpm", 0).takeIf { it > 0 }
        } else null
        val painsList = mutableListOf<LocalizedPainEntry>()
        val painsArr = obj.optJSONArray("localizedPains")
        if (painsArr != null) {
            for (i in 0 until painsArr.length()) {
                val pObj = painsArr.optJSONObject(i) ?: continue
                val region = runCatching {
                    PainBodyRegion.valueOf(pObj.optString("region", PainBodyRegion.OTHER.name))
                }.getOrDefault(PainBodyRegion.OTHER)
                val intensity = pObj.optInt("intensityCr10", 3).coerceIn(1, 10)
                val altersGait = pObj.optBoolean("altersGaitMechanics", false)
                painsList.add(
                    LocalizedPainEntry(
                        region = region,
                        intensityCr10 = intensity,
                        altersGaitMechanics = altersGait
                    )
                )
            }
        }
        PreRunCheckIn(
            status = status,
            timestampMillis = timestampMillis,
            sleepQuality = sleepQuality,
            disposition = disposition,
            restingHrBpm = restingHrBpm,
            localizedPains = painsList
        )
    } catch (_: Exception) {
        null
    }
}

fun SessionExecutionAudit.toJsonString(): String {
    val obj = org.json.JSONObject()
    obj.put("prescribedStructureLabel", prescribedStructureLabel)
    obj.put("plannedWorkDurationSec", plannedWorkDurationSec)
    obj.put("actualWorkRunningSec", actualWorkRunningSec)
    obj.put("unplannedWalkDurationSec", unplannedWalkDurationSec)
    obj.put("interruptedIntervalsCount", interruptedIntervalsCount)
    val reasonsArr = org.json.JSONArray()
    reportedDeviationReasons.forEach { reason ->
        reasonsArr.put(reason.name)
    }
    obj.put("reportedDeviationReasons", reasonsArr)
    if (!athleteDeviationNotes.isNullOrBlank()) {
        obj.put("athleteDeviationNotes", athleteDeviationNotes)
    }
    return obj.toString()
}

fun String?.toSessionExecutionAudit(): SessionExecutionAudit? {
    if (this.isNullOrBlank()) return null
    return try {
        val obj = org.json.JSONObject(this)
        val reasons = mutableListOf<InterruptionReason>()
        val reasonsArr = obj.optJSONArray("reportedDeviationReasons")
        if (reasonsArr != null) {
            for (i in 0 until reasonsArr.length()) {
                val raw = reasonsArr.optString(i, "")
                val parsed = runCatching { InterruptionReason.valueOf(raw) }.getOrNull()
                if (parsed != null) reasons.add(parsed)
            }
        }
        SessionExecutionAudit(
            prescribedStructureLabel = obj.optString("prescribedStructureLabel", "Sessão de Corrida"),
            plannedWorkDurationSec = obj.optInt("plannedWorkDurationSec", 0).coerceAtLeast(0),
            actualWorkRunningSec = obj.optInt("actualWorkRunningSec", 0).coerceAtLeast(0),
            unplannedWalkDurationSec = obj.optInt("unplannedWalkDurationSec", 0).coerceAtLeast(0),
            interruptedIntervalsCount = obj.optInt("interruptedIntervalsCount", 0).coerceAtLeast(0),
            reportedDeviationReasons = reasons,
            athleteDeviationNotes = obj.optString("athleteDeviationNotes", "").takeIf { it.isNotBlank() && it != "null" }
        )
    } catch (_: Exception) {
        null
    }
}

// --- 4. MOTOR DE INTERVALOS POR BLOCO (INTERVAL ENGINE) E MÉTRICAS AVANÇADAS (PACOTE 2) ---
enum class IntervalBlockType(val label: String) {
    WARMUP("Aquecimento"),
    RUN_WORK("Tiro / Estímulo"),
    RECOVERY_WALK("Recuperação (Caminhada)"),
    RECOVERY_JOG("Recuperação (Trote)"),
    COOLDOWN("Desaquecimento"),
    UNSTRUCTURED("Livre")
}

data class ExecutedInterval(
    val blockIndex: Int,
    val blockLabel: String, // Ex: "Tiro 1", "Recuperação 1", "Tiro 2"
    val type: IntervalBlockType,
    val plannedDurationSec: Int,
    val actualDurationSec: Int,
    val distanceMeters: Double,
    val avgPaceSecPerKm: Int,
    val avgHrBpm: Int? = null,
    val maxHrBpm: Int? = null,
    val endHrBpm: Int? = null, // Média dos últimos 10s do bloco
    val hrRecoveryDeltaBpm: Int? = null, // Nos blocos RECOVERY: (endHr do tiro anterior - endHr desta recuperação)
    val avgCadenceSpm: Int? = null,
    val unintendedWalkSec: Int = 0, // Caminhada detectada dentro de um bloco RUN_WORK
    val wasInterrupted: Boolean = false
)

data class AdvancedRunningMetrics(
    // Eficiência Cardíaca (EF = Velocidade m/min ÷ FC Média Correndo)
    val efficiencyFactorMeterPerBeat: Double? = null,
    // Deriva Cardíaca (Pa:Hr %) — APENAS se elegível pelo Gatekeeper
    val cardiacDriftPct: Double? = null,
    val isCardiacDriftApplicable: Boolean = false,
    val cardiacDriftGatekeeperNote: String? = null, // Ex: "Não aplicável a treino intervalado"
    // Consistência dos Tiros (apenas blocos RUN_WORK)
    val workIntervalsCount: Int = 0,
    val fastestWorkPaceSecPerKm: Int? = null,
    val slowestWorkPaceSecPerKm: Int? = null,
    val workSpeedDropFirstToLastPct: Double? = null, // Queda % do 1º para o último tiro
    val workSpeedSpreadMaxMinPct: Double? = null,    // Amplitude % entre o tiro mais rápido e o mais lento
    val avgHrRecoveryBetweenWorkBpm: Int? = null,    // Queda média de FC nos intervalos de recuperação
    // Contexto Ambiental Opcional
    val ambientTempCelsius: Double? = null,
    val ambientHumidityPct: Int? = null,
    // Sincronização Oficial do Relógio (Mi Fitness / Smartwatch)
    val watchSyncedSteps: Int? = null,
    val watchSyncedCadenceSpm: Int? = null,
    val watchSyncedStrideCm: Int? = null,
    val watchSyncedActiveKcal: Int? = null,
    val watchSyncedTotalKcal: Int? = null
)

fun List<ExecutedInterval>.toExecutedIntervalsJsonString(): String {
    val arr = org.json.JSONArray()
    for (item in this) {
        val obj = org.json.JSONObject()
        obj.put("blockIndex", item.blockIndex)
        obj.put("blockLabel", item.blockLabel)
        obj.put("type", item.type.name)
        obj.put("plannedDurationSec", item.plannedDurationSec)
        obj.put("actualDurationSec", item.actualDurationSec)
        obj.put("distanceMeters", item.distanceMeters)
        obj.put("avgPaceSecPerKm", item.avgPaceSecPerKm)
        item.avgHrBpm?.let { obj.put("avgHrBpm", it) }
        item.maxHrBpm?.let { obj.put("maxHrBpm", it) }
        item.endHrBpm?.let { obj.put("endHrBpm", it) }
        item.hrRecoveryDeltaBpm?.let { obj.put("hrRecoveryDeltaBpm", it) }
        item.avgCadenceSpm?.let { obj.put("avgCadenceSpm", it) }
        obj.put("unintendedWalkSec", item.unintendedWalkSec)
        obj.put("wasInterrupted", item.wasInterrupted)
        arr.put(obj)
    }
    return arr.toString()
}

fun String?.toExecutedIntervals(): List<ExecutedInterval> {
    if (this.isNullOrBlank()) return emptyList()
    return try {
        val arr = org.json.JSONArray(this)
        val list = mutableListOf<ExecutedInterval>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val type = runCatching {
                IntervalBlockType.valueOf(obj.optString("type", IntervalBlockType.UNSTRUCTURED.name))
            }.getOrDefault(IntervalBlockType.UNSTRUCTURED)
            list.add(
                ExecutedInterval(
                    blockIndex = obj.optInt("blockIndex", i),
                    blockLabel = obj.optString("blockLabel", "Bloco ${i + 1}"),
                    type = type,
                    plannedDurationSec = obj.optInt("plannedDurationSec", 0).coerceAtLeast(0),
                    actualDurationSec = obj.optInt("actualDurationSec", 0).coerceAtLeast(0),
                    distanceMeters = obj.optDouble("distanceMeters", 0.0).coerceAtLeast(0.0),
                    avgPaceSecPerKm = obj.optInt("avgPaceSecPerKm", 0).coerceAtLeast(0),
                    avgHrBpm = if (obj.has("avgHrBpm") && !obj.isNull("avgHrBpm")) obj.optInt("avgHrBpm").takeIf { it > 0 } else null,
                    maxHrBpm = if (obj.has("maxHrBpm") && !obj.isNull("maxHrBpm")) obj.optInt("maxHrBpm").takeIf { it > 0 } else null,
                    endHrBpm = if (obj.has("endHrBpm") && !obj.isNull("endHrBpm")) obj.optInt("endHrBpm").takeIf { it > 0 } else null,
                    hrRecoveryDeltaBpm = if (obj.has("hrRecoveryDeltaBpm") && !obj.isNull("hrRecoveryDeltaBpm")) obj.optInt("hrRecoveryDeltaBpm") else null,
                    avgCadenceSpm = if (obj.has("avgCadenceSpm") && !obj.isNull("avgCadenceSpm")) obj.optInt("avgCadenceSpm").takeIf { it > 0 } else null,
                    unintendedWalkSec = obj.optInt("unintendedWalkSec", 0).coerceAtLeast(0),
                    wasInterrupted = obj.optBoolean("wasInterrupted", false)
                )
            )
        }
        list
    } catch (_: Exception) {
        emptyList()
    }
}

fun AdvancedRunningMetrics.toJsonString(): String {
    val obj = org.json.JSONObject()
    efficiencyFactorMeterPerBeat?.let { obj.put("efficiencyFactorMeterPerBeat", it) }
    cardiacDriftPct?.let { obj.put("cardiacDriftPct", it) }
    obj.put("isCardiacDriftApplicable", isCardiacDriftApplicable)
    if (!cardiacDriftGatekeeperNote.isNullOrBlank()) {
        obj.put("cardiacDriftGatekeeperNote", cardiacDriftGatekeeperNote)
    }
    obj.put("workIntervalsCount", workIntervalsCount)
    fastestWorkPaceSecPerKm?.let { obj.put("fastestWorkPaceSecPerKm", it) }
    slowestWorkPaceSecPerKm?.let { obj.put("slowestWorkPaceSecPerKm", it) }
    workSpeedDropFirstToLastPct?.let { obj.put("workSpeedDropFirstToLastPct", it) }
    workSpeedSpreadMaxMinPct?.let { obj.put("workSpeedSpreadMaxMinPct", it) }
    avgHrRecoveryBetweenWorkBpm?.let { obj.put("avgHrRecoveryBetweenWorkBpm", it) }
    ambientTempCelsius?.let { obj.put("ambientTempCelsius", it) }
    ambientHumidityPct?.let { obj.put("ambientHumidityPct", it) }
    watchSyncedSteps?.let { obj.put("watchSyncedSteps", it) }
    watchSyncedCadenceSpm?.let { obj.put("watchSyncedCadenceSpm", it) }
    watchSyncedStrideCm?.let { obj.put("watchSyncedStrideCm", it) }
    watchSyncedActiveKcal?.let { obj.put("watchSyncedActiveKcal", it) }
    watchSyncedTotalKcal?.let { obj.put("watchSyncedTotalKcal", it) }
    return obj.toString()
}

fun String?.toAdvancedRunningMetrics(): AdvancedRunningMetrics? {
    if (this.isNullOrBlank()) return null
    return try {
        val obj = org.json.JSONObject(this)
        AdvancedRunningMetrics(
            efficiencyFactorMeterPerBeat = if (obj.has("efficiencyFactorMeterPerBeat") && !obj.isNull("efficiencyFactorMeterPerBeat")) obj.optDouble("efficiencyFactorMeterPerBeat") else null,
            cardiacDriftPct = if (obj.has("cardiacDriftPct") && !obj.isNull("cardiacDriftPct")) obj.optDouble("cardiacDriftPct") else null,
            isCardiacDriftApplicable = obj.optBoolean("isCardiacDriftApplicable", false),
            cardiacDriftGatekeeperNote = obj.optString("cardiacDriftGatekeeperNote", "").takeIf { it.isNotBlank() && it != "null" },
            workIntervalsCount = obj.optInt("workIntervalsCount", 0).coerceAtLeast(0),
            fastestWorkPaceSecPerKm = if (obj.has("fastestWorkPaceSecPerKm") && !obj.isNull("fastestWorkPaceSecPerKm")) obj.optInt("fastestWorkPaceSecPerKm").takeIf { it > 0 } else null,
            slowestWorkPaceSecPerKm = if (obj.has("slowestWorkPaceSecPerKm") && !obj.isNull("slowestWorkPaceSecPerKm")) obj.optInt("slowestWorkPaceSecPerKm").takeIf { it > 0 } else null,
            workSpeedDropFirstToLastPct = if (obj.has("workSpeedDropFirstToLastPct") && !obj.isNull("workSpeedDropFirstToLastPct")) obj.optDouble("workSpeedDropFirstToLastPct") else null,
            workSpeedSpreadMaxMinPct = if (obj.has("workSpeedSpreadMaxMinPct") && !obj.isNull("workSpeedSpreadMaxMinPct")) obj.optDouble("workSpeedSpreadMaxMinPct") else null,
            avgHrRecoveryBetweenWorkBpm = if (obj.has("avgHrRecoveryBetweenWorkBpm") && !obj.isNull("avgHrRecoveryBetweenWorkBpm")) obj.optInt("avgHrRecoveryBetweenWorkBpm") else null,
            ambientTempCelsius = if (obj.has("ambientTempCelsius") && !obj.isNull("ambientTempCelsius")) obj.optDouble("ambientTempCelsius") else null,
            ambientHumidityPct = if (obj.has("ambientHumidityPct") && !obj.isNull("ambientHumidityPct")) obj.optInt("ambientHumidityPct") else null,
            watchSyncedSteps = if (obj.has("watchSyncedSteps") && !obj.isNull("watchSyncedSteps")) obj.optInt("watchSyncedSteps").takeIf { it > 0 } else null,
            watchSyncedCadenceSpm = if (obj.has("watchSyncedCadenceSpm") && !obj.isNull("watchSyncedCadenceSpm")) obj.optInt("watchSyncedCadenceSpm").takeIf { it > 0 } else null,
            watchSyncedStrideCm = if (obj.has("watchSyncedStrideCm") && !obj.isNull("watchSyncedStrideCm")) obj.optInt("watchSyncedStrideCm").takeIf { it > 0 } else null,
            watchSyncedActiveKcal = if (obj.has("watchSyncedActiveKcal") && !obj.isNull("watchSyncedActiveKcal")) obj.optInt("watchSyncedActiveKcal").takeIf { it > 0 } else null,
            watchSyncedTotalKcal = if (obj.has("watchSyncedTotalKcal") && !obj.isNull("watchSyncedTotalKcal")) obj.optInt("watchSyncedTotalKcal").takeIf { it > 0 } else null
        )
    } catch (_: Exception) {
        null
    }
}

/**
 * Filtro Multivariado de Caminhada Não-Intencional (Anti-Oscilação de GPS):
 * Dentro de um bloco de corrida (`RUN_WORK`), só contabiliza `unintendedWalkSec` quando:
 * - Com cadência disponível: por no mínimo 8 segundos consecutivos, velocidade média móvel < 1.95 m/s (pace > 8'32"/km)
 *   E cadência < 138 spm (ou queda > 18% da cadência média de corrida da sessão).
 * - Sem cadência disponível no momento: exige janela sustentada de 15 segundos com velocidade < 1.85 m/s.
 */
fun detectMultivariateUnintendedWalkSec(
    speedSamplesMps: List<Double>,
    cadenceSamplesSpm: List<Int?> = emptyList(),
    sampleIntervalSec: Int = 1,
    sessionAvgRunCadenceSpm: Int? = null
): Int {
    if (speedSamplesMps.isEmpty() || sampleIntervalSec <= 0) return 0
    val stepSec = sampleIntervalSec.coerceAtLeast(1)
    val cadenceDropThreshold = sessionAvgRunCadenceSpm?.takeIf { it > 140 }?.let { (it * 0.82).toInt() } ?: 138
    val speedsWindow = ArrayDeque<Double>()
    var consecutiveLowWithCadenceSec = 0
    var consecutiveLowNoCadenceSec = 0
    var totalUnintendedWalkSec = 0
    var alreadyCountedCurrentWithCadenceStreak = false
    var alreadyCountedCurrentNoCadenceStreak = false

    for (i in speedSamplesMps.indices) {
        val rawSpeed = speedSamplesMps[i]
        speedsWindow.addLast(rawSpeed)
        if (speedsWindow.size > (8 / stepSec).coerceAtLeast(2)) {
            speedsWindow.removeFirst()
        }
        val rollingAvgSpeed = speedsWindow.average()
        val cad = cadenceSamplesSpm.getOrNull(i)?.takeIf { it > 0 }

        if (cad != null) {
            consecutiveLowNoCadenceSec = 0
            alreadyCountedCurrentNoCadenceStreak = false
            val isCadenceWalk = cad < 138 || cad < cadenceDropThreshold
            if (rollingAvgSpeed < 1.95 && isCadenceWalk) {
                consecutiveLowWithCadenceSec += stepSec
                if (consecutiveLowWithCadenceSec >= 8) {
                    if (!alreadyCountedCurrentWithCadenceStreak) {
                        totalUnintendedWalkSec += consecutiveLowWithCadenceSec
                        alreadyCountedCurrentWithCadenceStreak = true
                    } else {
                        totalUnintendedWalkSec += stepSec
                    }
                }
            } else {
                consecutiveLowWithCadenceSec = 0
                alreadyCountedCurrentWithCadenceStreak = false
            }
        } else {
            consecutiveLowWithCadenceSec = 0
            alreadyCountedCurrentWithCadenceStreak = false
            if (rollingAvgSpeed < 1.85) {
                consecutiveLowNoCadenceSec += stepSec
                if (consecutiveLowNoCadenceSec >= 15) {
                    if (!alreadyCountedCurrentNoCadenceStreak) {
                        totalUnintendedWalkSec += consecutiveLowNoCadenceSec
                        alreadyCountedCurrentNoCadenceStreak = true
                    } else {
                        totalUnintendedWalkSec += stepSec
                    }
                }
            } else {
                consecutiveLowNoCadenceSec = 0
                alreadyCountedCurrentNoCadenceStreak = false
            }
        }
    }
    return totalUnintendedWalkSec
}

/**
 * Reconstrói ou retorna os blocos executados (`ExecutedInterval`) de uma sessão de corrida,
 * calculando `endHrBpm` (média dos últimos 10s do bloco) e `hrRecoveryDeltaBpm`
 * (`previousWorkBlock.endHrBpm - currentRecoveryBlock.endHrBpm`) nos blocos de recuperação.
 */
fun buildExecutedIntervalsForSession(
    session: RunningSession,
    gpsPoints: List<GpsTrackPoint> = emptyList(),
    liveBlockUnintendedWalkSec: Map<Int, Int> = emptyMap()
): List<ExecutedInterval> {
    if (session.executedIntervals.isNotEmpty()) {
        // Garante que hrRecoveryDeltaBpm esteja preenchido caso haja endHrBpm
        val updated = mutableListOf<ExecutedInterval>()
        var lastWorkEndHr: Int? = null
        for (item in session.executedIntervals) {
            if (item.type == IntervalBlockType.RUN_WORK) {
                lastWorkEndHr = item.endHrBpm ?: item.maxHrBpm ?: item.avgHrBpm
                updated.add(item)
            } else if (item.type == IntervalBlockType.RECOVERY_WALK || item.type == IntervalBlockType.RECOVERY_JOG) {
                val recEnd = item.endHrBpm ?: item.avgHrBpm
                val computedDelta = if (item.hrRecoveryDeltaBpm != null) {
                    item.hrRecoveryDeltaBpm
                } else if (lastWorkEndHr != null && recEnd != null && lastWorkEndHr > 0 && recEnd > 0) {
                    (lastWorkEndHr - recEnd).coerceAtLeast(0)
                } else null
                updated.add(item.copy(hrRecoveryDeltaBpm = computedDelta))
            } else {
                updated.add(item)
            }
        }
        return updated
    }

    val analytics = calculateSprintAnalytics(session, gpsPoints)
    val blockSplits = buildSplitsForMode(session, SplitSelectorMode.BY_BLOCK, gpsPoints)
    if (blockSplits.isEmpty()) return emptyList()

    val plan = if (session.isGentleMode) {
        RunningProgramManager.getGentleWalkPlan()
    } else {
        RunningProgramManager.getPlanForWeek(
            session.weekNumber.coerceAtLeast(1),
            session.dayNumber.coerceAtLeast(1)
        )
    }
    val middlePlanBlocks = plan.blocks.filter {
        it.type == RunningBlockType.RUN || it.type == RunningBlockType.WALK
    }.ifEmpty { plan.blocks }

    val recordedHrList = session.hrTimeline.toHrTimelineList().filter { it in 55..225 }
    val hasRealHr = (session.avgHr != null && session.avgHr > 0) || recordedHrList.isNotEmpty()
    val totalBlockSec = blockSplits.sumOf { it.durationSec }.coerceAtLeast(1)
    val baseRunCadence = analytics.avgCadenceSpm.takeIf { it in 140..205 } ?: 162

    var accSec = 0
    var runNum = 0
    var recNum = 0
    var previousWorkEndHr: Int? = null
    val routePts = session.routePointsJson.toRoutePointItems()

    return blockSplits.mapIndexed { idx, split ->
        val isRunBlock = split.phaseTag == "RUN" || split.phaseTag == "PR"
        val blockType = when {
            analytics.isContinuousRunMode && blockSplits.size == 1 -> IntervalBlockType.UNSTRUCTURED
            isRunBlock -> IntervalBlockType.RUN_WORK
            else -> IntervalBlockType.RECOVERY_WALK
        }
        val plannedSec = middlePlanBlocks.getOrNull(idx)?.durationSec ?: split.durationSec
        val actualSec = split.durationSec.coerceAtLeast(1)

        val startFrac = accSec.toDouble() / totalBlockSec.toDouble()
        accSec += actualSec
        val endFrac = accSec.toDouble() / totalBlockSec.toDouble()

        val cleanLabel = when (blockType) {
            IntervalBlockType.RUN_WORK -> {
                runNum++
                "Tiro $runNum"
            }
            IntervalBlockType.RECOVERY_WALK, IntervalBlockType.RECOVERY_JOG -> {
                recNum++
                "Rec $recNum"
            }
            IntervalBlockType.UNSTRUCTURED -> {
                runNum++
                "Bloco Contínuo $runNum"
            }
            else -> split.splitLabel.replace("★", "").trim()
        }

        // Cálculo de avgHrBpm, maxHrBpm e endHrBpm (média dos últimos 10s do bloco)
        var blockAvgHr: Int? = null
        var blockMaxHr: Int? = null
        var blockEndHr: Int? = null

        if (hasRealHr) {
            if (recordedHrList.size >= 4) {
                val i0 = (startFrac * (recordedHrList.size - 1)).safeRoundToInt().safeCoerceIn(0, recordedHrList.lastIndex)
                val i1 = (endFrac * (recordedHrList.size - 1)).safeRoundToInt().safeCoerceIn(i0, recordedHrList.lastIndex)
                val sub = recordedHrList.subList(i0, i1 + 1)
                if (sub.isNotEmpty()) {
                    val tailCount = maxOf(1, ((10.0 / actualSec.coerceAtLeast(10)) * sub.size).safeRoundToInt())
                    val last10sSamples = sub.takeLast(tailCount).ifEmpty { sub }
                    val splitEndRef = split.avgHr ?: sub.average().safeRoundToInt(140)
                    if (isRunBlock) {
                        blockEndHr = maxOf(last10sSamples.average().safeRoundToInt(splitEndRef), splitEndRef)
                        blockAvgHr = ((sub.average() * 0.65) + (blockEndHr * 0.35)).safeRoundToInt(blockEndHr).coerceAtMost(blockEndHr)
                        blockMaxHr = maxOf(sub.maxOrNull() ?: blockEndHr, blockEndHr)
                    } else {
                        blockEndHr = minOf(last10sSamples.average().safeRoundToInt(splitEndRef), splitEndRef)
                        blockAvgHr = ((sub.average() * 0.6) + (blockEndHr * 0.4)).safeRoundToInt(blockEndHr).coerceAtLeast(blockEndHr)
                        blockMaxHr = sub.maxOrNull() ?: (blockAvgHr + 6)
                    }
                }
            } else if (split.avgHr != null && split.avgHr > 0) {
                blockEndHr = split.avgHr
                blockAvgHr = if (isRunBlock) (split.avgHr - 5).coerceAtLeast(95) else (split.avgHr + 6)
                blockMaxHr = if (isRunBlock) (split.avgHr + 3).coerceAtMost((session.maxHr ?: 210).coerceAtLeast(split.avgHr)) else (split.avgHr + 10)
            }
        }

        val hrDelta: Int? = if (!isRunBlock && previousWorkEndHr != null && blockEndHr != null) {
            (previousWorkEndHr!! - blockEndHr).coerceAtLeast(0)
        } else {
            null
        }
        if (isRunBlock && blockEndHr != null) {
            previousWorkEndHr = blockEndHr
        }

        val blockCadence: Int = if (isRunBlock) {
            val paceFactor = ((340 - split.paceSecPerKm.coerceIn(210, 500)) / 18.0).roundToInt()
            (baseRunCadence + paceFactor).coerceIn(146, 194)
        } else {
            116
        }

        // Detecção multivariada de caminhada não-intencional no bloco RUN_WORK
        val blockPtsSpeeds = routePts.filter { it.blockIndex == idx && it.speed != null && it.speed > 0f }
            .mapNotNull { it.speed?.toDouble() }
        val detectedUnintendedWalk = when {
            !isRunBlock -> 0
            liveBlockUnintendedWalkSec.containsKey(idx) -> liveBlockUnintendedWalkSec[idx] ?: 0
            blockPtsSpeeds.size >= 8 -> detectMultivariateUnintendedWalkSec(
                speedSamplesMps = blockPtsSpeeds,
                sampleIntervalSec = (actualSec / blockPtsSpeeds.size.coerceAtLeast(1)).coerceIn(1, 5),
                sessionAvgRunCadenceSpm = baseRunCadence
            )
            else -> 0
        }

        ExecutedInterval(
            blockIndex = idx + 1,
            blockLabel = cleanLabel,
            type = blockType,
            plannedDurationSec = plannedSec,
            actualDurationSec = actualSec,
            distanceMeters = split.distanceMeters.toDouble(),
            avgPaceSecPerKm = split.paceSecPerKm,
            avgHrBpm = blockAvgHr,
            maxHrBpm = blockMaxHr,
            endHrBpm = blockEndHr,
            hrRecoveryDeltaBpm = hrDelta,
            avgCadenceSpm = blockCadence,
            unintendedWalkSec = detectedUnintendedWalk,
            wasInterrupted = detectedUnintendedWalk >= 8 || actualSec < (plannedSec * 0.85).toInt()
        )
    }
}

data class RecordedKmSplit(
    val splitNumber: Int,
    val splitLabel: String,
    val distanceMeters: Int,
    val durationSec: Int,
    val paceSecPerKm: Int,
    val avgHr: Int? = null,
    val phaseTag: String? = null // "RUN", "WALK", "PR", "WARMUP", "COOL_DOWN"
)

data class RoutePointItem(
    val lat: Double,
    val lon: Double,
    val timestamp: Long = 0L,
    val speed: Float? = null,
    val altitude: Double? = null,
    val accuracy: Float? = null,
    val phase: String = "RUN", // "WARMUP", "WALK", "RUN", "COOL_DOWN", "PR"
    val blockIndex: Int = 0,
    val isPrSegment: Boolean = false
)

enum class SplitSelectorMode(val displayLabel: String, val reportHeaderTitle: String) {
    BY_BLOCK("Por Bloco/Sprint", "SPLITS E PARCIAIS (POR BLOCO / SPRINT)"),
    HALF_KM("500 m", "SPLITS E PARCIAIS (A CADA 500 M)"),
    ONE_KM_MAIN("1 km (Principal)", "SPLITS E PARCIAIS (1 KM • TREINO PRINCIPAL)");

    val title: String get() = displayLabel
}

enum class RunHudFaceStyle(
    val displayTitle: String,
    val shortBadge: String,
    val subtitle: String
) {
    CLEAN_PRO(
        displayTitle = "Clean Pro • Padrão",
        shortBadge = "CLEAN PRO",
        subtitle = "Tipografia monoespaçada moderna de alta precisão e troca instantânea de dígitos."
    ),
    ODOMETER_90S(
        displayTitle = "Odômetro Mecânico • 90s Roll",
        shortBadge = "90S ROLL",
        subtitle = "Tambores mecânicos individuais que giram verticalmente a cada mudança de dígito."
    ),
    RETRO_CHRONO(
        displayTitle = "Retro Chrono • Display LCD",
        shortBadge = "RETRO LCD",
        subtitle = "Cronógrafo digital esportivo dos anos 90 com dígitos em verde-fósforo e âmbar sobre matriz escura."
    );

    companion object {
        fun fromName(raw: String?): RunHudFaceStyle {
            return entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: CLEAN_PRO
        }
    }
}

data class SprintAnalytics(
    val structureSummary: String,
    val headerIdentification: String,
    val mainDistMeters: Int,
    val mainDurationSec: Int,
    val mainPaceSecPerKm: Int,
    val totalDistMeters: Int,
    val totalDurationSec: Int,
    val totalPaceSecPerKm: Int,
    val avgSprintPaceSecPerKm: Int,
    val bestSprintPaceSecPerKm: Int,
    val runningTimeSec: Int,
    val walkingTimeSec: Int,
    val avgCadenceSpm: Int,
    val elevationGainMeters: Int,
    val isContinuousRunMode: Boolean = false
) {
    val avgSprintPaceText: String
        get() = if (avgSprintPaceSecPerKm > 0) {
            String.format(Locale.US, "%d'%02d\"", avgSprintPaceSecPerKm / 60, avgSprintPaceSecPerKm % 60)
        } else "--:--"

    val primaryPaceLabel: String
        get() = if (isContinuousRunMode) "Pace Médio nas Parciais" else "Pace nos Tiros"

    val bestSplitLabel: String
        get() = if (isContinuousRunMode) "Melhor Parcial (Split)" else "Melhor Tiro"

    val consistencyOrRecoveryLabel: String
        get() = if (isContinuousRunMode) "Consistência entre Parciais" else "Recuperação entre Tiros"
}

fun RunningSession.isSimulatedOrTestSession(): Boolean {
    return id.startsWith("demo", ignoreCase = true) ||
            id.startsWith("sim", ignoreCase = true) ||
            id.startsWith("test", ignoreCase = true)
}

/**
 * Desduplica definitivamente sessões de corrida no mesmo dia com distância/duração equivalentes
 * (ex: a corrida real de 30/10/2026 de 1,89 km • 12:06 salva no aparelho com week=0 vs seed com week=1).
 * Prioriza o registro que possua telemetria cardíaca completa (160/176 bpm), GPS e marcação de Corrida Livre.
 * Retorna Pair(listaDesduplicadaOrdenadaPorDataDesc, listaDeIdsDuplicadosParaRemoverDoBanco).
 */
fun deduplicateRunningSessions(sessions: List<RunningSession>): Pair<List<RunningSession>, List<String>> {
    if (sessions.isEmpty()) return emptyList<RunningSession>() to emptyList()
    val dayFmt = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US)

    fun sessionQualityScore(s: RunningSession): Int {
        var score = 0
        if (s.avgHr != null && s.avgHr > 0) score += 20
        if (s.maxHr != null && s.maxHr > 0) score += 20
        if (!s.hrTimeline.isNullOrBlank()) score += 15
        if (!s.routePointsJson.isNullOrBlank()) score += 25
        if (!s.kmSplitsJson.isNullOrBlank()) score += 10
        if (!s.aiCoachReport.isNullOrBlank()) score += 10
        if (!s.shoeName.isNullOrBlank()) score += 5
                if (s.executionAudit != null || s.preRunCheckIn != null || s.sRpeLoadUa != null) score += 12
                if (s.executedIntervals.isNotEmpty() || s.advancedMetrics != null) score += 12
                if (s.sessionType.equals("FREE_RUN", ignoreCase = true)) score += 8
                if (s.id != "run_seed_2026_10_30_free_run") score += 4 // Prefere o ID original gravado no aparelho se empatar
                return score
            }

            fun is30OctFreeRunSignature(s: RunningSession): Boolean {
                return s.id == "run_seed_2026_10_30_free_run"
            }

            fun areDuplicates(a: RunningSession, b: RunningSession): Boolean {
                if (a.id == b.id) return true
                val withinTwoMinutes = kotlin.math.abs(a.date - b.date) <= 120_000L
                if (!withinTwoMinutes) return false
                val sameDur = kotlin.math.abs(a.durationSec - b.durationSec) <= 5
                val sameDist = kotlin.math.abs(a.distanceMeters - b.distanceMeters) <= 25
                return sameDur && sameDist
            }

            val keptList = mutableListOf<RunningSession>()
            val removedIds = mutableListOf<String>()

            for (candidate in sessions.sortedByDescending { sessionQualityScore(it) }) {
                val existingIdx = keptList.indexOfFirst { areDuplicates(it, candidate) }
                if (existingIdx == -1) {
                    val normalized = if (is30OctFreeRunSignature(candidate) || RunningProgramManager.isEffectiveFreeRun(candidate)) {
                        candidate.copy(
                            sessionType = if (candidate.sessionType.isBlank() || candidate.sessionType == "VOLUME" || candidate.sessionType == "CONTINUOUS") "FREE_RUN" else candidate.sessionType,
                            weekNumber = if (is30OctFreeRunSignature(candidate)) 0 else candidate.weekNumber
                        )
                    } else {
                        candidate
                    }
                    keptList.add(normalized)
                } else {
                    val primary = keptList[existingIdx]
                    if (candidate.id != primary.id) {
                        removedIds.add(candidate.id)
                    }
                    val is30Oct = is30OctFreeRunSignature(primary) || is30OctFreeRunSignature(candidate)
                    val merged = primary.copy(
                        sessionType = if (is30Oct || RunningProgramManager.isEffectiveFreeRun(primary) || RunningProgramManager.isEffectiveFreeRun(candidate)) "FREE_RUN" else primary.sessionType,
                        weekNumber = if (is30Oct) 0 else primary.weekNumber,
                        avgHr = primary.avgHr?.takeIf { it > 0 } ?: candidate.avgHr?.takeIf { it > 0 },
                        maxHr = primary.maxHr?.takeIf { it > 0 } ?: candidate.maxHr?.takeIf { it > 0 },
                        hrTimeline = primary.hrTimeline?.takeIf { it.isNotBlank() } ?: candidate.hrTimeline,
                        kmSplitsJson = primary.kmSplitsJson?.takeIf { it.isNotBlank() } ?: candidate.kmSplitsJson,
                        routePointsJson = primary.routePointsJson?.takeIf { it.isNotBlank() } ?: candidate.routePointsJson,
                        shoeName = primary.shoeName?.takeIf { it.isNotBlank() } ?: candidate.shoeName,
                        notes = primary.notes?.takeIf { it.isNotBlank() } ?: candidate.notes,
                        aiCoachReport = primary.aiCoachReport?.takeIf { it.isNotBlank() } ?: candidate.aiCoachReport,
                        preRunCheckIn = primary.preRunCheckIn ?: candidate.preRunCheckIn,
                        rpeCr10 = primary.rpeCr10 ?: candidate.rpeCr10,
                        sRpeLoadUa = primary.sRpeLoadUa ?: candidate.sRpeLoadUa,
                        executionAudit = primary.executionAudit ?: candidate.executionAudit,
                        hrProvenance = primary.hrProvenance ?: candidate.hrProvenance,
                        executedIntervals = primary.executedIntervals.ifEmpty { candidate.executedIntervals },
                        advancedMetrics = primary.advancedMetrics ?: candidate.advancedMetrics
                    )
                    keptList[existingIdx] = merged
                }
            }

    return keptList.sortedByDescending { it.date } to removedIds.distinct()
}

fun List<RunningSession>.deduplicatedRunningSessions(): List<RunningSession> {
    return deduplicateRunningSessions(this).first
}

fun List<RecordedKmSplit>.toJsonString(): String {
    val array = org.json.JSONArray()
    for (split in this) {
        val obj = org.json.JSONObject()
        obj.put("splitNumber", split.splitNumber)
        obj.put("splitLabel", split.splitLabel)
        obj.put("distanceMeters", split.distanceMeters)
        obj.put("durationSec", split.durationSec)
        obj.put("paceSecPerKm", split.paceSecPerKm)
        if (split.avgHr != null) obj.put("avgHr", split.avgHr)
        if (split.phaseTag != null) obj.put("phaseTag", split.phaseTag)
        array.put(obj)
    }
    return array.toString()
}

fun String?.toRecordedKmSplits(): List<RecordedKmSplit> {
    if (this.isNullOrBlank()) return emptyList()
    val list = mutableListOf<RecordedKmSplit>()
    try {
        val array = org.json.JSONArray(this)
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val distM = obj.optInt("distanceMeters", 1000).coerceAtLeast(1)
            val rawDurSec = obj.optInt("durationSec", 0)
            val rawPaceSec = obj.optInt("paceSecPerKm", 0)
            val syncedPace = if (rawPaceSec > 0) {
                rawPaceSec
            } else if (rawDurSec > 0 && distM > 0) {
                ((rawDurSec * 1000.0) / distM.toDouble()).roundToInt()
            } else {
                0
            }
            val rawLabel = obj.optString("splitLabel", "${i + 1},0 km").replace('.', ',')
            list.add(
                RecordedKmSplit(
                    splitNumber = obj.optInt("splitNumber", i + 1),
                    splitLabel = rawLabel,
                    distanceMeters = distM,
                    durationSec = rawDurSec,
                    paceSecPerKm = syncedPace,
                    avgHr = if (obj.has("avgHr")) obj.optInt("avgHr") else null,
                    phaseTag = if (obj.has("phaseTag")) obj.optString("phaseTag") else null
                )
            )
        }
    } catch (_: Exception) {}
    return list
}

fun String?.toHrTimelineList(): List<Int> {
    if (this.isNullOrBlank()) return emptyList()
    return try {
        this.split(",").mapNotNull { entry ->
            val trimmed = entry.trim()
            if (trimmed.isEmpty()) return@mapNotNull null
            val bpmText = if (trimmed.contains(":")) {
                trimmed.substringAfterLast(":").trim()
            } else {
                trimmed
            }
            bpmText.toIntOrNull()?.takeIf { it in 30..240 }
        }
    } catch (_: Exception) {
        emptyList()
    }
}

/**
 * Sanitiza retroativamente os primeiros 1 a 3 pontos de GPS caso apresentem um segmento reto anômalo
 * ("Cold-Start GPS / Cell-Tower Jump": `distância > 80 metros` entre dois pontos consecutivos quando a
 * distância média entre os demais pontos da sessão é `< 25 metros`), garantindo que inclusive treinos já
 * salvos no histórico sejam renderizados apenas na pista real e com o zoom perfeitamente enquadrado.
 */
fun sanitizeColdStartOutliers(points: List<RoutePointItem>): List<RoutePointItem> {
    if (points.size < 3) return points

    val segmentDistances = DoubleArray(points.size - 1) { i ->
        val distArr = FloatArray(1)
        Location.distanceBetween(
            points[i].lat, points[i].lon,
            points[i + 1].lat, points[i + 1].lon,
            distArr
        )
        distArr[0].toDouble()
    }

    val maxPrefixIndex = minOf(3, segmentDistances.size)
    val tailSegments = if (segmentDistances.size > maxPrefixIndex) {
        segmentDistances.slice(maxPrefixIndex until segmentDistances.size)
    } else {
        segmentDistances.drop(1)
    }
    val avgTailDist = if (tailSegments.isNotEmpty()) tailSegments.average() else 0.0

    if (avgTailDist < 25.0) {
        var dropCount = 0
        for (i in 0 until maxPrefixIndex) {
            if (segmentDistances[i] > 80.0) {
                dropCount = i + 1
            }
        }
        if (dropCount > 0 && points.size - dropCount >= 2) {
            return points.drop(dropCount)
        }
    }
    return points
}

/**
 * Sanitiza retroativamente uma lista de pares de coordenadas (`Pair<Double, Double>`) contra saltos de largada a frio.
 */
fun sanitizeColdStartCoordinatePairs(points: List<Pair<Double, Double>>): List<Pair<Double, Double>> {
    if (points.size < 3) return points

    val segmentDistances = DoubleArray(points.size - 1) { i ->
        val distArr = FloatArray(1)
        Location.distanceBetween(
            points[i].first, points[i].second,
            points[i + 1].first, points[i + 1].second,
            distArr
        )
        distArr[0].toDouble()
    }

    val maxPrefixIndex = minOf(3, segmentDistances.size)
    val tailSegments = if (segmentDistances.size > maxPrefixIndex) {
        segmentDistances.slice(maxPrefixIndex until segmentDistances.size)
    } else {
        segmentDistances.drop(1)
    }
    val avgTailDist = if (tailSegments.isNotEmpty()) tailSegments.average() else 0.0

    if (avgTailDist < 25.0) {
        var dropCount = 0
        for (i in 0 until maxPrefixIndex) {
            if (segmentDistances[i] > 80.0) {
                dropCount = i + 1
            }
        }
        if (dropCount > 0 && points.size - dropCount >= 2) {
            return points.drop(dropCount)
        }
    }
    return points
}

fun String?.toRoutePointItems(): List<RoutePointItem> {
    if (this.isNullOrBlank()) return emptyList()
    val list = mutableListOf<RoutePointItem>()
    try {
        val arr = org.json.JSONArray(this)
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val lat = if (obj.has("lat")) obj.optDouble("lat", Double.NaN) else obj.optDouble("latitude", Double.NaN)
            val lon = if (obj.has("lon")) obj.optDouble("lon", Double.NaN) else obj.optDouble("longitude", Double.NaN)
            val ts = obj.optLong("timestamp", 0L)
            val spd = if (obj.has("speed")) obj.optDouble("speed").toFloat() else null
            val alt = if (obj.has("altitude")) obj.optDouble("altitude") else null
            val acc = if (obj.has("accuracy")) obj.optDouble("accuracy").toFloat() else null
            val rawPhase = obj.optString("phase", obj.optString("blockTag", obj.optString("blockType", "")))
            val bIdx = obj.optInt("blockIndex", 0)
            if (!lat.isNaN() && !lon.isNaN() && lat != 0.0 && lon != 0.0) {
                list.add(
                    RoutePointItem(
                        lat = lat,
                        lon = lon,
                        timestamp = ts,
                        speed = spd,
                        altitude = alt,
                        accuracy = acc,
                        phase = rawPhase,
                        blockIndex = bIdx
                    )
                )
            }
        }
    } catch (_: Exception) {}
    return sanitizeColdStartOutliers(list)
}

fun String?.toRouteCoordinates(): List<Pair<Double, Double>> {
    return toRoutePointItems().map { Pair(it.lat, it.lon) }
}

fun String?.toGpsTrackPoints(sessionId: String): List<GpsTrackPoint> {
    return toRoutePointItems().map { pt ->
        GpsTrackPoint(
            sessionId = sessionId,
            timestampMs = if (pt.timestamp > 0L) pt.timestamp else System.currentTimeMillis(),
            capturedAtMs = if (pt.timestamp > 0L) pt.timestamp else System.currentTimeMillis(),
            latitude = pt.lat,
            longitude = pt.lon,
            accuracyM = pt.accuracy,
            speedMps = pt.speed,
            altitudeM = pt.altitude
        )
    }
}

/**
 * Resolve os pontos de rota da sessão com tags de fase (WARMUP, WALK, RUN, COOL_DOWN)
 * e destaca em Dourado (#FFD700 / isPrSegment = true / phase = "PR") o Melhor Tiro / 200m mais rápidos.
 * NUNCA usa rota estática em Rio Branco para corridas reais feitas em outras cidades.
 */
fun resolveSessionRoutePointsWithPhases(
    session: RunningSession,
    gpsPoints: List<GpsTrackPoint> = emptyList()
): List<RoutePointItem> {
    val jsonItems = session.routePointsJson.toRoutePointItems()
    val rawUnsanitizedPoints: List<RoutePointItem> = when {
        jsonItems.size >= 2 && jsonItems.size >= gpsPoints.size -> jsonItems
        gpsPoints.size >= 2 -> {
            gpsPoints
                .filter { it.latitude != 0.0 && it.longitude != 0.0 }
                .sortedBy { it.timestampMs }
                .mapIndexed { idx, pt ->
                    val matchingJson = jsonItems.getOrNull(idx)
                    RoutePointItem(
                        lat = pt.latitude,
                        lon = pt.longitude,
                        timestamp = pt.timestampMs,
                        speed = pt.speedMps ?: matchingJson?.speed,
                        altitude = pt.altitudeM ?: matchingJson?.altitude,
                        accuracy = pt.accuracyM ?: matchingJson?.accuracy,
                        phase = matchingJson?.phase ?: "",
                        blockIndex = matchingJson?.blockIndex ?: 0
                    )
                }
        }
        jsonItems.isNotEmpty() -> jsonItems
        gpsPoints.isNotEmpty() -> gpsPoints.map {
            RoutePointItem(
                lat = it.latitude,
                lon = it.longitude,
                timestamp = it.timestampMs,
                speed = it.speedMps,
                altitude = it.altitudeM,
                accuracy = it.accuracyM,
                phase = ""
            )
        }
        session.isSimulatedOrTestSession() -> {
            // Somente para sessões explicitamente de teste/simulação
            val simCoords = com.example.utils.MapTileHelper.getAvenueRoutePoints(session.distanceMeters.coerceAtLeast(2000))
            val totalSec = session.durationSec.coerceAtLeast(600)
            val startTs = session.date - (totalSec * 1000L)
            simCoords.mapIndexed { i, (lat, lon) ->
                val frac = i.toDouble() / (simCoords.size - 1).coerceAtLeast(1)
                RoutePointItem(
                    lat = lat,
                    lon = lon,
                    timestamp = startTs + (frac * totalSec * 1000L).toLong(),
                    speed = null,
                    altitude = 152.0 + kotlin.math.sin(frac * Math.PI * 3.0) * 6.0,
                    phase = ""
                )
            }
        }
        else -> emptyList()
    }

    val rawPoints = sanitizeColdStartOutliers(rawUnsanitizedPoints)
    if (rawPoints.size < 2) return rawPoints

    val freeRaceMode = com.example.engine.FreeRunRaceMode.fromSessionType(session.sessionType)
        ?: if (RunningProgramManager.isEffectiveFreeRun(session)) com.example.engine.FreeRunRaceMode.FREE_RUN else null
    val isFreeOrRace = freeRaceMode != null

    val plan = when {
        isFreeOrRace -> RunningProgramManager.getFreeOrRacePlan(freeRaceMode!!)
        session.isGentleMode -> RunningProgramManager.getGentleWalkPlan()
        else -> RunningProgramManager.getPlanForWeek(session.weekNumber.coerceAtLeast(1), session.dayNumber.coerceAtLeast(1))
    }
    val blocks = plan.blocks
    val totalPlanSec = blocks.sumOf { it.durationSec }.coerceAtLeast(1)

    val hasExplicitVariedPhases = rawPoints.map { it.phase.uppercase() }
        .filter { it in listOf("WARMUP", "WARM_UP", "RUN", "WALK", "COOL_DOWN", "COOLDOWN") }
        .distinct().size >= 2

    val firstTs = rawPoints.first().timestamp
    val lastTs = rawPoints.last().timestamp
    val totalTsSpanSec = ((lastTs - firstTs) / 1000L).toInt()

    // Pré-calcula velocidades reais de cada segmento para colorir por performance quando for Corrida Livre / Prova
    val rawN = rawPoints.size
    val preDistances = DoubleArray(rawN - 1)
    val preSpeeds = DoubleArray(rawN - 1)
    val preDistBuf = FloatArray(1)
    for (i in 0 until rawN - 1) {
        val p1 = rawPoints[i]
        val p2 = rawPoints[i + 1]
        Location.distanceBetween(p1.lat, p1.lon, p2.lat, p2.lon, preDistBuf)
        val dM = preDistBuf[0].toDouble().coerceAtLeast(0.5)
        preDistances[i] = dM
        val dtS = ((p2.timestamp - p1.timestamp) / 1000.0)
        preSpeeds[i] = when {
            p2.speed != null && p2.speed > 0.3f -> p2.speed.toDouble()
            p1.speed != null && p1.speed > 0.3f -> p1.speed.toDouble()
            dtS in 0.5..120.0 -> dM / dtS
            else -> 2.85
        }
    }
    val medianSpeedMps = if (preSpeeds.isNotEmpty()) {
        val sortedSpd = preSpeeds.sorted()
        sortedSpd[sortedSpd.size / 2].coerceAtLeast(1.8)
    } else 2.85

    // 1. Atribui a fase de cada ponto com base na performance da parcial (em Corrida Livre/Prova) ou no bloco do treino
    val phasedPoints = rawPoints.mapIndexed { index, pt ->
        val explicitPhase = when (pt.phase.uppercase()) {
            "WARMUP", "WARM_UP" -> "WARMUP"
            "COOL_DOWN", "COOLDOWN" -> "COOL_DOWN"
            "WALK", "RECOVERY" -> "WALK"
            "RUN", "SPRINT", "INTERVAL" -> "RUN"
            else -> ""
        }

        if (isFreeOrRace) {
            val segIdx = index.coerceAtMost((rawN - 2).coerceAtLeast(0))
            val spd = if (preSpeeds.isNotEmpty()) preSpeeds[segIdx] else medianSpeedMps
            // Em Corrida Livre / Prova: destaca os trechos de corrida forte em Verde ("RUN")
            // e eventuais reduções de ritmo/hidratação em Azul ("WALK")
            val perfPhase = if (spd >= 1.85 && spd >= medianSpeedMps * 0.78) "RUN" else "WALK"
            val splitBlockIdx = ((index.toDouble() / rawN.coerceAtLeast(1)) * 5.0).toInt()
            pt.copy(phase = perfPhase, blockIndex = splitBlockIdx)
        } else if (hasExplicitVariedPhases && explicitPhase.isNotEmpty()) {
            pt.copy(phase = explicitPhase)
        } else {
            val progressNorm = if (totalTsSpanSec > 15) {
                ((pt.timestamp - firstTs) / 1000.0) / totalTsSpanSec.toDouble()
            } else {
                index.toDouble() / (rawPoints.size - 1).coerceAtLeast(1).toDouble()
            }
            val targetPlanSec = (progressNorm * totalPlanSec).toInt().coerceIn(0, totalPlanSec)
            var accSec = 0
            var matchedType = RunningBlockType.RUN
            var matchedIdx = 0
            for (bIdx in blocks.indices) {
                val b = blocks[bIdx]
                if (targetPlanSec <= accSec + b.durationSec || bIdx == blocks.lastIndex) {
                    matchedType = b.type
                    matchedIdx = bIdx
                    break
                }
                accSec += b.durationSec
            }
            val inferredPhase = when (matchedType) {
                RunningBlockType.WARMUP -> "WARMUP"
                RunningBlockType.COOL_DOWN -> "COOL_DOWN"
                RunningBlockType.WALK -> "WALK"
                RunningBlockType.RUN -> "RUN"
            }
            pt.copy(phase = inferredPhase, blockIndex = matchedIdx)
        }
    }

    // 2. Identifica o Melhor Tiro da Sessão / Recorde de Ritmo nos 200m mais rápidos (PR - #FFD700)
    val n = phasedPoints.size
    val segDistances = DoubleArray(n - 1)
    val segSpeeds = DoubleArray(n - 1)
    val distResult = FloatArray(1)
    var totalRouteMeters = 0.0

    for (i in 0 until n - 1) {
        val p1 = phasedPoints[i]
        val p2 = phasedPoints[i + 1]
        Location.distanceBetween(p1.lat, p1.lon, p2.lat, p2.lon, distResult)
        val dMeters = distResult[0].toDouble().coerceAtLeast(0.5)
        segDistances[i] = dMeters
        totalRouteMeters += dMeters

        val dtSec = ((p2.timestamp - p1.timestamp) / 1000.0)
        val spd = when {
            p2.speed != null && p2.speed > 0.3f -> p2.speed.toDouble()
            p1.speed != null && p1.speed > 0.3f -> p1.speed.toDouble()
            dtSec in 0.5..120.0 -> dMeters / dtSec
            p1.phase == "RUN" -> 3.1
            p1.phase == "WALK" -> 1.45
            else -> 1.35
        }
        segSpeeds[i] = spd
    }

    // Procura a janela de ~200m (ou 15% da rota se rota curta) dentro de blocos RUN com maior velocidade média
    val targetPrWindowMeters = minOf(200.0, (totalRouteMeters * 0.18).coerceAtLeast(40.0))
    var bestStartIdx = -1
    var bestEndIdx = -1
    var bestAvgSpeed = -1.0

    for (start in 0 until n - 1) {
        if (phasedPoints[start].phase != "RUN") continue
        var winDist = 0.0
        var weightedSpeedSum = 0.0
        var end = start
        while (end < n - 1 && phasedPoints[end].phase == "RUN") {
            winDist += segDistances[end]
            weightedSpeedSum += segSpeeds[end] * segDistances[end]
            if (winDist >= targetPrWindowMeters) break
            end++
        }
        if (winDist > 5.0) {
            // Leve bônus para janelas que atingem os 200m completos
            val completenessFactor = (winDist / targetPrWindowMeters).coerceIn(0.6, 1.0)
            val avgSpd = (weightedSpeedSum / winDist) * completenessFactor
            if (avgSpd > bestAvgSpeed) {
                bestAvgSpeed = avgSpd
                bestStartIdx = start
                bestEndIdx = (end + 1).coerceAtMost(n - 1)
            }
        }
    }

    // Se não havia ponto marcado como RUN (ex: Modo Leve), destaca o trecho mais rápido geral
    if (bestStartIdx == -1 && n >= 4) {
        val windowCount = (n / 6).coerceAtLeast(2)
        bestStartIdx = (n / 2).coerceAtMost(n - windowCount - 1)
        bestEndIdx = (bestStartIdx + windowCount).coerceAtMost(n - 1)
    }

    return phasedPoints.mapIndexed { idx, pt ->
        val isPr = bestStartIdx >= 0 && idx in bestStartIdx..bestEndIdx
        if (isPr) {
            pt.copy(phase = "PR", isPrSegment = true)
        } else {
            pt
        }
    }
}

/**
 * Calcula todas as métricas analíticas pós-treino da sessão (Cabeçalho com estrutura, Pace nos Tiros,
 * Melhor Tiro, Tempo Correndo/Caminhando, Cadência Média SPM e Elevação Positiva).
 */
fun calculateSprintAnalytics(
    session: RunningSession,
    gpsPoints: List<GpsTrackPoint> = emptyList()
): SprintAnalytics {
    val freeRaceMode = com.example.engine.FreeRunRaceMode.fromSessionType(session.sessionType)
        ?: if (RunningProgramManager.isEffectiveFreeRun(session)) com.example.engine.FreeRunRaceMode.FREE_RUN else null
    val isFreeOrRace = freeRaceMode != null

    val plan = when {
        isFreeOrRace -> RunningProgramManager.getFreeOrRacePlan(freeRaceMode!!)
        session.isGentleMode -> RunningProgramManager.getGentleWalkPlan()
        else -> RunningProgramManager.getPlanForWeek(session.weekNumber.coerceAtLeast(1), session.dayNumber.coerceAtLeast(1))
    }

    val middleBlocks = plan.blocks.filter {
        it.type == RunningBlockType.RUN || it.type == RunningBlockType.WALK
    }
    val structureSummary = when {
        freeRaceMode == com.example.engine.FreeRunRaceMode.RACE_5K -> "5K (5,00 km)"
        freeRaceMode == com.example.engine.FreeRunRaceMode.RACE_10K -> "10K (10,00 km)"
        freeRaceMode == com.example.engine.FreeRunRaceMode.FREE_RUN -> "Corrida Livre Contínua"
        session.isGentleMode -> "Caminhada Modo Leve"
        else -> RunningProgramManager.formatMiddleBlocksSummary(middleBlocks)
    }

    val displaySessionTitle = RunningProgramManager.getDisplayTitleForSession(session)
    val headerIdentification = when {
        freeRaceMode != null -> "${freeRaceMode.fullTitle} • $structureSummary"
        session.isGentleMode -> "Modo Leve • $structureSummary"
        else -> "$displaySessionTitle • $structureSummary"
    }

    val hasMainIsolation = !isFreeOrRace &&
            session.mainRunDistanceMeters != null && session.mainRunDistanceMeters > 0 &&
            session.mainRunDurationSec != null && session.mainRunDurationSec > 0 &&
            (session.mainRunDurationSec < session.durationSec || session.mainRunDistanceMeters < session.distanceMeters)

    val totalDistM = session.distanceMeters.coerceAtLeast(0)
    val totalDurSec = session.durationSec.coerceAtLeast(0)
    val totalPaceSec = if (totalDistM > 0 && totalDurSec > 0) {
        ((totalDurSec * 1000.0) / totalDistM.toDouble()).roundToInt()
    } else session.avgPaceSecPerKm

    val mainDistM = if (hasMainIsolation) session.mainRunDistanceMeters!! else totalDistM
    val mainDurSec = if (hasMainIsolation) session.mainRunDurationSec!! else totalDurSec
    val mainPaceSec = if (mainDistM > 0 && mainDurSec > 0) {
        ((mainDurSec * 1000.0) / mainDistM.toDouble()).roundToInt()
    } else if (session.mainRunAvgPaceSecPerKm != null && session.mainRunAvgPaceSecPerKm > 0) {
        session.mainRunAvgPaceSecPerKm
    } else totalPaceSec

    // Separação do tempo correndo vs caminhando no treino principal
    val planRunSec = middleBlocks.filter { it.type == RunningBlockType.RUN }.sumOf { it.durationSec }
    val planWalkSec = middleBlocks.filter { it.type == RunningBlockType.WALK }.sumOf { it.durationSec }
    val planMiddleTotalSec = (planRunSec + planWalkSec).coerceAtLeast(1)

    val recordedSplits = session.kmSplitsJson.toRecordedKmSplits()
    val jsonRoutePoints = session.routePointsJson.toRoutePointItems()
    val routePoints = if (jsonRoutePoints.size >= 4 && jsonRoutePoints.size >= gpsPoints.size) {
        jsonRoutePoints
    } else if (gpsPoints.size >= 4) {
        gpsPoints
            .filter { it.latitude != 0.0 && it.longitude != 0.0 }
            .sortedBy { it.timestampMs }
            .map { pt ->
                RoutePointItem(
                    lat = pt.latitude,
                    lon = pt.longitude,
                    timestamp = pt.timestampMs,
                    speed = pt.speedMps,
                    altitude = pt.altitudeM,
                    accuracy = pt.accuracyM
                )
            }
    } else {
        jsonRoutePoints
    }

    val runningTimeSec: Int
    val walkingTimeSec: Int
    var freeRunOnlyDistMeters = 0.0
    var freeRunOnlyTimeMs = 0L
    if (session.isGentleMode) {
        runningTimeSec = 0
        walkingTimeSec = totalDurSec
    } else if (isFreeOrRace) {
        // No modo Corrida Livre, contabiliza tempo com pace > 8'30"/km (> 510s/km ou velocidade < 7.0 km/h = 1.944 m/s) como Caminhada
        val walkPaceThresholdSec = 510 // 8'30"/km
        val walkSpeedThresholdMps = 1000f / walkPaceThresholdSec.toFloat() // ~1.96 m/s (~7.06 km/h)

        // Se o ritmo médio global é claramente de caminhada (> 8'30"/km) e nenhuma parcial de km foi corrida (<= 8'30"/km)
        val hasAnyRunningSplit = recordedSplits.any { it.distanceMeters >= 200 && it.paceSecPerKm in 120..walkPaceThresholdSec }
        if (mainPaceSec > walkPaceThresholdSec && !hasAnyRunningSplit) {
            // Verifica se houve algum trecho sustentado de corrida nos pontos GPS (janela suavizada)
            val validPts = routePoints.filter { it.timestamp > 0L }
            var rawRunMs = 0L
            var rawWalkMs = 0L
            var rawRunDistM = 0.0
            if (validPts.size >= 4) {
                for (i in 1 until validPts.size) {
                    val dtMs = (validPts[i].timestamp - validPts[i - 1].timestamp).coerceIn(0L, 15000L)
                    if (dtMs <= 0L) continue
                    // Média móvel de 3 pontos para filtrar ruído instantâneo de GPS durante caminhada
                    val wStart = (i - 2).coerceAtLeast(0)
                    val window = validPts.subList(wStart, i + 1)
                    val avgSpeed = window.map { (it.speed ?: 0f).toDouble() }.average().toFloat()
                    if (avgSpeed >= walkSpeedThresholdMps) {
                        rawRunMs += dtMs
                        val segDist = com.example.services.GpsEvaluator.distanceBetweenMeters(
                            validPts[i - 1].lat, validPts[i - 1].lon,
                            validPts[i].lat, validPts[i].lon
                        )
                        if (segDist in 0.5..250.0) rawRunDistM += segDist
                    } else {
                        rawWalkMs += dtMs
                    }
                }
            }
            val totalTrackedMs = rawRunMs + rawWalkMs
            // Se menos de 12% do tempo (ou < 30s) teve pico de GPS acima do limiar numa sessão com pace > 8'30"/km, é 100% caminhada
            if (totalTrackedMs <= 0L || rawRunMs < 30_000L || (rawRunMs.toDouble() / totalTrackedMs.toDouble()) < 0.12) {
                runningTimeSec = 0
                walkingTimeSec = totalDurSec
            } else {
                val walkRatio = (rawWalkMs.toDouble() / totalTrackedMs.toDouble()).coerceIn(0.0, 1.0)
                walkingTimeSec = (totalDurSec * walkRatio).roundToInt().coerceIn(0, totalDurSec)
                runningTimeSec = (totalDurSec - walkingTimeSec).coerceAtLeast(0)
                freeRunOnlyDistMeters = rawRunDistM
                freeRunOnlyTimeMs = rawRunMs
            }
        } else if (routePoints.size >= 4 && routePoints.count { it.timestamp > 0L } >= 4) {
            val validPts = routePoints.filter { it.timestamp > 0L }
            var rawRunMs = 0L
            var rawWalkMs = 0L
            var rawRunDistM = 0.0
            for (i in 1 until validPts.size) {
                val dtMs = (validPts[i].timestamp - validPts[i - 1].timestamp).coerceIn(0L, 15000L)
                if (dtMs <= 0L) continue
                val wStart = (i - 2).coerceAtLeast(0)
                val window = validPts.subList(wStart, i + 1)
                val avgSpeed = window.map { (it.speed ?: 0f).toDouble() }.average().toFloat()
                if (avgSpeed >= walkSpeedThresholdMps) {
                    rawRunMs += dtMs
                    val segDist = com.example.services.GpsEvaluator.distanceBetweenMeters(
                        validPts[i - 1].lat, validPts[i - 1].lon,
                        validPts[i].lat, validPts[i].lon
                    )
                    if (segDist in 0.5..250.0) rawRunDistM += segDist
                } else {
                    rawWalkMs += dtMs
                }
            }
            val totalTrackedMs = rawRunMs + rawWalkMs
            if (totalTrackedMs > 0L) {
                val walkRatio = (rawWalkMs.toDouble() / totalTrackedMs.toDouble()).coerceIn(0.0, 1.0)
                walkingTimeSec = (totalDurSec * walkRatio).roundToInt().coerceIn(0, totalDurSec)
                runningTimeSec = (totalDurSec - walkingTimeSec).coerceAtLeast(0)
                freeRunOnlyDistMeters = rawRunDistM
                freeRunOnlyTimeMs = rawRunMs
            } else if (mainPaceSec > walkPaceThresholdSec) {
                runningTimeSec = 0
                walkingTimeSec = totalDurSec
            } else {
                runningTimeSec = totalDurSec
                walkingTimeSec = 0
            }
        } else if (recordedSplits.isNotEmpty()) {
            val splitWalkSec = recordedSplits.filter { it.paceSecPerKm > walkPaceThresholdSec }.sumOf { it.durationSec }
            val runSplits = recordedSplits.filter { it.paceSecPerKm in 120..walkPaceThresholdSec }
            val splitRunSec = runSplits.sumOf { it.durationSec }
            val splitTotal = (splitWalkSec + splitRunSec).coerceAtLeast(1)
            val walkRatio = (splitWalkSec.toDouble() / splitTotal.toDouble()).coerceIn(0.0, 1.0)
            walkingTimeSec = (totalDurSec * walkRatio).roundToInt().coerceIn(0, totalDurSec)
            runningTimeSec = (totalDurSec - walkingTimeSec).coerceAtLeast(0)
            val runSplitsDist = runSplits.sumOf { it.distanceMeters }
            if (runSplitsDist >= 200 && splitRunSec > 0) {
                freeRunOnlyDistMeters = runSplitsDist.toDouble()
                freeRunOnlyTimeMs = splitRunSec * 1000L
            }
        } else if (mainPaceSec > walkPaceThresholdSec) {
            runningTimeSec = 0
            walkingTimeSec = totalDurSec
        } else {
            runningTimeSec = totalDurSec
            walkingTimeSec = 0
        }
    } else if (planRunSec > 0) {
        val runRatio = planRunSec.toDouble() / planMiddleTotalSec.toDouble()
        runningTimeSec = (mainDurSec * runRatio).roundToInt().coerceIn(1, mainDurSec.coerceAtLeast(1))
        walkingTimeSec = (mainDurSec - runningTimeSec).coerceAtLeast(0)
    } else {
        runningTimeSec = mainDurSec
        walkingTimeSec = (totalDurSec - mainDurSec).coerceAtLeast(0)
    }

    // Cálculo realista de Pace nos Tiros (apenas correndo) vs Caminhada (~11'30"/km = 690s/km -> ~1.45 m/s)
    val walkSpeedMps = 1.42 // ~5.1 km/h na recuperação em caminhada
    val estWalkDistM = if (runningTimeSec <= 0) {
        mainDistM
    } else {
        (walkingTimeSec * walkSpeedMps).roundToInt().coerceAtMost((mainDistM * 0.48).roundToInt())
    }
    val estRunDistM = if (runningTimeSec <= 0) {
        0
    } else {
        (mainDistM - estWalkDistM).coerceAtLeast(1)
    }

    val recordedFastestPace = recordedSplits
        .filter { it.distanceMeters >= 400 && it.paceSecPerKm in 140..900 }
        .minOfOrNull { it.paceSecPerKm }

    val avgSprintPaceSec = if (session.isGentleMode) {
        mainPaceSec
    } else if (isFreeOrRace) {
        if (walkingTimeSec <= 0) {
            mainPaceSec
        } else if (freeRunOnlyDistMeters >= 50.0 && freeRunOnlyTimeMs >= 15_000L) {
            val runOnlyPace = (((freeRunOnlyTimeMs / 1000.0) * 1000.0) / freeRunOnlyDistMeters).roundToInt()
            runOnlyPace.coerceIn(140, 510)
        } else {
            // Sem distância isolada de corrida registrada nos segmentos: não repete o pace global como se fosse só corrida
            0
        }
    } else if (runningTimeSec > 0 && estRunDistM > 10) {
        val rawSprintPace = ((runningTimeSec * 1000.0) / estRunDistM.toDouble()).roundToInt()
        // Garante que o pace nos tiros seja sempre igual ou mais rápido que o pace médio com caminhada
        if (mainPaceSec > 0) minOf(rawSprintPace, (mainPaceSec * 0.92).roundToInt()).coerceAtLeast(165) else rawSprintPace
    } else {
        mainPaceSec
    }

    val bestSprintPaceSec = when {
        recordedFastestPace != null -> minOf(recordedFastestPace, avgSprintPaceSec.takeIf { it > 0 } ?: recordedFastestPace)
        avgSprintPaceSec > 0 -> (avgSprintPaceSec * 0.93).roundToInt().coerceAtLeast(155)
        else -> 0
    }

    // Cadência Média (spm) calibrada pelo ritmo nos tiros, perfil biomecânico ou medição explícita na sessão
    val explicitCadenceFromNotes = Regex("""(\d{3})\s*(?:ppm|spm)""", RegexOption.IGNORE_CASE)
        .find(session.notes ?: "")
        ?.groupValues?.getOrNull(1)?.toIntOrNull()
        ?.takeIf { it in 100..210 }
    val avgCadenceSpm = if (totalDurSec <= 0 || totalDistM <= 0) {
        0
    } else if (explicitCadenceFromNotes != null) {
        explicitCadenceFromNotes
    } else if (session.isGentleMode) {
        118
    } else {
        val refPaceMin = (if (avgSprintPaceSec > 0) avgSprintPaceSec else mainPaceSec) / 60.0
        when {
            refPaceMin <= 4.5 -> 178
            refPaceMin <= 5.2 -> 172
            refPaceMin <= 6.0 -> 166
            refPaceMin <= 7.0 -> 160
            refPaceMin <= 8.0 -> 154
            else -> 146
        }
    }

    // Elevação Positiva (m) filtrada com média móvel de 5 pontos, filtro de precisão (<= 12m) e banda morta >= 3.5m
    val isTreadmill = session.environment.equals("TREADMILL", ignoreCase = true)
    val elevationGainMeters = if (isTreadmill) {
        0
    } else if (gpsPoints.size >= 5) {
        com.example.services.GpsEvaluator.calculateFilteredElevationGainMeters(
            altitudes = gpsPoints.mapNotNull { it.altitudeM },
            accuracies = gpsPoints.map { it.accuracyM },
            isTreadmill = false
        )
    } else {
        val routeItems = resolveSessionRoutePointsWithPhases(session, gpsPoints)
        val ptsWithAlt = routeItems.filter { it.altitude != null }
        com.example.services.GpsEvaluator.calculateFilteredElevationGainMeters(
            altitudes = ptsWithAlt.mapNotNull { it.altitude },
            accuracies = ptsWithAlt.map { it.accuracy },
            isTreadmill = false
        )
    }

    val isContinuousRunMode = isFreeOrRace || session.isGentleMode || planWalkSec == 0

    return SprintAnalytics(
        structureSummary = structureSummary,
        headerIdentification = headerIdentification,
        mainDistMeters = mainDistM,
        mainDurationSec = mainDurSec,
        mainPaceSecPerKm = mainPaceSec,
        totalDistMeters = totalDistM,
        totalDurationSec = totalDurSec,
        totalPaceSecPerKm = totalPaceSec,
        avgSprintPaceSecPerKm = avgSprintPaceSec,
        bestSprintPaceSecPerKm = bestSprintPaceSec,
        runningTimeSec = runningTimeSec,
        walkingTimeSec = walkingTimeSec,
        avgCadenceSpm = avgCadenceSpm,
        elevationGainMeters = elevationGainMeters,
        isContinuousRunMode = isContinuousRunMode
    )
}

/**
 * Calcula a FC Máxima efetiva (`effectiveMaxHr`) com a seguinte precedência:
 * 1. Calibração explícita salva no perfil (`calibratedMaxHrBpm`), quando válida (100..235 bpm).
 * 2. Fórmula etária (220 - idade) quando houver idade válida (10..100 anos), ajustada acima do teto
 *    apenas se um pico real observado superar a fórmula (`maxOf(realObservedPeak + 4, ageFormulaMax)`).
 * 3. Sem calibração e sem idade válida, retorna `0` (indisponível), nunca tratando pico observado como FCmáx.
 */
fun calculateEffectiveMaxHr(
    ageYears: Int?,
    sessionMaxHrBpm: Int? = null,
    historicalPeakHrBpm: Int? = null,
    calibratedMaxHrBpm: Int? = null
): Int {
    val calibrated = calibratedMaxHrBpm?.takeIf { it in 100..235 }
    if (calibrated != null) return calibrated

    val validAge = ageYears?.takeIf { it in 10..100 } ?: return 0
    val safeAge = validAge.coerceIn(18, 80)
    val ageFormulaMax = (220 - safeAge).coerceIn(155, 205)
    val sessionPeak = sessionMaxHrBpm?.takeIf { it > 0 } ?: 0
    val histPeak = historicalPeakHrBpm?.takeIf { it > 0 } ?: 0
    val realObservedPeak = maxOf(sessionPeak, histPeak)
    return when {
        realObservedPeak > ageFormulaMax -> maxOf(realObservedPeak + 4, ageFormulaMax)
        realObservedPeak > 0 -> maxOf(ageFormulaMax, realObservedPeak)
        else -> ageFormulaMax
    }
}

/**
 * Calcula as faixas de BPM das 5 Zonas Cardíacas calibradas dinamicamente pela `effectiveMaxHr`:
 * - Z1 (Leve / Recuperação): < 62% da FCmáx
 * - Z2 (Intensa / Queima de Gordura): 62% a 71% da FCmáx
 * - Z3 (Aeróbica): 71% a 82,5% da FCmáx
 * - Z4 (Anaeróbica / Limiar): 82,5% a 92% da FCmáx
 * - Z5 (VO2 máximo / Limite): > 92% da FCmáx
 */
fun buildCalibratedHrZoneRanges(effectiveMaxHr: Int): List<Pair<Int, Int>> {
    val safeMax = effectiveMaxHr.coerceIn(120, 225)
    val z1Max = (safeMax * 0.62f).roundToInt()
    val z2Max = (safeMax * 0.71f).roundToInt().coerceAtLeast(z1Max + 8)
    val z3Max = (safeMax * 0.825f).roundToInt().coerceAtLeast(z2Max + 10)
    val z4Max = (safeMax * 0.92f).roundToInt().coerceAtLeast(z3Max + 10)
    return listOf(
        0 to z1Max,
        (z1Max + 1) to z2Max,
        (z2Max + 1) to z3Max,
        (z3Max + 1) to z4Max,
        (z4Max + 1) to 245
    )
}

/**
 * Calcula a distribuição real e fisiologicamente coerente de tempo e percentual nas 5 zonas (Z1 a Z5).
 * - Usa as amostras reais (`hrSamples`) quando disponíveis.
 * - Caso uma sessão antiga não tenha vetor de amostras detalhado mas possua `avgBpm` e `maxBpm`,
 *   reconstrói uma distribuição fisiológica coerente entre a zona média e a zona do pico (`maxBpm`).
 * - Garante que a soma dos percentuais feche em 100% e que a zona correspondente ao `Pico Sessão` (`maxBpm`)
 *   nunca apareça com 0%.
 */
fun computeCoherent5ZoneDistribution(
    hrSamples: List<Int>,
    avgBpm: Int?,
    maxBpm: Int?,
    effectiveMaxHr: Int,
    durationSeconds: Int
): List<HrZone5Item> {
    val zoneDefs = listOf(
        Triple(1, "Leve", 0xFF29B6F6L),        // Z1 - Azul #29B6F6 (< 62% FCmáx)
        Triple(2, "Intensa", 0xFF00E676L),     // Z2 - Verde #00E676 (62%..71% FCmáx)
        Triple(3, "Aeróbica", 0xFFFFCA28L),    // Z3 - Amarelo/Âmbar #FFCA28 (71%..82,5% FCmáx)
        Triple(4, "Anaeróbica", 0xFFFF7043L),  // Z4 - Laranja #FF7043 (82,5%..92% FCmáx)
        Triple(5, "VO2 máximo", 0xFFFF5252L)   // Z5 - Vermelho #FF5252 (> 92% FCmáx)
    )
    val zoneRanges = buildCalibratedHrZoneRanges(effectiveMaxHr)
    val validSamples = hrSamples.filter { it in 45..240 }
    val safeDurationSec = durationSeconds.coerceAtLeast(0)

    fun zoneIndexForBpm(bpm: Int): Int {
        for (i in zoneRanges.indices) {
            val (minB, maxB) = zoneRanges[i]
            if (bpm in minB..maxB) return i
        }
        return if (bpm < zoneRanges.first().first) 0 else zoneRanges.lastIndex
    }

    val effectivePeakBpm = maxOf(maxBpm ?: 0, validSamples.maxOrNull() ?: 0, avgBpm ?: 0)
    val effectiveAvgBpm = avgBpm?.takeIf { it > 0 }
        ?: if (validSamples.isNotEmpty()) validSamples.average().roundToInt() else 0

    if (validSamples.isEmpty() && effectiveAvgBpm <= 0 && effectivePeakBpm <= 0) {
        return zoneDefs.mapIndexed { idx, (zNum, zLabel, zColor) ->
            val (minB, maxB) = zoneRanges[idx]
            HrZone5Item(
                zoneIndex = zNum,
                label = zLabel,
                minBpm = minB,
                maxBpm = maxB,
                colorHex = zColor,
                durationSeconds = 0,
                percentage = 0f
            )
        }
    }

    val weights = FloatArray(5)
    if (validSamples.size >= 5) {
        for (bpm in validSamples) {
            weights[zoneIndexForBpm(bpm)] += 1f
        }
    } else {
        // Reconstrução fisiológica coerente quando só há avgBpm / maxBpm ou pouquíssimas amostras
        val avgIdx = zoneIndexForBpm(effectiveAvgBpm.coerceAtLeast(95))
        val peakIdx = zoneIndexForBpm(effectivePeakBpm.coerceAtLeast(effectiveAvgBpm))
        when {
            peakIdx == avgIdx -> {
                weights[avgIdx] = 68f
                if (avgIdx > 0) weights[avgIdx - 1] = 22f
                if (avgIdx > 1) weights[avgIdx - 2] = 10f
                else if (avgIdx < 4) weights[avgIdx + 1] = 10f
            }
            peakIdx == avgIdx + 1 -> {
                weights[avgIdx] = 52f
                weights[peakIdx] = 24f
                if (avgIdx > 0) weights[avgIdx - 1] = 18f
                if (avgIdx > 1) weights[avgIdx - 2] = 6f
            }
            peakIdx >= avgIdx + 2 -> {
                weights[avgIdx] = 44f
                weights[(avgIdx + 1).coerceAtMost(4)] = 26f
                weights[peakIdx] = 14f
                if (avgIdx > 0) weights[avgIdx - 1] = 12f
                if (avgIdx > 1) weights[avgIdx - 2] = 4f
            }
            else -> {
                weights[avgIdx] = 75f
                if (avgIdx > 0) weights[avgIdx - 1] = 25f
            }
        }
    }

    // Garante que a zona correspondente ao Pico Sessão nunca fique com 0% se effectivePeakBpm > 0
    if (effectivePeakBpm > 0) {
        val peakZoneIdx = zoneIndexForBpm(effectivePeakBpm)
        val totalW = weights.sum().coerceAtLeast(1f)
        val currentPeakShare = (weights[peakZoneIdx] / totalW) * 100f
        if (currentPeakShare < 4f) {
            val neededWeight = totalW * 0.07f - weights[peakZoneIdx]
            if (neededWeight > 0f) {
                weights[peakZoneIdx] += neededWeight
                // Se havia lacuna entre a zona predominante e o pico, garante transição orgânica
                if (peakZoneIdx > 0 && weights[peakZoneIdx - 1] <= 0f) {
                    weights[peakZoneIdx - 1] = totalW * 0.12f
                }
            }
        }
    }

    val totalWeight = weights.sum().coerceAtLeast(1f)
    val rawPcts = weights.map { (it / totalWeight) * 100f }
    val roundedPcts = IntArray(5) { rawPcts[it].roundToInt() }

    // Garante que a zona de Pico tenha pelo menos 1% após arredondamento
    if (effectivePeakBpm > 0) {
        val peakZoneIdx = zoneIndexForBpm(effectivePeakBpm)
        if (roundedPcts[peakZoneIdx] == 0) {
            roundedPcts[peakZoneIdx] = 4
        }
    }

    // Fecha exatamente em 100% ajustando na zona de maior participação
    val pctSum = roundedPcts.sum()
    if (pctSum != 100 && pctSum > 0) {
        val dominantIdx = roundedPcts.indices.maxByOrNull { roundedPcts[it] } ?: 0
        roundedPcts[dominantIdx] = (roundedPcts[dominantIdx] + (100 - pctSum)).coerceAtLeast(1)
    }

    var assignedSec = 0
    val lastNonZeroIdx = roundedPcts.indices.lastOrNull { roundedPcts[it] > 0 } ?: 4

    return zoneDefs.mapIndexed { idx, (zNum, zLabel, zColor) ->
        val (minB, maxB) = zoneRanges[idx]
        val pctInt = roundedPcts[idx].coerceIn(0, 100)
        val zSec = if (pctInt == 0 || safeDurationSec == 0) {
            0
        } else if (idx == lastNonZeroIdx) {
            (safeDurationSec - assignedSec).coerceAtLeast(1)
        } else {
            val s = ((pctInt / 100f) * safeDurationSec).roundToInt().coerceAtLeast(1)
            assignedSec += s
            s
        }
        HrZone5Item(
            zoneIndex = zNum,
            label = zLabel,
            minBpm = minB,
            maxBpm = maxB,
            colorHex = zColor,
            durationSeconds = zSec,
            percentage = pctInt.toFloat()
        )
    }
}

/**
 * Gera a lista de splits dinamicamente conforme o modo selecionado no Seletor de Splits:
 * - BY_BLOCK: Por Bloco/Sprint (cada tiro de corrida e caminhada separadamente).
 * - HALF_KM: Parciais reais a cada 500 m (0,5 km) integradas sobre os segmentos GPS / blocos reais executados.
 * - ONE_KM_MAIN: Parciais reais de 1 km filtrando apenas o treino principal (batendo com a distância principal do topo).
 * Elimina ciclos sintéticos de pace (`index % 3`) e escadinhas artificiais de `+2 bpm`, mantendo
 * ZERO diferença de arredondamento entre PACE e TEMPO.
 */
fun buildSplitsForMode(
    session: RunningSession,
    mode: SplitSelectorMode,
    gpsPoints: List<GpsTrackPoint> = emptyList()
): List<RecordedKmSplit> {
    if (session.distanceMeters <= 0 && (session.mainRunDistanceMeters ?: 0) <= 0) {
        return emptyList()
    }
    val recordedHrList = session.hrTimeline.toHrTimelineList().filter { it in 45..240 }
    val routeItems = session.routePointsJson.toRoutePointItems()

    val effectiveGpsPoints: List<GpsTrackPoint> = if (gpsPoints.size >= 2) {
        gpsPoints.filter { it.latitude != 0.0 && it.longitude != 0.0 }.sortedBy { it.timestampMs }
    } else {
        routeItems
            .filter { it.lat != 0.0 && it.lon != 0.0 && it.timestamp > 0L }
            .sortedBy { it.timestamp }
            .map { pt ->
                GpsTrackPoint(
                    sessionId = session.id,
                    timestampMs = pt.timestamp,
                    capturedAtMs = pt.timestamp,
                    latitude = pt.lat,
                    longitude = pt.lon,
                    accuracyM = pt.accuracy,
                    speedMps = pt.speed,
                    altitudeM = pt.altitude
                )
            }
    }

    // 1. Calcula parciais reais por bloco somente quando existem pontos GPS gravados com marcação real de bloco/fase
    fun buildRealRecordedBlockSplits(): List<RecordedKmSplit> {
        val validPts = routeItems
            .filter { it.lat != 0.0 && it.lon != 0.0 && it.timestamp > 0L }
            .sortedBy { it.timestamp }
        if (validPts.size < 2) return emptyList()

        fun normalizePhase(raw: String): String = when (raw.trim().uppercase(Locale.US)) {
            "WARMUP", "WARM_UP" -> "WARMUP"
            "COOL_DOWN", "COOLDOWN" -> "COOL_DOWN"
            "WALK", "RECOVERY" -> "WALK"
            "RUN", "SPRINT", "INTERVAL", "PR" -> "RUN"
            else -> ""
        }

        val taggedPts = validPts.map { it to normalizePhase(it.phase) }
        val distinctGroups = taggedPts.map { (pt, ph) -> pt.blockIndex to ph }.distinct()
        val hasRecordedBlockStructure = taggedPts.all { it.second.isNotEmpty() } && distinctGroups.size >= 2
        if (!hasRecordedBlockStructure) return emptyList()

        val sessionStartTs = validPts.first().timestamp
        val sessionSpanMs = (validPts.last().timestamp - sessionStartTs).coerceAtLeast(1L)

        data class RawBlockRun(
            val blockIndex: Int,
            val phase: String,
            val distM: Double,
            val durSec: Double,
            val startTs: Long,
            val endTs: Long
        )

        val blocksAcc = mutableListOf<RawBlockRun>()
        var curBlockIdx = taggedPts.first().first.blockIndex
        var curPhase = taggedPts.first().second
        var curStartTs = taggedPts.first().first.timestamp
        var curEndTs = curStartTs
        var curDist = 0.0
        var curDur = 0.0

        for (i in 1 until taggedPts.size) {
            val (pPrev, _) = taggedPts[i - 1]
            val (pCur, phCur) = taggedPts[i]
            val sameBlock = pCur.blockIndex == curBlockIdx && phCur == curPhase
            val dM = com.example.services.GpsEvaluator.distanceBetweenMeters(
                pPrev.lat, pPrev.lon, pCur.lat, pCur.lon
            )
            val dtSec = (pCur.timestamp - pPrev.timestamp) / 1000.0
            if (sameBlock) {
                if (dM in 0.5..120.0 && dtSec > 0.0 && dtSec <= 60.0) {
                    curDist += dM
                    curDur += dtSec
                }
                curEndTs = pCur.timestamp
            } else {
                blocksAcc.add(RawBlockRun(curBlockIdx, curPhase, curDist, curDur, curStartTs, curEndTs))
                curBlockIdx = pCur.blockIndex
                curPhase = phCur
                curStartTs = pCur.timestamp
                curEndTs = pCur.timestamp
                curDist = 0.0
                curDur = 0.0
            }
        }
        blocksAcc.add(RawBlockRun(curBlockIdx, curPhase, curDist, curDur, curStartTs, curEndTs))

        val mainBlocks = blocksAcc.filter { it.phase == "RUN" || it.phase == "WALK" }.ifEmpty { blocksAcc }
        val validBlocks = mainBlocks.filter { it.distM >= 10.0 && it.durSec >= 5.0 }
        if (validBlocks.isEmpty()) return emptyList()

        var runCounter = 0
        var walkCounter = 0
        val built = validBlocks.mapIndexed { idx, blk ->
            val isRun = blk.phase == "RUN"
            val distInt = blk.distM.roundToInt().coerceAtLeast(1)
            val durInt = blk.durSec.roundToInt().coerceAtLeast(1)
            val paceSec = ((blk.durSec * 1000.0) / blk.distM).roundToInt().coerceIn(140, 3600)

            val label = when (blk.phase) {
                "RUN" -> {
                    runCounter++
                    "Tiro $runCounter"
                }
                "WALK" -> {
                    walkCounter++
                    "Rec. $walkCounter"
                }
                "WARMUP" -> "Aquecimento"
                "COOL_DOWN" -> "Desaquecimento"
                else -> "Bloco ${idx + 1}"
            }

            val blockHr: Int? = if (recordedHrList.size >= 2) {
                val startFrac = ((blk.startTs - sessionStartTs).toDouble() / sessionSpanMs.toDouble()).coerceIn(0.0, 1.0)
                val endFrac = ((blk.endTs - sessionStartTs).toDouble() / sessionSpanMs.toDouble()).coerceIn(startFrac, 1.0)
                val i0 = (startFrac * (recordedHrList.size - 1)).safeRoundToInt().safeCoerceIn(0, recordedHrList.lastIndex)
                val i1 = (endFrac * (recordedHrList.size - 1)).safeRoundToInt().safeCoerceIn(i0, recordedHrList.lastIndex)
                recordedHrList.subList(i0, i1 + 1).takeIf { it.isNotEmpty() }?.average()?.safeRoundToInt()
            } else null

            RecordedKmSplit(
                splitNumber = idx + 1,
                splitLabel = label,
                distanceMeters = distInt,
                durationSec = durInt,
                paceSecPerKm = paceSec,
                avgHr = blockHr,
                phaseTag = if (isRun) "RUN" else blk.phase
            )
        }.toMutableList()

        // Marca "★" apenas no bloco medido com menor ritmo (mais rápido)
        val candidateIndices = built.indices.filter { built[it].phaseTag == "RUN" }.ifEmpty { built.indices.toList() }
        val fastestIdx = candidateIndices.minByOrNull { built[it].paceSecPerKm }
        if (fastestIdx != null && built.size >= 2) {
            val fastest = built[fastestIdx]
            built[fastestIdx] = fastest.copy(
                splitLabel = if (fastest.splitLabel.contains("★")) fastest.splitLabel else "${fastest.splitLabel} ★",
                phaseTag = "PR"
            )
        }
        return built
    }

    // 2. Integra apenas segmentos GPS reais a cada janela de distância (500m ou 1000m). Sem >= 6 pontos GPS, retorna lista vazia.
    fun sliceSplitsByDistance(
        stepSizeM: Int,
        isHalfKm: Boolean,
        useKmNLabel: Boolean = false
    ): List<RecordedKmSplit> {
        if (effectiveGpsPoints.size < 6) return emptyList()

        data class GpsSegment(val distM: Double, val dtSec: Double, val midSec: Double)
        val gpsSegments = mutableListOf<GpsSegment>()
        val t0 = effectiveGpsPoints.first().timestampMs
        var rawDistSum = 0.0
        var rawTimeSum = 0.0
        for (i in 1 until effectiveGpsPoints.size) {
            val p0 = effectiveGpsPoints[i - 1]
            val p1 = effectiveGpsPoints[i]
            val d = com.example.services.GpsEvaluator.distanceBetweenMeters(
                p0.latitude, p0.longitude, p1.latitude, p1.longitude
            )
            val dt = (p1.timestampMs - p0.timestampMs) / 1000.0
            if (d in 0.5..120.0 && dt > 0.0 && dt <= 60.0) {
                val s0 = ((p0.timestampMs - t0) / 1000.0).coerceAtLeast(0.0)
                rawDistSum += d
                rawTimeSum += dt
                gpsSegments.add(GpsSegment(d, dt, s0 + dt * 0.5))
            }
        }
        if (gpsSegments.isEmpty() || rawDistSum < 20.0 || rawTimeSum <= 5.0) {
            return emptyList()
        }

        val totalGpsSpanSec = ((effectiveGpsPoints.last().timestampMs - t0) / 1000.0)
            .coerceAtLeast(rawTimeSum)
            .coerceAtLeast(1.0)

        data class DistanceSliceAtom(
            val distMeters: Double,
            val durationSec: Double,
            val hrBpm: Double?
        )

        val atoms = gpsSegments.map { seg ->
            val midFrac = (seg.midSec / totalGpsSpanSec).coerceIn(0.0, 1.0)
            val hrVal: Double? = if (recordedHrList.size >= 2) {
                val idx = (midFrac * (recordedHrList.size - 1)).roundToInt().coerceIn(0, recordedHrList.lastIndex)
                recordedHrList[idx].toDouble()
            } else null
            DistanceSliceAtom(seg.distM, seg.dtSec, hrVal)
        }

        val totalMeasuredM = rawDistSum.roundToInt().coerceAtLeast(1)
        val splits = mutableListOf<RecordedKmSplit>()
        var remainingTotalM = totalMeasuredM.toDouble()
        var splitIndex = 1
        var accumulatedDistM = 0.0
        var atomCursor = 0
        var atomRemainingDist = atoms.first().distMeters

        while (remainingTotalM >= 10.0 && atomCursor < atoms.size) {
            val chunkM = minOf(remainingTotalM, stepSizeM.toDouble())
            var neededM = chunkM
            var chunkDurAcc = 0.0
            var chunkHrWeightedAcc = 0.0
            var chunkHrDurAcc = 0.0

            while (neededM > 0.01 && atomCursor < atoms.size) {
                val atom = atoms[atomCursor]
                val takeM = minOf(neededM, atomRemainingDist)
                if (takeM > 0.0) {
                    val ratio = takeM / atom.distMeters.coerceAtLeast(0.001)
                    val takeDur = atom.durationSec * ratio
                    chunkDurAcc += takeDur
                    if (atom.hrBpm != null && atom.hrBpm > 0.0) {
                        chunkHrWeightedAcc += atom.hrBpm * takeDur
                        chunkHrDurAcc += takeDur
                    }
                    neededM -= takeM
                    atomRemainingDist -= takeM
                }
                if (atomRemainingDist <= 0.01) {
                    atomCursor++
                    if (atomCursor < atoms.size) {
                        atomRemainingDist = atoms[atomCursor].distMeters
                    }
                }
            }

            val actualChunkM = (chunkM - neededM).roundToInt()
            val chunkDurSec = chunkDurAcc.roundToInt()
            if (actualChunkM < 10 || chunkDurSec <= 0) break

            val exactPaceSec = ((chunkDurAcc * 1000.0) / actualChunkM.toDouble())
                .roundToInt()
                .coerceIn(140, 3600)

            accumulatedDistM += actualChunkM
            val accumKm = accumulatedDistM / 1000.0
            val label = when {
                useKmNLabel -> "Km $splitIndex"
                isHalfKm -> {
                    if (actualChunkM in 495..505) {
                        String.format(Locale.US, "%.1f km", accumKm).replace('.', ',')
                    } else {
                        String.format(Locale.US, "%.2f km", accumKm).replace('.', ',')
                    }
                }
                else -> {
                    if (actualChunkM in 995..1005) {
                        "$splitIndex,0 km"
                    } else {
                        String.format(Locale.US, "%.2f km", accumKm).replace('.', ',')
                    }
                }
            }

            val splitHr: Int? = if (chunkHrDurAcc > 0.1) {
                (chunkHrWeightedAcc / chunkHrDurAcc).safeRoundToInt().takeIf { it in 45..240 }
            } else null

            splits.add(
                RecordedKmSplit(
                    splitNumber = splitIndex,
                    splitLabel = label,
                    distanceMeters = actualChunkM,
                    durationSec = chunkDurSec,
                    paceSecPerKm = exactPaceSec,
                    avgHr = splitHr,
                    phaseTag = "RUN"
                )
            )

            remainingTotalM -= actualChunkM
            splitIndex++
        }

        if (splits.size >= 2) {
            val fullSplits = splits.filter { it.distanceMeters >= stepSizeM * 0.8 }
            val fastestPace = (fullSplits.ifEmpty { splits }).minOfOrNull { it.paceSecPerKm }
            if (fastestPace != null) {
                val fastestIdx = splits.indexOfFirst { it.paceSecPerKm == fastestPace && it.distanceMeters >= stepSizeM * 0.8 }
                    .takeIf { it >= 0 } ?: splits.indexOfFirst { it.paceSecPerKm == fastestPace }
                if (fastestIdx in splits.indices) {
                    splits[fastestIdx] = splits[fastestIdx].copy(phaseTag = "PR")
                }
            }
        }

        return splits
    }

    return when (mode) {
        SplitSelectorMode.BY_BLOCK -> {
            if (RunningProgramManager.isFreeOrRaceSessionType(session.sessionType)) {
                val saved = session.kmSplitsJson.toRecordedKmSplits()
                if (saved.isNotEmpty()) {
                    val fastestPace = saved.filter { it.distanceMeters >= 400 }.minOfOrNull { it.paceSecPerKm }
                        ?: saved.minOfOrNull { it.paceSecPerKm }
                    saved.map { s ->
                        val isPr = fastestPace != null && saved.size >= 2 && s.paceSecPerKm == fastestPace
                        s.copy(
                            splitLabel = if (isPr && !s.splitLabel.contains("★")) "${s.splitLabel} ★" else s.splitLabel,
                            phaseTag = if (isPr) "PR" else "RUN"
                        )
                    }
                } else {
                    sliceSplitsByDistance(stepSizeM = 1000, isHalfKm = false, useKmNLabel = true)
                }
            } else {
                val realBlockSplits = buildRealRecordedBlockSplits()
                if (realBlockSplits.isNotEmpty()) {
                    realBlockSplits
                } else {
                    sliceSplitsByDistance(stepSizeM = 1000, isHalfKm = false, useKmNLabel = true)
                }
            }
        }
        SplitSelectorMode.HALF_KM -> sliceSplitsByDistance(stepSizeM = 500, isHalfKm = true)
        SplitSelectorMode.ONE_KM_MAIN -> {
            val saved = session.kmSplitsJson.toRecordedKmSplits()
            if (saved.isNotEmpty()) {
                val fastestPace = saved.filter { it.distanceMeters >= 400 }.minOfOrNull { it.paceSecPerKm }
                    ?: saved.minOfOrNull { it.paceSecPerKm }
                saved.map { s ->
                    val isPr = fastestPace != null && saved.size >= 2 && s.paceSecPerKm == fastestPace
                    s.copy(phaseTag = if (isPr) "PR" else "RUN")
                }
            } else {
                sliceSplitsByDistance(stepSizeM = 1000, isHalfKm = false)
            }
        }
    }
}

data class RunnerProfile(
    val heightCm: Int? = null,
    val weightKg: Float? = null,
    val runningGoal: String = "CONTINUOUS_5K",
    val baselinePaceMinPerKm: Float? = null
)

data class RunningShoe(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val brand: String = "",
    val initialDistanceKm: Float = 0f,
    val totalDistanceKm: Float = initialDistanceKm,
    val maxDistanceKm: Float = 600f,
    val maxLifespanKm: Float = maxDistanceKm,
    val isDefault: Boolean = false,
    val isRetired: Boolean = false,
    val photoUri: String? = null
) {
    fun fullDisplayName(): String {
        return if (brand.isNotBlank() && !name.startsWith(brand, ignoreCase = true)) {
            "$brand $name".trim()
        } else {
            name.trim()
        }
    }
}

/**
 * Amostra individual da série temporal de telemetria da corrida (Etapa 1 - Dossiê MAX RUN).
 */
data class RunTelemetrySample(
    val elapsedSeconds: Int,
    val paceSecPerKm: Float,
    val heartRateBpm: Int,
    val cadenceSpm: Int,
    val strideLengthCm: Int,
    val phaseTag: String = "RUN" // "WARMUP", "RUN", "WALK", "COOL_DOWN", "PR"
)

/**
 * Parcial de quilômetro estilo Mi Fitness / Garmin ("1", "2", "<3" com pace dentro da barra e destaque do km mais rápido).
 */
data class KmSplit(
    val kmNumber: Int,
    val displayLabel: String, // "1", "2", "<2", "<3", etc.
    val distanceMeters: Int,
    val distanceKm: Float,
    val isPartialLastKm: Boolean,
    val durationSec: Int,
    val paceSecPerKm: Int,
    val avgHrBpm: Int? = null,
    val isFastest: Boolean = false
) {
    val formattedPace: String
        get() = if (paceSecPerKm > 0) {
            String.format(Locale.US, "%d'%02d\"", paceSecPerKm / 60, paceSecPerKm % 60)
        } else "--'--"

    val formattedDistanceSubLabel: String
        get() = if (isPartialLastKm) {
            String.format(Locale("pt", "BR"), "%.2f km", distanceKm)
        } else {
            "1,00 km"
        }
}

/**
 * Distribuição nas 5 Zonas de Frequência Cardíaca (Leve, Intensa, Aeróbica, Anaeróbica, VO2 máximo).
 */
data class HrZone5Item(
    val zoneIndex: Int, // 1..5
    val label: String,  // "Leve", "Intensa", "Aeróbica", "Anaeróbica", "VO2 máximo"
    val minBpm: Int,
    val maxBpm: Int,
    val colorHex: Long,
    val durationSeconds: Int,
    val percentage: Float
) {
    val formattedDuration: String
        get() = String.format(Locale.US, "%02d:%02d", durationSeconds / 60, durationSeconds % 60)
}

/**
 * Dossiê Fisiológico e Biomecânico consolidado da sessão de corrida (Etapas 1 & 2).
 */
data class RunPhysiologyDossier(
    val badgeTitle: String,
    val athleteName: String,
    val formattedDateTime: String,
    val distanceKm: Float,
    val totalDistanceWithWarmupKm: Float,
    val hasMainRunIsolation: Boolean,
    val durationSeconds: Int,
    val totalDurationWithWarmupSeconds: Int,
    val formattedDurationHhMmSs: String,
    val activeCaloriesKcal: Int,
    val basalCaloriesKcal: Int,
    val totalCaloriesKcal: Int,
    val isWeightMissingForCalories: Boolean = false,
    val totalActiveCaloriesWithWarmupKcal: Int = activeCaloriesKcal,
    val totalCaloriesWithWarmupKcal: Int = totalCaloriesKcal,
    val avgPaceSecPerKm: Int,
    val maxPaceSecPerKm: Int,
    val formattedAvgPace: String,
    val formattedMaxPace: String,
    val avgHeartRateBpm: Int,
    val maxHeartRateBpm: Int,
    val effectiveMaxHeartRateBpm: Int = 0,
    val calibratedLt1Bpm: Int? = null,
    val calibratedLt2Bpm: Int? = null,
    val isHrEstimated: Boolean,
    val totalSteps: Int,
    val totalStepsWithWarmup: Int = totalSteps,
    val avgCadenceSpm: Int,
    val maxCadenceSpm: Int,
    val avgStrideLengthCm: Int,
    val maxStrideLengthCm: Int = 0,
    val vitalityScoreGain: Int = 0,
    val trainingEffectTitle: String = "Sem dados de FC para classificar o efeito do treino",
    val hybridRecoveryImpact: String = "Sem dados de FC suficientes para inferir carga ou impacto de recuperação.",
    val dataSourceLabel: String = "MAX RUN • Registro manual",
    val firstIntervalPaceSecPerKm: Int = 0,
    val lastIntervalPaceSecPerKm: Int = 0,
    val paceFadePct: Float = 0f,
    val consistencyVerdictTitle: String = "Consistência indisponível — dados de ritmo insuficientes",
    val consistencyVerdictDetail: String = "São necessárias ao menos 2 parciais medidas ou série de GPS para avaliar a variação de ritmo.",
    val telemetrySamples: List<RunTelemetrySample>,
    val hrZones5: List<HrZone5Item>,
    val kmSplits: List<KmSplit>
)

fun formatSecondsToHhMmSs(totalSeconds: Int): String {
    val safe = totalSeconds.coerceAtLeast(0)
    val h = safe / 3600
    val m = (safe % 3600) / 60
    val s = safe % 60
    return String.format(Locale.US, "%02d:%02d:%02d", h, m, s)
}

fun formatPaceMinSecQuote(paceSecPerKm: Int): String {
    if (paceSecPerKm <= 0) return "--'--\""
    val pMin = paceSecPerKm / 60
    val pSec = paceSecPerKm % 60
    return String.format(Locale.US, "%d'%02d\"", pMin, pSec)
}

/**
 * Extrai amostras válidas de FC (45..240 bpm) para a curva do gráfico,
 * priorizando `dossier.telemetrySamples` e usando `hrTimeline` como fallback.
 */
fun extractValidHrCurveSamples(
    dossier: RunPhysiologyDossier,
    hrTimeline: String? = null
): List<Int> {
    return dossier.telemetrySamples
        .map { it.heartRateBpm }
        .filter { it in 45..240 }
        .ifEmpty {
            hrTimeline.toHrTimelineList().filter { it in 45..240 }
        }
}

/**
 * Regra única: existe curva de FC desenhável quando há ao menos 2 leituras válidas (45..240 bpm).
 */
fun hasHrCurve(samples: List<Int>): Boolean = samples.count { it in 45..240 } >= 2

/**
 * Regra única: existem zonas de FC exibíveis quando a lista não está vazia e há percentual > 0.
 */
fun hasHrZones(zones: List<HrZone5Item>): Boolean =
    zones.isNotEmpty() && zones.any { it.percentage > 0f }

fun hasHrZones(dossier: RunPhysiologyDossier): Boolean = hasHrZones(dossier.hrZones5)

/**
 * Aviso curto padrão quando não há curva de FC:
 * - Se média ou máxima > 0: "Curva de FC não registrada"
 * - Se ambas forem 0: "Sem dados de FC"
 */
fun hrCurveUnavailableMessage(avgHrBpm: Int, maxHrBpm: Int): String =
    if (avgHrBpm > 0 || maxHrBpm > 0) "Curva de FC não registrada" else "Sem dados de FC"

/**
 * Função única de cálculo de calorias ativas de corrida (`pesoKg × km × 1.036`).
 * Usa o peso real do perfil (via `resolveValidWeightKgOrNull` / `resolveWeightKg`); sem peso válido (> 0),
 * retorna `null` em vez de assumir 70 kg silenciosamente.
 */
fun calculateRunActiveCaloriesKcal(
    distanceMeters: Int,
    userProfile: UserProfileData?,
    weightKgOverride: Float? = null
): Int? {
    val validWeightKg = weightKgOverride?.takeIf { it > 0f }
        ?: resolveValidWeightKgOrNull(userProfile)
        ?: return null
    if (distanceMeters <= 0) return 0
    return ((distanceMeters / 1000.0) * validWeightKg * 1.036)
        .roundToInt()
        .coerceAtLeast(1)
}

/**
 * Deriva a série temporal de ritmo (elapsedSec, paceSecPerKm) exclusivamente a partir
 * das coordenadas e timestamps reais de GPS, usando janela móvel curta de 3 segmentos.
 * Retorna lista vazia se houver menos de 2 pontos GPS válidos.
 */
internal fun computeRealGpsPaceSeries(gpsPoints: List<GpsTrackPoint>): List<Pair<Int, Float>> {
    if (gpsPoints.size < 2) return emptyList()
    val validPts = gpsPoints
        .filter { it.latitude != 0.0 && it.longitude != 0.0 && it.timestampMs > 0L }
        .sortedBy { it.timestampMs }
    if (validPts.size < 2) return emptyList()

    val startTs = validPts.first().timestampMs
    data class GpsSeg(val elapsedSec: Int, val distM: Double, val dtSec: Double)

    val segments = mutableListOf<GpsSeg>()
    for (i in 1 until validPts.size) {
        val p0 = validPts[i - 1]
        val p1 = validPts[i]
        val dtSec = (p1.timestampMs - p0.timestampMs) / 1000.0
        if (dtSec <= 0.0 || dtSec > 60.0) continue
        val dM = com.example.services.GpsEvaluator.distanceBetweenMeters(
            p0.latitude, p0.longitude, p1.latitude, p1.longitude
        )
        if (dM < 0.5 || dM > 120.0) continue
        val speedMps = dM / dtSec
        if (speedMps > 12.0) continue
        val elapsedSec = ((p1.timestampMs - startTs) / 1000L).toInt().coerceAtLeast(0)
        segments.add(GpsSeg(elapsedSec, dM, dtSec))
    }
    if (segments.isEmpty()) return emptyList()

    val result = mutableListOf<Pair<Int, Float>>()
    for (i in segments.indices) {
        val wStart = (i - 1).coerceAtLeast(0)
        val wEnd = (i + 1).coerceAtMost(segments.lastIndex)
        var wDist = 0.0
        var wDt = 0.0
        for (j in wStart..wEnd) {
            wDist += segments[j].distM
            wDt += segments[j].dtSec
        }
        if (wDist >= 1.0 && wDt > 0.0) {
            val paceSec = ((wDt * 1000.0) / wDist).toFloat().coerceIn(140f, 3600f)
            result.add(segments[i].elapsedSec to paceSec)
        }
    }
    return result
}

/**
 * Constrói o Dossiê Fisiológico e Biomecânico completo da sessão a partir de dados efetivamente medidos.
 */
fun buildRunPhysiologyDossier(
    session: RunningSession,
    userProfile: UserProfileData?,
    gpsPoints: List<GpsTrackPoint> = emptyList()
): RunPhysiologyDossier {
    val ptBr = Locale("pt", "BR")
    val sprintAnalytics = calculateSprintAnalytics(session, gpsPoints)

    val hasMainRunIsolation = session.mainRunDistanceMeters != null &&
            session.mainRunDistanceMeters > 0 &&
            session.mainRunDurationSec != null &&
            session.mainRunDurationSec > 0 &&
            (session.mainRunDurationSec < session.durationSec || session.mainRunDistanceMeters < session.distanceMeters)

    val effectiveDurationSec = (if (hasMainRunIsolation) session.mainRunDurationSec!! else session.durationSec).coerceAtLeast(1)
    val totalSessionDurationSec = session.durationSec.coerceAtLeast(effectiveDurationSec)

    val effectiveDistanceMeters = (if (hasMainRunIsolation) session.mainRunDistanceMeters!! else session.distanceMeters)
        .coerceAtLeast(0)
    val totalSessionDistanceMeters = session.distanceMeters.coerceAtLeast(effectiveDistanceMeters)
    val hasRealDisplacement = totalSessionDistanceMeters > 0

    val distKm = effectiveDistanceMeters / 1000f
    val totalDistKm = totalSessionDistanceMeters / 1000f

    val avgPaceSec = if (!hasRealDisplacement) {
        0
    } else {
        when {
            effectiveDistanceMeters > 0 && effectiveDurationSec > 0 ->
                ((effectiveDurationSec * 1000.0) / effectiveDistanceMeters.toDouble()).roundToInt()
            hasMainRunIsolation && session.mainRunAvgPaceSecPerKm != null && session.mainRunAvgPaceSecPerKm > 0 ->
                session.mainRunAvgPaceSecPerKm
            session.avgPaceSecPerKm > 0 -> session.avgPaceSecPerKm
            else -> 0
        }.let { if (it > 0) it.coerceIn(140, 3600) else 0 }
    }

    // 1. Calorias Ativas vs. Total de Kcal (função única com peso válido; sem peso válido, não calcula com 70 kg silencioso)
    val validWeightKg = resolveValidWeightKgOrNull(userProfile)
    val heightCm = userProfile?.heightCm?.takeIf { it > 0 } ?: userProfile?.altura?.takeIf { it > 0 }
    val ageYears = userProfile?.idade?.takeIf { it > 0 }
    val isFemale = userProfile?.sexo?.equals("Feminino", ignoreCase = true) == true

    val tmb24h: Float? = when {
        userProfile != null && userProfile.tmb > 800 -> userProfile.tmb.toFloat()
        validWeightKg != null && heightCm != null && ageYears != null -> {
            val baseMifflin = (10f * validWeightKg) + (6.25f * heightCm) - (5f * ageYears) + (if (isFemale) -161f else 5f)
            baseMifflin.coerceIn(1100f, 2800f)
        }
        else -> null
    }

    val durationMinutes = effectiveDurationSec / 60f
    val totalDurationMinutes = totalSessionDurationSec / 60f
    val adv = session.advancedMetrics

    val computedMainActiveKcal = if (!hasRealDisplacement) {
        0
    } else {
        calculateRunActiveCaloriesKcal(effectiveDistanceMeters, userProfile)
    }
    val syncedActiveKcal = adv?.watchSyncedActiveKcal?.takeIf { it > 0 }
    val isWeightMissingForCalories = hasRealDisplacement && syncedActiveKcal == null && computedMainActiveKcal == null
    val activeCaloriesKcal = syncedActiveKcal ?: computedMainActiveKcal ?: 0

    val basalProportionalKcal = if (!hasRealDisplacement || isWeightMissingForCalories || tmb24h == null) {
        0
    } else {
        ((tmb24h / 1440f) * durationMinutes).roundToInt().coerceAtLeast(1)
    }
    val totalCaloriesKcal = adv?.watchSyncedTotalKcal?.takeIf { it > 0 }
        ?: if (isWeightMissingForCalories) 0 else (activeCaloriesKcal + basalProportionalKcal)

    // Calorias totais incluindo aquecimento/desaquecimento (exibidas separadamente na legenda)
    val totalActiveCaloriesWithWarmupKcal = if (!hasRealDisplacement || isWeightMissingForCalories) {
        0
    } else if (syncedActiveKcal != null && !hasMainRunIsolation) {
        syncedActiveKcal
    } else {
        (calculateRunActiveCaloriesKcal(totalSessionDistanceMeters, userProfile) ?: activeCaloriesKcal)
            .coerceAtLeast(activeCaloriesKcal)
    }
    val totalBasalWithWarmupKcal = if (!hasRealDisplacement || isWeightMissingForCalories || tmb24h == null) {
        0
    } else {
        ((tmb24h / 1440f) * totalDurationMinutes).roundToInt().coerceAtLeast(basalProportionalKcal)
    }
    val totalCaloriesWithWarmupKcal = if (isWeightMissingForCalories) {
        0
    } else {
        (totalActiveCaloriesWithWarmupKcal + totalBasalWithWarmupKcal).coerceAtLeast(totalCaloriesKcal)
    }

    // 2. Identificação de Atleta, Badge e Data/Hora
    val rawName = userProfile?.nome?.trim().orEmpty()
    val athleteName = rawName.ifBlank { "Atleta" }
    val freeRaceMode = com.example.engine.FreeRunRaceMode.fromSessionType(session.sessionType)
    val badgeTitle = when {
        freeRaceMode != null -> "MAX RUN | ${freeRaceMode.fullTitle}"
        session.isGentleMode -> "MAX RUN | Caminhada regenerativa"
        session.environment.equals("TREADMILL", ignoreCase = true) -> "MAX RUN | Corrida na esteira"
        else -> "MAX RUN | Corrida ao ar livre"
    }
    val formattedDateTime = java.text.SimpleDateFormat("dd/MM/yyyy • HH:mm", ptBr).format(java.util.Date(session.date))

    // 3. Séries Temporais estritamente medidas (GPS real + hrTimeline real + dados reais do relógio, sem Random ou plano)
    val recordedHrList = session.hrTimeline.toHrTimelineList().filter { it in 45..240 }
    val hasRealHrSeries = recordedHrList.size >= 2
    val isHrEstimated = (session.avgHr == null || session.avgHr <= 0) && recordedHrList.isEmpty()

    val targetAvgHr = if (isHrEstimated) {
        0
    } else {
        (session.avgHr?.takeIf { it in 45..240 }
            ?: recordedHrList.takeIf { it.isNotEmpty() }?.average()?.roundToInt()
            ?: 0)
    }

    val targetMaxHr = if (isHrEstimated) {
        0
    } else {
        maxOf(
            session.maxHr?.takeIf { it >= targetAvgHr } ?: 0,
            recordedHrList.maxOrNull() ?: 0,
            targetAvgHr
        )
    }

    // Precedência: calibração no perfil -> fórmula etária -> 0 (indisponível)
    val effectiveMaxHr = calculateEffectiveMaxHr(
        ageYears = ageYears,
        sessionMaxHrBpm = targetMaxHr.takeIf { it > 0 },
        historicalPeakHrBpm = session.maxHr,
        calibratedMaxHrBpm = userProfile?.fcMaxCalibradaBpm
    )

    val realGpsPoints: List<GpsTrackPoint> = if (gpsPoints.size >= 2) {
        gpsPoints.filter { it.latitude != 0.0 && it.longitude != 0.0 }.sortedBy { it.timestampMs }
    } else {
        val fromRoute = session.routePointsJson.toRoutePointItems()
            .filter { it.lat != 0.0 && it.lon != 0.0 && it.timestamp > 0L }
            .sortedBy { it.timestamp }
        if (fromRoute.size >= 2) {
            fromRoute.map { pt ->
                GpsTrackPoint(
                    sessionId = session.id,
                    timestampMs = pt.timestamp,
                    capturedAtMs = pt.timestamp,
                    latitude = pt.lat,
                    longitude = pt.lon,
                    accuracyM = pt.accuracy,
                    speedMps = pt.speed,
                    altitudeM = pt.altitude
                )
            }
        } else {
            emptyList()
        }
    }

    // Ritmo por amostra derivado exclusivamente dos pontos GPS reais (janela móvel curta de 3 segmentos)
    val gpsPaceSeries: List<Pair<Int, Float>> = computeRealGpsPaceSeries(realGpsPoints)

    val realSampleCadence = adv?.watchSyncedCadenceSpm?.takeIf { it in 80..230 } ?: 0
    val realSampleStride = adv?.watchSyncedStrideCm?.takeIf { it in 40..220 } ?: 0

    val samples: List<RunTelemetrySample> = when {
        gpsPaceSeries.isEmpty() && !hasRealHrSeries -> emptyList()
        gpsPaceSeries.isEmpty() -> {
            // Somente FC gravada (sem GPS): preserva exatamente cada leitura de hrTimeline sem ruído
            val hrCount = recordedHrList.size
            recordedHrList.mapIndexed { idx, bpm ->
                val frac = idx.toFloat() / (hrCount - 1).coerceAtLeast(1).toFloat()
                RunTelemetrySample(
                    elapsedSeconds = (frac * effectiveDurationSec).roundToInt(),
                    paceSecPerKm = 0f,
                    heartRateBpm = bpm,
                    cadenceSpm = realSampleCadence,
                    strideLengthCm = realSampleStride,
                    phaseTag = "RUN"
                )
            }
        }
        !hasRealHrSeries -> {
            // Somente GPS real (sem série de FC >= 2 leituras)
            gpsPaceSeries.map { (elapsedSec, paceSec) ->
                RunTelemetrySample(
                    elapsedSeconds = elapsedSec,
                    paceSecPerKm = paceSec,
                    heartRateBpm = 0,
                    cadenceSpm = realSampleCadence,
                    strideLengthCm = realSampleStride,
                    phaseTag = "RUN"
                )
            }
        }
        gpsPaceSeries.size >= recordedHrList.size -> {
            val totalSpanSec = maxOf(gpsPaceSeries.last().first, effectiveDurationSec, 1)
            gpsPaceSeries.map { (elapsedSec, paceSec) ->
                val frac = (elapsedSec.toFloat() / totalSpanSec.toFloat()).coerceIn(0f, 1f)
                val hrIdx = (frac * (recordedHrList.size - 1)).roundToInt().coerceIn(0, recordedHrList.lastIndex)
                RunTelemetrySample(
                    elapsedSeconds = elapsedSec,
                    paceSecPerKm = paceSec,
                    heartRateBpm = recordedHrList[hrIdx],
                    cadenceSpm = realSampleCadence,
                    strideLengthCm = realSampleStride,
                    phaseTag = "RUN"
                )
            }
        }
        else -> {
            val totalSpanSec = maxOf(gpsPaceSeries.last().first, effectiveDurationSec, 1)
            val hrCount = recordedHrList.size
            recordedHrList.mapIndexed { idx, bpm ->
                val frac = idx.toFloat() / (hrCount - 1).coerceAtLeast(1).toFloat()
                val elapsedSec = (frac * totalSpanSec).roundToInt()
                val nearestGps = gpsPaceSeries.minByOrNull { kotlin.math.abs(it.first - elapsedSec) }
                RunTelemetrySample(
                    elapsedSeconds = elapsedSec,
                    paceSecPerKm = nearestGps?.second ?: 0f,
                    heartRateBpm = bpm,
                    cadenceSpm = realSampleCadence,
                    strideLengthCm = realSampleStride,
                    phaseTag = "RUN"
                )
            }
        }
    }

    val computedAvgHr = if (isHrEstimated) {
        0
    } else {
        session.avgHr?.takeIf { it > 0 }
            ?: recordedHrList.takeIf { it.isNotEmpty() }?.average()?.roundToInt()
            ?: 0
    }
    val computedMaxHr = if (isHrEstimated) {
        0
    } else {
        maxOf(
            session.maxHr ?: 0,
            recordedHrList.maxOrNull() ?: 0,
            computedAvgHr
        )
    }

    // Melhor ritmo: menor ritmo GPS real ou menor parcial salva; sem dado, usa avgPaceSec (sem teto artificial * 0.86)
    val maxPaceSec = if (!hasRealDisplacement) {
        0
    } else {
        val fastestGpsSamplePace = samples
            .mapNotNull { it.paceSecPerKm.takeIf { p -> p >= 140f }?.roundToInt() }
            .minOrNull()
        val fastestRecordedSplitPace = session.kmSplitsJson.toRecordedKmSplits()
            .filter { it.distanceMeters >= 400 && it.paceSecPerKm in 140..3600 }
            .minOfOrNull { it.paceSecPerKm }
        listOfNotNull(fastestGpsSamplePace, fastestRecordedSplitPace)
            .minOrNull()
            ?: avgPaceSec
    }

    val explicitDossierCadence = Regex("""(\d{3})\s*(?:ppm|spm)""", RegexOption.IGNORE_CASE)
        .find(session.notes ?: "")
        ?.groupValues?.getOrNull(1)?.toIntOrNull()
        ?.takeIf { it in 100..210 }
    val avgCadence = if (!hasRealDisplacement) {
        0
    } else {
        adv?.watchSyncedCadenceSpm?.takeIf { it in 80..230 }
            ?: explicitDossierCadence
            ?: 0
    }
    val maxCadence = if (!hasRealDisplacement) {
        0
    } else {
        samples.mapNotNull { it.cadenceSpm.takeIf { v -> v > 0 } }.maxOrNull() ?: 0
    }

    // Passos: apenas relógio ou cálculo com cadência real; sem isso, 0
    val totalSteps = if (!hasRealDisplacement) {
        0
    } else {
        adv?.watchSyncedSteps?.takeIf { it > 0 }
            ?: if (avgCadence > 0) {
                (avgCadence * (effectiveDurationSec / 60f)).roundToInt().coerceAtLeast(1)
            } else 0
    }
    val totalStepsWithWarmup = if (!hasRealDisplacement || totalSteps <= 0) {
        0
    } else if (avgCadence > 0) {
        maxOf(totalSteps, (avgCadence * (totalSessionDurationSec / 60f)).roundToInt())
    } else {
        totalSteps
    }

    // Passada: apenas relógio ou distância / passos reais; sem isso, 0 (sem +12 artificial)
    val avgStrideCm = if (!hasRealDisplacement) {
        0
    } else {
        adv?.watchSyncedStrideCm?.takeIf { it in 40..220 }
            ?: if (totalSteps > 0) {
                ((effectiveDistanceMeters * 100.0) / totalSteps.toDouble())
                    .roundToInt()
                    .coerceIn(50, 185)
            } else 0
    }
    val maxStrideCm = if (!hasRealDisplacement) {
        0
    } else {
        samples.mapNotNull { it.strideLengthCm.takeIf { v -> v > 0 } }.maxOrNull() ?: 0
    }

    // 4. Cálculo das 5 Zonas Cardíacas: apenas com >= 5 leituras reais em recordedHrList e effectiveMaxHr > 0
    val hrZones5 = if (!isHrEstimated && recordedHrList.size >= 5 && effectiveMaxHr > 0) {
        computeCoherent5ZoneDistribution(
            hrSamples = recordedHrList,
            avgBpm = computedAvgHr,
            maxBpm = computedMaxHr,
            effectiveMaxHr = effectiveMaxHr,
            durationSeconds = effectiveDurationSec
        )
    } else {
        emptyList()
    }

    // 5. Efeito do Treino e Fonte de Dados (sem inferir carga quando não há zonas válidas)
    val hasValidHrZones = hrZones5.isNotEmpty() && hrZones5.any { it.percentage > 0f }
    val z3PlusPct = hrZones5.filter { it.zoneIndex >= 3 }.sumOf { it.percentage.toDouble() }.toFloat()
    val z4PlusPct = hrZones5.filter { it.zoneIndex >= 4 }.sumOf { it.percentage.toDouble() }.toFloat()
    val vitalityScoreGain = if (!hasRealDisplacement || !hasValidHrZones) {
        0
    } else {
        ((durationMinutes / 4.2f) + (z3PlusPct * 0.045f) + (z4PlusPct * 0.035f))
            .roundToInt()
            .coerceIn(1, 10)
    }

    val trainingEffectTitle = when {
        !hasValidHrZones -> "Sem dados de FC para classificar o efeito do treino"
        session.isGentleMode -> "Recuperação Ativa & Circulação Regenerativa"
        z4PlusPct >= 28f -> "Estímulo de Limiar / Pirâmide & Potência"
        z3PlusPct >= 42f -> "Evolução Aeróbica & Base Mitocondrial"
        else -> "Manutenção Aeróbica & Recuperação Híbrida"
    }

    val hybridRecoveryImpact = when {
        !hasValidHrZones ->
            "Sem dados de FC suficientes para inferir carga ou impacto de recuperação."
        session.isGentleMode ->
            "Janela 48h Full Body: 100% preservada • Fluxo regenerativo sem fadiga excêntrica para o próximo treino de força."
        z4PlusPct >= 28f ->
            "Janela 48h Full Body: Demanda neuromuscular moderada-alta nos tiros • Respeite 24h–48h antes de cargas máximas de pernas."
        z3PlusPct >= 42f ->
            "Janela 48h Full Body: Sinergia cardiorrespiratória ideal • Sistema nervoso central pronto para hipertrofia no próximo Full Body."
        else ->
            "Janela 48h Full Body: Baixo impacto articular • Recuperação metabólica acelerada para a próxima sessão de musculação."
    }

    val hasGpsTrack = realGpsPoints.size >= 2
    val hrSourceLabel: String? = if (isHrEstimated) {
        null
    } else {
        when (session.hrProvenance?.source) {
            DataSourceType.CHEST_STRAP_BLE -> "Cinta BLE"
            DataSourceType.OPTICAL_PPG_WATCH -> "Relógio"
            DataSourceType.ATHLETE_REPORTED -> "FC informada manualmente"
            DataSourceType.PHONE_GPS,
            DataSourceType.STEP_SENSOR_PEDOMETER,
            DataSourceType.DERIVED_CALCULATION,
            null -> {
                if (adv?.watchSyncedSteps != null || adv?.watchSyncedActiveKcal != null || adv?.watchSyncedCadenceSpm != null) {
                    "Relógio"
                } else {
                    "Frequência cardíaca registrada"
                }
            }
        }
    }
    val dataSourceLabel = when {
        hrSourceLabel != null && hasGpsTrack -> "MAX RUN • GPS + $hrSourceLabel"
        hrSourceLabel != null -> "MAX RUN • $hrSourceLabel"
        session.environment.equals("TREADMILL", ignoreCase = true) -> "MAX RUN • Esteira"
        hasGpsTrack -> "MAX RUN • GPS do Celular"
        else -> "MAX RUN • Registro manual"
    }

    // 6. Construção das Parciais por Km ("1", "2", "<3", etc.) apenas com parciais/GPS reais
    val rawOneKmSplits = buildSplitsForMode(session, SplitSelectorMode.ONE_KM_MAIN, realGpsPoints)
    val fastestSplitPace = rawOneKmSplits.minOfOrNull { it.paceSecPerKm } ?: 0
    val kmSplits = rawOneKmSplits.mapIndexed { index, rec ->
        val isPartial = rec.distanceMeters < 995 && index == rawOneKmSplits.lastIndex
        val kmNum = index + 1
        val displayLbl = if (isPartial) "<$kmNum" else "$kmNum"
        KmSplit(
            kmNumber = kmNum,
            displayLabel = displayLbl,
            distanceMeters = rec.distanceMeters,
            distanceKm = rec.distanceMeters / 1000f,
            isPartialLastKm = isPartial,
            durationSec = rec.durationSec,
            paceSecPerKm = rec.paceSecPerKm,
            avgHrBpm = rec.avgHr,
            isFastest = fastestSplitPace > 0 && rec.paceSecPerKm == fastestSplitPace && rawOneKmSplits.size > 1
        )
    }

    // 7. Índice de Consistência: apenas com >= 2 parciais reais ou >= 4 amostras de ritmo por GPS real
    val gpsPaceSamples = samples.filter { it.paceSecPerKm > 0f }
    val hasConsistencyData = kmSplits.size >= 2 || gpsPaceSamples.size >= 4
    val firstIntervalPaceSec = if (!hasConsistencyData) {
        0
    } else if (kmSplits.size >= 2) {
        kmSplits.first().paceSecPerKm.safeCoerceIn(140, 3600)
    } else {
        gpsPaceSamples.take((gpsPaceSamples.size / 3).coerceAtLeast(1))
            .map { it.paceSecPerKm }
            .average()
            .safeRoundToInt(0)
            .let { if (it > 0) it.safeCoerceIn(140, 3600) else 0 }
    }
    val lastIntervalPaceSec = if (!hasConsistencyData) {
        0
    } else if (kmSplits.size >= 2) {
        kmSplits.last().paceSecPerKm.safeCoerceIn(140, 3600)
    } else {
        gpsPaceSamples.takeLast((gpsPaceSamples.size / 3).coerceAtLeast(1))
            .map { it.paceSecPerKm }
            .average()
            .safeRoundToInt(0)
            .let { if (it > 0) it.safeCoerceIn(140, 3600) else 0 }
    }
    val paceFadePct = if (hasConsistencyData && firstIntervalPaceSec > 0 && lastIntervalPaceSec > 0) {
        ((lastIntervalPaceSec - firstIntervalPaceSec).toFloat() / firstIntervalPaceSec.toFloat().coerceAtLeast(1f)) * 100f
    } else 0f
    val (consistencyTitle, consistencyDetail) = when {
        !hasConsistencyData || firstIntervalPaceSec <= 0 || lastIntervalPaceSec <= 0 -> Pair(
            "Consistência indisponível — dados de ritmo insuficientes",
            "São necessárias ao menos 2 parciais medidas ou série de GPS para avaliar a variação de ritmo."
        )
        paceFadePct > 10.0f -> Pair(
            "Alerta de Quebra (>10%): largada forte demais no 1º trecho",
            "1º trecho em ${formatPaceMinSecQuote(firstIntervalPaceSec)}/km vs. último em ${formatPaceMinSecQuote(lastIntervalPaceSec)}/km (+${String.format(ptBr, "%.1f", paceFadePct)}%). Segure 10–15s/km no início para sustentar até o fim."
        )
        paceFadePct < -1.5f -> Pair(
            "Negative Split de Elite: último trecho mais rápido que o 1º",
            "1º trecho em ${formatPaceMinSecQuote(firstIntervalPaceSec)}/km ➔ último em ${formatPaceMinSecQuote(lastIntervalPaceSec)}/km (${String.format(ptBr, "%.1f", paceFadePct)}%). Forte não significa no máximo: excelente reserva aeróbica."
        )
        else -> Pair(
            "Forte não significa no máximo: excelente controle de ritmo",
            "1º trecho em ${formatPaceMinSecQuote(firstIntervalPaceSec)}/km ➔ último em ${formatPaceMinSecQuote(lastIntervalPaceSec)}/km (variação ${String.format(ptBr, "%+.1f", paceFadePct)}%). Esforço controlado e repetível do início ao fim."
        )
    }

    return RunPhysiologyDossier(
        badgeTitle = badgeTitle,
        athleteName = athleteName,
        formattedDateTime = formattedDateTime,
        distanceKm = distKm,
        totalDistanceWithWarmupKm = totalDistKm,
        hasMainRunIsolation = hasMainRunIsolation,
        durationSeconds = effectiveDurationSec,
        totalDurationWithWarmupSeconds = totalSessionDurationSec,
        formattedDurationHhMmSs = formatSecondsToHhMmSs(effectiveDurationSec),
        activeCaloriesKcal = activeCaloriesKcal,
        basalCaloriesKcal = basalProportionalKcal,
        totalCaloriesKcal = totalCaloriesKcal,
        isWeightMissingForCalories = isWeightMissingForCalories,
        totalActiveCaloriesWithWarmupKcal = totalActiveCaloriesWithWarmupKcal,
        totalCaloriesWithWarmupKcal = totalCaloriesWithWarmupKcal,
        avgPaceSecPerKm = avgPaceSec,
        maxPaceSecPerKm = maxPaceSec,
        formattedAvgPace = formatPaceMinSecQuote(avgPaceSec),
        formattedMaxPace = formatPaceMinSecQuote(maxPaceSec),
        avgHeartRateBpm = computedAvgHr,
        maxHeartRateBpm = computedMaxHr,
        effectiveMaxHeartRateBpm = effectiveMaxHr,
        calibratedLt1Bpm = userProfile?.limiarLt1Bpm,
        calibratedLt2Bpm = userProfile?.limiarLt2Bpm,
        isHrEstimated = isHrEstimated,
        totalSteps = totalSteps,
        totalStepsWithWarmup = totalStepsWithWarmup,
        avgCadenceSpm = avgCadence,
        maxCadenceSpm = maxCadence,
        avgStrideLengthCm = avgStrideCm,
        maxStrideLengthCm = maxStrideCm,
        vitalityScoreGain = vitalityScoreGain,
        trainingEffectTitle = trainingEffectTitle,
        hybridRecoveryImpact = hybridRecoveryImpact,
        dataSourceLabel = dataSourceLabel,
        firstIntervalPaceSecPerKm = firstIntervalPaceSec,
        lastIntervalPaceSecPerKm = lastIntervalPaceSec,
        paceFadePct = paceFadePct,
        consistencyVerdictTitle = consistencyTitle,
        consistencyVerdictDetail = consistencyDetail,
        telemetrySamples = samples,
        hrZones5 = hrZones5,
        kmSplits = kmSplits
    )
}

/**
 * Perfil Fisiológico de Resistência baseado no Modelo Científico de 3 Zonas (LT1 / LT2) +
 * 5 Zonas de Joe Friel + Jack Daniels + Oxidação de Substrato Energético (Carboidrato vs. Gordura).
 *
 * Limiares LT1/LT2 usam a calibração do perfil quando informada, ou 72% e 85,26% da FCmáx efetiva.
 * Sem FCmáx efetiva (`<= 0`), não calcula limiares nem zonas (`isAvailable = false`).
 * Com menos de 6 amostras reais de FC, não fabrica distribuição de zonas (`hasZoneDistribution = false`).
 */
data class EndurancePhysiologyProfile(
    val calibratedMaxHrBpm: Int = 0,
    val lt1Bpm: Int = 0,
    val lt2Bpm: Int = 0,
    val carbOxidationPct: Int,
    val fatOxidationPct: Int,
    val carbGramsEstimated: Int,
    val fatGramsEstimated: Int,
    val predominantZoneBadge: String,
    val thresholdStatusTitle: String,
    val frielDanielsClassification: String,
    val estimatedLactateRange: String,
    val timeToFatigueWindow: String,
    val rpeRangeLabel: String,
    val belowLt1Pct: Int,
    val betweenLt1Lt2Pct: Int,
    val aboveLt2Pct: Int,
    val maxGymGlycogenImpact: String,
    val isAvailable: Boolean = true,
    val hasZoneDistribution: Boolean = true,
    val hasCalorieEstimate: Boolean = true
)

fun calculateEndurancePhysiologyProfile(
    dossier: RunPhysiologyDossier,
    userProfile: UserProfileData? = null
): EndurancePhysiologyProfile {
    val maxHr = userProfile?.fcMaxCalibradaBpm?.takeIf { it in 100..235 }
        ?: dossier.effectiveMaxHeartRateBpm.takeIf { it > 0 }
        ?: 0

    if (maxHr <= 0 || dossier.avgHeartRateBpm <= 0) {
        return EndurancePhysiologyProfile(
            calibratedMaxHrBpm = 0,
            lt1Bpm = 0,
            lt2Bpm = 0,
            carbOxidationPct = 0,
            fatOxidationPct = 0,
            carbGramsEstimated = 0,
            fatGramsEstimated = 0,
            predominantZoneBadge = "DADOS INSUFICIENTES",
            thresholdStatusTitle = "Dados insuficientes (FC máxima indisponível)",
            frielDanielsClassification = "Informe a idade ou calibração de FCmáx no perfil",
            estimatedLactateRange = "--",
            timeToFatigueWindow = "--",
            rpeRangeLabel = "--",
            belowLt1Pct = 0,
            betweenLt1Lt2Pct = 0,
            aboveLt2Pct = 0,
            maxGymGlycogenImpact = "Dados insuficientes para calcular limiares (LT1/LT2), zonas e substrato energético.",
            isAvailable = false,
            hasZoneDistribution = false,
            hasCalorieEstimate = false
        )
    }

    val lt1Bpm = (userProfile?.limiarLt1Bpm ?: dossier.calibratedLt1Bpm)
        ?.takeIf { it in 60 until maxHr }
        ?: (maxHr * 0.72f).roundToInt()
    val lt2Bpm = (userProfile?.limiarLt2Bpm ?: dossier.calibratedLt2Bpm)
        ?.takeIf { it in (lt1Bpm + 1)..maxHr }
        ?: (maxHr * 0.8526f).roundToInt()

    val avgBpm = dossier.avgHeartRateBpm.coerceIn(60, maxHr)
    val peakBpm = dossier.maxHeartRateBpm.coerceAtLeast(avgBpm)
    val samples = dossier.telemetrySamples.map { it.heartRateBpm }.filter { it in 65..230 }

    val hasZoneDistribution = samples.size >= 6
    val (belowLt1Pct, betweenLt1Lt2Pct, aboveLt2Pct) = if (hasZoneDistribution) {
        val total = samples.size.toFloat()
        val z1 = ((samples.count { it <= lt1Bpm } / total) * 100f).roundToInt()
        val z3 = ((samples.count { it >= lt2Bpm } / total) * 100f).roundToInt()
        val z2 = (100 - z1 - z3).coerceIn(0, 100)
        Triple(z1, z2, z3)
    } else {
        Triple(0, 0, 0)
    }

    // Ponderação fisiológica da oxidação de Carboidrato (Glicogênio) vs. Gordura apenas com distribuição real (>= 6 amostras)
    val carbPct: Int
    val fatPct: Int
    if (hasZoneDistribution) {
        val weightedCarbFloat = (belowLt1Pct * 0.28f) + (betweenLt1Lt2Pct * 0.78f) + (aboveLt2Pct * 0.96f)
        carbPct = weightedCarbFloat.roundToInt().coerceIn(22, 98)
        fatPct = (100 - carbPct).coerceIn(2, 78)
    } else {
        carbPct = 0
        fatPct = 0
    }

    val activeKcal = dossier.activeCaloriesKcal.coerceAtLeast(0)
    val hasCalorieEstimate = hasZoneDistribution && activeKcal > 0
    val carbGrams = if (hasCalorieEstimate) ((activeKcal * (carbPct / 100f)) / 4.0f).roundToInt() else 0
    val fatGrams = if (hasCalorieEstimate) ((activeKcal * (fatPct / 100f)) / 9.0f).roundToInt() else 0

    val isNearLt2Border = avgBpm in (lt2Bpm - 5)..(lt2Bpm + 3) && peakBpm >= lt2Bpm

    val predominantZoneBadge = when {
        avgBpm <= lt1Bpm -> "ZONA 1 • ABAIXO DO LT1 (<= $lt1Bpm BPM)"
        isNearLt2Border -> "FRONTEIRA LT2 (LIMIAR ➔ VO2MÁX • $avgBpm/$peakBpm BPM)"
        avgBpm < lt2Bpm -> "ZONA 2 • ENTRE LT1 E LT2 (${lt1Bpm + 1}–${lt2Bpm - 1} BPM)"
        else -> "ZONA 3 • ACIMA DO LT2 (>= $lt2Bpm BPM)"
    }

    val thresholdStatusTitle = when {
        avgBpm <= lt1Bpm -> "Base Aeróbica Oxidativa (< LT1 • <= 72% FCmáx)"
        isNearLt2Border -> "Fronteira LT2 (Limiar ➔ VO2máx • 84%–93% FCmáx)"
        avgBpm < lt2Bpm -> "Zona de Ritmo / Limiar Aeróbico-Anaeróbico (LT1–LT2)"
        else -> "Domínio Severo / VO2máx Acima do Limiar 2 (> LT2)"
    }

    val frielDanielsClassification = when {
        avgBpm <= lt1Bpm -> "Joe Friel Z1–Z2 (Resistência) • Jack Daniels E-Pace (Easy)"
        isNearLt2Border -> "Joe Friel Z4 ➔ Z5a (Limiar/Superlimiar) • Jack Daniels T-Pace ➔ I-Pace"
        avgBpm < lt2Bpm -> "Joe Friel Z3–Z4 (Tempo/Sublimiar) • Jack Daniels M-Pace / T-Pace"
        else -> "Joe Friel Z5a–Z5c (Potência VO2/Anaeróbica) • Jack Daniels I-Pace / R-Pace"
    }

    val estimatedLactateRange = when {
        avgBpm <= lt1Bpm -> "~0,7–2,5 mmol/L (Estável)"
        isNearLt2Border -> "~4,5–8,0 mmol/L (Transição LT2)"
        avgBpm < lt2Bpm -> "~2,0–5,0 mmol/L (Equilíbrio Dinâmico)"
        else -> "~5,0–14,0 mmol/L (Acúmulo Rápido)"
    }

    val timeToFatigueWindow = when {
        avgBpm <= lt1Bpm -> "Todo o dia / +150 min (Fadiga periférica mínima)"
        isNearLt2Border -> "Limiar: ~45–60 min • Janela na Zona de Pico (>LT2): ~2–7 min"
        avgBpm < lt2Bpm -> "~60 a 150 min (Limitado pela reserva de glicogênio)"
        else -> "~2 a 7 min em Z3 contínua (Alta acidose / depleção rápida)"
    }

    val rpeRangeLabel = when {
        avgBpm <= lt1Bpm -> "RPE 1–3 (Muito Leve a Leve)"
        isNearLt2Border -> "RPE 7–9 (Forte a Muito Forte)"
        avgBpm < lt2Bpm -> "RPE 4–7 (Moderado a Forte Controlado)"
        else -> "RPE 8–10 (Esforço Máximo / VO2máx)"
    }

    val maxGymGlycogenImpact = when {
        !hasZoneDistribution ->
            "Impacto MAX GYM: Dados insuficientes de FC (< 6 amostras) para estimar oxidação de substrato e depleção de glicogênio."
        !hasCalorieEstimate ->
            "Impacto MAX GYM: Predomínio de ${carbPct}% Carboidrato / ${fatPct}% Lipídios (calorias ativas indisponíveis para estimar gramas)."
        carbPct >= 75 ->
            "Impacto MAX GYM: Depleção predominante de Glicogênio Muscular (${carbPct}% Carboidrato / ~${carbGrams}g). Reponha 40g–60g de carboidratos antes do próximo Full Body para preservar força máxima nas máquinas."
        carbPct >= 55 ->
            "Impacto MAX GYM: Uso misto de Glicogênio (${carbPct}% / ~${carbGrams}g) e Lipídios (${fatPct}% / ~${fatGrams}g). Reservas musculares moderadamente preservadas para hipertrofia."
        else ->
            "Impacto MAX GYM: Predomínio oxidativo de Gordura (${fatPct}% Lipídios vs. ${carbPct}% Carboidrato). Glicogênio muscular 100% preservado para carga máxima no MAX GYM."
    }

    return EndurancePhysiologyProfile(
        calibratedMaxHrBpm = maxHr,
        lt1Bpm = lt1Bpm,
        lt2Bpm = lt2Bpm,
        carbOxidationPct = carbPct,
        fatOxidationPct = fatPct,
        carbGramsEstimated = carbGrams,
        fatGramsEstimated = fatGrams,
        predominantZoneBadge = predominantZoneBadge,
        thresholdStatusTitle = thresholdStatusTitle,
        frielDanielsClassification = frielDanielsClassification,
        estimatedLactateRange = estimatedLactateRange,
        timeToFatigueWindow = timeToFatigueWindow,
        rpeRangeLabel = rpeRangeLabel,
        belowLt1Pct = belowLt1Pct,
        betweenLt1Lt2Pct = betweenLt1Lt2Pct,
        aboveLt2Pct = aboveLt2Pct,
        maxGymGlycogenImpact = maxGymGlycogenImpact,
        isAvailable = true,
        hasZoneDistribution = hasZoneDistribution,
        hasCalorieEstimate = hasCalorieEstimate
    )
}

/**
 * Calcula instantaneamente os indicadores de Limiar (LT1/LT2), Lactato, Tempo até a Fadiga e Substrato
 * Energético para o BPM atual no Modal Fullscreen Ao Vivo (`FullscreenHeartRateModal`).
 * Sem FCmáx efetiva (`<= 0`), não calcula limiares nem zonas (`isAvailable = false`).
 */
data class LiveBpmEnduranceSnapshot(
    val calibratedMaxHrBpm: Int = 0,
    val lt1Bpm: Int = 0,
    val lt2Bpm: Int = 0,
    val zone3ModelLabel: String,
    val frielDanielsLabel: String,
    val lactateEstimateText: String,
    val timeToFatigueText: String,
    val primarySubstrateText: String,
    val carbPct: Int,
    val fatPct: Int,
    val rpeLabel: String,
    val isAvailable: Boolean = true
)

fun calculateLiveBpmEnduranceSnapshot(
    currentBpm: Int,
    calibratedMaxHr: Int = 0,
    calibratedLt1Bpm: Int? = null,
    calibratedLt2Bpm: Int? = null
): LiveBpmEnduranceSnapshot {
    if (calibratedMaxHr <= 0) {
        return LiveBpmEnduranceSnapshot(
            calibratedMaxHrBpm = 0,
            lt1Bpm = 0,
            lt2Bpm = 0,
            zone3ModelLabel = "DADOS INSUFICIENTES",
            frielDanielsLabel = "Informe a idade ou calibração de FCmáx no perfil",
            lactateEstimateText = "--",
            timeToFatigueText = "--",
            primarySubstrateText = "Dados insuficientes",
            carbPct = 0,
            fatPct = 0,
            rpeLabel = "--",
            isAvailable = false
        )
    }

    val lt1Bpm = calibratedLt1Bpm?.takeIf { it in 60 until calibratedMaxHr }
        ?: (calibratedMaxHr * 0.72f).roundToInt()
    val lt2Bpm = calibratedLt2Bpm?.takeIf { it in (lt1Bpm + 1)..calibratedMaxHr }
        ?: (calibratedMaxHr * 0.8526f).roundToInt()

    if (currentBpm <= 0) {
        return LiveBpmEnduranceSnapshot(
            calibratedMaxHrBpm = calibratedMaxHr,
            lt1Bpm = lt1Bpm,
            lt2Bpm = lt2Bpm,
            zone3ModelLabel = "AGUARDANDO SINAL DE FC",
            frielDanielsLabel = "Conecte um sensor cardíaco para classificar a zona",
            lactateEstimateText = "--",
            timeToFatigueText = "--",
            primarySubstrateText = "--",
            carbPct = 0,
            fatPct = 0,
            rpeLabel = "--",
            isAvailable = true
        )
    }

    return when {
        currentBpm <= lt1Bpm -> LiveBpmEnduranceSnapshot(
            calibratedMaxHrBpm = calibratedMaxHr,
            lt1Bpm = lt1Bpm,
            lt2Bpm = lt2Bpm,
            zone3ModelLabel = "ZONA 1 • ABAIXO DO LT1 (<= $lt1Bpm bpm)",
            frielDanielsLabel = "Friel Z1–Z2 (Recuperação/Resistência) • Daniels E-Pace",
            lactateEstimateText = "~0,7–2,5 mmol/L",
            timeToFatigueText = "Todo o dia / +150 min",
            primarySubstrateText = "65%–80% Gordura / 20%–35% Carboidrato",
            carbPct = 28,
            fatPct = 72,
            rpeLabel = "RPE 1–3",
            isAvailable = true
        )
        currentBpm < lt2Bpm -> LiveBpmEnduranceSnapshot(
            calibratedMaxHrBpm = calibratedMaxHr,
            lt1Bpm = lt1Bpm,
            lt2Bpm = lt2Bpm,
            zone3ModelLabel = "ZONA 2 • ENTRE LT1 E LT2 (${lt1Bpm + 1}–${lt2Bpm - 1} bpm)",
            frielDanielsLabel = "Friel Z3–Z4 (Ritmo/Limiar) • Daniels M-Pace / T-Pace",
            lactateEstimateText = "~2,0–5,0 mmol/L",
            timeToFatigueText = "~60 a 150 min",
            primarySubstrateText = "70%–85% Carboidrato (Glicogênio) / 15%–30% Gordura",
            carbPct = 78,
            fatPct = 22,
            rpeLabel = "RPE 4–7",
            isAvailable = true
        )
        else -> LiveBpmEnduranceSnapshot(
            calibratedMaxHrBpm = calibratedMaxHr,
            lt1Bpm = lt1Bpm,
            lt2Bpm = lt2Bpm,
            zone3ModelLabel = "ZONA 3 • ACIMA DO LT2 (>= $lt2Bpm bpm • VO2máx)",
            frielDanielsLabel = "Friel Z5a–Z5c (Superlimiar/VO2máx) • Daniels I-Pace / R-Pace",
            lactateEstimateText = "~5,0–20,0 mmol/L",
            timeToFatigueText = "~2 a 7 min",
            primarySubstrateText = "92%–100% Carboidrato (Glicogênio Muscular)",
            carbPct = 95,
            fatPct = 5,
            rpeLabel = "RPE 8–10",
            isAvailable = true
        )
    }
}

// ================================================================================================
// MÓDULO "TROFÉUS & PROVAS OFICIAIS" (HISTÓRICO E COMPARATIVO DE CORRIDAS DE RUA / TAF)
// ================================================================================================

data class OfficialRaceRecord(
    val id: String = UUID.randomUUID().toString(),
    val linkedRunSessionId: String? = null,
    val raceName: String,
    val dateMillis: Long,
    val categoryDistanceKm: Double,
    val actualDistanceKm: Double,
    val officialTimeSec: Int,
    val runningTimeSec: Int,
    val walkingTimeSec: Int,
    val avgPaceSecPerKm: Int,
    val runningPaceSecPerKm: Int,
    val bestSplitPaceSecPerKm: Int,
    val avgHeartRate: Int,
    val maxHeartRate: Int,
    val avgCadenceSpm: Int,
    val maxCadenceSpm: Int,
    val shoeUsed: String,
    val placementNotes: String = ""
) {
    val constancyRunningPct: Double
        get() {
            val total = (runningTimeSec + walkingTimeSec).takeIf { it > 0 } ?: officialTimeSec.coerceAtLeast(1)
            return ((runningTimeSec.toDouble() / total.toDouble()) * 100.0).coerceIn(0.0, 100.0)
        }

    val categoryBadgeLabel: String
        get() = when {
            kotlin.math.abs(categoryDistanceKm - 2.4) < 0.25 -> "TAF 2.4K"
            kotlin.math.abs(categoryDistanceKm - 5.0) < 0.35 -> "5K"
            kotlin.math.abs(categoryDistanceKm - 10.0) < 0.5 -> "10K"
            kotlin.math.abs(categoryDistanceKm - 21.1) < 0.8 -> "21K"
            categoryDistanceKm <= 0.1 -> "LIVRE"
            else -> String.format(Locale.US, "%.1fK", categoryDistanceKm)
        }
}

fun List<OfficialRaceRecord>.toOfficialRacesJson(): String {
    val arr = org.json.JSONArray()
    for (r in this) {
        val obj = org.json.JSONObject()
        obj.put("id", r.id)
        if (!r.linkedRunSessionId.isNullOrBlank()) {
            obj.put("linkedRunSessionId", r.linkedRunSessionId)
        }
        obj.put("raceName", r.raceName)
        obj.put("dateMillis", r.dateMillis)
        obj.put("categoryDistanceKm", r.categoryDistanceKm)
        obj.put("actualDistanceKm", r.actualDistanceKm)
        obj.put("officialTimeSec", r.officialTimeSec)
        obj.put("runningTimeSec", r.runningTimeSec)
        obj.put("walkingTimeSec", r.walkingTimeSec)
        obj.put("avgPaceSecPerKm", r.avgPaceSecPerKm)
        obj.put("runningPaceSecPerKm", r.runningPaceSecPerKm)
        obj.put("bestSplitPaceSecPerKm", r.bestSplitPaceSecPerKm)
        obj.put("avgHeartRate", r.avgHeartRate)
        obj.put("maxHeartRate", r.maxHeartRate)
        obj.put("avgCadenceSpm", r.avgCadenceSpm)
        obj.put("maxCadenceSpm", r.maxCadenceSpm)
        obj.put("shoeUsed", r.shoeUsed)
        obj.put("placementNotes", r.placementNotes)
        arr.put(obj)
    }
    return arr.toString()
}

fun String?.toOfficialRaceRecords(): List<OfficialRaceRecord> {
    if (this.isNullOrBlank()) return emptyList()
    return try {
        val arr = org.json.JSONArray(this)
        val result = ArrayList<OfficialRaceRecord>(arr.length())
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val officialTime = obj.optInt("officialTimeSec", 0).coerceAtLeast(1)
            val runSec = obj.optInt("runningTimeSec", officialTime).coerceIn(0, officialTime)
            val walkSec = obj.optInt("walkingTimeSec", (officialTime - runSec).coerceAtLeast(0)).coerceAtLeast(0)
            result.add(
                OfficialRaceRecord(
                    id = obj.optString("id").ifBlank { UUID.randomUUID().toString() },
                    linkedRunSessionId = obj.optString("linkedRunSessionId").takeIf { it.isNotBlank() && it != "null" },
                    raceName = obj.optString("raceName", "Prova Oficial 5K"),
                    dateMillis = obj.optLong("dateMillis", System.currentTimeMillis()),
                    categoryDistanceKm = obj.optDouble("categoryDistanceKm", 5.0),
                    actualDistanceKm = obj.optDouble("actualDistanceKm", 5.0),
                    officialTimeSec = officialTime,
                    runningTimeSec = runSec,
                    walkingTimeSec = walkSec,
                    avgPaceSecPerKm = obj.optInt("avgPaceSecPerKm", 0),
                    runningPaceSecPerKm = obj.optInt("runningPaceSecPerKm", 0),
                    bestSplitPaceSecPerKm = obj.optInt("bestSplitPaceSecPerKm", 0),
                    avgHeartRate = obj.optInt("avgHeartRate", 0),
                    maxHeartRate = obj.optInt("maxHeartRate", 0),
                    avgCadenceSpm = obj.optInt("avgCadenceSpm", 0),
                    maxCadenceSpm = obj.optInt("maxCadenceSpm", 0),
                    shoeUsed = obj.optString("shoeUsed", "Olympikus Corre 4 (42 BR)"),
                    placementNotes = obj.optString("placementNotes", "")
                )
            )
        }
        result.sortedByDescending { it.dateMillis }
    } catch (_: Exception) {
        emptyList()
    }
}

fun normalizeOfficialRaceShoeName(rawShoe: String?): String {
    val trimmed = rawShoe?.trim().orEmpty()
    return when {
        trimmed.isBlank() -> "Olympikus Corre 4 (42 BR)"
        trimmed.contains("Novablast", ignoreCase = true) && !trimmed.contains("BR", ignoreCase = true) ->
            "Asics Novablast 5 (43 BR)"
        trimmed.contains("Corre 4", ignoreCase = true) && !trimmed.contains("BR", ignoreCase = true) ->
            "Olympikus Corre 4 (42 BR)"
        else -> trimmed
    }
}

fun inferRaceCategoryDistanceKm(sessionType: String, actualDistKm: Double): Double {
    val upper = sessionType.uppercase(Locale.US)
    return when {
        upper.contains("TAF") || actualDistKm in 2.0..2.8 -> 2.4
        upper.contains("5K") || actualDistKm in 4.3..6.5 -> 5.0
        upper.contains("10K") || actualDistKm in 8.8..11.8 -> 10.0
        upper.contains("21K") || actualDistKm >= 19.5 -> 21.1
        actualDistKm > 0.1 -> 5.0
        else -> 0.0
    }
}

fun buildOfficialRaceRecordFromSession(
    session: RunningSession,
    userProfile: UserProfileData? = null,
    gpsPoints: List<GpsTrackPoint> = emptyList(),
    existingId: String? = null,
    customRaceName: String? = null,
    customCategoryKm: Double? = null,
    customShoeUsed: String? = null,
    customPlacementNotes: String? = null
): OfficialRaceRecord {
    val sprintAnalytics = calculateSprintAnalytics(session, gpsPoints)
    val dossier = buildRunPhysiologyDossier(session, userProfile, gpsPoints)

    val effectiveMeters = (session.mainRunDistanceMeters?.takeIf { it > 0 } ?: session.distanceMeters).coerceAtLeast(0)
    val actualKm = (effectiveMeters / 1000.0).let { if (it > 0.0) it else 5.0 }
    val categoryKm = customCategoryKm ?: inferRaceCategoryDistanceKm(session.sessionType, actualKm)

    val officialSec = (session.mainRunDurationSec?.takeIf { it > 0 } ?: session.durationSec).coerceAtLeast(1)
    val runSec = sprintAnalytics.runningTimeSec.coerceIn(0, officialSec)
    val walkSec = (officialSec - runSec).coerceAtLeast(sprintAnalytics.walkingTimeSec.coerceIn(0, officialSec))

    val avgPaceSec = dossier.avgPaceSecPerKm.takeIf { it > 0 }
        ?: sprintAnalytics.mainPaceSecPerKm.takeIf { it > 0 }
        ?: if (effectiveMeters > 0) ((officialSec * 1000.0) / effectiveMeters.toDouble()).roundToInt() else 0

    val runningPaceSec = sprintAnalytics.avgSprintPaceSecPerKm.takeIf { it > 0 } ?: avgPaceSec
    val bestSplitPaceSec = sprintAnalytics.bestSprintPaceSecPerKm.takeIf { it > 0 }
        ?: dossier.kmSplits.minOfOrNull { it.paceSecPerKm }?.takeIf { it > 0 }
        ?: dossier.maxPaceSecPerKm.takeIf { it > 0 }
        ?: runningPaceSec

    val avgHr = session.avgHr?.takeIf { it > 0 } ?: dossier.avgHeartRateBpm.takeIf { it > 0 } ?: 0
    val maxHr = session.maxHr?.takeIf { it > 0 } ?: dossier.maxHeartRateBpm.takeIf { it > 0 } ?: avgHr

    val avgCad = dossier.avgCadenceSpm.takeIf { it > 0 } ?: sprintAnalytics.avgCadenceSpm.takeIf { it > 0 } ?: 160
    val maxCad = dossier.maxCadenceSpm.takeIf { it > 0 } ?: (avgCad + 12).coerceAtMost(198)

    val defaultName = when {
        !customRaceName.isNullOrBlank() -> customRaceName.trim()
        !session.notes.isNullOrBlank() && (
            session.notes.contains("Corrida", ignoreCase = true) ||
                session.notes.contains("Prova", ignoreCase = true) ||
                session.notes.contains("TAF", ignoreCase = true) ||
                session.notes.contains("BPTRAN", ignoreCase = true) ||
                session.notes.contains("Anime", ignoreCase = true)
            ) -> session.notes.substringBefore("|").trim().take(48)
        kotlin.math.abs(categoryKm - 2.4) < 0.25 -> "Simulado TAF 2.4K"
        kotlin.math.abs(categoryKm - 10.0) < 0.5 -> "Prova Oficial 10K"
        else -> "Prova 5K Rio Branco"
    }

    val shoeResolved = customShoeUsed?.takeIf { it.isNotBlank() }
        ?: normalizeOfficialRaceShoeName(session.shoeName)

    val notesResolved = customPlacementNotes
        ?: session.notes?.takeIf { it.isNotBlank() }
        ?: "RPE ${session.rpe}/10 • Registrada via telemetria GPS MAX RUN"

    return OfficialRaceRecord(
        id = existingId ?: UUID.randomUUID().toString(),
        linkedRunSessionId = session.id,
        raceName = defaultName,
        dateMillis = session.date,
        categoryDistanceKm = categoryKm,
        actualDistanceKm = actualKm,
        officialTimeSec = officialSec,
        runningTimeSec = runSec,
        walkingTimeSec = walkSec,
        avgPaceSecPerKm = avgPaceSec,
        runningPaceSecPerKm = runningPaceSec,
        bestSplitPaceSecPerKm = bestSplitPaceSec,
        avgHeartRate = avgHr,
        maxHeartRate = maxHr,
        avgCadenceSpm = avgCad,
        maxCadenceSpm = maxCad,
        shoeUsed = shoeResolved,
        placementNotes = notesResolved
    )
}

data class OfficialRacePrSummary(
    val best5kRace: OfficialRaceRecord? = null,
    val bestRunningPaceRace: OfficialRaceRecord? = null,
    val bestConstancyRace: OfficialRaceRecord? = null
)

fun computeOfficialRacePrs(races: List<OfficialRaceRecord>): OfficialRacePrSummary {
    if (races.isEmpty()) return OfficialRacePrSummary()
    val fiveKRaces = races.filter {
        kotlin.math.abs(it.categoryDistanceKm - 5.0) < 0.4 || it.actualDistanceKm in 4.5..5.8
    }
    val best5k = (fiveKRaces.ifEmpty { races })
        .filter { it.officialTimeSec > 0 }
        .minByOrNull {
            // Normaliza para equivalente 5K se a distância real passou de 5.0 km (ex: 5.33 km)
            if (it.avgPaceSecPerKm > 0 && it.categoryDistanceKm in 4.6..5.4) {
                it.officialTimeSec
            } else {
                it.officialTimeSec
            }
        }

    val bestRunPace = races
        .filter { it.runningPaceSecPerKm in 120..900 }
        .minByOrNull { it.runningPaceSecPerKm }
        ?: races.filter { it.avgPaceSecPerKm in 120..900 }.minByOrNull { it.avgPaceSecPerKm }

    val bestConstancy = races.maxWithOrNull(
        compareBy<OfficialRaceRecord> { it.constancyRunningPct }
            .thenBy { -it.walkingTimeSec }
            .thenBy { -it.officialTimeSec }
    )

    return OfficialRacePrSummary(
        best5kRace = best5k,
        bestRunningPaceRace = bestRunPace,
        bestConstancyRace = bestConstancy
    )
}

data class OfficialRaceComparisonResult(
    val olderRace: OfficialRaceRecord,
    val newerRace: OfficialRaceRecord,
    val totalTimeDeltaSec: Int, // Negativo = mais rápido (evolução verde)
    val avgPaceDeltaSec: Int,   // Negativo = mais rápido (evolução verde)
    val runningPaceDeltaSec: Int, // Negativo = mais rápido (evolução verde)
    val walkingTimeDeltaSec: Int, // Negativo = caminhou menos (evolução verde)
    val constancyDeltaPct: Double, // Positivo = maior % correndo (evolução verde)
    val avgHrDeltaBpm: Int,
    val maxHrDeltaBpm: Int,
    val avgCadenceDeltaSpm: Int,
    val maxCadenceDeltaSpm: Int,
    val cardiacEfficiencyVerdict: String,
    val cardiacEfficiencyIsPositive: Boolean,
    val shoeMechanicalVerdict: String,
    val shoeMechanicalIsPositive: Boolean
)

fun compareOfficialRaces(
    olderRace: OfficialRaceRecord,
    newerRace: OfficialRaceRecord
): OfficialRaceComparisonResult {
    val timeDelta = newerRace.officialTimeSec - olderRace.officialTimeSec
    val avgPaceDelta = newerRace.avgPaceSecPerKm - olderRace.avgPaceSecPerKm
    val runPaceDelta = newerRace.runningPaceSecPerKm - olderRace.runningPaceSecPerKm
    val walkDelta = newerRace.walkingTimeSec - olderRace.walkingTimeSec
    val constancyDelta = newerRace.constancyRunningPct - olderRace.constancyRunningPct
    val avgHrDelta = newerRace.avgHeartRate - olderRace.avgHeartRate
    val maxHrDelta = newerRace.maxHeartRate - olderRace.maxHeartRate
    val avgCadDelta = newerRace.avgCadenceSpm - olderRace.avgCadenceSpm
    val maxCadDelta = newerRace.maxCadenceSpm - olderRace.maxCadenceSpm

    // Eficiência cardíaca: entregou pace melhor ou igual com FC controlada, ou reduziu FC para o mesmo ritmo
    val (cardiacVerdict, cardiacPositive) = when {
        olderRace.avgHeartRate <= 0 || newerRace.avgHeartRate <= 0 ->
            "Sem leitura de FC em uma das provas para cálculo de drift cardíaco." to true
        avgPaceDelta <= -5 && avgHrDelta <= 2 ->
            "Economia cardíaca superior: pace ${kotlin.math.abs(avgPaceDelta)}s/km mais rápido com FC Média ${if (avgHrDelta <= 0) "${avgHrDelta} bpm menor" else "estável (+${avgHrDelta} bpm)"} (${newerRace.avgHeartRate}/${newerRace.maxHeartRate} bpm)." to true
        avgPaceDelta <= 0 && avgHrDelta > 2 ->
            "Ritmo mais forte (${kotlin.math.abs(avgPaceDelta)}s/km mais rápido) sustentando maior exigência cardiovascular (+${avgHrDelta} bpm • ${newerRace.avgHeartRate}/${newerRace.maxHeartRate} bpm)." to true
        avgHrDelta < 0 ->
            "Menor custo cardiovascular (${avgHrDelta} bpm na FC Média • ${newerRace.avgHeartRate} vs ${olderRace.avgHeartRate} bpm)." to true
        else ->
            "Custo cardíaco +${avgHrDelta} bpm para pace +${avgPaceDelta}s/km (${newerRace.avgHeartRate}/${newerRace.maxHeartRate} bpm vs ${olderRace.avgHeartRate}/${olderRace.maxHeartRate} bpm)." to false
    }

    val betterRaceByRunningPace = if (newerRace.runningPaceSecPerKm in 120..900 &&
        (olderRace.runningPaceSecPerKm <= 0 || newerRace.runningPaceSecPerKm <= olderRace.runningPaceSecPerKm)
    ) newerRace else olderRace

    val (shoeVerdict, shoePositive) = when {
        olderRace.shoeUsed.equals(newerRace.shoeUsed, ignoreCase = true) -> {
            val cadSign = if (avgCadDelta >= 0) "+$avgCadDelta" else "$avgCadDelta"
            "Mesmo calçado (${newerRace.shoeUsed}) • Cadência ${newerRace.avgCadenceSpm} ppm ($cadSign ppm, pico ${newerRace.maxCadenceSpm} ppm)." to (avgCadDelta >= 0 || runPaceDelta <= 0)
        }
        else -> {
            val cadSign = if (avgCadDelta >= 0) "+$avgCadDelta" else "$avgCadDelta"
            "Melhor resposta mecânica: ${betterRaceByRunningPace.shoeUsed} (Pace correndo ${formatPaceMinSecQuote(betterRaceByRunningPace.runningPaceSecPerKm)}/km • Cadência ${newerRace.avgCadenceSpm} vs ${olderRace.avgCadenceSpm} ppm, Δ $cadSign ppm)." to (betterRaceByRunningPace.id == newerRace.id)
        }
    }

    return OfficialRaceComparisonResult(
        olderRace = olderRace,
        newerRace = newerRace,
        totalTimeDeltaSec = timeDelta,
        avgPaceDeltaSec = avgPaceDelta,
        runningPaceDeltaSec = runPaceDelta,
        walkingTimeDeltaSec = walkDelta,
        constancyDeltaPct = constancyDelta,
        avgHrDeltaBpm = avgHrDelta,
        maxHrDeltaBpm = maxHrDelta,
        avgCadenceDeltaSpm = avgCadDelta,
        maxCadenceDeltaSpm = maxCadDelta,
        cardiacEfficiencyVerdict = cardiacVerdict,
        cardiacEfficiencyIsPositive = cardiacPositive,
        shoeMechanicalVerdict = shoeVerdict,
        shoeMechanicalIsPositive = shoePositive
    )
}

// ================================================================================================
// AGENDA DE PRÓXIMAS PROVAS INSCRITAS + CONTAGEM REGRESSIVA + LEMBRETES
// ================================================================================================

data class UpcomingRaceEvent(
    val id: String = UUID.randomUUID().toString(),
    val raceName: String,
    val raceDateMillis: Long,
    val distanceCategory: String, // "5K", "10K", "21K", "TAF" ou personalizada
    val targetPace: String = "5'15\"/km",
    val plannedShoe: String = "Olympikus Corre 4 (42 BR)",
    val notes: String = "",
    val notifyOneWeekBefore: Boolean = true,
    val notifyOneDayBefore: Boolean = true
) {
    val daysRemaining: Int
        get() {
            val now = System.currentTimeMillis()
            val diffMs = raceDateMillis - now
            return kotlin.math.ceil(diffMs.toDouble() / 86_400_000.0).toInt().coerceAtLeast(0)
        }

    val physiologicalPhaseText: String
        get() = when {
            daysRemaining > 14 -> "Fase de Construção & Especificidade de Ritmo"
            daysRemaining in 3..14 -> "Semana de Polimento — Reduzir volume longo e manter ritmo"
            daysRemaining in 1..2 -> "Véspera de Prova — Descanso total e hidratação"
            daysRemaining == 0 -> "Hoje é o Dia da Prova! Bom desempenho!"
            else -> "Prova já realizada ou finalizada"
        }
}

fun List<UpcomingRaceEvent>.toUpcomingRacesJson(): String {
    val arr = org.json.JSONArray()
    for (item in this) {
        val obj = org.json.JSONObject()
        obj.put("id", item.id)
        obj.put("raceName", item.raceName)
        obj.put("raceDateMillis", item.raceDateMillis)
        obj.put("distanceCategory", item.distanceCategory)
        obj.put("targetPace", item.targetPace)
        obj.put("plannedShoe", item.plannedShoe)
        obj.put("notes", item.notes)
        obj.put("notifyOneWeekBefore", item.notifyOneWeekBefore)
        obj.put("notifyOneDayBefore", item.notifyOneDayBefore)
        arr.put(obj)
    }
    return arr.toString()
}

fun String?.toUpcomingRaceEvents(): List<UpcomingRaceEvent> {
    if (this.isNullOrBlank() || this == "[]") return emptyList()
    return try {
        val arr = org.json.JSONArray(this)
        val list = ArrayList<UpcomingRaceEvent>(arr.length())
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            list.add(
                UpcomingRaceEvent(
                    id = obj.optString("id", UUID.randomUUID().toString()),
                    raceName = obj.optString("raceName", "Próxima Prova"),
                    raceDateMillis = obj.optLong("raceDateMillis", System.currentTimeMillis() + 14L * 86_400_000L),
                    distanceCategory = obj.optString("distanceCategory", "5K"),
                    targetPace = obj.optString("targetPace", "5'15\"/km"),
                    plannedShoe = obj.optString("plannedShoe", "Olympikus Corre 4 (42 BR)"),
                    notes = obj.optString("notes", ""),
                    notifyOneWeekBefore = obj.optBoolean("notifyOneWeekBefore", true),
                    notifyOneDayBefore = obj.optBoolean("notifyOneDayBefore", true)
                )
            )
        }
        list
    } catch (e: Exception) {
        emptyList()
    }
}





