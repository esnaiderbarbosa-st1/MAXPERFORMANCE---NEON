package com.example.services.integration

import android.content.Context
import android.os.Build
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.example.data.model.*
import com.example.services.domain.HealthEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Unified wearable sensor metrics synced from a smartwatch or fitness platform.
 */
data class WearableMetrics(
    val steps: Int,
    val averageHeartRate: Int,
    val activeCalories: Int,
    val sleepingHours: Float,
    val hydrationMl: Int,
    val stressLevel: Int, // scale 1-100
    val batteryBodyPercent: Int, // e.g. Garmin Body Battery
    val hrvStatusMs: Int, // Heart Rate Variability
    val timestamp: Long = System.currentTimeMillis()
) {
    companion object {
        fun empty(timestamp: Long = System.currentTimeMillis()) = WearableMetrics(
            steps = 0,
            averageHeartRate = 0,
            activeCalories = 0,
            sleepingHours = 0f,
            hydrationMl = 0,
            stressLevel = 0,
            batteryBodyPercent = 0,
            hrvStatusMs = 0,
            timestamp = timestamp
        )
    }
}

data class WorkoutHealthConnectSnapshot(
    val avgHr: Int,
    val maxHr: Int,
    val hrTimeline: String,
    val steps: Int,
    val activeCalories: Int,
    val sampleCount: Int
)

/**
 * Universal interface defining data contracts for third-party smartwatch integrations.
 */
interface SmartwatchConnector {
    val source: IntegrationSource
    fun isAvailable(): Boolean
    suspend fun connect(): Result<Boolean>
    suspend fun disconnect(): Result<Boolean>
    suspend fun fetchLatestMetrics(): Result<WearableMetrics>
    suspend fun fetchLatestCardioSessions(sinceTimestamp: Long): Result<List<CardioSession>>
}

/**
 * Real Google Health Connect integration using AndroidX HealthConnectClient.
 * Never returns mocked/synthetic values: returns zeros when unavailable, unpermitted, or without records.
 */
class HealthConnectConnector(private val context: Context) : SmartwatchConnector {

    companion object {
        val REQUIRED_PERMISSIONS: Set<String> = setOf(
            HealthPermission.getReadPermission(HeartRateRecord::class),
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
            HealthPermission.getReadPermission(DistanceRecord::class),
            HealthPermission.getReadPermission(ExerciseSessionRecord::class),
            HealthPermission.getReadPermission(SleepSessionRecord::class)
        )
    }

    override val source = IntegrationSource.HEALTH_CONNECT

    override fun isAvailable(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        return runCatching {
            HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE
        }.getOrDefault(false)
    }

    suspend fun hasAllPermissions(): Boolean {
        if (!isAvailable()) return false
        return runCatching {
            val client = HealthConnectClient.getOrCreate(context)
            client.permissionController.getGrantedPermissions().containsAll(REQUIRED_PERMISSIONS)
        }.getOrDefault(false)
    }

    override suspend fun connect(): Result<Boolean> = runCatching {
        isAvailable() && hasAllPermissions()
    }

    override suspend fun disconnect(): Result<Boolean> = Result.success(true)

    override suspend fun fetchLatestMetrics(): Result<WearableMetrics> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || !isAvailable()) {
            return Result.success(WearableMetrics.empty())
        }
        return runCatching {
            if (!hasAllPermissions()) {
                return@runCatching WearableMetrics.empty()
            }

            val client = HealthConnectClient.getOrCreate(context)
            val now = Instant.now()
            val startOfDay = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant()
            val last24hStart = now.minus(24, ChronoUnit.HOURS)

            val todayAggregate = runCatching {
                client.aggregate(
                    AggregateRequest(
                        metrics = setOf(
                            StepsRecord.COUNT_TOTAL,
                            ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL,
                            HeartRateRecord.BPM_AVG
                        ),
                        timeRangeFilter = TimeRangeFilter.between(startOfDay, now)
                    )
                )
            }.getOrNull()

            val steps = (todayAggregate?.get(StepsRecord.COUNT_TOTAL) ?: 0L)
                .toInt()
                .coerceAtLeast(0)
            val activeCalories = (todayAggregate?.get(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)?.inKilocalories ?: 0.0)
                .toInt()
                .coerceAtLeast(0)
            val averageHeartRate = (todayAggregate?.get(HeartRateRecord.BPM_AVG) ?: 0L)
                .toInt()
                .coerceAtLeast(0)

            val sleepingHours = runCatching {
                val sleepResponse = client.readRecords(
                    ReadRecordsRequest(
                        recordType = SleepSessionRecord::class,
                        timeRangeFilter = TimeRangeFilter.between(last24hStart, now)
                    )
                )
                val totalMinutes = sleepResponse.records.sumOf { record ->
                    Duration.between(record.startTime, record.endTime).toMinutes().coerceAtLeast(0L)
                }
                totalMinutes / 60f
            }.getOrDefault(0f)

            WearableMetrics(
                steps = steps,
                averageHeartRate = averageHeartRate,
                activeCalories = activeCalories,
                sleepingHours = sleepingHours,
                hydrationMl = 0,
                stressLevel = 0,
                batteryBodyPercent = 0,
                hrvStatusMs = 0,
                timestamp = System.currentTimeMillis()
            )
        }.recover {
            WearableMetrics.empty()
        }
    }

    /**
     * Queries Health Connect for HeartRateRecord, StepsRecord, and ActiveCaloriesBurnedRecord
     * within [startTimeMillis - 2 min, endTimeMillis + 2 min] to align smartwatch clock offsets.
     * Resamples HR samples every 10 seconds in "sec:bpm,sec:bpm" format.
     */
    suspend fun fetchWorkoutWindowMetrics(
        startTimeMillis: Long,
        endTimeMillis: Long
    ): WorkoutHealthConnectSnapshot? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || !isAvailable()) return null
        return runCatching {
            if (!hasAllPermissions()) return@runCatching null

            val client = HealthConnectClient.getOrCreate(context)
            val safeEndMs = endTimeMillis.coerceAtLeast(startTimeMillis + 60_000L)
            val windowStart = Instant.ofEpochMilli((startTimeMillis - 120_000L).coerceAtLeast(0L))
            val windowEnd = Instant.ofEpochMilli(safeEndMs + 120_000L)
            val timeRange = TimeRangeFilter.between(windowStart, windowEnd)

            val hrResponse = runCatching {
                client.readRecords(
                    ReadRecordsRequest(
                        recordType = HeartRateRecord::class,
                        timeRangeFilter = timeRange
                    )
                )
            }.getOrNull()

            val validSamples = hrResponse?.records
                .orEmpty()
                .flatMap { it.samples }
                .filter { it.beatsPerMinute in 30L..240L }
                .sortedBy { it.time }

            val sampleCount = validSamples.size
            val avgHr = if (sampleCount > 0) {
                validSamples.map { it.beatsPerMinute.toDouble() }.average().toInt()
            } else {
                0
            }
            val maxHr = if (sampleCount > 0) {
                validSamples.maxOf { it.beatsPerMinute.toInt() }
            } else {
                0
            }

            val hrTimeline = if (sampleCount > 0) {
                validSamples
                    .groupBy { sample ->
                        val sec = ((sample.time.toEpochMilli() - startTimeMillis) / 1000L).coerceAtLeast(0L)
                        (sec / 10L) * 10L
                    }
                    .toSortedMap()
                    .entries
                    .joinToString(",") { (bucketSec, bucketSamples) ->
                        val bucketAvg = bucketSamples.map { it.beatsPerMinute.toDouble() }.average().toInt()
                        "$bucketSec:$bucketAvg"
                    }
            } else {
                ""
            }

            val windowAggregate = runCatching {
                client.aggregate(
                    AggregateRequest(
                        metrics = setOf(
                            StepsRecord.COUNT_TOTAL,
                            ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL
                        ),
                        timeRangeFilter = timeRange
                    )
                )
            }.getOrNull()

            var steps = (windowAggregate?.get(StepsRecord.COUNT_TOTAL) ?: 0L)
                .toInt()
                .coerceAtLeast(0)
            var activeCalories = (windowAggregate?.get(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)?.inKilocalories ?: 0.0)
                .toInt()
                .coerceAtLeast(0)

            if (steps == 0) {
                steps = runCatching {
                    client.readRecords(
                        ReadRecordsRequest(
                            recordType = StepsRecord::class,
                            timeRangeFilter = timeRange
                        )
                    ).records.sumOf { it.count }.toInt().coerceAtLeast(0)
                }.getOrDefault(0)
            }

            if (activeCalories == 0) {
                activeCalories = runCatching {
                    client.readRecords(
                        ReadRecordsRequest(
                            recordType = ActiveCaloriesBurnedRecord::class,
                            timeRangeFilter = timeRange
                        )
                    ).records.sumOf { it.energy.inKilocalories }.toInt().coerceAtLeast(0)
                }.getOrDefault(0)
            }

            if (sampleCount == 0 && steps <= 0) {
                return@runCatching null
            }

            WorkoutHealthConnectSnapshot(
                avgHr = avgHr,
                maxHr = maxHr,
                hrTimeline = hrTimeline,
                steps = steps,
                activeCalories = activeCalories,
                sampleCount = sampleCount
            )
        }.getOrNull()
    }

    override suspend fun fetchLatestCardioSessions(sinceTimestamp: Long): Result<List<CardioSession>> {
        return Result.success(emptyList())
    }
}

/**
 * Placeholder connector for future Garmin Connect Developer API integration.
 * Returns no mocked metrics or synthetic sessions.
 */
class GarminConnector : SmartwatchConnector {
    override val source = IntegrationSource.GARMIN
    override fun isAvailable(): Boolean = false
    override suspend fun connect(): Result<Boolean> = Result.success(false)
    override suspend fun disconnect(): Result<Boolean> = Result.success(true)

    override suspend fun fetchLatestMetrics(): Result<WearableMetrics> {
        return Result.success(WearableMetrics.empty())
    }

    override suspend fun fetchLatestCardioSessions(sinceTimestamp: Long): Result<List<CardioSession>> {
        return Result.success(emptyList())
    }
}

/**
 * Placeholder connector for future Polar AccessLink API integration.
 * Returns no mocked metrics or synthetic sessions.
 */
class PolarConnector : SmartwatchConnector {
    override val source = IntegrationSource.POLAR
    override fun isAvailable(): Boolean = false
    override suspend fun connect(): Result<Boolean> = Result.success(false)
    override suspend fun disconnect(): Result<Boolean> = Result.success(true)

    override suspend fun fetchLatestMetrics(): Result<WearableMetrics> {
        return Result.success(WearableMetrics.empty())
    }

    override suspend fun fetchLatestCardioSessions(sinceTimestamp: Long): Result<List<CardioSession>> {
        return Result.success(emptyList())
    }
}

/**
 * Central orchestrator managing active smartwatch connectors and validating metrics.
 */
class SmartwatchIntegrationManager(private val context: Context? = null) {

    private val _enabledSources = MutableStateFlow<Set<IntegrationSource>>(setOf(IntegrationSource.MANUAL))
    val enabledSources: StateFlow<Set<IntegrationSource>> = _enabledSources.asStateFlow()

    private val connectors: Map<IntegrationSource, SmartwatchConnector> = buildMap {
        if (context != null) {
            put(IntegrationSource.HEALTH_CONNECT, HealthConnectConnector(context.applicationContext))
        }
        put(IntegrationSource.GARMIN, GarminConnector())
        put(IntegrationSource.POLAR, PolarConnector())
    }

    fun enableSource(source: IntegrationSource) {
        val current = _enabledSources.value.toMutableSet()
        current.add(source)
        _enabledSources.value = current
    }

    fun disableSource(source: IntegrationSource) {
        val current = _enabledSources.value.toMutableSet()
        current.remove(source)
        _enabledSources.value = current
    }

    /**
     * Syncs latest health metrics across all authorized third-party platforms.
     * Incorporates central validation via HealthEngine to assert sanity of wearable data.
     */
    suspend fun syncAndValidateAll(): List<CardioSession> {
        val sessionsToSync = mutableListOf<CardioSession>()

        for (source in _enabledSources.value) {
            val connector = connectors[source] ?: continue
            if (connector.isAvailable()) {
                val sessionsResult = connector.fetchLatestCardioSessions(System.currentTimeMillis() - 86400000L)
                sessionsResult.onSuccess { sessions ->
                    val validatedSessions = sessions.filter { session ->
                        when (val validation = HealthEngine.validateCardioSession(session)) {
                            is ValidationResult.Valid -> true
                            is ValidationResult.Invalid -> {
                                android.util.Log.e("SmartwatchManager", "Discarding corrupt wearable session: ${validation.message}")
                                false
                            }
                        }
                    }
                    sessionsToSync.addAll(validatedSessions)
                }
            }
        }

        return sessionsToSync
    }
}
