package com.powerclock.alarm.domain.missions

import com.powerclock.alarm.domain.model.MissionConfig
import com.powerclock.alarm.domain.model.MissionType

/**
 * Guarantees that dismissing an alarm always has to be earned.
 *
 * Rule: every ringing alarm must contain at least one workout mission. If the
 * configured stack has none, a default workout is appended at ring time. Users
 * who told us exercise is not safe for them keep their configured missions;
 * if they configured nothing, a brain mission is used instead so the alarm
 * still cannot be dismissed with a single tap.
 */
object MissionEnforcer {

    val DEFAULT_WORKOUT = MissionConfig(MissionType.SQUATS, target = 5)
    val DEFAULT_BRAIN = MissionConfig(MissionType.MATH, target = 3, difficulty = 1)

    fun enforce(configured: List<MissionConfig>, cannotExercise: Boolean): List<MissionConfig> = when {
        cannotExercise && configured.isNotEmpty() -> configured
        cannotExercise -> listOf(DEFAULT_BRAIN)
        configured.any { it.type.isWorkout } -> configured
        else -> configured + DEFAULT_WORKOUT
    }
}
