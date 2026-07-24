package com.powerclock.alarm.alarmengine

/**
 * Pure overlap queue for simultaneous alarms. Exactly one alarm can be
 * "active" (owning the audio pipeline); the rest wait in FIFO order.
 * Duplicate ids — from a re-delivered PendingIntent or a rapid
 * reschedule — are ignored so the same alarm can never ring twice at once.
 */
class RingQueue {

    var activeId: Long? = null
        private set

    private val pending = ArrayDeque<Long>()

    val queuedCount: Int get() = pending.size

    /** @return true when the caller should start ringing [id] right now. */
    fun requestRing(id: Long): Boolean = when {
        activeId == null -> {
            activeId = id
            true
        }
        activeId == id || pending.contains(id) -> false
        else -> {
            pending.addLast(id)
            false
        }
    }

    /** Ends the active alarm; returns the next queued id (now active) or null. */
    fun finishActive(): Long? {
        activeId = pending.removeFirstOrNull()
        return activeId
    }

    /** Drops a queued (not active) alarm, e.g. deleted while waiting. */
    fun remove(id: Long) {
        pending.remove(id)
    }

    fun clear() {
        activeId = null
        pending.clear()
    }
}
