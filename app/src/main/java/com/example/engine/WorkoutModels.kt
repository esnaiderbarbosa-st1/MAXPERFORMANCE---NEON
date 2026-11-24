package com.example.engine

import com.example.data.model.GymProfile
import com.example.data.model.Workout

data class UserPreferences(
    val level: String = "Intermediário",
    val preferredFocus: String = "Hipertrofia"
)

data class WorkoutConstraints(
    val noBarbell: Boolean = false,
    val lowerBackPain: Boolean = false,
    val timeLimitMinutes: Int = 60,
    val busyGym: Boolean = false,
    val generalRestrictions: List<String> = emptyList()
)

data class WorkoutContext(
    val durationMinutes: Int,
    val objective: String,
    val gymProfile: GymProfile?,
    val technique: String,
    val userPreferences: UserPreferences,
    
    // Sprint ENG-005 Expanded Context Fields (with defaults to maintain compatibility)
    val experience: String = userPreferences.level,
    val availableTime: Int = durationMinutes,
    val availableEquipment: List<String> = emptyList(),
    val targetMuscles: List<String> = emptyList(),
    val muscleGroup: String = "",
    val excludedMuscles: List<String> = emptyList(),
    val preferredExercises: List<String> = emptyList(),
    val injuredMuscles: List<String> = emptyList(),
    val fatigueLevel: Double = 0.0,
    val recoveryProfile: Double = 8.0,
    val lastWorkout: Workout? = null,
    val history: List<com.example.data.model.WorkoutHistoryEntry> = emptyList(),
    val weeklyVolume: Int = 0,
    val preferredProgression: String = technique,
    val preferredIntensity: String = "",
    val cardioPreference: String = "Nenhum",
    val date: Long = System.currentTimeMillis(),
    val weekday: Int = 1,
    val constraints: WorkoutConstraints = WorkoutConstraints(
        noBarbell = false,
        lowerBackPain = false,
        timeLimitMinutes = durationMinutes,
        busyGym = false,
        generalRestrictions = emptyList()
    ),
    
    // RFC-REC-003 Prioritizer Context Fields
    val safetyConstrainedExerciseIds: MutableList<String> = mutableListOf(),
    var returnSessionsRemaining: Int = 0,
    val lastWorkoutDate: Long? = null,
    val treinosTotaisHistorico: Int = 0,
    val metaSemanal: Int = 3,
    val adherenceRate: Double? = null,
    val recuperacaoAlta: Boolean = false,
    val repsAlvoBatidas: Boolean = false,
    val tonelagemAtual: Float = 0f,
    var tonelagemEsperada: Float = 0f,
    val last4WeeksTonnages: List<Float> = emptyList(),
    var consecutiveFloorWeeks: Int = 0,
    val unavailableExerciseIds: List<String> = emptyList(),
    val includeWarmup: Boolean = true
)

data class WorkoutState(
    val currentExerciseIndex: Int = 0,
    val currentSetIndex: Int = 0,
    val elapsedTimeMillis: Long = 0L,
    val rpe: Int = 8
)
