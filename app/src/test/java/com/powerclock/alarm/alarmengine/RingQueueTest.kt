package com.powerclock.alarm.alarmengine

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RingQueueTest {

    @Test
    fun `first alarm starts immediately`() {
        val q = RingQueue()
        assertThat(q.requestRing(1L)).isTrue()
        assertThat(q.activeId).isEqualTo(1L)
    }

    @Test
    fun `duplicate delivery of the active alarm is ignored`() {
        val q = RingQueue()
        q.requestRing(1L)
        assertThat(q.requestRing(1L)).isFalse()
        assertThat(q.queuedCount).isEqualTo(0)
    }

    @Test
    fun `overlapping alarm queues instead of double ringing`() {
        val q = RingQueue()
        q.requestRing(1L)
        assertThat(q.requestRing(2L)).isFalse()
        assertThat(q.queuedCount).isEqualTo(1)
    }

    @Test
    fun `duplicate queued alarm is not queued twice`() {
        val q = RingQueue()
        q.requestRing(1L)
        q.requestRing(2L)
        assertThat(q.requestRing(2L)).isFalse()
        assertThat(q.queuedCount).isEqualTo(1)
    }

    @Test
    fun `finishing active promotes next in FIFO order`() {
        val q = RingQueue()
        q.requestRing(1L)
        q.requestRing(2L)
        q.requestRing(3L)
        assertThat(q.finishActive()).isEqualTo(2L)
        assertThat(q.activeId).isEqualTo(2L)
        assertThat(q.finishActive()).isEqualTo(3L)
        assertThat(q.finishActive()).isNull()
        assertThat(q.activeId).isNull()
    }

    @Test
    fun `queued alarm can be removed when deleted`() {
        val q = RingQueue()
        q.requestRing(1L)
        q.requestRing(2L)
        q.remove(2L)
        assertThat(q.finishActive()).isNull()
    }
}
