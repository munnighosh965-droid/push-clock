package com.powerclock.alarm.alarmengine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Fired by AlarmManager at the exact trigger instant. Immediately hands the
 * work to the foreground ringing service; the receiver itself stays tiny so
 * it always finishes within the broadcast window.
 */
@AndroidEntryPoint
class AlarmReceiver : BroadcastReceiver() {

    @Inject
    lateinit var notifications: AlarmNotifications

    @Inject
    lateinit var scheduler: AlarmScheduler

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_RING -> {
                val alarmId = intent.getLongExtra(EXTRA_ALARM_ID, -1L)
                if (alarmId <= 0L) return
                val serviceIntent = Intent(context, AlarmRingingService::class.java).apply {
                    action = AlarmRingingService.ACTION_RING
                    putExtra(AlarmRingingService.EXTRA_ALARM_ID, alarmId)
                }
                ContextCompat.startForegroundService(context, serviceIntent)
            }

            ACTION_BEDTIME_REMINDER -> {
                notifications.bedtimeReminder()
                val pending = goAsync()
                CoroutineScope(Dispatchers.Default).launch {
                    try {
                        scheduler.rescheduleBedtimeReminder()
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }

    companion object {
        const val ACTION_RING = "com.powerclock.alarm.action.RING"
        const val ACTION_BEDTIME_REMINDER = "com.powerclock.alarm.action.BEDTIME_REMINDER"
        const val EXTRA_ALARM_ID = "alarm_id"
    }
}
