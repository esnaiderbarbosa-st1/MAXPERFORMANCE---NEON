package com.example.domain.analysis

import android.content.Context
import com.example.data.model.BioimpedanceRecord
import com.example.data.model.GymWorkoutSession
import com.example.data.model.RunningSession
import com.example.data.model.calculateSprintAnalytics
import com.example.data.repository.GymHistoryRepository
import com.example.domain.recomp.BodyRecompEngine
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Janelas de tempo selecionáveis para a Análise Longitudinal de Período (Macro-Análise Híbrida IA).
 */
enum class MacroPeriodWindow(
    val days: Int?,
    val pillLabel: String,
    val fullTitle: String,
    val subtitle: String
) {
    DAYS_7(
        days = 7,
        pillLabel = "7 DIAS",
        fullTitle = "7 Dias • Bateria Semanal",
        subtitle = "Microciclo Semanal Integrado"
    ),
    DAYS_15(
        days = 15,
        pillLabel = "15 DIAS",
        fullTitle = "15 Dias • Bloco Quinzenal",
        subtitle = "Mesociclo Quinzenal de Adaptação"
    ),
    DAYS_30(
        days = 30,
        pillLabel = "30 DIAS",
        fullTitle = "30 Dias • Fechamento Mensal",
        subtitle = "Fechamento Mensal de Performance"
    ),
    ALL_CYCLE(
        days = null,
        pillLabel = "CICLO TAF",
        fullTitle = "Ciclo Completo • Rumo ao TAF",
        subtitle = "Histórico Longitudinal Completo"
    )
}

/**
 * Status semântico da Eficiência Aeróbica (Pilar 2).
 */
enum class AerobicEfficiencyStatus(
    val badgeLabel: String,
    val colorHex: Long
) {
    GAIN("GANHO DE EFICIÊNCIA AERÓBICA", 0xFF00E676),
    STABLE("ESTABILIDADE CARDIOVASCULAR", 0xFF00E5FF),
    DRIFT_WARNING("SINAL DE DESGASTE / DERIVA CARDÍACA", 0xFFFF5252)
}

/**
 * Diretriz Fisiológica para a Próxima Semana (no Relatório Executivo).
 */
enum class NextWeekDirective(
    val label: String,
    val recommendation: String,
    val colorHex: Long
) {
    INCREASE_SPRINT_STIMULUS(
        label = "AUMENTAR ESTÍMULO DE TIROS",
        recommendation = "Eficiência cardiovascular em alta e carga de pernas equilibrada. Autorizado avançar +5% a +8% no volume de tiros ou reduzir 3s/km na meta de Sprint Pace na próxima bateria.",
        colorHex = 0xFF00E676
    ),
    MAINTAIN_PROGRESSION(
        label = "MANTER PROGRESSÃO",
        recommendation = "Adaptação fisiológica consistente entre corrida, musculação e recomposição. Mantenha a progressão prevista na planilha sem pular blocos de caminhada regenerativa.",
        colorHex = 0xFF00E5FF
    ),
    METABOLIC_DELOAD(
        label = "DELOAD / RECUPERAÇÃO METABÓLICA",
        recommendation = "Detectado acúmulo de fadiga concorrente ou deriva cardíaca elevada para o mesmo pace. Reduza 25% do volume de treino de pernas nas 48h pré-corrida e priorize sono e hidratação.",
        colorHex = 0xFFFF5252
    )
}

/**
 * Pilar 1 — Volume & Consistência de Corrida (MAX RUN)
 */
data class VolumeConsistencyPillar(
    val totalSessions: Int,
    val totalDistanceKm: Double,
    val totalDurationSec: Int,
    val totalDurationFormatted: String,
    val globalAvgPaceSecPerKm: Int,
    val globalAvgPaceFormatted: String,
    val avgSprintPaceSecPerKm: Int,
    val avgSprintPaceFormatted: String,
    val bestSprintPaceSecPerKm: Int,
    val bestSprintPaceFormatted: String,
    val firstHalfSprintPaceSec: Int,
    val secondHalfSprintPaceSec: Int,
    val sprintPaceDeltaSec: Int, // negativo = ficou mais rápido (evolução)
    val sprintPaceEvolutionLabel: String,
    val isEvolutionPositive: Boolean,
    val consistencyProgress: Float,
    val narrative: String
)

/**
 * Pilar 2 — Eficiência Aeróbica & Evolução Cardíaca (Pace × BPM)
 */
data class AerobicEfficiencyPillar(
    val sessionsWithHrCount: Int,
    val avgHrBpm: Int,
    val peakHrBpm: Int,
    val calibratedMaxHrBpm: Int?,
    val avgEfficiencyFactor: Double, // Velocidade (m/min) / FC Média
    val firstHalfEf: Double,
    val secondHalfEf: Double,
    val efDeltaPercent: Double,
    val status: AerobicEfficiencyStatus,
    val efficiencyProgress: Float,
    val narrative: String
)

/**
 * Pilar 3 — Balanço de Carga Híbrida & Fadiga Concorrente (MAX GYM × MAX RUN)
 */
data class HybridLoadBalancePillar(
    val totalGymSessions: Int,
    val totalTonnageKg: Double,
    val lowerBodyTonnageKg: Double,
    val lowerBodyPercent: Int,
    val upperAndCoreTonnageKg: Double,
    val upperAndCorePercent: Int,
    val lowerBodySessionsNearRunCount: Int,
    val avgCadenceSpm: Int,
    val concurrentStatusLabel: String,
    val isConcurrentOverload: Boolean,
    val balanceProgress: Float,
    val narrative: String
)

/**
 * Pilar 4 — Recomposição Corporal & Impacto Termodinâmico no Pace (MAX RECOMP)
 */
data class ThermodynamicRecompPillar(
    val recordsInPeriodCount: Int,
    val startDateLabel: String,
    val endDateLabel: String,
    val startWeightKg: Float,
    val endWeightKg: Float,
    val weightDeltaKg: Float,
    val startFatPercent: Float,
    val endFatPercent: Float,
    val fatPercentDelta: Float,
    val startFatMassKg: Float,
    val endFatMassKg: Float,
    val fatMassDeltaKg: Float,
    val startMuscleMassKg: Float,
    val endMuscleMassKg: Float,
    val muscleMassDeltaKg: Float,
    val estimatedPaceGainSecPerKm: Float, // negativo = mais rápido (ex: -3.5s/km)
    val estimatedPaceGainFormatted: String,
    val recompProgress: Float,
    val narrative: String
)

/**
 * Dossiê Completo de Análise Longitudinal de Período (Macro-Análise Híbrida IA).
 */
data class PeriodMacroDossier(
    val window: MacroPeriodWindow,
    val periodRangeLabel: String,
    val generatedAtLabel: String,
    val pillar1Volume: VolumeConsistencyPillar,
    val pillar2Aerobic: AerobicEfficiencyPillar,
    val pillar3Hybrid: HybridLoadBalancePillar,
    val pillar4Recomp: ThermodynamicRecompPillar,
    val nextWeekDirective: NextWeekDirective,
    val coachExecutiveVerdict: String,
    val formattedReportText: String
)

object PeriodMacroAnalysisEngine {

    private val ptBr = Locale("pt", "BR")

    /**
     * Calcula o Dossiê Completo da Macro-Análise Híbrida IA para a janela selecionada.
     * Possui tratamento defensivo total (runCatching) para zero, 1 ou múltiplos treinos.
     */
    fun analyzePeriod(
        window: MacroPeriodWindow,
        allRunningSessions: List<RunningSession>,
        allGymSessions: List<GymWorkoutSession>,
        allRecompRecords: List<BioimpedanceRecord>,
        userAgeYears: Int?
    ): PeriodMacroDossier {
        return runCatching {
            val nowMs = System.currentTimeMillis()
            val latestRunMs = allRunningSessions.maxOfOrNull { it.date } ?: 0L
            val latestGymMs = allGymSessions.maxOfOrNull { it.date } ?: 0L
            val latestRecompMs = allRecompRecords.maxOfOrNull { it.timestampMillis } ?: 0L
            val anchorMs = maxOf(nowMs, latestRunMs, latestGymMs, latestRecompMs)

            val cutoffMs: Long? = window.days?.let { days ->
                anchorMs - (days.toLong() * 24L * 3600L * 1000L)
            }

            val runsInWindow = if (cutoffMs != null) {
                allRunningSessions.filter { it.date >= cutoffMs }.sortedBy { it.date }
            } else {
                allRunningSessions.sortedBy { it.date }
            }

            val gymsInWindow = if (cutoffMs != null) {
                allGymSessions.filter { it.date >= cutoffMs }.sortedBy { it.date }
            } else {
                allGymSessions.sortedBy { it.date }
            }

            val sortedAllRecomp = allRecompRecords.sortedBy { it.timestampMillis }
            val recompInWindow = if (cutoffMs != null) {
                val filtered = sortedAllRecomp.filter { it.timestampMillis >= cutoffMs }
                when {
                    filtered.size >= 2 -> filtered
                    filtered.size == 1 && sortedAllRecomp.size >= 2 -> {
                        val prev = sortedAllRecomp.lastOrNull { it.timestampMillis < filtered.first().timestampMillis }
                        if (prev != null) listOf(prev, filtered.first()) else sortedAllRecomp.takeLast(2)
                    }
                    else -> sortedAllRecomp.takeLast(2)
                }
            } else {
                sortedAllRecomp
            }

            val dateFmt = SimpleDateFormat("dd/MM/yyyy", ptBr)
            val dateTimeFmt = SimpleDateFormat("dd/MM/yyyy HH:mm", ptBr)
            val startBoundMs = cutoffMs ?: listOfNotNull(
                allRunningSessions.minOfOrNull { it.date },
                allGymSessions.minOfOrNull { it.date },
                allRecompRecords.minOfOrNull { it.timestampMillis }
            ).minOrNull() ?: (anchorMs - 7L * 24L * 3600L * 1000L)

            val periodRangeLabel = "${dateFmt.format(Date(startBoundMs))} a ${dateFmt.format(Date(anchorMs))}"
            val generatedAtLabel = dateTimeFmt.format(Date(nowMs))

            val p1 = computePillar1Volume(runsInWindow, window)
            val p2 = computePillar2Aerobic(runsInWindow, allRunningSessions, userAgeYears)
            val p3 = computePillar3Hybrid(gymsInWindow, runsInWindow, p1)
            val p4 = computePillar4Recomp(recompInWindow, sortedAllRecomp)

            val directive = resolveNextWeekDirective(p1, p2, p3, p4)
            val executiveVerdict = buildExecutiveVerdict(window, p1, p2, p3, p4, directive)

            val partialDossier = PeriodMacroDossier(
                window = window,
                periodRangeLabel = periodRangeLabel,
                generatedAtLabel = generatedAtLabel,
                pillar1Volume = p1,
                pillar2Aerobic = p2,
                pillar3Hybrid = p3,
                pillar4Recomp = p4,
                nextWeekDirective = directive,
                coachExecutiveVerdict = executiveVerdict,
                formattedReportText = ""
            )

            partialDossier.copy(
                formattedReportText = buildFormattedPeriodReportText(partialDossier)
            )
        }.getOrElse {
            buildSafeFallbackDossier(window)
        }
    }

    /**
     * Atalho que carrega automaticamente as bases caso a tela chamadora não possua todas em estado.
     */
    fun analyzeFromContext(
        context: Context,
        window: MacroPeriodWindow,
        runningSessionsOverride: List<RunningSession>? = null,
        gymSessionsOverride: List<GymWorkoutSession>? = null,
        recompRecordsOverride: List<BioimpedanceRecord>? = null,
        userAgeYears: Int? = null
    ): PeriodMacroDossier {
        val gymSessions = gymSessionsOverride ?: runCatching {
            GymHistoryRepository.init(context)
            GymHistoryRepository.sessions.value
        }.getOrDefault(emptyList())

        val recompRecords = recompRecordsOverride ?: runCatching {
            BodyRecompEngine.loadRecords(context)
        }.getOrDefault(emptyList())

        val runningSessions = runningSessionsOverride ?: emptyList()

        return analyzePeriod(
            window = window,
            allRunningSessions = runningSessions,
            allGymSessions = gymSessions,
            allRecompRecords = recompRecords,
            userAgeYears = userAgeYears
        )
    }

    private fun computePillar1Volume(
        runs: List<RunningSession>,
        window: MacroPeriodWindow
    ): VolumeConsistencyPillar {
        if (runs.isEmpty()) {
            return VolumeConsistencyPillar(
                totalSessions = 0,
                totalDistanceKm = 0.0,
                totalDurationSec = 0,
                totalDurationFormatted = "00:00:00",
                globalAvgPaceSecPerKm = 0,
                globalAvgPaceFormatted = "--:--",
                avgSprintPaceSecPerKm = 0,
                avgSprintPaceFormatted = "--:--",
                bestSprintPaceSecPerKm = 0,
                bestSprintPaceFormatted = "--:--",
                firstHalfSprintPaceSec = 0,
                secondHalfSprintPaceSec = 0,
                sprintPaceDeltaSec = 0,
                sprintPaceEvolutionLabel = "Sem sessões no período",
                isEvolutionPositive = true,
                consistencyProgress = 0.08f,
                narrative = "Nenhuma sessão de corrida registrada na janela (${window.pillLabel}). Inicie um treino ao ar livre no MAX RUN para destravar a curva de Pace de Tiros."
            )
        }

        val totalSessions = runs.size
        val totalDistanceMeters = runs.sumOf { it.distanceMeters.coerceAtLeast(0).toLong() }
        val totalDistanceKm = totalDistanceMeters / 1000.0
        val totalDurationSec = runs.sumOf { it.durationSec.coerceAtLeast(0) }
        val totalDurationFormatted = formatDurationHhMmSs(totalDurationSec)

        val globalAvgPaceSec = if (totalDistanceMeters > 50L && totalDurationSec > 0) {
            ((totalDurationSec.toDouble() / totalDistanceMeters.toDouble()) * 1000.0).roundToInt()
        } else {
            runs.mapNotNull { it.avgPaceSecPerKm.takeIf { p -> p in 120..1800 } }
                .takeIf { it.isNotEmpty() }?.average()?.roundToInt() ?: 0
        }

        // Calcula o Sprint Pace de cada sessão usando o motor real de Sprints
        val sessionSprintPaces = runs.mapNotNull { session ->
            val analytics = runCatching { calculateSprintAnalytics(session) }.getOrNull()
            val sprintPace = analytics?.avgSprintPaceSecPerKm?.takeIf { it in 120..1200 }
                ?: session.mainRunAvgPaceSecPerKm?.takeIf { it in 120..1200 }
                ?: session.avgPaceSecPerKm.takeIf { it in 120..1200 }
            sprintPace?.let { session to it }
        }

        val bestSprintPaces = runs.mapNotNull { session ->
            val analytics = runCatching { calculateSprintAnalytics(session) }.getOrNull()
            analytics?.bestSprintPaceSecPerKm?.takeIf { it in 120..1200 }
                ?: session.mainRunAvgPaceSecPerKm?.takeIf { it in 120..1200 }
                ?: session.avgPaceSecPerKm.takeIf { it in 120..1200 }
        }

        val avgSprintPaceSec = if (sessionSprintPaces.isNotEmpty()) {
            sessionSprintPaces.map { it.second }.average().roundToInt()
        } else {
            globalAvgPaceSec
        }

        val bestSprintPaceSec = bestSprintPaces.minOrNull() ?: avgSprintPaceSec

        // Evolução da 1ª metade vs 2ª metade (ou primeira vs última sessão)
        val (firstHalfSec, secondHalfSec, deltaSec) = if (sessionSprintPaces.size >= 2) {
            val mid = (sessionSprintPaces.size / 2).coerceAtLeast(1)
            val firstHalf = sessionSprintPaces.take(mid).map { it.second }.average().roundToInt()
            val secondHalf = sessionSprintPaces.drop(mid).map { it.second }.average().roundToInt()
            Triple(firstHalf, secondHalf, secondHalf - firstHalf)
        } else if (sessionSprintPaces.size == 1) {
            val single = sessionSprintPaces.first().second
            Triple(single, single, 0)
        } else {
            Triple(0, 0, 0)
        }

        val evolutionLabel = when {
            sessionSprintPaces.size < 2 -> "Base única (${formatPace(avgSprintPaceSec)}/km)"
            deltaSec < -2 -> "Evolução ${deltaSec}s/km nos tiros"
            deltaSec > 2 -> "Oscilação +${deltaSec}s/km nos tiros"
            else -> "Ritmo estável (${formatPace(avgSprintPaceSec)}/km)"
        }

        val targetSessions = when (window) {
            MacroPeriodWindow.DAYS_7 -> 3f
            MacroPeriodWindow.DAYS_15 -> 6f
            MacroPeriodWindow.DAYS_30 -> 12f
            MacroPeriodWindow.ALL_CYCLE -> 16f
        }
        val consistencyProgress = (totalSessions.toFloat() / targetSessions).coerceIn(0.12f, 1f)

        val narrative = buildString {
            append("Acumulado de ${String.format(ptBr, "%.2f km", totalDistanceKm)} em $totalSessions ")
            append(if (totalSessions == 1) "sessão" else "sessões")
            append(" (${totalDurationFormatted} em movimento). ")
            if (avgSprintPaceSec > 0) {
                append("O Pace Médio de Tiros (Sprint Pace) fechou em ${formatPace(avgSprintPaceSec)}/km ")
                append("(melhor parcial em ${formatPace(bestSprintPaceSec)}/km). ")
            }
            if (sessionSprintPaces.size >= 2) {
                if (deltaSec < 0) {
                    append("A 2ª metade do período foi ${abs(deltaSec)}s/km mais rápida que a 1ª (${formatPace(firstHalfSec)} → ${formatPace(secondHalfSec)}/km), confirmando ganho neuromotor.")
                } else if (deltaSec > 0) {
                    append("A 2ª metade do período registrou ${formatPace(secondHalfSec)}/km vs ${formatPace(firstHalfSec)}/km na 1ª metade (+${deltaSec}s/km), sugerindo maior densidade de carga ou terreno.")
                } else {
                    append("Consistência mecânica exemplar entre o início e o fim do bloco (${formatPace(secondHalfSec)}/km).")
                }
            } else {
                append("Complete mais uma sessão na janela para destravar o comparativo entre 1ª e 2ª metade do período.")
            }
        }

        return VolumeConsistencyPillar(
            totalSessions = totalSessions,
            totalDistanceKm = totalDistanceKm,
            totalDurationSec = totalDurationSec,
            totalDurationFormatted = totalDurationFormatted,
            globalAvgPaceSecPerKm = globalAvgPaceSec,
            globalAvgPaceFormatted = formatPace(globalAvgPaceSec),
            avgSprintPaceSecPerKm = avgSprintPaceSec,
            avgSprintPaceFormatted = formatPace(avgSprintPaceSec),
            bestSprintPaceSecPerKm = bestSprintPaceSec,
            bestSprintPaceFormatted = formatPace(bestSprintPaceSec),
            firstHalfSprintPaceSec = firstHalfSec,
            secondHalfSprintPaceSec = secondHalfSec,
            sprintPaceDeltaSec = deltaSec,
            sprintPaceEvolutionLabel = evolutionLabel,
            isEvolutionPositive = deltaSec <= 2,
            consistencyProgress = consistencyProgress,
            narrative = narrative
        )
    }

    private fun computePillar2Aerobic(
        runs: List<RunningSession>,
        allRuns: List<RunningSession>,
        userAgeYears: Int?
    ): AerobicEfficiencyPillar {
        val runsWithHr = runs.filter { (it.avgHr ?: 0) > 60 }
        val validAge = userAgeYears?.takeIf { it in 10..100 }
        val historicalPeakHr = (allRuns.mapNotNull { it.maxHr } + runs.mapNotNull { it.maxHr }).maxOrNull() ?: 0
        val calibratedMaxHr: Int? = validAge?.let { age ->
            val agePredictedMax = (220 - age).coerceIn(150, 205)
            maxOf(agePredictedMax, historicalPeakHr)
        }

        if (runsWithHr.isEmpty()) {
            return AerobicEfficiencyPillar(
                sessionsWithHrCount = 0,
                avgHrBpm = 0,
                peakHrBpm = 0,
                calibratedMaxHrBpm = calibratedMaxHr,
                avgEfficiencyFactor = 0.0,
                firstHalfEf = 0.0,
                secondHalfEf = 0.0,
                efDeltaPercent = 0.0,
                status = AerobicEfficiencyStatus.STABLE,
                efficiencyProgress = 0.2f,
                narrative = "Sem telemetria BLE cardíaca no período selecionado. Conecte a cinta peitoral ECG no topo do player para calcular o Fator de Eficiência (Velocidade ÷ BPM)."
            )
        }

        val avgHr = runsWithHr.mapNotNull { it.avgHr }.average().roundToInt()
        val peakHr = runsWithHr.mapNotNull { it.maxHr ?: it.avgHr }.maxOrNull() ?: avgHr

        // Efficiency Factor (EF) = Velocidade em m/min dividida pelo BPM médio
        // Ex: Pace 6'00"/km = 166.7 m/min. A 155 bpm -> EF = 1.075 m/batimento
        val efPerSession = runsWithHr.mapNotNull { s ->
            val hr = s.avgHr?.takeIf { it > 60 } ?: return@mapNotNull null
            val paceSec = s.mainRunAvgPaceSecPerKm?.takeIf { it in 120..1500 }
                ?: s.avgPaceSecPerKm.takeIf { it in 120..1500 }
                ?: if (s.distanceMeters > 50 && s.durationSec > 0) {
                    ((s.durationSec.toDouble() / s.distanceMeters.toDouble()) * 1000.0).roundToInt()
                } else null
            if (paceSec != null && paceSec > 0) {
                val speedMetersPerMin = 60000.0 / paceSec.toDouble()
                speedMetersPerMin / hr.toDouble()
            } else null
        }

        val avgEf = if (efPerSession.isNotEmpty()) efPerSession.average() else 1.0
        val (firstEf, secondEf, efDeltaPct) = if (efPerSession.size >= 2) {
            val mid = (efPerSession.size / 2).coerceAtLeast(1)
            val f = efPerSession.take(mid).average()
            val s = efPerSession.drop(mid).average()
            val pct = if (f > 0.01) ((s - f) / f) * 100.0 else 0.0
            Triple(f, s, pct)
        } else {
            Triple(avgEf, avgEf, 0.0)
        }

        val exceedsCalibratedThreshold = calibratedMaxHr != null && avgHr > (calibratedMaxHr * 0.91).roundToInt()
        val status = when {
            efDeltaPct >= 1.5 -> AerobicEfficiencyStatus.GAIN
            efDeltaPct <= -3.0 || exceedsCalibratedThreshold -> AerobicEfficiencyStatus.DRIFT_WARNING
            else -> AerobicEfficiencyStatus.STABLE
        }

        val progress = ((avgEf - 0.65) / (1.45 - 0.65)).toFloat().coerceIn(0.25f, 1.0f)
        val calibratedMaxHrLabel = calibratedMaxHr?.let { "$it bpm" } ?: "idade não informada"

        val narrative = buildString {
            append("Telemetria ECG registrada em ${runsWithHr.size} ")
            append(if (runsWithHr.size == 1) "sessão" else "sessões")
            append(": FC média de $avgHr bpm e pico real de $peakHr bpm (FCmáx calibrada: $calibratedMaxHrLabel). ")
            append("O Fator de Eficiência Aeróbica (metros percorridos por batimento cardíaco) atingiu ${String.format(ptBr, "%.2f", avgEf)} m/bpm. ")
            if (efPerSession.size >= 2) {
                val sign = if (efDeltaPct >= 0) "+" else ""
                append("Variação no período: $sign${String.format(ptBr, "%.1f%%", efDeltaPct)} → ${status.badgeLabel}.")
            } else {
                append("Status atual: ${status.badgeLabel}.")
            }
        }

        return AerobicEfficiencyPillar(
            sessionsWithHrCount = runsWithHr.size,
            avgHrBpm = avgHr,
            peakHrBpm = peakHr,
            calibratedMaxHrBpm = calibratedMaxHr,
            avgEfficiencyFactor = avgEf,
            firstHalfEf = firstEf,
            secondHalfEf = secondEf,
            efDeltaPercent = efDeltaPct,
            status = status,
            efficiencyProgress = progress,
            narrative = narrative
        )
    }

    private fun computePillar3Hybrid(
        gyms: List<GymWorkoutSession>,
        runs: List<RunningSession>,
        pillar1: VolumeConsistencyPillar
    ): HybridLoadBalancePillar {
        val avgCadence = runs.mapNotNull { s ->
            runCatching { calculateSprintAnalytics(s).avgCadenceSpm }.getOrNull()?.takeIf { it > 100 }
        }.takeIf { it.isNotEmpty() }?.average()?.roundToInt() ?: 162

        if (gyms.isEmpty()) {
            return HybridLoadBalancePillar(
                totalGymSessions = 0,
                totalTonnageKg = 0.0,
                lowerBodyTonnageKg = 0.0,
                lowerBodyPercent = 0,
                upperAndCoreTonnageKg = 0.0,
                upperAndCorePercent = 0,
                lowerBodySessionsNearRunCount = 0,
                avgCadenceSpm = avgCadence,
                concurrentStatusLabel = "SEM CARGA CONCORRENTE NO PERÍODO",
                isConcurrentOverload = false,
                balanceProgress = 0.15f,
                narrative = "Nenhuma sessão de musculação registrada no MAX GYM dentro desta janela. O custo mecânico de membros inferiores está 100% dedicado à corrida."
            )
        }

        var lowerKg = 0.0
        var upperCoreKg = 0.0
        var lowerSessionsNearRun = 0

        val lowerKeywords = listOf(
            "perna", "quadr", "agachamento", "leg", "extensora", "flexora",
            "panturrilha", "glúteo", "gluteo", "stiff", "afundo", "passada",
            "hack", "adutora", "abdutora", "levantamento terra", "soleo", "gêmeos"
        )

        gyms.forEach { session ->
            var sessionLowerKg = 0.0
            var sessionTotalCalculated = 0.0

            session.exercises.forEach { ex ->
                val text = "${ex.name} ${ex.category} ${ex.equipment}".lowercase(ptBr)
                val isLower = lowerKeywords.any { kw -> text.contains(kw) }
                val exVol = ex.sets.filter { it.completed || it.weight > 0f }.sumOf {
                    (it.weight.coerceAtLeast(0f) * it.reps.coerceAtLeast(0)).toDouble()
                }
                sessionTotalCalculated += exVol
                if (isLower) {
                    sessionLowerKg += exVol
                }
            }

            val effectiveSessionVol = if (sessionTotalCalculated > 0.0) {
                sessionTotalCalculated
            } else {
                session.totalVolumeKg.toDouble().coerceAtLeast(0.0)
            }

            val workoutNameLower = session.workoutName.lowercase(ptBr)
            val effectiveLowerVol = when {
                sessionLowerKg > 0.0 -> sessionLowerKg
                lowerKeywords.any { workoutNameLower.contains(it) } || workoutNameLower.contains("treino b") || workoutNameLower.contains("lower") -> effectiveSessionVol * 0.75
                else -> 0.0
            }

            lowerKg += effectiveLowerVol
            upperCoreKg += (effectiveSessionVol - effectiveLowerVol).coerceAtLeast(0.0)

            // Verifica se houve treino pesado de pernas até 36h antes de alguma corrida
            if (effectiveLowerVol >= 1200.0) {
                val hadRunSoonAfter = runs.any { run ->
                    val diffHours = (run.date - session.date) / (1000.0 * 3600.0)
                    diffHours in 0.0..36.0
                }
                if (hadRunSoonAfter) lowerSessionsNearRun++
            }
        }

        val totalTonnage = (lowerKg + upperCoreKg).coerceAtLeast(1.0)
        val lowerPct = ((lowerKg / totalTonnage) * 100.0).roundToInt().coerceIn(0, 100)
        val upperPct = (100 - lowerPct).coerceIn(0, 100)

        val isOverload = (lowerPct > 52 && pillar1.sprintPaceDeltaSec > 4) || lowerSessionsNearRun >= 2
        val statusLabel = when {
            isOverload -> "ALERTA DE INTERFERÊNCIA CONCORRENTE (PERNAS × TIROS)"
            lowerKg > 0 && pillar1.sprintPaceDeltaSec <= 0 -> "SINERGIA HÍBRIDA POSITIVA (FORÇA + VELOCIDADE)"
            else -> "BALANÇO HÍBRIDO EQUILIBRADO"
        }

        val balanceProgress = (gyms.size.toFloat() / 6f).coerceIn(0.25f, 1f)

        val narrative = buildString {
            append("${gyms.size} ")
            append(if (gyms.size == 1) "sessão" else "sessões")
            append(" no MAX GYM somando ${formatTonnage(lowerKg + upperCoreKg)} de tonelagem total: ")
            append("${formatTonnage(lowerKg)} em Membros Inferiores ($lowerPct%) vs ${formatTonnage(upperCoreKg)} em Superiores/Tronco ($upperPct%). ")
            if (isOverload) {
                append("Identificamos $lowerSessionsNearRun ")
                append(if (lowerSessionsNearRun == 1) "choque" else "choques")
                append(" de treino pesado de pernas (<36h antes dos tiros), impactando a cadência média ($avgCadence spm). Separe o treino de quadríceps/posterior em pelo menos 48h dos tiros de velocidade.")
            } else {
                append("O volume de força de membros inferiores está sustentando a rigidez tendínea e a potência de passada ($avgCadence spm) sem degradar o Pace de Tiros.")
            }
        }

        return HybridLoadBalancePillar(
            totalGymSessions = gyms.size,
            totalTonnageKg = lowerKg + upperCoreKg,
            lowerBodyTonnageKg = lowerKg,
            lowerBodyPercent = lowerPct,
            upperAndCoreTonnageKg = upperCoreKg,
            upperAndCorePercent = upperPct,
            lowerBodySessionsNearRunCount = lowerSessionsNearRun,
            avgCadenceSpm = avgCadence,
            concurrentStatusLabel = statusLabel,
            isConcurrentOverload = isOverload,
            balanceProgress = balanceProgress,
            narrative = narrative
        )
    }

    private fun computePillar4Recomp(
        windowRecords: List<BioimpedanceRecord>,
        allRecords: List<BioimpedanceRecord>
    ): ThermodynamicRecompPillar {
        val records = if (windowRecords.isNotEmpty()) windowRecords else allRecords
        if (records.isEmpty()) {
            return ThermodynamicRecompPillar(
                recordsInPeriodCount = 0,
                startDateLabel = "--",
                endDateLabel = "--",
                startWeightKg = 0f,
                endWeightKg = 0f,
                weightDeltaKg = 0f,
                startFatPercent = 0f,
                endFatPercent = 0f,
                fatPercentDelta = 0f,
                startFatMassKg = 0f,
                endFatMassKg = 0f,
                fatMassDeltaKg = 0f,
                startMuscleMassKg = 0f,
                endMuscleMassKg = 0f,
                muscleMassDeltaKg = 0f,
                estimatedPaceGainSecPerKm = 0f,
                estimatedPaceGainFormatted = "--",
                recompProgress = 0f,
                narrative = "Sem registros de bioimpedância no período."
            )
        }

        val start = records.first()
        val end = records.last()

        val weightDelta = end.weightKg - start.weightKg
        val fatPctDelta = end.bodyFatPercent - start.bodyFatPercent
        val fatMassDelta = end.fatMassKg - start.fatMassKg
        val muscleDelta = end.muscleMassKg - start.muscleMassKg

        // Regra fisiológica solicitada: ~2,5s/km de ganho potencial de economia de corrida
        // para cada -1,0 kg de massa gorda / lastro não-contrátil eliminado.
        val effectiveDeadWeightDeltaKg = if (abs(fatMassDelta) >= 0.05f) fatMassDelta else weightDelta
        val paceImpactSecPerKm = effectiveDeadWeightDeltaKg * 2.5f
        val paceGainFormatted = when {
            paceImpactSecPerKm < -0.1f -> String.format(ptBr, "%.0fs/km", paceImpactSecPerKm)
            paceImpactSecPerKm > 0.1f -> String.format(ptBr, "+%.0fs/km", paceImpactSecPerKm)
            else -> "Estável"
        }

        val recompProgress = when {
            fatMassDelta < 0f && muscleDelta >= -0.2f -> (0.65f + (abs(fatMassDelta) / 4f)).coerceIn(0.5f, 1f)
            else -> 0.45f
        }

        val narrative = buildString {
            append("Comparativo entre ${start.dateTimeLabel.take(10)} e ${end.dateTimeLabel.take(10)}: ")
            append("Peso ${formatSignedKg(weightDelta)} (${String.format(ptBr, "%.1f", start.weightKg)} → ${String.format(ptBr, "%.1f kg", end.weightKg)}), ")
            append("Gordura ${formatSignedKg(fatMassDelta)} (${String.format(ptBr, "%.1f%%", start.bodyFatPercent)} → ${String.format(ptBr, "%.1f%%", end.bodyFatPercent)}) ")
            append("e Massa Muscular ${formatSignedKg(muscleDelta)} (${String.format(ptBr, "%.1f kg", end.muscleMassKg)}). ")
            if (paceImpactSecPerKm < -0.1f) {
                append("A redução de lastro lipídico gera um Ganho Estimado de Economia de Corrida de ${String.format(ptBr, "%.1fs/km", abs(paceImpactSecPerKm))} nos tiros sem exigir batimentos extras.")
            } else if (paceImpactSecPerKm > 0.1f) {
                append("A oscilação de massa representa um custo adicional estimado de +${String.format(ptBr, "%.1fs/km", paceImpactSecPerKm)}; priorize déficit calórico limpo com preservação proteica.")
            } else {
                append("Composição corporal em equilíbrio termodinâmico no recorte selecionado; registre nova pesagem semanal para atualizar a curva de economia de corrida.")
            }
        }

        return ThermodynamicRecompPillar(
            recordsInPeriodCount = records.size,
            startDateLabel = start.dateTimeLabel.take(10),
            endDateLabel = end.dateTimeLabel.take(10),
            startWeightKg = start.weightKg,
            endWeightKg = end.weightKg,
            weightDeltaKg = weightDelta,
            startFatPercent = start.bodyFatPercent,
            endFatPercent = end.bodyFatPercent,
            fatPercentDelta = fatPctDelta,
            startFatMassKg = start.fatMassKg,
            endFatMassKg = end.fatMassKg,
            fatMassDeltaKg = fatMassDelta,
            startMuscleMassKg = start.muscleMassKg,
            endMuscleMassKg = end.muscleMassKg,
            muscleMassDeltaKg = muscleDelta,
            estimatedPaceGainSecPerKm = paceImpactSecPerKm,
            estimatedPaceGainFormatted = paceGainFormatted,
            recompProgress = recompProgress,
            narrative = narrative
        )
    }

    private fun resolveNextWeekDirective(
        p1: VolumeConsistencyPillar,
        p2: AerobicEfficiencyPillar,
        p3: HybridLoadBalancePillar,
        p4: ThermodynamicRecompPillar
    ): NextWeekDirective {
        if (p2.status == AerobicEfficiencyStatus.DRIFT_WARNING || p3.isConcurrentOverload) {
            return NextWeekDirective.METABOLIC_DELOAD
        }
        if (p1.totalSessions >= 2 && (p1.sprintPaceDeltaSec < -2 || p2.status == AerobicEfficiencyStatus.GAIN || p4.fatMassDeltaKg <= -0.4f)) {
            return NextWeekDirective.INCREASE_SPRINT_STIMULUS
        }
        return NextWeekDirective.MAINTAIN_PROGRESSION
    }

    private fun buildExecutiveVerdict(
        window: MacroPeriodWindow,
        p1: VolumeConsistencyPillar,
        p2: AerobicEfficiencyPillar,
        p3: HybridLoadBalancePillar,
        p4: ThermodynamicRecompPillar,
        directive: NextWeekDirective
    ): String {
        return buildString {
            append("No recorte ${window.fullTitle}, o cruzamento das 3 bases (MAX RUN + MAX GYM + MAX RECOMP) aponta ")
            append("diretriz de ${directive.label}. ")
            if (p1.totalSessions > 0) {
                append("Você acumulou ${String.format(ptBr, "%.2f km", p1.totalDistanceKm)} com Sprint Pace médio de ${p1.avgSprintPaceFormatted}/km ")
            } else {
                append("Ainda sem corridas registradas na janela ")
            }
            if (p2.sessionsWithHrCount > 0) {
                append("e FC média de ${p2.avgHrBpm} bpm (${p2.status.badgeLabel}). ")
            } else {
                append("(conecte a cinta BLE nas próximas corridas para medir a eficiência cardíaca). ")
            }
            append(directive.recommendation)
        }
    }

    /**
     * Gera o Relatório Executivo de Período completo e estruturado em texto puro,
     * pronto para compartilhar no WhatsApp, salvar em notas ou enviar ao treinador/IA.
     */
    fun buildFormattedPeriodReportText(dossier: PeriodMacroDossier): String {
        val p1 = dossier.pillar1Volume
        val p2 = dossier.pillar2Aerobic
        val p3 = dossier.pillar3Hybrid
        val p4 = dossier.pillar4Recomp

        return buildString {
            appendLine("==================================================")
            appendLine("DOSSIÊ DE PERÍODO • MACRO-ANÁLISE HÍBRIDA IA")
            appendLine("MAX RUN × MAX GYM × MAX RECOMP")
            appendLine("==================================================")
            appendLine("Janela Analisada: ${dossier.window.fullTitle}")
            appendLine("Período: ${dossier.periodRangeLabel}")
            appendLine("Gerado em: ${dossier.generatedAtLabel}")
            appendLine("--------------------------------------------------")
            appendLine("1. VOLUME & CONSISTÊNCIA DE CORRIDA (MAX RUN)")
            appendLine("--------------------------------------------------")
            appendLine("• Sessões Concluídas: ${p1.totalSessions}")
            appendLine("• Distância Acumulada: ${String.format(ptBr, "%.2f km", p1.totalDistanceKm)}")
            appendLine("• Tempo Total em Movimento: ${p1.totalDurationFormatted}")
            appendLine("• Pace Global do Período: ${p1.globalAvgPaceFormatted}/km")
            appendLine("• Pace Médio de Tiros (Sprint Pace): ${p1.avgSprintPaceFormatted}/km (Melhor: ${p1.bestSprintPaceFormatted}/km)")
            appendLine("• Evolução 1ª vs 2ª Metade: ${p1.sprintPaceEvolutionLabel}")
            appendLine("• Análise: ${p1.narrative}")
            appendLine()
            appendLine("--------------------------------------------------")
            appendLine("2. EFICIÊNCIA AERÓBICA & EVOLUÇÃO CARDÍACA (PACE × BPM)")
            appendLine("--------------------------------------------------")
            appendLine("• Status Cardíaco: ${p2.status.badgeLabel}")
            if (p2.sessionsWithHrCount > 0) {
                val maxHrReportText = p2.calibratedMaxHrBpm?.let { "$it bpm" } ?: "idade não informada"
                appendLine("• FC Média / Pico Real no Período: ${p2.avgHrBpm} bpm / ${p2.peakHrBpm} bpm (FCmáx Calibrada: $maxHrReportText)")
                appendLine("• Fator de Eficiência (Velocidade ÷ BPM): ${String.format(ptBr, "%.2f", p2.avgEfficiencyFactor)} m/bpm")
            } else {
                appendLine("• Telemetria BLE: Sem sessões com cinta cardíaca no período")
            }
            appendLine("• Análise: ${p2.narrative}")
            appendLine()
            appendLine("--------------------------------------------------")
            appendLine("3. BALANÇO DE CARGA HÍBRIDA & FADIGA CONCORRENTE (MAX GYM × MAX RUN)")
            appendLine("--------------------------------------------------")
            appendLine("• Diagnóstico Híbrido: ${p3.concurrentStatusLabel}")
            appendLine("• Sessões de Força (MAX GYM): ${p3.totalGymSessions} treinos (${formatTonnage(p3.totalTonnageKg)} totais)")
            appendLine("• Divisão Muscular: Pernas ${formatTonnage(p3.lowerBodyTonnageKg)} (${p3.lowerBodyPercent}%) | Superiores/Tronco ${formatTonnage(p3.upperAndCoreTonnageKg)} (${p3.upperAndCorePercent}%)")
            appendLine("• Cadência Média de Corrida: ${p3.avgCadenceSpm} spm")
            appendLine("• Análise: ${p3.narrative}")
            appendLine()
            appendLine("--------------------------------------------------")
            appendLine("4. RECOMPOSIÇÃO CORPORAL & IMPACTO TERMODINÂMICO NO PACE (MAX RECOMP)")
            appendLine("--------------------------------------------------")
            if (p4.recordsInPeriodCount == 0) {
                appendLine("• Sem registros de bioimpedância no período.")
            } else {
                appendLine("• Variação de Peso: ${String.format(ptBr, "%.1f kg", p4.startWeightKg)} → ${String.format(ptBr, "%.1f kg", p4.endWeightKg)} (${formatSignedKg(p4.weightDeltaKg)})")
                appendLine("• Massa Gorda: ${String.format(ptBr, "%.1f kg", p4.startFatMassKg)} (${String.format(ptBr, "%.1f%%", p4.startFatPercent)}) → ${String.format(ptBr, "%.1f kg", p4.endFatMassKg)} (${String.format(ptBr, "%.1f%%", p4.endFatPercent)}) [${formatSignedKg(p4.fatMassDeltaKg)}]")
                appendLine("• Massa Muscular: ${String.format(ptBr, "%.1f kg", p4.startMuscleMassKg)} → ${String.format(ptBr, "%.1f kg", p4.endMuscleMassKg)} (${formatSignedKg(p4.muscleMassDeltaKg)})")
                appendLine("• Ganho Estimado de Economia de Corrida: ${p4.estimatedPaceGainFormatted}")
                appendLine("• Análise: ${p4.narrative}")
            }
            appendLine()
            appendLine("==================================================")
            appendLine("DIRETRIZ FISIOLÓGICA PARA A PRÓXIMA SEMANA:")
            appendLine(">>> ${dossier.nextWeekDirective.label} <<<")
            appendLine("--------------------------------------------------")
            appendLine(dossier.coachExecutiveVerdict)
            appendLine("==================================================")
        }.trimEnd()
    }

    private fun buildSafeFallbackDossier(window: MacroPeriodWindow): PeriodMacroDossier {
        val p1 = computePillar1Volume(emptyList(), window)
        val p2 = computePillar2Aerobic(emptyList(), emptyList(), null)
        val p3 = computePillar3Hybrid(emptyList(), emptyList(), p1)
        val p4 = computePillar4Recomp(emptyList(), emptyList())
        val directive = NextWeekDirective.MAINTAIN_PROGRESSION
        val verdict = "Dossiê pronto para cruzar suas sessões do MAX RUN, MAX GYM e MAX RECOMP."
        val base = PeriodMacroDossier(
            window = window,
            periodRangeLabel = window.fullTitle,
            generatedAtLabel = "--",
            pillar1Volume = p1,
            pillar2Aerobic = p2,
            pillar3Hybrid = p3,
            pillar4Recomp = p4,
            nextWeekDirective = directive,
            coachExecutiveVerdict = verdict,
            formattedReportText = ""
        )
        return base.copy(formattedReportText = buildFormattedPeriodReportText(base))
    }

    fun formatPace(secPerKm: Int): String {
        if (secPerKm <= 0 || secPerKm > 3600) return "--:--"
        val m = secPerKm / 60
        val s = secPerKm % 60
        return String.format(Locale.US, "%d'%02d\"", m, s)
    }

    fun formatDurationHhMmSs(totalSec: Int): String {
        if (totalSec <= 0) return "00:00:00"
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return String.format(Locale.US, "%02d:%02d:%02d", h, m, s)
    }

    fun formatTonnage(kg: Double): String {
        return if (kg >= 1000.0) {
            String.format(ptBr, "%.2f t", kg / 1000.0)
        } else {
            String.format(ptBr, "%.0f kg", kg)
        }
    }

    fun formatSignedKg(delta: Float): String {
        val sign = if (delta > 0.04f) "+" else ""
        return String.format(ptBr, "%s%.1f kg", sign, delta)
    }
}
