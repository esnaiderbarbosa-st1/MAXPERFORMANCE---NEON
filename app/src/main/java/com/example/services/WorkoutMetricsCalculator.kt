package com.example.services

import android.location.Location
import com.example.data.model.GpsTrackPoint
import com.example.engine.RunningBlock
import com.example.engine.RunningBlockType
import com.example.engine.RunningWorkoutPlan
import kotlin.math.roundToInt

enum class WorkoutMetricGroup {
    EVOLUTION,
    CARGA,
    RECUPERATION
}

data class BlockMetricSummary(
    val blockIndex: Int,
    val blockType: RunningBlockType,
    val label: String,
    val durationSec: Int,
    val distanceMeters: Double,
    val avgPaceSecPerKm: Int?,
    val countsForEvolution: Boolean
)

data class WorkoutMetricsResult(
    val evolutionDurationSec: Int,
    val evolutionDistanceMeters: Double,
    val evolutionAvgPaceSecPerKm: Int?,
    val cargaDurationSec: Int,
    val cargaDistanceMeters: Double,
    val cargaCaloriesKcal: Int,
    val recuperationAvgPaceSecPerKm: Int?,
    val blockSummaries: List<BlockMetricSummary>,
    val partialSplits: List<PartialSplit>
)

data class PartialSplit(
    val title: String, // ex: "1,0 km", "0,25 km", "Bloco 1"
    val durationSec: Int,
    val distanceMeters: Double,
    val paceSecPerKm: Int?
)

object WorkoutMetricsCalculator {

    fun calculateMetrics(
        plan: RunningWorkoutPlan?,
        skippedIndices: Set<Int>,
        totalElapsedSec: Int,
        totalMeasuredDistanceMeters: Double,
        splitIntervalKm: Float, // 0.25f, 0.5f, 1.0f, ou -1.0f para "por bloco"
        userWeightKg: Float? = null
    ): WorkoutMetricsResult {
        val blocks = plan?.blocks ?: emptyList()
        
        val effectiveBlocks = if (blocks.isEmpty()) {
            listOf(RunningBlock(RunningBlockType.RUN, maxOf(totalElapsedSec, 60), "Treino"))
        } else {
            blocks
        }

        var remainingElapsed = totalElapsedSec
        var runningDistanceAccumulator = 0.0
        val blockSummaries = mutableListOf<BlockMetricSummary>()

        for (i in effectiveBlocks.indices) {
            if (i in skippedIndices) continue
            val block = effectiveBlocks[i]
            val blockDuration = minOf(maxOf(remainingElapsed, 0), block.durationSec)
            remainingElapsed -= blockDuration

            val countsEvolution = block.type == RunningBlockType.RUN
            val blockDist = if (totalMeasuredDistanceMeters > 0.0) {
                val totalActivePlanSec = effectiveBlocks.indices.filter { it !in skippedIndices }.sumOf { effectiveBlocks[it].durationSec }
                if (totalActivePlanSec > 0) {
                    totalMeasuredDistanceMeters * (block.durationSec.toDouble() / totalActivePlanSec.toDouble())
                } else {
                    0.0
                }
            } else {
                0.0
            }

            runningDistanceAccumulator += blockDist

            val pace = if (blockDist > 1.0 && blockDuration > 0) {
                ((blockDuration * 1000.0) / blockDist).toInt()
            } else {
                null
            }

            blockSummaries.add(
                BlockMetricSummary(
                    blockIndex = i,
                    blockType = block.type,
                    label = block.label,
                    durationSec = blockDuration,
                    distanceMeters = blockDist,
                    avgPaceSecPerKm = pace,
                    countsForEvolution = countsEvolution
                )
            )
        }

        // EVOLUÇÃO: Só blocos de CORRIDA
        val evoDuration = blockSummaries.filter { it.countsForEvolution }.sumOf { it.durationSec }
        val evoDist = blockSummaries.filter { it.countsForEvolution }.sumOf { it.distanceMeters }
        val evoPace = if (evoDist > 1.0 && evoDuration > 0) ((evoDuration * 1000.0) / evoDist).toInt() else null

        // CARGA: Tudo (sem inventar distância ou calorias se o deslocamento medido for 0.0)
        val cargaDuration = blockSummaries.sumOf { it.durationSec }
        val cargaDist = if (totalMeasuredDistanceMeters > 0.0) totalMeasuredDistanceMeters else 0.0
        val cargaCalories = if (cargaDist > 0.0) {
            com.example.data.model.calculateRunActiveCaloriesKcal(
                distanceMeters = cargaDist.roundToInt(),
                userProfile = null,
                weightKgOverride = userWeightKg
            ) ?: 0
        } else {
            0
        }

        // RECUPERATION: Pace médio das CAMINHADAS entre os tiros
        val walkSummaries = blockSummaries.filter { it.blockType == RunningBlockType.WALK }
        val walkDuration = walkSummaries.sumOf { it.durationSec }
        val walkDist = walkSummaries.sumOf { it.distanceMeters }
        val recuperationPace = if (walkDist > 1.0 && walkDuration > 0) ((walkDuration * 1000.0) / walkDist).toInt() else null

        // PARCIAIS
        val partials = mutableListOf<PartialSplit>()
        if (splitIntervalKm > 0f && cargaDist > 0.0) {
            val intervalMeters = (splitIntervalKm * 1000.0).toDouble()
            var currentDist = 0.0
            var currentSec = 0.0
            var splitIndex = 1

            val totalDistSoFar = cargaDist.coerceAtLeast(1.0)
            val totalSecSoFar = cargaDuration.coerceAtLeast(1)

            var targetDist = intervalMeters
            while (targetDist <= totalDistSoFar + 1.0) {
                val segmentDist = minOf(intervalMeters, totalDistSoFar - currentDist)
                if (segmentDist <= 0.0) break
                val segmentSec = (segmentDist / totalDistSoFar) * totalSecSoFar
                val pSec = if (segmentDist > 1.0 && segmentSec > 0.0) ((segmentSec * 1000.0) / segmentDist).toInt() else null

                val title = if (splitIntervalKm >= 1f) {
                    "${splitIndex},0 km"
                } else if (splitIntervalKm == 0.5f) {
                    String.format(java.util.Locale.US, "%.1f km", splitIndex * 0.5f)
                } else {
                    String.format(java.util.Locale.US, "%.2f km", splitIndex * 0.25f)
                }

                partials.add(
                    PartialSplit(
                        title = title,
                        durationSec = segmentSec.toInt(),
                        distanceMeters = segmentDist,
                        paceSecPerKm = pSec
                    )
                )

                currentDist += segmentDist
                currentSec += segmentSec
                splitIndex++
                targetDist += intervalMeters
            }

            if (currentDist < totalDistSoFar - 5.0 && totalDistSoFar - currentDist > 10.0) {
                val remDist = totalDistSoFar - currentDist
                val remSec = totalSecSoFar - currentSec
                val pSec = if (remDist > 1.0 && remSec > 0.0) ((remSec * 1000.0) / remDist).toInt() else null
                partials.add(
                    PartialSplit(
                        title = String.format(java.util.Locale.US, "%.2f km", totalDistSoFar / 1000.0),
                        durationSec = remSec.toInt(),
                        distanceMeters = remDist,
                        paceSecPerKm = pSec
                    )
                )
            }
        } else if (splitIntervalKm <= 0f) {
            for (bs in blockSummaries) {
                partials.add(
                    PartialSplit(
                        title = bs.label,
                        durationSec = bs.durationSec,
                        distanceMeters = bs.distanceMeters,
                        paceSecPerKm = bs.avgPaceSecPerKm
                    )
                )
            }
        }

        return WorkoutMetricsResult(
            evolutionDurationSec = evoDuration,
            evolutionDistanceMeters = evoDist,
            evolutionAvgPaceSecPerKm = evoPace,
            cargaDurationSec = cargaDuration,
            cargaDistanceMeters = cargaDist,
            cargaCaloriesKcal = cargaCalories,
            recuperationAvgPaceSecPerKm = recuperationPace,
            blockSummaries = blockSummaries,
            partialSplits = partials
        )
    }

    fun calculateSplits(
        distanceMeters: Int,
        durationSec: Int,
        splitIntervalKm: Float = 0.5f
    ): List<SplitData> {
        if (distanceMeters <= 0 || durationSec <= 0 || splitIntervalKm <= 0f) return emptyList()

        val intervalMeters = (splitIntervalKm * 1000f).toInt()
        val splits = mutableListOf<SplitData>()
        var accumulatedDist = 0
        var accumulatedTime = 0

        while (accumulatedDist < distanceMeters) {
            val currentChunkMeters = minOf(intervalMeters, distanceMeters - accumulatedDist)
            if (currentChunkMeters <= 0) break

            val currentChunkTimeSec = ((currentChunkMeters.toDouble() / distanceMeters.toDouble()) * durationSec).toInt()
            accumulatedDist += currentChunkMeters
            accumulatedTime += currentChunkTimeSec

            val paceSecPerKm = if (currentChunkMeters > 0 && currentChunkTimeSec > 0) {
                ((currentChunkTimeSec.toDouble() * 1000.0) / currentChunkMeters.toDouble()).toInt()
            } else null

            splits.add(
                SplitData(
                    accumulatedDistanceKm = accumulatedDist / 1000f,
                    splitDistanceMeters = currentChunkMeters,
                    accumulatedTimeSec = accumulatedTime,
                    splitTimeSec = currentChunkTimeSec,
                    paceSecPerKm = paceSecPerKm
                )
            )
        }
        return splits
    }

    suspend fun calculateSplitsFromPoints(
        points: List<GpsTrackPoint>,
        splitIntervalKm: Float
    ): List<SplitData> {
        if (points.isEmpty() || splitIntervalKm <= 0f) return emptyList()

        // a) Filtra pontos com latitude/longitude != 0 e ordena por timestampMs ASC
        val validPoints = points
            .filter { it.latitude != 0.0 && it.longitude != 0.0 }
            .sortedBy { it.timestampMs }

        if (validPoints.size < 2) return emptyList()

        val intervalMeters = (splitIntervalKm * 1000f).toDouble()
        val splits = mutableListOf<SplitData>()

        val startTimestamp = validPoints.first().timestampMs
        var chunkStartTimestamp = startTimestamp
        var currentChunkDist = 0.0
        var totalAccumDist = 0.0
        var splitIndex = 1

        val results = FloatArray(1)
        var previousPoint = validPoints.first()

        for (i in 1 until validPoints.size) {
            val point = validPoints[i]
            Location.distanceBetween(
                previousPoint.latitude,
                previousPoint.longitude,
                point.latitude,
                point.longitude,
                results
            )
            val segmentDist = results[0].toDouble()

            currentChunkDist += segmentDist
            totalAccumDist += segmentDist

            if (currentChunkDist >= intervalMeters) {
                val splitTimeSec = ((point.timestampMs - chunkStartTimestamp) / 1000L).toInt().coerceAtLeast(1)
                val accumTimeSec = ((point.timestampMs - startTimestamp) / 1000L).toInt().coerceAtLeast(1)
                val paceSecPerKm = if (currentChunkDist > 0.0) {
                    ((splitTimeSec.toDouble() * 1000.0) / currentChunkDist).toInt()
                } else null

                splits.add(
                    SplitData(
                        accumulatedDistanceKm = (splitIndex * splitIntervalKm),
                        splitDistanceMeters = currentChunkDist.roundToInt(),
                        accumulatedTimeSec = accumTimeSec,
                        splitTimeSec = splitTimeSec,
                        paceSecPerKm = paceSecPerKm
                    )
                )

                chunkStartTimestamp = point.timestampMs
                currentChunkDist = 0.0
                splitIndex++
            }

            previousPoint = point
        }

        // Se sobrou distância final significativa (ex: parcial final de pelo menos 30m)
        if (currentChunkDist >= 30.0) {
            val lastPoint = validPoints.last()
            val splitTimeSec = ((lastPoint.timestampMs - chunkStartTimestamp) / 1000L).toInt().coerceAtLeast(1)
            val accumTimeSec = ((lastPoint.timestampMs - startTimestamp) / 1000L).toInt().coerceAtLeast(1)
            val paceSecPerKm = if (currentChunkDist > 0.0) {
                ((splitTimeSec.toDouble() * 1000.0) / currentChunkDist).toInt()
            } else null

            splits.add(
                SplitData(
                    accumulatedDistanceKm = (totalAccumDist / 1000.0).toFloat(),
                    splitDistanceMeters = currentChunkDist.roundToInt(),
                    accumulatedTimeSec = accumTimeSec,
                    splitTimeSec = splitTimeSec,
                    paceSecPerKm = paceSecPerKm
                )
            )
        }

        return splits
    }
}

data class SplitData(
    val accumulatedDistanceKm: Float,
    val splitDistanceMeters: Int,
    val accumulatedTimeSec: Int,
    val splitTimeSec: Int,
    val paceSecPerKm: Int?
)
