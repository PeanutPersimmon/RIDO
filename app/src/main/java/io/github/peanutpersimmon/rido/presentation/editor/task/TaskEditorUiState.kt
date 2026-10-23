package io.github.peanutpersimmon.rido.presentation.editor.task

import io.github.peanutpersimmon.rido.domain.model.TaskStatus
import io.github.peanutpersimmon.rido.presentation.editor.EditorMode
import io.github.peanutpersimmon.rido.presentation.editor.EditorOptionsUiModel
import java.time.LocalDate
import java.time.LocalTime

data class TaskEditorUiState(
    val loading: Boolean = true,
    val mode: EditorMode = EditorMode.CREATE,
    val id: Long? = null,
    val title: String = "",
    val note: String = "",
    val location: String = "",
    val plannedDate: LocalDate? = null,
    val plannedStart: LocalTime? = null,
    val plannedReminderOffsetMinutes: Int? = null,
    val dueDate: LocalDate? = null,
    val dueTime: LocalTime? = null,
    val dueReminderOffsetMinutes: Int? = null,
    val estimatedHours: String = "",
    val estimatedMinutes: String = "",
    val categoryId: Long? = null,
    val tagIds: Set<Long> = emptySet(),
    val parentTaskId: Long? = null,
    val status: TaskStatus = TaskStatus.TODO,
    val options: EditorOptionsUiModel = EditorOptionsUiModel(),
    val saving: Boolean = false,
    val errorMessage: String? = null,
) {
    val canSave: Boolean get() = title.isNotBlank() && !saving
}
