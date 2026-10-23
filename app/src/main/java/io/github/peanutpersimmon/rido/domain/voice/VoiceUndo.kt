package io.github.peanutpersimmon.rido.domain.voice

import io.github.peanutpersimmon.rido.domain.model.*
import io.github.peanutpersimmon.rido.domain.repository.RidoCoreRepository

/** Memory-only, short-lived Snackbar undo; no command history is persisted. */
data class VoiceDataSnapshot(val tasks: List<TaggedTask> = emptyList(), val events: List<TaggedEvent> = emptyList(), val records: List<TaggedSession> = emptyList())
data class VoiceUndo(val before: VoiceDataSnapshot, val after: VoiceDataSnapshot)
data class VoiceExecutionReceipt(val message: String, val undo: VoiceUndo)
internal class VoiceWriteRejected(val error: DomainError) : RuntimeException()
internal fun <T> DomainResult<T>.requireVoiceSuccess(): T = when (this) {
    is DomainResult.Success -> value
    is DomainResult.Failure -> throw VoiceWriteRejected(error)
}

class UndoVoiceActionUseCase(private val repository: RidoCoreRepository) {
    suspend operator fun invoke(undo: VoiceUndo): DomainResult<Unit> = try {
        repository.voiceTransaction {
            fun stale(): Nothing = throw VoiceWriteRejected(DomainError.DataIntegrity("内容已有后续变化，无法撤销"))
            undo.after.tasks.forEach { if (repository.getTask(it.task.id) != it) stale() }
            undo.after.events.forEach { if (repository.getEvent(it.event.id) != it) stale() }
            undo.after.records.forEach { if (repository.getSession(it.session.id) != it) stale() }
            val newTasks = undo.after.tasks.filter { after -> undo.before.tasks.none { it.task.id == after.task.id } }
            val newEvents = undo.after.events.filter { after -> undo.before.events.none { it.event.id == after.event.id } }
            if (newTasks.isNotEmpty() || newEvents.isNotEmpty()) {
                val tasks = repository.getAllTasks()
                val sessions = repository.getSessions(Long.MIN_VALUE, Long.MAX_VALUE)
                if (newTasks.any { created -> tasks.any { it.parentTaskId == created.task.id } || sessions.any { it.session.taskId == created.task.id } }) stale()
                if (newEvents.any { created -> sessions.any { it.session.sourceEventId == created.event.id } }) stale()
            }
            newTasks.forEach { repository.deleteTask(it.task.id).requireVoiceSuccess() }
            newEvents.forEach { repository.deleteEvent(it.event.id).requireVoiceSuccess() }
            undo.after.records.filter { after -> undo.before.records.none { it.session.id == after.session.id } }
                .forEach { repository.deleteSession(it.session.id).requireVoiceSuccess() }
            undo.before.tasks.forEach { repository.upsertTask(it.task, it.tagIds).requireVoiceSuccess() }
            undo.before.events.forEach { repository.upsertEvent(it.event, it.tagIds).requireVoiceSuccess() }
            undo.before.records.forEach { repository.updateSession(it.session, it.tagIds).requireVoiceSuccess() }
            DomainResult.Success(Unit)
        }
    } catch (e: VoiceWriteRejected) { DomainResult.Failure(e.error) }
}
