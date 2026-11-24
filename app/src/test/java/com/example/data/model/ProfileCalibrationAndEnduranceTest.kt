package com.example.data.model

import com.example.domain.recomp.BioimpedanceReferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileCalibrationAndEnduranceTest {

    private fun buildTestDossier(
        avgHr: Int = 152,
        maxHr: Int = 168,
        effectiveMaxHr: Int = 0,
        activeKcal: Int = 300,
        sampleCount: Int = 10,
        calibratedLt1: Int? = null,
        calibratedLt2: Int? = null
    ): RunPhysiologyDossier {
        val samples = (0 until sampleCount).map { idx ->
            RunTelemetrySample(
                elapsedSeconds = idx * 30,
                paceSecPerKm = 360f,
                heartRateBpm = 140 + (idx % 30),
                cadenceSpm = 165,
                strideLengthCm = 100
            )
        }
        return RunPhysiologyDossier(
            badgeTitle = "MAX RUN | Teste",
            athleteName = "Atleta Teste",
            formattedDateTime = "01/11/2026 • 07:00",
            distanceKm = 5.0f,
            totalDistanceWithWarmupKm = 5.0f,
            hasMainRunIsolation = false,
            durationSeconds = 1800,
            totalDurationWithWarmupSeconds = 1800,
            formattedDurationHhMmSs = "00:30:00",
            activeCaloriesKcal = activeKcal,
            basalCaloriesKcal = 35,
            totalCaloriesKcal = activeKcal + 35,
            avgPaceSecPerKm = 360,
            maxPaceSecPerKm = 330,
            formattedAvgPace = "6'00\"",
            formattedMaxPace = "5'30\"",
            avgHeartRateBpm = avgHr,
            maxHeartRateBpm = maxHr,
            effectiveMaxHeartRateBpm = effectiveMaxHr,
            calibratedLt1Bpm = calibratedLt1,
            calibratedLt2Bpm = calibratedLt2,
            isHrEstimated = false,
            totalSteps = 4500,
            avgCadenceSpm = 165,
            maxCadenceSpm = 175,
            avgStrideLengthCm = 100,
            telemetrySamples = samples,
            hrZones5 = emptyList(),
            kmSplits = emptyList()
        )
    }

    @Test
    fun calculateEffectiveMaxHr_respectsCalibrationAgeAndUnavailablePrecedence() {
        // 1. Calibração explícita prevalece sobre idade e picos
        assertEquals(
            190,
            calculateEffectiveMaxHr(
                ageYears = 50,
                sessionMaxHrBpm = 172,
                historicalPeakHrBpm = 180,
                calibratedMaxHrBpm = 190
            )
        )
        assertEquals(
            188,
            calculateEffectiveMaxHr(
                ageYears = null,
                sessionMaxHrBpm = 160,
                calibratedMaxHrBpm = 188
            )
        )

        // 2. Apenas idade válida (sem 190 hardcoded para 50 anos)
        assertEquals(170, calculateEffectiveMaxHr(ageYears = 50))
        assertEquals(180, calculateEffectiveMaxHr(ageYears = 40))

        // 3. Pico observado acima da fórmula etária eleva apenas peak + 4 (sem piso 190)
        assertEquals(
            180,
            calculateEffectiveMaxHr(ageYears = 50, sessionMaxHrBpm = 176)
        )

        // 4. Sem calibração e sem idade válida: retorna 0 mesmo com pico observado
        assertEquals(0, calculateEffectiveMaxHr(ageYears = null, sessionMaxHrBpm = 178, historicalPeakHrBpm = 182))
        assertEquals(0, calculateEffectiveMaxHr(ageYears = 0, sessionMaxHrBpm = 175))
    }

    @Test
    fun hasPersonalGoals_dependsOnMetaFieldsNotName() {
        val esnaiderWithoutMetas = UserProfileData(nome = "Esnaider")
        assertFalse(BioimpedanceReferences.hasPersonalGoals(esnaiderWithoutMetas))

        val otherWithFatGoal = UserProfileData(nome = "Maria", metaPercentualGordura = 20.0f)
        assertTrue(BioimpedanceReferences.hasPersonalGoals(otherWithFatGoal))

        val otherWithMuscleGoal = UserProfileData(nome = "Joao", metaPercentualMusculo = 72.0f)
        assertTrue(BioimpedanceReferences.hasPersonalGoals(otherWithMuscleGoal))

        val otherWithWaterGoal = UserProfileData(nome = "Ana", metaAguaLitros = 3.0f)
        assertTrue(BioimpedanceReferences.hasPersonalGoals(otherWithWaterGoal))

        assertTrue(BioimpedanceReferences.hasPersonalGoals(UserProfileData.PRESET_ESNAIDER))
        assertFalse(BioimpedanceReferences.hasPersonalGoals(UserProfileData.PRESET_ALYNE))
        assertFalse(BioimpedanceReferences.hasPersonalGoals(UserProfileData()))
        assertFalse(BioimpedanceReferences.hasPersonalGoals(null))
    }

    @Test
    fun calculateEndurancePhysiologyProfile_withoutMaxHr_producesNoThresholdsOrZones() {
        val dossier = buildTestDossier(avgHr = 150, maxHr = 165, effectiveMaxHr = 0, sampleCount = 12)
        val result = calculateEndurancePhysiologyProfile(dossier)

        assertFalse(result.isAvailable)
        assertFalse(result.hasZoneDistribution)
        assertEquals(0, result.calibratedMaxHrBpm)
        assertEquals(0, result.lt1Bpm)
        assertEquals(0, result.lt2Bpm)
        assertEquals(0, result.belowLt1Pct)
        assertEquals(0, result.betweenLt1Lt2Pct)
        assertEquals(0, result.aboveLt2Pct)
        assertEquals(0, result.carbOxidationPct)
        assertEquals(0, result.fatOxidationPct)
    }

    @Test
    fun calculateEndurancePhysiologyProfile_withFewSamplesOrZeroKcal_doesNotFabricateZonesOrGrams() {
        val fewSamplesDossier = buildTestDossier(
            avgHr = 152,
            maxHr = 168,
            effectiveMaxHr = 180,
            activeKcal = 0,
            sampleCount = 4
        )
        val fewResult = calculateEndurancePhysiologyProfile(fewSamplesDossier)
        assertTrue(fewResult.isAvailable)
        assertFalse(fewResult.hasZoneDistribution)
        assertFalse(fewResult.hasCalorieEstimate)
        assertEquals(0, fewResult.belowLt1Pct)
        assertEquals(0, fewResult.betweenLt1Lt2Pct)
        assertEquals(0, fewResult.aboveLt2Pct)
        assertEquals(0, fewResult.carbGramsEstimated)
        assertEquals(0, fewResult.fatGramsEstimated)

        val zeroKcalDossier = buildTestDossier(
            avgHr = 152,
            maxHr = 168,
            effectiveMaxHr = 180,
            activeKcal = 0,
            sampleCount = 10
        )
        val zeroKcalResult = calculateEndurancePhysiologyProfile(zeroKcalDossier)
        assertTrue(zeroKcalResult.isAvailable)
        assertTrue(zeroKcalResult.hasZoneDistribution)
        assertFalse(zeroKcalResult.hasCalorieEstimate)
        assertEquals(0, zeroKcalResult.carbGramsEstimated)
        assertEquals(0, zeroKcalResult.fatGramsEstimated)
    }

    @Test
    fun userProfileData_defaultValuesAreNeutral() {
        val neutral = UserProfileData()
        assertEquals("", neutral.nome)
        assertEquals("", neutral.sexo)
        assertEquals(0, neutral.idade)
        assertEquals(0, neutral.altura)
        assertEquals(0f, neutral.peso, 0.001f)
        assertEquals(0, neutral.tmb)
        assertNull(neutral.heightCm)
        assertNull(neutral.weightKg)
        assertNull(neutral.baselinePaceMinPerKm)
        assertNull(neutral.fcMaxCalibradaBpm)
        assertNull(neutral.limiarLt1Bpm)
        assertNull(neutral.limiarLt2Bpm)
        assertNull(neutral.metaPercentualGordura)
        assertNull(neutral.metaPercentualMusculo)
        assertNull(neutral.metaAguaLitros)
    }
}
