package io.github.peanutpersimmon.rido.ui.feature.plan

import io.github.peanutpersimmon.rido.domain.model.Session
import io.github.peanutpersimmon.rido.domain.model.SessionKind
import io.github.peanutpersimmon.rido.ui.opensSleepEditor
import org.junit.Assert.assertEquals
import org.junit.Test

class PlanRecordClickTest {
    @Test fun activityClickCarriesKindAndOpensRegularEditor() {
        assertRecordClick(SessionKind.ACTIVITY, expectedSleepEditor = false)
    }

    @Test fun nightSleepClickCarriesKindAndOpensSleepEditor() {
        assertRecordClick(SessionKind.NIGHT_SLEEP, expectedSleepEditor = true)
    }

    @Test fun napClickCarriesKindAndKeepsSleepEditorBehavior() {
        assertRecordClick(SessionKind.NAP, expectedSleepEditor = true)
    }

    private fun assertRecordClick(kind: SessionKind, expectedSleepEditor: Boolean) {
        val record = Session(
            id = 42L,
            title = "Record",
            kind = kind,
            startAtMillis = 1_000L,
            endAtMillis = 2_000L,
            createdAtMillis = 1_000L,
            updatedAtMillis = 2_000L,
        )
        var clickedId: Long? = null
        var clickedKind: SessionKind? = null

        dispatchRecordClick(record) { id, actualKind ->
            clickedId = id
            clickedKind = actualKind
        }

        assertEquals(record.id, clickedId)
        assertEquals(kind, clickedKind)
        assertEquals(expectedSleepEditor, opensSleepEditor(requireNotNull(clickedKind)))
    }
}
