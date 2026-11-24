package com.example.services.recommendation.planners

import com.example.data.model.Exercise

interface RestPlanner {
    fun calculateRestTime(
        objective: String,
        methodId: String,
        exercise: Exercise,
        recoveryIndex: Double
    ): Int // Returns rest time in seconds
}

class RestPlannerImpl : RestPlanner {
    override fun calculateRestTime(
        objective: String,
        methodId: String,
        exercise: Exercise,
        recoveryIndex: Double
    ): Int {
        // 1. Get base rest from exercise recommendedRestRange (e.g., "60-90", "120-180")
        var baseRestSeconds = 90 // Default fallback
        val recommendedRange = exercise.recommendedRestRange
        if (recommendedRange.contains("-")) {
            val parts = recommendedRange.split("-")
            try {
                // Average of the range
                baseRestSeconds = (parts[0].trim().toInt() + parts[1].trim().toInt()) / 2
            } catch (e: Exception) {
                // fallback remains 90
            }
        } else {
            try {
                baseRestSeconds = recommendedRange.trim().toInt()
            } catch (e: Exception) {
                // fallback remains 90
            }
        }

        // 2. Adjust based on training objective (overrides if necessary)
        val objectiveRestSeconds = when (objective.lowercase()) {
            "strength", "força" -> 180 // Strength requires 3-5 mins (180-300s)
            "hypertrophy", "hipertrofia" -> 90 // 60-120s
            "bodybuilding" -> 75 // 60-90s
            "weight_loss", "emagrecimento" -> 60 // 45-90s
            "endurance", "resistência" -> 45 // 30-60s
            "maintenance", "manutenção" -> 90
            "performance" -> 120 // 2-3 mins
            "rehabilitation", "reabilitação" -> 90
            else -> baseRestSeconds
        }

        // We combine exercise-specific recommendations and objective expectations
        var restSeconds = if (objective.lowercase() == "strength" || objective.lowercase() == "força") {
            // Strength heavily prioritizes longer rest
            maxOf(objectiveRestSeconds, baseRestSeconds)
        } else {
            (objectiveRestSeconds + baseRestSeconds) / 2
        }

        // 3. Adjust based on intensity method
        restSeconds = when (methodId.lowercase()) {
            "drop_set" -> 30 // reduction for metabolic distress
            "rest_pause" -> 15 // extremely short rest for rest-pause mini-sets
            "myo_reps" -> 10 // extremely short rest
            "cluster" -> 20 // cluster micro-pauses
            "superset" -> 0 // no rest between superset exercises
            "giant_set" -> 0 // no rest between exercises
            "feeder_sets" -> 45 // shorter rest for high rep pumping
            else -> restSeconds
        }

        // 4. Adjust based on recovery index (fatigue demands more rest)
        if (recoveryIndex < 5.0 && restSeconds > 0) {
            restSeconds += if (recoveryIndex < 3.0) 60 else 30
        }

        return restSeconds
    }
}
