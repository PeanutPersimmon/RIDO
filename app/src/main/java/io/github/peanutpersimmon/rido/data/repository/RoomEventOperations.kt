package io.github.peanutpersimmon.rido.data.repository

import io.github.peanutpersimmon.rido.backup.withBackupGateTransaction as withTransaction
import io.github.peanutpersimmon.rido.data.local.room.RidoDatabase
import io.github.peanutpersimmon.rido.data.mapper.toEntity
import io.github.peanutpersimmon.rido.domain.model.Event
import io.github.peanutpersimmon.rido.domain.validation.DataValidator

internal class RoomEventOperations(private val database: RidoDatabase) {
    private val eventDao = database.eventDao()
    private val sessionDao = database.sessionDao()

    suspend fun save(event: Event, tagIds: Set<Long>): Long = database.withTransaction {
        DataValidator.requireValid(DataValidator.validateEvent(event))
        val id = if (event.id == 0L) eventDao.insert(event.toEntity()) else {
            eventDao.update(event.toEntity())
            event.id
        }
        eventDao.replaceTags(id, tagIds)
        id
    }

    suspend fun restore(
        event: Event,
        tagIds: Set<Long>,
        linkedSessionIds: List<Long>,
    ) = database.withTransaction {
        DataValidator.requireValid(DataValidator.validateEvent(event))
        if (event.id == 0L || eventDao.getById(event.id) != null) {
            throw DataIntegrityException("Event cannot be restored with this id.")
        }
        eventDao.insert(event.toEntity())
        eventDao.replaceTags(event.id, tagIds)
        if (linkedSessionIds.isNotEmpty()) {
            sessionDao.restoreSourceEventIfNull(linkedSessionIds, event.id)
        }
    }

}
