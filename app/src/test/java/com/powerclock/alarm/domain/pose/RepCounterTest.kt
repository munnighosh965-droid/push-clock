package com.powerclock.alarm.domain.pose

import com.google.common.truth.Truth.assertThat
import com.powerclock.alarm.domain.model.MissionType
import com.powerclock.alarm.domain.model.Sensitivity
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

/**
 * Rep-counter tests driven entirely by synthetic landmark sequences:
 * valid repetitions, partial repetitions, jitter/noise, low-confidence
 * pauses, and double-count prevention.
 */
class RepCounterTest {

    // ------------------------------------------------------------ builders

    /** Push-up sample with both elbows at [elbowDeg]; alignment joints omitted. */
    private fun pushUpSample(tMs: Long, elbowDeg: Float, visibility: Float = 0.9f): PoseSample {
        val rad = Math.toRadians(elbowDeg.toDouble())
        fun arm(shoulderX: Float, y: Float): Triple<PosePoint, PosePoint, PosePoint> {
            val d = 0.15f
            val shoulder = PosePoint(shoulderX, y, visibility)
            val elbow = PosePoint(shoulderX + d, y, visibility)
            // elbow->shoulder = (-1,0); wrist at inner angle elbowDeg.
            val wrist = PosePoint(
                elbow.x + d * (-cos(rad)).toFloat(),
                elbow.y + d * sin(rad).toFloat(),
                visibility,
            )
            return Triple(shoulder, elbow, wrist)
        }
        val (ls, le, lw) = arm(0.3f, 0.45f)
        val (rs, re, rw) = arm(0.3f, 0.55f)
        return PoseSample(
            tMs,
            mapOf(
                BodyPoint.LEFT_SHOULDER to ls, BodyPoint.LEFT_ELBOW to le, BodyPoint.LEFT_WRIST to lw,
                BodyPoint.RIGHT_SHOULDER to rs, BodyPoint.RIGHT_ELBOW to re, BodyPoint.RIGHT_WRIST to rw,
            ),
        )
    }

    /** Squat sample with both knees at [kneeDeg]; hip drops as the knee bends. */
    private fun squatSample(tMs: Long, kneeDeg: Float, visibility: Float = 0.9f): PoseSample {
        val rad = Math.toRadians(kneeDeg.toDouble())
        fun leg(x: Float): Triple<PosePoint, PosePoint, PosePoint> {
            val knee = PosePoint(x, 0.6f, visibility)
            val ankle = PosePoint(x, 0.8f, visibility)
            // knee->ankle = (0, 1): hip at inner angle kneeDeg.
            val hip = PosePoint(
                x + 0.2f * sin(rad).toFloat(),
                0.6f + 0.2f * cos(rad).toFloat(),
                visibility,
            )
            return Triple(hip, knee, ankle)
        }
        val (lh, lk, la) = leg(0.45f)
        val (rh, rk, ra) = leg(0.55f)
        return PoseSample(
            tMs,
            mapOf(
                BodyPoint.LEFT_HIP to lh, BodyPoint.LEFT_KNEE to lk, BodyPoint.LEFT_ANKLE to la,
                BodyPoint.RIGHT_HIP to rh, BodyPoint.RIGHT_KNEE to rk, BodyPoint.RIGHT_ANKLE to ra,
            ),
        )
    }

    /** Jumping-jack sample: [open] = star position, otherwise neutral. */
    private fun jackSample(tMs: Long, open: Boolean, visibility: Float = 0.9f): PoseSample {
        val wristY = if (open) 0.15f else 0.5f
        val ankleSpread = if (open) 0.2f else 0.04f
        return PoseSample(
            tMs,
            mapOf(
                BodyPoint.NOSE to PosePoint(0.5f, 0.1f, visibility),
                BodyPoint.LEFT_SHOULDER to PosePoint(0.4f, 0.3f, visibility),
                BodyPoint.RIGHT_SHOULDER to PosePoint(0.6f, 0.3f, visibility),
                BodyPoint.LEFT_WRIST to PosePoint(0.35f, wristY, visibility),
                BodyPoint.RIGHT_WRIST to PosePoint(0.65f, wristY, visibility),
                BodyPoint.LEFT_ANKLE to PosePoint(0.5f - ankleSpread, 0.9f, visibility),
                BodyPoint.RIGHT_ANKLE to PosePoint(0.5f + ankleSpread, 0.9f, visibility),
            ),
        )
    }

    /** Feeds [angles] at 100 ms intervals and returns the final rep count. */
    private fun runPushUps(counter: RepCounter, angles: List<Float>, visibility: Float = 0.9f): Int {
        var t = 0L
        var reps = 0
        for (angle in angles) {
            t += 100
            reps = counter.process(pushUpSample(t, angle, visibility)).repCount
        }
        return reps
    }

    private fun holdAngles(angle: Float, frames: Int) = List(frames) { angle }

    // ------------------------------------------------------------ push-ups

    @Test
    fun `full push-up cycles count`() {
        val counter = RepCounter.forType(MissionType.PUSH_UPS, Sensitivity.NORMAL)
        val oneRep = holdAngles(170f, 5) + holdAngles(90f, 5) + holdAngles(170f, 5)
        val reps = runPushUps(counter, oneRep + holdAngles(90f, 5) + holdAngles(170f, 5))
        assertThat(reps).isEqualTo(2)
    }

    @Test
    fun `partial push-up does not count`() {
        val counter = RepCounter.forType(MissionType.PUSH_UPS, Sensitivity.NORMAL)
        // Only descends to 120 degrees: never reaches the 100-degree work zone.
        val reps = runPushUps(
            counter,
            holdAngles(170f, 5) + holdAngles(120f, 5) + holdAngles(170f, 5),
        )
        assertThat(reps).isEqualTo(0)
    }

    @Test
    fun `beginner sensitivity accepts shallower reps than strict`() {
        val angles = holdAngles(150f, 5) + holdAngles(110f, 5) + holdAngles(150f, 5)
        val beginner = RepCounter.forType(MissionType.PUSH_UPS, Sensitivity.BEGINNER)
        val strict = RepCounter.forType(MissionType.PUSH_UPS, Sensitivity.STRICT)
        assertThat(runPushUps(beginner, angles)).isEqualTo(1)
        assertThat(runPushUps(strict, angles)).isEqualTo(0)
    }

    @Test
    fun `jitter around the threshold cannot double count`() {
        val counter = RepCounter.forType(MissionType.PUSH_UPS, Sensitivity.NORMAL)
        // One descent with noisy bouncing at the bottom, then one ascent:
        // must count exactly once.
        val noisyBottom = listOf(95f, 105f, 92f, 108f, 90f, 104f, 95f)
        val reps = runPushUps(counter, holdAngles(170f, 5) + noisyBottom + holdAngles(170f, 8))
        assertThat(reps).isEqualTo(1)
    }

    @Test
    fun `instant flapping is rejected by timing gates`() {
        val counter = RepCounter.forType(MissionType.PUSH_UPS, Sensitivity.NORMAL)
        // Alternating every single frame (100 ms) - faster than a human
        // push-up; the phase gates allow at most a fraction to count.
        var t = 0L
        var reps = 0
        repeat(30) { i ->
            t += 100
            val angle = if (i % 2 == 0) 170f else 90f
            reps = counter.process(pushUpSample(t, angle)).repCount
        }
        // 15 "cycles" attempted in 3 seconds; legitimate counting allows
        // at most 3s / 0.7s spacing = 4.
        assertThat(reps).isAtMost(4)
    }

    @Test
    fun `low confidence pauses counting and keeps state`() {
        val counter = RepCounter.forType(MissionType.PUSH_UPS, Sensitivity.NORMAL)
        var t = 0L
        // Get into start position.
        repeat(5) {
            t += 100
            counter.process(pushUpSample(t, 170f))
        }
        // Camera loses the user mid-rep.
        t += 100
        val paused = counter.process(pushUpSample(t, 90f, visibility = 0.1f))
        assertThat(paused.paused).isTrue()
        assertThat(paused.hint).isEqualTo(PoseHint.NOT_VISIBLE)
        assertThat(paused.repCount).isEqualTo(0)
        // Confidence returns; the rep can still be completed.
        repeat(5) {
            t += 100
            counter.process(pushUpSample(t, 90f))
        }
        var result: RepUpdate? = null
        repeat(5) {
            t += 100
            result = counter.process(pushUpSample(t, 170f))
        }
        assertThat(result!!.repCount).isEqualTo(1)
    }

    @Test
    fun `knee push-up variant counts like push-up`() {
        val counter = RepCounter.forType(MissionType.KNEE_PUSH_UPS, Sensitivity.NORMAL)
        val reps = runPushUps(
            counter,
            holdAngles(170f, 5) + holdAngles(90f, 5) + holdAngles(170f, 5),
        )
        assertThat(reps).isEqualTo(1)
    }

    @Test
    fun `reset clears progress`() {
        val counter = RepCounter.forType(MissionType.PUSH_UPS, Sensitivity.NORMAL)
        runPushUps(counter, holdAngles(170f, 5) + holdAngles(90f, 5) + holdAngles(170f, 5))
        assertThat(counter.repCount).isEqualTo(1)
        counter.reset()
        assertThat(counter.repCount).isEqualTo(0)
    }

    // -------------------------------------------------------------- squats

    @Test
    fun `full squat cycles count with calibrated hip drop`() {
        val counter = RepCounter.forType(MissionType.SQUATS, Sensitivity.NORMAL)
        var t = 0L
        var reps = 0
        fun feed(angle: Float, frames: Int) {
            repeat(frames) {
                t += 100
                reps = counter.process(squatSample(t, angle)).repCount
            }
        }
        feed(175f, 6) // stand + calibrate
        feed(90f, 6)  // deep squat
        feed(175f, 6) // stand
        feed(90f, 6)
        feed(175f, 6)
        assertThat(reps).isEqualTo(2)
    }

    @Test
    fun `half squat does not count`() {
        val counter = RepCounter.forType(MissionType.SQUATS, Sensitivity.NORMAL)
        var t = 0L
        var reps = 0
        fun feed(angle: Float, frames: Int) {
            repeat(frames) {
                t += 100
                reps = counter.process(squatSample(t, angle)).repCount
            }
        }
        feed(175f, 6)
        feed(140f, 6) // knees barely bent
        feed(175f, 6)
        assertThat(reps).isEqualTo(0)
    }

    // ------------------------------------------------------- jumping jacks

    @Test
    fun `jumping jack open-close cycles count`() {
        val counter = RepCounter.forType(MissionType.JUMPING_JACKS, Sensitivity.NORMAL)
        var t = 0L
        var reps = 0
        fun feed(open: Boolean, frames: Int) {
            repeat(frames) {
                t += 100
                reps = counter.process(jackSample(t, open)).repCount
            }
        }
        feed(false, 5)
        feed(true, 5)
        feed(false, 5)
        feed(true, 5)
        feed(false, 5)
        feed(true, 5)
        feed(false, 5)
        assertThat(reps).isEqualTo(3)
    }

    @Test
    fun `arms only without leg separation does not count`() {
        val counter = RepCounter.forType(MissionType.JUMPING_JACKS, Sensitivity.NORMAL)
        var t = 0L
        var reps = 0
        // Start neutral.
        repeat(5) {
            t += 100
            reps = counter.process(jackSample(t, open = false)).repCount
        }
        // Raise arms but keep feet together (open sample with closed ankles).
        repeat(5) {
            t += 100
            val sample = PoseSample(
                t,
                jackSample(t, open = true).points.toMutableMap().apply {
                    put(BodyPoint.LEFT_ANKLE, PosePoint(0.46f, 0.9f, 0.9f))
                    put(BodyPoint.RIGHT_ANKLE, PosePoint(0.54f, 0.9f, 0.9f))
                },
            )
            reps = counter.process(sample).repCount
        }
        repeat(5) {
            t += 100
            reps = counter.process(jackSample(t, open = false)).repCount
        }
        assertThat(reps).isEqualTo(0)
    }

    @Test
    fun `unknown mission type is rejected`() {
        try {
            RepCounter.forType(MissionType.MATH, Sensitivity.NORMAL)
            assert(false) { "expected IllegalArgumentException" }
        } catch (expected: IllegalArgumentException) {
        }
    }
}
