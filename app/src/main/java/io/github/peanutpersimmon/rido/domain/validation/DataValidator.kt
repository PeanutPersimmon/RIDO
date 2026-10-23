package io.github.peanutpersimmon.rido.domain.validation

import io.github.peanutpersimmon.rido.domain.model.Event
import io.github.peanutpersimmon.rido.domain.model.KeywordRule
import io.github.peanutpersimmon.rido.domain.model.Session
import io.github.peanutpersimmon.rido.domain.model.SessionKind
import io.github.peanutpersimmon.rido.domain.model.SessionTemporalType
import io.github.peanutpersimmon.rido.domain.model.Task

enum class ValidationError {
    BLANK_TITLE,
    BLANK_NAME,
    INVALID_MINUTE_OF_DAY,
    PLANNED_DATE_REQUIRED,
    DUE_TIME_WITHOUT_DATE,
    REMINDER_WITHOUT_ANCHOR,
    INVALID_REMINDER_OFFSET,
    INVALID_ESTIMATED_DURATION,
    PARENT_SELF_REFERENCE,
    PARENT_CYCLE,
    EVENT_END_WITHOUT_START,
    EVENT_END_DATE_WITHOUT_END_TIME,
    EVENT_END_BEFORE_START,
    SESSION_POINT_WITH_END,
    SESSION_SLEEP_POINT,
    SESSION_SLEEP_METADATA,
    SESSION_END_NOT_AFTER_START,
    SESSION_OVERLAP,
    BLANK_KEYWORD,
    KEYWORD_RULE_WITHOUT_TARGET,
    BLANK_COMMAND_ID,
}

class DataValidationException(
    val errors: Set<ValidationError>,
) : IllegalArgumentException(errors.joinToString(prefix = "Invalid data: "))

object DataValidator {
    val reminderOffsets = setOf(0, 5, 10, 30, 60)
    /** Task location is optional; whitespace has the same meaning as an empty field. */
    fun normalizedTaskLocation(value: String?): String? = value?.trim()?.takeIf(String::isNotEmpty)
    private const val MINUTE_OF_DAY_MIN = 0
    private const val MINUTE_OF_DAY_MAX = 1439

    fun isMinuteOfDayValid(value: Int): Boolean = value in MINUTE_OF_DAY_MIN..MINUTE_OF_DAY_MAX

    fun validateTask(
        task: Task,
        parentByTaskId: Map<Long, Long?> = emptyMap(),
    ): Set<ValidationError> = buildSet {
        if (task.title.trim().isEmpty()) add(ValidationError.BLANK_TITLE)

        listOfNotNull(task.plannedStartMinuteOfDay, task.dueMinuteOfDay)
            .filterNot(::isMinuteOfDayValid)
            .forEach { add(ValidationError.INVALID_MINUTE_OF_DAY) }

        if (task.plannedStartMinuteOfDay != null && task.plannedDateEpochDay == null) {
            add(ValidationError.PLANNED_DATE_REQUIRED)
        }
        if (task.dueDateEpochDay == null && task.dueMinuteOfDay != null) {
            add(ValidationError.DUE_TIME_WITHOUT_DATE)
        }
        if (task.plannedReminderOffsetMinutes != null &&
            (task.plannedDateEpochDay == null || task.plannedStartMinuteOfDay == null)) add(ValidationError.REMINDER_WITHOUT_ANCHOR)
        if (task.dueReminderOffsetMinutes != null &&
            (task.dueDateEpochDay == null || task.dueMinuteOfDay == null)) add(ValidationError.REMINDER_WITHOUT_ANCHOR)
        if (listOfNotNull(task.plannedReminderOffsetMinutes, task.dueReminderOffsetMinutes).any { it !in reminderOffsets })
            add(ValidationError.INVALID_REMINDER_OFFSET)
        if (task.estimatedDurationMinutes != null && task.estimatedDurationMinutes <= 0) {
            add(ValidationError.INVALID_ESTIMATED_DURATION)
        }
        if (task.parentTaskId != null && task.parentTaskId == task.id) {
            add(ValidationError.PARENT_SELF_REFERENCE)
        } else if (hasParentCycle(task.id, task.parentTaskId, parentByTaskId)) {
            add(ValidationError.PARENT_CYCLE)
        }
    }

    fun validateEvent(event: Event): Set<ValidationError> = buildSet {
        if (event.title.trim().isEmpty()) add(ValidationError.BLANK_TITLE)

        listOfNotNull(event.startMinuteOfDay, event.endMinuteOfDay)
            .filterNot(::isMinuteOfDayValid)
            .forEach { add(ValidationError.INVALID_MINUTE_OF_DAY) }

        if (event.startMinuteOfDay == null && event.endMinuteOfDay != null) {
            add(ValidationError.EVENT_END_WITHOUT_START)
        }
        if (event.startReminderOffsetMinutes != null && event.startMinuteOfDay == null)
            add(ValidationError.REMINDER_WITHOUT_ANCHOR)
        if (event.startReminderOffsetMinutes != null && event.startReminderOffsetMinutes !in reminderOffsets)
            add(ValidationError.INVALID_REMINDER_OFFSET)
        if (event.endDateEpochDay != null && event.endMinuteOfDay == null) {
            add(ValidationError.EVENT_END_DATE_WITHOUT_END_TIME)
        }
        if (event.startMinuteOfDay != null && event.endMinuteOfDay != null) {
            val endDate = event.endDateEpochDay ?: event.dateEpochDay
            if (
                endDate < event.dateEpochDay ||
                (endDate == event.dateEpochDay && event.endMinuteOfDay <= event.startMinuteOfDay)
            ) {
                add(ValidationError.EVENT_END_BEFORE_START)
            }
        }

    }

    fun validateSession(session: Session, tagIds: Set<Long> = emptySet()): Set<ValidationError> = buildSet {
        if (session.title.trim().isEmpty()) add(ValidationError.BLANK_TITLE)
        if (session.temporalType == SessionTemporalType.POINT && session.endAtMillis != null) {
            add(ValidationError.SESSION_POINT_WITH_END)
        }
        if (session.kind != SessionKind.ACTIVITY && session.temporalType == SessionTemporalType.POINT) {
            add(ValidationError.SESSION_SLEEP_POINT)
        }
        if (session.kind != SessionKind.ACTIVITY &&
            (session.taskId != null || session.categoryId != null || session.sourceEventId != null || tagIds.isNotEmpty())) {
            add(ValidationError.SESSION_SLEEP_METADATA)
        }
        if (session.temporalType == SessionTemporalType.INTERVAL &&
            session.endAtMillis != null && session.endAtMillis <= session.startAtMillis) {
            add(ValidationError.SESSION_END_NOT_AFTER_START)
        }
    }

    /** Shared by Room writes and voice preview; actual records may never be in the future. */
    fun validateActualSession(session: Session, nowMillis: Long, tagIds: Set<Long> = emptySet()): io.github.peanutpersimmon.rido.domain.model.DomainError? {
        val errors = validateSession(session, tagIds)
        return when {
            session.startAtMillis > nowMillis || session.endAtMillis?.let { it > nowMillis } == true ->
                io.github.peanutpersimmon.rido.domain.model.DomainError.InvalidTime
            ValidationError.BLANK_TITLE in errors -> io.github.peanutpersimmon.rido.domain.model.DomainError.EmptyTitle
            errors.isNotEmpty() -> io.github.peanutpersimmon.rido.domain.model.DomainError.InvalidTime
            else -> null
        }
    }

    fun validateKeywordRule(rule: KeywordRule): Set<ValidationError> = buildSet {
        if (rule.keyword.trim().isEmpty()) add(ValidationError.BLANK_KEYWORD)
        if (rule.categoryId == null && rule.tagIds.isEmpty()) {
            add(ValidationError.KEYWORD_RULE_WITHOUT_TARGET)
        }
    }

    fun validateDisplayName(name: String): Set<ValidationError> =
        if (name.trim().isEmpty()) setOf(ValidationError.BLANK_NAME) else emptySet()

    fun validateCommandId(commandId: String): Set<ValidationError> =
        if (commandId.trim().isEmpty()) setOf(ValidationError.BLANK_COMMAND_ID) else emptySet()

    fun hasParentCycle(
        taskId: Long,
        proposedParentId: Long?,
        parentByTaskId: Map<Long, Long?>,
    ): Boolean {
        if (proposedParentId == null) return false
        var currentId: Long? = proposedParentId
        val visited = mutableSetOf<Long>()
        while (currentId != null) {
            if (currentId == taskId) return true
            if (!visited.add(currentId)) return true
            currentId = parentByTaskId[currentId]
        }
        return false
    }

    /** Half-open intervals: [start, end). A null end is an open active interval. */
    fun sessionsOverlap(
        firstStart: Long,
        firstEnd: Long?,
        secondStart: Long,
        secondEnd: Long?,
    ): Boolean {
        val firstEndExclusive = firstEnd ?: Long.MAX_VALUE
        val secondEndExclusive = secondEnd ?: Long.MAX_VALUE
        return firstStart < secondEndExclusive && secondStart < firstEndExclusive
    }

    fun overlapsAnySession(
        existingSessions: Iterable<Session>,
        candidate: Session,
    ): Boolean = existingSessions.any { existing ->
        val isSamePersistedSession = candidate.id != 0L && existing.id == candidate.id
        !isSamePersistedSession && existing.temporalType == SessionTemporalType.INTERVAL &&
            candidate.temporalType == SessionTemporalType.INTERVAL && sessionsOverlap(
            firstStart = existing.startAtMillis,
            firstEnd = existing.endAtMillis,
            secondStart = candidate.startAtMillis,
            secondEnd = candidate.endAtMillis,
        )
    }

    fun requireValid(errors: Set<ValidationError>) {
        if (errors.isNotEmpty()) throw DataValidationException(errors)
    }
}
