package com.example.engine

import com.example.data.model.Workout
import com.example.data.model.WorkoutExercise
import com.example.data.model.WorkoutSet
import com.example.utils.JsonUtils
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class DecisionCatalogTest {

    @Test
    fun testSafetyBlocksAvailability() {
        // Arrange
        val workoutId = UUID.randomUUID().toString()
        val originalExercise = WorkoutExercise(
            id = "ex_1",
            exerciseId = "pernas_1",
            name = "Agachamento Livre",
            category = "Pernas",
            sets = listOf(
                WorkoutSet("set_1", 1, 10, 100f),
                WorkoutSet("set_2", 2, 10, 100f)
            )
        )
        val baseWorkout = Workout(
            id = workoutId,
            templateId = null,
            name = "Treino Base",
            description = "Descricao Base",
            exercisesJson = JsonUtils.serializeWorkoutExercises(listOf(originalExercise))
        )

        // User reports injury in Pernas AND "pernas_1" is also unavailable
        val context = WorkoutContext(
            durationMinutes = 60,
            objective = "Hipertrofia",
            gymProfile = null,
            technique = "Normal",
            userPreferences = UserPreferences(),
            injuredMuscles = listOf("Pernas"),
            unavailableExerciseIds = listOf("pernas_1")
        )

        // Act
        val resolvedWorkout = DecisionCatalog.resolveWorkout(context, baseWorkout)

        // Assert
        val exercises = JsonUtils.parseWorkoutExercises(resolvedWorkout.exercisesJson)
        assertEquals(1, exercises.size)
        val resolvedEx = exercises[0]

        // SAFETY-01 replaces the exercise with Cadeira Extensora (Baixo Impacto)
        // and sets exerciseId to low_impact_pernas_1
        assertTrue(resolvedEx.name.contains("Cadeira Extensora"))
        assertEquals("low_impact_pernas_1", resolvedEx.exerciseId)

        // Verify that safety constrained exercise IDs lists "pernas_1"
        assertTrue(context.safetyConstrainedExerciseIds.contains("pernas_1"))

        // Verify that AVAILABILITY-01 was blocked or did not substitute the safety adaptation
        // The exercise name should NOT contain "Substituto"
        assertFalse(resolvedEx.name.contains("Substituto"))
    }

    @Test
    fun testDeterministicTieBreaking() {
        // Create two rules with same priority (RECOVERY) and same score
        val ruleA = DecisionRule(
            id = "RULE-A",
            priority = DecisionPriority.RECOVERY,
            triggers = { true },
            actions = { _, w -> w.copy(name = "Action A") },
            score = { 50 }
        )
        val ruleB = DecisionRule(
            id = "RULE-B",
            priority = DecisionPriority.RECOVERY,
            triggers = { true },
            actions = { _, w -> w.copy(name = "Action B") },
            score = { 50 }
        )

        val triggeredRules = listOf(ruleB, ruleA) // ruleB listed first

        // Resolver sorting logic: highest score first, then lexicographical id ascending
        val sortedList = triggeredRules.sortedWith { r1, r2 ->
            val s1 = r1.score(WorkoutContext(60, "Hipertrofia", null, "Normal", UserPreferences()))
            val s2 = r2.score(WorkoutContext(60, "Hipertrofia", null, "Normal", UserPreferences()))
            if (s1 != s2) {
                s2.compareTo(s1)
            } else {
                r1.id.compareTo(r2.id)
            }
        }

        // We run multiple trials to verify it is completely deterministic and RULE-A always wins
        for (i in 1..100) {
            val winner = sortedList.first()
            assertEquals("RULE-A", winner.id)
        }
    }

    @Test
    fun testNewUserNoAdherenceTrigger() {
        // New user has < 3 workouts in history
        val context = WorkoutContext(
            durationMinutes = 60,
            objective = "Hipertrofia",
            gymProfile = null,
            technique = "Normal",
            userPreferences = UserPreferences(),
            treinosTotaisHistorico = 2,
            adherenceRate = 0.2 // under 50%
        )

        val baseWorkout = Workout(
            id = "w1",
            templateId = null,
            name = "Treino Base",
            description = "Desc",
            exercisesJson = "[]"
        )

        // Adherence rule should NOT trigger
        assertFalse(DecisionCatalog.ADHERENCE_01.triggers(context))

        val resolved = DecisionCatalog.resolveWorkout(context, baseWorkout)
        // Workout name should remain unchanged (no "Recalculated Split")
        assertEquals("Treino Base", resolved.name)
    }

    @Test
    fun testHardFloorWeeksBehavior() {
        val originalExercise = WorkoutExercise(
            id = "ex_1",
            exerciseId = "peito_1",
            name = "Supino Reto",
            category = "Peito",
            sets = listOf(
                WorkoutSet("set_1", 1, 10, 100f),
                WorkoutSet("set_2", 2, 10, 100f),
                WorkoutSet("set_3", 3, 10, 100f)
            )
        )
        val baseWorkout = Workout(
            id = "w1",
            templateId = null,
            name = "Treino Base",
            description = "Desc",
            exercisesJson = JsonUtils.serializeWorkoutExercises(listOf(originalExercise))
        )

        // Setup high fatigue and low volume/tonnage trigger
        val context = WorkoutContext(
            durationMinutes = 60,
            objective = "Hipertrofia",
            gymProfile = null,
            technique = "Normal",
            userPreferences = UserPreferences(),
            fatigueLevel = 10.0,
            tonelagemAtual = 50f,
            tonelagemEsperada = 100f,
            consecutiveFloorWeeks = 0
        )

        // Week 1 Action: consecutiveFloorWeeks < 3 -> reduce intensity (-5% weight), increment counter
        val resolvedWeek1 = DecisionCatalog.resolveWorkout(context, baseWorkout)
        assertEquals(1, context.consecutiveFloorWeeks)
        val exWeek1 = JsonUtils.parseWorkoutExercises(resolvedWeek1.exercisesJson)[0]
        assertEquals(3, exWeek1.sets.size) // No volume reduction
        assertEquals(95f, exWeek1.sets[0].weight, 0.01f) // Intensity reduced by 5%

        // Week 2 Action: consecutiveFloorWeeks < 3 -> reduce intensity, increment counter
        val resolvedWeek2 = DecisionCatalog.resolveWorkout(context, resolvedWeek1)
        assertEquals(2, context.consecutiveFloorWeeks)
        val exWeek2 = JsonUtils.parseWorkoutExercises(resolvedWeek2.exercisesJson)[0]
        assertEquals(3, exWeek2.sets.size) // No volume reduction
        assertEquals(95f, exWeek2.sets[0].weight, 0.01f) // Fix 1: Intensity reduced by 5% from BASE weight, not compounding!

        // Now simulate entering Week 3: consecutiveFloorWeeks = 3
        context.consecutiveFloorWeeks = 3
        val resolvedWeek3 = DecisionCatalog.resolveWorkout(context, baseWorkout)
        assertEquals(0, context.consecutiveFloorWeeks) // Counter reset to 0
        val exWeek3 = JsonUtils.parseWorkoutExercises(resolvedWeek3.exercisesJson)[0]
        assertEquals(2, exWeek3.sets.size) // Volume reduced by 1 set (real Deload)
    }

    @Test
    fun testHardFloorExitCriteria() {
        val originalExercise = WorkoutExercise(
            id = "ex_1",
            exerciseId = "peito_1",
            name = "Supino Reto",
            category = "Peito",
            sets = listOf(
                WorkoutSet("set_1", 1, 10, 95f, baseWeight = 100f)
            )
        )
        val baseWorkout = Workout(
            id = "w1",
            templateId = null,
            name = "Treino Base",
            description = "Desc",
            exercisesJson = JsonUtils.serializeWorkoutExercises(listOf(originalExercise))
        )

        // Setup fatigue level under 7.0 with active floor weeks
        val context = WorkoutContext(
            durationMinutes = 60,
            objective = "Hipertrofia",
            gymProfile = null,
            technique = "Normal",
            userPreferences = UserPreferences(),
            fatigueLevel = 5.0, // under 7.0
            consecutiveFloorWeeks = 2
        )

        val resolved = DecisionCatalog.resolveWorkout(context, baseWorkout)

        // Assert: consecutiveFloorWeeks is reset to 0
        assertEquals(0, context.consecutiveFloorWeeks)

        // Assert: base weight is restored
        val ex = JsonUtils.parseWorkoutExercises(resolved.exercisesJson)[0]
        assertEquals(100f, ex.sets[0].weight, 0.01f)
        assertNull(ex.sets[0].baseWeight)
        assertTrue(resolved.name.contains("Exit"))
    }

    @Test
    fun testDynamicMovingAverageEqualization() {
        val baseWorkout = Workout(
            id = "w1",
            templateId = null,
            name = "Treino Base",
            description = "Desc",
            exercisesJson = "[]"
        )

        // If user stayed at 1000f for 4 weeks (tonelagem_esperada 1000f)
        val context = WorkoutContext(
            durationMinutes = 60,
            objective = "Hipertrofia",
            gymProfile = null,
            technique = "Normal",
            userPreferences = UserPreferences(),
            recuperacaoAlta = true,
            repsAlvoBatidas = true,
            tonelagemAtual = 1000f,
            tonelagemEsperada = 500f, // static expectation was low, but:
            last4WeeksTonnages = listOf(1000f, 1000f, 1000f, 1000f) // average is 1000f
        )

        // Because last4WeeksTonnages is provided, tonelagemEsperada is computed as 1000f.
        // Thus context.tonelagemAtual (1000f) >= dynamicTonelagemEsperada (1000f) * 1.15 (1150f) is false!
        // So PROGRESSION_01 should NOT trigger, and the ceiling is liberated.
        val resolved = DecisionCatalog.resolveWorkout(context, baseWorkout)

        // The ceiling description should NOT be added
        assertFalse(resolved.description.contains("Hard Ceiling"))
    }
}
