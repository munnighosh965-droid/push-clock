package com.powerclock.alarm.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AlarmDao {
    @Query("SELECT * FROM alarms ORDER BY hour, minute")
    fun observeAll(): Flow<List<AlarmEntity>>

    @Query("SELECT * FROM alarms ORDER BY hour, minute")
    suspend fun getAll(): List<AlarmEntity>

    @Query("SELECT * FROM alarms WHERE enabled = 1")
    suspend fun getEnabled(): List<AlarmEntity>

    @Query("SELECT * FROM alarms WHERE id = :id")
    suspend fun getById(id: Long): AlarmEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(alarm: AlarmEntity): Long

    @Update
    suspend fun update(alarm: AlarmEntity)

    @Delete
    suspend fun delete(alarm: AlarmEntity)

    @Query("DELETE FROM alarms WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM alarms")
    suspend fun deleteAll()
}

@Dao
interface WakeEventDao {
    @Query("SELECT * FROM wake_events ORDER BY rangAtMs DESC")
    fun observeAll(): Flow<List<WakeEventEntity>>

    @Query("SELECT * FROM wake_events ORDER BY rangAtMs DESC")
    suspend fun getAll(): List<WakeEventEntity>

    @Query("SELECT * FROM wake_events WHERE rangAtMs >= :sinceMs ORDER BY rangAtMs ASC")
    suspend fun getSince(sinceMs: Long): List<WakeEventEntity>

    @Query("SELECT * FROM wake_events WHERE id = :id")
    suspend fun getById(id: Long): WakeEventEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: WakeEventEntity): Long

    @Update
    suspend fun update(event: WakeEventEntity)

    @Query("DELETE FROM wake_events")
    suspend fun deleteAll()
}
