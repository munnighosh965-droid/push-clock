package com.powerclock.alarm.domain.missions

import com.google.common.truth.Truth.assertThat
import com.powerclock.alarm.domain.model.MissionConfig
import com.powerclock.alarm.domain.model.MissionType
import org.junit.Test

class MissionEnforcerTest {

    @Test
    fun `empty stack gets the default workout`() {
        val result = MissionEnforcer.enforce(emptyList(), cannotExercise = false)
        assertThat(result).containsExactly(MissionEnforcer.DEFAULT_WORKOUT)
    }

    @Test
    fun `stack without a workout gets one appended`() {
        val math = MissionConfig(MissionType.MATH, target = 3)
        val result = MissionEnforcer.enforce(listOf(math), cannotExercise = false)
        assertThat(result).containsExactly(math, MissionEnforcer.DEFAULT_WORKOUT).inOrder()
    }

    @Test
    fun `stack with a workout is unchanged`() {
        val stack = listOf(
            MissionConfig(MissionType.MATH, target = 3),
            MissionConfig(MissionType.PUSH_UPS, target = 10),
        )
        assertThat(MissionEnforcer.enforce(stack, cannotExercise = false)).isEqualTo(stack)
    }

    @Test
    fun `cannot-exercise users keep their configured missions`() {
        val stack = listOf(MissionConfig(MissionType.TYPING, target = 1))
        assertThat(MissionEnforcer.enforce(stack, cannotExercise = true)).isEqualTo(stack)
    }

    @Test
    fun `cannot-exercise users with an empty stack get a brain mission`() {
        val result = MissionEnforcer.enforce(emptyList(), cannotExercise = true)
        assertThat(result).containsExactly(MissionEnforcer.DEFAULT_BRAIN)
        assertThat(result.none { it.type.isWorkout }).isTrue()
    }

    @Test
    fun `enforced stacks are never empty`() {
        assertThat(MissionEnforcer.enforce(emptyList(), cannotExercise = false)).isNotEmpty()
        assertThat(MissionEnforcer.enforce(emptyList(), cannotExercise = true)).isNotEmpty()
    }
}
