package io.github.peanutpersimmon.rido.reminder

import io.github.peanutpersimmon.rido.domain.model.Event
import io.github.peanutpersimmon.rido.domain.model.Task
import io.github.peanutpersimmon.rido.domain.model.TaskStatus
import io.github.peanutpersimmon.rido.domain.time.epochDayMinuteToMillis
import java.time.ZoneId

enum class ReminderAnchor { TASK_PLANNED, TASK_DUE, EVENT_START }

data class ScheduledReminder(
    val anchor: ReminderAnchor,
    val ownerId: Long,
    val anchorMillis: Long,
    val offsetMinutes: Int,
) {
    val triggerMillis: Long get() = anchorMillis - offsetMinutes * 60_000L
    val key: String get() = "${anchor.name}:$ownerId"
}

object ReminderPlan {
    fun future(reminders: Iterable<ScheduledReminder>, nowMillis: Long): List<ScheduledReminder> =
        reminders.filter { it.triggerMillis > nowMillis }

    fun matchesDeliveredAlarm(
        current: ScheduledReminder?, anchorMillis: Long, offsetMinutes: Int, triggerMillis: Long,
    ): Boolean = current != null && current.anchorMillis == anchorMillis &&
        current.offsetMinutes == offsetMinutes && current.triggerMillis == triggerMillis

    fun forTask(task: Task, zone: ZoneId, defaults: ReminderDefaults = ReminderDefaults()): List<ScheduledReminder> {
        if (task.status == TaskStatus.DONE || task.status == TaskStatus.CANCELLED) return emptyList()
        return buildList {
            val plannedDate = task.plannedDateEpochDay
            val plannedMinute = task.plannedStartMinuteOfDay
            val plannedOffset = task.plannedReminderOffsetMinutes ?: defaults.offsetMinutes
            if (plannedDate != null && plannedMinute != null && plannedOffset != null)
                add(ScheduledReminder(ReminderAnchor.TASK_PLANNED, task.id,
                    epochDayMinuteToMillis(plannedDate, plannedMinute, zone), plannedOffset))
            val dueDate = task.dueDateEpochDay
            val dueMinute = task.dueMinuteOfDay ?: defaults.dueDayMinuteOfDay
            val dueOffset = task.dueReminderOffsetMinutes ?: if (task.dueMinuteOfDay == null) 0 else defaults.offsetMinutes
            if (dueDate != null && dueMinute != null && dueOffset != null)
                add(ScheduledReminder(ReminderAnchor.TASK_DUE, task.id,
                    epochDayMinuteToMillis(dueDate, dueMinute, zone), dueOffset))
        }
    }

    fun forEvent(event: Event, zone: ZoneId, defaults: ReminderDefaults = ReminderDefaults()): List<ScheduledReminder> {
        val minute = event.startMinuteOfDay ?: return emptyList()
        val offset = event.startReminderOffsetMinutes ?: defaults.offsetMinutes
        return listOf(ScheduledReminder(ReminderAnchor.EVENT_START, event.id,
            epochDayMinuteToMillis(event.dateEpochDay, minute, zone), offset))
    }
}
