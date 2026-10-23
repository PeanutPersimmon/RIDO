package io.github.peanutpersimmon.rido.presentation.today

import io.github.peanutpersimmon.rido.domain.model.Task
import io.github.peanutpersimmon.rido.domain.service.TaskTemporalPolicy
import java.time.LocalDate
import java.time.ZoneId

/** Compact Today copy only; Task due and overdue semantics remain in TaskTemporalPolicy. */
internal object TaskDueDisplay {
    fun prominent(task: Task, date: LocalDate, nowMillis: Long, zone: ZoneId): String? {
        val dueDay = task.dueDateEpochDay ?: return null
        val reference = TaskTemporalPolicy.reference(date, zone, nowMillis)
        val overdue = TaskTemporalPolicy.overdue(task, reference, zone)
        if (!overdue && dueDay != date.toEpochDay()) return null
        return value(task, date)
    }

    fun value(task: Task, date: LocalDate): String? {
        val dueDay = task.dueDateEpochDay ?: return null
        val dueDate = LocalDate.ofEpochDay(dueDay)
        val clock = task.dueMinuteOfDay?.let { "%02d:%02d".format(it / 60, it % 60) }
        return if (dueDay == date.toEpochDay() && clock != null) clock
            else "${dueDate.monthValue}/${dueDate.dayOfMonth}${clock?.let { " $it" }.orEmpty()}"
    }
}
