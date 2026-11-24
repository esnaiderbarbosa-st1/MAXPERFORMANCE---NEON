package com.example.services

object GpsConfig {
    const val MAX_ACCURACY_METERS = 35.0f
    const val MIN_DISPLACEMENT_METERS = 1.5f
    const val ACCURACY_DISPLACEMENT_FACTOR = 0.25f
    const val MAX_SPEED_MPS = 8.5 // 8.5 m/s = 30.6 km/h
    const val ABSOLUTE_MAX_IMPLICIT_SPEED_MPS = MAX_SPEED_MPS * 2.0 // 17.0 m/s = 61.2 km/h (independente do Doppler)
    const val ABSOLUTE_JUMP_MIN_DISTANCE_METERS = 30.0 // distância mínima (m) para rejeitar salto absurdo independente do Doppler
    const val MIN_SPEED_DOPPLER_MPS = 0.45f // abaixo de 0.45 m/s = estático

    const val STATIONARY_DISPLACEMENT_THRESHOLD_METERS = 6.0f
    const val STATIONARY_SPEED_THRESHOLD_MPS = 0.55f // ~2.0 km/h para aceitar caminhada leve ao ar livre

    const val MAX_LOCATION_AGE_SEC = 15.0
    const val WEAK_SIGNAL_TIMEOUT_SEC = 12.0
    const val SEARCHING_SIGNAL_TIMEOUT_SEC = 30.0
    const val GOOD_ACCURACY_THRESHOLD = 20.0f

    const val UPDATE_INTERVAL_MS = 2000L
    const val MIN_UPDATE_INTERVAL_MS = 1000L
}
