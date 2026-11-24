package com.example.services.telemetry

import com.example.data.model.TelemetryEventEntity
import com.example.data.model.CrashReportEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Repository in charge of loading/persisting telemetry logs and coordinating background sync.
 */
class TelemetryRepository(private val storage: TelemetryStorage) {

    suspend fun recordEvent(event: TelemetryEventEntity) = withContext(Dispatchers.IO) {
        storage.saveEvent(event)
    }

    suspend fun recordCrash(crash: CrashReportEntity) = withContext(Dispatchers.IO) {
        storage.saveCrash(crash)
    }

    suspend fun deleteEvent(id: String) = withContext(Dispatchers.IO) {
        storage.deleteEventById(id)
    }

    suspend fun purgeDataOlderThanDays(days: Int) = withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - (days * 24L * 60L * 60L * 1000L)
        storage.purgeOlderThan(cutoff)
    }

    fun observeAllEvents(): Flow<List<TelemetryEventEntity>> {
        return storage.getAllEventsFlow()
    }

    suspend fun loadAllEvents(): List<TelemetryEventEntity> = withContext(Dispatchers.IO) {
        storage.getAllEvents()
    }

    fun observeEventsByUser(userId: String): Flow<List<TelemetryEventEntity>> {
        return storage.getEventsByUser(userId)
    }

    fun observeEventsByType(eventType: String): Flow<List<TelemetryEventEntity>> {
        return storage.getEventsByType(eventType)
    }

    suspend fun loadUnsyncedEvents(): List<TelemetryEventEntity> = withContext(Dispatchers.IO) {
        storage.getUnsyncedEvents()
    }

    suspend fun markAsSynced(ids: List<String>) = withContext(Dispatchers.IO) {
        if (ids.isNotEmpty()) {
            storage.markEventsAsSynced(ids)
        }
    }

    fun observeEventCount(): Flow<Int> {
        return storage.getEventCountFlow()
    }

    suspend fun getEventCount(): Int = withContext(Dispatchers.IO) {
        storage.getEventCount()
    }

    fun observeCrashCount(): Flow<Int> {
        return storage.getCrashCountFlow()
    }

    suspend fun getCrashCount(): Int = withContext(Dispatchers.IO) {
        storage.getCrashCount()
    }

    fun observeAllCrashes(): Flow<List<CrashReportEntity>> {
        return storage.getAllCrashesFlow()
    }

    suspend fun loadAllCrashes(): List<CrashReportEntity> = withContext(Dispatchers.IO) {
        storage.getAllCrashes()
    }

    /**
     * Preparation contract for Future Cloud Synchronization.
     * When cloud uploads are enabled, this method will push unsynced records to the remote database.
     */
    suspend fun synchronizeWithCloud(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val unsynced = loadUnsyncedEvents()
            if (unsynced.isEmpty()) {
                return@withContext Result.success(Unit)
            }
            // Mock remote sync call logic here (future integration endpoint)
            // val remoteSuccessIds = remoteApi.upload(unsynced)
            // markAsSynced(remoteSuccessIds)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
