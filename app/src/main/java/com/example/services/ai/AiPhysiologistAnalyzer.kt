package com.example.services.ai

import android.content.Context
import com.example.BuildConfig
import com.example.data.model.GpsTrackPoint
import com.example.data.model.GymExerciseRecord
import com.example.data.model.GymWorkoutSession
import com.example.data.model.RecordedKmSplit
import com.example.data.model.RunningSession
import com.example.data.model.SprintAnalytics
import com.example.data.model.UserProfileData
import com.example.data.model.safeCoerceIn
import com.example.data.model.safeRoundToInt
import com.example.data.model.toHrTimelineList
import com.example.utils.WeightCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

enum class EffortPerception(val label: String, val badgeColorHex: Long) {
    SOBROU_RESERVA("Sobrou reserva", 0xFF10B981),
    ESFORCO_CONTROLADO("Esforço controlado", 0xFF0EA5E9),
    NO_LIMITE("No limite / Exausto", 0xFFF59E0B)
}

enum class HrSourceType(val label: String) {
    CINTA_PEITORAL("Cinta Peitoral"),
    SMARTWATCH("Smartwatch"),
    SEM_SENSOR("Sem sensor")
}

data class PreAnalysisCheckIn(
    val effortPerception: EffortPerception = EffortPerception.ESFORCO_CONTROLADO,
    val hrSource: HrSourceType = HrSourceType.CINTA_PEITORAL,
    val customNote: String = ""
)

data class GymLoadAdjustment(
    val exercicio: String,
    val equipamento: String,
    val cargaAtualKg: Float,
    val novaCargaSugeridaKg: Float,
    val metaReps: Int,
    val motivoCurto: String
) {
    fun toJsonObject(): JSONObject {
        return JSONObject().apply {
            put("exercicio", exercicio)
            put("equipamento", equipamento)
            put("cargaAtualKg", cargaAtualKg.toDouble())
            put("novaCargaSugeridaKg", novaCargaSugeridaKg.toDouble())
            put("metaReps", metaReps)
            put("motivoCurto", motivoCurto)
        }
    }

    companion object {
        fun fromJsonObject(obj: JSONObject): GymLoadAdjustment {
            return GymLoadAdjustment(
                exercicio = obj.optString("exercicio", ""),
                equipamento = obj.optString("equipamento", "Geral"),
                cargaAtualKg = obj.optDouble("cargaAtualKg", 0.0).toFloat(),
                novaCargaSugeridaKg = obj.optDouble("novaCargaSugeridaKg", 0.0).toFloat(),
                metaReps = obj.optInt("metaReps", 10),
                motivoCurto = obj.optString("motivoCurto", "")
            )
        }

        fun listToJson(list: List<GymLoadAdjustment>): String {
            val arr = JSONArray()
            list.forEach { arr.put(it.toJsonObject()) }
            return arr.toString()
        }

        fun listFromJson(jsonStr: String?): List<GymLoadAdjustment> {
            if (jsonStr.isNullOrBlank()) return emptyList()
            val list = mutableListOf<GymLoadAdjustment>()
            try {
                val arr = JSONArray(jsonStr)
                for (i in 0 until arr.length()) {
                    list.add(fromJsonObject(arr.getJSONObject(i)))
                }
            } catch (_: Exception) {}
            return list
        }
    }
}

data class PhysiologistAnalysisResult(
    val reportText: String,
    val sourceLabel: String, // "Gemini 2.5 Flash Cloud" or "Motor Fisiológico Local"
    val isFromCloud: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val loadAdjustments: List<GymLoadAdjustment> = emptyList()
)

object AiPhysiologistAnalyzer {
    private const val PREFS_NAME = "max_physiologist_ai_prefs"
    private const val KEY_GEMINI_API_KEY = "pref_gemini_api_key"
    private const val KEY_ECO_MODE = "pref_ai_eco_mode"
    private const val KEY_LAST_CLOUD_DATE = "pref_ai_last_cloud_date"
    private const val KEY_CLOUD_USED_TODAY = "pref_ai_cloud_used_today"
    private const val KEY_EXTRA_QUOTA_TODAY = "pref_ai_extra_quota_today"
    private const val PREFIX_SESSION_REPORT = "cached_report_"
    private const val PREFIX_SESSION_SOURCE = "cached_source_"
    private const val PREFIX_SESSION_ADJUSTMENTS = "cached_adj_"

    private const val GEMINI_ENDPOINT =
        "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun todayDateKey(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    }

    private fun ensureQuotaDayCurrent(context: Context) {
        val p = prefs(context)
        val today = todayDateKey()
        val savedDate = p.getString(KEY_LAST_CLOUD_DATE, "") ?: ""
        if (savedDate != today) {
            p.edit()
                .putString(KEY_LAST_CLOUD_DATE, today)
                .putInt(KEY_CLOUD_USED_TODAY, 0)
                .putInt(KEY_EXTRA_QUOTA_TODAY, 0)
                .apply()
        }
    }

    fun getSavedUserApiKey(context: Context): String {
        return prefs(context).getString(KEY_GEMINI_API_KEY, "")?.trim() ?: ""
    }

    fun setSavedUserApiKey(context: Context, apiKey: String) {
        prefs(context).edit().putString(KEY_GEMINI_API_KEY, apiKey.trim()).apply()
    }

    fun getEffectiveApiKey(context: Context): String {
        val userKey = getSavedUserApiKey(context)
        if (userKey.isNotBlank() && userKey.length >= 20) return userKey
        val buildKey = BuildConfig.GEMINI_API_KEY_1.trim()
        if (buildKey.isNotBlank() &&
            !buildKey.equals("MY_GEMINI_API_KEY", ignoreCase = true) &&
            !buildKey.equals("YOUR_API_KEY", ignoreCase = true) &&
            !buildKey.equals("null", ignoreCase = true) &&
            buildKey.length >= 20
        ) {
            return buildKey
        }
        return ""
    }

    fun hasValidApiKey(context: Context): Boolean {
        return getEffectiveApiKey(context).isNotBlank()
    }

    fun isEcoModeEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_ECO_MODE, true)
    }

    fun setEcoModeEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ECO_MODE, enabled).apply()
    }

    fun getCloudUsedToday(context: Context): Int {
        ensureQuotaDayCurrent(context)
        return prefs(context).getInt(KEY_CLOUD_USED_TODAY, 0)
    }

    fun getDailyAllowedCloudCalls(context: Context): Int {
        ensureQuotaDayCurrent(context)
        val extra = prefs(context).getInt(KEY_EXTRA_QUOTA_TODAY, 0)
        return 1 + extra
    }

    fun getRemainingCloudQuotaToday(context: Context): Int {
        ensureQuotaDayCurrent(context)
        if (!isEcoModeEnabled(context)) return 99
        return (getDailyAllowedCloudCalls(context) - getCloudUsedToday(context)).coerceAtLeast(0)
    }

    fun canUseCloudToday(context: Context): Boolean {
        if (!hasValidApiKey(context)) return false
        if (!isEcoModeEnabled(context)) return true
        return getRemainingCloudQuotaToday(context) > 0
    }

    fun unlockExtraAnalysisToday(context: Context): Int {
        ensureQuotaDayCurrent(context)
        val p = prefs(context)
        val currentExtra = p.getInt(KEY_EXTRA_QUOTA_TODAY, 0) + 1
        p.edit().putInt(KEY_EXTRA_QUOTA_TODAY, currentExtra).apply()
        return getRemainingCloudQuotaToday(context)
    }

    private fun recordCloudUsage(context: Context) {
        ensureQuotaDayCurrent(context)
        val p = prefs(context)
        val used = p.getInt(KEY_CLOUD_USED_TODAY, 0) + 1
        p.edit().putInt(KEY_CLOUD_USED_TODAY, used).apply()
    }

    fun saveCachedAnalysisForSession(
        context: Context,
        sessionId: String,
        result: PhysiologistAnalysisResult
    ) {
        if (sessionId.isBlank()) return
        prefs(context).edit()
            .putString(PREFIX_SESSION_REPORT + sessionId, result.reportText)
            .putString(PREFIX_SESSION_SOURCE + sessionId, result.sourceLabel)
            .putString(
                PREFIX_SESSION_ADJUSTMENTS + sessionId,
                GymLoadAdjustment.listToJson(result.loadAdjustments)
            )
            .apply()
    }

    fun normalizeGymWorkoutName(rawName: String): String {
        return rawName
            .replace(Regex("""\s*-\s*Recalculated Split\b""", RegexOption.IGNORE_CASE), "")
            .trim()
    }

    private fun sanitizeLegacyReportText(rawReport: String): String {
        var text = rawReport
        // 1. Remove estrutura duplicada entre parênteses ex: "2R + 2W + 2x (3R + 3W) + 2R + 2W (2R + 2W + 2x (3R + 3W) + 2R + 2W)"
        val dupRegex = Regex("""Estrutura Executada:\s*(.+?)\s*\(\1\)""")
        text = dupRegex.replace(text) { m ->
            "Estrutura Executada: ${m.groupValues[1].trim()}"
        }
        // 2. Corrige porcentagens > 100% da FCmáx estimada fixa (ex: "Pico de 184 bpm (108% da FCmáx estimada de 170 bpm)")
        val hrRegex = Regex("""Pico de (\d+)\s*bpm\s*\((\d+)%\s*da FCmáx estimada de (\d+)\s*bpm\)""")
        text = hrRegex.replace(text) { m ->
            val peak = m.groupValues[1].toIntOrNull() ?: 184
            val calibratedMax = maxOf(peak + 4, 188)
            val calibratedPct = ((peak * 100.0) / calibratedMax).safeRoundToInt().safeCoerceIn(50, 100)
            "Pico de $peak bpm ($calibratedPct% da FCmáx real calibrada em $calibratedMax bpm)"
        }
        // 3. Remove sufixo "- Recalculated Split" e desduplica linhas repetidas de "Interferência Híbrida (48h)"
        text = text.replace(Regex("""\s*-\s*Recalculated Split\b""", RegexOption.IGNORE_CASE), "")
        val seenHybridKeys = mutableSetOf<String>()
        val hybridLineRegex = Regex("""^•\s*Interferência Híbrida\s*\(48h\):\s*treino\s*'([^']+)'\s*realizado há\s*(\d+)h.*$""", RegexOption.IGNORE_CASE)
        val cleanedLines = mutableListOf<String>()
        for (line in text.lines()) {
            val trimmed = line.trim()
            val match = hybridLineRegex.matchEntire(trimmed)
            if (match != null) {
                val wName = normalizeGymWorkoutName(match.groupValues[1]).lowercase(Locale.ROOT)
                val hoursStr = match.groupValues[2]
                val key = "$wName|$hoursStr"
                if (seenHybridKeys.add(key)) {
                    val fixedLine = if (
                        (wName.contains("mobilidade") || wName.contains("recuperação") || wName.contains("recuperacao") || line.contains("volume total de 0 kg")) &&
                        line.contains("membros superiores/core", ignoreCase = true)
                    ) {
                        line.replace(
                            Regex("""volume total de 0(?:[.,]0)?\s*kg\s*\(membros superiores/core\)""", RegexOption.IGNORE_CASE),
                            "sessão de Mobilidade / Recuperação Ativa (0 kg - sem sobrecarga mecânica)"
                        )
                    } else {
                        line
                    }
                    cleanedLines.add(fixedLine)
                }
            } else {
                cleanedLines.add(line)
            }
        }
        var sanitized = cleanedLines.joinToString("\n")
        sanitized = sanitized.replace("Recuperação Autonômica nos Intervalos (HRR):", "Recuperação Cardíaca nos Intervalos (HRR):")
        sanitized = sanitized.replace("recuperação autonômica média de", "queda observada média de")
        return sanitized
    }

    fun getCachedAnalysisForSession(
        context: Context,
        sessionId: String,
        fallbackReportText: String? = null,
        fallbackAdjustmentsJson: String? = null
    ): PhysiologistAnalysisResult? {
        val p = prefs(context)
        val rawSavedReport = p.getString(PREFIX_SESSION_REPORT + sessionId, null)
            ?.takeIf { it.isNotBlank() }
            ?: fallbackReportText?.takeIf { it.isNotBlank() }
            ?: return null
        val savedReport = sanitizeLegacyReportText(rawSavedReport)

        val savedSource = p.getString(PREFIX_SESSION_SOURCE + sessionId, null)
            ?: if (savedReport.contains("Gemini 2.5 Flash", ignoreCase = true)) {
                "Gemini 2.5 Flash Cloud"
            } else {
                "Cache da Sessão • MAX PERFORMANCE"
            }

        val savedAdjStr = p.getString(PREFIX_SESSION_ADJUSTMENTS + sessionId, null)
            ?.takeIf { it.isNotBlank() }
            ?: fallbackAdjustmentsJson
        val adjustments = GymLoadAdjustment.listFromJson(savedAdjStr)

        return PhysiologistAnalysisResult(
            reportText = savedReport,
            sourceLabel = savedSource,
            isFromCloud = savedSource.contains("Gemini", ignoreCase = true),
            loadAdjustments = adjustments
        )
    }

    // =========================================================================
    // 1. ANÁLISE PÓS-TREINO: MUSCULAÇÃO (MAX GYM)
    // =========================================================================

    suspend fun analyzeGymSession(
        context: Context,
        session: GymWorkoutSession,
        allGymSessions: List<GymWorkoutSession>,
        recentRunningSessions: List<RunningSession>,
        userProfile: UserProfileData?,
        checkIn: PreAnalysisCheckIn,
        forceCloudRegenerate: Boolean = false
    ): PhysiologistAnalysisResult = withContext(Dispatchers.IO) {
        val deterministicAdjustments = computeGymLoadAdjustments(session, allGymSessions, context)

        val localReport = generateLocalGymPhysiologistReport(
            session = session,
            allGymSessions = allGymSessions,
            recentRunningSessions = recentRunningSessions,
            userProfile = userProfile,
            checkIn = checkIn,
            adjustments = deterministicAdjustments,
            context = context
        )

        val shouldCallCloud = hasValidApiKey(context) &&
            (forceCloudRegenerate || canUseCloudToday(context))

        if (shouldCallCloud) {
            val prompt = buildGymCloudPrompt(
                session = session,
                allGymSessions = allGymSessions,
                recentRunningSessions = recentRunningSessions,
                userProfile = userProfile,
                checkIn = checkIn,
                deterministicAdjustments = deterministicAdjustments,
                context = context
            )
            val cloudText = callGeminiFlashRestApi(
                apiKey = getEffectiveApiKey(context),
                systemInstruction = GYM_SYSTEM_INSTRUCTION,
                userPrompt = prompt
            )
            if (!cloudText.isNullOrBlank()) {
                recordCloudUsage(context)
                val result = PhysiologistAnalysisResult(
                    reportText = cloudText.trim(),
                    sourceLabel = "Gemini 2.5 Flash Cloud",
                    isFromCloud = true,
                    loadAdjustments = deterministicAdjustments
                )
                saveCachedAnalysisForSession(context, session.id, result)
                return@withContext result
            }
        }

        val fallbackResult = PhysiologistAnalysisResult(
            reportText = localReport,
            sourceLabel = "Motor Fisiológico Local • Determinístico",
            isFromCloud = false,
            loadAdjustments = deterministicAdjustments
        )
        saveCachedAnalysisForSession(context, session.id, fallbackResult)
        fallbackResult
    }

    // =========================================================================
    // 2. ANÁLISE PÓS-TREINO: CORRIDA (MAX RUN)
    // =========================================================================

    suspend fun analyzeRunningSession(
        context: Context,
        session: RunningSession,
        allRunningSessions: List<RunningSession>,
        recentGymSessions: List<GymWorkoutSession>,
        userProfile: UserProfileData?,
        sprintAnalytics: SprintAnalytics,
        splits: List<RecordedKmSplit>,
        gpsPoints: List<GpsTrackPoint>,
        checkIn: PreAnalysisCheckIn,
        forceCloudRegenerate: Boolean = false
    ): PhysiologistAnalysisResult = withContext(Dispatchers.IO) {
        val localReport = generateLocalRunningPhysiologistReport(
            context = context,
            session = session,
            allRunningSessions = allRunningSessions,
            recentGymSessions = recentGymSessions,
            userProfile = userProfile,
            sprintAnalytics = sprintAnalytics,
            splits = splits,
            gpsPoints = gpsPoints,
            checkIn = checkIn
        )

        val conservativeVerdictBlock = buildConservativePhysiologistVerdict(session)

        val shouldCallCloud = hasValidApiKey(context) &&
            (forceCloudRegenerate || canUseCloudToday(context))

        if (shouldCallCloud) {
            val prompt = buildRunningCloudPrompt(
                context = context,
                session = session,
                allRunningSessions = allRunningSessions,
                recentGymSessions = recentGymSessions,
                userProfile = userProfile,
                sprintAnalytics = sprintAnalytics,
                splits = splits,
                gpsPoints = gpsPoints,
                checkIn = checkIn
            )
            val cloudText = callGeminiFlashRestApi(
                apiKey = getEffectiveApiKey(context),
                systemInstruction = RUNNING_SYSTEM_INSTRUCTION,
                userPrompt = prompt
            )
            if (!cloudText.isNullOrBlank()) {
                recordCloudUsage(context)
                val finalCloudReport = if (
                    conservativeVerdictBlock != null &&
                    !cloudText.contains("Não há evidência suficiente de perda de condicionamento cardiorrespiratório", ignoreCase = true)
                ) {
                    "${cloudText.trim()}\n\n$conservativeVerdictBlock"
                } else {
                    cloudText.trim()
                }
                val result = PhysiologistAnalysisResult(
                    reportText = finalCloudReport,
                    sourceLabel = "Gemini 2.5 Flash Cloud",
                    isFromCloud = true,
                    loadAdjustments = emptyList()
                )
                saveCachedAnalysisForSession(context, session.id, result)
                return@withContext result
            }
        }

        val fallbackResult = PhysiologistAnalysisResult(
            reportText = localReport,
            sourceLabel = "Motor Fisiológico Local • Determinístico",
            isFromCloud = false,
            loadAdjustments = emptyList()
        )
        saveCachedAnalysisForSession(context, session.id, fallbackResult)
        fallbackResult
    }

    private fun buildConservativePhysiologistVerdict(session: RunningSession): String? {
        val preRun = session.preRunCheckIn
        val audit = session.executionAudit
        val hasPreRunPain = preRun?.localizedPains?.isNotEmpty() == true
        val hasNonMetabolicDeviation = audit?.reportedDeviationReasons?.any {
            it == com.example.data.model.InterruptionReason.GI_DISTRESS ||
                it == com.example.data.model.InterruptionReason.EXTERNAL_STOP
        } == true
        if (!hasPreRunPain && !hasNonMetabolicDeviation) return null

        val reasons = buildList {
            preRun?.localizedPains?.forEach { p ->
                val gait = if (p.altersGaitMechanics) ", altera passada" else ""
                add("Dor localizada em ${p.region.label} (${p.intensityCr10}/10$gait)")
            }
            audit?.reportedDeviationReasons?.forEach { r ->
                add(r.label)
            }
        }.ifEmpty { listOf("Intercorrência periférica/externa") }.joinToString("; ")

        val rpeCr10Val = session.rpeCr10 ?: session.rpe.takeIf { it > 0 } ?: 6
        val sRpeUaVal = session.sRpeLoadUa
            ?: com.example.data.model.calculateSrpeLoadUa(session.durationSec, rpeCr10Val)
            ?: 0

        return buildString {
            appendLine("• Veredito Fisiológico Conservador:")
            appendLine("  1. Não há evidência suficiente de perda de condicionamento cardiorrespiratório.")
            appendLine("  2. A execução foi impactada por fatores não-metabólicos/periféricos reportados ($reasons).")
            appendLine("  3. A sessão gerou carga interna de $sRpeUaVal UA e não deve ser usada isoladamente para medir evolução de ritmo.")
            append("  4. A próxima sessão depende da recuperação muscular e clínica.")
        }
    }

    /**
     * Calcula as métricas avançadas do Pacote 2 (Fator de Eficiência EF, Deriva Cardíaca Pa:Hr com Gatekeeper estrito
     * e Consistência dos Tiros RUN_WORK) de forma determinística e pura.
     */
    fun computeAdvancedRunningMetrics(
        session: RunningSession,
        gpsPoints: List<GpsTrackPoint> = emptyList()
    ): com.example.data.model.AdvancedRunningMetrics {
        val sprintAnalytics = com.example.data.model.calculateSprintAnalytics(session, gpsPoints)
        val intervals = com.example.data.model.buildExecutedIntervalsForSession(session, gpsPoints)
        val hrList = session.hrTimeline.toHrTimelineList().filter { it in 55..225 }

        // 1. Fator de Eficiência (EF = Velocidade Média Correndo (m/min) / FC Média Correndo (bpm))
        val runningTimeSec = sprintAnalytics.runningTimeSec
        val runningPaceSec = sprintAnalytics.avgSprintPaceSecPerKm.takeIf { it in 130..900 }
            ?: session.mainRunAvgPaceSecPerKm?.takeIf { it in 130..900 }
            ?: session.avgPaceSecPerKm.takeIf { it in 130..900 }
            ?: 0
        val runningSpeedMPerMin = if (runningPaceSec > 0) 60000.0 / runningPaceSec.toDouble() else 0.0

        val runWorkIntervals = intervals.filter {
            it.type == com.example.data.model.IntervalBlockType.RUN_WORK ||
                it.type == com.example.data.model.IntervalBlockType.UNSTRUCTURED
        }
        val runningAvgHrBpm: Double? = runWorkIntervals.mapNotNull { it.avgHrBpm }.filter { it > 80 }
            .takeIf { it.isNotEmpty() }?.average()
            ?: session.avgHr?.takeIf { it > 80 }?.toDouble()
            ?: hrList.filter { it > 80 }.takeIf { it.isNotEmpty() }?.average()

        val efficiencyFactor: Double? = if (
            runningAvgHrBpm != null &&
            runningAvgHrBpm > 80.0 &&
            runningTimeSec >= 300 &&
            runningSpeedMPerMin > 0.0
        ) {
            ((runningSpeedMPerMin / runningAvgHrBpm) * 100.0).safeRoundToInt() / 100.0
        } else {
            null
        }

        // 2. Deriva Cardíaca (Pa:Hr / Desacoplamento Aeróbico) com Gatekeeper Estrito
        val hasProgrammedWalkBlocks = intervals.any {
            it.type == com.example.data.model.IntervalBlockType.RECOVERY_WALK ||
                it.type == com.example.data.model.IntervalBlockType.RECOVERY_JOG
        } || !sprintAnalytics.isContinuousRunMode
        val totalMainSec = (sprintAnalytics.runningTimeSec + sprintAnalytics.walkingTimeSec).coerceAtLeast(1)
        val walkFraction = sprintAnalytics.walkingTimeSec.toDouble() / totalMainSec.toDouble()

        val isEligibleForDrift = !hasProgrammedWalkBlocks &&
            runningTimeSec >= 1200 &&
            walkFraction < 0.05 &&
            runningAvgHrBpm != null &&
            runningAvgHrBpm > 80.0

        var cardiacDriftPct: Double? = null
        val gatekeeperNote: String?

        if (!isEligibleForDrift) {
            gatekeeperNote = "Não aplicável a treino intervalado/fracionado ou menor que 20 min contínuos"
        } else {
            // Exclui os primeiros 5 minutos (300s de inércia inicial) e divide o restante em 1ª metade e 2ª metade
            val kmSplits = com.example.data.model.buildSplitsForMode(
                session = session,
                mode = com.example.data.model.SplitSelectorMode.HALF_KM,
                gpsPoints = gpsPoints
            )
            var accSec = 0
            val postWarmupSplits = kmSplits.filter { sp ->
                val endSec = accSec + sp.durationSec
                accSec = endSec
                endSec > 300 && sp.paceSecPerKm in 140..900
            }
            if (postWarmupSplits.size >= 2 && (hrList.size >= 6 || postWarmupSplits.all { (it.avgHr ?: 0) > 80 })) {
                val midIdx = postWarmupSplits.size / 2
                val firstHalf = postWarmupSplits.subList(0, midIdx)
                val secondHalf = postWarmupSplits.subList(midIdx, postWarmupSplits.size)

                val p1Avg = firstHalf.map { it.paceSecPerKm }.average()
                val p2Avg = secondHalf.map { it.paceSecPerKm }.average()
                val v1MPerMin = if (!p1Avg.isNaN() && p1Avg > 0.0) 60000.0 / p1Avg else 0.0
                val v2MPerMin = if (!p2Avg.isNaN() && p2Avg > 0.0) 60000.0 / p2Avg else 0.0

                val hr1 = firstHalf.mapNotNull { it.avgHr }.filter { it > 80 }.takeIf { it.isNotEmpty() }?.average()
                    ?: if (hrList.size >= 6) {
                        val startIdx = (hrList.size * 0.20).toInt().safeCoerceIn(0, (hrList.lastIndex - 2).coerceAtLeast(0))
                        val midHrIdx = ((startIdx + hrList.size) / 2).coerceAtLeast(startIdx + 1).coerceAtMost(hrList.size)
                        hrList.subList(startIdx, midHrIdx).average()
                    } else runningAvgHrBpm
                val hr2 = secondHalf.mapNotNull { it.avgHr }.filter { it > 80 }.takeIf { it.isNotEmpty() }?.average()
                    ?: if (hrList.size >= 6) {
                        val startIdx = (hrList.size * 0.20).toInt().safeCoerceIn(0, (hrList.lastIndex - 2).coerceAtLeast(0))
                        val midHrIdx = ((startIdx + hrList.size) / 2).coerceAtLeast(startIdx + 1).coerceAtMost(hrList.size)
                        hrList.subList(midHrIdx, hrList.size).average()
                    } else runningAvgHrBpm

                if (!hr1.isNaN() && !hr2.isNaN() && hr1 > 80.0 && hr2 > 80.0 && v1MPerMin > 0.0 && v2MPerMin > 0.0) {
                    val ef1 = v1MPerMin / hr1
                    val ef2 = v2MPerMin / hr2
                    if (ef1 > 0.01) {
                        cardiacDriftPct = (((ef1 - ef2) / ef1) * 1000.0).safeRoundToInt() / 10.0
                    }
                }
            }
            gatekeeperNote = if (cardiacDriftPct == null) {
                "Dados insuficientes de FC contínua após os 5 min iniciais para cálculo de deriva"
            } else {
                null
            }
        }

        // 3. Consistência dos Tiros (RUN_WORK)
        val strictWorkBlocks = intervals.filter {
            it.type == com.example.data.model.IntervalBlockType.RUN_WORK && it.avgPaceSecPerKm in 130..900
        }
        val workCount = strictWorkBlocks.size
        val fastestWorkPace: Int?
        val slowestWorkPace: Int?
        val speedDropFirstToLastPct: Double?
        val speedSpreadMaxMinPct: Double?

        if (workCount >= 2) {
            fastestWorkPace = strictWorkBlocks.minOfOrNull { it.avgPaceSecPerKm }
            slowestWorkPace = strictWorkBlocks.maxOfOrNull { it.avgPaceSecPerKm }
            val firstPace = strictWorkBlocks.firstOrNull()?.avgPaceSecPerKm?.takeIf { it > 0 } ?: 360
            val lastPace = strictWorkBlocks.lastOrNull()?.avgPaceSecPerKm?.takeIf { it > 0 } ?: 360
            val vFirst = 1000.0 / firstPace.toDouble()
            val vLast = 1000.0 / lastPace.toDouble()
            speedDropFirstToLastPct = (((vLast - vFirst) / vFirst.coerceAtLeast(0.01)) * 1000.0).safeRoundToInt() / 10.0

            val vMax = 1000.0 / (fastestWorkPace ?: 360).coerceAtLeast(1).toDouble()
            val vMin = 1000.0 / (slowestWorkPace ?: 360).coerceAtLeast(1).toDouble()
            speedSpreadMaxMinPct = (((vMax - vMin) / vMax.coerceAtLeast(0.01)) * 1000.0).safeRoundToInt() / 10.0
        } else {
            fastestWorkPace = strictWorkBlocks.firstOrNull()?.avgPaceSecPerKm
            slowestWorkPace = strictWorkBlocks.firstOrNull()?.avgPaceSecPerKm
            speedDropFirstToLastPct = null
            speedSpreadMaxMinPct = null
        }

        val recoveryDeltas = intervals.filter {
            it.type == com.example.data.model.IntervalBlockType.RECOVERY_WALK ||
                it.type == com.example.data.model.IntervalBlockType.RECOVERY_JOG
        }.mapNotNull { it.hrRecoveryDeltaBpm }.filter { it > 0 }

        val avgHrRecovery = if (recoveryDeltas.isNotEmpty()) {
            recoveryDeltas.average().safeRoundToInt()
        } else null

        return com.example.data.model.AdvancedRunningMetrics(
            efficiencyFactorMeterPerBeat = efficiencyFactor,
            cardiacDriftPct = cardiacDriftPct,
            isCardiacDriftApplicable = isEligibleForDrift && cardiacDriftPct != null,
            cardiacDriftGatekeeperNote = gatekeeperNote,
            workIntervalsCount = workCount,
            fastestWorkPaceSecPerKm = fastestWorkPace,
            slowestWorkPaceSecPerKm = slowestWorkPace,
            workSpeedDropFirstToLastPct = speedDropFirstToLastPct,
            workSpeedSpreadMaxMinPct = speedSpreadMaxMinPct,
            avgHrRecoveryBetweenWorkBpm = avgHrRecovery,
            ambientTempCelsius = session.advancedMetrics?.ambientTempCelsius,
            ambientHumidityPct = session.advancedMetrics?.ambientHumidityPct
        )
    }

    // =========================================================================
    // 3. CLIENTE HTTPS NATIVO (GEMINI 2.5 FLASH REST API)
    // =========================================================================

    private fun callGeminiFlashRestApi(
        apiKey: String,
        systemInstruction: String,
        userPrompt: String
    ): String? {
        var connection: HttpURLConnection? = null
        return try {
            val url = URL("$GEMINI_ENDPOINT?key=$apiKey")
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15_000
                readTimeout = 25_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            }

            val payload = JSONObject().apply {
                put(
                    "systemInstruction",
                    JSONObject().put(
                        "parts",
                        JSONArray().put(JSONObject().put("text", systemInstruction))
                    )
                )
                put(
                    "contents",
                    JSONArray().put(
                        JSONObject().apply {
                            put("role", "user")
                            put(
                                "parts",
                                JSONArray().put(JSONObject().put("text", userPrompt))
                            )
                        }
                    )
                )
                put(
                    "generationConfig",
                    JSONObject().apply {
                        put("temperature", 0.35)
                        put("maxOutputTokens", 1800)
                    }
                )
            }

            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(payload.toString())
                writer.flush()
            }

            val code = connection.responseCode
            if (code in 200..299) {
                val responseStr = BufferedReader(
                    InputStreamReader(connection.inputStream, Charsets.UTF_8)
                ).use { it.readText() }

                val json = JSONObject(responseStr)
                val candidates = json.optJSONArray("candidates")
                if (candidates != null && candidates.length() > 0) {
                    val content = candidates.getJSONObject(0).optJSONObject("content")
                    val parts = content?.optJSONArray("parts")
                    if (parts != null && parts.length() > 0) {
                        val sb = StringBuilder()
                        for (i in 0 until parts.length()) {
                            sb.append(parts.getJSONObject(i).optString("text", ""))
                        }
                        sb.toString().takeIf { it.isNotBlank() }
                    } else null
                } else null
            } else {
                null
            }
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    // =========================================================================
    // 4. FÍSICA DE PROGRESSÃO DE CARGA DOS EQUIPAMENTOS (MAX GYM)
    // =========================================================================

    /**
     * Regra Física dos Equipamentos MAX GYM:
     * - Halteres <= 10 kg sobem de 1 em 1 kg;
     * - Halteres > 10 kg sobem estritamente em números pares de 2 em 2 kg (12, 14, 16, 18 kg...);
     * - Polia/Torre sobe por placa (5 kg);
     * - Barra/Máquina/Smith sobe em passos de 2,5 kg;
     * - Se subir para o próximo par/passo representar salto > 10% (ex: 12 kg -> 14 kg = +16,7%),
     *   recomenda primeiro consolidar no topo da faixa (+1 a +2 reps ou pico de contração de 2s)
     *   mantendo a carga atual antes de saltar para o próximo par.
     */
    fun computeGymLoadAdjustments(
        session: GymWorkoutSession,
        allGymSessions: List<GymWorkoutSession>,
        context: Context? = null
    ): List<GymLoadAdjustment> {
        val ptBr = Locale.forLanguageTag("pt-BR")
        val previousSessions = allGymSessions
            .filter { it.id != session.id && it.date < session.date }
            .sortedByDescending { it.date }

        return session.exercises.mapNotNull { ex ->
            val completedSets = ex.sets.filter { it.completed && it.weight > 0f }
            val referenceSets = if (completedSets.isNotEmpty()) completedSets else ex.sets.filter { it.weight > 0f }
            if (referenceSets.isEmpty()) return@mapNotNull null

            val lastSet = referenceSets.last()
            val topWeight = referenceSets.maxOf { it.weight }
            val avgReps = referenceSets.map { it.reps }.average().safeRoundToInt(10).coerceAtLeast(1)
            val firstSetReps = referenceSets.first().reps
            val lastSetReps = lastSet.reps
            val repDrop = firstSetReps - lastSetReps

            val savedCategory = WeightCalculator.getSavedEquipmentCategory(context, ex.name, ex.equipment)
            val hasCustomStep = WeightCalculator.hasExplicitCustomStep(context, ex.name)
            val customStepKg = WeightCalculator.getSavedWeightStep(context, ex.name, ex.equipment, topWeight)
            val stepFormatted = WeightCalculator.formatStepNumber(customStepKg)

            val isDumbbell = savedCategory.contains("halter", ignoreCase = true)

            val nextPhysicalWeight = if (hasCustomStep) {
                (java.lang.Math.round((topWeight + customStepKg) * 100f) / 100f)
            } else if (isDumbbell) {
                if (topWeight < 10f) {
                    (topWeight + 1f).safeRoundToInt().toFloat()
                } else {
                    val baseEven = (topWeight.safeRoundToInt() / 2) * 2
                    (baseEven + 2).toFloat()
                }
            } else {
                (java.lang.Math.round((topWeight + customStepKg) * 100f) / 100f)
            }

            val jumpPct = if (topWeight > 0f) ((nextPhysicalWeight - topWeight) / topWeight) * 100f else 0f
            val hasPainNote = ex.notes.contains("dor", ignoreCase = true) ||
                ex.notes.contains("incômodo", ignoreCase = true) ||
                ex.notes.contains("lesão", ignoreCase = true)

            // Verifica histórico anterior do exercício
            val prevEx = previousSessions.firstNotNullOfOrNull { prevS ->
                prevS.exercises.find { it.name.equals(ex.name, ignoreCase = true) }
            }
            val prevTopWeight = prevEx?.sets?.filter { it.completed }?.maxOfOrNull { it.weight } ?: 0f

            val (suggestedKg, suggestedReps, reason) = when {
                hasPainNote -> {
                    Triple(
                        topWeight,
                        avgReps.coerceAtLeast(8),
                        "Manter carga e priorizar amplitude sem dor relatada em campo"
                    )
                }
                repDrop >= 4 || avgReps < 8 -> {
                    Triple(
                        topWeight,
                        (avgReps + 1).coerceAtMost(12),
                        "Queda de $repDrop reps entre séries; estabilizar fadiga e recuperar ${avgReps + 1} reps"
                    )
                }
                prevTopWeight > 0f && topWeight > prevTopWeight && ((topWeight - prevTopWeight) / prevTopWeight) > 0.10f -> {
                    Triple(
                        topWeight,
                        (avgReps + 1).coerceAtMost(12),
                        "Salto recente >10%; consolidar carga atual com +1 rep antes de novo incremento (+$stepFormatted kg)"
                    )
                }
                avgReps >= 10 && jumpPct > 10.0f -> {
                    val nextStr = formatKg(nextPhysicalWeight, ptBr)
                    val jumpStr = String.format(ptBr, "%.1f%%", jumpPct)
                    val eqLabel = if (isDumbbell && !hasCustomStep) {
                        "próximo par de halteres ($nextStr kg)"
                    } else {
                        "próximo salto da máquina (+$stepFormatted kg -> $nextStr kg)"
                    }
                    Triple(
                        topWeight,
                        (avgReps + 2).coerceAtMost(15),
                        "Subir p/ $eqLabel = salto de +$jumpStr (>10%). Consolidar ${(avgReps + 2).coerceAtMost(15)} reps + pico 2s antes de saltar"
                    )
                }
                avgReps >= 10 -> {
                    val stepDesc = "$savedCategory (Δ +$stepFormatted kg)"
                    Triple(
                        nextPhysicalWeight,
                        avgReps.coerceIn(8, 12),
                        "$stepDesc (+${String.format(ptBr, "%.1f%%", jumpPct)}) após consolidar $avgReps reps"
                    )
                }
                else -> {
                    Triple(
                        topWeight,
                        (avgReps + 1).coerceAtMost(12),
                        "Evoluir volume na faixa alvo (${avgReps} -> ${(avgReps + 1).coerceAtMost(12)} reps) antes de subir +$stepFormatted kg"
                    )
                }
            }

            GymLoadAdjustment(
                exercicio = ex.name,
                equipamento = "$savedCategory • Δ +$stepFormatted kg",
                cargaAtualKg = topWeight,
                novaCargaSugeridaKg = suggestedKg,
                metaReps = suggestedReps,
                motivoCurto = reason
            )
        }
    }

    // =========================================================================
    // 5. MOTOR DETERMINÍSTICO LOCAL — MUSCULAÇÃO (6 PASSOS OBRIGATÓRIOS)
    // =========================================================================

    private fun generateLocalGymPhysiologistReport(
        session: GymWorkoutSession,
        allGymSessions: List<GymWorkoutSession>,
        recentRunningSessions: List<RunningSession>,
        userProfile: UserProfileData?,
        checkIn: PreAnalysisCheckIn,
        adjustments: List<GymLoadAdjustment>,
        context: Context? = null
    ): String {
        val ptBr = Locale.forLanguageTag("pt-BR")
        val sb = StringBuilder()

        val durationMin = (session.durationSeconds / 60.0).coerceAtLeast(1.0)
        val densityKgPerMin = session.totalVolumeKg / durationMin
        val athleteWeight = (userProfile?.peso ?: 75f).coerceAtLeast(40f)
        val relativeVolume = session.totalVolumeKg / athleteWeight

        // Volume por grupo muscular
        val groupVolumes = linkedMapOf<String, Float>()
        var pushVolume = 0f
        var pullVolume = 0f
        var anteriorLegVolume = 0f
        var posteriorLegVolume = 0f

        session.exercises.forEach { ex ->
            val sets = ex.sets.filter { it.completed }
            val exVol = sets.fold(0f) { acc, s -> acc + (s.weight * s.reps) }
            val group = classifyMuscleGroup(ex.name, ex.category)
            groupVolumes[group] = (groupVolumes[group] ?: 0f) + exVol

            when (classifyMovementPattern(ex.name, ex.category)) {
                "PUSH" -> pushVolume += exVol
                "PULL" -> pullVolume += exVol
                "ANTERIOR_LEG" -> anteriorLegVolume += exVol
                "POSTERIOR_LEG" -> posteriorLegVolume += exVol
            }
        }

        // 1. RESUMO DA SESSÃO
        sb.appendLine("1. RESUMO DA SESSÃO")
        sb.appendLine(
            "• Volume Total: ${formatKg(session.totalVolumeKg, ptBr)} kg (${String.format(ptBr, "%.1fx", relativeVolume)} o peso corporal de ${formatKg(athleteWeight, ptBr)} kg)."
        )
        sb.appendLine(
            "• Duração e Densidade Mecânica: ${session.durationSeconds / 60} min (${session.completedSets}/${session.totalSets} séries concluídas) • Densidade: ${String.format(ptBr, "%.1f", densityKgPerMin)} kg/min."
        )
        if (groupVolumes.isNotEmpty()) {
            val groupsStr = groupVolumes.entries
                .sortedByDescending { it.value }
                .joinToString(" | ") { "${it.key}: ${formatKg(it.value, ptBr)} kg" }
            sb.appendLine("• Volume por Grupo Muscular: $groupsStr.")
        }
        sb.appendLine(
            "• Check-in Pós-Treino: Sensação final '${checkIn.effortPerception.label}'" +
                (if (checkIn.customNote.isNotBlank()) " • Nota: \"${checkIn.customNote}\"." else ".")
        )
        sb.appendLine()

        // 2. PROGRESSÃO
        sb.appendLine("2. PROGRESSÃO POR EXERCÍCIO (VS. ÚLTIMA SESSÃO)")
        val prevSessions = allGymSessions
            .filter { it.id != session.id && it.date < session.date }
            .sortedByDescending { it.date }

        session.exercises.forEach { ex ->
            val savedCat = WeightCalculator.getSavedEquipmentCategory(context, ex.name, ex.equipment)
            val isBw = WeightCalculator.isBodyweightExercise(context, ex.name, ex.equipment)
            val completedAll = ex.sets.filter { it.completed }
            val curSetsWeighted = completedAll.filter { it.weight > 0f }

            if (isBw && curSetsWeighted.isEmpty() && completedAll.isNotEmpty()) {
                val curTotalReps = completedAll.sumOf { it.reps }
                val prevMatch = prevSessions.firstNotNullOfOrNull { ps ->
                    ps.exercises.find { it.name.equals(ex.name, ignoreCase = true) }?.let { Pair(ps.date, it) }
                }
                if (prevMatch == null) {
                    sb.appendLine(
                        "• ${ex.name} [Peso Corporal]: LINHA DE BASE ($curTotalReps reps totais em ${completedAll.size} séries)."
                    )
                } else {
                    val (prevDate, prevEx) = prevMatch
                    val prevTotalReps = prevEx.sets.filter { it.completed }.sumOf { it.reps }
                    val dateShort = SimpleDateFormat("dd/MM", ptBr).format(Date(prevDate))
                    val status = when {
                        curTotalReps > prevTotalReps -> "PROGREDIU"
                        curTotalReps < prevTotalReps -> "REGREDIU"
                        else -> "MANTEVE"
                    }
                    sb.appendLine(
                        "• ${ex.name} [Peso Corporal]: $status vs. $dateShort (Volume calistênico: $prevTotalReps -> $curTotalReps reps totais)."
                    )
                }
            } else {
                val curTopW = curSetsWeighted.maxOfOrNull { it.weight } ?: 0f
                val curVol = curSetsWeighted.fold(0f) { acc, s -> acc + s.weight * s.reps }
                val savedStep = WeightCalculator.getSavedWeightStep(context, ex.name, ex.equipment, curTopW)
                val eqTag = if (WeightCalculator.isLbsMode(context, ex.name)) {
                    val stepLbs = WeightCalculator.getSavedStepLbs(context, ex.name)
                    "$savedCat • Δ +${WeightCalculator.formatStepNumber(stepLbs)} lbs (${WeightCalculator.formatStepNumber(savedStep)} kg)"
                } else {
                    "$savedCat • Δ +${WeightCalculator.formatStepNumber(savedStep)} kg"
                }
                val prevMatch = prevSessions.firstNotNullOfOrNull { ps ->
                    ps.exercises.find { it.name.equals(ex.name, ignoreCase = true) }?.let { Pair(ps.date, it) }
                }
                if (prevMatch == null) {
                    sb.appendLine(
                        "• ${ex.name} [$eqTag]: LINHA DE BASE (${formatKg(curTopW, ptBr)} kg • vol. ${formatKg(curVol, ptBr)} kg)."
                    )
                } else {
                    val (prevDate, prevEx) = prevMatch
                    val prevSets = prevEx.sets.filter { it.completed && it.weight > 0f }
                    val prevTopW = prevSets.maxOfOrNull { it.weight } ?: 0f
                    val prevVol = prevSets.fold(0f) { acc, s -> acc + s.weight * s.reps }
                    val dateShort = SimpleDateFormat("dd/MM", ptBr).format(Date(prevDate))
                    val status = when {
                        curTopW > prevTopW || (curTopW == prevTopW && curVol > prevVol * 1.03f) -> "PROGREDIU"
                        curTopW < prevTopW || curVol < prevVol * 0.92f -> "REGREDIU"
                        else -> "MANTEVE"
                    }
                    sb.appendLine(
                        "• ${ex.name} [$eqTag]: $status vs. $dateShort (Carga máx: ${formatKg(prevTopW, ptBr)} -> ${formatKg(curTopW, ptBr)} kg | Vol: ${formatKg(prevVol, ptBr)} -> ${formatKg(curVol, ptBr)} kg)."
                    )
                }
            }
        }
        sb.appendLine()

        // 3. QUALIDADE DO ESTÍMULO
        sb.appendLine("3. QUALIDADE DO ESTÍMULO")
        val allReps = session.exercises.flatMap { ex -> ex.sets.filter { it.completed }.map { it.reps } }
        val avgSessionReps = if (allReps.isNotEmpty()) allReps.average() else 0.0
        val goalLabel = userProfile?.nivelExperiencia?.takeIf { it.isNotBlank() } ?: "Hipertrofia & Força"
        sb.appendLine("• RPE / RIR telemetrado por série: sem dados (avaliado via percepção global '${checkIn.effortPerception.label}' e curva de repetições).")
        sb.appendLine("• Intervalo de Descanso cronometrado por série: sem dados (densidade global observada de ${String.format(ptBr, "%.1f", densityKgPerMin)} kg/min).")
        sb.appendLine(
            "• Faixa de Repetições vs. Objetivo ($goalLabel): média de ${String.format(ptBr, "%.1f", avgSessionReps)} reps/série " +
                when {
                    avgSessionReps in 8.0..12.5 -> "(dentro da janela ótima de tensão mecânica 8–12 reps)."
                    avgSessionReps < 8.0 -> "(predomínio tensional/força submáxima <8 reps)."
                    else -> "(predomínio metabólico/resistência muscular >12 reps)."
                }
        )
        sb.appendLine()

        // 4. EQUILÍBRIO E INTERFERÊNCIA HÍBRIDA (48H)
        sb.appendLine("4. EQUILÍBRIO BIOMECÂNICO E INTERFERÊNCIA HÍBRIDA (48H)")
        if (pushVolume > 0f || pullVolume > 0f) {
            val ratio = if (pullVolume > 0f) pushVolume / pullVolume else 0f
            sb.appendLine(
                "• Relação Empurrar / Puxar: Empurrar ${formatKg(pushVolume, ptBr)} kg vs. Puxar ${formatKg(pullVolume, ptBr)} kg" +
                    (if (ratio > 0f) " (razão ${String.format(ptBr, "%.2f", ratio)}:1)." else ".")
            )
        } else {
            sb.appendLine("• Relação Empurrar / Puxar: sessão focada em outros segmentos.")
        }
        if (anteriorLegVolume > 0f || posteriorLegVolume > 0f) {
            sb.appendLine(
                "• Relação Anterior / Posterior de Coxa: Anterior ${formatKg(anteriorLegVolume, ptBr)} kg vs. Posterior ${formatKg(posteriorLegVolume, ptBr)} kg."
            )
        }
        val runsLast48h = recentRunningSessions.filter { r ->
            val diffHours = (session.date - r.date) / (1000.0 * 3600.0)
            diffHours in 0.0..48.0
        }
        if (runsLast48h.isEmpty()) {
            sb.appendLine("• Cruzamento Híbrido (48h): nenhuma sessão de corrida registrada nas 48h anteriores — baixo risco de interferência concorrente.")
        } else {
            runsLast48h.forEach { r ->
                val hoursAgo = ((session.date - r.date) / (1000.0 * 3600.0)).safeRoundToInt()
                val distKm = r.distanceMeters / 1000.0
                val pMin = r.avgPaceSecPerKm / 60
                val pSec = r.avgPaceSecPerKm % 60
                sb.appendLine(
                    "• Cruzamento Híbrido (48h): corrida de ${String.format(ptBr, "%.2f", distKm)} km (pace ${String.format(Locale.US, "%d'%02d\"", pMin, pSec)}/km) realizada há ${hoursAgo}h. Depleção residual de glicogênio considerada na leitura de fadiga."
                )
            }
        }
        sb.appendLine()

        // 5. FADIGA E RISCO
        sb.appendLine("5. FADIGA E RISCO ARTICULAR")
        val fatigueAlerts = mutableListOf<String>()
        session.exercises.forEach { ex ->
            val sets = ex.sets.filter { it.completed }
            if (sets.size >= 2) {
                val drop = sets.first().reps - sets.last().reps
                if (drop >= 4) {
                    fatigueAlerts.add("Queda brusca de $drop reps em ${ex.name} (${sets.first().reps} -> ${sets.last().reps} reps)")
                }
            }
            if (ex.notes.isNotBlank()) {
                fatigueAlerts.add("Nota de campo em ${ex.name}: \"${ex.notes}\"")
            }
        }
        if (checkIn.customNote.isNotBlank()) {
            fatigueAlerts.add("Observação do atleta: \"${checkIn.customNote}\"")
        }
        if (fatigueAlerts.isEmpty()) {
            sb.appendLine("• Nenhuma queda abrupta de repetições (>=4 reps) ou queixa álgica detectada. Estabilidade neuromuscular preservada entre as séries.")
        } else {
            fatigueAlerts.forEach { alert ->
                sb.appendLine("• $alert.")
            }
        }
        sb.appendLine()

        // 6. PONTOS DE MELHORIA + ALTERNATIVAS PRÁTICAS
        sb.appendLine("6. PONTOS DE MELHORIA (PRIORIZADOS) & ALTERNATIVAS PRÁTICAS")
        val improvements = mutableListOf<String>()
        val consolidatedExs = adjustments.filter { it.novaCargaSugeridaKg == it.cargaAtualKg && it.motivoCurto.contains(">10%") }
        if (consolidatedExs.isNotEmpty()) {
            val dumbbellExs = consolidatedExs.filter {
                it.equipamento.contains("halter", ignoreCase = true) ||
                    it.exercicio.contains("halter", ignoreCase = true)
            }
            val machineOrBarExs = consolidatedExs.filterNot {
                it.equipamento.contains("halter", ignoreCase = true) ||
                    it.exercicio.contains("halter", ignoreCase = true)
            }
            if (dumbbellExs.isNotEmpty()) {
                improvements.add(
                    "Respeitar a regra física de halteres (+2 kg por mão = +4 kg totais) nos exercícios com halteres (${dumbbellExs.joinToString { it.exercicio }}): consolidar +1 a +2 reps com 2s de pico de contração antes de saltar >10% de carga."
                )
            }
            if (machineOrBarExs.isNotEmpty()) {
                improvements.add(
                    "Progredir carga de forma conservadora (+2,5 kg a +5 kg nas anilhas/placas da máquina) ou consolidar +1 a +2 reps com 2s de pico de contração antes de saltar >10% de carga em: ${machineOrBarExs.joinToString { it.exercicio }}."
                )
            }
        }
        if (anteriorLegVolume > 0f && posteriorLegVolume < anteriorLegVolume * 0.45f) {
            improvements.add("Elevar o volume de cadeia posterior (isquiotibiais/glúteos) para proteger joelhos e dar suporte à propulsão na corrida.")
        }
        if (pushVolume > 0f && pullVolume < pushVolume * 0.7f) {
            improvements.add("Compensar o volume de puxadas/dorsais e retratores escapulares para equilíbrio glenoumeral.")
        }
        if (improvements.size < 3) {
            improvements.add("Padronizar descanso de 90–120s nos multiarticulares e 60–90s nos isoladores para manter densidade acima de ${String.format(ptBr, "%.0f", densityKgPerMin.coerceAtLeast(80.0))} kg/min.")
        }
        improvements.take(3).forEachIndexed { idx, item ->
            sb.appendLine("${idx + 1}) $item")
        }
        sb.appendLine("• Alternativa Prática 1: Se o próximo par de halteres gerar perda de amplitude, usar banco inclinado/polia constante ou cadência 3-1-1 na carga atual.")
        sb.appendLine("• Alternativa Prática 2: Em dias de treino duplo (Gym + Corrida TAF), manter RIR 2 (parar 2 reps antes da falha) nos exercícios de membros inferiores.")

        return sb.toString().trim()
    }

    // =========================================================================
    // 6. MOTOR DETERMINÍSTICO LOCAL — CORRIDA (4 PASSOS OBRIGATÓRIOS)
    // =========================================================================

    private fun resolveRealHrSourceDescription(
        context: Context?,
        session: RunningSession,
        checkIn: PreAnalysisCheckIn
    ): String {
        val bleManager = context?.let {
            runCatching { com.example.services.HeartRateBleManager.getInstance(it) }.getOrNull()
        }
        val connName = bleManager?.connectedDeviceName?.value?.trim().orEmpty()
        val linkedWatchName = bleManager?.linkedSmartwatchName?.value?.trim().orEmpty()
        val notesText = "${session.notes.orEmpty()} ${checkIn.customNote}".trim()

        val candidateDevice = connName.ifBlank { linkedWatchName }.ifBlank { notesText }
        val chestStrapKeywords = listOf(
            "polar h", "h10", "h9", "hrm", "magene", "coospo", "geoid", "gehod", "h500", "wahoo", "tickr"
        )
        val smartwatchKeywords = listOf(
            "watch", "redmi", "xiaomi", "band", "amazfit", "galaxy", "garmin", "apple", "haylou", "mi ", "coros", "huawei", "fitbit", "zeblaze"
        )

        val isVerifiedChestStrap = candidateDevice.isNotBlank() &&
            chestStrapKeywords.any { candidateDevice.contains(it, ignoreCase = true) }
        val isVerifiedSmartwatch = !isVerifiedChestStrap && (
            (candidateDevice.isNotBlank() && smartwatchKeywords.any { candidateDevice.contains(it, ignoreCase = true) }) ||
                (bleManager?.isSmartwatchBridgeActive?.value == true && linkedWatchName.isNotBlank()) ||
                checkIn.hrSource == HrSourceType.SMARTWATCH
            )

        return when {
            isVerifiedSmartwatch -> {
                val displayWatchName = when {
                    connName.isNotBlank() && smartwatchKeywords.any { connName.contains(it, ignoreCase = true) } -> connName
                    linkedWatchName.isNotBlank() -> linkedWatchName
                    else -> "Smartwatch Vinculado"
                }
                "Smartwatch ($displayWatchName • Sensor Óptico PPG em tempo real)"
            }
            isVerifiedChestStrap -> {
                val displayStrapName = connName.ifBlank { "Cinta BLE" }
                "Cinta Peitoral ($displayStrapName • alta precisão eletrocardiográfica ECG batimento a batimento)"
            }
            checkIn.hrSource == HrSourceType.CINTA_PEITORAL -> {
                "Cinta Peitoral (alta precisão eletrocardiográfica ECG batimento a batimento)"
            }
            else -> {
                "Smartwatch / Sensor Óptico PPG em tempo real"
            }
        }
    }

    internal fun resolveCoherentRpeCr10(
        session: RunningSession,
        checkIn: PreAnalysisCheckIn
    ): Int? {
        val rawRpe = session.rpeCr10?.takeIf { it in 1..10 }
            ?: session.rpe.takeIf { it in 1..10 }
        return when (checkIn.effortPerception) {
            EffortPerception.SOBROU_RESERVA -> {
                // "Sobrou reserva" jamais pode contradizer com RPE forte/exaustivo (ex.: default 6 • Forte)
                if (rawRpe == null || rawRpe >= 5) 3 else rawRpe
            }
            EffortPerception.ESFORCO_CONTROLADO -> rawRpe
            EffortPerception.NO_LIMITE -> rawRpe ?: 8
        }
    }

    internal fun generateLocalRunningPhysiologistReport(
        context: Context? = null,
        session: RunningSession,
        allRunningSessions: List<RunningSession>,
        recentGymSessions: List<GymWorkoutSession>,
        userProfile: UserProfileData?,
        sprintAnalytics: SprintAnalytics,
        splits: List<RecordedKmSplit>,
        gpsPoints: List<GpsTrackPoint>,
        checkIn: PreAnalysisCheckIn
    ): String {
        val ptBr = Locale.forLanguageTag("pt-BR")
        val sb = StringBuilder()

        val dossier = com.example.data.model.buildRunPhysiologyDossier(
            session = session,
            userProfile = userProfile,
            gpsPoints = gpsPoints
        )

        // Matemática 100% fechada (Pace = Tempo / Distância) separando Treino Principal vs. Sessão Completa
        val mainDistM = sprintAnalytics.mainDistMeters.coerceAtLeast(1)
        val mainDurSec = sprintAnalytics.mainDurationSec.coerceAtLeast(1)
        val mainDistKm = mainDistM / 1000.0
        val mainPaceSec = ((mainDurSec * 1000.0) / mainDistM.toDouble()).safeRoundToInt()
        val mainDurHhMmSs = com.example.data.model.formatSecondsToHhMmSs(mainDurSec)
        val mainPaceStr = formatPace(mainPaceSec)

        val totalDistM = sprintAnalytics.totalDistMeters.coerceAtLeast(mainDistM)
        val totalDistKm = totalDistM / 1000.0
        val warmupDeltaM = (totalDistM - mainDistM).coerceAtLeast(0)
        val totalDurSec = if (warmupDeltaM >= 50 && sprintAnalytics.totalDurationSec <= mainDurSec) {
            val plan = com.example.engine.RunningProgramManager.getPlanForWeek(
                session.weekNumber.coerceAtLeast(1),
                session.dayNumber.coerceAtLeast(1)
            )
            val warmupPlanSec = plan.blocks.filter {
                it.type == com.example.engine.RunningBlockType.WARMUP ||
                    it.type == com.example.engine.RunningBlockType.COOL_DOWN
            }.sumOf { it.durationSec }.takeIf { it > 0 } ?: ((warmupDeltaM / 1.45).safeRoundToInt().coerceAtLeast(180))
            mainDurSec + warmupPlanSec
        } else {
            sprintAnalytics.totalDurationSec.coerceAtLeast(mainDurSec)
        }
        val totalPaceSec = ((totalDurSec * 1000.0) / totalDistM.toDouble()).safeRoundToInt()
        val totalDurHhMmSs = com.example.data.model.formatSecondsToHhMmSs(totalDurSec)
        val totalPaceStr = formatPace(totalPaceSec)

        val cleanHeader = sprintAnalytics.headerIdentification.trim()
        val cleanStructure = sprintAnalytics.structureSummary.trim()
        val structureDisplay = if (
            cleanStructure.isBlank() ||
            cleanStructure.equals(cleanHeader, ignoreCase = true) ||
            cleanHeader.contains(cleanStructure, ignoreCase = true)
        ) {
            cleanHeader
        } else {
            "$cleanHeader ($cleanStructure)"
        }

        // 1. ATUALIZAÇÃO DO TREINO E RESPOSTA AO ESFORÇO
        sb.appendLine("1. ATUALIZAÇÃO DO TREINO E RESPOSTA AO ESFORÇO")
        sb.appendLine("• Estrutura Executada: $structureDisplay.")
        if (totalDistM > mainDistM + 20 || totalDurSec > mainDurSec + 15) {
            sb.appendLine(
                "• Treino Principal (Estímulos + Recuperações): ${String.format(ptBr, "%.2f", mainDistKm)} km em $mainDurHhMmSs (Pace: $mainPaceStr/km)."
            )
            sb.appendLine(
                "• Sessão Completa (c/ Aquecimento/Desaquecimento): ${String.format(ptBr, "%.2f", totalDistKm)} km em $totalDurHhMmSs (Pace global: $totalPaceStr/km)."
            )
        } else {
            sb.appendLine(
                "• Sessão Executada: ${String.format(ptBr, "%.2f", totalDistKm)} km em $totalDurHhMmSs (Pace: $totalPaceStr/km)."
            )
        }
        val effortAnalysis = when (checkIn.effortPerception) {
            EffortPerception.SOBROU_RESERVA ->
                "Atleta reportou 'Sobrou reserva', indicando excelente economia de corrida e margem metabólica para sustentar ou progredir o volume da planilha."
            EffortPerception.ESFORCO_CONTROLADO ->
                "Atleta reportou 'Esforço controlado', sinalizando estímulo na zona-alvo prescrita sem sobrecarga autonômica excessiva."
            EffortPerception.NO_LIMITE ->
                "Atleta reportou 'No limite / Exausto', exigindo atenção à recuperação glicogênica e hidratação nas próximas 24–48h."
        }
        sb.appendLine("• Resposta ao Esforço: $effortAnalysis")
        if (checkIn.customNote.isNotBlank() || !session.notes.isNullOrBlank()) {
            val combinedNote = listOfNotNull(
                checkIn.customNote.takeIf { it.isNotBlank() },
                session.notes?.takeIf { it.isNotBlank() }
            ).joinToString(" | ")
            sb.appendLine("• Observação de Campo: \"$combinedNote\".")
        }
        sb.appendLine()

        // 2. RESPOSTA CARDIOVASCULAR
        val isContinuousMode = sprintAnalytics.isContinuousRunMode
        if (isContinuousMode) {
            sb.appendLine("2. RESPOSTA CARDIOVASCULAR E CONSISTÊNCIA ENTRE PARCIAIS")
        } else {
            sb.appendLine("2. RESPOSTA CARDIOVASCULAR E RECUPERAÇÃO ENTRE TIROS")
        }
        val hrList = session.hrTimeline.toHrTimelineList().filter { it > 0 }
        val blockSplits = com.example.data.model.buildSplitsForMode(
            session = session,
            mode = com.example.data.model.SplitSelectorMode.BY_BLOCK,
            gpsPoints = gpsPoints
        )
        val splitHrValues = splits.mapNotNull { it.avgHr }.filter { it > 0 }
        val validHrTimeline = hrList.filter { it in 45..240 }
        val avgHr = session.avgHr?.takeIf { it > 0 }
            ?: if (validHrTimeline.isNotEmpty()) validHrTimeline.average().safeRoundToInt()
            else if (splitHrValues.isNotEmpty()) splitHrValues.average().safeRoundToInt()
            else dossier.avgHeartRateBpm.takeIf { it > 0 }
        val maxHr = session.maxHr?.takeIf { it > 0 }
            ?: validHrTimeline.maxOrNull()
            ?: splitHrValues.maxOrNull()
            ?: dossier.maxHeartRateBpm.takeIf { it > 0 }
            ?: avgHr
        val hasValidHrData = (avgHr != null && avgHr > 0) || (maxHr != null && maxHr > 0)

        val validAge = userProfile?.idade?.takeIf { it in 10..100 }
        val calibratedMaxHr = userProfile?.fcMaxCalibradaBpm?.takeIf { it in 100..235 }
        val historicalPeakHr = allRunningSessions.mapNotNull { it.maxHr?.takeIf { v -> v in 45..240 } }.maxOrNull() ?: maxHr
        val effectiveMaxHr = com.example.data.model.calculateEffectiveMaxHr(
            ageYears = validAge,
            sessionMaxHrBpm = maxHr,
            historicalPeakHrBpm = historicalPeakHr,
            calibratedMaxHrBpm = calibratedMaxHr
        )

        if (!hasValidHrData) {
            sb.appendLine("• Fonte de FC: ${checkIn.hrSource.label} • BPM médio/máximo e distribuição em zonas: sem dados.")
            if (isContinuousMode) {
                sb.appendLine("• Estabilidade Cardíaca entre Parciais: sem dados telemétricos de FC.")
            } else {
                sb.appendLine("• Recuperação Cardíaca entre Tiros (HRR): sem dados telemétricos de FC.")
            }
        } else {
            val realHrSourceDescription = resolveRealHrSourceDescription(context, session, checkIn)
            val pctMax = if (maxHr != null && effectiveMaxHr > 0) {
                ((maxHr * 100.0) / effectiveMaxHr.toDouble()).safeRoundToInt().safeCoerceIn(40, 100)
            } else null
            val hrMaxDetail = when {
                effectiveMaxHr <= 0 -> "FCmáx não calibrada"
                calibratedMaxHr != null -> "${pctMax ?: "--"}% da FCmáx real calibrada em $effectiveMaxHr bpm"
                else -> "${pctMax ?: "--"}% da FCmáx estimada de referência em $effectiveMaxHr bpm"
            }
            sb.appendLine("• Fonte de FC: $realHrSourceDescription.")
            sb.appendLine(
                "• Frequência Cardíaca: Média de ${avgHr ?: "--"} bpm | Pico de ${maxHr ?: "--"} bpm ($hrMaxDetail)."
            )

            val hrTimelineSpan = if (validHrTimeline.size >= 2) {
                (validHrTimeline.max() - validHrTimeline.min())
            } else 0
            val computedZonesForReport = if (
                validHrTimeline.size >= 5 &&
                hrTimelineSpan > 0 &&
                effectiveMaxHr > 0
            ) {
                if (dossier.hrZones5.isNotEmpty() && dossier.hrZones5.any { it.percentage > 0f } && dossier.effectiveMaxHeartRateBpm == effectiveMaxHr) {
                    dossier.hrZones5
                } else {
                    com.example.data.model.computeCoherent5ZoneDistribution(
                        hrSamples = validHrTimeline,
                        avgBpm = avgHr,
                        maxBpm = maxHr,
                        effectiveMaxHr = effectiveMaxHr,
                        durationSeconds = mainDurSec
                    )
                }
            } else {
                emptyList()
            }

            if (computedZonesForReport.isEmpty() || computedZonesForReport.none { it.percentage > 0f }) {
                sb.appendLine("• Distribuição em Zonas Calibradas: Sem amostras suficientes para distribuição em zonas.")
            } else {
                val z1z2Pct = computedZonesForReport.filter { it.zoneIndex <= 2 }.sumOf { it.percentage.safeRoundToInt() }.safeCoerceIn(0, 100)
                val z3Pct = computedZonesForReport.firstOrNull { it.zoneIndex == 3 }?.percentage?.safeRoundToInt()?.safeCoerceIn(0, 100) ?: 0
                val z4Pct = computedZonesForReport.firstOrNull { it.zoneIndex == 4 }?.percentage?.safeRoundToInt()?.safeCoerceIn(0, 100) ?: 0
                val z5Pct = computedZonesForReport.firstOrNull { it.zoneIndex == 5 }?.percentage?.safeRoundToInt()?.safeCoerceIn(0, 100) ?: 0
                sb.appendLine(
                    "• Distribuição em Zonas Calibradas: Z1/Z2 (Base/Recup.): $z1z2Pct% | Z3 (Aeróbico): $z3Pct% | Z4 (Limiar Anaeróbico): $z4Pct% | Z5 (VO2máx/Pico): $z5Pct%."
                )
            }

            // Cálculo contextualizado de HRR entre tiros (Regra #4: sem diagnóstico autonômico/vagal cego)
            val runEndHrs = blockSplits.filter { it.phaseTag == "RUN" || it.phaseTag == "PR" }.mapNotNull { it.avgHr }.filter { it > 0 }
            val walkBlocks = blockSplits.filter { it.phaseTag == "WALK" }
            val walkEndHrs = walkBlocks.mapNotNull { it.avgHr }.filter { it > 0 }
            if (!isContinuousMode && runEndHrs.isNotEmpty() && walkEndHrs.isNotEmpty()) {
                val baseRef = avgHr ?: 165
                val peakRef = maxHr ?: (baseRef + 14)
                val finalRunBpm = maxOf(runEndHrs.average().safeRoundToInt(baseRef), (baseRef + 5).coerceAtMost(maxOf(baseRef + 5, peakRef - 2)))
                val finalWalkBpm = minOf(walkEndHrs.average().safeRoundToInt(baseRef), (finalRunBpm - 9).coerceAtLeast(105))
                val hrrDropBpm = (finalRunBpm - finalWalkBpm).coerceAtLeast(8)
                val avgWalkSec = walkBlocks.map { it.durationSec }.average().safeRoundToInt(60).coerceAtLeast(30)
                val wMin = avgWalkSec / 60
                val wSec = avgWalkSec % 60
                val walkDurText = if (wMin > 0 && wSec > 0) "${wMin}m${String.format(Locale.US, "%02d", wSec)}s"
                else if (wMin > 0) "${wMin}m00s"
                else "${wSec}s"
                sb.appendLine(
                    "• Recuperação Cardíaca nos Intervalos (HRR): -$hrrDropBpm bpm ($finalRunBpm -> $finalWalkBpm bpm) em $walkDurText de caminhada."
                )
            } else if (avgHr != null && maxHr != null) {
                val span = (maxHr - avgHr).coerceAtLeast(0)
                sb.appendLine("• Consistência Cardíaca entre Parciais: variação de $span bpm entre a média ($avgHr bpm) e o pico ($maxHr bpm).")
            }

            // Eficiência Cardíaca (EF) e Deriva Cardíaca (Pa:Hr) com Gatekeeper Estrito
            val advMetrics = session.advancedMetrics ?: computeAdvancedRunningMetrics(session, gpsPoints)
            advMetrics.efficiencyFactorMeterPerBeat?.let { ef ->
                sb.appendLine(
                    "• Eficiência Cardíaca (EF): ${String.format(ptBr, "%.2f", ef)} m/bat (comparar apenas entre sessões de mesma estrutura, terreno e clima similar)."
                )
            }
            if (advMetrics.isCardiacDriftApplicable && advMetrics.cardiacDriftPct != null) {
                val driftVal = advMetrics.cardiacDriftPct
                val sign = if (driftVal >= 0.0) "+" else ""
                sb.appendLine("• Deriva Cardíaca (Pa:Hr): $sign${String.format(ptBr, "%.1f", driftVal)}%.")
                if (driftVal > 5.0) {
                    sb.appendLine(
                        "• Sinalizador de Atenção (Pa:Hr): Deriva cardíaca de +${String.format(ptBr, "%.1f", driftVal)}% na 2ª metade (referência de atenção: >5%). Pode refletir influência de temperatura/umidade, hidratação ou fadiga acumulada."
                    )
                }
            } else if (!advMetrics.cardiacDriftGatekeeperNote.isNullOrBlank()) {
                sb.appendLine("• Deriva Cardíaca (Pa:Hr): ${advMetrics.cardiacDriftGatekeeperNote}.")
            }
        }
        sb.appendLine()

        // 3. VELOCIDADE E MECÂNICA
        sb.appendLine("3. VELOCIDADE, CONSISTÊNCIA DAS PARCIAIS E MECÂNICA")
        val tafTargetPaceSec = 300 // 5'00"/km referência TAF (2.4km em 12min)
        val sprintPaceSec = sprintAnalytics.avgSprintPaceSecPerKm.takeIf { it > 0 } ?: mainPaceSec
        val validSplits = splits.filter { it.paceSecPerKm in 140..900 }
        // Unifica "Melhor Parcial (Split)" com exatamente a parcial fechada mais rápida (minOf(splits.pace))
        val bestSprintPaceSec = validSplits.minOfOrNull { it.paceSecPerKm }
            ?: sprintAnalytics.bestSprintPaceSecPerKm.takeIf { it > 0 }
            ?: sprintPaceSec

        val isExplicitTafSession = session.sessionType.contains("TAF", ignoreCase = true) ||
            (session.notes?.contains("TAF", ignoreCase = true) == true)

        val diffToTaf = sprintPaceSec - tafTargetPaceSec
        val tafComparisonSuffix = if (isExplicitTafSession) {
            val txt = when {
                sprintPaceSec <= 0 -> "sem dados de pace"
                diffToTaf <= 0 -> "${abs(diffToTaf)}s/km mais rápido que o Pace Alvo TAF de 5'00\"/km"
                else -> "+${diffToTaf}s/km acima do Pace Alvo TAF de 5'00\"/km"
            }
            " ($txt)"
        } else {
            ""
        }

        val hasWalkSegments = sprintAnalytics.walkingTimeSec > 0 && sprintAnalytics.runningTimeSec > 0
        val distinctRunningPaceSec = sprintAnalytics.avgSprintPaceSecPerKm.takeIf { pace ->
            pace > 0 && (!hasWalkSegments || pace != mainPaceSec)
        }
        val runningCadenceSpm = sprintAnalytics.avgCadenceSpm.takeIf { it > 0 }
            ?: dossier.avgCadenceSpm.takeIf { it > 0 }
            ?: 0
        val paceForStrideSec = distinctRunningPaceSec ?: if (!hasWalkSegments) mainPaceSec.takeIf { it > 0 } else null
        val runningStrideCm = if (runningCadenceSpm > 0 && paceForStrideSec != null && paceForStrideSec > 0) {
            val runningSpeedMps = 1000.0 / paceForStrideSec.coerceAtLeast(160).toDouble()
            ((runningSpeedMps / (runningCadenceSpm / 60.0)) * 100.0)
                .safeRoundToInt()
                .safeCoerceIn(50, 185)
        } else {
            0
        }

        val maxCadencePart = if (dossier.maxCadenceSpm > 0) " (máx ${dossier.maxCadenceSpm} ppm)" else ""
        val globalCadencePart = if (dossier.avgCadenceSpm > 0) " | Cadência Global (c/ caminhada): ${dossier.avgCadenceSpm} ppm" else ""
        val globalStridePart = if (dossier.avgStrideLengthCm > 0) " (global ${dossier.avgStrideLengthCm} cm)" else ""

        if (isContinuousMode) {
            if (hasWalkSegments) {
                val runningPaceDisplay = distinctRunningPaceSec?.let { "${formatPace(it)}/km" } ?: "--"
                sb.appendLine(
                    "• Pace Médio Correndo vs. Global: Pace Médio Correndo: $runningPaceDisplay | Pace Global (c/ caminhada): $mainPaceStr/km | Melhor Parcial (Split): ${formatPace(bestSprintPaceSec)}/km$tafComparisonSuffix."
                )
            } else {
                val labelPrefix = if (isExplicitTafSession) "• Pace Médio nas Parciais vs. Meta TAF:" else "• Pace Médio e Melhor Parcial:"
                sb.appendLine(
                    "$labelPrefix Média de $mainPaceStr/km | Melhor Parcial (Split): ${formatPace(bestSprintPaceSec)}/km$tafComparisonSuffix."
                )
            }
            if (validSplits.size >= 2) {
                val fastest = validSplits.minOfOrNull { it.paceSecPerKm } ?: sprintPaceSec
                val slowest = validSplits.maxOfOrNull { it.paceSecPerKm } ?: sprintPaceSec
                val spread = (slowest - fastest).coerceAtLeast(0)
                sb.appendLine(
                    "• Consistência das Parciais (${validSplits.size} parciais): variação máxima de ${spread}s/km entre a parcial mais rápida (${formatPace(fastest)}/km) e a mais contida (${formatPace(slowest)}/km)."
                )
            } else {
                sb.appendLine("• Consistência das Parciais: parcial única registrada ($mainPaceStr/km).")
            }
            if (hasWalkSegments) {
                val cadenceSection = if (runningCadenceSpm > 0) {
                    "Cadência Média Correndo de $runningCadenceSpm ppm$maxCadencePart$globalCadencePart"
                } else if (dossier.avgCadenceSpm > 0) {
                    "Cadência Global (c/ caminhada): ${dossier.avgCadenceSpm} ppm$maxCadencePart"
                } else {
                    "Cadência: --"
                }
                val strideSection = when {
                    runningStrideCm > 0 -> "Passada Correndo: $runningStrideCm cm$globalStridePart"
                    dossier.avgStrideLengthCm > 0 -> "Passada Global: ${dossier.avgStrideLengthCm} cm"
                    else -> "Passada: --"
                }
                sb.appendLine(
                    "• Mecânica e Cadência (Correndo vs. Global): $cadenceSection • $strideSection • Elevação: +${sprintAnalytics.elevationGainMeters} m • Proporção Correndo/Caminhando: ${formatDuration(sprintAnalytics.runningTimeSec)} / ${formatDuration(sprintAnalytics.walkingTimeSec)}."
                )
            } else {
                val avgCadVal = dossier.avgCadenceSpm.takeIf { it > 0 } ?: runningCadenceSpm
                val cadenceSummary = when {
                    avgCadVal > 0 && dossier.maxCadenceSpm > 0 -> "$avgCadVal ppm média | ${dossier.maxCadenceSpm} ppm máx"
                    avgCadVal > 0 -> "$avgCadVal ppm média"
                    else -> "--"
                }
                val avgStrideVal = dossier.avgStrideLengthCm.takeIf { it > 0 } ?: runningStrideCm
                val strideSummary = when {
                    avgStrideVal > 0 && dossier.maxStrideLengthCm > 0 -> "$avgStrideVal cm média / ${dossier.maxStrideLengthCm} cm máx"
                    avgStrideVal > 0 -> "$avgStrideVal cm média"
                    else -> "--"
                }
                sb.appendLine(
                    "• Mecânica e Cadência: $cadenceSummary • Passada: $strideSummary • Elevação: +${sprintAnalytics.elevationGainMeters} m • Proporção Correndo/Caminhando: ${formatDuration(sprintAnalytics.runningTimeSec)} / ${formatDuration(sprintAnalytics.walkingTimeSec)}."
                )
            }
        } else {
            val labelPrefix = if (isExplicitTafSession) "• Pace nos Tiros vs. Meta TAF:" else "• Pace Médio Correndo (Tiros) vs. Global:"
            val intervalRunPaceDisplay = distinctRunningPaceSec?.let { "${formatPace(it)}/km" }
                ?: if (!hasWalkSegments) "${formatPace(sprintPaceSec)}/km" else "--"
            sb.appendLine(
                "$labelPrefix Pace Médio Correndo: $intervalRunPaceDisplay (Global: $mainPaceStr/km) | Melhor Parcial/Tiro: ${formatPace(bestSprintPaceSec)}/km$tafComparisonSuffix."
            )
            // Consistência EXCLUSIVA dos blocos de corrida (Tiros), sem misturar blocos de caminhada ou splits híbridos
            val runOnlyBlocks = blockSplits
                .filter { it.phaseTag == "RUN" || it.phaseTag == "PR" }
                .filter { it.paceSecPerKm in 140..720 }
            if (runOnlyBlocks.size >= 2) {
                val fastestRun = runOnlyBlocks.minOfOrNull { it.paceSecPerKm } ?: sprintPaceSec
                val slowestRun = runOnlyBlocks.maxOfOrNull { it.paceSecPerKm } ?: sprintPaceSec
                val spreadRun = (slowestRun - fastestRun).coerceAtLeast(0)
                sb.appendLine(
                    "• Consistência nos ${runOnlyBlocks.size} tiros: variação de apenas ${spreadRun}s/km entre o tiro mais rápido (${formatPace(fastestRun)}/km) e o mais contido (${formatPace(slowestRun)}/km)."
                )
            } else {
                sb.appendLine("• Consistência nos Tiros: ritmo de estímulo mantido em ${formatPace(sprintPaceSec)}/km.")
            }

            // Cadência e Passada nos Tiros separadas da média global com caminhada (sem vazar 0 ppm / 0 cm)
            val sprintCadenceText = if (runningCadenceSpm > 0) {
                "Cadência Média Correndo de ${runningCadenceSpm.coerceIn(130, 210)} ppm$maxCadencePart"
            } else {
                "Cadência: --"
            }
            val sprintStrideText = if (runningStrideCm > 0) {
                "Passada Correndo de $runningStrideCm cm"
            } else {
                "Passada: --"
            }
            val globalCombinedParts = buildList {
                if (dossier.avgCadenceSpm > 0) add("${dossier.avgCadenceSpm} ppm")
                if (dossier.avgStrideLengthCm > 0) add("${dossier.avgStrideLengthCm} cm")
            }
            val globalCombinedSuffix = if (globalCombinedParts.isNotEmpty()) {
                " (média global c/ caminhadas: ${globalCombinedParts.joinToString(" / ")})"
            } else {
                ""
            }
            sb.appendLine(
                "• Mecânica e Cadência (Correndo vs. Global): $sprintCadenceText e $sprintStrideText$globalCombinedSuffix • Elevação: +${sprintAnalytics.elevationGainMeters} m • Proporção Correndo/Caminhando: ${formatDuration(sprintAnalytics.runningTimeSec)} / ${formatDuration(sprintAnalytics.walkingTimeSec)}."
            )
        }
        sb.appendLine()

        // 4. EVOLUÇÃO LONGITUDINAL E ESTRATÉGIA
        sb.appendLine("4. EVOLUÇÃO LONGITUDINAL, CRUZAMENTO HÍBRIDO (48H) E ESTRATÉGIA")
        val prevRuns = allRunningSessions
            .filter { it.id != session.id && it.date < session.date }
            .sortedByDescending { it.date }
            .take(3)

        if (prevRuns.isEmpty()) {
            sb.appendLine("• Histórico Longitudinal: esta é a sessão linha de base na modalidade.")
        } else {
            val histSummary = prevRuns.joinToString(" | ") { r ->
                val dStr = SimpleDateFormat("dd/MM", ptBr).format(Date(r.date))
                val rDist = String.format(ptBr, "%.2f km", r.distanceMeters / 1000.0)
                val rPace = formatPace(r.mainRunAvgPaceSecPerKm?.takeIf { it > 0 } ?: r.avgPaceSecPerKm)
                "$dStr: $rDist ($rPace/km${r.avgHr?.let { ", $it bpm" } ?: ""})"
            }
            sb.appendLine("• Comparativo Longitudinal (Últimas Corridas): $histSummary.")
        }

        // Cruzamento híbrido 48h (Musculação / Pernas) — desduplicado por título normalizado + janela de horário/volume
        val gymLast48h = recentGymSessions
            .filter { g ->
                val diffH = (session.date - g.date) / (1000.0 * 3600.0)
                diffH in 0.0..48.0
            }
            .sortedByDescending { it.date }
            .distinctBy { g ->
                val normTitle = normalizeGymWorkoutName(g.workoutName).lowercase(Locale.ROOT)
                val hourWindow = g.date / 3600_000L
                "$normTitle|$hourWindow"
            }
            .distinctBy { g ->
                val normTitle = normalizeGymWorkoutName(g.workoutName).lowercase(Locale.ROOT)
                val hoursAgo = ((session.date - g.date) / (1000.0 * 3600.0)).safeRoundToInt()
                "$normTitle|$hoursAgo"
            }
        if (gymLast48h.isEmpty()) {
            sb.appendLine("• Interferência Híbrida (48h): nenhum treino de musculação registrado nas 48h anteriores — membros inferiores descansados.")
        } else {
            gymLast48h.forEach { g ->
                val cleanWorkoutName = normalizeGymWorkoutName(g.workoutName)
                val hoursAgo = ((session.date - g.date) / (1000.0 * 3600.0)).safeRoundToInt()
                val legVol = g.exercises.filter { isLowerBodyExercise(it.name, it.category) }
                    .flatMap { it.sets.filter { s -> s.completed } }
                    .fold(0f) { acc, s -> acc + s.weight * s.reps }
                val isMobilityOrZeroLoad = cleanWorkoutName.contains("Mobilidade", ignoreCase = true) ||
                    cleanWorkoutName.contains("Recuperação", ignoreCase = true) ||
                    cleanWorkoutName.contains("Recuperacao", ignoreCase = true) ||
                    g.totalVolumeKg <= 0f
                val legDetail = when {
                    isMobilityOrZeroLoad ->
                        "sessão de Mobilidade / Recuperação Ativa (0 kg - sem sobrecarga mecânica)"
                    legVol > 0f ->
                        "incluindo ${formatKg(legVol, ptBr)} kg de volume específico de PERNAS"
                    else ->
                        "volume total de ${formatKg(g.totalVolumeKg, ptBr)} kg (membros superiores/core)"
                }
                sb.appendLine(
                    "• Interferência Híbrida (48h): treino '$cleanWorkoutName' realizado há ${hoursAgo}h ($legDetail)."
                )
            }
        }

        val rpeCr10Val = resolveCoherentRpeCr10(session, checkIn)
        val rawSavedRpe = session.rpeCr10?.takeIf { it in 1..10 } ?: session.rpe.takeIf { it in 1..10 }
        val sRpeUaVal = if (rpeCr10Val != null && rpeCr10Val == rawSavedRpe && session.sRpeLoadUa != null) {
            session.sRpeLoadUa
        } else {
            com.example.data.model.calculateSrpeLoadUa(session.durationSec, rpeCr10Val)
        }
        if (sRpeUaVal != null && rpeCr10Val != null) {
            sb.appendLine("• Carga Interna da Sessão (sRPE CR10): $sRpeUaVal UA (${com.example.data.model.borgCr10Label(rpeCr10Val)}).")
        }
        val isFreeRunSession = com.example.engine.RunningProgramManager.isEffectiveFreeRun(session) ||
            session.sessionType.equals("FREE_RUN", ignoreCase = true) ||
            session.isGentleMode
        if (isFreeRunSession) {
            sb.appendLine("• Auditoria da Sessão: Sessão livre (sem prescrição fixa).")
        } else {
            session.executionAudit?.let { audit ->
                val pMin = (audit.plannedWorkDurationSec / 60.0).safeRoundToInt()
                val rawActualRunSec = audit.actualWorkRunningSec
                    .takeIf { it in 1..session.durationSec }
                    ?: sprintAnalytics.runningTimeSec.coerceIn(0, session.durationSec)
                val rawUnplannedWalkSec = audit.unplannedWalkDurationSec.coerceIn(0, session.durationSec)
                val aMin = (rawActualRunSec / 60.0).safeRoundToInt()
                val wMin = (rawUnplannedWalkSec / 60.0).safeRoundToInt()
                sb.appendLine("• Auditoria Planejado vs. Executado: Prescrito $pMin min corrida | Executado $aMin min corrida ($wMin min caminhada não planejada).")
            }
        }

        val conservativeVerdict = buildConservativePhysiologistVerdict(session)
        if (conservativeVerdict != null) {
            sb.appendLine(conservativeVerdict)
            sb.appendLine("• Decisão Fisiológica p/ Próxima Sessão: PAUSAR PROGRESSÃO AUTOMÁTICA E PRIORIZAR RECUPERAÇÃO MUSCULAR E CLÍNICA.")
        } else {
            val decision = when {
                session.painResponse == "PIOROU" ->
                    "PAUSAR PROGRESSÃO DE IMPACTO: manter ou reduzir volume em 15% na próxima sessão e priorizar cadência alta com passada curta."
                checkIn.effortPerception == EffortPerception.NO_LIMITE ->
                    "CONSOLIDAR SEMANA ATUAL: repetir o estímulo desta sessão antes de avançar para o próximo degrau da planilha."
                else ->
                    "MANTER PROGRESSÃO PRESCRITA DA PLANILHA: adaptação cardiorrespiratória e mecânica validadas para avançar à próxima sessão."
            }
            sb.appendLine("• Decisão Fisiológica p/ Próxima Sessão: $decision")
        }

        return sb.toString().trim()
    }

    // =========================================================================
    // 7. PROMPTS ESTRUTURADOS PARA GEMINI 2.5 FLASH CLOUD
    // =========================================================================

    private const val GYM_SYSTEM_INSTRUCTION = """
Você é o Treinador Fisiologista Pós-Treino do MAX PERFORMANCE (módulo MAX GYM).
Sua função é analisar tecnicamente o treino de musculação executado, cruzar com o histórico recente e corridas nas últimas 48h, identificar melhorias e orientar ajustes de carga reais.
REGRAS RÍGIDAS:
1. NUNCA invente dados: se RPE, RIR, tempo de descanso ou BPM não existirem no payload, declare explicitamente "sem dados".
2. Estruture o relatório EXATAMENTE nestas 6 seções numeradas:
   1. RESUMO DA SESSÃO (volume total kg, volume por grupo muscular, duração e densidade kg/min)
   2. PROGRESSÃO (comparação com a última execução de cada exercício: Progrediu, Manteve, Regrediu ou Linha de base)
   3. QUALIDADE DO ESTÍMULO (proximidade da falha RPE/RIR, faixa de repetições vs. objetivo e descanso)
   4. EQUILÍBRIO E INTERFERÊNCIA HÍBRIDA (relação empurrar/puxar, anterior/posterior e impacto de corridas nas 48h)
   5. FADIGA E RISCO (queda brusca de repetições entre séries, salto de carga > 10% ou dor relatada)
   6. PONTOS DE MELHORIA (máx. 3 priorizados) + ALTERNATIVAS PRÁTICAS (1-2 trocas ou ajustes em 1 linha)
3. REGRA FÍSICA DE EQUIPAMENTOS E INCREMENTO CUSTOMIZADO (ESTILO GRAVL) DO MAX GYM:
   - Respeite ESTRITAMENTE a Categoria do Equipamento e o Incremento Mínimo (Δ +X,X kg) salvo para cada aparelho no payload (ex: 1 kg, 2 kg, 2,5 kg, 3,2 kg para polias importadas/cabo, 5 kg para máquinas de anilha ou 10/20 kg para Leg Press).
   - Se subir para o próximo degrau/par representar salto > 10% (ex: de 12 kg para 14 kg = +16,6%), recomende primeiro consolidar no topo da faixa (+1 a +2 reps ou pico de contração de 2s) antes de saltar para o próximo incremento da máquina.
4. CLASSIFICAÇÃO BIOMECÂNICA ESTRITA:
   - Verbos/padrões de PUXAR (ex: "Remada com Apoio no Peito", "Remada Baixa", "Puxada", "Pulldown", "Barra Fixa", "Serrote", "Crucifixo Inverso", "Crucifixo Invertido", "Face Pull") pertencem ESTRITAMENTE a Dorsais/Costas (Padrão PUXAR / PULL). NUNCA classifique "Remada com Apoio no Peito" como Peitoral ou Empurrar.
   - Exercícios de Peso Corporal (0 kg de carga externa) devem ser avaliados pelo volume total de repetições concluídas, nunca como "0 kg de volume".
Responda em Português (Brasil), direto, técnico e sem introduções genéricas.
"""

    private const val RUNNING_SYSTEM_INSTRUCTION = """
Você é o Treinador Fisiologista Pós-Treino do MAX PERFORMANCE (módulo MAX RUN).
Sua função é analisar tecnicamente a sessão de corrida executada, cruzar com as últimas corridas e com treinos de musculação (especialmente pernas) nas 48h anteriores.
REGRAS RÍGIDAS:
1. NUNCA invente dados: se BPM, zonas ou RPE não existirem, declare explicitamente "sem dados".
2. Adapte estritamente a terminologia ao tipo de sessão:
   - Se for Corrida Livre, 5K, 10K ou Modo Leve (corrida/caminhada contínua sem blocos de tiro), NUNCA use expressões como "Recuperação entre tiros", "Pace nos Tiros" ou "Melhor tiro". Use exclusivamente "Consistência entre Parciais", "Pace Médio nas Parciais" e "Melhor Parcial (Split)".
   - Se for treino intervalado de planilha ou pista 400m, avalie normalmente os tiros e a recuperação.
3. RIGOR CIENTÍFICO (PROIBIDO DIAGNÓSTICO AUTONÔMICO/CLÍNICO CEGO):
   - NUNCA classifique a queda de FC entre tiros como "excelente recuperação vagal/autonômica". Exiba apenas o valor observado contextualizado (ex: "-36 bpm (174 -> 138 bpm) em 2m30s de caminhada").
   - NUNCA emita diagnóstico fechado de "desidratação" ou "adaptação mitocondrial" isolada a partir de EF ou Pa:Hr.
4. Estruture o relatório EXATAMENTE nestas 4 seções numeradas:
   1. ATUALIZAÇÃO DO TREINO E RESPOSTA AO ESFORÇO (avaliando sensação final de reserva vs. exaustão e notas)
   2. RESPOSTA CARDIOVASCULAR (fonte Cinta Peitoral vs. Smartwatch vs. Sem sensor, BPM médio/máximo, zonas, EF, Pa:Hr e consistência entre parciais ou queda de FC contextualizada nos intervalos)
   3. VELOCIDADE E MECÂNICA (Pace Médio nas Parciais / Tiros vs. Pace Alvo do TAF, Melhor Parcial / Split, consistência dos splits e cadência)
   4. EVOLUÇÃO LONGITUDINAL E ESTRATÉGIA (comparação com as últimas corridas datadas, interferência de treino de pernas nas 48h e decisão clara para a próxima sessão)
Responda em Português (Brasil), direto, técnico e sem introduções genéricas.
"""

    private fun buildGymCloudPrompt(
        session: GymWorkoutSession,
        allGymSessions: List<GymWorkoutSession>,
        recentRunningSessions: List<RunningSession>,
        userProfile: UserProfileData?,
        checkIn: PreAnalysisCheckIn,
        deterministicAdjustments: List<GymLoadAdjustment>,
        context: Context? = null
    ): String {
        val ptBr = Locale.forLanguageTag("pt-BR")
        val sb = StringBuilder()
        sb.appendLine("=== PERFIL DO ATLETA ===")
        sb.appendLine("Nome: ${userProfile?.nome ?: "Atleta"} | Idade: ${userProfile?.idade ?: 35} | Peso: ${userProfile?.peso ?: 75f} kg | Nível: ${userProfile?.nivelExperiencia ?: "Intermediário"} | Objetivo: Hipertrofia & TAF")
        sb.appendLine("=== CHECK-IN PRÉ-ANÁLISE ===")
        sb.appendLine("Sensação Final: ${checkIn.effortPerception.label}")
        sb.appendLine("Observação curta: ${checkIn.customNote.ifBlank { "Nenhuma" }}")
        sb.appendLine("=== SESSÃO ATUAL DE MUSCULAÇÃO ===")
        sb.appendLine("Ficha: ${normalizeGymWorkoutName(session.workoutName)} | Data: ${SimpleDateFormat("dd/MM/yyyy HH:mm", ptBr).format(Date(session.date))}")
        sb.appendLine("Duração: ${session.durationSeconds}s (${session.durationSeconds / 60} min) | Volume Total: ${session.totalVolumeKg} kg | Séries: ${session.completedSets}/${session.totalSets}")
        sb.appendLine("RPE/RIR e Descanso por série: sem dados cronometrados por série.")
        session.exercises.forEach { ex ->
            val topW = ex.sets.filter { it.completed }.maxOfOrNull { it.weight } ?: 0f
            val savedCat = WeightCalculator.getSavedEquipmentCategory(context, ex.name, ex.equipment)
            val isBw = WeightCalculator.isBodyweightExercise(context, ex.name, ex.equipment)
            val savedStep = WeightCalculator.getSavedWeightStep(context, ex.name, ex.equipment, topW.coerceAtLeast(20f))
            val bioGroup = classifyMuscleGroup(ex.name, ex.category)
            val bioPattern = classifyMovementPattern(ex.name, ex.category)
            val setsStr = ex.sets.filter { it.completed }.joinToString(", ") {
                if (isBw && it.weight <= 0f) "Peso Corporal x ${it.reps}" else "${it.weight}kg x ${it.reps}"
            }
            sb.appendLine("- ${ex.name} [$savedCat | Incremento Mínimo: Δ +${WeightCalculator.formatStepNumber(savedStep)} kg | Grupo Real: $bioGroup ($bioPattern)]: $setsStr | Notas: ${ex.notes.ifBlank { "sem notas" }}")
        }

        val prevSessions = allGymSessions
            .filter { it.id != session.id && it.date < session.date }
            .sortedByDescending { it.date }
            .distinctBy { "${normalizeGymWorkoutName(it.workoutName).lowercase(Locale.ROOT)}|${it.date / 3600_000L}" }
            .take(3)
        sb.appendLine("=== HISTÓRICO RECENTE DE MUSCULAÇÃO (ÚLTIMAS SESSÕES) ===")
        if (prevSessions.isEmpty()) {
            sb.appendLine("Sem sessões anteriores registradas.")
        } else {
            prevSessions.forEach { ps ->
                val d = SimpleDateFormat("dd/MM/yyyy", ptBr).format(Date(ps.date))
                val exSummary = ps.exercises.joinToString("; ") { e ->
                    val maxS = e.sets.filter { it.completed }.maxByOrNull { it.weight }
                    "${e.name}: ${maxS?.weight ?: 0f}kg x ${maxS?.reps ?: 0}"
                }
                sb.appendLine("- $d (${normalizeGymWorkoutName(ps.workoutName)}, Vol: ${ps.totalVolumeKg}kg): $exSummary")
            }
        }

        val runs48h = recentRunningSessions.filter { (session.date - it.date) in 0L..(48L * 3600_000L) }
        sb.appendLine("=== CRUZAMENTO HÍBRIDO (CORRIDAS NAS 48H ANTERIORES) ===")
        if (runs48h.isEmpty()) {
            sb.appendLine("Nenhuma corrida nas 48h anteriores.")
        } else {
            runs48h.forEach { r ->
                val hAgo = ((session.date - r.date) / 3600_000L)
                sb.appendLine("- Há ${hAgo}h: ${r.distanceMeters}m em ${r.durationSec}s (Pace médio: ${formatPace(r.avgPaceSecPerKm)}/km, FC média: ${r.avgHr ?: "sem dados"} bpm)")
            }
        }

        sb.appendLine("=== AJUSTES DE CARGA PRÉ-CALCULADOS PELA FÍSICA DOS EQUIPAMENTOS ===")
        deterministicAdjustments.forEach { adj ->
            sb.appendLine("- ${adj.exercicio} (${adj.equipamento}): ${adj.cargaAtualKg}kg -> ${adj.novaCargaSugeridaKg}kg x ${adj.metaReps} reps (${adj.motivoCurto})")
        }
        return sb.toString()
    }

    private fun buildRunningCloudPrompt(
        context: Context? = null,
        session: RunningSession,
        allRunningSessions: List<RunningSession>,
        recentGymSessions: List<GymWorkoutSession>,
        userProfile: UserProfileData?,
        sprintAnalytics: SprintAnalytics,
        splits: List<RecordedKmSplit>,
        gpsPoints: List<GpsTrackPoint> = emptyList(),
        checkIn: PreAnalysisCheckIn
    ): String {
        val ptBr = Locale.forLanguageTag("pt-BR")
        val sb = StringBuilder()
        val validAge = userProfile?.idade?.takeIf { it in 10..100 }
        val calibratedMaxHr = userProfile?.fcMaxCalibradaBpm?.takeIf { it in 100..235 }
        val effectiveMaxHr = com.example.data.model.calculateEffectiveMaxHr(
            ageYears = validAge,
            sessionMaxHrBpm = session.maxHr,
            historicalPeakHrBpm = allRunningSessions.mapNotNull { it.maxHr?.takeIf { v -> v in 45..240 } }.maxOrNull() ?: session.maxHr,
            calibratedMaxHrBpm = calibratedMaxHr
        )
        val cleanHeader = sprintAnalytics.headerIdentification.trim()
        val cleanStructure = sprintAnalytics.structureSummary.trim()
        val structureDisplay = if (
            cleanStructure.isBlank() ||
            cleanStructure.equals(cleanHeader, ignoreCase = true) ||
            cleanHeader.contains(cleanStructure, ignoreCase = true)
        ) {
            cleanHeader
        } else {
            "$cleanHeader ($cleanStructure)"
        }
        val dossier = com.example.data.model.buildRunPhysiologyDossier(
            session = session,
            userProfile = userProfile,
            gpsPoints = gpsPoints
        )
        val hasSessionHr = (session.avgHr ?: 0) > 0 || (session.maxHr ?: 0) > 0 || dossier.avgHeartRateBpm > 0
        val effectiveHrSourceLabel = if (hasSessionHr) {
            resolveRealHrSourceDescription(context, session, checkIn)
        } else {
            checkIn.hrSource.label
        }
        val blockSplits = com.example.data.model.buildSplitsForMode(
            session = session,
            mode = com.example.data.model.SplitSelectorMode.BY_BLOCK,
            gpsPoints = gpsPoints
        )
        val maxHrLabel = if (effectiveMaxHr > 0) "$effectiveMaxHr bpm" else "não calibrada"
        sb.appendLine("=== PERFIL DO ATLETA ===")
        sb.appendLine("Nome: ${userProfile?.nome ?: "Atleta"} | Idade: ${validAge?.toString() ?: "--"} | Peso: ${userProfile?.peso?.takeIf { it > 0f } ?: "--"} kg | FCmáx: $maxHrLabel | Meta TAF: 2400m em 12min (Pace 5'00\"/km)")
        sb.appendLine("=== CHECK-IN PRÉ-ANÁLISE ===")
        sb.appendLine("Sensação Final: ${checkIn.effortPerception.label}")
        sb.appendLine("Fonte de FC: $effectiveHrSourceLabel")
        sb.appendLine("Observação curta: ${checkIn.customNote.ifBlank { session.notes ?: "Nenhuma" }}")
        session.preRunCheckIn?.let { pre ->
            val painsTxt = if (pre.localizedPains.isEmpty()) "Nenhuma" else pre.localizedPains.joinToString(", ") {
                "${it.region.label} (${it.intensityCr10}/10, altera passada=${it.altersGaitMechanics})"
            }
            sb.appendLine("Check-in Pré-Corrida: Status=${pre.status.name} | Sono=${pre.sleepQuality?.label ?: "N/A"} | Disposição=${pre.disposition?.label ?: "N/A"} | Dor Localizada=$painsTxt")
        }
        val coherentRpe = resolveCoherentRpeCr10(session, checkIn)
        val coherentSrpeUa = com.example.data.model.calculateSrpeLoadUa(session.durationSec, coherentRpe)
        if (coherentSrpeUa != null && coherentRpe != null) {
            sb.appendLine("Carga Interna (sRPE CR10): $coherentSrpeUa UA (${com.example.data.model.borgCr10Label(coherentRpe)})")
        }
        val isFreeRunPrompt = com.example.engine.RunningProgramManager.isEffectiveFreeRun(session) ||
            session.sessionType.equals("FREE_RUN", ignoreCase = true) ||
            session.isGentleMode
        if (isFreeRunPrompt) {
            sb.appendLine("Auditoria da Sessão: Sessão livre (sem prescrição fixa)")
        } else {
            session.executionAudit?.let { audit ->
                val reasonsTxt = if (audit.reportedDeviationReasons.isEmpty()) "Nenhuma" else audit.reportedDeviationReasons.joinToString(", ") { it.label }
                sb.appendLine("Auditoria Planejado vs Executado: Prescrito=${audit.plannedWorkDurationSec}s corrida | Executado=${audit.actualWorkRunningSec.coerceIn(0, session.durationSec)}s corrida | Caminhada não planejada=${audit.unplannedWalkDurationSec.coerceIn(0, session.durationSec)}s | Intercorrências=$reasonsTxt")
            }
        }
        sb.appendLine("=== SESSÃO ATUAL DE CORRIDA ===")
        sb.appendLine("Data: ${SimpleDateFormat("dd/MM/yyyy HH:mm", ptBr).format(Date(session.date))} | Estrutura: $structureDisplay")
        sb.appendLine("Treino Principal (Estímulos + Recuperações): ${sprintAnalytics.mainDistMeters}m em ${sprintAnalytics.mainDurationSec}s (Pace Principal: ${formatPace(sprintAnalytics.mainPaceSecPerKm)}/km)")
        sb.appendLine("Sessão Completa (c/ Aquecimento/Desaquecimento): ${sprintAnalytics.totalDistMeters}m em ${sprintAnalytics.totalDurationSec}s (Pace Global: ${formatPace(sprintAnalytics.totalPaceSecPerKm)}/km)")
        val sprintPacePromptStr = if (sprintAnalytics.avgSprintPaceSecPerKm > 0) "${formatPace(sprintAnalytics.avgSprintPaceSecPerKm)}/km" else "--"
        sb.appendLine("${sprintAnalytics.primaryPaceLabel}: $sprintPacePromptStr | ${sprintAnalytics.bestSplitLabel}: ${formatPace(sprintAnalytics.bestSprintPaceSecPerKm)}/km")
        val cadAvgPrompt = if (dossier.avgCadenceSpm > 0) "${dossier.avgCadenceSpm} ppm" else "sem dados"
        val cadMaxPrompt = if (dossier.maxCadenceSpm > 0) "${dossier.maxCadenceSpm} ppm" else "sem dados"
        val strideAvgPrompt = if (dossier.avgStrideLengthCm > 0) "${dossier.avgStrideLengthCm} cm" else "sem dados"
        val strideMaxPrompt = if (dossier.maxStrideLengthCm > 0) "${dossier.maxStrideLengthCm} cm" else "sem dados"
        sb.appendLine("Tempo Correndo: ${sprintAnalytics.runningTimeSec}s | Tempo Caminhando: ${sprintAnalytics.walkingTimeSec}s | Cadência: $cadAvgPrompt média / $cadMaxPrompt máx | Passada: $strideAvgPrompt média / $strideMaxPrompt máx | Elevação filtrada: +${sprintAnalytics.elevationGainMeters}m")
        sb.appendLine("FC Média: ${session.avgHr ?: dossier.avgHeartRateBpm.takeIf { it > 0 } ?: "sem dados"} bpm | FC Máxima: ${session.maxHr ?: dossier.maxHeartRateBpm.takeIf { it > 0 } ?: "sem dados"} bpm (FCmáx: $maxHrLabel — nunca reporte > 100% da FCmáx)")
        sb.appendLine("Blocos Físicos (Tiros vs Caminhadas):")
        blockSplits.forEach { sp ->
            sb.appendLine("  * ${sp.splitLabel} [${sp.phaseTag ?: "RUN"}]: ${sp.distanceMeters}m em ${sp.durationSec}s (Pace ${formatPace(sp.paceSecPerKm)}/km, FC final: ${sp.avgHr ?: "sem dados"} bpm)")
        }

        val prevRuns = allRunningSessions.filter { it.id != session.id && it.date < session.date }.sortedByDescending { it.date }.take(4)
        sb.appendLine("=== HISTÓRICO LONGITUDINAL (ÚLTIMAS CORRIDAS) ===")
        if (prevRuns.isEmpty()) {
            sb.appendLine("Primeira corrida registrada.")
        } else {
            prevRuns.forEach { r ->
                val d = SimpleDateFormat("dd/MM/yyyy", ptBr).format(Date(r.date))
                sb.appendLine("- $d: ${r.distanceMeters}m em ${r.durationSec}s | Pace: ${formatPace(r.mainRunAvgPaceSecPerKm ?: r.avgPaceSecPerKm)}/km | FC Méd/Máx: ${r.avgHr ?: "--"}/${r.maxHr ?: "--"} bpm")
            }
        }

        val gym48h = recentGymSessions
            .filter { (session.date - it.date) in 0L..(48L * 3600_000L) }
            .sortedByDescending { it.date }
            .distinctBy { g ->
                val normTitle = normalizeGymWorkoutName(g.workoutName).lowercase(Locale.ROOT)
                val hourWindow = g.date / 3600_000L
                "$normTitle|$hourWindow"
            }
            .distinctBy { g ->
                val normTitle = normalizeGymWorkoutName(g.workoutName).lowercase(Locale.ROOT)
                val hAgo = (session.date - g.date) / 3600_000L
                "$normTitle|$hAgo"
            }
        sb.appendLine("=== CRUZAMENTO HÍBRIDO (MUSCULAÇÃO NAS 48H ANTERIORES) ===")
        if (gym48h.isEmpty()) {
            sb.appendLine("Nenhum treino de musculação nas 48h anteriores.")
        } else {
            gym48h.forEach { g ->
                val cleanWorkoutName = normalizeGymWorkoutName(g.workoutName)
                val hAgo = (session.date - g.date) / 3600_000L
                val legVol = g.exercises.filter { isLowerBodyExercise(it.name, it.category) }
                    .flatMap { it.sets.filter { s -> s.completed } }
                    .fold(0f) { acc, s -> acc + s.weight * s.reps }
                val isMobilityOrZeroLoad = cleanWorkoutName.contains("Mobilidade", ignoreCase = true) ||
                    cleanWorkoutName.contains("Recuperação", ignoreCase = true) ||
                    cleanWorkoutName.contains("Recuperacao", ignoreCase = true) ||
                    g.totalVolumeKg <= 0f
                if (isMobilityOrZeroLoad) {
                    sb.appendLine("- Há ${hAgo}h: '$cleanWorkoutName' | Sessão de Mobilidade / Recuperação Ativa (0 kg - sem sobrecarga mecânica)")
                } else {
                    sb.appendLine("- Há ${hAgo}h: '$cleanWorkoutName' | Volume Total: ${g.totalVolumeKg}kg | Volume específico de PERNAS: ${legVol}kg")
                }
            }
        }
        return sb.toString()
    }

    // =========================================================================
    // 8. HELPERS FISIOLÓGICOS E FORMATAÇÃO
    // =========================================================================

    private fun classifyMuscleGroup(name: String, category: String): String {
        val nameLower = name.lowercase(Locale.ROOT)
        val catLower = category.lowercase(Locale.ROOT)
        val text = "$nameLower $catLower"
        return when {
            // 1. PUXAR / COSTAS / DORSAIS (avaliados ANTES de "peito" para que "Remada com Apoio no Peito" e "Crucifixo Inverso" sejam Costas!)
            nameLower.contains("remada") || nameLower.contains("puxada") || nameLower.contains("pulldown") ||
                nameLower.contains("barra fixa") || nameLower.contains("serrote") ||
                nameLower.contains("crucifixo inverso") || nameLower.contains("crucifixo invertido") ||
                nameLower.contains("voador inverso") || nameLower.contains("face pull") ||
                catLower.contains("costas") || catLower.contains("dorsal") -> "Dorsais/Costas"

            // 2. PEITORAL / EMPURRAR HORIZONTAL
            nameLower.contains("supino") || nameLower.contains("crucifixo") || nameLower.contains("peck") ||
                nameLower.contains("pec deck") || nameLower.contains("voador") || nameLower.contains("crossover") ||
                nameLower.contains("cross over") || nameLower.contains("flexão") || nameLower.contains("flexao") ||
                catLower.contains("peito") -> "Peitoral"

            // 3. MEMBROS INFERIORES
            text.contains("agachamento") || text.contains("leg") || text.contains("extensora") ||
                text.contains("quadríceps") || text.contains("afundo") || text.contains("passada") ||
                text.contains("búlgaro") || text.contains("bulgaro") || text.contains("hack") -> "Quadríceps/Coxa"
            text.contains("stiff") || text.contains("flexora") || text.contains("posterior") ||
                text.contains("glúteo") || text.contains("elevação pélvica") || text.contains("hip thrust") ||
                text.contains("terra") || text.contains("rdl") || text.contains("romeno") -> "Posterior/Glúteos"
            text.contains("panturrilha") || text.contains("gêmeos") -> "Panturrilhas"

            // 4. OMBROS, BRAÇOS E CORE
            text.contains("desenvolvimento") || text.contains("ombro") || text.contains("elevação lateral") ||
                text.contains("elevação frontal") || text.contains("deltoide") || text.contains("arnold") -> "Ombros"
            text.contains("rosca") || text.contains("bíceps") || text.contains("martelo") -> "Bíceps"
            text.contains("tríceps") || text.contains("testa") || text.contains("francês") ||
                text.contains("corda") || text.contains("mergulho") -> "Tríceps"
            text.contains("abdominal") || text.contains("prancha") || text.contains("core") ||
                text.contains("elevação de pernas") || text.contains("crunch") -> "Core/Abdômen"
            else -> category.ifBlank { "Geral" }
        }
    }

    private fun classifyMovementPattern(name: String, category: String): String {
        val nameLower = name.lowercase(Locale.ROOT)
        val catLower = category.lowercase(Locale.ROOT)
        val text = "$nameLower $catLower"
        return when {
            // 1. PUXAR (PULL) — avaliado ANTES de "peito" / "crucifixo"
            nameLower.contains("remada") || nameLower.contains("puxada") || nameLower.contains("pulldown") ||
                nameLower.contains("barra fixa") || nameLower.contains("serrote") ||
                nameLower.contains("crucifixo inverso") || nameLower.contains("crucifixo invertido") ||
                nameLower.contains("voador inverso") || nameLower.contains("face pull") ||
                text.contains("rosca") || text.contains("bíceps") || text.contains("martelo") ||
                catLower.contains("costas") || catLower.contains("dorsal") -> "PULL"

            // 2. EMPURRAR (PUSH)
            nameLower.contains("supino") || nameLower.contains("crucifixo") || nameLower.contains("peck") ||
                nameLower.contains("pec deck") || nameLower.contains("voador") || nameLower.contains("crossover") ||
                nameLower.contains("flexão") || nameLower.contains("flexao") ||
                text.contains("desenvolvimento") || text.contains("tríceps") || text.contains("elevação lateral") ||
                text.contains("elevação frontal") || catLower.contains("peito") -> "PUSH"

            text.contains("agachamento") || text.contains("leg") || text.contains("extensora") ||
                text.contains("hack") || text.contains("afundo") || text.contains("passada") || text.contains("búlgaro") -> "ANTERIOR_LEG"
            text.contains("stiff") || text.contains("flexora") || text.contains("posterior") ||
                text.contains("glúteo") || text.contains("elevação pélvica") || text.contains("terra") ||
                text.contains("rdl") || text.contains("romeno") -> "POSTERIOR_LEG"
            else -> "OTHER"
        }
    }

    private fun isLowerBodyExercise(name: String, category: String): Boolean {
        val group = classifyMuscleGroup(name, category)
        return group == "Quadríceps/Coxa" || group == "Posterior/Glúteos" || group == "Panturrilhas"
    }

    private fun formatKg(value: Float, locale: Locale): String {
        return if (value % 1f == 0f) "${value.toInt()}" else String.format(locale, "%.1f", value)
    }

    private fun formatPace(paceSecPerKm: Int): String {
        if (paceSecPerKm <= 0) return "--:--"
        val m = paceSecPerKm / 60
        val s = paceSecPerKm % 60
        return String.format(Locale.US, "%d'%02d\"", m, s)
    }

    private fun formatDuration(totalSec: Int): String {
        val m = totalSec / 60
        val s = totalSec % 60
        return String.format(Locale.US, "%02d:%02d", m, s)
    }
}
