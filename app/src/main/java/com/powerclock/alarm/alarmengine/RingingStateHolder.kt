package com.powerclock.alarm.alarmengine

import com.powerclock.alarm.domain.model.Alarm
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Snapshot of the alarm currently being handled by the ringing service. */
data class RingingSession(
    val alarm: Alarm,
    val wakeEventId: Long,
    val rangAtMs: Long,
    val queuedCount: Int = 0,
)

/**
 * Shared in-memory bridge between [AlarmRingingService] and the ringing UI.
 * A plain singleton StateFlow keeps the two components decoupled without
 * binder plumbing.
 */
@Singleton
class RingingStateHolder @Inject constructor() {
    private val _session = MutableStateFlow<RingingSession?>(null)
    val session: StateFlow<RingingSession?> = _session.asStateFlow()

    fun set(session: RingingSession?) {
        _session.value = session
    }

    fun updateQueuedCount(count: Int) {
        _session.value = _session.value?.copy(queuedCount = count)
    }
}
