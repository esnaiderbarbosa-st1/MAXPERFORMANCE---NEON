package com.example.data.model

data class CurrentGymContext(
    val gymId: String,
    val name: String,
    val lastUsedTimestamp: Long = System.currentTimeMillis(),
    val gymProfile: GymProfile,
    val barWeight: Float = 20.0f,
    val availablePlates: List<Float> = listOf(20f, 15f, 10f, 5f, 2.5f, 1.25f),
    val machineIncrement: Float = 2.5f,
    val availableEquipment: List<String> = emptyList(),
    val enabledExercisesIds: List<String> = emptyList()
)

data class CurrentContext(
    val currentUser: UserProfileData? = null,
    val currentGym: CurrentGymContext? = null,
    val currentWorkout: Workout? = null,
    val lastSynchronization: Long = System.currentTimeMillis()
)
