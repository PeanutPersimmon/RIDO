package io.github.peanutpersimmon.rido.presentation.voice

import io.github.peanutpersimmon.rido.domain.voice.*
import io.github.peanutpersimmon.rido.domain.model.TaggedEvent
import io.github.peanutpersimmon.rido.domain.model.SessionKind
import io.github.peanutpersimmon.rido.domain.model.SessionTemporalType
import io.github.peanutpersimmon.rido.domain.time.epochDayMinuteToMillis
import io.github.peanutpersimmon.rido.domain.service.ResolvedClassification
import io.github.peanutpersimmon.rido.presentation.editor.task.TaskEditorUiState
import io.github.peanutpersimmon.rido.presentation.editor.event.EventEditorUiState
import io.github.peanutpersimmon.rido.presentation.editor.session.SessionEditorUiState
import io.github.peanutpersimmon.rido.presentation.editor.toLocalDate
import io.github.peanutpersimmon.rido.presentation.editor.toLocalTime
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** A one-shot editor prefill, not a persisted command or a database entity. */
data class VoiceEditorSeed(
    val draft: VoiceActionDraft,
    val classification: ResolvedClassification? = null,
    val event: TaggedEvent? = null,
    val linkedTaskId: VoiceFieldPatch<Long> = VoiceFieldPatch.Keep,
)

fun VoiceEditorSeed.apply(state: TaskEditorUiState): TaskEditorUiState {
    val f = draft.fields
    val estimate = f.estimatedDuration.applyTo((state.estimatedHours.toIntOrNull() ?: 0) * 60 + (state.estimatedMinutes.toIntOrNull() ?: 0))
    return state.copy(title = f.title.applyTo(state.title).orEmpty(),
        note = f.note.applyTo(state.note).orEmpty(),
        location = f.location.applyTo(state.location).orEmpty(),
        plannedDate = f.date.applyTo(state.plannedDate), plannedStart = f.start.applyTo(state.plannedStart),
        plannedReminderOffsetMinutes = if (f.start.applyTo(state.plannedStart) != null) f.reminderOffsetMinutes ?: state.plannedReminderOffsetMinutes else null,
        dueDate = f.dueDate.applyTo(state.dueDate), dueTime = f.dueTime.applyTo(state.dueTime),
        dueReminderOffsetMinutes = if (f.start.applyTo(state.plannedStart) == null && f.dueTime.applyTo(state.dueTime) != null)
            f.reminderOffsetMinutes ?: state.dueReminderOffsetMinutes else state.dueReminderOffsetMinutes,
        estimatedHours = estimate?.div(60)?.takeIf { it > 0 }?.toString().orEmpty(),
        estimatedMinutes = estimate?.rem(60)?.takeIf { it > 0 }?.toString().orEmpty(),
        categoryId = classification?.categoryId ?: state.categoryId, tagIds = classification?.tagIds ?: state.tagIds)
}

fun VoiceEditorSeed.apply(state: EventEditorUiState): EventEditorUiState {
    val f = draft.fields
    val date = f.date.applyTo(state.date) ?: state.date
    val endDate = if (f.endDate == VoiceFieldPatch.Keep && state.endDate != null && f.date is VoiceFieldPatch.Set)
        state.endDate.plusDays(date.toEpochDay() - state.date.toEpochDay()) else f.endDate.applyTo(state.endDate)
    return state.copy(title = f.title.applyTo(state.title).orEmpty(), date = date,
        startTime = f.start.applyTo(state.startTime), endTime = f.end.applyTo(state.endTime),
        startReminderOffsetMinutes = f.reminderOffsetMinutes ?: state.startReminderOffsetMinutes,
        endDate = endDate, location = f.location.applyTo(state.location).orEmpty(),
        note = f.note.applyTo(state.note).orEmpty(),
        categoryId = classification?.categoryId ?: state.categoryId, tagIds = classification?.tagIds ?: state.tagIds)
}

fun VoiceEditorSeed.apply(state: SessionEditorUiState, zone: ZoneId): SessionEditorUiState {
    val f = draft.fields
    val e = event?.event
    fun minute(value: Int?) = value?.let { LocalTime.MIN.plusMinutes(it.toLong()) }
    val plannedStart = e?.startMinuteOfDay?.let { epochDayMinuteToMillis(e.dateEpochDay, it, zone) }
    val plannedEnd = e?.endMinuteOfDay?.let {
        epochDayMinuteToMillis(e.endDateEpochDay ?: e.dateEpochDay, it, zone)
    }
    val usableEnd = plannedEnd?.takeIf { plannedStart != null && it > plannedStart && it <= draft.nowMillis }
    val copied = if (e == null) state else state.copy(title = e.title, note = e.note.orEmpty(), location = e.location.orEmpty(),
        categoryId = e.categoryId, tagIds = requireNotNull(event).tagIds, sourceEventId = e.id, sourceEventTitle = e.title,
        startDate = LocalDate.ofEpochDay(e.dateEpochDay), startTime = minute(e.startMinuteOfDay) ?: state.startTime,
        endDate = usableEnd?.toLocalDate(zone), endTime = usableEnd?.toLocalTime(zone))
    val startDate = f.startAt?.toLocalDate(zone) ?: f.date.applyTo(copied.startDate) ?: copied.startDate
    val startTime = f.startAt?.toLocalTime(zone) ?: f.start.applyTo(copied.startTime) ?: copied.startTime
    val endDate = f.endAt?.toLocalDate(zone) ?: f.endDate.applyTo(copied.endDate)
        ?: startDate.takeIf { f.end is VoiceFieldPatch.Set }
    val proposedEndTime = f.endAt?.toLocalTime(zone) ?: f.end.applyTo(copied.endTime)
    val pointRecord = draft.recordTemporalType == SessionTemporalType.POINT
    val finalEndDate = endDate.takeUnless { pointRecord }
    val finalEndTime = proposedEndTime.takeUnless { pointRecord }
    val temporalType = when {
        copied.kind != SessionKind.ACTIVITY || copied.wasActiveAtLoad -> SessionTemporalType.INTERVAL
        draft.recordTemporalType == SessionTemporalType.INTERVAL -> SessionTemporalType.INTERVAL
        e?.endMinuteOfDay != null && !pointRecord -> SessionTemporalType.INTERVAL
        finalEndDate != null && finalEndTime != null -> SessionTemporalType.INTERVAL
        else -> SessionTemporalType.POINT
    }
    val hasExplicitStart = f.startAt != null || f.start is VoiceFieldPatch.Set
    return copied.copy(title = f.title.applyTo(copied.title).orEmpty(),
        startDate = startDate, startTime = startTime,
        startTimeConfirmed = if (e != null) plannedStart?.let { it <= draft.nowMillis } == true || hasExplicitStart
            else copied.startTimeConfirmed,
        endDate = finalEndDate,
        endTime = finalEndTime,
        temporalType = temporalType,
        requiresCompletedIntervalEnd = draft.action == VoiceAction.CreateRecordFromEvent &&
            temporalType == SessionTemporalType.INTERVAL && finalEndDate == null,
        requiredIntervalEndLimitMillis = draft.nowMillis.takeIf {
            draft.action == VoiceAction.CreateRecordFromEvent && temporalType == SessionTemporalType.INTERVAL && finalEndDate == null
        },
        requiredIntervalZoneId = zone.takeIf {
            draft.action == VoiceAction.CreateRecordFromEvent && temporalType == SessionTemporalType.INTERVAL && finalEndDate == null
        },
        location = f.location.applyTo(copied.location).orEmpty(), note = f.note.applyTo(copied.note).orEmpty(),
        taskId = linkedTaskId.applyTo(copied.taskId),
        categoryId = classification?.categoryId ?: copied.categoryId, tagIds = classification?.tagIds ?: copied.tagIds)
}
