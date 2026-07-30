package com.powerclock.alarm.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.powerclock.alarm.MainActivity
import com.powerclock.alarm.R
import com.powerclock.alarm.data.repo.AlarmRepository
import com.powerclock.alarm.domain.scheduling.NextOccurrenceCalculator
import com.powerclock.alarm.ui.components.TimeFormat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.ZonedDateTime
import javax.inject.Inject

/**
 * Home-screen widget: the Power Clock mark running as a real clock.
 *
 * The hands are driven by the framework's [android.widget.AnalogClock] inside
 * the launcher process, so they keep time without this app being scheduled at
 * all. The only thing that needs refreshing is the next-alarm line, which is
 * why [android.R.attr.updatePeriodMillis] is zero: updates are pushed when
 * alarms actually change.
 */
@AndroidEntryPoint
class PowerClockWidgetProvider : AppWidgetProvider() {

    @Inject
    lateinit var alarmRepository: AlarmRepository

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val summary = nextAlarmSummary(context)
                appWidgetIds.forEach { id ->
                    appWidgetManager.updateAppWidget(id, buildViews(context, summary))
                }
            } catch (_: Throwable) {
                // A widget that cannot read the database still tells the time.
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun nextAlarmSummary(context: Context): String {
        val now = ZonedDateTime.now()
        val next = alarmRepository.getAll()
            .filter { it.enabled }
            .mapNotNull { alarm -> NextOccurrenceCalculator.nextTrigger(alarm, now) }
            .minOrNull()
            ?: return context.getString(R.string.widget_no_alarm)
        return context.getString(R.string.widget_next_alarm, TimeFormat.nextAlarm(next))
    }

    private fun buildViews(context: Context, nextAlarm: String): RemoteViews =
        RemoteViews(context.packageName, R.layout.widget_power_clock).apply {
            setTextViewText(R.id.widget_next_alarm, nextAlarm)
            setOnClickPendingIntent(
                R.id.widget_root,
                PendingIntent.getActivity(
                    context,
                    OPEN_APP_REQUEST,
                    Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
        }

    companion object {
        private const val OPEN_APP_REQUEST = 90_001

        /** Redraws the next-alarm line after any alarm change. */
        fun requestUpdate(context: Context) {
            try {
                val manager = AppWidgetManager.getInstance(context) ?: return
                val ids = manager.getAppWidgetIds(
                    ComponentName(context, PowerClockWidgetProvider::class.java),
                )
                if (ids.isEmpty()) return
                context.sendBroadcast(
                    Intent(context, PowerClockWidgetProvider::class.java).apply {
                        action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                    },
                )
            } catch (_: Throwable) {
            }
        }
    }
}
