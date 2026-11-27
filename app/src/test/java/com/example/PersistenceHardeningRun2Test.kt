package com.example

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.AppDatabase
import com.example.data.model.GpsTrackPoint
import com.example.data.model.GymExerciseRecord
import com.example.data.model.GymSetRecord
import com.example.data.model.GymWorkoutSession
import com.example.data.model.RunningSession
import com.example.data.model.SetType
import com.example.data.model.Workout
import com.example.data.model.WorkoutExercise
import com.example.data.model.WorkoutSessionStatus
import com.example.data.model.WorkoutSet
import com.example.data.repository.FitnessRepository
import com.example.data.repository.GymHistoryRepository
import com.example.services.WorkoutForegroundService
import com.example.services.WorkoutServiceState
import com.example.ui.viewmodel.AppScreen
import com.example.ui.viewmodel.MainViewModel
import com.example.ui.viewmodel.WorkoutFlowState
import com.example.utils.JsonUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PersistenceHardeningRun2Test {

    private lateinit var app: Application
    private lateinit var inMemoryDb: AppDatabase
    private lateinit var inMemoryRepo: FitnessRepository
    private lateinit var appPrefs: SharedPreferences
    private lateinit var gymHistoryPrefs: SharedPreferences
    private val activeStores = mutableListOf<ViewModelStore>()

    @Before
    fun setUp() = runBlocking {
        app = ApplicationProvider.getApplicationContext()
        inMemoryDb = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        inMemoryRepo = FitnessRepository(inMemoryDb)

        appPrefs = app.getSharedPreferences("max_performance_prefs", Context.MODE_PRIVATE)
        appPrefs.edit().clear().commit()

        gymHistoryPrefs = app.getSharedPreferences(GymHistoryRepository.PREFS_NAME, Context.MODE_PRIVATE)
        gymHistoryPrefs.edit().clear().commit()
        GymHistoryRepository.resetForTesting()

        val appDb = AppDatabase.getDatabase(app)
        appDb.workoutSessionDao().clearAll()
        appDb.historyDao().clearAllHistory()
        appDb.runningSessionDao().clearAllRunningSessions()

        resetServiceSingleton()
    }

    @After
    fun tearDown() = runBlocking {
        activeStores.forEach { runCatching { it.clear() } }
        activeStores.clear()
        inMemoryDb.close()

        appPrefs.edit().clear().commit()
        gymHistoryPrefs.edit().clear().commit()
        GymHistoryRepository.resetForTesting()

        val appDb = AppDatabase.getDatabase(app)
        appDb.workoutSessionDao().clearAll()
        appDb.historyDao().clearAllHistory()
        appDb.runningSessionDao().clearAllRunningSessions()

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

    private fun sampleWorkout(
        id: String = "w_run2_strength",
        name: String = "Upper Strength Run2"
    ): Workout {
        val exercises = listOf(
            WorkoutExercise(
                id = "ex_run2_1",
                exerciseId = "peito_1",
                name = "Supino Reto",
                category = "Peito",
                equipment = "Barra",
                restTimeSeconds = 90,
                progressionType = "Linear",
                executionType = "REPS",
                sets = listOf(
                    WorkoutSet(
                        id = "set_run2_1",
                        index = 1,
                        reps = 10,
                        weight = 80f,
                        baseWeight = 80f,
                        completed = true,
                        rpe = "8",
                        setType = SetType.STANDARD
                    ),
                    WorkoutSet(
                        id = "set_run2_2",
                        index = 2,
                        reps = 10,
                        weight = 80f,
                        baseWeight = 80f,
                        completed = false,
                        rpe = "",
                        setType = SetType.STANDARD
                    )
                )
            )
        )
        return Workout(
            id = id,
            name = name,
            exercisesJson = JsonUtils.serializeWorkoutExercises(exercises)
        )
    }

    private fun sampleGymSession(
        id: String,
        workoutName: String = "Full Body Teste",
        notes: String = ""
    ): GymWorkoutSession {
        return GymWorkoutSession(
            id = id,
            date = System.currentTimeMillis(),
            workoutName = workoutName,
            durationSeconds = 1800,
            totalVolumeKg = 2400f,
            totalSets = 3,
            completedSets = 3,
            exercises = listOf(
                GymExerciseRecord(
                    name = "Supino Reto",
                    equipment = "Barra",
                    category = "Peito",
                    sets = listOf(
                        GymSetRecord(1, 80f, 10, true),
                        GymSetRecord(2, 80f, 10, true),
                        GymSetRecord(3, 80f, 10, true)
                    )
                )
            ),
            notes = notes
        )
    }

    @Test
    fun run2_seedDoesNotReappearAfterDeleteOrClearAndReinit() {
        // 1) Instalacao limpa: seeds inseridos uma unica vez e flag marcada como true
        GymHistoryRepository.init(app)
        assertEquals(2, GymHistoryRepository.sessions.value.size)
        assertTrue(gymHistoryPrefs.getBoolean(GymHistoryRepository.KEY_SEEDS_INITIALIZED, false))

        // 2) Excluir um registro seed e reinicializar o repositorio
        val deleted = GymHistoryRepository.deleteSession(
            app,
            GymHistoryRepository.OFFICIAL_FIELD_SESSION_2026_10_30_ID
        )
        assertTrue(deleted)
        assertEquals(1, GymHistoryRepository.sessions.value.size)

        GymHistoryRepository.resetForTesting()
        GymHistoryRepository.init(app)
        assertEquals(1, GymHistoryRepository.sessions.value.size)
        assertTrue(
            GymHistoryRepository.sessions.value.none {
                it.id == GymHistoryRepository.OFFICIAL_FIELD_SESSION_2026_10_30_ID
            }
        )

        // 3) Limpar todo o historico e reinicializar o repositorio: nenhum seed deve ressuscitar
        GymHistoryRepository.clearAllSessions(app)
        assertEquals(0, GymHistoryRepository.sessions.value.size)
        assertTrue(gymHistoryPrefs.getBoolean(GymHistoryRepository.KEY_SEEDS_INITIALIZED, false))

        GymHistoryRepository.resetForTesting()
        GymHistoryRepository.init(app)
        assertEquals(0, GymHistoryRepository.sessions.value.size)
    }

    @Test
    fun run2_legacyInstallWithExistingHistoryMarksSeedFlagWithoutReinsertingSeeds() {
        val existingUserSession = sampleGymSession(id = "legacy_user_workout_1", workoutName = "Treino Legado")
        val legacyArray = JSONArray().apply { put(existingUserSession.toJson()) }
        gymHistoryPrefs.edit()
            .putString(GymHistoryRepository.KEY_SESSIONS_JSON, legacyArray.toString())
            .commit()

        GymHistoryRepository.init(app)

        assertEquals(1, GymHistoryRepository.sessions.value.size)
        assertEquals("legacy_user_workout_1", GymHistoryRepository.sessions.value.first().id)
        assertTrue(gymHistoryPrefs.getBoolean(GymHistoryRepository.KEY_SEEDS_INITIALIZED, false))
        assertFalse(GymHistoryRepository.isCorruptedLockActive.value)
    }

    @Test
    fun run2_corruptedJsonIsQuarantinedAndNeverOverwrittenBySeedsOrWrites() {
        val corruptedPayload = "{corrupted_user_history_json_payload"
        gymHistoryPrefs.edit()
            .putString(GymHistoryRepository.KEY_SESSIONS_JSON, corruptedPayload)
            .commit()

        GymHistoryRepository.init(app)

        // Quarentena ativa, conteudo original intacto e copiado para chave de quarentena
        assertTrue(GymHistoryRepository.isCorruptedLockActive.value)
        assertTrue(GymHistoryRepository.sessions.value.isEmpty())
        assertEquals(
            corruptedPayload,
            gymHistoryPrefs.getString(GymHistoryRepository.KEY_SESSIONS_JSON, null)
        )
        assertEquals(
            corruptedPayload,
            gymHistoryPrefs.getString(GymHistoryRepository.KEY_CORRUPTED_RAW_JSON, null)
        )
        assertTrue(gymHistoryPrefs.getLong(GymHistoryRepository.KEY_CORRUPTED_TIMESTAMP, 0L) > 0L)

        // Tentativas de escrita/exclusao nao podem sobrescrever o JSON corrompido
        val saveAllowed = GymHistoryRepository.saveSession(app, sampleGymSession(id = "blocked_save"))
        val deleteAllowed = GymHistoryRepository.deleteSession(app, "blocked_delete")
        assertFalse(saveAllowed)
        assertFalse(deleteAllowed)
        assertEquals(
            corruptedPayload,
            gymHistoryPrefs.getString(GymHistoryRepository.KEY_SESSIONS_JSON, null)
        )

        // Reinicializacao mantem quarentena e nao injeta seeds
        GymHistoryRepository.resetForTesting()
        GymHistoryRepository.init(app)
        assertTrue(GymHistoryRepository.isCorruptedLockActive.value)
        assertTrue(GymHistoryRepository.sessions.value.isEmpty())
        assertEquals(
            corruptedPayload,
            gymHistoryPrefs.getString(GymHistoryRepository.KEY_SESSIONS_JSON, null)
        )

        // Apenas limpeza explicita desbloqueia escrita e remove quarentena
        GymHistoryRepository.clearAllSessions(app)
        assertFalse(GymHistoryRepository.isCorruptedLockActive.value)
        assertFalse(gymHistoryPrefs.contains(GymHistoryRepository.KEY_CORRUPTED_RAW_JSON))
        assertTrue(GymHistoryRepository.saveSession(app, sampleGymSession(id = "recovered_session_1")))
        assertEquals(1, GymHistoryRepository.sessions.value.size)
        assertEquals("recovered_session_1", GymHistoryRepository.sessions.value.first().id)
    }

    @Test
    fun run2_corruptedElementInsideJsonArrayQuarantinesWithoutDestructiveOverwrite() {
        val partiallyCorruptedArray = JSONArray().apply {
            put(sampleGymSession(id = "valid_1").toJson())
            put("not_a_valid_json_object")
        }.toString()

        gymHistoryPrefs.edit()
            .putString(GymHistoryRepository.KEY_SESSIONS_JSON, partiallyCorruptedArray)
            .commit()

        GymHistoryRepository.init(app)

        assertTrue(GymHistoryRepository.isCorruptedLockActive.value)
        assertTrue(GymHistoryRepository.sessions.value.isEmpty())
        assertEquals(
            partiallyCorruptedArray,
            gymHistoryPrefs.getString(GymHistoryRepository.KEY_SESSIONS_JSON, null)
        )
        assertEquals(
            partiallyCorruptedArray,
            gymHistoryPrefs.getString(GymHistoryRepository.KEY_CORRUPTED_RAW_JSON, null)
        )
    }

    @Test
    fun run2_finishActiveWorkoutRoomFailurePreventsGymHistoryWrite() = runBlocking {
        // Garante historico limpo para observacao inequivoca
        GymHistoryRepository.clearAllSessions(app)
        assertEquals(0, GymHistoryRepository.sessions.value.size)

        val (vm, _) = createMainViewModel()
        awaitCondition { vm.isSessionRecoveryCompleted() }

        val workout = sampleWorkout()
        vm.startWorkoutSession(workout)
        awaitCondition { vm.isPreSessionActive.value }

        vm.confirmAndStartWorkout()
        awaitCondition {
            vm.workoutFlowState.value == WorkoutFlowState.Running && vm.currentSessionId.value != null
        }
        val activeSessionId = vm.currentSessionId.value
        assertNotNull(activeSessionId)

        val appDb = AppDatabase.getDatabase(app)
        awaitCondition {
            runBlocking { appDb.workoutSessionDao().getSessionById(activeSessionId!!) != null }
        }
        val roomRowBefore = appDb.workoutSessionDao().getSessionById(activeSessionId!!)
        assertNotNull(roomRowBefore)
        assertEquals(WorkoutSessionStatus.ATIVO.rawValue, roomRowBefore!!.status)

        // Simula conflito/falha na transicao do Room marcando a sessao como DESCARTADO no banco
        val affected = appDb.workoutSessionDao().updateStatusAndDurationVersioned(
            sessionId = activeSessionId,
            newStatus = WorkoutSessionStatus.DESCARTADO.rawValue,
            expectedVersion = roomRowBefore.version + 1,
            completedAt = System.currentTimeMillis(),
            durationSeconds = 10L
        )
        assertEquals(1, affected)

        vm.finishActiveWorkout(autoCompleteIfEmpty = true)

        awaitCondition { vm.workoutErrorMessage.value != null }

        // Como a transicao no Room falhou, nada deve ter sido gravado em GymHistoryRepository nem em workout_history
        assertEquals(0, GymHistoryRepository.sessions.value.size)
        assertTrue(GymHistoryRepository.sessions.value.none { it.id == activeSessionId })
        val historyEntries = appDb.historyDao().getAllHistory().first()
        assertTrue(historyEntries.none { it.id == activeSessionId })
        assertTrue(vm.activeScreen.value != AppScreen.CONCLUIDO)
    }

    @Test
    fun run2_finishActiveWorkoutRoomSuccessWritesOnceAndIsIdempotentBySessionId() = runBlocking {
        GymHistoryRepository.clearAllSessions(app)
        assertEquals(0, GymHistoryRepository.sessions.value.size)

        val (vm, _) = createMainViewModel()
        awaitCondition { vm.isSessionRecoveryCompleted() }

        val workout = sampleWorkout(id = "w_run2_ok", name = "Upper Push Run2")
        vm.startWorkoutSession(workout)
        awaitCondition { vm.isPreSessionActive.value }

        vm.confirmAndStartWorkout()
        awaitCondition {
            vm.workoutFlowState.value == WorkoutFlowState.Running && vm.currentSessionId.value != null
        }
        val activeSessionId = vm.currentSessionId.value!!
        val appDb = AppDatabase.getDatabase(app)
        awaitCondition {
            runBlocking { appDb.workoutSessionDao().getSessionById(activeSessionId) != null }
        }

        vm.finishActiveWorkout(autoCompleteIfEmpty = true)
        awaitCondition { vm.activeScreen.value == AppScreen.CONCLUIDO && vm.currentSessionId.value == null }

        // 1) Room confirmado como CONCLUIDO
        val persistedSession = appDb.workoutSessionDao().getSessionById(activeSessionId)
        assertNotNull(persistedSession)
        assertEquals(WorkoutSessionStatus.CONCLUIDO.rawValue, persistedSession!!.status)

        // 2) Gravacao unica com sessionId canonico em GymHistoryRepository e WorkoutHistoryEntry
        val gymSessions = GymHistoryRepository.sessions.value
        assertEquals(1, gymSessions.size)
        assertEquals(activeSessionId, gymSessions.first().id)

        val historyEntries = appDb.historyDao().getAllHistory().first()
        assertEquals(1, historyEntries.count { it.id == activeSessionId })

        // 3) Idempotencia por sessionId em caso de retentativa de gravacao
        val retried = GymHistoryRepository.saveSession(
            app,
            gymSessions.first().copy(notes = "retentativa_idempotente")
        )
        assertTrue(retried)
        assertEquals(1, GymHistoryRepository.sessions.value.size)
        assertEquals(1, GymHistoryRepository.sessions.value.count { it.id == activeSessionId })
        assertEquals("retentativa_idempotente", GymHistoryRepository.sessions.value.first().notes)

        // 4) Persistencia consistente apos reinicializacao do repositorio
        GymHistoryRepository.resetForTesting()
        GymHistoryRepository.init(app)
        assertEquals(1, GymHistoryRepository.sessions.value.size)
        assertEquals(activeSessionId, GymHistoryRepository.sessions.value.first().id)
    }

    @Test
    fun run2_deleteRunningSessionRemovesSessionAndLinkedGpsPointsWithoutAffectingOthers() = runBlocking {
        val runToDelete = RunningSession(
            id = "run_delete_target",
            date = System.currentTimeMillis() - 10_000L,
            sessionType = "VOLUME",
            durationSec = 1800,
            distanceMeters = 5000,
            avgPaceSecPerKm = 360
        )
        val runToKeep = RunningSession(
            id = "run_keep_intact",
            date = System.currentTimeMillis(),
            sessionType = "CONTINUOUS",
            durationSec = 2400,
            distanceMeters = 7000,
            avgPaceSecPerKm = 342
        )

        inMemoryRepo.saveRunningSession(runToDelete)
        inMemoryRepo.saveRunningSession(runToKeep)

        val now = System.currentTimeMillis()
        inMemoryDb.gpsTrackPointDao().insertAll(
            listOf(
                GpsTrackPoint("run_delete_target", 1000L, now, -23.5501, -46.6331, 5.0f, 3.0f, 90f, 760.0),
                GpsTrackPoint("run_delete_target", 2000L, now + 1000L, -23.5502, -46.6332, 4.5f, 3.1f, 91f, 760.5),
                GpsTrackPoint("run_delete_target", 3000L, now + 2000L, -23.5503, -46.6333, 4.0f, 3.2f, 92f, 761.0),
                GpsTrackPoint("run_keep_intact", 1000L, now, -23.5601, -46.6431, 5.0f, 3.3f, 180f, 770.0),
                GpsTrackPoint("run_keep_intact", 2000L, now + 1000L, -23.5602, -46.6432, 4.2f, 3.4f, 181f, 770.5)
            )
        )

        // Estado inicial validado
        assertEquals(2, inMemoryDb.runningSessionDao().getAllRunningSessions().first().size)
        assertEquals(3, inMemoryDb.gpsTrackPointDao().countForSession("run_delete_target"))
        assertEquals(2, inMemoryDb.gpsTrackPointDao().countForSession("run_keep_intact"))

        // Exclusao transacional da corrida-alvo
        inMemoryRepo.deleteRunningSession("run_delete_target")

        // Corrida-alvo e seus pontos GPS removidos; outra corrida e seus pontos GPS intactos
        val remainingRuns = inMemoryDb.runningSessionDao().getAllRunningSessions().first()
        assertEquals(1, remainingRuns.size)
        assertEquals("run_keep_intact", remainingRuns.first().id)
        assertEquals(0, inMemoryDb.gpsTrackPointDao().countForSession("run_delete_target"))
        assertEquals(2, inMemoryDb.gpsTrackPointDao().countForSession("run_keep_intact"))
    }
}
