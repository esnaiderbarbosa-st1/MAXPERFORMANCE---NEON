package com.example.services.recommendation.knowledge.models

/**
 * Represents a progression strategy to schedule weight, rep, or set changes over time.
 *
 * @property id Unique identifier for the progression strategy.
 * @property name Descriptive name of the progression strategy (e.g., "Linear").
 * @property description Detailed explanation of how progression is structured.
 * @property recommendedGoals List of goal IDs for which this strategy is recommended.
 * @property minimumExperience Minimum training experience required (e.g., Beginner, Intermediate).
 * @property recommendedDurationWeeks Suggested continuous duration of this progression block in weeks.
 * @property loadIncrease Guideline for load increments.
 * @property volumeIncrease Guideline for volume increments.
 * @property deloadRecommended Whether a regular deload is recommended at the end of this progression block.
 */
data class ProgressionStrategy(
    val id: String,
    val name: String,
    val description: String,
    val recommendedGoals: List<String>,
    val minimumExperience: String,
    val recommendedDurationWeeks: Int,
    val loadIncrease: String,
    val volumeIncrease: String,
    val deloadRecommended: Boolean
)
