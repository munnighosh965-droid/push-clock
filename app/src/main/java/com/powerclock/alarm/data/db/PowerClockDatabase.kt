package com.powerclock.alarm.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [AlarmEntity::class, WakeEventEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class PowerClockDatabase : RoomDatabase() {
    abstract fun alarmDao(): AlarmDao
    abstract fun wakeEventDao(): WakeEventDao

    companion object {
        const val NAME = "powerclock.db"
    }
}
