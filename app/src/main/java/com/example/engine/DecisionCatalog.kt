package com.example.engine

import com.example.data.model.Workout
import com.example.data.model.WorkoutExercise
import com.example.data.model.WorkoutSet
import com.example.utils.JsonUtils
import java.util.UUID
import kotlin.math.max

enum class DecisionPriority { SAFETY, RECOVERY, ADHERENCE, AVAILABILITY, PROGRESSION, OPTIMIZATION }

data class DecisionRule(
    val id: String,
    val priority: DecisionPriority,
    val triggers: (WorkoutContext) -> Boolean,
    val actions: (WorkoutContext, Workout) -> Workout,
    val score: (WorkoutContext) -> Int = { 0 }, // usado apenas para desempate dentro da mesma priority
    val isBlocker: Boolean = false,
    val notificationMessage: (WorkoutContext) -> String? = { null }
)

object DecisionCatalog {

    val SAFETY_01 = DecisionRule(
        id = "SAFETY-01",
        priority = DecisionPriority.SAFETY,
        triggers = { context -> context.injuredMuscles.isNotEmpty() },
        actions = { context, workout ->
            val exercises = JsonUtils.parseWorkoutExercises(workout.exercisesJson)
            val updatedExercises = exercises.map { ex ->
                val isAssociated = context.injuredMuscles.any { muscle ->
                    ex.category.equals(muscle, ignoreCase = true) || ex.name.contains(muscle, ignoreCase = true)
                }
                if (isAssociated) {
                    context.safetyConstrainedExerciseIds.add(ex.exerciseId)
                    val lowImpactName = when (ex.category.lowercase()) {
                        "pernas" -> "Cadeira Extensora (Baixo Impacto)"
                        "peito" -> "Crucifixo Maquina (Baixo Impacto)"
                        "costas" -> "Puxador Frente (Baixo Impacto)"
                        "ombros" -> "Elevacao Lateral (Baixo Impacto)"
                        else -> "${ex.name} (Adaptado Baixo Impacto)"
                    }
                    ex.copy(
                        id = "ex_safety_${UUID.randomUUID()}",
                        exerciseId = "low_impact_${ex.exerciseId}",
                        name = lowImpactName,
                        progressionType = "Linear"
                    )
                } else {
                    ex
                }
            }
            workout.copy(
                exercisesJson = JsonUtils.serializeWorkoutExercises(updatedExercises),
                name = "${workout.name} - Safety Adjusted",
                description = "${workout.description} (Safety constraint applied for: ${context.injuredMuscles.joinToString()})"
            )
        },
        score = { 100 },
        isBlocker = true,
        notificationMessage = { context ->
            if (context.injuredMuscles.isNotEmpty()) {
                "🔴 Modo de Segurança ativado: Exercícios adaptados para evitar sobrecarga em ${context.injuredMuscles.joinToString()}."
            } else null
        }
    )

    val RECOVERY_01 = DecisionRule(
        id = "RECOVERY-01",
        priority = DecisionPriority.RECOVERY,
        triggers = { context ->
            val lastDate = context.lastWorkoutDate
            lastDate != null && (context.date - lastDate) > 14L * 24L * 60L * 60L * 1000L
        },
        actions = { context, workout ->
            val exercises = JsonUtils.parseWorkoutExercises(workout.exercisesJson)
            val updatedExercises = exercises.map { ex ->
                val updatedSets = ex.sets.map { set ->
                    set.copy(weight = set.weight * 0.8f)
                }
                val truncatedSets = if (updatedSets.size > 1) updatedSets.dropLast(1) else updatedSets
                ex.copy(sets = truncatedSets)
            }
            context.returnSessionsRemaining = 2
            workout.copy(
                exercisesJson = JsonUtils.serializeWorkoutExercises(updatedExercises),
                name = "${workout.name} - Recovery Return",
                description = "${workout.description} (Pause return: loads reduced by 20 percent and volume reduced by 1 set)"
            )
        },
        score = { 90 },
        isBlocker = true,
        notificationMessage = { _ ->
            "🟡 Retorno de Pausa Longa: Cargas reduzidas em 20% e volume ajustado para recuperação segura."
        }
    )

    val ADHERENCE_01 = DecisionRule(
        id = "ADHERENCE-01",
        priority = DecisionPriority.ADHERENCE,
        triggers = { context ->
            val janelaDias = max(14, context.metaSemanal * 3)
            if (context.treinosTotaisHistorico < 3) {
                false
            } else {
                context.adherenceRate != null && context.adherenceRate < 0.5
            }
        },
        actions = { context, workout ->
            val enriched = com.example.data.repository.WorkoutProtocolsRepository.ensureCalfAndTrapsForFullBody(workout)
            enriched.copy(
                name = "${enriched.name} - Recalculated Split",
                description = "${enriched.description} (Adherence rate under 50 percent in dynamic window: Split recalculated)"
            )
        },
        score = { 50 },
        isBlocker = false,
        notificationMessage = { _ ->
            "🟠 Baixa Aderência: Divisão de treino recalculada para facilitar a consistência."
        }
    )

    val PROGRESSION_01 = DecisionRule(
        id = "PROGRESSION-01",
        priority = DecisionPriority.PROGRESSION,
        triggers = { context ->
            context.returnSessionsRemaining == 0 &&
            context.recuperacaoAlta &&
            context.repsAlvoBatidas &&
            context.tonelagemAtual >= context.tonelagemEsperada * 1.15f
        },
        actions = { context, workout ->
            val exercises = JsonUtils.parseWorkoutExercises(workout.exercisesJson)
            val updatedExercises = exercises.map { ex ->
                ex.copy(progressionType = "Manutencao")
            }
            workout.copy(
                exercisesJson = JsonUtils.serializeWorkoutExercises(updatedExercises),
                description = "${workout.description} (Hard Ceiling: load increase blocked, forced maintenance)"
            )
        },
        score = { 70 },
        isBlocker = true,
        notificationMessage = { _ ->
            "🔵 Teto de Progressão Ativo: Tonelagem acima do esperado; manutenção forçada aplicada."
        }
    )

    val RECOVERY_02 = DecisionRule(
        id = "RECOVERY-02",
        priority = DecisionPriority.RECOVERY,
        triggers = { context ->
            (context.fatigueLevel >= 9.0 && context.tonelagemAtual <= context.tonelagemEsperada * 0.85f) ||
            (context.fatigueLevel < 7.0 && context.consecutiveFloorWeeks > 0)
        },
        actions = { context, workout ->
            val exercises = JsonUtils.parseWorkoutExercises(workout.exercisesJson)
            if (context.fatigueLevel < 7.0) {
                context.consecutiveFloorWeeks = 0
                val updatedExercises = exercises.map { ex ->
                    val updatedSets = ex.sets.map { set ->
                        val base = set.baseWeight ?: set.weight
                        set.copy(weight = base, baseWeight = null)
                    }
                    ex.copy(sets = updatedSets)
                }
                workout.copy(
                    exercisesJson = JsonUtils.serializeWorkoutExercises(updatedExercises),
                    name = "${workout.name} - Hard Floor Exit",
                    description = "${workout.description} (Hard Floor Exit: fatigue < 7/10, original loads restored)"
                )
            } else {
                if (context.consecutiveFloorWeeks < 3) {
                    val updatedExercises = exercises.map { ex ->
                        val updatedSets = ex.sets.map { set ->
                            val base = set.baseWeight ?: set.weight
                            set.copy(weight = base * 0.95f, baseWeight = base)
                        }
                        ex.copy(sets = updatedSets)
                    }
                    context.consecutiveFloorWeeks++
                    workout.copy(
                        exercisesJson = JsonUtils.serializeWorkoutExercises(updatedExercises),
                        name = "${workout.name} - Hard Floor Intensity Reduction",
                        description = "${workout.description} (Hard Floor Active (Week ${context.consecutiveFloorWeeks}): weight reduced 5 percent from base)"
                    )
                } else {
                    val updatedExercises = exercises.map { ex ->
                        val truncatedSets = if (ex.sets.size > 1) ex.sets.dropLast(1) else ex.sets
                        val restoredSets = truncatedSets.map { set ->
                            val base = set.baseWeight ?: set.weight
                            set.copy(weight = base, baseWeight = null)
                        }
                        ex.copy(sets = restoredSets)
                    }
                    context.consecutiveFloorWeeks = 0
                    workout.copy(
                        exercisesJson = JsonUtils.serializeWorkoutExercises(updatedExercises),
                        name = "${workout.name} - Hard Floor Real Deload",
                        description = "${workout.description} (Hard Floor Active (Week 3 plus): real Deload applied, volume reduced, base weight reset)"
                    )
                }
            }
        },
        score = { 80 },
        isBlocker = false,
        notificationMessage = { context ->
            if (context.fatigueLevel < 7.0) {
                "🟢 Saída de Hard Floor: Cargas originais restauradas com fadiga controlada."
            } else if (context.consecutiveFloorWeeks < 3) {
                "🟠 Fadiga Crítica (Semana ${context.consecutiveFloorWeeks}): Redução preventiva de 5% na base de carga."
            } else {
                "🔴 Deload Real Aplicado: Redução de volume e reset da base de peso devido à fadiga persistente."
            }
        }
    )

    val AVAILABILITY_01 = DecisionRule(
        id = "AVAILABILITY-01",
        priority = DecisionPriority.AVAILABILITY,
        triggers = { context ->
            context.unavailableExerciseIds.isNotEmpty() || (context.gymProfile?.unavailableExercisesIds?.isNotEmpty() ?: false)
        },
        actions = { context, workout ->
            val exercises = JsonUtils.parseWorkoutExercises(workout.exercisesJson)
            val updatedExercises = exercises.map { ex ->
                val isUnavailable = context.unavailableExerciseIds.contains(ex.exerciseId) || 
                                    (context.gymProfile?.unavailableExercisesIds?.contains(ex.exerciseId) ?: false)
                val isSafetyConstrained = context.safetyConstrainedExerciseIds.contains(ex.exerciseId)
                
                if (isUnavailable && !isSafetyConstrained) {
                    val subId = "sub_${ex.exerciseId}"
                    ex.copy(
                        id = "ex_avail_${UUID.randomUUID()}",
                        exerciseId = subId,
                        name = "${ex.name} (Substituto Equipamento Indisponivel)"
                    )
                } else {
                    ex
                }
            }
            workout.copy(
                exercisesJson = JsonUtils.serializeWorkoutExercises(updatedExercises),
                name = "${workout.name} - Availability Adjusted",
                description = "${workout.description} (Unavailable exercises replaced, respecting safety constraints)"
            )
        },
        score = { 60 },
        isBlocker = false,
        notificationMessage = { _ ->
            "🟡 Equipamentos Indisponíveis: Exercícios adaptados conforme disponibilidade da academia."
        }
    )

    val ALL_RULES = listOf(
        SAFETY_01,
        RECOVERY_01,
        ADHERENCE_01,
        PROGRESSION_01,
        RECOVERY_02,
        AVAILABILITY_01
    )

    fun resolveWorkout(context: WorkoutContext, baseWorkout: Workout): Workout {
        if (context.last4WeeksTonnages.isNotEmpty()) {
            context.tonelagemEsperada = context.last4WeeksTonnages.average().toFloat()
        }

        var currentWorkout = baseWorkout
        val priorities = DecisionPriority.values()
        
        for (priority in priorities) {
            val triggeredRules = ALL_RULES.filter { it.priority == priority && it.triggers(context) }
            if (triggeredRules.isNotEmpty()) {
                val winningRule = triggeredRules.sortedWith { r1, r2 ->
                    val s1 = r1.score(context)
                    val s2 = r2.score(context)
                    if (s1 != s2) {
                        s2.compareTo(s1)
                    } else {
                        r1.id.compareTo(r2.id)
                    }
                }.first()
                
                currentWorkout = winningRule.actions(context, currentWorkout)
                
                if (winningRule.isBlocker) {
                    break
                }
            }
        }
        return com.example.data.repository.WorkoutProtocolsRepository.ensureCalfAndTrapsForFullBody(currentWorkout)
    }
}
