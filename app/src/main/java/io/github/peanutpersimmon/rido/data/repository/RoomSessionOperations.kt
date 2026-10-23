package io.github.peanutpersimmon.rido.data.repository

import io.github.peanutpersimmon.rido.backup.withBackupGateTransaction as withTransaction
import io.github.peanutpersimmon.rido.data.local.room.RidoDatabase
import io.github.peanutpersimmon.rido.data.mapper.toDomain
import io.github.peanutpersimmon.rido.data.mapper.toEntity
import io.github.peanutpersimmon.rido.domain.model.DomainError
import io.github.peanutpersimmon.rido.domain.model.DomainResult
import io.github.peanutpersimmon.rido.domain.model.Session
import io.github.peanutpersimmon.rido.domain.model.SessionKind
import io.github.peanutpersimmon.rido.domain.model.SessionTemporalType
import io.github.peanutpersimmon.rido.domain.model.TaskStatus
import io.github.peanutpersimmon.rido.domain.service.SleepCheckInPolicy
import io.github.peanutpersimmon.rido.domain.validation.DataValidator
import io.github.peanutpersimmon.rido.domain.validation.ValidationError
import java.time.ZoneId

internal class RoomSessionOperations(
    private val database: RidoDatabase,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    private val sessionDao = database.sessionDao()
    private val taskDao = database.taskDao()

    suspend fun save(session: Session, tagIds: Set<Long>): Long = database.withTransaction {
        validate(session, tagIds)?.let { throw DataIntegrityException(it.toString()) }
        val excludedId = session.id.takeIf { it != 0L } ?: Long.MIN_VALUE
        if (session.temporalType == SessionTemporalType.INTERVAL &&
            sessionDao.countOverlaps(session.startAtMillis, session.endAtMillis, excludedId) > 0) {
            DataValidator.requireValid(setOf(ValidationError.SESSION_OVERLAP))
        }
        if (session.temporalType == SessionTemporalType.INTERVAL && session.endAtMillis == null) {
            val otherActiveCount = sessionDao.getActiveSessions().count { it.id != session.id }
            if (otherActiveCount > 0) {
                throw DataIntegrityException("A second active Session cannot be saved; use startSession().")
            }
        }
        val id = if (session.id == 0L) sessionDao.insert(session.copy(sourceEventId = session.sourceEventId?.takeIf { database.eventDao().getById(it) != null }).toEntity()) else {
            sessionDao.update(session.copy(sourceEventId = if (session.kind == SessionKind.ACTIVITY)
                sessionDao.getById(session.id)?.sourceEventId else null).toEntity())
            session.id
        }
        sessionDao.replaceTags(id, tagIds)
        id
    }

    suspend fun restore(session: Session, tagIds: Set<Long>) = database.withTransaction {
        if (session.id == 0L || sessionDao.getById(session.id) != null) {
            throw DataIntegrityException("Session cannot be restored with this id.")
        }
        validate(session, tagIds)?.let { throw DataIntegrityException(it.toString()) }
        if (session.temporalType == SessionTemporalType.INTERVAL &&
            sessionDao.countOverlaps(session.startAtMillis, session.endAtMillis, session.id) > 0) {
            throw DataIntegrityException("Session restore overlaps another Session.")
        }
        if (session.temporalType == SessionTemporalType.INTERVAL && session.endAtMillis == null && sessionDao.getActiveSessions().isNotEmpty()) {
            throw DataIntegrityException("An active Session already exists.")
        }
        sessionDao.insert(session.copy(sourceEventId = session.sourceEventId?.takeIf { database.eventDao().getById(it) != null }).toEntity())
        sessionDao.replaceTags(session.id, tagIds)
    }

    suspend fun start(session: Session, tagIds: Set<Long>): DomainResult<Long> = database.withTransaction {
        if (session.id != 0L || session.temporalType != SessionTemporalType.INTERVAL || session.endAtMillis != null) {
            return@withTransaction DomainResult.Failure(DomainError.InvalidTime)
        }
        validate(session, tagIds)?.let { return@withTransaction DomainResult.Failure(it) }
        if (session.kind == SessionKind.ACTIVITY && session.taskId != null) {
            val linked = taskDao.getById(session.taskId)
                ?: return@withTransaction DomainResult.Failure(DomainError.NotFound("Task", session.taskId))
            if (linked.status == TaskStatus.DONE || linked.status == TaskStatus.CANCELLED)
                return@withTransaction DomainResult.Failure(DomainError.DataIntegrity("进行中记录不能关联已完成或已取消任务"))
        }
        val activeSessions = sessionDao.getActiveSessions()
        if (activeSessions.size > 1) {
            return@withTransaction DomainResult.Failure(
                DomainError.DataIntegrity("Database contains more than one active Session."),
            )
        }
        val active = activeSessions.singleOrNull()
        if (active != null && session.startAtMillis <= active.startAtMillis) {
            return@withTransaction DomainResult.Failure(DomainError.InvalidTime)
        }
        if (sessionDao.countOverlaps(session.startAtMillis, null, active?.id ?: Long.MIN_VALUE) > 0) {
            return@withTransaction DomainResult.Failure(DomainError.SessionOverlap)
        }
        active?.let { sessionDao.finish(it.id, session.startAtMillis, session.startAtMillis) }
        val id = sessionDao.insert(session.copy(sourceEventId = session.sourceEventId?.takeIf { database.eventDao().getById(it) != null }).toEntity())
        sessionDao.replaceTags(id, tagIds)
        DomainResult.Success(id)
    }

    suspend fun stop(endAtMillis: Long): DomainResult<Session> = database.withTransaction {
        val activeSessions = sessionDao.getActiveSessions()
        if (activeSessions.size > 1) {
            return@withTransaction DomainResult.Failure(
                DomainError.DataIntegrity("Database contains more than one active Session."),
            )
        }
        val active = activeSessions.singleOrNull()?.toDomain()
            ?: return@withTransaction DomainResult.Failure(DomainError.NoActiveSession)
        if (endAtMillis <= active.startAtMillis || endAtMillis > nowMillis()) {
            return@withTransaction DomainResult.Failure(DomainError.InvalidTime)
        }
        val stopped = active.copy(endAtMillis = endAtMillis, updatedAtMillis = endAtMillis)
        sessionDao.finish(active.id, endAtMillis, endAtMillis)
        DomainResult.Success(stopped)
    }

    suspend fun finishSleepCheckIn(
        endAtMillis: Long,
        zoneId: ZoneId,
    ): DomainResult<Session> = database.withTransaction {
        val activeSessions = sessionDao.getActiveSessions()
        if (activeSessions.size > 1) {
            return@withTransaction DomainResult.Failure(
                DomainError.DataIntegrity("Database contains more than one active Session."),
            )
        }
        val active = activeSessions.singleOrNull()?.toDomain()
            ?: return@withTransaction DomainResult.Failure(DomainError.NoActiveSession)
        if (active.kind !in setOf(SessionKind.NIGHT_SLEEP, SessionKind.NAP)) {
            return@withTransaction DomainResult.Failure(
                DomainError.DataIntegrity("当前进行中的记录不是睡眠"),
            )
        }
        if (endAtMillis > nowMillis() || endAtMillis <= active.startAtMillis) {
            return@withTransaction DomainResult.Failure(DomainError.InvalidTime)
        }
        if (SleepCheckInPolicy.isOpenIntervalTooLong(active.startAtMillis, endAtMillis)) {
            return@withTransaction DomainResult.Failure(DomainError.SleepIntervalTooLong)
        }

        val finalKind = SleepCheckInPolicy.inferKind(
            startAtMillis = active.startAtMillis,
            endAtMillis = endAtMillis,
            zoneId = zoneId,
        )
        val finished = active.copy(
            title = SleepCheckInPolicy.titleFor(finalKind),
            kind = finalKind,
            endAtMillis = endAtMillis,
            updatedAtMillis = endAtMillis,
        )
        validate(finished)?.let { return@withTransaction DomainResult.Failure(it) }
        sessionDao.update(finished.toEntity())
        DomainResult.Success(finished)
    }

    suspend fun createPast(session: Session, tagIds: Set<Long>): DomainResult<Long> = database.withTransaction {
        if (session.id != 0L || session.temporalType == SessionTemporalType.INTERVAL && session.endAtMillis == null) {
            return@withTransaction DomainResult.Failure(DomainError.InvalidTime)
        }
        validate(session, tagIds)?.let { return@withTransaction DomainResult.Failure(it) }
        if (session.temporalType == SessionTemporalType.INTERVAL &&
            sessionDao.countOverlaps(session.startAtMillis, session.endAtMillis) > 0) {
            return@withTransaction DomainResult.Failure(DomainError.SessionOverlap)
        }
        val id = sessionDao.insert(session.copy(sourceEventId = session.sourceEventId?.takeIf { database.eventDao().getById(it) != null }).toEntity())
        sessionDao.replaceTags(id, tagIds)
        DomainResult.Success(id)
    }

    suspend fun update(session: Session, tagIds: Set<Long>): DomainResult<Unit> = database.withTransaction {
        if (session.id == 0L || sessionDao.getById(session.id) == null) {
            return@withTransaction DomainResult.Failure(DomainError.NotFound("Session", session.id))
        }
        validate(session, tagIds)?.let { return@withTransaction DomainResult.Failure(it) }
        if (session.temporalType == SessionTemporalType.INTERVAL &&
            sessionDao.countOverlaps(session.startAtMillis, session.endAtMillis, session.id) > 0) {
            return@withTransaction DomainResult.Failure(DomainError.SessionOverlap)
        }
        if (session.temporalType == SessionTemporalType.INTERVAL && session.endAtMillis == null && sessionDao.getActiveSessions().any { it.id != session.id }) {
            return@withTransaction DomainResult.Failure(DomainError.SessionOverlap)
        }
        sessionDao.update(session.copy(sourceEventId = if (session.kind == SessionKind.ACTIVITY)
            sessionDao.getById(session.id)?.sourceEventId else null).toEntity())
        sessionDao.replaceTags(session.id, tagIds)
        DomainResult.Success(Unit)
    }

    suspend fun delete(sessionId: Long): DomainResult<Unit> = database.withTransaction {
        if (sessionDao.deleteById(sessionId) == 0) {
            DomainResult.Failure(DomainError.NotFound("Session", sessionId))
        } else {
            DomainResult.Success(Unit)
        }
    }

    private fun validate(session: Session, tagIds: Set<Long> = emptySet()): DomainError? =
        DataValidator.validateActualSession(session, nowMillis(), tagIds)
}
