package com.powerclock.alarm.domain.stats

import com.powerclock.alarm.domain.model.MissionType
import com.powerclock.alarm.domain.model.WakeEvent
import com.powerclock.alarm.domain.model.WakeOutcome

/**
 * Transparent, rule-based personalization. Every suggestion explains its
 * trigger and nothing is ever applied without the user tapping to confirm.
 */
object InsightEngine {

    enum class Kind {
        STRONGER_SOUND,
        DIFFERENT_MISSION,
        LOWER_TARGET,
        RAISE_TARGET,
        EARLIER_BEDTIME,
        STACK_MISSIONS,
    }

    data class Suggestion(val kind: Kind, val reason: String)

    /**
     * @param recentEvents events from roughly the last 14 days, newest last.
     * @param currentWorkoutTarget the configured rep target of the user's
     *   most used workout mission, or null when no workout is configured.
     */
    fun suggestions(
        recentEvents: List<WakeEvent>,
        currentWorkoutTarget: Int?,
    ): List<Suggestion> {
        if (recentEvents.isEmpty()) return emptyList()
        val out = mutableListOf<Suggestion>()

        // Slow responders: mission started > 2 min after ringing, 3+ times.
        val slowStarts = recentEvents.count { e ->
            val start = e.missionStartedAtMs
            start != null && start - e.rangAtMs > 2 * 60_000L
        }
        if (slowStarts >= 3) {
            out += Suggestion(
                Kind.STRONGER_SOUND,
                "You took more than two minutes to start your mission $slowStarts times recently. A stronger sound or Heavy Sleeper mode could help.",
            )
        }

        // Frequently abandoned mission -> emergency or missed 3+ times.
        val abandoned = recentEvents.count { it.outcome != WakeOutcome.COMPLETED }
        if (abandoned >= 3) {
            out += Suggestion(
                Kind.DIFFERENT_MISSION,
                "Your current mission was not completed $abandoned times in the last two weeks. Trying a different mission style might suit your mornings better.",
            )
        }

        // Repeated emergency dismissals on workout missions -> lower target.
        val workoutEmergencies = recentEvents.count {
            it.outcome == WakeOutcome.EMERGENCY && it.missionSummary.containsWorkout()
        }
        if (workoutEmergencies >= 2 && currentWorkoutTarget != null && currentWorkoutTarget > 3) {
            out += Suggestion(
                Kind.LOWER_TARGET,
                "Workout missions ended with emergency dismiss $workoutEmergencies times. A lower repetition target keeps mornings safe and sustainable.",
            )
        }

        // Consistent success streak -> offer (never force) a higher target.
        val lastTen = recentEvents.takeLast(10)
        if (lastTen.size >= 10 && lastTen.all { it.outcome == WakeOutcome.COMPLETED } &&
            currentWorkoutTarget != null && currentWorkoutTarget < 30
        ) {
            out += Suggestion(
                Kind.RAISE_TARGET,
                "Ten completed missions in a row. If it feels easy, you could raise your target by a couple of reps.",
            )
        }

        // Chronic slow mornings -> earlier bedtime reminder.
        val verySlow = recentEvents.count { e ->
            val end = e.dismissedAtMs
            end != null && end - e.rangAtMs > 10 * 60_000L
        }
        if (verySlow >= 3) {
            out += Suggestion(
                Kind.EARLIER_BEDTIME,
                "Several mornings took over ten minutes to finish. An earlier bedtime reminder often makes wake-ups easier.",
            )
        }

        // Repeated MISSED (auto-silenced) -> suggest stacking a QR mission.
        val missed = recentEvents.count { it.outcome == WakeOutcome.MISSED }
        if (missed >= 2) {
            out += Suggestion(
                Kind.STACK_MISSIONS,
                "Some alarms timed out unanswered. Stacking a scan-the-QR-card mission placed away from bed gets you on your feet.",
            )
        }

        return out
    }

    private fun String.containsWorkout(): Boolean =
        MissionType.WORKOUTS.any { contains(it.name) }
}
