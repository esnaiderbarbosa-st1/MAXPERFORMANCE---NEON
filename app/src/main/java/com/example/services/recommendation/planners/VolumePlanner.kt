package com.example.services.recommendation.planners

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

interface VolumePlanner {
    data class VolumeResult(
        val exercisesCount: Int,
        val setsPerExercise: Int,
        val weeklyVolume: Int,
        val minWeeklyVolume: Int,
        val maxWeeklyVolume: Int
    )

    fun calculateVolume(
        objective: String,
        timeAvailableMinutes: Int,
        level: String,
        recoveryIndex: Double
    ): VolumeResult
}

class VolumePlannerImpl : VolumePlanner {
    override fun calculateVolume(
        objective: String,
        timeAvailableMinutes: Int,
        level: String,
        recoveryIndex: Double
    ): VolumePlanner.VolumeResult {
        // Base range of weekly sets per muscle group based on objective
        val (baseMin, baseMax) = when (objective.lowercase()) {
            "strength", "força" -> Pair(6, 12)
            "hypertrophy", "hipertrofia" -> Pair(10, 20)
            "bodybuilding" -> Pair(15, 25)
            "weight_loss", "emagrecimento" -> Pair(12, 16)
            "endurance", "resistência" -> Pair(12, 18)
            "maintenance", "manutenção" -> Pair(4, 8)
            "performance" -> Pair(8, 12)
            "rehabilitation", "reabilitação" -> Pair(4, 8)
            else -> Pair(10, 16)
        }

        // Adjust based on experience/level
        val levelMultiplier = when (level.lowercase()) {
            "iniciante" -> 0.7
            "intermediário" -> 1.0
            "avançado" -> 1.3
            else -> 1.0
        }

        var minWeekly = (baseMin * levelMultiplier).roundToInt()
        var maxWeekly = (baseMax * levelMultiplier).roundToInt()

        // Adjust based on recovery index (scale 0-10, where 8.0 is perfect)
        val recoveryMultiplier = when {
            recoveryIndex >= 8.0 -> 1.1
            recoveryIndex >= 5.0 -> 1.0
            recoveryIndex >= 3.0 -> 0.7
            else -> 0.5
        }

        minWeekly = (minWeekly * recoveryMultiplier).roundToInt()
        maxWeekly = (maxWeekly * recoveryMultiplier).roundToInt()

        // Ensure minimum values are sensible
        minWeekly = max(2, minWeekly)
        maxWeekly = max(4, maxWeekly)

        // Calculate single workout volume (assuming 2-3 sessions per muscle group per week)
        val targetWeeklyVolume = ((minWeekly + maxWeekly) / 2)
        val singleWorkoutTargetVolume = max(3, (targetWeeklyVolume / 2.5).roundToInt())

        // Determine sets per exercise based on level
        val setsPerExercise = when (level.lowercase()) {
            "iniciante" -> 3
            "intermediário" -> 3
            "avançado" -> 4
            else -> 3
        }

        // Calculate number of exercises based on time and total sets
        // A single exercise + rest takes roughly 5-6 minutes for advanced users.
        val timeFactor = if (level.lowercase() == "avançado") 6 else 7
        val maxExercisesByTime = max(1, (timeAvailableMinutes / timeFactor))
        val targetByLevel = if (level.lowercase() == "avançado" && timeAvailableMinutes >= 60) 9 else (singleWorkoutTargetVolume.toDouble() / setsPerExercise).roundToInt()
        var exercisesCount = max(targetByLevel, (timeAvailableMinutes / 7))
        exercisesCount = min(exercisesCount, maxExercisesByTime)
        exercisesCount = max(1, exercisesCount)

        return VolumePlanner.VolumeResult(
            exercisesCount = exercisesCount,
            setsPerExercise = setsPerExercise,
            weeklyVolume = targetWeeklyVolume,
            minWeeklyVolume = minWeekly,
            maxWeeklyVolume = maxWeekly
        )
    }
}
