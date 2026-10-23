package io.github.peanutpersimmon.rido.domain.usecase

import io.github.peanutpersimmon.rido.domain.model.Session
import io.github.peanutpersimmon.rido.domain.model.SessionKind
import io.github.peanutpersimmon.rido.domain.model.SessionTemporalType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecordEditorDefaultsTest {
    private fun row(kind: SessionKind, start: Long, end: Long?) = Session(
        title = "事实", kind = kind, startAtMillis = start, endAtMillis = end,
        createdAtMillis = 1, updatedAtMillis = 1,
    )

    @Test fun latestCompletedActivityOrNapEndBecomesEditableStart() {
        val rows = listOf(
            row(SessionKind.ACTIVITY, 10, 20),
            row(SessionKind.NAP, 25, 35),
            row(SessionKind.NIGHT_SLEEP, 0, 39),
            row(SessionKind.ACTIVITY, 36, null),
            row(SessionKind.ACTIVITY, 40, 55),
        )
        assertEquals(35L, latestPreviousActualEnd(rows, 0, 50))
        assertNull(latestPreviousActualEnd(rows, 36, 50))
    }

    @Test fun pointFactsAndCurrentEndpointDoNotCreateInvalidDefaultInterval() {
        val point = row(SessionKind.ACTIVITY, 20, null).copy(temporalType = SessionTemporalType.POINT)
        assertNull(latestPreviousActualEnd(listOf(point, row(SessionKind.NAP, 30, 50)), 0, 50))
    }
}
