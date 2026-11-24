package com.example.utils

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CrashLog {
    @Volatile
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun record(throwable: Throwable, origem: String, context: Context? = null) {
        try {
            val ctx = context?.applicationContext ?: appContext
            if (ctx == null) {
                Log.w("CrashLog", "Context not initialized for CrashLog.record in $origem", throwable)
                return
            }
            val crashFile = File(ctx.filesDir, "last_crash.txt")
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            val logEntry = buildString {
                append("=== SWALLOWED EXCEPTION ===\n")
                append("Data/Hora: ").append(timestamp).append("\n")
                append("Origem: ").append(origem).append("\n")
                append("Thread: ").append(Thread.currentThread().name).append("\n")
                append("Modelo: ").append(Build.MODEL).append("\n")
                append("SDK: ").append(Build.VERSION.SDK_INT).append("\n")
                append("StackTrace:\n").append(Log.getStackTraceString(throwable)).append("\n")
                append("===========================\n\n")
            }
            crashFile.appendText(logEntry)
        } catch (e: Exception) {
            Log.w("CrashLog", "Failed to write crash log for $origem", e)
        }
    }
}
