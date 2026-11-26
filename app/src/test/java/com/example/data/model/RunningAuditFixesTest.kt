package com.example.data.model

import com.example.ui.components.HybridReadinessEngine
import com.example.ui.components.RunnerAnamnesisManager
import com.example.ui.components.RunnerAnamnesisProfile
import com.example.utils.StepCadenceTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Testes unitários JVM puros (sem Robolectric) para os ajustes médios de corrida do Lote C5c:
 * - Limiar dinâmico de alerta de voz de FC baseado em `fcMaxCalibradaBpm` (95% da FCmáx efetiva)
 * - Respeito a `fcMaxCalibradaBpm` no Raio-X de Prontidão (`HybridReadinessEngine.evaluate`) e calibração
 * - Bloqueio de contagem de passos durante pausa em `StepCadenceTracker`
 */
class RunningAuditFixesTest {

    @Test
    fun highHrVoiceAlertThreshold_withCalibratedMaxHr176_triggersBefore185Bpm() {
        val calibratedMaxHr = 176
        val threshold = calculateHighHrVoiceAlertThresholdBpm(
            calibratedMaxHrBpm = calibratedMaxHr,
            ageYears = 30
        )

        // 95% de 176 = 167.2 -> 167 bpm (muito abaixo do antigo valor fixo de 185 bpm)
        assertEquals(167, threshold)
        assertTrue("Limiar dinâmico deve ser inferior a 185 bpm", threshold < 185)

        // Abaixo ou igual ao limiar não dispara; acima de 167 (e ainda abaixo de 185) já dispara
        assertFalse(shouldTriggerHighHrVoiceAlert(liveBpm = 167, calibratedMaxHrBpm = calibratedMaxHr, ageYears = 30))
        assertTrue(shouldTriggerHighHrVoiceAlert(liveBpm = 168, calibratedMaxHrBpm = calibratedMaxHr, ageYears = 30))
        assertTrue(shouldTriggerHighHrVoiceAlert(liveBpm = 175, calibratedMaxHrBpm = calibratedMaxHr, ageYears = 30))
    }

    @Test
    fun highHrVoiceAlertThreshold_withoutCalibration_fallsBackToAgeFormulaOrDefault185() {
        // Sem calibração e idade 40 -> FCmáx = 180 -> 95% = 171 bpm
        assertEquals(171, calculateHighHrVoiceAlertThresholdBpm(calibratedMaxHrBpm = null, ageYears = 40))
        assertFalse(shouldTriggerHighHrVoiceAlert(liveBpm = 171, calibratedMaxHrBpm = null, ageYears = 40))
        assertTrue(shouldTriggerHighHrVoiceAlert(liveBpm = 172, calibratedMaxHrBpm = null, ageYears = 40))

        // Sem calibração e sem idade válida -> fallback conservador de 185 bpm
        assertEquals(185, calculateHighHrVoiceAlertThresholdBpm(calibratedMaxHrBpm = null, ageYears = null))
        assertFalse(shouldTriggerHighHrVoiceAlert(liveBpm = 185, calibratedMaxHrBpm = null, ageYears = null))
        assertTrue(shouldTriggerHighHrVoiceAlert(liveBpm = 186, calibratedMaxHrBpm = null, ageYears = null))
    }

    @Test
    fun anamnesisCalibration_respectsCalibratedMaxHrBpmWhenProvided() {
        val summary = RunnerAnamnesisManager.calculateCalibration(
            profile = RunnerAnamnesisProfile(),
            athleteAge = 35,
            history = emptyList(),
            baselinePaceMinPerKm = 5.5f,
            calibratedMaxHrBpm = 176
        )

        assertEquals(176, summary.maxHrBpm)
    }

    @Test
    fun hybridReadinessRaioX_respectsCalibratedMaxHrBpmForZ4Z5Wear() {
        val now = System.currentTimeMillis()
        // Corrida de 25 min há 6 horas sustentando 162 bpm contínuos:
        // - Para FCmáx calibrada = 176 bpm: 162 bpm é ~92% (Z4/Z5) -> desgaste cardiovascular alto
        // - Para FCmáx calibrada = 205 bpm: 162 bpm é ~79% (Z3) -> desgaste cardiovascular menor
        val hrTimeline = List(60) { 162 }.joinToString(",")
        val recentRun = RunningSession(
            id = "run_raiox_test",
            date = now - 6L * 3600L * 1000L,
            weekNumber = 1,
            dayNumber = 1,
            durationSec = 1500,
            distanceMeters = 4500,
            avgPaceSecPerKm = 333,
            rpe = 6,
            avgHr = 162,
            maxHr = 166,
            hrTimeline = hrTimeline
        )

        val reportWith176 = HybridReadinessEngine.evaluate(
            gymHistory = emptyList(),
            gymSessions = emptyList(),
            runningHistory = listOf(recentRun),
            athleteAge = 30,
            calibratedMaxHrBpm = 176
        )
        val reportWith205 = HybridReadinessEngine.evaluate(
            gymHistory = emptyList(),
            gymSessions = emptyList(),
            runningHistory = listOf(recentRun),
            athleteAge = 30,
            calibratedMaxHrBpm = 205
        )

        assertTrue(
            "Com fcMaxCalibradaBpm=176, 162 bpm deve entrar em Z4/Z5 e elevar o desgaste cardiovascular",
            reportWith176.z4z5Minutes48h > reportWith205.z4z5Minutes48h
        )
        assertTrue(
            "Com fcMaxCalibradaBpm=176, o desgaste em Z4/Z5 deve acionar isHighCardioWear48h",
            reportWith176.isHighCardioWear48h
        )
        assertFalse(
            "Com fcMaxCalibradaBpm=205, 162 bpm permanece em Z3 e não aciona desgaste alto de Z4/Z5",
            reportWith205.isHighCardioWear48h
        )
        assertTrue(
            "Prontidão deve ser menor quando fcMaxCalibradaBpm=176 classifica a sessão em Z4/Z5",
            reportWith176.scorePct < reportWith205.scorePct
        )
    }

    @Test
    fun stepCadenceTracker_doesNotIncrementStepsWhilePaused() {
        StepCadenceTracker.reset()
        try {
            val baseTs = 1_000_000L
            StepCadenceTracker.setPaused(false)
            StepCadenceTracker.onStepDetectedAt(baseTs)
            StepCadenceTracker.onStepDetectedAt(baseTs + 400L)
            assertEquals(2, StepCadenceTracker.totalSteps.value)

            // Pausa: nenhum passo deve ser contabilizado e a cadência zera
            StepCadenceTracker.setPaused(true)
            assertEquals(0, StepCadenceTracker.currentCadenceSpm.value)
            StepCadenceTracker.onStepDetectedAt(baseTs + 800L)
            StepCadenceTracker.onStepDetectedAt(baseTs + 1200L)
            StepCadenceTracker.onStepDetectedAt(baseTs + 1600L)
            assertEquals(2, StepCadenceTracker.totalSteps.value)
            assertEquals(0, StepCadenceTracker.currentCadenceSpm.value)

            // Retoma: volta a contabilizar normalmente sem somar passos da pausa
            StepCadenceTracker.setPaused(false)
            StepCadenceTracker.onStepDetectedAt(baseTs + 5000L)
            assertEquals(3, StepCadenceTracker.totalSteps.value)
        } finally {
            StepCadenceTracker.reset()
        }
    }
}
