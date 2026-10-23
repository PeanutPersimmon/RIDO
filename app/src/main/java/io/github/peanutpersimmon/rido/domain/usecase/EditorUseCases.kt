package io.github.peanutpersimmon.rido.domain.usecase

import io.github.peanutpersimmon.rido.domain.model.Category
import io.github.peanutpersimmon.rido.domain.model.DomainResult
import io.github.peanutpersimmon.rido.domain.model.Event
import io.github.peanutpersimmon.rido.domain.model.Session
import io.github.peanutpersimmon.rido.domain.model.Tag
import io.github.peanutpersimmon.rido.domain.model.TaggedEvent
import io.github.peanutpersimmon.rido.domain.model.TaggedSession
import io.github.peanutpersimmon.rido.domain.model.TaggedTask
import io.github.peanutpersimmon.rido.domain.model.Task
import io.github.peanutpersimmon.rido.domain.repository.DeletedEventSnapshot
import io.github.peanutpersimmon.rido.domain.repository.DeletedSessionSnapshot
import io.github.peanutpersimmon.rido.domain.repository.RidoCoreRepository

data class EditorOptions(
    val categories: List<Category>,
    val tags: List<Tag>,
    val tasks: List<Task>,
)

class GetEditorOptionsUseCase(private val repository: RidoCoreRepository) {
    suspend operator fun invoke(): EditorOptions = EditorOptions(
        categories = repository.getCategories(),
        tags = repository.getTags(),
        tasks = repository.getAllTasks(),
    )
}

class GetTaskForEditorUseCase(private val repository: RidoCoreRepository) {
    suspend operator fun invoke(id: Long): TaggedTask? = repository.getTask(id)
}
class SaveTaskUseCase(private val repository: RidoCoreRepository) {
    suspend operator fun invoke(task: Task, tagIds: Set<Long>) = repository.upsertTask(task, tagIds)
}
class DeleteTaskUseCase(private val repository: RidoCoreRepository) {
    suspend operator fun invoke(id: Long) = repository.deleteTask(id)
}

class GetEventForEditorUseCase(private val repository: RidoCoreRepository) {
    suspend operator fun invoke(id: Long): TaggedEvent? = repository.getEvent(id)
}
class SaveEventUseCase(private val repository: RidoCoreRepository) {
    suspend operator fun invoke(event: Event, tagIds: Set<Long>) = repository.upsertEvent(event, tagIds)
}
class DeleteEventUseCase(private val repository: RidoCoreRepository) {
    suspend operator fun invoke(id: Long) = repository.deleteEvent(id)
}
class RestoreEventUseCase(private val repository: RidoCoreRepository) {
    suspend operator fun invoke(snapshot: DeletedEventSnapshot) = repository.restoreEvent(snapshot)
}

class GetSessionForEditorUseCase(private val repository: RidoCoreRepository) {
    suspend operator fun invoke(id: Long): TaggedSession? = repository.getSession(id)
}
class GetPreviousActualEndUseCase(private val repository: RidoCoreRepository) {
    suspend operator fun invoke(dayStartMillis: Long, nowMillis: Long): Long? =
        (repository.getCompletedActivityIntervalsInRange(dayStartMillis, nowMillis) +
            repository.getNapsInRange(dayStartMillis, nowMillis))
            .map { it.session }.let { latestPreviousActualEnd(it, dayStartMillis, nowMillis) }
}

internal fun latestPreviousActualEnd(
    candidates: Iterable<Session>, dayStartMillis: Long, nowMillis: Long,
): Long? = candidates.asSequence()
    .filter { it.kind in setOf(io.github.peanutpersimmon.rido.domain.model.SessionKind.ACTIVITY,
        io.github.peanutpersimmon.rido.domain.model.SessionKind.NAP) &&
        it.temporalType == io.github.peanutpersimmon.rido.domain.model.SessionTemporalType.INTERVAL }
    .mapNotNull { it.endAtMillis }
    .filter { it >= dayStartMillis && it < nowMillis }
    .maxOrNull()
class SaveSessionEditUseCase(private val repository: RidoCoreRepository) {
    suspend operator fun invoke(session: Session, tagIds: Set<Long>): DomainResult<Unit> =
        repository.updateSession(session, tagIds)
}
class DeleteSessionWithUndoUseCase(private val repository: RidoCoreRepository) {
    suspend operator fun invoke(id: Long) = repository.deleteSessionWithSnapshot(id)
}
class RestoreSessionUseCase(private val repository: RidoCoreRepository) {
    suspend operator fun invoke(snapshot: DeletedSessionSnapshot) = repository.restoreSession(snapshot)
}
