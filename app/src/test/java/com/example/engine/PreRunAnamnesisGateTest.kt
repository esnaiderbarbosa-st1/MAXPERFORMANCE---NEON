package com.example.engine

import com.example.data.model.RunningSession
import com.example.ui.components.HybridReadinessEngine
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Testes unitários JVM puros (sem Robolectric) para o fluxo "Instant GO" na Corrida Livre e Prova.
 */
class PreRunAnamnesisGateTest {

    @Test
    fun shouldShowPreRunAnamnesis_returnsFalse_forFreeRunAndRaceModes() {
        // Via enum explícito FreeRunRaceMode
        assertFalse(RunningProgramManager.shouldShowPreRunAnamnesis(freeRaceMode = FreeRunRaceMode.FREE_RUN))
        assertFalse(RunningProgramManager.shouldShowPreRunAnamnesis(freeRaceMode = FreeRunRaceMode.RACE_5K))
        assertFalse(RunningProgramManager.shouldShowPreRunAnamnesis(freeRaceMode = FreeRunRaceMode.RACE_10K))

        // Via sessionType gerado por getFreeOrRacePlan (inclusive case-insensitive)
        assertFalse(
            RunningProgramManager.shouldShowPreRunAnamnesis(
                freeRaceMode = null,
                sessionType = RunningProgramManager.getFreeOrRacePlan(FreeRunRaceMode.FREE_RUN).sessionType
            )
        )
        assertFalse(
            RunningProgramManager.shouldShowPreRunAnamnesis(
                freeRaceMode = null,
                sessionType = RunningProgramManager.getFreeOrRacePlan(FreeRunRaceMode.RACE_5K).sessionType
            )
        )
        assertFalse(
            RunningProgramManager.shouldShowPreRunAnamnesis(
                freeRaceMode = null,
                sessionType = RunningProgramManager.getFreeOrRacePlan(FreeRunRaceMode.RACE_10K).sessionType
            )
        )
        assertFalse(RunningProgramManager.shouldShowPreRunAnamnesis(freeRaceMode = null, sessionType = "free_run"))
    }

    @Test
    fun shouldShowPreRunAnamnesis_returnsFalse_forLeakedFreeRunTitleWeekZeroOrFreeRegenerative() {
        // Caso vazado 1: freeRaceMode == null e sessionType não-FREE_RUN, mas título indica "Corrida Livre" ou "Livre"
        assertFalse(
            RunningProgramManager.shouldShowPreRunAnamnesis(
                freeRaceMode = null,
                sessionType = "CONTINUOUS",
                planTitle = "Corrida Livre"
            )
        )
        assertFalse(
            RunningProgramManager.shouldShowPreRunAnamnesis(
                freeRaceMode = null,
                sessionType = null,
                displayTitle = "Corrida Livre"
            )
        )
        assertFalse(
            RunningProgramManager.shouldShowPreRunAnamnesis(
                freeRaceMode = null,
                sessionType = "VOLUME",
                planTitle = "Livre"
            )
        )

        // Caso vazado 2: weekNumber <= 0 (que getDisplayTitleForSessionType exibe como "Corrida Livre")
        assertFalse(
            RunningProgramManager.shouldShowPreRunAnamnesis(
                freeRaceMode = null,
                sessionType = "CONTINUOUS",
                weekNumber = 0
            )
        )

        // Caso vazado 3: programa "free_regenerative" / sessionType "FREE_REGENERATIVE" ("Rodagem Livre / Regenerativo")
        val regenPlan = RunningProgramManager.getPlanForProgram("free_regenerative", week = 1, day = 1)
        assertFalse(
            RunningProgramManager.shouldShowPreRunAnamnesis(
                freeRaceMode = null,
                sessionType = regenPlan.sessionType,
                planTitle = regenPlan.title,
                weekNumber = regenPlan.weekNumber,
                programId = "free_regenerative"
            )
        )
        assertFalse(
            RunningProgramManager.shouldShowPreRunAnamnesis(
                freeRaceMode = null,
                sessionType = "FREE_REGENERATIVE"
            )
        )
        assertFalse(
            RunningProgramManager.shouldShowPreRunAnamnesis(
                freeRaceMode = null,
                programId = "free_regenerative"
            )
        )
    }

    @Test
    fun shouldShowPreRunAnamnesis_returnsTrue_forStructuredPlanWorkouts() {
        val structuredPlan = RunningProgramManager.getPlanForWeek(week = 1, day = 1)
        assertTrue(
            RunningProgramManager.shouldShowPreRunAnamnesis(
                freeRaceMode = null,
                sessionType = structuredPlan.sessionType,
                planTitle = structuredPlan.title,
                displayTitle = "Semana 1 · Dia 1",
                weekNumber = structuredPlan.weekNumber,
                programId = "run_30m"
            )
        )
        val road5kPlan = RunningProgramManager.getPlanForProgram("road_to_5k", week = 2, day = 1)
        assertTrue(
            RunningProgramManager.shouldShowPreRunAnamnesis(
                freeRaceMode = null,
                sessionType = road5kPlan.sessionType,
                planTitle = road5kPlan.title,
                displayTitle = "Semana 2 · Dia 1",
                weekNumber = road5kPlan.weekNumber,
                programId = "road_to_5k"
            )
        )
        val rtmPlan = RunningProgramManager.getPlanForProgram("run_to_maintain", week = 1, day = 1)
        assertTrue(
            RunningProgramManager.shouldShowPreRunAnamnesis(
                freeRaceMode = null,
                sessionType = rtmPlan.sessionType,
                planTitle = rtmPlan.title,
                weekNumber = rtmPlan.weekNumber,
                programId = "run_to_maintain"
            )
        )
        val trackPlan = RunningProgramManager.getTrack400mPlan(weekNumber = 1)
        assertTrue(
            RunningProgramManager.shouldShowPreRunAnamnesis(
                freeRaceMode = null,
                sessionType = trackPlan.sessionType,
                planTitle = trackPlan.title,
                weekNumber = trackPlan.weekNumber,
                programId = "fartlek_intervals"
            )
        )
        assertTrue(RunningProgramManager.shouldShowPreRunAnamnesis(freeRaceMode = null, sessionType = "VOLUME"))
        assertTrue(RunningProgramManager.shouldShowPreRunAnamnesis(freeRaceMode = null, sessionType = "CONTINUOUS"))
        assertTrue(RunningProgramManager.shouldShowPreRunAnamnesis(freeRaceMode = null, sessionType = "SPECIFICITY"))
        assertTrue(RunningProgramManager.shouldShowPreRunAnamnesis(freeRaceMode = null, sessionType = "TRACK_400M"))
        assertTrue(RunningProgramManager.shouldShowPreRunAnamnesis(freeRaceMode = null, sessionType = "RX_INT-400", planTitle = "INT-400 • Tiros de Pista 10× 400m", weekNumber = 1))
        assertTrue(RunningProgramManager.shouldShowPreRunAnamnesis(freeRaceMode = null, sessionType = null))
    }

    @Test
    fun sessionWithNullPreRunCheckIn_isSafeForRaioXReadinessEvaluation() {
        val freeRunSession = RunningSession(
            id = "taf_instant_go_1",
            date = System.currentTimeMillis() - 2L * 3600L * 1000L,
            weekNumber = 0,
            dayNumber = 1,
            sessionType = FreeRunRaceMode.FREE_RUN.sessionType,
            durationSec = 720,
            distanceMeters = 2400,
            avgPaceSecPerKm = 300,
            rpe = 8,
            avgHr = 168,
            maxHr = 176,
            preRunCheckIn = null
        )

        assertNull(freeRunSession.preRunCheckIn)
        val report = HybridReadinessEngine.evaluate(
            gymHistory = emptyList(),
            gymSessions = emptyList(),
            runningHistory = listOf(freeRunSession),
            athleteAge = 32,
            calibratedMaxHrBpm = 176
        )
        assertTrue(report.scorePct in 38..100)
        assertTrue(report.hasRunningSession48h)
    }
}
