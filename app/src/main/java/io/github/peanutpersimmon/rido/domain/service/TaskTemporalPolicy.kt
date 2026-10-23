package io.github.peanutpersimmon.rido.domain.service

import io.github.peanutpersimmon.rido.domain.model.*
import io.github.peanutpersimmon.rido.domain.time.epochDayMinuteToMillis
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Snapshot-derived state; no persisted overdue or completion entity. */
object TaskTemporalPolicy {
    fun reference(date: LocalDate, zone: ZoneId, now: Long): Long =
        if (Instant.ofEpochMilli(now).atZone(zone).toLocalDate() == date) now
        else date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1

    fun exists(task: Task, reference: Long) = task.createdAtMillis <= reference
    fun completed(task: Task, reference: Long) = task.completedAtMillis?.let { it <= reference } == true
    fun isVisibleInPlan(task: Task) = task.status != TaskStatus.CANCELLED

    fun currentPlan(task: Task, date: LocalDate, zone: ZoneId, now: Long): Boolean {
        val ref = reference(date, zone, now)
        if (!exists(task, ref) || !isVisibleInPlan(task)) return false
        val completionDate = task.completedAtMillis?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
        if (completionDate != null && completionDate < date) return false
        return task.plannedDateEpochDay?.let { it <= date.toEpochDay() } ?: (task.dueDateEpochDay != null)
    }

    fun overdue(task: Task, reference: Long, zone: ZoneId): Boolean {
        if (!exists(task, reference) || completed(task, reference) || !isVisibleInPlan(task)) return false
        val day = task.dueDateEpochDay ?: return false
        val deadline = task.dueMinuteOfDay?.let { epochDayMinuteToMillis(day, it, zone) }
            ?: LocalDate.ofEpochDay(day).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return if (task.dueMinuteOfDay == null) reference >= deadline else reference > deadline
    }

    fun completionPoints(tasks: List<Task>, sessions: List<Session>, start: Long, end: Long): List<Task> =
        tasks.filter { task ->
            val at = task.completedAtMillis
            isVisibleInPlan(task) && task.completionSource != TaskCompletionSource.CHILDREN &&
                at != null && at >= start && at < end &&
                sessions.none { it.taskId == task.id && it.endAtMillis == at }
        }.sortedBy { it.completedAtMillis }

    fun dueLabel(task: Task, reference: Long, zone: ZoneId): String? {
        val day = task.dueDateEpochDay ?: return null
        val date = LocalDate.ofEpochDay(day)
        if (overdue(task, reference, zone)) {
            val days = java.time.temporal.ChronoUnit.DAYS.between(date, Instant.ofEpochMilli(reference).atZone(zone).toLocalDate())
            return if (days > 0) "逾期 $days 天" else "已逾期"
        }
        val time = task.dueMinuteOfDay?.let { " %02d:%02d".format(it / 60, it % 60) }.orEmpty()
        return "截止 ${date.monthValue}/${date.dayOfMonth}$time"
    }
}
