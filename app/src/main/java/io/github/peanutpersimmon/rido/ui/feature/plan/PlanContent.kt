package io.github.peanutpersimmon.rido.ui.feature.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.peanutpersimmon.rido.domain.model.Event
import io.github.peanutpersimmon.rido.domain.model.Session
import io.github.peanutpersimmon.rido.domain.model.SessionTemporalType
import io.github.peanutpersimmon.rido.domain.model.Task
import io.github.peanutpersimmon.rido.domain.model.TaskStatus
import io.github.peanutpersimmon.rido.domain.service.TaskTemporalPolicy
import io.github.peanutpersimmon.rido.presentation.plan.MonthContent
import io.github.peanutpersimmon.rido.presentation.plan.PlanUiState
import io.github.peanutpersimmon.rido.ui.theme.AppTheme
import io.github.peanutpersimmon.rido.ui.components.LocalFloatingNavigationClearance
import io.github.peanutpersimmon.rido.ui.theme.CategoryColors
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

internal fun Task.appearsOn(date: LocalDate): Boolean =
    plannedDateEpochDay == date.toEpochDay() || dueDateEpochDay == date.toEpochDay()

internal fun Event.appearsOn(date: LocalDate): Boolean = date.toEpochDay() == dateEpochDay

internal fun monthRecordsOnDate(records: List<Session>, date: LocalDate, zone: ZoneId): List<Session> {
    val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
    val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    return records.filter { record ->
        when (record.temporalType) {
            SessionTemporalType.POINT -> record.startAtMillis >= start && record.startAtMillis < end
            SessionTemporalType.INTERVAL -> record.endAtMillis?.let { it > start && record.startAtMillis < end } == true
        }
    }
}

internal data class MonthSummaryItem(
    val title: AnnotatedString,
    val isEvent: Boolean = false,
    val pointRecordId: Long? = null,
    val categoryId: Long? = null,
)

internal fun monthSummaryItems(state: PlanUiState, date: LocalDate, zone: ZoneId): List<MonthSummaryItem> =
    if (state.monthContent == MonthContent.RECORD) {
        monthRecordsOnDate(state.records, date, zone).map { record ->
            MonthSummaryItem(
                title = AnnotatedString(record.title),
                pointRecordId = record.id.takeIf { record.temporalType == SessionTemporalType.POINT },
                categoryId = record.categoryId,
            )
        }
    } else {
        val reference = TaskTemporalPolicy.reference(date, zone, state.nowMillis)
        state.events.filter { it.appearsOn(date) }.map { event ->
            MonthSummaryItem(AnnotatedString(event.title), isEvent = true, categoryId = event.categoryId)
        } + state.tasks.filter { it.appearsOn(date) && TaskTemporalPolicy.exists(it, reference) }
            .map { task -> MonthSummaryItem(taskTitleText(task, reference), categoryId = task.categoryId) }
    }

private fun minuteLabel(minute: Int?): String =
    minute?.let { "%02d:%02d".format(it / 60, it % 60) }.orEmpty()

@Composable
internal fun PlanBoard(
    state: PlanUiState,
    onTaskClick: (Long) -> Unit,
    onEventClick: (Long) -> Unit,
    onToggleTask: (Task) -> Unit,
    modifier: Modifier = Modifier,
) {
    val monday = state.selectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val dates = listOf<LocalDate?>(null) + (0L..6L).map(monday::plusDays)
    BoxWithConstraints(modifier.padding(horizontal = 16.dp)) {
        val rowHeight = (maxHeight - 1.5.dp) / 4
        val tableShape = RoundedCornerShape(10.dp)
        Column(Modifier.fillMaxSize().clip(tableShape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, tableShape)) {
            dates.chunked(2).forEachIndexed { rowIndex, row ->
                Row(Modifier.fillMaxWidth().height(rowHeight)) {
                    row.forEachIndexed { columnIndex, date ->
                        val reference = TaskTemporalPolicy.reference(
                            date ?: state.selectedDate, ZoneId.systemDefault(), state.nowMillis)
                        val tasks = state.tasks.filter { task ->
                            if (date == null) {
                                task.plannedDateEpochDay == null && task.dueDateEpochDay == null &&
                                        TaskTemporalPolicy.exists(task, reference)
                            } else task.appearsOn(date) && TaskTemporalPolicy.exists(task, reference)
                        }
                        val events = if (date == null) emptyList()
                        else state.events.filter { it.appearsOn(date) }
                        Column(Modifier.weight(1f).fillMaxHeight()
                            .padding(start = 7.dp, end = 6.dp, top = 4.dp, bottom = 3.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(if (date == null) "No Date" else
                                    date.dayOfWeek.getDisplayName(
                                        java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH),
                                    fontSize = 10.sp, lineHeight = 11.sp, fontWeight = FontWeight.Normal,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (date != null) Text(" · ${date.monthValue}/${date.dayOfMonth}",
                                    fontSize = 10.sp, lineHeight = 11.sp, fontWeight = FontWeight.Normal,
                                    color = if (date == state.selectedDate)
                                        MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(Modifier.height(2.dp))
                            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                                events.forEach { event ->
                                    BoardItem(minuteLabel(event.startMinuteOfDay), AnnotatedString(event.title),
                                        true, planCategoryColors(event.categoryId, state.categoryColorKeys,
                                            MaterialTheme.colorScheme)) { onEventClick(event.id) }
                                }
                                tasks.forEach { task ->
                                    val time = if (date != null &&
                                        task.plannedDateEpochDay == date.toEpochDay()) {
                                        task.plannedStartMinuteOfDay
                                    } else if (date != null &&
                                        task.dueDateEpochDay == date.toEpochDay()) {
                                        task.dueMinuteOfDay
                                    } else null
                                    BoardItem(minuteLabel(time), taskTitleText(task, reference), false,
                                        categoryColors = planCategoryColors(task.categoryId, state.categoryColorKeys,
                                            MaterialTheme.colorScheme),
                                        task = task, onToggleTask = onToggleTask) {
                                        onTaskClick(task.id)
                                    }
                                }
                            }
                        }
                        if (columnIndex == 0) Box(Modifier.width(0.5.dp).fillMaxHeight()
                            .background(MaterialTheme.colorScheme.outlineVariant))
                    }
                }
                if (rowIndex < 3) Box(Modifier.fillMaxWidth().height(0.5.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant))
            }
        }
    }
}

@Composable
private fun BoardItem(time: String, title: AnnotatedString, event: Boolean,
                      categoryColors: CategoryColors? = null,
                      task: Task? = null, onToggleTask: (Task) -> Unit = {}, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth()
        .then(if (categoryColors != null) Modifier.background(categoryColors.container,
            RoundedCornerShape(4.dp)) else Modifier)
        .clickable(onClick = onClick).padding(vertical = 1.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Top) {
        if (time.isNotBlank()) Text(time, fontSize = 9.sp, lineHeight = 11.sp,
            color = categoryColors?.secondaryContent ?: MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1)
        Text(title, Modifier.weight(1f), fontSize = 10.sp, lineHeight = 11.sp,
            fontWeight = FontWeight.Normal,
            color = categoryColors?.content ?: MaterialTheme.colorScheme.onSurface,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
        task?.let { TaskCompletionAction(it, onToggleTask) }
    }
}

@Composable
internal fun TaskCompletionAction(task: Task, onToggleTask: (Task) -> Unit,
                                  modifier: Modifier = Modifier) {
    val completed = task.status == TaskStatus.DONE
    Box(modifier.size(18.dp).toggleable(
        value = completed,
        role = Role.Checkbox,
        onValueChange = { onToggleTask(task) },
    ).semantics {
        contentDescription = if (completed) "取消完成" else "完成任务"
    }, contentAlignment = Alignment.Center) {
        Icon(
            if (completed) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
            contentDescription = null,
            modifier = Modifier.size(15.dp),
            tint = if (completed) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

internal enum class TaskListFilter(val label: String) {
    UNFINISHED("未完成"), DONE("已完成"), ALL("全部");

    fun includes(task: Task): Boolean = when (this) {
        UNFINISHED -> task.status != TaskStatus.DONE && task.status != TaskStatus.CANCELLED
        DONE -> task.status == TaskStatus.DONE
        ALL -> task.status != TaskStatus.CANCELLED
    }
}

internal data class TaskTreeNode(
    val task: Task,
    val depth: Int,
    val childCount: Int,
    val parentContextTitle: String? = null,
)

internal fun taskGroups(tasks: List<Task>, filter: TaskListFilter): List<List<TaskTreeNode>> {
    val allById = tasks.associateBy(Task::id)
    val sorted = tasks.filter(filter::includes).sortedWith(compareBy<Task> {
        it.status == TaskStatus.DONE || it.status == TaskStatus.CANCELLED
    }.thenBy { it.plannedDateEpochDay ?: it.dueDateEpochDay ?: Long.MAX_VALUE }.thenBy { it.id })
    val ids = sorted.mapTo(mutableSetOf()) { it.id }
    val children = sorted.filter {
        it.parentTaskId?.let(ids::contains) == true && it.parentTaskId != it.id
    }.groupBy { requireNotNull(it.parentTaskId) }
    val roots = sorted.filter {
        it.parentTaskId == null || !ids.contains(it.parentTaskId) ||
                it.parentTaskId == it.id
    }
    val visited = mutableSetOf<Long>()
    fun group(root: Task): List<TaskTreeNode> {
        val result = mutableListOf<TaskTreeNode>()
        fun visit(task: Task, depth: Int) {
            if (!visited.add(task.id)) return
            val direct = children[task.id].orEmpty()
            val parentContext = if (depth == 0 && task.parentTaskId !in ids) {
                task.parentTaskId?.let(allById::get)?.title
            } else null
            result += TaskTreeNode(task, depth, direct.size, parentContext)
            direct.forEach { visit(it, depth + 1) }
        }
        visit(root, 0)
        return result
    }
    val groups = roots.map(::group).toMutableList()
    sorted.filter { it.id !in visited }.forEach { groups.add(group(it)) }
    return groups.filter { it.isNotEmpty() }
}

@Composable
internal fun PlanTaskList(
    state: PlanUiState,
    onTaskClick: (Long) -> Unit,
    onToggleTask: (Task) -> Unit,
    filter: TaskListFilter,
    modifier: Modifier = Modifier,
) {
    val groups = taskGroups(state.tasks, filter)
    Column(modifier.verticalScroll(rememberScrollState())
        .padding(bottom = LocalFloatingNavigationClearance.current)
        .padding(horizontal = 16.dp)) {
        if (groups.isEmpty()) Text("暂无任务", style = AppTheme.type.body)
        groups.forEach { group ->
            Column(Modifier.fillMaxWidth().padding(bottom = 9.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)) {
                group.forEach { node ->
                    val task = node.task
                    val categoryColors = planCategoryColors(task.categoryId, state.categoryColorKeys,
                        MaterialTheme.colorScheme)
                    val date = task.dueDateEpochDay ?: task.plannedDateEpochDay
                    val dateText = date?.let { shortTaskDate(LocalDate.ofEpochDay(it), LocalDate.now()) }
                    val metadata = buildList {
                        node.parentContextTitle?.let { add("来自父任务：$it") }
                        if (node.childCount > 0) add("${node.childCount} 个子任务")
                        task.location?.takeIf(String::isNotBlank)?.let(::add)
                        task.estimatedDurationMinutes?.let { add(durationLabel(it)) }
                        if (TaskTemporalPolicy.overdue(task, state.nowMillis, ZoneId.systemDefault())) {
                            TaskTemporalPolicy.dueLabel(task, state.nowMillis,
                                ZoneId.systemDefault())?.let { label -> add(label) }
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(start = (node.depth * 14).dp)
                        .height(IntrinsicSize.Min)) {
                        if (node.depth > 0) {
                            Box(Modifier.width(1.dp).fillMaxHeight()
                                .background(MaterialTheme.colorScheme.outlineVariant))
                            Spacer(Modifier.width(5.dp))
                        }
                        Column(Modifier.weight(1f).clip(RoundedCornerShape(9.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerLow)
                            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(9.dp))
                            .clickable { onTaskClick(task.id) }
                            .padding(horizontal = 10.dp, vertical = 7.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (categoryColors != null) Box(Modifier.size(5.dp)
                                    .background(categoryColors.base, androidx.compose.foundation.shape.CircleShape))
                                Text(taskTitleText(task, state.nowMillis), Modifier.weight(1f),
                                    fontSize = if (node.depth == 0) 13.sp else 12.sp,
                                    lineHeight = 15.sp,
                                    fontWeight = if (node.depth == 0) FontWeight.Medium
                                    else FontWeight.Normal,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                dateText?.let {
                                    Text(it, fontSize = 12.sp, lineHeight = 15.sp,
                                        fontWeight = FontWeight.Normal,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1)
                                }
                                TaskCompletionAction(task, onToggleTask)
                            }
                            if (metadata.isNotEmpty()) {
                                Text(metadata.joinToString(" · "), fontSize = 10.sp, lineHeight = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun shortTaskDate(date: LocalDate, reference: LocalDate): String =
    if (date.year == reference.year) "${date.monthValue}/${date.dayOfMonth}"
    else "${date.year}/${date.monthValue}/${date.dayOfMonth}"

private fun durationLabel(minutes: Int): String = when {
    minutes < 60 -> "预计 ${minutes}m"
    minutes % 60 == 0 -> "预计 ${minutes / 60}h"
    else -> "预计 ${minutes / 60}h${minutes % 60}m"
}

private object MonthTertiaryCompact {
    val height = 20.dp
    val horizontalPadding = 6.dp
    val gap = 2.dp
    val textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, lineHeight = 14.sp)
}

private object MonthGridGeometry {
    val modeToCalendarGap = 4.dp
    val gridPadding = 4.dp
    val weekdayHeight = 22.dp
    val dateHeight = 20.dp
    val dateToSummaryGap = 2.dp
    val summaryHeight = 16.dp
    val summaryGap = 1.dp

    /** "+N" line: the 11sp text lineHeight plus a small buffer, so the digits are never
     *  cut by the cell's clip or crowded by the summary pill above. */
    val overflowHeight = 12.dp

    /** Bounds for the per-week row height. The real height is derived from the space the
     *  month page actually has, so tall screens show more summaries instead of dead space. */
    val minWeekHeight = 68.dp
    val maxWeekHeight = 116.dp

    /** Maximum summary pills that fit inside one week cell, keeping room for the "+N"
     *  overflow line so it is never squeezed against the cell's clipped edge. */
    fun summaryLimit(weekHeight: Dp): Int {
        val available = weekHeight - dateHeight - dateToSummaryGap - overflowHeight
        val slot = summaryHeight + summaryGap
        return maxOf(1, ((available + summaryGap) / slot).toInt())
    }
}

@Composable
internal fun PlanMonth(
    state: PlanUiState,
    onContent: (MonthContent) -> Unit,
    onDate: (LocalDate) -> Unit,
    onPointRecordClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val month = state.selectedDate.withDayOfMonth(1)
    var expandedRecordDate by remember(month, state.monthContent) { mutableStateOf<LocalDate?>(null) }
    val leading = month.dayOfWeek.value - 1
    val dates = List<LocalDate?>(leading) { null } +
            (1..month.lengthOfMonth()).map(month::withDayOfMonth)
    val rows = dates.chunked(7)

    Box(modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp)) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(
                    MonthTertiaryCompact.gap,
                    Alignment.CenterHorizontally,
                ),
            ) {
                MonthModeAction("计划", state.monthContent == MonthContent.PLAN) {
                    onContent(MonthContent.PLAN)
                }
                MonthModeAction("记录", state.monthContent == MonthContent.RECORD) {
                    onContent(MonthContent.RECORD)
                }
            }
            Spacer(Modifier.height(MonthGridGeometry.modeToCalendarGap))
            // The grid claims whatever height the page has left and every week row takes an
            // equal share of it. The summary budget is derived from that measured height, so a
            // tall screen shows more pills instead of leaving dead space below the calendar.
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                val gridChrome = MonthGridGeometry.weekdayHeight +
                        MonthGridGeometry.gridPadding * 2
                val weekHeight = ((maxHeight - gridChrome) / rows.size).coerceIn(
                    MonthGridGeometry.minWeekHeight,
                    MonthGridGeometry.maxWeekHeight,
                )
                val summaryLimit = MonthGridGeometry.summaryLimit(weekHeight)
                Column(
                    Modifier
                        .fillMaxWidth()
                        .height(gridChrome + weekHeight * rows.size)
                        .clip(RoundedCornerShape(11.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(MonthGridGeometry.gridPadding),
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(MonthGridGeometry.weekdayHeight),
                    ) {
                        listOf("一", "二", "三", "四", "五", "六", "日").forEach { label ->
                            MonthColumn {
                                Text(
                                    label,
                                    Modifier.align(Alignment.Center),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    rows.forEach { week ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .height(weekHeight),
                        ) {
                            (week + List(7 - week.size) { null }).forEach { date ->
                                MonthColumn {
                                    if (date != null) {
                                        Column(
                                            Modifier
                                                .fillMaxSize()
                                                .clipToBounds()
                                                .clickable { onDate(date) }
                                                .padding(horizontal = 2.dp),
                                        ) {
                                            Box(
                                                Modifier
                                                    .fillMaxWidth()
                                                    .height(MonthGridGeometry.dateHeight),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Text(
                                                    date.dayOfMonth.toString(),
                                                    fontSize = 12.sp,
                                                    lineHeight = 16.sp,
                                                    fontWeight = FontWeight.Normal,
                                                    color = if (date == state.selectedDate) {
                                                        MaterialTheme.colorScheme.primary
                                                    } else {
                                                        MaterialTheme.colorScheme.onSurface
                                                    },
                                                )
                                            }
                                            Spacer(Modifier.height(MonthGridGeometry.dateToSummaryGap))
                                            val items = monthSummaryItems(state, date, ZoneId.systemDefault())
                                            Column(
                                                verticalArrangement = Arrangement.spacedBy(1.dp),
                                            ) {
                                                items.take(summaryLimit).forEach { item ->
                                                    MonthPill(item.title, item.isEvent, item.categoryId,
                                                        state.categoryColorKeys,
                                                        item.pointRecordId?.let { id -> { onPointRecordClick(id) } })
                                                }
                                                if (items.size > summaryLimit) {
                                                    Box(
                                                        Modifier
                                                            .fillMaxWidth()
                                                            .height(MonthGridGeometry.overflowHeight)
                                                            .then(if (state.monthContent == MonthContent.RECORD)
                                                                Modifier.clickable { expandedRecordDate = date }
                                                            else Modifier),
                                                        contentAlignment = Alignment.CenterStart,
                                                    ) {
                                                        Text(
                                                            "+" + (items.size - summaryLimit),
                                                            fontSize = 9.sp,
                                                            lineHeight = 11.sp,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    expandedRecordDate?.let { expandedDate ->
        val records = monthRecordsOnDate(state.records, expandedDate, ZoneId.systemDefault())
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { expandedRecordDate = null },
            title = { Text("${expandedDate.monthValue}月${expandedDate.dayOfMonth}日记录") },
            text = {
                Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    records.forEach { record ->
                        MonthPill(AnnotatedString(record.title), false, record.categoryId,
                            state.categoryColorKeys) {
                            expandedRecordDate = null
                            if (record.temporalType == SessionTemporalType.POINT) onPointRecordClick(record.id)
                            else onDate(expandedDate)
                        }
                    }
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { expandedRecordDate = null }) {
                    Text("关闭")
                }
            },
        )
    }
}
@Composable
private fun RowScope.MonthColumn(content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.weight(1f).fillMaxHeight(), content = content)
}

@Composable
private fun MonthPill(title: AnnotatedString, isEvent: Boolean,
                      categoryId: Long?, categoryColorKeys: Map<Long, String>, onClick: (() -> Unit)? = null) {
    val colors = planCategoryColors(categoryId, categoryColorKeys, MaterialTheme.colorScheme)
    Box(Modifier.fillMaxWidth().height(MonthGridGeometry.summaryHeight)
        .background(colors?.medium ?: MaterialTheme.colorScheme.surfaceVariant,
            RoundedCornerShape(5.dp))
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        .padding(horizontal = 4.dp), contentAlignment = Alignment.CenterStart) {
        Text(title, fontSize = 10.sp, lineHeight = 12.sp, maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = colors?.mediumContent ?: MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun MonthModeAction(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(Modifier.height(MonthTertiaryCompact.height)
        .background(
            if (selected) MaterialTheme.colorScheme.primary
            else androidx.compose.ui.graphics.Color.Transparent,
            AppTheme.shapes.pill,
        )
        .clickable(onClick = onClick)
        .padding(horizontal = MonthTertiaryCompact.horizontalPadding),
        contentAlignment = Alignment.Center) {
        Text(label, style = MonthTertiaryCompact.textStyle,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun taskTitleText(task: Task, reference: Long) = androidx.compose.ui.text.buildAnnotatedString {
    pushStyle(SpanStyle(textDecoration = if (TaskTemporalPolicy.completed(task, reference))
        TextDecoration.LineThrough else null))
    append(task.title)
    pop()
}
