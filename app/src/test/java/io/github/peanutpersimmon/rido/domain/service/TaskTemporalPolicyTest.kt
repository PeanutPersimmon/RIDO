package io.github.peanutpersimmon.rido.domain.service

import io.github.peanutpersimmon.rido.domain.model.*
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class TaskTemporalPolicyTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val today = LocalDate.of(2026, 9, 22)
    private fun at(date: LocalDate, hour: Int = 0) = date.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()
    private val now get() = at(today, 18)
    private fun task() = Task(id = 1, title = "task", createdAtMillis = at(today, 9), updatedAtMillis = at(today, 9))

    @Test fun dueOnlyStartsAtCreationNotDeadline() {
        val t = task().copy(dueDateEpochDay = today.plusDays(5).toEpochDay())
        assertTrue(TaskTemporalPolicy.currentPlan(t, today, zone, now))
        assertFalse(TaskTemporalPolicy.currentPlan(t, today.minusDays(1), zone, now))
        assertTrue(TaskTemporalPolicy.currentPlan(t, today.plusDays(6), zone, now))
    }
    @Test fun noDateCompletionDoesNotInventAPlan() {
        val t = task().copy(completedAtMillis = now, status = TaskStatus.DONE)
        assertFalse(TaskTemporalPolicy.currentPlan(t, today, zone, now))
        assertEquals(listOf(t), TaskTemporalPolicy.completionPoints(listOf(t), emptyList(), at(today), at(today.plusDays(1))))
    }
    @Test fun historicalCompletionAndOverdueUseReferenceDate() {
        val t = task().copy(createdAtMillis = at(today.minusDays(4)), dueDateEpochDay = today.minusDays(2).toEpochDay(),
            status = TaskStatus.DONE, completedAtMillis = at(today, 15))
        val beforeDue = TaskTemporalPolicy.reference(today.minusDays(3), zone, now)
        val overdueDay = TaskTemporalPolicy.reference(today.minusDays(1), zone, now)
        assertFalse(TaskTemporalPolicy.completed(t, beforeDue))
        assertFalse(TaskTemporalPolicy.overdue(t, beforeDue, zone))
        assertTrue(TaskTemporalPolicy.overdue(t, overdueDay, zone))
        assertTrue(TaskTemporalPolicy.currentPlan(t, today, zone, now))
        assertTrue(TaskTemporalPolicy.completed(t, now))
        assertFalse(TaskTemporalPolicy.overdue(t, now, zone))
        assertFalse(TaskTemporalPolicy.currentPlan(t, today.plusDays(1), zone, now))
    }
    @Test fun dateOnlyDeadlineExpiresAtFollowingMidnight() {
        val t = task().copy(dueDateEpochDay = today.toEpochDay())
        val midnight = at(today.plusDays(1))
        assertFalse(TaskTemporalPolicy.overdue(t, midnight - 1, zone))
        assertTrue(TaskTemporalPolicy.overdue(t, midnight, zone))
    }
    @Test fun timedDeadlineDoesNotWaitUntilMidnight() {
        val t = task().copy(dueDateEpochDay = today.toEpochDay(), dueMinuteOfDay = 12 * 60)
        assertFalse(TaskTemporalPolicy.overdue(t, at(today, 12), zone))
        assertTrue(TaskTemporalPolicy.overdue(t, at(today, 12) + 1, zone))
    }
    @Test fun startDateGatesPlanAndCompletionDayIsRetained() {
        val t = task().copy(plannedDateEpochDay = today.plusDays(1).toEpochDay())
        assertFalse(TaskTemporalPolicy.currentPlan(t, today, zone, now))
        assertTrue(TaskTemporalPolicy.currentPlan(t, today.plusDays(1), zone, now))
    }
    @Test fun dedupRequiresBothTaskIdentityAndExactEnd() {
        val t = task().copy(completedAtMillis = now, status = TaskStatus.DONE)
        val s = Session(id = 2, title = "actual", taskId = t.id, startAtMillis = now - 1000,
            endAtMillis = now, createdAtMillis = now - 1000, updatedAtMillis = now)
        fun points(record: Session) = TaskTemporalPolicy.completionPoints(listOf(t), listOf(record), at(today), at(today.plusDays(1)))
        assertTrue(points(s).isEmpty())
        assertEquals(1, points(s.copy(taskId = 99)).size)
        assertEquals(1, points(s.copy(endAtMillis = now - 1)).size)
    }
    @Test fun reopenedTaskHasNoCompletionPoint() {
        val t = task().copy(status = TaskStatus.IN_PROGRESS, dueDateEpochDay = today.minusDays(1).toEpochDay())
        assertFalse(TaskTemporalPolicy.completed(t, now))
        assertTrue(TaskTemporalPolicy.overdue(t, now, zone))
        assertTrue(TaskTemporalPolicy.completionPoints(listOf(t), emptyList(), at(today), at(today.plusDays(1))).isEmpty())
    }

    @Test fun cancelledTaskIsExcludedFromPlansRegardlessOfCancellationTimestamp() {
        val planned = task().copy(plannedDateEpochDay = today.toEpochDay())
        val withoutTimestamp = planned.copy(status = TaskStatus.CANCELLED, cancelledAtMillis = null)
        val withTimestamp = planned.copy(status = TaskStatus.CANCELLED, cancelledAtMillis = at(today, 12))

        assertFalse(TaskTemporalPolicy.currentPlan(withoutTimestamp, today, zone, now))
        assertFalse(TaskTemporalPolicy.currentPlan(withTimestamp, today, zone, now))
    }

    @Test fun planVisibilityKeepsTodoInProgressAndDoneButExcludesCancelled() {
        assertTrue(TaskTemporalPolicy.isVisibleInPlan(task().copy(status = TaskStatus.TODO)))
        assertTrue(TaskTemporalPolicy.isVisibleInPlan(task().copy(status = TaskStatus.IN_PROGRESS)))
        assertTrue(TaskTemporalPolicy.isVisibleInPlan(task().copy(status = TaskStatus.DONE)))
        assertFalse(TaskTemporalPolicy.isVisibleInPlan(task().copy(status = TaskStatus.CANCELLED)))
    }

    @Test fun cancelledTaskNeverCreatesACompletionPoint() {
        val cancelled = task().copy(
            status = TaskStatus.CANCELLED,
            completedAtMillis = now,
            cancelledAtMillis = null,
        )

        assertTrue(
            TaskTemporalPolicy.completionPoints(
                listOf(cancelled),
                emptyList(),
                at(today),
                at(today.plusDays(1)),
            ).isEmpty(),
        )
    }

    @Test fun childrenCompletedParentHasStateButNoFactWhileManualParentHasFact() {
        val parent = task().copy(status = TaskStatus.DONE, completedAtMillis = now,
            completionSource = TaskCompletionSource.CHILDREN)
        assertTrue(TaskTemporalPolicy.completed(parent, now))
        assertTrue(TaskTemporalPolicy.completionPoints(listOf(parent), emptyList(), at(today),
            at(today.plusDays(1))).isEmpty())
        assertEquals(listOf(parent.copy(completionSource = TaskCompletionSource.MANUAL)),
            TaskTemporalPolicy.completionPoints(listOf(parent.copy(completionSource = TaskCompletionSource.MANUAL)),
                emptyList(), at(today), at(today.plusDays(1))))
    }
}
