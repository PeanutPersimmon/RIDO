package io.github.peanutpersimmon.rido.ui.feature.editor.task

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.peanutpersimmon.rido.domain.model.TaskStatus
import io.github.peanutpersimmon.rido.presentation.editor.EditorMode
import io.github.peanutpersimmon.rido.presentation.editor.task.TaskEditorViewModel
import io.github.peanutpersimmon.rido.ui.components.AppDialog
import io.github.peanutpersimmon.rido.ui.components.picker.PickerHost
import io.github.peanutpersimmon.rido.ui.components.picker.rememberPickerHostState
import io.github.peanutpersimmon.rido.ui.feature.editor.components.*
import io.github.peanutpersimmon.rido.ui.theme.AppTheme
import java.time.LocalDate

@Composable
fun TaskEditorSheet(viewModel: TaskEditorViewModel, id: Long?, onBack: () -> Unit,
    onChanged: () -> Unit = {},
    voiceSeed: io.github.peanutpersimmon.rido.presentation.voice.VoiceEditorSeed? = null,
    onDeleted: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val picker = rememberPickerHostState()
    var confirmDelete by remember { mutableStateOf(false) }
    var ready by remember(id) { mutableStateOf(false) }
    LaunchedEffect(id, voiceSeed) { viewModel.load(id).join(); if (!viewModel.uiState.value.loading) voiceSeed?.let(viewModel::applyVoice); ready = true }
    LaunchedEffect(ready, state.errorMessage) {
        if (ready) state.errorMessage?.let { message ->
            viewModel.clearError()
            snackbar.showSnackbar(message)
        }
    }
    EditorLayout(
        if (id == null) "新建任务" else "编辑任务", ready && state.canSave, snackbar,
        onBack, { viewModel.save { onChanged(); onBack() } },
        deleteEnabled = ready && !state.saving,
        onDelete = if (id != null) ({ confirmDelete = true }) else null,
    ) {
        if (!ready) { androidx.compose.material3.Text("载入中"); return@EditorLayout }
        voiceSeed?.let {
            androidx.compose.material3.Text("请核对后保存 · ${it.draft.originalText}", style = io.github.peanutpersimmon.rido.ui.theme.AppTheme.type.caption)
        }
        androidx.compose.foundation.layout.Column {
            TitleField(state.title, viewModel::changeTitle)
            NoteField(state.note) { value -> viewModel.update { it.copy(note = value) } }
        }
        TaskTimeLayout(planned = { modifier ->
            TaskPlannedTimeField(
                value = TaskPlannedTimeValue(state.plannedDate, state.plannedStart),
                picker = picker,
                modifier = modifier,
                trailingContent = if (reminderBellVisible(state.plannedDate, state.plannedStart)) ({
                    ReminderBell("计划提醒", state.plannedReminderOffsetMinutes) { offset ->
                        viewModel.update { it.copy(plannedReminderOffsetMinutes = offset) }
                    }
                }) else null,
                onChange = { value ->
                    viewModel.update {
                        it.copy(plannedDate = value.date, plannedStart = value.start,
                            plannedReminderOffsetMinutes = it.plannedReminderOffsetMinutes.takeIf { value.date != null && value.start != null })
                    }
                },
            )
        }, deadline = { modifier ->
            SplitDateTimeField("截止", EditorTimeRole.END, state.dueDate, state.dueTime, picker,
                modifier = modifier,
                trailingContent = if (reminderBellVisible(state.dueDate, state.dueTime)) ({
                    ReminderBell("截止提醒", state.dueReminderOffsetMinutes) { offset ->
                        viewModel.update { it.copy(dueReminderOffsetMinutes = offset) }
                    }
                }) else null,
                onDateChange = { date -> viewModel.update { current -> current.copy(dueDate = date, dueTime = current.dueTime.takeIf { date != null },
                    dueReminderOffsetMinutes = current.dueReminderOffsetMinutes.takeIf { date != null && current.dueTime != null }) } },
                onTimeChange = { time -> viewModel.update { it.copy(dueDate = it.dueDate ?: LocalDate.now(), dueTime = time,
                    dueReminderOffsetMinutes = it.dueReminderOffsetMinutes.takeIf { time != null }) } },
                onClear = { viewModel.update { it.copy(dueDate = null, dueTime = null, dueReminderOffsetMinutes = null) } })
        }, estimate = { modifier ->
            EstimatedDurationField(state.estimatedHours, state.estimatedMinutes, modifier) { hours, minutes ->
                viewModel.update { it.copy(estimatedHours = hours, estimatedMinutes = minutes) }
            }
        })
        EditorToolGroup {
            CategoryPicker(state.options.categories, state.categoryId, viewModel::chooseCategory)
            TagPicker(state.options.tags, state.tagIds, viewModel::chooseTags)
            LocationField(state.location) { value -> viewModel.update { it.copy(location = value) } }
            ParentTaskPicker(state.options.parentTasks, state.parentTaskId, { value -> viewModel.update { it.copy(parentTaskId = value) } }, "父任务")
            if (state.mode == EditorMode.EDIT) EditorTool(
                Icons.Outlined.CheckCircle, if (state.status == TaskStatus.DONE) "重新打开" else "完成任务",
                onClick = { viewModel.toggleCompletion { onChanged(); onBack() } }, enabled = !state.saving,
            )
        }
    }
    PickerHost(picker)
    if (confirmDelete) AppDialog("删除任务？", { confirmDelete = false }, actions = {
        EditorTextAction("取消", { confirmDelete = false })
        EditorTextAction("删除", { confirmDelete = false; viewModel.delete { onChanged(); onDeleted() } })
    }) {
        androidx.compose.material3.Text("关联记录会解除任务关联，删除后不提供不完整的撤销。", style = AppTheme.type.editorDescription)
    }
}
