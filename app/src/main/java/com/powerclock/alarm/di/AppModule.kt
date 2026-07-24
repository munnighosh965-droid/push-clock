package com.powerclock.alarm.di

import android.content.Context
import androidx.room.Room
import com.powerclock.alarm.data.db.AlarmDao
import com.powerclock.alarm.data.db.PowerClockDatabase
import com.powerclock.alarm.data.db.WakeEventDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): PowerClockDatabase =
        Room.databaseBuilder(context, PowerClockDatabase::class.java, PowerClockDatabase.NAME)
            .build()

    @Provides
    fun provideAlarmDao(db: PowerClockDatabase): AlarmDao = db.alarmDao()

    @Provides
    fun provideWakeEventDao(db: PowerClockDatabase): WakeEventDao = db.wakeEventDao()
}
