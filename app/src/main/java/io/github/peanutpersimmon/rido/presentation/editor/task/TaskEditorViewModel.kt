package io.github.peanutpersimmon.rido.presentation.editor.task

import androidx.lifecycle.ViewModel
import io.github.peanutpersimmon.rido.presentation.voice.VoiceEditorSeed
import io.github.peanutpersimmon.rido.presentation.voice.apply
import androidx.lifecycle.viewModelScope
import io.github.peanutpersimmon.rido.domain.model.DomainResult
import io.github.peanutpersimmon.rido.domain.model.Task
import io.github.peanutpersimmon.rido.domain.model.TaskStatus
import io.github.peanutpersimmon.rido.domain.validation.DataValidator
import io.github.peanutpersimmon.rido.domain.usecase.CompleteTaskUseCase
import io.github.peanutpersimmon.rido.domain.usecase.DeleteTaskUseCase
import io.github.peanutpersimmon.rido.domain.usecase.GetEditorOptionsUseCase
import io.github.peanutpersimmon.rido.domain.usecase.GetTaskForEditorUseCase
import io.github.peanutpersimmon.rido.domain.usecase.ReopenTaskUseCase
import io.github.peanutpersimmon.rido.domain.usecase.SaveTaskUseCase
import io.github.peanutpersimmon.rido.presentation.editor.EditorMode
import io.github.peanutpersimmon.rido.presentation.editor.editorOptions
import io.github.peanutpersimmon.rido.presentation.editor.userMessage
import io.github.peanutpersimmon.rido.presentation.time.CurrentTimeProvider
import io.github.peanutpersimmon.rido.presentation.time.SystemCurrentTimeProvider
import java.time.LocalTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TaskEditorViewModel(
    private val getTask: GetTaskForEditorUseCase,
    private val getOptions: GetEditorOptionsUseCase,
    private val saveTask: SaveTaskUseCase,
    private val deleteTask: DeleteTaskUseCase,
    private val completeTask: CompleteTaskUseCase,
    private val reopenTask: ReopenTaskUseCase,
    private val clock: CurrentTimeProvider = SystemCurrentTimeProvider,
    private val initialClassification: io.github.peanutpersimmon.rido.domain.usecase.InitialClassificationUseCase? = null,
) : ViewModel() {
    private val mutableState = MutableStateFlow(TaskEditorUiState())
    val uiState = mutableState.asStateFlow()
    private var original: Task? = null
    private var classificationJob: kotlinx.coroutines.Job? = null
    private var categoryManuallyChanged = false
    private var tagsManuallyChanged = false

    fun load(id: Long?) = viewModelScope.launch {
        val record = id?.let { getTask(it) }
        if (id != null && record == null) {
            mutableState.value = TaskEditorUiState(errorMessage = "内容已不存在，请关闭后重试")
            return@launch
        }
        original = record?.task
        categoryManuallyChanged = false
        tagsManuallyChanged = record?.tagIds?.isNotEmpty() == true
        val options = getOptions()
        mutableState.value = record?.let {
            val task = it.task
            TaskEditorUiState(
                loading = false, mode = EditorMode.EDIT, id = task.id, title = task.title,
                note = task.note.orEmpty(), location = task.location.orEmpty(),
                plannedDate = task.plannedDateEpochDay?.let(java.time.LocalDate::ofEpochDay),
                plannedStart = task.plannedStartMinuteOfDay?.let { LocalTime.MIN.plusMinutes(it.toLong()) },
                plannedReminderOffsetMinutes = task.plannedReminderOffsetMinutes,
                dueDate = task.dueDateEpochDay?.let(java.time.LocalDate::ofEpochDay),
                dueTime = task.dueMinuteOfDay?.let { LocalTime.MIN.plusMinutes(it.toLong()) },
                dueReminderOffsetMinutes = task.dueReminderOffsetMinutes,
                estimatedHours = task.estimatedDurationMinutes?.div(60)?.takeIf { value -> value > 0 }?.toString().orEmpty(),
                estimatedMinutes = task.estimatedDurationMinutes?.rem(60)?.takeIf { value -> value > 0 }?.toString().orEmpty(),
                categoryId = task.categoryId, tagIds = it.tagIds, parentTaskId = task.parentTaskId,
                status = task.status,
                options = editorOptions(options.categories, options.tags, options.tasks, task.id),
            )
        } ?: TaskEditorUiState(
            loading = false,
            options = editorOptions(options.categories, options.tags, options.tasks),
        )
    }

    fun applyVoice(seed: VoiceEditorSeed) = mutableState.update { seed.apply(it) }

    fun update(transform: (TaskEditorUiState) -> TaskEditorUiState) = mutableState.update(transform)
    fun changeTitle(value: String) {
        update { it.copy(title = value) }
        classificationJob?.cancel()
        if (original?.categoryId != null || initialClassification == null) return
        classificationJob = viewModelScope.launch {
            kotlinx.coroutines.delay(180)
            val match = initialClassification(io.github.peanutpersimmon.rido.domain.usecase.InitialClassificationInput(value, applyKeywordRules = true))
            mutableState.update { state -> if (state.title != value) state else state.copy(
                categoryId = if (categoryManuallyChanged) state.categoryId else match.categoryId,
                tagIds = if (tagsManuallyChanged) state.tagIds else match.tagIds,
            ) }
        }
    }
    fun chooseCategory(value: Long?) { categoryManuallyChanged = true; update { it.copy(categoryId = value) } }
    fun chooseTags(value: Set<Long>) { tagsManuallyChanged = true; update { it.copy(tagIds = value) } }
    fun clearError() = mutableState.update { it.copy(errorMessage = null) }
    fun clearPlannedStart() = update { it.copy(plannedStart = null, plannedReminderOffsetMinutes = null) }

    fun save(onSuccess: () -> Unit) = viewModelScope.launch {
        val state = mutableState.value
        if (!state.canSave) return@launch
        mutableState.update { it.copy(saving = true, errorMessage = null) }
        val now = clock.nowMillis()
        val estimated = state.estimatedHours.toIntOrNull().orZero() * 60 + state.estimatedMinutes.toIntOrNull().orZero()
        val base = original
        val task = Task(
            id = state.id ?: 0, title = state.title, note = state.note.ifBlank { null },
            location = DataValidator.normalizedTaskLocation(state.location),
            status = base?.status ?: TaskStatus.TODO, parentTaskId = state.parentTaskId,
            plannedDateEpochDay = state.plannedDate?.toEpochDay(),
            plannedStartMinuteOfDay = state.plannedStart?.toSecondOfDay()?.div(60),
            plannedReminderOffsetMinutes = state.plannedReminderOffsetMinutes.takeIf { state.plannedDate != null && state.plannedStart != null },
            dueDateEpochDay = state.dueDate?.toEpochDay(), dueMinuteOfDay = state.dueTime?.toSecondOfDay()?.div(60),
            dueReminderOffsetMinutes = state.dueReminderOffsetMinutes.takeIf { state.dueDate != null && state.dueTime != null },
            estimatedDurationMinutes = estimated.takeIf { it > 0 }, categoryId = state.categoryId,
            createdAtMillis = base?.createdAtMillis ?: now, updatedAtMillis = now,
            completedAtMillis = base?.completedAtMillis, cancelledAtMillis = base?.cancelledAtMillis,
            completionSource = base?.completionSource,
        )
        when (val result = saveTask(task, state.tagIds)) {
            is DomainResult.Success -> onSuccess()
            is DomainResult.Failure -> mutableState.update { it.copy(saving = false, errorMessage = result.error.userMessage()) }
        }
    }

    fun delete(onSuccess: () -> Unit) = viewModelScope.launch {
        val id = mutableState.value.id ?: return@launch
        when (val result = deleteTask(id)) {
            is DomainResult.Success -> onSuccess()
            is DomainResult.Failure -> mutableState.update { it.copy(errorMessage = result.error.userMessage()) }
        }
    }

    fun toggleCompletion(onSuccess: () -> Unit) = viewModelScope.launch {
        val state = mutableState.value
        val id = state.id ?: return@launch
        val result = if (state.status == TaskStatus.DONE) reopenTask(id, clock.nowMillis())
        else completeTask(id, clock.nowMillis())
        when (result) {
            is DomainResult.Success -> onSuccess()
            is DomainResult.Failure -> mutableState.update { it.copy(errorMessage = result.error.userMessage()) }
        }
    }

    private fun Int?.orZero() = this ?: 0
}
