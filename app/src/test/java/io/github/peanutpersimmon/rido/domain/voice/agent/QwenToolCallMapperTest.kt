package io.github.peanutpersimmon.rido.domain.voice.agent

import io.github.peanutpersimmon.rido.data.remote.qwen.QwenAgentProtocol
import io.github.peanutpersimmon.rido.domain.model.SessionTemporalType
import io.github.peanutpersimmon.rido.domain.voice.*
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class QwenToolCallMapperTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val now = Instant.parse("2026-09-23T13:30:00Z")
    private val context = VoiceAgentContext(now, zone)
    private val mapper = QwenToolCallMapper()
    private fun map(name: String, json: String) = mapper.map(name, json, "原始输入", context)

    @Test fun finalSchemaHasExplicitShapesAndNoPointFactOrBatch() {
        val functions = QwenAgentProtocol.tools().map { it.asJsonObject.getAsJsonObject("function") }
        val names = functions.map { it.get("name").asString }.toSet()
        assertTrue(names.containsAll(setOf("create_task", "create_event", "create_record",
            "stop_record", "stop_record_and_complete_task", "create_record_and_complete_task", "request_clarification")))
        assertFalse(names.contains("create_point_fact"))
        assertFalse(names.any { it.contains("batch") })
        fun props(name: String) = functions.first { it.get("name").asString == name }
            .getAsJsonObject("parameters").getAsJsonObject("properties").keySet()
        assertTrue(props("create_task").containsAll(setOf("planned_date", "planned_at", "due_date", "due_at")))
        assertFalse(props("create_task").any { it.contains("end") || it.contains("interval") })
        assertTrue(props("create_event").contains("temporal_type"))
        assertTrue(props("create_record").containsAll(setOf("temporal_type", "occurred_at", "start_at", "end_at")))
        assertFalse(props("stop_record").contains("complete_linked_task"))
    }

    @Test fun taskPlannedPointDueAndEstimateStaySeparate() {
        val d = map("create_task", """{"title":"写论文","planned_at":"2026-09-24T15:00:00+08:00",
            "due_date":"2026-09-25","estimated_duration_minutes":120}""")
        assertEquals(VoiceAction.CreateTask, d.action)
        assertEquals(LocalDate.of(2026, 9, 24), d.fields.date.applyTo(null))
        assertEquals(LocalTime.of(15, 0), d.fields.start.applyTo(null))
        assertEquals(LocalDate.of(2026, 9, 25), d.fields.dueDate.applyTo(null))
        assertEquals(120, d.fields.estimatedDuration.applyTo(null))
        assertNull(d.fields.endAt)
    }

    @Test fun eventDatePointAndIntervalKeepTheirChosenType() {
        val date = map("create_event", """{"title":"组会","temporal_type":"DATE","date":"2026-09-24"}""")
        val point = map("create_event", """{"title":"组会","temporal_type":"POINT","start_at":"2026-09-24T15:00:00+08:00"}""")
        val interval = map("create_event", """{"title":"组会","temporal_type":"INTERVAL",
            "start_at":"2026-09-24T15:00:00+08:00","end_at":"2026-09-24T17:00:00+08:00"}""")
        assertEquals(VoiceEventTemporalType.DATE, date.eventTemporalType)
        assertEquals(VoiceEventTemporalType.POINT, point.eventTemporalType)
        assertEquals(VoiceEventTemporalType.INTERVAL, interval.eventTemporalType)
        assertEquals(VoiceAction.CreateEvent, interval.action)
    }

    @Test fun completedMeetingIsRecoveredAsRecordEvenWhenModelCallsEventTool() {
        val reported = mapper.map("create_event", """{"title":"开会","temporal_type":"INTERVAL",
            "start_at":"2026-09-23T15:00:00+08:00","end_at":"2026-09-23T16:00:00+08:00"}""",
            "刚才三点到四点开会了", context)
        assertEquals(VoiceAction.CreateRecord, reported.action)
        assertEquals(SessionTemporalType.INTERVAL, reported.recordTemporalType)
        val arranged = mapper.map("create_event", """{"title":"开会","temporal_type":"INTERVAL",
            "start_at":"2026-09-24T15:00:00+08:00","end_at":"2026-09-24T16:00:00+08:00"}""",
            "明天三点到四点开会", context)
        assertEquals(VoiceAction.CreateEvent, arranged.action)
    }

    @Test fun explicitSpokenReminderOffsetIsMappedForScheduledObjects() {
        val task = map("create_task", """{"title":"组会","planned_at":"2026-09-24T15:00:00+08:00",
            "reminder_offset_minutes":30}""")
        val event = map("create_event", """{"title":"组会","temporal_type":"POINT",
            "start_at":"2026-09-24T15:00:00+08:00","reminder_offset_minutes":30}""")
        assertEquals(30, task.fields.reminderOffsetMinutes)
        assertEquals(30, event.fields.reminderOffsetMinutes)
    }

    @Test fun pointRecordUsesOnlyOccurredAt() {
        val d = map("create_record", """{"title":"取快递","temporal_type":"POINT",
            "occurred_at":"2026-09-23T09:30:00+08:00"}""")
        assertEquals(VoiceAction.CreateRecord, d.action)
        assertEquals(SessionTemporalType.POINT, d.recordTemporalType)
        assertNull(d.fields.endAt)
        assertThrows(InvalidVoiceToolCall::class.java) {
            map("create_record", """{"title":"取快递","temporal_type":"POINT",
                "occurred_at":"2026-09-23T09:30:00+08:00","end_at":"2026-09-23T10:00:00+08:00"}""")
        }
    }
    @Test fun compoundRecordRequiresExplicitTaskQueryAndActualTime() {
        assertThrows(InvalidVoiceToolCall::class.java) {
            map("create_record_and_complete_task", """{"title":"取快递","temporal_type":"POINT",
                "occurred_at":"2026-09-23T09:30:00+08:00"}""")
        }
        assertThrows(InvalidVoiceToolCall::class.java) {
            map("create_record_and_complete_task", """{"title":"取快递","temporal_type":"POINT",
                "task_query":"取快递"}""")
        }
        val point = map("create_record_and_complete_task", """{"title":"取快递","temporal_type":"POINT",
            "occurred_at":"2026-09-23T09:30:00+08:00","task_query":"取快递"}""")
        assertEquals(VoiceAction.CreateRecordAndCompleteTask, point.action)
        assertEquals("取快递", point.fields.linkedTask.applyTo(null))
    }

    @Test fun intervalRecordRequiresBothActualBoundaries() {
        assertThrows(InvalidVoiceToolCall::class.java) {
            map("create_record", """{"title":"实验","temporal_type":"INTERVAL",
                "start_at":"2026-09-23T09:00:00+08:00"}""")
        }
        val d = map("create_record", """{"title":"实验","temporal_type":"INTERVAL",
            "start_at":"2026-09-23T09:00:00+08:00","end_at":"2026-09-23T11:00:00+08:00"}""")
        assertEquals(SessionTemporalType.INTERVAL, d.recordTemporalType)
    }

    @Test fun durationAloneNeverAnchorsARecordToNow() {
        assertThrows(InvalidVoiceToolCall::class.java) {
            map("create_record", """{"title":"实验","temporal_type":"INTERVAL","duration_minutes":40}""")
        }
    }

    @Test fun pointAndIntervalRecordUpdatesAreExplicit() {
        val point = map("update_record", """{"target_query":"取快递","temporal_type":"POINT",
            "occurred_at":"2026-09-23T09:30:00+08:00"}""")
        val interval = map("update_record", """{"target_query":"实验","temporal_type":"INTERVAL",
            "start_at":"2026-09-23T09:00:00+08:00","end_at":"2026-09-23T11:00:00+08:00"}""")
        assertEquals(SessionTemporalType.POINT, point.recordTemporalType)
        assertEquals(SessionTemporalType.INTERVAL, interval.recordTemporalType)
    }

    @Test fun eventRecordMappingNeverFillsActualTimesFromPlan() {
        val point = map("create_record_from_event",
            """{"event_query":"组会","temporal_type":"POINT"}""")
        val interval = map("create_record_from_event",
            """{"event_query":"组会","temporal_type":"INTERVAL"}""")
        assertNull(point.fields.startAt)
        assertNull(interval.fields.startAt)
        assertNull(interval.fields.endAt)
        val clearEnd = map("update_record",
            """{"target_query":"实验","clear_fields":["end"]}""")
        assertEquals(VoiceFieldPatch.Clear, clearEnd.fields.end)
    }

    @Test fun stopAndStopWithCompletionAreDistinctActions() {
        assertEquals(VoiceAction.StopRecord, map("stop_record", "{}").action)
        assertEquals(VoiceAction.StopRecordAndCompleteTask,
            map("stop_record_and_complete_task", "{}").action)
    }

    @Test fun dateOnlyPastFactClarificationKeepsKnownDate() {
        val d = map("request_clarification", """{"reason":"RECORD_MISSING_TIME",
            "known_date":"2026-09-22","record_title":"取快递"}""")
        assertEquals(VoiceClarification.RecordMissingTime, d.clarificationReason)
        assertEquals(LocalDate.of(2026, 9, 22), d.fields.date.applyTo(null))
        assertNull(d.fields.startAt)
    }

    @Test fun objectAmbiguityCarriesConcreteTaskAndEventOptions() {
        val d = map("request_clarification", """{"reason":"OBJECT_TYPE_AMBIGUOUS",
            "task_option":{"title":"写论文","due_date":"2026-09-25"},
            "event_option":{"title":"写论文","temporal_type":"INTERVAL",
                "start_at":"2026-09-24T15:00:00+08:00","end_at":"2026-09-24T17:00:00+08:00"}}""")
        assertEquals(2, d.clarificationOptions.size)
        assertEquals(VoiceAction.CreateTask, d.clarificationOptions[0].draft.action)
        assertEquals(VoiceAction.CreateEvent, d.clarificationOptions[1].draft.action)
    }

    @Test fun explicitMultipleObjectsOnlyRequestsClarification() {
        val d = map("request_clarification", """{"reason":"MULTIPLE_PRIMARY_OBJECTS"}""")
        assertEquals(VoiceAction.Clarify, d.action)
        assertEquals(VoiceClarification.MultiplePrimaryObjects, d.clarificationReason)
    }

    @Test fun unsupportedOldToolCannotMap() {
        assertThrows(UnsupportedVoiceToolCall::class.java) {
            map("create_point_fact", """{"title":"取快递","start_at":"2026-09-23T09:30:00+08:00"}""")
        }
    }

    @Test fun promptUsesSemanticFirstRulesAndNoOldFactTool() {
        val text = QwenAgentProtocol.instructions(context)
        assertTrue(text.contains("责任 / 安排 / 已发生事实"))
        assertTrue(text.contains("Task / Event / Record"))
        assertTrue(text.contains("明天三点打电话"))
        assertTrue(text.contains("明天下午写论文"))
        assertTrue(text.contains("MULTIPLE_PRIMARY_OBJECTS"))
        assertFalse(text.contains("create_point_fact"))
        assertFalse(text.contains("create_explicit_batch"))
        listOf("明天三点打电话", "明天下午写论文", "明天3到5点写论文", "周五前写完论文",
            "刚刚吃药了", "昨天吃药了", "今天完成报销", "下午三点组会", "下午三点提醒我组会")
            .forEach { assertTrue("Prompt missing frozen example: $it", text.contains(it)) }
    }

    @Test fun frozenExampleCallsHaveOnePrimaryObjectEach() {
        val ambiguity = map("request_clarification", """{"reason":"OBJECT_TYPE_AMBIGUOUS",
            "task_option":{"title":"打电话","planned_at":"2026-09-24T15:00:00+08:00"},
            "event_option":{"title":"打电话","temporal_type":"POINT","start_at":"2026-09-24T15:00:00+08:00"}}""")
        assertEquals(VoiceClarification.ObjectTypeAmbiguous, ambiguity.clarificationReason)
        val afternoon = map("request_clarification", """{"reason":"OBJECT_TYPE_AMBIGUOUS",
            "task_option":{"title":"写论文","planned_date":"2026-09-24"},
            "event_option":{"title":"写论文","temporal_type":"DATE","date":"2026-09-24"}}""")
        assertEquals(VoiceAction.Clarify, afternoon.action)
        assertEquals(VoiceEventTemporalType.INTERVAL, map("create_event", """{"title":"写论文",
            "temporal_type":"INTERVAL","start_at":"2026-09-24T15:00:00+08:00",
            "end_at":"2026-09-24T17:00:00+08:00"}""").eventTemporalType)
        assertEquals(VoiceAction.CreateTask, map("create_task", """{"title":"写论文","due_date":"2026-09-25"}""").action)
        assertEquals(SessionTemporalType.POINT, map("create_record", """{"title":"吃药",
            "temporal_type":"POINT","occurred_at":"2026-09-23T21:29:00+08:00"}""").recordTemporalType)
        assertEquals(VoiceClarification.RecordMissingTime, map("request_clarification", """{
            "reason":"RECORD_MISSING_TIME","known_date":"2026-09-22","record_title":"吃药"}""").clarificationReason)
        assertEquals(VoiceAction.CreateTask, map("create_task", """{"title":"报销","due_date":"2026-09-23"}""").action)
        assertEquals(VoiceEventTemporalType.POINT, map("create_event", """{"title":"组会",
            "temporal_type":"POINT","start_at":"2026-09-23T15:00:00+08:00"}""").eventTemporalType)
        assertEquals(VoiceAction.CreateTask, map("create_task", """{"title":"提醒组会",
            "planned_at":"2026-09-23T15:00:00+08:00"}""").action)
    }
}
