package com.example.services.recommendation

/**
 * Report containing explanation metrics and rationales of why certain recommendation decisions were made.
 * Helpful for debugging, testing, and eventual UI telemetry rendering.
 */
data class RecommendationReport(
    val workoutId: String,
    val timestamp: Long = System.currentTimeMillis(),
    val durationMinutes: Int,
    val objective: String,
    val triggeredRules: List<String>,
    val explanations: List<String>,
    val telemetryEventsLogged: List<String>
)
