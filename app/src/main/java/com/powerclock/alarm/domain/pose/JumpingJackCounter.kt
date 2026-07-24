package com.powerclock.alarm.domain.pose

import com.powerclock.alarm.domain.model.Sensitivity

/**
 * Jumping-jack counter: arms raised above shoulder/head level combined
 * with feet separated wider than a shoulder-width multiple, then a full
 * return to neutral. Shoulder width is measured live, so the check scales
 * with the user's distance from the camera.
 */
class JumpingJackCounter(sensitivity: Sensitivity) : RepCounter(sensitivity) {

    private val openLegRatio = when (sensitivity) {
        Sensitivity.BEGINNER -> 1.25f
        Sensitivity.NORMAL -> 1.45f
        Sensitivity.STRICT -> 1.7f
    }
    private val closedLegRatio = 1.1f

    /** STRICT requires wrists above the nose; others above shoulders. */
    private val armsAboveHead = sensitivity == Sensitivity.STRICT

    override val requiredPoints = arrayOf(
        BodyPoint.LEFT_SHOULDER, BodyPoint.RIGHT_SHOULDER,
        BodyPoint.LEFT_WRIST, BodyPoint.RIGHT_WRIST,
        BodyPoint.LEFT_ANKLE, BodyPoint.RIGHT_ANKLE,
    )

    private fun shoulderWidth(sample: PoseSample): Float? {
        val l = sample[BodyPoint.LEFT_SHOULDER] ?: return null
        val r = sample[BodyPoint.RIGHT_SHOULDER] ?: return null
        val w = PoseGeometry.distance(l, r)
        return if (w < 1e-4f) null else w
    }

    private fun ankleSpreadRatio(sample: PoseSample): Float? {
        val width = shoulderWidth(sample) ?: return null
        val l = sample[BodyPoint.LEFT_ANKLE] ?: return null
        val r = sample[BodyPoint.RIGHT_ANKLE] ?: return null
        return PoseGeometry.distance(l, r) / width
    }

    private fun armsUp(sample: PoseSample): Boolean {
        val lw = sample[BodyPoint.LEFT_WRIST] ?: return false
        val rw = sample[BodyPoint.RIGHT_WRIST] ?: return false
        val reference = if (armsAboveHead) {
            sample[BodyPoint.NOSE]?.y ?: return false
        } else {
            val ls = sample[BodyPoint.LEFT_SHOULDER] ?: return false
            val rs = sample[BodyPoint.RIGHT_SHOULDER] ?: return false
            minOf(ls.y, rs.y)
        }
        // y grows downward: "above" means smaller y.
        return lw.y < reference && rw.y < reference
    }

    private fun armsDown(sample: PoseSample): Boolean {
        val lw = sample[BodyPoint.LEFT_WRIST] ?: return false
        val rw = sample[BodyPoint.RIGHT_WRIST] ?: return false
        val ls = sample[BodyPoint.LEFT_SHOULDER] ?: return false
        val rs = sample[BodyPoint.RIGHT_SHOULDER] ?: return false
        return lw.y > ls.y && rw.y > rs.y
    }

    /** Neutral stance: arms down, feet together. */
    override fun isAtStart(sample: PoseSample): Boolean {
        val spread = ankleSpreadRatio(sample) ?: return false
        return armsDown(sample) && spread <= closedLegRatio
    }

    /** Open "star" position: arms raised, feet apart. */
    override fun isInWorkPosition(sample: PoseSample): Boolean {
        val spread = ankleSpreadRatio(sample) ?: return false
        return armsUp(sample) && spread >= openLegRatio
    }

    override fun progress(sample: PoseSample): Float {
        val spread = ankleSpreadRatio(sample) ?: return 0f
        return ((spread - closedLegRatio) / (openLegRatio - closedLegRatio)).coerceIn(0f, 1f)
    }
}
