package com.powerclock.alarm.domain.stats

import com.powerclock.alarm.domain.model.WakeEvent
import com.powerclock.alarm.domain.model.WakeOutcome
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Aggregated, locally computed wake statistics.
 *
 * Power Score formula (documented for users on the Progress screen):
 *
 *   score = 40 x on-time rate (last 30 days)
 *         + 30 x mission completion rate (last 30 days)
 *         + min(20, current streak x 2)
 *         + min(10, total safe reps / 50)
 *
 * capped to 0..100. Days with no alarms are neutral: they neither feed the
 * rates nor break the streak (rest days are respected).
 */
data class WakeStats(
    val currentStreak: Int,
    val bestStreak: Int,
    val onTimeRate30d: Float,
    val completionRate30d: Float,
    val totalReps: Int,
    val avgMissionSeconds: Int,
    val powerScore: Int,
    val last7Days: List<DayResult>,
    val wakes7d: Int,
    val wakes30d: Int,
) {
    enum class DayResult { SUCCESS, EMERGENCY, MISSED, NO_ALARM }

    companion object {
        fun compute(
            events: List<WakeEvent>,
            zone: ZoneId,
            today: LocalDate,
        ): WakeStats {
            val byDay = events.groupBy {
                Instant.ofEpochMilli(it.rangAtMs).atZone(zone).toLocalDate()
            }

            fun dayResult(date: LocalDate): DayResult {
                val dayEvents = byDay[date] ?: return DayResult.NO_ALARM
                return when {
                    dayEvents.any { it.outcome == WakeOutcome.COMPLETED } -> DayResult.SUCCESS
                    dayEvents.any { it.outcome == WakeOutcome.EMERGENCY } -> DayResult.EMERGENCY
                    else -> DayResult.MISSED
                }
            }

            // Streak: walk backwards from today. NO_ALARM days are neutral
            // (rest days never break a streak); the walk ends at the first
            // missed/emergency day or once history is exhausted.
            var currentStreak = 0
            var cursor = today
            var guard = 0
            while (guard < 3650) {
                when (dayResult(cursor)) {
                    DayResult.SUCCESS -> currentStreak++
                    DayResult.NO_ALARM -> {
                        if (byDay.keys.none { it <= cursor }) break
                    }
                    else -> break
                }
                cursor = cursor.minusDays(1)
                guard++
            }

            // Best streak over full history.
            var bestStreak = 0
            var run = 0
            val allDates = byDay.keys.sorted()
            if (allDates.isNotEmpty()) {
                var d = allDates.first()
                while (!d.isAfter(today)) {
                    when (dayResult(d)) {
                        DayResult.SUCCESS -> {
                            run++
                            if (run > bestStreak) bestStreak = run
                        }
                        DayResult.NO_ALARM -> Unit
                        else -> run = 0
                    }
                    d = d.plusDays(1)
                }
            }
            if (currentStreak > bestStreak) bestStreak = currentStreak

            val cutoff30 = today.minusDays(29)
            val recent = events.filter {
                Instant.ofEpochMilli(it.rangAtMs).atZone(zone).toLocalDate() >= cutoff30
            }
            val completed = recent.count { it.outcome == WakeOutcome.COMPLETED }
            val completionRate = if (recent.isEmpty()) 0f else completed.toFloat() / recent.size

            // On time = mission finished within 5 minutes of ringing.
            val onTime = recent.count {
                it.outcome == WakeOutcome.COMPLETED &&
                    it.dismissedAtMs != null &&
                    it.dismissedAtMs - it.rangAtMs <= 5 * 60_000L
            }
            val onTimeRate = if (recent.isEmpty()) 0f else onTime.toFloat() / recent.size

            val totalReps = events.sumOf { it.totalReps }

            val durations = recent.mapNotNull { e ->
                val start = e.missionStartedAtMs ?: return@mapNotNull null
                val end = e.dismissedAtMs ?: return@mapNotNull null
                if (e.outcome == WakeOutcome.COMPLETED && end > start) (end - start) / 1000 else null
            }
            val avgMissionSeconds = if (durations.isEmpty()) 0 else (durations.sum() / durations.size).toInt()

            val score = (
                40f * onTimeRate +
                    30f * completionRate +
                    minOf(20f, currentStreak * 2f) +
                    minOf(10f, totalReps / 50f)
                ).toInt().coerceIn(0, 100)

            val last7 = (6 downTo 0).map { dayResult(today.minusDays(it.toLong())) }

            val cutoff7 = today.minusDays(6)
            val wakes7 = events.count {
                val d = Instant.ofEpochMilli(it.rangAtMs).atZone(zone).toLocalDate()
                d >= cutoff7 && it.outcome == WakeOutcome.COMPLETED
            }

            return WakeStats(
                currentStreak = currentStreak,
                bestStreak = bestStreak,
                onTimeRate30d = onTimeRate,
                completionRate30d = completionRate,
                totalReps = totalReps,
                avgMissionSeconds = avgMissionSeconds,
                powerScore = score,
                last7Days = last7,
                wakes7d = wakes7,
                wakes30d = completed,
            )
        }

        val EMPTY = WakeStats(
            currentStreak = 0, bestStreak = 0, onTimeRate30d = 0f, completionRate30d = 0f,
            totalReps = 0, avgMissionSeconds = 0, powerScore = 0,
            last7Days = List(7) { DayResult.NO_ALARM }, wakes7d = 0, wakes30d = 0,
        )
    }
}
