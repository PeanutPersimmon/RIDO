package io.github.peanutpersimmon.rido.presentation.editor.session

import androidx.lifecycle.ViewModel
import io.github.peanutpersimmon.rido.presentation.voice.VoiceEditorSeed
import io.github.peanutpersimmon.rido.presentation.voice.apply
import io.github.peanutpersimmon.rido.domain.model.SessionKind
import io.github.peanutpersimmon.rido.domain.model.SessionTemporalType
import io.github.peanutpersimmon.rido.domain.voice.VoiceAction
import io.github.peanutpersimmon.rido.domain.voice.applyTo
import androidx.lifecycle.viewModelScope
import io.github.peanutpersimmon.rido.domain.model.DomainResult
import io.github.peanutpersimmon.rido.domain.model.Session
import io.github.peanutpersimmon.rido.domain.repository.DeletedSessionSnapshot
import io.github.peanutpersimmon.rido.domain.usecase.CreatePastSessionUseCase
import io.github.peanutpersimmon.rido.domain.usecase.CreatePastSessionAndCompleteTaskUseCase
import io.github.peanutpersimmon.rido.domain.usecase.DeleteSessionWithUndoUseCase
import io.github.peanutpersimmon.rido.domain.usecase.GetEditorOptionsUseCase
import io.github.peanutpersimmon.rido.domain.usecase.GetSessionForEditorUseCase
import io.github.peanutpersimmon.rido.domain.usecase.NewSessionInput
import io.github.peanutpersimmon.rido.domain.usecase.RestoreSessionUseCase
import io.github.peanutpersimmon.rido.domain.usecase.SaveSessionEditUseCase
import io.github.peanutpersimmon.rido.domain.usecase.StartSessionUseCase
import io.github.peanutpersimmon.rido.presentation.editor.EditorMode
import io.github.peanutpersimmon.rido.presentation.editor.editorOptions
import io.github.peanutpersimmon.rido.presentation.editor.localDateTimeMillis
import io.github.peanutpersimmon.rido.presentation.editor.toLocalDate
import io.github.peanutpersimmon.rido.presentation.editor.toLocalTime
import io.github.peanutpersimmon.rido.presentation.editor.userMessage
import io.github.peanutpersimmon.rido.presentation.time.CurrentTimeProvider
import io.github.peanutpersimmon.rido.presentation.time.SystemCurrentTimeProvider
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SessionEditorViewModel(
    private val getSession: GetSessionForEditorUseCase,
    private val getOptions: GetEditorOptionsUseCase,
    private val createPast: CreatePastSessionUseCase,
    private val startSession: StartSessionUseCase,
    private val saveEdit: SaveSessionEditUseCase,
    private val deleteSession: DeleteSessionWithUndoUseCase,
    private val restoreSession: RestoreSessionUseCase,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
    private val clock: CurrentTimeProvider = SystemCurrentTimeProvider,
    private val getEvent: io.github.peanutpersimmon.rido.domain.usecase.GetEventForEditorUseCase? = null,
    private val observeChanges: io.github.peanutpersimmon.rido.domain.usecase.ObserveTimelineDataChangesUseCase? = null,
    private val createPastAndComplete: CreatePastSessionAndCompleteTaskUseCase? = null,
    private val getPreviousActualEnd: io.github.peanutpersimmon.rido.domain.usecase.GetPreviousActualEndUseCase? = null,
    private val initialClassification: io.github.peanutpersimmon.rido.domain.usecase.InitialClassificationUseCase? = null,
) : ViewModel() {
    private val mutableState = MutableStateFlow(SessionEditorUiState())
    val uiState = mutableState.asStateFlow()
    private var original: Session? = null
    private var classificationJob: kotlinx.coroutines.Job? = null
    private var categoryManuallyChanged = false
    private var tagsManuallyChanged = false
    private var sleepKindChosen = false
    private var voiceStart = false
    private var voiceSeed: VoiceEditorSeed? = null

    private var sourceWatch: kotlinx.coroutines.Job? = null

    fun load(id: Long?, sourceEventId: Long? = null) = viewModelScope.launch {
        sourceWatch?.cancel()
        voiceStart = false
        voiceSeed = null
        val record = id?.let { getSession(it) }
        if (id != null && record == null) {
            mutableState.value = SessionEditorUiState(errorMessage = "内容已不存在，请关闭后重试")
            return@launch
        }
        original = record?.session
        categoryManuallyChanged = false
        tagsManuallyChanged = record?.tagIds?.isNotEmpty() == true
        sleepKindChosen = false
        val options = getOptions()
        val now = clock.nowMillis()
        mutableState.value = record?.let {
            val session = it.session
            SessionEditorUiState(
                loading = false, mode = EditorMode.EDIT, id = session.id, title = session.title,
                startDate = session.startAtMillis.toLocalDate(zoneId), startTime = session.startAtMillis.toLocalTime(zoneId),
                endDate = session.endAtMillis?.toLocalDate(zoneId), endTime = session.endAtMillis?.toLocalTime(zoneId),
                temporalType = session.temporalType,
                wasActiveAtLoad = session.temporalType == SessionTemporalType.INTERVAL && session.endAtMillis == null,
                categoryId = session.categoryId.takeIf { session.kind == io.github.peanutpersimmon.rido.domain.model.SessionKind.ACTIVITY },
                tagIds = it.tagIds.takeIf { session.kind == io.github.peanutpersimmon.rido.domain.model.SessionKind.ACTIVITY } ?: emptySet(),
                taskId = session.taskId.takeIf { session.kind == io.github.peanutpersimmon.rido.domain.model.SessionKind.ACTIVITY },
                sourceEventId = session.sourceEventId.takeIf { session.kind == io.github.peanutpersimmon.rido.domain.model.SessionKind.ACTIVITY },
                sourceEventTitle = session.sourceEventId?.takeIf { session.kind == io.github.peanutpersimmon.rido.domain.model.SessionKind.ACTIVITY }
                    ?.let { getEvent?.invoke(it)?.event?.title },
                kind = session.kind, location = session.location.orEmpty(), note = session.note.orEmpty(),
                options = editorOptions(options.categories, options.tags, options.tasks),
            )
        } ?: run {
            val editorNow = now - Math.floorMod(now, 60_000L)
            val dayStart = editorNow.toLocalDate(zoneId).atStartOfDay(zoneId).toInstant().toEpochMilli()
            val previousEnd = if (sourceEventId == null) getPreviousActualEnd?.invoke(dayStart, editorNow) else null
            SessionEditorUiState(
                loading = false,
                startDate = previousEnd?.toLocalDate(zoneId) ?: editorNow.toLocalDate(zoneId),
                startTime = previousEnd?.toLocalTime(zoneId) ?: editorNow.toLocalTime(zoneId),
                endDate = previousEnd?.let { editorNow.toLocalDate(zoneId) },
                endTime = previousEnd?.let { editorNow.toLocalTime(zoneId) },
                temporalType = if (previousEnd != null) SessionTemporalType.INTERVAL else SessionTemporalType.POINT,
                options = editorOptions(options.categories, options.tags, options.tasks),
            )
        }
        if (id == null && sourceEventId != null) prefillEvent(sourceEventId)
        sourceWatch = viewModelScope.launch {
            observeChanges?.invoke()?.collect {
                val source = if (id != null) getSession(id)?.session?.sourceEventId else mutableState.value.sourceEventId
                val event = source?.let { getEvent?.invoke(it)?.event }
                update { it.copy(sourceEventId = event?.id, sourceEventTitle = event?.title) }
            }
        }
    }

    private suspend fun prefillEvent(eventId: Long) {
        val tagged = getEvent?.invoke(eventId) ?: return
        val e = tagged.event
        val now = clock.nowMillis()
        val start = e.startMinuteOfDay?.let {
            io.github.peanutpersimmon.rido.domain.time.epochDayMinuteToMillis(e.dateEpochDay, it, zoneId)
        }
        val end = e.endMinuteOfDay?.let {
            io.github.peanutpersimmon.rido.domain.time.epochDayMinuteToMillis(e.endDateEpochDay ?: e.dateEpochDay, it, zoneId)
        }
        update { state ->
            val legalStart = start?.takeIf { it <= now }
            val legalEnd = end?.takeIf { legalStart != null && it > legalStart && it <= now }
            state.copy(title = e.title, note = e.note.orEmpty(), location = e.location.orEmpty(),
                categoryId = e.categoryId, tagIds = tagged.tagIds, sourceEventId = e.id, sourceEventTitle = e.title,
                startDate = legalStart?.toLocalDate(zoneId) ?: java.time.LocalDate.ofEpochDay(e.dateEpochDay),
                startTime = legalStart?.toLocalTime(zoneId) ?: state.startTime,
                startTimeConfirmed = legalStart != null,
                endDate = legalEnd?.toLocalDate(zoneId),
                endTime = legalEnd?.toLocalTime(zoneId),
                temporalType = if (e.endMinuteOfDay == null) SessionTemporalType.POINT else SessionTemporalType.INTERVAL,
                requiresCompletedIntervalEnd = e.endMinuteOfDay != null && legalEnd == null,
                requiredIntervalEndLimitMillis = now,
                requiredIntervalZoneId = zoneId)
        }
    }

    fun loadSleep(id: Long?) = viewModelScope.launch {
        load(id).join()
        if (id == null) {
            val end = clock.nowMillis()
            val start = end - 8 * 60 * 60 * 1000L
            mutableState.update { it.copy(
                title = "夜间睡眠", kind = io.github.peanutpersimmon.rido.domain.model.SessionKind.NIGHT_SLEEP,
                startDate = start.toLocalDate(zoneId), startTime = start.toLocalTime(zoneId),
                endDate = end.toLocalDate(zoneId), endTime = end.toLocalTime(zoneId),
                temporalType = SessionTemporalType.INTERVAL,
                categoryId = null, taskId = null, tagIds = emptySet(),
            ) }
        }
    }

    fun applyVoice(seed: VoiceEditorSeed) {
        voiceStart = true
        voiceSeed = seed
        mutableState.update { seed.apply(it, zoneId) }
    }

    fun applySleepVoice(seed: VoiceEditorSeed) {
        voiceStart = seed.draft.action == VoiceAction.StartSleep
        voiceSeed = seed
        sleepKindChosen = io.github.peanutpersimmon.rido.domain.voice.VoiceSemanticCue.isExplicitNap(seed.draft.originalText) ||
            io.github.peanutpersimmon.rido.domain.voice.VoiceSemanticCue.isExplicitNightSleep(seed.draft.originalText)
        mutableState.update { current ->
            val applied = seed.apply(current, zoneId)
            val fields = seed.draft.fields
            val kind = io.github.peanutpersimmon.rido.domain.voice.VoiceIntentPolicy
                .resolvedSleepKind(seed.draft, zoneId) ?: SessionKind.NIGHT_SLEEP
            val hasExplicitEnd = fields.endAt != null || fields.end !is io.github.peanutpersimmon.rido.domain.voice.VoiceFieldPatch.Keep
            applied.copy(
                title = fields.title.applyTo(null) ?: if (kind == SessionKind.NAP) "小憩" else "夜间睡眠",
                kind = kind,
                endDate = applied.endDate.takeIf {
                    seed.draft.action != VoiceAction.StartSleep && hasExplicitEnd
                },
                endTime = applied.endTime.takeIf {
                    seed.draft.action != VoiceAction.StartSleep && hasExplicitEnd
                },
                categoryId = null,
                taskId = null,
                tagIds = emptySet(),
                sourceEventId = null,
                sourceEventTitle = null,
                temporalType = SessionTemporalType.INTERVAL,
            )
        }
    }

    fun update(transform: (SessionEditorUiState) -> SessionEditorUiState) = mutableState.update(transform)
    fun changeTitle(value: String) {
        update { it.copy(title = value) }
        classificationJob?.cancel()
        if (original?.categoryId != null || mutableState.value.kind != SessionKind.ACTIVITY || initialClassification == null) return
        classificationJob = viewModelScope.launch {
            kotlinx.coroutines.delay(180)
            val match = initialClassification(io.github.peanutpersimmon.rido.domain.usecase.InitialClassificationInput(value, applyKeywordRules = true))
            mutableState.update { state -> if (state.title != value || state.kind != SessionKind.ACTIVITY) state else state.copy(
                categoryId = if (categoryManuallyChanged) state.categoryId else match.categoryId,
                tagIds = if (tagsManuallyChanged) state.tagIds else match.tagIds,
            ) }
        }
    }
    fun chooseCategory(value: Long?, kind: SessionKind) {
        categoryManuallyChanged = true
        update { it.copy(categoryId = value, kind = kind) }
    }
    fun chooseTags(value: Set<Long>) { tagsManuallyChanged = true; update { it.copy(tagIds = value) } }
    fun chooseSleepKind(kind: SessionKind) {
        sleepKindChosen = true
        update { it.copy(kind = kind, title = io.github.peanutpersimmon.rido.domain.service.SleepCheckInPolicy.titleFor(kind),
            categoryId = null, taskId = null, tagIds = emptySet()) }
    }
    fun setSleepStart(date: java.time.LocalDate, time: java.time.LocalTime) =
        mutableState.update { inferSleepKind(it.copy(startDate = date, startTime = time)) }
    fun setSleepEnd(date: java.time.LocalDate, time: java.time.LocalTime) =
        mutableState.update { inferSleepKind(it.copy(endDate = date, endTime = time)) }
    private fun inferSleepKind(state: SessionEditorUiState): SessionEditorUiState {
        if (sleepKindChosen || state.kind == SessionKind.ACTIVITY) return state
        val endDate = state.endDate ?: return state
        val endTime = state.endTime ?: return state
        val start = localDateTimeMillis(state.startDate, state.startTime, zoneId)
        val end = localDateTimeMillis(endDate, endTime, zoneId)
        if (end <= start) return state
        val kind = io.github.peanutpersimmon.rido.domain.service.SleepCheckInPolicy.inferKind(start, end, zoneId)
        return state.copy(kind = kind,
            title = io.github.peanutpersimmon.rido.domain.service.SleepCheckInPolicy.titleFor(kind))
    }
    fun clearError() = mutableState.update { it.copy(errorMessage = null) }
    fun clearEnd() = mutableState.update(SessionEditorUiState::withoutEnd)
    fun setEnd(date: java.time.LocalDate?, time: java.time.LocalTime?) =
        mutableState.update { it.withEnd(date, time).copy(
            requiredIntervalEndLimitMillis = if (it.requiresCompletedIntervalEnd) clock.nowMillis()
                else it.requiredIntervalEndLimitMillis,
        ) }

    fun save(completeTask: Boolean = false, onSuccess: () -> Unit) = viewModelScope.launch {
        val state = mutableState.value
        if (state.mode == EditorMode.CREATE && !state.canSavePast || state.mode == EditorMode.EDIT && !state.canSaveEdit) return@launch
        if (completeTask && (!state.canSaveAndCompleteTask || createPastAndComplete == null)) return@launch
        val baseStart = original?.startAtMillis ?: voiceSeed?.draft?.fields?.startAt
        val baseEnd = original?.endAtMillis ?: voiceSeed?.draft?.fields?.endAt
        val start = baseStart?.takeIf { it.toLocalDate(zoneId) == state.startDate && it.toLocalTime(zoneId) == state.startTime }
            ?: localDateTimeMillis(state.startDate, state.startTime, zoneId)
        val end = if (state.endDate != null && state.endTime != null)
            baseEnd?.takeIf { it.toLocalDate(zoneId) == state.endDate && it.toLocalTime(zoneId) == state.endTime }
                ?: localDateTimeMillis(state.endDate, state.endTime, zoneId)
            else null
        val now = clock.nowMillis()
        if (start > now || end?.let { it > now } == true) {
            mutableState.update { it.copy(saving = false, errorMessage = "记录时间不能晚于当前时间") }
            return@launch
        }
        mutableState.update { it.copy(saving = true, errorMessage = null) }
        val result = if (state.mode == EditorMode.CREATE) {
            val historical = input(state, start, end).copy(temporalType = state.saveTemporalType)
            if (completeTask) requireNotNull(createPastAndComplete)(historical) else createPast(historical)
        } else {
            val base = requireNotNull(original)
            saveEdit(
                Session(
                    id = requireNotNull(state.id), title = state.title, note = state.note.ifBlank { null },
                    location = state.location.ifBlank { null }, kind = state.kind,
                    temporalType = state.saveTemporalType, startAtMillis = start,
                    endAtMillis = end, taskId = state.taskId, categoryId = state.categoryId,
                    sourceEventId = state.sourceEventId,
                    createdAtMillis = base.createdAtMillis, updatedAtMillis = clock.nowMillis(),
                ),
                state.tagIds,
            )
        }
        handle(result, onSuccess)
    }

    fun startNow(onSuccess: () -> Unit) = viewModelScope.launch {
        val state = mutableState.value
        if (!state.canStartNow) return@launch
        mutableState.update { it.copy(saving = true, errorMessage = null) }
        handle(startSession(input(state, clock.nowMillis(), null).copy(
            temporalType = SessionTemporalType.INTERVAL,
        ), requireIdle = voiceStart), onSuccess)
    }

    fun delete(onDeleted: (DeletedSessionSnapshot) -> Unit) = viewModelScope.launch {
        when (val result = deleteSession(mutableState.value.id ?: return@launch)) {
            is DomainResult.Success -> onDeleted(result.value)
            is DomainResult.Failure -> mutableState.update { it.copy(errorMessage = result.error.userMessage()) }
        }
    }

    suspend fun restore(snapshot: DeletedSessionSnapshot): Boolean = restoreSession(snapshot) is DomainResult.Success

    private fun input(state: SessionEditorUiState, start: Long, end: Long?) = NewSessionInput(
        title = state.title, startAtMillis = start, endAtMillis = end, taskId = state.taskId,
        sourceEventId = state.sourceEventId,
        categoryId = state.categoryId, tagIds = state.tagIds, note = state.note.ifBlank { null },
        location = state.location.ifBlank { null }, kind = state.kind, createdAtMillis = clock.nowMillis(),
    )

    private fun <T> handle(result: DomainResult<T>, onSuccess: () -> Unit) {
        when (result) {
            is DomainResult.Success -> onSuccess()
            is DomainResult.Failure -> mutableState.update { it.copy(saving = false, errorMessage = result.error.userMessage()) }
        }
    }
}
