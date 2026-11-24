package com.example.services.telemetry

import com.example.data.database.TelemetryDao
import com.example.data.model.TelemetryEventEntity
import com.example.data.model.CrashReportEntity
import kotlinx.coroutines.flow.Flow

/**
 * Storage coordinator directly communicating with the Room Telemetry Dao.
 * Keeps storage-specific logic encapsulated, facilitating future migrations (e.g. to raw SQLite or SQLDelight).
 */
class TelemetryStorage(private val telemetryDao: TelemetryDao) {

    suspend fun saveEvent(event: TelemetryEventEntity) {
        telemetryDao.insertEvent(event)
    }

    suspend fun saveCrash(crash: CrashReportEntity) {
        telemetryDao.insertCrash(crash)
    }

    suspend fun deleteEventById(id: String) {
        telemetryDao.deleteEventById(id)
    }

    suspend fun purgeOlderThan(timestamp: Long) {
        telemetryDao.deleteEventsOlderThan(timestamp)
        telemetryDao.deleteCrashesOlderThan(timestamp)
    }

    fun getAllEventsFlow(): Flow<List<TelemetryEventEntity>> {
        return telemetryDao.getAllEventsFlow()
    }

    suspend fun getAllEvents(): List<TelemetryEventEntity> {
        return telemetryDao.getAllEvents()
    }

    fun getEventsByUser(userId: String): Flow<List<TelemetryEventEntity>> {
        return telemetryDao.getEventsByUser(userId)
    }

    fun getEventsByType(eventType: String): Flow<List<TelemetryEventEntity>> {
        return telemetryDao.getEventsByType(eventType)
    }

    suspend fun getUnsyncedEvents(): List<TelemetryEventEntity> {
        return telemetryDao.getUnsyncedEvents()
    }

    suspend fun markEventsAsSynced(ids: List<String>) {
        telemetryDao.markEventsAsSynced(ids)
    }

    fun getEventCountFlow(): Flow<Int> {
        return telemetryDao.getEventCountFlow()
    }

    suspend fun getEventCount(): Int {
        return telemetryDao.getEventCount()
    }

    fun getCrashCountFlow(): Flow<Int> {
        return telemetryDao.getCrashCountFlow()
    }

    suspend fun getCrashCount(): Int {
        return telemetryDao.getCrashCount()
    }

    fun getAllCrashesFlow(): Flow<List<CrashReportEntity>> {
        return telemetryDao.getAllCrashesFlow()
    }

    suspend fun getAllCrashes(): List<CrashReportEntity> {
        return telemetryDao.getAllCrashes()
    }
}
