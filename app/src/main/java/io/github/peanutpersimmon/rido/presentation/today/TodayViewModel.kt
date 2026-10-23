package io.github.peanutpersimmon.rido.presentation.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.peanutpersimmon.rido.domain.model.DomainResult
import io.github.peanutpersimmon.rido.domain.model.TaggedSession
import io.github.peanutpersimmon.rido.domain.service.AwakeDisplayWindowResolver
import io.github.peanutpersimmon.rido.domain.service.AwakeDisplayContentProjector
import io.github.peanutpersimmon.rido.domain.service.TimelineProjector
import io.github.peanutpersimmon.rido.domain.usecase.AwakeDayRequest
import io.github.peanutpersimmon.rido.domain.usecase.GetTodayStableSnapshotUseCase
import io.github.peanutpersimmon.rido.domain.usecase.ObserveActiveSessionUseCase
import io.github.peanutpersimmon.rido.domain.usecase.ObserveTimelineDataChangesUseCase
import io.github.peanutpersimmon.rido.domain.usecase.TodayPlanProjector
import io.github.peanutpersimmon.rido.domain.usecase.TodayStableSnapshot
import io.github.peanutpersimmon.rido.presentation.time.CurrentTimeProvider
import io.github.peanutpersimmon.rido.presentation.time.SystemCurrentTimeProvider
import io.github.peanutpersimmon.rido.presentation.time.alignedTicks
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.Dispatchers

/** Editor success pulses use the same snapshot loader as Room invalidation. */
@OptIn(ExperimentalCoroutinesApi::class)
internal class TodayRefreshSignals {
    private val editorChanges = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    fun observe(roomChanges: Flow<Unit>): Flow<Unit> = merge(roomChanges, editorChanges)

    fun editorChanged() { editorChanges.tryEmit(Unit) }
}

private data class TodayClockSample(
    val calendarDate: java.time.LocalDate,
    val nowMillis: Long,
)

internal data class TodaySnapshotFrame(
    val snapshot: TodayStableSnapshot,
    val referenceNowMillis: Long,
)

@OptIn(ExperimentalCoroutinesApi::class)
internal fun stableTodaySnapshots(
    changes: Flow<Unit>,
    ticks: Flow<Long>,
    zoneId: ZoneId,
    nowProvider: () -> Long,
    load: suspend (calendarDate: java.time.LocalDate, nowMillis: Long) -> TodayStableSnapshot,
): Flow<TodaySnapshotFrame> {
    val dateChanges = ticks.map { nowMillis ->
        TodayClockSample(
            calendarDate = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate(),
            nowMillis = nowMillis,
        )
    }.distinctUntilChangedBy(TodayClockSample::calendarDate)
    return combine(changes, dateChanges) { _, clock -> clock }
        .mapLatest { clock ->
            // Data changes arrive between minute ticks. Read the wall clock for each reload,
            // then carry that time into projection so freshly created items already exist.
            val refreshNow = maxOf(clock.nowMillis, nowProvider())
            val date = Instant.ofEpochMilli(refreshNow).atZone(zoneId).toLocalDate()
            val snapshot = load(date, refreshNow)
            TodaySnapshotFrame(snapshot, maxOf(refreshNow, nowProvider()))
        }
}

@OptIn(ExperimentalCoroutinesApi::class)
internal fun todayTicks(
    pageVisible: Flow<Boolean>,
    activeSession: Flow<TaggedSession?>,
    tickFlow: (periodMillis: Long) -> Flow<Long>,
): Flow<Long> = combine(pageVisible, activeSession) { visible, active ->
    if (!visible) null else if (active == null) 60_000L else 1_000L
}.distinctUntilChanged().flatMapLatest { periodMillis ->
    if (periodMillis == null) emptyFlow() else tickFlow(periodMillis)
}

private data class TodayProjectionInput(
    val snapshot: TodayStableSnapshot,
    val activeSession: TaggedSession?,
    val nowMillis: Long,
    val displayMode: TimelineDisplayMode,
)

internal fun projectTodaySnapshot(
    snapshot: TodayStableSnapshot,
    activeSession: TaggedSession?,
    nowMillis: Long,
    displayMode: TimelineDisplayMode,
    defaultAwakeTime: DefaultAwakeTimeConfig,
    zoneId: ZoneId,
    locale: Locale,
): TodayUiState {
    val sources = snapshot.displaySources
    val relevantContent = AwakeDisplayContentProjector.project(
        displayDate = snapshot.calendarDate,
        sessions = sources.sessions.map { it.session },
        events = sources.events.map { it.event },
        tasks = sources.tasks.map { it.task },
        zoneId = zoneId,
        asOfMillis = nowMillis,
        includePointRecords = true,
        includeTaskCompletions = false,
    )
    val displayWindow = AwakeDisplayWindowResolver.resolveToday(
        displayDate = snapshot.calendarDate,
        nightSleeps = snapshot.nightSleeps,
        relevantContent = relevantContent,
        defaultWakeMinute = defaultAwakeTime.wakeMinute,
        defaultSleepMinute = defaultAwakeTime.sleepMinute,
        zoneId = zoneId,
        nowMillis = nowMillis,
    )
    return when (val timeline = TimelineProjector.project(
        window = displayWindow,
        sessions = sources.sessions,
        events = sources.events,
        tasks = sources.tasks,
        asOfMillis = nowMillis,
        zoneId = zoneId,
    )) {
        is DomainResult.Failure -> TodayUiState.Error(timeline.error.toString())
        is DomainResult.Success -> TodayUiMapper.map(
            date = snapshot.calendarDate,
            displayWindow = displayWindow,
            entries = timeline.value,
            activeSession = activeSession,
            labels = snapshot.labels,
            planItems = TodayPlanProjector.project(
                date = snapshot.calendarDate,
                zoneId = zoneId,
                asOfMillis = nowMillis,
                tasks = snapshot.allTasks,
                events = sources.events,
            ),
            displayMode = displayMode,
            nowMillis = nowMillis,
            zoneId = zoneId,
            locale = locale,
            metadataSources = snapshot.metadataSources,
        )
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModel(
    observeChanges: ObserveTimelineDataChangesUseCase,
    observeActiveSession: ObserveActiveSessionUseCase,
    private val getStableSnapshot: GetTodayStableSnapshotUseCase,
    private val defaultAwakeTime: DefaultAwakeTimeConfig = DefaultAwakeTimeConfig.Standard,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
    private val locale: Locale = Locale.getDefault(),
    private val timeProvider: CurrentTimeProvider = SystemCurrentTimeProvider,
    private val tickFlow: (periodMillis: Long) -> Flow<Long> = { periodMillis ->
        alignedTicks(timeProvider, periodMillis)
    },
) : ViewModel() {
    private val refreshSignals = TodayRefreshSignals()
    private val displayMode = MutableStateFlow(TimelineDisplayMode.OVERVIEW)
    private val pageVisible = MutableStateFlow(true)
    private val activeSession = observeActiveSession()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    private val ticks = todayTicks(pageVisible, activeSession, tickFlow).shareIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        replay = 1,
    )
    private val stableSnapshots = stableTodaySnapshots(
        changes = refreshSignals.observe(observeChanges()),
        ticks = ticks,
        zoneId = zoneId,
        nowProvider = timeProvider::nowMillis,
    ) { calendarDate, nowMillis ->
        getStableSnapshot(
            AwakeDayRequest(
                calendarDate = calendarDate,
                defaultWakeMinute = defaultAwakeTime.wakeMinute,
                defaultSleepMinute = defaultAwakeTime.sleepMinute,
                zoneId = zoneId,
                asOfMillis = nowMillis,
            ),
        )
    }

    val uiState = combine(
        stableSnapshots,
        activeSession,
        ticks,
        displayMode,
    ) { frame, active, tickNowMillis, mode ->
        TodayProjectionInput(frame.snapshot, active,
            maxOf(frame.referenceNowMillis, tickNowMillis), mode)
    }.filter { input ->
        input.snapshot.calendarDate == Instant.ofEpochMilli(input.nowMillis)
            .atZone(zoneId)
            .toLocalDate()
    }.map { input ->
        projectTodaySnapshot(
            snapshot = input.snapshot,
            activeSession = input.activeSession,
            nowMillis = input.nowMillis,
            displayMode = input.displayMode,
            defaultAwakeTime = defaultAwakeTime,
            zoneId = zoneId,
            locale = locale,
        )
    }.flowOn(Dispatchers.Default).catch { error ->
        emit(TodayUiState.Error(error.message ?: error::class.java.simpleName))
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        TodayUiState.Loading,
    )

    fun toggleDisplayMode() {
        displayMode.update {
            if (it == TimelineDisplayMode.OVERVIEW) TimelineDisplayMode.DETAIL else TimelineDisplayMode.OVERVIEW
        }
    }

    fun setPageVisible(visible: Boolean) {
        pageVisible.value = visible
    }

    fun refreshAfterEditorChange() {
        refreshSignals.editorChanged()
    }

    companion object {
        fun factory(
            observeChanges: ObserveTimelineDataChangesUseCase,
            observeActiveSession: ObserveActiveSessionUseCase,
            getStableSnapshot: GetTodayStableSnapshotUseCase,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = TodayViewModel(
                observeChanges,
                observeActiveSession,
                getStableSnapshot,
            ) as T
        }
    }
}
