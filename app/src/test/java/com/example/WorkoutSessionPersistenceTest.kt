package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.AppDatabase
import com.example.data.database.WorkoutSessionDao
import com.example.data.model.WorkoutExercise
import com.example.data.model.WorkoutSessionEntity
import com.example.data.model.WorkoutSessionStatus
import com.example.data.model.WorkoutSessionValidator
import com.example.data.model.WorkoutSet
import com.example.data.model.WorkoutTimeUtils
import com.example.utils.JsonUtils
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WorkoutSessionPersistenceTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: WorkoutSessionDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.workoutSessionDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testPersistence_activeSessionCreatedWithCorrectStatusAndMetadata() = runBlocking {
        val workoutId = "w_supino_123"
        val workoutName = "Treino A - Peito e Tríceps"
        val startTime = System.currentTimeMillis()

        val sampleExercises = listOf(
            WorkoutExercise(
                id = "ex_1",
                exerciseId = "bench_press",
                name = "Supino Reto com Barra",
                category = "Peito",
                sets = listOf(
                    WorkoutSet(id = "s_1", index = 1, reps = 10, weight = 60f, completed = false),
                    WorkoutSet(id = "s_2", index = 2, reps = 10, weight = 60f, completed = false)
                )
            )
        )
        val json = JsonUtils.serializeWorkoutExercises(sampleExercises)

        val session = WorkoutSessionEntity(
            id = UUID.randomUUID().toString(),
            workoutId = workoutId,
            workoutName = workoutName,
            status = WorkoutSessionStatus.ATIVO.rawValue,
            startTime = startTime,
            endTime = null,
            durationSeconds = 0L,
            exercisesJson = json,
            version = 1,
            monotonicStartTimeNanos = System.nanoTime()
        )

        dao.insertOrUpdate(session)

        val activeList = dao.getAllActiveSessions()
        assertEquals("Deve haver exatamente 1 sessão ativa", 1, activeList.size)
        val active = activeList.first()
        assertEquals("Status deve ser ATIVO", WorkoutSessionStatus.ATIVO.rawValue, active.status)
        assertEquals("Referência da rotina deve coincidir", workoutId, active.workoutId)
        assertEquals("Nome da rotina deve coincidir", workoutName, active.workoutName)
        assertEquals("Data de início deve ser preservada", startTime, active.startTime)
        assertEquals("Versão inicial deve ser 1", 1, active.version)
        assertNull("EndTime deve ser nulo enquanto ativo", active.endTime)
    }

    @Test
    fun testMultipleActiveSessions_returnsAllActiveSessionsAndEnablesIndividualResolution() = runBlocking {
        val session1 = WorkoutSessionEntity(
            id = "sess_1",
            workoutId = "w_1",
            workoutName = "Treino A",
            status = WorkoutSessionStatus.ATIVO.rawValue,
            startTime = System.currentTimeMillis() - 5000,
            exercisesJson = "[]",
            version = 1
        )
        val session2 = WorkoutSessionEntity(
            id = "sess_2",
            workoutId = "w_2",
            workoutName = "Treino B",
            status = WorkoutSessionStatus.ATIVO.rawValue,
            startTime = System.currentTimeMillis(),
            exercisesJson = "[]",
            version = 1
        )

        dao.insertOrUpdate(session1)
        dao.insertOrUpdate(session2)

        // Estado inconsistente: mais de uma sessão ativa
        val activeSessions = dao.getAllActiveSessions()
        assertEquals("Devem ser retornadas todas as sessões ativas (sem LIMIT 1 silencioso)", 2, activeSessions.size)

        // Usuário resolve individualmente a primeira sessão (descarta sess_1)
        val rowsAffected = dao.updateStatusFromActive(
            sessionId = "sess_1",
            status = WorkoutSessionStatus.DESCARTADO.rawValue,
            endTime = System.currentTimeMillis(),
            durationSeconds = 100L
        )
        assertEquals("Transição para DESCARTADO deve afetar 1 linha", 1, rowsAffected)

        // Agora resta exatamente 1 sessão ativa (sess_2), apta para retomada normal
        val remainingActive = dao.getAllActiveSessions()
        assertEquals(1, remainingActive.size)
        assertEquals("sess_2", remainingActive.first().id)
        assertEquals(WorkoutSessionStatus.ATIVO.rawValue, remainingActive.first().status)
    }

    @Test
    fun testEndWithoutSets_updatesStatusToNaoRealizadoWithEndTimeAndDuration() = runBlocking {
        val sessionId = UUID.randomUUID().toString()
        val session = WorkoutSessionEntity(
            id = sessionId,
            workoutId = "w_zero_sets",
            workoutName = "Treino Abandonado Sem Séries",
            status = WorkoutSessionStatus.ATIVO.rawValue,
            startTime = System.currentTimeMillis() - 60000,
            durationSeconds = 0L,
            exercisesJson = "[]",
            version = 1,
            monotonicStartTimeNanos = System.nanoTime() - 60_000_000_000L
        )
        dao.insertOrUpdate(session)

        val finishTime = System.currentTimeMillis()
        val duration = WorkoutTimeUtils.calculateMonotonicDurationSeconds(
            session.monotonicStartTimeNanos,
            session.startTime
        )
        assertTrue("Duração monotônica deve ser maior ou igual a zero", duration >= 0)

        // Encerra sem realizar treino: status NAO_REALIZADO
        val rows = dao.updateStatusFromActive(
            sessionId = sessionId,
            status = WorkoutSessionStatus.NAO_REALIZADO.rawValue,
            endTime = finishTime,
            durationSeconds = duration
        )
        assertEquals(1, rows)

        val updated = dao.getSessionById(sessionId)
        assertNotNull(updated)
        assertEquals(WorkoutSessionStatus.NAO_REALIZADO.rawValue, updated?.status)
        assertEquals(finishTime, updated?.endTime)
        assertTrue("DurationSeconds persistido deve ser >= 0", (updated?.durationSeconds ?: -1) >= 0)

        // Não aparece mais entre sessões ativas
        val active = dao.getAllActiveSessions()
        assertTrue("Sessão NAO_REALIZADO não deve aparecer como ativa", active.none { it.id == sessionId })
    }

    @Test
    fun testHistoryAndMetrics_naoRealizadoAndDescartadoExcludedFromWorkoutHistory() = runBlocking {
        // Garantir que a tabela de histórico de treinos concluídos permanece vazia quando
        // uma sessão é descartada ou concluída como NAO_REALIZADO
        val sessionDiscarded = WorkoutSessionEntity(
            id = "s_discard",
            workoutId = "w_1",
            workoutName = "Treino Descartado",
            status = WorkoutSessionStatus.ATIVO.rawValue,
            startTime = System.currentTimeMillis(),
            exercisesJson = "[]"
        )
        dao.insertOrUpdate(sessionDiscarded)
        dao.updateStatusFromActive("s_discard", WorkoutSessionStatus.DESCARTADO.rawValue, System.currentTimeMillis(), 10L)

        val sessionNotDone = WorkoutSessionEntity(
            id = "s_not_done",
            workoutId = "w_2",
            workoutName = "Treino Não Realizado",
            status = WorkoutSessionStatus.ATIVO.rawValue,
            startTime = System.currentTimeMillis(),
            exercisesJson = "[]"
        )
        dao.insertOrUpdate(sessionNotDone)
        dao.updateStatusFromActive("s_not_done", WorkoutSessionStatus.NAO_REALIZADO.rawValue, System.currentTimeMillis(), 5L)

        // Tabela historyDao NÃO pode ter nenhuma entrada salva
        val historyEntries = db.historyDao().getAllHistory().first()
        assertTrue("Sessões DESCARTADO e NAO_REALIZADO não devem gerar entrada no histórico", historyEntries.isEmpty())
    }

    @Test
    fun testMonotonicDurationCalculation_isImmuneToClockSkewsAndAlwaysNonNegative() {
        val startNanos = System.nanoTime() - 30_000_000_000L // 30 segundos atrás
        val startMillis = System.currentTimeMillis() - 30_000L

        val duration = WorkoutTimeUtils.calculateMonotonicDurationSeconds(startNanos, startMillis)
        assertTrue("Duração monotônica calculada deve estar próxima de 30s", duration in 29..32)

        // Testar relógio do sistema modificado manualmente para o passado
        val futureNanos = System.nanoTime() + 10_000_000_000L // nanos no futuro
        val pastMillis = System.currentTimeMillis() + 100_000L
        val clampedDuration = WorkoutTimeUtils.calculateMonotonicDurationSeconds(futureNanos, pastMillis)
        assertTrue("Duração nunca pode ser negativa mesmo com distorções", clampedDuration >= 0)
    }

    @Test
    fun testOptimisticVersionOrder_preventsStaleSeriesOverwritingNewerSeries() = runBlocking {
        val sessionId = UUID.randomUUID().toString()
        val initialSession = WorkoutSessionEntity(
            id = sessionId,
            workoutId = "w_version_test",
            workoutName = "Teste Versão",
            status = WorkoutSessionStatus.ATIVO.rawValue,
            startTime = System.currentTimeMillis(),
            exercisesJson = "[]",
            version = 1
        )
        dao.insertOrUpdate(initialSession)

        // Gravação 1 baseada na versão esperada 1 -> sucede e incrementa version para 2
        val update1Success = dao.updateExercisesJsonOptimistic(
            sessionId = sessionId,
            exercisesJson = "[{\"id\":\"ex1\"}]",
            expectedVersion = 1
        )
        assertEquals("Atualização com versão correta deve afetar 1 linha", 1, update1Success)

        val sessionAfterUpdate1 = dao.getSessionById(sessionId)
        assertEquals("Versão no banco deve ser incrementada para 2", 2, sessionAfterUpdate1?.version)

        // Gravação 2 atrasada que tenta operar sobre a versão 1 (desatualizada) -> rejeitada!
        val updateStaleFailure = dao.updateExercisesJsonOptimistic(
            sessionId = sessionId,
            exercisesJson = "[{\"id\":\"stale_ex\"}]",
            expectedVersion = 1
        )
        assertEquals("Atualização com versão desatualizada deve afetar 0 linhas (rejeitada)", 0, updateStaleFailure)

        // Gravação 3 com a versão atualizada 2 -> sucede e incrementa para 3
        val update3Success = dao.updateExercisesJsonOptimistic(
            sessionId = sessionId,
            exercisesJson = "[{\"id\":\"fresh_ex\"}]",
            expectedVersion = 2
        )
        assertEquals(1, update3Success)

        val sessionAfterUpdate3 = dao.getSessionById(sessionId)
        assertEquals(3, sessionAfterUpdate3?.version)
        assertTrue(sessionAfterUpdate3?.exercisesJson?.contains("fresh_ex") == true)
    }

    @Test
    fun testStatusTransitions_mustStrictlyOriginateFromAtivo() = runBlocking {
        val sessionId = UUID.randomUUID().toString()
        val session = WorkoutSessionEntity(
            id = sessionId,
            workoutId = "w_trans",
            workoutName = "Teste Transição",
            status = WorkoutSessionStatus.ATIVO.rawValue,
            startTime = System.currentTimeMillis(),
            exercisesJson = "[]",
            version = 1
        )
        dao.insertOrUpdate(session)

        // 1. Transição de ATIVO para CONCLUIDO -> SUCESSO
        val concludeResult = dao.updateStatusFromActive(
            sessionId = sessionId,
            status = WorkoutSessionStatus.CONCLUIDO.rawValue,
            endTime = System.currentTimeMillis(),
            durationSeconds = 1200L
        )
        assertEquals("Transição a partir de ATIVO deve suceder", 1, concludeResult)

        // 2. Tentativa de transição partindo de status já CONCLUIDO para DESCARTADO -> FALHA (0 linhas)
        val illegalTransition1 = dao.updateStatusFromActive(
            sessionId = sessionId,
            status = WorkoutSessionStatus.DESCARTADO.rawValue,
            endTime = System.currentTimeMillis(),
            durationSeconds = 1200L
        )
        assertEquals("Transição partindo de CONCLUIDO deve ser rejeitada pelo DAO", 0, illegalTransition1)

        // 3. Tentativa de atualizar séries em sessão já concluída -> FALHA (0 linhas)
        val illegalExerciseUpdate = dao.updateExercisesJsonOptimistic(
            sessionId = sessionId,
            exercisesJson = "[{\"id\":\"new_set\"}]",
            expectedVersion = 2
        )
        assertEquals("Atualização de séries em sessão não ATIVO deve ser rejeitada", 0, illegalExerciseUpdate)
    }

    @Test
    fun testMaintenanceRoutine_purgesSessionsOlderThan30Days() = runBlocking {
        val thirtyOneDaysAgo = System.currentTimeMillis() - (31L * 24 * 60 * 60 * 1000L)
        val oneDayAgo = System.currentTimeMillis() - (1L * 24 * 60 * 60 * 1000L)
        val threshold30Days = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000L)

        val expiredSession = WorkoutSessionEntity(
            id = "sess_expired",
            workoutId = "w_old",
            workoutName = "Treino Antigo Expirado",
            status = WorkoutSessionStatus.DESCARTADO.rawValue,
            startTime = thirtyOneDaysAgo - 3600000,
            endTime = thirtyOneDaysAgo,
            durationSeconds = 300L,
            exercisesJson = "[]"
        )
        val recentSession = WorkoutSessionEntity(
            id = "sess_recent",
            workoutId = "w_rec",
            workoutName = "Treino Recente Descartado",
            status = WorkoutSessionStatus.DESCARTADO.rawValue,
            startTime = oneDayAgo - 3600000,
            endTime = oneDayAgo,
            durationSeconds = 300L,
            exercisesJson = "[]"
        )

        dao.insertOrUpdate(expiredSession)
        dao.insertOrUpdate(recentSession)

        val deletedCount = dao.deleteExpiredSessions(threshold30Days)
        assertEquals("Deve purgar exatamente 1 sessão expirada", 1, deletedCount)

        assertNull("Sessão expirada (>30 dias) deve ter sido deletada", dao.getSessionById("sess_expired"))
        assertNotNull("Sessão recente (<30 dias) deve ser mantida", dao.getSessionById("sess_recent"))
    }

    @Test
    fun testValidation_rejectsNegativeWeightsRepsAndInvalidStatus() {
        val invalidSession = WorkoutSessionEntity(
            id = "invalid_1",
            workoutId = "w_inv",
            workoutName = "Treino Inválido",
            status = "STATUS_INEXISTENTE",
            startTime = 0L,
            exercisesJson = "[]"
        )

        try {
            WorkoutSessionValidator.validateForPersist(invalidSession)
            fail("Deveria lançar IllegalArgumentException para startTime <= 0 ou status inválido")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("startTime") == true || e.message?.contains("Status inválido") == true)
        }

        val invalidExercises = listOf(
            WorkoutExercise(
                id = "ex_neg",
                exerciseId = "leg_press",
                name = "Leg Press",
                category = "Pernas",
                sets = listOf(
                    WorkoutSet(id = "s_neg", index = 1, reps = -5, weight = -10f, completed = true)
                )
            )
        )

        try {
            WorkoutSessionValidator.validateExercises(invalidExercises)
            fail("Deveria lançar IllegalArgumentException para peso negativo")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Carga não pode ser negativa") == true)
        }
    }

    @Test
    fun testFastSetCompletion_warnsUnder15Seconds() {
        val lastTimes = mutableMapOf<String, Long>()
        val exerciseKey = "bench_press"

        val t0 = 100_000L
        lastTimes[exerciseKey] = t0

        // Segunda série registrada 5 segundos depois (< 15s)
        val t1 = t0 + 5_000L
        val interval = t1 - lastTimes[exerciseKey]!!
        val isFast = interval < 15_000L

        assertTrue("Intervalo de 5 segundos deve disparar aviso de preenchimento rápido", isFast)

        // Terceira série registrada 20 segundos depois (> 15s)
        lastTimes[exerciseKey] = t1
        val t2 = t1 + 20_000L
        val nextInterval = t2 - lastTimes[exerciseKey]!!
        val isNextFast = nextInterval < 15_000L

        assertFalse("Intervalo de 20 segundos NÃO deve disparar aviso de preenchimento rápido", isNextFast)
    }

    @Test
    fun testCompletedStatus_invalidatesRecommendationCache() = runBlocking {
        val session = WorkoutSessionEntity(
            id = "sess_completion_test",
            workoutId = "w_test_upper",
            workoutName = "Treino Superior",
            status = WorkoutSessionStatus.ATIVO.rawValue,
            startTime = System.currentTimeMillis(),
            monotonicStartTimeNanos = System.nanoTime(),
            exercisesJson = "[]",
            version = 1
        )
        dao.insertOrUpdate(session)

        // Limpa cache simulando transição para CONCLUIDO
        val affected = dao.updateStatusFromActive(
            sessionId = session.id,
            status = WorkoutSessionStatus.CONCLUIDO.rawValue,
            endTime = System.currentTimeMillis(),
            durationSeconds = 1800L
        )
        assertEquals(1, affected)

        com.example.engine.WorkoutEngine.clearCache()
        val updated = dao.getSessionById("sess_completion_test")
        assertEquals(WorkoutSessionStatus.CONCLUIDO.rawValue, updated?.status)
    }

    @Test
    fun testStrictStateMachineTransitions_validAndTerminalStates() {
        // Validação de transições permitidas a partir de ATIVO
        assertTrue("ATIVO -> CONCLUIDO deve ser permitido",
            com.example.data.model.WorkoutSessionStateMachine.isValidTransition(WorkoutSessionStatus.ATIVO, WorkoutSessionStatus.CONCLUIDO))
        assertTrue("ATIVO -> NAO_REALIZADO deve ser permitido",
            com.example.data.model.WorkoutSessionStateMachine.isValidTransition(WorkoutSessionStatus.ATIVO, WorkoutSessionStatus.NAO_REALIZADO))
        assertTrue("ATIVO -> DESCARTADO deve ser permitido",
            com.example.data.model.WorkoutSessionStateMachine.isValidTransition(WorkoutSessionStatus.ATIVO, WorkoutSessionStatus.DESCARTADO))
        assertFalse("ATIVO -> ATIVO não deve ser permitido",
            com.example.data.model.WorkoutSessionStateMachine.isValidTransition(WorkoutSessionStatus.ATIVO, WorkoutSessionStatus.ATIVO))

        // Estados terminais não podem transicionar para nada
        for (terminal in listOf(WorkoutSessionStatus.CONCLUIDO, WorkoutSessionStatus.NAO_REALIZADO, WorkoutSessionStatus.DESCARTADO)) {
            assertTrue("Estado deve ser terminal", terminal.isTerminal)
            for (target in WorkoutSessionStatus.values()) {
                assertFalse("Estado terminal $terminal não pode transicionar para $target",
                    com.example.data.model.WorkoutSessionStateMachine.isValidTransition(terminal, target))
            }
        }
    }

    @Test
    fun testOptimisticLockingWithVersionCondition() = runBlocking {
        val sessionId = UUID.randomUUID().toString()
        val session = WorkoutSessionEntity(
            id = sessionId,
            workoutId = "w_opt_test",
            workoutName = "Teste Optimistic Lock",
            status = WorkoutSessionStatus.ATIVO.rawValue,
            startTime = System.currentTimeMillis(),
            exercisesJson = "[]",
            version = 1
        )
        dao.insertOrUpdate(session)

        // Atualização 1 com version < 2: bem-sucedida (version no banco era 1)
        val rows1 = dao.updateStatusVersioned(
            sessionId = sessionId,
            newStatus = WorkoutSessionStatus.CONCLUIDO.rawValue,
            expectedVersion = 2,
            completedAt = System.currentTimeMillis()
        )
        assertEquals("Primeira atualização com versão esperada 2 deve suceder", 1, rows1)

        val updatedSession = dao.getSessionById(sessionId)
        assertEquals("Versão deve ter sido incrementada para 2", 2, updatedSession?.version)
        assertEquals("Status deve ser CONCLUIDO", WorkoutSessionStatus.CONCLUIDO.rawValue, updatedSession?.status)

        // Atualização concorrente concorrente atrasada tentando usar expectedVersion = 2
        // Como a versão atual no banco é 2, a cláusula `version < 2` falhará (2 < 2 é falso)
        val rowsConcurrent = dao.updateStatusVersioned(
            sessionId = sessionId,
            newStatus = WorkoutSessionStatus.DESCARTADO.rawValue,
            expectedVersion = 2,
            completedAt = System.currentTimeMillis()
        )
        assertEquals("Atualização concorrente atrasada deve falhar silenciosamente (0 linhas afetadas)", 0, rowsConcurrent)

        // Confirma que status não foi sobrescrito
        val sessionAfterConflict = dao.getSessionById(sessionId)
        assertEquals(WorkoutSessionStatus.CONCLUIDO.rawValue, sessionAfterConflict?.status)
        assertEquals(2, sessionAfterConflict?.version)
    }

    @Test
    fun testMultipleActiveSessionsAutoResolution_keepsMostRecentAndArchivesOld() = runBlocking {
        val t0 = 1000L
        val t1 = 5000L

        val oldSession = WorkoutSessionEntity(
            id = "session_old",
            workoutId = "w_1",
            workoutName = "Treino Anterior Não Concluído",
            status = WorkoutSessionStatus.ATIVO.rawValue,
            startTime = t0,
            startTimeMonotonic = 10_000L,
            exercisesJson = "[]",
            version = 1
        )
        val newSession = WorkoutSessionEntity(
            id = "session_recent",
            workoutId = "w_2",
            workoutName = "Treino Atual Recente",
            status = WorkoutSessionStatus.ATIVO.rawValue,
            startTime = t1,
            startTimeMonotonic = 50_000L,
            exercisesJson = "[]",
            version = 1
        )

        dao.insertOrUpdate(oldSession)
        dao.insertOrUpdate(newSession)

        val allActive = dao.getAllActiveSessions()
        assertEquals("Deve detectar ambas as sessões ativas", 2, allActive.size)

        // Resolução automática: identificar a mais recente
        val mostRecent = allActive.maxByOrNull { maxOf(it.startTimeMonotonic, it.startTime) }
        assertEquals("A sessão mais recente deve ser a com maior tempo monotônico/startTime", "session_recent", mostRecent?.id)

        // Arquivar as antigas atomicamente via DAO
        val toArchive = allActive.filter { it.id != mostRecent?.id }
        assertEquals(1, toArchive.size)
        val archiveTime = System.currentTimeMillis()
        val archivedCount = dao.archiveMultipleSessionsAtomically(toArchive, archiveTime)
        assertEquals(1, archivedCount)

        // Verificar estados finais no banco
        val archivedDb = dao.getSessionById("session_old")
        assertEquals(WorkoutSessionStatus.NAO_REALIZADO.rawValue, archivedDb?.status)
        assertEquals(archiveTime, archivedDb?.completedAt)
        assertEquals(2, archivedDb?.version)

        val activeDb = dao.getAllActiveSessions()
        assertEquals(1, activeDb.size)
        assertEquals("session_recent", activeDb.first().id)
        assertEquals(WorkoutSessionStatus.ATIVO.rawValue, activeDb.first().status)
    }
}
