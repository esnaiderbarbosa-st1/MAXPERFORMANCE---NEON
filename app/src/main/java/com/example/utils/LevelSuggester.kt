package com.example.utils

import com.example.data.model.UserProfileData
import com.example.data.model.WorkoutHistoryEntry

data class LevelSuggestion(
    val suggest: Boolean,
    val newLevel: String,
    val reason: String
)

object LevelSuggester {

    fun getMonthsSinceFirstWorkout(history: List<WorkoutHistoryEntry>): Int {
        if (history.isEmpty()) return 0
        val firstWorkoutDate = history.minOf { it.date }
        val elapsedMs = System.currentTimeMillis() - firstWorkoutDate
        val months = elapsedMs.toDouble() / (1000.0 * 60.0 * 60.0 * 24.0 * 30.44)
        return months.toInt().coerceAtLeast(0)
    }

    fun getWeightIncreasePercentage(history: List<WorkoutHistoryEntry>): Int {
        if (history.size < 10) {
            if (history.size < 2) return 0
            val sorted = history.sortedBy { it.date }
            val first = sorted.first().totalWeight
            val last = sorted.last().totalWeight
            if (first <= 0f) return 0
            return (((last - first) / first) * 100f).toInt().coerceAtLeast(0)
        }
        val sortedHistory = history.sortedBy { it.date }
        val first5 = sortedHistory.take(5)
        val last5 = sortedHistory.takeLast(5)
        val firstAvg = first5.map { it.totalWeight }.average()
        val lastAvg = last5.map { it.totalWeight }.average()
        if (firstAvg <= 0.0) return 0
        val percentage = ((lastAvg - firstAvg) / firstAvg) * 100.0
        return percentage.toInt().coerceAtLeast(0)
    }

    fun shouldSuggestLevelUp(profile: UserProfileData, history: List<WorkoutHistoryEntry>): LevelSuggestion {
        val totalWorkouts = history.size
        val monthsActive = getMonthsSinceFirstWorkout(history)
        val weightIncrease = getWeightIncreasePercentage(history)

        if (profile.nivelExperiencia == "Iniciante") {
            if (totalWorkouts >= 50 && monthsActive >= 4 && weightIncrease >= 15) {
                return LevelSuggestion(
                    suggest = true,
                    newLevel = "Intermediário",
                    reason = "Você completou $totalWorkouts treinos em $monthsActive meses e sua carga média aumentou $weightIncrease%!"
                )
            }
        }

        if (profile.nivelExperiencia == "Intermediário") {
            if (totalWorkouts >= 120 && monthsActive >= 12 && weightIncrease >= 25) {
                return LevelSuggestion(
                    suggest = true,
                    newLevel = "Avançado",
                    reason = "Você completou $totalWorkouts treinos em $monthsActive meses e sua carga média aumentou $weightIncrease%!"
                )
            }
        }

        return LevelSuggestion(suggest = false, newLevel = "", reason = "")
    }
}
