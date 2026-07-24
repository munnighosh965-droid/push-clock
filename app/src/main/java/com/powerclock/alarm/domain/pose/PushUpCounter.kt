package com.powerclock.alarm.domain.pose

import com.powerclock.alarm.domain.model.Sensitivity

/**
 * Push-up counter driven by elbow flexion plus a torso-alignment check.
 *
 * The user is expected to be roughly side-on or angled to the camera; we
 * use whichever body side is more visible. Down = elbows bent past the
 * sensitivity threshold, up = arms extended. Alignment (shoulder-hip-knee
 * for the knee variant, shoulder-hip-ankle otherwise) rejects hip-sag or
 * pike "reps" on stricter settings.
 */
class PushUpCounter(
    sensitivity: Sensitivity,
    private val kneeVariant: Boolean,
) : RepCounter(sensitivity) {

    private val downAngle = when (sensitivity) {
        Sensitivity.BEGINNER -> 115f
        Sensitivity.NORMAL -> 100f
        Sensitivity.STRICT -> 90f
    }
    private val upAngle = when (sensitivity) {
        Sensitivity.BEGINNER -> 145f
        Sensitivity.NORMAL -> 155f
        Sensitivity.STRICT -> 160f
    }
    private val minAlignment = when (sensitivity) {
        Sensitivity.BEGINNER -> 0f // beginners: no alignment gate
        Sensitivity.NORMAL -> 130f
        Sensitivity.STRICT -> 150f
    }

    override val requiredPoints = arrayOf(
        BodyPoint.LEFT_SHOULDER, BodyPoint.RIGHT_SHOULDER,
        BodyPoint.LEFT_ELBOW, BodyPoint.RIGHT_ELBOW,
        BodyPoint.LEFT_WRIST, BodyPoint.RIGHT_WRIST,
    )

    private fun elbowAngle(sample: PoseSample): Float? {
        val left = angleOf(
            sample, BodyPoint.LEFT_SHOULDER, BodyPoint.LEFT_ELBOW, BodyPoint.LEFT_WRIST,
        )
        val right = angleOf(
            sample, BodyPoint.RIGHT_SHOULDER, BodyPoint.RIGHT_ELBOW, BodyPoint.RIGHT_WRIST,
        )
        return when {
            left != null && right != null -> minOf(left, right)
            else -> left ?: right
        }
    }

    private fun angleOf(sample: PoseSample, a: BodyPoint, b: BodyPoint, c: BodyPoint): Float? {
        val pa = sample[a] ?: return null
        val pb = sample[b] ?: return null
        val pc = sample[c] ?: return null
        if (pa.visibility < visibilityThreshold ||
            pb.visibility < visibilityThreshold ||
            pc.visibility < visibilityThreshold
        ) return null
        return PoseGeometry.angleDegrees(pa, pb, pc)
    }

    private fun bodyAlignment(sample: PoseSample): Float? {
        val lower = if (kneeVariant) BodyPoint.LEFT_KNEE else BodyPoint.LEFT_ANKLE
        val lowerR = if (kneeVariant) BodyPoint.RIGHT_KNEE else BodyPoint.RIGHT_ANKLE
        val left = angleOf(sample, BodyPoint.LEFT_SHOULDER, BodyPoint.LEFT_HIP, lower)
        val right = angleOf(sample, BodyPoint.RIGHT_SHOULDER, BodyPoint.RIGHT_HIP, lowerR)
        return when {
            left != null && right != null -> (left + right) / 2f
            else -> left ?: right
        }
    }

    override fun isAtStart(sample: PoseSample): Boolean {
        val angle = elbowAngle(sample) ?: return false
        return angle >= upAngle
    }

    override fun isInWorkPosition(sample: PoseSample): Boolean {
        val angle = elbowAngle(sample) ?: return false
        return angle <= downAngle
    }

    override fun validateRep(sample: PoseSample): Boolean {
        if (minAlignment <= 0f) return true
        // Alignment landmarks are often partly occluded in push-up framing;
        // when they are simply not visible we do not punish the user.
        val alignment = bodyAlignment(sample) ?: return true
        return alignment >= minAlignment
    }

    override fun progress(sample: PoseSample): Float {
        val angle = elbowAngle(sample) ?: return 0f
        return ((upAngle - angle) / (upAngle - downAngle)).coerceIn(0f, 1f)
    }
}
