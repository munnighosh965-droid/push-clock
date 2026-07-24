package com.powerclock.alarm.domain.stats

import com.google.common.truth.Truth.assertThat
import com.powerclock.alarm.domain.model.WakeEvent
import com.powerclock.alarm.domain.model.WakeOutcome
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class WakeStatsTest {

    private val zone = ZoneId.of("UTC")
    private val today = LocalDate.of(2025, 6, 20)

    private fun event(
        daysAgo: Long,
        outcome: WakeOutcome,
        reps: Int = 0,
        dismissSeconds: Long = 60,
    ): WakeEvent {
        val rang = today.minusDays(daysAgo).atTime(LocalTime.of(7, 0)).atZone(zone)
            .toInstant().toEpochMilli()
        return WakeEvent(
            alarmId = 1,
            scheduledAtMs = rang,
            rangAtMs = rang,
            missionStartedAtMs = rang + 10_000,
            dismissedAtMs = if (outcome == WakeOutcome.MISSED) null else rang + dismissSeconds * 1000,
            outcome = outcome,
            totalReps = reps,
        )
    }

    @Test
    fun `empty history gives empty stats`() {
        val stats = WakeStats.compute(emptyList(), zone, today)
        assertThat(stats.currentStreak).isEqualTo(0)
        assertThat(stats.powerScore).isEqualTo(0)
    }

    @Test
    fun `consecutive successes build streak`() {
        val events = (0L..4L).map { event(it, WakeOutcome.COMPLETED) }
        val stats = WakeStats.compute(events, zone, today)
        assertThat(stats.currentStreak).isEqualTo(5)
        assertThat(stats.bestStreak).isEqualTo(5)
    }

    @Test
    fun `rest days without alarms do not break streak`() {
        // Success today, nothing yesterday (rest), success two days ago.
        val events = listOf(event(0, WakeOutcome.COMPLETED), event(2, WakeOutcome.COMPLETED))
        val stats = WakeStats.compute(events, zone, today)
        assertThat(stats.currentStreak).isEqualTo(2)
    }

    @Test
    fun `missed day breaks current streak but best is kept`() {
        val events = listOf(
            event(0, WakeOutcome.COMPLETED),
            event(1, WakeOutcome.MISSED),
            event(2, WakeOutcome.COMPLETED),
            event(3, WakeOutcome.COMPLETED),
            event(4, WakeOutcome.COMPLETED),
        )
        val stats = WakeStats.compute(events, zone, today)
        assertThat(stats.currentStreak).isEqualTo(1)
        assertThat(stats.bestStreak).isEqualTo(3)
    }

    @Test
    fun `emergency dismissal counts as break`() {
        val events = listOf(
            event(0, WakeOutcome.COMPLETED),
            event(1, WakeOutcome.EMERGENCY),
        )
        val stats = WakeStats.compute(events, zone, today)
        assertThat(stats.currentStreak).isEqualTo(1)
    }

    @Test
    fun `power score formula matches documentation`() {
        // 10 days, all completed on time, 100 reps total, streak 10.
        val events = (0L..9L).map { event(it, WakeOutcome.COMPLETED, reps = 10) }
        val stats = WakeStats.compute(events, zone, today)
        // 40*1.0 + 30*1.0 + min(20, 10*2) + min(10, 100/50) = 40+30+20+2 = 92
        assertThat(stats.powerScore).isEqualTo(92)
    }

    @Test
    fun `slow dismissals reduce on-time rate`() {
        val events = listOf(
            event(0, WakeOutcome.COMPLETED, dismissSeconds = 30),
            event(1, WakeOutcome.COMPLETED, dismissSeconds = 60 * 20), // 20 min
        )
        val stats = WakeStats.compute(events, zone, today)
        assertThat(stats.onTimeRate30d).isWithin(0.01f).of(0.5f)
        assertThat(stats.completionRate30d).isWithin(0.01f).of(1f)
    }

    @Test
    fun `total reps accumulate across all history`() {
        val events = listOf(
            event(0, WakeOutcome.COMPLETED, reps = 5),
            event(1, WakeOutcome.COMPLETED, reps = 7),
        )
        assertThat(WakeStats.compute(events, zone, today).totalReps).isEqualTo(12)
    }

    @Test
    fun `last7Days marks outcomes in chronological order`() {
        val events = listOf(event(0, WakeOutcome.COMPLETED), event(6, WakeOutcome.MISSED))
        val stats = WakeStats.compute(events, zone, today)
        assertThat(stats.last7Days.first()).isEqualTo(WakeStats.DayResult.MISSED)
        assertThat(stats.last7Days.last()).isEqualTo(WakeStats.DayResult.SUCCESS)
        assertThat(stats.last7Days[3]).isEqualTo(WakeStats.DayResult.NO_ALARM)
    }
}
