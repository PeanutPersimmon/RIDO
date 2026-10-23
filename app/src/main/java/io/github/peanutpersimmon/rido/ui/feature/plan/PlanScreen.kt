package io.github.peanutpersimmon.rido.ui.feature.plan

import io.github.peanutpersimmon.rido.ui.theme.AppTheme
import io.github.peanutpersimmon.rido.ui.components.LocalFloatingNavigationClearance
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.peanutpersimmon.rido.presentation.plan.MonthContent
import io.github.peanutpersimmon.rido.presentation.plan.PlanUiState
import io.github.peanutpersimmon.rido.presentation.plan.PlanViewMode
import io.github.peanutpersimmon.rido.presentation.plan.PlanViewModel
import io.github.peanutpersimmon.rido.domain.model.SessionKind
import io.github.peanutpersimmon.rido.ui.components.CompactCapsule
import io.github.peanutpersimmon.rido.ui.components.CompactDateNavigation
import io.github.peanutpersimmon.rido.ui.components.ScrollablePrimaryNavigation
import io.github.peanutpersimmon.rido.ui.components.PageHeader
import io.github.peanutpersimmon.rido.ui.components.PageNavBand
import io.github.peanutpersimmon.rido.ui.components.SegmentedControlLevel
import io.github.peanutpersimmon.rido.ui.components.picker.AppCalendarDialog
import io.github.peanutpersimmon.rido.ui.components.picker.CalendarSelectionMode
import io.github.peanutpersimmon.rido.ui.theme.AppMotion
import java.time.DayOfWeek
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlinx.coroutines.flow.first

private fun Modifier.planViewSwipe(
    currentMode: PlanViewMode,
    planModes: List<PlanViewMode>,
    onMode: (PlanViewMode) -> Unit,
): Modifier = pointerInput(currentMode, planModes, onMode) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var previous = down.position
        var horizontal = 0f
        var vertical = 0f
        var multiplePointers = false
        var event = awaitPointerEvent(PointerEventPass.Initial)
        do {
            if (event.changes.count { it.pressed } > 1) multiplePointers = true
            event.changes.firstOrNull { it.id == down.id }?.let { change ->
                val delta = change.position - previous
                horizontal += delta.x
                vertical += delta.y
                previous = change.position
            }
            if (event.changes.any { it.pressed }) {
                event = awaitPointerEvent(PointerEventPass.Initial)
            }
        } while (event.changes.any { it.pressed })

        val threshold = max(viewConfiguration.touchSlop * 2f, 40.dp.toPx())
        if (!multiplePointers && abs(horizontal) >= threshold &&
            abs(horizontal) > abs(vertical) * 1.25f) {
            val current = planModes.indexOf(currentMode)
            val next = (current + if (horizontal < 0f) 1 else -1)
                .coerceIn(0, planModes.lastIndex)
            if (next != current) onMode(planModes[next])
        }
    }
}

@Composable
fun PlanScreen(
    viewModel: PlanViewModel,
    contentPadding: PaddingValues,
    onTaskClick: (Long) -> Unit,
    onEventClick: (Long) -> Unit,
    onRecordClick: (Long, SessionKind) -> Unit,
    onTaskChanged: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val planModes by viewModel.viewOrder.collectAsStateWithLifecycle()
    val d = AppTheme.dimens
    val navigationClearance = LocalFloatingNavigationClearance.current
    var datePickerOpen by remember { mutableStateOf(false) }
    var taskListFilter by rememberSaveable { mutableStateOf(TaskListFilter.UNFINISHED) }
    val toggleTask: (io.github.peanutpersimmon.rido.domain.model.Task) -> Unit = { task ->
        viewModel.toggleTaskCompletion(task, onTaskChanged)
    }
    Column(Modifier.fillMaxSize().padding(contentPadding)) {
        PlanHeader(
            state = state,
            planModes = planModes,
            onMode = viewModel::selectMode,
            onStep = viewModel::step,
            onDateClick = { datePickerOpen = true },
            taskListFilter = taskListFilter,
            onTaskListFilter = { taskListFilter = it },
        )
        Box(Modifier.weight(1f).fillMaxWidth()
            .planViewSwipe(state.mode, planModes, viewModel::selectMode)) {
            when {
                state.loading -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
                state.error != null -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(state.error.orEmpty(), color = MaterialTheme.colorScheme.error)
                }
                else -> {
                    val motionDistance = with(LocalDensity.current) { 24.dp.roundToPx() }
                    AnimatedContent(
                        targetState = state,
                        modifier = Modifier.fillMaxSize(),
                        contentKey = { it.mode },
                        transitionSpec = {
                            val forward = planModes.indexOf(targetState.mode) >
                                    planModes.indexOf(initialState.mode)
                            val direction = if (forward) 1 else -1
                            val enter = slideInHorizontally(
                                animationSpec = tween(
                                    AppMotion.standardDurationMillis,
                                    easing = AppMotion.standardEasing,
                                ),
                                initialOffsetX = { motionDistance * direction },
                            ) + fadeIn(
                                animationSpec = tween(
                                    AppMotion.standardDurationMillis,
                                    easing = AppMotion.standardEasing,
                                ),
                            )
                            val exit = slideOutHorizontally(
                                animationSpec = tween(
                                    AppMotion.standardDurationMillis,
                                    easing = AppMotion.standardEasing,
                                ),
                                targetOffsetX = { -motionDistance * direction },
                            ) + fadeOut(
                                animationSpec = tween(
                                    AppMotion.standardDurationMillis,
                                    easing = AppMotion.standardEasing,
                                ),
                            )
                            (enter togetherWith exit).using(
                                SizeTransform(
                                    clip = false,
                                    sizeAnimationSpec = { _, _ -> snap() },
                                )
                            )
                        },
                        label = "plan-view",
                    ) { contentState ->
                        val zoomDate = contentState.selectedDate.takeIf {
                            contentState.mode == PlanViewMode.TODO_DONE
                        }
                        val pageZoom = remember(contentState.mode, zoomDate) { PlanTimelineZoomState() }
                        when (contentState.mode) {
                            PlanViewMode.DAY -> RecordDay(
                                contentState.selectedDate,
                                contentState.records,
                                contentState.categoryColorKeys,
                                onRecordClick,
                                pageZoom,
                                Modifier.fillMaxSize().padding(top = d.planDayBoardGap),
                            )
                            PlanViewMode.WEEK -> RecordWeek(
                                contentState.selectedDate,
                                contentState.records,
                                contentState.categoryColorKeys,
                                onRecordClick,
                                pageZoom,
                                Modifier.fillMaxSize(),
                            )
                            PlanViewMode.MONTH -> PlanMonth(
                                contentState,
                                viewModel::selectMonthContent,
                                onDate = { date ->
                                    viewModel.selectDate(date)
                                    viewModel.selectMode(
                                        if (contentState.monthContent == MonthContent.RECORD) {
                                            PlanViewMode.DAY
                                        } else {
                                            PlanViewMode.TODO_DONE
                                        }
                                    )
                                },
                                onPointRecordClick = { id -> onRecordClick(id, SessionKind.ACTIVITY) },
                                modifier = Modifier.fillMaxSize().padding(top = d.planMonthGap, bottom = navigationClearance),
                            )
                            PlanViewMode.BOARD -> PlanBoard(
                                contentState,
                                onTaskClick,
                                onEventClick,
                                toggleTask,
                                Modifier.fillMaxSize().padding(top = d.planDayBoardGap, bottom = navigationClearance),
                            )
                            PlanViewMode.TODO_DONE -> TodoDoneDay(
                                contentState,
                                onTaskClick,
                                onEventClick,
                                onRecordClick,
                                toggleTask,
                                pageZoom,
                                Modifier.fillMaxSize().padding(top = d.planDayBoardGap),
                            )
                            PlanViewMode.TASK_LIST -> PlanTaskList(
                                contentState,
                                onTaskClick,
                                toggleTask,
                                taskListFilter,
                                Modifier.fillMaxSize().padding(top = d.planDayBoardGap),
                            )
                        }
                    }
                }
            }
        }
    }
    if (datePickerOpen) {
        AppCalendarDialog(state.selectedDate, CalendarSelectionMode.DAY,
            onDismiss = { datePickerOpen = false },
            onConfirm = { viewModel.selectDate(it); datePickerOpen = false },
            confirmDayOnClick = true)
    }
}

@Composable
private fun PlanHeader(
    state: PlanUiState,
    planModes: List<PlanViewMode>,
    onMode: (PlanViewMode) -> Unit,
    onStep: (Long) -> Unit,
    onDateClick: () -> Unit,
    taskListFilter: TaskListFilter,
    onTaskListFilter: (TaskListFilter) -> Unit,
) {
    val d = io.github.peanutpersimmon.rido.ui.theme.AppTheme.dimens
    Column(Modifier.fillMaxWidth().padding(top = d.pageHeaderTop)) {
        PageHeader {
            ScrollablePrimaryNavigation(
                planModes,
                state.mode,
                { it.name },
                { it.label },
                onMode,
            )
        }
        // The second row slot is shared with Statistics' granularity row. Task list fills it
        // with its filter chips instead of the date navigation, so the band keeps one height.
        if (state.mode != PlanViewMode.TASK_LIST) {
            CompactDateNavigation(timeTitle(state), { onStep(-1) }, { onStep(1) }, onDateClick,
                Modifier.padding(horizontal = d.pageHorizontal),
                touchTarget = io.github.peanutpersimmon.rido.ui.components.DATE_NAVIGATION_TOUCH_TARGET)
        } else {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = d.pageHorizontal),
                horizontalArrangement = Arrangement.spacedBy(d.spaceXs),
            ) {
                TaskListFilter.entries.forEach { option ->
                    CompactCapsule(
                        label = option.label,
                        selected = option == taskListFilter,
                        onClick = { onTaskListFilter(option) },
                        modifier = Modifier.weight(1f),
                        level = SegmentedControlLevel.SECONDARY,
                    )
                }
            }
        }
    }
}

private fun timeTitle(state: PlanUiState): String {
    val date = state.selectedDate
    return when (state.mode) {
        PlanViewMode.DAY, PlanViewMode.TODO_DONE ->
            date.format(DateTimeFormatter.ofPattern("MMM d · EEE", Locale.ENGLISH)).uppercase(Locale.ENGLISH)
        PlanViewMode.WEEK, PlanViewMode.BOARD -> {
            val monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val sunday = monday.plusDays(6)
            "${monday.monthValue}.${monday.dayOfMonth} – ${sunday.monthValue}.${sunday.dayOfMonth} · 第${monday.get(WeekFields.ISO.weekOfWeekBasedYear())}周"
        }
        PlanViewMode.MONTH -> "${date.year}年${date.monthValue}月"
        PlanViewMode.TASK_LIST -> ""
    }
}
