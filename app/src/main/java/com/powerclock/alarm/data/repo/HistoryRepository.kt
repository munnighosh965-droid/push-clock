package com.powerclock.alarm.data.repo

import com.powerclock.alarm.data.db.WakeEventDao
import com.powerclock.alarm.data.db.WakeEventEntity
import com.powerclock.alarm.domain.model.WakeEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HistoryRepository @Inject constructor(
    private val dao: WakeEventDao,
) {
    fun observeAll(): Flow<List<WakeEvent>> =
        dao.observeAll().map { list -> list.map(WakeEventEntity::toDomain) }

    suspend fun getAll(): List<WakeEvent> = dao.getAll().map(WakeEventEntity::toDomain)

    suspend fun getSince(sinceMs: Long): List<WakeEvent> =
        dao.getSince(sinceMs).map(WakeEventEntity::toDomain)

    suspend fun insert(event: WakeEvent): Long = dao.insert(WakeEventEntity.fromDomain(event))

    suspend fun update(event: WakeEvent) = dao.update(WakeEventEntity.fromDomain(event))

    suspend fun getById(id: Long): WakeEvent? = dao.getById(id)?.toDomain()

    suspend fun deleteAll() = dao.deleteAll()

    /** Plain CSV export; written through the system file picker only. */
    suspend fun exportCsv(): String {
        val sb = StringBuilder()
        sb.appendLine("id,alarm_label,scheduled_at,rang_at,mission_started_at,dismissed_at,outcome,total_reps,mission_summary,energy_rating")
        for (e in getAll()) {
            sb.appendLine(
                listOf(
                    e.id,
                    csv(e.alarmLabel),
                    e.scheduledAtMs,
                    e.rangAtMs,
                    e.missionStartedAtMs ?: "",
                    e.dismissedAtMs ?: "",
                    e.outcome.name,
                    e.totalReps,
                    csv(e.missionSummary),
                    e.energyRating ?: "",
                ).joinToString(","),
            )
        }
        return sb.toString()
    }

    private fun csv(value: String): String = "\"" + value.replace("\"", "\"\"") + "\""
}
