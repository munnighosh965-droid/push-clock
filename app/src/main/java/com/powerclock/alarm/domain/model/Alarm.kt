package com.powerclock.alarm.domain.model

import java.time.DayOfWeek

/**
 * Domain model of a single alarm.
 *
 * [repeatDaysMask] uses bit 0 = Monday .. bit 6 = Sunday (ISO order).
 * A mask of 0 means a one-time alarm that fires at the next occurrence of
 * [hour]:[minute] and then disables itself.
 */
data class Alarm(
    val id: Long = 0L,
    val hour: Int,
    val minute: Int,
    val repeatDaysMask: Int = 0,
    val label: String = "",
    val enabled: Boolean = true,
    val soundId: String = "reactor",
    val randomSound: Boolean = false,
    val customSoundUri: String? = null,
    val customSoundTitle: String? = null,
    val customSoundStartMs: Long = 0L,
    val volumePercent: Int = 80,
    val gradualVolume: Boolean = true,
    val heavySleeper: Boolean = false,
    val vibrate: Boolean = true,
    val vibrationPatternId: Int = 0,
    val flashlight: Boolean = false,
    val missionsEncoded: String = "",
    val fallbackMissionType: MissionType = MissionType.MATH,
    val createdAtMs: Long = 0L,
) {
    init {
        require(hour in 0..23) { "hour out of range" }
        require(minute in 0..59) { "minute out of range" }
        require(volumePercent in 0..100) { "volume out of range" }
    }

    val missions: List<MissionConfig> get() = MissionConfig.decodeStack(missionsEncoded)
    val isRepeating: Boolean get() = repeatDaysMask != 0

    fun repeatsOn(day: DayOfWeek): Boolean =
        repeatDaysMask and (1 shl (day.value - 1)) != 0

    companion object {
        fun maskFor(days: Set<DayOfWeek>): Int =
            days.fold(0) { acc, d -> acc or (1 shl (d.value - 1)) }

        fun daysFor(mask: Int): Set<DayOfWeek> =
            DayOfWeek.entries.filter { mask and (1 shl (it.value - 1)) != 0 }.toSet()

        const val EVERY_DAY = 0b1111111
        val WEEKDAYS = maskFor(
            setOf(
                DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY,
            ),
        )
    }
}
