package io.github.peanutpersimmon.rido.domain.service

import io.github.peanutpersimmon.rido.domain.model.SessionKind
import java.time.Instant
import java.time.ZoneId

object SleepCheckInPolicy {
    private const val MILLIS_PER_MINUTE = 60_000L
    private const val NAP_MAX_DURATION_MINUTES = 240L
    private const val MAX_OPEN_SLEEP_DURATION_MILLIS = 24L * 60L * 60L * 1_000L

    fun isAllowedOpenInterval(startAtMillis: Long, endAtMillis: Long): Boolean =
        startAtMillis < endAtMillis &&
            startAtMillis >= endAtMillis - MAX_OPEN_SLEEP_DURATION_MILLIS

    fun isOpenIntervalTooLong(startAtMillis: Long, endAtMillis: Long): Boolean =
        endAtMillis - startAtMillis > MAX_OPEN_SLEEP_DURATION_MILLIS

    fun inferKind(
        startAtMillis: Long,
        endAtMillis: Long,
        zoneId: ZoneId,
    ): SessionKind {
        val start = Instant.ofEpochMilli(startAtMillis).atZone(zoneId)
        val end = Instant.ofEpochMilli(endAtMillis).atZone(zoneId)
        val durationMinutes = (endAtMillis - startAtMillis) / MILLIS_PER_MINUTE
        val isNap = start.toLocalDate() == end.toLocalDate() &&
            durationMinutes < NAP_MAX_DURATION_MINUTES &&
            start.hour in 9..20
        return if (isNap) SessionKind.NAP else SessionKind.NIGHT_SLEEP
    }

    fun titleFor(kind: SessionKind): String = when (kind) {
        SessionKind.NAP -> "小憩"
        SessionKind.NIGHT_SLEEP -> "夜间睡眠"
        SessionKind.ACTIVITY -> error("Activity is not a sleep check-in kind.")
    }
}
