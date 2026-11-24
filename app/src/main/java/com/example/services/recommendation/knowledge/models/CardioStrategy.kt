package com.example.services.recommendation.knowledge.models

/**
 * Represents a cardiovascular strategy suitable for specific fitness levels and training objectives.
 *
 * @property id Unique identifier for the cardio strategy.
 * @property name Descriptive name of the cardio strategy (e.g., "HIIT").
 * @property description Detailed description of how the cardio is executed.
 * @property recommendedGoals List of goal IDs for which this cardio strategy is recommended.
 * @property durationMinutes Recommended duration per session in minutes.
 * @property frequency Recommended sessions per week.
 * @property interferenceLevel Potential muscle-building interference level (e.g., Low, High).
 * @property energyCost Level of metabolic energy expended during execution.
 */
data class CardioStrategy(
    val id: String,
    val name: String,
    val description: String,
    val recommendedGoals: List<String>,
    val durationMinutes: Int,
    val frequency: String,
    val interferenceLevel: String,
    val energyCost: String
)
