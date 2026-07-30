package com.powerclock.alarm.ui.components

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class TimeFormatTest {

    private fun at(hour: Int, minute: Int): ZonedDateTime =
        ZonedDateTime.of(2026, 7, 29, hour, minute, 0, 0, ZoneId.of("UTC"))

    @Test
    fun `clock renders 12-hour time with meridiem`() {
        assertThat(TimeFormat.clock(at(19, 5))).isEqualTo("7:05 PM")
        assertThat(TimeFormat.clock(at(7, 5))).isEqualTo("7:05 AM")
    }

    @Test
    fun `midnight and noon use 12 rather than 0`() {
        assertThat(TimeFormat.clock(at(0, 30))).isEqualTo("12:30 AM")
        assertThat(TimeFormat.clock(at(12, 30))).isEqualTo("12:30 PM")
        assertThat(TimeFormat.hourMinute(0, 0)).isEqualTo("12:00 AM")
        assertThat(TimeFormat.hourMinute(12, 0)).isEqualTo("12:00 PM")
    }

    @Test
    fun `clock digits and meridiem split for the hero clock`() {
        assertThat(TimeFormat.clockDigits(at(23, 45))).isEqualTo("11:45")
        assertThat(TimeFormat.meridiem(at(23, 45))).isEqualTo("PM")
        assertThat(TimeFormat.meridiem(at(11, 45))).isEqualTo("AM")
    }

    @Test
    fun `hourMinute covers every hour of the day`() {
        for (hour in 0..23) {
            val formatted = TimeFormat.hourMinute(hour, 15)
            val expectedSuffix = if (hour < 12) "AM" else "PM"
            assertThat(formatted).endsWith(expectedSuffix)
            val hour12 = formatted.substringBefore(":").toInt()
            assertThat(hour12).isIn(1..12)
        }
    }

    @Test
    fun `minutesAsClock converts minutes since midnight`() {
        assertThat(TimeFormat.minutesAsClock(0)).isEqualTo("12:00 AM")
        assertThat(TimeFormat.minutesAsClock(23 * 60)).isEqualTo("11:00 PM")
        assertThat(TimeFormat.minutesAsClock(13 * 60 + 7)).isEqualTo("1:07 PM")
    }

    @Test
    fun `nextAlarm includes weekday date and 12-hour time`() {
        assertThat(TimeFormat.nextAlarm(at(6, 30))).isEqualTo("Wed, Jul 29 · 6:30 AM")
    }
}
