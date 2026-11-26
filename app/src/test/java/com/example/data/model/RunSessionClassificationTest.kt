package com.example.data.model

import com.example.engine.RunningProgramManager
import com.example.utils.AppBackupManager
import com.example.utils.AppBackupPayload
import com.example.utils.RunningCsvExporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RunSessionClassificationTest {

    @Test
    fun freeRunMarkedAsRaceWithOptionalNameUpdatesTitleAndBadgeAndRevertsCleanly() {
        val freeRun = RunningSession(
            id = "run_free_1",
            date = 1_765_000_000_000L,
            weekNumber = 0,
            dayNumber = 0,
            sessionType = "FREE_RUN",
            durationSec = 1520,
            distanceMeters = 5050,
            avgPaceSecPerKm = 301,
            environment = "OUTDOOR"
        )

        assertFalse(freeRun.isClassifiedAsRace())
        assertEquals("Corrida Livre", RunningProgramManager.getDisplayTitleForSession(freeRun))
        assertEquals("LIVRE", RunningProgramManager.getBadgeLabelForSession(freeRun))

        // Marca como Prova com nome personalizado
        val asNamedRace = freeRun.withRaceClassification(isRace = true, raceName = "  Circuito 5K  ")
        assertTrue(asNamedRace.isClassifiedAsRace())
        assertEquals("Circuito 5K", asNamedRace.customRaceName())
        assertEquals("Circuito 5K", RunningProgramManager.getDisplayTitleForSession(asNamedRace))
        assertEquals("PROVA", RunningProgramManager.getBadgeLabelForSession(asNamedRace))
        // Preserva sessionType original para reversibilidade lossless
        assertEquals("FREE_RUN", asNamedRace.sessionType)

        // Marca como Prova sem nome -> mantém título original com badge PROVA
        val asUnnamedRace = freeRun.withRaceClassification(isRace = true, raceName = "   ")
        assertTrue(asUnnamedRace.isClassifiedAsRace())
        assertNull(asUnnamedRace.customRaceName())
        assertEquals("Corrida Livre", RunningProgramManager.getDisplayTitleForSession(asUnnamedRace))
        assertEquals("PROVA", RunningProgramManager.getBadgeLabelForSession(asUnnamedRace))

        // Desmarca voltando para Treino -> limpa raceName e restaura badge/título
        val revertedTraining = asNamedRace.withRaceClassification(isRace = false, raceName = "Circuito 5K")
        assertFalse(revertedTraining.isClassifiedAsRace())
        assertNull(revertedTraining.customRaceName())
        assertNull(revertedTraining.advancedMetrics?.raceName)
        assertEquals("Corrida Livre", RunningProgramManager.getDisplayTitleForSession(revertedTraining))
        assertEquals("LIVRE", RunningProgramManager.getBadgeLabelForSession(revertedTraining))
    }

    @Test
    fun nativeRaceSessionCanBeUnmarkedBackToTraining() {
        val race5k = RunningSession(
            id = "run_race_5k",
            date = 1_765_100_000_000L,
            weekNumber = 0,
            dayNumber = 0,
            sessionType = "RACE_5K",
            durationSec = 1480,
            distanceMeters = 5000,
            avgPaceSecPerKm = 296,
            environment = "OUTDOOR"
        )

        assertTrue(race5k.isClassifiedAsRace())
        assertEquals("Prova 5K", RunningProgramManager.getDisplayTitleForSession(race5k))
        assertEquals("PROVA", RunningProgramManager.getBadgeLabelForSession(race5k))

        val unmarked = race5k.withRaceClassification(isRace = false, raceName = null)
        assertFalse(unmarked.isClassifiedAsRace())
        assertEquals("Treino 5K", RunningProgramManager.getDisplayTitleForSession(unmarked))
        assertEquals("5K", RunningProgramManager.getBadgeLabelForSession(unmarked))
    }

    @Test
    fun backupJsonRoundTripPreservesRaceClassificationAndSupportsLegacyBackups() {
        val markedRace = RunningSession(
            id = "run_backup_race",
            date = 1_765_200_000_000L,
            weekNumber = 0,
            dayNumber = 0,
            sessionType = "FREE_RUN",
            durationSec = 1500,
            distanceMeters = 5000,
            avgPaceSecPerKm = 300,
            advancedMetrics = AdvancedRunningMetrics(
                efficiencyFactorMeterPerBeat = 1.28,
                watchSyncedSteps = 4920
            )
        ).withRaceClassification(isRace = true, raceName = "Circuito 5K")

        val unmarkedRace = RunningSession(
            id = "run_backup_unmarked",
            date = 1_765_300_000_000L,
            weekNumber = 0,
            dayNumber = 0,
            sessionType = "RACE_5K",
            durationSec = 1550,
            distanceMeters = 5000,
            avgPaceSecPerKm = 310
        ).withRaceClassification(isRace = false, raceName = null)

        val payload = AppBackupPayload(
            backupVersion = AppBackupManager.CURRENT_BACKUP_VERSION,
            exportedAtMillis = 1_765_400_000_000L,
            runningSessions = listOf(markedRace, unmarkedRace)
        )

        val json = AppBackupManager.serializeBackup(payload)
        val parsed = AppBackupManager.parseBackup(json)
        assertTrue("Backup JSON com classificação de prova deve restaurar sem erros", parsed.isSuccess)

        val restoredRuns = parsed.getOrThrow().runningSessions.associateBy { it.id }
        val restoredMarked = restoredRuns.getValue("run_backup_race")
        assertTrue(restoredMarked.isClassifiedAsRace())
        assertEquals("Circuito 5K", restoredMarked.customRaceName())
        assertEquals("Circuito 5K", RunningProgramManager.getDisplayTitleForSession(restoredMarked))
        assertEquals("PROVA", RunningProgramManager.getBadgeLabelForSession(restoredMarked))
        // Preserva outras métricas avançadas intactas
        assertEquals(4920, restoredMarked.advancedMetrics?.watchSyncedSteps)

        val restoredUnmarked = restoredRuns.getValue("run_backup_unmarked")
        assertFalse(restoredUnmarked.isClassifiedAsRace())
        assertEquals(false, restoredUnmarked.advancedMetrics?.isRaceOverride)
        assertNull(restoredUnmarked.customRaceName())

        // Compatibilidade retroativa com backup .json antigo (sem isRaceOverride, raceName nem advancedMetrics)
        val legacyBackupJson = """
            {
              "backupVersion": 1,
              "exportedAtMillis": 1760000000000,
              "runningSessions": [
                {
                  "id": "legacy_free_run",
                  "date": 1760000000000,
                  "weekNumber": 0,
                  "dayNumber": 0,
                  "sessionType": "FREE_RUN",
                  "durationSec": 1800,
                  "distanceMeters": 5000,
                  "avgPaceSecPerKm": 360,
                  "rpe": 6,
                  "environment": "OUTDOOR",
                  "isGentleMode": false
                },
                {
                  "id": "legacy_with_old_adv",
                  "date": 1760000500000,
                  "weekNumber": 3,
                  "dayNumber": 2,
                  "sessionType": "CONTINUOUS",
                  "durationSec": 1600,
                  "distanceMeters": 4500,
                  "avgPaceSecPerKm": 355,
                  "rpe": 7,
                  "environment": "OUTDOOR",
                  "isGentleMode": false,
                  "advancedMetrics": {
                    "efficiencyFactorMeterPerBeat": 1.15,
                    "isCardiacDriftApplicable": false,
                    "workIntervalsCount": 0
                  }
                }
              ]
            }
        """.trimIndent()

        val legacyParsed = AppBackupManager.parseBackup(legacyBackupJson)
        assertTrue("Backup antigo sem isRaceOverride/raceName deve ser importado normalmente", legacyParsed.isSuccess)
        val legacyRuns = legacyParsed.getOrThrow().runningSessions.associateBy { it.id }
        val legacyFree = legacyRuns.getValue("legacy_free_run")
        assertFalse(legacyFree.isClassifiedAsRace())
        assertEquals("Corrida Livre", RunningProgramManager.getDisplayTitleForSession(legacyFree))

        val legacyOldAdv = legacyRuns.getValue("legacy_with_old_adv")
        assertFalse(legacyOldAdv.isClassifiedAsRace())
        assertNull(legacyOldAdv.advancedMetrics?.isRaceOverride)
        assertNull(legacyOldAdv.advancedMetrics?.raceName)
    }

    @Test
    fun csvExporterIncludesClassificationAndUpdatedRaceTitle() {
        val raceRun = RunningSession(
            id = "run_csv_race",
            date = 1_765_500_000_000L,
            weekNumber = 0,
            dayNumber = 0,
            sessionType = "FREE_RUN",
            durationSec = 1490,
            distanceMeters = 5000,
            avgPaceSecPerKm = 298,
            environment = "OUTDOOR"
        ).withRaceClassification(isRace = true, raceName = "Circuito 5K Noturno")

        val trainingRun = RunningSession(
            id = "run_csv_train",
            date = 1_765_400_000_000L,
            weekNumber = 2,
            dayNumber = 1,
            sessionType = "VOLUME",
            durationSec = 1800,
            distanceMeters = 4200,
            avgPaceSecPerKm = 428,
            environment = "OUTDOOR"
        )

        val csv = RunningCsvExporter.buildCsvContent(
            history = listOf(raceRun, trainingRun),
            userProfile = UserProfileData(nome = "Atleta MAX")
        )
        val lines = csv.trim().lines()
        assertEquals(3, lines.size)
        assertTrue(lines[0].contains("Classificacao;Titulo_Sessao"))
        assertTrue(lines[1].contains(";Prova;Circuito 5K Noturno;"))
        assertTrue(lines[2].contains(";Treino;Semana 2 · Dia 1;"))
    }
}
