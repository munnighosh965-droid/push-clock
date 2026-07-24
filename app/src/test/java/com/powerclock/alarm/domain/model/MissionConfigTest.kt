package com.powerclock.alarm.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MissionConfigTest {

    @Test
    fun `encode decode roundtrip`() {
        val config = MissionConfig(MissionType.SQUATS, target = 12, difficulty = 2, sensitivity = Sensitivity.STRICT)
        assertThat(MissionConfig.decode(config.encode())).isEqualTo(config)
    }

    @Test
    fun `stack roundtrip preserves order`() {
        val stack = listOf(
            MissionConfig(MissionType.QR_SCAN, target = 1),
            MissionConfig(MissionType.SQUATS, target = 5, sensitivity = Sensitivity.BEGINNER),
            MissionConfig(MissionType.MATH, target = 2, difficulty = 3),
        )
        val decoded = MissionConfig.decodeStack(MissionConfig.encodeStack(stack))
        assertThat(decoded).isEqualTo(stack)
    }

    @Test
    fun `decode garbage returns null`() {
        assertThat(MissionConfig.decode("NOT_A_MISSION:5:1:NORMAL")).isNull()
        assertThat(MissionConfig.decode("")).isNull()
        assertThat(MissionConfig.decode("MATH")).isNull()
    }

    @Test
    fun `decode clamps out-of-range target`() {
        val decoded = MissionConfig.decode("SQUATS:999:1:NORMAL")!!
        assertThat(decoded.target).isEqualTo(MissionConfig.MAX_TARGET)
    }

    @Test
    fun `decodeStack skips broken entries`() {
        val decoded = MissionConfig.decodeStack("MATH:3:1:NORMAL|garbage|SHAKE:20:1:NORMAL")
        assertThat(decoded.map { it.type }).containsExactly(MissionType.MATH, MissionType.SHAKE).inOrder()
    }

    @Test(expected = IllegalArgumentException::class)
    fun `unsafe target rejected`() {
        MissionConfig(MissionType.PUSH_UPS, target = 500)
    }

    @Test
    fun `safe fallbacks never need camera`() {
        MissionType.SAFE_FALLBACKS.forEach {
            assertThat(it.needsCamera).isFalse()
            assertThat(it.isWorkout).isFalse()
        }
    }
}
