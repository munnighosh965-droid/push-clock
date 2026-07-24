package com.powerclock.alarm.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.powerclock.alarm.domain.model.Alarm
import com.powerclock.alarm.domain.model.MissionType
import com.powerclock.alarm.domain.model.WakeEvent
import com.powerclock.alarm.domain.model.WakeOutcome

@Entity(tableName = "alarms")
data class AlarmEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val hour: Int,
    val minute: Int,
    val repeatDaysMask: Int,
    val label: String,
    val enabled: Boolean,
    val soundId: String,
    val randomSound: Boolean,
    val customSoundUri: String?,
    val customSoundTitle: String?,
    val customSoundStartMs: Long,
    val volumePercent: Int,
    val gradualVolume: Boolean,
    val heavySleeper: Boolean,
    val vibrate: Boolean,
    val vibrationPatternId: Int,
    val flashlight: Boolean,
    val missionsEncoded: String,
    val fallbackMissionType: String,
    val createdAtMs: Long,
) {
    fun toDomain() = Alarm(
        id = id,
        hour = hour,
        minute = minute,
        repeatDaysMask = repeatDaysMask,
        label = label,
        enabled = enabled,
        soundId = soundId,
        randomSound = randomSound,
        customSoundUri = customSoundUri,
        customSoundTitle = customSoundTitle,
        customSoundStartMs = customSoundStartMs,
        volumePercent = volumePercent,
        gradualVolume = gradualVolume,
        heavySleeper = heavySleeper,
        vibrate = vibrate,
        vibrationPatternId = vibrationPatternId,
        flashlight = flashlight,
        missionsEncoded = missionsEncoded,
        fallbackMissionType = MissionType.entries.firstOrNull { it.name == fallbackMissionType }
            ?: MissionType.MATH,
        createdAtMs = createdAtMs,
    )

    companion object {
        fun fromDomain(a: Alarm) = AlarmEntity(
            id = a.id,
            hour = a.hour,
            minute = a.minute,
            repeatDaysMask = a.repeatDaysMask,
            label = a.label,
            enabled = a.enabled,
            soundId = a.soundId,
            randomSound = a.randomSound,
            customSoundUri = a.customSoundUri,
            customSoundTitle = a.customSoundTitle,
            customSoundStartMs = a.customSoundStartMs,
            volumePercent = a.volumePercent,
            gradualVolume = a.gradualVolume,
            heavySleeper = a.heavySleeper,
            vibrate = a.vibrate,
            vibrationPatternId = a.vibrationPatternId,
            flashlight = a.flashlight,
            missionsEncoded = a.missionsEncoded,
            fallbackMissionType = a.fallbackMissionType.name,
            createdAtMs = a.createdAtMs,
        )
    }
}

@Entity(tableName = "wake_events")
data class WakeEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val alarmId: Long,
    val alarmLabel: String,
    val scheduledAtMs: Long,
    val rangAtMs: Long,
    val missionStartedAtMs: Long?,
    val dismissedAtMs: Long?,
    val outcome: String,
    val totalReps: Int,
    val missionSummary: String,
    val energyRating: Int?,
) {
    fun toDomain() = WakeEvent(
        id = id,
        alarmId = alarmId,
        alarmLabel = alarmLabel,
        scheduledAtMs = scheduledAtMs,
        rangAtMs = rangAtMs,
        missionStartedAtMs = missionStartedAtMs,
        dismissedAtMs = dismissedAtMs,
        outcome = WakeOutcome.entries.firstOrNull { it.name == outcome } ?: WakeOutcome.MISSED,
        totalReps = totalReps,
        missionSummary = missionSummary,
        energyRating = energyRating,
    )

    companion object {
        fun fromDomain(e: WakeEvent) = WakeEventEntity(
            id = e.id,
            alarmId = e.alarmId,
            alarmLabel = e.alarmLabel,
            scheduledAtMs = e.scheduledAtMs,
            rangAtMs = e.rangAtMs,
            missionStartedAtMs = e.missionStartedAtMs,
            dismissedAtMs = e.dismissedAtMs,
            outcome = e.outcome.name,
            totalReps = e.totalReps,
            missionSummary = e.missionSummary,
            energyRating = e.energyRating,
        )
    }
}
