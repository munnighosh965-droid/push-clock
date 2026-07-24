package com.powerclock.alarm.domain.scheduling

import com.google.common.truth.Truth.assertThat
import com.powerclock.alarm.domain.model.Alarm
import org.junit.Test
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime

class NextOccurrenceCalculatorTest {

    private val berlin = ZoneId.of("Europe/Berlin")
    private val newYork = ZoneId.of("America/New_York")

    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int, zone: ZoneId = berlin): ZonedDateTime =
        ZonedDateTime.of(y, m, d, h, min, 0, 0, zone)

    // ------------------------------------------------------------ one-time

    @Test
    fun `one-time alarm later today fires today`() {
        val alarm = Alarm(hour = 8, minute = 30)
        val now = at(2025, 6, 10, 6, 0) // Tuesday
        val next = NextOccurrenceCalculator.nextTrigger(alarm, now)!!
        assertThat(next).isEqualTo(at(2025, 6, 10, 8, 30))
    }

    @Test
    fun `one-time alarm already passed rolls to tomorrow`() {
        val alarm = Alarm(hour = 8, minute = 30)
        val now = at(2025, 6, 10, 9, 0)
        val next = NextOccurrenceCalculator.nextTrigger(alarm, now)!!
        assertThat(next).isEqualTo(at(2025, 6, 11, 8, 30))
    }

    @Test
    fun `alarm at exactly now rolls to next occurrence`() {
        val alarm = Alarm(hour = 8, minute = 30)
        val now = at(2025, 6, 10, 8, 30)
        val next = NextOccurrenceCalculator.nextTrigger(alarm, now)!!
        assertThat(next).isEqualTo(at(2025, 6, 11, 8, 30))
    }

    // ------------------------------------------------------- midnight edge

    @Test
    fun `midnight alarm rolls over correctly`() {
        val alarm = Alarm(hour = 0, minute = 0)
        val now = at(2025, 6, 10, 23, 59)
        val next = NextOccurrenceCalculator.nextTrigger(alarm, now)!!
        assertThat(next).isEqualTo(at(2025, 6, 11, 0, 0))
    }

    @Test
    fun `2359 alarm one minute before midnight fires same day`() {
        val alarm = Alarm(hour = 23, minute = 59)
        val now = at(2025, 6, 10, 23, 58)
        val next = NextOccurrenceCalculator.nextTrigger(alarm, now)!!
        assertThat(next).isEqualTo(at(2025, 6, 10, 23, 59))
    }

    // ------------------------------------------------------- weekday masks

    @Test
    fun `weekday alarm skips weekend`() {
        val alarm = Alarm(hour = 7, minute = 0, repeatDaysMask = Alarm.WEEKDAYS)
        val now = at(2025, 6, 13, 8, 0) // Friday after 7:00
        val next = NextOccurrenceCalculator.nextTrigger(alarm, now)!!
        assertThat(next.dayOfWeek).isEqualTo(DayOfWeek.MONDAY)
        assertThat(next).isEqualTo(at(2025, 6, 16, 7, 0))
    }

    @Test
    fun `single-day mask waits a full week when just missed`() {
        val mask = Alarm.maskFor(setOf(DayOfWeek.TUESDAY))
        val alarm = Alarm(hour = 7, minute = 0, repeatDaysMask = mask)
        val now = at(2025, 6, 10, 7, 30) // Tuesday 07:30
        val next = NextOccurrenceCalculator.nextTrigger(alarm, now)!!
        assertThat(next).isEqualTo(at(2025, 6, 17, 7, 0))
    }

    @Test
    fun `every day mask fires today when still ahead`() {
        val alarm = Alarm(hour = 22, minute = 0, repeatDaysMask = Alarm.EVERY_DAY)
        val now = at(2025, 6, 10, 21, 0)
        val next = NextOccurrenceCalculator.nextTrigger(alarm, now)!!
        assertThat(next).isEqualTo(at(2025, 6, 10, 22, 0))
    }

    @Test
    fun `mask helpers roundtrip`() {
        val days = setOf(DayOfWeek.MONDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
        assertThat(Alarm.daysFor(Alarm.maskFor(days))).isEqualTo(days)
    }

    // ------------------------------------------------ daylight saving time

    @Test
    fun `alarm inside DST spring-forward gap resolves to shifted time`() {
        // US DST 2025: clocks jump 02:00 -> 03:00 on March 9.
        val alarm = Alarm(hour = 2, minute = 30)
        val now = at(2025, 3, 9, 0, 0, newYork)
        val next = NextOccurrenceCalculator.nextTrigger(alarm, now)!!
        // 02:30 does not exist; java.time shifts by the gap to 03:30.
        assertThat(next.hour).isEqualTo(3)
        assertThat(next.minute).isEqualTo(30)
        assertThat(next.toLocalDate()).isEqualTo(at(2025, 3, 9, 0, 0, newYork).toLocalDate())
    }

    @Test
    fun `alarm during DST fall-back ambiguity picks earlier offset`() {
        // US DST end 2025: clocks repeat 01:00-02:00 on November 2.
        val alarm = Alarm(hour = 1, minute = 30)
        val now = at(2025, 11, 2, 0, 0, newYork)
        val next = NextOccurrenceCalculator.nextTrigger(alarm, now)!!
        assertThat(next.hour).isEqualTo(1)
        assertThat(next.minute).isEqualTo(30)
        // Earlier offset (EDT, -04:00) is chosen for the ambiguous time.
        assertThat(next.offset.totalSeconds).isEqualTo(-4 * 3600)
    }

    @Test
    fun `repeating alarm across DST keeps wall-clock time`() {
        val alarm = Alarm(hour = 7, minute = 0, repeatDaysMask = Alarm.EVERY_DAY)
        val beforeShift = at(2025, 3, 8, 8, 0, newYork) // day before spring forward
        val next = NextOccurrenceCalculator.nextTrigger(alarm, beforeShift)!!
        assertThat(next.hour).isEqualTo(7)
        assertThat(next.toLocalDate().dayOfMonth).isEqualTo(9)
    }

    // ------------------------------------------------------ timezone moves

    @Test
    fun `calculation follows the zone of the provided now`() {
        val alarm = Alarm(hour = 7, minute = 0)
        val tokyoNow = ZonedDateTime.of(2025, 6, 10, 6, 0, 0, 0, ZoneId.of("Asia/Tokyo"))
        val next = NextOccurrenceCalculator.nextTrigger(alarm, tokyoNow)!!
        assertThat(next.zone).isEqualTo(ZoneId.of("Asia/Tokyo"))
        assertThat(next.hour).isEqualTo(7)
    }

    // ------------------------------------------------------ after dismissal

    @Test
    fun `one-time alarm has no occurrence after dismissal`() {
        val alarm = Alarm(hour = 7, minute = 0)
        val dismissed = at(2025, 6, 10, 7, 5)
        assertThat(NextOccurrenceCalculator.nextAfterDismissal(alarm, dismissed)).isNull()
    }

    @Test
    fun `repeating alarm schedules next day after dismissal`() {
        val alarm = Alarm(hour = 7, minute = 0, repeatDaysMask = Alarm.EVERY_DAY)
        val dismissed = at(2025, 6, 10, 7, 5)
        val next = NextOccurrenceCalculator.nextAfterDismissal(alarm, dismissed)!!
        assertThat(next).isEqualTo(at(2025, 6, 11, 7, 0))
    }
}
