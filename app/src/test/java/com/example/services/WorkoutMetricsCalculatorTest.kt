package com.example.services

import com.example.engine.RunningBlock
import com.example.engine.RunningBlockType
import com.example.engine.RunningWorkoutPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutMetricsCalculatorTest {

    @Test
    fun testConstantTrail3mps_splitsAreEqual() {
        val plan = RunningWorkoutPlan(
            weekNumber = 1,
            sessionType = "CONTINUOUS",
            title = "Contínuo",
            description = "1000m",
            blocks = listOf(RunningBlock(RunningBlockType.RUN, 333, "Corrida"))
        )
        val result = WorkoutMetricsCalculator.calculateMetrics(
            plan = plan,
            skippedIndices = emptySet(),
            totalElapsedSec = 333,
            totalMeasuredDistanceMeters = 1000.0,
            splitIntervalKm = 1.0f
        )

        assertTrue("Deve conter parciais", result.partialSplits.isNotEmpty())
        val firstSplit = result.partialSplits.first()
        assertEquals(1000.0, firstSplit.distanceMeters, 10.0)
    }

    @Test
    fun testBlocksSeparation_evolutionExcludesWarmupAndWalk() {
        val plan = RunningWorkoutPlan(
            weekNumber = 1,
            sessionType = "INTERVAL",
            title = "Intervalado",
            description = "Aquecimento + Tiros + Caminhadas",
            blocks = listOf(
                RunningBlock(RunningBlockType.WARMUP, 300, "Aquecimento"),
                RunningBlock(RunningBlockType.RUN, 60, "Tiro 1"),
                RunningBlock(RunningBlockType.WALK, 90, "Caminhada"),
                RunningBlock(RunningBlockType.RUN, 60, "Tiro 2")
            )
        )

        val result = WorkoutMetricsCalculator.calculateMetrics(
            plan = plan,
            skippedIndices = emptySet(),
            totalElapsedSec = 510,
            totalMeasuredDistanceMeters = 600.0,
            splitIntervalKm = -1.0f
        )

        assertEquals(120, result.evolutionDurationSec)
        assertEquals(510, result.cargaDurationSec)
        assertTrue(result.recuperationAvgPaceSecPerKm != null)
    }

    @Test
    fun testStaticWarmup_gpsStationary_durationCountsInCargaDistance0() {
        val plan = RunningWorkoutPlan(
            weekNumber = 1,
            sessionType = "INTERVAL",
            title = "Aquecimento estático",
            description = "Aquecimento parado",
            blocks = listOf(
                RunningBlock(RunningBlockType.WARMUP, 300, "Aquecimento Estático"),
                RunningBlock(RunningBlockType.RUN, 300, "Corrida")
            )
        )

        val result = WorkoutMetricsCalculator.calculateMetrics(
            plan = plan,
            skippedIndices = emptySet(),
            totalElapsedSec = 600,
            totalMeasuredDistanceMeters = 900.0,
            splitIntervalKm = 1.0f
        )

        assertEquals(600, result.cargaDurationSec)
        assertEquals(300, result.evolutionDurationSec)
    }
}
