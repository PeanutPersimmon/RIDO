package io.github.peanutpersimmon.rido.domain.service

import io.github.peanutpersimmon.rido.domain.model.DomainResult
import io.github.peanutpersimmon.rido.domain.model.SessionKind
import io.github.peanutpersimmon.rido.domain.model.SessionTemporalType
import io.github.peanutpersimmon.rido.domain.model.TaggedSession
import io.github.peanutpersimmon.rido.domain.time.TimeInterval
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class DurationRank(val id: Long?, val durationMillis: Long)
data class DurationPoint(val label: String, val durationMillis: Long)

data class ActualStatistics(
    val segments: List<ActualSegment>,
    val period: StatisticsPeriod,
    val zone: ZoneId,
) {
    val totalMillis: Long get() = segments.sumOf { it.interval.durationMillis }
    private fun categoryKey(segment: ActualSegment): Long = segment.categoryId ?: -1L
    fun rank(category: Boolean): List<DurationRank> =
        segments.flatMap { segment ->
            (if (category) setOf(categoryKey(segment)) else segment.tagIds.map { it as Long? }.toSet())
                .map { id -> id to segment }
        }.groupBy({ it.first }, { it.second })
            .map { (id, entries) -> DurationRank(id, entries.sumOf { it.interval.durationMillis }) }
            .filter { it.durationMillis > 0L }
            .sortedWith(compareByDescending<DurationRank> { it.durationMillis }.thenBy { it.id })

    fun selected(category: Boolean, id: Long?): List<ActualSegment> = segments.filter {
        if (category) categoryKey(it) == id else id != null && id in it.tagIds
    }

    fun trend(selected: List<ActualSegment>): List<DurationPoint> = period.buckets().map { bucket ->
        val bounds = TimeInterval(bucket.start.atStartOfDay(zone).toInstant().toEpochMilli(), bucket.endExclusive.atStartOfDay(zone).toInstant().toEpochMilli())
        DurationPoint(bucket.label, selected.sumOf { it.interval.intersection(bounds)?.durationMillis ?: 0L })
    }

    fun daily(selected: List<ActualSegment>): Map<LocalDate, Long> {
        val totals = mutableMapOf<LocalDate, Long>()
        selected.forEach { segment ->
            var date = Instant.ofEpochMilli(segment.interval.startMillis).atZone(zone).toLocalDate()
            while (date < period.endExclusive) {
                val next = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                val duration = segment.interval.intersection(TimeInterval(date.atStartOfDay(zone).toInstant().toEpochMilli(), next))?.durationMillis ?: 0L
                if (duration > 0L) totals[date] = (totals[date] ?: 0L) + duration
                if (next >= segment.interval.endMillis) break
                date = date.plusDays(1)
            }
        }
        return totals
    }
}

object ActualStatisticsCalculator {
    /** Only completed activity intervals contribute ordinary duration. */
    fun calculate(
        sessions: List<TaggedSession>, period: StatisticsPeriod,
        zone: ZoneId, asOfMillis: Long,
    ): DomainResult<ActualStatistics> {
        val confirmedSessions = sessions.filter {
            it.session.kind == SessionKind.ACTIVITY &&
                it.session.temporalType == SessionTemporalType.INTERVAL &&
                it.session.endAtMillis != null && it.session.endAtMillis > it.session.startAtMillis &&
                it.session.endAtMillis <= asOfMillis
        }
        return when (val result = ActualTimeResolver.resolve(confirmedSessions, period.interval(zone), asOfMillis)) {
            is DomainResult.Failure -> result
            is DomainResult.Success -> DomainResult.Success(
                ActualStatistics(
                    segments = result.value.segments,
                    period = period,
                    zone = zone,
                ),
            )
        }
    }
}
