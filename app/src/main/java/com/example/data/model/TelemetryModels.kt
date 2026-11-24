package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

enum class TelemetryEventType {
    APP_STARTED,
    APP_CLOSED,
    SCREEN_OPENED,
    SCREEN_CLOSED,
    WORKOUT_STARTED,
    WORKOUT_FINISHED,
    AI_REQUEST,
    AI_RESPONSE,
    BIOIMPEDANCE_IMPORTED,
    CARDIO_IMPORTED,
    RECOVERY_VIEWED,
    SYNC_STARTED,
    SYNC_COMPLETED,
    SYNC_FAILED,
    WARNING,
    ERROR,
    RECOVERY_UPDATED,
    HOME_REFRESHED,
    SCREEN_KEEP_ON_ENABLED,
    SCREEN_KEEP_ON_DISABLED,
    PROGRESSION_CHANGED,
    LAST_WEIGHT_LOADED,
    WORKOUT_GENERATED,
    METHOD_SELECTED,
    EXERCISE_DISCARDED,
    RECOVERY_REDUCED_VOLUME,
    EQUIPMENT_FALLBACK,
    PERFORMANCE_BENCHMARK,
    SESSION_PREP_STARTED,
    SESSION_PREP_FINISHED,
    SESSION_PREP_FAILED,
    SESSION_PREP_CACHE_HIT,
    SESSION_PREP_CACHE_MISS,
    CUSTOM
}

@Entity(tableName = "telemetry_events")
data class TelemetryEventEntity(
    @PrimaryKey val id: String,
    val timestamp: Long,
    val sessionId: String,
    val eventType: String,
    val userId: String?,
    val screen: String?,
    val appVersion: String,
    val androidVersion: String,
    val deviceModel: String,
    val metadataJson: String,
    val synced: Boolean
) : Serializable

@Entity(tableName = "crash_reports")
data class CrashReportEntity(
    @PrimaryKey val id: String,
    val exceptionClass: String,
    val message: String?,
    val stacktrace: String,
    val screen: String?,
    val timestamp: Long,
    val sessionId: String,
    val appVersion: String,
    val androidVersion: String,
    val deviceModel: String
) : Serializable
