package com.example.services.domain

import com.example.data.model.WorkoutExercise
import com.example.data.model.WorkoutHistoryEntry
import com.example.ui.viewmodel.MainViewModel.ExerciseWeeklyProgress
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object PerformanceEngine {

    // 1. Estimar 1RM (Fórmula de Brzycki com teto de segurança para altas repetições)
    fun estimar1RM(peso: Double, repeticoes: Int): Double {
        if (peso <= 0.0) return 0.0
        if (repeticoes <= 1) return peso
        val effectiveReps = repeticoes.coerceIn(1, 30)
        return peso / (1.0278 - (0.0278 * effectiveReps))
    }

    // 2. Calcular Carga recomendada para %1RM
    fun calcularCarga(rm: Double, percentual: Int): Double {
        return rm * (percentual / 100.0)
    }

    // 3. RPE para %1RM Estimado
    fun rpeParaPercentual(rpe: Int): Int {
        return when (rpe) {
            10 -> 100
            9 -> 92
            8 -> 86
            7 -> 80
            6 -> 74
            else -> 65
        }
    }

    fun getSuggestedWeightForExercise(
        historyEntries: List<WorkoutHistoryEntry>,
        exerciseName: String,
        equipment: String = "Barra",
        gymProfile: com.example.data.model.GymProfile? = null,
        progressionType: String = "Linear"
    ): Float {
        return com.example.data.repository.ExerciseHistoryRepository.getSuggestedWeightForExercise(
            history = historyEntries,
            exerciseNameOrId = exerciseName,
            equipment = equipment,
            gymProfile = gymProfile,
            progressionType = progressionType
        )
    }

    fun getHistoricalMaxForExercise(historyEntries: List<WorkoutHistoryEntry>, exerciseName: String): Pair<Float, Int> {
        var maxWeight = 0f
        var maxReps = 0
        try {
            for (entry in historyEntries) {
                val exercises = com.example.utils.JsonUtils.parseWorkoutExercises(entry.exercisesJson)
                for (ex in exercises) {
                    if (ex.name.equals(exerciseName, ignoreCase = true)) {
                        for (s in ex.sets) {
                            if (s.completed) {
                                if (s.weight > maxWeight) {
                                    maxWeight = s.weight
                                    maxReps = s.reps
                                } else if (s.weight == maxWeight) {
                                    if (s.reps > maxReps) {
                                        maxReps = s.reps
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("MAX_PERFORMANCE_DEBUG", "Error calculating historical max for $exerciseName", e)
        }
        return Pair(maxWeight, maxReps)
    }

    fun getHistoricalMaxRepsForWeight(historyEntries: List<WorkoutHistoryEntry>, exerciseName: String, weight: Float): Int {
        var maxReps = 0
        try {
            for (entry in historyEntries) {
                val exercises = com.example.utils.JsonUtils.parseWorkoutExercises(entry.exercisesJson)
                for (ex in exercises) {
                    if (ex.name.equals(exerciseName, ignoreCase = true)) {
                        for (s in ex.sets) {
                            if (s.completed && s.weight == weight) {
                                if (s.reps > maxReps) {
                                    maxReps = s.reps
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("MAX_PERFORMANCE_DEBUG", "Error calculating historical max reps for $exerciseName at $weight", e)
        }
        return maxReps
    }

    fun getWeeklyProgressForExercise(historyEntries: List<WorkoutHistoryEntry>, exerciseName: String): List<ExerciseWeeklyProgress> {
        val progressList = mutableListOf<ExerciseWeeklyProgress>()
        try {
            val weeklyExercises = mutableMapOf<Long, MutableList<WorkoutExercise>>()
            
            for (entry in historyEntries) {
                val date = entry.date
                val exercises = com.example.utils.JsonUtils.parseWorkoutExercises(entry.exercisesJson)
                val matchEx = exercises.find { it.name.equals(exerciseName, ignoreCase = true) }
                if (matchEx != null) {
                    val cal = Calendar.getInstance()
                    cal.timeInMillis = date
                    cal.set(Calendar.HOUR_OF_DAY, 0)
                    cal.set(Calendar.MINUTE, 0)
                    cal.set(Calendar.SECOND, 0)
                    cal.set(Calendar.MILLISECOND, 0)
                    val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
                    val diff = if (dayOfWeek == Calendar.SUNDAY) -6 else Calendar.MONDAY - dayOfWeek
                    cal.add(Calendar.DAY_OF_MONTH, diff)
                    val startOfWeek = cal.timeInMillis
                    
                    if (!weeklyExercises.containsKey(startOfWeek)) {
                        weeklyExercises[startOfWeek] = mutableListOf()
                    }
                    weeklyExercises[startOfWeek]?.add(matchEx)
                }
            }
            
            val sdf = SimpleDateFormat("dd/MM", Locale.getDefault())
            
            for ((startOfWeek, exercisesInWeek) in weeklyExercises) {
                var totalVolumeInWeek = 0f
                var maxWeightInWeek = 0f
                var completedSetsInWeek = 0
                
                for (ex in exercisesInWeek) {
                    for (s in ex.sets) {
                        if (s.completed) {
                            totalVolumeInWeek += (s.reps * s.weight)
                            completedSetsInWeek++
                            if (s.weight > maxWeightInWeek) {
                                maxWeightInWeek = s.weight
                            }
                        }
                    }
                }
                
                if (completedSetsInWeek > 0) {
                    val startStr = sdf.format(Date(startOfWeek))
                    val endStr = sdf.format(Date(startOfWeek + 6 * 24 * 60 * 60 * 1000L))
                    val label = "$startStr - $endStr"
                    
                    progressList.add(
                        ExerciseWeeklyProgress(
                            weekLabel = label,
                            totalVolume = totalVolumeInWeek,
                            maxWeight = maxWeightInWeek,
                            totalSets = completedSetsInWeek,
                            timestamp = startOfWeek
                        )
                    )
                }
            }
            
        } catch (e: Exception) {
            android.util.Log.e("MAX_PERFORMANCE_DEBUG", "Error calculating weekly progress for $exerciseName", e)
        }
        
        return progressList.sortedBy { it.timestamp }.takeLast(3)
    }
}
