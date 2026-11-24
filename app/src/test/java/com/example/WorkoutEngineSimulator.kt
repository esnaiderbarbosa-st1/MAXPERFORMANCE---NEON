package com.example

import com.example.data.model.Workout
import com.example.data.model.WorkoutExercise
import com.example.data.model.WorkoutSet
import com.example.engine.DecisionCatalog
import com.example.engine.WorkoutContext
import com.example.engine.UserPreferences
import com.example.utils.JsonUtils
import org.junit.Test

/**
 * HARNESS DE SIMULAÇÃO FISIOLÓGICA REAL
 * Roda o motor de decisão real por 12 semanas para perfis sintéticos.
 */
class WorkoutEngineSimulator {

    // Simula o log do motor
    data class DecisionLog(val week: Int, val profile: String, val ruleId: String, val reason: String, val load: Float)

    @Test
    fun rodarSimulacaoDe12Semanas() {
        val resultados = mutableListOf<DecisionLog>()

        // 1. Simulação do Ego-Lifter (Sempre se diz recuperado 10/10, quer aumentar carga)
        val originalExerciseEgo = WorkoutExercise(
            id = "ex_ego",
            exerciseId = "peito_ego",
            name = "Supino Reto",
            category = "Peito",
            sets = listOf(
                WorkoutSet("set_1", 1, 10, 100f)
            )
        )
        var workoutEgo = Workout(
            id = "w_ego",
            templateId = null,
            name = "Treino Ego",
            description = "Original",
            exercisesJson = JsonUtils.serializeWorkoutExercises(listOf(originalExerciseEgo))
        )

        // Inicializa histórico com teto de 1000f
        val tonnagesHistoryEgo = mutableListOf(1000f, 1000f, 1000f, 1000f)
        var contextEgo = WorkoutContext(
            durationMinutes = 60,
            objective = "Hipertrofia",
            gymProfile = null,
            technique = "Normal",
            userPreferences = UserPreferences(),
            recuperacaoAlta = true,
            repsAlvoBatidas = true,
            tonelagemAtual = 1200f, // Excede a média móvel de 1000f em 20%
            last4WeeksTonnages = tonnagesHistoryEgo
        )

        for (semana in 1..12) {
            val inputWorkout = workoutEgo.copy(description = "Original")
            val resolved = DecisionCatalog.resolveWorkout(contextEgo, inputWorkout)
            val exercises = JsonUtils.parseWorkoutExercises(resolved.exercisesJson)
            val currentLoad = exercises[0].sets[0].weight

            val ruleId = if (resolved.description.contains("Hard Ceiling")) {
                "PROGRESSION-01_CEILING"
            } else {
                "PROGRESSION_NORMAL"
            }
            val reason = if (ruleId == "PROGRESSION-01_CEILING") {
                "Tonelagem no limite (Hard Cap). Bloqueando aumento de carga."
            } else {
                "Média móvel equalizada. Progressão liberada."
            }

            resultados.add(DecisionLog(semana, "EGO_LIFTER", ruleId, reason, currentLoad))

            // Simula progressão: se o teto liberou, aumenta carga na próxima semana.
            val nextLoad = if (ruleId == "PROGRESSION-01_CEILING") {
                currentLoad
            } else {
                currentLoad + 2.5f
            }

            // Atualiza histórico e tonelagem atual
            val actualTonnage = if (ruleId == "PROGRESSION-01_CEILING") 1200f else 1200f * (nextLoad / 100f)
            tonnagesHistoryEgo.removeAt(0)
            tonnagesHistoryEgo.add(actualTonnage)
            contextEgo = contextEgo.copy(tonelagemAtual = actualTonnage)

            val updatedExercise = exercises[0].copy(
                sets = listOf(WorkoutSet("set_1", 1, 10, nextLoad))
            )
            workoutEgo = resolved.copy(
                exercisesJson = JsonUtils.serializeWorkoutExercises(listOf(updatedExercise))
            )
        }

        // 2. Simulação do Ansiolítico (Sempre diz que está fadigado 9/10, mas tonelagem está baixa)
        val originalExerciseAns = WorkoutExercise(
            id = "ex_ans",
            exerciseId = "peito_ans",
            name = "Supino Reto",
            category = "Peito",
            sets = listOf(
                WorkoutSet("set_1", 1, 10, 80f),
                WorkoutSet("set_2", 2, 10, 80f),
                WorkoutSet("set_3", 3, 10, 80f)
            )
        )
        var workoutAns = Workout(
            id = "w_ans",
            templateId = null,
            name = "Treino Ansiolítico",
            description = "Original",
            exercisesJson = JsonUtils.serializeWorkoutExercises(listOf(originalExerciseAns))
        )

        val contextAns = WorkoutContext(
            durationMinutes = 60,
            objective = "Hipertrofia",
            gymProfile = null,
            technique = "Normal",
            userPreferences = UserPreferences(),
            fatigueLevel = 10.0, // Sempre fadigado
            tonelagemEsperada = 800f,
            tonelagemAtual = 600f, // 25% abaixo do esperado (Gatilho do Hard Floor)
            consecutiveFloorWeeks = 0
        )

        for (semana in 1..12) {
            val inputWorkout = workoutAns.copy(name = "Treino Base", description = "Original")
            val resolved = DecisionCatalog.resolveWorkout(contextAns, inputWorkout)
            val exercises = JsonUtils.parseWorkoutExercises(resolved.exercisesJson)[0]
            val currentLoad = exercises.sets[0].weight
            val setsCount = exercises.sets.size

            val ruleId = if (resolved.name.contains("Real Deload")) {
                "RECOVERY-02_DELOAD"
            } else {
                "RECOVERY-02_FLOOR"
            }
            val reason = if (ruleId == "RECOVERY-02_DELOAD") {
                "3ª semana de floor. Aplicando Deload real (volume de $setsCount series)."
            } else {
                "Fadiga alta, mas tonelagem baixa. Reduzindo apenas intensidade (-5% do peso base)."
            }

            resultados.add(DecisionLog(semana, "ANSIOLITICO", ruleId, reason, currentLoad))

            workoutAns = resolved
        }

        // Imprime o Relatório em formato CSV
        println("\n=====================================")
        println("RELATÓRIO DE SIMULAÇÃO DO MOTOR (CSV)")
        println("Semana,Perfil,RegraDisparada,Motivo,CargaSugerida(kg)")
        println("=====================================")
        
        resultados.forEach {
            println("${it.week},${it.profile},${it.ruleId},\"${it.reason}\",${it.load}")
        }
        println("=====================================\n")
    }
}
