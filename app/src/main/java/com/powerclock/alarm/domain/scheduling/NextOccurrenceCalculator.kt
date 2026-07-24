package com.powerclock.alarm.domain.scheduling

import com.powerclock.alarm.domain.model.Alarm
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Pure, timezone-aware computation of when an alarm should fire next.
 *
 * All arithmetic is done with [ZonedDateTime] so daylight-saving gaps and
 * overlaps resolve the way a wall clock user expects: an alarm set for a
 * time that does not exist on a DST-forward day fires at the shifted
 * instant chosen by [java.time]; an ambiguous time fires at the earlier
 * offset.
 */
object NextOccurrenceCalculator {

    /**
     * Returns the next trigger instant for [alarm] strictly after [now],
     * or null when the alarm has no valid occurrence (never happens for
     * enabled alarms, but kept explicit for safety).
     */
    fun nextTrigger(alarm: Alarm, now: ZonedDateTime): ZonedDateTime? {
        val time = LocalTime.of(alarm.hour, alarm.minute)
        if (!alarm.isRepeating) {
            val todayCandidate = candidateAt(now.zone, now.toLocalDate(), time)
            return if (todayCandidate.isAfter(now)) {
                todayCandidate
            } else {
                candidateAt(now.zone, now.toLocalDate().plusDays(1), time)
            }
        }
        // Repeating: scan up to 8 days to cover "today but already passed".
        for (offset in 0..7) {
            val date = now.toLocalDate().plusDays(offset.toLong())
            if (!alarm.repeatsOn(date.dayOfWeek)) continue
            val candidate = candidateAt(now.zone, date, time)
            if (candidate.isAfter(now)) return candidate
        }
        return null
    }

    /**
     * The next trigger for [alarm] after it has just been dismissed at
     * [dismissedAt]. One-time alarms return null (they disable themselves);
     * repeating alarms return the following occurrence.
     */
    fun nextAfterDismissal(alarm: Alarm, dismissedAt: ZonedDateTime): ZonedDateTime? {
        if (!alarm.isRepeating) return null
        return nextTrigger(alarm, dismissedAt)
    }

    /** Days-of-week helper for previews ("Mon, Wed, Fri"). */
    fun describeDays(mask: Int): List<DayOfWeek> = Alarm.daysFor(mask).sortedBy { it.value }

    private fun candidateAt(zone: ZoneId, date: LocalDate, time: LocalTime): ZonedDateTime =
        ZonedDateTime.of(date, time, zone)
}
