package com.powerclock.alarm.domain.missions

import com.google.common.truth.Truth.assertThat
import com.powerclock.alarm.domain.missions.FallbackSelector.FailureReason
import com.powerclock.alarm.domain.model.MissionConfig
import com.powerclock.alarm.domain.model.MissionType
import org.junit.Test

class FallbackSelectorTest {

    @Test
    fun `camera failure with camera-needing fallback forces math`() {
        val failed = MissionConfig(MissionType.PUSH_UPS, target = 10)
        val replacement = FallbackSelector.replacementFor(
            failed, configuredFallback = MissionType.QR_SCAN, reason = FailureReason.CAMERA_UNAVAILABLE,
        )
        assertThat(replacement.type).isEqualTo(MissionType.MATH)
        assertThat(replacement.type.needsCamera).isFalse()
    }

    @Test
    fun `camera failure keeps configured non-camera fallback`() {
        val failed = MissionConfig(MissionType.SQUATS, target = 8)
        val replacement = FallbackSelector.replacementFor(
            failed, MissionType.TYPING, FailureReason.CAMERA_UNAVAILABLE,
        )
        assertThat(replacement.type).isEqualTo(MissionType.TYPING)
    }

    @Test
    fun `pose model failure never falls back to another workout`() {
        val failed = MissionConfig(MissionType.JUMPING_JACKS, target = 20)
        // A workout can never be the configured fallback in the UI, but the
        // selector must stay safe even against corrupt data.
        val replacement = FallbackSelector.replacementFor(
            failed, MissionType.PUSH_UPS, FailureReason.POSE_MODEL_UNAVAILABLE,
        )
        assertThat(replacement.type.isWorkout).isFalse()
    }

    @Test
    fun `shake sensor failure with shake fallback forces math`() {
        val failed = MissionConfig(MissionType.SHAKE, target = 30)
        val replacement = FallbackSelector.replacementFor(
            failed, MissionType.SHAKE, FailureReason.SENSOR_UNAVAILABLE,
        )
        assertThat(replacement.type).isEqualTo(MissionType.MATH)
    }

    @Test
    fun `replacement target stays within sane bounds`() {
        val failed = MissionConfig(MissionType.PUSH_UPS, target = 30)
        val replacement = FallbackSelector.replacementFor(
            failed, MissionType.MATH, FailureReason.CAMERA_UNAVAILABLE,
        )
        assertThat(replacement.target).isAtMost(5)
        assertThat(replacement.target).isAtLeast(1)
    }
}
