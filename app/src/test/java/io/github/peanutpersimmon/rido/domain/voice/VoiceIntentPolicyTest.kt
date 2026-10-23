package io.github.peanutpersimmon.rido.domain.voice

import io.github.peanutpersimmon.rido.domain.model.SessionKind
import io.github.peanutpersimmon.rido.domain.model.Session
import io.github.peanutpersimmon.rido.domain.model.SessionTemporalType
import io.github.peanutpersimmon.rido.domain.model.TaggedSession
import io.github.peanutpersimmon.rido.presentation.editor.EditorMode
import io.github.peanutpersimmon.rido.presentation.editor.session.SessionEditorUiState
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceIntentPolicyTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val day = LocalDate.of(2026, 9, 26)
    private val now = at(15)
    private fun at(hour: Int) = day.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()
    private fun draft(action: VoiceAction, fields: VoiceFields, instant: Long = now,
        eventType: VoiceEventTemporalType? = null, recordType: SessionTemporalType? = null) =
        VoiceActionDraft(action, "写论文", fields, "原句", instant,
            eventTemporalType = eventType, recordTemporalType = recordType)

    @Test fun reportedMeetingCannotBeSavedAsEventWhenModelChoosesWrongTool() {
        val wrong = draft(VoiceAction.CreateEvent, VoiceFields(
            date = VoiceFieldPatch.Set(day), start = VoiceFieldPatch.Set(LocalTime.of(9, 0)),
            end = VoiceFieldPatch.Set(LocalTime.of(10, 0))),
            eventType = VoiceEventTemporalType.INTERVAL).copy(originalText = "刚才九点到十点开会了")
        assertTrue(VoiceIntentPolicy.evaluate(wrong, zone) is VoiceIntentDecision.NeedsClarification)
        val reported = draft(VoiceAction.CreateRecord, VoiceFields(startAt = at(9), endAt = at(10)),
            recordType = SessionTemporalType.INTERVAL).copy(originalText = "刚才九点到十点开会了")
        assertTrue(VoiceIntentPolicy.evaluate(reported, zone) is VoiceIntentDecision.Ready)
        assertFalse(VoiceSemanticCue.isReportedFact("刚才安排了明天开会"))
    }

    @Test fun ambiguousSleepUsesExistingSleepCheckInPolicyAndExplicitNapWordsWin() {
        val shortDay = draft(VoiceAction.CreateSleepRecord,
            VoiceFields(startAt = at(13), endAt = at(14), sleepKind = SessionKind.NIGHT_SLEEP))
            .copy(originalText = "睡了一会")
        assertEquals(SessionKind.NAP, VoiceIntentPolicy.resolvedSleepKind(shortDay, zone))
        assertEquals(SessionKind.NIGHT_SLEEP, VoiceIntentPolicy.resolvedSleepKind(
            shortDay.copy(fields = shortDay.fields.copy(startAt = at(1), endAt = at(9))), zone))
        assertEquals(SessionKind.NAP, VoiceIntentPolicy.resolvedSleepKind(
            shortDay.copy(originalText = "午睡了"), zone))
        assertTrue(VoiceIntentPolicy.evaluate(
            shortDay.copy(action = VoiceAction.CreateRecord, recordTemporalType = SessionTemporalType.INTERVAL),
            zone) is VoiceIntentDecision.NeedsClarification)
    }

    @Test fun pastTaskAndPastEventKeepTheirModelChosenTypes() {
        val task = draft(VoiceAction.CreateTask, VoiceFields(
            title = VoiceFieldPatch.Set("写论文"),
            date = VoiceFieldPatch.Set(day.minusDays(1)),
            start = VoiceFieldPatch.Set(LocalTime.of(9, 0)),
        ))
        val event = draft(VoiceAction.CreateEvent, VoiceFields(
            title = VoiceFieldPatch.Set("组会"),
            date = VoiceFieldPatch.Set(day.minusDays(1)),
            start = VoiceFieldPatch.Set(LocalTime.of(9, 0)),
            end = VoiceFieldPatch.Set(LocalTime.of(10, 0)),
        ), eventType = VoiceEventTemporalType.INTERVAL)
        assertEquals(VoiceAction.CreateTask, (VoiceIntentPolicy.evaluate(task, zone) as VoiceIntentDecision.Ready).draft.action)
        assertEquals(VoiceAction.CreateEvent, (VoiceIntentPolicy.evaluate(event, zone) as VoiceIntentDecision.Ready).draft.action)
    }

    @Test fun futureRecordIsClarifiedAndNeverBecomesEvent() {
        val record = draft(VoiceAction.CreateRecord, VoiceFields(startAt = at(16)),
            recordType = SessionTemporalType.POINT)
        val result = VoiceIntentPolicy.evaluate(record, zone) as VoiceIntentDecision.NeedsClarification
        assertEquals(VoiceClarification.TimeSemanticConflict, result.reason)
        assertEquals(VoiceAction.CreateRecord, result.draft.action)
    }

    @Test fun completedIntervalRemainsRecordRegardlessOfClock() {
        val record = draft(VoiceAction.CreateRecord,
            VoiceFields(startAt = at(9), endAt = at(10)),
            recordType = SessionTemporalType.INTERVAL)
        val result = VoiceIntentPolicy.evaluate(record, zone) as VoiceIntentDecision.Ready
        assertEquals(VoiceAction.CreateRecord, result.draft.action)
        assertEquals(SessionTemporalType.INTERVAL, result.draft.recordTemporalType)
    }

    @Test fun taskIntervalRequiresClarificationInsteadOfDroppingEnd() {
        val task = draft(VoiceAction.CreateTask, VoiceFields(
            date = VoiceFieldPatch.Set(day), start = VoiceFieldPatch.Set(LocalTime.of(9, 0)),
            end = VoiceFieldPatch.Set(LocalTime.of(10, 0))))
        val result = VoiceIntentPolicy.evaluate(task, zone) as VoiceIntentDecision.NeedsClarification
        assertEquals(VoiceClarification.ObjectTypeAmbiguous, result.reason)
        assertEquals(VoiceAction.CreateTask, result.draft.action)
    }

    @Test fun pointRecordPersistsAsPointAndDoesNotCreateEvent() {
        val record = draft(VoiceAction.CreateRecord, VoiceFields(
            title = VoiceFieldPatch.Set("取快递"), startAt = at(14)),
            recordType = SessionTemporalType.POINT)
        val ready = (VoiceIntentPolicy.evaluate(record, zone) as VoiceIntentDecision.Ready).draft
        val prepared = VoiceExecutionPlanner.prepare(ready, zone = zone, now = now)
        assertTrue(prepared.canExecuteDirectly)
        assertEquals(SessionTemporalType.POINT, prepared.record!!.session.temporalType)
        assertNull(prepared.record!!.session.endAtMillis)
        assertNull(prepared.event)
    }

    @Test fun dateOnlyPastRecordPreservesDateAndRequestsActualTime() {
        val record = draft(VoiceAction.CreateRecord, VoiceFields(
            title = VoiceFieldPatch.Set("取快递"),
            date = VoiceFieldPatch.Set(day.minusDays(1))),
            recordType = SessionTemporalType.POINT)
        val result = VoiceIntentPolicy.evaluate(record, zone) as VoiceIntentDecision.NeedsClarification
        assertEquals(VoiceClarification.RecordMissingTime, result.reason)
        assertEquals(day.minusDays(1), result.draft.fields.date.applyTo(null))
        assertNull(result.draft.fields.startAt)
    }

    @Test fun sleepIsNeverChangedToEventByFutureTime() {
        val sleep = draft(VoiceAction.CreateSleepRecord,
            VoiceFields(startAt = at(16), endAt = at(17), sleepKind = SessionKind.NAP))
        val result = VoiceIntentPolicy.evaluate(sleep, zone) as VoiceIntentDecision.NeedsClarification
        assertEquals(VoiceClarification.TimeSemanticConflict, result.reason)
        assertEquals(VoiceAction.CreateSleepRecord, result.draft.action)
    }

    @Test fun startRecordRetainsActiveInterval() {
        val record = draft(VoiceAction.StartRecord, VoiceFields(startAt = now))
        val ready = (VoiceIntentPolicy.evaluate(record, zone) as VoiceIntentDecision.Ready).draft
        val prepared = VoiceExecutionPlanner.prepare(ready, zone = zone, now = now)
        assertEquals(SessionTemporalType.INTERVAL, prepared.record!!.session.temporalType)
        assertNull(prepared.record!!.session.endAtMillis)
    }

    @Test fun explicitRecordUpdateCanChangePointAndIntervalShapes() {
        val point = TaggedSession(Session(id = 7, title = "实验", temporalType = SessionTemporalType.POINT,
            startAtMillis = at(9), createdAtMillis = 0, updatedAtMillis = 0))
        val toInterval = draft(VoiceAction.UpdateRecord,
            VoiceFields(startAt = at(9), endAt = at(10)), recordType = SessionTemporalType.INTERVAL)
        val interval = VoiceExecutionPlanner.prepare(toInterval, record = point, zone = zone, now = now).record!!.session
        assertEquals(SessionTemporalType.INTERVAL, interval.temporalType)
        assertEquals(at(10), interval.endAtMillis)
        val toPoint = draft(VoiceAction.UpdateRecord, VoiceFields(startAt = at(11)),
            recordType = SessionTemporalType.POINT)
        val updatedPoint = VoiceExecutionPlanner.prepare(toPoint, record = TaggedSession(interval), zone = zone, now = now)
            .record!!.session
        assertEquals(SessionTemporalType.POINT, updatedPoint.temporalType)
        assertNull(updatedPoint.endAtMillis)
        assertEquals(at(11), updatedPoint.startAtMillis)
    }

    @Test fun clearingCompletedIntervalEndCreatesPointButActiveUpdateStaysActive() {
        val completed = TaggedSession(Session(id = 7, title = "实验", temporalType = SessionTemporalType.INTERVAL,
            startAtMillis = at(9), endAtMillis = at(10), createdAtMillis = 0, updatedAtMillis = 0))
        val clearEnd = draft(VoiceAction.UpdateRecord,
            VoiceFields(end = VoiceFieldPatch.Clear), recordType = SessionTemporalType.INTERVAL)
        val ready = (VoiceIntentPolicy.evaluate(clearEnd, zone) as VoiceIntentDecision.Ready).draft
        val point = VoiceExecutionPlanner.prepare(ready, record = completed, zone = zone, now = now).record!!.session
        assertEquals(SessionTemporalType.POINT, point.temporalType)
        assertEquals(at(9), point.startAtMillis)
        assertNull(point.endAtMillis)
        assertFalse(SessionEditorUiState(loading = false, mode = EditorMode.EDIT,
            temporalType = point.temporalType).isActive)

        val active = TaggedSession(completed.session.copy(endAtMillis = null))
        val rename = draft(VoiceAction.UpdateRecord, VoiceFields(title = VoiceFieldPatch.Set("新标题")))
        val stillActive = VoiceExecutionPlanner.prepare(rename, record = active, zone = zone, now = now).record!!.session
        assertEquals(SessionTemporalType.INTERVAL, stillActive.temporalType)
        assertNull(stillActive.endAtMillis)
    }
}
