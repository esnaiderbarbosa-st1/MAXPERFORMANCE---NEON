package com.example.engine

import com.example.data.model.GymProfile
import com.example.data.model.Workout

// Core interface for future integration of Recovery
interface RecoveryProvider {
    fun getRecoveryIndex(): Double
    fun getFatigueLevel(): Double
}

// Default implementation of RecoveryProvider
class DefaultRecoveryProvider : RecoveryProvider {
    override fun getRecoveryIndex(): Double = 8.0
    override fun getFatigueLevel(): Double = 0.0
}

// Core interface for future integration of WorkoutHistory
interface WorkoutHistoryProvider {
    fun getLastWorkout(): Workout?
    fun getWeeklyVolume(): Int
    fun getAllHistory(): List<com.example.data.model.WorkoutHistoryEntry> = emptyList()
}

// Default implementation of WorkoutHistoryProvider
class DefaultWorkoutHistoryProvider : WorkoutHistoryProvider {
    override fun getLastWorkout(): Workout? = null
    override fun getWeeklyVolume(): Int = 0
    override fun getAllHistory(): List<com.example.data.model.WorkoutHistoryEntry> = emptyList()
}

// Core interface for future integration of GymProfile
interface GymProfileProvider {
    fun getActiveGymProfile(): GymProfile?
    val availableProfiles: List<GymProfile>
}

// Default implementation of GymProfileProvider
class DefaultGymProfileProvider : GymProfileProvider {
    override fun getActiveGymProfile(): GymProfile? = null
    override val availableProfiles: List<GymProfile> = emptyList()
}

// Core interface for future integration of UserPreferences
interface UserPreferenceProvider {
    fun getUserPreferences(): UserPreferences
    fun getInjuredMuscles(): List<String>
    fun getPreferredExercises(): List<String>
    fun getCardioPreference(): String
}

// Default implementation of UserPreferenceProvider
class DefaultUserPreferenceProvider : UserPreferenceProvider {
    override fun getUserPreferences(): UserPreferences = UserPreferences()
    override fun getInjuredMuscles(): List<String> = emptyList()
    override fun getPreferredExercises(): List<String> = emptyList()
    override fun getCardioPreference(): String = "Nenhum"
}

interface WorkoutContextProvider {
    fun build(): WorkoutContext
    fun buildWithDetailedSources(
        durationMinutes: Int,
        objective: String,
        technique: String,
        targetMuscles: List<String>,
        excludedMuscles: List<String>,
        constraints: WorkoutConstraints = WorkoutConstraints()
    ): WorkoutContext
}

class QuickWorkoutProvider(
    private val durationMinutes: Int,
    private val objective: String,
    private val gymProfile: GymProfile?,
    private val technique: String,
    private val userPreferences: UserPreferences = UserPreferences(),
    private val muscleGroup: String = ""
) : WorkoutContextProvider {
    override fun build(): WorkoutContext {
        val targetMusclesList = if (muscleGroup.isNotBlank() && !muscleGroup.equals("Full Body", ignoreCase = true)) {
            listOf(muscleGroup)
        } else {
            emptyList()
        }
        return WorkoutContext(
            durationMinutes = durationMinutes,
            objective = objective,
            gymProfile = gymProfile,
            technique = technique,
            userPreferences = userPreferences,
            muscleGroup = muscleGroup,
            targetMuscles = targetMusclesList
        )
    }

    override fun buildWithDetailedSources(
        durationMinutes: Int,
        objective: String,
        technique: String,
        targetMuscles: List<String>,
        excludedMuscles: List<String>,
        constraints: WorkoutConstraints
    ): WorkoutContext {
        return WorkoutContext(
            durationMinutes = durationMinutes,
            objective = objective,
            gymProfile = gymProfile,
            technique = technique,
            userPreferences = userPreferences,
            targetMuscles = targetMuscles,
            excludedMuscles = excludedMuscles,
            constraints = constraints
        )
    }
}

// Advanced context provider leveraging individual providers
class AdaptiveWorkoutContextProvider(
    private val recoveryProvider: RecoveryProvider = DefaultRecoveryProvider(),
    private val historyProvider: WorkoutHistoryProvider = DefaultWorkoutHistoryProvider(),
    private val gymProfileProvider: GymProfileProvider = DefaultGymProfileProvider(),
    private val userPreferenceProvider: UserPreferenceProvider = DefaultUserPreferenceProvider()
) : WorkoutContextProvider {
    override fun build(): WorkoutContext {
        val prefs = userPreferenceProvider.getUserPreferences()
        val gym = gymProfileProvider.getActiveGymProfile()
        
        android.util.Log.i("MAX_PERFORMANCE_DEBUG", "AdaptiveWorkoutContextProvider.build: " +
            "prefs.level='${prefs.level}' userProfile.nivelExperiencia='<TO_BE_SYNCED_NEXT_SPRINT>'")
        
        return WorkoutContext(
            durationMinutes = 45,
            objective = prefs.preferredFocus,
            gymProfile = gym,
            technique = "Linear",
            userPreferences = prefs,
            experience = prefs.level,
            availableTime = 45,
            availableEquipment = extractEquipmentFromGym(gym),
            targetMuscles = emptyList(),
            excludedMuscles = userPreferenceProvider.getInjuredMuscles(),
            preferredExercises = userPreferenceProvider.getPreferredExercises(),
            injuredMuscles = userPreferenceProvider.getInjuredMuscles(),
            fatigueLevel = recoveryProvider.getFatigueLevel(),
            recoveryProfile = recoveryProvider.getRecoveryIndex(),
            lastWorkout = historyProvider.getLastWorkout(),
            history = historyProvider.getAllHistory(),
            weeklyVolume = historyProvider.getWeeklyVolume(),
            preferredProgression = "Linear",
            preferredIntensity = "",
            cardioPreference = userPreferenceProvider.getCardioPreference()
        )
    }

    override fun buildWithDetailedSources(
        durationMinutes: Int,
        objective: String,
        technique: String,
        targetMuscles: List<String>,
        excludedMuscles: List<String>,
        constraints: WorkoutConstraints
    ): WorkoutContext {
        val prefs = userPreferenceProvider.getUserPreferences()
        val gym = gymProfileProvider.getActiveGymProfile() ?: gymProfileProvider.availableProfiles.firstOrNull()
        
        return WorkoutContext(
            durationMinutes = durationMinutes,
            objective = objective,
            gymProfile = gym,
            technique = technique,
            userPreferences = prefs,
            experience = prefs.level,
            availableTime = durationMinutes,
            availableEquipment = extractEquipmentFromGym(gym),
            targetMuscles = targetMuscles,
            excludedMuscles = excludedMuscles + userPreferenceProvider.getInjuredMuscles() + if (constraints.lowerBackPain) listOf("Lombar") else emptyList(),
            preferredExercises = userPreferenceProvider.getPreferredExercises(),
            injuredMuscles = userPreferenceProvider.getInjuredMuscles(),
            fatigueLevel = recoveryProvider.getFatigueLevel(),
            recoveryProfile = recoveryProvider.getRecoveryIndex(),
            lastWorkout = historyProvider.getLastWorkout(),
            history = historyProvider.getAllHistory(),
            weeklyVolume = historyProvider.getWeeklyVolume(),
            preferredProgression = technique,
            preferredIntensity = "",
            cardioPreference = userPreferenceProvider.getCardioPreference(),
            constraints = constraints
        )
    }

    private fun extractEquipmentFromGym(gym: GymProfile?): List<String> {
        if (gym == null) return emptyList()
        val equip = mutableListOf<String>()
        if (gym.hasOlympicBar) equip.add("Barra")
        if (gym.hasDumbbells) equip.add("Halteres")
        if (gym.hasCables) equip.add("Polia")
        if (gym.hasMachines) equip.add("Máquina")
        if (gym.hasSmith) equip.add("Smith")
        if (gym.hasHack) equip.add("Hack")
        if (gym.hasLegPress) equip.add("Leg Press")
        return equip
    }
}
