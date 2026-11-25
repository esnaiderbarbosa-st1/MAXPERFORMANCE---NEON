package com.example.data.repository

import com.example.data.model.WorkoutExercise
import com.example.data.model.WorkoutHistoryEntry
import com.example.data.model.WorkoutSet
import com.example.services.recommendation.knowledge.TrainingKnowledgeBase
import com.example.utils.JsonUtils
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LoadProgressionTest {

    @Before
    fun setUp() {
        ExerciseHistoryRepository.clearCache()
        ExerciseHistoryRepository.clearAiOverrides()
    }

    @After
    fun tearDown() {
        ExerciseHistoryRepository.clearCache()
        ExerciseHistoryRepository.clearAiOverrides()
    }

    private fun buildHistoryEntry(
        id: String,
        dateMillis: Long,
        exerciseName: String,
        equipment: String,
        sets: List<WorkoutSet>,
        progressionType: String = "Linear"
    ): WorkoutHistoryEntry {
        val ex = WorkoutExercise(
            id = "ex_$id",
            exerciseId = exerciseName.lowercase().replace(" ", "_"),
            name = exerciseName,
            category = "Peito",
            sets = sets,
            equipment = equipment,
            progressionType = progressionType
        )
        val totalWeight = sets.filter { it.completed }.sumOf { (it.weight * it.reps).toDouble() }.toFloat()
        return WorkoutHistoryEntry(
            id = id,
            workoutId = "w1",
            workoutName = "Treino A",
            date = dateMillis,
            durationSeconds = 2400L,
            totalWeight = totalWeight.coerceAtLeast(100f),
            exercisesCompletedCount = 1,
            exercisesJson = JsonUtils.serializeWorkoutExercises(listOf(ex))
        )
    }

    @Test
    fun linearProgression_allSetsHitTargetReps_barbellSuggestsSingleConservativeStep() {
        val history = listOf(
            buildHistoryEntry(
                id = "h1",
                dateMillis = 1_700_000_000_000L,
                exerciseName = "Supino Reto com Barra",
                equipment = "Barra",
                sets = listOf(
                    WorkoutSet(id = "s1", index = 1, reps = 10, weight = 60f, completed = true),
                    WorkoutSet(id = "s2", index = 2, reps = 10, weight = 60f, completed = true),
                    WorkoutSet(id = "s3", index = 3, reps = 10, weight = 60f, completed = true)
                )
            )
        )

        val suggestion = ExerciseHistoryRepository.getSuggestedLoadDetailsForExercise(
            history = history,
            exerciseNameOrId = "Supino Reto com Barra",
            equipment = "Barra",
            progressionType = "Linear",
            targetReps = 10
        )

        assertTrue(suggestion.progressionApplied)
        assertEquals("linear", suggestion.strategyId)
        assertEquals(60f, suggestion.previousWeightKg, 0.01f)
        assertEquals(62.5f, suggestion.suggestedWeightKg, 0.01f)
        assertEquals(2.5f, suggestion.deltaKg, 0.01f)
        assertEquals(
            62.5f,
            ExerciseHistoryRepository.getSuggestedWeightForExercise(
                history = history,
                exerciseNameOrId = "Supino Reto com Barra",
                equipment = "Barra",
                progressionType = "Linear",
                targetReps = 10
            ),
            0.01f
        )
    }

    @Test
    fun doubleProgression_allSetsHitTargetReps_dumbbellSuggestsConservativeStepWithinCeiling() {
        val history = listOf(
            buildHistoryEntry(
                id = "h_db_heavy",
                dateMillis = 1_700_000_000_000L,
                exerciseName = "Supino Inclinado com Halteres",
                equipment = "Halteres",
                sets = listOf(
                    WorkoutSet(id = "s1", index = 1, reps = 12, weight = 22f, completed = true),
                    WorkoutSet(id = "s2", index = 2, reps = 12, weight = 22f, completed = true),
                    WorkoutSet(id = "s3", index = 3, reps = 12, weight = 22f, completed = true)
                ),
                progressionType = "Progressão Dupla"
            )
        )

        val suggestion = ExerciseHistoryRepository.getSuggestedLoadDetailsForExercise(
            history = history,
            exerciseNameOrId = "Supino Inclinado com Halteres",
            equipment = "Halteres",
            progressionType = "Progressão Dupla",
            targetReps = 12
        )

        assertTrue(suggestion.progressionApplied)
        assertEquals("double_progression", suggestion.strategyId)
        assertEquals(22f, suggestion.previousWeightKg, 0.01f)
        assertEquals(24f, suggestion.suggestedWeightKg, 0.01f)
        assertEquals(2.0f, suggestion.deltaKg, 0.01f)
    }

    @Test
    fun linearProgression_lightDumbbellBelow10Kg_increasesBy1Kg() {
        val history = listOf(
            buildHistoryEntry(
                id = "h_db_light",
                dateMillis = 1_700_000_000_000L,
                exerciseName = "Elevação Lateral com Halteres",
                equipment = "Halteres",
                sets = listOf(
                    WorkoutSet(id = "s1", index = 1, reps = 12, weight = 8f, completed = true),
                    WorkoutSet(id = "s2", index = 2, reps = 12, weight = 8f, completed = true),
                    WorkoutSet(id = "s3", index = 3, reps = 12, weight = 8f, completed = true)
                )
            )
        )

        val suggestion = ExerciseHistoryRepository.getSuggestedLoadDetailsForExercise(
            history = history,
            exerciseNameOrId = "Elevação Lateral com Halteres",
            equipment = "Halteres",
            progressionType = "Linear",
            targetReps = 12
        )

        assertTrue(suggestion.progressionApplied)
        assertEquals(9f, suggestion.suggestedWeightKg, 0.01f)
        assertEquals(1.0f, suggestion.deltaKg, 0.01f)
    }

    @Test
    fun criteriaNotMet_oneSetBelowTargetReps_preservesPreviousWeight() {
        val history = listOf(
            buildHistoryEntry(
                id = "h_short_rep",
                dateMillis = 1_700_000_000_000L,
                exerciseName = "Supino Reto com Barra",
                equipment = "Barra",
                sets = listOf(
                    WorkoutSet(id = "s1", index = 1, reps = 10, weight = 60f, completed = true),
                    WorkoutSet(id = "s2", index = 2, reps = 10, weight = 60f, completed = true),
                    WorkoutSet(id = "s3", index = 3, reps = 8, weight = 60f, completed = true)
                )
            )
        )

        val suggestion = ExerciseHistoryRepository.getSuggestedLoadDetailsForExercise(
            history = history,
            exerciseNameOrId = "Supino Reto com Barra",
            equipment = "Barra",
            progressionType = "Linear",
            targetReps = 10
        )

        assertFalse(suggestion.progressionApplied)
        assertEquals(60f, suggestion.suggestedWeightKg, 0.01f)
        assertEquals(0f, suggestion.deltaKg, 0.01f)
    }

    @Test
    fun criteriaNotMet_incompleteSetInLatestSession_preservesPreviousWeight() {
        val history = listOf(
            buildHistoryEntry(
                id = "h_incomplete",
                dateMillis = 1_700_000_000_000L,
                exerciseName = "Cadeira Extensora",
                equipment = "Máquina",
                sets = listOf(
                    WorkoutSet(id = "s1", index = 1, reps = 12, weight = 61f, completed = true),
                    WorkoutSet(id = "s2", index = 2, reps = 12, weight = 61f, completed = true),
                    WorkoutSet(id = "s3", index = 3, reps = 12, weight = 61f, completed = false)
                )
            )
        )

        val suggestion = ExerciseHistoryRepository.getSuggestedLoadDetailsForExercise(
            history = history,
            exerciseNameOrId = "Cadeira Extensora",
            equipment = "Máquina",
            progressionType = "Linear",
            targetReps = 12
        )

        assertFalse(suggestion.progressionApplied)
        assertEquals(61f, suggestion.suggestedWeightKg, 0.01f)
        assertEquals(0f, suggestion.deltaKg, 0.01f)
    }

    @Test
    fun antiJumpCeiling_legPressAndLowerBodyBarbell_neverExceedEquipmentCaps() {
        val legPressHistory = listOf(
            buildHistoryEntry(
                id = "h_legpress",
                dateMillis = 1_700_000_000_000L,
                exerciseName = "Leg Press 45",
                equipment = "Máquina",
                sets = listOf(
                    WorkoutSet(id = "s1", index = 1, reps = 10, weight = 120f, completed = true),
                    WorkoutSet(id = "s2", index = 2, reps = 10, weight = 120f, completed = true),
                    WorkoutSet(id = "s3", index = 3, reps = 10, weight = 120f, completed = true)
                )
            )
        )

        val legPressSuggestion = ExerciseHistoryRepository.getSuggestedLoadDetailsForExercise(
            history = legPressHistory,
            exerciseNameOrId = "Leg Press 45",
            equipment = "Máquina",
            progressionType = "Linear",
            targetReps = 10,
            isLowerBodyCompound = true
        )

        // Default step for Leg Press (Anilhas) is 10kg, but anti-jump cap strictly limits machine progression to <= +5kg
        assertTrue(legPressSuggestion.progressionApplied)
        assertEquals(125f, legPressSuggestion.suggestedWeightKg, 0.01f)
        assertTrue("Machine delta must not exceed 5kg", legPressSuggestion.deltaKg <= 5.0f)

        ExerciseHistoryRepository.clearCache()

        val squatHistory = listOf(
            buildHistoryEntry(
                id = "h_squat",
                dateMillis = 1_700_000_000_000L,
                exerciseName = "Agachamento Livre com Barra",
                equipment = "Barra",
                sets = listOf(
                    WorkoutSet(id = "s1", index = 1, reps = 8, weight = 80f, completed = true),
                    WorkoutSet(id = "s2", index = 2, reps = 8, weight = 80f, completed = true),
                    WorkoutSet(id = "s3", index = 3, reps = 8, weight = 80f, completed = true)
                )
            )
        )

        val squatSuggestion = ExerciseHistoryRepository.getSuggestedLoadDetailsForExercise(
            history = squatHistory,
            exerciseNameOrId = "Agachamento Livre com Barra",
            equipment = "Barra",
            progressionType = "Linear",
            targetReps = 8,
            isLowerBodyCompound = true
        )

        assertTrue(squatSuggestion.progressionApplied)
        assertEquals(82.5f, squatSuggestion.suggestedWeightKg, 0.01f)
        assertTrue("Barbell delta must not exceed 2.5kg", squatSuggestion.deltaKg <= 2.5f)
    }

    @Test
    fun antiJumpCeiling_oddDumbbellWeight_doesNotJump3KgOnRounding() {
        val history = listOf(
            buildHistoryEntry(
                id = "h_odd_db",
                dateMillis = 1_700_000_000_000L,
                exerciseName = "Rosca Alternada com Halteres",
                equipment = "Halteres",
                sets = listOf(
                    WorkoutSet(id = "s1", index = 1, reps = 10, weight = 11f, completed = true),
                    WorkoutSet(id = "s2", index = 2, reps = 10, weight = 11f, completed = true)
                )
            )
        )

        val suggestion = ExerciseHistoryRepository.getSuggestedLoadDetailsForExercise(
            history = history,
            exerciseNameOrId = "Rosca Alternada com Halteres",
            equipment = "Halteres",
            progressionType = "Linear",
            targetReps = 10
        )

        assertTrue(suggestion.progressionApplied)
        assertTrue("Dumbbell delta must stay <= 2.0kg (was ${suggestion.deltaKg})", suggestion.deltaKg <= 2.0f)
        assertEquals(12f, suggestion.suggestedWeightKg, 0.01f)
    }

    @Test
    fun deloadAndMaintenanceAndLatestSessionPriority_respected() {
        val history = listOf(
            buildHistoryEntry(
                id = "h_older_hit",
                dateMillis = 1_700_000_000_000L,
                exerciseName = "Remada Curvada com Barra",
                equipment = "Barra",
                sets = listOf(
                    WorkoutSet(id = "s1", index = 1, reps = 10, weight = 50f, completed = true),
                    WorkoutSet(id = "s2", index = 2, reps = 10, weight = 50f, completed = true)
                )
            ),
            buildHistoryEntry(
                id = "h_latest_miss",
                dateMillis = 1_700_500_000_000L,
                exerciseName = "Remada Curvada com Barra",
                equipment = "Barra",
                sets = listOf(
                    WorkoutSet(id = "s1", index = 1, reps = 10, weight = 50f, completed = true),
                    WorkoutSet(id = "s2", index = 2, reps = 7, weight = 50f, completed = true)
                )
            )
        )

        // Most recent session missed targetReps -> must keep 50kg even though older session hit targetReps
        val latestCheck = ExerciseHistoryRepository.getSuggestedLoadDetailsForExercise(
            history = history,
            exerciseNameOrId = "Remada Curvada com Barra",
            equipment = "Barra",
            progressionType = "Linear",
            targetReps = 10
        )
        assertFalse(latestCheck.progressionApplied)
        assertEquals(50f, latestCheck.suggestedWeightKg, 0.01f)

        // Explicit Deload reduces by ~10% (50 -> 45)
        val deload = ExerciseHistoryRepository.getSuggestedLoadDetailsForExercise(
            history = history,
            exerciseNameOrId = "Remada Curvada com Barra",
            equipment = "Barra",
            progressionType = "Deload",
            targetReps = 10
        )
        assertFalse(deload.progressionApplied)
        assertEquals("deload", deload.strategyId)
        assertEquals(45f, deload.suggestedWeightKg, 0.01f)

        // Explicit Maintenance preserves weight even when criteria are met
        val eligibleOnly = listOf(history.first())
        ExerciseHistoryRepository.clearCache()
        val maintenance = ExerciseHistoryRepository.getSuggestedLoadDetailsForExercise(
            history = eligibleOnly,
            exerciseNameOrId = "Remada Curvada com Barra",
            equipment = "Barra",
            progressionType = "Manutenção",
            targetReps = 10
        )
        assertFalse(maintenance.progressionApplied)
        assertEquals("maintenance", maintenance.strategyId)
        assertEquals(50f, maintenance.suggestedWeightKg, 0.01f)

        // Knowledge base ceiling verification
        assertEquals(2.0f, TrainingKnowledgeBase.ProgressionStrategies.maxConservativeIncrementKg("Halteres"), 0.01f)
        assertEquals(2.5f, TrainingKnowledgeBase.ProgressionStrategies.maxConservativeIncrementKg("Barra"), 0.01f)
        assertEquals(5.0f, TrainingKnowledgeBase.ProgressionStrategies.maxConservativeIncrementKg("Máquina"), 0.01f)
    }
}
