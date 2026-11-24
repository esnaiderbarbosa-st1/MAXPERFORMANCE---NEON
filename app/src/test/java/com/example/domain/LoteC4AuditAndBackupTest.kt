package com.example.domain

import com.example.data.model.BioimpedanceRecord
import com.example.data.model.GymExerciseRecord
import com.example.data.model.GymSetRecord
import com.example.data.model.GymWorkoutSession
import com.example.data.model.RunningSession
import com.example.data.model.UpcomingRaceEvent
import com.example.data.model.UserProfileData
import com.example.data.model.Workout
import com.example.data.model.WorkoutHistoryEntry
import com.example.data.model.WorkoutSessionEntity
import com.example.domain.analysis.MacroPeriodWindow
import com.example.domain.analysis.PeriodMacroAnalysisEngine
import com.example.domain.recomp.BioimpedanceReferences
import com.example.domain.recomp.BodyRecompEngine
import com.example.utils.AppBackupManager
import com.example.utils.AppBackupPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LoteC4AuditAndBackupTest {

    @Test
    fun newProfileWithPersonalGoalsAndEmptyBioimpedanceHistoryNeverInjectsAuthorData() {
        val otherAthleteWithGoals = UserProfileData(
            id = "1",
            nome = "Atleta Teste",
            idade = 32,
            peso = 70.0f,
            altura = 175,
            sexo = "Masculino",
            metaPercentualGordura = 15.0f,
            metaPercentualMusculo = 75.0f,
            metaAguaLitros = 3.5f
        )
        assertTrue(
            "O perfil de teste deve ter metas pessoais configuradas para exercitar o cenário do bug anterior",
            BioimpedanceReferences.hasPersonalGoals(otherAthleteWithGoals)
        )

        val migrated = BodyRecompEngine.migrateOrFixDuplicateBaselineRecords(
            loaded = emptyList(),
            savedMigrationVersion = 0,
            profile = otherAthleteWithGoals
        )
        assertTrue("Histórico vazio deve permanecer vazio na migração", migrated.isEmpty())

        val timelineCards = BodyRecompEngine.buildRecompTimelineCards(
            records = emptyList(),
            profile = otherAthleteWithGoals
        )
        assertTrue("Linha do tempo vazia deve permanecer vazia", timelineCards.isEmpty())

        val fallback = BodyRecompEngine.defaultFallbackRecord(
            profile = otherAthleteWithGoals,
            nowMillis = 1_700_000_000_000L
        )
        assertEquals(70.0f, fallback.weightKg, 0.01f)
        assertEquals(0f, fallback.bodyFatPercent, 0.01f)
        assertEquals(0f, fallback.muscleMassKg, 0.01f)
        assertFalse("Fallback não pode vazar 84,7 kg do autor", fallback.weightKg == 84.7f)
        assertFalse("Fallback não pode vazar 23,0% BF do autor", fallback.bodyFatPercent == 23.0f)
    }

    @Test
    fun periodMacroAnalysisWithZeroBioimpedanceRecordsHasNoHardcodedAuthorValues() {
        val dossier = PeriodMacroAnalysisEngine.analyzePeriod(
            window = MacroPeriodWindow.DAYS_30,
            allRunningSessions = emptyList(),
            allGymSessions = emptyList(),
            allRecompRecords = emptyList(),
            userAgeYears = 32
        )

        val p4 = dossier.pillar4Recomp
        assertEquals(0, p4.recordsInPeriodCount)
        assertEquals(0f, p4.startWeightKg, 0.001f)
        assertEquals(0f, p4.endWeightKg, 0.001f)
        assertEquals(0f, p4.startFatPercent, 0.001f)
        assertEquals(0f, p4.endFatPercent, 0.001f)
        assertEquals(0f, p4.startFatMassKg, 0.001f)
        assertEquals(0f, p4.endFatMassKg, 0.001f)
        assertEquals(0f, p4.startMuscleMassKg, 0.001f)
        assertEquals(0f, p4.endMuscleMassKg, 0.001f)
        assertEquals("--", p4.estimatedPaceGainFormatted)
        assertTrue(
            "Narrativa deve informar ausência de registros",
            p4.narrative.contains("Sem registros de bioimpedância no período", ignoreCase = true)
        )

        val report = dossier.formattedReportText
        assertTrue(
            "Relatório formatado deve informar ausência de bioimpedância",
            report.contains("Sem registros de bioimpedância no período", ignoreCase = true)
        )
        assertFalse("Relatório não pode conter 84,7", report.contains("84,7"))
        assertFalse("Relatório não pode conter 84.7", report.contains("84.7"))
        assertFalse("Relatório não pode conter 61,8", report.contains("61,8"))
        assertFalse("Relatório não pode conter 61.8", report.contains("61.8"))
        assertFalse("Relatório não pode conter 19,5", report.contains("19,5"))
        assertFalse("Relatório não pode conter 23,0%", report.contains("23,0%"))
    }

    @Test
    fun backupJsonRoundTripPreservesAllSectionsAndRejectsMalformedJson() {
        val profile = UserProfileData(
            id = "1",
            nome = "Maria Corredora",
            idade = 29,
            peso = 62.5f,
            altura = 168,
            sexo = "Feminino",
            metaPercentualGordura = 20.0f
        )
        val runSession = RunningSession(
            id = "run_backup_test_1",
            date = 1_760_000_000_000L,
            sessionType = "CONTINUOUS",
            distanceMeters = 5020,
            durationSec = 1560,
            avgPaceSecPerKm = 310,
            avgHr = 152,
            maxHr = 171,
            kmSplitsJson = """[{"kmIndex":1,"distanceMeters":1000.0,"elapsedSeconds":305,"paceSecondsPerKm":305,"avgHr":148}]"""
        )
        val workout = Workout(
            id = "workout_1",
            name = "Full Body A",
            description = "Treino base de força",
            exercisesJson = "[]",
            createdAt = 1_760_000_000_000L
        )
        val historyEntry = WorkoutHistoryEntry(
            id = "hist_1",
            workoutId = "workout_1",
            workoutName = "Full Body A",
            date = 1_760_000_100_000L,
            durationSeconds = 2700L,
            totalWeight = 8450.5f,
            exercisesCompletedCount = 6,
            exercisesJson = "[]"
        )
        val workoutSessionEntity = WorkoutSessionEntity(
            id = "ws_1",
            workoutId = "workout_1",
            workoutName = "Full Body A",
            status = "CONCLUIDO",
            startTime = 1_760_000_000_000L,
            endTime = 1_760_002_700_000L,
            completedAt = 1_760_002_700_000L,
            durationSeconds = 2700L,
            exercisesJson = "[]",
            version = 2,
            startTimeMonotonic = 123456L,
            monotonicStartTimeNanos = 987654321L
        )
        val gymSession = GymWorkoutSession(
            id = "gym_1",
            date = 1_760_000_200_000L,
            workoutName = "Full Body A",
            durationSeconds = 2700,
            totalVolumeKg = 8450.5f,
            totalSets = 12,
            completedSets = 12,
            exercises = listOf(
                GymExerciseRecord(
                    name = "Agachamento Livre",
                    equipment = "Barra",
                    category = "Pernas",
                    sets = listOf(GymSetRecord(1, 60f, 10, true))
                )
            ),
            notes = "Execução firme"
        )
        val bioRecord = BioimpedanceRecord(
            id = "bio_1",
            timestampMillis = 1_760_000_300_000L,
            dateTimeLabel = "15/10/2026 - 07:00",
            athleteName = "Maria Corredora",
            sourceApp = "Fitdays",
            weightKg = 62.5f,
            bmi = 22.1f,
            bodyFatPercent = 21.4f,
            muscleRatePercent = 73.2f,
            fatFreeMassKg = 49.1f,
            subcutaneousFatPercent = 18.0f,
            visceralFatIndex = 5.0f,
            bodyWaterPercent = 56.4f,
            skeletalMusclePercent = 43.8f,
            muscleMassKg = 45.8f,
            boneMassKg = 3.1f,
            proteinPercent = 19.2f,
            bmrKcal = 1480,
            metabolicAge = 27
        )
        val upcomingRace = UpcomingRaceEvent(
            id = "race_1",
            raceName = "Corrida 10K Cidade",
            raceDateMillis = 1_770_000_000_000L,
            distanceCategory = "10K",
            targetPace = "5'00\"/km",
            plannedShoe = "Corre 4"
        )

        val original = AppBackupPayload(
            backupVersion = AppBackupManager.CURRENT_BACKUP_VERSION,
            exportedAtMillis = 1_760_000_500_000L,
            appVersion = "1.0 (342)",
            userProfile = profile,
            runningSessions = listOf(runSession),
            workouts = listOf(workout),
            workoutHistory = listOf(historyEntry),
            workoutSessions = listOf(workoutSessionEntity),
            gymSessions = listOf(gymSession),
            bioimpedanceRecords = listOf(bioRecord),
            upcomingRaces = listOf(upcomingRace)
        )

        val json = AppBackupManager.serializeBackup(original)
        val parsedResult = AppBackupManager.parseBackup(json)
        assertTrue("Backup serializado deve ser restaurado sem erros", parsedResult.isSuccess)

        val restored = parsedResult.getOrNull()
        assertNotNull(restored)
        assertEquals(original.backupVersion, restored!!.backupVersion)
        assertEquals(profile.nome, restored.userProfile?.nome)
        assertEquals(profile.peso, restored.userProfile?.peso)
        assertEquals(1, restored.runningSessions.size)
        assertEquals(runSession.id, restored.runningSessions.first().id)
        assertEquals(runSession.kmSplitsJson, restored.runningSessions.first().kmSplitsJson)
        assertEquals(1, restored.workouts.size)
        assertEquals(workout.id, restored.workouts.first().id)
        assertEquals(1, restored.workoutHistory.size)
        assertEquals(historyEntry.totalWeight, restored.workoutHistory.first().totalWeight, 0.01f)
        assertEquals(1, restored.workoutSessions.size)
        assertEquals(workoutSessionEntity.id, restored.workoutSessions.first().id)
        assertEquals(1, restored.gymSessions.size)
        assertEquals(gymSession.exercises.first().name, restored.gymSessions.first().exercises.first().name)
        assertEquals(1, restored.bioimpedanceRecords.size)
        assertEquals(bioRecord.weightKg, restored.bioimpedanceRecords.first().weightKg, 0.01f)
        assertEquals(1, restored.upcomingRaces.size)
        assertEquals(upcomingRace.raceName, restored.upcomingRaces.first().raceName)

        // Validação defensiva contra arquivos corrompidos ou estranhos
        assertTrue(AppBackupManager.parseBackup("").isFailure)
        assertTrue(AppBackupManager.parseBackup("{corrompido").isFailure)
        assertTrue(AppBackupManager.parseBackup("[1,2,3]").isFailure)
        assertTrue(AppBackupManager.parseBackup("""{"foo":"bar"}""").isFailure)
    }
}
