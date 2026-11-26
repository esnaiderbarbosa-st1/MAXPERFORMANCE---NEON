@file:OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class
)

package com.example.ui.components

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.RunningSession
import com.example.data.model.toHrTimelineList
import com.example.data.model.toRecordedKmSplits
import com.example.engine.RunningBlock
import com.example.engine.RunningBlockType
import com.example.engine.RunningWorkoutPlan
import com.example.ui.theme.RunColors
import com.example.utils.AudioSynth
import java.util.Locale
import kotlin.math.pow
import kotlin.math.roundToInt

enum class RunnerGoalOption(
    val id: String,
    val title: String,
    val subtitle: String,
    val iconBadge: String,
    val shortBadge: String,
    val recommendedProgramId: String,
    val accentColor: Color
) {
    GOAL_5K_TAF(
        id = "5K_TAF",
        title = "5 km (TAF / Performance)",
        subtitle = "Velocidade aeróbica máxima e tiros de ritmo",
        iconBadge = "5K",
        shortBadge = "Meta 5K / TAF",
        recommendedProgramId = "road_to_5k",
        accentColor = Color(0xFF00E5FF)
    ),
    GOAL_10K(
        id = "10K",
        title = "10 km Performance",
        subtitle = "Resistência de limiar e volume progressivo",
        iconBadge = "10K",
        shortBadge = "Meta 10K",
        recommendedProgramId = "couch_to_10k",
        accentColor = Color(0xFF00E676)
    ),
    GOAL_21K(
        id = "21K",
        title = "Meia Maratona (21,1 km)",
        subtitle = "Endurance longo e economia de corrida",
        iconBadge = "21K",
        shortBadge = "Meia 21,1K",
        recommendedProgramId = "couch_to_10k",
        accentColor = Color(0xFFFFD600)
    ),
    GOAL_MAINTAIN(
        id = "RUN_TO_MAINTAIN",
        title = "Manutenção (Run to Maintain)",
        subtitle = "3 treinos/semana integrados à musculação",
        iconBadge = "RTM",
        shortBadge = "Run to Maintain",
        recommendedProgramId = "run_to_maintain",
        accentColor = Color(0xFFA855F7)
    ),
    GOAL_RECOMP(
        id = "WEIGHT_LOSS_RECOMP",
        title = "Emagrecimento & Recomposição",
        subtitle = "Oxidação lipídica (Z2) e alta constância",
        iconBadge = "Z2",
        shortBadge = "Recomposição",
        recommendedProgramId = "run_30m",
        accentColor = Color(0xFFFF6B00)
    );

    companion object {
        fun fromId(id: String?): RunnerGoalOption =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: GOAL_5K_TAF
    }
}

enum class RunnerLevelOption(
    val id: String,
    val label: String,
    val subtitle: String,
    val badge: String,
    val enduranceExponent: Double
) {
    BEGINNER(
        id = "BEGINNER",
        label = "Iniciante",
        subtitle = "Primeiros 5 km contínuos ou retorno gradual aos treinos",
        badge = "N1",
        enduranceExponent = 1.08
    ),
    INTERMEDIATE(
        id = "INTERMEDIATE",
        label = "Intermediário",
        subtitle = "Corre 5K a 10K regularmente e busca evoluir pace",
        badge = "N2",
        enduranceExponent = 1.06
    ),
    ADVANCED(
        id = "ADVANCED",
        label = "Avançado",
        subtitle = "Domina treinos intervalados (Z4/Z5), Fartlek e longões",
        badge = "N3",
        enduranceExponent = 1.045
    );

    companion object {
        fun fromId(id: String?): RunnerLevelOption =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: INTERMEDIATE
    }
}

enum class FieldTestProtocol(
    val id: String,
    val label: String,
    val distanceMeters: Int
) {
    TEST_3000M("TEST_3000M", "Teste 3.000m (Vel. Crítica)", 3000),
    BEST_5000M("BEST_5000M", "Melhor 5 km Recente", 5000);

    companion object {
        fun fromId(id: String?): FieldTestProtocol =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: BEST_5000M
    }
}

data class RunnerAnamnesisProfile(
    val goal: RunnerGoalOption = RunnerGoalOption.GOAL_5K_TAF,
    val level: RunnerLevelOption = RunnerLevelOption.INTERMEDIATE,
    val maxWeeklyVolumeKm: Int = 25,
    val availableDaysPerWeek: Int = 3,
    val testProtocol: FieldTestProtocol = FieldTestProtocol.BEST_5000M,
    val testTimeSeconds: Int = 1650, // 27:30 nos 5K ou 15:30 nos 3K
    val isConfigured: Boolean = false
)

data class CalibratedZoneSpec(
    val zoneCode: String,
    val title: String,
    val minPaceSecPerKm: Int,
    val maxPaceSecPerKm: Int,
    val minBpm: Int,
    val maxBpm: Int,
    val color: Color
) {
    fun formattedPaceRange(): String =
        "${formatPaceSec(minPaceSecPerKm)}–${formatPaceSec(maxPaceSecPerKm)}/km"
}

data class RacePredictionItem(
    val distanceLabel: String,
    val distanceKm: Float,
    val estimatedTotalSeconds: Int,
    val estimatedPaceSecPerKm: Int
) {
    val formattedTime: String get() = formatRaceDuration(estimatedTotalSeconds)
    val formattedPace: String get() = "${formatPaceSec(estimatedPaceSecPerKm)}/km"
}

data class PhysiologicalCalibrationSummary(
    val vamKmh: Float,
    val vamPaceSecPerKm: Int,
    val lt2PaceSecPerKm: Int,
    val lt1PaceSecPerKm: Int,
    val lt1Bpm: Int,
    val lt2Bpm: Int,
    val maxHrBpm: Int,
    val zones: List<CalibratedZoneSpec>,
    val predictions: List<RacePredictionItem>,
    val sourceLabel: String
)

enum class RxBlockPillType(val bgColor: Color, val textColor: Color) {
    WARMUP(Color(0xFF00B0FF), Color(0xFF00E5FF)),
    STIMULUS_Z4_Z5(Color(0xFFFF6B00), Color(0xFFFF9E40)),
    TEMPO_Z3(Color(0xFF00E676), Color(0xFF69F0AE)),
    RECOVERY_Z1_Z2(Color(0xFF10B981), Color(0xFF34D399)),
    COOLDOWN(Color(0xFFA855F7), Color(0xFFD8B4FE))
}

data class RxBlockPill(
    val label: String,
    val type: RxBlockPillType
)

enum class RxWorkoutCategory(val id: String, val label: String, val accentColor: Color) {
    ALL("ALL", "Todos", Color(0xFF00E5FF)),
    BEGINNER("BEGINNER", "Iniciante", Color(0xFF38BDF8)),
    FARTLEK("FARTLEK", "Fartlek", Color(0xFF00E676)),
    INTERVAL("INTERVAL", "Intervalados (Z4/Z5)", Color(0xFFFF6B00)),
    PROGRESSIVE("PROGRESSIVE", "Progressivos & Tempo", Color(0xFFFFB300)),
    LONG_RUN("LONG_RUN", "Rodagem & Longo", Color(0xFF60A5FA)),
    RUN_TO_MAINTAIN("RUN_TO_MAINTAIN", "Run to Maintain", Color(0xFFA855F7))
}

data class RxWorkoutStepBuilder(
    val blockType: RunningBlockType,
    val durationSec: Int,
    val label: String,
    val paceZoneIndex: Int // 0=Z1, 1=Z2, 2=Z3, 3=Z4, 4=Z5
)

data class RxWorkoutTemplate(
    val code: String,
    val title: String,
    val subtitle: String,
    val category: RxWorkoutCategory,
    val durationLabel: String,
    val distanceEstimateLabel: String,
    val blockPills: List<RxBlockPill>,
    val steps: List<RxWorkoutStepBuilder>
) {
    fun toRunningWorkoutPlan(calibration: PhysiologicalCalibrationSummary): RunningWorkoutPlan {
        val builtBlocks = steps.map { step ->
            val zone = calibration.zones.getOrNull(step.paceZoneIndex.coerceIn(0, 4))
            val minP = if (step.blockType == RunningBlockType.RUN) zone?.minPaceSecPerKm else null
            val maxP = if (step.blockType == RunningBlockType.RUN) zone?.maxPaceSecPerKm else null
            val paceSuffix = if (step.blockType == RunningBlockType.RUN && zone != null) {
                " (${zone.formattedPaceRange()})"
            } else ""
            RunningBlock(
                type = step.blockType,
                durationSec = step.durationSec,
                label = "${step.label}$paceSuffix",
                minPaceSecPerKm = minP,
                maxPaceSecPerKm = maxP
            )
        }
        return RunningWorkoutPlan(
            weekNumber = 1,
            sessionType = "RX_$code",
            title = "$code • $title",
            description = "$subtitle • Blocos: ${blockPills.joinToString(" + ") { it.label }}",
            blocks = builtBlocks
        )
    }
}

object RunnerAnamnesisManager {
    private const val PREFS_NAME = "max_run_anamnesis_prefs"
    private const val KEY_IS_CONFIGURED = "anamnesis_configured"
    private const val KEY_HAS_AUTO_SHOWN = "anamnesis_auto_shown_once"
    private const val KEY_GOAL = "anamnesis_goal"
    private const val KEY_LEVEL = "anamnesis_level"
    private const val KEY_WEEKLY_KM = "anamnesis_weekly_km"
    private const val KEY_DAYS_PER_WEEK = "anamnesis_days_per_week"
    private const val KEY_TEST_PROTOCOL = "anamnesis_test_protocol"
    private const val KEY_TEST_TIME_SEC = "anamnesis_test_time_sec"

    fun loadProfile(context: Context, fallbackPaceMinPerKm: Float? = null): RunnerAnamnesisProfile {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val default5kSec = if (fallbackPaceMinPerKm != null && fallbackPaceMinPerKm in 3.0f..12.0f) {
            (fallbackPaceMinPerKm * 5f * 60f).roundToInt()
        } else {
            1650 // 27:30 (5:30/km)
        }
        return RunnerAnamnesisProfile(
            goal = RunnerGoalOption.fromId(prefs.getString(KEY_GOAL, RunnerGoalOption.GOAL_5K_TAF.id)),
            level = RunnerLevelOption.fromId(prefs.getString(KEY_LEVEL, RunnerLevelOption.INTERMEDIATE.id)),
            maxWeeklyVolumeKm = prefs.getInt(KEY_WEEKLY_KM, 25).coerceIn(10, 120),
            availableDaysPerWeek = prefs.getInt(KEY_DAYS_PER_WEEK, 3).coerceIn(2, 6),
            testProtocol = FieldTestProtocol.fromId(prefs.getString(KEY_TEST_PROTOCOL, FieldTestProtocol.BEST_5000M.id)),
            testTimeSeconds = prefs.getInt(KEY_TEST_TIME_SEC, default5kSec).coerceIn(420, 4800),
            isConfigured = prefs.getBoolean(KEY_IS_CONFIGURED, false)
        )
    }

    fun saveProfile(context: Context, profile: RunnerAnamnesisProfile) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_IS_CONFIGURED, true)
            .putBoolean(KEY_HAS_AUTO_SHOWN, true)
            .putString(KEY_GOAL, profile.goal.id)
            .putString(KEY_LEVEL, profile.level.id)
            .putInt(KEY_WEEKLY_KM, profile.maxWeeklyVolumeKm)
            .putInt(KEY_DAYS_PER_WEEK, profile.availableDaysPerWeek)
            .putString(KEY_TEST_PROTOCOL, profile.testProtocol.id)
            .putInt(KEY_TEST_TIME_SEC, profile.testTimeSeconds)
            .apply()
    }

    fun hasAutoShownOnce(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_HAS_AUTO_SHOWN, false)
    }

    fun markAutoShownOnce(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_HAS_AUTO_SHOWN, true)
            .apply()
    }

    /**
     * Atualiza automaticamente a VAM e recalibra o tempo de referência (3K/5K) e pace base
     * quando o motor estilo Runna detecta evolução real nos tiros com FC controlada.
     */
    fun applyAutoRegulatedVamUpgrade(
        context: Context,
        newVamKmh: Float,
        currentProfile: RunnerAnamnesisProfile = loadProfile(context)
    ): Pair<RunnerAnamnesisProfile, Float> {
        val safeVamKmh = newVamKmh.coerceIn(7.0f, 24.0f)
        val vamMps = safeVamKmh / 3.6
        val newTestTimeSec = if (currentProfile.testProtocol == FieldTestProtocol.TEST_3000M) {
            val speed3kMps = vamMps * 0.98
            (3000.0 / speed3kMps).roundToInt().coerceIn(450, 3600)
        } else {
            val speed5kMps = vamMps * 0.94
            (5000.0 / speed5kMps).roundToInt().coerceIn(750, 4800)
        }
        val updatedProfile = currentProfile.copy(
            testTimeSeconds = newTestTimeSec,
            isConfigured = true
        )
        saveProfile(context, updatedProfile)
        val new5kPaceSecPerKm = (1000.0 / (vamMps * 0.94)).roundToInt().coerceIn(165, 660)
        val newBaselinePaceMinPerKm = (new5kPaceSecPerKm / 60f)
        return updatedProfile to newBaselinePaceMinPerKm
    }

    fun calculateCalibration(
        profile: RunnerAnamnesisProfile,
        athleteAge: Int,
        history: List<RunningSession>,
        baselinePaceMinPerKm: Float?,
        calibratedMaxHrBpm: Int? = null
    ): PhysiologicalCalibrationSummary {
        // Verifica se há um 5K ou 3K real mais rápido no histórico se o perfil ainda não foi configurado manualmente
        val bestHistory5kSec = history
            .filter { it.distanceMeters >= 3000 && it.durationSec > 400 && it.avgPaceSecPerKm in 180..720 }
            .minOfOrNull { sess ->
                val distKm = sess.distanceMeters / 1000.0
                (sess.durationSec * (5.0 / distKm).pow(1.06)).roundToInt()
            }

        val (refDistanceM, refTimeSec, sourceLabel) = when {
            profile.isConfigured -> Triple(
                profile.testProtocol.distanceMeters,
                profile.testTimeSeconds.coerceAtLeast(450),
                if (profile.testProtocol == FieldTestProtocol.TEST_3000M) {
                    "Teste 3K (${formatRaceDuration(profile.testTimeSeconds)})"
                } else {
                    "5K Calibrado (${formatRaceDuration(profile.testTimeSeconds)})"
                }
            )
            bestHistory5kSec != null -> Triple(
                5000,
                bestHistory5kSec,
                "Histórico Real (${formatRaceDuration(bestHistory5kSec)} nos 5K)"
            )
            baselinePaceMinPerKm != null && baselinePaceMinPerKm > 0f -> {
                val sec5k = (baselinePaceMinPerKm * 5f * 60f).roundToInt().coerceIn(750, 3600)
                Triple(5000, sec5k, "Pace Base (${formatPaceSec((baselinePaceMinPerKm * 60f).roundToInt())}/km)")
            }
            else -> Triple(5000, profile.testTimeSeconds, "Referência Inicial")
        }

        // Velocidade Aeróbica Máxima (VAM):
        // Em 3.000m o atleta sustenta ~98% da VAM; em 5.000m sustenta ~94% da VAM.
        val avgSpeedMps = refDistanceM.toDouble() / refTimeSec.toDouble()
        val vamMps = if (refDistanceM == 3000) avgSpeedMps / 0.98 else avgSpeedMps / 0.94
        val vamKmh = (vamMps * 3.6).toFloat().coerceIn(6.5f, 24.0f)
        val vamPaceSecPerKm = (1000.0 / vamMps).roundToInt().coerceIn(150, 600)

        // Limiar Anaeróbico (LT2) ocorre a ~87% da VAM; Limiar Aeróbico (LT1) a ~76% da VAM
        val lt2PaceSec = (vamPaceSecPerKm / 0.87).roundToInt().coerceIn(175, 660)
        val lt1PaceSec = (vamPaceSecPerKm / 0.76).roundToInt().coerceIn(200, 750)

        val calibratedMaxHr = calibratedMaxHrBpm?.takeIf { it in 100..235 }
            ?: maxOf(220 - athleteAge.coerceIn(18, 85), 190)
        val lt1Bpm = (calibratedMaxHr * 0.72f).roundToInt()
        val lt2Bpm = (calibratedMaxHr * 0.85f).roundToInt()

        val zones = listOf(
            CalibratedZoneSpec(
                zoneCode = "Z1",
                title = "Recuperação Ativa (< LT1)",
                minPaceSecPerKm = (lt1PaceSec * 1.06).roundToInt(),
                maxPaceSecPerKm = (lt1PaceSec * 1.18).roundToInt(),
                minBpm = (calibratedMaxHr * 0.50f).roundToInt(),
                maxBpm = (calibratedMaxHr * 0.62f).roundToInt(),
                color = Color(0xFF00B0FF)
            ),
            CalibratedZoneSpec(
                zoneCode = "Z2",
                title = "Base Aeróbica / Rodagem (<= LT1)",
                minPaceSecPerKm = (lt2PaceSec * 1.09).roundToInt(),
                maxPaceSecPerKm = (lt1PaceSec * 1.05).roundToInt(),
                minBpm = (calibratedMaxHr * 0.62f).roundToInt() + 1,
                maxBpm = lt1Bpm,
                color = Color(0xFF00E5FF)
            ),
            CalibratedZoneSpec(
                zoneCode = "Z3",
                title = "Tempo Run / Moderado (LT1–LT2)",
                minPaceSecPerKm = (lt2PaceSec * 1.02).roundToInt(),
                maxPaceSecPerKm = (lt2PaceSec * 1.08).roundToInt(),
                minBpm = lt1Bpm + 1,
                maxBpm = (lt2Bpm - 4).coerceAtLeast(lt1Bpm + 4),
                color = Color(0xFF00E676)
            ),
            CalibratedZoneSpec(
                zoneCode = "Z4",
                title = "Limiar Anaeróbico (LT2)",
                minPaceSecPerKm = (lt2PaceSec * 0.96).roundToInt(),
                maxPaceSecPerKm = (lt2PaceSec * 1.02).roundToInt(),
                minBpm = (lt2Bpm - 3),
                maxBpm = (lt2Bpm + 5),
                color = Color(0xFFFFB300)
            ),
            CalibratedZoneSpec(
                zoneCode = "Z5",
                title = "VO2máx / Tiros Curtos (VAM)",
                minPaceSecPerKm = (vamPaceSecPerKm * 0.95).roundToInt(),
                maxPaceSecPerKm = (vamPaceSecPerKm * 1.03).roundToInt(),
                minBpm = (lt2Bpm + 6),
                maxBpm = calibratedMaxHr,
                color = Color(0xFFFF5252)
            )
        )

        // Projeção de Prova (Fórmula de Riegel ajustada pelo nível e volume semanal)
        val baseExponent = profile.level.enduranceExponent -
            if (profile.maxWeeklyVolumeKm >= 40) 0.01 else if (profile.maxWeeklyVolumeKm <= 15) -0.01 else 0.0

        fun predictForDistance(targetMeters: Int, label: String): RacePredictionItem {
            val ratio = targetMeters.toDouble() / refDistanceM.toDouble()
            val predictedSec = (refTimeSec.toDouble() * ratio.pow(baseExponent)).roundToInt()
            val paceSec = ((predictedSec.toDouble() / targetMeters.toDouble()) * 1000.0).roundToInt()
            return RacePredictionItem(
                distanceLabel = label,
                distanceKm = targetMeters / 1000f,
                estimatedTotalSeconds = predictedSec,
                estimatedPaceSecPerKm = paceSec
            )
        }

        val predictions = listOf(
            predictForDistance(5000, "5 km"),
            predictForDistance(10000, "10 km"),
            predictForDistance(21097, "21,1 km (Meia)")
        )

        return PhysiologicalCalibrationSummary(
            vamKmh = vamKmh,
            vamPaceSecPerKm = vamPaceSecPerKm,
            lt2PaceSecPerKm = lt2PaceSec,
            lt1PaceSecPerKm = lt1PaceSec,
            lt1Bpm = lt1Bpm,
            lt2Bpm = lt2Bpm,
            maxHrBpm = calibratedMaxHr,
            zones = zones,
            predictions = predictions,
            sourceLabel = sourceLabel
        )
    }

    private fun repeatIntervals(
        reps: Int,
        workSec: Int,
        workLabel: String,
        workZone: Int,
        restSec: Int,
        restLabel: String,
        warmupSec: Int = 300,
        cooldownSec: Int = 300
    ): List<RxWorkoutStepBuilder> = buildList {
        add(RxWorkoutStepBuilder(RunningBlockType.WARMUP, warmupSec, "Aquecimento Progressivo", 0))
        for (i in 1..reps) {
            add(RxWorkoutStepBuilder(RunningBlockType.RUN, workSec, "$workLabel ($i/$reps)", workZone))
            if (i < reps || restSec > 0) {
                add(RxWorkoutStepBuilder(RunningBlockType.WALK, restSec, "$restLabel ($i/$reps)", 0))
            }
        }
        add(RxWorkoutStepBuilder(RunningBlockType.COOL_DOWN, cooldownSec, "Desaquecimento Leve", 0))
    }

    val RX_PRO_WORKOUT_CATALOG: List<RxWorkoutTemplate> = listOf(
        // 1. INICIANTE / TRANSIÇÃO
        RxWorkoutTemplate(
            code = "INI35-1'X1'",
            title = "Alternado 10× (1' Trote + 1' Caminhada)",
            subtitle = "Adaptação articular e cardiorrespiratória sem impacto excessivo",
            category = RxWorkoutCategory.BEGINNER,
            durationLabel = "35 min",
            distanceEstimateLabel = "~4,2 km",
            blockPills = listOf(
                RxBlockPill("AQ 8'", RxBlockPillType.WARMUP),
                RxBlockPill("10× 1' Trote Z2", RxBlockPillType.TEMPO_Z3),
                RxBlockPill("10× 1' Cam. Z1", RxBlockPillType.RECOVERY_Z1_Z2),
                RxBlockPill("ESF 7'", RxBlockPillType.COOLDOWN)
            ),
            steps = repeatIntervals(
                reps = 10,
                workSec = 60,
                workLabel = "Trote Leve Z2",
                workZone = 1,
                restSec = 60,
                restLabel = "Caminhada Ativa Z1",
                warmupSec = 480,
                cooldownSec = 420
            )
        ),
        RxWorkoutTemplate(
            code = "INI50-4'X1'",
            title = "Sustentação 6× (4' Corrida + 1' Caminhada)",
            subtitle = "Consolidação de base aeróbica rumo aos 30 minutos contínuos",
            category = RxWorkoutCategory.BEGINNER,
            durationLabel = "50 min",
            distanceEstimateLabel = "~6,5 km",
            blockPills = listOf(
                RxBlockPill("AQ 10'", RxBlockPillType.WARMUP),
                RxBlockPill("6× 4' Corrida Z2", RxBlockPillType.TEMPO_Z3),
                RxBlockPill("6× 1' Cam. Z1", RxBlockPillType.RECOVERY_Z1_Z2),
                RxBlockPill("ESF 10'", RxBlockPillType.COOLDOWN)
            ),
            steps = repeatIntervals(
                reps = 6,
                workSec = 240,
                workLabel = "Corrida Confortável Z2",
                workZone = 1,
                restSec = 60,
                restLabel = "Caminhada Recuperação",
                warmupSec = 600,
                cooldownSec = 600
            )
        ),
        RxWorkoutTemplate(
            code = "INI4KM-200X200",
            title = "Transição Mecânica 200m × 200m",
            subtitle = "10 repetições curtas de ~200m soltos com 200m de trote/caminhada",
            category = RxWorkoutCategory.BEGINNER,
            durationLabel = "34 min",
            distanceEstimateLabel = "4,0 km",
            blockPills = listOf(
                RxBlockPill("AQ 5'", RxBlockPillType.WARMUP),
                RxBlockPill("10× 200m Z3", RxBlockPillType.STIMULUS_Z4_Z5),
                RxBlockPill("10× 200m Z1", RxBlockPillType.RECOVERY_Z1_Z2),
                RxBlockPill("ESF 5'", RxBlockPillType.COOLDOWN)
            ),
            steps = repeatIntervals(
                reps = 10,
                workSec = 65,
                workLabel = "Estímulo 200m Z3",
                workZone = 2,
                restSec = 80,
                restLabel = "Recuperação 200m Z1",
                warmupSec = 300,
                cooldownSec = 300
            )
        ),
        RxWorkoutTemplate(
            code = "INI-30SEG/1MIN30",
            title = "Iniciação Neuromotora 30s × 1'30\"",
            subtitle = "10 estímulos curtos de 30s com 1m30s de recuperação completa",
            category = RxWorkoutCategory.BEGINNER,
            durationLabel = "30 min",
            distanceEstimateLabel = "~3,8 km",
            blockPills = listOf(
                RxBlockPill("AQ 5'", RxBlockPillType.WARMUP),
                RxBlockPill("10× 30s Forte", RxBlockPillType.STIMULUS_Z4_Z5),
                RxBlockPill("10× 1'30\" Cam.", RxBlockPillType.RECOVERY_Z1_Z2),
                RxBlockPill("ESF 5'", RxBlockPillType.COOLDOWN)
            ),
            steps = repeatIntervals(
                reps = 10,
                workSec = 30,
                workLabel = "Estímulo 30s Z3/Z4",
                workZone = 3,
                restSec = 90,
                restLabel = "Caminhada 1'30\" Z1",
                warmupSec = 300,
                cooldownSec = 300
            )
        ),

        // 2. FARTLEK
        RxWorkoutTemplate(
            code = "FAR6-400X200",
            title = "Fartlek Dinâmico 400m Forte × 200m Leve",
            subtitle = "8 mudanças de ritmo alternando ~400m em Z4 e ~200m trote regenerativo",
            category = RxWorkoutCategory.FARTLEK,
            durationLabel = "36 min",
            distanceEstimateLabel = "6,0 km",
            blockPills = listOf(
                RxBlockPill("AQ 6'", RxBlockPillType.WARMUP),
                RxBlockPill("8× 400m Z4", RxBlockPillType.STIMULUS_Z4_Z5),
                RxBlockPill("8× 200m Z2", RxBlockPillType.RECOVERY_Z1_Z2),
                RxBlockPill("ESF 6'", RxBlockPillType.COOLDOWN)
            ),
            steps = repeatIntervals(
                reps = 8,
                workSec = 115,
                workLabel = "Fartlek Forte 400m Z4",
                workZone = 3,
                restSec = 70,
                restLabel = "Trote Ativo 200m Z2",
                warmupSec = 360,
                cooldownSec = 360
            )
        ),
        RxWorkoutTemplate(
            code = "FAR8-800X800",
            title = "Fartlek Clássico 5× (800m Z4 + 800m Z2)",
            subtitle = "Variação contínua de velocidade trabalhando reciclagem de lactato em movimento",
            category = RxWorkoutCategory.FARTLEK,
            durationLabel = "48 min",
            distanceEstimateLabel = "8,0 km",
            blockPills = listOf(
                RxBlockPill("AQ 5'", RxBlockPillType.WARMUP),
                RxBlockPill("5× 800m Z4", RxBlockPillType.STIMULUS_Z4_Z5),
                RxBlockPill("5× 800m Z2", RxBlockPillType.RECOVERY_Z1_Z2),
                RxBlockPill("ESF 5'", RxBlockPillType.COOLDOWN)
            ),
            steps = repeatIntervals(
                reps = 5,
                workSec = 230,
                workLabel = "Bloco 800m Forte Z4",
                workZone = 3,
                restSec = 260,
                restLabel = "Bloco 800m Trote Z2",
                warmupSec = 300,
                cooldownSec = 300
            )
        ),
        RxWorkoutTemplate(
            code = "FAR12-500X500",
            title = "Fartlek Volume 12× (500m Forte / 500m Leve)",
            subtitle = "12 km totais alternando meio quilômetro em Z4 e meio quilômetro em Z2",
            category = RxWorkoutCategory.FARTLEK,
            durationLabel = "62 min",
            distanceEstimateLabel = "12,0 km",
            blockPills = listOf(
                RxBlockPill("AQ 5'", RxBlockPillType.WARMUP),
                RxBlockPill("12× 500m Z4", RxBlockPillType.STIMULUS_Z4_Z5),
                RxBlockPill("12× 500m Z2", RxBlockPillType.RECOVERY_Z1_Z2),
                RxBlockPill("ESF 5'", RxBlockPillType.COOLDOWN)
            ),
            steps = repeatIntervals(
                reps = 12,
                workSec = 140,
                workLabel = "Fartlek 500m Forte Z4",
                workZone = 3,
                restSec = 165,
                restLabel = "Fartlek 500m Leve Z2",
                warmupSec = 300,
                cooldownSec = 300
            )
        ),
        RxWorkoutTemplate(
            code = "FAR12-3KMX3KM",
            title = "Fartlek Longo 2× (3 km Z3/Z4 + 3 km Z2)",
            subtitle = "Blocos longos específicos para resistência de 10K e Meia Maratona",
            category = RxWorkoutCategory.FARTLEK,
            durationLabel = "64 min",
            distanceEstimateLabel = "12,0 km",
            blockPills = listOf(
                RxBlockPill("AQ 5'", RxBlockPillType.WARMUP),
                RxBlockPill("2× 3km Z3/Z4", RxBlockPillType.STIMULUS_Z4_Z5),
                RxBlockPill("2× 3km Z2", RxBlockPillType.RECOVERY_Z1_Z2),
                RxBlockPill("ESF 5'", RxBlockPillType.COOLDOWN)
            ),
            steps = repeatIntervals(
                reps = 2,
                workSec = 900,
                workLabel = "Bloco 3 km Ritmo Firme Z3/Z4",
                workZone = 3,
                restSec = 1020,
                restLabel = "Bloco 3 km Rodagem Z2",
                warmupSec = 300,
                cooldownSec = 300
            )
        ),

        // 3. INTERVALADOS (Z4/Z5)
        RxWorkoutTemplate(
            code = "INT7-100X100",
            title = "Intervalado Curto 14× (100m Z5 × 100m Rec)",
            subtitle = "Potência aeróbica, economia de corrida e recrutamento de fibras rápidas",
            category = RxWorkoutCategory.INTERVAL,
            durationLabel = "34 min",
            distanceEstimateLabel = "7,0 km",
            blockPills = listOf(
                RxBlockPill("AQ 10'", RxBlockPillType.WARMUP),
                RxBlockPill("14× 100m Z5", RxBlockPillType.STIMULUS_Z4_Z5),
                RxBlockPill("14× 100m Z1", RxBlockPillType.RECOVERY_Z1_Z2),
                RxBlockPill("ESF 8'", RxBlockPillType.COOLDOWN)
            ),
            steps = repeatIntervals(
                reps = 14,
                workSec = 24,
                workLabel = "Sprint 100m Z5 (VAM)",
                workZone = 4,
                restSec = 40,
                restLabel = "Trote/Caminhada 100m Z1",
                warmupSec = 600,
                cooldownSec = 480
            )
        ),
        RxWorkoutTemplate(
            code = "INT6-400X1'30\"",
            title = "Tiros de Pista 10× 400m (Pausa 1'30\")",
            subtitle = "Sessão clássica de VO2máx no ritmo de 3K/5K com recuperação controlada",
            category = RxWorkoutCategory.INTERVAL,
            durationLabel = "42 min",
            distanceEstimateLabel = "6,0 km",
            blockPills = listOf(
                RxBlockPill("AQ 8'", RxBlockPillType.WARMUP),
                RxBlockPill("10× 400m Z4/Z5", RxBlockPillType.STIMULUS_Z4_Z5),
                RxBlockPill("10× 1'30\" Z1", RxBlockPillType.RECOVERY_Z1_Z2),
                RxBlockPill("ESF 6'", RxBlockPillType.COOLDOWN)
            ),
            steps = repeatIntervals(
                reps = 10,
                workSec = 105,
                workLabel = "Tiro 400m Z4/Z5",
                workZone = 4,
                restSec = 90,
                restLabel = "Recuperação 1'30\" Z1",
                warmupSec = 480,
                cooldownSec = 360
            )
        ),
        RxWorkoutTemplate(
            code = "INT10-1KMX2'",
            title = "Intervalado Longo 10× 1 km (Pausa 2')",
            subtitle = "Padrão ouro para elevar o Limiar Anaeróbico (LT2) e VO2máx em provas de 5K a 21K",
            category = RxWorkoutCategory.INTERVAL,
            durationLabel = "70 min",
            distanceEstimateLabel = "12,0 km",
            blockPills = listOf(
                RxBlockPill("AQ 5'", RxBlockPillType.WARMUP),
                RxBlockPill("10× 1km Z4/Z5", RxBlockPillType.STIMULUS_Z4_Z5),
                RxBlockPill("10× 2' Rec Z1", RxBlockPillType.RECOVERY_Z1_Z2),
                RxBlockPill("ESF 5'", RxBlockPillType.COOLDOWN)
            ),
            steps = repeatIntervals(
                reps = 10,
                workSec = 290,
                workLabel = "Tiro 1 km Forte Z4/Z5",
                workZone = 3,
                restSec = 120,
                restLabel = "Pausa Regenerativa 2 min",
                warmupSec = 300,
                cooldownSec = 300
            )
        ),
        RxWorkoutTemplate(
            code = "INT8-4KMX7'",
            title = "Super-Bloco de Limiar 2× 4 km (Pausa 7')",
            subtitle = "Resistência específica de Limiar LT2 para 10K e Meia Maratona",
            category = RxWorkoutCategory.INTERVAL,
            durationLabel = "58 min",
            distanceEstimateLabel = "10,0 km",
            blockPills = listOf(
                RxBlockPill("AQ 5'", RxBlockPillType.WARMUP),
                RxBlockPill("2× 4km Limiar Z4", RxBlockPillType.STIMULUS_Z4_Z5),
                RxBlockPill("7' Rec Z1", RxBlockPillType.RECOVERY_Z1_Z2),
                RxBlockPill("ESF 5'", RxBlockPillType.COOLDOWN)
            ),
            steps = listOf(
                RxWorkoutStepBuilder(RunningBlockType.WARMUP, 300, "Aquecimento Progressivo", 0),
                RxWorkoutStepBuilder(RunningBlockType.RUN, 1200, "Bloco 1 • 4 km Limiar Z4", 3),
                RxWorkoutStepBuilder(RunningBlockType.WALK, 420, "Recuperação Ativa 7 min Z1", 0),
                RxWorkoutStepBuilder(RunningBlockType.RUN, 1200, "Bloco 2 • 4 km Limiar Z4", 3),
                RxWorkoutStepBuilder(RunningBlockType.COOL_DOWN, 300, "Desaquecimento Leve", 0)
            )
        ),

        // 4. PROGRESSIVOS & TEMPO RUN
        RxWorkoutTemplate(
            code = "PRO8-2KM",
            title = "Progressivo 8 km (Aumentando a cada 2 km)",
            subtitle = "4 blocos de 2 km evoluindo de Z2 Confortável até o Limiar Z4 (Negative Split)",
            category = RxWorkoutCategory.PROGRESSIVE,
            durationLabel = "46 min",
            distanceEstimateLabel = "8,0 km",
            blockPills = listOf(
                RxBlockPill("2km Z2", RxBlockPillType.RECOVERY_Z1_Z2),
                RxBlockPill("2km Z3-", RxBlockPillType.TEMPO_Z3),
                RxBlockPill("2km Z3+", RxBlockPillType.TEMPO_Z3),
                RxBlockPill("2km Z4 Forte", RxBlockPillType.STIMULUS_Z4_Z5)
            ),
            steps = listOf(
                RxWorkoutStepBuilder(RunningBlockType.WARMUP, 180, "Soltura Inicial (3 min)", 0),
                RxWorkoutStepBuilder(RunningBlockType.RUN, 690, "Km 1–2 • Base Aeróbica Z2", 1),
                RxWorkoutStepBuilder(RunningBlockType.RUN, 650, "Km 3–4 • Ritmo Moderado Z3", 2),
                RxWorkoutStepBuilder(RunningBlockType.RUN, 620, "Km 5–6 • Tempo Run Firme Z3+", 2),
                RxWorkoutStepBuilder(RunningBlockType.RUN, 590, "Km 7–8 • Fechamento Forte Z4", 3),
                RxWorkoutStepBuilder(RunningBlockType.COOL_DOWN, 180, "Desaquecimento (3 min)", 0)
            )
        ),
        RxWorkoutTemplate(
            code = "PRO12-2KM",
            title = "Progressivo Longo 12 km (Escada 2 km)",
            subtitle = "Construção de resistência mental e fisiológica para fechar provas mais rápido que largou",
            category = RxWorkoutCategory.PROGRESSIVE,
            durationLabel = "66 min",
            distanceEstimateLabel = "12,0 km",
            blockPills = listOf(
                RxBlockPill("4km Z2", RxBlockPillType.RECOVERY_Z1_Z2),
                RxBlockPill("4km Z3", RxBlockPillType.TEMPO_Z3),
                RxBlockPill("4km Z4", RxBlockPillType.STIMULUS_Z4_Z5),
                RxBlockPill("ESF 4'", RxBlockPillType.COOLDOWN)
            ),
            steps = listOf(
                RxWorkoutStepBuilder(RunningBlockType.WARMUP, 240, "Aquecimento Leve", 0),
                RxWorkoutStepBuilder(RunningBlockType.RUN, 690, "Km 1–2 • Z2 Leve", 1),
                RxWorkoutStepBuilder(RunningBlockType.RUN, 675, "Km 3–4 • Z2 Estável", 1),
                RxWorkoutStepBuilder(RunningBlockType.RUN, 645, "Km 5–6 • Z3 Tempo", 2),
                RxWorkoutStepBuilder(RunningBlockType.RUN, 630, "Km 7–8 • Z3+ Firme", 2),
                RxWorkoutStepBuilder(RunningBlockType.RUN, 600, "Km 9–10 • Z4 Limiar", 3),
                RxWorkoutStepBuilder(RunningBlockType.RUN, 580, "Km 11–12 • Z4+ Forte", 3),
                RxWorkoutStepBuilder(RunningBlockType.COOL_DOWN, 240, "Desaquecimento", 0)
            )
        ),
        RxWorkoutTemplate(
            code = "PRO50-20|20|10",
            title = "Tempo Run 50 min (20' Z2 + 20' Z3 + 10' Z4)",
            subtitle = "Protocolo clássico de transição aeróbica-anaeróbica por tempo",
            category = RxWorkoutCategory.PROGRESSIVE,
            durationLabel = "56 min",
            distanceEstimateLabel = "~9,2 km",
            blockPills = listOf(
                RxBlockPill("AQ 3'", RxBlockPillType.WARMUP),
                RxBlockPill("20' Z2 Base", RxBlockPillType.RECOVERY_Z1_Z2),
                RxBlockPill("20' Z3 Tempo", RxBlockPillType.TEMPO_Z3),
                RxBlockPill("10' Z4 Limiar", RxBlockPillType.STIMULUS_Z4_Z5),
                RxBlockPill("ESF 3'", RxBlockPillType.COOLDOWN)
            ),
            steps = listOf(
                RxWorkoutStepBuilder(RunningBlockType.WARMUP, 180, "Aquecimento 3 min", 0),
                RxWorkoutStepBuilder(RunningBlockType.RUN, 1200, "Bloco 1 • 20 min Rodagem Z2", 1),
                RxWorkoutStepBuilder(RunningBlockType.RUN, 1200, "Bloco 2 • 20 min Tempo Run Z3", 2),
                RxWorkoutStepBuilder(RunningBlockType.RUN, 600, "Bloco 3 • 10 min Limiar Z4", 3),
                RxWorkoutStepBuilder(RunningBlockType.COOL_DOWN, 180, "Desaquecimento 3 min", 0)
            )
        ),

        // 5. RODAGEM & LONGO
        RxWorkoutTemplate(
            code = "ROD5K-Z2",
            title = "Rodagem Regenerativa / Base 5 km (Z2)",
            subtitle = "Corrida contínua abaixo do LT1 para biogênese mitocondrial e oxidação de gordura",
            category = RxWorkoutCategory.LONG_RUN,
            durationLabel = "34 min",
            distanceEstimateLabel = "5,0 km",
            blockPills = listOf(
                RxBlockPill("AQ 3'", RxBlockPillType.WARMUP),
                RxBlockPill("5 km Contínuo Z2", RxBlockPillType.RECOVERY_Z1_Z2),
                RxBlockPill("ESF 3'", RxBlockPillType.COOLDOWN)
            ),
            steps = listOf(
                RxWorkoutStepBuilder(RunningBlockType.WARMUP, 180, "Aquecimento Suave", 0),
                RxWorkoutStepBuilder(RunningBlockType.RUN, 1680, "Rodagem Base 5 km (Zona 2)", 1),
                RxWorkoutStepBuilder(RunningBlockType.COOL_DOWN, 180, "Desaquecimento", 0)
            )
        ),
        RxWorkoutTemplate(
            code = "LON10K",
            title = "Longão Aeróbico 10 km (Zona 2)",
            subtitle = "Volume aeróbico contínuo em ritmo de conversa confortável",
            category = RxWorkoutCategory.LONG_RUN,
            durationLabel = "62 min",
            distanceEstimateLabel = "10,0 km",
            blockPills = listOf(
                RxBlockPill("AQ 5'", RxBlockPillType.WARMUP),
                RxBlockPill("10 km Endurance Z2", RxBlockPillType.RECOVERY_Z1_Z2),
                RxBlockPill("ESF 5'", RxBlockPillType.COOLDOWN)
            ),
            steps = listOf(
                RxWorkoutStepBuilder(RunningBlockType.WARMUP, 300, "Aquecimento Leve", 0),
                RxWorkoutStepBuilder(RunningBlockType.RUN, 3300, "Longão 10 km Contínuo Z2", 1),
                RxWorkoutStepBuilder(RunningBlockType.COOL_DOWN, 300, "Desaquecimento", 0)
            )
        ),
        RxWorkoutTemplate(
            code = "LON15K",
            title = "Longão Endurance 15 km (Base 21K)",
            subtitle = "Desenvolvimento de capilarização muscular e eficiência lipídica",
            category = RxWorkoutCategory.LONG_RUN,
            durationLabel = "90 min",
            distanceEstimateLabel = "15,0 km",
            blockPills = listOf(
                RxBlockPill("AQ 5'", RxBlockPillType.WARMUP),
                RxBlockPill("15 km Endurance Z2", RxBlockPillType.RECOVERY_Z1_Z2),
                RxBlockPill("ESF 5'", RxBlockPillType.COOLDOWN)
            ),
            steps = listOf(
                RxWorkoutStepBuilder(RunningBlockType.WARMUP, 300, "Aquecimento Leve", 0),
                RxWorkoutStepBuilder(RunningBlockType.RUN, 4800, "Longão 15 km Zona 2", 1),
                RxWorkoutStepBuilder(RunningBlockType.COOL_DOWN, 300, "Desaquecimento", 0)
            )
        ),
        RxWorkoutTemplate(
            code = "LON21K",
            title = "Simulado / Longão Meia Maratona 21 km",
            subtitle = "Sessão específica de resistência para 21,1 km com controle de FC",
            category = RxWorkoutCategory.LONG_RUN,
            durationLabel = "125 min",
            distanceEstimateLabel = "21,1 km",
            blockPills = listOf(
                RxBlockPill("AQ 5'", RxBlockPillType.WARMUP),
                RxBlockPill("16 km Z2 Estável", RxBlockPillType.RECOVERY_Z1_Z2),
                RxBlockPill("5 km Z3 Firme", RxBlockPillType.TEMPO_Z3),
                RxBlockPill("ESF 5'", RxBlockPillType.COOLDOWN)
            ),
            steps = listOf(
                RxWorkoutStepBuilder(RunningBlockType.WARMUP, 300, "Aquecimento Leve", 0),
                RxWorkoutStepBuilder(RunningBlockType.RUN, 5100, "Bloco 1 • 16 km Base Z2", 1),
                RxWorkoutStepBuilder(RunningBlockType.RUN, 1500, "Bloco 2 • 5 km Firme Z3", 2),
                RxWorkoutStepBuilder(RunningBlockType.COOL_DOWN, 300, "Desaquecimento", 0)
            )
        ),

        // 6. RUN TO MAINTAIN (INSPIRAÇÃO RUNNA • MANUTENÇÃO HÍBRIDA MAX GYM + MAX RUN)
        RxWorkoutTemplate(
            code = "RTM-Z2-30M",
            title = "Run to Maintain • Dia 1: Rodagem Z2 (Zero Fadiga)",
            subtitle = "Mantém o VO2máx e a saúde cardiovascular sem interferir na hipertrofia do MAX GYM",
            category = RxWorkoutCategory.RUN_TO_MAINTAIN,
            durationLabel = "30 min",
            distanceEstimateLabel = "~4,8 km",
            blockPills = listOf(
                RxBlockPill("AQ 4'", RxBlockPillType.WARMUP),
                RxBlockPill("22' Z2 Confortável", RxBlockPillType.RECOVERY_Z1_Z2),
                RxBlockPill("ESF 4'", RxBlockPillType.COOLDOWN)
            ),
            steps = listOf(
                RxWorkoutStepBuilder(RunningBlockType.WARMUP, 240, "Aquecimento Solto", 0),
                RxWorkoutStepBuilder(RunningBlockType.RUN, 1320, "Rodagem Manutenção Z2 (<= LT1)", 1),
                RxWorkoutStepBuilder(RunningBlockType.COOL_DOWN, 240, "Desaquecimento", 0)
            )
        ),
        RxWorkoutTemplate(
            code = "RTM-FARTLEK-6X1M",
            title = "Run to Maintain • Dia 2: Estímulo Neuromotor 6× 1'",
            subtitle = "Toque curto de velocidade para preservar economia de corrida sem estresse articular",
            category = RxWorkoutCategory.RUN_TO_MAINTAIN,
            durationLabel = "28 min",
            distanceEstimateLabel = "~4,5 km",
            blockPills = listOf(
                RxBlockPill("AQ 6'", RxBlockPillType.WARMUP),
                RxBlockPill("6× 1' Forte Z4", RxBlockPillType.STIMULUS_Z4_Z5),
                RxBlockPill("6× 1'30\" Trote Z1", RxBlockPillType.RECOVERY_Z1_Z2),
                RxBlockPill("ESF 7'", RxBlockPillType.COOLDOWN)
            ),
            steps = repeatIntervals(
                reps = 6,
                workSec = 60,
                workLabel = "Estímulo Neuromotor 1 min Z4",
                workZone = 3,
                restSec = 90,
                restLabel = "Trote Solto 1'30\" Z1",
                warmupSec = 360,
                cooldownSec = 420
            )
        ),
        RxWorkoutTemplate(
            code = "RTM-LONGO-45M",
            title = "Run to Maintain • Dia 3: Longo Confortável 45 min",
            subtitle = "Sessão aeróbica prazerosa de fim de semana sem cobrança de pace máximo",
            category = RxWorkoutCategory.RUN_TO_MAINTAIN,
            durationLabel = "45 min",
            distanceEstimateLabel = "~7,2 km",
            blockPills = listOf(
                RxBlockPill("AQ 5'", RxBlockPillType.WARMUP),
                RxBlockPill("35' Z2 Fluido", RxBlockPillType.RECOVERY_Z1_Z2),
                RxBlockPill("ESF 5'", RxBlockPillType.COOLDOWN)
            ),
            steps = listOf(
                RxWorkoutStepBuilder(RunningBlockType.WARMUP, 300, "Aquecimento Leve", 0),
                RxWorkoutStepBuilder(RunningBlockType.RUN, 2100, "Longo Confortável Z2 (35 min)", 1),
                RxWorkoutStepBuilder(RunningBlockType.COOL_DOWN, 300, "Desaquecimento", 0)
            )
        )
    )
}

fun formatPaceSec(paceSec: Int): String {
    val clean = paceSec.coerceIn(120, 1200)
    return String.format(Locale.US, "%d'%02d\"", clean / 60, clean % 60)
}

fun formatRaceDuration(totalSec: Int): String {
    val clean = totalSec.coerceAtLeast(60)
    val h = clean / 3600
    val m = (clean % 3600) / 60
    val s = clean % 60
    return if (h > 0) {
        String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    } else {
        String.format(Locale.US, "%02d:%02d", m, s)
    }
}

/**
 * Seção Integrada ao Modal "Alterar ▾" (Configuração da Corrida):
 * Permite acessar o Catálogo RX-PRO & Run to Maintain ou recalibrar a Anamnese sem poluir a tela principal do botão START!
 */
@Composable
fun RunConfigModalRxAndAnamnesisSection(
    selectedRxWorkout: RxWorkoutTemplate?,
    isRunToMaintainActive: Boolean,
    calibration: PhysiologicalCalibrationSummary,
    onOpenRxLibraryModal: () -> Unit,
    onClearRxWorkout: () -> Unit,
    onActivateRunToMaintain: () -> Unit,
    onOpenAnamnesisModal: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "TREINOS ESTRUTURADOS (RX-PRO & MANUTENÇÃO)",
                color = RunColors.textSecondary,
                fontSize = 12.sp,
                lineHeight = 15.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "Calibrar Zonas",
                color = Color(0xFF00D4FF),
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        AudioSynth.playTickSound()
                        onOpenAnamnesisModal()
                    }
                    .padding(horizontal = 6.dp, vertical = 6.dp)
                    .testTag("btn_open_runner_anamnesis")
            )
        }

        // Card de acesso ao Catálogo RX-PRO (ou resumo do treino RX selecionado)
        Surface(
            onClick = {
                AudioSynth.playTickSound()
                onOpenRxLibraryModal()
            },
            color = if (selectedRxWorkout != null) Color(0xFFFF6B00).copy(alpha = 0.14f) else Color(0xFF151922),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(
                width = if (selectedRxWorkout != null) 1.5.dp else 1.dp,
                color = if (selectedRxWorkout != null) Color(0xFFFF9E40) else Color(0xFFFF6B00).copy(alpha = 0.5f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("btn_open_rx_workout_library")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (selectedRxWorkout != null) {
                                "Ativo: ${selectedRxWorkout.code} • ${selectedRxWorkout.title}"
                            } else {
                                "Catálogo RX-PRO & Run to Maintain"
                            },
                            color = if (selectedRxWorkout != null) Color.White else Color(0xFFFF9E40),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = if (selectedRxWorkout != null) {
                                "${selectedRxWorkout.durationLabel} • ${selectedRxWorkout.distanceEstimateLabel} • Toque para trocar"
                            } else {
                                "Fartlek, Intervalados Z4/Z5, Progressivos, Longos e Manutenção"
                            },
                            color = Color(0xFF94A3B8),
                            fontSize = 11.5.sp
                        )
                    }

                    if (selectedRxWorkout != null) {
                        Surface(
                            onClick = {
                                AudioSynth.playTickSound()
                                onClearRxWorkout()
                            },
                            color = Color(0xFF262D3D),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "Limpar",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    } else {
                        Surface(
                            color = Color(0xFFFF6B00).copy(alpha = 0.2f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "Explorar ▸",
                                color = Color(0xFFFF9E40),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                if (selectedRxWorkout != null) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        selectedRxWorkout.blockPills.forEach { pill ->
                            RxBlockPillBadge(pill = pill)
                        }
                    }
                }
            }
        }

        // Atalho Rápido Modo Manutenção (Run to Maintain 3x/semana)
        Surface(
            onClick = {
                AudioSynth.playTickSound()
                onActivateRunToMaintain()
            },
            color = if (isRunToMaintainActive) Color(0xFFA855F7).copy(alpha = 0.18f) else Color(0xFF151922),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(
                width = if (isRunToMaintainActive) 1.5.dp else 1.dp,
                color = if (isRunToMaintainActive) Color(0xFFA855F7) else Color(0xFF232A38)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Modo Run to Maintain (3x/Semana)",
                        color = if (isRunToMaintainActive) Color.White else Color(0xFFCBD5E1),
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Manutenção aeróbica sem interferir na hipertrofia • LT2 ${formatPaceSec(calibration.lt2PaceSecPerKm)}/km",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp
                    )
                }
                Text(
                    text = if (isRunToMaintainActive) "✓ Ativo" else "Ativar",
                    color = if (isRunToMaintainActive) Color(0xFFD8B4FE) else Color(0xFFA855F7),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }
    }
}

/**
 * Card de Projeção de Prova (5K / 10K / 21,1K) + VAM / LT2 / Zonas Z1–Z5 para a aba PERFIL (`ProfileScreen.kt`).
 */
@Composable
fun ProfileRunningPredictorAndZonesSection(
    anamnesisProfile: RunnerAnamnesisProfile,
    calibration: PhysiologicalCalibrationSummary,
    onOpenAnamnesisWizard: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Resumo do Perfil Fisiológico (Objetivo, Nível, VAM e LT2)
        Surface(
            color = Color(0xFF181C26),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, Color(0xFF262B38)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "FOCO ATUAL & CAPACIDADE AERÓBICA",
                            color = Color(0xFF00E5FF),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = "${anamnesisProfile.goal.title} • ${anamnesisProfile.level.label}",
                            color = Color.White,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${anamnesisProfile.availableDaysPerWeek} dias/sem • Teto ${anamnesisProfile.maxWeeklyVolumeKm} km/sem",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp
                        )
                    }
                    Surface(
                        color = Color(0xFF00E5FF).copy(alpha = 0.14f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.45f))
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "VAM",
                                color = Color(0xFF00E5FF),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                text = "${String.format(Locale.US, "%.1f", calibration.vamKmh).replace('.', ',')} km/h",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }

                // Calculadora de Projeção de Prova (5K, 10K, 21,1K)
                Text(
                    text = "PROJEÇÃO DE PROVA (RACE PREDICTOR)",
                    color = Color(0xFF94A3B8),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0D0F14), RoundedCornerShape(10.dp))
                        .padding(vertical = 10.dp, horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    calibration.predictions.forEachIndexed { index, pred ->
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = pred.distanceLabel.uppercase(Locale.getDefault()),
                                color = Color(0xFF94A3B8),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false
                            )
                            Text(
                                text = pred.formattedTime,
                                color = when (index) {
                                    0 -> Color(0xFF00E676)
                                    1 -> Color(0xFF00E5FF)
                                    else -> Color(0xFFFFD600)
                                },
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Black,
                                maxLines = 1,
                                softWrap = false
                            )
                            Text(
                                text = pred.formattedPace,
                                color = Color(0xFFCBD5E1),
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                        if (index < calibration.predictions.lastIndex) {
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(30.dp)
                                    .background(Color(0xFF262D3D))
                            )
                        }
                    }
                }
            }
        }

        // Botão Principal para Abrir o Wizard de 3 Passos de Anamnese e Calibração
        Button(
            onClick = {
                AudioSynth.playTickSound()
                onOpenAnamnesisWizard()
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00B0FF)),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("btn_open_anamnesis_wizard_profile")
        ) {
            Icon(
                imageVector = Icons.Default.EmojiEvents,
                contentDescription = null,
                tint = Color(0xFF06101E),
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "CALIBRAR PERFIL & ANAMNESE DE CORRIDA",
                color = Color(0xFF06101E),
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
fun RxBlockPillBadge(pill: RxBlockPill) {
    Surface(
        color = pill.type.bgColor.copy(alpha = 0.16f),
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, pill.type.bgColor.copy(alpha = 0.45f))
    ) {
        Text(
            text = pill.label,
            color = pill.type.textColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
        )
    }
}

/**
 * MODAL 1: WIZARD DE ANAMNESE FISIOLÓGICA EM 3 ETAPAS ESTILO RUNNA
 * - Etapa 1 de 3: "Qual é o seu foco principal?" (5 Cards Largos Full-Width de 68.dp com Badge, Título, Subtítulo e Check)
 * - Etapa 2 de 3: "Sua rotina e experiência" (3 Cards Largos de Nível + Seletor Amplo de Dias 2x..6x + Controle Ergonômico de Volume Semanal)
 * - Etapa 3 de 3: "Calibração Fisiológica & Ritmo" (Teste 3.000m / 5 km + Cronômetro interativo + VAM, LT2, Projeção 5K/10K/21K e Zonas Z1–Z5)
 */
@Composable
fun RunnerAnamnesisModal(
    initialProfile: RunnerAnamnesisProfile,
    athleteAge: Int,
    runningHistory: List<RunningSession>,
    baselinePaceMinPerKm: Float?,
    calibratedMaxHrBpm: Int? = null,
    onSaveProfile: (RunnerAnamnesisProfile, Float, String) -> Unit,
    onDismiss: () -> Unit
) {
    var currentStep by remember { mutableIntStateOf(1) }
    var selectedGoal by remember { mutableStateOf(initialProfile.goal) }
    var selectedLevel by remember { mutableStateOf(initialProfile.level) }
    var selectedWeeklyKm by remember { mutableIntStateOf(initialProfile.maxWeeklyVolumeKm.coerceIn(15, 70)) }
    var selectedDaysPerWeek by remember { mutableIntStateOf(initialProfile.availableDaysPerWeek.coerceIn(2, 6)) }
    var selectedProtocol by remember { mutableStateOf(initialProfile.testProtocol) }

    var testTotalSeconds by remember {
        mutableIntStateOf(initialProfile.testTimeSeconds.coerceIn(480, 4200))
    }

    val draftProfile = remember(
        selectedGoal,
        selectedLevel,
        selectedWeeklyKm,
        selectedDaysPerWeek,
        selectedProtocol,
        testTotalSeconds
    ) {
        RunnerAnamnesisProfile(
            goal = selectedGoal,
            level = selectedLevel,
            maxWeeklyVolumeKm = selectedWeeklyKm,
            availableDaysPerWeek = selectedDaysPerWeek,
            testProtocol = selectedProtocol,
            testTimeSeconds = testTotalSeconds,
            isConfigured = true
        )
    }

    val liveCalibration = remember(draftProfile, athleteAge, runningHistory, baselinePaceMinPerKm, calibratedMaxHrBpm) {
        RunnerAnamnesisManager.calculateCalibration(
            profile = draftProfile,
            athleteAge = athleteAge,
            history = runningHistory,
            baselinePaceMinPerKm = baselinePaceMinPerKm,
            calibratedMaxHrBpm = calibratedMaxHrBpm
        )
    }

    val stepTitle = when (currentStep) {
        1 -> "Qual é o seu foco principal?"
        2 -> "Sua rotina e experiência"
        else -> "Calibração Fisiológica & Ritmo"
    }
    val stepSubtitle = when (currentStep) {
        1 -> "Escolha sua meta atual para estruturarmos seu plano e zonas de treino."
        2 -> "Ajuste seu nível, frequência semanal e teto seguro de quilometragem."
        else -> "Informe seu tempo recente de 3.000m ou 5 km para calibrar VAM e Limiar LT2."
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        ProvideRunFontScale {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .heightIn(max = 740.dp)
                    .navigationBarsPadding(),
                shape = RoundedCornerShape(24.dp),
                color = Color(0xFF0D0F14),
                border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.35f))
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // TOPO DO WIZARD: Barra de Progresso Segmentada + Etapa X de 3
                    Surface(
                        color = Color(0xFF151922),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    color = Color(0xFF00E5FF).copy(alpha = 0.16f),
                                    shape = RoundedCornerShape(50),
                                    border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.45f))
                                ) {
                                    Text(
                                        text = "ETAPA $currentStep DE 3",
                                        color = Color(0xFF00E5FF),
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = 0.8.sp,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        AudioSynth.playTickSound()
                                        onDismiss()
                                    },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(Color.White.copy(alpha = 0.08f), CircleShape)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Fechar Wizard",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            // Barra de Progresso em 3 Segmentos Estilo Runna
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                (1..3).forEach { stepIdx ->
                                    val isDoneOrActive = stepIdx <= currentStep
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(5.dp)
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(
                                                if (isDoneOrActive) Color(0xFF00E5FF) else Color(0xFF262D3D)
                                            )
                                    )
                                }
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = stepTitle,
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Black
                                )
                                Text(
                                    text = stepSubtitle,
                                    color = Color(0xFF94A3B8),
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }

                    // CORPO DA ETAPA ATUAL
                    Column(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 18.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        when (currentStep) {
                            // =========================================================================
                            // ETAPA 1 DE 3: "Qual é o seu foco principal?" (5 Cards Largos Full-Width)
                            // =========================================================================
                            1 -> {
                                RunnerGoalOption.entries.forEach { goal ->
                                    val isSelected = selectedGoal == goal
                                    Surface(
                                        onClick = {
                                            selectedGoal = goal
                                            if (goal == RunnerGoalOption.GOAL_MAINTAIN) {
                                                selectedDaysPerWeek = 3
                                            }
                                            AudioSynth.playTickSound()
                                        },
                                        color = if (isSelected) Color(0xFF00E5FF).copy(alpha = 0.12f) else Color(0xFF151922),
                                        shape = RoundedCornerShape(16.dp),
                                        border = BorderStroke(
                                            width = if (isSelected) 1.5.dp else 1.dp,
                                            color = if (isSelected) Color(0xFF00E5FF) else Color(0xFF262D3D)
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 68.dp)
                                            .testTag("wizard_goal_card_${goal.id}")
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 14.dp, vertical = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            // Ícone / Badge Vetorial à Esquerda
                                            Box(
                                                modifier = Modifier
                                                    .size(44.dp)
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(
                                                        if (isSelected) Color(0xFF00E5FF).copy(alpha = 0.22f)
                                                        else goal.accentColor.copy(alpha = 0.14f)
                                                    )
                                                    .border(
                                                        1.dp,
                                                        if (isSelected) Color(0xFF00E5FF) else goal.accentColor.copy(alpha = 0.45f),
                                                        RoundedCornerShape(12.dp)
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = goal.iconBadge,
                                                    color = if (isSelected) Color(0xFF00E5FF) else goal.accentColor,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Black
                                                )
                                            }

                                            // Título (15.sp Bold) + Subtítulo (12.sp cinza claro)
                                            Column(
                                                modifier = Modifier.weight(1f),
                                                verticalArrangement = Arrangement.spacedBy(2.dp)
                                            ) {
                                                Text(
                                                    text = goal.title,
                                                    color = Color.White,
                                                    fontSize = 15.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = goal.subtitle,
                                                    color = Color(0xFFCBD5E1),
                                                    fontSize = 12.sp,
                                                    lineHeight = 15.sp,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }

                                            // Check ✓ Ciano Neon quando selecionado
                                            Box(
                                                modifier = Modifier
                                                    .size(26.dp)
                                                    .clip(CircleShape)
                                                    .background(
                                                        if (isSelected) Color(0xFF00E5FF) else Color.Transparent
                                                    )
                                                    .border(
                                                        width = 1.5.dp,
                                                        color = if (isSelected) Color(0xFF00E5FF) else Color(0xFF475569),
                                                        shape = CircleShape
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (isSelected) {
                                                    Text(
                                                        text = "✓",
                                                        color = Color(0xFF06101E),
                                                        fontSize = 14.sp,
                                                        fontWeight = FontWeight.Black
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // =========================================================================
                            // ETAPA 2 DE 3: "Sua rotina e experiência" (Cards Largos + Seletor Amplo)
                            // =========================================================================
                            2 -> {
                                // A) Nível de Experiência (3 Cards Full-Width elegantes)
                                Text(
                                    text = "NÍVEL DE EXPERIÊNCIA NA CORRIDA",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 0.6.sp
                                )

                                RunnerLevelOption.entries.forEach { lvl ->
                                    val isSel = selectedLevel == lvl
                                    Surface(
                                        onClick = {
                                            selectedLevel = lvl
                                            AudioSynth.playTickSound()
                                        },
                                        color = if (isSel) Color(0xFF00E5FF).copy(alpha = 0.12f) else Color(0xFF151922),
                                        shape = RoundedCornerShape(14.dp),
                                        border = BorderStroke(
                                            width = if (isSel) 1.5.dp else 1.dp,
                                            color = if (isSel) Color(0xFF00E5FF) else Color(0xFF262D3D)
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 62.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 14.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(38.dp)
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(
                                                        if (isSel) Color(0xFF00E5FF).copy(alpha = 0.22f)
                                                        else Color(0xFF262D3D)
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = lvl.badge,
                                                    color = if (isSel) Color(0xFF00E5FF) else Color(0xFFCBD5E1),
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Black
                                                )
                                            }
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = lvl.label,
                                                    color = Color.White,
                                                    fontSize = 14.5.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = lvl.subtitle,
                                                    color = Color(0xFF94A3B8),
                                                    fontSize = 11.5.sp,
                                                    lineHeight = 14.sp
                                                )
                                            }
                                            if (isSel) {
                                                Text(
                                                    text = "✓",
                                                    color = Color(0xFF00E5FF),
                                                    fontSize = 16.sp,
                                                    fontWeight = FontWeight.Black
                                                )
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                // B) Frequência Semanal (5 Botões Largos distribuídos igualmente 2x..6x)
                                Surface(
                                    color = Color(0xFF151922),
                                    shape = RoundedCornerShape(16.dp),
                                    border = BorderStroke(1.dp, Color(0xFF262D3D)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "DIAS DISPONÍVEIS / SEMANA",
                                                color = Color(0xFF94A3B8),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Black,
                                                letterSpacing = 0.5.sp
                                            )
                                            Text(
                                                text = "$selectedDaysPerWeek treinos/sem",
                                                color = Color(0xFF00E5FF),
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Black
                                            )
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            (2..6).forEach { days ->
                                                val isSel = selectedDaysPerWeek == days
                                                Surface(
                                                    onClick = {
                                                        selectedDaysPerWeek = days
                                                        AudioSynth.playTickSound()
                                                    },
                                                    color = if (isSel) Color(0xFF00E5FF).copy(alpha = 0.2f) else Color(0xFF0D0F14),
                                                    shape = RoundedCornerShape(12.dp),
                                                    border = BorderStroke(
                                                        width = if (isSel) 1.5.dp else 1.dp,
                                                        color = if (isSel) Color(0xFF00E5FF) else Color(0xFF262D3D)
                                                    ),
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .height(50.dp)
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Text(
                                                            text = "${days}x",
                                                            color = if (isSel) Color.White else Color(0xFF94A3B8),
                                                            fontSize = 15.sp,
                                                            fontWeight = FontWeight.Black
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                // C) Volume Máximo Semanal Pretendido (Slider + Botões Amplos -5/+5 km)
                                Surface(
                                    color = Color(0xFF151922),
                                    shape = RoundedCornerShape(16.dp),
                                    border = BorderStroke(1.dp, Color(0xFF262D3D)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "VOLUME MÁXIMO SEMANAL",
                                                color = Color(0xFF94A3B8),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Black,
                                                letterSpacing = 0.5.sp
                                            )
                                            Text(
                                                text = "$selectedWeeklyKm km / sem",
                                                color = Color(0xFF00E676),
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Black
                                            )
                                        }

                                        Slider(
                                            value = selectedWeeklyKm.toFloat(),
                                            onValueChange = { v ->
                                                val stepped = ((v / 5f).roundToInt() * 5).coerceIn(15, 70)
                                                if (stepped != selectedWeeklyKm) {
                                                    selectedWeeklyKm = stepped
                                                }
                                            },
                                            valueRange = 15f..70f,
                                            steps = 10,
                                            colors = SliderDefaults.colors(
                                                thumbColor = Color(0xFF00E676),
                                                activeTrackColor = Color(0xFF00E676),
                                                inactiveTrackColor = Color(0xFF262D3D)
                                            )
                                        )

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            listOf(15, 25, 40, 55).forEach { presetKm ->
                                                val isSel = selectedWeeklyKm == presetKm
                                                Surface(
                                                    onClick = {
                                                        selectedWeeklyKm = presetKm
                                                        AudioSynth.playTickSound()
                                                    },
                                                    color = if (isSel) Color(0xFF00E676).copy(alpha = 0.18f) else Color(0xFF0D0F14),
                                                    shape = RoundedCornerShape(10.dp),
                                                    border = BorderStroke(
                                                        width = if (isSel) 1.5.dp else 1.dp,
                                                        color = if (isSel) Color(0xFF00E676) else Color(0xFF262D3D)
                                                    ),
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .height(42.dp)
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Text(
                                                            text = "${presetKm} km",
                                                            color = if (isSel) Color.White else Color(0xFF94A3B8),
                                                            fontSize = 12.5.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // =========================================================================
                            // ETAPA 3 DE 3: "Calibração Fisiológica & Ritmo" (Teste 3K/5K + VAM + Z1-Z5)
                            // =========================================================================
                            else -> {
                                // 1. Escolha do Protocolo (2 Cards Largos Lado a Lado)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    FieldTestProtocol.entries.forEach { proto ->
                                        val isSel = selectedProtocol == proto
                                        Surface(
                                            onClick = {
                                                if (selectedProtocol != proto) {
                                                    selectedProtocol = proto
                                                    testTotalSeconds = if (proto == FieldTestProtocol.TEST_3000M) {
                                                        930 // 15:30 nos 3K
                                                    } else {
                                                        1650 // 27:30 nos 5K
                                                    }
                                                    AudioSynth.playTickSound()
                                                }
                                            },
                                            color = if (isSel) Color(0xFF00E5FF).copy(alpha = 0.16f) else Color(0xFF151922),
                                            shape = RoundedCornerShape(14.dp),
                                            border = BorderStroke(
                                                width = if (isSel) 1.5.dp else 1.dp,
                                                color = if (isSel) Color(0xFF00E5FF) else Color(0xFF262D3D)
                                            ),
                                            modifier = Modifier
                                                .weight(1f)
                                                .heightIn(min = 58.dp)
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.Center
                                            ) {
                                                Text(
                                                    text = if (proto == FieldTestProtocol.TEST_3000M) "Teste 3.000m" else "Melhor 5 km",
                                                    color = Color.White,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Black
                                                )
                                                Text(
                                                    text = if (proto == FieldTestProtocol.TEST_3000M) "Velocidade Crítica / TAF" else "Referência Recente",
                                                    color = if (isSel) Color(0xFF00E5FF) else Color(0xFF94A3B8),
                                                    fontSize = 11.sp,
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                    }
                                }

                                // 2. Cronômetro Interativo do Teste (Botões largos -15s / +15s e Display Grande)
                                val displayMins = testTotalSeconds / 60
                                val displaySecs = testTotalSeconds % 60
                                val testPaceSecPerKm = (testTotalSeconds * 1000f / selectedProtocol.distanceMeters).roundToInt()

                                Surface(
                                    color = Color(0xFF151922),
                                    shape = RoundedCornerShape(16.dp),
                                    border = BorderStroke(1.dp, Color(0xFF262D3D)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Text(
                                            text = "TEMPO EM ${selectedProtocol.distanceMeters} METROS",
                                            color = Color(0xFF94A3B8),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Black,
                                            letterSpacing = 0.6.sp
                                        )

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Surface(
                                                onClick = {
                                                    testTotalSeconds = (testTotalSeconds - 15).coerceAtLeast(480)
                                                    AudioSynth.playTickSound()
                                                },
                                                color = Color(0xFF0D0F14),
                                                shape = RoundedCornerShape(12.dp),
                                                border = BorderStroke(1.dp, Color(0xFF262D3D)),
                                                modifier = Modifier.size(width = 68.dp, height = 48.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text(
                                                        text = "-15s",
                                                        color = Color(0xFF00E5FF),
                                                        fontSize = 14.sp,
                                                        fontWeight = FontWeight.Black
                                                    )
                                                }
                                            }

                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text(
                                                    text = String.format(Locale.US, "%02d:%02d", displayMins, displaySecs),
                                                    color = Color.White,
                                                    fontSize = 30.sp,
                                                    fontWeight = FontWeight.Black
                                                )
                                                Text(
                                                    text = "Ritmo médio: ${formatPaceSec(testPaceSecPerKm)}/km",
                                                    color = Color(0xFF00E676),
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }

                                            Surface(
                                                onClick = {
                                                    testTotalSeconds = (testTotalSeconds + 15).coerceAtMost(4200)
                                                    AudioSynth.playTickSound()
                                                },
                                                color = Color(0xFF0D0F14),
                                                shape = RoundedCornerShape(12.dp),
                                                border = BorderStroke(1.dp, Color(0xFF262D3D)),
                                                modifier = Modifier.size(width = 68.dp, height = 48.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text(
                                                        text = "+15s",
                                                        color = Color(0xFF00E5FF),
                                                        fontSize = 14.sp,
                                                        fontWeight = FontWeight.Black
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                // 3. Painel de Calibração Fisiológica ao Vivo (VAM, LT2, Projeção 5K/10K/21K e Zonas Z1–Z5)
                                Surface(
                                    color = Color(0xFF151922),
                                    shape = RoundedCornerShape(16.dp),
                                    border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.35f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(Color(0xFF0D0F14), RoundedCornerShape(10.dp))
                                                .padding(vertical = 10.dp, horizontal = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(
                                                modifier = Modifier.weight(1f),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                Text("VAM", color = Color(0xFF94A3B8), fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                                                Text(
                                                    text = "${String.format(Locale.US, "%.1f", liveCalibration.vamKmh).replace('.', ',')} km/h",
                                                    color = Color(0xFF00E5FF),
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Black
                                                )
                                                Text(
                                                    text = "${formatPaceSec(liveCalibration.vamPaceSecPerKm)}/km",
                                                    color = Color(0xFFCBD5E1),
                                                    fontSize = 10.sp
                                                )
                                            }
                                            Box(modifier = Modifier.width(1.dp).height(28.dp).background(Color(0xFF262D3D)))
                                            Column(
                                                modifier = Modifier.weight(1f),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                Text("LIMIAR LT2", color = Color(0xFF94A3B8), fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                                                Text(
                                                    text = "${formatPaceSec(liveCalibration.lt2PaceSecPerKm)}/km",
                                                    color = Color(0xFF00E676),
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Black
                                                )
                                                Text(
                                                    text = "${liveCalibration.lt2Bpm} bpm",
                                                    color = Color(0xFFCBD5E1),
                                                    fontSize = 10.sp
                                                )
                                            }
                                            Box(modifier = Modifier.width(1.dp).height(28.dp).background(Color(0xFF262D3D)))
                                            Column(
                                                modifier = Modifier.weight(1f),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                Text("BASE LT1", color = Color(0xFF94A3B8), fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                                                Text(
                                                    text = "${formatPaceSec(liveCalibration.lt1PaceSecPerKm)}/km",
                                                    color = Color(0xFFFFD600),
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Black
                                                )
                                                Text(
                                                    text = "${liveCalibration.lt1Bpm} bpm",
                                                    color = Color(0xFFCBD5E1),
                                                    fontSize = 10.sp
                                                )
                                            }
                                        }

                                        // Zonas Z1 a Z5 Calibradas
                                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                            liveCalibration.zones.forEach { z ->
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .background(Color(0xFF0D0F14), RoundedCornerShape(8.dp))
                                                        .padding(horizontal = 10.dp, vertical = 7.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                        modifier = Modifier.weight(1f)
                                                    ) {
                                                        Text(
                                                            text = z.zoneCode,
                                                            color = z.color,
                                                            fontSize = 11.5.sp,
                                                            fontWeight = FontWeight.Black
                                                        )
                                                        Text(
                                                            text = z.title,
                                                            color = Color.White,
                                                            fontSize = 11.5.sp,
                                                            fontWeight = FontWeight.SemiBold,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                    Text(
                                                        text = "${z.formattedPaceRange()} • ${z.minBpm}–${z.maxBpm} bpm",
                                                        color = z.color,
                                                        fontSize = 10.5.sp,
                                                        fontWeight = FontWeight.Black,
                                                        maxLines = 1,
                                                        softWrap = false
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // RODAPÉ DE NAVEGAÇÃO DO WIZARD (Voltar / Continuar / Salvar e Calibrar)
                    Surface(
                        color = Color(0xFF151922),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    AudioSynth.playTickSound()
                                    if (currentStep > 1) {
                                        currentStep--
                                    } else {
                                        onDismiss()
                                    }
                                },
                                modifier = Modifier
                                    .weight(0.95f)
                                    .height(50.dp),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f))
                            ) {
                                Text(
                                    text = if (currentStep > 1) "← VOLTAR" else "FECHAR",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }

                            Button(
                                onClick = {
                                    AudioSynth.playTickSound()
                                    if (currentStep < 3) {
                                        currentStep++
                                    } else {
                                        val newBaselinePaceMin = (liveCalibration.lt2PaceSecPerKm / 60f).coerceIn(3.2f, 10.5f)
                                        onSaveProfile(
                                            draftProfile,
                                            newBaselinePaceMin,
                                            draftProfile.goal.recommendedProgramId
                                        )
                                    }
                                },
                                modifier = Modifier
                                    .weight(1.45f)
                                    .height(50.dp)
                                    .testTag("btn_save_anamnesis_profile"),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF))
                            ) {
                                Text(
                                    text = if (currentStep < 3) "CONTINUAR →" else "SALVAR E CALIBRAR ✓",
                                    color = Color(0xFF06101E),
                                    fontWeight = FontWeight.Black,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * MODAL 2: BIBLIOTECA DE TREINOS ESTRUTURADOS (PADRÃO RX-PRO) + RUN TO MAINTAIN
 */
@Composable
fun RxProWorkoutLibraryModal(
    calibration: PhysiologicalCalibrationSummary,
    selectedRxCode: String?,
    onSelectRxWorkout: (RxWorkoutTemplate) -> Unit,
    onSelectStandardProgramCatalog: () -> Unit,
    onDismiss: () -> Unit
) {
    var selectedCategory by remember { mutableStateOf(RxWorkoutCategory.ALL) }

    val filteredWorkouts = remember(selectedCategory) {
        if (selectedCategory == RxWorkoutCategory.ALL) {
            RunnerAnamnesisManager.RX_PRO_WORKOUT_CATALOG
        } else {
            RunnerAnamnesisManager.RX_PRO_WORKOUT_CATALOG.filter { it.category == selectedCategory }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        ProvideRunFontScale {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .heightIn(max = 720.dp)
                    .navigationBarsPadding(),
                shape = RoundedCornerShape(22.dp),
                color = Color(0xFF0D0F14),
                border = BorderStroke(1.dp, Color(0xFFFF6B00).copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Header
                    Surface(
                        color = Color(0xFF151922),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "BANCO DE TREINOS ESTRUTURADOS (RX-PRO)",
                                    color = Color(0xFFFF9E40),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 0.5.sp
                                )
                                Text(
                                    text = "Ritmos calibrados p/ seu Limiar LT2 (${formatPaceSec(calibration.lt2PaceSecPerKm)}/km)",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.5.sp
                                )
                            }
                            IconButton(
                                onClick = {
                                    AudioSynth.playTickSound()
                                    onDismiss()
                                },
                                modifier = Modifier
                                    .size(38.dp)
                                    .background(Color.White.copy(alpha = 0.08f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Fechar Banco de Treinos",
                                    tint = Color.White
                                )
                            }
                        }
                    }

                    // Abas de Categoria
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(RxWorkoutCategory.entries) { cat ->
                            val isSel = selectedCategory == cat
                            Surface(
                                onClick = {
                                    selectedCategory = cat
                                    AudioSynth.playTickSound()
                                },
                                color = if (isSel) cat.accentColor.copy(alpha = 0.22f) else Color(0xFF151922),
                                shape = RoundedCornerShape(50),
                                border = BorderStroke(
                                    width = if (isSel) 1.5.dp else 1.dp,
                                    color = if (isSel) cat.accentColor else Color(0xFF262D3D)
                                )
                            ) {
                                Text(
                                    text = cat.label,
                                    color = if (isSel) Color.White else Color(0xFFCBD5E1),
                                    fontSize = 12.sp,
                                    fontWeight = if (isSel) FontWeight.Black else FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }

                    // Lista de Treinos RX-PRO com pílulas coloridas para cada bloco
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 14.dp)
                    ) {
                        items(filteredWorkouts, key = { it.code }) { workout ->
                            val isSelected = selectedRxCode.equals(workout.code, ignoreCase = true)
                            Surface(
                                onClick = {
                                    AudioSynth.playTickSound()
                                    onSelectRxWorkout(workout)
                                },
                                color = if (isSelected) workout.category.accentColor.copy(alpha = 0.14f) else Color(0xFF151922),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) workout.category.accentColor else Color(0xFF262D3D)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("rx_workout_item_${workout.code}")
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(7.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Surface(
                                                color = workout.category.accentColor.copy(alpha = 0.22f),
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text(
                                                    text = workout.code,
                                                    color = workout.category.accentColor,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Black,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                )
                                            }
                                            Text(
                                                text = workout.category.label,
                                                color = Color(0xFF94A3B8),
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                        Text(
                                            text = "${workout.durationLabel} • ${workout.distanceEstimateLabel}",
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    Text(
                                        text = workout.title,
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Black
                                    )

                                    Text(
                                        text = workout.subtitle,
                                        color = Color(0xFF94A3B8),
                                        fontSize = 11.5.sp,
                                        lineHeight = 15.sp
                                    )

                                    // Pílulas coloridas dos blocos (AQ, Estímulo Z4/Z5, Recuperação Z1/Z2, ESF)
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        workout.blockPills.forEach { pill ->
                                            RxBlockPillBadge(pill = pill)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Rodapé com opção de abrir também as Planilhas de 10 Semanas
                    Surface(
                        color = Color(0xFF151922),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = {
                                    AudioSynth.playTickSound()
                                    onSelectStandardProgramCatalog()
                                }
                            ) {
                                Text(
                                    text = "Ver Planilhas 10 Semanas",
                                    color = Color(0xFF00E5FF),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Button(
                                onClick = {
                                    AudioSynth.playTickSound()
                                    onDismiss()
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF262D3D))
                            ) {
                                Text("FECHAR", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================
// PACOTE 1 — MOTOR REAL DE PRONTIDÃO HÍBRIDA (48H MUSCULAÇÃO + CORRIDA)
// E AUTO-REGULAÇÃO DE VAM/RITMOS ESTILO RUNNA
// ============================================================================

data class HybridReadinessReport(
    val scorePct: Int = 94,
    val tierBadge: String = "Alta — 94%",
    val tierTitle: String = "Alta — 94% (Pronto para Estímulo)",
    val homeBadgeLabel: String = "Alta • 94%",
    val homeFatigueLabel: String = "Baixa",
    val accentColor: Color = Color(0xFF22C55E),
    val hasLowerBodySession48h: Boolean = false,
    val hoursSinceLowerBody: Int? = null,
    val lowerBodyWorkoutName: String? = null,
    val lowerBodyVolumeKg: Int = 0,
    val lowerBodySets: Int = 0,
    val lowerBodyAvgRpe: Float = 0f,
    val lowerBodyStatusTitle: String = "Membros Inferiores Descansados (100%)",
    val lowerBodyStatusDetail: String = "Nenhum treino pesado de Quadríceps, Posterior, Glúteos ou Panturrilha nas últimas 48h.",
    val hasRunningSession48h: Boolean = false,
    val hoursSinceLastRun: Int? = null,
    val z4z5Minutes48h: Float = 0f,
    val lastRunDistanceKm: Float = 0f,
    val lastRunAvgHr: Int? = null,
    val lastRunMaxHr: Int? = null,
    val lastRunRpe: Int? = null,
    val isHighCardioWear48h: Boolean = false,
    val cardioStatusTitle: String = "Sistema Autonômico Descansado (100%)",
    val cardioStatusDetail: String = "Sem desgaste acumulado em Z4/Z5 nas últimas 48h. Reserva cardiovascular preservada.",
    val shouldSuggestRegenerativeZ2Swap: Boolean = false,
    val coachRecommendationText: String = "Prontidão neuromuscular e autonômica elevada. Excelente janela fisiológica para treinos de ritmo, intervalados (Z4/Z5) ou progressão de carga.",
    val isRegenerativeZ2AlreadySelected: Boolean = false
)

object HybridReadinessEngine {

    private val lowerBodyKeywords = listOf(
        "perna", "quadríceps", "quadriceps", "posterior", "glúteo", "gluteo", "panturrilha",
        "agachamento", "leg press", "extensora", "flexora", "stiff", "terra", "rdl",
        "afundo", "búlgaro", "bulgaro", "passada", "hack", "elevação pélvica", "elevacao pelvica",
        "abdutora", "adutora", "gêmeos", "gemeos", "lower", "legs", "inferiores"
    )

    private fun isLowerBodyText(text: String): Boolean {
        val lower = text.lowercase(Locale.getDefault())
        return lowerBodyKeywords.any { lower.contains(it) }
    }

    fun evaluate(
        gymHistory: List<com.example.data.model.WorkoutHistoryEntry>,
        gymSessions: List<com.example.data.model.GymWorkoutSession>,
        runningHistory: List<RunningSession>,
        athleteAge: Int = 40,
        selectedRunningProgramId: String = "run_30m",
        consecutiveDays: Int = 0,
        calibratedMaxHrBpm: Int? = null
    ): HybridReadinessReport {
        val now = System.currentTimeMillis()
        val window48hStart = now - 48L * 3600L * 1000L
        val ptBr = Locale("pt", "BR")

        // 1. ANÁLISE DE IMPACTO DE MEMBROS INFERIORES (48H)
        var hasLowerBody48h = false
        var latestLowerBodyTimestamp = 0L
        var lowerBodyWorkoutTitle: String? = null
        var totalLowerBodyKg = 0f
        var totalLowerBodySets = 0
        val rpeValues = mutableListOf<Float>()

        // Verifica em GymWorkoutSession (sessões detalhadas do MAX GYM)
        val recentGymSessions = gymSessions.filter { it.date >= window48hStart }
        for (sess in recentGymSessions) {
            val sessTime = sess.date
            val titleHasLegs = isLowerBodyText(sess.workoutName) ||
                sess.workoutName.contains("Full Body", ignoreCase = true)
            val legExercises = sess.exercises.filter { ex ->
                isLowerBodyText(ex.name) || isLowerBodyText(ex.category)
            }
            if (titleHasLegs || legExercises.isNotEmpty()) {
                val targetExercises = if (legExercises.isNotEmpty()) legExercises else sess.exercises
                var sessSets = 0
                var sessKg = 0f
                for (ex in targetExercises) {
                    val doneSets = ex.sets.filter { it.completed }
                    val effectiveSets = doneSets.ifEmpty { ex.sets }
                    for (s in effectiveSets) {
                        sessSets++
                        sessKg += (s.weight.coerceAtLeast(0f) * s.reps.coerceAtLeast(1))
                    }
                }
                if (sessSets > 0 || sess.totalVolumeKg > 0f) {
                    hasLowerBody48h = true
                    if (sessTime > latestLowerBodyTimestamp) {
                        latestLowerBodyTimestamp = sessTime
                        lowerBodyWorkoutTitle = sess.workoutName
                    }
                    totalLowerBodySets += sessSets.coerceAtLeast(4)
                    totalLowerBodyKg += if (sessKg > 0f) sessKg else (sess.totalVolumeKg * 0.45f)
                }
            }
        }

        // Complementa com WorkoutHistoryEntry caso não esteja duplicado em GymWorkoutSession
        if (!hasLowerBody48h) {
            val recentHistoryEntries = gymHistory.filter { it.date >= window48hStart }
            for (entry in recentHistoryEntries) {
                val parsedExs = try {
                    com.example.utils.JsonUtils.parseWorkoutExercises(entry.exercisesJson)
                } catch (_: Exception) {
                    emptyList()
                }
                val legExs = parsedExs.filter { ex ->
                    isLowerBodyText(ex.name) || isLowerBodyText(ex.category)
                }
                val workoutIsFullOrLegs = isLowerBodyText(entry.workoutName) ||
                    entry.workoutName.contains("Full Body", ignoreCase = true)
                if (workoutIsFullOrLegs || legExs.isNotEmpty()) {
                    hasLowerBody48h = true
                    if (entry.date > latestLowerBodyTimestamp) {
                        latestLowerBodyTimestamp = entry.date
                        lowerBodyWorkoutTitle = entry.workoutName
                    }
                    val targetExs = if (legExs.isNotEmpty()) legExs else parsedExs
                    var setsCount = 0
                    var volKg = 0f
                    for (ex in targetExs) {
                        val doneSets = ex.sets.filter { it.completed }.ifEmpty { ex.sets }
                        for (s in doneSets) {
                            setsCount++
                            volKg += (s.weight.coerceAtLeast(0f) * s.reps.coerceAtLeast(1))
                            s.rpe.replace(',', '.').toFloatOrNull()?.takeIf { it in 4f..10f }?.let { rpeValues.add(it) }
                        }
                    }
                    totalLowerBodySets += if (setsCount > 0) setsCount else (entry.exercisesCompletedCount.coerceAtLeast(2) * 3)
                    totalLowerBodyKg += if (volKg > 0f) volKg else (entry.totalWeight.toFloat() * 0.5f).coerceAtLeast(1200f)
                }
            }
        }

        val hoursSinceLegs = if (hasLowerBody48h && latestLowerBodyTimestamp > 0L) {
            ((now - latestLowerBodyTimestamp) / (3600L * 1000L)).toInt().coerceIn(0, 48)
        } else null
        val avgGymRpe = if (rpeValues.isNotEmpty()) rpeValues.average().toFloat() else if (hasLowerBody48h) 8.0f else 0f

        val lowerBodyPenalty = if (!hasLowerBody48h) {
            0
        } else {
            val recencyFactor = when {
                (hoursSinceLegs ?: 48) <= 18 -> 1.0f
                (hoursSinceLegs ?: 48) <= 30 -> 0.80f
                (hoursSinceLegs ?: 48) <= 36 -> 0.65f
                else -> 0.45f
            }
            val volumePenalty = when {
                totalLowerBodySets >= 12 || totalLowerBodyKg >= 3500f -> 20
                totalLowerBodySets >= 7 || totalLowerBodyKg >= 1800f -> 15
                else -> 11
            }
            val rpeBonus = if (avgGymRpe >= 8.5f) 5 else if (avgGymRpe >= 7.5f) 3 else 0
            ((volumePenalty + rpeBonus) * recencyFactor).roundToInt().coerceIn(6, 26)
        }

        // 2. ANÁLISE DE DESGASTE CARDIOVASCULAR E AUTONÔMICO (48H)
        val validRecentRuns = runningHistory
            .filter { it.date >= window48hStart && it.distanceMeters >= 200 && !it.isGentleMode }
            .sortedByDescending { it.date }

        val hasRun48h = validRecentRuns.isNotEmpty()
        val lastRun = validRecentRuns.firstOrNull()
        val hoursSinceRun = lastRun?.let {
            ((now - it.date) / (3600L * 1000L)).toInt().coerceIn(0, 48)
        }

        val effectiveMaxHr = com.example.data.model.calculateEffectiveMaxHr(
            ageYears = athleteAge.takeIf { it in 10..100 },
            sessionMaxHrBpm = lastRun?.maxHr,
            historicalPeakHrBpm = runningHistory.mapNotNull { it.maxHr }.maxOrNull(),
            calibratedMaxHrBpm = calibratedMaxHrBpm
        )

        var accumulatedZ4Z5Sec = 0
        if (effectiveMaxHr > 0) {
            for (run in validRecentRuns) {
                val samples = run.hrTimeline.toHrTimelineList().filter { it in 45..240 }
                val dist5Zones = com.example.data.model.computeCoherent5ZoneDistribution(
                    hrSamples = samples,
                    avgBpm = run.avgHr,
                    maxBpm = run.maxHr,
                    effectiveMaxHr = effectiveMaxHr,
                    durationSeconds = run.durationSec.coerceAtLeast(1)
                )
                // Z4 (index 3) e Z5 (index 4)
                val z4Sec = dist5Zones.getOrNull(3)?.durationSeconds ?: 0
                val z5Sec = dist5Zones.getOrNull(4)?.durationSeconds ?: 0
                accumulatedZ4Z5Sec += (z4Sec + z5Sec)
            }
        }
        val z4z5Minutes = accumulatedZ4Z5Sec / 60f
        val maxRecentRunRpe = validRecentRuns.maxOfOrNull { it.rpe } ?: 0
        val isHighCardioWear = z4z5Minutes > 12f || maxRecentRunRpe >= 8

        val cardioPenalty = if (!hasRun48h) {
            0
        } else {
            val recencyFactor = when {
                (hoursSinceRun ?: 48) <= 18 -> 1.0f
                (hoursSinceRun ?: 48) <= 30 -> 0.75f
                else -> 0.50f
            }
            val baseWear = when {
                z4z5Minutes >= 18f || maxRecentRunRpe >= 9 -> 22
                z4z5Minutes > 12f || maxRecentRunRpe >= 8 -> 16
                z4z5Minutes >= 6f || maxRecentRunRpe >= 7 -> 10
                else -> 6
            }
            (baseWear * recencyFactor).roundToInt().coerceIn(4, 24)
        }

        val consecutivePenalty = when {
            consecutiveDays >= 4 -> 10
            consecutiveDays == 3 -> 6
            else -> 0
        }

        // Quando não há desgaste em 48h, Prontidão fica em 96% (Alta)
        val baseScore = if (!hasLowerBody48h && !hasRun48h) 96 else 98
        val readinessScore = (baseScore - lowerBodyPenalty - cardioPenalty - consecutivePenalty).coerceIn(38, 100)

        val (tierBadge, tierTitle, homeBadge, homeFatigue, accentColor) = when {
            readinessScore >= 90 -> Quintuple(
                "Alta — $readinessScore%",
                "Alta — $readinessScore% (Pronto para Performance)",
                "Alta • $readinessScore%",
                "Baixa",
                Color(0xFF22C55E)
            )
            readinessScore >= 65 -> Quintuple(
                "Moderada — $readinessScore%",
                "Moderada — $readinessScore% (Atenção Híbrida)",
                "Moderada • $readinessScore%",
                "Moderada",
                Color(0xFFF59E0B)
            )
            else -> Quintuple(
                "Atenção / Recuperação — $readinessScore%",
                "Atenção / Recuperação — $readinessScore%",
                "Atenção • $readinessScore%",
                "Alta",
                Color(0xFFEF4444)
            )
        }

        val lowerBodyTitle = if (hasLowerBody48h) {
            "Em recuperação pós-musculação (há ${hoursSinceLegs ?: 12}h)"
        } else {
            "Membros Inferiores Descansados (100%)"
        }

        val lowerBodyDetail = if (hasLowerBody48h) {
            val cleanName = lowerBodyWorkoutTitle?.substringBefore("—")?.trim() ?: "Musculação (Inferiores)"
            "$cleanName • $totalLowerBodySets séries de inferiores (${totalLowerBodyKg.roundToInt()} kg) • RPE médio ${String.format(ptBr, "%.1f", avgGymRpe)}"
        } else {
            "Sem sobrecarga de Quadríceps, Posterior de Coxa, Glúteos ou Panturrilha nas últimas 48h. Musculatura pronta para impacto e velocidade."
        }

        val cardioTitle = when {
            !hasRun48h -> "Sistema Autonômico Descansado (100%)"
            isHighCardioWear -> "Desgaste Cardiovascular Elevado (${String.format(ptBr, "%.0f", z4z5Minutes)} min em Z4/Z5)"
            else -> "Carga Aeróbica Controlada (há ${hoursSinceRun ?: 12}h)"
        }

        val cardioDetail = if (lastRun != null) {
            val distKm = lastRun.distanceMeters / 1000f
            val rpeLabel = when {
                lastRun.rpe >= 9 -> "No limite (RPE ${lastRun.rpe})"
                lastRun.rpe >= 8 -> "Difícil (RPE ${lastRun.rpe})"
                lastRun.rpe >= 6 -> "Moderado (RPE ${lastRun.rpe})"
                else -> "Leve (RPE ${lastRun.rpe})"
            }
            val hrPart = if (lastRun.avgHr != null && lastRun.avgHr > 0) {
                "FC ${lastRun.avgHr}/${lastRun.maxHr ?: lastRun.avgHr} bpm"
            } else {
                "Sem cinta BLE"
            }
            "Última corrida: ${String.format(ptBr, "%.2f", distKm)} km • ${String.format(ptBr, "%.0f", z4z5Minutes)} min em Z4/Z5 • $hrPart • Esforço: $rpeLabel"
        } else {
            "Nenhuma corrida com desgaste em Z4/Z5 (> 12 min) nas últimas 48h. Sistema nervoso parassimpático recuperado."
        }

        val isAlreadyRegenerative = selectedRunningProgramId == "free_regenerative"
        val isLegsFatiguedRecently = hasLowerBody48h && (hoursSinceLegs ?: 48) <= 36
        val shouldSuggestSwap = isLegsFatiguedRecently || isHighCardioWear || readinessScore < 78

        val coachText = when {
            isLegsFatiguedRecently && isHighCardioWear ->
                "Fadiga concorrente detectada: membros inferiores em recuperação de carga (${hoursSinceLegs}h atrás) + ${String.format(ptBr, "%.0f", z4z5Minutes)} min acumulados em Z4/Z5. Proteja tendões e musculatura trocando tiros por Rodagem Regenerativa Z2 hoje."
            isLegsFatiguedRecently ->
                "Você treinou membros inferiores há ${hoursSinceLegs}h (${totalLowerBodyKg.roundToInt()} kg acumulados). Se a sessão de corrida de hoje tiver tiros em Z4/Z5, troque para Rodagem Regenerativa Z2 para acelerar a supercompensação muscular."
            isHighCardioWear ->
                "Alto tempo acumulado em Z4/Z5 (${String.format(ptBr, "%.0f", z4z5Minutes)} min) ou esforço intenso nas últimas 48h. Priorize volume leve em Zona 2 hoje para restaurar o tônus vagal."
            else ->
                "Prontidão híbrida em $readinessScore%. Membros inferiores e sistema cardiovascular prontos para sustentar blocos de ritmo (Z3/Z4) ou tiros de VAM sem risco de sobrecarga concorrente."
        }

        return HybridReadinessReport(
            scorePct = readinessScore,
            tierBadge = tierBadge,
            tierTitle = tierTitle,
            homeBadgeLabel = homeBadge,
            homeFatigueLabel = homeFatigue,
            accentColor = accentColor,
            hasLowerBodySession48h = hasLowerBody48h,
            hoursSinceLowerBody = hoursSinceLegs,
            lowerBodyWorkoutName = lowerBodyWorkoutTitle,
            lowerBodyVolumeKg = totalLowerBodyKg.roundToInt(),
            lowerBodySets = totalLowerBodySets,
            lowerBodyAvgRpe = avgGymRpe,
            lowerBodyStatusTitle = lowerBodyTitle,
            lowerBodyStatusDetail = lowerBodyDetail,
            hasRunningSession48h = hasRun48h,
            hoursSinceLastRun = hoursSinceRun,
            z4z5Minutes48h = z4z5Minutes,
            lastRunDistanceKm = (lastRun?.distanceMeters ?: 0) / 1000f,
            lastRunAvgHr = lastRun?.avgHr,
            lastRunMaxHr = lastRun?.maxHr,
            lastRunRpe = lastRun?.rpe,
            isHighCardioWear48h = isHighCardioWear,
            cardioStatusTitle = cardioTitle,
            cardioStatusDetail = cardioDetail,
            shouldSuggestRegenerativeZ2Swap = shouldSuggestSwap,
            coachRecommendationText = coachText,
            isRegenerativeZ2AlreadySelected = isAlreadyRegenerative
        )
    }

    private data class Quintuple<A, B, C, D, E>(
        val first: A,
        val second: B,
        val third: C,
        val fourth: D,
        val fifth: E
    )
}

@Composable
fun HybridReadinessExecutiveDialog(
    report: HybridReadinessReport,
    onDismiss: () -> Unit,
    onSwapToRegenerativeZ2: () -> Unit
) {
    var swapConfirmedLocal by remember(report.isRegenerativeZ2AlreadySelected) {
        mutableStateOf(report.isRegenerativeZ2AlreadySelected)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(top = 12.dp, bottom = 12.dp),
            shape = RoundedCornerShape(22.dp),
            color = Color(0xFF0F131A),
            border = BorderStroke(1.5.dp, report.accentColor.copy(alpha = 0.55f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Cabeçalho Executivo
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "RAIO-X DE PRONTIDÃO HÍBRIDA",
                                color = Color(0xFF00E5FF),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "Musculação (MAX GYM) × Corrida (MAX RUN)",
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp,
                                lineHeight = 14.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF1E2532))
                                .testTag("btn_close_hybrid_readiness_top")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Fechar Raio-X de Prontidão",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // 1. Score de Prontidão Híbrida (0-100%)
                    Surface(
                        color = Color(0xFF161C26),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, report.accentColor.copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "SCORE DE PRONTIDÃO NEUROMUSCULAR",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = report.tierTitle,
                                        color = report.accentColor,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                                Surface(
                                    color = report.accentColor.copy(alpha = 0.18f),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, report.accentColor.copy(alpha = 0.6f))
                                ) {
                                    Text(
                                        text = "${report.scorePct}%",
                                        color = report.accentColor,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Black,
                                        maxLines = 1,
                                        softWrap = false,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                            }

                            // Barra de Progresso do Score
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFF262F3F))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth((report.scorePct / 100f).coerceIn(0.08f, 1f))
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(report.accentColor)
                                )
                            }

                            Text(
                                text = "Faixas: Alta (90–100%) • Moderada (65–89%) • Atenção / Recuperação (< 65%)",
                                color = Color(0xFF94A3B8),
                                fontSize = 10.5.sp
                            )
                        }
                    }

                    // 2. Status Muscular de Membros Inferiores (48h)
                    val legColor = if (report.hasLowerBodySession48h) Color(0xFFF59E0B) else Color(0xFF22C55E)
                    Surface(
                        color = Color(0xFF151A24),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, legColor.copy(alpha = 0.38f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(13.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "MEMBROS INFERIORES (ÚLTIMAS 48H)",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 10.sp,
                                    lineHeight = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Surface(
                                    color = legColor.copy(alpha = 0.16f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = if (report.hasLowerBodySession48h) "EM RECUPERAÇÃO" else "DESCANSADO",
                                        color = legColor,
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Black,
                                        maxLines = 1,
                                        softWrap = false,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = report.lowerBodyStatusTitle,
                                color = Color.White,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = report.lowerBodyStatusDetail,
                                color = Color(0xFFCBD5E1),
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }

                    // 3. Status Autonômico / Cardiovascular (48h)
                    val cardioColor = when {
                        report.isHighCardioWear48h -> Color(0xFFEF4444)
                        report.hasRunningSession48h -> Color(0xFF38BDF8)
                        else -> Color(0xFF22C55E)
                    }
                    Surface(
                        color = Color(0xFF151A24),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, cardioColor.copy(alpha = 0.38f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(13.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "STATUS AUTONÔMICO & CARDIOVASCULAR (48H)",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 10.sp,
                                    lineHeight = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Surface(
                                    color = cardioColor.copy(alpha = 0.16f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = when {
                                            report.isHighCardioWear48h -> "Z4/Z5 ELEVADO"
                                            report.hasRunningSession48h -> "ESTÍMULO OK"
                                            else -> "100% PRONTO"
                                        },
                                        color = cardioColor,
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Black,
                                        maxLines = 1,
                                        softWrap = false,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = report.cardioStatusTitle,
                                color = Color.White,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = report.cardioStatusDetail,
                                color = Color(0xFFCBD5E1),
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }

                    // 4. Parecer & Sugestão Inteligente de Ajuste (Botão de Ação p/ Rodagem Regenerativa Z2)
                    Surface(
                        color = Color(0xFF131D2B),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(13.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "SUGESTÃO INTELIGENTE DE AUTO-REGULAÇÃO",
                                color = Color(0xFF00E5FF),
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                text = report.coachRecommendationText,
                                color = Color(0xFFE2E8F0),
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )

                            if (swapConfirmedLocal) {
                                Surface(
                                    color = Color(0xFF10B981).copy(alpha = 0.18f),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, Color(0xFF10B981)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "✅ Sessão de hoje ajustada para Rodagem Regenerativa Z2 (Proteção de Membros Inferiores Ativa)",
                                        color = Color(0xFF34D399),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                                    )
                                }
                            } else {
                                Button(
                                    onClick = {
                                        AudioSynth.playTickSound()
                                        swapConfirmedLocal = true
                                        onSwapToRegenerativeZ2()
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 50.dp)
                                        .testTag("btn_swap_to_regenerative_z2"),
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (report.shouldSuggestRegenerativeZ2Swap) {
                                            Color(0xFF00E676)
                                        } else {
                                            Color(0xFF1E293B)
                                        }
                                    )
                                ) {
                                    Text(
                                        text = "Trocar sessão de hoje por Rodagem Regenerativa Z2 (Proteger Membros Inferiores)",
                                        color = if (report.shouldSuggestRegenerativeZ2Swap) Color(0xFF06130E) else Color(0xFF38BDF8),
                                        fontSize = 12.sp,
                                        lineHeight = 16.sp,
                                        fontWeight = FontWeight.Black,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = {
                        AudioSynth.playTickSound()
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("btn_close_hybrid_readiness_xray"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF262D3D))
                ) {
                    Text(
                        text = "FECHAR RAIO-X",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// ============================================================================
// AUTO-REGULAÇÃO DA PLANILHA E VAM (ESTILO RUNNA — DETECÇÃO DE EVOLUÇÃO DE PACE)
// ============================================================================

data class RunnaPaceEvolutionResult(
    val deliveredPaceSecPerKm: Int,
    val deliveredPaceFormatted: String,
    val previousVamKmh: Float,
    val newVamKmh: Float,
    val controlledHrBpm: Int?,
    val maxHrPct: Int?,
    val newLt2PaceFormatted: String,
    val newZ2RangeFormatted: String
)

fun evaluateRunnaPaceEvolution(
    session: RunningSession,
    sprintAnalytics: com.example.data.model.SprintAnalytics,
    allRunningSessions: List<RunningSession>,
    athleteAge: Int,
    anamnesisProfile: RunnerAnamnesisProfile,
    baselinePaceMinPerKm: Float?,
    calibratedMaxHrBpm: Int? = null
): RunnaPaceEvolutionResult? {
    if (session.isGentleMode || session.distanceMeters < 400 || session.durationSec < 120) return null

    // Calibração anterior (excluindo a sessão atual para comparar contra a meta vigente antes do treino)
    val historyBeforeSession = allRunningSessions.filter { it.id != session.id }
    val currentCalibration = RunnerAnamnesisManager.calculateCalibration(
        profile = anamnesisProfile,
        athleteAge = athleteAge,
        history = historyBeforeSession,
        baselinePaceMinPerKm = baselinePaceMinPerKm,
        calibratedMaxHrBpm = calibratedMaxHrBpm
    )

    val effectiveMaxHr = com.example.data.model.calculateEffectiveMaxHr(
        ageYears = athleteAge.takeIf { it in 10..100 },
        sessionMaxHrBpm = session.maxHr,
        historicalPeakHrBpm = allRunningSessions.mapNotNull { it.maxHr }.maxOrNull(),
        calibratedMaxHrBpm = calibratedMaxHrBpm
    )

    val hasValidSprints = !sprintAnalytics.isContinuousRunMode && sprintAnalytics.avgSprintPaceSecPerKm in 150..450
    val sprintPaceSec = if (hasValidSprints) {
        sprintAnalytics.avgSprintPaceSecPerKm
    } else {
        (session.mainRunAvgPaceSecPerKm ?: session.avgPaceSecPerKm).takeIf { it in 150..450 } ?: return null
    }

    val recordedSplits = session.kmSplitsJson.toRecordedKmSplits()
    val runSplitHrs = recordedSplits
        .filter { it.phaseTag.equals("RUN", ignoreCase = true) || it.phaseTag.isNullOrBlank() }
        .mapNotNull { it.avgHr?.takeIf { h -> h in 60..235 } }
    val sprintAvgHr: Int? = if (runSplitHrs.isNotEmpty()) {
        runSplitHrs.average().roundToInt()
    } else {
        session.avgHr?.takeIf { it > 0 }
    }
    val hrPctOfMax: Int? = if (sprintAvgHr != null && effectiveMaxHr > 0) {
        ((sprintAvgHr.toFloat() / effectiveMaxHr.toFloat()) * 100f).roundToInt()
    } else null

    // Controle cardíaco: FC média nos tiros <= 92% da FCmáx (ou sem sensor cardíaco mas batendo meta de ritmo)
    val isHrControlled = hrPctOfMax == null || hrPctOfMax <= 92
    if (!isHrControlled) return null

    // Meta atual de ritmo nos blocos de estímulo (Z4/LT2 ou VAM)
    val currentTargetStimulusPaceSec = currentCalibration.lt2PaceSecPerKm
    val beatStimulusTarget = hasValidSprints && sprintPaceSec <= (currentTargetStimulusPaceSec - 4)

    // Ou bateu novo recorde estimado de 3K / 5K
    val current5kPredSec = currentCalibration.predictions.firstOrNull()?.estimatedTotalSeconds ?: 1650
    val beatDistanceRecord = session.distanceMeters >= 2800 && run {
        val distKm = session.distanceMeters / 1000.0
        val equiv5kSec = (session.durationSec * (5.0 / distKm).pow(1.06)).roundToInt()
        equiv5kSec <= (current5kPredSec - 15)
    }

    // Cálculo da nova VAM estimada a partir dos tiros entregues com controle cardíaco
    val deliveredSpeedKmh = (3600.0 / sprintPaceSec.toDouble()).toFloat()
    // Se FC nos tiros ficou <= 86% FCmáx (ex: 160 bpm / 190 bpm = 84%), o atleta correu os tiros a ~94-95% da VAM
    val vamEfficiencyFactor = when {
        hrPctOfMax != null && hrPctOfMax <= 85 -> 0.945f
        hrPctOfMax != null && hrPctOfMax <= 89 -> 0.96f
        else -> 0.975f
    }
    val rawEstimatedVamKmh = (deliveredSpeedKmh / vamEfficiencyFactor).coerceIn(7.5f, 23.5f)
    val roundedNewVamKmh = ((rawEstimatedVamKmh * 10f).roundToInt() / 10f)

    // Critério final: tiros mais rápidos que o pace alvo atual (ou novo recorde) E ganho real de VAM
    if ((!beatStimulusTarget && !beatDistanceRecord) || roundedNewVamKmh <= currentCalibration.vamKmh + 0.1f) {
        return null
    }

    val newVamMps = roundedNewVamKmh / 3.6
    val newVamPaceSec = (1000.0 / newVamMps).roundToInt()
    val newLt2PaceSec = (newVamPaceSec / 0.87).roundToInt()
    val newLt1PaceSec = (newVamPaceSec / 0.76).roundToInt()
    val newZ2Min = (newLt2PaceSec * 1.09).roundToInt()
    val newZ2Max = (newLt1PaceSec * 1.05).roundToInt()

    return RunnaPaceEvolutionResult(
        deliveredPaceSecPerKm = sprintPaceSec,
        deliveredPaceFormatted = "${formatPaceSec(sprintPaceSec)}/km",
        previousVamKmh = currentCalibration.vamKmh,
        newVamKmh = roundedNewVamKmh,
        controlledHrBpm = sprintAvgHr,
        maxHrPct = hrPctOfMax,
        newLt2PaceFormatted = "${formatPaceSec(newLt2PaceSec)}/km",
        newZ2RangeFormatted = "${formatPaceSec(newZ2Min)}–${formatPaceSec(newZ2Max)}/km"
    )
}

@Composable
fun RunnaPaceEvolutionBanner(
    session: RunningSession,
    sprintAnalytics: com.example.data.model.SprintAnalytics,
    allRunningSessions: List<RunningSession>,
    athleteAge: Int,
    baselinePaceMinPerKm: Float?,
    onApplyNewBaselinePace: (Float) -> Unit,
    modifier: Modifier = Modifier,
    calibratedMaxHrBpm: Int? = null
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var calibrationVersion by remember { mutableIntStateOf(0) }
    var isUpdatedInThisView by rememberSaveable(session.id) { mutableStateOf(false) }
    var appliedVamValue by rememberSaveable(session.id) { mutableFloatStateOf(0f) }

    val anamnesisProfile = remember(context, calibrationVersion, baselinePaceMinPerKm) {
        RunnerAnamnesisManager.loadProfile(context, baselinePaceMinPerKm)
    }

    val evolution = remember(
        session.id,
        session.distanceMeters,
        session.durationSec,
        sprintAnalytics.avgSprintPaceSecPerKm,
        sprintAnalytics.isContinuousRunMode,
        anamnesisProfile,
        baselinePaceMinPerKm,
        calibratedMaxHrBpm
    ) {
        evaluateRunnaPaceEvolution(
            session = session,
            sprintAnalytics = sprintAnalytics,
            allRunningSessions = allRunningSessions,
            athleteAge = athleteAge,
            anamnesisProfile = anamnesisProfile,
            baselinePaceMinPerKm = baselinePaceMinPerKm,
            calibratedMaxHrBpm = calibratedMaxHrBpm
        )
    }

    if (evolution == null && !isUpdatedInThisView) return

    val ptBr = remember { Locale("pt", "BR") }
    val displayVam = if (isUpdatedInThisView && appliedVamValue > 0f) appliedVamValue else (evolution?.newVamKmh ?: 12.6f)
    val displayPace = evolution?.deliveredPaceFormatted ?: "5'01\"/km"

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("runna_pace_evolution_banner"),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF0B1F1D),
        border = BorderStroke(1.5.dp, Color(0xFF00E676))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Evolução de Performance Detectada!",
                    color = Color(0xFF00E676),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    color = Color(0xFF00E5FF).copy(alpha = 0.18f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.6f))
                ) {
                    Text(
                        text = "AUTO-REGULAÇÃO RUNNA",
                        color = Color(0xFF00E5FF),
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }
            }

            Text(
                text = "Você entregou os tiros a $displayPace com excelente controle cardíaco. Sua VAM estimada subiu para ${String.format(ptBr, "%.1f", displayVam)} km/h!",
                color = Color(0xFFE2E8F0),
                fontSize = 12.5.sp,
                lineHeight = 17.sp,
                fontWeight = FontWeight.Medium
            )

            if (evolution != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF112A28))
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "VAM: ${String.format(ptBr, "%.1f", evolution.previousVamKmh)} → ${String.format(ptBr, "%.1f", displayVam)} km/h",
                        color = Color(0xFF00E5FF),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Novo Limiar Z4: ${evolution.newLt2PaceFormatted}",
                        color = Color(0xFF69F0AE),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (isUpdatedInThisView) {
                Surface(
                    color = Color(0xFF00E676).copy(alpha = 0.2f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0xFF00E676)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "✅ Ritmos da planilha e zonas Z1–Z5 recalibrados para VAM ${String.format(ptBr, "%.1f", displayVam)} km/h!",
                        color = Color(0xFF69F0AE),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                    )
                }
            } else if (evolution != null) {
                Button(
                    onClick = {
                        AudioSynth.playTickSound()
                        val (_, newBaselinePaceMin) = RunnerAnamnesisManager.applyAutoRegulatedVamUpgrade(
                            context = context,
                            newVamKmh = evolution.newVamKmh,
                            currentProfile = anamnesisProfile
                        )
                        appliedVamValue = evolution.newVamKmh
                        isUpdatedInThisView = true
                        calibrationVersion++
                        onApplyNewBaselinePace(newBaselinePaceMin)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("btn_update_runna_paces"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676))
                ) {
                    Text(
                        text = "Atualizar Ritmos da Planilha (+Velocidade)",
                        color = Color(0xFF04140C),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }
    }
}

