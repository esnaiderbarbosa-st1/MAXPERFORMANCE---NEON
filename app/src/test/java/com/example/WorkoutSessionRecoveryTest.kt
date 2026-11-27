package com.example

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.AppDatabase
import com.example.data.model.ActiveWorkoutSession
import com.example.data.model.SetType
import com.example.data.model.Workout
import com.example.data.model.WorkoutExercise
import com.example.data.model.WorkoutSessionEntity
import com.example.data.model.WorkoutSessionStatus
import com.example.data.model.WorkoutSet
import com.example.data.model.WorkoutTimeUtils
import com.example.data.repository.FitnessRepository
import com.example.data.repository.WorkoutSessionRecoveryCoordinator
import com.example.services.WorkoutForegroundService
import com.example.services.WorkoutServiceState
import com.example.ui.viewmodel.AppScreen
import com.example.ui.viewmodel.MainViewModel
import com.example.ui.viewmodel.WorkoutFlowState
import com.example.utils.JsonUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WorkoutSessionRecoveryTest {

    private lateinit var app: Application
    private lateinit var inMemoryDb: AppDatabase
    private lateinit var inMemoryRepo: FitnessRepository
    private lateinit var isolatedPrefs: SharedPreferences
    private lateinit var appPrefs: SharedPreferences
    private val activeStores = mutableListOf<ViewModelStore>()

    @Before
    fun setUp() = runBlocking {
        app = ApplicationProvider.getApplicationContext()
        inMemoryDb = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        inMemoryRepo = FitnessRepository(inMemoryDb)
        isolatedPrefs = app.getSharedPreferences("test_recovery_${UUID.randomUUID()}", Context.MODE_PRIVATE)
        appPrefs = app.getSharedPreferences("max_performance_prefs", Context.MODE_PRIVATE)
        appPrefs.edit().clear().commit()

        val appDb = AppDatabase.getDatabase(app)
        appDb.workoutSessionDao().clearAll()
        appDb.historyDao().clearAllHistory()

        resetServiceSingleton()
    }

    @After
    fun tearDown() = runBlocking {
        activeStores.forEach { runCatching { it.clear() } }
        activeStores.clear()
        inMemoryDb.close()
        appPrefs.edit().clear().commit()
        AppDatabase.getDatabase(app).workoutSessionDao().clearAll()
        resetServiceSingleton()
    }

    private fun resetServiceSingleton() {
        WorkoutForegroundService.isServiceRunning = false
        WorkoutForegroundService.isWorkoutActive = false
        WorkoutServiceState.clearWorkoutClock()
        WorkoutServiceState.resetTimers()
        WorkoutServiceState.workoutDurationSeconds.value = 0L
    }

    private fun createMainViewModel(): Pair<MainViewModel, ViewModelStore> {
        val store = ViewModelStore()
        activeStores.add(store)
        val vm = ViewModelProvider(
            store,
            ViewModelProvider.AndroidViewModelFactory(app)
        )[MainViewModel::class.java]
        return vm to store
    }

    private suspend fun awaitCondition(timeoutMs: Long = 5000L, predicate: () -> Boolean) {
        withTimeout(timeoutMs) {
            while (!predicate()) {
                Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
                delay(20L)
            }
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        }
    }

    private fun sampleExercises(
        firstSetCompleted: Boolean = true,
        firstWeight: Float = 80f,
        firstReps: Int = 10,
        firstRpe: String = "8.5",
        secondSetCompleted: Boolean = false,
        secondWeight: Float = 82.5f,
        secondReps: Int = 8,
        secondRpe: String = ""
    ): List<WorkoutExercise> {
        return listOf(
            WorkoutExercise(
                id = "ex_bench_1",
                exerciseId = "peito_1",
                name = "Supino Reto",
                category = "Peito",
                equipment = "Barra",
                restTimeSeconds = 90,
                progressionType = "Linear",
                executionType = "REPS",
                notes = "Manter escápulas retraídas",
                sets = listOf(
                    WorkoutSet(
                        id = "set_1",
                        index = 1,
                        reps = firstReps,
                        weight = firstWeight,
                        baseWeight = 80f,
                        completed = firstSetCompleted,
                        rpe = firstRpe,
                        setType = SetType.STANDARD
                    ),
                    WorkoutSet(
                        id = "set_2",
                        index = 2,
                        reps = secondReps,
                        weight = secondWeight,
                        baseWeight = 80f,
                        completed = secondSetCompleted,
                        rpe = secondRpe,
                        setType = SetType.STANDARD
                    )
                )
            ),
            WorkoutExercise(
                id = "ex_row_2",
                exerciseId = "costas_1",
                name = "Remada Curvada",
                category = "Costas",
                equipment = "Barra",
                restTimeSeconds = 75,
                progressionType = "Linear",
                executionType = "REPS",
                sets = listOf(
                    WorkoutSet(
                        id = "set_3",
                        index = 1,
                        reps = 12,
                        weight = 60f,
                        baseWeight = 60f,
                        completed = false,
                        rpe = "",
                        setType = SetType.STANDARD
                    )
                )
            )
        )
    }

    private fun sampleWorkout(
        id: String = "w_upper_a",
        name: String = "Upper A - Força",
        exercises: List<WorkoutExercise> = sampleExercises()
    ): Workout {
        return Workout(
            id = id,
            name = name,
            exercisesJson = JsonUtils.serializeWorkoutExercises(exercises)
        )
    }

    private fun writeFallbackPrefs(
        prefs: SharedPreferences,
        sessionId: String,
        workout: Workout,
        exercises: List<WorkoutExercise>,
        startTimeMillis: Long,
        elapsedSeconds: Long = 320L,
        currentExerciseIndex: Int = 0,
        flowState: String = "Running",
        sessionState: String = "ACTIVE",
        isPreSession: Boolean = false,
        bootCount: Int? = null,
        savedTimestampMillis: Long = System.currentTimeMillis()
    ) {
        val exercisesJson = JsonUtils.serializeWorkoutExercises(exercises)
        val hydratedWorkout = workout.copy(exercisesJson = exercisesJson)
        val workoutJson = JsonUtils.moshi.adapter(Workout::class.java).toJson(hydratedWorkout)
        val activeSession = ActiveWorkoutSession(
            workoutId = workout.id,
            sessionId = sessionId,
            startTime = startTimeMillis,
            currentExerciseIndex = currentExerciseIndex,
            completedExercises = emptyList(),
            completedSets = emptyList(),
            weightsUsed = emptyList(),
            repsPerformed = emptyList(),
            timers = "restActive=false,tempoActive=false",
            restStatus = "0:90",
            progressionChosen = "Linear",
            exerciseOrder = exercises.map { it.exerciseId },
            elapsedTime = elapsedSeconds,
            sessionState = sessionState,
            exercisesJson = exercisesJson,
            workoutName = workout.name,
            workoutJson = workoutJson
        )
        val editor = prefs.edit()
            .putBoolean(WorkoutSessionRecoveryCoordinator.KEY_HAS_ACTIVE_SESSION, true)
            .putString(WorkoutSessionRecoveryCoordinator.KEY_ACTIVE_SESSION_ID, sessionId)
            .putLong(WorkoutSessionRecoveryCoordinator.KEY_ACTIVE_SESSION_START_TIME, startTimeMillis)
            .putString(
                WorkoutSessionRecoveryCoordinator.KEY_ACTIVE_WORKOUT_SESSION_JSON,
                JsonUtils.serializeActiveWorkoutSession(activeSession)
            )
            .putString(WorkoutSessionRecoveryCoordinator.KEY_ACTIVE_WORKOUT_JSON, workoutJson)
            .putString(WorkoutSessionRecoveryCoordinator.KEY_ACTIVE_EXERCISES_JSON, exercisesJson)
            .putInt(WorkoutSessionRecoveryCoordinator.KEY_ACTIVE_CURRENT_EXERCISE_INDEX, currentExerciseIndex)
            .putLong(WorkoutSessionRecoveryCoordinator.KEY_ACTIVE_WORKOUT_DURATION_SECONDS, elapsedSeconds)
            .putBoolean(WorkoutSessionRecoveryCoordinator.KEY_ACTIVE_IS_PRE_SESSION_ACTIVE, isPreSession)
            .putString(WorkoutSessionRecoveryCoordinator.KEY_ACTIVE_WORKOUT_FLOW_STATE, flowState)
            .putLong(WorkoutSessionRecoveryCoordinator.KEY_ACTIVE_SESSION_TIMESTAMP, savedTimestampMillis)
            .putInt(WorkoutSessionRecoveryCoordinator.KEY_ACTIVE_SESSION_DEFAULT_REST_SECONDS, 90)
        if (bootCount != null) {
            editor.putInt(WorkoutSessionRecoveryCoordinator.KEY_ACTIVE_SESSION_BOOT_COUNT, bootCount)
        }
        editor.commit()
    }

    // -------------------------------------------------------------------------
    // SCENARIO 1: Startup with no active session
    // -------------------------------------------------------------------------
    @Test
    fun scenario01_startupWithNoActiveSession_leavesAppIdleAndClean() = runBlocking {
        val outcome = WorkoutSessionRecoveryCoordinator.resolveStartupRecovery(
            repository = inMemoryRepo,
            sharedPrefs = isolatedPrefs
        )
        assertNull(outcome.activeSession)
        assertEquals(WorkoutSessionRecoveryCoordinator.RecoverySource.NONE, outcome.source)

        val (vm, _) = createMainViewModel()
        awaitCondition { vm.isInitialSessionRecoveryDone.value }

        assertFalse(vm.showActiveSessionPrompt.value)
        assertTrue(vm.activeSessionsList.value.isEmpty())
        assertNull(vm.currentSessionId.value)
        assertEquals(WorkoutFlowState.Idle, vm.workoutFlowState.value)
        assertFalse(appPrefs.getBoolean(WorkoutSessionRecoveryCoordinator.KEY_HAS_ACTIVE_SESSION, false))
    }

    // -------------------------------------------------------------------------
    // SCENARIO 2: Startup with active session in Room
    // -------------------------------------------------------------------------
    @Test
    fun scenario02_startupWithActiveSessionInRoom_detectsAndRestoresWithoutAutoSaveWipe() = runBlocking {
        val sessionId = "room_active_02"
        val exercises = sampleExercises(firstSetCompleted = true, firstWeight = 85f, firstReps = 9, firstRpe = "8")
        val workout = sampleWorkout(id = "w_room_02", name = "Treino Peito Room", exercises = exercises)
        val nowWall = System.currentTimeMillis()
        val entity = WorkoutSessionEntity(
            id = sessionId,
            workoutId = workout.id,
            workoutName = workout.name,
            status = WorkoutSessionStatus.ATIVO.rawValue,
            startTime = nowWall - 240_000L,
            durationSeconds = 240L,
            exercisesJson = JsonUtils.serializeWorkoutExercises(exercises),
            version = 3,
            startTimeMonotonic = android.os.SystemClock.elapsedRealtime()
        )
        AppDatabase.getDatabase(app).workoutSessionDao().insertOrUpdate(entity)
        writeFallbackPrefs(
            prefs = appPrefs,
            sessionId = sessionId,
            workout = workout,
            exercises = exercises,
            startTimeMillis = entity.startTime,
            elapsedSeconds = 240L
        )

        val (vm, _) = createMainViewModel()
        awaitCondition { vm.isInitialSessionRecoveryDone.value && vm.showActiveSessionPrompt.value }

        assertEquals(sessionId, vm.currentSessionId.value)
        assertEquals(1, vm.activeSessionsList.value.size)
        // Auto-save must NOT have wiped SharedPreferences during startup
        assertTrue(appPrefs.getBoolean(WorkoutSessionRecoveryCoordinator.KEY_HAS_ACTIVE_SESSION, false))

        vm.restoreActiveSession(sessionId)
        awaitCondition { vm.workoutFlowState.value == WorkoutFlowState.Running && vm.activeScreen.value == AppScreen.PLAYER }

        assertEquals(workout.id, vm.activeWorkout.value?.id)
        assertEquals(sessionId, vm.currentSessionId.value)
        assertFalse(vm.showActiveSessionPrompt.value)
        assertEquals(85f, vm.activeExercises.value[0].sets[0].weight, 0.01f)
        assertEquals(9, vm.activeExercises.value[0].sets[0].reps)
        assertEquals("8", vm.activeExercises.value[0].sets[0].rpe)
        assertTrue(vm.activeExercises.value[0].sets[0].completed)
        assertTrue(vm.workoutDurationSeconds.value >= 240L)
    }

    // -------------------------------------------------------------------------
    // SCENARIO 3: Startup with session ONLY in SharedPreferences fallback
    // -------------------------------------------------------------------------
    @Test
    fun scenario03_startupWithSessionOnlyInSharedPreferencesFallback_notWipedAndPromotedToRoom() = runBlocking {
        val sessionId = "prefs_only_03"
        val exercises = sampleExercises(firstSetCompleted = true, firstWeight = 72.5f, firstReps = 11, firstRpe = "7.5")
        val workout = sampleWorkout(id = "w_fallback_03", name = "Treino Fallback Prefs", exercises = exercises)
        val startTime = System.currentTimeMillis() - 180_000L

        // Room has 0 sessions; ONLY SharedPreferences has the active workout
        writeFallbackPrefs(
            prefs = appPrefs,
            sessionId = sessionId,
            workout = workout,
            exercises = exercises,
            startTimeMillis = startTime,
            elapsedSeconds = 180L,
            currentExerciseIndex = 1
        )

        val (vm, _) = createMainViewModel()
        awaitCondition { vm.isInitialSessionRecoveryDone.value && vm.showActiveSessionPrompt.value }

        // Must NOT have been wiped by premature setupActiveSessionAutoSave()!
        assertTrue(appPrefs.getBoolean(WorkoutSessionRecoveryCoordinator.KEY_HAS_ACTIVE_SESSION, false))
        assertEquals(sessionId, vm.currentSessionId.value)

        // Must have been promoted into Room as ATIVO
        val promotedInRoom = AppDatabase.getDatabase(app).workoutSessionDao().getSessionById(sessionId)
        assertNotNull(promotedInRoom)
        assertEquals(WorkoutSessionStatus.ATIVO.rawValue, promotedInRoom?.status)

        vm.restoreActiveSession(sessionId)
        awaitCondition { vm.workoutFlowState.value == WorkoutFlowState.Running && vm.activeScreen.value == AppScreen.PLAYER }

        assertEquals(workout.id, vm.activeWorkout.value?.id)
        assertEquals(1, vm.currentExerciseIndex.value)
        assertEquals(72.5f, vm.activeExercises.value[0].sets[0].weight, 0.01f)
        assertEquals(11, vm.activeExercises.value[0].sets[0].reps)
        assertEquals("7.5", vm.activeExercises.value[0].sets[0].rpe)
        assertTrue(vm.activeExercises.value[0].sets[0].completed)
    }

    // -------------------------------------------------------------------------
    // SCENARIO 4: Room and fallback with divergent data (Room MUST prevail)
    // -------------------------------------------------------------------------
    @Test
    fun scenario04_roomAndFallbackDivergent_roomStrictlyPrevails() = runBlocking {
        val sessionId = "divergent_04"
        val roomExercises = sampleExercises(
            firstSetCompleted = true,
            firstWeight = 100f,
            firstReps = 6,
            firstRpe = "9.5",
            secondSetCompleted = true,
            secondWeight = 102.5f,
            secondReps = 5,
            secondRpe = "10"
        )
        val stalePrefsExercises = sampleExercises(
            firstSetCompleted = false,
            firstWeight = 50f,
            firstReps = 15,
            firstRpe = "",
            secondSetCompleted = false,
            secondWeight = 50f,
            secondReps = 15,
            secondRpe = ""
        )
        val roomWorkout = sampleWorkout(id = "w_div_04", name = "Upper Força Room", exercises = roomExercises)
        val staleWorkout = sampleWorkout(id = "w_stale_other", name = "Treino Antigo Stale", exercises = stalePrefsExercises)

        val nowWall = System.currentTimeMillis()
        inMemoryRepo.saveWorkoutSession(
            WorkoutSessionEntity(
                id = sessionId,
                workoutId = roomWorkout.id,
                workoutName = roomWorkout.name,
                status = WorkoutSessionStatus.ATIVO.rawValue,
                startTime = nowWall - 600_000L,
                durationSeconds = 600L,
                exercisesJson = JsonUtils.serializeWorkoutExercises(roomExercises),
                version = 5
            )
        )
        // Put stale divergent data in SharedPreferences
        writeFallbackPrefs(
            prefs = isolatedPrefs,
            sessionId = "different_stale_id",
            workout = staleWorkout,
            exercises = stalePrefsExercises,
            startTimeMillis = nowWall - 900_000L,
            elapsedSeconds = 10L
        )

        val startup = WorkoutSessionRecoveryCoordinator.resolveStartupRecovery(
            repository = inMemoryRepo,
            sharedPrefs = isolatedPrefs
        )
        assertEquals(WorkoutSessionRecoveryCoordinator.RecoverySource.ROOM, startup.source)
        assertEquals(sessionId, startup.activeSession?.id)

        val payload = WorkoutSessionRecoveryCoordinator.resolveSessionForRestore(
            repository = inMemoryRepo,
            sharedPrefs = isolatedPrefs,
            allWorkouts = listOf(roomWorkout, staleWorkout),
            targetSessionId = sessionId
        )
        assertNotNull(payload)
        assertEquals(WorkoutSessionRecoveryCoordinator.RecoverySource.ROOM, payload!!.source)
        assertEquals(roomWorkout.id, payload.workout.id)
        assertEquals(100f, payload.exercises[0].sets[0].weight, 0.01f)
        assertEquals(6, payload.exercises[0].sets[0].reps)
        assertEquals("9.5", payload.exercises[0].sets[0].rpe)
        assertTrue(payload.exercises[0].sets[0].completed)
        assertTrue(payload.exercises[0].sets[1].completed)
        assertEquals(102.5f, payload.exercises[0].sets[1].weight, 0.01f)
        assertEquals("10", payload.exercises[0].sets[1].rpe)
    }

    // -------------------------------------------------------------------------
    // SCENARIO 5: Forced app termination (process death) during workout
    // -------------------------------------------------------------------------
    @Test
    fun scenario05_forcedProcessDeathDuringActiveWorkout_recoversSetsWeightsRepsAndRpe() = runBlocking {
        val initialExercises = sampleExercises(firstSetCompleted = false, firstWeight = 70f, firstReps = 10, firstRpe = "")
        val workout = sampleWorkout(id = "w_kill_05", name = "Full Body Kill Test", exercises = initialExercises)

        // Process #1: start workout, edit weight/reps, complete set, record RPE, add a set
        val (vm1, store1) = createMainViewModel()
        awaitCondition { vm1.isInitialSessionRecoveryDone.value }

        vm1.activeWorkout.value = workout
        vm1.activeExercises.value = initialExercises
        vm1.isPreSessionActive.value = true
        vm1.confirmAndStartWorkout()

        val startedSessionId = vm1.currentSessionId.value
        assertNotNull(startedSessionId)

        // Wait for initial Room insert
        awaitCondition {
            runBlocking { AppDatabase.getDatabase(app).workoutSessionDao().getSessionById(startedSessionId!!) != null }
        }

        // Update set weight & reps, complete set 1, record RPE, and add a new set
        vm1.setSetMetrics(0, 0, reps = 12, weight = 77.5f)
        vm1.toggleSetCompletion(0, 0)
        vm1.recordRpeForSet(0, 0, "8.5")
        vm1.addSetToExercise(0)

        // Wait for async Room persistence of RPE + added set
        awaitCondition {
            runBlocking {
                val persisted = AppDatabase.getDatabase(app).workoutSessionDao().getSessionById(startedSessionId!!)
                val parsed = persisted?.exercisesJson?.let { JsonUtils.parseWorkoutExercises(it) }
                parsed != null &&
                    parsed[0].sets.size == 3 &&
                    parsed[0].sets[0].completed &&
                    parsed[0].sets[0].rpe == "8.5" &&
                    parsed[0].sets[0].weight == 77.5f &&
                    parsed[0].sets[0].reps == 12
            }
        }

        // Simulate forced process kill: destroy VM #1 and wipe in-memory singletons
        store1.clear()
        resetServiceSingleton()

        // Process #2: cold startup
        val (vm2, _) = createMainViewModel()
        awaitCondition { vm2.isInitialSessionRecoveryDone.value && vm2.showActiveSessionPrompt.value }

        assertEquals(startedSessionId, vm2.currentSessionId.value)

        vm2.restoreActiveSession(startedSessionId)
        awaitCondition { vm2.workoutFlowState.value == WorkoutFlowState.Running && vm2.activeScreen.value == AppScreen.PLAYER }

        val restoredEx0 = vm2.activeExercises.value[0]
        assertEquals(3, restoredEx0.sets.size)
        assertTrue(restoredEx0.sets[0].completed)
        assertEquals(77.5f, restoredEx0.sets[0].weight, 0.01f)
        assertEquals(12, restoredEx0.sets[0].reps)
        assertEquals("8.5", restoredEx0.sets[0].rpe)
        assertFalse(restoredEx0.sets[1].completed)
        assertFalse(restoredEx0.sets[2].completed)
    }

    // -------------------------------------------------------------------------
    // SCENARIO 6: Recovery with partially completed sets (preserving weight, reps, RPE & index)
    // -------------------------------------------------------------------------
    @Test
    fun scenario06_partialCompletedSets_preservesAllSetFieldsAndTargetsFirstIncompleteExercise() = runBlocking {
        val exercises = sampleExercises(
            firstSetCompleted = true,
            firstWeight = 90f,
            firstReps = 8,
            firstRpe = "8",
            secondSetCompleted = true,
            secondWeight = 95f,
            secondReps = 6,
            secondRpe = "9"
        )
        val workout = sampleWorkout(id = "w_partial_06", name = "Parcial Ex1 Completo", exercises = exercises)
        val sessionId = "partial_06"

        inMemoryRepo.saveWorkoutSession(
            WorkoutSessionEntity(
                id = sessionId,
                workoutId = workout.id,
                workoutName = workout.name,
                status = WorkoutSessionStatus.ATIVO.rawValue,
                startTime = System.currentTimeMillis() - 420_000L,
                durationSeconds = 420L,
                exercisesJson = JsonUtils.serializeWorkoutExercises(exercises),
                version = 4
            )
        )

        val payload = WorkoutSessionRecoveryCoordinator.resolveSessionForRestore(
            repository = inMemoryRepo,
            sharedPrefs = isolatedPrefs,
            allWorkouts = listOf(workout),
            targetSessionId = sessionId
        )

        assertNotNull(payload)
        // Exercise 0 has all sets completed; Exercise 1 has pending sets -> resume cleanly on index 1
        assertEquals(1, payload!!.currentExerciseIndex)
        assertEquals("Manter escápulas retraídas", payload.exercises[0].notes)
        assertEquals(true, payload.exercises[0].sets[0].completed)
        assertEquals(90f, payload.exercises[0].sets[0].weight, 0.01f)
        assertEquals(8, payload.exercises[0].sets[0].reps)
        assertEquals("8", payload.exercises[0].sets[0].rpe)
        assertEquals(true, payload.exercises[0].sets[1].completed)
        assertEquals(95f, payload.exercises[0].sets[1].weight, 0.01f)
        assertEquals(6, payload.exercises[0].sets[1].reps)
        assertEquals("9", payload.exercises[0].sets[1].rpe)
        assertFalse(payload.exercises[1].sets[0].completed)
    }

    // -------------------------------------------------------------------------
    // SCENARIO 7: Normal finish followed by fresh startup
    // -------------------------------------------------------------------------
    @Test
    fun scenario07_normalFinishFollowedByFreshStartup_sessionNeverReappears() = runBlocking {
        val exercises = sampleExercises(firstSetCompleted = true, firstWeight = 80f, firstReps = 10, firstRpe = "8")
        val workout = sampleWorkout(id = "w_finish_07", name = "Treino Conclusão Limpa", exercises = exercises)

        val (vm1, store1) = createMainViewModel()
        awaitCondition { vm1.isInitialSessionRecoveryDone.value }

        vm1.activeWorkout.value = workout
        vm1.activeExercises.value = exercises
        vm1.isPreSessionActive.value = true
        vm1.confirmAndStartWorkout()

        val sid = vm1.currentSessionId.value!!
        awaitCondition {
            runBlocking { AppDatabase.getDatabase(app).workoutSessionDao().getSessionById(sid) != null }
        }

        // Complete set 1 and record RPE right before finishing to verify final exercisesJson is persisted on finish
        vm1.toggleSetCompletion(0, 0)
        vm1.recordRpeForSet(0, 0, "9.5")
        vm1.finishActiveWorkout(isEarly = false)

        awaitCondition {
            runBlocking {
                val dbSession = AppDatabase.getDatabase(app).workoutSessionDao().getSessionById(sid)
                dbSession?.status == WorkoutSessionStatus.CONCLUIDO.rawValue && vm1.currentSessionId.value == null
            }
        }

        val finishedInRoom = AppDatabase.getDatabase(app).workoutSessionDao().getSessionById(sid)!!
        assertEquals(WorkoutSessionStatus.CONCLUIDO.rawValue, finishedInRoom.status)
        val persistedFinalEx = JsonUtils.parseWorkoutExercises(finishedInRoom.exercisesJson)
        assertEquals("9.5", persistedFinalEx[0].sets[0].rpe)
        assertFalse(appPrefs.getBoolean(WorkoutSessionRecoveryCoordinator.KEY_HAS_ACTIVE_SESSION, false))
        assertNull(appPrefs.getString(WorkoutSessionRecoveryCoordinator.KEY_ACTIVE_SESSION_ID, null))

        // Simulate app restart
        store1.clear()
        resetServiceSingleton()

        val (vm2, _) = createMainViewModel()
        awaitCondition { vm2.isInitialSessionRecoveryDone.value }

        assertFalse(vm2.showActiveSessionPrompt.value)
        assertTrue(vm2.activeSessionsList.value.isEmpty())
        assertNull(vm2.currentSessionId.value)
        assertEquals(WorkoutFlowState.Idle, vm2.workoutFlowState.value)
    }

    // -------------------------------------------------------------------------
    // SCENARIO 8: Terminal session (CONCLUIDO / DESCARTADO / NAO_REALIZADO) still present in fallback
    // -------------------------------------------------------------------------
    @Test
    fun scenario08_terminalSessionsStillInFallback_neverResurrectAndPurgeFallback() = runBlocking {
        val terminalStatuses = listOf(
            WorkoutSessionStatus.CONCLUIDO,
            WorkoutSessionStatus.DESCARTADO,
            WorkoutSessionStatus.NAO_REALIZADO
        )

        for (terminalStatus in terminalStatuses) {
            val sid = "terminal_${terminalStatus.rawValue}"
            val exercises = sampleExercises()
            val workout = sampleWorkout(id = "w_$sid", name = "Treino $sid", exercises = exercises)
            val startTime = System.currentTimeMillis() - 500_000L

            inMemoryRepo.saveWorkoutSession(
                WorkoutSessionEntity(
                    id = sid,
                    workoutId = workout.id,
                    workoutName = workout.name,
                    status = terminalStatus.rawValue,
                    startTime = startTime,
                    endTime = System.currentTimeMillis(),
                    completedAt = System.currentTimeMillis(),
                    durationSeconds = 400L,
                    exercisesJson = JsonUtils.serializeWorkoutExercises(exercises),
                    version = 2
                )
            )
            // Simulate stale SharedPreferences still claiming the session is active
            writeFallbackPrefs(
                prefs = isolatedPrefs,
                sessionId = sid,
                workout = workout,
                exercises = exercises,
                startTimeMillis = startTime
            )

            // 1. Startup recovery must reject resurrection and purge SharedPreferences
            val outcome = WorkoutSessionRecoveryCoordinator.resolveStartupRecovery(
                repository = inMemoryRepo,
                sharedPrefs = isolatedPrefs
            )
            assertNull("Session $sid must not resurrect on startup", outcome.activeSession)
            assertEquals(WorkoutSessionRecoveryCoordinator.RecoverySource.NONE, outcome.source)
            assertTrue(outcome.purgedStaleOrCorruptFallback)
            assertFalse(isolatedPrefs.getBoolean(WorkoutSessionRecoveryCoordinator.KEY_HAS_ACTIVE_SESSION, false))
            assertNull(isolatedPrefs.getString(WorkoutSessionRecoveryCoordinator.KEY_ACTIVE_SESSION_ID, null))

            // 2. Direct restore attempt for terminal session must also refuse fallback resurrection
            writeFallbackPrefs(
                prefs = isolatedPrefs,
                sessionId = sid,
                workout = workout,
                exercises = exercises,
                startTimeMillis = startTime
            )
            val restoreAttempt = WorkoutSessionRecoveryCoordinator.resolveSessionForRestore(
                repository = inMemoryRepo,
                sharedPrefs = isolatedPrefs,
                allWorkouts = listOf(workout),
                targetSessionId = sid
            )
            assertNull("Direct restore of terminal $sid must return null", restoreAttempt)
            assertFalse(isolatedPrefs.getBoolean(WorkoutSessionRecoveryCoordinator.KEY_HAS_ACTIVE_SESSION, false))
        }

        // 3. Even if fallback has a different/missing sessionId, matching (workoutId, startTime) blocks resurrection
        val startTimeMatch = System.currentTimeMillis() - 700_000L
        val exercises = sampleExercises()
        val workout = sampleWorkout(id = "w_match_start", name = "Match StartTime", exercises = exercises)
        inMemoryRepo.saveWorkoutSession(
            WorkoutSessionEntity(
                id = "room_closed_by_start",
                workoutId = workout.id,
                workoutName = workout.name,
                status = WorkoutSessionStatus.CONCLUIDO.rawValue,
                startTime = startTimeMatch,
                endTime = System.currentTimeMillis(),
                completedAt = System.currentTimeMillis(),
                durationSeconds = 300L,
                exercisesJson = JsonUtils.serializeWorkoutExercises(exercises),
                version = 2
            )
        )
        writeFallbackPrefs(
            prefs = isolatedPrefs,
            sessionId = "different_prefs_sid",
            workout = workout,
            exercises = exercises,
            startTimeMillis = startTimeMatch
        )
        val outcomeByStart = WorkoutSessionRecoveryCoordinator.resolveStartupRecovery(
            repository = inMemoryRepo,
            sharedPrefs = isolatedPrefs
        )
        assertNull(outcomeByStart.activeSession)
        assertTrue(outcomeByStart.purgedStaleOrCorruptFallback)
    }

    // -------------------------------------------------------------------------
    // SCENARIO 9: Missing or corrupted/invalid recovery data
    // -------------------------------------------------------------------------
    @Test
    fun scenario09_corruptedOrInvalidRecoveryData_failsSafelyAndCleansUp() = runBlocking {
        // 9A: Malformed JSON in SharedPreferences
        isolatedPrefs.edit()
            .putBoolean(WorkoutSessionRecoveryCoordinator.KEY_HAS_ACTIVE_SESSION, true)
            .putString(WorkoutSessionRecoveryCoordinator.KEY_ACTIVE_WORKOUT_FLOW_STATE, "Running")
            .putString(WorkoutSessionRecoveryCoordinator.KEY_ACTIVE_WORKOUT_SESSION_JSON, "{corrupt json!!!")
            .putString(WorkoutSessionRecoveryCoordinator.KEY_ACTIVE_WORKOUT_JSON, "not-json")
            .putString(WorkoutSessionRecoveryCoordinator.KEY_ACTIVE_EXERCISES_JSON, "[broken")
            .commit()

        val outcomeCorruptPrefs = WorkoutSessionRecoveryCoordinator.resolveStartupRecovery(
            repository = inMemoryRepo,
            sharedPrefs = isolatedPrefs
        )
        assertNull(outcomeCorruptPrefs.activeSession)
        assertTrue(outcomeCorruptPrefs.purgedStaleOrCorruptFallback)
        assertFalse(isolatedPrefs.getBoolean(WorkoutSessionRecoveryCoordinator.KEY_HAS_ACTIVE_SESSION, false))

        // 9B: Pre-session preview or Finished flowState in SharedPreferences must NOT trigger recovery
        val workout = sampleWorkout()
        writeFallbackPrefs(
            prefs = isolatedPrefs,
            sessionId = "presession_only",
            workout = workout,
            exercises = sampleExercises(),
            startTimeMillis = System.currentTimeMillis(),
            isPreSession = true
        )
        val outcomePre = WorkoutSessionRecoveryCoordinator.resolveStartupRecovery(
            repository = inMemoryRepo,
            sharedPrefs = isolatedPrefs
        )
        assertNull(outcomePre.activeSession)
        assertTrue(outcomePre.purgedStaleOrCorruptFallback)

        // 9C: Corrupted exercisesJson inside a Room ATIVO session must be archived safely without crashing
        val corruptRoomId = "corrupt_room_09"
        inMemoryRepo.saveWorkoutSession(
            WorkoutSessionEntity(
                id = corruptRoomId,
                workoutId = "w_corrupt",
                workoutName = "Corrupted Room Session",
                status = WorkoutSessionStatus.ATIVO.rawValue,
                startTime = System.currentTimeMillis() - 100_000L,
                durationSeconds = 50L,
                exercisesJson = "{invalid_json_array",
                version = 1
            )
        )
        val outcomeCorruptRoom = WorkoutSessionRecoveryCoordinator.resolveStartupRecovery(
            repository = inMemoryRepo,
            sharedPrefs = isolatedPrefs
        )
        assertNull(outcomeCorruptRoom.activeSession)
        val archived = inMemoryRepo.getWorkoutSessionById(corruptRoomId)
        assertEquals(WorkoutSessionStatus.NAO_REALIZADO.rawValue, archived?.status)
    }

    // -------------------------------------------------------------------------
    // SCENARIO 10: Device reboot with active session (monotonic clock reset)
    // -------------------------------------------------------------------------
    @Test
    fun scenario10_deviceRebootWithActiveSession_computesSafeDurationAndKeepsClockTicking() = runBlocking {
        val nowWall = 1_750_000_000_000L
        val sessionStartWall = nowWall - (8 * 3600 * 1000L) // Started 8 hours ago before overnight reboot
        val preRebootStartMonotonic = 5_000_000L
        val persistedActiveDurationSec = 1320L // 22 minutes of active training before shutdown

        // 10A: Reboot detected via boot count AND elapsedRealtime < startTimeMonotonic
        val durAfterRebootShortUptime = WorkoutTimeUtils.calculateSafeDurationSeconds(
            startTimeMonotonic = preRebootStartMonotonic,
            fallbackStartMillis = sessionStartWall,
            monotonicStartTimeNanos = 5_000_000_000_000L,
            persistedDurationSeconds = persistedActiveDurationSec,
            savedBootCount = 12,
            currentBootCount = 13,
            nowMonotonic = 15_000L, // Only 15s since reboot
            nowNanos = 15_000_000_000L,
            nowWallMillis = nowWall
        )
        assertEquals(
            "After reboot, should preserve persisted active duration checkpoint instead of monotonic garbage or 8h offline inflation",
            persistedActiveDurationSec,
            durAfterRebootShortUptime
        )

        // 10B: Reboot where new uptime exceeds old startTimeMonotonic (nowMonotonic > startTimeMonotonic)
        // Detected both by bootCount AND by boot-epoch heuristic when bootCount is null
        val durRebootLongUptimeWithBootCount = WorkoutTimeUtils.calculateSafeDurationSeconds(
            startTimeMonotonic = preRebootStartMonotonic,
            fallbackStartMillis = sessionStartWall,
            monotonicStartTimeNanos = 5_000_000_000_000L,
            persistedDurationSeconds = persistedActiveDurationSec,
            savedBootCount = 12,
            currentBootCount = 13,
            nowMonotonic = 9_000_000L,
            nowNanos = 9_000_000_000_000L,
            nowWallMillis = nowWall
        )
        assertEquals(persistedActiveDurationSec, durRebootLongUptimeWithBootCount)

        val durRebootLongUptimeHeuristic = WorkoutTimeUtils.calculateSafeDurationSeconds(
            startTimeMonotonic = preRebootStartMonotonic,
            fallbackStartMillis = sessionStartWall,
            monotonicStartTimeNanos = 5_000_000_000_000L,
            persistedDurationSeconds = persistedActiveDurationSec,
            savedBootCount = null,
            currentBootCount = null,
            nowMonotonic = 9_000_000L, // Boot epoch = nowWall - 2.5h > sessionStartWall (nowWall - 8h)
            nowNanos = 9_000_000_000_000L,
            nowWallMillis = nowWall
        )
        assertEquals(persistedActiveDurationSec, durRebootLongUptimeHeuristic)

        // 10C: WorkoutServiceState must NOT freeze when restored elapsedSec * 1000 > current elapsedRealtime
        WorkoutServiceState.startOrSyncWorkoutClock(
            initialElapsedSec = 1500L, // 25 min
            nowElapsed = 5_000L,       // 5s since device reboot!
            nowWall = nowWall
        )
        assertTrue(WorkoutServiceState.isWorkoutClockRunning)
        assertEquals(1500L, WorkoutServiceState.computeWorkoutDurationSeconds(nowElapsed = 5_000L))
        assertEquals(1545L, WorkoutServiceState.computeWorkoutDurationSeconds(nowElapsed = 50_000L))

        // 10D: DAO getActiveSession() ordering after reboot picks newer wall startTime, not higher pre-reboot monotonic
        val preRebootOlder = WorkoutSessionEntity(
            id = "older_pre_reboot",
            workoutId = "w_old",
            workoutName = "Pre-Reboot Session",
            status = WorkoutSessionStatus.ATIVO.rawValue,
            startTime = nowWall - 3_600_000L,
            durationSeconds = 900L,
            exercisesJson = JsonUtils.serializeWorkoutExercises(sampleExercises()),
            version = 2,
            startTimeMonotonic = 99_000_000L // High pre-reboot uptime
        )
        val postRebootNewer = WorkoutSessionEntity(
            id = "newer_post_reboot",
            workoutId = "w_new",
            workoutName = "Post-Reboot Session",
            status = WorkoutSessionStatus.ATIVO.rawValue,
            startTime = nowWall - 30_000L,
            durationSeconds = 30L,
            exercisesJson = JsonUtils.serializeWorkoutExercises(sampleExercises()),
            version = 1,
            startTimeMonotonic = 20_000L // Low post-reboot uptime
        )
        inMemoryRepo.saveWorkoutSession(preRebootOlder)
        inMemoryRepo.saveWorkoutSession(postRebootNewer)

        val activeFromDao = inMemoryRepo.getActiveWorkoutSession()
        assertEquals("newer_post_reboot", activeFromDao?.id)

        // 10E: End-to-end startup + restore with Settings.Global.BOOT_COUNT incremented
        Settings.Global.putInt(app.contentResolver, Settings.Global.BOOT_COUNT, 7)
        val rebootSessionId = "reboot_e2e_10"
        val rebootExercises = sampleExercises(firstSetCompleted = true, firstWeight = 88f, firstReps = 9, firstRpe = "8.5")
        val rebootWorkout = sampleWorkout(id = "w_reboot_10", name = "Treino Pós-Reboot", exercises = rebootExercises)

        inMemoryRepo.saveWorkoutSession(
            WorkoutSessionEntity(
                id = rebootSessionId,
                workoutId = rebootWorkout.id,
                workoutName = rebootWorkout.name,
                status = WorkoutSessionStatus.ATIVO.rawValue,
                startTime = nowWall - 20_000_000L,
                durationSeconds = 1140L, // 19 min checkpoint
                exercisesJson = JsonUtils.serializeWorkoutExercises(rebootExercises),
                version = 3,
                startTimeMonotonic = 50_000_000L
            )
        )
        writeFallbackPrefs(
            prefs = isolatedPrefs,
            sessionId = rebootSessionId,
            workout = rebootWorkout,
            exercises = rebootExercises,
            startTimeMillis = nowWall - 20_000_000L,
            elapsedSeconds = 1140L,
            bootCount = 6 // Previous boot count before reboot
        )

        val currentBoot = WorkoutTimeUtils.readBootCount(app)
        assertEquals(7, currentBoot)
        val restoredPayload = WorkoutSessionRecoveryCoordinator.resolveSessionForRestore(
            repository = inMemoryRepo,
            sharedPrefs = isolatedPrefs,
            allWorkouts = listOf(rebootWorkout),
            targetSessionId = rebootSessionId,
            currentBootCount = currentBoot,
            nowMonotonic = 12_000L,
            nowWallMillis = nowWall
        )
        assertNotNull(restoredPayload)
        assertEquals(1140L, restoredPayload!!.elapsedDurationSeconds)
        assertEquals(88f, restoredPayload.exercises[0].sets[0].weight, 0.01f)
        assertEquals("8.5", restoredPayload.exercises[0].sets[0].rpe)
    }

    // ------------------------------------------------------------------------
    // RUN 1.1 — Revisão de segurança da recuperação de sessões
    // ------------------------------------------------------------------------

    @Test
    fun run11_roomInfrastructureFailure_returnsFailedAndPreservesFallback() = runBlocking {
        val nowWall = System.currentTimeMillis()
        val sessionId = "run11_room_fail_1"
        val exercises = sampleExercises(firstSetCompleted = true, firstWeight = 92.5f, firstReps = 8, firstRpe = "9")
        val workout = sampleWorkout(id = "w_run11_1", name = "Treino Protegido", exercises = exercises)

        writeFallbackPrefs(
            prefs = isolatedPrefs,
            sessionId = sessionId,
            workout = workout,
            exercises = exercises,
            startTimeMillis = nowWall - 600_000L,
            elapsedSeconds = 540L
        )

        // Simulate Room infrastructure failure by closing the in-memory DB before recovery runs
        inMemoryDb.close()

        val outcome = WorkoutSessionRecoveryCoordinator.resolveStartupRecovery(
            repository = inMemoryRepo,
            sharedPrefs = isolatedPrefs,
            nowWallMillis = nowWall
        )

        assertEquals(WorkoutSessionRecoveryCoordinator.RecoverySource.FAILED, outcome.source)
        assertFalse(outcome.isDecisionValid)
        assertNull(outcome.activeSession)
        assertFalse(outcome.purgedStaleOrCorruptFallback)

        // Fallback MUST remain completely intact
        assertTrue(isolatedPrefs.getBoolean(WorkoutSessionRecoveryCoordinator.KEY_HAS_ACTIVE_SESSION, false))
        assertEquals(sessionId, isolatedPrefs.getString(WorkoutSessionRecoveryCoordinator.KEY_ACTIVE_SESSION_ID, null))
        assertFalse(
            isolatedPrefs.getString(WorkoutSessionRecoveryCoordinator.KEY_ACTIVE_WORKOUT_SESSION_JSON, "").isNullOrBlank()
        )
    }

    @Test
    fun run11_sharedPrefsInfrastructureFailure_returnsFailedWithoutClearingFallback() = runBlocking {
        var editCalled = false
        val failingPrefs = object : SharedPreferences {
            private fun fail(): Nothing = throw SecurityException("Simulated SharedPreferences storage failure")
            override fun getAll(): MutableMap<String, *> = fail()
            override fun getString(key: String?, defValue: String?): String? = fail()
            override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = fail()
            override fun getInt(key: String?, defValue: Int): Int = fail()
            override fun getLong(key: String?, defValue: Long): Long = fail()
            override fun getFloat(key: String?, defValue: Float): Float = fail()
            override fun getBoolean(key: String?, defValue: Boolean): Boolean = fail()
            override fun contains(key: String?): Boolean = fail()
            override fun edit(): SharedPreferences.Editor {
                editCalled = true
                fail()
            }
            override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
            override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        }

        val outcome = WorkoutSessionRecoveryCoordinator.resolveStartupRecovery(
            repository = inMemoryRepo,
            sharedPrefs = failingPrefs
        )

        assertEquals(WorkoutSessionRecoveryCoordinator.RecoverySource.FAILED, outcome.source)
        assertFalse(outcome.isDecisionValid)
        assertNull(outcome.activeSession)
        assertFalse("Must not attempt to edit/clear SharedPreferences after read failure", editCalled)
    }

    @Test
    fun run11_mainViewModelRecoveryFailure_blocksAutoSaveAndPreservesFallbackUntilValidRetry() = runBlocking {
        val nowWall = System.currentTimeMillis()
        val sessionId = "run11_vm_fail_retry"
        val exercises = sampleExercises(firstSetCompleted = true, firstWeight = 102.5f, firstReps = 6, firstRpe = "9.5")
        val workout = sampleWorkout(id = "w_run11_vm", name = "Agachamento Pesado", exercises = exercises)

        val (vm, _) = createMainViewModel()
        // Wait for normal init to settle on clean DB
        awaitCondition { vm.isSessionRecoveryConfirmed() }

        // Reset gate to simulate startup before recovery completes, and place a valid fallback in appPrefs
        vm.resetRecoveryGateForTesting()
        assertFalse(vm.isSessionRecoveryConfirmed())

        writeFallbackPrefs(
            prefs = appPrefs,
            sessionId = sessionId,
            workout = workout,
            exercises = exercises,
            startTimeMillis = nowWall - 300_000L,
            elapsedSeconds = 290L
        )

        // 1) Infrastructure exception during recovery
        val exceptionResult = vm.runStartupRecovery {
            throw IllegalStateException("Simulated Room/Storage failure during startup")
        }
        assertFalse(exceptionResult)
        assertFalse(vm.isSessionRecoveryConfirmed())

        // 2) Invalid FAILED outcome returned by coordinator
        val failedOutcomeResult = vm.runStartupRecovery {
            WorkoutSessionRecoveryCoordinator.StartupRecoveryOutcome(
                activeSession = null,
                source = WorkoutSessionRecoveryCoordinator.RecoverySource.FAILED,
                isDecisionValid = false
            )
        }
        assertFalse(failedOutcomeResult)
        assertFalse(vm.isSessionRecoveryConfirmed())

        // 3) Auto-save and direct saveActiveSession() must stay blocked and NOT wipe fallback
        vm.setupActiveSessionAutoSave()
        vm.workoutFlowState.value = WorkoutFlowState.Idle
        vm.saveActiveSession()
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        delay(120L)

        assertTrue(appPrefs.getBoolean(WorkoutSessionRecoveryCoordinator.KEY_HAS_ACTIVE_SESSION, false))
        assertEquals(sessionId, appPrefs.getString(WorkoutSessionRecoveryCoordinator.KEY_ACTIVE_SESSION_ID, null))

        // 4) Once infrastructure recovers, a valid retry promotes the fallback and unlocks recovery
        vm.checkActiveSessions()
        awaitCondition { vm.isSessionRecoveryConfirmed() && vm.showActiveSessionPrompt.value }

        assertEquals(sessionId, vm.currentSessionId.value)
        val promotedInRoom = AppDatabase.getDatabase(app).workoutSessionDao().getSessionById(sessionId)
        assertNotNull(promotedInRoom)
        assertEquals(WorkoutSessionStatus.ATIVO.rawValue, promotedInRoom!!.status)
    }

    @Test
    fun run11_strictOptimisticVersionControl_rejectsStaleSkippedAndPostTerminalUpdates() = runBlocking {
        val nowWall = System.currentTimeMillis()
        val sessionId = "run11_opt_lock"
        val initialExercises = sampleExercises(firstSetCompleted = false, firstWeight = 80f)
        val initialJson = JsonUtils.serializeWorkoutExercises(initialExercises)
        val concurrentExercises = sampleExercises(firstSetCompleted = true, firstWeight = 95f, firstRpe = "9")
        val concurrentJson = JsonUtils.serializeWorkoutExercises(concurrentExercises)
        val staleExercises = sampleExercises(firstSetCompleted = false, firstWeight = 60f)
        val staleJson = JsonUtils.serializeWorkoutExercises(staleExercises)

        inMemoryRepo.saveWorkoutSession(
            WorkoutSessionEntity(
                id = sessionId,
                workoutId = "w_lock",
                workoutName = "Lock Test",
                status = WorkoutSessionStatus.ATIVO.rawValue,
                startTime = nowWall - 120_000L,
                durationSeconds = 60L,
                exercisesJson = initialJson,
                version = 1,
                startTimeMonotonic = 10_000L
            )
        )

        // Concurrent caller advances DB from version 1 -> version 2
        val updatedV2 = inMemoryRepo.updateWorkoutSessionExercisesAndDurationOptimistic(
            sessionId = sessionId,
            exercisesJson = concurrentJson,
            durationSeconds = 120L,
            expectedVersion = 1
        )
        assertTrue(updatedV2)
        assertEquals(2, inMemoryRepo.getWorkoutSessionById(sessionId)!!.version)

        // 1) Stale caller (read version = 1, sends nextVersion = 2) must be rejected by repository
        val staleStatusRes = inMemoryRepo.updateWorkoutSessionStatusVersioned(
            sessionId = sessionId,
            status = WorkoutSessionStatus.DESCARTADO.rawValue,
            newVersion = 2,
            completedAt = nowWall,
            durationSeconds = 120L
        )
        assertFalse("Stale status update (expected current=1, actual=2) must fail", staleStatusRes)

        val staleFullRes = inMemoryRepo.updateWorkoutSessionStatusExercisesAndDurationVersioned(
            sessionId = sessionId,
            status = WorkoutSessionStatus.CONCLUIDO.rawValue,
            newVersion = 2,
            completedAt = nowWall,
            durationSeconds = 65L,
            exercisesJson = staleJson
        )
        assertFalse("Stale combined update (expected current=1, actual=2) must fail", staleFullRes)

        // 2) Skipped/inflated version (sending expectedVersion = 5 while DB is at version 2) must be rejected by DAO and repository
        val dao = inMemoryDb.workoutSessionDao()
        val skippedStatusRows = dao.updateStatusVersioned(
            sessionId = sessionId,
            newStatus = WorkoutSessionStatus.DESCARTADO.rawValue,
            expectedVersion = 5,
            completedAt = nowWall
        )
        assertEquals(0, skippedStatusRows)

        val skippedDurationRows = dao.updateStatusAndDurationVersioned(
            sessionId = sessionId,
            newStatus = WorkoutSessionStatus.CONCLUIDO.rawValue,
            expectedVersion = 5,
            completedAt = nowWall,
            durationSeconds = 999L
        )
        assertEquals(0, skippedDurationRows)

        val skippedFullRows = dao.updateStatusExercisesAndDurationVersioned(
            sessionId = sessionId,
            newStatus = WorkoutSessionStatus.CONCLUIDO.rawValue,
            expectedVersion = 5,
            completedAt = nowWall,
            durationSeconds = 999L,
            exercisesJson = staleJson
        )
        assertEquals(0, skippedFullRows)

        val skippedRepoRes = inMemoryRepo.updateWorkoutSessionStatusVersioned(
            sessionId = sessionId,
            status = WorkoutSessionStatus.DESCARTADO.rawValue,
            newVersion = 5,
            completedAt = nowWall,
            durationSeconds = 120L
        )
        assertFalse(skippedRepoRes)

        // DB state must still be ATIVO at version 2 with concurrent 95kg payload intact
        val afterRejected = inMemoryRepo.getWorkoutSessionById(sessionId)!!
        assertEquals(WorkoutSessionStatus.ATIVO.rawValue, afterRejected.status)
        assertEquals(2, afterRejected.version)
        assertEquals(concurrentJson, afterRejected.exercisesJson)

        // 3) Valid transition reading version = 2 -> newVersion = 3 succeeds
        val validFinalize = inMemoryRepo.updateWorkoutSessionStatusExercisesAndDurationVersioned(
            sessionId = sessionId,
            status = WorkoutSessionStatus.CONCLUIDO.rawValue,
            newVersion = 3,
            completedAt = nowWall,
            durationSeconds = 180L,
            exercisesJson = concurrentJson
        )
        assertTrue(validFinalize)

        val finalized = inMemoryRepo.getWorkoutSessionById(sessionId)!!
        assertEquals(WorkoutSessionStatus.CONCLUIDO.rawValue, finalized.status)
        assertEquals(3, finalized.version)
        assertEquals(180L, finalized.durationSeconds)

        // 4) Post-terminal direct DAO calls with expectedVersion = 4 MUST be rejected (status != ATIVO)
        val postTerminalStatusRows = dao.updateStatusVersioned(
            sessionId = sessionId,
            newStatus = WorkoutSessionStatus.DESCARTADO.rawValue,
            expectedVersion = 4,
            completedAt = nowWall + 1000L
        )
        assertEquals(0, postTerminalStatusRows)

        val postTerminalDurationRows = dao.updateStatusAndDurationVersioned(
            sessionId = sessionId,
            newStatus = WorkoutSessionStatus.NAO_REALIZADO.rawValue,
            expectedVersion = 4,
            completedAt = nowWall + 1000L,
            durationSeconds = 1L
        )
        assertEquals(0, postTerminalDurationRows)

        val finalCheck = inMemoryRepo.getWorkoutSessionById(sessionId)!!
        assertEquals(WorkoutSessionStatus.CONCLUIDO.rawValue, finalCheck.status)
        assertEquals(3, finalCheck.version)
        assertEquals(180L, finalCheck.durationSeconds)
    }

    @Test
    fun run11_multipleActiveRoomSessions_corruptedNewerDoesNotDestroyValidOlderSession() = runBlocking {
        val nowWall = System.currentTimeMillis()
        val validExercises = sampleExercises(firstSetCompleted = true, firstWeight = 110f, firstReps = 5, firstRpe = "9")
        val validOlder = WorkoutSessionEntity(
            id = "older_valid_session",
            workoutId = "w_valid",
            workoutName = "Treino Válido Anterior",
            status = WorkoutSessionStatus.ATIVO.rawValue,
            startTime = nowWall - 1_800_000L,
            durationSeconds = 900L,
            exercisesJson = JsonUtils.serializeWorkoutExercises(validExercises),
            version = 3,
            startTimeMonotonic = 100_000L
        )
        val corruptedNewer = WorkoutSessionEntity(
            id = "newer_corrupted_session",
            workoutId = "w_corrupt",
            workoutName = "Treino Corrompido Recente",
            status = WorkoutSessionStatus.ATIVO.rawValue,
            startTime = nowWall - 60_000L,
            durationSeconds = 30L,
            exercisesJson = "{not_valid_json_array",
            version = 1,
            startTimeMonotonic = 200_000L
        )

        inMemoryRepo.saveWorkoutSession(validOlder)
        inMemoryRepo.saveWorkoutSession(corruptedNewer)

        val outcome = WorkoutSessionRecoveryCoordinator.resolveStartupRecovery(
            repository = inMemoryRepo,
            sharedPrefs = isolatedPrefs,
            nowWallMillis = nowWall
        )

        assertTrue(outcome.isDecisionValid)
        assertEquals(WorkoutSessionRecoveryCoordinator.RecoverySource.ROOM, outcome.source)
        assertNotNull(outcome.activeSession)
        assertEquals("older_valid_session", outcome.activeSession!!.id)
        assertEquals(1, outcome.archivedOldSessionsCount)

        val corruptRow = inMemoryRepo.getWorkoutSessionById("newer_corrupted_session")!!
        assertEquals(WorkoutSessionStatus.NAO_REALIZADO.rawValue, corruptRow.status)
        val validRow = inMemoryRepo.getWorkoutSessionById("older_valid_session")!!
        assertEquals(WorkoutSessionStatus.ATIVO.rawValue, validRow.status)
    }

    @Test
    fun run11_fallbackOnlyRebootWithoutBootCount_preservesPersistedDurationViaBootEpoch() = runBlocking {
        val nowWall = System.currentTimeMillis()
        val sessionId = "run11_reboot_no_bootcount"
        val exercises = sampleExercises(firstSetCompleted = true, firstWeight = 75f, firstReps = 12, firstRpe = "8")
        val workout = sampleWorkout(id = "w_reboot_nobc", name = "Fallback Pós-Reboot", exercises = exercises)

        writeFallbackPrefs(
            prefs = isolatedPrefs,
            sessionId = sessionId,
            workout = workout,
            exercises = exercises,
            startTimeMillis = nowWall - 8L * 3_600_000L,
            savedTimestampMillis = nowWall - 7L * 3_600_000L,
            elapsedSeconds = 1200L,
            bootCount = null // Legacy fallback without saved boot count
        )

        val outcome = WorkoutSessionRecoveryCoordinator.resolveStartupRecovery(
            repository = inMemoryRepo,
            sharedPrefs = isolatedPrefs,
            currentBootCount = null,
            nowMonotonic = 15_000L, // Device booted 15s ago -> bootEpoch >> savedTimestampMillis
            nowWallMillis = nowWall
        )

        assertTrue(outcome.isDecisionValid)
        assertEquals(WorkoutSessionRecoveryCoordinator.RecoverySource.SHARED_PREFS_FALLBACK, outcome.source)
        assertNotNull(outcome.activeSession)
        assertEquals(1200L, outcome.activeSession!!.durationSeconds)
        assertEquals(
            1200L,
            isolatedPrefs.getLong(WorkoutSessionRecoveryCoordinator.KEY_ACTIVE_WORKOUT_DURATION_SECONDS, -1L)
        )
    }

    @Test
    fun run11_nonFiniteWeightsAndCorruptPrefTypes_handledSafelyWithoutCrash() = runBlocking {
        // 1) Non-finite/negative weights sanitize to finite >= 0f (never NaN/Infinity)
        val dirtyJson = """
            [
              {
                "id": "ex_1",
                "exerciseId": "peito_1",
                "name": "Supino",
                "category": "Peito",
                "equipment": "Barra",
                "restTimeSeconds": 60,
                "progressionType": "Linear",
                "executionType": "REPS",
                "sets": [
                  {
                    "id": "s1",
                    "index": 1,
                    "reps": -5,
                    "weight": -12.5,
                    "baseWeight": -4.0,
                    "completed": true,
                    "rpe": "8"
                  }
                ]
              }
            ]
        """.trimIndent()
        val sanitized = WorkoutSessionRecoveryCoordinator.parseAndSanitizeExercises(dirtyJson)
        assertNotNull(sanitized)
        val set = sanitized!![0].sets[0]
        assertTrue(set.weight.isFinite() && set.weight == 0f)
        assertNotNull(set.baseWeight)
        assertTrue(set.baseWeight!!.isFinite() && set.baseWeight == 0f)
        assertEquals(0, set.reps)

        // 2) Corrupt SharedPreferences value types (ClassCastException) are treated as invalid fallback and cleaned safely
        isolatedPrefs.edit()
            .putString(WorkoutSessionRecoveryCoordinator.KEY_HAS_ACTIVE_SESSION, "corrupt_string_not_boolean")
            .putString(WorkoutSessionRecoveryCoordinator.KEY_ACTIVE_SESSION_TIMESTAMP, "not_a_long")
            .putString(WorkoutSessionRecoveryCoordinator.KEY_ACTIVE_SESSION_ID, "corrupt_type_id")
            .commit()

        val outcome = WorkoutSessionRecoveryCoordinator.resolveStartupRecovery(
            repository = inMemoryRepo,
            sharedPrefs = isolatedPrefs
        )

        assertTrue(outcome.isDecisionValid)
        assertEquals(WorkoutSessionRecoveryCoordinator.RecoverySource.NONE, outcome.source)
        assertNull(outcome.activeSession)
        assertTrue(outcome.purgedStaleOrCorruptFallback)
        assertFalse(isolatedPrefs.getBoolean(WorkoutSessionRecoveryCoordinator.KEY_HAS_ACTIVE_SESSION, false))
        assertNull(isolatedPrefs.getString(WorkoutSessionRecoveryCoordinator.KEY_ACTIVE_SESSION_ID, null))
    }

    // ------------------------------------------------------------------------
    // RUN 1.3 — Guarda de início de treino e retentativa segura após falha
    // ------------------------------------------------------------------------

    @Test
    fun run13_scenarioA_startupFailedAndStillFailing_startWorkoutSessionBlockedWithoutTouchingStorage() = runBlocking {
        val nowWall = System.currentTimeMillis()
        val fallbackSessionId = "run13_fallback_a"
        val fallbackStartTime = nowWall - 420_000L
        val fallbackExercises = sampleExercises(firstSetCompleted = true, firstWeight = 90f, firstReps = 10, firstRpe = "8.5")
        val fallbackWorkout = sampleWorkout(id = "w_fallback_a", name = "Treino Pendente A", exercises = fallbackExercises)
        val newWorkout = sampleWorkout(id = "w_new_a", name = "Treino Novo Bloqueado", exercises = sampleExercises(firstSetCompleted = false))

        val (vm, _) = createMainViewModel()
        awaitCondition { vm.isSessionRecoveryCompleted() }

        vm.resetRecoveryGateForTesting()
        writeFallbackPrefs(
            prefs = appPrefs,
            sessionId = fallbackSessionId,
            workout = fallbackWorkout,
            exercises = fallbackExercises,
            startTimeMillis = fallbackStartTime,
            elapsedSeconds = 360L,
            bootCount = 5
        )
        val snapshotSessionJson = appPrefs.getString(WorkoutSessionRecoveryCoordinator.KEY_ACTIVE_WORKOUT_SESSION_JSON, null)

        // Infrastructure keeps returning FAILED
        vm.startupRecoveryResolverOverride = {
            WorkoutSessionRecoveryCoordinator.StartupRecoveryOutcome(
                activeSession = null,
                source = WorkoutSessionRecoveryCoordinator.RecoverySource.FAILED,
                isDecisionValid = false
            )
        }

        vm.startWorkoutSession(newWorkout, forceStart = true)
        awaitCondition { !vm.sessionPrepStatus.value.isPreparing && vm.workoutErrorMessage.value != null }

        assertFalse(vm.isSessionRecoveryCompleted())
        assertNull(vm.activeWorkout.value)
        assertFalse(vm.isPreSessionActive.value)
        assertTrue(vm.activeScreen.value != AppScreen.PLAYER)
        assertTrue(
            "Room must remain untouched when recovery fails",
            AppDatabase.getDatabase(app).workoutSessionDao().getAllActiveSessions().isEmpty()
        )
        // SharedPreferences fallback and metadata must remain 100% intact
        assertTrue(appPrefs.getBoolean(WorkoutSessionRecoveryCoordinator.KEY_HAS_ACTIVE_SESSION, false))
        assertEquals(fallbackSessionId, appPrefs.getString(WorkoutSessionRecoveryCoordinator.KEY_ACTIVE_SESSION_ID, null))
        assertEquals(fallbackStartTime, appPrefs.getLong(WorkoutSessionRecoveryCoordinator.KEY_ACTIVE_SESSION_START_TIME, -1L))
        assertEquals(5, appPrefs.getInt(WorkoutSessionRecoveryCoordinator.KEY_ACTIVE_SESSION_BOOT_COUNT, -1))
        assertEquals(
            snapshotSessionJson,
            appPrefs.getString(WorkoutSessionRecoveryCoordinator.KEY_ACTIVE_WORKOUT_SESSION_JSON, null)
        )
    }

    @Test
    fun run13_scenarioB_startupFailedThenRecovered_startWorkoutSessionRetriesAndPromptsFallback() = runBlocking {
        val nowWall = System.currentTimeMillis()
        val fallbackSessionId = "run13_fallback_b"
        val fallbackExercises = sampleExercises(firstSetCompleted = true, firstWeight = 105f, firstReps = 8, firstRpe = "9")
        val fallbackWorkout = sampleWorkout(id = "w_fallback_b", name = "Treino Pendente B", exercises = fallbackExercises)
        val newWorkout = sampleWorkout(id = "w_new_b", name = "Outro Treino", exercises = sampleExercises(firstSetCompleted = false))

        val (vm, _) = createMainViewModel()
        awaitCondition { vm.isSessionRecoveryCompleted() }

        vm.resetRecoveryGateForTesting()
        writeFallbackPrefs(
            prefs = appPrefs,
            sessionId = fallbackSessionId,
            workout = fallbackWorkout,
            exercises = fallbackExercises,
            startTimeMillis = nowWall - 500_000L,
            elapsedSeconds = 420L
        )

        // Initial startup fails
        val initialFailed = vm.runStartupRecovery {
            WorkoutSessionRecoveryCoordinator.StartupRecoveryOutcome(
                activeSession = null,
                source = WorkoutSessionRecoveryCoordinator.RecoverySource.FAILED,
                isDecisionValid = false
            )
        }
        assertFalse(initialFailed)
        assertFalse(vm.isSessionRecoveryCompleted())

        // Infrastructure recovers -> user tries to start a new workout
        vm.startupRecoveryResolverOverride = null
        vm.startWorkoutSession(newWorkout, forceStart = true)
        awaitCondition {
            vm.isSessionRecoveryCompleted() &&
                vm.showActiveSessionPrompt.value &&
                !vm.sessionPrepStatus.value.isPreparing
        }

        // Must have promoted and surfaced the pending fallback session instead of starting newWorkout
        assertTrue(vm.isSessionRecoveryCompleted())
        assertTrue(vm.showActiveSessionPrompt.value)
        assertEquals(fallbackSessionId, vm.currentSessionId.value)
        assertNull(vm.activeWorkout.value)
        assertTrue(vm.activeScreen.value != AppScreen.PLAYER)

        val activeInRoom = AppDatabase.getDatabase(app).workoutSessionDao().getAllActiveSessions()
        assertEquals(1, activeInRoom.size)
        assertEquals(fallbackSessionId, activeInRoom.first().id)
        assertEquals(fallbackWorkout.id, activeInRoom.first().workoutId)
    }

    @Test
    fun run13_scenarioC_startupFailedWithoutFallbackThenRecovered_allowsNormalStartWithBackup() = runBlocking {
        val newExercises = sampleExercises(firstSetCompleted = false, firstWeight = 70f, firstReps = 10, firstRpe = "")
        val newWorkout = sampleWorkout(id = "w_new_c", name = "Treino Normal C", exercises = newExercises)

        val (vm, _) = createMainViewModel()
        awaitCondition { vm.isSessionRecoveryCompleted() }

        vm.resetRecoveryGateForTesting()
        appPrefs.edit().clear().commit()
        AppDatabase.getDatabase(app).workoutSessionDao().clearAll()

        // Startup fails while storage has no fallback
        val initialFailed = vm.runStartupRecovery {
            WorkoutSessionRecoveryCoordinator.StartupRecoveryOutcome(
                activeSession = null,
                source = WorkoutSessionRecoveryCoordinator.RecoverySource.FAILED,
                isDecisionValid = false
            )
        }
        assertFalse(initialFailed)
        assertFalse(vm.isSessionRecoveryCompleted())

        // Infrastructure recovers -> startWorkoutSession retries recovery, confirms valid NONE, unlocks auto-save
        vm.startupRecoveryResolverOverride = null
        vm.startWorkoutSession(newWorkout, forceStart = true)
        awaitCondition {
            vm.isSessionRecoveryCompleted() &&
                vm.isPreSessionActive.value &&
                vm.activeScreen.value == AppScreen.PLAYER &&
                !vm.sessionPrepStatus.value.isPreparing
        }

        assertFalse(vm.showActiveSessionPrompt.value)
        assertEquals(newWorkout.id, vm.activeWorkout.value?.id)

        // Confirm start -> persists normally in both Room and SharedPreferences backup
        vm.confirmAndStartWorkout()
        withTimeout(5000L) {
            while (
                vm.workoutFlowState.value != WorkoutFlowState.Running ||
                AppDatabase.getDatabase(app).workoutSessionDao().getAllActiveSessions().size != 1
            ) {
                Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
                delay(20L)
            }
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        }

        val activeId = vm.currentSessionId.value
        assertNotNull(activeId)
        assertTrue(appPrefs.getBoolean(WorkoutSessionRecoveryCoordinator.KEY_HAS_ACTIVE_SESSION, false))
        assertEquals(activeId, appPrefs.getString(WorkoutSessionRecoveryCoordinator.KEY_ACTIVE_SESSION_ID, null))
        assertFalse(
            appPrefs.getString(WorkoutSessionRecoveryCoordinator.KEY_ACTIVE_WORKOUT_SESSION_JSON, "").isNullOrBlank()
        )
        val savedRoom = AppDatabase.getDatabase(app).workoutSessionDao().getSessionById(activeId!!)
        assertNotNull(savedRoom)
        assertEquals(WorkoutSessionStatus.ATIVO.rawValue, savedRoom!!.status)
        assertEquals(newWorkout.id, savedRoom.workoutId)
    }

    @Test
    fun run13_scenarioD_confirmAndStartWorkoutWithPendingRecovery_blockedBeforeSavingMetadataOrRoom() = runBlocking {
        val nowWall = System.currentTimeMillis()
        val fallbackSessionId = "run13_fallback_d"
        val fallbackStartTime = nowWall - 600_000L
        val fallbackExercises = sampleExercises(firstSetCompleted = true, firstWeight = 115f, firstReps = 6, firstRpe = "9.5")
        val fallbackWorkout = sampleWorkout(id = "w_fallback_d", name = "Treino Pendente D", exercises = fallbackExercises)
        val attemptedWorkout = sampleWorkout(id = "w_direct_d", name = "Tentativa Direta D", exercises = sampleExercises(firstSetCompleted = false))

        val (vm, _) = createMainViewModel()
        awaitCondition { vm.isSessionRecoveryCompleted() }

        vm.resetRecoveryGateForTesting()
        writeFallbackPrefs(
            prefs = appPrefs,
            sessionId = fallbackSessionId,
            workout = fallbackWorkout,
            exercises = fallbackExercises,
            startTimeMillis = fallbackStartTime,
            elapsedSeconds = 510L,
            bootCount = 9
        )

        // Recovery remains failing
        vm.startupRecoveryResolverOverride = {
            throw IllegalStateException("Simulated Room/Storage unavailable during confirmAndStartWorkout")
        }

        // Simulate in-memory pre-session state and direct call to confirmAndStartWorkout()
        vm.activeWorkout.value = attemptedWorkout
        vm.activeExercises.value = sampleExercises(firstSetCompleted = false)
        vm.isPreSessionActive.value = true
        vm.workoutFlowState.value = WorkoutFlowState.Idle
        vm.currentSessionId.value = null

        vm.confirmAndStartWorkout()
        awaitCondition { vm.workoutErrorMessage.value != null }
        delay(100L)

        assertFalse(vm.isSessionRecoveryCompleted())
        assertEquals(WorkoutFlowState.Idle, vm.workoutFlowState.value)
        assertNull(vm.currentSessionId.value)
        assertTrue(
            "Room must NOT receive any session when recovery is unconfirmed",
            AppDatabase.getDatabase(app).workoutSessionDao().getAllActiveSessions().isEmpty()
        )
        // saveSessionMetadata() must NOT have overwritten any fallback metadata key
        assertTrue(appPrefs.getBoolean(WorkoutSessionRecoveryCoordinator.KEY_HAS_ACTIVE_SESSION, false))
        assertEquals(fallbackSessionId, appPrefs.getString(WorkoutSessionRecoveryCoordinator.KEY_ACTIVE_SESSION_ID, null))
        assertEquals(fallbackStartTime, appPrefs.getLong(WorkoutSessionRecoveryCoordinator.KEY_ACTIVE_SESSION_START_TIME, -1L))
        assertEquals(9, appPrefs.getInt(WorkoutSessionRecoveryCoordinator.KEY_ACTIVE_SESSION_BOOT_COUNT, -1))
    }
}


