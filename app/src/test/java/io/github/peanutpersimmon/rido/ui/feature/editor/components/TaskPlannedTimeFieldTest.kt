package io.github.peanutpersimmon.rido.ui.feature.editor.components

import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.*
import org.junit.Test

class TaskPlannedTimeFieldTest {
    private val date = LocalDate.of(2026, 10, 5)

    @Test fun summaryShowsOnlyDateOrOneTime() {
        assertNull(taskPlannedTimeSummary(TaskPlannedTimeValue(null, null)))
        assertEquals("10/5", taskPlannedTimeSummary(TaskPlannedTimeValue(date, null)))
        assertEquals("10/5 · 14:00", taskPlannedTimeSummary(TaskPlannedTimeValue(date, LocalTime.of(14, 0))))
    }

    @Test fun clearingStartPreservesDate() {
        val value = TaskPlannedTimeValue(date, LocalTime.of(14, 0))
        assertEquals(TaskPlannedTimeValue(date, null), value.withoutStart())
        assertEquals(TaskPlannedTimeValue(null, null), value.cleared())
    }
}

