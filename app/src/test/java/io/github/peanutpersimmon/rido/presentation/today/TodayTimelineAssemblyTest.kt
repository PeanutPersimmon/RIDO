package io.github.peanutpersimmon.rido.presentation.today

import io.github.peanutpersimmon.rido.domain.model.AwakeDay
import io.github.peanutpersimmon.rido.domain.model.CalendarDate
import io.github.peanutpersimmon.rido.domain.model.DomainResult
import io.github.peanutpersimmon.rido.domain.model.Event
import io.github.peanutpersimmon.rido.domain.model.Session
import io.github.peanutpersimmon.rido.domain.model.SessionKind
import io.github.peanutpersimmon.rido.domain.model.SessionTemporalType
import io.github.peanutpersimmon.rido.domain.model.TaggedEvent
import io.github.peanutpersimmon.rido.domain.model.TaggedSession
import io.github.peanutpersimmon.rido.domain.model.TaggedTask
import io.github.peanutpersimmon.rido.domain.model.Task
import io.github.peanutpersimmon.rido.domain.model.TaskCompletionSource
import io.github.peanutpersimmon.rido.domain.model.TaskStatus
import io.github.peanutpersimmon.rido.domain.model.TimelineEntry
import io.github.peanutpersimmon.rido.domain.model.TimelineEntryType
import io.github.peanutpersimmon.rido.domain.model.TimelineState
import io.github.peanutpersimmon.rido.domain.service.TimelineProjector
import io.github.peanutpersimmon.rido.domain.time.TimeInterval
import io.github.peanutpersimmon.rido.domain.usecase.ClassificationLabels
import io.github.peanutpersimmon.rido.domain.usecase.CategoryPresentation
import io.github.peanutpersimmon.rido.domain.usecase.TimelineMetadataProjector
import io.github.peanutpersimmon.rido.ui.feature.today.todayCategoryColors
import io.github.peanutpersimmon.rido.ui.feature.today.isTimedSpan
import io.github.peanutpersimmon.rido.ui.feature.today.isPlan
import io.github.peanutpersimmon.rido.ui.theme.resolveCategoryColors
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TodayTimelineAssemblyTest {
    private val zone = ZoneId.of("UTC")
    private val date = LocalDate.of(2026, 1, 1)
    private val dayStart = date.atStartOfDay(zone).toInstant().toEpochMilli()
    private val awake = AwakeDay(
        calendarDate = CalendarDate(date),
        interval = TimeInterval(at(7), at(24)),
        wakeAtMillis = null,
        sleepAtMillis = null,
    )
    private val labels = ClassificationLabels(emptyMap(), emptyMap())

    @Test
    fun `ordinary Today intervals and points retain category keys and theme colors`() {
        val categories = ClassificationLabels(
            categoriesById = mapOf(
                1L to CategoryPresentation("研究", "blue", false),
                2L to CategoryPresentation("会议", "neutral", false),
                3L to CategoryPresentation("旧分类", "retired", false),
            ),
            tagNames = emptyMap(),
        )
        val task = TaggedTask(Task(id = 1, title = "Task", categoryId = 1,
            plannedDateEpochDay = date.toEpochDay(), plannedStartMinuteOfDay = 14 * 60,
            estimatedDurationMinutes = 60, createdAtMillis = dayStart, updatedAtMillis = dayStart))
        val intervalEvent = TaggedEvent(Event(id = 2, title = "Event interval", categoryId = 2,
            dateEpochDay = date.toEpochDay(), startMinuteOfDay = 15 * 60,
            endMinuteOfDay = 16 * 60, createdAtMillis = dayStart, updatedAtMillis = dayStart))
        val pointEvent = TaggedEvent(Event(id = 3, title = "Event point", categoryId = 1,
            dateEpochDay = date.toEpochDay(), startMinuteOfDay = 17 * 60,
            createdAtMillis = dayStart, updatedAtMillis = dayStart))
        val intervalRecord = TaggedSession(Session(id = 4, title = "Record interval", categoryId = 1,
            startAtMillis = at(8), endAtMillis = at(9),
            createdAtMillis = dayStart, updatedAtMillis = dayStart))
        val pointRecord = TaggedSession(Session(id = 5, title = "Record point", categoryId = 3,
            temporalType = SessionTemporalType.POINT, startAtMillis = at(10),
            createdAtMillis = dayStart, updatedAtMillis = dayStart))
        val entries = assemble(sessions = listOf(intervalRecord, pointRecord),
            events = listOf(intervalEvent, pointEvent), tasks = listOf(task),
            classificationLabels = categories, nowMillis = at(18)).associateBy { it.sourceId }
        assertEquals("blue", entries.getValue(1L).categoryColorKey)
        assertFalse(entries.getValue(1L).isTimedSpan)
        assertEquals("neutral", entries.getValue(2L).categoryColorKey)
        assertTrue(entries.getValue(2L).isTimedSpan)
        assertEquals("blue", entries.getValue(3L).categoryColorKey)
        assertFalse(entries.getValue(3L).isTimedSpan)
        assertEquals("blue", entries.getValue(4L).categoryColorKey)
        assertTrue(entries.getValue(4L).isTimedSpan)
        assertEquals("retired", entries.getValue(5L).categoryColorKey)
        assertFalse(entries.getValue(5L).isTimedSpan)

        val light = lightColorScheme()
        val dark = darkColorScheme()
        assertEquals(resolveCategoryColors("blue", light), todayCategoryColors(entries.getValue(4L), light))
        assertEquals(resolveCategoryColors("blue", dark), todayCategoryColors(entries.getValue(4L), dark))
        assertEquals(resolveCategoryColors("neutral", light), todayCategoryColors(entries.getValue(2L), light))
        assertEquals(resolveCategoryColors("neutral", light), todayCategoryColors(entries.getValue(5L), light))
        assertNull(todayCategoryColors(entries.getValue(4L).copy(categoryColorKey = null), light))
        assertNull(todayCategoryColors(entries.getValue(4L).copy(sessionKind = SessionKind.NAP), light))
        assertNull(todayCategoryColors(entries.getValue(4L).copy(sessionKind = SessionKind.NIGHT_SLEEP), light))
        assertEquals(listOf(at(8), at(9)), entries.getValue(4L).axisTimes)
        assertEquals(listOf(at(10)), entries.getValue(5L).axisTimes)
    }

    @Test
    fun `completed categorized task remains a Task Point rather than a Record`() {
        val completed = TaggedTask(Task(id = 22, title = "已完成任务", status = TaskStatus.DONE,
            plannedDateEpochDay = date.toEpochDay(), plannedStartMinuteOfDay = 10 * 60,
            completedAtMillis = at(11), categoryId = 1,
            createdAtMillis = dayStart, updatedAtMillis = at(11)))
        val categories = ClassificationLabels(mapOf(1L to CategoryPresentation("研究", "blue", false)),
            emptyMap())
        val entries = assemble(tasks = listOf(completed), classificationLabels = categories,
            nowMillis = at(12))
        val point = entries.single()
        assertEquals(TimelineEntryType.TASK_PLAN_POINT, point.entryType)
        assertTrue(point.completed)
        assertEquals("blue", point.categoryColorKey)
        assertFalse(point.isTimedSpan)
        assertTrue(entries.none { it.entryType == TimelineEntryType.SESSION_POINT })
    }

    @Test
    fun `categorized nap keeps its special visuals and night sleep stays outside content`() {
        val labels = ClassificationLabels(mapOf(1L to CategoryPresentation("休息", "blue", false)),
            emptyMap())
        val nap = TaggedSession(Session(id = 30, title = "小憩", kind = SessionKind.NAP,
            categoryId = 1, startAtMillis = at(13), endAtMillis = at(13, 30),
            createdAtMillis = dayStart, updatedAtMillis = dayStart))
        val sleep = TaggedSession(Session(id = 31, title = "睡眠", kind = SessionKind.NIGHT_SLEEP,
            categoryId = 1, startAtMillis = at(21), endAtMillis = at(22),
            createdAtMillis = dayStart, updatedAtMillis = dayStart))
        val entries = assemble(sessions = listOf(nap, sleep), classificationLabels = labels,
            nowMillis = at(23))
        val napEntry = entries.single { it.sourceId == 30L }
        assertEquals(SessionKind.NAP, napEntry.sessionKind)
        assertNull(todayCategoryColors(napEntry, lightColorScheme()))
        assertTrue(entries.none { it.sourceId == 31L })
    }

    @Test
    fun `active session keeps projector effective end through final Today assembly`() {
        val active = TaggedSession(
            Session(
                id = 1,
                title = "Active session",
                startAtMillis = at(14),
                endAtMillis = null,
                createdAtMillis = dayStart,
                updatedAtMillis = dayStart,
            ),
        )
        val asOf = at(15, 20)

        val entry = assemble(
            sessions = listOf(active),
            activeSession = active,
            nowMillis = asOf,
        ).single { it.entryType == TimelineEntryType.SESSION_BLOCK }

        assertEquals(at(14), entry.startAtMillis)
        assertEquals(asOf, entry.endAtMillis)
        assertTrue(entry.isTimedSpan)
        assertTrue(entry.isActive)
        assertNull(active.session.endAtMillis)
    }

    @Test
    fun `finished session keeps persisted end instead of asOf`() {
        val finished = TaggedSession(
            Session(
                id = 2,
                title = "Finished session",
                startAtMillis = at(14),
                endAtMillis = at(15),
                createdAtMillis = dayStart,
                updatedAtMillis = at(15),
            ),
        )

        val entry = assemble(
            sessions = listOf(finished),
            nowMillis = at(16),
        ).single { it.entryType == TimelineEntryType.SESSION_BLOCK }

        assertEquals(at(14), entry.startAtMillis)
        assertEquals(at(15), entry.endAtMillis)
        assertTrue(entry.isTimedSpan)
        assertFalse(entry.isActive)
    }

    @Test
    fun `start-only task remains a point through final Today assembly`() {
        val task = TaggedTask(
            Task(
                id = 4,
                title = "Point task",
                plannedDateEpochDay = date.toEpochDay(),
                plannedStartMinuteOfDay = 14 * 60,
                createdAtMillis = dayStart,
                updatedAtMillis = dayStart,
            ),
        )

        val entry = assemble(
            tasks = listOf(task),
            nowMillis = at(12),
        ).single { it.entryType == TimelineEntryType.TASK_PLAN_POINT }

        assertEquals(at(14), entry.startAtMillis)
        assertNull(entry.endAtMillis)
        assertFalse(entry.isTimedSpan)
    }

    @Test
    fun `cancelled task is absent from final Today assembly`() {
        val cancelled = TaggedTask(
            Task(
                id = 5,
                title = "Cancelled task",
                status = TaskStatus.CANCELLED,
                plannedDateEpochDay = date.toEpochDay(),
                plannedStartMinuteOfDay = 14 * 60,
                completedAtMillis = at(13),
                createdAtMillis = dayStart,
                updatedAtMillis = dayStart,
            ),
        )

        assertTrue(assemble(tasks = listOf(cancelled), nowMillis = at(17)).isEmpty())
    }

    @Test
    fun `timed event keeps planned span through final Today assembly`() {
        val event = TaggedEvent(
            Event(
                id = 7,
                title = "Timed event",
                dateEpochDay = date.toEpochDay(),
                startMinuteOfDay = 14 * 60,
                endMinuteOfDay = 16 * 60,
                createdAtMillis = dayStart,
                updatedAtMillis = dayStart,
            ),
        )

        val entry = assemble(events = listOf(event), nowMillis = at(12))
            .single { it.entryType == TimelineEntryType.EVENT_BLOCK }

        assertEquals(at(14), entry.startAtMillis)
        assertEquals(at(16), entry.endAtMillis)
        assertEquals(TimelineState.PLANNED, entry.state)
        assertTrue(entry.isTimedSpan)
    }

    @Test
    fun `point record keeps actual side tags and task relation through final Today assembly`() {
        val parent = Task(id = 90, title = "Linked task", createdAtMillis = dayStart,
            updatedAtMillis = dayStart)
        val point = TaggedSession(Session(id = 9, title = "取快递",
            temporalType = SessionTemporalType.POINT, startAtMillis = at(10),
            endAtMillis = null, taskId = parent.id, createdAtMillis = dayStart,
            updatedAtMillis = at(10)), tagIds = setOf(70))
        val classificationLabels = ClassificationLabels(
            categoriesById = emptyMap(),
            tagNames = mapOf(70L to "Focus"),
            tasks = mapOf(parent.id to parent),
        )

        val entry = assemble(
            sessions = listOf(point),
            classificationLabels = classificationLabels,
            nowMillis = at(12),
        ).single { it.entryType == TimelineEntryType.SESSION_POINT }

        assertEquals(TimelineState.ACTUAL, entry.state)
        assertEquals(listOf("Focus"), entry.tagLabels)
        assertEquals("Linked task", entry.relatedTaskTitle)
        assertFalse(entry.isPlan)
        assertFalse(entry.isActive)
        assertNull(entry.durationLabel)
        assertNull(entry.endAtMillis)
    }

    @Test
    fun `session before default awake time survives final Today assembly without real boundaries`() {
        val early = TaggedSession(
            Session(
                id = 10,
                title = "Early session",
                startAtMillis = at(3),
                endAtMillis = at(4),
                createdAtMillis = dayStart,
                updatedAtMillis = at(4),
            ),
        )

        val entry = assemble(sessions = listOf(early), nowMillis = at(12))
            .single { it.entryType == TimelineEntryType.SESSION_BLOCK }

        assertEquals(at(3), entry.startAtMillis)
        assertEquals(at(4), entry.endAtMillis)
    }

    @Test
    fun `same-time semantic order survives metadata enrichment`() {
        val session = TaggedSession(
            Session(
                id = 11,
                title = "Session",
                startAtMillis = at(10),
                endAtMillis = at(11),
                createdAtMillis = dayStart,
                updatedAtMillis = at(11),
            ),
        )
        val completion = TaggedTask(
            Task(
                id = 12,
                title = "Completion",
                status = TaskStatus.DONE,
                completedAtMillis = at(10),
                completionSource = TaskCompletionSource.MANUAL,
                createdAtMillis = dayStart,
                updatedAtMillis = at(10),
            ),
        )
        val plan = TaggedTask(
            Task(
                id = 13,
                title = "Plan",
                plannedDateEpochDay = date.toEpochDay(),
                plannedStartMinuteOfDay = 10 * 60,
                createdAtMillis = dayStart,
                updatedAtMillis = dayStart,
            ),
        )

        val entries = assemble(
            sessions = listOf(session, TaggedSession(Session(id = 10, title = "Point",
                temporalType = SessionTemporalType.POINT, startAtMillis = at(10),
                endAtMillis = null, createdAtMillis = dayStart, updatedAtMillis = dayStart))),
            tasks = listOf(completion, plan),
            nowMillis = at(12),
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

    private fun assemble(
        sessions: List<TaggedSession> = emptyList(),
        events: List<TaggedEvent> = emptyList(),
        tasks: List<TaggedTask> = emptyList(),
        activeSession: TaggedSession? = null,
        classificationLabels: ClassificationLabels = labels,
        nowMillis: Long,
    ): List<TimelineEntryUiModel> {
        val projected = TimelineProjector.project(
            awakeDay = awake,
            sessions = sessions,
            events = events,
            tasks = tasks,
            asOfMillis = nowMillis,
            zoneId = zone,
        )
        assertTrue(projected is DomainResult.Success)

        return TodayUiMapper.map(
            date = date,
            awakeDay = awake,
            entries = (projected as DomainResult.Success<List<TimelineEntry>>).value,
            activeSession = activeSession,
            labels = classificationLabels,
            planItems = emptyList(),
            displayMode = TimelineDisplayMode.OVERVIEW,
            nowMillis = nowMillis,
            zoneId = zone,
            locale = Locale.ENGLISH,
            metadataSources = TimelineMetadataProjector.project(
                tasks = tasks.map { it.task },
                sessions = sessions,
                events = events.map { it.event },
            ),
        ).timelineEntries
    }

    private fun at(hour: Int, minute: Int = 0): Long =
        dayStart + hour * 3_600_000L + minute * 60_000L
}
