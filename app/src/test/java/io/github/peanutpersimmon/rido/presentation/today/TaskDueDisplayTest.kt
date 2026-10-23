package io.github.peanutpersimmon.rido.presentation.today

import io.github.peanutpersimmon.rido.domain.model.Task
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class TaskDueDisplayTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val today = LocalDate.of(2026, 10, 4)
    private fun now(hour: Int, minute: Int) =
        today.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()
    private fun task(due: LocalDate, minute: Int? = null) = Task(
        id = 1, title = "看文献", plannedDateEpochDay = today.toEpochDay(),
        plannedStartMinuteOfDay = 8 * 60 + 20, dueDateEpochDay = due.toEpochDay(),
        dueMinuteOfDay = minute, estimatedDurationMinutes = 60,
        createdAtMillis = now(8, 0), updatedAtMillis = now(8, 0))

    @Test fun dueTodayIsProminentBeforeDeadlineAndOverdueAfterward() {
        val item = task(today, 10 * 60 + 50)
        assertEquals("10:50", TaskDueDisplay.prominent(item, today, now(9, 0), zone))
        assertEquals("10:50",
            TaskDueDisplay.prominent(item, today, now(12, 30), zone))
        assertEquals(60, item.estimatedDurationMinutes)
        assertEquals(8 * 60 + 20, item.plannedStartMinuteOfDay)
    }

    @Test fun futureDueStaysInDetailAndDueOnlyTodayHasVisibleCopy() {
        assertNull(TaskDueDisplay.prominent(task(today.plusDays(3)), today, now(12, 0), zone))
        assertEquals("10/7", TaskDueDisplay.value(task(today.plusDays(3)), today))
        assertEquals("10/7 12:00", TaskDueDisplay.value(task(today.plusDays(3), 12 * 60), today))
        val dueOnly = task(today).copy(plannedDateEpochDay = null, plannedStartMinuteOfDay = null)
        assertEquals("10/4", TaskDueDisplay.prominent(dueOnly, today, now(12, 0), zone))
    }

    @Test fun dateOnlyDueDoesNotBecomeOverdueUntilFollowingDay() {
        val dueOnly = task(today).copy(plannedDateEpochDay = null, plannedStartMinuteOfDay = null)
        assertEquals("10/4", TaskDueDisplay.prominent(dueOnly, today, now(23, 59), zone))
        val next = today.plusDays(1)
        val nextNoon = next.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        assertEquals("10/4",
            TaskDueDisplay.prominent(dueOnly, next, nextNoon, zone))
    }
}
