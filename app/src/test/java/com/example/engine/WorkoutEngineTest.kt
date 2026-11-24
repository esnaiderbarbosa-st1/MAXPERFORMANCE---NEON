package com.example.engine

import com.example.utils.JsonUtils
import org.junit.Assert.*
import org.junit.Test

class WorkoutEngineTest {

    @Test
    fun testGenerateForceObjective() {
        val context = WorkoutContext(
            durationMinutes = 45,
            objective = "Força",
            gymProfile = null,
            technique = "Normal",
            userPreferences = UserPreferences()
        )

        val workout = WorkoutEngine.generate(context)
        assertNotNull(workout)
        assertTrue(workout.name.contains("Força"))

        val exercises = JsonUtils.parseWorkoutExercises(workout.exercisesJson)
        assertFalse(exercises.isEmpty())

        for (exercise in exercises.filter { it.blockType != "Aquecimento" }) {
            for (set in exercise.sets) {
                assertEquals(6, set.reps)
            }
        }
    }

    @Test
    fun testGenerateDefinitionObjective() {
        val context = WorkoutContext(
            durationMinutes = 40,
            objective = "Definição",
            gymProfile = null,
            technique = "Normal",
            userPreferences = UserPreferences()
        )

        val workout = WorkoutEngine.generate(context)
        assertNotNull(workout)
        assertTrue(workout.name.contains("Definição"))

        val exercises = JsonUtils.parseWorkoutExercises(workout.exercisesJson)
        assertFalse(exercises.isEmpty())

        for (exercise in exercises.filter { it.blockType != "Aquecimento" }) {
            for (set in exercise.sets) {
                assertEquals(15, set.reps)
            }
        }
    }

    @Test
    fun testGenerateHypertrophyObjective() {
        val context = WorkoutContext(
            durationMinutes = 40,
            objective = "Hipertrofia",
            gymProfile = null,
            technique = "Normal",
            userPreferences = UserPreferences()
        )

        val workout = WorkoutEngine.generate(context)
        assertNotNull(workout)
        assertTrue(workout.name.contains("Hipertrofia"))

        val exercises = JsonUtils.parseWorkoutExercises(workout.exercisesJson)
        assertFalse(exercises.isEmpty())

        for (exercise in exercises.filter { it.blockType != "Aquecimento" }) {
            for (set in exercise.sets) {
                assertEquals(10, set.reps)
            }
        }
    }

    @Test
    fun testChestWorkoutOnlyAllowsPushClusterAndNoHybrids() {
        val context = WorkoutContext(
            durationMinutes = 60,
            objective = "Peito",
            gymProfile = null,
            technique = "Normal",
            userPreferences = UserPreferences()
        )

        val workout = WorkoutEngine.generate(context)
        assertNotNull(workout)
        val exercises = JsonUtils.parseWorkoutExercises(workout.exercisesJson)
        assertFalse(exercises.isEmpty())

        for (exercise in exercises) {
            // All exercises in a Chest workout must belong to the EMPURRÃO cluster
            val categoryLower = exercise.category.lowercase()
            assertTrue(
                "Exercise ${exercise.name} in category ${exercise.category} is not in EMPURRÃO cluster",
                categoryLower == "peito" || categoryLower == "ombros" || categoryLower == "tríceps"
            )
            // Biomechanical dilution prevention check: absolutely no Pull/Tração exercises!
            assertNotEquals("Costas", exercise.category)
            assertNotEquals("Bíceps", exercise.category)
        }
    }

    @Test
    fun testInsufficientDurationWarning() {
        val context = WorkoutContext(
            durationMinutes = 15,
            objective = "Peito",
            gymProfile = null,
            technique = "Normal",
            userPreferences = UserPreferences()
        )

        val workout = WorkoutEngine.generate(context)
        assertNotNull(workout)
        val exercises = JsonUtils.parseWorkoutExercises(workout.exercisesJson)
        
        // If the duration is so short that we prescribe less than 2 exercises, the warning must be present
        if (exercises.size < 2) {
            assertTrue(
                "Description does not contain the Portuguese warning. Actual: ${workout.description}",
                workout.description.contains("Para um treino de Peito com qualidade mínima, recomendamos 40 minutos")
            )
        }
    }

    @Test
    fun testCalisthenicsDeprioritizedByDefaultButAllowedIfPreferred() {
        // Scenario 1: Default workout with weights. Bodyweight exercises like peito_5 (Flexão de Braços) should be excluded
        val defaultContext = WorkoutContext(
            durationMinutes = 60,
            objective = "Peito",
            gymProfile = null,
            technique = "Normal",
            userPreferences = UserPreferences(),
            availableEquipment = listOf("Barra", "Halteres", "Polia", "Máquina")
        )

        val defaultWorkout = WorkoutEngine.generate(defaultContext)
        val defaultExercises = JsonUtils.parseWorkoutExercises(defaultWorkout.exercisesJson)
        
        val containsFlexaoByDefault = defaultExercises.any { it.exerciseId == "peito_5" }
        assertFalse("By default, Flexão de Braços should be excluded from chest workout", containsFlexaoByDefault)

        // Scenario 2: Explicitly set peito_5 as preferred. It should be included in the workout
        val preferredContext = WorkoutContext(
            durationMinutes = 60,
            objective = "Peito",
            gymProfile = null,
            technique = "Normal",
            userPreferences = UserPreferences(),
            availableEquipment = listOf("Barra", "Halteres", "Polia", "Máquina", "Peso Corporal"),
            preferredExercises = listOf("peito_5")
        )

        val preferredWorkout = WorkoutEngine.generate(preferredContext)
        val preferredExercises = JsonUtils.parseWorkoutExercises(preferredWorkout.exercisesJson)
        
        println("DEBUG PREFERRED EXERCISES: ${preferredExercises.map { it.exerciseId }}")
        val containsFlexaoIfPreferred = preferredExercises.any { it.exerciseId == "peito_5" }
        assertTrue("Flexão de Braços should be included if explicitly preferred", containsFlexaoIfPreferred)
    }

    @Test
    fun testFullBodyAbcVariantsAndRunTrackerV46() {
        val engine = com.example.services.recommendation.RecommendationEngine()
        val family = engine.generateFullBodyFamily()
        assertEquals("Full Body family must have exactly 3 workouts (A, B, C)", 3, family.size)
        assertTrue(family[0].name.startsWith("Full Body A"))
        assertTrue(family[1].name.startsWith("Full Body B"))
        assertTrue(family[2].name.startsWith("Full Body C"))

        family.forEach { workout ->
            val exs = JsonUtils.parseWorkoutExercises(workout.exercisesJson)
            assertTrue("Each Full Body variant should have at least 7 exercises", exs.size >= 7)
        }

        // Verify RunTracker 15s grace period and TTS announcements
        assertEquals(15, com.example.services.RunTracker.PACE_GRACE_PERIOD_SECONDS)
        val splitMsg = com.example.services.RunTracker.buildKmSplitAnnouncement(
            kmNumber = 2,
            splitDurationSec = 315,
            avgPaceSecPerKm = 320
        )
        assertTrue(splitMsg.contains("2 quilômetros, tempo parcial"))
        assertTrue(splitMsg.contains("por quilômetro", ignoreCase = true))

        val coolDownMsg = com.example.services.RunTracker.buildCoolDownTransitionAnnouncement(5)
        assertTrue(coolDownMsg.contains("desaquecimento", ignoreCase = true))
    }
}

