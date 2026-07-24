package com.powerclock.alarm.domain.model

/** How a ringing session ended. */
enum class WakeOutcome {
    /** All missions completed. */
    COMPLETED,

    /** User used the deliberate emergency dismiss. */
    EMERGENCY,

    /** Alarm auto-silenced after the safety timeout. */
    MISSED,
}

/**
 * One historical wake attempt, stored locally only.
 */
data class WakeEvent(
    val id: Long = 0L,
    val alarmId: Long,
    val alarmLabel: String = "",
    val scheduledAtMs: Long,
    val rangAtMs: Long,
    val missionStartedAtMs: Long? = null,
    val dismissedAtMs: Long? = null,
    val outcome: WakeOutcome = WakeOutcome.MISSED,
    val totalReps: Int = 0,
    val missionSummary: String = "",
    val energyRating: Int? = null,
)
