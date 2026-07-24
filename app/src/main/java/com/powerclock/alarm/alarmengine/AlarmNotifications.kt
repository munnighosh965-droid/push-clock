package com.powerclock.alarm.alarmengine

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.powerclock.alarm.MainActivity
import com.powerclock.alarm.R
import com.powerclock.alarm.domain.model.Alarm
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlarmNotifications @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        const val CHANNEL_RINGING = "powerclock_ringing"
        const val CHANNEL_REMINDERS = "powerclock_reminders"
        const val NOTIFICATION_ID_RINGING = 4001
        const val NOTIFICATION_ID_BEDTIME = 4002
    }

    private val manager: NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun ensureChannels() {
        val ringing = NotificationChannel(
            CHANNEL_RINGING,
            context.getString(R.string.channel_ringing_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.channel_ringing_desc)
            // Audio is produced by the ringing service, not the notification.
            setSound(null, null)
            enableVibration(false)
            setBypassDnd(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        val reminders = NotificationChannel(
            CHANNEL_REMINDERS,
            context.getString(R.string.channel_reminders_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.channel_reminders_desc)
        }
        manager.createNotificationChannel(ringing)
        manager.createNotificationChannel(reminders)
    }

    fun ringingNotification(alarm: Alarm): Notification {
        val fullScreenIntent = Intent(context, RingingActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(RingingActivity.EXTRA_ALARM_ID, alarm.id)
        }
        val fullScreenPending = PendingIntent.getActivity(
            context,
            9001,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val label = alarm.label.ifBlank { context.getString(R.string.default_alarm_label) }
        return NotificationCompat.Builder(context, CHANNEL_RINGING)
            .setSmallIcon(R.drawable.ic_stat_powerclock)
            .setContentTitle(context.getString(R.string.notif_ringing_title))
            .setContentText(label)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setAutoCancel(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(fullScreenPending, true)
            .setContentIntent(fullScreenPending)
            .setSilent(true)
            .build()
    }

    fun bedtimeReminder() {
        val contentIntent = PendingIntent.getActivity(
            context,
            9002,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_stat_powerclock)
            .setContentTitle(context.getString(R.string.notif_bedtime_title))
            .setContentText(context.getString(R.string.notif_bedtime_text))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()
        try {
            manager.notify(NOTIFICATION_ID_BEDTIME, notification)
        } catch (_: SecurityException) {
            // Notification permission revoked; the reminder is best-effort.
        }
    }
}
