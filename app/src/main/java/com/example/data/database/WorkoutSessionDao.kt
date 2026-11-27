package com.example.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.data.model.WorkoutSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutSessionDao {
    @Query("SELECT * FROM workout_sessions WHERE status = 'ATIVO'")
    fun getAllActiveSessionsFlow(): Flow<List<WorkoutSessionEntity>>

    @Query("SELECT * FROM workout_sessions ORDER BY startTime DESC")
    suspend fun getAllSessions(): List<WorkoutSessionEntity>

    @Query("SELECT * FROM workout_sessions WHERE status = 'ATIVO'")
    suspend fun getAllActiveSessions(): List<WorkoutSessionEntity>

    @Query("SELECT * FROM workout_sessions WHERE status = 'ATIVO' ORDER BY startTime DESC, version DESC LIMIT 1")
    fun getActiveSessionFlow(): Flow<WorkoutSessionEntity?>

    @Query("SELECT * FROM workout_sessions WHERE status = 'ATIVO' ORDER BY startTime DESC, version DESC LIMIT 1")
    suspend fun getActiveSession(): WorkoutSessionEntity?

    @Query("SELECT * FROM workout_sessions WHERE id = :sessionId LIMIT 1")
    suspend fun getSessionById(sessionId: String): WorkoutSessionEntity?

    @Query("SELECT * FROM workout_sessions WHERE workoutId = :workoutId AND startTime = :startTime ORDER BY version DESC LIMIT 1")
    suspend fun getSessionByWorkoutAndStartTime(workoutId: String, startTime: Long): WorkoutSessionEntity?

    @Query("SELECT version FROM workout_sessions WHERE id = :sessionId LIMIT 1")
    suspend fun getSessionVersion(sessionId: String): Int?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(session: WorkoutSessionEntity)

    // expectedVersion represents the target next version (readVersion + 1);
    // strict equality against (expectedVersion - 1) prevents stale, skipped, or post-terminal overwrites.
    @Query("UPDATE workout_sessions SET status = :newStatus, version = version + 1, completedAt = :completedAt WHERE id = :sessionId AND version = (:expectedVersion - 1) AND status = 'ATIVO'")
    suspend fun updateStatusVersioned(
        sessionId: String,
        newStatus: String,
        expectedVersion: Int,
        completedAt: Long?
    ): Int

    @Query("UPDATE workout_sessions SET status = :newStatus, version = version + 1, completedAt = :completedAt, endTime = :completedAt, durationSeconds = :durationSeconds WHERE id = :sessionId AND version = (:expectedVersion - 1) AND status = 'ATIVO'")
    suspend fun updateStatusAndDurationVersioned(
        sessionId: String,
        newStatus: String,
        expectedVersion: Int,
        completedAt: Long?,
        durationSeconds: Long
    ): Int

    @Query("UPDATE workout_sessions SET status = :newStatus, version = version + 1, completedAt = :completedAt, endTime = :completedAt, durationSeconds = :durationSeconds, exercisesJson = :exercisesJson WHERE id = :sessionId AND version = (:expectedVersion - 1) AND status = 'ATIVO'")
    suspend fun updateStatusExercisesAndDurationVersioned(
        sessionId: String,
        newStatus: String,
        expectedVersion: Int,
        completedAt: Long?,
        durationSeconds: Long,
        exercisesJson: String
    ): Int

    @Query("UPDATE workout_sessions SET status = :status, completedAt = :endTime, endTime = :endTime, durationSeconds = :durationSeconds, version = version + 1 WHERE id = :sessionId AND status = 'ATIVO'")
    suspend fun updateStatusFromActive(
        sessionId: String,
        status: String,
        endTime: Long?,
        durationSeconds: Long
    ): Int

    @Query("UPDATE workout_sessions SET exercisesJson = :exercisesJson, version = version + 1 WHERE id = :sessionId AND version = :expectedVersion AND status = 'ATIVO'")
    suspend fun updateExercisesJsonOptimistic(
        sessionId: String,
        exercisesJson: String,
        expectedVersion: Int
    ): Int

    @Query("UPDATE workout_sessions SET exercisesJson = :exercisesJson, durationSeconds = :durationSeconds, version = version + 1 WHERE id = :sessionId AND version = :expectedVersion AND status = 'ATIVO'")
    suspend fun updateExercisesAndDurationOptimistic(
        sessionId: String,
        exercisesJson: String,
        durationSeconds: Long,
        expectedVersion: Int
    ): Int

    @Query("UPDATE workout_sessions SET exercisesJson = :exercisesJson, version = version + 1 WHERE id = :sessionId AND status = 'ATIVO'")
    suspend fun updateExercisesJson(sessionId: String, exercisesJson: String): Int

    @Query("UPDATE workout_sessions SET status = :status, completedAt = :endTime, endTime = :endTime, durationSeconds = :durationSeconds, version = version + 1 WHERE id = :sessionId AND status = 'ATIVO'")
    suspend fun updateStatus(sessionId: String, status: String, endTime: Long?, durationSeconds: Long): Int

    @Query("DELETE FROM workout_sessions WHERE status IN ('DESCARTADO', 'NAO_REALIZADO') AND ((completedAt IS NOT NULL AND completedAt < :thresholdTime) OR (endTime IS NOT NULL AND endTime < :thresholdTime))")
    suspend fun deleteExpiredSessions(thresholdTime: Long): Int

    @Query("SELECT * FROM workout_sessions WHERE status IN ('DESCARTADO', 'NAO_REALIZADO') ORDER BY COALESCE(completedAt, endTime) DESC")
    fun getArchivedSessionsFlow(): Flow<List<WorkoutSessionEntity>>

    @Query("DELETE FROM workout_sessions WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: String)

    @Query("DELETE FROM workout_sessions")
    suspend fun clearAll()

    @Transaction
    suspend fun archiveMultipleSessionsAtomically(
        sessionsToArchive: List<WorkoutSessionEntity>,
        archiveTimestamp: Long
    ): Int {
        var count = 0
        for (session in sessionsToArchive) {
            val affected = updateStatusAndDurationVersioned(
                sessionId = session.id,
                newStatus = "NAO_REALIZADO",
                expectedVersion = session.version + 1,
                completedAt = archiveTimestamp,
                durationSeconds = session.durationSeconds
            )
            if (affected > 0) count++
        }
        return count
    }
}
