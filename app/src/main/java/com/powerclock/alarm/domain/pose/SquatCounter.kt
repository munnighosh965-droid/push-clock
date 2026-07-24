package com.powerclock.alarm.domain.pose

import com.powerclock.alarm.domain.model.Sensitivity

/**
 * Squat counter based on knee flexion with a calibrated hip-drop check.
 *
 * Standing height is calibrated from the first confident standing frames,
 * so the counter adapts to any body size and camera distance instead of
 * assuming fixed pixel measurements.
 */
class SquatCounter(sensitivity: Sensitivity) : RepCounter(sensitivity) {

    private val downKneeAngle = when (sensitivity) {
        Sensitivity.BEGINNER -> 130f
        Sensitivity.NORMAL -> 110f
        Sensitivity.STRICT -> 95f
    }
    private val upKneeAngle = when (sensitivity) {
        Sensitivity.BEGINNER -> 155f
        Sensitivity.NORMAL -> 162f
        Sensitivity.STRICT -> 168f
    }

    /** Required hip descent as a fraction of standing hip-to-ankle height. */
    private val minHipDropRatio = when (sensitivity) {
        Sensitivity.BEGINNER -> 0.05f
        Sensitivity.NORMAL -> 0.12f
        Sensitivity.STRICT -> 0.2f
    }

    private var standingHipY: Float? = null
    private var standingLegLength: Float? = null

    override val requiredPoints = arrayOf(
        BodyPoint.LEFT_HIP, BodyPoint.RIGHT_HIP,
        BodyPoint.LEFT_KNEE, BodyPoint.RIGHT_KNEE,
        BodyPoint.LEFT_ANKLE, BodyPoint.RIGHT_ANKLE,
    )

    override fun reset() {
        super.reset()
        standingHipY = null
        standingLegLength = null
    }

    private fun kneeAngle(sample: PoseSample): Float? {
        val left = angle(sample, BodyPoint.LEFT_HIP, BodyPoint.LEFT_KNEE, BodyPoint.LEFT_ANKLE)
        val right = angle(sample, BodyPoint.RIGHT_HIP, BodyPoint.RIGHT_KNEE, BodyPoint.RIGHT_ANKLE)
        return when {
            left != null && right != null -> (left + right) / 2f
            else -> left ?: right
        }
    }

    private fun angle(sample: PoseSample, a: BodyPoint, b: BodyPoint, c: BodyPoint): Float? {
        val pa = sample[a] ?: return null
        val pb = sample[b] ?: return null
        val pc = sample[c] ?: return null
        if (minOf(pa.visibility, pb.visibility, pc.visibility) < visibilityThreshold) return null
        return PoseGeometry.angleDegrees(pa, pb, pc)
    }

    private fun hipY(sample: PoseSample): Float? {
        val l = sample[BodyPoint.LEFT_HIP] ?: return null
        val r = sample[BodyPoint.RIGHT_HIP] ?: return null
        return (l.y + r.y) / 2f
    }

    private fun ankleY(sample: PoseSample): Float? {
        val l = sample[BodyPoint.LEFT_ANKLE] ?: return null
        val r = sample[BodyPoint.RIGHT_ANKLE] ?: return null
        return (l.y + r.y) / 2f
    }

    override fun isAtStart(sample: PoseSample): Boolean {
        val angle = kneeAngle(sample) ?: return false
        val standing = angle >= upKneeAngle
        if (standing) {
            // Tolerant re-calibration: keep the highest observed standing hip.
            val hip = hipY(sample)
            val ankle = ankleY(sample)
            if (hip != null && ankle != null && ankle > hip) {
                val current = standingHipY
                if (current == null || hip < current) {
                    standingHipY = hip
                    standingLegLength = ankle - hip
                }
            }
        }
        return standing
    }

    override fun isInWorkPosition(sample: PoseSample): Boolean {
        val angle = kneeAngle(sample) ?: return false
        if (angle > downKneeAngle) return false
        // Hip-drop confirmation when calibration data exists.
        val baseHip = standingHipY ?: return true
        val leg = standingLegLength ?: return true
        val hip = hipY(sample) ?: return true
        return hip - baseHip >= leg * minHipDropRatio
    }

    override fun progress(sample: PoseSample): Float {
        val angle = kneeAngle(sample) ?: return 0f
        return ((upKneeAngle - angle) / (upKneeAngle - downKneeAngle)).coerceIn(0f, 1f)
    }
}
