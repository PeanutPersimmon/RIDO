package io.github.peanutpersimmon.rido.reminder

import io.github.peanutpersimmon.rido.domain.model.Event
import io.github.peanutpersimmon.rido.domain.model.Task
import io.github.peanutpersimmon.rido.domain.model.TaskStatus
import io.github.peanutpersimmon.rido.domain.validation.DataValidator
import io.github.peanutpersimmon.rido.domain.validation.ValidationError
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class ReminderPlanTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val day = LocalDate.of(2026, 10, 5)
    private val midnight = day.atStartOfDay(zone).toInstant().toEpochMilli()
    private fun task() = Task(id = 7, title = "任务", plannedDateEpochDay = day.toEpochDay(),
        plannedStartMinuteOfDay = 600, dueDateEpochDay = day.toEpochDay(), dueMinuteOfDay = 720,
        plannedReminderOffsetMinutes = 10, dueReminderOffsetMinutes = 30,
        createdAtMillis = 1, updatedAtMillis = 1)
    private fun event() = Event(id = 9, title = "日程", dateEpochDay = day.toEpochDay(),
        startMinuteOfDay = 840, startReminderOffsetMinutes = 60,
        createdAtMillis = 1, updatedAtMillis = 1)

    @Test fun threeAnchorsProduceIndependentTriggerTimes() {
        val planned = ReminderPlan.forTask(task(), zone).single { it.anchor == ReminderAnchor.TASK_PLANNED }
        val due = ReminderPlan.forTask(task(), zone).single { it.anchor == ReminderAnchor.TASK_DUE }
        val start = ReminderPlan.forEvent(event(), zone).single()
        assertEquals(midnight + 9 * 3_600_000L + 50 * 60_000L, planned.triggerMillis)
        assertEquals(midnight + 11 * 3_600_000L + 30 * 60_000L, due.triggerMillis)
        assertEquals(midnight + 13 * 3_600_000L, start.triggerMillis)
        assertNotEquals(planned.key, due.key)
    }

    @Test fun globalDefaultsApplyWithoutEntityFieldsAndExplicitOffsetWins() {
        val defaults = ReminderDefaults(offsetMinutes = 30, dueDayMinuteOfDay = 18 * 60)
        val automatic = ReminderPlan.forEvent(event().copy(startReminderOffsetMinutes = null), zone, defaults).single()
        assertEquals(30, automatic.offsetMinutes)
        assertEquals(60, ReminderPlan.forEvent(event(), zone, defaults).single().offsetMinutes)
        val dueDay = ReminderPlan.forTask(task().copy(plannedDateEpochDay = null,
            plannedStartMinuteOfDay = null, plannedReminderOffsetMinutes = null,
            dueMinuteOfDay = null, dueReminderOffsetMinutes = null), zone, defaults).single()
        assertEquals(midnight + 18 * 3_600_000L, dueDay.triggerMillis)
    }

    @Test fun doneCancelledAndUndatedAreNotScheduledButDueDateOnlyUsesMorning() {
        assertTrue(ReminderPlan.forTask(task().copy(status = TaskStatus.DONE), zone).isEmpty())
        assertTrue(ReminderPlan.forTask(task().copy(status = TaskStatus.CANCELLED), zone).isEmpty())
        val dueDay = ReminderPlan.forTask(task().copy(plannedStartMinuteOfDay = null,
            plannedReminderOffsetMinutes = null, dueMinuteOfDay = null,
            dueReminderOffsetMinutes = null), zone).single()
        assertEquals(midnight + 9 * 3_600_000L, dueDay.triggerMillis)
        assertTrue(ReminderPlan.forTask(task().copy(plannedDateEpochDay = null,
            plannedStartMinuteOfDay = null, plannedReminderOffsetMinutes = null,
            dueDateEpochDay = null, dueMinuteOfDay = null, dueReminderOffsetMinutes = null), zone).isEmpty())
        assertTrue(ReminderPlan.forEvent(event().copy(startMinuteOfDay = null), zone).isEmpty())
        assertTrue(ReminderPlan.future(ReminderPlan.forTask(task(), zone), midnight + 13 * 3_600_000L).isEmpty())
    }

    @Test fun changedAnchorAndClosedReminderMakeOldAlarmStale() {
        val old = ReminderPlan.forTask(task(), zone).first()
        val moved = ReminderPlan.forTask(task().copy(plannedStartMinuteOfDay = 660), zone).first()
        assertEquals(old.key, moved.key)
        assertNotEquals(old.triggerMillis, moved.triggerMillis)
        assertFalse(ReminderPlan.matchesDeliveredAlarm(moved, old.anchorMillis, old.offsetMinutes, old.triggerMillis))
        assertFalse(ReminderPlan.matchesDeliveredAlarm(null, old.anchorMillis, old.offsetMinutes, old.triggerMillis))
        assertTrue(ReminderPlan.matchesDeliveredAlarm(old, old.anchorMillis, old.offsetMinutes, old.triggerMillis))
        assertEquals(10, ReminderPlan.forTask(task().copy(plannedReminderOffsetMinutes = null), zone)
            .single { it.anchor == ReminderAnchor.TASK_PLANNED }.offsetMinutes)
    }

    @Test fun offsetAndAnchorValidationRejectsInvalidConfig() {
        assertTrue(DataValidator.validateTask(task()).isEmpty())
        assertTrue(ValidationError.REMINDER_WITHOUT_ANCHOR in DataValidator.validateTask(
            task().copy(plannedStartMinuteOfDay = null)))
        assertTrue(ValidationError.REMINDER_WITHOUT_ANCHOR in DataValidator.validateTask(
            task().copy(dueMinuteOfDay = null)))
        assertTrue(ValidationError.INVALID_REMINDER_OFFSET in DataValidator.validateTask(
            task().copy(dueReminderOffsetMinutes = -1)))
        assertTrue(ValidationError.INVALID_REMINDER_OFFSET in DataValidator.validateEvent(
            event().copy(startReminderOffsetMinutes = 15)))
        assertTrue(ValidationError.REMINDER_WITHOUT_ANCHOR in DataValidator.validateEvent(
            event().copy(startMinuteOfDay = null)))
    }
}
