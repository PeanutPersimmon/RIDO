package io.github.peanutpersimmon.rido.data.repository

import io.github.peanutpersimmon.rido.backup.withBackupGateTransaction as withTransaction
import io.github.peanutpersimmon.rido.data.local.room.RidoDatabase
import io.github.peanutpersimmon.rido.data.mapper.toDomain
import io.github.peanutpersimmon.rido.data.mapper.toEntity
import io.github.peanutpersimmon.rido.data.mapper.toTaggedDomain
import io.github.peanutpersimmon.rido.domain.model.DomainError
import io.github.peanutpersimmon.rido.domain.model.DomainResult
import io.github.peanutpersimmon.rido.domain.model.Event
import io.github.peanutpersimmon.rido.domain.model.Task
import io.github.peanutpersimmon.rido.domain.repository.DeletedEventSnapshot
import io.github.peanutpersimmon.rido.domain.repository.DeletedSessionSnapshot
import io.github.peanutpersimmon.rido.domain.validation.DataValidationException
import io.github.peanutpersimmon.rido.domain.validation.DataValidator

internal class RoomEditorOperations(private val database: RidoDatabase) {
    private val taskDao = database.taskDao()
    private val eventDao = database.eventDao()
    private val sessionDao = database.sessionDao()
    private val eventOperations = RoomEventOperations(database)
    private val sessionOperations = RoomSessionOperations(database)

    suspend fun getTask(id: Long) = taskDao.getWithTags(id)?.toTaggedDomain()
    suspend fun getEvent(id: Long) = eventDao.getWithTags(id)?.toTaggedDomain()
    suspend fun getSession(id: Long) = sessionDao.getWithTags(id)?.toTaggedDomain()
    suspend fun getAllTasks(): List<Task> = taskDao.getAll().map { it.toDomain() }

    suspend fun upsertTask(task: Task, tagIds: Set<Long>): DomainResult<Long> = database.withTransaction {
        try {
            val links = taskDao.getParentLinks().associate { it.id to it.parentTaskId }
            DataValidator.requireValid(DataValidator.validateTask(task, links))
            val id = if (task.id == 0L) taskDao.insert(task.toEntity()) else {
                if (taskDao.getById(task.id) == null) {
                    return@withTransaction DomainResult.Failure(DomainError.NotFound("Task", task.id))
                }
                taskDao.update(task.toEntity())
                task.id
            }
            taskDao.replaceTags(id, tagIds)
            DomainResult.Success(id)
        } catch (error: DataValidationException) {
            DomainResult.Failure(error.toDomainError())
        }
    }

    suspend fun upsertEvent(event: Event, tagIds: Set<Long>): DomainResult<Long> = try {
        DomainResult.Success(eventOperations.save(event, tagIds))
    } catch (error: DataValidationException) {
        DomainResult.Failure(error.toDomainError())
    }

    suspend fun deleteTask(taskId: Long): DomainResult<Unit> = database.withTransaction {
        if (taskDao.deleteById(taskId) == 0) DomainResult.Failure(DomainError.NotFound("Task", taskId))
        else DomainResult.Success(Unit)
    }

    suspend fun deleteEvent(eventId: Long): DomainResult<DeletedEventSnapshot> = database.withTransaction {
        val record = eventDao.getWithTags(eventId)?.toTaggedDomain()
            ?: return@withTransaction DomainResult.Failure(DomainError.NotFound("Event", eventId))
        val linkedSessionIds = sessionDao.getIdsForSourceEvent(eventId)
        eventDao.deleteById(eventId)
        DomainResult.Success(DeletedEventSnapshot(record, linkedSessionIds))
    }

    suspend fun restoreEvent(snapshot: DeletedEventSnapshot): DomainResult<Unit> = try {
        eventOperations.restore(
            snapshot.record.event,
            snapshot.record.tagIds,
            snapshot.linkedSessionIds,
        )
        DomainResult.Success(Unit)
    } catch (error: Exception) {
        DomainResult.Failure(DomainError.DataIntegrity(error.message ?: "Event restore failed"))
    }

    suspend fun deleteSession(sessionId: Long): DomainResult<DeletedSessionSnapshot> = database.withTransaction {
        val record = sessionDao.getWithTags(sessionId)?.toTaggedDomain()
            ?: return@withTransaction DomainResult.Failure(DomainError.NotFound("Session", sessionId))
        sessionDao.deleteById(sessionId)
        DomainResult.Success(DeletedSessionSnapshot(record))
    }

    suspend fun restoreSession(snapshot: DeletedSessionSnapshot): DomainResult<Unit> = try {
        sessionOperations.restore(snapshot.record.session, snapshot.record.tagIds)
        DomainResult.Success(Unit)
    } catch (error: Exception) {
        DomainResult.Failure(DomainError.DataIntegrity(error.message ?: "Session restore failed"))
    }

    private fun DataValidationException.toDomainError(): DomainError = when {
        errors.any { it.name.contains("TITLE") } -> DomainError.EmptyTitle
        errors.any { it.name.contains("PARENT_CYCLE") } -> DomainError.ParentCycle
        else -> DomainError.InvalidTime
    }
}
