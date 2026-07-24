package com.powerclock.alarm.domain.pose

import com.powerclock.alarm.domain.model.MissionType
import com.powerclock.alarm.domain.model.Sensitivity

/** Coaching hints surfaced to the user while counting. */
enum class PoseHint {
    NONE,
    NOT_VISIBLE,      // landmark confidence too low -> reposition phone
    GET_IN_POSITION,  // waiting for the exercise start position
    GO_LOWER,         // partial rep detected
    FULL_RETURN,      // must fully return to the start position
}

/** State pushed to the UI after each processed frame. */
data class RepUpdate(
    val repCount: Int,
    val justCounted: Boolean,
    val paused: Boolean,
    val inStartPosition: Boolean,
    val hint: PoseHint,
    /** 0..1 progress through the current movement, for the UI ring. */
    val phaseProgress: Float = 0f,
)

/**
 * Base class for exercise-specific repetition state machines.
 *
 * Counting is deliberately conservative:
 *  - a rep needs a full cycle: start position -> work phase -> full return;
 *  - each phase must be held for [minPhaseMs] before it registers
 *    (hysteresis + timing gate against jitter double counts);
 *  - consecutive reps are separated by at least [minRepIntervalMs];
 *  - when landmark confidence drops below [visibilityThreshold] the machine
 *    pauses and preserves its state instead of guessing.
 */
abstract class RepCounter(protected val sensitivity: Sensitivity) {

    protected val visibilityThreshold: Float = when (sensitivity) {
        Sensitivity.BEGINNER -> 0.35f
        Sensitivity.NORMAL -> 0.5f
        Sensitivity.STRICT -> 0.6f
    }
    protected val minPhaseMs: Long = when (sensitivity) {
        Sensitivity.BEGINNER -> 150L
        Sensitivity.NORMAL -> 250L
        Sensitivity.STRICT -> 350L
    }
    protected val minRepIntervalMs: Long = 700L

    var repCount: Int = 0
        protected set

    protected enum class Phase { WAITING_FOR_START, AT_START, IN_WORK_PHASE }

    protected var phase = Phase.WAITING_FOR_START
    protected var phaseEnteredAt = 0L
    protected var lastRepAt = 0L
    private var pausedNow = false

    /** Landmarks this exercise needs to see. */
    protected abstract val requiredPoints: Array<BodyPoint>

    /** True when the body is in the exercise start position. */
    protected abstract fun isAtStart(sample: PoseSample): Boolean

    /** True when the body is in the "work" position (bottom of push-up, etc.). */
    protected abstract fun isInWorkPosition(sample: PoseSample): Boolean

    /** Extra validation applied when a rep is about to be counted. */
    protected open fun validateRep(sample: PoseSample): Boolean = true

    /** 0..1 how deep into the movement the current sample is. */
    protected open fun progress(sample: PoseSample): Float =
        if (phase == Phase.IN_WORK_PHASE) 1f else 0f

    open fun reset() {
        repCount = 0
        phase = Phase.WAITING_FOR_START
        phaseEnteredAt = 0L
        lastRepAt = 0L
        pausedNow = false
    }

    fun process(sample: PoseSample): RepUpdate {
        val visible = sample.avgVisibility(*requiredPoints) >= visibilityThreshold
        if (!visible) {
            pausedNow = true
            return RepUpdate(
                repCount = repCount,
                justCounted = false,
                paused = true,
                inStartPosition = phase != Phase.WAITING_FOR_START,
                hint = PoseHint.NOT_VISIBLE,
            )
        }
        pausedNow = false

        val now = sample.timestampMs
        var counted = false
        var hint = PoseHint.NONE

        when (phase) {
            Phase.WAITING_FOR_START -> {
                if (isAtStart(sample)) {
                    enterPhase(Phase.AT_START, now)
                } else {
                    hint = PoseHint.GET_IN_POSITION
                }
            }

            Phase.AT_START -> {
                if (!isAtStart(sample) && isInWorkPosition(sample)) {
                    if (now - phaseEnteredAt >= minPhaseMs) {
                        enterPhase(Phase.IN_WORK_PHASE, now)
                    }
                } else if (!isAtStart(sample)) {
                    // Between positions; partial movement in progress.
                    hint = PoseHint.GO_LOWER
                }
            }

            Phase.IN_WORK_PHASE -> {
                if (isAtStart(sample)) {
                    val heldLongEnough = now - phaseEnteredAt >= minPhaseMs
                    val spacedOut = now - lastRepAt >= minRepIntervalMs
                    if (heldLongEnough && spacedOut && validateRep(sample)) {
                        repCount++
                        counted = true
                        lastRepAt = now
                    }
                    enterPhase(Phase.AT_START, now)
                } else if (!isInWorkPosition(sample)) {
                    hint = PoseHint.FULL_RETURN
                }
            }
        }

        return RepUpdate(
            repCount = repCount,
            justCounted = counted,
            paused = false,
            inStartPosition = phase != Phase.WAITING_FOR_START,
            hint = hint,
            phaseProgress = progress(sample),
        )
    }

    private fun enterPhase(newPhase: Phase, now: Long) {
        phase = newPhase
        phaseEnteredAt = now
    }

    companion object {
        fun forType(type: MissionType, sensitivity: Sensitivity): RepCounter = when (type) {
            MissionType.PUSH_UPS -> PushUpCounter(sensitivity, kneeVariant = false)
            MissionType.KNEE_PUSH_UPS -> PushUpCounter(sensitivity, kneeVariant = true)
            MissionType.SQUATS -> SquatCounter(sensitivity)
            MissionType.JUMPING_JACKS -> JumpingJackCounter(sensitivity)
            else -> throw IllegalArgumentException("$type is not a camera workout")
        }
    }
}
