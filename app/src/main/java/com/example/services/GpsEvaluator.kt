package com.example.services

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class GpsPoint(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val elapsedRealtimeNanos: Long,
    val speedMps: Float? = null
)

data class PaceWindowSample(
    val distanceMeters: Double,
    val durationSec: Double
)

sealed class EvaluationResult {
    data class Accepted(val distanceAddedMeters: Double, val newAnchor: GpsPoint) : EvaluationResult()
    data class ColdStartAnchorReset(val newAnchor: GpsPoint, val reason: String) : EvaluationResult()
    data class Rejected(val reason: String) : EvaluationResult()
}

object GpsEvaluator {

    /** Idade máxima permitida para qualquer coordenada na largada ou durante o treino (5 segundos). */
    const val COLD_START_MAX_LOCATION_AGE_SEC = 5.0

    /** Precisão máxima exigida nos primeiros 30 segundos de treino (22,0 metros) para bloquear triangulação de antena celular. */
    const val COLD_START_MAX_ACCURACY_METERS = 22.0f

    /** Janela inicial de largada a frio (30 segundos em nanossegundos). */
    const val COLD_START_WINDOW_NANOS = 30_000_000_000L

    /** Distância de salto abrupto nos primeiros pontos que caracteriza "Cold-Start Cell-Tower Jump" (> 65m). */
    const val COLD_START_JUMP_DISTANCE_METERS = 65.0

    /** Velocidade implícita anômala de salto de largada (> 9,5 m/s). */
    const val COLD_START_JUMP_SPEED_MPS = 9.5

    fun distanceBetweenMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadiusMeters = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadiusMeters * c
    }

    /**
     * Avalia um novo ponto GPS aplicando proteção rigorosa contra "Ponto Fantasma de Largada a Frio"
     * (Cold-Start GPS / Cell-Tower Jump):
     * 1. Rejeita coordenadas com idade > 5 segundos antes do início da sessão ou antes do instante atual.
     * 2. Nos primeiros 30 segundos de sessão (ou para o ponto zero), rejeita qualquer ponto com `accuracy > 22.0f` metros.
     * 3. Caso o 2º ou 3º ponto apresente salto abrupto em relação ao 1º ponto (> 65m com velocidade implícita > 9.5 m/s
     *    ou transição brusca de precisão de > 40m para < 12m), emite `ColdStartAnchorReset` para descartar o 1º ponto
     *    fantasma e redefinir o ponto inicial da rota na via real.
     */
    fun evaluatePoint(
        anchor: GpsPoint?,
        newPoint: GpsPoint,
        currentNanos: Long,
        isPaused: Boolean,
        sessionStartNanos: Long = 0L,
        recordedPointsCount: Int = if (anchor == null) 0 else 1,
        isPhysicallyMoving: Boolean = com.example.utils.StepCadenceTracker.isPhysicallyMoving()
    ): EvaluationResult {
        if (isPaused) {
            return EvaluationResult.Rejected("PAUSED")
        }

        // 1. Bloqueio de idade da coordenada (> 5s antes de currentNanos ou > 5s antes do início da sessão)
        if (newPoint.elapsedRealtimeNanos > 0L) {
            if (currentNanos > 0L) {
                val ageFromNowSec = (currentNanos - newPoint.elapsedRealtimeNanos) / 1_000_000_000.0
                if (ageFromNowSec > COLD_START_MAX_LOCATION_AGE_SEC) {
                    return EvaluationResult.Rejected("OUTDATED_POINT")
                }
            }
            if (sessionStartNanos > 0L) {
                val ageBeforeSessionStartSec = (sessionStartNanos - newPoint.elapsedRealtimeNanos) / 1_000_000_000.0
                if (ageBeforeSessionStartSec > COLD_START_MAX_LOCATION_AGE_SEC) {
                    return EvaluationResult.Rejected("STALE_PRE_SESSION_LOCATION")
                }
            }
        }

        // 2. Gate de Precisão na Largada (primeiros 30s ou enquanto ainda não há âncora confiável)
        val isWithinColdStartWindow = sessionStartNanos <= 0L ||
            currentNanos <= 0L ||
            (currentNanos - sessionStartNanos) <= COLD_START_WINDOW_NANOS

        val maxAllowedAccuracy = if (anchor == null || isWithinColdStartWindow || recordedPointsCount < 3) {
            COLD_START_MAX_ACCURACY_METERS
        } else {
            GpsConfig.MAX_ACCURACY_METERS
        }

        if (newPoint.accuracyMeters <= 0f || newPoint.accuracyMeters > maxAllowedAccuracy) {
            return EvaluationResult.Rejected("POOR_ACCURACY")
        }

        // Primeiro ponto válido que passou nos filtros rigorosos de idade (<= 5s) e precisão (<= 22m)
        if (anchor == null) {
            return EvaluationResult.Accepted(distanceAddedMeters = 0.0, newAnchor = newPoint)
        }

        val distMeters = distanceBetweenMeters(
            anchor.latitude, anchor.longitude,
            newPoint.latitude, newPoint.longitude
        )

        val deltaTimeSec = if (newPoint.elapsedRealtimeNanos > 0L && anchor.elapsedRealtimeNanos > 0L) {
            (newPoint.elapsedRealtimeNanos - anchor.elapsedRealtimeNanos) / 1_000_000_000.0
        } else 0.0

        val implicitSpeedMps = if (deltaTimeSec > 0.0) distMeters / deltaTimeSec else 0.0
        val hasExplicitDoppler = newPoint.speedMps != null
        val dopplerSpeedMps = newPoint.speedMps?.toDouble() ?: 0.0
        val effectiveSpeedMps = maxOf(dopplerSpeedMps, implicitSpeedMps)

        // 3. Detecção de Salto de Largada a Frio no 2º ou 3º ponto ("Cold-Start Anchor Reset"):
        // Se nos primeiros pontos (recordedPointsCount in 1..2) houver um salto > 65m com velocidade implícita > 9.5 m/s
        // OU transição brusca de precisão (> 40m para < 12m, ou melhoria brusca com salto > 65m),
        // descartamos a âncora fantasma inicial e redefinimos o ponto zero da rota para este novo ponto preciso!
        if (recordedPointsCount in 1..2) {
            val isAbruptJumpSpeed = distMeters > COLD_START_JUMP_DISTANCE_METERS &&
                (implicitSpeedMps > COLD_START_JUMP_SPEED_MPS || deltaTimeSec <= 0.0)
            val isAccuracySnapToRoad = (anchor.accuracyMeters > 40.0f && newPoint.accuracyMeters < 12.0f) ||
                (distMeters > COLD_START_JUMP_DISTANCE_METERS && newPoint.accuracyMeters <= 12.0f && anchor.accuracyMeters > newPoint.accuracyMeters * 1.6f)

            if (isAbruptJumpSpeed || isAccuracySnapToRoad) {
                return EvaluationResult.ColdStartAnchorReset(
                    newAnchor = newPoint,
                    reason = "COLD_START_ANCHOR_REPLACED"
                )
            }
        }

        // Gate 1B: Bloqueio de drift estacionário quando o chip GPS informa Doppler explícito abaixo do limiar de movimento
        // (ex.: parado no semáforo com speedMps = 0.0f e ruído posicional <= max(12m, 1.5 * accuracy))
        if (hasExplicitDoppler &&
            dopplerSpeedMps < GpsConfig.MIN_SPEED_DOPPLER_MPS &&
            distMeters <= maxOf(12.0, newPoint.accuracyMeters.toDouble() * 1.5)
        ) {
            return EvaluationResult.Rejected("STATIONARY_DOPPLER_SPEED")
        }

        // Check stationary speed only when both Doppler and coordinate displacement speed are below threshold
        if (effectiveSpeedMps < GpsConfig.MIN_SPEED_DOPPLER_MPS && distMeters < 2.5) {
            return EvaluationResult.Rejected("STATIONARY_SPEED")
        }

        // Gate 2: Max Human Speed Gate (> 8.5 m/s = 30.6 km/h)
        if (implicitSpeedMps > GpsConfig.MAX_SPEED_MPS && (dopplerSpeedMps > GpsConfig.MAX_SPEED_MPS || dopplerSpeedMps == 0.0)) {
            return EvaluationResult.Rejected("UNREALISTIC_SPEED")
        }
        if (implicitSpeedMps > GpsConfig.ABSOLUTE_MAX_IMPLICIT_SPEED_MPS &&
            distMeters > GpsConfig.ABSOLUTE_JUMP_MIN_DISTANCE_METERS
        ) {
            return EvaluationResult.Rejected("UNREALISTIC_SPEED")
        }

        // Gate 3: Sensor Fusion (Zero-Cadence Stationary Lock)
        if (com.example.utils.StepCadenceTracker.hasStepSensor && !isPhysicallyMoving) {
            val maxStationaryDriftMeters = maxOf(
                GpsConfig.STATIONARY_DISPLACEMENT_THRESHOLD_METERS.toDouble(),
                newPoint.accuracyMeters.toDouble() * 1.2
            )
            if (distMeters < maxStationaryDriftMeters &&
                (!hasExplicitDoppler || dopplerSpeedMps < GpsConfig.STATIONARY_SPEED_THRESHOLD_MPS)
            ) {
                return EvaluationResult.Rejected("STATIONARY_ZERO_CADENCE_LOCK")
            }
        }

        // Gate 4: Minimum Displacement Gate (capped at 5.0m so medium accuracy doesn't freeze distance)
        val accuracyScaledMin = (GpsConfig.ACCURACY_DISPLACEMENT_FACTOR * newPoint.accuracyMeters).toDouble().coerceAtMost(5.0)
        val minRequiredDisplacement = maxOf(
            GpsConfig.MIN_DISPLACEMENT_METERS.toDouble(),
            accuracyScaledMin
        )

        if (distMeters < minRequiredDisplacement) {
            return EvaluationResult.Rejected("DISPLACEMENT_BELOW_THRESHOLD")
        }

        return EvaluationResult.Accepted(
            distanceAddedMeters = distMeters,
            newAnchor = newPoint.copy(speedMps = effectiveSpeedMps.toFloat())
        )
    }

    /**
     * Sanitiza retroativamente uma lista de `GpsPoint`, removendo os primeiros 1 a 3 pontos caso
     * apresentem um salto reto anômalo (`distância > 80 metros` entre dois pontos consecutivos
     * quando a distância média entre os demais pontos da sessão é `< 25 metros`).
     */
    fun sanitizeColdStartOutliers(points: List<GpsPoint>): List<GpsPoint> {
        if (points.size < 3) return points

        val segmentDistances = DoubleArray(points.size - 1) { i ->
            distanceBetweenMeters(
                points[i].latitude,
                points[i].longitude,
                points[i + 1].latitude,
                points[i + 1].longitude
            )
        }

        val maxPrefixIndex = minOf(3, segmentDistances.size)
        val tailSegments = if (segmentDistances.size > maxPrefixIndex) {
            segmentDistances.slice(maxPrefixIndex until segmentDistances.size)
        } else {
            segmentDistances.drop(1)
        }
        val avgTailDist = if (tailSegments.isNotEmpty()) tailSegments.average() else 0.0

        if (avgTailDist < 25.0) {
            var dropCount = 0
            for (i in 0 until maxPrefixIndex) {
                val segDist = segmentDistances[i]
                val isAccuracyJump = points[i].accuracyMeters > 40f && points[i + 1].accuracyMeters < 12f
                if (segDist > 80.0 || (segDist > 65.0 && isAccuracyJump)) {
                    dropCount = i + 1
                }
            }
            if (dropCount > 0 && points.size - dropCount >= 2) {
                return points.drop(dropCount)
            }
        }
        return points
    }

    fun determineStatus(
        lastValidNanos: Long?,
        lastAccuracyMeters: Float,
        currentNanos: Long,
        isTracking: Boolean,
        hasPermission: Boolean
    ): RunTrackerStatus {
        if (!hasPermission) return RunTrackerStatus.NO_PERMISSION
        if (!isTracking) return RunTrackerStatus.IDLE

        if (lastValidNanos == null) return RunTrackerStatus.SEARCHING

        val timeSinceLastValidSec = (currentNanos - lastValidNanos) / 1_000_000_000.0

        return when {
            timeSinceLastValidSec > GpsConfig.SEARCHING_SIGNAL_TIMEOUT_SEC -> RunTrackerStatus.SEARCHING
            timeSinceLastValidSec > GpsConfig.WEAK_SIGNAL_TIMEOUT_SEC -> RunTrackerStatus.WEAK
            lastAccuracyMeters <= GpsConfig.GOOD_ACCURACY_THRESHOLD -> RunTrackerStatus.GOOD
            else -> RunTrackerStatus.WEAK
        }
    }

    fun calculatePaceSecPerKm(
        deltaDistanceMeters: Double,
        deltaTimeSec: Double,
        recentSamples: MutableList<PaceWindowSample>,
        maxWindowSize: Int = 5
    ): Int? {
        if (deltaDistanceMeters <= 0.0 || deltaTimeSec <= 0.0) {
            if (recentSamples.isNotEmpty()) {
                recentSamples.clear()
            }
            return null
        }

        val segmentSpeedMps = deltaDistanceMeters / deltaTimeSec
        if (segmentSpeedMps >= GpsConfig.MIN_SPEED_DOPPLER_MPS) {
            recentSamples.add(PaceWindowSample(deltaDistanceMeters, deltaTimeSec))
            while (recentSamples.size > maxWindowSize.coerceAtLeast(1)) {
                recentSamples.removeAt(0)
            }
            val totalDistanceMeters = recentSamples.sumOf { it.distanceMeters }
            val totalDurationSec = recentSamples.sumOf { it.durationSec }
            if (totalDurationSec > 0.0) {
                val windowSpeedMps = totalDistanceMeters / totalDurationSec
                if (windowSpeedMps >= GpsConfig.MIN_SPEED_DOPPLER_MPS) {
                    val secPerKm = kotlin.math.round(1000.0 / windowSpeedMps).toInt()
                    if (secPerKm in 120..1850) {
                        return secPerKm
                    }
                }
            }
        } else if (recentSamples.isNotEmpty()) {
            recentSamples.clear()
        }
        return null
    }

    /**
     * Filtra oscilações verticais espúrias do chip GPS em terrenos planos (evitando "+203m" irreais):
     * - Retorna 0 se o ambiente for Esteira ("TREADMILL").
     * - Descarta leituras sem altitude real ou com precisão horizontal ruim (accuracy > 12.0m).
     * - Rejeita saltos verticais instantâneos espúrios (> 5.5m em um único ponto GPS).
     * - Aplica média móvel de 5 pontos nas altitudes válidas.
     * - Aplica banda morta (deadband vertical): só contabiliza ganho positivo quando a subida
     *   acumulada sustentada desde o último vale atingir >= 3.5 metros.
     */
    fun calculateFilteredElevationGainMeters(
        altitudes: List<Double>,
        accuracies: List<Float?> = emptyList(),
        isTreadmill: Boolean = false
    ): Int {
        if (isTreadmill || altitudes.size < 5) return 0

        val hasAnyAccuracy = accuracies.any { it != null && it > 0f }
        val rawValidAltitudes = altitudes.mapIndexedNotNull { index, alt ->
            val acc = accuracies.getOrNull(index)
            val accuracyOk = if (hasAnyAccuracy) {
                acc != null && acc > 0f && acc <= 12.0f
            } else {
                acc == null || (acc > 0f && acc <= 12.0f)
            }
            if (alt != 0.0 && !alt.isNaN() && accuracyOk) alt else null
        }
        if (rawValidAltitudes.size < 5) return 0

        // Filtra saltos verticais instantâneos (multipath de prédio/árvore > 5.5m num único fix)
        val despikedAltitudes = ArrayList<Double>(rawValidAltitudes.size)
        var lastAcceptedAlt = rawValidAltitudes[0]
        despikedAltitudes.add(lastAcceptedAlt)
        for (i in 1 until rawValidAltitudes.size) {
            val candidate = rawValidAltitudes[i]
            val stepDiff = kotlin.math.abs(candidate - lastAcceptedAlt)
            if (stepDiff <= 5.5) {
                despikedAltitudes.add(candidate)
                lastAcceptedAlt = candidate
            } else {
                // Amortecimento de outlier vertical
                val damped = lastAcceptedAlt + (candidate - lastAcceptedAlt).coerceIn(-2.0, 2.0)
                despikedAltitudes.add(damped)
                lastAcceptedAlt = damped
            }
        }
        if (despikedAltitudes.size < 5) return 0

        // Média móvel de 5 pontos (janela [idx - 2 .. idx + 2])
        val smoothed = DoubleArray(despikedAltitudes.size) { idx ->
            val start = (idx - 2).coerceAtLeast(0)
            val end = (idx + 2).coerceAtMost(despikedAltitudes.lastIndex)
            var sum = 0.0
            var count = 0
            for (k in start..end) {
                sum += despikedAltitudes[k]
                count++
            }
            sum / count.coerceAtLeast(1)
        }

        var totalGain = 0.0
        var anchorAltitude = smoothed[0]
        val deadbandMeters = 3.5
        val maxSustainedStepMeters = 15.0

        for (i in 1 until smoothed.size) {
            val current = smoothed[i]
            val deltaFromAnchor = current - anchorAltitude
            if (deltaFromAnchor >= deadbandMeters) {
                if (deltaFromAnchor <= maxSustainedStepMeters) {
                    totalGain += deltaFromAnchor
                }
                anchorAltitude = current
            } else if (current < anchorAltitude) {
                anchorAltitude = current
            }
        }

        return kotlin.math.round(totalGain).toInt().coerceAtLeast(0)
    }
}

