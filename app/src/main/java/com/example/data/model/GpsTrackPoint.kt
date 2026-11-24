package com.example.data.model

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "gps_track_points",
    primaryKeys = ["sessionId", "timestampMs"],
    indices = [
        Index(value = ["sessionId"]),
        Index(value = ["sessionId", "timestampMs"])
    ]
)
data class GpsTrackPoint(
    val sessionId: String,
    val timestampMs: Long,
    val capturedAtMs: Long,
    val latitude: Double,
    val longitude: Double,
    val accuracyM: Float? = null,
    val speedMps: Float? = null,
    val bearingDeg: Float? = null,
    val altitudeM: Double? = null
)
