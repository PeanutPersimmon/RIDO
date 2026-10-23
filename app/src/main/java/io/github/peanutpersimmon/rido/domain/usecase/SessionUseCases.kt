package io.github.peanutpersimmon.rido.domain.usecase

import io.github.peanutpersimmon.rido.domain.model.DomainResult
import io.github.peanutpersimmon.rido.domain.model.DomainError
import io.github.peanutpersimmon.rido.domain.model.Session
import io.github.peanutpersimmon.rido.domain.model.SessionKind
import io.github.peanutpersimmon.rido.domain.model.SessionTemporalType
import io.github.peanutpersimmon.rido.domain.model.TaggedSession
import io.github.peanutpersimmon.rido.domain.repository.RidoCoreRepository
import kotlinx.coroutines.flow.Flow

data class NewSessionInput(
    val title: String,
    val startAtMillis: Long,
    val endAtMillis: Long? = null,
    val taskId: Long? = null,
    val categoryId: Long? = null,
    val tagIds: Set<Long> = emptySet(),
    val note: String? = null,
    val location: String? = null,
    val kind: SessionKind = SessionKind.ACTIVITY,
    val createdAtMillis: Long = startAtMillis,
    val sourceEventId: Long? = null,
    val temporalType: SessionTemporalType = SessionTemporalType.INTERVAL,
)

private fun NewSessionInput.toSession(): Session = Session(
    title = title,
    note = note,
    location = location,
    kind = kind,
    temporalType = temporalType,
    startAtMillis = startAtMillis,
    endAtMillis = endAtMillis,
    taskId = taskId,
    sourceEventId = sourceEventId,
    categoryId = categoryId,
    createdAtMillis = createdAtMillis,
    updatedAtMillis = createdAtMillis,
)

class StartSessionUseCase(private val repository: RidoCoreRepository) {
    suspend operator fun invoke(input: NewSessionInput, requireIdle: Boolean = false): DomainResult<Long> =
        if (input.temporalType != SessionTemporalType.INTERVAL) DomainResult.Failure(DomainError.InvalidTime)
        else if (requireIdle) repository.startSessionIfIdle(input.copy(endAtMillis = null).toSession(), input.tagIds)
        else repository.startSession(input.copy(endAtMillis = null).toSession(), input.tagIds)
}

class StopActiveSessionUseCase(private val repository: RidoCoreRepository) {
    suspend operator fun invoke(endAtMillis: Long): DomainResult<Session> =
        repository.stopActiveSession(endAtMillis)
}

class StopSessionAndCompleteTaskUseCase(private val repository: RidoCoreRepository) {
    suspend operator fun invoke(endAtMillis: Long): DomainResult<Session> =
        repository.stopSessionAndCompleteTask(endAtMillis)
}

class CreatePastSessionUseCase(private val repository: RidoCoreRepository) {
    suspend operator fun invoke(input: NewSessionInput): DomainResult<Long> =
        repository.createPastSession(input.toSession(), input.tagIds)
}

class CreatePastSessionAndCompleteTaskUseCase(private val repository: RidoCoreRepository) {
    suspend operator fun invoke(input: NewSessionInput): DomainResult<Long> =
        if (input.kind != SessionKind.ACTIVITY || input.taskId == null ||
            input.temporalType == SessionTemporalType.INTERVAL && input.endAtMillis == null ||
            input.temporalType == SessionTemporalType.POINT && input.endAtMillis != null
        ) DomainResult.Failure(DomainError.InvalidTime)
        else repository.createPastSessionAndCompleteTask(input.toSession(), input.tagIds)
}

/** Sleep shares Session persistence while enforcing its product boundary at the write edge. */
class CreateSleepSessionUseCase(private val repository: RidoCoreRepository) {
    suspend fun create(input: NewSessionInput): DomainResult<Long> {
        val sleep = input.asSleep() ?: return unavailable()
        return CreatePastSessionUseCase(repository)(sleep)
    }

    suspend fun start(input: NewSessionInput): DomainResult<Long> {
        val sleep = input.asSleep() ?: return unavailable()
        return StartSessionUseCase(repository)(sleep, requireIdle = true)
    }

    private fun NewSessionInput.asSleep(): NewSessionInput? =
        takeIf { it.kind in setOf(SessionKind.NAP, SessionKind.NIGHT_SLEEP) &&
            it.temporalType == SessionTemporalType.INTERVAL }?.copy(
            temporalType = SessionTemporalType.INTERVAL,
            taskId = null,
            categoryId = null,
            tagIds = emptySet(),
            sourceEventId = null,
        )

    private fun unavailable(): DomainResult<Long> =
        DomainResult.Failure(DomainError.DataIntegrity("当前无法创建睡眠记录"))
}

class UpdateSessionUseCase(private val repository: RidoCoreRepository) {
    suspend operator fun invoke(session: Session, tagIds: Set<Long>): DomainResult<Unit> =
        repository.updateSession(session, tagIds)
}

class DeleteSessionUseCase(private val repository: RidoCoreRepository) {
    suspend operator fun invoke(sessionId: Long): DomainResult<Unit> =
        repository.deleteSession(sessionId)
}

class ObserveActiveSessionUseCase(private val repository: RidoCoreRepository) {
    operator fun invoke(): Flow<TaggedSession?> = repository.observeActiveSession()
}
