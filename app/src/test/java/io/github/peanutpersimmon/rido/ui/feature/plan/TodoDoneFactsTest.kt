package io.github.peanutpersimmon.rido.ui.feature.plan

import io.github.peanutpersimmon.rido.domain.model.Event
import io.github.peanutpersimmon.rido.domain.model.Session
import io.github.peanutpersimmon.rido.domain.model.SessionKind
import io.github.peanutpersimmon.rido.domain.model.SessionTemporalType
import io.github.peanutpersimmon.rido.domain.model.Task
import io.github.peanutpersimmon.rido.domain.model.TaskStatus
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TodoDoneFactsTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val date = LocalDate.of(2026, 9, 26)

    @Test fun todoDoneItemsKeepTaskAndEventCategoryIds() {
        val taskItem = todoDoneTaskItem(task(7, 9 * 60).copy(categoryId = 31),
            date, zone, dayStart())
        val eventItem = todoDoneEventItem(event(8, "组会", 10 * 60).copy(categoryId = 32), zone)
        assertEquals(31L, taskItem.categoryId)
        assertEquals(32L, eventItem.categoryId)
        assertNull(todoDoneTaskItem(task(9, 11 * 60), date, zone, dayStart()).categoryId)
    }

    @Test fun todoDonePlannedEventKeepsLocationAndNoteForFullDetail() {
        val item = todoDoneEventItem(event(4, "组会", 900).copy(
            location = "会议室", note = "带材料"), zone)
        assertEquals("会议室", item.detail)
        assertEquals("带材料", item.note)
        assertTrue(item.atMillis != null)
    }

    @Test fun plannedTaskUsesCompactDueValueAndOverdueMarkerOnlyWhileOpen() {
        val due = task(12, 9 * 60).copy(dueDateEpochDay = date.toEpochDay(),
            dueMinuteOfDay = 12 * 60)
        val before = todoDoneTaskItem(due, date, zone, at(11))
        val after = todoDoneTaskItem(due, date, zone, at(13))
        assertEquals("12:00", before.detail)
        assertFalse(before.overdue)
        assertEquals("12:00", after.detail)
        assertTrue(after.overdue)
        val done = todoDoneTaskItem(due.copy(status = TaskStatus.DONE,
            completedAtMillis = at(11)), date, zone, at(13))
        assertFalse(done.overdue)
    }

    @Test fun todoDoneFullDetailIncludesAvailableTaskRelations() {
        val parent = task(8, 9 * 60).copy(title = "论文")
        val child = task(9, 10 * 60).copy(
            title = "修改摘要", parentTaskId = parent.id, note = "核对结论")
        val item = todoDoneTaskItem(child, date, zone,
            date.atStartOfDay(zone).toInstant().toEpochMilli())
        assertEquals(listOf("父任务：论文", "核对结论"),
            todoDoneDetails(item, listOf(parent, child)))

        val record = session(10, SessionKind.ACTIVITY, 200, 300).copy(
            taskId = child.id, location = "图书馆", note = "已完成")
        assertEquals(listOf("图书馆", "关联任务：修改摘要", "已完成"),
            todoDoneRecordDetails(record, listOf(parent, child)))
    }

    @Test fun pointCompletedActivityAndNapEnterDone() {
        val activity = session(1, SessionKind.ACTIVITY, 200, 300)
        val sleep = session(2, SessionKind.NIGHT_SLEEP, 100, 400)
        val nap = session(3, SessionKind.NAP, 250, 350)
        val point = session(4, SessionKind.ACTIVITY, 250, 250)
            .copy(temporalType = SessionTemporalType.POINT, endAtMillis = null)
        val active = session(5, SessionKind.ACTIVITY, 300, 400).copy(endAtMillis = null)
        val nextDayBoundary = point.copy(id = 6, startAtMillis = 500)

        assertEquals(
            listOf(activity, nap, point),
            visibleTodoDoneRecords(listOf(sleep, activity, nap, point, active, nextDayBoundary),
                axisStart = 0, axisEnd = 500),
        )
    }

    @Test fun pointAndIntervalCanShareAnAnchorWithoutMerging() {
        val first = session(1, SessionKind.ACTIVITY, 200, 200)
            .copy(temporalType = SessionTemporalType.POINT, endAtMillis = null)
        val second = first.copy(id = 2, title = "另一个点")
        val interval = session(3, SessionKind.ACTIVITY, 200, 300)
        assertEquals(listOf(first, second, interval),
            visibleTodoDoneRecords(listOf(first, second, interval), 0, 500))
        val inputs = listOf(first, second, interval).mapNotNull {
            todoDoneRecordInput(it, 0, 500, 32f, 12f)
        }
        assertEquals(3, inputs.size)
        assertTrue(inputs.none { it.side })
        assertEquals(listOf(null, null, 300L), inputs.map { it.end })
        assertEquals(listOf(200L, 200L, 200L), inputs.map { it.start })
        assertEquals(listOf(12f, 12f, 0f), inputs.map { it.anchorLead })
    }

    @Test fun recordClickKeepsSessionIdentityForPointAndInterval() {
        val opened = mutableListOf<Pair<Long, SessionKind>>()
        val point = session(4, SessionKind.ACTIVITY, 200, 200)
            .copy(temporalType = SessionTemporalType.POINT, endAtMillis = null)
        val interval = session(5, SessionKind.ACTIVITY, 200, 300)
        dispatchRecordClick(point) { id, kind -> opened += id to kind }
        dispatchRecordClick(interval) { id, kind -> opened += id to kind }
        assertEquals(listOf(4L to SessionKind.ACTIVITY, 5L to SessionKind.ACTIVITY), opened)
    }

    @Test fun taskCompletionWithoutRecordRemainsItsOwnFact() {
        val completedAt = at(17, 30)
        val completed = task(20, 14 * 60).copy(status = TaskStatus.DONE,
            completedAtMillis = completedAt)
        assertEquals(listOf(completed),
            todoDoneCompletionFacts(listOf(completed), emptyList(), dayStart(), dayEnd()))
    }

    @Test fun linkedIntervalWithExactEndSuppressesDuplicateCompletion() {
        val completedAt = at(17, 30)
        val completed = task(20, 14 * 60).copy(status = TaskStatus.DONE,
            completedAtMillis = completedAt)
        val record = session(30, SessionKind.ACTIVITY, at(16), completedAt)
            .copy(taskId = completed.id)
        assertTrue(todoDoneCompletionFacts(listOf(completed), listOf(record),
            dayStart(), dayEnd()).isEmpty())
        assertEquals(1, todoDoneCompletionFacts(listOf(completed),
            listOf(record.copy(endAtMillis = completedAt - 1)), dayStart(), dayEnd()).size)
    }

    @Test fun linkedPointAtExactTimeSuppressesDuplicateCompletion() {
        val completedAt = at(17, 26)
        val completed = task(20, 14 * 60).copy(status = TaskStatus.DONE,
            completedAtMillis = completedAt)
        val point = session(30, SessionKind.ACTIVITY, completedAt, completedAt)
            .copy(temporalType = SessionTemporalType.POINT, endAtMillis = null,
                taskId = completed.id)
        assertTrue(todoDoneCompletionFacts(listOf(completed), listOf(point),
            dayStart(), dayEnd()).isEmpty())
        assertEquals(1, todoDoneCompletionFacts(listOf(completed),
            listOf(point.copy(startAtMillis = completedAt - 1)), dayStart(), dayEnd()).size)
    }

    @Test fun sameTitleAndTimeWithoutTaskLinkRemainTwoFacts() {
        val completedAt = at(17, 26)
        val completed = task(20, 14 * 60).copy(title = "取快递",
            status = TaskStatus.DONE, completedAtMillis = completedAt)
        val point = session(30, SessionKind.ACTIVITY, completedAt, completedAt)
            .copy(title = "取快递", temporalType = SessionTemporalType.POINT,
                endAtMillis = null, taskId = null)
        assertEquals(listOf(point), visibleTodoDoneRecords(listOf(point), dayStart(), dayEnd()))
        assertEquals(listOf(completed), todoDoneCompletionFacts(listOf(completed),
            listOf(point), dayStart(), dayEnd()))
    }

    @Test fun plannedAndActualPointAndIntervalsKeepTheirOwnTimes() {
        val eventPoint = todoDoneEventItem(event(1, "组会", 15 * 60), zone)
        val pointRecord = session(2, SessionKind.ACTIVITY, at(15, 8), at(15, 8))
            .copy(temporalType = SessionTemporalType.POINT, endAtMillis = null)
        val eventInterval = todoDoneEventItem(event(3, "组会", 14 * 60)
            .copy(endMinuteOfDay = 15 * 60), zone)
        val intervalRecord = session(4, SessionKind.ACTIVITY, at(14, 18), at(15, 37))
            .copy(sourceEventId = 3)
        val plannedTask = task(5, 14 * 60)
        val taskRecord = intervalRecord.copy(id = 6, taskId = plannedTask.id,
            startAtMillis = at(14, 20), endAtMillis = at(15, 40))

        assertEquals(at(15), eventPoint.atMillis)
        assertEquals(at(15, 8), pointRecord.startAtMillis)
        assertEquals(at(14), eventInterval.atMillis)
        assertEquals(at(15), eventInterval.endAtMillis)
        assertEquals(at(14, 18), intervalRecord.startAtMillis)
        assertEquals(at(15, 37), intervalRecord.endAtMillis)
        assertEquals(at(14), todoDoneTaskItems(listOf(plannedTask), date, zone, at(18))
            .single().atMillis)
        assertEquals(at(14, 20), taskRecord.startAtMillis)
        assertEquals(at(15, 40), taskRecord.endAtMillis)
    }

    @Test fun todoDoneStartOnlyTaskRemainsAPointAndEstimateDoesNotCreateAnEnd() {
        val item = todoDoneTaskItem(
            task = task(2, startMinute = 9 * 60, estimate = 120),
            date = date,
            zoneId = zone,
            reference = date.atStartOfDay(zone).toInstant().toEpochMilli(),
        )

        assertNull(item.endAtMillis)
        assertFalse(item.isTimedSpan)
        assertEquals("预计 2h", item.estimate)
    }

    @Test fun todoDoneLeftPlanExcludesCancelledAndKeepsNormalTaskStates() {
        val now = date.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        val todo = task(3, 9 * 60).copy(status = TaskStatus.TODO)
        val inProgress = task(4, 10 * 60).copy(status = TaskStatus.IN_PROGRESS)
        val cancelled = task(5, 11 * 60).copy(status = TaskStatus.CANCELLED)

        assertEquals(
            listOf(todo.id, inProgress.id),
            todoDoneTaskItems(listOf(todo, inProgress, cancelled), date, zone, now).map { it.id },
        )
    }

    private fun event(id: Long, title: String, minute: Int) = Event(
        id = id,
        title = title,
        dateEpochDay = date.toEpochDay(),
        startMinuteOfDay = minute,
        createdAtMillis = 0,
        updatedAtMillis = 0,
    )

    private fun session(id: Long, kind: SessionKind, start: Long, end: Long) = Session(
        id = id,
        title = "记录",
        kind = kind,
        startAtMillis = start,
        endAtMillis = end,
        createdAtMillis = 0,
        updatedAtMillis = 0,
    )

    private fun task(
        id: Long,
        startMinute: Int,
        estimate: Int? = null,
    ) = Task(
        id = id,
        title = "任务",
        plannedDateEpochDay = date.toEpochDay(),
        plannedStartMinuteOfDay = startMinute,
        estimatedDurationMinutes = estimate,
        createdAtMillis = 0,
        updatedAtMillis = 0,
    )

    private fun dayStart() = date.atStartOfDay(zone).toInstant().toEpochMilli()
    private fun dayEnd() = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    private fun at(hour: Int, minute: Int = 0) = date.atTime(hour, minute)
        .atZone(zone).toInstant().toEpochMilli()
}
