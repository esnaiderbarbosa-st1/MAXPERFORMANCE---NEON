package com.example.services

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class GpsEvaluatorTest {

    private fun offsetLatLon(baseLat: Double, baseLon: Double, deltaMetersNorth: Double, deltaMetersEast: Double): Pair<Double, Double> {
        val latOffset = deltaMetersNorth / 111000.0
        val lonOffset = deltaMetersEast / (111000.0 * Math.cos(Math.toRadians(baseLat)))
        return Pair(baseLat + latOffset, baseLon + lonOffset)
    }

    @Test
    fun testStationary120sWithNoiseAndDopplerSpeed0_distanceUnder10m() {
        val startLat = -23.550520
        val startLon = -46.633308
        val baseNanos = 1_000_000_000_000L

        var anchor: GpsPoint? = null
        var totalDistance = 0.0

        for (sec in 0..120) {
            val currentNanos = baseNanos + (sec * 1_000_000_000L)
            val noiseNorth = if (sec % 2 == 0) 3.5 else -3.5
            val noiseEast = if (sec % 3 == 0) 2.5 else -2.5
            val (lat, lon) = offsetLatLon(startLat, startLon, noiseNorth, noiseEast)

            val point = GpsPoint(
                latitude = lat,
                longitude = lon,
                accuracyMeters = 8.0f,
                elapsedRealtimeNanos = currentNanos,
                speedMps = 0.0f
            )

            val result = GpsEvaluator.evaluatePoint(
                anchor = anchor,
                newPoint = point,
                currentNanos = currentNanos,
                isPaused = false
            )

            if (result is EvaluationResult.Accepted) {
                totalDistance += result.distanceAddedMeters
                anchor = result.newAnchor
            }
        }

        println("[TEST REPORT] Parado 120s (ruído +-4m, speed 0m/s): Distância medida = %.2f m (Requisito: <= 10m)".format(totalDistance))
        assertTrue("Distância medida ($totalDistance m) deve ser <= 10 m", totalDistance <= 10.0)
    }

    @Test
    fun testStraightWalk1_3mpsFor600s_noise3m_errorUnder10Percent() {
        val startLat = -23.550520
        val startLon = -46.633308
        val baseNanos = 1_000_000_000_000L
        val trueDistanceMeters = 1.3 * 600.0 // 780 meters

        var anchor: GpsPoint? = null
        var totalDistance = 0.0

        for (sec in 0..600) {
            val currentNanos = baseNanos + (sec * 1_000_000_000L)
            val walkDistanceMeters = 1.3 * sec
            val noiseNorth = if (sec % 2 == 0) 1.5 else -1.5
            val (lat, lon) = offsetLatLon(startLat, startLon, walkDistanceMeters + noiseNorth, 0.0)

            val point = GpsPoint(
                latitude = lat,
                longitude = lon,
                accuracyMeters = 10.0f,
                elapsedRealtimeNanos = currentNanos,
                speedMps = 1.3f
            )

            val result = GpsEvaluator.evaluatePoint(
                anchor = anchor,
                newPoint = point,
                currentNanos = currentNanos,
                isPaused = false
            )

            if (result is EvaluationResult.Accepted) {
                totalDistance += result.distanceAddedMeters
                anchor = result.newAnchor
            }
        }

        val errorPercent = (abs(totalDistance - trueDistanceMeters) / trueDistanceMeters) * 100.0
        println("[TEST REPORT] Caminhada 1.3 m/s por 600s: Distância real = %.1f m, Medida = %.2f m, Erro = %.2f%% (Requisito: <= 10%%)"
            .format(trueDistanceMeters, totalDistance, errorPercent))
        assertTrue("Erro de medição (%.2f%%) deve ser <= 10%%".format(errorPercent), errorPercent <= 10.0)
    }

    @Test
    fun testRun3mpsFor300s_interval2s_errorUnder5Percent() {
        val startLat = -23.550520
        val startLon = -46.633308
        val baseNanos = 1_000_000_000_000L
        val trueDistanceMeters = 3.0 * 300.0 // 900 meters

        var anchor: GpsPoint? = null
        var totalDistance = 0.0

        for (sec in 0..300 step 2) {
            val currentNanos = baseNanos + (sec * 1_000_000_000L)
            val runDistanceMeters = 3.0 * sec
            val (lat, lon) = offsetLatLon(startLat, startLon, runDistanceMeters, 0.0)

            val point = GpsPoint(
                latitude = lat,
                longitude = lon,
                accuracyMeters = 5.0f,
                elapsedRealtimeNanos = currentNanos,
                speedMps = 3.0f
            )

            val result = GpsEvaluator.evaluatePoint(
                anchor = anchor,
                newPoint = point,
                currentNanos = currentNanos,
                isPaused = false
            )

            if (result is EvaluationResult.Accepted) {
                totalDistance += result.distanceAddedMeters
                anchor = result.newAnchor
            }
        }

        val errorPercent = (abs(totalDistance - trueDistanceMeters) / trueDistanceMeters) * 100.0
        println("[TEST REPORT] Corrida 3.0 m/s por 300s: Distância real = %.1f m, Medida = %.2f m, Erro = %.2f%% (Requisito: <= 5%%)"
            .format(trueDistanceMeters, totalDistance, errorPercent))
        assertTrue("Erro de medição (%.2f%%) deve ser <= 5%%".format(errorPercent), errorPercent <= 5.0)
    }

    @Test
    fun testIsolated500mJumpIn1s_isIgnored() {
        val startLat = -23.550520
        val startLon = -46.633308
        val baseNanos = 1_000_000_000_000L

        val p1 = GpsPoint(startLat, startLon, 5f, baseNanos, 3f)
        val anchorResult = GpsEvaluator.evaluatePoint(null, p1, baseNanos, false)
        assertTrue(anchorResult is EvaluationResult.Accepted)
        val anchor = (anchorResult as EvaluationResult.Accepted).newAnchor

        val (jumpLat, jumpLon) = offsetLatLon(startLat, startLon, 500.0, 0.0)
        val jumpNanos = baseNanos + 1_000_000_000L
        val jumpPoint = GpsPoint(jumpLat, jumpLon, 5f, jumpNanos, 3f)

        val result = GpsEvaluator.evaluatePoint(anchor, jumpPoint, jumpNanos, false, recordedPointsCount = 5)
        assertTrue("Salto de 500m em 1s deve ser rejeitado", result is EvaluationResult.Rejected)
        assertEquals("UNREALISTIC_SPEED", (result as EvaluationResult.Rejected).reason)
    }

    @Test
    fun testPointWithAccuracy40m_isIgnored() {
        val startLat = -23.550520
        val startLon = -46.633308
        val baseNanos = 1_000_000_000_000L

        val p1 = GpsPoint(startLat, startLon, 5f, baseNanos, 3f)
        val anchorResult = GpsEvaluator.evaluatePoint(null, p1, baseNanos, false)
        val anchor = (anchorResult as EvaluationResult.Accepted).newAnchor

        val (nextLat, nextLon) = offsetLatLon(startLat, startLon, 10.0, 0.0)
        val nextNanos = baseNanos + 2_000_000_000L
        val badPoint = GpsPoint(nextLat, nextLon, 40.0f, nextNanos, 3f)

        val result = GpsEvaluator.evaluatePoint(anchor, badPoint, nextNanos, false)
        assertTrue("Ponto com precisão de 40m deve ser rejeitado", result is EvaluationResult.Rejected)
        assertEquals("POOR_ACCURACY", (result as EvaluationResult.Rejected).reason)
    }

    @Test
    fun testWalkedSegmentDuringPause_doesNotAddDistanceAfterResume() {
        val startLat = -23.550520
        val startLon = -46.633308
        var currentNanos = 1_000_000_000_000L

        var anchor: GpsPoint? = null
        var totalDistance = 0.0

        for (sec in 0..30 step 2) {
            currentNanos += 2_000_000_000L
            val dist = (100.0 / 30.0) * sec
            val (lat, lon) = offsetLatLon(startLat, startLon, dist, 0.0)
            val point = GpsPoint(lat, lon, 5f, currentNanos, 3.3f)
            val res = GpsEvaluator.evaluatePoint(anchor, point, currentNanos, isPaused = false)
            if (res is EvaluationResult.Accepted) {
                totalDistance += res.distanceAddedMeters
                anchor = res.newAnchor
            }
        }

        val distanceBeforePause = totalDistance

        for (sec in 1..60 step 2) {
            currentNanos += 2_000_000_000L
            val walkDistDuringPause = 100.0 + (200.0 / 60.0) * sec
            val (lat, lon) = offsetLatLon(startLat, startLon, walkDistDuringPause, 0.0)
            val point = GpsPoint(lat, lon, 5f, currentNanos, 1.5f)
            val res = GpsEvaluator.evaluatePoint(anchor, point, currentNanos, isPaused = true)
            if (res is EvaluationResult.Accepted) {
                totalDistance += res.distanceAddedMeters
                anchor = res.newAnchor
            }
        }

        assertEquals("Distância não deve mudar durante a pausa", distanceBeforePause, totalDistance, 0.001)

        // RESUME: anchor is reset
        anchor = null

        val newStartLatLon = offsetLatLon(startLat, startLon, 300.0, 0.0)
        for (sec in 0..30 step 2) {
            currentNanos += 2_000_000_000L
            val distFromNewStart = (100.0 / 30.0) * sec
            val (lat, lon) = offsetLatLon(newStartLatLon.first, newStartLatLon.second, distFromNewStart, 0.0)
            val point = GpsPoint(lat, lon, 5f, currentNanos, 3.3f)
            val res = GpsEvaluator.evaluatePoint(anchor, point, currentNanos, isPaused = false)
            if (res is EvaluationResult.Accepted) {
                totalDistance += res.distanceAddedMeters
                anchor = res.newAnchor
            }
        }

        println("[TEST REPORT] Pausa: Distância antes da pausa = %.1f m, Distância total pós-retomada = %.1f m (Soma apenas trechos ativos)"
            .format(distanceBeforePause, totalDistance))
        assertTrue("Trecho percorrido durante a pausa foi descartado corretamente", abs(totalDistance - 200.0) < 10.0)
    }

    @Test
    fun testJump500mIn1sWithDoppler3mpsAndRecordedPoints5_isRejectedAsUnrealisticSpeed() {
        val startLat = -23.550520
        val startLon = -46.633308
        val sessionStartNanos = 1_000_000_000_000L
        val anchorNanos = sessionStartNanos + 60_000_000_000L

        val anchor = GpsPoint(startLat, startLon, 5f, anchorNanos, 3f)
        val (jumpLat, jumpLon) = offsetLatLon(startLat, startLon, 500.0, 0.0)
        val jumpNanos = anchorNanos + 1_000_000_000L
        val jumpPoint = GpsPoint(jumpLat, jumpLon, 5f, jumpNanos, 3f)

        val result = GpsEvaluator.evaluatePoint(
            anchor = anchor,
            newPoint = jumpPoint,
            currentNanos = jumpNanos,
            isPaused = false,
            sessionStartNanos = sessionStartNanos,
            recordedPointsCount = 5
        )
        assertTrue("Salto de 500m em 1s com Doppler 3 m/s deve ser rejeitado", result is EvaluationResult.Rejected)
        assertEquals("UNREALISTIC_SPEED", (result as EvaluationResult.Rejected).reason)
    }

    @Test
    fun testLegitimateFastMovement7mpsFor2s_isAccepted() {
        val startLat = -23.550520
        val startLon = -46.633308
        val sessionStartNanos = 1_000_000_000_000L
        val anchorNanos = sessionStartNanos + 60_000_000_000L

        val anchor = GpsPoint(startLat, startLon, 5f, anchorNanos, 7f)
        val (nextLat, nextLon) = offsetLatLon(startLat, startLon, 14.0, 0.0)
        val nextNanos = anchorNanos + 2_000_000_000L
        val fastPoint = GpsPoint(nextLat, nextLon, 5f, nextNanos, 7f)

        val result = GpsEvaluator.evaluatePoint(
            anchor = anchor,
            newPoint = fastPoint,
            currentNanos = nextNanos,
            isPaused = false,
            sessionStartNanos = sessionStartNanos,
            recordedPointsCount = 5
        )
        assertTrue("Movimento legítimo rápido (7 m/s por 2s, ~14m) deve ser aceito", result is EvaluationResult.Accepted)
        val accepted = result as EvaluationResult.Accepted
        assertTrue("Distância adicionada deve ser próxima de 14m", abs(accepted.distanceAddedMeters - 14.0) < 1.0)
    }

    @Test
    fun testSignalLossRecovery100mIn30s_isAccepted() {
        val startLat = -23.550520
        val startLon = -46.633308
        val sessionStartNanos = 1_000_000_000_000L
        val anchorNanos = sessionStartNanos + 60_000_000_000L

        val anchor = GpsPoint(startLat, startLon, 5f, anchorNanos, 3.3f)
        val (nextLat, nextLon) = offsetLatLon(startLat, startLon, 100.0, 0.0)
        val nextNanos = anchorNanos + 30_000_000_000L
        val recoveredPoint = GpsPoint(nextLat, nextLon, 5f, nextNanos, 3.3f)

        val result = GpsEvaluator.evaluatePoint(
            anchor = anchor,
            newPoint = recoveredPoint,
            currentNanos = nextNanos,
            isPaused = false,
            sessionStartNanos = sessionStartNanos,
            recordedPointsCount = 5
        )
        assertTrue("Retorno após perda de sinal (100m em 30s) deve ser aceito", result is EvaluationResult.Accepted)
        val accepted = result as EvaluationResult.Accepted
        assertTrue("Distância adicionada deve ser próxima de 100m", abs(accepted.distanceAddedMeters - 100.0) < 2.0)
    }

    @Test
    fun testSlidingWindowPaceUsesTotalDistanceOverTotalTime_notArithmeticSpeedAverage() {
        val window = mutableListOf<PaceWindowSample>()

        // 3 segmentos curtos de 1s a 4.0 m/s (distância = 4.0m cada -> 12.0m em 3.0s)
        GpsEvaluator.calculatePaceSecPerKm(4.0, 1.0, window)
        GpsEvaluator.calculatePaceSecPerKm(4.0, 1.0, window)
        GpsEvaluator.calculatePaceSecPerKm(4.0, 1.0, window)

        // 2 segmentos longos de 5s a 2.0 m/s (distância = 10.0m cada -> 20.0m em 10.0s)
        GpsEvaluator.calculatePaceSecPerKm(10.0, 5.0, window)
        val paceSecPerKm = GpsEvaluator.calculatePaceSecPerKm(10.0, 5.0, window)

        // Velocidade física real da janela = (12 + 20) / (3 + 10) = 32 / 13 = 2.461538... m/s
        // Pace real = round(1000 / (32/13)) = round(406.25) = 406 s/km (6'46"/km)
        // A média aritmética antiga ((4+4+4+2+2)/5 = 3.2 m/s) distorceria para 312 s/km (5'12"/km)
        assertEquals(5, window.size)
        assertEquals(406, paceSecPerKm)
    }

    @Test
    fun testSlidingWindowPaceEvictsOldestSampleBeyondMaxWindow() {
        val window = mutableListOf<PaceWindowSample>()

        // 3 segmentos a 4.0 m/s (2s cada = 8m) + 2 segmentos a 2.0 m/s (2s cada = 4m)
        repeat(3) { GpsEvaluator.calculatePaceSecPerKm(8.0, 2.0, window) }
        repeat(2) { GpsEvaluator.calculatePaceSecPerKm(4.0, 2.0, window) }
        // Total = (24 + 8) / 10 = 3.2 m/s -> round(1000 / 3.2) = 313 s/km
        val paceBeforeEviction = GpsEvaluator.calculatePaceSecPerKm(4.0, 2.0, window)
        // Após a 6ª amostra (expulsa 1 amostra de 8m/2s): restam 2x(8m,2s) + 3x(4m,2s) = 28m / 10s = 2.8 m/s
        // Pace real = round(1000 / 2.8) = 357 s/km
        assertEquals(5, window.size)
        assertEquals(357, paceBeforeEviction)
    }
}

