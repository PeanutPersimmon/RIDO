package io.github.peanutpersimmon.rido.domain.validation

import io.github.peanutpersimmon.rido.domain.model.Session
import io.github.peanutpersimmon.rido.domain.model.SessionKind
import io.github.peanutpersimmon.rido.domain.model.SessionTemporalType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionOverlapTest {
    @Test fun sleepMustRemainIntervalWithoutOrdinaryMetadata() {
        for (kind in listOf(SessionKind.NIGHT_SLEEP, SessionKind.NAP)) {
            val sleep = session(id = 1, start = 100, end = 200).copy(kind = kind)
            assertTrue(DataValidator.validateSession(sleep).isEmpty())
            assertTrue(ValidationError.SESSION_SLEEP_POINT in DataValidator.validateSession(
                sleep.copy(temporalType = SessionTemporalType.POINT, endAtMillis = null)))
            listOf(sleep.copy(taskId = 2), sleep.copy(categoryId = 3), sleep.copy(sourceEventId = 4))
                .forEach { assertTrue(ValidationError.SESSION_SLEEP_METADATA in DataValidator.validateSession(it)) }
            assertTrue(ValidationError.SESSION_SLEEP_METADATA in DataValidator.validateSession(sleep, setOf(5)))
        }
    }

    @Test
    fun exactIntervals_overlap() = assertOverlap(100, 200, 100, 200)

    @Test
    fun partialIntervals_overlap() = assertOverlap(100, 200, 150, 250)

    @Test
    fun containingInterval_overlaps() = assertOverlap(100, 300, 150, 200)

    @Test
    fun adjacentIntervals_doNotOverlap() {
        assertFalse(DataValidator.sessionsOverlap(100, 200, 200, 300))
    }

    @Test
    fun separatedIntervals_doNotOverlap() {
        assertFalse(DataValidator.sessionsOverlap(100, 150, 200, 300))
    }

    @Test
    fun activeSession_overlapsLaterInterval() = assertOverlap(100, null, 1_000, 2_000)

    @Test
    fun intervalBeforeActiveSession_doesNotOverlap() {
        assertFalse(DataValidator.sessionsOverlap(100, 200, 200, null))
    }

    @Test
    fun crossMidnightInterval_usesAbsoluteOrdering() = assertOverlap(1_000, 10_000, 9_000, 11_000)

    @Test
    fun editingSession_excludesItsOwnId() {
        val existing = listOf(session(id = 4, start = 100, end = 200))
        val edited = session(id = 4, start = 110, end = 210)
        assertFalse(DataValidator.overlapsAnySession(existing, edited))
    }

    @Test
    fun editingSession_stillDetectsAnotherSession() {
        val existing = listOf(
            session(id = 4, start = 100, end = 200),
            session(id = 5, start = 300, end = 400),
        )
        val edited = session(id = 4, start = 350, end = 450)
        assertTrue(DataValidator.overlapsAnySession(existing, edited))
    }

    private fun assertOverlap(firstStart: Long, firstEnd: Long?, secondStart: Long, secondEnd: Long?) {
        assertTrue(DataValidator.sessionsOverlap(firstStart, firstEnd, secondStart, secondEnd))
        assertTrue(DataValidator.sessionsOverlap(secondStart, secondEnd, firstStart, firstEnd))
    }

    private fun session(id: Long, start: Long, end: Long?) = Session(
        id = id,
        title = "Session $id",
        startAtMillis = start,
        endAtMillis = end,
        createdAtMillis = 1,
        updatedAtMillis = 1,
    )
}
