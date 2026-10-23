package io.github.peanutpersimmon.rido.presentation.editor.session

import io.github.peanutpersimmon.rido.domain.model.SessionKind
import io.github.peanutpersimmon.rido.domain.model.SessionTemporalType
import io.github.peanutpersimmon.rido.domain.model.TaskStatus
import io.github.peanutpersimmon.rido.presentation.editor.EditorMode
import io.github.peanutpersimmon.rido.presentation.editor.EditorOptionsUiModel
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

data class SessionEditorUiState(
    val loading: Boolean = true,
    val mode: EditorMode = EditorMode.CREATE,
    val id: Long? = null,
    val title: String = "",
    val startDate: LocalDate = LocalDate.now(),
    val startTime: LocalTime = LocalTime.now().withSecond(0).withNano(0),
    val endDate: LocalDate? = null,
    val endTime: LocalTime? = null,
    val temporalType: SessionTemporalType = SessionTemporalType.POINT,
    val wasActiveAtLoad: Boolean = false,
    val requiresCompletedIntervalEnd: Boolean = false,
    val requiredIntervalEndLimitMillis: Long? = null,
    val requiredIntervalZoneId: ZoneId? = null,
    val startTimeConfirmed: Boolean = true,
    val categoryId: Long? = null,
    val tagIds: Set<Long> = emptySet(),
    val taskId: Long? = null,
    val sourceEventId: Long? = null,
    val sourceEventTitle: String? = null,
    val kind: SessionKind = SessionKind.ACTIVITY,
    val location: String = "",
    val note: String = "",
    val options: EditorOptionsUiModel = EditorOptionsUiModel(),
    val saving: Boolean = false,
    val errorMessage: String? = null,
) {
    val isActive: Boolean get() = mode == EditorMode.EDIT &&
        temporalType == SessionTemporalType.INTERVAL && endDate == null && endTime == null
    val canSavePast: Boolean get() = title.isNotBlank() && startTimeConfirmed && endComplete &&
        hasValidRequiredEnd &&
        (kind == SessionKind.ACTIVITY || endDate != null) && !saving
    private val hasValidRequiredEnd: Boolean get() {
        if (!requiresCompletedIntervalEnd) return true
        val date = endDate ?: return false
        val time = endTime ?: return false
        val zone = requiredIntervalZoneId ?: return false
        val limit = requiredIntervalEndLimitMillis ?: return false
        val endMillis = date.atTime(time).atZone(zone).toInstant().toEpochMilli()
        val startMillis = startDate.atTime(startTime).atZone(zone).toInstant().toEpochMilli()
        return endMillis > startMillis && endMillis <= limit
    }
    val endComplete: Boolean get() = (endDate == null) == (endTime == null)
    val canSaveEdit: Boolean get() = title.isNotBlank() && startTimeConfirmed && endComplete && !saving
    val canStartNow: Boolean get() = mode == EditorMode.CREATE && title.isNotBlank() &&
        !requiresCompletedIntervalEnd && !saving &&
        (taskId == null || options.parentTasks.any { it.id == taskId && it.status !in setOf(TaskStatus.DONE, TaskStatus.CANCELLED) })

    val taskChoices get() = options.parentTasks.filter { choice ->
        choice.id == taskId && mode == EditorMode.EDIT ||
            choice.status != TaskStatus.CANCELLED && (!isActive || choice.status != TaskStatus.DONE)
    }
    val showSaveAndCompleteTask: Boolean get() = mode == EditorMode.CREATE && kind == SessionKind.ACTIVITY &&
        taskId != null && options.parentTasks.any {
            it.id == taskId && it.status != TaskStatus.DONE && it.status != TaskStatus.CANCELLED
        }
    val canSaveAndCompleteTask: Boolean get() = showSaveAndCompleteTask && canSavePast

    /** The time fields determine historical shape; a running interval retains its loaded identity. */
    val saveTemporalType: SessionTemporalType get() = when {
        kind != SessionKind.ACTIVITY || isActive || requiresCompletedIntervalEnd || endDate != null -> SessionTemporalType.INTERVAL
        else -> SessionTemporalType.POINT
    }

    fun withEnd(date: LocalDate?, time: LocalTime?): SessionEditorUiState =
        if (date != null && time != null) copy(
            endDate = date, endTime = time, temporalType = SessionTemporalType.INTERVAL,
        ) else withoutEnd()

    fun withoutEnd(): SessionEditorUiState = copy(
        endDate = null, endTime = null,
        temporalType = if (wasActiveAtLoad || requiresCompletedIntervalEnd || kind != SessionKind.ACTIVITY) SessionTemporalType.INTERVAL
            else SessionTemporalType.POINT,
    )
}
