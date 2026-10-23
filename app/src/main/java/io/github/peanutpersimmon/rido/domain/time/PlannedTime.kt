package io.github.peanutpersimmon.rido.domain.time

import io.github.peanutpersimmon.rido.domain.model.Event
import java.time.LocalDate
import java.time.ZoneId

fun epochDayMinuteToMillis(epochDay: Long, minuteOfDay: Int, zoneId: ZoneId): Long =
    LocalDate.ofEpochDay(epochDay)
        .atStartOfDay(zoneId)
        .plusMinutes(minuteOfDay.toLong())
        .toInstant()
        .toEpochMilli()

fun Event.plannedInterval(zoneId: ZoneId): TimeInterval? {
    val startMinute = startMinuteOfDay ?: return null
    val endMinute = endMinuteOfDay ?: return null
    val start = epochDayMinuteToMillis(dateEpochDay, startMinute, zoneId)
    val end = epochDayMinuteToMillis(endDateEpochDay ?: dateEpochDay, endMinute, zoneId)
    return if (start < end) TimeInterval(start, end) else null
}


