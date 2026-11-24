package com.example.engine

import com.example.data.model.GymExerciseRecord
import com.example.data.model.GymWorkoutSession
import com.example.data.model.PreRunCheckIn
import com.example.data.model.RunningSession
import com.example.data.model.UpcomingRaceEvent
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.ceil
import kotlin.math.roundToInt

enum class LowerBodyReadinessLevel {
    ACUTE_FATIGUE,       // < 24h
    RECOVERING_24_48H,   // 24h a 48h
    RECOVERED_OVER_48H   // > 48h ou sem estímulo recente
}

enum class TafCountdownPhase(val badgeLabel: String) {
    RACE_DAY("DIA DO TAF OFICIAL"),
    SUPERCOMPENSATION_1_3D("SUPERCOMPENSAÇÃO FINAL (1–3 DIAS)"),
    TAPERING_4_7D("SEMANA DE POLIMENTO / TAPERING (4–7 DIAS)"),
    FINAL_SPECIFICITY_8_12D("FASE FINAL DE ESPECIFICIDADE (8–12 DIAS)"),
    BASE_BUILDING("CONSTRUÇÃO ESPECÍFICA (>12 DIAS)")
}

data class HybridLoadCoachSummary(
    // 1. Carga Semanal Híbrida (sRPE - 7d e 14d)
    val runningWeeklyLoadUa: Int,
    val gymWeeklyLoadUa: Int,
    val totalHybridLoadUa: Int,
    val runningSharePct: Int,
    val gymSharePct: Int,
    val runningSessionsCount7d: Int,
    val gymSessionsCount7d: Int,
    val running14dLoadUa: Int,
    val gym14dLoadUa: Int,
    val total14dLoadUa: Int,

    // 2. Rastreio de Fadiga Neuromuscular de Membros Inferiores
    val hoursSinceLastLowerBodyGym: Int?,
    val lastLowerBodyWorkoutName: String?,
    val lastLowerBodySetsCount: Int,
    val lowerBodyReadinessLevel: LowerBodyReadinessLevel,
    val lowerBodyReadinessStatusText: String,
    val localizedPainImpactNote: String?,

    // 3. Protocolo Inteligente de Reta Final TAF
    val targetRaceEvent: UpcomingRaceEvent,
    val daysUntilTargetRace: Int,
    val tafPhase: TafCountdownPhase,
    val tafProtocolText: String,
    val todayCoachDirective: String
)

/**
 * Motor Determinístico Read-Only de Consolidação de Carga Híbrida (Corrida + Musculação)
 * e Coach de Polimento para o TAF.
 *
 * Opera em modo 100% Read-Only sobre RunningSession, GymWorkoutSession e UpcomingRaceEvent,
 * garantindo zero impacto nos serviços de execução de treino (GPS / Áudio / Foreground Services).
 */
object HybridLoadCoachEngine {

    private val lowerBodyKeywords = listOf(
        "quadríceps", "quadriceps", "posterior", "panturrilha", "panturrilhas",
        "glúteo", "glúteos", "gluteo", "gluteos", "perna", "pernas", "inferiores",
        "coxa", "agachamento", "leg press", "leg ", "rdl", "terra", "extensora",
        "flexora", "afundo", "passada", "búlgaro", "bulgaro", "hack", "stiff",
        "elevação pélvica", "elevacao pelvica", "hip thrust", "gêmeos", "gemeos"
    )

    /**
     * Cria o evento padrão editável "TAF Oficial PMAC" para 22/11/2026 (permitindo troca rápida para 23/11).
     */
    fun buildDefaultTafUpcomingEvent(): UpcomingRaceEvent {
        val cal = Calendar.getInstance(TimeZone.getDefault()).apply {
            set(Calendar.YEAR, 2026)
            set(Calendar.MONTH, Calendar.NOVEMBER)
            set(Calendar.DAY_OF_MONTH, 22)
            set(Calendar.HOUR_OF_DAY, 7)
            set(Calendar.MINUTE, 30)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return UpcomingRaceEvent(
            id = "upcoming_taf_oficial_pmac",
            raceName = "TAF Oficial PMAC",
            raceDateMillis = cal.timeInMillis,
            distanceCategory = "TAF",
            targetPace = "5'00\"/km",
            plannedShoe = "Olympikus Corre 4",
            notes = "Meta oficial TAF: 2.400m em até 12:00 (Pace 5'00\"/km). Toque em editar caso sua bateria seja dia 23/11.",
            notifyOneWeekBefore = true,
            notifyOneDayBefore = true
        )
    }

    /**
     * Verifica se um exercício de musculação envolve membros inferiores.
     */
    fun isLowerBodyExercise(exercise: GymExerciseRecord): Boolean {
        val combined = "${exercise.name} ${exercise.category}".lowercase()
        // Exclui remada curvada / terra parcial apenas se for explicitamente costas sem foco em pernas
        if (combined.contains("remada") || combined.contains("puxada") || combined.contains("rosca") || combined.contains("supino") || combined.contains("tríceps") || combined.contains("triceps")) {
            return false
        }
        return lowerBodyKeywords.any { combined.contains(it) }
    }

    /**
     * Calcula a carga interna sRPE (UA) de uma sessão de corrida.
     * Se a sessão não possuir sRpeLoadUa explícito, estima de forma conservadora por durationMinutes * 6.
     */
    fun computeRunningSessionLoadUa(session: RunningSession): Int {
        session.sRpeLoadUa?.takeIf { it > 0 }?.let { return it }
        val durationMin = (session.durationSec / 60.0).roundToInt().coerceAtLeast(1)
        val rpeCr10 = session.rpeCr10?.takeIf { it in 1..10 }
        return if (rpeCr10 != null) {
            durationMin * rpeCr10
        } else {
            durationMin * 6
        }
    }

    /**
     * Calcula a carga interna (UA) de uma sessão de musculação.
     * Ignora sessões puramente de mobilidade/recuperação ativa com 0 kg.
     * Usa duração em minutos * RPE da sessão (se houver) ou séries concluídas * 18 UA.
     */
    fun computeGymSessionLoadUa(session: GymWorkoutSession): Int {
        val normName = session.workoutName.lowercase()
        val isZeroLoadMobility = session.totalVolumeKg <= 0f &&
            (normName.contains("mobilidade") || normName.contains("recuperação") || normName.contains("recuperacao") || normName.contains("alongamento"))
        if (isZeroLoadMobility) return 0

        val completedSetsCount = session.exercises
            .flatMap { it.sets }
            .count { it.completed }
            .takeIf { it > 0 }
            ?: session.completedSets.coerceAtLeast(0)

        val explicitRpeRegex = Regex("""\bRPE\s*[:=]?\s*(\d{1,2})\b""", RegexOption.IGNORE_CASE)
        val explicitRpe = explicitRpeRegex.find(session.notes)?.groupValues?.getOrNull(1)?.toIntOrNull()?.takeIf { it in 1..10 }
        val durationMin = (session.durationSeconds / 60.0).roundToInt()

        return if (explicitRpe != null && durationMin >= 10) {
            durationMin * explicitRpe
        } else {
            completedSetsCount.coerceAtLeast(1) * 18
        }
    }

    /**
     * Consolida toda a telemetria híbrida dos últimos 7 e 14 dias, rastreia fadiga de membros inferiores (72h)
     * e gera o protocolo inteligente de reta final para o TAF.
     */
    fun evaluateHybridState(
        runningSessions: List<RunningSession>,
        gymSessions: List<GymWorkoutSession>,
        upcomingRaces: List<UpcomingRaceEvent>,
        activePreRunCheckIn: PreRunCheckIn? = null,
        nowMillis: Long = System.currentTimeMillis()
    ): HybridLoadCoachSummary {
        val window7dMillis = 7L * 86_400_000L
        val window14dMillis = 14L * 86_400_000L

        // 1. Carga Semanal Híbrida (7d e 14d)
        val runs7d = runningSessions.filter { (nowMillis - it.date) in 0L..window7dMillis }
        val runs14d = runningSessions.filter { (nowMillis - it.date) in 0L..window14dMillis }

        val gyms7d = gymSessions
            .filter { (nowMillis - it.date) in 0L..window7dMillis }
            .distinctBy { "${it.workoutName.trim().lowercase()}|${it.date / 3600_000L}" }
        val gyms14d = gymSessions
            .filter { (nowMillis - it.date) in 0L..window14dMillis }
            .distinctBy { "${it.workoutName.trim().lowercase()}|${it.date / 3600_000L}" }

        val runningWeeklyLoadUa = runs7d.sumOf { computeRunningSessionLoadUa(it) }
        val gymWeeklyLoadUa = gyms7d.sumOf { computeGymSessionLoadUa(it) }
        val totalHybridLoadUa = runningWeeklyLoadUa + gymWeeklyLoadUa

        val runningSharePct = if (totalHybridLoadUa > 0) {
            ((runningWeeklyLoadUa * 100.0) / totalHybridLoadUa.toDouble()).roundToInt().coerceIn(0, 100)
        } else 50
        val gymSharePct = if (totalHybridLoadUa > 0) (100 - runningSharePct).coerceIn(0, 100) else 50

        val running14dLoadUa = runs14d.sumOf { computeRunningSessionLoadUa(it) }
        val gym14dLoadUa = gyms14d.sumOf { computeGymSessionLoadUa(it) }
        val total14dLoadUa = running14dLoadUa + gym14dLoadUa

        // 2. Rastreio de Fadiga Neuromuscular de Membros Inferiores
        data class LowerBodyStimulus(
            val sessionDateMillis: Long,
            val workoutName: String,
            val lowerBodySets: Int
        )

        val latestLowerBodyStimulus = gymSessions
            .sortedByDescending { it.date }
            .mapNotNull { g ->
                val normTitle = g.workoutName.lowercase()
                val isMobilityZero = g.totalVolumeKg <= 0f &&
                    (normTitle.contains("mobilidade") || normTitle.contains("recuperação") || normTitle.contains("recuperacao"))
                if (isMobilityZero) return@mapNotNull null

                val lowerExercises = g.exercises.filter { isLowerBodyExercise(it) }
                val lowerSets = lowerExercises.sumOf { ex -> ex.sets.count { it.completed } }
                val titleIndicatesLegs = lowerBodyKeywords.any { normTitle.contains(it) }
                val effectiveSets = when {
                    lowerSets > 0 -> lowerSets
                    titleIndicatesLegs -> g.completedSets.coerceAtLeast(4)
                    else -> 0
                }
                if (effectiveSets > 0 && g.date <= nowMillis + 3600_000L) {
                    LowerBodyStimulus(
                        sessionDateMillis = g.date,
                        workoutName = g.workoutName,
                        lowerBodySets = effectiveSets
                    )
                } else null
            }
            .firstOrNull()

        val hoursSinceLowerBody = latestLowerBodyStimulus?.let {
            ((nowMillis - it.sessionDateMillis).coerceAtLeast(0L) / 3600_000L).toInt()
        }

        val readinessLevel = when {
            hoursSinceLowerBody == null -> LowerBodyReadinessLevel.RECOVERED_OVER_48H
            hoursSinceLowerBody < 24 -> LowerBodyReadinessLevel.ACUTE_FATIGUE
            hoursSinceLowerBody <= 48 -> LowerBodyReadinessLevel.RECOVERING_24_48H
            else -> LowerBodyReadinessLevel.RECOVERED_OVER_48H
        }

        val readinessStatusText = when (readinessLevel) {
            LowerBodyReadinessLevel.ACUTE_FATIGUE ->
                "Fadiga Aguda de Membros Inferiores (<24h) — Evitar tiros máximos hoje"
            LowerBodyReadinessLevel.RECOVERING_24_48H ->
                "Recuperação Muscular em Andamento (24-48h) — Atenção à panturrilha e posterior"
            LowerBodyReadinessLevel.RECOVERED_OVER_48H ->
                "Membros Inferiores Recuperados (>48h) — Janela ideal para treino de ritmo/velocidade"
        }

        // Verifica impacto de dor localizada do check-in ativo ou da corrida mais recente (últimas 36h)
        val recentPreRunCheckIn = activePreRunCheckIn
            ?: runningSessions
                .filter { (nowMillis - it.date) in 0L..(36L * 3600_000L) }
                .maxByOrNull { it.date }
                ?.preRunCheckIn

        val localizedPainImpactNote = recentPreRunCheckIn?.localizedPains
            ?.takeIf { it.isNotEmpty() }
            ?.joinToString("; ") { p ->
                val gait = if (p.altersGaitMechanics) " (altera mecânica da passada)" else ""
                "${p.region.label}: ${p.intensityCr10}/10$gait"
            }

        // 3. Protocolo Inteligente de Reta Final TAF
        val defaultTafEvent = buildDefaultTafUpcomingEvent()
        val resolvedTargetEvent = upcomingRaces
            .filter { it.raceDateMillis >= nowMillis - 86_400_000L }
            .sortedWith(
                compareByDescending<UpcomingRaceEvent> {
                    it.distanceCategory.equals("TAF", ignoreCase = true) ||
                        it.raceName.contains("TAF", ignoreCase = true)
                }.thenBy { it.raceDateMillis }
            )
            .firstOrNull()
            ?: upcomingRaces.firstOrNull()
            ?: defaultTafEvent

        val diffMillis = (resolvedTargetEvent.raceDateMillis - nowMillis).coerceAtLeast(0L)
        val daysUntilRace = ceil(diffMillis.toDouble() / 86_400_000.0).toInt().coerceAtLeast(0)

        val tafPhase = when {
            daysUntilRace == 0 -> TafCountdownPhase.RACE_DAY
            daysUntilRace in 1..3 -> TafCountdownPhase.SUPERCOMPENSATION_1_3D
            daysUntilRace in 4..7 -> TafCountdownPhase.TAPERING_4_7D
            daysUntilRace in 8..12 -> TafCountdownPhase.FINAL_SPECIFICITY_8_12D
            else -> TafCountdownPhase.BASE_BUILDING
        }

        val tafProtocolText = when (tafPhase) {
            TafCountdownPhase.FINAL_SPECIFICITY_8_12D ->
                "Manter intensidade nos tiros específicos do TAF, mas iniciar redução de 20% no volume de séries de pernas na musculação."
            TafCountdownPhase.TAPERING_4_7D ->
                "ALERTA DE POLIMENTO: Suspender treinos pesados de pernas na academia (zero falha muscular em quadríceps/posterior/panturrilha). Na corrida, reduzir volume total em 40% mantendo apenas estímulos curtos no pace do TAF."
            TafCountdownPhase.SUPERCOMPENSATION_1_3D ->
                "Descanso muscular total de membros inferiores. Foco em sono, hidratação e ativação leve."
            TafCountdownPhase.RACE_DAY ->
                "DIA DA PROVA / TAF: Membros inferiores preservados. Realize apenas aquecimento articular leve e confie no ritmo treinado."
            TafCountdownPhase.BASE_BUILDING ->
                "Fase de Construção Específica: manter progressão da planilha de corrida e espaçar treinos pesados de pernas em pelo menos 48h antes dos treinos de ritmo."
        }

        // Diretriz combinada para Hoje (cruzando dias restantes p/ o TAF + fadiga de pernas + dor localizada)
        val todayDirective = buildString {
            if (!localizedPainImpactNote.isNullOrBlank()) {
                append("Atenção clínica prioritária ($localizedPainImpactNote): evite impacto de alta intensidade hoje e priorize recuperação articular. ")
            }
            when (tafPhase) {
                TafCountdownPhase.SUPERCOMPENSATION_1_3D -> {
                    append("A $daysUntilRace ${if (daysUntilRace == 1) "dia" else "dias"} do ${resolvedTargetEvent.raceName}: descanso muscular total de pernas na academia e apenas ativação leve ou repouso para garantir 100% de glicogênio e frescor neuromuscular.")
                }
                TafCountdownPhase.TAPERING_4_7D -> {
                    if (readinessLevel == LowerBodyReadinessLevel.ACUTE_FATIGUE || readinessLevel == LowerBodyReadinessLevel.RECOVERING_24_48H) {
                        append("Faltam $daysUntilRace dias para o ${resolvedTargetEvent.raceName} com estímulo recente de pernas (${hoursSinceLowerBody}h atrás): suspenda qualquer carga de membros inferiores hoje e realize apenas rodagem curta ou descanso ativo.")
                    } else {
                        append("Faltam $daysUntilRace dias para o ${resolvedTargetEvent.raceName} (Janela de Polimento): pernas recuperadas — execute estímulo curto no pace alvo (${resolvedTargetEvent.targetPace}) com volume reduzido em 40% e zero musculação de pernas.")
                    }
                }
                TafCountdownPhase.FINAL_SPECIFICITY_8_12D -> {
                    when (readinessLevel) {
                        LowerBodyReadinessLevel.ACUTE_FATIGUE ->
                            append("Faltam $daysUntilRace dias para o ${resolvedTargetEvent.raceName}, mas há fadiga aguda de pernas (${hoursSinceLowerBody}h atrás, ${latestLowerBodyStimulus?.lowerBodySets ?: 0} séries). Transfira tiros máximos para amanhã ou mantenha trote regenerativo hoje.")
                        LowerBodyReadinessLevel.RECOVERING_24_48H ->
                            append("Faltam $daysUntilRace dias para o ${resolvedTargetEvent.raceName} e as pernas estão em recuperação (${hoursSinceLowerBody}h pós-musculação). Mantenha os tiros no ritmo alvo do TAF sem forçar sprint final acima da meta, e reduza 20% das séries no próximo treino de pernas.")
                        LowerBodyReadinessLevel.RECOVERED_OVER_48H ->
                            append("Faltam $daysUntilRace dias para o ${resolvedTargetEvent.raceName} e os membros inferiores estão recuperados (>48h). Hoje é janela ideal para executar os tiros específicos no pace do TAF (${resolvedTargetEvent.targetPace}). Na academia, reduza 20% do volume de séries de pernas.")
                    }
                }
                TafCountdownPhase.RACE_DAY -> {
                    append("Hoje é o dia do ${resolvedTargetEvent.raceName}! Hidratação leve, aquecimento progressivo e controle de ritmo desde os primeiros 400m.")
                }
                TafCountdownPhase.BASE_BUILDING -> {
                    when (readinessLevel) {
                        LowerBodyReadinessLevel.ACUTE_FATIGUE ->
                            append("Fadiga aguda de pernas (${hoursSinceLowerBody}h pós-treino com ${latestLowerBodyStimulus?.lowerBodySets ?: 0} séries): priorize corrida regenerativa/leve hoje e evite tiros máximos.")
                        LowerBodyReadinessLevel.RECOVERING_24_48H ->
                            append("Pernas em janela de recuperação (24–48h): execute o treino prescrito com atenção à cadência alta (passada curta) para poupar panturrilhas e posteriores.")
                        LowerBodyReadinessLevel.RECOVERED_OVER_48H ->
                            append("Membros inferiores 100% recuperados (>48h): janela excelente para sessão de qualidade/ritmo na corrida.")
                    }
                }
            }
        }

        return HybridLoadCoachSummary(
            runningWeeklyLoadUa = runningWeeklyLoadUa,
            gymWeeklyLoadUa = gymWeeklyLoadUa,
            totalHybridLoadUa = totalHybridLoadUa,
            runningSharePct = runningSharePct,
            gymSharePct = gymSharePct,
            runningSessionsCount7d = runs7d.size,
            gymSessionsCount7d = gyms7d.size,
            running14dLoadUa = running14dLoadUa,
            gym14dLoadUa = gym14dLoadUa,
            total14dLoadUa = total14dLoadUa,
            hoursSinceLastLowerBodyGym = hoursSinceLowerBody,
            lastLowerBodyWorkoutName = latestLowerBodyStimulus?.workoutName,
            lastLowerBodySetsCount = latestLowerBodyStimulus?.lowerBodySets ?: 0,
            lowerBodyReadinessLevel = readinessLevel,
            lowerBodyReadinessStatusText = readinessStatusText,
            localizedPainImpactNote = localizedPainImpactNote,
            targetRaceEvent = resolvedTargetEvent,
            daysUntilTargetRace = daysUntilRace,
            tafPhase = tafPhase,
            tafProtocolText = tafProtocolText,
            todayCoachDirective = todayDirective
        )
    }
}
