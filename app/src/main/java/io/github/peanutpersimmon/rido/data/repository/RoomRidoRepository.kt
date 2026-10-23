package io.github.peanutpersimmon.rido.data.repository

import io.github.peanutpersimmon.rido.backup.withBackupGateTransaction as withTransaction
import io.github.peanutpersimmon.rido.data.local.room.RidoDatabase
import io.github.peanutpersimmon.rido.data.local.room.entity.ProcessedCommandEntity
import io.github.peanutpersimmon.rido.data.mapper.toDomain
import io.github.peanutpersimmon.rido.data.mapper.toEntity
import io.github.peanutpersimmon.rido.domain.model.Event
import io.github.peanutpersimmon.rido.domain.model.Category
import io.github.peanutpersimmon.rido.domain.model.DomainError
import io.github.peanutpersimmon.rido.domain.model.DomainResult
import io.github.peanutpersimmon.rido.domain.model.KeywordRule
import io.github.peanutpersimmon.rido.domain.model.Session
import io.github.peanutpersimmon.rido.domain.model.Tag
import io.github.peanutpersimmon.rido.domain.model.Task
import io.github.peanutpersimmon.rido.domain.model.TaskCompletionSource
import io.github.peanutpersimmon.rido.domain.model.TaskStatus
import io.github.peanutpersimmon.rido.domain.model.TaggedEvent
import io.github.peanutpersimmon.rido.domain.model.TaggedSession
import io.github.peanutpersimmon.rido.domain.model.TaggedTask
import io.github.peanutpersimmon.rido.domain.repository.RidoCoreRepository
import io.github.peanutpersimmon.rido.domain.repository.DeletedEventSnapshot
import io.github.peanutpersimmon.rido.domain.repository.DeletedSessionSnapshot
import io.github.peanutpersimmon.rido.domain.service.TaskHierarchyPolicy
import io.github.peanutpersimmon.rido.domain.validation.DataValidator
import kotlinx.coroutines.flow.Flow
import java.time.ZoneId

class DataIntegrityException(message: String) : IllegalStateException(message)
private class CompoundCompletionRejected(val failure: DomainResult.Failure) : IllegalStateException()

sealed interface CommandExecution<out T> {
    data class Executed<T>(val value: T) : CommandExecution<T>
    data object AlreadyProcessed : CommandExecution<Nothing>
}

/**
 * Transaction boundary for writes that combine DAO operations and domain invariants.
 * UI/ViewModel code must use use cases backed by this boundary instead of composing DAOs.
 */
class RoomRidoRepository(
    private val database: RidoDatabase,
) : RidoCoreRepository {
    private val taskDao = database.taskDao()
    private val sessionDao = database.sessionDao()
    private val tagDao = database.tagDao()
    private val processedCommandDao = database.processedCommandDao()
    private val sessionOperations = RoomSessionOperations(database)
    private val eventOperations = RoomEventOperations(database)
    private val projectionQueries = RoomProjectionQueries(database)
    private val editorOperations = RoomEditorOperations(database)

    override suspend fun <T> voiceTransaction(block: suspend () -> T): T = database.withTransaction { block() }

    override fun observeDataChanges(): Flow<Unit> = projectionQueries.observeDataChanges()

    override fun observeActiveSession(): Flow<TaggedSession?> =
        projectionQueries.observeActiveSession()

    suspend fun saveTask(task: Task, tagIds: Set<Long> = emptySet()): Long = database.withTransaction {
        val parentLinks = taskDao.getParentLinks().associate { it.id to it.parentTaskId }
        DataValidator.requireValid(DataValidator.validateTask(task, parentLinks))
        val taskId = if (task.id == 0L) {
            taskDao.insert(task.toEntity())
        } else {
            taskDao.update(task.toEntity())
            task.id
        }
        taskDao.replaceTags(taskId, tagIds)
        taskId
    }

    suspend fun saveEvent(event: Event, tagIds: Set<Long> = emptySet()): Long =
        eventOperations.save(event, tagIds)

    suspend fun saveSession(session: Session, tagIds: Set<Long> = emptySet()): Long =
        sessionOperations.save(session, tagIds)

    /** Ends the existing active Session at the new Session start and inserts the new active Session atomically. */
    override suspend fun startSession(
        session: Session,
        tagIds: Set<Long>,
    ): DomainResult<Long> = sessionOperations.start(session, tagIds)

    override suspend fun startSessionIfIdle(session: Session, tagIds: Set<Long>): DomainResult<Long> =
        database.withTransaction {
            if (sessionDao.getActiveSessions().isNotEmpty())
                DomainResult.Failure(DomainError.DataIntegrity("已有进行中的记录，请先结束它"))
            else sessionOperations.start(session, tagIds)
        }

    override suspend fun stopConfirmedSession(id: Long, taskId: Long?, end: Long, completeTask: Boolean): DomainResult<Session> =
        database.withTransaction {
            val active = sessionDao.getActiveSessions().singleOrNull()
            if (active == null || active.id != id || active.taskId != taskId)
                DomainResult.Failure(DomainError.DataIntegrity("进行中的记录已改变，请重新输入"))
            else if (completeTask) stopSessionAndCompleteTask(end) else stopActiveSession(end)
        }

    /** Returns zero or one active Session; multiple rows are treated as database corruption. */
    suspend fun getActiveSession(): Session? = database.withTransaction {
        val activeSessions = sessionDao.getActiveSessions()
        if (activeSessions.size > 1) {
            throw DataIntegrityException("Database contains more than one active Session.")
        }
        activeSessions.singleOrNull()?.toDomain()
    }

    override suspend fun stopActiveSession(endAtMillis: Long): DomainResult<Session> =
        sessionOperations.stop(endAtMillis)

    override suspend fun finishSleepCheckIn(
        endAtMillis: Long,
        zoneId: ZoneId,
    ): DomainResult<Session> = sessionOperations.finishSleepCheckIn(endAtMillis, zoneId)

    override suspend fun stopSessionAndCompleteTask(endAtMillis: Long): DomainResult<Session> =
        database.withTransaction {
            val active = sessionDao.getActiveSessions().singleOrNull()
            if (active?.taskId != null && taskDao.getById(active.taskId) == null) {
                return@withTransaction DomainResult.Failure(DomainError.NotFound("Task", active.taskId))
            }
            when (val stopped = sessionOperations.stop(endAtMillis)) {
                is DomainResult.Failure -> stopped
                is DomainResult.Success -> {
                    val taskId = stopped.value.taskId
                    if (taskId != null) {
                        when (val completed = completeTaskInTransaction(taskId, endAtMillis)) {
                            is DomainResult.Failure -> return@withTransaction completed
                            is DomainResult.Success -> Unit
                        }
                    }
                    stopped
                }
            }
        }

    override suspend fun createPastSession(
        session: Session,
        tagIds: Set<Long>,
    ): DomainResult<Long> = sessionOperations.createPast(session, tagIds)

    override suspend fun createPastSessionAndCompleteTask(
        session: Session,
        tagIds: Set<Long>,
    ): DomainResult<Long> = try {
        database.withTransaction {
            val taskId = session.taskId
                ?: return@withTransaction DomainResult.Failure(DomainError.DataIntegrity("请先关联任务"))
            if (session.kind != io.github.peanutpersimmon.rido.domain.model.SessionKind.ACTIVITY ||
                session.temporalType == io.github.peanutpersimmon.rido.domain.model.SessionTemporalType.INTERVAL && session.endAtMillis == null ||
                session.temporalType == io.github.peanutpersimmon.rido.domain.model.SessionTemporalType.POINT && session.endAtMillis != null
            ) return@withTransaction DomainResult.Failure(DomainError.InvalidTime)
            val task = taskDao.getById(taskId)?.toDomain()
                ?: return@withTransaction DomainResult.Failure(DomainError.NotFound("Task", taskId))
            if (task.status == TaskStatus.DONE || task.status == TaskStatus.CANCELLED)
                return@withTransaction DomainResult.Failure(DomainError.DataIntegrity("任务已完成或已取消，请先重新打开"))
            val completedAt = if (session.temporalType == io.github.peanutpersimmon.rido.domain.model.SessionTemporalType.POINT)
                session.startAtMillis else requireNotNull(session.endAtMillis)
            when (val saved = sessionOperations.createPast(session, tagIds)) {
                is DomainResult.Failure -> saved
                is DomainResult.Success -> {
                    when (val completed = completeTaskInTransaction(taskId, completedAt)) {
                        is DomainResult.Failure -> throw CompoundCompletionRejected(completed)
                        is DomainResult.Success -> saved
                    }
                }
            }
        }
    } catch (rejected: CompoundCompletionRejected) {
        rejected.failure
    }

    override suspend fun updateSession(
        session: Session,
        tagIds: Set<Long>,
    ): DomainResult<Unit> = sessionOperations.update(session, tagIds)

    override suspend fun deleteSession(sessionId: Long): DomainResult<Unit> =
        sessionOperations.delete(sessionId)

    override suspend fun completeTask(
        taskId: Long,
        completedAtMillis: Long,
    ): DomainResult<Unit> = database.withTransaction {
        completeTaskInTransaction(taskId, completedAtMillis)
    }

    override suspend fun reopenTask(
        taskId: Long,
        updatedAtMillis: Long,
    ): DomainResult<Unit> = database.withTransaction {
        val task = taskDao.getById(taskId)?.toDomain()
            ?: return@withTransaction DomainResult.Failure(DomainError.NotFound("Task", taskId))
        taskDao.update(
            task.copy(
                status = TaskStatus.IN_PROGRESS,
                completedAtMillis = null,
                cancelledAtMillis = null,
                completionSource = null,
                updatedAtMillis = updatedAtMillis,
            ).toEntity(),
        )
        reconcileAncestors(task.parentTaskId, updatedAtMillis)
        DomainResult.Success(Unit)
    }

    suspend fun getScheduledEvents(firstEpochDay: Long, lastEpochDay: Long): List<Event> =
        database.eventDao().getScheduledInRange(firstEpochDay, lastEpochDay).map { it.toDomain() }

    override suspend fun getNightSleeps(): List<Session> =
        projectionQueries.getNightSleeps()

    override suspend fun getSessions(
        rangeStartMillis: Long,
        rangeEndMillis: Long,
    ): List<TaggedSession> = projectionQueries.getSessions(rangeStartMillis, rangeEndMillis)

    override suspend fun getCompletedActivityIntervalsInRange(
        rangeStartMillis: Long,
        rangeEndMillis: Long,
    ): List<TaggedSession> = projectionQueries.getCompletedActivityIntervalsInRange(rangeStartMillis, rangeEndMillis)

    override suspend fun getActivityFactsInRange(
        rangeStartMillis: Long,
        rangeEndMillis: Long,
    ): List<TaggedSession> = projectionQueries.getActivityFactsInRange(rangeStartMillis, rangeEndMillis)

    override suspend fun getNapsInRange(
        rangeStartMillis: Long,
        rangeEndMillis: Long,
    ): List<TaggedSession> = projectionQueries.getNapsInRange(rangeStartMillis, rangeEndMillis)

    override suspend fun getEvents(
        firstEpochDay: Long,
        lastEpochDay: Long,
        rangeStartMillis: Long,
        rangeEndMillis: Long,
    ): List<TaggedEvent> = projectionQueries.getEvents(
        firstEpochDay,
        lastEpochDay,
        rangeStartMillis,
        rangeEndMillis,
    )

    override suspend fun getTasks(
        firstEpochDay: Long,
        lastEpochDay: Long,
        rangeStartMillis: Long,
        rangeEndMillis: Long,
    ): List<TaggedTask> = projectionQueries.getTasks(
        firstEpochDay,
        lastEpochDay,
        rangeStartMillis,
        rangeEndMillis,
    )

    override suspend fun getEnabledKeywordRules(): List<KeywordRule> =
        projectionQueries.getEnabledKeywordRules()

    override suspend fun getCategories(): List<Category> =
        projectionQueries.getCategories()

    override suspend fun getTags(): List<Tag> =
        projectionQueries.getTags()

    override suspend fun getTask(taskId: Long): TaggedTask? = editorOperations.getTask(taskId)
    override suspend fun getEvent(eventId: Long): TaggedEvent? = editorOperations.getEvent(eventId)
    override suspend fun getEventsByIds(eventIds: Set<Long>): List<Event> =
        projectionQueries.getEventsByIds(eventIds)
    override suspend fun getSession(sessionId: Long): TaggedSession? = editorOperations.getSession(sessionId)
    override suspend fun getAllTasks(): List<Task> = editorOperations.getAllTasks()
    override suspend fun upsertTask(task: Task, tagIds: Set<Long>): DomainResult<Long> =
        editorOperations.upsertTask(task, tagIds)
    override suspend fun upsertEvent(event: Event, tagIds: Set<Long>): DomainResult<Long> =
        editorOperations.upsertEvent(event, tagIds)
    override suspend fun deleteTask(taskId: Long): DomainResult<Unit> = editorOperations.deleteTask(taskId)
    override suspend fun deleteEvent(eventId: Long): DomainResult<DeletedEventSnapshot> =
        editorOperations.deleteEvent(eventId)
    override suspend fun restoreEvent(snapshot: DeletedEventSnapshot): DomainResult<Unit> =
        editorOperations.restoreEvent(snapshot)
    override suspend fun deleteSessionWithSnapshot(sessionId: Long): DomainResult<DeletedSessionSnapshot> =
        editorOperations.deleteSession(sessionId)
    override suspend fun restoreSession(snapshot: DeletedSessionSnapshot): DomainResult<Unit> =
        editorOperations.restoreSession(snapshot)

    suspend fun saveTag(tag: Tag): Long = database.withTransaction {
        DataValidator.requireValid(DataValidator.validateDisplayName(tag.name))
        if (tag.id == 0L) {
            tagDao.insert(tag.toEntity())
        } else {
            tagDao.update(tag.toEntity())
            tag.id
        }
    }

    /** Business writes and the idempotency marker commit or roll back together. */
    suspend fun <T> executeCommandOnce(
        commandId: String,
        processedAtMillis: Long,
        action: suspend RoomRidoRepository.() -> T,
    ): CommandExecution<T> = database.withTransaction {
        DataValidator.requireValid(DataValidator.validateCommandId(commandId))
        val normalizedId = commandId.trim()
        if (processedCommandDao.exists(normalizedId)) {
            CommandExecution.AlreadyProcessed
        } else {
            val value = action()
            processedCommandDao.insert(ProcessedCommandEntity(normalizedId, processedAtMillis))
            CommandExecution.Executed(value)
        }
    }

    private suspend fun reconcileAncestors(startParentId: Long?, nowMillis: Long) {
        var currentParentId = startParentId
        val visited = mutableSetOf<Long>()
        while (currentParentId != null) {
            if (!visited.add(currentParentId)) {
                throw DataIntegrityException("Task parent cycle detected while reconciling ancestors.")
            }
            val parent = taskDao.getById(currentParentId)?.toDomain()
                ?: throw DataIntegrityException("Missing parent Task $currentParentId.")
            val children = taskDao.getChildren(currentParentId).map { it.toDomain() }
            val reconciled = TaskHierarchyPolicy.reconcileParent(parent, children, nowMillis)
            if (reconciled != parent) taskDao.update(reconciled.toEntity())
            currentParentId = parent.parentTaskId
        }
    }

    private suspend fun completeTaskInTransaction(
        taskId: Long,
        completedAtMillis: Long,
    ): DomainResult<Unit> {
        val task = taskDao.getById(taskId)?.toDomain()
            ?: return DomainResult.Failure(DomainError.NotFound("Task", taskId))
        taskDao.update(
            task.copy(
                status = TaskStatus.DONE,
                completedAtMillis = completedAtMillis,
                cancelledAtMillis = null,
                completionSource = TaskCompletionSource.MANUAL,
                updatedAtMillis = completedAtMillis,
            ).toEntity(),
        )
        reconcileAncestors(task.parentTaskId, completedAtMillis)
        return DomainResult.Success(Unit)
    }

}

