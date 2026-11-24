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
import com.example.data.model.UpcomingRaceEvent
import java.util.Calendar
import java.util.Locale

/**
 * Agendador e Receiver para lembretes de Próximas Provas Oficiais (MAX RUN).
 * Dispara notificações automáticas no Android:
 * - 7 dias antes (08:00): Semana de polimento e redução de fadiga.
 * - 1 dia antes (18:00): Véspera, preparação de tênis/número de peito e hidratação.
 */
class UpcomingRaceReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val raceName = intent.getStringExtra(UpcomingRaceReminderScheduler.EXTRA_RACE_NAME) ?: "Prova Oficial"
        val shoeName = intent.getStringExtra(UpcomingRaceReminderScheduler.EXTRA_SHOE_NAME) ?: "seu tênis de prova"
        val reminderType = intent.getStringExtra(UpcomingRaceReminderScheduler.EXTRA_REMINDER_TYPE) ?: "ONE_DAY"
        val notifId = intent.getIntExtra(UpcomingRaceReminderScheduler.EXTRA_NOTIFICATION_ID, 2030)

        UpcomingRaceReminderScheduler.showNotification(
            context = context,
            raceName = raceName,
            plannedShoe = shoeName,
            reminderType = reminderType,
            notificationId = notifId
        )
    }
}

object UpcomingRaceReminderScheduler {

    const val CHANNEL_ID = "upcoming_races_reminder_channel"
    const val EXTRA_RACE_NAME = "extra_race_name"
    const val EXTRA_SHOE_NAME = "extra_shoe_name"
    const val EXTRA_REMINDER_TYPE = "extra_reminder_type"
    const val EXTRA_NOTIFICATION_ID = "extra_notification_id"

    private const val ACTION_UPCOMING_RACE_REMINDER = "com.example.ACTION_UPCOMING_RACE_REMINDER"

    fun scheduleAlarmsForRace(context: Context, race: UpcomingRaceEvent) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val now = System.currentTimeMillis()

        // 1. Alarme de 7 dias antes às 08:00
        if (race.notifyOneWeekBefore) {
            val cal7Days = Calendar.getInstance().apply {
                timeInMillis = race.raceDateMillis
                add(Calendar.DAY_OF_YEAR, -7)
                set(Calendar.HOUR_OF_DAY, 8)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (cal7Days.timeInMillis > now) {
                val reqCode = (race.id.hashCode() * 31 + 7).toInt() and 0x7FFFFFFF
                val intent = Intent(context, UpcomingRaceReminderReceiver::class.java).apply {
                    action = ACTION_UPCOMING_RACE_REMINDER
                    putExtra(EXTRA_RACE_NAME, race.raceName)
                    putExtra(EXTRA_SHOE_NAME, race.plannedShoe)
                    putExtra(EXTRA_REMINDER_TYPE, "SEVEN_DAYS")
                    putExtra(EXTRA_NOTIFICATION_ID, reqCode)
                }
                val pi = PendingIntent.getBroadcast(
                    context,
                    reqCode,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                setAlarm(alarmManager, cal7Days.timeInMillis, pi)
            }
        }

        // 2. Alarme de 1 dia antes às 18:00 (Véspera)
        if (race.notifyOneDayBefore) {
            val cal1Day = Calendar.getInstance().apply {
                timeInMillis = race.raceDateMillis
                add(Calendar.DAY_OF_YEAR, -1)
                set(Calendar.HOUR_OF_DAY, 18)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (cal1Day.timeInMillis > now) {
                val reqCode = (race.id.hashCode() * 31 + 1).toInt() and 0x7FFFFFFF
                val intent = Intent(context, UpcomingRaceReminderReceiver::class.java).apply {
                    action = ACTION_UPCOMING_RACE_REMINDER
                    putExtra(EXTRA_RACE_NAME, race.raceName)
                    putExtra(EXTRA_SHOE_NAME, race.plannedShoe)
                    putExtra(EXTRA_REMINDER_TYPE, "ONE_DAY")
                    putExtra(EXTRA_NOTIFICATION_ID, reqCode)
                }
                val pi = PendingIntent.getBroadcast(
                    context,
                    reqCode,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                setAlarm(alarmManager, cal1Day.timeInMillis, pi)
            }
        }
    }

    private fun setAlarm(alarmManager: AlarmManager, triggerAtMillis: Long, pendingIntent: PendingIntent) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
        } catch (_: SecurityException) {
            try {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            } catch (_: Exception) {}
        } catch (_: Exception) {}
    }

    fun cancelAlarmsForRace(context: Context, race: UpcomingRaceEvent) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        val req7 = (race.id.hashCode() * 31 + 7).toInt() and 0x7FFFFFFF
        val intent7 = Intent(context, UpcomingRaceReminderReceiver::class.java).apply {
            action = ACTION_UPCOMING_RACE_REMINDER
        }
        val pi7 = PendingIntent.getBroadcast(
            context,
            req7,
            intent7,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try { alarmManager.cancel(pi7) } catch (_: Exception) {}

        val req1 = (race.id.hashCode() * 31 + 1).toInt() and 0x7FFFFFFF
        val intent1 = Intent(context, UpcomingRaceReminderReceiver::class.java).apply {
            action = ACTION_UPCOMING_RACE_REMINDER
        }
        val pi1 = PendingIntent.getBroadcast(
            context,
            req1,
            intent1,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try { alarmManager.cancel(pi1) } catch (_: Exception) {}
    }

    fun showNotification(
        context: Context,
        raceName: String,
        plannedShoe: String,
        reminderType: String,
        notificationId: Int
    ) {
        ensureChannel(context)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permission = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            if (permission != PackageManager.PERMISSION_GRANTED) return
        }

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (reminderType == "SEVEN_DAYS") {
            "Falta 1 semana: $raceName"
        } else {
            "Amanhã é dia de Prova: $raceName!"
        }

        val body = if (reminderType == "SEVEN_DAYS") {
            "Falta 1 semana para $raceName! Hora de iniciar o polimento nos treinos e reduzir a fadiga acumulada."
        } else {
            "Amanhã é dia de $raceName! Separe o tênis ($plannedShoe), número de peito e hidrate-se bem."
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        try {
            notificationManager?.notify(notificationId, notification)
        } catch (_: SecurityException) {}
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Lembretes de Provas Oficiais (MAX RUN)",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alertas de contagem regressiva e polimento para provas agendadas"
                enableVibration(true)
            }
            manager.createNotificationChannel(channel)
        }
    }
}
