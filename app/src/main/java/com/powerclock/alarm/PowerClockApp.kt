package com.powerclock.alarm

import android.app.Application
import com.powerclock.alarm.alarmengine.AlarmNotifications
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class PowerClockApp : Application() {

    @Inject
    lateinit var notifications: AlarmNotifications

    override fun onCreate() {
        super.onCreate()
        notifications.ensureChannels()
    }
}
