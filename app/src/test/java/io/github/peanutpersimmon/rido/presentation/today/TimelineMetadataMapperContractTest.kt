package io.github.peanutpersimmon.rido.presentation.today

import io.github.peanutpersimmon.rido.domain.model.Event
import io.github.peanutpersimmon.rido.domain.model.Session
import io.github.peanutpersimmon.rido.domain.model.Task
import io.github.peanutpersimmon.rido.domain.model.TaskStatus
import io.github.peanutpersimmon.rido.domain.model.TimelineEntryType
import io.github.peanutpersimmon.rido.domain.model.TimelineState
import io.github.peanutpersimmon.rido.domain.usecase.ClassificationLabels
import io.github.peanutpersimmon.rido.domain.usecase.TimelineMetadataSources
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineMetadataMapperContractTest {
    private val zone = ZoneId.of("UTC")
    private val date = LocalDate.ofEpochDay(0)
    private val labels = ClassificationLabels(emptyMap(), emptyMap())

    @Test fun metadataEnrichmentPreservesCountOrderAndEveryCanonicalField() {
        val entries = listOf(
            ui(1, TimelineEntryType.SESSION_BLOCK, TimelineState.ACTUAL, 100, 900),
            ui(2, TimelineEntryType.SESSION_POINT, TimelineState.ACTUAL, 100, null),
            ui(3, TimelineEntryType.TASK_PLAN_POINT, TimelineState.PLANNED, 100, 300),
            ui(4, TimelineEntryType.EVENT_BLOCK, TimelineState.PLANNED, 100, null),
            ui(5, TimelineEntryType.EVENT_BLOCK, TimelineState.PLANNED, 100, 400),
        )
        val sources = TimelineMetadataSources(
            tasksById = mapOf(
                3L to Task(
                    id = 3,
                    title = "Metadata task",
                    createdAtMillis = 0,
                    updatedAtMillis = 0,
                ),
            ),
            sourceEventTitlesBySessionId = mapOf(1L to "Source event", 2L to "Point source"),
        )

        val result = map(entries, sources)

        assertEquals(entries.size, result.size)
        assertEquals(entries.map(::core), result.map(::core))
        assertEquals(entries.map { it.sourceId }, result.map { it.sourceId })
        assertEquals(entries.map { it.title }, result.map { it.title })
        assertEquals(entries.map { it.tagLabels }, result.map { it.tagLabels })
    }

    @Test fun taskSourceAddsOnlyDisplayMetadata() {
        val parent = Task(id = 99, title = "Parent", createdAtMillis = 0, updatedAtMillis = 0)
        val task = Task(
            id = 3,
            title = "Raw task title",
            status = TaskStatus.DONE,
            parentTaskId = parent.id,
            plannedDateEpochDay = 0,
            plannedStartMinuteOfDay = 60,
            dueDateEpochDay = 0,
            dueMinuteOfDay = 180,
            estimatedDurationMinutes = 90,
            createdAtMillis = 0,
            updatedAtMillis = 0,
            completedAtMillis = 1_000,
        )
        val entry = ui(3, TimelineEntryType.TASK_PLAN_POINT, TimelineState.PLANNED, 10, 20)
        val result = TimelineEndpointMapper.map(
            entries = listOf(entry),
            sources = TimelineMetadataSources(mapOf(task.id to task), emptyMap()),
            labels = labels.copy(tasks = mapOf(parent.id to parent)),
            zone = zone,
            date = date,
            nowMillis = 3_600_000L,
        ).single()

        assertEquals(core(entry), core(result))
        assertEquals("Parent", result.relatedTaskTitle)
        assertEquals("1h 30m", result.estimatedDurationLabel)
        assertEquals("03:00", result.dueLabel)
        assertTrue(result.completed)
        assertTrue(!result.isOverdue)
    }

    @Test fun sessionSourceAddsEventTitleWithoutRebuildingTheEntry() {
        val event = Event(
            id = 20,
            title = "Source event",
            dateEpochDay = 0,
            createdAtMillis = 0,
            updatedAtMillis = 0,
        )
        val session = Session(
            id = 1,
            title = "Conflicting raw title",
            startAtMillis = 500,
            endAtMillis = null,
            categoryId = 88,
            sourceEventId = event.id,
            createdAtMillis = 0,
            updatedAtMillis = 0,
        )
        val entry = ui(1, TimelineEntryType.SESSION_BLOCK, TimelineState.ACTUAL, 100, 900)
            .copy(title = "Canonical title", categoryLabel = "Canonical category")

        val result = map(
            listOf(entry),
            TimelineMetadataSources(emptyMap(), mapOf(session.id to event.title)),
        ).single()

        assertEquals(entry, result.copy(sourceEventTitle = null))
        assertEquals("Source event", result.sourceEventTitle)
    }

    @Test fun overdueTaskHasMarkerAndDueValueButCompletedTaskDoesNot() {
        val task = Task(id = 8, title = "看文献", plannedDateEpochDay = date.toEpochDay(),
            plannedStartMinuteOfDay = 60, dueDateEpochDay = date.toEpochDay(),
            dueMinuteOfDay = 180, createdAtMillis = 0, updatedAtMillis = 0)
        val entry = ui(8, TimelineEntryType.TASK_PLAN_POINT, TimelineState.PLANNED, 100, null)
        val overdue = TimelineEndpointMapper.map(listOf(entry),
            TimelineMetadataSources(mapOf(task.id to task), emptyMap()), labels, zone, date,
            4 * 60 * 60 * 1000L).single()
        assertTrue(overdue.isOverdue)
        assertEquals("03:00", overdue.prominentDueLabel)
        assertTrue(!overdue.dueLabel.orEmpty().contains("截止"))
        assertTrue(!overdue.dueLabel.orEmpty().contains("逾期"))
        val completed = TimelineEndpointMapper.map(listOf(entry),
            TimelineMetadataSources(mapOf(task.id to task.copy(status = TaskStatus.DONE,
                completedAtMillis = 3 * 60 * 60 * 1000L)), emptyMap()), labels, zone, date,
            4 * 60 * 60 * 1000L).single()
        assertTrue(!completed.isOverdue)
    }

    @Test fun pointRecordKeepsActualStateTagsAndTaskMetadata() {
        val entry = ui(7, TimelineEntryType.SESSION_POINT, TimelineState.ACTUAL, 700, null)
            .copy(
                tagLabels = listOf("kept-tag"),
                relatedTaskTitle = "Kept parent",
                completed = false,
            )
        val result = map(
            listOf(entry),
            TimelineMetadataSources(emptyMap(), mapOf(7L to "Source event")),
        ).single()

        assertEquals(core(entry), core(result))
        assertEquals(TimelineState.ACTUAL, result.state)
        assertEquals(listOf("kept-tag"), result.tagLabels)
        assertEquals("Kept parent", result.relatedTaskTitle)
        assertEquals("Source event", result.sourceEventTitle)
        assertTrue(!result.completed)
        assertNull(result.endAtMillis)
    }

    private fun map(
        entries: List<TimelineEntryUiModel>,
        sources: TimelineMetadataSources,
    ): List<TimelineEntryUiModel> = TimelineEndpointMapper.map(
        entries = entries,
        sources = sources,
        labels = labels,
        zone = zone,
        date = date,
        nowMillis = 3_600_000L,
    )

    private fun ui(
        id: Long,
        type: TimelineEntryType,
        state: TimelineState,
        start: Long,
        end: Long?,
    ) = TimelineEntryUiModel(
        sourceId = id,
        startAtMillis = start,
        endAtMillis = end,
        title = "Canonical $id",
        timeLabel = "canonical-time-$id",
        durationLabel = end?.let { "canonical-duration-$id" },
        entryType = type,
        state = state,
        categoryLabel = "category-$id",
        tagLabels = listOf("tag-$id"),
        location = "location-$id",
        note = "note-$id",
        isActive = false,
        axisTimes = listOfNotNull(start, end),
    )

    private fun core(entry: TimelineEntryUiModel) = CanonicalCore(
        sourceId = entry.sourceId,
        startAtMillis = entry.startAtMillis,
        endAtMillis = entry.endAtMillis,
        entryType = entry.entryType,
        state = entry.state,
        axisTimes = entry.axisTimes,
    )

    private data class CanonicalCore(
        val sourceId: Long?,
        val startAtMillis: Long,
        val endAtMillis: Long?,
        val entryType: TimelineEntryType,
        val state: TimelineState,
        val axisTimes: List<Long>,
    )
}
