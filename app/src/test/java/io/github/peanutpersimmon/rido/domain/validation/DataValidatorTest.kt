package io.github.peanutpersimmon.rido.domain.validation

import io.github.peanutpersimmon.rido.domain.model.Event
import io.github.peanutpersimmon.rido.domain.model.KeywordRule
import io.github.peanutpersimmon.rido.domain.model.Session
import io.github.peanutpersimmon.rido.domain.model.SessionKind
import io.github.peanutpersimmon.rido.domain.model.SessionTemporalType
import io.github.peanutpersimmon.rido.domain.model.Task
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DataValidatorTest {
    @Test
    fun task_blankTitle_isInvalid() {
        assertTaskError(task(title = "  "), ValidationError.BLANK_TITLE)
    }

    @Test
    fun task_selfParent_isInvalid() {
        assertTaskError(task(id = 7, parentTaskId = 7), ValidationError.PARENT_SELF_REFERENCE)
    }

    @Test
    fun task_deepParentCycle_isInvalid() {
        val errors = DataValidator.validateTask(
            task(id = 1, parentTaskId = 2),
            parentByTaskId = mapOf(2L to 3L, 3L to 1L),
        )
        assertTrue(ValidationError.PARENT_CYCLE in errors)
    }

    @Test
    fun minuteOfDay_boundaries_areValidated() {
        assertFalse(DataValidator.isMinuteOfDayValid(-1))
        assertTrue(DataValidator.isMinuteOfDayValid(0))
        assertTrue(DataValidator.isMinuteOfDayValid(1439))
        assertFalse(DataValidator.isMinuteOfDayValid(1440))
    }

    @Test
    fun task_dueTimeWithoutDate_isInvalid() {
        assertTaskError(task(dueMinuteOfDay = 600), ValidationError.DUE_TIME_WITHOUT_DATE)
    }

    @Test
    fun task_nonPositiveEstimate_isInvalid() {
        assertTaskError(task(estimatedDurationMinutes = 0), ValidationError.INVALID_ESTIMATED_DURATION)
    }

    @Test
    fun event_dateOnly_isValid() {
        assertTrue(DataValidator.validateEvent(event()).isEmpty())
    }

    @Test
    fun event_blankTitle_isInvalid() {
        val invalid = event().copy(title = "\t")
        assertEventError(invalid, ValidationError.BLANK_TITLE)
    }

    @Test
    fun event_startOnly_isValid() {
        assertTrue(DataValidator.validateEvent(event(startMinuteOfDay = 840)).isEmpty())
    }

    @Test
    fun event_sameDayIntervalMustHavePositiveDuration() {
        assertTrue(DataValidator.validateEvent(event(startMinuteOfDay = 840, endMinuteOfDay = 900)).isEmpty())
        assertEventError(event(startMinuteOfDay = 840, endMinuteOfDay = 840), ValidationError.EVENT_END_BEFORE_START)
    }

    @Test
    fun event_sameDayEndBeforeStart_isInvalid() {
        assertEventError(
            event(startMinuteOfDay = 900, endMinuteOfDay = 899),
            ValidationError.EVENT_END_BEFORE_START,
        )
    }

    @Test
    fun event_endWithoutStart_isInvalid() {
        assertEventError(event(endMinuteOfDay = 900), ValidationError.EVENT_END_WITHOUT_START)
    }

    @Test
    fun event_crossMidnightPlan_isValid() {
        assertTrue(
            DataValidator.validateEvent(
                event(
                    startMinuteOfDay = 1380,
                    endDateEpochDay = 2,
                    endMinuteOfDay = 90,
                ),
            ).isEmpty(),
        )
    }

    @Test
    fun session_endBeforeStart_isInvalid() {
        assertSessionError(session(startAtMillis = 200, endAtMillis = 100))
    }

    @Test
    fun session_blankTitle_isInvalid() {
        val invalid = session(startAtMillis = 100, endAtMillis = 200).copy(title = " ")
        assertTrue(ValidationError.BLANK_TITLE in DataValidator.validateSession(invalid))
    }

    @Test
    fun session_zeroDuration_isInvalid() {
        assertSessionError(session(startAtMillis = 100, endAtMillis = 100))
    }

    @Test
    fun session_positiveDuration_isValid() {
        assertTrue(DataValidator.validateSession(session(startAtMillis = 100, endAtMillis = 101)).isEmpty())
    }

    @Test
    fun pointHasNoEndAndDoesNotOverlapAnInterval() {
        val interval = session(startAtMillis = 100, endAtMillis = 300)
        val point = session(startAtMillis = 200, endAtMillis = null)
            .copy(temporalType = SessionTemporalType.POINT)
        assertTrue(DataValidator.validateSession(point).isEmpty())
        assertFalse(DataValidator.overlapsAnySession(listOf(interval), point))
        assertFalse(DataValidator.overlapsAnySession(listOf(point), interval))
        assertTrue(ValidationError.SESSION_POINT_WITH_END in
            DataValidator.validateSession(point.copy(endAtMillis = 201)))
    }

    @Test
    fun overlappingIntervalsStillConflictAndSleepPointIsInvalid() {
        val first = session(startAtMillis = 100, endAtMillis = 300)
        val second = session(startAtMillis = 200, endAtMillis = 400)
        assertTrue(DataValidator.overlapsAnySession(listOf(first), second))
        for (kind in listOf(SessionKind.NAP, SessionKind.NIGHT_SLEEP)) {
            assertTrue(ValidationError.SESSION_SLEEP_POINT in
                DataValidator.validateSession(first.copy(kind = kind, temporalType = SessionTemporalType.POINT,
                    endAtMillis = null)))
            assertTrue(DataValidator.validateSession(first.copy(kind = kind)).isEmpty())
        }
    }

    @Test
    fun adjacentActualIntervalsOfEveryKindRemainLegalOnCreateAndEdit() {
        val first = session(9 * 60, 9 * 60 + 34).copy(id = 1)
        val next = session(9 * 60 + 34, 10 * 60).copy(id = 2)
        assertFalse(DataValidator.overlapsAnySession(listOf(first), next))
        assertTrue(DataValidator.overlapsAnySession(listOf(first.copy(endAtMillis = 9 * 60 + 35)), next))
        val sleep = first.copy(kind = SessionKind.NIGHT_SLEEP)
        assertFalse(DataValidator.overlapsAnySession(listOf(sleep), next))
        val nap = session(13 * 60 + 20, 14 * 60 + 10).copy(id = 3, kind = SessionKind.NAP)
        assertFalse(DataValidator.overlapsAnySession(listOf(nap),
            session(14 * 60 + 10, 15 * 60)))
        assertFalse(DataValidator.overlapsAnySession(listOf(session(12 * 60, 13 * 60 + 20)), nap))
        assertFalse(DataValidator.overlapsAnySession(listOf(first, next),
            next.copy(startAtMillis = first.endAtMillis!!)))
        assertTrue(DataValidator.overlapsAnySession(listOf(first),
            next.copy(startAtMillis = first.endAtMillis!! - 1)))
    }

    @Test
    fun session_crossMidnight_isValidBecauseInstantsAreOrdered() {
        assertTrue(DataValidator.validateSession(session(startAtMillis = 1_000, endAtMillis = 10_000)).isEmpty())
    }

    @Test
    fun keywordRule_blankKeyword_isInvalid() {
        val errors = DataValidator.validateKeywordRule(keywordRule(keyword = " ", categoryId = 1))
        assertTrue(ValidationError.BLANK_KEYWORD in errors)
    }

    @Test
    fun keywordRule_withoutCategoryOrTag_isInvalid() {
        val errors = DataValidator.validateKeywordRule(keywordRule(keyword = "meeting"))
        assertTrue(ValidationError.KEYWORD_RULE_WITHOUT_TARGET in errors)
    }

    @Test
    fun displayName_isTrimmedAndMustNotBeBlank() {
        assertTrue(ValidationError.BLANK_NAME in DataValidator.validateDisplayName("  "))
        assertTrue(DataValidator.validateDisplayName("Custom").isEmpty())
    }

    private fun assertTaskError(task: Task, error: ValidationError) {
        assertTrue(error in DataValidator.validateTask(task))
    }

    private fun assertEventError(event: Event, error: ValidationError) {
        assertTrue(error in DataValidator.validateEvent(event))
    }

    private fun assertSessionError(session: Session) {
        assertTrue(ValidationError.SESSION_END_NOT_AFTER_START in DataValidator.validateSession(session))
    }

    private fun task(
        id: Long = 1,
        title: String = "Task",
        parentTaskId: Long? = null,
        plannedDateEpochDay: Long? = null,
        plannedStartMinuteOfDay: Int? = null,
        dueMinuteOfDay: Int? = null,
        estimatedDurationMinutes: Int? = null,
    ) = Task(
        id = id,
        title = title,
        parentTaskId = parentTaskId,
        plannedDateEpochDay = plannedDateEpochDay,
        plannedStartMinuteOfDay = plannedStartMinuteOfDay,
        dueMinuteOfDay = dueMinuteOfDay,
        estimatedDurationMinutes = estimatedDurationMinutes,
        createdAtMillis = 1,
        updatedAtMillis = 1,
    )

    private fun event(
        startMinuteOfDay: Int? = null,
        endDateEpochDay: Long? = null,
        endMinuteOfDay: Int? = null,
    ) = Event(
        title = "Event",
        dateEpochDay = 1,
        startMinuteOfDay = startMinuteOfDay,
        endDateEpochDay = endDateEpochDay,
        endMinuteOfDay = endMinuteOfDay,
        createdAtMillis = 1,
        updatedAtMillis = 1,
    )

    private fun session(startAtMillis: Long, endAtMillis: Long?) = Session(
        title = "Session",
        startAtMillis = startAtMillis,
        endAtMillis = endAtMillis,
        createdAtMillis = 1,
        updatedAtMillis = 1,
    )

    private fun keywordRule(
        keyword: String,
        categoryId: Long? = null,
        tagIds: Set<Long> = emptySet(),
    ) = KeywordRule(
        keyword = keyword,
        categoryId = categoryId,
        tagIds = tagIds,
        createdAtMillis = 1,
        updatedAtMillis = 1,
    )
}
