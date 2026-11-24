package com.example.utils

import com.example.data.model.CardioSession
import com.example.data.model.Classroom
import com.example.data.model.WorkoutHistoryEntry

// Represents a muscle recovery state
data class MuscleRecovery(
    val name: String,
    val recoveryPercent: Float, // 0.0 to 1.0 (0% to 100%)
    val hoursRemaining: Float,
    val statusText: String // "Recuperado", "Recuperando", "Fadigado"
)

// Represents an unlocked or locked trophy
data class Trophy(
    val id: String,
    val title: String,
    val description: String,
    val iconName: String,
    val isUnlocked: Boolean
)

object RecoveryAndTrophies {

    // Muscle recovery times in hours (Large muscles: 72 hours, Small muscles: 24 hours)
    private val RECOVERY_TIMES_HOURS = mapOf(
        "Peito" to 72f,
        "Costas" to 72f,
        "Pernas" to 72f,
        "Ombros" to 24f,
        "Bíceps" to 24f,
        "Tríceps" to 24f,
        "Trapézio" to 24f,
        "Abdominais" to 24f
    )

    private fun normalizeToCanonicalMuscle(rawCategory: String, exerciseName: String = ""): String? {
        val norm = rawCategory.trim().lowercase()
        return when {
            norm.contains("peito") || norm.contains("peitoral") -> "Peito"
            norm.contains("costas") || norm.contains("dorsal") || norm.contains("dorsais") || norm.contains("lombar") -> "Costas"
            norm.contains("perna") || norm.contains("quadríceps") || norm.contains("quadriceps") ||
                norm.contains("posterior") || norm.contains("isquiotibiais") || norm.contains("glúteo") ||
                norm.contains("gluteo") || norm.contains("panturrilha") || norm.contains("adutor") ||
                norm.contains("abdutor") || norm.contains("inferiores") -> "Pernas"
            norm.contains("ombro") || norm.contains("deltoide") -> "Ombros"
            norm.contains("bíceps") || norm.contains("biceps") || norm.contains("braquial") || norm.contains("antebraço") -> "Bíceps"
            norm.contains("tríceps") || norm.contains("triceps") -> "Tríceps"
            norm.contains("trapézio") || norm.contains("trapezio") -> "Trapézio"
            norm.contains("abdom") || norm.contains("abdômen") || norm.contains("core") -> "Abdominais"
            else -> {
                val nameNorm = exerciseName.trim().lowercase()
                when {
                    nameNorm.contains("agachamento") || nameNorm.contains("leg press") ||
                        nameNorm.contains("extensora") || nameNorm.contains("flexora") ||
                        nameNorm.contains("stiff") || nameNorm.contains("terra") ||
                        nameNorm.contains("elevação pélvica") || nameNorm.contains("panturrilha") ||
                        nameNorm.contains("afundo") || nameNorm.contains("búlgaro") || nameNorm.contains("passada") -> "Pernas"
                    nameNorm.contains("supino") || nameNorm.contains("crucifixo") || nameNorm.contains("crossover") -> "Peito"
                    nameNorm.contains("remada") || nameNorm.contains("puxada") || nameNorm.contains("barra fixa") || nameNorm.contains("pulldown") -> "Costas"
                    nameNorm.contains("desenvolvimento") || nameNorm.contains("elevação lateral") || nameNorm.contains("elevação frontal") -> "Ombros"
                    nameNorm.contains("rosca") -> "Bíceps"
                    nameNorm.contains("tríceps") || nameNorm.contains("triceps") || nameNorm.contains("testa") -> "Tríceps"
                    nameNorm.contains("encolhimento") -> "Trapézio"
                    nameNorm.contains("abdominal") || nameNorm.contains("prancha") -> "Abdominais"
                    else -> null
                }
            }
        }
    }

    // Calculates recovery state for the 8 muscle groups
    fun calculateRecovery(history: List<WorkoutHistoryEntry>): List<MuscleRecovery> {
        val muscleLastTrained = mutableMapOf<String, Long>()

        for (entry in history) {
            val exercises = try {
                JsonUtils.parseWorkoutExercises(entry.exercisesJson)
            } catch (_: Exception) {
                emptyList()
            }
            for (ex in exercises) {
                // Highly realistic recovery: only update last trained time if at least one set was completed
                if (ex.sets.any { it.completed }) {
                    val canonical = normalizeToCanonicalMuscle(ex.category, ex.name)
                    if (canonical != null) {
                        val prev = muscleLastTrained[canonical] ?: 0L
                        if (entry.date > prev) {
                            muscleLastTrained[canonical] = entry.date
                        }
                    }
                }
            }
        }

        val currentTime = System.currentTimeMillis()
        val result = mutableListOf<MuscleRecovery>()

        val categories = listOf("Peito", "Costas", "Pernas", "Ombros", "Bíceps", "Tríceps", "Trapézio", "Abdominais")
        for (muscle in categories) {
            val lastTrainedTime = muscleLastTrained[muscle]
            val recoveryTimeHours = RECOVERY_TIMES_HOURS[muscle] ?: 48f

            if (lastTrainedTime == null) {
                // Never trained, fully recovered
                result.add(MuscleRecovery(muscle, 1.0f, 0f, "Recuperado"))
            } else {
                val elapsedMs = (currentTime - lastTrainedTime).coerceAtLeast(0L)
                val elapsedHours = elapsedMs.toFloat() / (1000f * 60f * 60f)

                if (elapsedHours >= recoveryTimeHours) {
                    result.add(MuscleRecovery(muscle, 1.0f, 0f, "Recuperado"))
                } else {
                    val remainingHours = recoveryTimeHours - elapsedHours
                    val percent = (elapsedHours / recoveryTimeHours).coerceIn(0f, 1f)
                    val status = if (percent < 0.35f) "Fadigado" else "Recuperando"
                    result.add(MuscleRecovery(muscle, percent, remainingHours, status))
                }
            }
        }

        return result
    }

    // Evaluates the 6 trophies based on user's logs and classrooms
    fun evaluateTrophies(
        history: List<WorkoutHistoryEntry>,
        cardio: List<CardioSession>,
        classrooms: List<Classroom>,
        customWorkoutCount: Int
    ): List<Trophy> {
        val trophies = mutableListOf<Trophy>()

        // 1. Primeiro Passo: Complete at least 1 workout
        trophies.add(Trophy(
            "t_1",
            "Primeiro Passo",
            "Conclua o primeiro treino no aplicativo.",
            "fitness_center",
            isUnlocked = history.isNotEmpty()
        ))

        // 2. Consistência de Ferro: Complete at least 5 workouts in history
        trophies.add(Trophy(
            "t_2",
            "Consistência de Ferro",
            "Conclua pelo menos 5 treinos no histórico.",
            "emoji_events",
            isUnlocked = history.size >= 5
        ))

        // 3. Força Destrutiva: Lifted a cumulative total of over 1,000kg
        val totalVolume = history.sumOf { it.totalWeight.toDouble() }
        trophies.add(Trophy(
            "t_3",
            "Força Destrutiva",
            "Erga um total acumulado de mais de 1.000 kg em carga.",
            "bolt",
            isUnlocked = totalVolume >= 1000.0
        ))

        // 4. Cardio Master: Logged at least 3 cardio sessions
        trophies.add(Trophy(
            "t_4",
            "Cardio Master",
            "Registre pelo menos 3 sessões de exercícios de Cardio.",
            "directions_run",
            isUnlocked = cardio.size >= 3
        ))

        // 5. Hipertrofia Ativa: Created at least 1 custom workout template
        trophies.add(Trophy(
            "t_5",
            "Hipertrofia Ativa",
            "Crie um treino personalizado de sua autoria.",
            "build",
            isUnlocked = customWorkoutCount >= 1
        ))

        // 6. Membro de Elite: Joined a classroom
        trophies.add(Trophy(
            "t_6",
            "Membro de Elite",
            "Participe de uma Sala de Aula de professor.",
            "groups",
            isUnlocked = classrooms.isNotEmpty()
        ))

        return trophies
    }
}
