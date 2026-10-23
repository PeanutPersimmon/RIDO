package io.github.peanutpersimmon.rido.ui.feature.editor.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.peanutpersimmon.rido.ui.components.picker.PickerHostState
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

internal data class TaskPlannedTimeValue(
    val date: LocalDate?,
    val start: LocalTime?,
) {
    fun withDateAndStart(date: LocalDate, start: LocalTime?): TaskPlannedTimeValue =
        copy(date = date, start = start)

    fun withoutStart(): TaskPlannedTimeValue = copy(start = null)
    fun cleared(): TaskPlannedTimeValue = TaskPlannedTimeValue(null, null)
}

private val plannedDateFormatter = DateTimeFormatter.ofPattern("M/d")
private val plannedTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

internal fun taskPlannedTimeSummary(value: TaskPlannedTimeValue): String? {
    val date = value.date?.format(plannedDateFormatter) ?: return null
    val start = value.start?.format(plannedTimeFormatter) ?: return date
    return "$date · $start"
}

@Composable
internal fun TaskPlannedTimeField(
    value: TaskPlannedTimeValue,
    picker: PickerHostState,
    modifier: Modifier = Modifier,
    trailingContent: (@Composable () -> Unit)? = null,
    onChange: (TaskPlannedTimeValue) -> Unit,
) {
    EditorTimeTool(
        icon = Icons.Outlined.CalendarMonth,
        iconTestTag = "task-time-plan-icon",
        label = "计划时间",
        summary = taskPlannedTimeSummary(value),
        onClick = {
            picker.show(
                initialDate = value.date ?: LocalDate.now(),
                initialTime = value.start,
                timeOptional = true,
            ) { date, start ->
                onChange(value.withDateAndStart(date, start))
            }
        },
        modifier = modifier,
        onClear = if (value.date != null) ({ onChange(value.cleared()) }) else null,
        trailingContent = trailingContent,
    )
}
