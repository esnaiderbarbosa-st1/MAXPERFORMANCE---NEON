package com.example.services.recommendation

import com.example.data.model.Exercise
import com.example.services.recommendation.planners.*
import com.example.utils.JsonUtils
import org.junit.Assert.*
import org.junit.Test

class RecommendationEngineTest {

    private val volumePlanner = VolumePlannerImpl()
    private val restPlanner = RestPlannerImpl()
    private val progressionPlanner = ProgressionPlannerImpl()
    private val intensityPlanner = IntensityPlannerImpl()
    private val exerciseSelector = ExerciseSelectorImpl()
    private val timePlanner = TimePlannerImpl()
    private val recoveryPlanner = RecoveryPlannerImpl()
    private val recommendationEngine = RecommendationEngine(
        volumePlanner, restPlanner, progressionPlanner, intensityPlanner, exerciseSelector, timePlanner, recoveryPlanner
    )

    @Test
    fun testVolumePlanner_hypertrophy() {
        val result = volumePlanner.calculateVolume(
            objective = "hypertrophy",
            timeAvailableMinutes = 60,
            level = "Intermediário",
            recoveryIndex = 8.0
        )
        assertNotNull(result)
        assertTrue(result.exercisesCount >= 1)
        assertEquals(3, result.setsPerExercise)
        assertTrue(result.maxWeeklyVolume >= result.minWeeklyVolume)
    }

    @Test
    fun testRestPlanner_strength() {
        val dummyExercise = Exercise(
            id = "test_squat",
            name = "Agachamento",
            category = "Pernas",
            recommendedRestRange = "120-180"
        )
        val restSeconds = restPlanner.calculateRestTime(
            objective = "strength",
            methodId = "straight_sets",
            exercise = dummyExercise,
            recoveryIndex = 8.0
        )
        // For strength, baseRestSeconds is average of 120-180 = 150s, objective is 180s.
        // Combined is max of both or similar, so it should be at least 150 seconds.
        assertTrue(restSeconds >= 150)
    }

    @Test
    fun testProgressionPlanner_beginner() {
        val suggestion = progressionPlanner.suggestProgression(
            objective = "hypertrophy",
            experience = "Iniciante"
        )
        assertEquals("linear", suggestion.primaryStrategyId)
    }

    @Test
    fun testIntensityPlanner_beginner() {
        val suggestion = intensityPlanner.suggestIntensityMethods(
            objective = "hypertrophy",
            experience = "Iniciante"
        )
        // Beginners should have straight_sets suggested and avoid high fatigue advanced methods
        assertTrue(suggestion.recommendedMethods.contains("straight_sets"))
        assertFalse(suggestion.recommendedMethods.contains("myo_reps"))
    }

    @Test
    fun testExerciseSelector_filtersPeito() {
        val exercises = exerciseSelector.selectExercises(
            muscleGroup = "Peito",
            equipmentList = listOf("Halteres", "Barra"),
            timeAvailableMinutes = 45,
            restrictions = emptyList()
        )
        assertFalse(exercises.isEmpty())
        for (ex in exercises) {
            val hasPeito = ex.category == "Peito" || ex.primaryMuscle == "Peito" || ex.secondaryMuscles.any { it == "Peito" }
            assertTrue("Exercise ${ex.name} must contain Peito in its muscles", hasPeito)
            assertTrue("Exercise ${ex.name} has unexpected equipment: ${ex.equipment}", 
                ex.equipment == "Halteres" || ex.equipment == "Barra" || ex.equipment == "Peso Corporal"
            )
        }
    }

    @Test
    fun testTimePlanner_calculatesTotalSeconds() {
        val dummyExercise = Exercise(
            id = "ex1",
            name = "Supino",
            category = "Peito"
        )
        val totalSeconds = timePlanner.calculateTotalWorkoutTimeSeconds(
            exercises = listOf(dummyExercise, dummyExercise),
            setsPerExercise = 3,
            restTimeSeconds = 60
        )
        // Each exercise takes execution + rest + setup.
        // Setup default = 60, exec default = 45 * 3 = 135, rest = 60 * 2 = 120. Total per exercise = 315.
        // 2 exercises + transition = 315 + 315 + 90 = 720.
        assertEquals(720, totalSeconds)
    }

    @Test
    fun testRecoveryPlanner_extremeFatigue() {
        val actions = recoveryPlanner.assessRecovery(1.0)
        assertEquals(0.4, actions.volumeAdjustmentPercent, 0.01)
        assertTrue(actions.shouldSwapExercise)
        assertTrue(actions.avoidMethods.contains("drop_set"))
    }

    @Test
    fun testRecommendationEngine_orchestration() {
        val context = RecommendationContext(
            objective = "hypertrophy",
            timeAvailableMinutes = 45,
            availableEquipment = listOf("Halteres"),
            targetMuscleGroup = "Peito",
            userLevel = "Intermediário",
            restrictions = emptyList(),
            recoveryIndex = 8.0
        )

        val workout = recommendationEngine.generateWorkout(context)
        assertNotNull(workout)
        assertTrue(workout.name.contains("Hipertrofia") || workout.name.contains("hypertrophy"))
        assertFalse(workout.description.isEmpty())

        val exercises = JsonUtils.parseWorkoutExercises(workout.exercisesJson)
        assertFalse(exercises.isEmpty())
        for (ex in exercises) {
            assertTrue(ex.equipment == "Halteres" || ex.equipment == "Peso Corporal")
            assertFalse(ex.sets.isEmpty())
        }
    }

    @Test
    fun testClusterBoundaryHard_chestWorkouts_conformToEmpurraoOnly() {
        // Run generation 10 times to fulfill requirement: "Teste: gerar 10 treinos de peito, verificar que NENHUM contém exercício de outro cluster."
        for (i in 1..10) {
            val context = RecommendationContext(
                objective = "hypertrophy",
                timeAvailableMinutes = 45,
                availableEquipment = listOf("Halteres", "Barra", "Polia"),
                targetMuscleGroup = "Peito",
                userLevel = "Intermediário",
                restrictions = emptyList(),
                recoveryIndex = 8.0
            )

            val workout = recommendationEngine.generateWorkout(context)
            assertNotNull("Workout #$i should not be null", workout)

            // Validate that the entire workout conforms to the EMPURRAO cluster
            val isValid = validateClusterBoundary(workout, Cluster.EMPURRAO)
            assertTrue("Workout #$i failed cluster boundary validation! Target: EMPURRAO. Workout exercises: ${workout.exercises.map { "${it.name} (${it.category})" }}", isValid)

            // Double check manually in the test
            val exercises = workout.exercises
            for (ex in exercises) {
                // Ignore Cardio and Alongamento blocks
                if (ex.blockType == "Cardio" || ex.blockType == "Alongamento" || ex.category.lowercase() == "cardio" || ex.category.lowercase() == "alongamento") {
                    continue
                }
                assertEquals("Exercise ${ex.name} category is ${ex.category}, expected EMPURRAO cluster", Cluster.EMPURRAO, ex.cluster)
            }
        }
    }

    @Test
    fun testSprint2_quantityCoherence() {
        val durations = listOf(30, 45, 60, 90)
        val levels = listOf("Iniciante", "Intermediário", "Avançado", "Elite")
        
        for (level in levels) {
            for (duration in durations) {
                val context = RecommendationContext(
                    objective = "hipertrofia",
                    timeAvailableMinutes = duration,
                    availableEquipment = listOf("Barra", "Halteres", "Polia", "Máquina"),
                    targetMuscleGroup = "Peito",
                    userLevel = level,
                    restrictions = emptyList(),
                    recoveryIndex = 8.0
                )
                val workout = recommendationEngine.generateWorkout(context)
                assertNotNull(workout)
                
                val isValid = validateQuantityCoherence(workout, duration, level)
                val expectedRange = getExpectedExerciseCount(duration, level)
                assertTrue(
                    "Level: $level, Duration: $duration. Expected range: $expectedRange, got: ${workout.exercises.size}",
                    isValid
                )
            }
        }
    }

    @Test
    fun testSprint3_monotonyAndEquipmentHarmony() {
        val context = RecommendationContext(
            objective = "hipertrofia",
            timeAvailableMinutes = 60,
            availableEquipment = listOf("Barra", "Halteres", "Polia", "Máquina"),
            targetMuscleGroup = "Peito",
            userLevel = "Avançado",
            restrictions = emptyList(),
            recoveryIndex = 8.0
        )
        
        val workout = recommendationEngine.generateWorkout(context)
        assertNotNull(workout)
        
        val exercises = workout.exercises.filter { 
            it.blockType != "Cardio" && it.blockType != "Alongamento" && it.blockType != "Aquecimento"
        }
        
        println("DIAGNOSTIC - Generated exercises:")
        for (ex in exercises) {
            println("  - Name: ${ex.name}, Pattern: ${ex.movementPattern}, SpecificPattern: ${getSpecificMovementPattern(ex.name, ex.movementPattern)}, Equipment: ${ex.equipment}, SpecificEquipment: ${getSpecificImplementCategory(ex.equipment)}")
        }
        
        // 1. Verify monotony score
        val monotony = calculateMonotony(workout.exercises)
        assertTrue("Monotony score should be < 0.50, but was $monotony", monotony < 0.50)
        
        // 2. No 3+ exercises with same pattern
        val specificPatterns = exercises.map { getSpecificMovementPattern(it.name, it.movementPattern) }
        val patternCounts = specificPatterns.groupBy { it }.mapValues { it.value.size }
        for ((pattern, count) in patternCounts) {
            assertTrue("Movement pattern '$pattern' was repeated $count times, which is 3 or more!", count < 3)
        }
        
        // 3. No 3+ exercises with same implemento (equipment)
        val specificImplements = exercises.map { getSpecificImplementCategory(it.equipment) }
        val implementCounts = specificImplements.groupBy { it }.mapValues { it.value.size }
        for ((equipment, count) in implementCounts) {
            assertTrue("Equipment '$equipment' was repeated $count times, which is 3 or more!", count < 3)
        }
        
        // 4. Bodyweight <= 1 in gym
        val bodyweightCount = exercises.count { 
            it.equipment.lowercase().contains("peso corporal") || it.equipment.lowercase().contains("bodyweight")
        }
        assertTrue("Bodyweight count in commercial gym should be <= 1, but was $bodyweightCount", bodyweightCount <= 1)
    }

    @Test
    fun testSprint4_neuralSequenceAndFinalValidation() {
        val context = RecommendationContext(
            objective = "hipertrofia",
            timeAvailableMinutes = 60,
            availableEquipment = listOf("Barra", "Halteres", "Polia", "Máquina"),
            targetMuscleGroup = "Peito",
            userLevel = "Avançado",
            restrictions = emptyList(),
            recoveryIndex = 8.0
        )
        
        // 1. Generate workout
        val workout = recommendationEngine.generateWorkout(context)
        assertNotNull(workout)
        
        // 2. Map context to request to run finalValidation
        val targetCluster = Cluster.EMPURRAO
        val request = WorkoutRequest(
            cluster = targetCluster,
            duration = context.timeAvailableMinutes,
            level = context.userLevel,
            context = GymContext.GYM_COMMERCIAL
        )
        
        // 3. Perform finalValidation
        val validationResult = finalValidation(workout, request)
        assertTrue("Workout must pass all 5 validation checks, but failed with: $validationResult", validationResult is ValidationResult.Valid)
        
        // 4. Specifically verify neural demand sequence
        val exercises = workout.exercises
        val filtered = exercises.filter { 
            it.blockType != "Aquecimento" && it.blockType != "Cardio" && it.blockType != "Alongamento" &&
            it.category.lowercase() != "cardio" && it.category.lowercase() != "alongamento"
        }
        
        println("NEURAL SEQUENCE CHECK:")
        for (i in filtered.indices) {
            println("  - Exercise: ${filtered[i].name}, Demand Index: ${filtered[i].neuralDemandIndex}")
        }
        
        for (i in 0 until filtered.size - 1) {
            val firstDemand = filtered[i].neuralDemandIndex
            val secondDemand = filtered[i + 1].neuralDemandIndex
            assertTrue(
                "Neural sequence violated: ${filtered[i].name} (demand: $firstDemand) was placed before ${filtered[i+1].name} (demand: $secondDemand)",
                firstDemand >= secondDemand
            )
        }
    }

    @Test
    fun testRoleBasedComposition_allSplits() {
        val splits = listOf("Peito", "Costas", "Ombros", "Pernas")

        for (split in splits) {
            val exercises = exerciseSelector.selectExercises(
                muscleGroup = split,
                equipmentList = listOf("Barra", "Halteres", "Polia", "Máquina", "Peso Corporal"),
                timeAvailableMinutes = 60,
                restrictions = emptyList()
            )

            assertFalse("Selection for $split should not be empty", exercises.isEmpty())
            assertTrue("Selection for $split should have at least 4 exercises", exercises.size >= 4)

            println("ROLE COMPOSITION DIAGNOSTIC for $split:")
            exercises.forEachIndexed { idx, ex ->
                println("  $idx. ${ex.name} | Cat: ${ex.category} | Equip: ${ex.equipment} | Compound: ${ex.compound}")
            }

            // Check primary compound exists in position 0
            val first = exercises.first()
            assertTrue("First exercise for $split must be compound", first.compound || first.exerciseType.lowercase() == "composto")

            // Check that we have a mix of categories/muscles (Primary + Synergist or Stabilizer)
            val categories = exercises.map { it.category }.distinct()
            val primaryMuscles = exercises.map { it.primaryMuscle }.distinct()
            
            // Should contain synergists / stabilizers or variations, not only 1 single category for everything if synergists are defined
            println("  Categories included for $split: $categories")
            println("  Primary muscles included for $split: $primaryMuscles")
        }
    }

    @Test
    fun testTreinoPeito60minAvancadoAcademiaIntegrado() {
        val context = com.example.engine.WorkoutContext(
            durationMinutes = 60,
            objective = "Peito",
            gymProfile = com.example.data.model.GymProfile(
                id = "1",
                name = "Academia Comercial",
                latitude = null,
                longitude = null,
                availableExercisesIds = emptyList(),
                unavailableExercisesIds = emptyList()
            ),
            technique = "Linear",
            userPreferences = com.example.engine.UserPreferences(
                level = "Avançado",
                preferredFocus = "Peito"
            ),
            experience = "Avançado",
            targetMuscles = listOf("Peito")
        )

        val workout = com.example.engine.WorkoutEngine.generate(context)
        assertNotNull(workout)

        val exercises = workout.exercises
        println("INTEGRATED DIAGNOSTIC - Generated exercises count: ${exercises.size}")
        for (ex in exercises) {
            println("  - Name: ${ex.name}, Category: ${ex.category}, Equipment: ${ex.equipment}, BlockType: ${ex.blockType}")
        }

        // 1. All exercises must belong to the EMPURRAO cluster
        for (ex in exercises) {
            if (ex.blockType != "Cardio" && ex.blockType != "Alongamento" && ex.blockType != "Aquecimento") {
                val cluster = when (ex.category.lowercase()) {
                    "peito", "ombros", "tríceps" -> Cluster.EMPURRAO
                    "costas", "bíceps", "trapézio" -> Cluster.TRACAO
                    "pernas", "glúteos" -> Cluster.LEGS
                    "abdominais", "core" -> Cluster.CORE
                    else -> null
                }
                assertEquals("Exercise ${ex.name} does not belong to EMPURRAO cluster!", Cluster.EMPURRAO, cluster)
            }
        }

        // 2. Quantity must be between 5 and 10
        assertTrue("Quantity of exercises (${exercises.size}) must be between 5 and 10", exercises.size in 5..10)

        // 3. Monotony must be < 0.50
        val monotony = calculateMonotony(exercises)
        assertTrue("Monotony score must be < 0.50, but was $monotony", monotony < 0.50)

        // 4. Zero calisthenics/bodyweight in commercial gym for main exercises
        val bodyweightCount = exercises.count { 
            it.blockType != "Cardio" && it.blockType != "Alongamento" && it.blockType != "Aquecimento" &&
            (it.equipment.lowercase().contains("peso corporal") || it.equipment.lowercase().contains("bodyweight"))
        }
        assertEquals("There must be zero bodyweight exercises in a commercial gym, but found $bodyweightCount", 0, bodyweightCount)

        // 5. Neural sequence is valid
        val isValidSequence = verifyNeuralSequence(exercises)
        assertTrue("Neural demand sequence must be strictly non-ascending", isValidSequence)
    }

    @Test
    fun testBuild48_concurrentFatigueDetectionAndSupercompensationAdvisory() {
        val dayMs = 24 * 60 * 60 * 1000L
        val cal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 12)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val todayNoon = cal.timeInMillis

        val gymHistory = listOf(
            com.example.data.model.WorkoutHistoryEntry(
                id = "s1",
                workoutId = "FULL_BODY_A",
                workoutName = "Full Body A",
                date = todayNoon - 3 * dayMs,
                durationSeconds = 3200L,
                totalWeight = 8500f,
                exercisesCompletedCount = 6,
                exercisesJson = "[]"
            ),
            com.example.data.model.WorkoutHistoryEntry(
                id = "s2",
                workoutId = "FULL_BODY_B",
                workoutName = "Full Body B",
                date = todayNoon - 1 * dayMs,
                durationSeconds = 3300L,
                totalWeight = 9100f,
                exercisesCompletedCount = 6,
                exercisesJson = "[]"
            )
        )
        val runningHistory = listOf(
            com.example.data.model.RunningSession(
                id = "r1",
                date = todayNoon - 2 * dayMs,
                durationSec = 1800,
                distanceMeters = 4500,
                avgPaceSecPerKm = 360
            )
        )

        val pendingState = recommendationEngine.evaluateConcurrentFatigue(
            gymHistory = gymHistory,
            runningHistory = runningHistory,
            decision = FatigueAdvisoryDecision.PENDING,
            simulateThreeConsecutiveDays = false
        )

        assertTrue("3 consecutive days (Full Body A + Corrida + Full Body B) should trigger advisory", pendingState.isTriggered)
        assertTrue("Banner should be visible when PENDING", pendingState.shouldShowBanner)
        assertEquals(3, pendingState.consecutiveDays)
        assertEquals("Full Body A → Corrida → Full Body B", pendingState.sequenceFormatted)
        assertEquals(
            "Você acumulou 3 dias fortes seguidos. O ideal para hoje é Descanso Ativo ou Mobilidade Leve para blindar o SNC e as articulações.",
            pendingState.coachMessage
        )

        val acceptedRestState = recommendationEngine.evaluateConcurrentFatigue(
            gymHistory = gymHistory,
            runningHistory = runningHistory,
            decision = FatigueAdvisoryDecision.ACCEPTED_REST,
            simulateThreeConsecutiveDays = false
        )
        assertTrue("Accepted rest should mark isRestDayAccepted = true", acceptedRestState.isRestDayAccepted)
        assertEquals("Banner should hide after decision", false, acceptedRestState.shouldShowBanner)

        val overriddenState = recommendationEngine.evaluateConcurrentFatigue(
            gymHistory = gymHistory,
            runningHistory = runningHistory,
            decision = FatigueAdvisoryDecision.OVERRIDDEN_TRAIN,
            simulateThreeConsecutiveDays = false
        )
        assertEquals("Banner should hide when overridden", false, overriddenState.shouldShowBanner)

        val recoveryWorkout = recommendationEngine.createMobilityRecoveryWorkout()
        assertEquals("WORKOUT_ACTIVE_RECOVERY_MOBILITY", recoveryWorkout.id)
        assertTrue(recoveryWorkout.exercises.isNotEmpty())
    }
}

