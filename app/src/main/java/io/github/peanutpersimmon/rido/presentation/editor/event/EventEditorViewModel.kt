package io.github.peanutpersimmon.rido.presentation.editor.event

import androidx.lifecycle.ViewModel
import io.github.peanutpersimmon.rido.presentation.voice.VoiceEditorSeed
import io.github.peanutpersimmon.rido.presentation.voice.apply
import androidx.lifecycle.viewModelScope
import io.github.peanutpersimmon.rido.domain.model.DomainResult
import io.github.peanutpersimmon.rido.domain.model.Event
import io.github.peanutpersimmon.rido.domain.repository.DeletedEventSnapshot
import io.github.peanutpersimmon.rido.domain.usecase.DeleteEventUseCase
import io.github.peanutpersimmon.rido.domain.usecase.GetEditorOptionsUseCase
import io.github.peanutpersimmon.rido.domain.usecase.GetEventForEditorUseCase
import io.github.peanutpersimmon.rido.domain.usecase.RestoreEventUseCase
import io.github.peanutpersimmon.rido.domain.usecase.SaveEventUseCase
import io.github.peanutpersimmon.rido.presentation.editor.EditorMode
import io.github.peanutpersimmon.rido.presentation.editor.editorOptions
import io.github.peanutpersimmon.rido.presentation.editor.toLocalDate
import io.github.peanutpersimmon.rido.presentation.editor.userMessage
import io.github.peanutpersimmon.rido.presentation.time.CurrentTimeProvider
import io.github.peanutpersimmon.rido.presentation.time.SystemCurrentTimeProvider
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class EventEditorViewModel(
    private val getEvent: GetEventForEditorUseCase,
    private val getOptions: GetEditorOptionsUseCase,
    private val saveEvent: SaveEventUseCase,
    private val deleteEvent: DeleteEventUseCase,
    private val restoreEvent: RestoreEventUseCase,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
    private val clock: CurrentTimeProvider = SystemCurrentTimeProvider,
    private val initialClassification: io.github.peanutpersimmon.rido.domain.usecase.InitialClassificationUseCase? = null,
) : ViewModel() {
    private val mutableState = MutableStateFlow(EventEditorUiState())
    val uiState = mutableState.asStateFlow()
    private var original: Event? = null
    private var classificationJob: kotlinx.coroutines.Job? = null
    private var categoryManuallyChanged = false
    private var tagsManuallyChanged = false

    fun load(id: Long?) = viewModelScope.launch {
        val record = id?.let { getEvent(it) }
        if (id != null && record == null) {
            mutableState.value = EventEditorUiState(errorMessage = "内容已不存在，请关闭后重试")
            return@launch
        }
        original = record?.event
        categoryManuallyChanged = false
        tagsManuallyChanged = record?.tagIds?.isNotEmpty() == true
        val options = getOptions()
        mutableState.value = record?.let {
            val event = it.event
            EventEditorUiState(
                loading = false, mode = EditorMode.EDIT, id = event.id, title = event.title,
                date = LocalDate.ofEpochDay(event.dateEpochDay),
                startTime = event.startMinuteOfDay?.let { minute -> LocalTime.MIN.plusMinutes(minute.toLong()) },
                startReminderOffsetMinutes = event.startReminderOffsetMinutes,
                endDate = event.endDateEpochDay?.let(LocalDate::ofEpochDay),
                endTime = event.endMinuteOfDay?.let { minute -> LocalTime.MIN.plusMinutes(minute.toLong()) },
                categoryId = event.categoryId, tagIds = it.tagIds,
                location = event.location.orEmpty(), note = event.note.orEmpty(),
                options = editorOptions(options.categories, options.tags, options.tasks),
            )
        } ?: EventEditorUiState(
            loading = false,
            date = clock.nowMillis().toLocalDate(zoneId),
            options = editorOptions(options.categories, options.tags, options.tasks),
        )
    }

    fun applyVoice(seed: VoiceEditorSeed) = mutableState.update { seed.apply(it) }

    fun update(transform: (EventEditorUiState) -> EventEditorUiState) = mutableState.update(transform)
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
    fun clearStart() = update { it.copy(startTime = null, startReminderOffsetMinutes = null, endTime = null, endDate = null) }

    fun save(onSuccess: () -> Unit) = viewModelScope.launch {
        val state = mutableState.value
        if (!state.canSave) return@launch
        val now = clock.nowMillis()
        val base = original
        val event = Event(
            id = state.id ?: 0, title = state.title, note = state.note.ifBlank { null },
            location = state.location.ifBlank { null }, dateEpochDay = state.date.toEpochDay(),
            startMinuteOfDay = state.startTime?.toSecondOfDay()?.div(60),
            startReminderOffsetMinutes = state.startReminderOffsetMinutes.takeIf { state.startTime != null },
            endDateEpochDay = state.endDate?.toEpochDay(),
            endMinuteOfDay = state.endTime?.toSecondOfDay()?.div(60),
            categoryId = state.categoryId, createdAtMillis = base?.createdAtMillis ?: now, updatedAtMillis = now,
        )
        mutableState.update { it.copy(saving = true, errorMessage = null) }
        when (val result = saveEvent(event, state.tagIds)) {
            is DomainResult.Success -> onSuccess()
            is DomainResult.Failure -> mutableState.update { it.copy(saving = false, errorMessage = result.error.userMessage()) }
        }
    }

    fun delete(onDeleted: (DeletedEventSnapshot) -> Unit) = viewModelScope.launch {
        val id = mutableState.value.id ?: return@launch
        when (val result = deleteEvent(id)) {
            is DomainResult.Success -> onDeleted(result.value)
            is DomainResult.Failure -> mutableState.update { it.copy(errorMessage = result.error.userMessage()) }
        }
    }

    suspend fun restore(snapshot: DeletedEventSnapshot): Boolean = restoreEvent(snapshot) is DomainResult.Success

}
