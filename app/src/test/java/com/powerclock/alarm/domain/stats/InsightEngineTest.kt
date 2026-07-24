package com.powerclock.alarm.domain.stats

import com.google.common.truth.Truth.assertThat
import com.powerclock.alarm.domain.model.WakeEvent
import com.powerclock.alarm.domain.model.WakeOutcome
import org.junit.Test

class InsightEngineTest {

    private fun event(
        outcome: WakeOutcome = WakeOutcome.COMPLETED,
        startDelayMs: Long = 30_000,
        durationMs: Long = 90_000,
        summary: String = "MATH:3",
    ): WakeEvent {
        val rang = 1_000_000L
        return WakeEvent(
            alarmId = 1,
            scheduledAtMs = rang,
            rangAtMs = rang,
            missionStartedAtMs = rang + startDelayMs,
            dismissedAtMs = rang + durationMs,
            outcome = outcome,
            missionSummary = summary,
        )
    }

    @Test
    fun `no events means no suggestions`() {
        assertThat(InsightEngine.suggestions(emptyList(), null)).isEmpty()
    }

    @Test
    fun `slow starts suggest stronger sound`() {
        val events = List(3) { event(startDelayMs = 3 * 60_000L) }
        val kinds = InsightEngine.suggestions(events, null).map { it.kind }
        assertThat(kinds).contains(InsightEngine.Kind.STRONGER_SOUND)
    }

    @Test
    fun `abandoned missions suggest different mission`() {
        val events = List(3) { event(outcome = WakeOutcome.MISSED) }
        val kinds = InsightEngine.suggestions(events, null).map { it.kind }
        assertThat(kinds).contains(InsightEngine.Kind.DIFFERENT_MISSION)
    }

    @Test
    fun `workout emergencies suggest lower target`() {
        val events = List(2) {
            event(outcome = WakeOutcome.EMERGENCY, summary = "PUSH_UPS:10|EMERGENCY")
        }
        val kinds = InsightEngine.suggestions(events, currentWorkoutTarget = 10).map { it.kind }
        assertThat(kinds).contains(InsightEngine.Kind.LOWER_TARGET)
    }

    @Test
    fun `lower target not suggested at minimum`() {
        val events = List(2) {
            event(outcome = WakeOutcome.EMERGENCY, summary = "PUSH_UPS:3|EMERGENCY")
        }
        val kinds = InsightEngine.suggestions(events, currentWorkoutTarget = 3).map { it.kind }
        assertThat(kinds).doesNotContain(InsightEngine.Kind.LOWER_TARGET)
    }

    @Test
    fun `ten consecutive completions suggest raising target`() {
        val events = List(10) { event() }
        val kinds = InsightEngine.suggestions(events, currentWorkoutTarget = 5).map { it.kind }
        assertThat(kinds).contains(InsightEngine.Kind.RAISE_TARGET)
    }

    @Test
    fun `timeouts suggest stacking a QR mission`() {
        val events = List(2) { event(outcome = WakeOutcome.MISSED) }
        val kinds = InsightEngine.suggestions(events, null).map { it.kind }
        assertThat(kinds).contains(InsightEngine.Kind.STACK_MISSIONS)
    }
}
