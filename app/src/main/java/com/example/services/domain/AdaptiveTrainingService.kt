package com.example.services.domain

import com.example.data.model.*
import com.example.utils.MuscleRecovery
import com.example.utils.RecoveryAndTrophies
import com.example.utils.LevelSuggester
import com.example.utils.LevelSuggestion

data class AdaptiveRecommendations(
    val recommendedMuscleGroups: List<String>,
    val focusMovementPattern: MovementPattern,
    val recommendedIntensityRpe: Float,
    val volumeMultiplier: Float, // e.g. 1.0f, 0.8f for high fatigue, 1.2f for high recovery
    val levelSuggestion: LevelSuggestion?,
    val advice: String
)

object AdaptiveTrainingService {

    /**
     * Recommends training parameters based on historical data and current physical state.
     */
    fun generateRecommendations(
        profile: UserProfileData?,
        history: List<WorkoutHistoryEntry>,
        currentFatigueLevel: Int = 5 // scale 1..10, 1 is fresh, 10 is exhausted
    ): AdaptiveRecommendations {
        // 1. Calculate recovery of each muscle group
        val recoveryList = RecoveryAndTrophies.calculateRecovery(history)
        
        // Muscles with >= 85% recovery are ready for high intensity training
        val fullyRecoveredMuscles = recoveryList
            .filter { it.recoveryPercent >= 0.85f }
            .map { it.name }

        // 2. Adjust intensity and volume multiplier based on current fatigue level
        val (recommendedRpe, volumeMultiplier, advice) = when {
            currentFatigueLevel >= 8 -> Triple(
                6.5f,
                0.7f,
                "Seu nível de fadiga está muito alto ($currentFatigueLevel/10). Recomendamos um treino regenerativo (Deload) focado em mobilidade, RPE baixo (~6-7) e volume reduzido em 30%."
            )
            currentFatigueLevel >= 5 -> Triple(
                7.5f,
                0.9f,
                "Fadiga moderada detectada ($currentFatigueLevel/10). Mantenha o foco em boa execução, reduza ligeiramente o volume total (10% a menos) e evite falha concêntrica extrema."
            )
            else -> Triple(
                8.5f,
                1.1f,
                "Excelente nível de recuperação física! Ótimo momento para buscar progressão de carga ou intensidade. Busque RPE 8-9 e mantenha volume completo."
            )
        }

        // 3. Check for experience level upgrades
        val levelSuggestion = profile?.let {
            LevelSuggester.shouldSuggestLevelUp(it, history)
        }

        // 4. Recommend a focus movement pattern based on ready muscles
        val focusPattern = when {
            fullyRecoveredMuscles.contains("Peito") || fullyRecoveredMuscles.contains("Tríceps") -> MovementPattern.EMPURRAR
            fullyRecoveredMuscles.contains("Costas") || fullyRecoveredMuscles.contains("Bíceps") -> MovementPattern.PUXAR
            fullyRecoveredMuscles.contains("Pernas") -> MovementPattern.AGACHAMENTO
            else -> MovementPattern.OUTROS
        }

        // Clean list of recommended target muscles, prioritizing fully recovered ones
        val recommendedMuscles = if (fullyRecoveredMuscles.isNotEmpty()) {
            fullyRecoveredMuscles
        } else {
            listOf("Abdominais", "Ombros") // fallback safe muscles
        }

        return AdaptiveRecommendations(
            recommendedMuscleGroups = recommendedMuscles,
            focusMovementPattern = focusPattern,
            recommendedIntensityRpe = recommendedRpe,
            volumeMultiplier = volumeMultiplier,
            levelSuggestion = if (levelSuggestion?.suggest == true) levelSuggestion else null,
            advice = advice
        )
    }

    /**
     * Suggests a substitute exercise of similar movement pattern and muscle group
     * if the original equipment is not available.
     */
    fun suggestSubstitute(
        original: Exercise,
        availableEquipment: List<Equipment>,
        allExercises: List<Exercise>
    ): Exercise? {
        val originalPattern = original.movementPatternType
        val originalMuscle = original.primaryMuscleGroup

        return allExercises
            .filter { it.id != original.id }
            .filter { it.primaryMuscleGroup == originalMuscle }
            .filter { it.movementPatternType == originalPattern }
            .firstOrNull { it.equipmentType in availableEquipment }
    }
}
