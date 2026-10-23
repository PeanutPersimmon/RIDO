package io.github.peanutpersimmon.rido.domain.service

import io.github.peanutpersimmon.rido.domain.model.*
import io.github.peanutpersimmon.rido.domain.time.epochDayMinuteToMillis
import io.github.peanutpersimmon.rido.domain.time.TimeInterval
import java.time.ZoneId

/** Today projects record facts and planned entries. */
object TimelineProjector {
    fun projectionRange(awakeDay: AwakeDay, zoneId: ZoneId): TimeInterval {
        val date = awakeDay.calendarDate.value
        val calendarStart = date.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val calendarEnd = date.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        val start = awakeDay.wakeAtMillis ?: calendarStart
        val end = awakeDay.sleepAtMillis ?: maxOf(awakeDay.interval.endMillis, calendarEnd)
        return if (end > start) TimeInterval(start, end) else awakeDay.interval
    }

    fun projectionRange(window: AwakeDisplayWindow, zoneId: ZoneId): TimeInterval {
        val calendarStart = window.anchorDate.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val calendarEnd = window.anchorDate.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        val start = if (window.actualWakeAtMillis == null) {
            minOf(window.displayInterval.startMillis, calendarStart)
        } else {
            window.displayInterval.startMillis
        }
        val end = if (window.actualSleepAtMillis == null) {
            maxOf(window.displayInterval.endMillis, calendarEnd)
        } else {
            window.displayInterval.endMillis
        }
        return TimeInterval(start, end)
    }

    fun project(awakeDay: AwakeDay, sessions: List<TaggedSession>, events: List<TaggedEvent>,
        tasks: List<TaggedTask>, asOfMillis: Long, zoneId: ZoneId): DomainResult<List<TimelineEntry>> =
        projectInternal(
            date = awakeDay.calendarDate.value,
            displayRange = projectionRange(awakeDay, zoneId),
            wakeAtMillis = awakeDay.wakeAtMillis,
            sleepAtMillis = awakeDay.sleepAtMillis,
            wakeSessionId = awakeDay.wakeSessionId,
            sleepSessionId = awakeDay.sleepSessionId,
            sessions = sessions,
            events = events,
            tasks = tasks,
            asOfMillis = asOfMillis,
            zoneId = zoneId,
        )

    fun project(window: AwakeDisplayWindow, sessions: List<TaggedSession>, events: List<TaggedEvent>,
        tasks: List<TaggedTask>, asOfMillis: Long, zoneId: ZoneId): DomainResult<List<TimelineEntry>> =
        when (val result = projectInternal(
            date = window.anchorDate,
            displayRange = window.displayInterval,
            wakeAtMillis = window.actualWakeAtMillis,
            sleepAtMillis = window.actualSleepAtMillis,
            wakeSessionId = window.actualWakeSessionId,
            sleepSessionId = window.actualSleepSessionId,
            sessions = sessions,
            events = events,
            tasks = tasks,
            asOfMillis = asOfMillis,
            zoneId = zoneId,
        )) {
            is DomainResult.Failure -> result
            is DomainResult.Success -> DomainResult.Success(
                result.value.mapNotNull { it.clippedTo(window.displayInterval) },
            )
        }

    private fun projectInternal(
        date: java.time.LocalDate,
        displayRange: TimeInterval,
        wakeAtMillis: Long?,
        sleepAtMillis: Long?,
        wakeSessionId: Long?,
        sleepSessionId: Long?,
        sessions: List<TaggedSession>,
        events: List<TaggedEvent>,
        tasks: List<TaggedTask>,
        asOfMillis: Long,
        zoneId: ZoneId,
    ): DomainResult<List<TimelineEntry>> {
        val ownedSessions = sessions.filter {
            AwakeDisplayContentProjector.sessionOwnerDate(it.session, zoneId) == date
        }
        val resolution = when (val result = ActualTimeResolver.resolve(
            ownedSessions,
            displayRange,
            asOfMillis,
        )) {
            is DomainResult.Failure -> return result
            is DomainResult.Success -> result.value
        }
        val entries = mutableListOf<TimelineEntry>()
        wakeAtMillis?.let { entries += WakePoint(it, wakeSessionId) }
        sleepAtMillis?.let { entries += SleepPoint(it, sleepSessionId) }
        resolution.segments.filter { it.sourceType == ActualSourceType.SESSION && it.sessionKind != SessionKind.NIGHT_SLEEP }
            .forEach { segment ->
                entries += SessionBlock(segment.sourceId, segment.interval.startMillis, segment.interval.endMillis,
                    segment.title, segment.categoryId, segment.tagIds, segment.note, segment.location,
                    requireNotNull(segment.sessionKind), ownedSessions.find { it.session.id == segment.sourceId }?.session?.taskId)
            }
        ownedSessions.asSequence()
            .filter { it.session.kind == SessionKind.ACTIVITY && it.session.temporalType == SessionTemporalType.POINT }
            .filter { it.session.startAtMillis >= displayRange.startMillis && it.session.startAtMillis < displayRange.endMillis }
            .forEach { tagged ->
                val session = tagged.session
                entries += SessionPoint(session.id, session.startAtMillis, session.title,
                    session.categoryId, tagged.tagIds, session.note, session.location, session.taskId)
            }
        events.filter { AwakeDisplayContentProjector.eventOwnerDate(it.event, zoneId) == date }.forEach { tagged ->
            val e = tagged.event
            val minute = e.startMinuteOfDay ?: return@forEach
            val start = epochDayMinuteToMillis(e.dateEpochDay, minute, zoneId)
            val end = e.endMinuteOfDay?.let {
                epochDayMinuteToMillis(e.endDateEpochDay ?: e.dateEpochDay, it, zoneId)
            }
            entries += EventBlock(e.id, start, end, e.title, TimelineState.PLANNED,
                e.categoryId, tagged.tagIds, e.note, e.location)
        }
        tasks.filter { TaskTemporalPolicy.currentPlan(it.task, date, zoneId, asOfMillis) }.forEach { tagged ->
            val task = tagged.task
            val minute = task.plannedStartMinuteOfDay?.takeIf { task.plannedDateEpochDay == date.toEpochDay() } ?: return@forEach
            val plannedDate = requireNotNull(task.plannedDateEpochDay)
            entries += TaskPlanPoint(task.id, epochDayMinuteToMillis(plannedDate, minute, zoneId),
                task.title, task.categoryId, tagged.tagIds, task.note, task.location)
        }
        return DomainResult.Success(entries.sortedWith(compareBy<TimelineEntry> { it.startAtMillis }
            .thenBy { when (it.entryType) {
                TimelineEntryType.WAKE_POINT -> 0
                TimelineEntryType.SESSION_BLOCK -> 1
                TimelineEntryType.SESSION_POINT -> 2
                TimelineEntryType.EVENT_BLOCK, TimelineEntryType.TASK_PLAN_POINT -> 3
                TimelineEntryType.SLEEP_POINT -> 4
            } }.thenBy { it.sourceId ?: Long.MIN_VALUE }))
    }

    private fun TimelineEntry.clippedTo(range: TimeInterval): TimelineEntry? {
        val originalEnd = endAtMillis
        if (originalEnd == null) {
            return takeIf { startAtMillis >= range.startMillis && startAtMillis < range.endMillis }
        }
        val clippedStart = maxOf(startAtMillis, range.startMillis)
        val clippedEnd = minOf(originalEnd, range.endMillis)
        if (clippedEnd <= clippedStart) return null
        return when (this) {
            is SessionBlock -> copy(startAtMillis = clippedStart, endAtMillis = clippedEnd)
            is EventBlock -> copy(startAtMillis = clippedStart, endAtMillis = clippedEnd)
            else -> null
        }
    }
}
