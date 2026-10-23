package io.github.peanutpersimmon.rido.domain.service

import io.github.peanutpersimmon.rido.domain.model.SessionKind
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepCheckInPolicyTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val day = LocalDate.of(2026, 9, 26)

    @Test
    fun durationBelow240MinutesIsNapBut240MinutesIsNightSleep() {
        val start = at(13, 0)

        assertEquals(
            SessionKind.NAP,
            SleepCheckInPolicy.inferKind(start, start + 239 * MINUTE_MILLIS, zone),
        )
        assertEquals(
            SessionKind.NIGHT_SLEEP,
            SleepCheckInPolicy.inferKind(start, start + 240 * MINUTE_MILLIS, zone),
        )
    }

    @Test
    fun startHourUsesInclusiveNineThroughTwentyWindow() {
        assertEquals(SessionKind.NAP, kind(startHour = 9, startMinute = 0))
        assertEquals(SessionKind.NAP, kind(startHour = 20, startMinute = 59))
        assertEquals(SessionKind.NIGHT_SLEEP, kind(startHour = 21, startMinute = 0))
    }

    @Test
    fun crossingLocalMidnightIsNightSleep() {
        val start = at(23, 30)
        val end = day.plusDays(1).atTime(0, 30).atZone(zone).toInstant().toEpochMilli()

        assertEquals(SessionKind.NIGHT_SLEEP, SleepCheckInPolicy.inferKind(start, end, zone))
    }

    @Test
    fun openSleepWindowAllowsExactly24HoursOnly() {
        val end = at(13, 0)

        assertTrue(SleepCheckInPolicy.isAllowedOpenInterval(end - 24 * HOUR_MILLIS, end))
        assertFalse(SleepCheckInPolicy.isAllowedOpenInterval(end - 24 * HOUR_MILLIS - 1, end))
        assertFalse(SleepCheckInPolicy.isAllowedOpenInterval(end, end))
    }

    private fun kind(startHour: Int, startMinute: Int): SessionKind {
        val start = at(startHour, startMinute)
        return SleepCheckInPolicy.inferKind(start, start + 30 * MINUTE_MILLIS, zone)
    }

    private fun at(hour: Int, minute: Int): Long =
        day.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

    private companion object {
        const val MINUTE_MILLIS = 60_000L
        const val HOUR_MILLIS = 60 * MINUTE_MILLIS
    }
}
