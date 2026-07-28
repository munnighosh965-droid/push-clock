package com.powerclock.alarm.alarmengine

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Keeps alarms armed across every system event that can silently clear
 * AlarmManager registrations: reboot, app update, wall-clock changes,
 * timezone changes, and the user re-granting exact-alarm access.
 */
@AndroidEntryPoint
class SystemEventReceiver : BroadcastReceiver() {

    @Inject
    lateinit var scheduler: AlarmScheduler

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val relevant = action in setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
        )
        if (!relevant) return

        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                scheduler.rescheduleAll()
            } catch (_: Throwable) {
                // Never crash on a system broadcast; the app re-arms alarms
                // again on next launch.
            } finally {
                pending.finish()
            }
        }
    }
}
