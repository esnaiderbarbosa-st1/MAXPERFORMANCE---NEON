package com.example.services

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.model.RunningSession
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Gerenciador e agendador do Lembrete Diário de Corrida (MAX RUN).
 * Dispara notificação local em português contextualizada com o treino do dia
 * caso o usuário ainda não tenha registrado corrida na data atual.
 */
object RunningDailyReminderManager {

    const val CHANNEL_ID = "running_daily_reminder_channel"
    const val NOTIFICATION_ID = 2005
    private const val PREFS_NAME = "running_reminder_prefs"
    private const val RUNNING_SESSIONS_PREFS = "max_running_prefs"

    private const val KEY_ENABLED = "reminder_enabled"
    private const val KEY_HOUR = "reminder_hour"
    private const val KEY_MINUTE = "reminder_minute"
    private const val KEY_WEEK = "context_week"
    private const val KEY_DAY = "context_day"
    private const val KEY_PROGRAM_TITLE = "context_program_title"
    private const val KEY_WORKOUT_SUMMARY = "context_workout_summary"
    private const val KEY_LAST_RUN_DATE_YMD = "last_run_date_ymd"

    private const val ALARM_REQUEST_CODE = 4205

    private fun todayYmd(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    }

    fun isReminderEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_ENABLED, true)
    }

    fun getReminderTime(context: Context): Pair<Int, Int> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val h = prefs.getInt(KEY_HOUR, 18).coerceIn(0, 23)
        val m = prefs.getInt(KEY_MINUTE, 0).coerceIn(0, 59)
        return Pair(h, m)
    }

    fun setReminderConfig(
        context: Context,
        enabled: Boolean,
        hourOfDay: Int,
        minute: Int
    ) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_ENABLED, enabled)
            .putInt(KEY_HOUR, hourOfDay.coerceIn(0, 23))
            .putInt(KEY_MINUTE, minute.coerceIn(0, 59))
            .apply()

        if (enabled) {
            scheduleNextReminder(context)
        } else {
            cancelScheduledReminder(context)
        }
    }

    /**
     * Verifica se o usuário já registrou alguma corrida na data atual.
     */
    fun hasRunToday(context: Context, sessions: List<RunningSession>? = null): Boolean {
        val today = todayYmd()
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)

        if (sessions != null) {
            val ranToday = sessions.any { fmt.format(Date(it.date)) == today }
            if (ranToday) {
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_LAST_RUN_DATE_YMD, today)
                    .apply()
                return true
            }
        }

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getString(KEY_LAST_RUN_DATE_YMD, null) == today) {
            return true
        }

        // Fallback para leitura do JSON local quando executado em background pelo BroadcastReceiver
        try {
            val runPrefs = context.getSharedPreferences(RUNNING_SESSIONS_PREFS, Context.MODE_PRIVATE)
            val rawJson = runPrefs.getString("running_sessions_v1", null)
            if (!rawJson.isNullOrBlank()) {
                val arr = JSONArray(rawJson)
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val ts = obj.optLong("date", 0L)
                    if (ts > 0L && fmt.format(Date(ts)) == today) {
                        prefs.edit().putString(KEY_LAST_RUN_DATE_YMD, today).apply()
                        return true
                    }
                }
            }
        } catch (_: Exception) {}

        return false
    }

    fun markRunCompletedToday(context: Context) {
        val today = todayYmd()
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LAST_RUN_DATE_YMD, today)
            .apply()
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.cancel(NOTIFICATION_ID)
        } catch (_: Exception) {}
        if (isReminderEnabled(context)) {
            scheduleNextReminder(context, forceTomorrow = true)
        }
    }

    /**
     * Sincroniza o contexto do treino do dia (Semana, Dia, Programa e Estrutura)
     * e agenda o lembrete diário.
     */
    fun syncWorkoutContextAndSchedule(
        context: Context,
        weekNumber: Int,
        dayNumber: Int,
        programTitle: String,
        workoutSummary: String,
        sessions: List<RunningSession>
    ) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putInt(KEY_WEEK, weekNumber)
            .putInt(KEY_DAY, dayNumber)
            .putString(KEY_PROGRAM_TITLE, programTitle)
            .putString(KEY_WORKOUT_SUMMARY, workoutSummary)
            .apply()

        val ranToday = hasRunToday(context, sessions)
        if (isReminderEnabled(context)) {
            scheduleNextReminder(context, forceTomorrow = ranToday)
        }
    }

    fun buildContextualReminderContent(context: Context): Pair<String, String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val week = prefs.getInt(KEY_WEEK, 1).coerceAtLeast(1)
        val day = prefs.getInt(KEY_DAY, 1).coerceAtLeast(1)
        val programTitle = prefs.getString(KEY_PROGRAM_TITLE, "MAX RUN")?.takeIf { it.isNotBlank() } ?: "MAX RUN"
        val summary = prefs.getString(KEY_WORKOUT_SUMMARY, "Treino intervalado de corrida e caminhada")
            ?.takeIf { it.isNotBlank() }
            ?: "Treino intervalado de corrida e caminhada"

        val title = "Treino de Hoje: Semana $week • Dia $day 🏃"
        val body = "Você ainda não registrou corrida hoje! Seu treino ($programTitle): $summary. Vamos manter o ritmo?"
        return Pair(title, body)
    }

    fun scheduleNextReminder(context: Context, forceTomorrow: Boolean = false) {
        if (!isReminderEnabled(context)) return
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val (hour, minute) = getReminderTime(context)

        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        if (forceTomorrow || target.timeInMillis <= now.timeInMillis || hasRunToday(context)) {
            target.add(Calendar.DAY_OF_YEAR, 1)
        }

        val intent = Intent(context, RunningDailyReminderReceiver::class.java).apply {
            action = "ACTION_DAILY_RUNNING_REMINDER"
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    target.timeInMillis,
                    pendingIntent
                )
            } else {
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    target.timeInMillis,
                    pendingIntent
                )
            }
        } catch (_: SecurityException) {
            try {
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    target.timeInMillis,
                    pendingIntent
                )
            } catch (_: Exception) {}
        } catch (_: Exception) {}
    }

    fun cancelScheduledReminder(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, RunningDailyReminderReceiver::class.java).apply {
            action = "ACTION_DAILY_RUNNING_REMINDER"
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            alarmManager.cancel(pendingIntent)
        } catch (_: Exception) {}
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Lembrete Diário de Corrida",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notificações diárias contextualizadas com o treino de corrida do dia"
            }
            nm.createNotificationChannel(channel)
        }
    }

    /**
     * Dispara a notificação local caso o usuário ainda não tenha registrado corrida hoje
     * (ou imediatamente quando `forcePreview = true`).
     */
    fun dispatchReminderNotificationIfNoRunToday(
        context: Context,
        forcePreview: Boolean = false
    ): Boolean {
        if (!forcePreview && hasRunToday(context)) {
            scheduleNextReminder(context, forceTomorrow = true)
            return false
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPerm = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasPerm) return false
        }

        ensureChannel(context)
        val (title, body) = buildContextualReminderContent(context)

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            ALARM_REQUEST_CODE + 1,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_run_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()

        return try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.notify(NOTIFICATION_ID, notification)
            true
        } catch (_: Exception) {
            false
        }
    }
}

class RunningDailyReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        RunningDailyReminderManager.dispatchReminderNotificationIfNoRunToday(context, forcePreview = false)
        RunningDailyReminderManager.scheduleNextReminder(context, forceTomorrow = true)
    }
}
