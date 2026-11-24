package com.example.services.telemetry

import android.os.Build
import android.os.SystemClock
import android.util.Log

data class PerformanceThresholds(
    var decisionEngineMs: Double = 20.0,
    var recommendationEngineMs: Double = 50.0,
    var workoutBuilderMs: Double = 40.0,
    var hydrationMs: Double = 20.0,
    var historyCacheMs: Double = 20.0,
    var jsonParsingMs: Double = 20.0,
    var roomQueriesMs: Double = 20.0,
    var navigationMs: Double = 30.0,
    var totalSessionPrepMs: Double = 120.0
)

data class SessionPrepStepState(
    val isPreparing: Boolean = false,
    val currentStepText: String = "",
    val completedSteps: List<String> = emptyList()
)

object PerformanceMonitor {
    private const val TAG = "MAX_PERFORMANCE_BENCHMARK"

    var thresholds = PerformanceThresholds()

    class SessionTrace(
        val workoutId: String,
        val workoutName: String,
        val gymContext: String,
        val workoutType: String = "Hipertrofia"
    ) {
        val startNanos: Long = SystemClock.elapsedRealtimeNanos()
        
        var decisionEngineNanos: Long = 0L
        var recommendationEngineNanos: Long = 0L
        var workoutCompositionEngineNanos: Long = 0L
        var trainingExperienceLayerNanos: Long = 0L
        var exerciseKnowledgeGraphNanos: Long = 0L
        var workoutBuilderNanos: Long = 0L
        var hydrationNanos: Long = 0L
        var historicalLoadNanos: Long = 0L
        var historyCacheNanos: Long = 0L
        var jsonParsingNanos: Long = 0L
        var roomQueriesNanos: Long = 0L
        var navigationNanos: Long = 0L

        var isCacheHit: Boolean = false
        var exerciseCount: Int = 0

        inline fun <T> measureStep(stepSetter: (Long) -> Unit, block: () -> T): T {
            val stepStart = SystemClock.elapsedRealtimeNanos()
            try {
                return block()
            } finally {
                val duration = SystemClock.elapsedRealtimeNanos() - stepStart
                stepSetter(duration)
            }
        }

        fun finishTrace(isSuccess: Boolean = true, errorMessage: String? = null) {
            val totalNanos = SystemClock.elapsedRealtimeNanos() - startNanos
            val totalMs = totalNanos / 1_000_000.0

            val decMs = decisionEngineNanos / 1_000_000.0
            val recMs = recommendationEngineNanos / 1_000_000.0
            val wceMs = workoutCompositionEngineNanos / 1_000_000.0
            val telMs = trainingExperienceLayerNanos / 1_000_000.0
            val ekgMs = exerciseKnowledgeGraphNanos / 1_000_000.0
            val builderMs = workoutBuilderNanos / 1_000_000.0
            val hydMs = hydrationNanos / 1_000_000.0
            val histMs = historyCacheNanos / 1_000_000.0
            val navMs = navigationNanos / 1_000_000.0

            val deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}"
            val androidVersion = "Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})"

            // Format consolidated report
            val sb = StringBuilder()
            sb.append("\n==============================\n")
            sb.append("MAX PERFORMANCE BENCHMARK\n")
            sb.append("==============================\n")
            sb.append(String.format("%-28s %.2f ms\n", "Decision Engine .............", decMs))
            sb.append(String.format("%-28s %.2f ms\n", "Recommendation Engine .......", recMs))
            if (wceMs > 0) sb.append(String.format("%-28s %.2f ms\n", "Workout Composition Engine .", wceMs))
            if (telMs > 0) sb.append(String.format("%-28s %.2f ms\n", "Training Experience Layer ..", telMs))
            if (ekgMs > 0) sb.append(String.format("%-28s %.2f ms\n", "Exercise Knowledge Graph ...", ekgMs))
            sb.append(String.format("%-28s %.2f ms\n", "Workout Builder .............", builderMs))
            sb.append(String.format("%-28s %.2f ms\n", "Hydration ...................", hydMs))
            sb.append(String.format("%-28s %.2f ms\n", "History Cache ...............", histMs))
            sb.append(String.format("%-28s %.2f ms\n", "Navigation ..................", navMs))
            sb.append("------------------------------\n")
            sb.append(String.format("%-28s %.2f ms\n\n", "TOTAL .......................", totalMs))
            sb.append("Cache Hit: ").append(if (isCacheHit) "YES" else "NO").append("\n")
            sb.append("Gym Context: ").append(gymContext).append("\n")
            sb.append("Workout: ").append(workoutName).append("\n")
            sb.append("Exercises Count: ").append(exerciseCount).append("\n")
            sb.append("Device: ").append(deviceModel).append(" (").append(androidVersion).append(")\n")
            sb.append("==============================")

            Log.i(TAG, sb.toString())

            // Bottleneck Detection & Threshold Warnings
            checkThreshold("Decision Engine", decMs, thresholds.decisionEngineMs)
            checkThreshold("Recommendation Engine", recMs, thresholds.recommendationEngineMs)
            checkThreshold("Workout Builder", builderMs, thresholds.workoutBuilderMs)
            checkThreshold("Hydration", hydMs, thresholds.hydrationMs)
            checkThreshold("History Cache", histMs, thresholds.historyCacheMs)
            checkThreshold("Navigation", navMs, thresholds.navigationMs)
            checkThreshold("Total Session Prep", totalMs, thresholds.totalSessionPrepMs)

            // Log Telemetry Events
            try {
                if (isSuccess) {
                    Telemetry.log(
                        com.example.data.model.TelemetryEventType.SESSION_PREP_FINISHED,
                        mapOf(
                            "workout_id" to workoutId,
                            "workout_name" to workoutName,
                            "workout_type" to workoutType,
                            "total_time_ms" to totalMs,
                            "cache_hit" to isCacheHit,
                            "gym_context" to gymContext,
                            "exercise_count" to exerciseCount,
                            "device" to deviceModel,
                            "android_version" to androidVersion
                        )
                    )
                    Telemetry.log(
                        if (isCacheHit) com.example.data.model.TelemetryEventType.SESSION_PREP_CACHE_HIT
                        else com.example.data.model.TelemetryEventType.SESSION_PREP_CACHE_MISS,
                        mapOf("workout_id" to workoutId, "gym_context" to gymContext)
                    )
                } else {
                    Telemetry.log(
                        com.example.data.model.TelemetryEventType.SESSION_PREP_FAILED,
                        mapOf(
                            "workout_id" to workoutId,
                            "error" to (errorMessage ?: "Unknown error during prep")
                        )
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to send telemetry for session prep", e)
            }
        }

        private fun checkThreshold(stepName: String, actualMs: Double, thresholdMs: Double) {
            if (actualMs > thresholdMs) {
                Log.w(
                    TAG,
                    "WARNING: $stepName exceeded threshold (%.1f ms): %.2f ms".format(
                        thresholdMs, actualMs
                    )
                )
            }
        }
    }

    fun startSessionPrepTrace(
        workoutId: String,
        workoutName: String,
        gymContext: String,
        workoutType: String = "Hipertrofia"
    ): SessionTrace {
        val trace = SessionTrace(workoutId, workoutName, gymContext, workoutType)
        try {
            Telemetry.log(
                com.example.data.model.TelemetryEventType.SESSION_PREP_STARTED,
                mapOf(
                    "workout_id" to workoutId,
                    "workout_name" to workoutName,
                    "gym_context" to gymContext
                )
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to log SESSION_PREP_STARTED", e)
        }
        return trace
    }
}
