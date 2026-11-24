package com.example.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RunningDeduplicationTest {

    @Test
    fun twoRunsWith1890mAnd12MinOnDifferentDates_areNotDeduplicated() {
        val day1 = 1_793_372_700_000L // 30/10/2026
        val day2 = day1 + 7L * 24L * 3600_000L // +7 dias

        val run1 = RunningSession(
            id = "real_run_day1",
            date = day1,
            weekNumber = 0,
            dayNumber = 1,
            sessionType = "FREE_RUN",
            durationSec = 720,
            distanceMeters = 1890,
            avgPaceSecPerKm = 381,
            avgHr = null,
            maxHr = null
        )
        val run2 = RunningSession(
            id = "real_run_day2",
            date = day2,
            weekNumber = 0,
            dayNumber = 1,
            sessionType = "FREE_RUN",
            durationSec = 726,
            distanceMeters = 1890,
            avgPaceSecPerKm = 384,
            avgHr = 152,
            maxHr = 168
        )

        val (kept, removedIds) = deduplicateRunningSessions(listOf(run1, run2))

        assertEquals(2, kept.size)
        assertTrue("Não deve marcar nenhuma corrida em datas diferentes como duplicada", removedIds.isEmpty())
        assertEquals(listOf("real_run_day2", "real_run_day1"), kept.map { it.id })
    }

    @Test
    fun runWith1890mAnd12MinWithoutHr_keepsNullHrWithoutFabricating160Or176() {
        val baseDate = 1_793_500_000_000L
        val realRunWithoutHr = RunningSession(
            id = "real_run_no_hr",
            date = baseDate,
            weekNumber = 2,
            dayNumber = 1,
            sessionType = "FREE_RUN",
            durationSec = 726,
            distanceMeters = 1890,
            avgPaceSecPerKm = 384,
            avgHr = null,
            maxHr = null
        )
        val seedRunWithoutHr = RunningSession(
            id = "run_seed_2026_10_30_free_run",
            date = baseDate + 86_400_000L,
            weekNumber = 1,
            dayNumber = 1,
            sessionType = "FREE_RUN",
            durationSec = 726,
            distanceMeters = 1890,
            avgPaceSecPerKm = 384,
            avgHr = null,
            maxHr = null
        )

        val (kept, removedIds) = deduplicateRunningSessions(listOf(realRunWithoutHr, seedRunWithoutHr))

        assertEquals(2, kept.size)
        assertTrue(removedIds.isEmpty())

        val keptReal = kept.first { it.id == "real_run_no_hr" }
        assertNull("avgHr deve permanecer null sem leitura real", keptReal.avgHr)
        assertNull("maxHr deve permanecer null sem leitura real", keptReal.maxHr)
        assertEquals(2, keptReal.weekNumber)

        val keptSeed = kept.first { it.id == "run_seed_2026_10_30_free_run" }
        assertNull("avgHr não deve receber 160 fabricado mesmo no id seed", keptSeed.avgHr)
        assertNull("maxHr não deve receber 176 fabricado mesmo no id seed", keptSeed.maxHr)
    }

    @Test
    fun realDuplicatesWithinDateDurationAndDistanceTolerances_areStillMerged() {
        val baseDate = 1_794_000_000_000L
        val primaryWithRoute = RunningSession(
            id = "run_primary",
            date = baseDate,
            weekNumber = 3,
            dayNumber = 2,
            sessionType = "CONTINUOUS",
            durationSec = 1500,
            distanceMeters = 5000,
            avgPaceSecPerKm = 300,
            avgHr = null,
            maxHr = null,
            routePointsJson = "[{\"lat\":-9.9,\"lng\":-67.8}]",
            kmSplitsJson = null,
            notes = null
        )
        val duplicateWithHrAndNotes = RunningSession(
            id = "run_duplicate",
            date = baseDate + 90_000L, // +1m30s (dentro de ±2 min)
            weekNumber = 3,
            dayNumber = 2,
            sessionType = "CONTINUOUS",
            durationSec = 1504, // +4s (dentro de ±5 s)
            distanceMeters = 5020, // +20m (dentro de ±25 m)
            avgPaceSecPerKm = 300,
            avgHr = 155,
            maxHr = 171,
            routePointsJson = null,
            kmSplitsJson = "[{\"splitNumber\":1}]",
            notes = "Treino forte"
        )

        val (kept, removedIds) = deduplicateRunningSessions(listOf(primaryWithRoute, duplicateWithHrAndNotes))

        assertEquals(1, kept.size)
        assertEquals(1, removedIds.size)

        val merged = kept.first()
        assertEquals(155, merged.avgHr)
        assertEquals(171, merged.maxHr)
        assertEquals("[{\"lat\":-9.9,\"lng\":-67.8}]", merged.routePointsJson)
        assertEquals("[{\"splitNumber\":1}]", merged.kmSplitsJson)
        assertEquals("Treino forte", merged.notes)
    }

    @Test
    fun mergedDuplicatesWithoutHr_remainNullHr() {
        val baseDate = 1_794_100_000_000L
        val a = RunningSession(
            id = "dup_no_hr_a",
            date = baseDate,
            durationSec = 725,
            distanceMeters = 1890,
            avgPaceSecPerKm = 384,
            avgHr = null,
            maxHr = null
        )
        val b = RunningSession(
            id = "dup_no_hr_b",
            date = baseDate + 30_000L,
            durationSec = 726,
            distanceMeters = 1895,
            avgPaceSecPerKm = 384,
            avgHr = null,
            maxHr = null
        )

        val (kept, removedIds) = deduplicateRunningSessions(listOf(a, b))

        assertEquals(1, kept.size)
        assertEquals(1, removedIds.size)
        assertNull(kept.first().avgHr)
        assertNull(kept.first().maxHr)
    }

    @Test
    fun runsSeparatedByMoreThanTwoMinutes_areNotMerged() {
        val baseDate = 1_794_200_000_000L
        val a = RunningSession(
            id = "run_a",
            date = baseDate,
            durationSec = 720,
            distanceMeters = 1890,
            avgPaceSecPerKm = 381
        )
        val b = RunningSession(
            id = "run_b",
            date = baseDate + 125_000L, // > 120_000ms
            durationSec = 720,
            distanceMeters = 1890,
            avgPaceSecPerKm = 381
        )

        val (kept, removedIds) = deduplicateRunningSessions(listOf(a, b))

        assertEquals(2, kept.size)
        assertTrue(removedIds.isEmpty())
    }
}
