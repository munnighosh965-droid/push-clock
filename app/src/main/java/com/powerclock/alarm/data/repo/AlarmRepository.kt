package com.powerclock.alarm.data.repo

import com.powerclock.alarm.data.db.AlarmDao
import com.powerclock.alarm.data.db.AlarmEntity
import com.powerclock.alarm.domain.model.Alarm
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlarmRepository @Inject constructor(
    private val dao: AlarmDao,
) {
    fun observeAll(): Flow<List<Alarm>> =
        dao.observeAll().map { list -> list.map(AlarmEntity::toDomain) }

    suspend fun getAll(): List<Alarm> = dao.getAll().map(AlarmEntity::toDomain)

    suspend fun getEnabled(): List<Alarm> = dao.getEnabled().map(AlarmEntity::toDomain)

    suspend fun getById(id: Long): Alarm? = dao.getById(id)?.toDomain()

    suspend fun upsert(alarm: Alarm): Long {
        val stamped = if (alarm.createdAtMs == 0L) {
            alarm.copy(createdAtMs = System.currentTimeMillis())
        } else {
            alarm
        }
        return if (stamped.id == 0L) {
            dao.insert(AlarmEntity.fromDomain(stamped))
        } else {
            dao.update(AlarmEntity.fromDomain(stamped))
            stamped.id
        }
    }

    suspend fun duplicate(alarm: Alarm): Long =
        dao.insert(AlarmEntity.fromDomain(alarm.copy(id = 0L, createdAtMs = System.currentTimeMillis())))

    suspend fun setEnabled(id: Long, enabled: Boolean): Alarm? {
        val current = dao.getById(id) ?: return null
        val updated = current.copy(enabled = enabled)
        dao.update(updated)
        return updated.toDomain()
    }

    suspend fun delete(id: Long) = dao.deleteById(id)

    suspend fun deleteAll() = dao.deleteAll()
}
