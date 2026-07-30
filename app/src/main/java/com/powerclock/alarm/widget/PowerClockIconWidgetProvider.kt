package com.powerclock.alarm.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.powerclock.alarm.MainActivity
import com.powerclock.alarm.R

/**
 * A one-cell widget holding nothing but the Power Clock dial, so it can sit
 * among the app icons and read as a Power Clock icon that actually keeps
 * time. Android reserves live launcher icons for the preinstalled clock app;
 * this is the closest a third-party app can get.
 *
 * There is no data to refresh — the hands are driven by the framework's
 * AnalogClock inside the launcher process — so the provider only wires up the
 * tap target.
 */
class PowerClockIconWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_clock_icon).apply {
            setOnClickPendingIntent(
                R.id.icon_widget_root,
                PendingIntent.getActivity(
                    context,
                    OPEN_APP_REQUEST,
                    Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
        }
        appWidgetIds.forEach { id -> appWidgetManager.updateAppWidget(id, views) }
    }

    private companion object {
        const val OPEN_APP_REQUEST = 90_002
    }
}
