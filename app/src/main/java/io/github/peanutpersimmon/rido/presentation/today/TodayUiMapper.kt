package io.github.peanutpersimmon.rido.presentation.today

import io.github.peanutpersimmon.rido.domain.model.AwakeDay
import io.github.peanutpersimmon.rido.domain.model.AwakeDisplayWindow
import io.github.peanutpersimmon.rido.domain.model.BoundarySource
import io.github.peanutpersimmon.rido.domain.model.TaggedSession
import io.github.peanutpersimmon.rido.domain.model.TimelineEntry
import io.github.peanutpersimmon.rido.domain.model.TimelineEntryType
import io.github.peanutpersimmon.rido.domain.usecase.ClassificationLabels
import io.github.peanutpersimmon.rido.domain.usecase.TodayPlanItem
import io.github.peanutpersimmon.rido.domain.model.TaskStatus
import io.github.peanutpersimmon.rido.domain.service.TaskTemporalPolicy
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object TodayUiMapper {
    fun map(
        date: LocalDate,
        awakeDay: AwakeDay,
        entries: List<TimelineEntry>,
        activeSession: TaggedSession?,
        labels: ClassificationLabels,
        planItems: List<TodayPlanItem>,
        displayMode: TimelineDisplayMode,
        nowMillis: Long,
        zoneId: ZoneId,
        locale: Locale,
        metadataSources: io.github.peanutpersimmon.rido.domain.usecase.TimelineMetadataSources? = null,
    ): TodayUiState.Content = map(
        date = date,
        displayWindow = AwakeDisplayWindow(
            anchorDate = awakeDay.calendarDate.value,
            displayInterval = awakeDay.interval,
            actualWakeAtMillis = awakeDay.wakeAtMillis,
            actualSleepAtMillis = awakeDay.sleepAtMillis,
            startSource = if (awakeDay.wakeAtMillis == null) BoundarySource.DEFAULT else BoundarySource.REAL,
            endSource = if (awakeDay.sleepAtMillis == null) BoundarySource.DEFAULT else BoundarySource.REAL,
            actualWakeSessionId = awakeDay.wakeSessionId,
            actualSleepSessionId = awakeDay.sleepSessionId,
        ),
        entries = entries,
        activeSession = activeSession,
        labels = labels,
        planItems = planItems,
        displayMode = displayMode,
        nowMillis = nowMillis,
        zoneId = zoneId,
        locale = locale,
        metadataSources = metadataSources,
    )

    fun map(
        date: LocalDate,
        displayWindow: AwakeDisplayWindow,
        entries: List<TimelineEntry>,
        activeSession: TaggedSession?,
        labels: ClassificationLabels,
        planItems: List<TodayPlanItem>,
        displayMode: TimelineDisplayMode,
        nowMillis: Long,
        zoneId: ZoneId,
        locale: Locale,
        metadataSources: io.github.peanutpersimmon.rido.domain.usecase.TimelineMetadataSources? = null,
    ): TodayUiState.Content {
        val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", locale)
        val uiEntries = entries.map { entry ->
            val end = entry.endAtMillis
            val categoryPresentation = entry.categoryId?.let(labels.categoriesById::get)
            TimelineEntryUiModel(
                sourceId = entry.sourceId,
                startAtMillis = entry.startAtMillis,
                endAtMillis = end,
                title = entry.title,
                timeLabel = if (end == null) {
                    formatTime(entry.startAtMillis, zoneId, timeFormatter)
                } else {
                    "${formatTime(entry.startAtMillis, zoneId, timeFormatter)}–${formatTime(end, zoneId, timeFormatter)}"
                },
                durationLabel = end?.let { formatDuration(it - entry.startAtMillis) },
                entryType = entry.entryType,
                state = entry.state,
                relatedTaskTitle = when (entry) {
                    is io.github.peanutpersimmon.rido.domain.model.SessionBlock -> entry.taskId
                    is io.github.peanutpersimmon.rido.domain.model.SessionPoint -> entry.taskId
                    is io.github.peanutpersimmon.rido.domain.model.TaskPlanPoint -> labels.tasks[entry.sourceId]?.parentTaskId
                    else -> null
                }?.let { labels.tasks[it]?.title },
                sessionKind = (entry as? io.github.peanutpersimmon.rido.domain.model.SessionBlock)?.kind,
                categoryId = entry.categoryId,
                categoryLabel = categoryPresentation?.name,
                categoryColorKey = categoryPresentation?.colorKey,
                tagLabels = entry.tagIds.mapNotNull(labels.tagNames::get).sorted(),
                location = entry.location,
                note = entry.note,
                isActive = entry.entryType == TimelineEntryType.SESSION_BLOCK &&
                    entry.sourceId == activeSession?.session?.id,
            )
        }
        val plans = planItems.filter { it.startAtMillis == null }
        val overdueCount = metadataSources?.tasksById?.values?.count { task ->
            task.status != TaskStatus.DONE && task.status != TaskStatus.CANCELLED &&
                TaskTemporalPolicy.overdue(task, nowMillis, zoneId)
        } ?: 0
        val planSummary = if (plans.isNotEmpty() || overdueCount > 0) {
            TodayPlanSummaryUiModel(
                remainingCount = plans.count { !it.completed },
                overdueCount = overdueCount,
                nextTimeLabel = plans.firstOrNull()?.startAtMillis?.let {
                    val startLabel = formatTime(it, zoneId, timeFormatter)
                    plans.first().endAtMillis?.let { end ->
                        "$startLabel–${formatTime(end, zoneId, timeFormatter)}"
                    } ?: startLabel
                },
                nextTitle = plans.firstOrNull()?.title.orEmpty(),
                items = plans.map {
                    TodayPlanItemUiModel(
                        sourceId = it.sourceId,
                        timeLabel = it.startAtMillis?.let { start ->
                            val startLabel = formatTime(start, zoneId, timeFormatter)
                            it.endAtMillis?.let { end ->
                                "$startLabel–${formatTime(end, zoneId, timeFormatter)}"
                            } ?: startLabel
                        },
                        title = it.title, source = it.source, completed = it.completed,
                        detail = if (it.source == io.github.peanutpersimmon.rido.domain.usecase.TodayPlanSource.TASK)
                            metadataSources?.tasksById?.get(it.sourceId)?.let { task ->
                                TaskDueDisplay.prominent(task, date, nowMillis, zoneId)
                            } ?: it.detail else it.detail,
                        categoryColorKey = it.categoryId?.let(labels.categoriesById::get)?.colorKey,
                    )
                },
            )
        } else null
        return TodayUiState.Content(
            dateLabel = formatDateLabel(date),
            awakeDay = AwakeDayUiModel(
                startAtMillis = displayWindow.displayInterval.startMillis,
                endAtMillis = displayWindow.displayInterval.endMillis,
                startLabel = formatTime(displayWindow.displayInterval.startMillis, zoneId, timeFormatter),
                endLabel = formatTime(displayWindow.displayInterval.endMillis, zoneId, timeFormatter),
                wakeAtMillis = displayWindow.actualWakeAtMillis,
                sleepAtMillis = displayWindow.actualSleepAtMillis,
                startSource = displayWindow.startSource,
                endSource = displayWindow.endSource,
            ),
            timelineEntries = (metadataSources?.let {
                    TimelineEndpointMapper.map(uiEntries, it, labels, zoneId, date, nowMillis)
                } ?: uiEntries).map { entry ->
                    if (entry.entryType == TimelineEntryType.SESSION_BLOCK &&
                        entry.sourceId == activeSession?.session?.id
                    ) {
                        entry.copy(
                            isActive = true,
                            durationLabel = formatDuration(nowMillis - entry.startAtMillis),
                        )
                    } else {
                        entry
                    }
                },
            activeSessionId = activeSession?.session?.id,
            planSummary = planSummary,
            displayMode = displayMode,
            nowMillis = nowMillis,
            isToday = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate() == date,
        )
    }

    private fun formatTime(
        millis: Long,
        zoneId: ZoneId,
        formatter: DateTimeFormatter,
    ): String = Instant.ofEpochMilli(millis).atZone(zoneId).format(formatter)

    internal fun formatDuration(millis: Long): String {
        val totalMinutes = Duration.ofMillis(millis.coerceAtLeast(0)).toMinutes()
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return when {
            hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
            hours > 0 -> "${hours}h"
            else -> "${minutes}m"
        }
    }

    internal fun formatDateLabel(date: LocalDate): String =
        date.format(DateTimeFormatter.ofPattern("d\nMMM · EEE", Locale.ENGLISH)).uppercase(Locale.ENGLISH)
}
