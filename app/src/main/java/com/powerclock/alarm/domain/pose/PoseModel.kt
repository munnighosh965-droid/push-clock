package com.powerclock.alarm.domain.pose

import kotlin.math.acos
import kotlin.math.hypot
import kotlin.math.sqrt

/** The subset of body landmarks Power Clock uses for rep counting. */
enum class BodyPoint {
    NOSE,
    LEFT_SHOULDER, RIGHT_SHOULDER,
    LEFT_ELBOW, RIGHT_ELBOW,
    LEFT_WRIST, RIGHT_WRIST,
    LEFT_HIP, RIGHT_HIP,
    LEFT_KNEE, RIGHT_KNEE,
    LEFT_ANKLE, RIGHT_ANKLE,
}

/**
 * A landmark in normalized image coordinates (0..1, y grows downward)
 * with a 0..1 visibility/confidence value.
 */
data class PosePoint(val x: Float, val y: Float, val visibility: Float)

/** One pose observation at a moment in time. */
data class PoseSample(
    val timestampMs: Long,
    val points: Map<BodyPoint, PosePoint>,
) {
    operator fun get(p: BodyPoint): PosePoint? = points[p]

    fun avgVisibility(vararg needed: BodyPoint): Float {
        if (needed.isEmpty()) return 0f
        var sum = 0f
        for (p in needed) sum += points[p]?.visibility ?: 0f
        return sum / needed.size
    }
}

object PoseGeometry {

    /** Inner angle in degrees at vertex [b] formed by segments b->a and b->c. */
    fun angleDegrees(a: PosePoint, b: PosePoint, c: PosePoint): Float {
        val abx = a.x - b.x
        val aby = a.y - b.y
        val cbx = c.x - b.x
        val cby = c.y - b.y
        val dot = abx * cbx + aby * cby
        val magAb = sqrt(abx * abx + aby * aby)
        val magCb = sqrt(cbx * cbx + cby * cby)
        if (magAb < 1e-6f || magCb < 1e-6f) return 180f
        val cos = (dot / (magAb * magCb)).coerceIn(-1f, 1f)
        return Math.toDegrees(acos(cos.toDouble())).toFloat()
    }

    fun distance(a: PosePoint, b: PosePoint): Float = hypot(a.x - b.x, a.y - b.y)
}
