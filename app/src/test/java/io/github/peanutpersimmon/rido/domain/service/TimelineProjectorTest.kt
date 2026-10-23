package io.github.peanutpersimmon.rido.domain.service

import io.github.peanutpersimmon.rido.domain.model.AwakeDay
import io.github.peanutpersimmon.rido.domain.model.AwakeDisplayWindow
import io.github.peanutpersimmon.rido.domain.model.BoundarySource
import io.github.peanutpersimmon.rido.domain.model.CalendarDate
import io.github.peanutpersimmon.rido.domain.model.DomainResult
import io.github.peanutpersimmon.rido.domain.model.Event
import io.github.peanutpersimmon.rido.domain.model.EventBlock
import io.github.peanutpersimmon.rido.domain.model.Session
import io.github.peanutpersimmon.rido.domain.model.SessionBlock
import io.github.peanutpersimmon.rido.domain.model.SessionKind
import io.github.peanutpersimmon.rido.domain.model.SessionPoint
import io.github.peanutpersimmon.rido.domain.model.SessionTemporalType
import io.github.peanutpersimmon.rido.domain.model.SleepPoint
import io.github.peanutpersimmon.rido.domain.model.TaggedEvent
import io.github.peanutpersimmon.rido.domain.model.TaggedSession
import io.github.peanutpersimmon.rido.domain.model.TaggedTask
import io.github.peanutpersimmon.rido.domain.model.Task
import io.github.peanutpersimmon.rido.domain.model.TaskCompletionSource
import io.github.peanutpersimmon.rido.domain.model.TaskPlanPoint
import io.github.peanutpersimmon.rido.domain.model.TaskStatus
import io.github.peanutpersimmon.rido.domain.model.TimelineEntry
import io.github.peanutpersimmon.rido.domain.model.TimelineEntryType
import io.github.peanutpersimmon.rido.domain.model.TimelineState
import io.github.peanutpersimmon.rido.domain.model.WakePoint
import io.github.peanutpersimmon.rido.domain.time.TimeInterval
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class TimelineProjectorTest {
    private val zone = ZoneOffset.UTC
    private val date = LocalDate.of(2026, 1, 1)
    private val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
    private val hour = 3_600_000L
    private val awake = AwakeDay(
        calendarDate = CalendarDate(date),
        interval = TimeInterval(at(7), at(24)),
        wakeAtMillis = at(7),
        sleepAtMillis = at(24),
    )

    @Test fun completedTaskDoesNotCreateIndependentTodayFact() {
        val saved = TaggedTask(Task(id = 42, title = "拿快递", status = TaskStatus.DONE,
            completedAtMillis = at(10), completionSource = TaskCompletionSource.MANUAL,
            createdAtMillis = at(48), updatedAtMillis = at(48)))
        val entries = project(tasks = listOf(saved))
        assertEquals(at(10), saved.task.completedAtMillis)
        assertTrue(entries.none { it is SessionBlock || it is SessionPoint || it is TaskPlanPoint })
    }

    @Test fun nightSleepDoesNotCreateLargeBlock() {
        val entries = project(sessions = listOf(session(1, 7, 8, SessionKind.NIGHT_SLEEP)))
        assertFalse(entries.filterIsInstance<SessionBlock>().any { it.kind == SessionKind.NIGHT_SLEEP })
    }

    @Test fun wakePointIsProjected() {
        assertEquals(at(7), project().filterIsInstance<WakePoint>().single().startAtMillis)
    }

    @Test fun sleepPointIsProjected() {
        assertEquals(at(24), project().filterIsInstance<SleepPoint>().single().startAtMillis)
    }

    @Test fun napIsAnActualSessionBlock() {
        val block = project(sessions = listOf(session(1, 13, 14, SessionKind.NAP)))
            .filterIsInstance<SessionBlock>().single()
        assertEquals(SessionKind.NAP, block.kind)
        assertEquals(TimelineState.ACTUAL, block.state)
    }

    @Test fun activityIsAnActualSessionBlock() {
        val block = project(sessions = listOf(session(1, 10, 11, SessionKind.ACTIVITY)))
            .filterIsInstance<SessionBlock>().single()
        assertEquals(SessionKind.ACTIVITY, block.kind)
    }

    @Test fun activityPointIsAnActualSessionPointWithoutDuration() {
        val point = project(sessions = listOf(pointSession(44, 17, 26)))
            .filterIsInstance<SessionPoint>().single()
        assertEquals(TimelineEntryType.SESSION_POINT, point.entryType)
        assertEquals(44L, point.sourceId)
        assertEquals(at(17, 26), point.startAtMillis)
        assertNull(point.endAtMillis)
        assertEquals(TimelineState.ACTUAL, point.state)
    }

    @Test fun sleepPointsNeverBecomeOrdinarySessionPoints() {
        val entries = project(sessions = listOf(
            pointSession(44, 10, 0, SessionKind.NAP),
            pointSession(45, 11, 0, SessionKind.NIGHT_SLEEP),
        ))
        assertTrue(entries.none { it is SessionPoint })
    }

    @Test fun taskPlanBlockIsPlannedOnly() {
        val block = project(tasks = listOf(task(1, plannedStart = 10)))
            .filterIsInstance<TaskPlanPoint>().single()
        assertEquals(TimelineState.PLANNED, block.state)
        assertEquals(at(10), block.startAtMillis)
        assertNull(block.endAtMillis)
    }

    @Test fun startOnlyTaskPlanRemainsAPoint() {
        val block = project(tasks = listOf(task(1, plannedStart = 10)))
            .filterIsInstance<TaskPlanPoint>().single()

        assertNull(block.endAtMillis)
    }

    @Test fun manualAndChildrenCompletionsDoNotBecomeTodayEntries() {
        assertTrue(project(tasks = listOf(task(1, completionSource = TaskCompletionSource.MANUAL)))
            .none { it is TaskPlanPoint || it is SessionPoint })
        assertTrue(project(tasks = listOf(task(2, completionSource = TaskCompletionSource.CHILDREN)))
            .none { it is TaskPlanPoint || it is SessionPoint })
    }

    @Test fun cancelledTaskDoesNotHideLinkedSessionOrCreatePlanOrCompletionFacts() {
        val cancelled = TaggedTask(
            Task(
                id = 7,
                title = "Cancelled task",
                status = TaskStatus.CANCELLED,
                plannedDateEpochDay = date.toEpochDay(),
                plannedStartMinuteOfDay = 10 * 60,
                completedAtMillis = at(15),
                createdAtMillis = start,
                updatedAtMillis = start,
            ),
        )
        val linkedSession = session(8, 11, 12, SessionKind.ACTIVITY).let { tagged ->
            tagged.copy(session = tagged.session.copy(taskId = cancelled.task.id))
        }

        val entries = project(sessions = listOf(linkedSession), tasks = listOf(cancelled))

        assertEquals(listOf(8L), entries.filterIsInstance<SessionBlock>().map { it.sourceId })
        assertTrue(entries.none { it is TaskPlanPoint || it is SessionPoint })
    }

    @Test fun pastEventRemainsPlanned() {
        val block = project(events = listOf(event(1, 10, 11)))
            .filterIsInstance<EventBlock>().single { it.state == TimelineState.PLANNED }
        assertEquals(1L, block.sourceId)
    }

    @Test fun eventPointRemainsAPlannedPoint() {
        val source = event(6, 10, 11).let { tagged ->
            tagged.copy(event = tagged.event.copy(endMinuteOfDay = null))
        }
        val entry = project(events = listOf(source)).filterIsInstance<EventBlock>().single()
        assertEquals(TimelineState.PLANNED, entry.state)
        assertEquals(at(10), entry.startAtMillis)
        assertNull(entry.endAtMillis)
    }

    @Test fun overlappingPlansKeepBothSourceIdentities() {
        val blocks = project(events = listOf(event(1, 10, 12), event(2, 11, 13)))
            .filterIsInstance<EventBlock>()
            .filter { it.state == TimelineState.PLANNED }
        assertEquals(setOf(1L, 2L), blocks.mapTo(mutableSetOf()) { it.sourceId })
    }

    @Test fun ongoingEventKeepsOriginalPlanWithoutInferringFacts() {
        val entries = project(events = listOf(event(1, 14, 17)), asOf = at(15))
            .filterIsInstance<EventBlock>()
        assertEquals(listOf(TimelineState.PLANNED), entries.map { it.state })
        assertEquals(listOf(at(14) to at(17)), entries.map { it.startAtMillis to it.endAtMillis })
    }

    @Test fun sameTimeOrderingIsStableAndSemantic() {
        val customAwake = awake.copy(wakeAtMillis = null, sleepAtMillis = null)
        val entries = project(
            sessions = listOf(session(9, 10, 11, SessionKind.ACTIVITY), pointSession(8, 10, 0)),
            tasks = listOf(
                task(3, plannedStart = 10),
                task(2, completionSource = TaskCompletionSource.MANUAL, completionHour = 10),
            ),
            awakeDay = customAwake,
        ).filter { it.startAtMillis == at(10) }
        assertEquals(
            listOf(
                TimelineEntryType.SESSION_BLOCK,
                TimelineEntryType.SESSION_POINT,
                TimelineEntryType.TASK_PLAN_POINT,
            ),
            entries.map { it.entryType },
        )
    }

    @Test fun missingRealBoundariesKeepsEarlyMorningSessionInsideCalendarDay() {
        val boundaryless = awake.copy(
            interval = TimeInterval(at(7), at(23)),
            wakeAtMillis = null,
            sleepAtMillis = null,
        )

        val block = project(
            sessions = listOf(session(10, 3, 4, SessionKind.ACTIVITY)),
            awakeDay = boundaryless,
        ).filterIsInstance<SessionBlock>().single()

        assertEquals(at(3), block.startAtMillis)
        assertEquals(at(4), block.endAtMillis)
    }

    @Test fun missingRealBoundariesKeepsLateNightSessionInsideCalendarDay() {
        val boundaryless = awake.copy(
            interval = TimeInterval(at(7), at(23)),
            wakeAtMillis = null,
            sleepAtMillis = null,
        )

        val block = project(
            sessions = listOf(session(11, 23, 30, 23, 50, SessionKind.ACTIVITY)),
            awakeDay = boundaryless,
        ).filterIsInstance<SessionBlock>().single()

        assertEquals(at(23, 30), block.startAtMillis)
        assertEquals(at(23, 50), block.endAtMillis)
    }

    @Test fun realWakeAndSleepStillConstrainProjectionRange() {
        val bounded = awake.copy(
            interval = TimeInterval(at(7), at(23)),
            wakeAtMillis = at(7),
            sleepAtMillis = at(23),
        )

        val blocks = project(
            sessions = listOf(
                session(12, 3, 4, SessionKind.ACTIVITY),
                session(13, 22, 24, SessionKind.ACTIVITY),
            ),
            awakeDay = bounded,
        ).filterIsInstance<SessionBlock>()

        assertEquals(listOf(13L), blocks.map { it.sourceId })
        assertEquals(at(22), blocks.single().startAtMillis)
        assertEquals(at(23), blocks.single().endAtMillis)
    }

    @Test fun defaultDisplayBoundariesDoNotCreateWakeOrSleepFacts() {
        val window = AwakeDisplayWindow(
            anchorDate = date,
            displayInterval = TimeInterval(at(10), at(26)),
            actualWakeAtMillis = null,
            actualSleepAtMillis = null,
            startSource = BoundarySource.DEFAULT,
            endSource = BoundarySource.DEFAULT,
        )

        val entries = project(window = window)

        assertTrue(entries.none { it is WakePoint || it is SleepPoint })
    }

    @Test fun nowDisplayEndDoesNotCreateSleepFact() {
        val window = AwakeDisplayWindow(
            anchorDate = date,
            displayInterval = TimeInterval(at(10), at(18)),
            actualWakeAtMillis = at(10),
            actualSleepAtMillis = null,
            startSource = BoundarySource.REAL,
            endSource = BoundarySource.NOW,
        )

        val entries = project(window = window)

        assertEquals(listOf(at(10)), entries.filterIsInstance<WakePoint>().map { it.startAtMillis })
        assertTrue(entries.none { it is SleepPoint })
    }

    @Test fun widerCandidatesOutsideDisplayWindowAreNotProjected() {
        val window = AwakeDisplayWindow(
            anchorDate = date,
            displayInterval = TimeInterval(at(10), at(26)),
            actualWakeAtMillis = null,
            actualSleepAtMillis = null,
            startSource = BoundarySource.DEFAULT,
            endSource = BoundarySource.DEFAULT,
        )

        val blocks = project(
            window = window,
            sessions = listOf(
                session(20, 3, 4, SessionKind.ACTIVITY),
                session(21, 11, 12, SessionKind.ACTIVITY),
            ),
        ).filterIsInstance<SessionBlock>()

        assertEquals(listOf(21L), blocks.map { it.sourceId })
    }

    @Test fun pointAtExclusiveDisplayEndIsNotProjected() {
        val window = AwakeDisplayWindow(date, TimeInterval(at(10), at(20)), null, null,
            BoundarySource.DEFAULT, BoundarySource.DEFAULT)
        val points = project(window = window, sessions = listOf(
            pointSession(1, 10, 0), pointSession(2, 19, 59), pointSession(3, 20, 0),
        )).filterIsInstance<SessionPoint>()
        assertEquals(listOf(1L, 2L), points.map { it.sourceId })
    }

    @Test fun realWakeAndSleepAlsoConstrainPointRecords() {
        val bounded = awake.copy(interval = TimeInterval(at(7), at(23)),
            wakeAtMillis = at(7), sleepAtMillis = at(23))
        val points = project(awakeDay = bounded, sessions = listOf(
            pointSession(1, 6, 59), pointSession(2, 7, 0),
            pointSession(3, 22, 59), pointSession(4, 23, 0),
        )).filterIsInstance<SessionPoint>()
        assertEquals(listOf(2L, 3L), points.map { it.sourceId })
    }

    @Test fun midnightPointBelongsOnlyToFollowingDate() {
        val midnight = TaggedSession(Session(id = 90, title = "Midnight",
            temporalType = SessionTemporalType.POINT, startAtMillis = at(24),
            endAtMillis = null, createdAtMillis = start, updatedAtMillis = start))
        assertTrue(project(sessions = listOf(midnight)).none { it is SessionPoint })
        val nextWindow = AwakeDisplayWindow(date.plusDays(1), TimeInterval(at(24), at(48)),
            null, null, BoundarySource.DEFAULT, BoundarySource.DEFAULT)
        assertEquals(listOf(90L), project(nextWindow, sessions = listOf(midnight))
            .filterIsInstance<SessionPoint>().map { it.sourceId })
    }

    private fun project(
        sessions: List<TaggedSession> = emptyList(),
        events: List<TaggedEvent> = emptyList(),
        tasks: List<TaggedTask> = emptyList(),
        asOf: Long = at(24),
        awakeDay: AwakeDay = awake,
    ): List<TimelineEntry> {
        val result = TimelineProjector.project(awakeDay, sessions, events, tasks, asOf, zone)
        assertTrue(result is DomainResult.Success)
        return (result as DomainResult.Success).value
    }

    private fun project(
        window: AwakeDisplayWindow,
        sessions: List<TaggedSession> = emptyList(),
        events: List<TaggedEvent> = emptyList(),
        tasks: List<TaggedTask> = emptyList(),
        asOf: Long = at(24),
    ): List<TimelineEntry> {
        val result = TimelineProjector.project(window, sessions, events, tasks, asOf, zone)
        assertTrue(result is DomainResult.Success)
        return (result as DomainResult.Success).value
    }

    private fun session(id: Long, from: Int, to: Int, kind: SessionKind) = TaggedSession(
        Session(
            id = id,
            title = "Session $id",
            kind = kind,
            startAtMillis = at(from),
            endAtMillis = at(to),
            createdAtMillis = start,
            updatedAtMillis = start,
        ),
    )

    private fun pointSession(id: Long, hourValue: Int, minuteValue: Int,
        kind: SessionKind = SessionKind.ACTIVITY) = TaggedSession(Session(
            id = id, title = "Point $id", kind = kind, temporalType = SessionTemporalType.POINT,
            startAtMillis = at(hourValue, minuteValue), endAtMillis = null,
            createdAtMillis = start, updatedAtMillis = start,
        ))

    private fun session(
        id: Long,
        fromHour: Int,
        fromMinute: Int,
        toHour: Int,
        toMinute: Int,
        kind: SessionKind,
    ) = TaggedSession(
        Session(
            id = id,
            title = "Session $id",
            kind = kind,
            startAtMillis = at(fromHour, fromMinute),
            endAtMillis = at(toHour, toMinute),
            createdAtMillis = start,
            updatedAtMillis = start,
        ),
    )

    private fun event(id: Long, from: Int, to: Int) = TaggedEvent(
        Event(
            id = id,
            title = "Event $id",
            dateEpochDay = date.toEpochDay(),
            startMinuteOfDay = from * 60,
            endMinuteOfDay = to * 60,
            createdAtMillis = start,
            updatedAtMillis = start,
        ),
    )

    private fun task(
        id: Long,
        plannedStart: Int? = null,
        completionSource: TaskCompletionSource? = null,
        completionHour: Int = 15,
    ) = TaggedTask(
        Task(
            id = id,
            title = "Task $id",
            status = if (completionSource == null) TaskStatus.TODO else TaskStatus.DONE,
            plannedDateEpochDay = plannedStart?.let { date.toEpochDay() },
            plannedStartMinuteOfDay = plannedStart?.times(60),
            createdAtMillis = start,
            updatedAtMillis = start,
            completedAtMillis = completionSource?.let { at(completionHour) },
            completionSource = completionSource,
        ),
    )

    private fun at(hourValue: Int, minuteValue: Int = 0): Long =
        start + hourValue * hour + minuteValue * 60_000L
}
