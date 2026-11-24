package com.example

import android.app.Application
import android.os.Build
import android.util.Log
import com.example.utils.CrashLog
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainApplication : Application() {
    override fun onCreate() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val crashFile = File(filesDir, "last_crash.txt")
                val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                val crashReport = buildString {
                    append("=== UNCAUGHT CRASH ===\n")
                    append("Data/Hora: ").append(timestamp).append("\n")
                    append("Thread: ").append(thread.name).append("\n")
                    append("Modelo: ").append(Build.MODEL).append("\n")
                    append("SDK: ").append(Build.VERSION.SDK_INT).append("\n")
                    append("StackTrace:\n").append(Log.getStackTraceString(throwable)).append("\n")
                    append("=====================\n\n")
                }
                crashFile.appendText(crashReport)
            } catch (e: Exception) {
                Log.w("MainApplication", "Error writing crash log", e)
            } finally {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
        super.onCreate()
        CrashLog.init(this)
        runCatching {
            com.example.services.telemetry.Telemetry.initialize(this)
        }
    }
}
