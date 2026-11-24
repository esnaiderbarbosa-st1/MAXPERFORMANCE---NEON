package com.example.services.diagnostics

/**
 * Snapshot representing the structural, physical, and virtual status of the application.
 * Indispensable for support diagnostics, remote error reporting, and administrators.
 */
data class DiagnosticsReport(
    val appVersion: String,
    val androidVersion: String,
    val deviceModel: String,
    val databaseStatus: String, // e.g., "OK", "CORRUPTED", "UNKNOWN"
    val syncStatus: String, // e.g., "SYNCED", "PENDING_SYNC", "ERROR"
    val lastSyncTimestamp: Long,
    val pendingSyncCount: Int,
    val totalTelemetryCount: Int,
    val totalCrashCount: Int,
    val lastCrashTimestamp: Long?,
    val aiStatus: String, // e.g., "READY", "OFFLINE_FALLBACK"
    val healthIntegrationStatus: Map<String, Boolean> // e.g., "HEALTH_CONNECT" -> true, "GARMIN" -> false
)

/**
 * Interface defining self-diagnostic contracts, fully decoupled from user screens.
 */
interface DiagnosticsProvider {
    suspend fun generateReport(): DiagnosticsReport
    suspend fun clearOldLogs(): Result<Unit>
    suspend fun runSelfTests(): Result<Boolean>
}
