package com.example.services.recommendation.knowledge.models

/**
 * Represents a training objective with its recommended parameters, preferred strategies, and attributes.
 *
 * @property id Unique identifier for the training objective.
 * @property name Descriptive name of the objective (e.g., "Hipertrofia").
 * @property description Detailed explanation of the objective's purpose.
 * @property recommendedVolume Suggested weekly volume (e.g., in sets per muscle group).
 * @property recommendedRIR Recommended Reps in Reserve (RIR) range.
 * @property preferredRestRange Recommended resting time between sets.
 * @property preferredMethods Recommended intensity methods for this objective.
 * @property preferredProgressions Recommended progression strategies for this objective.
 * @property recommendedCardio Suggested cardio type and frequency.
 * @property fatigueTolerance Capacity to withstand fatigue associated with this objective.
 * @property difficulty Level of difficulty and technical demand.
 */
data class TrainingObjective(
    val id: String,
    val name: String,
    val description: String,
    val recommendedVolume: String,
    val recommendedRIR: String,
    val preferredRestRange: String,
    val preferredMethods: List<String>,
    val preferredProgressions: List<String>,
    val recommendedCardio: String,
    val fatigueTolerance: String,
    val difficulty: String
)
