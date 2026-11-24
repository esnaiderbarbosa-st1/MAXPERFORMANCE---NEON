package com.example.services.telemetry

import android.content.Context
import android.os.Build
import com.example.data.database.AppDatabase
import com.example.data.model.CrashReportEntity
import com.example.data.model.TelemetryEventEntity
import com.example.data.model.TelemetryEventType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.PrintWriter
import java.io.StringWriter
import java.util.UUID

/**
 * Orchestrator managing telemetry sessions and event logging. Runs asynchronously on a background
 * supervisor context to prevent performance impacts or application crashes.
 */
class TelemetryManager private constructor(
    val repository: TelemetryRepository,
    private val context: Context
) {
    // Unique session ID regenerated upon every fresh application execution
    val sessionId: String = UUID.randomUUID().toString()

    // Isolated scope ensuring telemetry tasks are fully asynchronous and never block user actions
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var currentUserId: String? = null
    private var currentScreen: String? = null

    fun setUserId(userId: String?) {
        currentUserId = userId
    }

    fun setScreen(screenName: String?) {
        currentScreen = screenName
    }

    /**
     * Logs a general diagnostic, usage, or sync event. Failures are handled silently.
     */
    fun log(
        eventType: TelemetryEventType,
        metadata: Map<String, Any> = emptyMap(),
        screenName: String? = null
    ) {
        scope.launch {
            try {
                val metadataJson = serializeMetadata(metadata)
                val event = TelemetryEventEntity(
                    id = UUID.randomUUID().toString(),
                    timestamp = System.currentTimeMillis(),
                    sessionId = sessionId,
                    eventType = eventType.name,
                    userId = currentUserId,
                    screen = screenName ?: currentScreen,
                    appVersion = getAppVersion(),
                    androidVersion = Build.VERSION.RELEASE,
                    deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
                    metadataJson = metadataJson,
                    synced = false
                )
                repository.recordEvent(event)
            } catch (e: Throwable) {
                // Fail silently to safeguard user experience
                android.util.Log.w("TelemetryManager", "Silent warning logging telemetry event", e)
            }
        }
    }

    /**
     * Gracefully logs and persists unexpected/caught application exceptions.
     */
    fun logCrash(throwable: Throwable, screenName: String? = null) {
        scope.launch {
            try {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                val stacktrace = sw.toString()

                val crash = CrashReportEntity(
                    id = UUID.randomUUID().toString(),
                    exceptionClass = throwable.javaClass.name,
                    message = throwable.localizedMessage ?: throwable.message,
                    stacktrace = stacktrace,
                    screen = screenName ?: currentScreen,
                    timestamp = System.currentTimeMillis(),
                    sessionId = sessionId,
                    appVersion = getAppVersion(),
                    androidVersion = Build.VERSION.RELEASE,
                    deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}"
                )
                repository.recordCrash(crash)

                // Double record as an ERROR telemetry event for general dashboard indexing
                log(
                    TelemetryEventType.ERROR,
                    metadata = mapOf(
                        "exceptionClass" to throwable.javaClass.name,
                        "errorMessage" to (throwable.message ?: "No description provided")
                    ),
                    screenName = screenName
                )
            } catch (e: Throwable) {
                android.util.Log.w("TelemetryManager", "Silent warning logging exception report", e)
            }
        }
    }

    private fun getAppVersion(): String {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "1.4.0"
        } catch (e: Exception) {
            "1.4.0"
        }
    }

    private fun serializeMetadata(metadata: Map<String, Any>): String {
        if (metadata.isEmpty()) return "{}"
        return try {
            val sb = StringBuilder()
            sb.append("{")
            val entries = metadata.entries.toList()
            for (i in entries.indices) {
                val entry = entries[i]
                sb.append("\"").append(entry.key).append("\":")
                val value = entry.value
                if (value is Number || value is Boolean) {
                    sb.append(value)
                } else {
                    sb.append("\"").append(value.toString().replace("\"", "\\\"")).append("\"")
                }
                if (i < entries.size - 1) {
                    sb.append(",")
                }
            }
            sb.append("}")
            sb.toString()
        } catch (e: Exception) {
            "{}"
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: TelemetryManager? = null

        fun initialize(context: Context): TelemetryManager {
            return INSTANCE ?: synchronized(this) {
                val database = AppDatabase.getDatabase(context.applicationContext)
                val storage = TelemetryStorage(database.telemetryDao())
                val repository = TelemetryRepository(storage)
                val manager = TelemetryManager(repository, context.applicationContext)
                INSTANCE = manager
                
                // Log app launch telemetry automatically upon initialization
                manager.log(TelemetryEventType.APP_STARTED, mapOf("mode" to "release_ready"))
                manager
            }
        }

        fun getInstance(): TelemetryManager {
            return INSTANCE ?: throw IllegalStateException("TelemetryManager must be initialized first!")
        }
    }
}
