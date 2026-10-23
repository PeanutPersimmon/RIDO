package io.github.peanutpersimmon.rido.ui.feature.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.peanutpersimmon.rido.domain.model.Event
import io.github.peanutpersimmon.rido.domain.model.BoundarySource
import io.github.peanutpersimmon.rido.domain.model.Session
import io.github.peanutpersimmon.rido.domain.model.SessionTemporalType
import io.github.peanutpersimmon.rido.domain.model.SessionKind
import io.github.peanutpersimmon.rido.domain.model.Task
import io.github.peanutpersimmon.rido.domain.model.TaskStatus
import io.github.peanutpersimmon.rido.domain.service.TaskTemporalPolicy
import io.github.peanutpersimmon.rido.domain.time.epochDayMinuteToMillis
import io.github.peanutpersimmon.rido.presentation.plan.PlanUiState
import io.github.peanutpersimmon.rido.presentation.today.TaskDueDisplay
import io.github.peanutpersimmon.rido.presentation.plan.isReviewInterval
import io.github.peanutpersimmon.rido.ui.feature.today.TodayPullState
import io.github.peanutpersimmon.rido.ui.feature.today.PullSummarySurface
import io.github.peanutpersimmon.rido.ui.feature.today.TimelineSummaryTypography
import io.github.peanutpersimmon.rido.ui.components.*
import io.github.peanutpersimmon.rido.ui.components.timelinePinchZoom
import io.github.peanutpersimmon.rido.ui.components.TimelineDetailReveal
import io.github.peanutpersimmon.rido.ui.theme.resolveSleepVisualColors
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.zIndex
import io.github.peanutpersimmon.rido.ui.theme.AppTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlinx.coroutines.launch

private val zone: ZoneId get() = ZoneId.systemDefault()
internal typealias PlanTimelineZoomState = TimelineZoomState
private val gutter = 26.dp
private val bottomSpace = 8.dp
private val TimelineCardHorizontalInset = 2.dp
private val TimelineCompactTitle = androidx.compose.ui.text.TextStyle(
    fontSize = 10.sp,
    lineHeight = 13.sp,
)
private val TimelineCompactMetadata = androidx.compose.ui.text.TextStyle(
    fontSize = 9.sp,
    lineHeight = 12.sp,
)
private fun Modifier.timelineCardGeometry(): Modifier =
    padding(horizontal = TimelineCardHorizontalInset)
private fun dayStart(date: LocalDate) = date.atStartOfDay(zone).toInstant().toEpochMilli()
private fun dayEnd(date: LocalDate) = dayStart(date.plusDays(1))
internal fun reviewIntervalOnDay(session: Session, date: LocalDate, zoneId: ZoneId): Pair<Long, Long>? {
    if (!isReviewInterval(session)) return null
    val start = maxOf(session.startAtMillis, date.atStartOfDay(zoneId).toInstant().toEpochMilli())
    val end = minOf(requireNotNull(session.endAtMillis),
        date.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli())
    return (start to end).takeIf { start < end }
}
private fun Session.overlaps(date: LocalDate): Boolean = reviewIntervalOnDay(this, date, zone) != null
private fun clockTime(millis: Long, zoneId: ZoneId = zone): String =
    Instant.ofEpochMilli(millis).atZone(zoneId).toLocalTime().let { "%02d:%02d".format(it.hour, it.minute) }
internal fun reviewTimeLabel(session: Session, date: LocalDate, zoneId: ZoneId): String? {
    val (start, end) = reviewIntervalOnDay(session, date, zoneId) ?: return null
    val dayEnd = date.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
    return "${clockTime(start, zoneId)}–${if (end == dayEnd) "24:00" else clockTime(end, zoneId)}"
}

internal fun dispatchRecordClick(
    session: Session,
    onRecordClick: (Long, SessionKind) -> Unit,
) {
    onRecordClick(session.id, session.kind)
}

@Composable
internal fun RecordDay(date: LocalDate, records: List<Session>, categoryColorKeys: Map<Long, String>,
    onRecordClick: (Long, SessionKind) -> Unit,
    zoom: PlanTimelineZoomState, modifier: Modifier = Modifier) {
    val scroll = rememberScrollState()
    val navigationClearance = LocalFloatingNavigationClearance.current
    BoxWithConstraints(modifier.padding(horizontal = 8.dp)) {
        val axisHeight = (maxHeight - bottomSpace).coerceAtLeast(1.dp) * zoom.scale
        Column(Modifier.fillMaxSize().timelinePinchZoom(zoom, scroll)
            .verticalScroll(scroll).padding(bottom = navigationClearance)) {
            Row(Modifier.fillMaxWidth().height(axisHeight)) {
                HourLabels(axisHeight, Modifier.width(gutter))
                Box(Modifier.weight(1f).fillMaxHeight()
                    .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                    .clipToBounds()) {
                    AxisGrid(axisHeight, Modifier.fillMaxWidth())
                    RecordColumn(date, axisHeight, records.filter { it.overlaps(date) }, categoryColorKeys,
                        onRecordClick, false, zoom.scale, Modifier.fillMaxWidth())
                }
            }
            if (zoom.scale > 1.001f) Spacer(Modifier.height(bottomSpace))
        }
    }
}

@Composable
internal fun RecordWeek(date: LocalDate, records: List<Session>, categoryColorKeys: Map<Long, String>,
    onRecordClick: (Long, SessionKind) -> Unit,
    zoom: PlanTimelineZoomState, modifier: Modifier = Modifier) {
    val monday = date.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
    val dates = (0L..6L).map(monday::plusDays)
    val scroll = rememberScrollState()
    val navigationClearance = LocalFloatingNavigationClearance.current
    BoxWithConstraints(modifier.padding(horizontal = 8.dp)) {
        val density = LocalDensity.current
        val headerHeight = 26.dp * maxOf(1f, density.fontScale)
        val headerToGrid = AppTheme.dimens.spaceMicro

        val axisHeight = (maxHeight - headerHeight - headerToGrid - bottomSpace)
            .coerceAtLeast(1.dp) * zoom.scale
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().height(headerHeight)) {
                Spacer(Modifier.width(gutter))
                Row(Modifier.weight(1f).fillMaxHeight()) {
                    dates.forEach { day ->
                        Column(Modifier.weight(1f).fillMaxHeight(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center) {
                            Text(day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH),
                                fontSize = 10.sp, lineHeight = 11.sp, maxLines = 1,
                                color = if (day == date) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(day.dayOfMonth.toString(), fontSize = 10.sp, lineHeight = 11.sp,
                                maxLines = 1, color = if (day == date) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            Spacer(Modifier.height(headerToGrid))
            Column(Modifier.fillMaxSize().timelinePinchZoom(zoom, scroll)
                .verticalScroll(scroll).padding(bottom = navigationClearance)) {
                Row(Modifier.fillMaxWidth().height(axisHeight)) {
                    HourLabels(axisHeight, Modifier.width(gutter))
                    Box(Modifier.weight(1f).fillMaxHeight()
                        .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                    .clipToBounds()) {
                        Row(Modifier.fillMaxSize()) {
                            dates.forEachIndexed { index, day ->
                                Box(Modifier.weight(1f).fillMaxHeight().clipToBounds()) {
                                    if (index > 0) Box(
                                        Modifier
                                            .align(Alignment.CenterStart)
                                            .offset(x = (-0.25).dp)
                                            .width(0.5.dp)
                                            .fillMaxHeight()
                                            .background(MaterialTheme.colorScheme.outlineVariant),
                                    )
                                    AxisGrid(axisHeight, Modifier.fillMaxWidth())
                                    RecordColumn(day, axisHeight, records.filter { it.overlaps(day) }, categoryColorKeys,
                                        onRecordClick, true, zoom.scale, Modifier.fillMaxWidth())
                                }
                            }
                        }
                    }
                }
                if (zoom.scale > 1.001f) Spacer(Modifier.height(bottomSpace))
            }
        }
    }
}

@Composable
private fun HourLabels(axisHeight: Dp, modifier: Modifier = Modifier) {
    val hourHeight = axisHeight / 24
    Box(modifier.height(axisHeight)) {
        for (hour in 0..23) {
            Text("%02d".format(hour), Modifier.fillMaxWidth().padding(end = 3.dp)
                .offset(y = hourHeight * hour + 1.dp), textAlign = TextAlign.End,
                fontSize = 10.sp, lineHeight = 11.sp, maxLines = 1,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AxisGrid(axisHeight: Dp, modifier: Modifier = Modifier) {
    val hourHeight = axisHeight / 24
    Box(modifier.height(axisHeight)) {
        for (hour in 1..23) {
            Box(Modifier.fillMaxWidth().offset(y = hourHeight * hour)
                .height(0.5.dp).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)))
        }
    }
}

@Composable
private fun RecordColumn(date: LocalDate, axisHeight: Dp, records: List<Session>,
    categoryColorKeys: Map<Long, String>,
    onRecordClick: (Long, SessionKind) -> Unit, compact: Boolean, zoom: Float, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val start = dayStart(date)
    val end = dayEnd(date)
    val geometry = remember(start, end, axisHeight, density) {
        TimelineGeometry(start, end, with(density) { axisHeight.toPx() })
    }
    Box(modifier.height(axisHeight)) {
        records.forEach { session ->
            val colors = if (session.kind == SessionKind.ACTIVITY)
                planCategoryColors(session.categoryId, categoryColorKeys, MaterialTheme.colorScheme)
            else null
            val sleepColors = resolveSleepVisualColors(session.kind, MaterialTheme.colorScheme)
            val (visibleStart, visibleEnd) = reviewIntervalOnDay(session, date, zone) ?: return@forEach
            val position = geometry.position(visibleStart, visibleEnd)
                ?: return@forEach
            Column(Modifier.fillMaxWidth().offset(y = with(density) { position.topPx.toDp() })
                .height(with(density) { position.heightPx.toDp() })
                .timelineCardGeometry()
                .clip(RoundedCornerShape(8.dp))
                .background(sleepColors?.container ?: colors?.container ?: MaterialTheme.colorScheme.surfaceContainer)
                .clickable { dispatchRecordClick(session, onRecordClick) }
                .padding(horizontal = if (compact) 2.dp else 5.dp, vertical = 1.dp)) {
                Text(session.title, style = TimelineCompactTitle,
                    color = sleepColors?.content ?: colors?.content ?: MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Normal,
                    maxLines = if (compact) 2 else 1, overflow = TextOverflow.Ellipsis)
                if (!compact) {
                    Text(requireNotNull(reviewTimeLabel(session, date, zone)), style = TimelineCompactMetadata,
                        maxLines = 1, color = sleepColors?.secondaryContent ?: colors?.secondaryContent
                            ?: MaterialTheme.colorScheme.onSurfaceVariant)
                    TimelineDetailReveal(
                        listOfNotNull(session.location, session.note).filter { it.isNotBlank() },
                        zoom,
                        textStyle = TimelineCompactMetadata,
                        textColor = sleepColors?.secondaryContent ?: colors?.secondaryContent,
                    )
                } else {
                    TimelineDetailReveal(listOf(clockTime(visibleStart)), zoom,
                        textStyle = TimelineCompactMetadata,
                        textColor = sleepColors?.secondaryContent ?: colors?.secondaryContent)
                }
            }
        }
    }
}

internal data class TodoItem(
    val title: String,
    val atMillis: Long?,
    val endAtMillis: Long?,
    val id: Long,
    val event: Boolean,
    val completed: Boolean = false,
    val detail: String? = null,
    val note: String? = null,
    val estimate: String? = null,
    val task: Task? = null,
    val categoryId: Long? = null,
    val overdue: Boolean = false,
)

internal val TodoItem.isTimedSpan: Boolean
    get() = atMillis != null && endAtMillis != null && endAtMillis > atMillis

internal fun todoDoneTaskItem(
    task: Task,
    date: LocalDate,
    zoneId: ZoneId,
    reference: Long,
): TodoItem {
    val plannedDate = task.plannedDateEpochDay
    val startMinute = task.plannedStartMinuteOfDay
        ?.takeIf { plannedDate == date.toEpochDay() }
    val at = startMinute?.let {
        epochDayMinuteToMillis(requireNotNull(plannedDate), it, zoneId)
    }
    return TodoItem(
        title = task.title,
        atMillis = at,
        endAtMillis = null,
        id = task.id,
        event = false,
        completed = TaskTemporalPolicy.completed(task, reference),
        detail = TaskDueDisplay.value(task, date),
        overdue = task.status != TaskStatus.DONE &&
            task.status != TaskStatus.CANCELLED &&
            TaskTemporalPolicy.overdue(task, reference, zoneId),
        note = task.note,
        estimate = estimateLabel(task.estimatedDurationMinutes),
        task = task,
        categoryId = task.categoryId,
    )
}

internal fun todoDoneTaskItems(
    tasks: List<Task>,
    date: LocalDate,
    zoneId: ZoneId,
    nowMillis: Long,
): List<TodoItem> {
    val reference = TaskTemporalPolicy.reference(date, zoneId, nowMillis)
    return tasks.filter {
        TaskTemporalPolicy.currentPlan(it, date, zoneId, nowMillis)
    }.map { task ->
        todoDoneTaskItem(task, date, zoneId, reference)
    }
}

internal fun todoDoneEventItem(event: Event, zoneId: ZoneId): TodoItem {
    val at = event.startMinuteOfDay?.let {
        LocalDate.ofEpochDay(event.dateEpochDay).atStartOfDay(zoneId)
            .plusMinutes(it.toLong()).toInstant().toEpochMilli()
    }
    val end = event.endMinuteOfDay?.let {
        LocalDate.ofEpochDay(event.endDateEpochDay ?: event.dateEpochDay)
            .atStartOfDay(zoneId).plusMinutes(it.toLong()).toInstant().toEpochMilli()
    }
    return TodoItem(event.title, at, end, event.id, true,
        detail = event.location, note = event.note, categoryId = event.categoryId)
}

internal fun todoDoneDetails(item: TodoItem, tasks: List<Task>): List<String> {
    val parent = item.task?.parentTaskId?.let { parentId ->
        tasks.firstOrNull { it.id == parentId }?.title?.let { "父任务：$it" }
    }
    return listOfNotNull(parent, item.task?.location, item.detail, item.note).filter(String::isNotBlank)
}

internal fun todoDoneRecordDetails(session: Session, tasks: List<Task>): List<String> {
    val related = session.taskId?.let { taskId ->
        tasks.firstOrNull { it.id == taskId }?.title?.let { "关联任务：$it" }
    }
    return listOfNotNull(session.location, related, session.note).filter(String::isNotBlank)
}

internal fun visibleTodoDoneRecords(
    records: List<Session>,
    axisStart: Long,
    axisEnd: Long,
): List<Session> = records.filter { session ->
    session.kind in setOf(SessionKind.ACTIVITY, SessionKind.NAP) && when (session.temporalType) {
        SessionTemporalType.POINT -> session.startAtMillis >= axisStart && session.startAtMillis < axisEnd
        SessionTemporalType.INTERVAL -> session.endAtMillis?.let { end ->
            end > axisStart && session.startAtMillis < axisEnd
        } == true
    }
}

internal fun todoDoneCompletionFacts(
    tasks: List<Task>, records: List<Session>, start: Long, end: Long,
): List<Task> = TaskTemporalPolicy.completionPoints(tasks, records, start, end).filter { task ->
    val completedAt = requireNotNull(task.completedAtMillis)
    records.none { record ->
        record.kind == SessionKind.ACTIVITY && record.temporalType == SessionTemporalType.POINT &&
            record.taskId == task.id && record.startAtMillis == completedAt
    }
}

internal sealed interface TodoDoneLaneValue {
    data class Todo(val item: TodoItem) : TodoDoneLaneValue
    data class Record(val session: Session) : TodoDoneLaneValue
    data class Completion(val task: Task) : TodoDoneLaneValue
}

internal fun todoDoneRecordInput(
    session: Session,
    axisStart: Long,
    axisEnd: Long,
    contentHeight: Float,
    pointAnchorLead: Float,
): ElasticTimelineItem<TodoDoneLaneValue>? {
    if (visibleTodoDoneRecords(listOf(session), axisStart, axisEnd).isEmpty()) return null
    val point = session.temporalType == SessionTemporalType.POINT
    return ElasticTimelineItem(
        value = TodoDoneLaneValue.Record(session),
        start = if (point) session.startAtMillis else session.startAtMillis.coerceIn(axisStart, axisEnd),
        end = if (point) null else requireNotNull(session.endAtMillis).coerceIn(axisStart, axisEnd),
        side = false,
        contentHeight = contentHeight,
        stableOrder = session.id,
        anchorLead = if (point) pointAnchorLead else 0f,
    )
}

private fun estimateLabel(minutes: Int?): String? = minutes?.takeIf { it > 0 }?.let {
    "预计 " + when {
        it < 60 -> "${it}m"
        it % 60 == 0 -> "${it / 60}h"
        else -> "${it / 60}h${it % 60}m"
    }
}

@Composable
internal fun TodoDoneDay(
    state: PlanUiState,
    onTaskClick: (Long) -> Unit,
    onEventClick: (Long) -> Unit,
    onRecordClick: (Long, SessionKind) -> Unit,
    onToggleTask: (Task) -> Unit,
    zoom: PlanTimelineZoomState,
    modifier: Modifier = Modifier,
) {
    val date = state.selectedDate
    val todos = todoDoneTaskItems(state.tasks, date, zone, state.nowMillis) +
        state.events.filter { it.dateEpochDay == date.toEpochDay() }.map { todoDoneEventItem(it, zone) }
    val displayWindow = requireNotNull(state.awakeDisplayWindow) {
        "TodoDone requires an AwakeDisplayWindow."
    }
    val axisStart = displayWindow.displayInterval.startMillis
    val axisEnd = displayWindow.displayInterval.endMillis
    val records = visibleTodoDoneRecords(state.records, axisStart, axisEnd)
    val completions = todoDoneCompletionFacts(
        state.tasks,
        records,
        axisStart,
        axisEnd,
    ).filter {
        it.completedAtMillis?.let { at ->
            at >= axisStart && at < axisEnd && Instant.ofEpochMilli(at).atZone(zone).toLocalDate() == date
        } == true
    }
    val untimed = todos.filter { it.atMillis == null }
    val scope = rememberCoroutineScope()
    val timelineScroll = rememberScrollState()
    val navigationClearance = LocalFloatingNavigationClearance.current

    BoxWithConstraints(modifier.padding(horizontal = AppTheme.dimens.pageHorizontal)) {
        val density = LocalDensity.current
        val maxReminderHeight = maxHeight * 0.35f
        val range = with(density) { maxReminderHeight.toPx() }
        val reminder = remember(date, range) { TodayPullState(range) }
        Box(Modifier.fillMaxSize()) {
            BoxWithConstraints(Modifier.fillMaxSize().clipToBounds()) {
                val viewportHeightPx = with(density) { maxHeight.toPx() }
                val topBoundary = timelineTopBoundary(untimed.isNotEmpty())
                val bottomBoundary = AppTheme.dimens.timelineBoundarySpace
                val boundaryPx = with(density) { (topBoundary + bottomBoundary).toPx() }
                val geometry = remember(
                    axisStart,
                    axisEnd,
                    maxHeight,
                    zoom.scale,
                    density,
                    boundaryPx,
                ) {
                    val oneXHeight = (viewportHeightPx - boundaryPx).coerceAtLeast(1f)
                    TimelineGeometry(
                        axisStart,
                        axisEnd,
                        oneXHeight * zoom.scale,
                    )
                }
                val boundary = topBoundary
                val axisX = maxWidth / 2
                val sideGap = AppTheme.dimens.timelineAxisGutter
                val sideWidth = (axisX - sideGap).coerceAtLeast(0.dp)
                val textMeasurer = rememberTextMeasurer()
                val contentMeasurer = rememberTimelineContentMeasurer()
                val fullItemWidth = (sideWidth - AppTheme.dimens.spaceXs).coerceAtLeast(0.dp)
                val endpointTimes = buildList {
                    add(axisStart)
                    add(axisEnd)
                    todos.forEach { item ->
                        item.atMillis?.let(::add)
                        item.endAtMillis?.let(::add)
                    }
                    records.forEach { record ->
                        add(record.startAtMillis)
                        record.endAtMillis?.let(::add)
                    }
                    completions.mapNotNullTo(this) { it.completedAtMillis }
                }.filter { it in axisStart..axisEnd }
                val timedBlockInsetPx = with(density) { AppTheme.dimens.spaceXs.toPx() }
                val boundaryOffsetPx = with(density) { boundary.toPx() }
                val labelSpacingPx = with(density) {
                    timelineTimeLabelHeight().toPx() + AppTheme.dimens.spaceXs.toPx()
                }
                val itemWidthPx = with(density) { fullItemWidth.toPx().toInt().coerceAtLeast(1) }
                val pointAnchorLead = maxOf(
                    textMeasurer.measure("Ag", AppTheme.type.timelineTitle, maxLines = 1).size.height,
                    textMeasurer.measure("2h", AppTheme.type.timelineDuration, maxLines = 1).size.height,
                ) / 2f
                val values: List<TodoDoneLaneValue> =
                    todos.map(TodoDoneLaneValue::Todo) +
                    records.map(TodoDoneLaneValue::Record) +
                    completions.map(TodoDoneLaneValue::Completion)
                // Neither nowMillis nor zoom.scale is a measurement/search key.
                val measuredBoundsHeights = remember(date, state.tasks, state.events,
                    state.records, todos.map { it.id to it.detail }, records.map { it.id },
                    completions.map { it.id },
                    itemWidthPx, contentMeasurer, textMeasurer) {
                    values.map { value ->
                        val title: String
                        val duration: String?
                        val details: List<String>
                        val point: Boolean
                        when (value) {
                            is TodoDoneLaneValue.Todo -> {
                                val item = value.item
                                title = if (item.overdue) "! ${item.title}" else item.title
                                duration = if (item.isTimedSpan) formatTimelineDuration(
                                    requireNotNull(item.atMillis), requireNotNull(item.endAtMillis))
                                else item.estimate
                                details = todoDoneDetails(item, state.tasks)
                                point = !item.isTimedSpan
                            }
                            is TodoDoneLaneValue.Record -> {
                                val session = value.session
                                title = session.title
                                duration = if (session.temporalType == SessionTemporalType.POINT) null
                                    else formatTimelineDuration(session.startAtMillis,
                                        requireNotNull(session.endAtMillis))
                                details = todoDoneRecordDetails(session, state.tasks)
                                point = session.temporalType == SessionTemporalType.POINT
                            }
                            is TodoDoneLaneValue.Completion -> {
                                title = "${value.task.title}完成"
                                duration = null
                                details = emptyList()
                                point = true
                            }
                        }
                        contentMeasurer.height(title, duration, emptyList(), itemWidthPx,
                            0f, textMeasurer, point) to
                        contentMeasurer.height(title, duration, details, itemWidthPx,
                            1f, textMeasurer, point)
                    }
                }
                val overviewHeights = values.indices.associate { index ->
                    values[index] to measuredBoundsHeights[index].first
                }
                val fullHeights = values.indices.associate { index ->
                    values[index] to measuredBoundsHeights[index].second
                }
                val heights = values.associateWith { value ->
                    val overview = overviewHeights.getValue(value)
                    overview + (fullHeights.getValue(value) - overview) * zoom.progress
                }
                val plannedInputs: List<ElasticTimelineItem<TodoDoneLaneValue>> = buildList {
                    todos.forEach { item ->
                        val at = item.atMillis ?: return@forEach
                        if (at !in axisStart..axisEnd) return@forEach
                        val end = item.endAtMillis?.takeIf { item.isTimedSpan }
                        val value = TodoDoneLaneValue.Todo(item)
                        if (end != null) {
                            geometry.position(at, end)?.let { position ->
                                add(
                                    ElasticTimelineItem(
                                        value = value,
                                        start = at.coerceIn(axisStart, axisEnd),
                                        end = end.coerceIn(axisStart, axisEnd),
                                        side = true,
                                        contentHeight = heights.getValue(value),
                                        stableOrder = item.id,
                                    ),
                                )
                            }
                        } else {
                            add(
                                ElasticTimelineItem(
                                    value = value,
                                    start = at,
                                    end = null,
                                    side = true,
                                    contentHeight = heights.getValue(value),
                                    stableOrder = item.id,
                                    anchorLead = pointAnchorLead,
                                ),
                            )
                        }
                    }
                }
                val actualInputs: List<ElasticTimelineItem<TodoDoneLaneValue>> = buildList {
                    records.forEach { session ->
                        val value = TodoDoneLaneValue.Record(session)
                        todoDoneRecordInput(session, axisStart, axisEnd,
                            heights.getValue(value), pointAnchorLead)?.let(::add)
                    }
                    completions.forEach { task ->
                        val at = task.completedAtMillis ?: return@forEach
                        val value = TodoDoneLaneValue.Completion(task)
                        add(
                            ElasticTimelineItem(
                                value = value,
                                start = at,
                                end = null,
                                side = false,
                                contentHeight = heights.getValue(value),
                                stableOrder = task.id,
                                anchorLead = pointAnchorLead,
                            ),
                        )
                    }
                }
                val itemGapPx = with(density) { AppTheme.dimens.timelineItemGap.toPx() }
                val bottomPaddingPx = with(density) { bottomBoundary.toPx() }
                val stableAxisEnd = axisEnd.takeUnless {
                    displayWindow.endSource == BoundarySource.NOW
                }
                val bounds = remember(state.selectedDate, state.tasks, state.events,
                    state.records, todos.map { it.id }, records.map { it.id },
                    completions.map { it.id },
                    axisStart, stableAxisEnd,
                    viewportHeightPx, itemWidthPx, boundaryOffsetPx, bottomPaddingPx,
                    itemGapPx, labelSpacingPx, timedBlockInsetPx, pointAnchorLead, density) {
                    calculateElasticTimelineZoomBounds(
                        items = (plannedInputs + actualInputs).map { item ->
                            item.copy(contentHeight = overviewHeights.getValue(item.value),
                                spanHeightLimit = Float.POSITIVE_INFINITY)
                        },
                        fullDetailHeights = fullHeights,
                        axisStart = axisStart, axisEnd = axisEnd,
                        viewportHeight = viewportHeightPx,
                        topBoundary = boundaryOffsetPx, bottomBoundary = bottomPaddingPx,
                        itemGap = itemGapPx, labelSpacing = labelSpacingPx,
                        blockInset = timedBlockInsetPx,
                    )
                }
                androidx.compose.runtime.LaunchedEffect(zoom, bounds) {
                    zoom.updateAdaptiveBounds(bounds.minZoom, bounds.maxZoom)
                }
                val liveInputs = (plannedInputs + actualInputs).map { item ->
                    val end = item.end
                    if (end == null) item else item.copy(spanHeightLimit = elasticIntervalHeightLimit(
                        overviewHeights.getValue(item.value), end - item.start,
                        density.density, (viewportHeightPx - boundaryPx).coerceAtLeast(1f),
                        axisEnd - axisStart, bounds.minZoom, timedBlockInsetPx,
                    ))
                }
                val resolved = arrangeElasticTimeline(
                    items = liveInputs,
                    axisStart = axisStart,
                    axisEnd = axisEnd,
                    trueY = { boundaryOffsetPx + geometry.timeToY(it) },
                    itemGap = itemGapPx,
                    labelSpacing = labelSpacingPx,
                    blockInset = timedBlockInsetPx,
                    bottomPadding = bottomPaddingPx,
                )
                val plannedPlacements = resolved.placements.filter { it.value is TodoDoneLaneValue.Todo }
                val actualPlacements = resolved.placements.filter { it.value !is TodoDoneLaneValue.Todo }
                fun y(time: Long) = with(density) { resolved.timeY(time).toDp() }
                val shown = selectTimelineMinuteLabelTimes(
                    endpointTimes, axisStart, axisEnd, zone, resolved::timeY, labelSpacingPx,
                    state.nowMillis.takeIf { displayWindow.endSource == BoundarySource.NOW },
                )
                val requiredHeightPx = resolved.requiredHeight
                val contentHeight = with(density) { requiredHeightPx.toDp() }
                val scrollEnabled = requiredHeightPx + with(density) { navigationClearance.toPx() } > viewportHeightPx

                androidx.compose.runtime.LaunchedEffect(scrollEnabled) {
                    if (!scrollEnabled && timelineScroll.value != 0) {
                        timelineScroll.scrollTo(0)
                    }
                }

                Column(
                    Modifier
                        .fillMaxSize()
                        .timelinePinchZoom(zoom, timelineScroll)
                        .verticalScroll(
                            timelineScroll,
                            enabled = scrollEnabled,
                        )
                        .padding(bottom = navigationClearance),
                ) {
                    Box(Modifier.fillMaxWidth().height(contentHeight)) {
                        CentralTimelineAxis(
                            axisX,
                            y(axisStart),
                            y(axisEnd) - y(axisStart),
                            Modifier.zIndex(TimelineAxisLayer),
                        )
                        if (
                            date == Instant.ofEpochMilli(state.nowMillis)
                                .atZone(zone)
                                .toLocalDate() &&
                            state.nowMillis in axisStart..axisEnd
                        ) {
                            CurrentTimeLine(
                                axisX,
                                y(state.nowMillis),
                                Modifier.zIndex(TimelineCardLayer - 0.1f),
                            )
                            CurrentTimeDot(
                                axisX,
                                y(state.nowMillis),
                                Modifier.zIndex(TimelineNowLayer),
                            )
                        }
                        endpointTimes.distinct().forEach { time ->
                            CentralTimelineEndpoint(
                                axisX,
                                y(time),
                                Modifier.zIndex(TimelineAxisLayer),
                            )
                            CentralTimelineLabel(
                                clockTime(time),
                                axisX,
                                y(time),
                                Modifier.zIndex(TimelineAxisLayer),
                                show = time in shown,
                            )
                        }

                        plannedPlacements.forEach { placement ->
                            val top = with(density) { placement.contentTop.toDp() }
                            val height = with(density) { placement.contentHeight.toDp() }
                            val itemWidth = fullItemWidth
                            val itemX = AppTheme.dimens.spaceXs
                            val item = (placement.value as TodoDoneLaneValue.Todo).item
                            val categoryColors = planCategoryColors(item.categoryId,
                                state.categoryColorKeys, MaterialTheme.colorScheme)
                            val at = requireNotNull(item.atMillis)
                            val end = item.endAtMillis?.takeIf { item.isTimedSpan }
                            if (end != null) {
                                TimelineBlockPlacement(
                                    top = top,
                                    visualHeight = height,
                                    onClick = {
                                        if (item.event) onEventClick(item.id)
                                        else onTaskClick(item.id)
                                    },
                                    modifier = Modifier
                                        .offset(x = itemX)
                                        .width(itemWidth)
                                        .zIndex(TimelineCardLayer),
                                    expandTouchTarget = false,
                                ) { visual ->
                                    TimelineTimeBlock(
                                        item.title,
                                        formatTimelineDuration(at, end),
                                        todoDoneDetails(item, state.tasks),
                                        TimelineVisualKind.PLANNED,
                                        visual,
                                        detailProgress = zoom.progress,
                                        backgroundColor = categoryColors?.container,
                                        titleColor = categoryColors?.content,
                                        metadataColor = categoryColors?.secondaryContent,
                                    )
                                }
                            } else {
                                TimelinePointItem(
                                    title = item.title,
                                    trailing = item.estimate,
                                    metadata = todoDoneDetails(item, state.tasks),
                                    zoom = zoom.scale,
                                    onClick = {
                                        if (item.event) onEventClick(item.id)
                                        else onTaskClick(item.id)
                                    },
                                    modifier = Modifier.offset(x = itemX,
                                        y = top)
                                        .width(itemWidth).zIndex(TimelinePointLayer),
                                    visualHeight = height,
                                    side = TimelinePointSide.PLANNED,
                                    overdue = item.overdue,
                                    prioritizeTitle = true,
                                    showMarker = categoryColors != null,
                                    markerColor = categoryColors?.base ?: MaterialTheme.colorScheme.onSurfaceVariant,
                                    metadataProgress = zoom.progress,
                                )
                            }
                        }

                        actualPlacements.forEach { placement ->
                            val top = with(density) { placement.contentTop.toDp() }
                            val height = with(density) { placement.contentHeight.toDp() }
                            val itemWidth = fullItemWidth
                            val itemX = axisX + sideGap
                            when (val value = placement.value) {
                                is TodoDoneLaneValue.Record -> {
                                    val session = value.session
                                    val categoryColors = if (session.kind == SessionKind.ACTIVITY)
                                        planCategoryColors(session.categoryId, state.categoryColorKeys,
                                            MaterialTheme.colorScheme) else null
                                    if (session.temporalType == SessionTemporalType.POINT) {
                                        TimelinePointItem(
                                            title = session.title,
                                            trailing = null,
                                            metadata = todoDoneRecordDetails(session, state.tasks),
                                            zoom = zoom.scale,
                                            onClick = { dispatchRecordClick(session, onRecordClick) },
                                            modifier = Modifier.offset(x = itemX, y = top)
                                                .width(itemWidth).zIndex(TimelinePointLayer),
                                            visualHeight = height,
                                            side = TimelinePointSide.ACTUAL,
                                            prioritizeTitle = true,
                                            showMarker = categoryColors != null,
                                            markerColor = categoryColors?.base ?: MaterialTheme.colorScheme.onSurfaceVariant,
                                            metadataProgress = zoom.progress,
                                        )
                                    } else {
                                        val end = requireNotNull(session.endAtMillis)
                                        TimelineBlockPlacement(
                                            top = top,
                                            visualHeight = height,
                                            onClick = { dispatchRecordClick(session, onRecordClick) },
                                            modifier = Modifier
                                                .offset(x = itemX)
                                                .width(itemWidth)
                                                .zIndex(TimelineCardLayer),
                                            expandTouchTarget = false,
                                        ) { visual ->
                                            TimelineTimeBlock(
                                                if (session.kind == SessionKind.NAP) "小憩" else session.title,
                                                formatTimelineDuration(session.startAtMillis, end),
                                                todoDoneRecordDetails(session, state.tasks),
                                                if (session.kind == SessionKind.NAP) TimelineVisualKind.NAP else TimelineVisualKind.ACTUAL,
                                                visual,
                                                detailProgress = zoom.progress,
                                                backgroundColor = categoryColors?.container,
                                                accentColor = categoryColors?.base,
                                                titleColor = categoryColors?.content,
                                                metadataColor = categoryColors?.secondaryContent,
                                            )
                                        }
                                    }
                                }
                                is TodoDoneLaneValue.Completion -> {
                                    val task = value.task
                                    TimelinePointItem(
                                        title = task.title,
                                        trailing = null,
                                        metadata = emptyList(),
                                        zoom = zoom.scale,
                                        onClick = { onTaskClick(task.id) },
                                        modifier = Modifier.offset(x = itemX,
                                            y = top)
                                            .width(itemWidth).zIndex(TimelinePointLayer),
                                        visualHeight = height,
                                        completed = true,
                                        side = TimelinePointSide.ACTUAL,
                                        prioritizeTitle = true,
                                        showMarker = false,
                                    )
                                }
                                else -> Unit
                            }
                        }
                    }
                }
            }
            if (untimed.isNotEmpty()) {
                Column(Modifier.align(Alignment.TopCenter).zIndex(1f)) {
                    PullSummarySurface(
                        summaryText = "${date.monthValue} 月 ${date.dayOfMonth} 日 · 无时间计划 ${untimed.size} 项",
                        state = reminder,
                        maxExpandedBodyHeight = maxReminderHeight,
                        onToggle = {
                            scope.launch {
                                reminder.settle(expand = reminder.fraction < 0.5f)
                            }
                        },
                    ) {
                        untimed.forEach { item ->
                            val categoryColors = planCategoryColors(item.categoryId,
                                state.categoryColorKeys, MaterialTheme.colorScheme)
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (item.event) onEventClick(item.id)
                                        else onTaskClick(item.id)
                                    },
                                horizontalArrangement = Arrangement.spacedBy(
                                    AppTheme.dimens.spaceS,
                                ),
                            ) {
                                if (categoryColors != null) Box(Modifier.size(5.dp)
                                    .background(categoryColors.base, androidx.compose.foundation.shape.CircleShape))
                                Text(
                                    planItemTitle(item),
                                    Modifier.weight(1f),
                                    style = TimelineSummaryTypography.item(),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                item.detail?.let {
                                    Text(
                                        it,
                                        style = TimelineSummaryTypography.metadata(),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                item.task?.let { task ->
                                    TaskCompletionAction(task, onToggleTask)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
private fun planItemTitle(item: TodoItem) = androidx.compose.ui.text.buildAnnotatedString {
    pushStyle(androidx.compose.ui.text.SpanStyle(textDecoration =
        if (item.completed) androidx.compose.ui.text.style.TextDecoration.LineThrough else null))
    append(item.title)
    pop()
}

