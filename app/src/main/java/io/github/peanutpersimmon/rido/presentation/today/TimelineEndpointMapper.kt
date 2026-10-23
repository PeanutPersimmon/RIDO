package io.github.peanutpersimmon.rido.presentation.today

import io.github.peanutpersimmon.rido.domain.model.*
import io.github.peanutpersimmon.rido.domain.service.TaskTemporalPolicy
import io.github.peanutpersimmon.rido.domain.usecase.*
import java.time.LocalDate
import java.time.ZoneId

/** Adds display-only metadata without changing canonical projection semantics or ordering. */
internal object TimelineEndpointMapper {
    fun map(entries: List<TimelineEntryUiModel>, sources: TimelineMetadataSources,
        labels: ClassificationLabels, zone: ZoneId, date: LocalDate = LocalDate.now(zone),
        nowMillis: Long = System.currentTimeMillis()): List<TimelineEntryUiModel> {
        val ref = TaskTemporalPolicy.reference(date, zone, nowMillis)
        return entries.map { entry ->
            when (entry.entryType) {
                TimelineEntryType.TASK_PLAN_POINT -> entry.sourceId?.let(sources.tasksById::get)?.let { task ->
                    entry.copy(
                        completed = TaskTemporalPolicy.completed(task, ref),
                        estimatedDurationLabel = task.estimatedDurationMinutes?.let {
                            TodayUiMapper.formatDuration(it.toLong() * 60_000L)
                        },
                        dueLabel = TaskDueDisplay.value(task, date),
                        prominentDueLabel = TaskDueDisplay.prominent(task, date, nowMillis, zone),
                        isOverdue = task.status != TaskStatus.DONE &&
                            task.status != TaskStatus.CANCELLED &&
                            TaskTemporalPolicy.overdue(task, ref, zone),
                        relatedTaskTitle = entry.relatedTaskTitle
                            ?: task.parentTaskId?.let { labels.tasks[it]?.title },
                    )
                } ?: entry
                TimelineEntryType.SESSION_BLOCK, TimelineEntryType.SESSION_POINT -> entry.copy(
                    sourceEventTitle = entry.sourceEventTitle
                        ?: entry.sourceId?.let(sources.sourceEventTitlesBySessionId::get),
                )
                else -> entry
            }
        }
    }
}
