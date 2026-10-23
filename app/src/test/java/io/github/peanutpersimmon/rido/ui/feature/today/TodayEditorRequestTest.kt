package io.github.peanutpersimmon.rido.ui.feature.today

import io.github.peanutpersimmon.rido.domain.model.TimelineEntryType
import io.github.peanutpersimmon.rido.domain.model.TimelineState
import io.github.peanutpersimmon.rido.presentation.today.TimelineEntryUiModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class TodayEditorRequestTest {
    @Test fun sessionPointOpensExistingRecordEditor() {
        val request = todayEditorRequestFor(entry(TimelineEntryType.SESSION_POINT, 42))!!
        assertEquals(InlineCreateKind.SESSION, request.kind)
        assertEquals(42L, request.id)
        assertFalse(request.isActive)
        assertFalse(request.sleep)
    }

    @Test fun plannedPointOpensTaskAndMissingIdentityCannotCreateNewRecord() {
        assertEquals(InlineCreateKind.TASK,
            todayEditorRequestFor(entry(TimelineEntryType.TASK_PLAN_POINT, 7))?.kind)
        assertNull(todayEditorRequestFor(entry(TimelineEntryType.SESSION_POINT, null)))
    }

    private fun entry(type: TimelineEntryType, id: Long?) = TimelineEntryUiModel(
        sourceId = id, startAtMillis = 1, endAtMillis = null, title = "Point",
        timeLabel = "00:00", durationLabel = null, entryType = type,
        state = if (type == TimelineEntryType.TASK_PLAN_POINT) TimelineState.PLANNED else TimelineState.ACTUAL,
        categoryLabel = null, tagLabels = emptyList(), location = null, note = null,
        isActive = false,
    )
}
