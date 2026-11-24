package com.example.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.RunningSession
import kotlinx.coroutines.flow.Flow

@Dao
interface RunningSessionDao {
    @Query("SELECT * FROM running_sessions ORDER BY date DESC")
    fun getAllRunningSessions(): Flow<List<RunningSession>>

    @Query("SELECT * FROM running_sessions ORDER BY date DESC LIMIT 3")
    fun getRecentRunningSessions(): Flow<List<RunningSession>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRunningSession(session: RunningSession)

    @Query("DELETE FROM running_sessions WHERE id = :id")
    suspend fun deleteRunningSessionById(id: String)

    @Query("DELETE FROM running_sessions")
    suspend fun clearAllRunningSessions()
}
