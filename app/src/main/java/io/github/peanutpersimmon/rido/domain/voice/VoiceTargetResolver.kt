package io.github.peanutpersimmon.rido.domain.voice

import io.github.peanutpersimmon.rido.domain.model.TaskStatus
import io.github.peanutpersimmon.rido.domain.repository.RidoCoreRepository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.first

data class VoiceTargetCandidate(val id: Long, val type: VoiceType, val title: String, val label: String)

/** No ranking, confidence, or history-based inference: all substring matches remain visible. */
object VoiceTargetMatching {
    fun matches(query: String, title: String): Boolean = query.trim().isNotEmpty() &&
        title.contains(query.trim(), ignoreCase = true)
    fun unique(candidates: List<VoiceTargetCandidate>): VoiceTargetCandidate? = candidates.singleOrNull()
}

class VoiceTargetResolver(private val repository: RidoCoreRepository, private val zone: ZoneId = ZoneId.systemDefault()) {
    suspend fun targets(draft: VoiceActionDraft): List<VoiceTargetCandidate> {
        val query = draft.targetText
        return when (draft.action) {
            VoiceAction.CreateTask, VoiceAction.UpdateTask, VoiceAction.CompleteTask, VoiceAction.UncompleteTask ->
                repository.getAllTasks().filter { VoiceTargetMatching.matches(query, it.title) }
                    .filter { when (draft.action) {
                        VoiceAction.CompleteTask -> it.status != TaskStatus.DONE && it.status != TaskStatus.CANCELLED
                        VoiceAction.UncompleteTask -> it.status == TaskStatus.DONE
                        else -> true
                    } }.map { VoiceTargetCandidate(it.id, VoiceType.Task, it.title,
                        "${it.title} · ${it.dueDateEpochDay?.let(LocalDate::ofEpochDay) ?: "无截止日期"} · #${it.id}") }
            VoiceAction.UpdateEvent, VoiceAction.CreateRecordFromEvent -> {
                val copy = draft.action == VoiceAction.CreateRecordFromEvent
                val first = if (copy && draft.targetDate != null) draft.targetDate.toEpochDay()
                    else LocalDate.of(1900, 1, 1).toEpochDay()
                val last = if (copy && draft.targetDate != null) draft.targetDate.toEpochDay()
                    else LocalDate.of(2200, 1, 1).toEpochDay()
                repository.getEvents(first, last, Long.MIN_VALUE, Long.MAX_VALUE)
                    .filter { VoiceTargetMatching.matches(query, it.event.title) }
                    .filter { draft.targetDate == null || it.event.dateEpochDay == draft.targetDate.toEpochDay() }
                    .filter { draft.targetStart == null || it.event.startMinuteOfDay == draft.targetStart.toSecondOfDay() / 60 }
                    .map { val e = it.event
                        VoiceTargetCandidate(e.id, VoiceType.Event, e.title,
                            "${LocalDate.ofEpochDay(e.dateEpochDay)} ${e.startMinuteOfDay?.let { m -> "%02d:%02d".format(m / 60, m % 60) }.orEmpty()} · ${e.title} · #${e.id}") }
            }
            VoiceAction.UpdateRecord -> repository.getSessions(Long.MIN_VALUE, Long.MAX_VALUE)
                .filter { VoiceTargetMatching.matches(query, it.session.title) }
                .filter { draft.targetStartAt == null || it.session.startAtMillis == draft.targetStartAt }
                .map { val s = it.session; VoiceTargetCandidate(s.id, VoiceType.Record, s.title,
                    "${Instant.ofEpochMilli(s.startAtMillis).atZone(zone).toLocalDateTime()} · ${s.title} · #${s.id}") }
            VoiceAction.StopRecord, VoiceAction.StopRecordAndCompleteTask -> listOfNotNull(repository.observeActiveSession().first())
                .filter { query.isBlank() || VoiceTargetMatching.matches(query, it.session.title) }
                .map { VoiceTargetCandidate(it.session.id, VoiceType.Record, it.session.title, "进行中 · ${it.session.title}") }
            else -> emptyList()
        }
    }

    suspend fun linkedTasks(query: String, allowDone: Boolean = false): List<VoiceTargetCandidate> = repository.getAllTasks()
        .filter { (allowDone || it.status != TaskStatus.DONE) && it.status != TaskStatus.CANCELLED &&
            VoiceTargetMatching.matches(query, it.title) }
        .map { VoiceTargetCandidate(it.id, VoiceType.Task, it.title, "${it.title} · #${it.id}") }
}
