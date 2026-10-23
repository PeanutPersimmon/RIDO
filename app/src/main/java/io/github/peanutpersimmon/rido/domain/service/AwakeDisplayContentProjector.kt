package io.github.peanutpersimmon.rido.domain.service

import io.github.peanutpersimmon.rido.domain.model.AwakeDisplayContent
import io.github.peanutpersimmon.rido.domain.model.Event
import io.github.peanutpersimmon.rido.domain.model.Session
import io.github.peanutpersimmon.rido.domain.model.SessionKind
import io.github.peanutpersimmon.rido.domain.model.SessionTemporalType
import io.github.peanutpersimmon.rido.domain.model.Task
import io.github.peanutpersimmon.rido.domain.time.epochDayMinuteToMillis
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

object AwakeDisplayContentProjector {
    fun project(
        displayDate: LocalDate,
        sessions: List<Session>,
        events: List<Event>,
        tasks: List<Task>,
        zoneId: ZoneId,
        asOfMillis: Long,
        includePointRecords: Boolean = false,
        includeTaskCompletions: Boolean = true,
        includeOverlappingActivityIntervals: Boolean = false,
    ): List<AwakeDisplayContent> = buildList {
        sessions.asSequence()
            .filter { it.kind != SessionKind.NIGHT_SLEEP && it.temporalType == SessionTemporalType.INTERVAL && sessionOwnerDate(it, zoneId) == displayDate }
            .forEach { session ->
                val end = session.endAtMillis ?: maxOf(asOfMillis, session.startAtMillis)
                add(AwakeDisplayContent(displayDate, session.startAtMillis, end))
            }
        if (includeOverlappingActivityIntervals) {
            val dayStart = displayDate.atStartOfDay(zoneId).toInstant().toEpochMilli()
            val dayEnd = displayDate.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
            sessions.asSequence()
                .filter { it.kind == SessionKind.ACTIVITY && it.temporalType == SessionTemporalType.INTERVAL &&
                    sessionOwnerDate(it, zoneId) != displayDate && it.startAtMillis < dayEnd &&
                    (it.endAtMillis?.let { end -> end > dayStart } == true) }
                .forEach { session ->
                    add(AwakeDisplayContent(displayDate, maxOf(session.startAtMillis, dayStart),
                        minOf(requireNotNull(session.endAtMillis), dayEnd)))
                }
        }
        if (includePointRecords) {
            sessions.asSequence()
                .filter { it.kind == SessionKind.ACTIVITY && it.temporalType == SessionTemporalType.POINT && sessionOwnerDate(it, zoneId) == displayDate }
                .forEach { add(AwakeDisplayContent(displayDate, it.startAtMillis)) }
        }

        events.asSequence().filter { eventOwnerDate(it, zoneId) == displayDate }.forEach { event ->
                val minute = event.startMinuteOfDay ?: return@forEach
                val start = epochDayMinuteToMillis(event.dateEpochDay, minute, zoneId)
                val end = event.endMinuteOfDay?.let {
                    epochDayMinuteToMillis(event.endDateEpochDay ?: event.dateEpochDay, it, zoneId)
                } ?: start
                if (end >= start) add(AwakeDisplayContent(displayDate, start, end))
        }

        tasks.asSequence()
            .filter { TaskTemporalPolicy.currentPlan(it, displayDate, zoneId, asOfMillis) }
            .filter { it.plannedDateEpochDay == displayDate.toEpochDay() }
            .forEach { task ->
                val minute = task.plannedStartMinuteOfDay ?: return@forEach
                val start = epochDayMinuteToMillis(displayDate.toEpochDay(), minute, zoneId)
                add(AwakeDisplayContent(displayDate, start))
            }

        if (includeTaskCompletions) {
            val dayStart = displayDate.atStartOfDay(zoneId).toInstant().toEpochMilli()
            val dayEnd = displayDate.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
            TaskTemporalPolicy.completionPoints(tasks, sessions, dayStart, dayEnd).forEach { task ->
                task.completedAtMillis?.let { add(AwakeDisplayContent(displayDate, it)) }
            }
        }

    }

    fun sessionOwnerDate(session: Session, zoneId: ZoneId): LocalDate =
        Instant.ofEpochMilli(session.startAtMillis).atZone(zoneId).toLocalDate()

    fun eventOwnerDate(event: Event, zoneId: ZoneId): LocalDate = LocalDate.ofEpochDay(event.dateEpochDay)
}
