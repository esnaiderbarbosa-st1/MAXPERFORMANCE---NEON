package com.example.services

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

data class AppNotification(
    val id: String,
    val title: String,
    val body: String,
    val timestamp: Long = System.currentTimeMillis(),
    val actionPayload: String? = null // e.g. "NAVIGATE_TO_ADMIN"
)

object NotificationsManager {
    private const val TAG = "NotificationsManager"

    private val _notificationsFlow = MutableSharedFlow<AppNotification>(extraBufferCapacity = 10)
    val notificationsFlow: SharedFlow<AppNotification> = _notificationsFlow

    suspend fun dispatchNotification(context: Context, title: String, body: String, actionPayload: String? = "NAVIGATE_TO_ADMIN") {
        val notification = AppNotification(
            id = java.util.UUID.randomUUID().toString(),
            title = title,
            body = body,
            actionPayload = actionPayload
        )

        // Log Firebase Cloud Messaging Trigger Info
        Log.i(TAG, "--------------------------------------------------------")
        Log.i(TAG, " [FCM PUSH NOTIFICATION SENT TO SUPERUSER DEVICE]")
        Log.i(TAG, " Title: $title")
        Log.i(TAG, " Body: $body")
        Log.i(TAG, " Payload: $actionPayload")
        Log.i(TAG, "--------------------------------------------------------")

        // Emit to flow for live UI reactions (like a floating in-app banner)
        _notificationsFlow.emit(notification)
    }
}
