package com.example.utils

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class IllustrationMissingLog(
    val exerciseName: String,
    val illustrationId: String,
    val equipment: String,
    val timestamp: Long = System.currentTimeMillis(),
    val buildVersion: String = "2.4.0-BETA (#20260724)"
) {
    val formattedTimestamp: String
        get() {
            val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
            return sdf.format(Date(timestamp))
        }

    val logOutput: String
        get() = """
            [Illustration Missing]
            Exercise: $exerciseName
            illustrationId: $illustrationId
            Equipment: $equipment
            Timestamp: $timestamp ($formattedTimestamp)
            Build: $buildVersion
        """.trimIndent()
}

object IllustrationMetricsManager {
    // Total library size metrics
    val totalNeededIllustrations: Int = 168
    
    // Curated available exact/alias illustrations in library
    private val _availableIllustrationsCount = MutableStateFlow(118)
    val availableIllustrationsCount: StateFlow<Int> = _availableIllustrationsCount.asStateFlow()

    // Session fallback counter
    private val _sessionFallbackCount = MutableStateFlow(0)
    val sessionFallbackCount: StateFlow<Int> = _sessionFallbackCount.asStateFlow()

    // Missing logs recorded during app session
    private val _missingLogs = MutableStateFlow<List<IllustrationMissingLog>>(emptyList())
    val missingLogs: StateFlow<List<IllustrationMissingLog>> = _missingLogs.asStateFlow()

    // Set of missing keys recorded in session to avoid spamming identical logs
    private val loggedKeys = mutableSetOf<String>()

    fun logMissingIllustration(exerciseName: String, illustrationId: String?, equipment: String?) {
        val safeIllustrationId = if (!illustrationId.isNullOrBlank()) illustrationId else "unmapped"
        val safeEquipment = if (!equipment.isNullOrBlank()) equipment.uppercase(Locale.ROOT) else "UNKNOWN"
        val logKey = "$exerciseName:$safeIllustrationId"

        val logEntry = IllustrationMissingLog(
            exerciseName = exerciseName,
            illustrationId = safeIllustrationId,
            equipment = safeEquipment
        )

        // Log to Android log pipeline
        Log.w(
            "IllustrationPipeline",
            "\n[Illustration Missing]\nExercise: $exerciseName\nillustrationId: $safeIllustrationId\nEquipment: $safeEquipment\nTimestamp: ${logEntry.timestamp}\nBuild: ${logEntry.buildVersion}"
        )

        if (!loggedKeys.contains(logKey)) {
            loggedKeys.add(logKey)
            _sessionFallbackCount.value += 1
            _missingLogs.value = listOf(logEntry) + _missingLogs.value
        }
    }

    fun clearSessionLogs() {
        loggedKeys.clear()
        _sessionFallbackCount.value = 0
        _missingLogs.value = emptyList()
    }

    fun getCoveragePercentage(): Float {
        if (totalNeededIllustrations == 0) return 100f
        return (availableIllustrationsCount.value.toFloat() / totalNeededIllustrations.toFloat()) * 100f
    }
}
