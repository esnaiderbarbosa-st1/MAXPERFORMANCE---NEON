package com.example.services.telemetry

import android.content.Context
import com.example.data.model.TelemetryEventType

/**
 * Global static facade providing easy, zero-overhead telemetry actions.
 * Encapsulates manager access to prevent runtime crashes during initial startup phases.
 */
object Telemetry {

    fun initialize(context: Context) {
        TelemetryManager.initialize(context)
    }

    /**
     * Logs a general analytical event. Safe to call from anywhere, even if not yet fully initialized.
     */
    fun log(eventType: TelemetryEventType, metadata: Map<String, Any> = emptyMap()) {
        try {
            TelemetryManager.getInstance().log(eventType, metadata)
        } catch (e: Exception) {
            android.util.Log.w("Telemetry", "Telemetry event skipped before initialization: ${eventType.name}")
        }
    }

    /**
     * Captures and logs a stacktrace.
     */
    fun logCrash(throwable: Throwable) {
        try {
            TelemetryManager.getInstance().logCrash(throwable)
        } catch (e: Exception) {
            android.util.Log.w("Telemetry", "Telemetry crash log skipped before initialization")
        }
    }

    /**
     * Binds the current active user ID to outgoing telemetry events.
     */
    fun setUserId(userId: String?) {
        try {
            TelemetryManager.getInstance().setUserId(userId)
        } catch (e: Exception) {}
    }

    /**
     * Binds the current active screen context to outgoing telemetry events.
     */
    fun setScreen(screenName: String?) {
        try {
            TelemetryManager.getInstance().setScreen(screenName)
        } catch (e: Exception) {}
    }
}
