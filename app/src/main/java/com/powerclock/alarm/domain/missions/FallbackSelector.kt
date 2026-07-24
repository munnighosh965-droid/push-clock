package com.powerclock.alarm.domain.missions

import com.powerclock.alarm.domain.model.MissionConfig
import com.powerclock.alarm.domain.model.MissionType

/**
 * Decides what happens when a mission cannot run during a live alarm
 * (camera denied, camera busy, sensor missing, pose model unavailable...).
 *
 * The rule: the failing mission alone is swapped for the alarm's configured
 * fallback; the rest of the stack is untouched, and the fallback itself must
 * never require the capability that just failed. The alarm always remains
 * dismissible.
 */
object FallbackSelector {

    enum class FailureReason { CAMERA_UNAVAILABLE, SENSOR_UNAVAILABLE, POSE_MODEL_UNAVAILABLE }

    fun replacementFor(
        failed: MissionConfig,
        configuredFallback: MissionType,
        reason: FailureReason,
    ): MissionConfig {
        val safeType = when {
            reason == FailureReason.CAMERA_UNAVAILABLE && configuredFallback.needsCamera ->
                MissionType.MATH
            reason == FailureReason.POSE_MODEL_UNAVAILABLE && configuredFallback.isWorkout ->
                MissionType.MATH
            reason == FailureReason.SENSOR_UNAVAILABLE && configuredFallback == MissionType.SHAKE ->
                MissionType.MATH
            else -> configuredFallback
        }
        val target = when (safeType) {
            MissionType.MATH -> failed.target.coerceIn(1, 5)
            MissionType.MEMORY -> failed.target.coerceIn(1, 3)
            MissionType.TYPING -> 1
            MissionType.SHAKE -> 20
            else -> MissionConfig.defaultTarget(safeType)
        }
        return MissionConfig(
            type = safeType,
            target = target,
            difficulty = failed.difficulty,
            sensitivity = failed.sensitivity,
        )
    }
}
