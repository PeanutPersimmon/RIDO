package io.github.peanutpersimmon.rido.domain.repository

import io.github.peanutpersimmon.rido.domain.model.Category
import io.github.peanutpersimmon.rido.domain.model.DomainResult
import io.github.peanutpersimmon.rido.domain.model.Event
import io.github.peanutpersimmon.rido.domain.model.KeywordRule
import io.github.peanutpersimmon.rido.domain.model.Session
import io.github.peanutpersimmon.rido.domain.model.TaggedEvent
import io.github.peanutpersimmon.rido.domain.model.TaggedSession
import io.github.peanutpersimmon.rido.domain.model.TaggedTask
import io.github.peanutpersimmon.rido.domain.model.Task
import io.github.peanutpersimmon.rido.domain.model.Tag
import kotlinx.coroutines.flow.Flow
import java.time.ZoneId

interface RidoCoreRepository {
    suspend fun <T> voiceTransaction(block: suspend () -> T): T = block()
    fun observeDataChanges(): Flow<Unit>
    fun observeActiveSession(): Flow<TaggedSession?>

    suspend fun startSession(session: Session, tagIds: Set<Long>): DomainResult<Long>
    // Conservative default for alternate repositories; Room implements the check atomically.
    suspend fun startSessionIfIdle(session: Session, tagIds: Set<Long>): DomainResult<Long> =
        DomainResult.Failure(io.github.peanutpersimmon.rido.domain.model.DomainError.DataIntegrity("无法安全开始记录"))
    suspend fun stopConfirmedSession(id: Long, taskId: Long?, end: Long, completeTask: Boolean): DomainResult<Session> =
        DomainResult.Failure(io.github.peanutpersimmon.rido.domain.model.DomainError.DataIntegrity("无法安全结束记录"))
    suspend fun stopActiveSession(endAtMillis: Long): DomainResult<Session>
    suspend fun finishSleepCheckIn(endAtMillis: Long, zoneId: ZoneId): DomainResult<Session> =
        DomainResult.Failure(
            io.github.peanutpersimmon.rido.domain.model.DomainError.DataIntegrity("无法安全结束睡眠记录"),
        )
    suspend fun stopSessionAndCompleteTask(endAtMillis: Long): DomainResult<Session>
    suspend fun createPastSession(session: Session, tagIds: Set<Long>): DomainResult<Long>
    suspend fun createPastSessionAndCompleteTask(session: Session, tagIds: Set<Long>): DomainResult<Long> =
        DomainResult.Failure(io.github.peanutpersimmon.rido.domain.model.DomainError.DataIntegrity("无法安全保存并完成任务"))
    suspend fun updateSession(session: Session, tagIds: Set<Long>): DomainResult<Unit>
    suspend fun deleteSession(sessionId: Long): DomainResult<Unit>
    suspend fun completeTask(taskId: Long, completedAtMillis: Long): DomainResult<Unit>
    suspend fun reopenTask(taskId: Long, updatedAtMillis: Long): DomainResult<Unit>
    suspend fun getNightSleeps(): List<Session>
    suspend fun getSessions(rangeStartMillis: Long, rangeEndMillis: Long): List<TaggedSession>
    suspend fun getCompletedActivityIntervalsInRange(rangeStartMillis: Long, rangeEndMillis: Long): List<TaggedSession> =
        error("Completed Activity Interval query is not implemented")
    suspend fun getActivityFactsInRange(rangeStartMillis: Long, rangeEndMillis: Long): List<TaggedSession> =
        error("Activity Fact query is not implemented")
    suspend fun getNapsInRange(rangeStartMillis: Long, rangeEndMillis: Long): List<TaggedSession> =
        error("Nap query is not implemented")
    suspend fun getEvents(
        firstEpochDay: Long,
        lastEpochDay: Long,
        rangeStartMillis: Long,
        rangeEndMillis: Long,
    ): List<TaggedEvent>
    suspend fun getTasks(
        firstEpochDay: Long,
        lastEpochDay: Long,
        rangeStartMillis: Long,
        rangeEndMillis: Long,
    ): List<TaggedTask>
    suspend fun getEnabledKeywordRules(): List<KeywordRule>
    suspend fun getCategories(): List<Category>
    suspend fun getTags(): List<Tag>
    suspend fun getTask(taskId: Long): TaggedTask?
    suspend fun getEvent(eventId: Long): TaggedEvent?
    suspend fun getEventsByIds(eventIds: Set<Long>): List<Event>
    suspend fun getSession(sessionId: Long): TaggedSession?
    suspend fun getAllTasks(): List<Task>
    suspend fun upsertTask(task: Task, tagIds: Set<Long>): DomainResult<Long>
    suspend fun upsertEvent(event: Event, tagIds: Set<Long>): DomainResult<Long>
    suspend fun deleteTask(taskId: Long): DomainResult<Unit>
    suspend fun deleteEvent(eventId: Long): DomainResult<DeletedEventSnapshot>
    suspend fun restoreEvent(snapshot: DeletedEventSnapshot): DomainResult<Unit>
    suspend fun deleteSessionWithSnapshot(sessionId: Long): DomainResult<DeletedSessionSnapshot>
    suspend fun restoreSession(snapshot: DeletedSessionSnapshot): DomainResult<Unit>
}

data class DeletedEventSnapshot(
    val record: TaggedEvent,
    val linkedSessionIds: List<Long> = emptyList(),
)
data class DeletedSessionSnapshot(val record: TaggedSession)
