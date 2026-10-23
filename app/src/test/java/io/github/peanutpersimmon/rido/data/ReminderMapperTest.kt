package io.github.peanutpersimmon.rido.data

import io.github.peanutpersimmon.rido.data.mapper.toDomain
import io.github.peanutpersimmon.rido.data.mapper.toEntity
import io.github.peanutpersimmon.rido.domain.model.Event
import io.github.peanutpersimmon.rido.domain.model.Task
import org.junit.Assert.assertEquals
import org.junit.Test

class ReminderMapperTest {
    @Test fun taskAndEventOffsetsSurviveRoomMapperRoundTrip() {
        val task = Task(title = "任务", plannedDateEpochDay = 1, plannedStartMinuteOfDay = 600,
            plannedReminderOffsetMinutes = 10, dueDateEpochDay = 1, dueMinuteOfDay = 720,
            dueReminderOffsetMinutes = 30, createdAtMillis = 1, updatedAtMillis = 1)
        val event = Event(title = "日程", dateEpochDay = 1, startMinuteOfDay = 840,
            startReminderOffsetMinutes = 60, createdAtMillis = 1, updatedAtMillis = 1)
        assertEquals(task, task.toEntity().toDomain())
        assertEquals(event, event.toEntity().toDomain())
    }
}
