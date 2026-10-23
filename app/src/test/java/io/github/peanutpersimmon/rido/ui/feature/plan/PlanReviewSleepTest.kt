package io.github.peanutpersimmon.rido.ui.feature.plan

import io.github.peanutpersimmon.rido.domain.model.Session
import io.github.peanutpersimmon.rido.domain.model.SessionKind
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlanReviewSleepTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val first = LocalDate.of(2026, 10, 2)
    private fun at(date: LocalDate, hour: Int, minute: Int = 0) =
        date.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

    @Test fun overnightSleepIsClippedPerDayWithoutChangingOriginalSession() {
        val start = at(first, 23, 30)
        val end = at(first.plusDays(1), 7)
        val sleep = Session(id = 1, title = "睡眠", kind = SessionKind.NIGHT_SLEEP,
            startAtMillis = start, endAtMillis = end,
            createdAtMillis = start, updatedAtMillis = end)
        val midnight = at(first.plusDays(1), 0)
        assertEquals(start to midnight, reviewIntervalOnDay(sleep, first, zone))
        assertEquals(midnight to end, reviewIntervalOnDay(sleep, first.plusDays(1), zone))
        assertEquals("23:30–24:00", reviewTimeLabel(sleep, first, zone))
        assertEquals("00:00–07:00", reviewTimeLabel(sleep, first.plusDays(1), zone))
        assertNull(reviewIntervalOnDay(sleep, first.minusDays(1), zone))
        assertEquals(start, sleep.startAtMillis)
        assertEquals(end, sleep.endAtMillis)
    }

    @Test fun napUsesItsActualDurationAndBoundaryTouchDoesNotDuplicate() {
        val start = at(first, 14)
        val nap = Session(id = 2, title = "小憩", kind = SessionKind.NAP,
            startAtMillis = start, endAtMillis = start + 30 * 60_000L,
            createdAtMillis = start, updatedAtMillis = start)
        assertEquals(start to start + 30 * 60_000L, reviewIntervalOnDay(nap, first, zone))
        assertEquals("14:00–14:30", reviewTimeLabel(nap, first, zone))
        assertNull(reviewIntervalOnDay(nap, first.plusDays(1), zone))
    }
}
