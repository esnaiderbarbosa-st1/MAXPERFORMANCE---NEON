package com.example.services.telemetry

import android.util.Log

object PerfTracker {
    const val TAG = "MAX_PERFORMANCE_METRICS"

    data class SessionPrepMetrics(
        var timeToOpenScreenMs: Long = 0L,
        var recommendationEngineMs: Long = 0L,
        var workoutBuilderMs: Long = 0L,
        var hydrationMs: Long = 0L,
        var roomQueriesMs: Long = 0L,
        var composeMs: Long = 0L,
        var totalSessionPrepMs: Long = 0L
    )

    inline fun <T> measure(stepName: String, block: () -> T): Pair<T, Long> {
        val start = System.currentTimeMillis()
        val result = block()
        val duration = System.currentTimeMillis() - start
        Log.d(TAG, "[$stepName] completed in ${duration}ms")
        return Pair(result, duration)
    }

    fun logMetrics(metrics: SessionPrepMetrics) {
        val message = """
            === MAX PERFORMANCE PIPELINE BENCHMARK ===
            - Total Session Prep: ${metrics.totalSessionPrepMs} ms
            - Recommendation Engine: ${metrics.recommendationEngineMs} ms
            - Workout Builder: ${metrics.workoutBuilderMs} ms
            - Hydration / Load Calculation: ${metrics.hydrationMs} ms
            - Room / Database Queries: ${metrics.roomQueriesMs} ms
            - Time to Open Screen: ${metrics.timeToOpenScreenMs} ms
            ===========================================
        """.trimIndent()
        Log.i(TAG, message)
        
        try {
            Telemetry.log(
                com.example.data.model.TelemetryEventType.PERFORMANCE_BENCHMARK,
                mapOf(
                    "total_ms" to metrics.totalSessionPrepMs,
                    "rec_engine_ms" to metrics.recommendationEngineMs,
                    "builder_ms" to metrics.workoutBuilderMs,
                    "hydration_ms" to metrics.hydrationMs,
                    "room_ms" to metrics.roomQueriesMs,
                    "time_to_screen_ms" to metrics.timeToOpenScreenMs
                )
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to log telemetry benchmark", e)
        }
    }
}
