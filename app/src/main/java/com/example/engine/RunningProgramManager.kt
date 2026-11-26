package com.example.engine

import java.io.Serializable
import com.example.data.model.RunningSession
import com.example.data.model.calculateEffectiveMaxHr
import com.example.data.model.calculateSprintAnalytics
import com.example.data.model.customRaceName
import com.example.data.model.isClassifiedAsRace
import com.example.data.model.isSimulatedOrTestSession
import com.example.data.model.toRecordedKmSplits

enum class RunningBlockType : Serializable {
    WARMUP,
    RUN,
    WALK,
    COOL_DOWN
}

/**
 * Modos de Corrida Livre e Prova Oficial (5K • 10K • Livre) do módulo MAX RUN.
 */
enum class FreeRunRaceMode(
    val sessionType: String,
    val pillLabel: String,
    val startBadgeLabel: String,
    val fullTitle: String,
    val hudHeaderLabel: String,
    val targetDistanceMeters: Int?,
    val subtitle: String
) : Serializable {
    FREE_RUN(
        sessionType = "FREE_RUN",
        pillLabel = "Livre",
        startBadgeLabel = "LIVRE",
        fullTitle = "Corrida Livre",
        hudHeaderLabel = "LIVRE",
        targetDistanceMeters = null,
        subtitle = "Distância Livre"
    ),
    RACE_5K(
        sessionType = "RACE_5K",
        pillLabel = "5K",
        startBadgeLabel = "5K",
        fullTitle = "5K",
        hudHeaderLabel = "5K • 5,00 KM",
        targetDistanceMeters = 5000,
        subtitle = "5,00 km"
    ),
    RACE_10K(
        sessionType = "RACE_10K",
        pillLabel = "10K",
        startBadgeLabel = "10K",
        fullTitle = "10K",
        hudHeaderLabel = "10K • 10,00 KM",
        targetDistanceMeters = 10000,
        subtitle = "10,00 km"
    );

    companion object {
        fun fromSessionType(sessionType: String?): FreeRunRaceMode? {
            if (sessionType.isNullOrBlank()) return null
            return entries.firstOrNull { it.sessionType.equals(sessionType.trim(), ignoreCase = true) }
        }
    }
}

data class RunningBlock(
    val type: RunningBlockType,
    val durationSec: Int,
    val label: String,
    val minPaceSecPerKm: Int? = if (type == RunningBlockType.RUN) 330 else null,
    val maxPaceSecPerKm: Int? = if (type == RunningBlockType.RUN) 420 else null
) : Serializable

data class RunningWorkoutPlan(
    val weekNumber: Int,
    val sessionType: String, // "VOLUME", "CONTINUOUS", "SPECIFICITY"
    val title: String,
    val description: String,
    val blocks: List<RunningBlock>
) : Serializable {
    val totalDurationSec: Int get() = blocks.sumOf { it.durationSec }
}

data class RunningProgress(
    val weekNumber: Int,
    val dayNumber: Int
) : Serializable

data class RunningProgramCatalogItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val badge: String,
    val durationLabel: String
) : Serializable

object RunningProgramManager {

    val ALL_RUNNING_PROGRAMS: List<RunningProgramCatalogItem> = listOf(
        RunningProgramCatalogItem(
            id = "run_30m",
            title = "Corra 30 Minutos",
            subtitle = "Progressão contínua de base aeróbica (10 semanas)",
            badge = "BASE AERÓBICA",
            durationLabel = "25–40 min"
        ),
        RunningProgramCatalogItem(
            id = "road_to_5k",
            title = "Rumo aos 5K",
            subtitle = "Iniciante/Intermediário — evolução estruturada até 5 km contínuos",
            badge = "META 5K",
            durationLabel = "30–40 min"
        ),
        RunningProgramCatalogItem(
            id = "couch_to_10k",
            title = "Couch to 10K",
            subtitle = "Progressão de resistência e volume aeróbico para 10 km",
            badge = "RESISTÊNCIA 10K",
            durationLabel = "40–60 min"
        ),
        RunningProgramCatalogItem(
            id = "tiros_10x400m",
            title = "Tiros 10 × 400m (Ritmo 5K)",
            subtitle = "10 repetições de 400m com 90s trote regenerativo — foco em fechar o 10º tiro igual ao 1º",
            badge = "SUSTENTAÇÃO DE PACE • 400M",
            durationLabel = "10×400m • 90s Rec"
        ),
        RunningProgramCatalogItem(
            id = "interval_4x4min",
            title = "Intervalado 4 × 4 min (VO2máx Controlado)",
            subtitle = "4 blocos de 4 min fortes (Esforço 8/10 • Z4 Alta/Z5: 167–175 bpm) + 2 min trote leve",
            badge = "VO2MÁX • 4×4 MIN",
            durationLabel = "34–38 min"
        ),
        RunningProgramCatalogItem(
            id = "threshold_3x8min",
            title = "Ritmo Fracionado 3 × 8 min (Limiar Sustentado)",
            subtitle = "3 blocos de 8 min firmes (Esforço 6–7/10 • Z3 Alta/Z4 Baixa: 156–165 bpm) + 2 min trote",
            badge = "LIMIAR • 3×8 MIN",
            durationLabel = "38–42 min"
        ),
        RunningProgramCatalogItem(
            id = "fartlek_intervals",
            title = "Intervalado de Pista 400m / Fartlek",
            subtitle = "Laps de 400m na pista com descanso controlado e disparo manual na largada",
            badge = "PISTA 400M / FARTLEK",
            durationLabel = "400m • Laps"
        ),
        RunningProgramCatalogItem(
            id = "run_to_maintain",
            title = "Run to Maintain (Manutenção Híbrida)",
            subtitle = "Plano equilibrado sem pressão de recordes (Rodagem Z2 + Estímulo Curto + Longo Confortável) integrado ao MAX GYM",
            badge = "MANUTENÇÃO • HÍBRIDO",
            durationLabel = "28–45 min"
        ),
        RunningProgramCatalogItem(
            id = "free_regenerative",
            title = "Rodagem Livre / Regenerativo",
            subtitle = "Corrida leve contínua em Zona 2 para recuperação ativa e soltura",
            badge = "ZONA 2 / LIVRE",
            durationLabel = "25–35 min"
        )
    )

    fun getProgramById(programId: String): RunningProgramCatalogItem {
        return ALL_RUNNING_PROGRAMS.find { it.id == programId } ?: ALL_RUNNING_PROGRAMS.first()
    }

    private val ptBrLocale = java.util.Locale("pt", "BR")

    fun formatDurationMin(durationSec: Int): String {
        val mins = durationSec / 60f
        return if (mins % 1f == 0f) {
            "${mins.toInt()}"
        } else {
            String.format(ptBrLocale, "%.1f", mins)
        }
    }

    /**
     * Formata os blocos principais (RUN / WALK) na notação de pirâmide/ondas estilo Run Tracker:
     * Ex.: "1,5R + 1,5W + 2x (2,5R + 2,5W) + 1,5R + 1,5W"
     * Ex.: "2R + 2W + 2x (3R + 3W) + 2R + 2W"
     * Ex.: "5R + 3W + 6R + 3W + 5R"
     */
    fun formatMiddleBlocksSummary(blocks: List<RunningBlock>): String {
        val middle = blocks.filter { it.type == RunningBlockType.RUN || it.type == RunningBlockType.WALK }
        if (middle.isEmpty()) return ""
        if (middle.size == 1) {
            val only = middle.first()
            val m = formatDurationMin(only.durationSec)
            return if (only.type == RunningBlockType.RUN) "${m}m Corrida Contínua" else "${m}m Caminhada Suave"
        }

        val rawTokens = mutableListOf<String>()
        var i = 0
        while (i < middle.size) {
            val cur = middle[i]
            val next = middle.getOrNull(i + 1)
            if (cur.type == RunningBlockType.RUN && next != null && next.type == RunningBlockType.WALK) {
                rawTokens.add("${formatDurationMin(cur.durationSec)}R + ${formatDurationMin(next.durationSec)}W")
                i += 2
            } else if (cur.type == RunningBlockType.RUN) {
                rawTokens.add("${formatDurationMin(cur.durationSec)}R")
                i += 1
            } else {
                rawTokens.add("${formatDurationMin(cur.durationSec)}W")
                i += 1
            }
        }

        val compressed = mutableListOf<String>()
        var idx = 0
        while (idx < rawTokens.size) {
            val token = rawTokens[idx]
            var count = 1
            while (idx + count < rawTokens.size && rawTokens[idx + count] == token) {
                count++
            }
            if (count > 1) {
                compressed.add("${count}x ($token)")
            } else {
                compressed.add(token)
            }
            idx += count
        }

        return compressed.joinToString(" + ")
    }

    private fun buildStructuredPlan(
        weekNumber: Int,
        sessionType: String,
        title: String,
        middleSteps: List<Pair<RunningBlockType, Float>>,
        runLabel: String = "Correr",
        walkLabel: String = "Caminhar",
        minPaceSecPerKm: Int? = 330,
        maxPaceSecPerKm: Int? = 420
    ): RunningWorkoutPlan {
        val blocks = buildList {
            add(RunningBlock(RunningBlockType.WARMUP, 300, "Aquecimento"))
            for ((type, mins) in middleSteps) {
                val sec = (mins * 60f).toInt()
                if (type == RunningBlockType.RUN) {
                    add(
                        RunningBlock(
                            type = RunningBlockType.RUN,
                            durationSec = sec,
                            label = runLabel,
                            minPaceSecPerKm = minPaceSecPerKm,
                            maxPaceSecPerKm = maxPaceSecPerKm
                        )
                    )
                } else {
                    add(RunningBlock(RunningBlockType.WALK, sec, walkLabel))
                }
            }
            add(RunningBlock(RunningBlockType.COOL_DOWN, 300, "Desaquecimento"))
        }
        val summary = formatMiddleBlocksSummary(blocks)
        return RunningWorkoutPlan(
            weekNumber = weekNumber,
            sessionType = sessionType,
            title = title,
            description = "Aquecimento 5min + $summary + Desaquecimento 5min",
            blocks = blocks
        )
    }

    private fun r(mins: Number): Pair<RunningBlockType, Float> = RunningBlockType.RUN to mins.toFloat()
    private fun w(mins: Number): Pair<RunningBlockType, Float> = RunningBlockType.WALK to mins.toFloat()

    fun getPlanForProgram(programId: String, week: Int, day: Int = 1): RunningWorkoutPlan {
        val wk = week.coerceIn(1, 10)
        val dy = day.coerceIn(1, 3)
        return when (programId) {
            "road_to_5k" -> {
                val steps: List<Pair<RunningBlockType, Float>> = when (wk) {
                    1 -> when (dy) {
                        1 -> listOf(r(2), w(1.5f), r(3), w(1.5f), r(3), w(1.5f), r(2), w(1.5f))
                        2 -> listOf(r(2.5f), w(1.5f), r(3.5f), w(1.5f), r(3.5f), w(1.5f), r(2.5f), w(1.5f))
                        else -> listOf(r(3), w(1.5f), r(4), w(2), r(4), w(2), r(3), w(1.5f))
                    }
                    2 -> when (dy) {
                        1 -> listOf(r(3), w(1.5f), r(4.5f), w(2), r(4.5f), w(2), r(3), w(1.5f))
                        2 -> listOf(r(4), w(2), r(5), w(2), r(5), w(2), r(4))
                        else -> listOf(r(4.5f), w(2), r(6), w(2), r(4.5f))
                    }
                    3 -> when (dy) {
                        1 -> listOf(r(5), w(2), r(6), w(2), r(5))
                        2 -> listOf(r(5), w(2), r(7), w(2), r(5))
                        else -> listOf(r(6), w(2), r(7), w(2), r(6))
                    }
                    4 -> when (dy) {
                        1 -> listOf(r(6), w(2), r(8), w(2), r(6))
                        2 -> listOf(r(7), w(2), r(8), w(2), r(7))
                        else -> listOf(r(8), w(2.5f), r(10), w(2.5f), r(6))
                    }
                    5 -> when (dy) {
                        1 -> listOf(r(8), w(2), r(10), w(2), r(8))
                        2 -> listOf(r(10), w(2.5f), r(12), w(2.5f), r(6))
                        else -> listOf(r(12), w(2.5f), r(12))
                    }
                    6 -> when (dy) {
                        1 -> listOf(r(10), w(2), r(12), w(2), r(8))
                        2 -> listOf(r(14), w(2.5f), r(12))
                        else -> listOf(r(22))
                    }
                    7 -> when (dy) {
                        1 -> listOf(r(14), w(2), r(14))
                        2 -> listOf(r(16), w(2), r(12))
                        else -> listOf(r(25))
                    }
                    8 -> when (dy) {
                        1 -> listOf(r(16), w(2), r(14))
                        2 -> listOf(r(26))
                        else -> listOf(r(28))
                    }
                    9 -> when (dy) {
                        1 -> listOf(r(18), w(1.5f), r(12))
                        2 -> listOf(r(28))
                        else -> listOf(r(30))
                    }
                    else -> when (dy) {
                        1 -> listOf(r(15), w(1.5f), r(12))
                        2 -> listOf(r(25))
                        else -> listOf(r(32))
                    }
                }
                buildStructuredPlan(
                    weekNumber = wk,
                    sessionType = "ROAD_TO_5K",
                    title = "Rumo aos 5K • Semana $wk Dia $dy",
                    middleSteps = steps,
                    runLabel = "Ritmo 5K",
                    walkLabel = "Recuperação"
                )
            }
            "couch_to_10k" -> {
                val steps: List<Pair<RunningBlockType, Float>> = when (wk) {
                    1 -> when (dy) {
                        1 -> listOf(r(8), w(2), r(10), w(2), r(8))
                        2 -> listOf(r(9), w(2), r(11), w(2), r(9))
                        else -> listOf(r(10), w(2), r(12), w(2), r(10))
                    }
                    2 -> when (dy) {
                        1 -> listOf(r(10), w(2), r(14), w(2), r(10))
                        2 -> listOf(r(12), w(2), r(14), w(2), r(10))
                        else -> listOf(r(12), w(2), r(15), w(2), r(12))
                    }
                    3 -> when (dy) {
                        1 -> listOf(r(12), w(2), r(16), w(2), r(12))
                        2 -> listOf(r(14), w(2), r(16), w(2), r(12))
                        else -> listOf(r(15), w(2), r(18), w(2), r(12))
                    }
                    4 -> when (dy) {
                        1 -> listOf(r(15), w(2), r(18), w(2), r(15))
                        2 -> listOf(r(18), w(2.5f), r(18), w(2.5f), r(10))
                        else -> listOf(r(20), w(2.5f), r(20))
                    }
                    5 -> when (dy) {
                        1 -> listOf(r(16), w(2), r(20), w(2), r(14))
                        2 -> listOf(r(22), w(2.5f), r(20))
                        else -> listOf(r(35))
                    }
                    6 -> when (dy) {
                        1 -> listOf(r(20), w(2), r(22))
                        2 -> listOf(r(25), w(2), r(20))
                        else -> listOf(r(40))
                    }
                    7 -> when (dy) {
                        1 -> listOf(r(22), w(2), r(22))
                        2 -> listOf(r(28), w(2), r(18))
                        else -> listOf(r(45))
                    }
                    8 -> when (dy) {
                        1 -> listOf(r(25), w(2), r(25))
                        2 -> listOf(r(45))
                        else -> listOf(r(50))
                    }
                    9 -> when (dy) {
                        1 -> listOf(r(28), w(2), r(22))
                        2 -> listOf(r(50))
                        else -> listOf(r(55))
                    }
                    else -> when (dy) {
                        1 -> listOf(r(25), w(2), r(20))
                        2 -> listOf(r(40))
                        else -> listOf(r(60))
                    }
                }
                buildStructuredPlan(
                    weekNumber = wk,
                    sessionType = "COUCH_TO_10K",
                    title = "Couch to 10K • Semana $wk Dia $dy",
                    middleSteps = steps,
                    runLabel = "Bloco Endurance 10K",
                    walkLabel = "Hidratação / Caminhada"
                )
            }
            "fartlek_intervals" -> {
                val steps: List<Pair<RunningBlockType, Float>> = when (dy) {
                    1 -> listOf(
                        r(1), w(1),
                        r(2), w(1.5f),
                        r(3), w(2),
                        r(2), w(1.5f),
                        r(1), w(1)
                    )
                    2 -> listOf(
                        r(1), w(1),
                        r(1.5f), w(1.5f),
                        r(2.5f), w(1.5f),
                        r(2.5f), w(1.5f),
                        r(1.5f), w(1.5f),
                        r(1), w(1)
                    )
                    else -> listOf(
                        r(1.5f), w(1),
                        r(2), w(1.5f),
                        r(3), w(1.5f),
                        r(3), w(1.5f),
                        r(2), w(1.5f),
                        r(1.5f), w(1)
                    )
                }
                buildStructuredPlan(
                    weekNumber = wk,
                    sessionType = "FARTLEK",
                    title = "Tiros / Fartlek • Semana $wk Dia $dy",
                    middleSteps = steps,
                    runLabel = "Tiro Forte",
                    walkLabel = "Trote / Recuperação",
                    minPaceSecPerKm = 270,
                    maxPaceSecPerKm = 345
                )
            }
            "tiros_10x400m" -> {
                getTrack400mPlan(
                    weekNumber = wk,
                    targetLaps = 10,
                    suggestedRestSec = 90,
                    customTitle = "Tiros 10 × 400m (Ritmo 5K)",
                    customSessionType = "TRACK_10X400M"
                )
            }
            "interval_4x4min" -> {
                val steps = listOf(
                    r(4), w(2),
                    r(4), w(2),
                    r(4), w(2),
                    r(4)
                )
                RunningWorkoutPlan(
                    weekNumber = wk,
                    sessionType = "INTERVAL_4X4MIN",
                    title = "Intervalado 4 × 4 min (VO2máx Controlado)",
                    description = "Aquecimento 5min + 4x (4min Forte Z4/Z5 [167–175 bpm] + 2min Trote Leve) + Desaquecimento 5min",
                    blocks = buildList {
                        add(RunningBlock(RunningBlockType.WARMUP, 300, "Aquecimento Leve"))
                        for ((idx, pair) in steps.withIndex()) {
                            val (type, mins) = pair
                            val sec = (mins * 60f).toInt()
                            if (type == RunningBlockType.RUN) {
                                val repNum = (idx / 2) + 1
                                add(
                                    RunningBlock(
                                        type = RunningBlockType.RUN,
                                        durationSec = sec,
                                        label = "Bloco Forte $repNum/4 (Esforço 8/10 • 167–175 bpm)",
                                        minPaceSecPerKm = 300,
                                        maxPaceSecPerKm = 360
                                    )
                                )
                            } else {
                                add(RunningBlock(RunningBlockType.WALK, sec, "Trote Regenerativo (2 min)"))
                            }
                        }
                        add(RunningBlock(RunningBlockType.COOL_DOWN, 300, "Desaquecimento 5min"))
                    }
                )
            }
            "threshold_3x8min" -> {
                val steps = listOf(
                    r(8), w(2),
                    r(8), w(2),
                    r(8)
                )
                RunningWorkoutPlan(
                    weekNumber = wk,
                    sessionType = "THRESHOLD_3X8MIN",
                    title = "Ritmo Fracionado 3 × 8 min (Limiar Sustentado)",
                    description = "Aquecimento 5min + 3x (8min Limiar Firme Z3/Z4 [156–165 bpm] + 2min Trote) + Desaquecimento 5min",
                    blocks = buildList {
                        add(RunningBlock(RunningBlockType.WARMUP, 300, "Aquecimento Leve"))
                        for ((idx, pair) in steps.withIndex()) {
                            val (type, mins) = pair
                            val sec = (mins * 60f).toInt()
                            if (type == RunningBlockType.RUN) {
                                val repNum = (idx / 2) + 1
                                add(
                                    RunningBlock(
                                        type = RunningBlockType.RUN,
                                        durationSec = sec,
                                        label = "Limiar Sustentado $repNum/3 (Esforço 6-7/10 • 156–165 bpm)",
                                        minPaceSecPerKm = 330,
                                        maxPaceSecPerKm = 385
                                    )
                                )
                            } else {
                                add(RunningBlock(RunningBlockType.WALK, sec, "Trote Leve (2 min)"))
                            }
                        }
                        add(RunningBlock(RunningBlockType.COOL_DOWN, 300, "Desaquecimento 5min"))
                    }
                )
            }
            "run_to_maintain" -> {
                when (dy) {
                    1 -> RunningWorkoutPlan(
                        weekNumber = wk,
                        sessionType = "RUN_TO_MAINTAIN",
                        title = "Run to Maintain • Dia 1: Rodagem Leve Z2",
                        description = "Aquecimento 4min + 22m Rodagem Confortável Z2 (Zero Fadiga p/ MAX GYM) + Desaquecimento 4min",
                        blocks = listOf(
                            RunningBlock(RunningBlockType.WARMUP, 240, "Aquecimento Solto (4 min)"),
                            RunningBlock(RunningBlockType.RUN, 1320, "Rodagem Confortável Z2 (22 min)", minPaceSecPerKm = 345, maxPaceSecPerKm = 420),
                            RunningBlock(RunningBlockType.COOL_DOWN, 240, "Desaquecimento Leve (4 min)")
                        )
                    )
                    2 -> buildStructuredPlan(
                        weekNumber = wk,
                        sessionType = "RUN_TO_MAINTAIN",
                        title = "Run to Maintain • Dia 2: Estímulo Neuromotor 6×1'",
                        middleSteps = listOf(
                            r(1), w(1.5f),
                            r(1), w(1.5f),
                            r(1), w(1.5f),
                            r(1), w(1.5f),
                            r(1), w(1.5f),
                            r(1), w(1.5f)
                        ),
                        runLabel = "Estímulo 1 min Z4",
                        walkLabel = "Trote Leve Z1 (1'30\")",
                        minPaceSecPerKm = 290,
                        maxPaceSecPerKm = 345
                    )
                    else -> RunningWorkoutPlan(
                        weekNumber = wk,
                        sessionType = "RUN_TO_MAINTAIN",
                        title = "Run to Maintain • Dia 3: Longo Confortável 45 min",
                        description = "Aquecimento 5min + 35m Longo Confortável Z2 + Desaquecimento 5min",
                        blocks = listOf(
                            RunningBlock(RunningBlockType.WARMUP, 300, "Aquecimento Leve (5 min)"),
                            RunningBlock(RunningBlockType.RUN, 2100, "Longo Confortável Z2 (35 min)", minPaceSecPerKm = 350, maxPaceSecPerKm = 430),
                            RunningBlock(RunningBlockType.COOL_DOWN, 300, "Desaquecimento (5 min)")
                        )
                    )
                }
            }
            "free_regenerative" -> {
                val continuousMins = (20 + dy * 5).coerceIn(20, 35)
                RunningWorkoutPlan(
                    weekNumber = wk,
                    sessionType = "FREE_REGENERATIVE",
                    title = "Rodagem Livre / Regenerativo • ${continuousMins}min",
                    description = "Aquecimento 5min + ${continuousMins}m Corrida Contínua (Zona 2) + Desaquecimento 5min",
                    blocks = listOf(
                        RunningBlock(RunningBlockType.WARMUP, 300, "Aquecimento Leve"),
                        RunningBlock(RunningBlockType.RUN, continuousMins * 60, "Rodagem Livre Z2", minPaceSecPerKm = 360, maxPaceSecPerKm = 460),
                        RunningBlock(RunningBlockType.COOL_DOWN, 300, "Desaquecimento")
                    )
                )
            }
            else -> getPlanForWeek(wk, dy)
        }
    }

    /**
     * Matriz de 10 semanas para o programa "Corra 30 Minutos" com progressão suave em pirâmide/ondas
     * inspirada na fisiologia progressiva do Run Tracker (sem saltos bruscos ou blocos engessados).
     */
    fun getPlanForWeek(week: Int, day: Int = 1): RunningWorkoutPlan {
        val wk = week.coerceIn(1, 10)
        val dy = day.coerceIn(1, 3)
        return when (wk) {
            1 -> when (dy) {
                1 -> buildStructuredPlan(
                    weekNumber = 1,
                    sessionType = "VOLUME",
                    title = "Semana 1 • Dia 1: Adaptação Inicial",
                    middleSteps = listOf(
                        r(1), w(1.5f),
                        r(1), w(1.5f),
                        r(1), w(1.5f),
                        r(1), w(1.5f),
                        r(1), w(1.5f),
                        r(1), w(1.5f)
                    )
                )
                2 -> buildStructuredPlan(
                    weekNumber = 1,
                    sessionType = "VOLUME",
                    title = "Semana 1 • Dia 2: Pirâmide Inicial",
                    middleSteps = listOf(
                        r(1), w(1.5f),
                        r(1.5f), w(1.5f),
                        r(1.5f), w(1.5f),
                        r(1), w(1.5f)
                    )
                )
                else -> buildStructuredPlan(
                    weekNumber = 1,
                    sessionType = "VOLUME",
                    title = "Semana 1 • Dia 3: Onda de Continuidade",
                    middleSteps = listOf(
                        r(1), w(1.5f),
                        r(2), w(1.5f),
                        r(2), w(1.5f),
                        r(1), w(1.5f)
                    )
                )
            }
            2 -> when (dy) {
                1 -> buildStructuredPlan(
                    weekNumber = 2,
                    sessionType = "VOLUME",
                    title = "Semana 2 • Dia 1: Consolidação em Pirâmide",
                    middleSteps = listOf(
                        r(1.5f), w(1.5f),
                        r(2), w(2),
                        r(2), w(2),
                        r(1.5f), w(1.5f)
                    )
                )
                2 -> buildStructuredPlan(
                    weekNumber = 2,
                    sessionType = "VOLUME",
                    title = "Semana 2 • Dia 2: Expansão de Intervalos",
                    middleSteps = listOf(
                        r(1.5f), w(1.5f),
                        r(2.5f), w(2),
                        r(2.5f), w(2),
                        r(1.5f), w(1.5f)
                    )
                )
                else -> buildStructuredPlan(
                    weekNumber = 2,
                    sessionType = "VOLUME",
                    title = "Semana 2 • Dia 3: Pirâmide de Resistência",
                    // 1,5R + 1,5W + 2x (2,5R + 2,5W) + 1,5R + 1,5W
                    middleSteps = listOf(
                        r(1.5f), w(1.5f),
                        r(2.5f), w(2.5f),
                        r(2.5f), w(2.5f),
                        r(1.5f), w(1.5f)
                    )
                )
            }
            3 -> when (dy) {
                1 -> buildStructuredPlan(
                    weekNumber = 3,
                    sessionType = "VOLUME",
                    title = "Semana 3 • Dia 1: Estímulo Moderado",
                    // 1,5R + 1,5W + 2x (2,5R + 2,5W) + 1,5R + 1,5W
                    middleSteps = listOf(
                        r(1.5f), w(1.5f),
                        r(2.5f), w(2.5f),
                        r(2.5f), w(2.5f),
                        r(1.5f), w(1.5f)
                    )
                )
                2 -> buildStructuredPlan(
                    weekNumber = 3,
                    sessionType = "VOLUME",
                    title = "Semana 3 • Dia 2: Pirâmide Progressiva",
                    // 1,5R + 1,5W + 2x (3R + 2,5W) + 1,5R + 1,5W
                    middleSteps = listOf(
                        r(1.5f), w(1.5f),
                        r(3), w(2.5f),
                        r(3), w(2.5f),
                        r(1.5f), w(1.5f)
                    )
                )
                else -> buildStructuredPlan(
                    weekNumber = 3,
                    sessionType = "VOLUME",
                    title = "Semana 3 • Dia 3: Pirâmide de 3 Minutos",
                    // W3D3: 2R + 2W + 2x (3R + 3W) + 2R + 2W
                    middleSteps = listOf(
                        r(2), w(2),
                        r(3), w(3),
                        r(3), w(3),
                        r(2), w(2)
                    )
                )
            }
            4 -> when (dy) {
                1 -> buildStructuredPlan(
                    weekNumber = 4,
                    sessionType = "CONTINUOUS",
                    title = "Semana 4 • Dia 1: Transição em Onda",
                    // 3R + 2W + 4R + 2,5W + 3R + 2W
                    middleSteps = listOf(
                        r(3), w(2),
                        r(4), w(2.5f),
                        r(3), w(2)
                    )
                )
                2 -> buildStructuredPlan(
                    weekNumber = 4,
                    sessionType = "CONTINUOUS",
                    title = "Semana 4 • Dia 2: Ritmo Sustentado",
                    // 3R + 2W + 2x (4,5R + 2,5W) + 3R
                    middleSteps = listOf(
                        r(3), w(2),
                        r(4.5f), w(2.5f),
                        r(4.5f), w(2.5f),
                        r(3)
                    )
                )
                else -> buildStructuredPlan(
                    weekNumber = 4,
                    sessionType = "CONTINUOUS",
                    title = "Semana 4 • Dia 3: Pirâmide de 5 Minutos",
                    // 4R + 2,5W + 5R + 2,5W + 4R
                    middleSteps = listOf(
                        r(4), w(2.5f),
                        r(5), w(2.5f),
                        r(4)
                    )
                )
            }
            5 -> when (dy) {
                1 -> buildStructuredPlan(
                    weekNumber = 5,
                    sessionType = "CONTINUOUS",
                    title = "Semana 5 • Dia 1: Pirâmide 5-6-5 Minutos",
                    // W5D1: 5R + 3W + 6R + 3W + 5R
                    middleSteps = listOf(
                        r(5), w(3),
                        r(6), w(3),
                        r(5)
                    )
                )
                2 -> buildStructuredPlan(
                    weekNumber = 5,
                    sessionType = "CONTINUOUS",
                    title = "Semana 5 • Dia 2: Pirâmide 6-7-6 Minutos",
                    // 6R + 3W + 7R + 3W + 6R
                    middleSteps = listOf(
                        r(6), w(3),
                        r(7), w(3),
                        r(6)
                    )
                )
                else -> buildStructuredPlan(
                    weekNumber = 5,
                    sessionType = "CONTINUOUS",
                    title = "Semana 5 • Dia 3: Pirâmide 7-8-7 Minutos",
                    // 7R + 3W + 8R + 3W + 7R
                    middleSteps = listOf(
                        r(7), w(3),
                        r(8), w(3),
                        r(7)
                    )
                )
            }
            6 -> when (dy) {
                1 -> buildStructuredPlan(
                    weekNumber = 6,
                    sessionType = "CONTINUOUS",
                    title = "Semana 6 • Dia 1: Intervalos Longos em Pirâmide",
                    // 8R + 3W + 10R + 3W + 6R
                    middleSteps = listOf(
                        r(8), w(3),
                        r(10), w(3),
                        r(6)
                    )
                )
                2 -> buildStructuredPlan(
                    weekNumber = 6,
                    sessionType = "CONTINUOUS",
                    title = "Semana 6 • Dia 2: Blocos de 10 e 5 Minutos",
                    // 2x (10R + 3W) + 5R
                    middleSteps = listOf(
                        r(10), w(3),
                        r(10), w(3),
                        r(5)
                    )
                )
                else -> buildStructuredPlan(
                    weekNumber = 6,
                    sessionType = "CONTINUOUS",
                    title = "Semana 6 • Dia 3: Duplo Bloco de 12 Minutos",
                    // 12R + 3W + 12R
                    middleSteps = listOf(
                        r(12), w(3),
                        r(12)
                    )
                )
            }
            7 -> when (dy) {
                1 -> buildStructuredPlan(
                    weekNumber = 7,
                    sessionType = "CONTINUOUS",
                    title = "Semana 7 • Dia 1: Escada Decrescente",
                    // 12R + 2,5W + 10R + 2,5W + 5R
                    middleSteps = listOf(
                        r(12), w(2.5f),
                        r(10), w(2.5f),
                        r(5)
                    )
                )
                2 -> buildStructuredPlan(
                    weekNumber = 7,
                    sessionType = "CONTINUOUS",
                    title = "Semana 7 • Dia 2: Blocos de 15 e 10 Minutos",
                    // 15R + 3W + 10R
                    middleSteps = listOf(
                        r(15), w(3),
                        r(10)
                    )
                )
                else -> buildStructuredPlan(
                    weekNumber = 7,
                    sessionType = "CONTINUOUS",
                    title = "Semana 7 • Dia 3: O Marco dos 20 Minutos",
                    middleSteps = listOf(r(20)),
                    runLabel = "Correr Contínuo"
                )
            }
            8 -> when (dy) {
                1 -> buildStructuredPlan(
                    weekNumber = 8,
                    sessionType = "CONTINUOUS",
                    title = "Semana 8 • Dia 1: Blocos Fortes 14 e 12 Minutos",
                    // 14R + 2W + 12R
                    middleSteps = listOf(
                        r(14), w(2),
                        r(12)
                    )
                )
                2 -> buildStructuredPlan(
                    weekNumber = 8,
                    sessionType = "CONTINUOUS",
                    title = "Semana 8 • Dia 2: Corrida Contínua 22min",
                    middleSteps = listOf(r(22)),
                    runLabel = "Correr Contínuo"
                )
                else -> buildStructuredPlan(
                    weekNumber = 8,
                    sessionType = "CONTINUOUS",
                    title = "Semana 8 • Dia 3: Corrida de 25 Minutos",
                    middleSteps = listOf(r(25)),
                    runLabel = "Correr Contínuo"
                )
            }
            9 -> when (dy) {
                1 -> buildStructuredPlan(
                    weekNumber = 9,
                    sessionType = "SPECIFICITY",
                    title = "Semana 9 • Dia 1: Duplo Bloco de 15 Minutos",
                    // 15R + 2W + 15R
                    middleSteps = listOf(
                        r(15), w(2),
                        r(15)
                    )
                )
                2 -> buildStructuredPlan(
                    weekNumber = 9,
                    sessionType = "SPECIFICITY",
                    title = "Semana 9 • Dia 2: Ritmo Sustentado 27min",
                    middleSteps = listOf(r(27)),
                    runLabel = "Correr Contínuo"
                )
                else -> buildStructuredPlan(
                    weekNumber = 9,
                    sessionType = "SPECIFICITY",
                    title = "Semana 9 • Dia 3: Simulado de 28 Minutos",
                    middleSteps = listOf(r(28)),
                    runLabel = "Correr Contínuo"
                )
            }
            10 -> when (dy) {
                1 -> buildStructuredPlan(
                    weekNumber = 10,
                    sessionType = "SPECIFICITY",
                    title = "Semana 10 • Dia 1: Polimento 18 + 10 Minutos",
                    // 18R + 2W + 10R
                    middleSteps = listOf(
                        r(18), w(2),
                        r(10)
                    )
                )
                2 -> buildStructuredPlan(
                    weekNumber = 10,
                    sessionType = "SPECIFICITY",
                    title = "Semana 10 • Dia 2: Pré-Prova 25min",
                    middleSteps = listOf(r(25)),
                    runLabel = "Correr Solto"
                )
                else -> buildStructuredPlan(
                    weekNumber = 10,
                    sessionType = "SPECIFICITY",
                    title = "Semana 10 • Dia 3: Grande Conquista 30min",
                    middleSteps = listOf(r(30)),
                    runLabel = "Meta 30min Contínuos"
                )
            }
            else -> getPlanForWeek(1, 1)
        }
    }

    /**
     * Creates a regenerative 20-minute gentle walk plan for zero-pressure recovery.
     */
    fun getGentleWalkPlan(): RunningWorkoutPlan {
        return RunningWorkoutPlan(
            weekNumber = 0,
            sessionType = "GENTLE_WALK",
            title = "Caminhada Regenerativa • Modo Leve",
            description = "Caminhada contínua e suave de 20 minutos sem pressão. O importante é se mover no seu ritmo.",
            blocks = listOf(
                RunningBlock(RunningBlockType.WALK, 1200, "Caminhada Suave")
            )
        )
    }

    /**
     * Cria o plano contínuo para Corrida Livre / Prova 5K / Prova 10K.
     * Sem transições forçadas de caminhada/tiro e sem encerramento automático por tempo.
     */
    fun getFreeOrRacePlan(mode: FreeRunRaceMode): RunningWorkoutPlan {
        return RunningWorkoutPlan(
            weekNumber = 0,
            sessionType = mode.sessionType,
            title = mode.fullTitle,
            description = mode.subtitle,
            blocks = listOf(
                RunningBlock(
                    type = RunningBlockType.RUN,
                    durationSec = 36_000, // 10h de teto para nunca encerrar sozinho por tempo
                    label = mode.fullTitle,
                    minPaceSecPerKm = null,
                    maxPaceSecPerKm = null
                )
            )
        )
    }

    /**
     * Cria o plano para Intervalado de Pista 400m (Laps por Distância + Descanso com Disparo Manual).
     */
    fun getTrack400mPlan(
        weekNumber: Int = 1,
        targetLaps: Int = 6,
        suggestedRestSec: Int = 120,
        customTitle: String = "Intervalado de Pista 400m",
        customSessionType: String = "TRACK_400M"
    ): RunningWorkoutPlan {
        return RunningWorkoutPlan(
            weekNumber = weekNumber,
            sessionType = customSessionType,
            title = customTitle,
            description = "${targetLaps}x 400m • Descanso ${suggestedRestSec / 60}:${String.format(java.util.Locale.US, "%02d", suggestedRestSec % 60)} (Foco: 10º tiro igual ao 1º)",
            blocks = listOf(
                RunningBlock(
                    type = RunningBlockType.RUN,
                    durationSec = 36_000, // Controlado por distância (400m por Lap) + disparo manual
                    label = if (targetLaps == 10) "Tiro 400m (Ritmo 5K • Rec 90s)" else "Tiro de Pista 400m",
                    minPaceSecPerKm = 240,
                    maxPaceSecPerKm = 340
                )
            )
        )
    }

    /**
     * Aplica o Aquecimento Opcional com Acelerações ("10 min trote leve + 4×20s acelerações progressivas c/ 40s recuperação")
     * antes da parte principal do treino selecionado.
     */
    fun applyWarmupWithStrides(plan: RunningWorkoutPlan, includeWarmupStrides: Boolean): RunningWorkoutPlan {
        if (!includeWarmupStrides) return plan
        val warmupStridesBlocks = buildList {
            add(RunningBlock(RunningBlockType.WARMUP, 600, "Aquecimento Trote Leve (10 min)"))
            for (s in 1..4) {
                add(RunningBlock(RunningBlockType.RUN, 20, "Aceleração Progressiva $s/4 (20s)", minPaceSecPerKm = 250, maxPaceSecPerKm = 330))
                add(RunningBlock(RunningBlockType.WALK, 40, "Recuperação Solta $s/4 (40s)"))
            }
        }
        val coreBlocks = plan.blocks.dropWhile { it.type == RunningBlockType.WARMUP }
        return plan.copy(
            description = "Aquec. 10m + 4×20s Acelerações + ${plan.description.removePrefix("Aquecimento 5min + ")}",
            blocks = warmupStridesBlocks + coreBlocks
        )
    }

    fun isTrack400mSessionType(sessionType: String?): Boolean {
        return sessionType.equals("TRACK_400M", ignoreCase = true) ||
            sessionType.equals("TRACK_10X400M", ignoreCase = true) ||
            sessionType.equals("FARTLEK_400M", ignoreCase = true)
    }

    fun isFreeOrRaceSessionType(sessionType: String?): Boolean {
        return FreeRunRaceMode.fromSessionType(sessionType) != null
    }

    /**
     * Determina se o modal de perguntas pré-treino ("Prontidão Expressa" / anamnese) deve ser exibido antes de iniciar.
     * - Retorna `false` ("Instant GO") para Corrida Livre (`FREE_RUN`), Modos Prova (`RACE_5K`, `RACE_10K`),
     *   Rodagem Livre (`FREE_REGENERATIVE` / `free_regenerative`), planos sem semana estruturada (`weekNumber <= 0`,
     *   que exibem subtítulo "Corrida Livre") ou qualquer treino cujo título/identificador indique Corrida Livre / Livre / Prova 5K / 10K.
     * - Retorna `true` exclusivamente para treinos estruturados da planilha e demais sessões guiadas.
     */
    fun shouldShowPreRunAnamnesis(
        freeRaceMode: FreeRunRaceMode? = null,
        sessionType: String? = null,
        planTitle: String? = null,
        displayTitle: String? = null,
        weekNumber: Int? = null,
        programId: String? = null
    ): Boolean {
        if (freeRaceMode != null) return false
        if (isFreeOrRaceSessionType(sessionType)) return false

        val normalizedType = sessionType?.trim()?.uppercase(java.util.Locale.ROOT).orEmpty()
        if (normalizedType == "FREE_REGENERATIVE" || normalizedType.contains("FREE_RUN")) return false

        val normalizedProgramId = programId?.trim()?.lowercase(java.util.Locale.ROOT).orEmpty()
        if (normalizedProgramId == "free_regenerative" || normalizedProgramId.contains("free_run")) return false

        if (weekNumber != null && weekNumber <= 0 && normalizedType != "GENTLE_WALK") return false

        fun indicatesFreeOrRaceTitle(rawTitle: String?): Boolean {
            if (rawTitle.isNullOrBlank()) return false
            val t = rawTitle.trim().lowercase(java.util.Locale.ROOT)
            return t == "livre" ||
                t == "5k" ||
                t == "10k" ||
                t.contains("corrida livre") ||
                t.contains("rodagem livre") ||
                t.contains("prova 5k") ||
                t.contains("prova 10k") ||
                t.contains("meta 5k") ||
                t.contains("meta 10k") ||
                Regex("\\blivre\\b").containsMatchIn(t)
        }

        if (indicatesFreeOrRaceTitle(planTitle)) return false
        if (indicatesFreeOrRaceTitle(displayTitle)) return false

        return true
    }

    fun isEffectiveFreeRun(session: RunningSession): Boolean {
        if (session.isGentleMode || session.sessionType.equals("GENTLE_WALK", ignoreCase = true)) return false
        if (session.sessionType.equals("FREE_RUN", ignoreCase = true)) return true
        if (isTrack400mSessionType(session.sessionType) ||
            session.sessionType.equals("INTERVAL_4X4MIN", ignoreCase = true) ||
            session.sessionType.equals("THRESHOLD_3X8MIN", ignoreCase = true) ||
            session.sessionType.equals("RACE_5K", ignoreCase = true) ||
            session.sessionType.equals("RACE_10K", ignoreCase = true)
        ) return false
        if (session.weekNumber <= 0) return true
        if (session.distanceMeters in 1880..1900 && session.durationSec in 720..732) return true
        if (session.notes?.contains("Corrida Livre", ignoreCase = true) == true) return true
        return false
    }

    fun getDisplayTitleForSession(session: RunningSession): String {
        val customName = session.customRaceName()
        if (customName != null) return customName
        val st = session.sessionType
        val isExplicitTraining = session.advancedMetrics?.isRaceOverride == false
        return when {
            session.isGentleMode || st.equals("GENTLE_WALK", ignoreCase = true) -> "Caminhada / Trote Leve"
            st.startsWith("RX_", ignoreCase = true) -> "Treino ${st.removePrefix("RX_")}"
            st.equals("RUN_TO_MAINTAIN", ignoreCase = true) -> "Run to Maintain • D${session.dayNumber}"
            st.equals("TRACK_10X400M", ignoreCase = true) -> "Tiros 10×400m"
            st.equals("INTERVAL_4X4MIN", ignoreCase = true) -> "Intervalado 4×4 min"
            st.equals("THRESHOLD_3X8MIN", ignoreCase = true) -> "Ritmo Fracionado 3×8 min"
            isTrack400mSessionType(st) -> "Intervalado Pista 400m"
            st.equals("RACE_5K", ignoreCase = true) -> if (isExplicitTraining) "Treino 5K" else "Prova 5K"
            st.equals("RACE_10K", ignoreCase = true) -> if (isExplicitTraining) "Treino 10K" else "Prova 10K"
            isEffectiveFreeRun(session) -> "Corrida Livre"
            else -> getDisplayTitleForSessionType(
                sessionType = st,
                isGentleMode = session.isGentleMode,
                weekNumber = session.weekNumber,
                dayNumber = session.dayNumber
            )
        }
    }

    fun getBadgeLabelForSession(session: RunningSession): String {
        if (session.isClassifiedAsRace()) return "PROVA"
        val st = session.sessionType
        return when {
            session.isGentleMode || st.equals("GENTLE_WALK", ignoreCase = true) -> "LEVE"
            st.startsWith("RX_RTM", ignoreCase = true) || st.equals("RUN_TO_MAINTAIN", ignoreCase = true) -> "MANUT."
            st.startsWith("RX_", ignoreCase = true) -> "RX-PRO"
            isTrack400mSessionType(st) ||
                st.equals("INTERVAL_4X4MIN", ignoreCase = true) ||
                st.equals("THRESHOLD_3X8MIN", ignoreCase = true) -> "PISTA"
            isEffectiveFreeRun(session) -> "LIVRE"
            st.equals("RACE_10K", ignoreCase = true) ||
                session.notes?.contains("10K", ignoreCase = true) == true -> "10K"
            else -> "5K"
        }
    }

    fun getDisplayTitleForSessionType(
        sessionType: String?,
        isGentleMode: Boolean,
        weekNumber: Int,
        dayNumber: Int
    ): String {
        val freeRace = FreeRunRaceMode.fromSessionType(sessionType)
        return when {
            freeRace != null -> freeRace.fullTitle
            sessionType?.startsWith("RX_", ignoreCase = true) == true -> "Treino ${sessionType.removePrefix("RX_")}"
            sessionType.equals("RUN_TO_MAINTAIN", ignoreCase = true) -> "Run to Maintain • Sem $weekNumber Dia $dayNumber"
            sessionType.equals("TRACK_10X400M", ignoreCase = true) -> "Tiros 10×400m"
            sessionType.equals("INTERVAL_4X4MIN", ignoreCase = true) -> "Intervalado 4×4 min"
            sessionType.equals("THRESHOLD_3X8MIN", ignoreCase = true) -> "Ritmo Fracionado 3×8 min"
            isTrack400mSessionType(sessionType) -> "Intervalado Pista 400m"
            isGentleMode || sessionType == "GENTLE_WALK" -> "Caminhada / Trote Leve"
            weekNumber <= 0 -> "Corrida Livre"
            else -> "Semana $weekNumber · Dia $dayNumber"
        }
    }

    /**
     * Calculates calibrated target pace based on user's initial baseline pace from the onboarding.
     */
    fun calculateTargetPaceForWeek(week: Int, baselinePaceMinPerKm: Float?): Float {
        val base = baselinePaceMinPerKm ?: 7.5f
        val progressionOffset = when (week.coerceIn(1, 10)) {
            1 -> 0.4f
            2 -> 0.2f
            3 -> 0.0f
            4 -> -0.3f
            5 -> -0.5f
            6 -> -0.7f
            7 -> -0.9f
            8 -> -1.1f
            9 -> -1.3f
            10 -> -1.5f
            else -> 0.0f
        }
        return (base + progressionOffset).coerceAtLeast(4.0f)
    }

    /**
     * Determines current suggested progress (weekNumber and dayNumber) for the next session
     * based on completed running sessions history. Gentle Walk / Free Run / Race sessions are ignored.
     */
    fun calculateCurrentProgress(history: List<RunningSession>): RunningProgress {
        val validSessions = history.filter {
            it.weekNumber in 1..10 &&
            it.sessionType != "GENTLE_WALK" &&
            !isFreeOrRaceSessionType(it.sessionType) &&
            !isEffectiveFreeRun(it)
        }
        if (validSessions.isEmpty()) {
            return RunningProgress(1, 1)
        }
        val lastSession = validSessions.maxByOrNull { it.date } ?: return RunningProgress(1, 1)
        val n = lastSession.weekNumber.coerceIn(1, 10)
        val d = lastSession.dayNumber.coerceIn(1, 3)

        return when {
            d == 1 -> RunningProgress(n, 2)
            d == 2 -> RunningProgress(n, 3)
            d >= 3 -> {
                if (n < 10) {
                    RunningProgress(n + 1, 1)
                } else {
                    RunningProgress(10, 3)
                }
            }
            else -> RunningProgress(n, 1)
        }
    }

    /**
     * Formata pace em segundos/km para "M'SS\"/km".
     */
    fun formatPaceSecPerKm(paceSecPerKm: Int): String {
        val safeSec = paceSecPerKm.coerceIn(150, 999)
        val mins = safeSec / 60
        val secs = safeSec % 60
        return String.format(java.util.Locale.US, "%d'%02d\"/km", mins, secs)
    }

    /**
     * Formata pace em minutos decimais/km para "M'SS\"/km".
     */
    fun formatPaceMinPerKm(paceMinPerKm: Float?): String {
        val base = paceMinPerKm ?: 7.0f
        val totalSec = kotlin.math.round(base * 60f).toInt().coerceIn(150, 999)
        return formatPaceSecPerKm(totalSec)
    }

    /**
     * MOTOR DE CÁLCULO DE PACE REAL (CICLO DE 14 DIAS):
     * - Analisa as sessões concluídas nos últimos 14 dias (ou as últimas 5 sessões válidas com distância >= 1,5 km;
     *   com fallback para sessões reais >= 600m caso o atleta ainda não tenha atingido 1,5 km).
     * - Descarta trechos de caminhada/descanso (pace > 9'30"/km = 570 seg/km).
     * - Calcula o Pace Base Efetivo (5K equivalente) ponderado pela relação Pace vs. Frequência Cardíaca Média:
     *   * Se o BPM médio esteve controlado <= 85% da FCmáx, considera o ritmo consolidado (+0 seg/km).
     *   * Se esteve entre 86% e 90% da FCmáx, aplica ajuste moderado (+5 seg/km).
     *   * Se esteve > 90% da FCmáx, aplica fator de segurança conservador de +10 a +15 seg/km.
     */
    fun analyzeRealPaceCalibration(
        history: List<RunningSession>,
        userAge: Int?,
        currentBaselinePaceMinPerKm: Float? = 7.0f,
        lastCalibrationTimestamp: Long = 0L,
        calibrationMode: PaceCalibrationMode = PaceCalibrationMode.AUTO_14_DAYS,
        nowMillis: Long = System.currentTimeMillis()
    ): PaceCalibrationAnalysis {
        val currentPaceMin = (currentBaselinePaceMinPerKm ?: 7.0f).coerceIn(3.0f, 15.0f)
        val currentPaceSec = kotlin.math.round(currentPaceMin * 60f).toInt()

        val realHistory = history.filter { session ->
            !session.isSimulatedOrTestSession() &&
                session.sessionType != "GENTLE_WALK" &&
                !session.isGentleMode &&
                session.durationSec >= 120 &&
                session.distanceMeters >= 400
        }.sortedByDescending { it.date }

        val historicalPeakHr = realHistory.mapNotNull { it.maxHr }.maxOrNull()
        val effectiveMaxHr = calculateEffectiveMaxHr(
            ageYears = userAge,
            sessionMaxHrBpm = null,
            historicalPeakHrBpm = historicalPeakHr
        )

        val fourteenDaysMs = 14L * 24L * 3600L * 1000L
        val cutoff14d = nowMillis - fourteenDaysMs

        // Prioriza sessões dos últimos 14 dias com >= 1,5 km; complementa ou usa as últimas 5 sessões válidas >= 1,5 km
        val valid1500m = realHistory.filter { it.distanceMeters >= 1500 }
        val recent14d = valid1500m.filter { it.date >= cutoff14d }
        val candidateSessions = when {
            recent14d.size >= 2 -> recent14d.take(8)
            valid1500m.isNotEmpty() -> valid1500m.take(5)
            else -> realHistory.take(5) // Fallback caso o atleta só tenha treinos curtos salvos
        }

        data class ActiveRunSample(
            val session: RunningSession,
            val activeDistMeters: Int,
            val activeDurationSec: Int,
            val activePaceSecPerKm: Int,
            val discardedWalkSplits: Int
        )

        val samples = mutableListOf<ActiveRunSample>()
        var totalDiscardedWalkSegments = 0

        for (session in candidateSessions) {
            val recordedSplits = session.kmSplitsJson.toRecordedKmSplits()
            // Descarta trechos de caminhada/descanso (pace > 9'30"/km = 570 seg/km ou fase WALK)
            val walkSplitsCount = recordedSplits.count { split ->
                split.paceSecPerKm > 570 ||
                    split.phaseTag?.equals("WALK", ignoreCase = true) == true ||
                    split.splitLabel.contains("Caminh", ignoreCase = true) ||
                    split.splitLabel.contains("Descanso", ignoreCase = true)
            }
            val activeSplits = recordedSplits.filter { split ->
                split.distanceMeters >= 100 &&
                    split.paceSecPerKm in 150..570 &&
                    split.phaseTag?.equals("WALK", ignoreCase = true) != true &&
                    !split.splitLabel.contains("Caminh", ignoreCase = true) &&
                    !split.splitLabel.contains("Descanso", ignoreCase = true)
            }

            if (activeSplits.isNotEmpty()) {
                val sumDist = activeSplits.sumOf { it.distanceMeters }.coerceAtLeast(100)
                val sumDur = activeSplits.sumOf { it.durationSec }.coerceAtLeast(20)
                val avgPace = kotlin.math.round((sumDur * 1000.0) / sumDist.toDouble()).toInt()
                if (avgPace in 150..570) {
                    totalDiscardedWalkSegments += walkSplitsCount
                    samples.add(
                        ActiveRunSample(
                            session = session,
                            activeDistMeters = sumDist,
                            activeDurationSec = sumDur,
                            activePaceSecPerKm = avgPace,
                            discardedWalkSplits = walkSplitsCount
                        )
                    )
                    continue
                }
            }

            // Fallback analítico via SprintAnalytics (que já isola tiros de corrida vs caminhada)
            val sprintAnalytics = runCatching {
                com.example.data.model.calculateSprintAnalytics(session, emptyList())
            }.getOrNull()

            val candidatePaceSec = when {
                sprintAnalytics != null && sprintAnalytics.avgSprintPaceSecPerKm in 150..570 -> sprintAnalytics.avgSprintPaceSecPerKm
                session.mainRunAvgPaceSecPerKm != null && session.mainRunAvgPaceSecPerKm in 150..570 -> session.mainRunAvgPaceSecPerKm
                session.avgPaceSecPerKm in 150..570 -> session.avgPaceSecPerKm
                sprintAnalytics != null && sprintAnalytics.mainPaceSecPerKm in 150..570 -> sprintAnalytics.mainPaceSecPerKm
                else -> null
            }

            if (candidatePaceSec != null) {
                val activeDist = if (sprintAnalytics != null && sprintAnalytics.walkingTimeSec > 0) {
                    totalDiscardedWalkSegments += 1
                    (sprintAnalytics.mainDistMeters - (sprintAnalytics.walkingTimeSec * 1.42).toInt()).coerceAtLeast(400)
                } else {
                    (session.mainRunDistanceMeters ?: session.distanceMeters).coerceAtLeast(400)
                }
                val activeDur = kotlin.math.round((activeDist / 1000.0) * candidatePaceSec).toInt()
                samples.add(
                    ActiveRunSample(
                        session = session,
                        activeDistMeters = activeDist,
                        activeDurationSec = activeDur,
                        activePaceSecPerKm = candidatePaceSec,
                        discardedWalkSplits = if ((sprintAnalytics?.walkingTimeSec ?: 0) > 0) 1 else 0
                    )
                )
            }
        }

        // Cálculo do ciclo de 14 dias
        val effectiveLastCalibrationMs = when {
            lastCalibrationTimestamp > 0L -> lastCalibrationTimestamp
            realHistory.isNotEmpty() -> realHistory.minOf { it.date }
            else -> nowMillis
        }
        val elapsedDays = ((nowMillis - effectiveLastCalibrationMs).coerceAtLeast(0L) / (24L * 3600L * 1000L)).toInt()
        val daysUntilNext = (14 - elapsedDays).coerceAtLeast(0)
        val isCycleComplete = lastCalibrationTimestamp > 0L && elapsedDays >= 14

        val dateFormat = java.text.SimpleDateFormat("dd/MM", ptBrLocale)
        val lastRevStr = if (lastCalibrationTimestamp > 0L) {
            dateFormat.format(java.util.Date(lastCalibrationTimestamp))
        } else if (realHistory.isNotEmpty()) {
            dateFormat.format(java.util.Date(effectiveLastCalibrationMs))
        } else {
            "Hoje"
        }

        val cycleStatusText = if (calibrationMode == PaceCalibrationMode.AUTO_14_DAYS) {
            if (daysUntilNext == 0 && lastCalibrationTimestamp > 0L) {
                "Autocalibração (14d) • Última revisão: $lastRevStr • Revisão disponível hoje"
            } else {
                "Autocalibração (14d) • Última revisão: $lastRevStr • Próxima em ${if (lastCalibrationTimestamp == 0L) 14 else daysUntilNext} dias"
            }
        } else {
            "Modo Manual • Última revisão: $lastRevStr • Autocalibração (14d) desativada"
        }

        if (samples.isEmpty()) {
            return PaceCalibrationAnalysis(
                hasEnoughData = false,
                analyzedSessionsCount = 0,
                totalActiveDistanceKm = 0f,
                discardedWalkSegmentsCount = 0,
                avgHrBpm = null,
                effectiveMaxHr = effectiveMaxHr,
                hrEffortRatioPct = null,
                rawRunPaceSecPerKm = null,
                safetyAdjustmentSecPerKm = 0,
                calculatedEffectivePaceMinPerKm = null,
                calculatedEffectivePaceSecPerKm = null,
                formattedCalculatedPace = "--'--\"/km",
                estimated5kTimeFormatted = "--:--",
                currentPaceMinPerKm = currentPaceMin,
                currentPaceSecPerKm = currentPaceSec,
                formattedCurrentPace = formatPaceSecPerKm(currentPaceSec),
                deltaSecPerKm = 0,
                signedDeltaSecPerKm = 0,
                calibrationMode = calibrationMode,
                lastCalibrationTimestamp = lastCalibrationTimestamp,
                daysSinceLastCalibration = elapsedDays,
                daysUntilNextCalibration = if (lastCalibrationTimestamp == 0L) 14 else daysUntilNext,
                isCycleComplete = isCycleComplete,
                shouldSuggestUpdate = false,
                cycleStatusLine = cycleStatusText,
                hrAdjustmentSummary = "Realize ao menos 1 corrida com GPS para ativar o cálculo de Pace Real."
            )
        }

        // Ponderação por distância ativa + peso de recência (corridas mais recentes pesam levemente mais)
        var weightedPaceSum = 0.0
        var weightTotal = 0.0
        samples.forEachIndexed { index, sample ->
            val recencyFactor = (1.0 - (index * 0.06)).coerceAtLeast(0.70)
            val distWeight = (sample.activeDistMeters / 1000.0).coerceIn(0.5, 10.0) * recencyFactor
            weightedPaceSum += sample.activePaceSecPerKm * distWeight
            weightTotal += distWeight
        }

        val rawRunPaceSec = kotlin.math.round(weightedPaceSum / weightTotal.coerceAtLeast(0.1)).toInt().coerceIn(180, 570)
        val totalActiveDistKm = (samples.sumOf { it.activeDistMeters } / 1000f)

        // Ponderação pela relação Pace vs. Frequência Cardíaca Média
        val hrValues = samples.mapNotNull { it.session.avgHr?.takeIf { hr -> hr in 75..220 } }
        val avgHrBpm = if (hrValues.isNotEmpty()) kotlin.math.round(hrValues.average()).toInt() else null
        val hrEffortRatioPct = if (avgHrBpm != null && effectiveMaxHr > 0) {
            kotlin.math.round((avgHrBpm.toDouble() * 100.0) / effectiveMaxHr.toDouble()).toInt()
        } else null

        val safetyAdjustmentSec = when {
            hrEffortRatioPct == null -> 0
            hrEffortRatioPct <= 85 -> 0 // Ritmo consolidado (BPM médio controlado <= 85% FCmáx)
            hrEffortRatioPct in 86..90 -> 5 // Esforço de limiar moderado (+5s/km)
            hrEffortRatioPct in 91..94 -> 12 // > 90% FCmáx: fator de segurança conservador (+12s/km)
            else -> 15 // >= 95% FCmáx: fator de segurança conservador máximo (+15s/km)
        }

        val effective5kPaceSec = (rawRunPaceSec + safetyAdjustmentSec).coerceIn(180, 570)
        val effective5kPaceMin = effective5kPaceSec / 60f
        val est5kTotalSec = effective5kPaceSec * 5
        val est5kFormatted = String.format(java.util.Locale.US, "%02d:%02d", est5kTotalSec / 60, est5kTotalSec % 60)

        val signedDelta = effective5kPaceSec - currentPaceSec
        val absDelta = kotlin.math.abs(signedDelta)

        val hrAdjustmentSummary = when {
            hrEffortRatioPct == null ->
                "Baseado em ${samples.size} ${if (samples.size == 1) "treino" else "treinos"} (${String.format(ptBrLocale, "%.1f", totalActiveDistKm)} km ativos, caminhadas >9'30\"/km descartadas)."
            hrEffortRatioPct <= 85 ->
                "FC média controlada (${avgHrBpm} bpm • ${hrEffortRatioPct}% FCmáx ≤85%): ritmo consolidado sem penalidade."
            hrEffortRatioPct in 86..90 ->
                "FC média em limiar (${avgHrBpm} bpm • ${hrEffortRatioPct}% FCmáx): ajuste prudente de +${safetyAdjustmentSec}s/km."
            else ->
                "FC média elevada (${avgHrBpm} bpm • ${hrEffortRatioPct}% FCmáx >90%): fator de segurança conservador de +${safetyAdjustmentSec}s/km aplicado."
        }

        val shouldSuggest = absDelta > 5 || (isCycleComplete && absDelta >= 2)

        return PaceCalibrationAnalysis(
            hasEnoughData = true,
            analyzedSessionsCount = samples.size,
            totalActiveDistanceKm = totalActiveDistKm,
            discardedWalkSegmentsCount = totalDiscardedWalkSegments,
            avgHrBpm = avgHrBpm,
            effectiveMaxHr = effectiveMaxHr,
            hrEffortRatioPct = hrEffortRatioPct,
            rawRunPaceSecPerKm = rawRunPaceSec,
            safetyAdjustmentSecPerKm = safetyAdjustmentSec,
            calculatedEffectivePaceMinPerKm = effective5kPaceMin,
            calculatedEffectivePaceSecPerKm = effective5kPaceSec,
            formattedCalculatedPace = formatPaceSecPerKm(effective5kPaceSec),
            estimated5kTimeFormatted = est5kFormatted,
            currentPaceMinPerKm = currentPaceMin,
            currentPaceSecPerKm = currentPaceSec,
            formattedCurrentPace = formatPaceSecPerKm(currentPaceSec),
            deltaSecPerKm = absDelta,
            signedDeltaSecPerKm = signedDelta,
            calibrationMode = calibrationMode,
            lastCalibrationTimestamp = lastCalibrationTimestamp,
            daysSinceLastCalibration = elapsedDays,
            daysUntilNextCalibration = if (lastCalibrationTimestamp == 0L) 14 else daysUntilNext,
            isCycleComplete = isCycleComplete,
            shouldSuggestUpdate = shouldSuggest,
            cycleStatusLine = cycleStatusText,
            hrAdjustmentSummary = hrAdjustmentSummary
        )
    }
}

enum class PaceCalibrationMode : Serializable {
    AUTO_14_DAYS,
    MANUAL
}

data class PaceCalibrationAnalysis(
    val hasEnoughData: Boolean,
    val analyzedSessionsCount: Int,
    val totalActiveDistanceKm: Float,
    val discardedWalkSegmentsCount: Int,
    val avgHrBpm: Int?,
    val effectiveMaxHr: Int,
    val hrEffortRatioPct: Int?,
    val rawRunPaceSecPerKm: Int?,
    val safetyAdjustmentSecPerKm: Int,
    val calculatedEffectivePaceMinPerKm: Float?,
    val calculatedEffectivePaceSecPerKm: Int?,
    val formattedCalculatedPace: String,
    val estimated5kTimeFormatted: String,
    val currentPaceMinPerKm: Float,
    val currentPaceSecPerKm: Int,
    val formattedCurrentPace: String,
    val deltaSecPerKm: Int,
    val signedDeltaSecPerKm: Int,
    val calibrationMode: PaceCalibrationMode,
    val lastCalibrationTimestamp: Long,
    val daysSinceLastCalibration: Int,
    val daysUntilNextCalibration: Int,
    val isCycleComplete: Boolean,
    val shouldSuggestUpdate: Boolean,
    val cycleStatusLine: String,
    val hrAdjustmentSummary: String
) : Serializable

