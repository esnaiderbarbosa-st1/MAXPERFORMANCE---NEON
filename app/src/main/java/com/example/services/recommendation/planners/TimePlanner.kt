package com.example.services.recommendation.planners

import com.example.data.model.Exercise

interface TimePlanner {
    fun calculateExerciseTimeSeconds(
        exercise: Exercise,
        setsCount: Int,
        restTimeSeconds: Int
    ): Int

    fun calculateTotalWorkoutTimeSeconds(
        exercises: List<Exercise>,
        setsPerExercise: Int,
        restTimeSeconds: Int
    ): Int
}

class TimePlannerImpl : TimePlanner {
    override fun calculateExerciseTimeSeconds(
        exercise: Exercise,
        setsCount: Int,
        restTimeSeconds: Int
    ): Int {
        if (setsCount <= 0) return 0

        // Use exercise execution time or default to 45 seconds
        val execTime = if (exercise.averageExecutionTime > 0) exercise.averageExecutionTime else 45
        // Use exercise setup time or default to 60 seconds
        val setupTime = if (exercise.averageSetupTime > 0) exercise.averageSetupTime else 60

        val totalExecutionTime = execTime * setsCount
        val totalRestTime = restTimeSeconds * (setsCount - 1)

        return totalExecutionTime + totalRestTime + setupTime
    }

    override fun calculateTotalWorkoutTimeSeconds(
        exercises: List<Exercise>,
        setsPerExercise: Int,
        restTimeSeconds: Int
    ): Int {
        if (exercises.isEmpty()) return 0

        var totalSeconds = 0
        for (exercise in exercises) {
            totalSeconds += calculateExerciseTimeSeconds(exercise, setsPerExercise, restTimeSeconds)
            // Extra transition/rest between exercises (e.g., 90 seconds to move to next station)
            totalSeconds += 90
        }

        // Remove the trailing transition rest for the last exercise
        totalSeconds = maxOf(0, totalSeconds - 90)

        return totalSeconds
    }
}
