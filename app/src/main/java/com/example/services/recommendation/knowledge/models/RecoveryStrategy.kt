package com.example.services.recommendation.knowledge.models

/**
 * Represents a recovery strategy mapping external/internal fatigue signs to training adjustments.
 *
 * @property id Unique identifier for the recovery strategy.
 * @property name Descriptive name of the recovery strategy (e.g., "Full Recovery").
 * @property description Detailed description of this recovery status and actions.
 * @property minimumRecovery Minimum subjective recovery/lifestyle elements required.
 * @property volumeAdjustment Guidance on adapting training volume.
 * @property restAdjustment Guidance on adapting rest times.
 * @property allowIntensityMethods Whether intensity techniques (e.g., rest-pause, drop sets) are recommended.
 * @property priority Ranking priority of this recovery state when multiple rules apply.
 */
data class RecoveryStrategy(
    val id: String,
    val name: String,
    val description: String,
    val minimumRecovery: String,
    val volumeAdjustment: String,
    val restAdjustment: String,
    val allowIntensityMethods: Boolean,
    val priority: Int
)
