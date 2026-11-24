package com.example.data.model

import java.util.UUID

/**
 * Entidade para os protocolos estruturados de treino (Full Body A/B/C/D, PPL, etc).
 */
data class WorkoutProtocol(
    val id: String = UUID.randomUUID().toString(),
    val name: String, // Ex: "Full Body A"
    val category: String, // "FULL_BODY" ou "PPL"
    val frequency: String, // Ex: "3x por semana"
    val description: String, // Descrição breve
    val exercises: List<ProtocolExercise>
)

/**
 * Exercício pertencente a um protocolo estruturado.
 */
data class ProtocolExercise(
    val name: String,
    val alternatives: String? = null, // Ex: "ou hack squat"
    val sets: Int,
    val reps: String, // Ex: "8-12" ou "10-15"
    val tip: String, // Dica de execução
    val muscleGroup: String // "Quadríceps", "Peito", etc.
)

/**
 * Item do Carrossel do Hero Card na Home Screen
 */
data class HeroCarouselItem(
    val id: String,
    val workout: Workout,
    val isRunning: Boolean = false,
    val isFixedOrSuggested: Boolean = false,
    val badgeLabel: String = "PRÓXIMO TREINO",
    val title: String = "",
    val subtitle: String = "",
    val durationText: String = "45-55 min",
    val nextGymWorkoutToAdvance: Workout? = null,
    val advanceGymButtonLabel: String? = null
)
