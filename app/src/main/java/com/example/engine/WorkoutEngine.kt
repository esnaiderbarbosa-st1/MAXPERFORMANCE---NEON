package com.example.engine

import com.example.data.model.Workout
import com.example.services.recommendation.RecommendationEngine
import com.example.utils.JsonUtils
import java.util.concurrent.ConcurrentHashMap

object WorkoutEngine {
    data class CacheEntry(val workout: Workout, val timestamp: Long)

    private val workoutCacheWithTTL = ConcurrentHashMap<String, CacheEntry>()
    private val CACHE_TTL_MS = 30 * 60 * 1000L // 30 minutos

    fun clearCache() {
        workoutCacheWithTTL.clear()
    }

    fun invalidateCache(workoutId: String) {
        workoutCacheWithTTL.entries.removeIf { it.value.workout.id == workoutId }
    }

    private fun buildCacheKey(context: WorkoutContext): String {
        val gymId = context.gymProfile?.id ?: "default"
        val profile = com.example.utils.WeightCalculator.activeUserProfile
        val profileSig = if (profile != null) "${profile.sexo}_${profile.peso}_${profile.idade}_${profile.nivelExperiencia}" else "default_profile"
        return "${context.targetMuscles.joinToString(",")}|${context.muscleGroup}|${context.objective}|${context.durationMinutes}|${context.experience}|$gymId|${context.fatigueLevel}|${context.recoveryProfile}|${context.userPreferences.level}|${context.technique}|${context.availableEquipment.joinToString(",")}|${context.preferredExercises.joinToString(",")}|$profileSig"
    }

    fun generate(context: WorkoutContext): Workout {
        val cacheKey = buildCacheKey(context)
        val cached = workoutCacheWithTTL[cacheKey]
        
        if (cached != null && System.currentTimeMillis() - cached.timestamp < CACHE_TTL_MS) {
            android.util.Log.d("MAX_PERF", "RecommendationEngine memoized cache hit for $cacheKey")
            return cached.workout
        }

        val knownMuscles = setOf(
            "peito", "costas", "pernas", "ombros", "bíceps", "biceps", 
            "tríceps", "triceps", "abdômen", "abdomen", "glúteos", "gluteos", "panturrilha", "panturrilhas"
        )
        val targetMuscles = if (context.targetMuscles.isNotEmpty()) {
            context.targetMuscles
        } else if (context.muscleGroup.isNotBlank() && !context.muscleGroup.equals("Full Body", ignoreCase = true)) {
            listOf(context.muscleGroup)
        } else {
            val objLower = context.objective.lowercase().trim()
            if (knownMuscles.contains(objLower)) {
                listOf(context.objective)
            } else {
                android.util.Log.w("MAX_PERF", "Target muscles not specified, falling back to Full Body. Objective: ${context.objective}")
                listOf("Peito", "Costas", "Pernas", "Ombros")
            }
        }

        val expLevel = if (context.experience.isNotBlank()) context.experience else "Intermediário"

        val mappedContext = context.copy(
            targetMuscles = targetMuscles,
            experience = expLevel
        )

        val recommendationEngine = RecommendationEngine()
        val fullWorkout = recommendationEngine.generateWorkout(mappedContext)

        val fullExercises = JsonUtils.parseWorkoutExercises(fullWorkout.exercisesJson)
        
        // For advanced workouts, we keep auxiliary exercises to provide a complete 8-10 exercise experience.
        // Otherwise, we filter them out to maintain strict legacy compatibility, but always preserve Aquecimento.
        val filtered = if (expLevel.equals("Avançado", ignoreCase = true)) {
            fullExercises
        } else {
            fullExercises.filter { 
                it.blockType == "Principal" || it.blockType == "Acessórios" || it.blockType == "Aquecimento"
            }
        }

        // Fallback: If filtered list is empty, assign fullExercises directly so Workout NEVER has 0 exercises
        val legacyExercises = if (filtered.isEmpty()) fullExercises else filtered

        val updatedJson = JsonUtils.serializeWorkoutExercises(legacyExercises)
        
        // Handle short duration warning for legacy backward compatibility
        val originalDesc = fullWorkout.description
        val finalDescription = if (context.durationMinutes < 30) {
            "Para um treino de ${context.objective} com qualidade mínima, recomendamos 40 minutos. $originalDesc"
        } else {
            originalDesc
        }

        val generated = fullWorkout.copy(
            exercisesJson = updatedJson,
            description = finalDescription
        )

        workoutCacheWithTTL[cacheKey] = CacheEntry(generated, System.currentTimeMillis())
        return generated
    }
}

