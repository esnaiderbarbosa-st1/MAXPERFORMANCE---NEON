package com.example.utils

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Sistema de Log de Diagnóstico Local para sessões de treino (Build #32).
 * Grava eventos críticos da sessão (início/fim de blocos, perda de GPS, estado de bateria, reconexões BLE, etc).
 */
object SessionDiagnosticLogger {
    private const val PREFS_NAME = "session_diagnostic_prefs"
    private const val KEY_LOGS = "diagnostic_logs_list"
    private const val MAX_LOG_ENTRIES = 100

    private val dateFormat = SimpleDateFormat("dd/MM HH:mm:ss", Locale("pt", "BR"))

    fun log(context: Context, tag: String, message: String) {
        try {
            val timestamp = dateFormat.format(Date())
            val entry = "[$timestamp] [$tag] $message"
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val currentLogs = getLogsList(context).toMutableList()
            currentLogs.add(0, entry) // mais recente primeiro
            if (currentLogs.size > MAX_LOG_ENTRIES) {
                currentLogs.removeAt(currentLogs.lastIndex)
            }
            prefs.edit().putString(KEY_LOGS, currentLogs.joinToString("\n")).apply()
        } catch (_: Exception) {}
    }

    fun getLogsList(context: Context): List<String> {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val raw = prefs.getString(KEY_LOGS, "") ?: ""
            if (raw.isBlank()) emptyList() else raw.split("\n")
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun getLogsFormatted(context: Context): String {
        val list = getLogsList(context)
        return if (list.isEmpty()) {
            "Nenhum log de diagnóstico registrado."
        } else {
            "=== LOG DE DIAGNÓSTICO DO SISTEMA (MAX PERFORMANCE v${AppBuildInfo.VERSION_NAME}) ===\n\n" + list.joinToString("\n")
        }
    }

    fun copyLogsToClipboard(context: Context) {
        val logs = getLogsFormatted(context)
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = ClipData.newPlainText("MAX_PERFORMANCE_DIAGNOSTIC_LOG", logs)
        clipboard?.setPrimaryClip(clip)
        Toast.makeText(context, "Log de diagnóstico copiado!", Toast.LENGTH_SHORT).show()
    }

    fun clearLogs(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().remove(KEY_LOGS).apply()
            Toast.makeText(context, "Logs limpos com sucesso", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {}
    }
}
