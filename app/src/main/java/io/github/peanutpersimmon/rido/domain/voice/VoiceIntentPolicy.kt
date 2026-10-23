package io.github.peanutpersimmon.rido.domain.voice

import io.github.peanutpersimmon.rido.domain.model.SessionTemporalType
import io.github.peanutpersimmon.rido.domain.model.SessionKind
import io.github.peanutpersimmon.rido.domain.service.SleepCheckInPolicy
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

enum class VoiceClarification {
    ObjectTypeAmbiguous,
    RecordMissingTime,
    IntervalMissingBoundary,
    TargetNotUnique,
    MultiplePrimaryObjects,
    TimeSemanticConflict,
    SourceEventMissing,
    ManualTypeChange,
}

sealed interface VoiceIntentDecision {
    data class Ready(val draft: VoiceActionDraft) : VoiceIntentDecision
    data class NeedsClarification(
        val reason: VoiceClarification,
        val draft: VoiceActionDraft,
    ) : VoiceIntentDecision
}

data class VoiceTemporalRange(val startAt: Long?, val endAt: Long?)

/** Validates the model's chosen object and time shape; never changes its primary type. */
object VoiceIntentPolicy {
    fun evaluate(draft: VoiceActionDraft, zone: ZoneId): VoiceIntentDecision {
        draft.clarificationReason?.let { return clarify(it, draft) }
        val spoken = draft.originalText
        if (VoiceSemanticCue.isSleep(spoken) && draft.action !in setOf(VoiceAction.CreateSleepRecord, VoiceAction.StartSleep))
            return clarify(VoiceClarification.ObjectTypeAmbiguous, draft)
        if (draft.action == VoiceAction.CreateEvent && VoiceSemanticCue.isReportedFact(spoken))
            return clarify(VoiceClarification.ObjectTypeAmbiguous, draft)
        val f = draft.fields
        val range = temporalRange(draft, zone)
        return when (draft.action) {
            VoiceAction.Clarify -> clarify(VoiceClarification.ObjectTypeAmbiguous, draft)
            VoiceAction.CreateTask -> {
                if (f.end != VoiceFieldPatch.Keep || f.endDate != VoiceFieldPatch.Keep ||
                    f.endAt != null || draft.eventTemporalType != null || draft.recordTemporalType != null
                ) clarify(VoiceClarification.ObjectTypeAmbiguous, draft)
                else if (f.start.applyTo(null) != null && f.date.applyTo(null) == null)
                    clarify(VoiceClarification.TimeSemanticConflict, draft)
                else VoiceIntentDecision.Ready(draft)
            }
            VoiceAction.CreateEvent -> {
                val start = range.startAt
                val end = range.endAt
                when (draft.eventTemporalType) {
                    VoiceEventTemporalType.DATE ->
                        if (f.date.applyTo(null) != null && start == null && end == null)
                            VoiceIntentDecision.Ready(draft)
                        else clarify(VoiceClarification.TimeSemanticConflict, draft)
                    VoiceEventTemporalType.POINT ->
                        if (start != null && end == null) VoiceIntentDecision.Ready(draft)
                        else clarify(VoiceClarification.TimeSemanticConflict, draft)
                    VoiceEventTemporalType.INTERVAL ->
                        if (start == null || end == null) clarify(VoiceClarification.IntervalMissingBoundary, draft)
                        else if (end <= start) clarify(VoiceClarification.TimeSemanticConflict, draft)
                        else VoiceIntentDecision.Ready(draft)
                    null -> clarify(VoiceClarification.TimeSemanticConflict, draft)
                }
            }
            VoiceAction.CreateRecord, VoiceAction.CreateRecordAndCompleteTask -> {
                val start = range.startAt
                val end = range.endAt
                if (draft.action == VoiceAction.CreateRecordAndCompleteTask && f.linkedTask !is VoiceFieldPatch.Set)
                    return clarify(VoiceClarification.TargetNotUnique, draft)
                when (draft.recordTemporalType) {
                    SessionTemporalType.POINT ->
                        if (end != null || f.end != VoiceFieldPatch.Keep)
                            clarify(VoiceClarification.TimeSemanticConflict, draft)
                        else if (start == null) clarify(VoiceClarification.RecordMissingTime, draft)
                        else if (start > draft.nowMillis) clarify(VoiceClarification.TimeSemanticConflict, draft)
                        else VoiceIntentDecision.Ready(draft)
                    SessionTemporalType.INTERVAL ->
                        if (start == null || end == null) clarify(VoiceClarification.IntervalMissingBoundary, draft)
                        else if (end <= start || start > draft.nowMillis || end > draft.nowMillis)
                            clarify(VoiceClarification.TimeSemanticConflict, draft)
                        else VoiceIntentDecision.Ready(draft)
                    null -> clarify(VoiceClarification.TimeSemanticConflict, draft)
                }
            }
            VoiceAction.CreateRecordFromEvent -> {
                if (range.startAt?.let { it > draft.nowMillis } == true ||
                    range.endAt?.let { it > draft.nowMillis } == true)
                    clarify(VoiceClarification.TimeSemanticConflict, draft)
                else VoiceIntentDecision.Ready(draft)
            }
            VoiceAction.StartRecord -> {
                if (f.startAt == null || f.startAt > draft.nowMillis || f.endAt != null)
                    clarify(VoiceClarification.TimeSemanticConflict, draft)
                else VoiceIntentDecision.Ready(draft)
            }
            VoiceAction.CreateSleepRecord, VoiceAction.StartSleep -> {
                if (range.startAt?.let { it > draft.nowMillis } == true ||
                    range.endAt?.let { it > draft.nowMillis } == true)
                    clarify(VoiceClarification.TimeSemanticConflict, draft)
                else VoiceIntentDecision.Ready(draft)
            }
            VoiceAction.UpdateRecord -> {
                if (f.startAt?.let { it > draft.nowMillis } == true ||
                    f.endAt?.let { it > draft.nowMillis } == true)
                    clarify(VoiceClarification.TimeSemanticConflict, draft)
                else VoiceIntentDecision.Ready(draft)
            }
            else -> VoiceIntentDecision.Ready(draft)
        }
    }

    fun resolvedSleepKind(draft: VoiceActionDraft, zone: ZoneId): SessionKind? {
        val spoken = draft.originalText
        if (VoiceSemanticCue.isExplicitNap(spoken)) return SessionKind.NAP
        if (VoiceSemanticCue.isExplicitNightSleep(spoken)) return SessionKind.NIGHT_SLEEP
        val range = temporalRange(draft, zone)
        return if (range.startAt != null && range.endAt != null)
            SleepCheckInPolicy.inferKind(range.startAt, range.endAt, zone)
        else draft.fields.sleepKind
    }

    private fun clarify(reason: VoiceClarification, draft: VoiceActionDraft) =
        VoiceIntentDecision.NeedsClarification(reason, draft)

    fun temporalRange(draft: VoiceActionDraft, zone: ZoneId): VoiceTemporalRange {
        val f = draft.fields
        val startDate = f.date.applyTo(null)
        val start = f.startAt ?: atMillis(startDate, f.start.applyTo(null), zone)
        val endDate = f.endDate.applyTo(null) ?: startDate
        val end = f.endAt ?: atMillis(endDate, f.end.applyTo(null), zone)
        return VoiceTemporalRange(start, end)
    }

    private fun atMillis(date: LocalDate?, time: LocalTime?, zone: ZoneId): Long? =
        if (date == null || time == null) null else date.atTime(time).atZone(zone).toInstant().toEpochMilli()
}

