package io.github.peanutpersimmon.rido.domain.service

import io.github.peanutpersimmon.rido.domain.model.DomainError
import io.github.peanutpersimmon.rido.domain.model.DomainResult
import io.github.peanutpersimmon.rido.domain.model.SessionKind
import io.github.peanutpersimmon.rido.domain.model.SessionTemporalType
import io.github.peanutpersimmon.rido.domain.model.TaggedSession
import io.github.peanutpersimmon.rido.domain.time.IntervalMath
import io.github.peanutpersimmon.rido.domain.time.TimeInterval

enum class ActualSourceType { SESSION }

data class ActualSegment(
    val interval: TimeInterval,
    val sourceType: ActualSourceType,
    val sourceId: Long,
    val title: String,
    val categoryId: Long?,
    val tagIds: Set<Long>,
    val note: String?,
    val location: String?,
    val sessionKind: SessionKind? = null,
)

data class ActualTimeResolution(val segments: List<ActualSegment>)

object ActualTimeResolver {
    fun resolve(
        sessions: List<TaggedSession>,
        range: TimeInterval,
        asOfMillis: Long,
    ): DomainResult<ActualTimeResolution> {
        val segments = sessions.mapNotNull { tagged ->
            val session = tagged.session
            if (session.temporalType != SessionTemporalType.INTERVAL) return@mapNotNull null
            val effectiveEnd = session.endAtMillis ?: asOfMillis
            if (effectiveEnd <= session.startAtMillis) return@mapNotNull null
            TimeInterval(session.startAtMillis, effectiveEnd).clipTo(range)?.let { interval ->
                ActualSegment(
                    interval = interval,
                    sourceType = ActualSourceType.SESSION,
                    sourceId = session.id,
                    title = session.title,
                    categoryId = session.categoryId,
                    tagIds = tagged.tagIds,
                    note = session.note,
                    location = session.location,
                    sessionKind = session.kind,
                )
            }
        }.sortedWith(compareBy({ it.interval.startMillis }, { it.sourceId }))
        if (IntervalMath.hasAnyOverlap(segments.map(ActualSegment::interval))) {
            return DomainResult.Failure(DomainError.SessionOverlap)
        }
        return DomainResult.Success(ActualTimeResolution(segments))
    }
}

