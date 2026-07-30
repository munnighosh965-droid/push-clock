package com.powerclock.alarm.alarmengine

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.powerclock.alarm.MainActivity
import com.powerclock.alarm.data.prefs.SettingsRepository
import com.powerclock.alarm.data.repo.AlarmRepository
import com.powerclock.alarm.domain.model.Alarm
import com.powerclock.alarm.domain.scheduling.NextOccurrenceCalculator
import com.powerclock.alarm.widget.PowerClockWidgetProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalTime
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The single place where alarms are (re)armed with [AlarmManager].
 *
 * - Every alarm gets a unique, immutable [PendingIntent] keyed by its row id,
 *   so re-scheduling always replaces the previous registration and duplicate
 *   ringing is impossible.
 * - User alarms use [AlarmManager.setAlarmClock], the exact, Doze-proof API
 *   intended for alarm-clock apps.
 * - When the exact-alarm permission is revoked, we fall back to
 *   [AlarmManager.setAndAllowWhileIdle] (delivery may be delayed) and the
 *   Reliability Check screen reports the problem honestly.
 */
@Singleton
class AlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val alarmRepository: AlarmRepository,
    private val settingsRepository: SettingsRepository,
) {
    private val alarmManager: AlarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun canScheduleExactAlarms(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }

    /** Arms [alarm] for its next occurrence. Returns the trigger time or null. */
    fun schedule(alarm: Alarm, now: ZonedDateTime = ZonedDateTime.now()): ZonedDateTime? {
        cancel(alarm.id)
        if (!alarm.enabled) return null
        val trigger = NextOccurrenceCalculator.nextTrigger(alarm, now) ?: return null
        val triggerMs = trigger.toInstant().toEpochMilli()
        val operation = ringPendingIntent(alarm.id)

        if (canScheduleExactAlarms()) {
            val showIntent = PendingIntent.getActivity(
                context,
                SHOW_REQUEST_BASE + alarm.id.toInt(),
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            alarmManager.setAlarmClock(
                AlarmManager.AlarmClockInfo(triggerMs, showIntent),
                operation,
            )
        } else {
            // Best effort without the exact-alarm special access.
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, operation)
        }
        PowerClockWidgetProvider.requestUpdate(context)
        return trigger
    }

    fun cancel(alarmId: Long) {
        alarmManager.cancel(ringPendingIntent(alarmId))
        PowerClockWidgetProvider.requestUpdate(context)
    }

    /**
     * Re-arms every enabled alarm. Called after boot, app update, time or
     * timezone changes, exact-permission changes, and app start.
     */
    suspend fun rescheduleAll() {
        val now = ZonedDateTime.now()
        alarmRepository.getAll().forEach { alarm ->
            if (alarm.enabled) schedule(alarm, now) else cancel(alarm.id)
        }
        rescheduleBedtimeReminder()
    }

    suspend fun rescheduleBedtimeReminder() {
        val settings = settingsRepository.current()
        val operation = bedtimePendingIntent()
        alarmManager.cancel(operation)
        if (!settings.bedtimeReminderEnabled) return
        val now = ZonedDateTime.now()
        val time = LocalTime.of(settings.bedtimeMinutes / 60, settings.bedtimeMinutes % 60)
        var next = now.with(time)
        if (!next.isAfter(now)) next = next.plusDays(1)
        // Reminders do not need exact delivery.
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            next.toInstant().toEpochMilli(),
            operation,
        )
    }

    private fun ringPendingIntent(alarmId: Long): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_RING
            putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarmId)
        }
        return PendingIntent.getBroadcast(
            context,
            alarmId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun bedtimePendingIntent(): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_BEDTIME_REMINDER
        }
        return PendingIntent.getBroadcast(
            context,
            BEDTIME_REQUEST,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private companion object {
        const val SHOW_REQUEST_BASE = 100_000
        const val BEDTIME_REQUEST = -2001
    }
}
