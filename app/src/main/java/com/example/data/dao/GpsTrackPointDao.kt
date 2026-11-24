package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.GpsTrackPoint
import kotlinx.coroutines.flow.Flow

@Dao
interface GpsTrackPointDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(point: GpsTrackPoint)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(points: List<GpsTrackPoint>)

    @Query("DELETE FROM gps_track_points WHERE sessionId = :sessionId")
    suspend fun deleteForSession(sessionId: String)

    @Query("SELECT * FROM gps_track_points WHERE sessionId = :sessionId ORDER BY timestampMs ASC")
    fun getForSession(sessionId: String): Flow<List<GpsTrackPoint>>

    @Query("SELECT * FROM gps_track_points WHERE sessionId = :sessionId ORDER BY timestampMs ASC")
    fun getForSessionOnce(sessionId: String): List<GpsTrackPoint>

    @Query("SELECT COUNT(*) FROM gps_track_points WHERE sessionId = :sessionId")
    suspend fun countForSession(sessionId: String): Int
}
