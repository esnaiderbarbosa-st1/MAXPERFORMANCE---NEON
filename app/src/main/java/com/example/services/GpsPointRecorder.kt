package com.example.services

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.data.dao.GpsTrackPointDao
import com.example.data.model.GpsTrackPoint
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class GpsPointRecorder(
    private val dao: GpsTrackPointDao,
    val sessionId: String,
    private var client: FusedLocationProviderClient? = null,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) {
    private var lastTimestampMs: Long = 0L
    private var lastLat: Double = 0.0
    private var lastLon: Double = 0.0

    fun start(context: Context) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "ACCESS_FINE_LOCATION não concedida; pulando gravação GPS")
            return
        }
        try {
            if (client == null) {
                client = LocationServices.getFusedLocationProviderClient(context.applicationContext)
            }
            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000L)
                .setMinUpdateIntervalMillis(3000L)
                .build()
            client?.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException ao pedir location updates", e)
        } catch (e: Throwable) {
            Log.w(TAG, "Falha ao iniciar gravação de pontos GPS", e)
        }
    }

    fun stop() {
        try {
            client?.removeLocationUpdates(locationCallback)
        } catch (_: Exception) {}
        try {
            scope.cancel()
        } catch (_: Exception) {}
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            if (!RunTracker.isCurrentlyTracking()) return
            val loc = result.lastLocation ?: return
            val now = System.currentTimeMillis()

            // Gate 1: Accuracy Gate
            if (lastTimestampMs != 0L && loc.hasAccuracy() && loc.accuracy > GpsConfig.MAX_ACCURACY_METERS) return

            val dist = if (lastLat != 0.0 || lastLon != 0.0) {
                FloatArray(1).also { Location.distanceBetween(lastLat, lastLon, loc.latitude, loc.longitude, it) }[0]
            } else 0f

            val deltaTimeSec = if (lastTimestampMs != 0L) (now - lastTimestampMs) / 1000.0 else 0.0
            val speedMps = if (loc.hasSpeed()) loc.speed else if (deltaTimeSec > 0) (dist / deltaTimeSec).toFloat() else 0f

            // Gate 2: Max Human Speed Gate
            if (lastTimestampMs != 0L && speedMps > GpsConfig.MAX_SPEED_MPS) return

            // Gate 3: Sensor Fusion (Zero-Cadence Stationary Lock)
            val isMoving = com.example.utils.StepCadenceTracker.isPhysicallyMoving(now)
            if (lastTimestampMs != 0L && com.example.utils.StepCadenceTracker.hasStepSensor && !isMoving) {
                if (dist < GpsConfig.STATIONARY_DISPLACEMENT_THRESHOLD_METERS && speedMps < GpsConfig.STATIONARY_SPEED_THRESHOLD_MPS) return
            }

            // Gate 4: Minimum Displacement
            if (lastTimestampMs != 0L && (now - lastTimestampMs) < 3000L && dist < GpsConfig.MIN_DISPLACEMENT_METERS) return

            lastTimestampMs = now
            lastLat = loc.latitude
            lastLon = loc.longitude
            val point = GpsTrackPoint(
                sessionId = sessionId,
                timestampMs = if (loc.time > 0L) loc.time else now,
                capturedAtMs = now,
                latitude = loc.latitude,
                longitude = loc.longitude,
                accuracyM = if (loc.hasAccuracy()) loc.accuracy else null,
                speedMps = if (loc.hasSpeed()) loc.speed else null,
                bearingDeg = if (loc.hasBearing()) loc.bearing else null,
                altitudeM = if (loc.hasAltitude()) loc.altitude else null
            )
            scope.launch {
                try {
                    dao.insert(point)
                } catch (e: Exception) {
                    Log.w(TAG, "Falha ao gravar ponto GPS", e)
                }
            }
        }
    }

    companion object {
        private const val TAG = "GpsPointRecorder"
    }
}
