package io.github.peanutpersimmon.rido.presentation.today

import io.github.peanutpersimmon.rido.domain.model.*
import io.github.peanutpersimmon.rido.domain.usecase.*
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class TimelineEndpointMapperTest {
    private val zone = ZoneId.of("UTC")
    private val labels = ClassificationLabels(emptyMap(), emptyMap())
    private fun ui(type: TimelineEntryType, start: Long, end: Long?) =
        TimelineEntryUiModel(1, start, end, "test", "", null, type, TimelineState.PLANNED,
            null, emptyList(), null, null, false)

    @Test fun rawSourcesCannotCreateEntries() {
        val task = Task(
            id = 1,
            title = "raw task",
            plannedDateEpochDay = 0,
            plannedStartMinuteOfDay = 60,
            createdAtMillis = 0,
            updatedAtMillis = 0,
        )

        val result = TimelineEndpointMapper.map(
            emptyList(),
            TimelineMetadataSources(
                tasksById = mapOf(task.id to task),
                sourceEventTitlesBySessionId = emptyMap(),
            ),
            labels,
            zone,
            java.time.LocalDate.ofEpochDay(0),
            3_600_000L,
        )

        assertTrue(result.isEmpty())
    }
    @Test fun activeSessionRetainsProjectedEffectiveEnd() {
        val result = TimelineEndpointMapper.map(listOf(ui(TimelineEntryType.SESSION_BLOCK, 100, 900)),
            TimelineMetadataSources(emptyMap(), emptyMap()), labels, zone, java.time.LocalDate.ofEpochDay(0), 3_600_000L).single()
        assertEquals(listOf(100L, 900L), result.axisTimes)
        assertEquals(900L, result.endAtMillis)
    }
}

