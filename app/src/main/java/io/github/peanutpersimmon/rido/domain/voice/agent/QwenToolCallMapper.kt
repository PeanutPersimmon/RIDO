package io.github.peanutpersimmon.rido.domain.voice.agent

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.github.peanutpersimmon.rido.domain.model.SessionKind
import io.github.peanutpersimmon.rido.domain.model.SessionTemporalType
import io.github.peanutpersimmon.rido.domain.voice.*
import java.time.DateTimeException
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

open class InvalidVoiceToolCall(message: String) : IllegalArgumentException(message)
class UnsupportedVoiceToolCall(name: String) : InvalidVoiceToolCall("不支持的工具：$name")

/** Strict, allow-listed conversion. Tool names never select methods dynamically. */
class QwenToolCallMapper(
    private val protocolDiagnostic: (String) -> Unit = {},
) {
    fun map(name: String, arguments: String, originalText: String, context: VoiceAgentContext): VoiceActionDraft {
        val json = try {
            JsonParser.parseString(arguments).takeIf { it.isJsonObject }?.asJsonObject
                ?: throw InvalidVoiceToolCall("工具参数必须是 JSON object")
        } catch (error: InvalidVoiceToolCall) {
            throw error
        } catch (_: Exception) {
            throw InvalidVoiceToolCall("工具参数不是有效 JSON")
        }
        val legacy = when (name) {
            "create_event", "update_event" -> setOf("start_time", "end_date", "end_time", "target_start_time")
            "create_task", "update_task" -> setOf("start_time", "due_time", "start_date", "start_at")
            else -> emptySet()
        }
        if (json.keySet().any { it in legacy })
            throw InvalidVoiceToolCall("工具使用了旧时间字段")
        if (name == "update_event") {
            val targetDate = json.date("target_date")
            val targetStartAt = json.offsetDateTime("target_start_at", context.zone)
            if (targetDate != null && targetStartAt != null && targetDate != targetStartAt.toLocalDate())
                throw InvalidVoiceToolCall("目标日期和开始时间不一致")
        }
        val now = context.now.toEpochMilli()
        val sourceText = originalText.takeIf(String::isNotBlank) ?: json.optionalString("source_text").orEmpty()
        if (name in setOf("create_sleep_record", "start_sleep") && json.has("category_hint"))
            protocolDiagnostic("Sleep tool category_hint ignored")
        return when (name) {
            "create_task" -> draft(VoiceAction.CreateTask,
                stripLocation(json.string("title"), json.optionalString("location")), sourceText, now,
                taskFields(json, creating = true, zone = context.zone), semanticIntent = VoiceSemanticIntent.TaskRequest)
            "update_task" -> draft(VoiceAction.UpdateTask, json.string("target_query"), sourceText, now,
                taskFields(json, creating = false, zone = context.zone))
            "complete_task" -> {
                val target = json.string("target_query")
                draft(VoiceAction.CompleteTask, target, sourceText, now,
                    VoiceFields(title = VoiceFieldPatch.Set(target),
                        completedAt = json.instantMillis("completed_at") ?: now),
                    semanticIntent = VoiceSemanticIntent.TaskCompletion)
            }
            "reopen_task" -> draft(VoiceAction.UncompleteTask, json.string("target_query"), sourceText, now)
            "create_event" -> {
                val title = stripLocation(json.string("title"), json.optionalString("location"))
                val reportedFact = VoiceSemanticCue.isReportedFact(sourceText)
                val actualStart = json.instantMillis("start_at")
                val actualEnd = json.instantMillis("end_at")
                if (reportedFact && actualStart != null) {
                    draft(VoiceAction.CreateRecord, title, sourceText, now,
                        VoiceFields(title = VoiceFieldPatch.Set(title), startAt = actualStart,
                            endAt = actualEnd, location = json.stringPatch("location"),
                            note = json.stringPatch("note"), categoryHint = json.categoryHint()),
                        semanticIntent = if (actualEnd == null) VoiceSemanticIntent.ReportedPoint else VoiceSemanticIntent.ReportedActivity,
                        recordTemporalType = if (actualEnd == null) SessionTemporalType.POINT else SessionTemporalType.INTERVAL)
                } else draft(VoiceAction.CreateEvent, title, sourceText, now,
                    eventFields(json, creating = true, zone = context.zone), semanticIntent = VoiceSemanticIntent.PlannedArrangement,
                    eventTemporalType = json.eventTemporalType())
            }
            "update_event" -> draft(VoiceAction.UpdateEvent, json.string("target_query"), sourceText, now,
                eventFields(json, creating = false, zone = context.zone),
                targetDate = json.date("target_date") ?: json.offsetDateTime("target_start_at", context.zone)?.toLocalDate(),
                targetStart = json.offsetDateTime("target_start_at", context.zone)?.toLocalTime())
            "create_record", "create_record_and_complete_task" -> {
                val title = stripLocation(json.string("title"), json.optionalString("location"))
                val shape = json.recordTemporalType(required = true)!!
                if (name == "create_record_and_complete_task") json.string("task_query")
                draft(if (name == "create_record") VoiceAction.CreateRecord else VoiceAction.CreateRecordAndCompleteTask,
                    title, sourceText, now, recordFields(json, creating = true, shape = shape),
                    semanticIntent = if (shape == SessionTemporalType.POINT) VoiceSemanticIntent.ReportedPoint
                        else VoiceSemanticIntent.ReportedActivity, recordTemporalType = shape)
            }
            "create_sleep_record" -> {
                val kind = json.sleepKind()
                val location = json.optionalString("location")
                val title = stripLocation(json.optionalString("title") ?: kind.defaultSleepTitle(), location)
                draft(VoiceAction.CreateSleepRecord, title, sourceText, now,
                    VoiceFields(
                        title = VoiceFieldPatch.Set(title),
                        note = json.stringPatch("note"),
                        location = patch(location, false),
                        startAt = json.instantMillis("start_at"),
                        endAt = json.instantMillis("end_at"),
                        sleepKind = kind,
                    ),
                    semanticIntent = VoiceSemanticIntent.SleepRecord)
            }
            "update_record" -> draft(VoiceAction.UpdateRecord, json.string("target_query"), sourceText, now,
                recordFields(json, creating = false, shape = json.recordTemporalType(required = false)),
                targetStartAt = json.instantMillis("target_start_at"), recordTemporalType = json.recordTemporalType(required = false))
            "start_record" -> {
                val title = stripLocation(json.string("title"), json.optionalString("location"))
                draft(VoiceAction.StartRecord, title, sourceText, now,
                    VoiceFields(title = VoiceFieldPatch.Set(title), note = json.stringPatch("note"),
                        location = json.stringPatch("location"), startAt = now,
                        linkedTask = json.stringPatch("task_query"), categoryHint = json.categoryHint()),
                    semanticIntent = VoiceSemanticIntent.StartTimer)
            }
            "start_sleep" -> {
                val kind = json.sleepKind()
                val title = json.optionalString("title") ?: kind.defaultSleepTitle()
                draft(VoiceAction.StartSleep, title, sourceText, now,
                    VoiceFields(
                        title = VoiceFieldPatch.Set(title),
                        startAt = json.instantMillis("start_at") ?: now,
                        endAt = null,
                        sleepKind = kind,
                    ),
                    semanticIntent = VoiceSemanticIntent.StartSleep)
            }
            "stop_record", "stop_record_and_complete_task" -> draft(
                if (name == "stop_record") VoiceAction.StopRecord else VoiceAction.StopRecordAndCompleteTask,
                json.optionalString("target_query").orEmpty(), sourceText, now, VoiceFields(endAt = now))
            "create_record_from_event" -> {
                val target = json.string("event_query")
                val shape = json.recordTemporalType(required = true)!!
                draft(VoiceAction.CreateRecordFromEvent, target, sourceText, now,
                    VoiceFields(startAt = if (shape == SessionTemporalType.POINT) json.instantMillis("actual_at") else json.instantMillis("actual_start_at"),
                        endAt = json.instantMillis("actual_end_at")),
                    targetDate = json.date("event_date"), recordTemporalType = shape)
            }
            "request_clarification" -> clarificationDraft(json, sourceText, now, context)
            else -> throw UnsupportedVoiceToolCall(name)
        }
    }

    private fun taskFields(json: JsonObject, creating: Boolean, zone: ZoneId): VoiceFields {
        val clear = json.clearFields(setOf("start", "due", "estimate", "note", "location", "parent"))
        val startAt = json.offsetDateTime("planned_at", zone)
        val dueAt = json.offsetDateTime("due_at", zone)
        if (startAt != null && json.date("planned_date") != null)
            throw InvalidVoiceToolCall("planned_date 和 planned_at 不能同时提供")
        if (dueAt != null && json.date("due_date") != null)
            throw InvalidVoiceToolCall("due_date 和 due_at 不能同时提供")
        val location = json.optionalString("location")
        return VoiceFields(
            title = cleanedTitle(json, creating, location),
            note = patch(json.optionalString("note"), "note" in clear),
            date = patch(startAt?.toLocalDate() ?: json.date("planned_date"), "start" in clear),
            start = patch(startAt?.toLocalTime(), "start" in clear),
            dueDate = patch(dueAt?.toLocalDate() ?: json.date("due_date"), "due" in clear),
            dueTime = patch(dueAt?.toLocalTime(), "due" in clear),
            estimatedDuration = patch(json.optionalPositiveInt("estimated_duration_minutes"), "estimate" in clear),
            location = patch(location, "location" in clear),
            parentTask = patch(json.optionalString("parent_query"), "parent" in clear),
            categoryHint = if (creating) json.categoryHint() else null,
            reminderOffsetMinutes = json.reminderOffset(),
        )
    }

    private fun eventFields(json: JsonObject, creating: Boolean, zone: ZoneId): VoiceFields {
        val clear = json.clearFields(setOf("start", "end", "location", "note"))
        val startAt = json.offsetDateTime("start_at", zone)
        val endAt = json.offsetDateTime("end_at", zone)
        val date = json.date("date")
        if (date != null && startAt != null)
            throw InvalidVoiceToolCall("date 和 start_at 不能同时提供")
        if (creating && date == null && startAt == null)
            throw InvalidVoiceToolCall("缺少必填参数：date 或 start_at")
        val location = json.optionalString("location")
        return VoiceFields(
            title = cleanedTitle(json, creating, location),
            date = patch(startAt?.toLocalDate() ?: date, false),
            start = patch(startAt?.toLocalTime(), "start" in clear),
            endDate = patch(endAt?.toLocalDate(), "end" in clear),
            end = patch(endAt?.toLocalTime(), "end" in clear),
            location = patch(location, "location" in clear),
            note = patch(json.optionalString("note"), "note" in clear),
            categoryHint = if (creating) json.categoryHint() else null,
            reminderOffsetMinutes = json.reminderOffset(),
        )
    }

    private fun recordFields(json: JsonObject, creating: Boolean, shape: SessionTemporalType?): VoiceFields {
        val clear = json.clearFields(setOf("end", "location", "note", "task"))
        if (json.has("duration_minutes")) throw InvalidVoiceToolCall("记录必须提供实际时刻，不能仅凭时长推断")
        val occurredAt = json.instantMillis("occurred_at")
        val startAt = json.instantMillis("start_at")
        val endAt = json.instantMillis("end_at")
        if (occurredAt != null && (startAt != null || endAt != null))
            throw InvalidVoiceToolCall("occurred_at 与起止时间不能同时提供")
        if (shape == SessionTemporalType.POINT && (startAt != null || endAt != null || "end" in clear))
            throw InvalidVoiceToolCall("POINT 只能使用 occurred_at")
        if (shape == SessionTemporalType.INTERVAL && occurredAt != null)
            throw InvalidVoiceToolCall("INTERVAL 必须使用 start_at 和 end_at")
        if (creating && shape == SessionTemporalType.POINT && occurredAt == null)
            throw InvalidVoiceToolCall("POINT 缺少 occurred_at；日期事实应先澄清具体时刻")
        if (creating && shape == SessionTemporalType.INTERVAL && (startAt == null || endAt == null))
            throw InvalidVoiceToolCall("INTERVAL 必须同时提供 start_at 和 end_at")
        if ("end" in clear && endAt != null)
            throw InvalidVoiceToolCall("end_at 和清空 end 不能同时提供")
        if (startAt != null && endAt != null && endAt <= startAt)
            throw InvalidVoiceToolCall("结束时间必须晚于开始时间")
        val location = json.optionalString("location")
        return VoiceFields(
            title = cleanedTitle(json, creating, location),
            note = patch(json.optionalString("note"), "note" in clear),
            location = patch(location, "location" in clear),
            linkedTask = patch(json.optionalString("task_query"), "task" in clear),
            startAt = occurredAt ?: startAt,
            endAt = endAt,
            end = if ("end" in clear) VoiceFieldPatch.Clear else VoiceFieldPatch.Keep,
            categoryHint = if (creating) json.categoryHint() else null,
        )
    }

    private fun draft(action: VoiceAction, target: String, original: String, now: Long,
        fields: VoiceFields = VoiceFields(),
        targetDate: LocalDate? = null, targetStart: LocalTime? = null, targetStartAt: Long? = null,
        semanticIntent: VoiceSemanticIntent = VoiceSemanticIntent.Unknown,
        eventTemporalType: VoiceEventTemporalType? = null,
        recordTemporalType: SessionTemporalType? = null) =
        VoiceActionDraft(action, target, fields, original, now,
            targetDate = targetDate, targetStart = targetStart, targetStartAt = targetStartAt,
            semanticIntent = semanticIntent, eventTemporalType = eventTemporalType,
            recordTemporalType = recordTemporalType)

    private fun JsonObject.eventTemporalType(): VoiceEventTemporalType = when (string("temporal_type")) {
        "DATE" -> VoiceEventTemporalType.DATE
        "POINT" -> VoiceEventTemporalType.POINT
        "INTERVAL" -> VoiceEventTemporalType.INTERVAL
        else -> throw InvalidVoiceToolCall("Event temporal_type 必须是 DATE、POINT 或 INTERVAL")
    }

    private fun JsonObject.recordTemporalType(required: Boolean): SessionTemporalType? =
        when (if (required) string("temporal_type") else optionalString("temporal_type")) {
            null -> null
            "POINT" -> SessionTemporalType.POINT
            "INTERVAL" -> SessionTemporalType.INTERVAL
            else -> throw InvalidVoiceToolCall("Record temporal_type 必须是 POINT 或 INTERVAL")
        }

    private fun clarificationDraft(json: JsonObject, source: String, now: Long,
        context: VoiceAgentContext): VoiceActionDraft {
        val reason = when (json.string("reason")) {
            "OBJECT_TYPE_AMBIGUOUS" -> VoiceClarification.ObjectTypeAmbiguous
            "RECORD_MISSING_TIME" -> VoiceClarification.RecordMissingTime
            "INTERVAL_MISSING_BOUNDARY" -> VoiceClarification.IntervalMissingBoundary
            "TARGET_NOT_UNIQUE" -> VoiceClarification.TargetNotUnique
            "MULTIPLE_PRIMARY_OBJECTS" -> VoiceClarification.MultiplePrimaryObjects
            "TIME_SEMANTIC_CONFLICT" -> VoiceClarification.TimeSemanticConflict
            else -> throw InvalidVoiceToolCall("未知澄清原因")
        }
        val options = if (reason == VoiceClarification.ObjectTypeAmbiguous) {
            listOf("task_option" to VoiceType.Task, "event_option" to VoiceType.Event).map { (key, type) ->
                val option = json.get(key)?.takeIf { it.isJsonObject }?.asJsonObject
                    ?: throw InvalidVoiceToolCall("对象歧义必须提供 Task 与 Event 两个备选方案")
                val name = if (type == VoiceType.Task) "create_task" else "create_event"
                VoiceClarificationOption(type, map(name, option.toString(), source, context))
            }
        } else emptyList()
        val date = json.date("known_date")
        val title = json.optionalString("record_title") ?: source
        return VoiceActionDraft(
            action = if (reason == VoiceClarification.RecordMissingTime) VoiceAction.CreateRecord else VoiceAction.Clarify,
            targetText = title,
            fields = VoiceFields(title = VoiceFieldPatch.Set(title), date = date?.let { VoiceFieldPatch.Set(it) } ?: VoiceFieldPatch.Keep),
            originalText = source, nowMillis = now, clarificationReason = reason,
            clarificationMessage = json.optionalString("question"), clarificationOptions = options,
            recordTemporalType = if (reason == VoiceClarification.RecordMissingTime) SessionTemporalType.POINT else null,
            semanticIntent = if (reason == VoiceClarification.RecordMissingTime) VoiceSemanticIntent.ReportedPoint else VoiceSemanticIntent.Unknown,
        )
    }

    private fun <T> patch(value: T?, clear: Boolean): VoiceFieldPatch<T> = when {
        clear -> VoiceFieldPatch.Clear
        value != null -> VoiceFieldPatch.Set(value)
        else -> VoiceFieldPatch.Keep
    }

    private fun JsonObject.stringPatch(name: String, required: Boolean = false): VoiceFieldPatch<String> =
        patch(if (required) string(name) else optionalString(name), false)

    /** prompt 已约束 title 不含地点；这里做确定性兜底清洗。 */
    private fun cleanedTitle(json: JsonObject, required: Boolean, location: String?): VoiceFieldPatch<String> =
        patch(if (required) stripLocation(json.string("title"), location)
        else json.optionalString("title")?.let { stripLocation(it, location) }, false)

    private fun stripLocation(title: String, location: String?): String {
        val loc = location?.trim().orEmpty()
        if (loc.isEmpty()) return title
        // 仅剥离句首的“在XX”“XX”或句尾的“XX”，避免误删标题中其他位置的同名字符
        return title
            .replace(Regex("^在?${Regex.escape(loc)}[，,、]?\\s*"), "")
            .replace(Regex("\\s*[，,、]?${Regex.escape(loc)}$"), "")
            .trim()
            .ifEmpty { title }
    }

    private fun JsonObject.string(name: String): String = optionalString(name)
        ?: throw InvalidVoiceToolCall("缺少必填参数：$name")

    private fun JsonObject.optionalString(name: String): String? {
        val value = get(name) ?: return null
        if (value.isJsonNull) return null
        if (!value.isJsonPrimitive || !value.asJsonPrimitive.isString)
            throw InvalidVoiceToolCall("$name 必须是字符串")
        return value.asString.trim().takeIf { it.isNotEmpty() }
            ?: throw InvalidVoiceToolCall("$name 不能为空")
    }

    private fun JsonObject.categoryHint(): String? =
        optionalString("category_hint")?.take(MAX_CATEGORY_HINT_LENGTH)

    private fun JsonObject.optionalBoolean(name: String): Boolean? {
        val value = get(name) ?: return null
        if (!value.isJsonPrimitive || !value.asJsonPrimitive.isBoolean)
            throw InvalidVoiceToolCall("$name 必须是布尔值")
        return value.asBoolean
    }

    private fun JsonObject.optionalPositiveInt(name: String): Int? {
        val value = get(name) ?: return null
        if (!value.isJsonPrimitive || !value.asJsonPrimitive.isNumber)
            throw InvalidVoiceToolCall("$name 必须是整数")
        val number = try { value.asInt } catch (_: Exception) {
            throw InvalidVoiceToolCall("$name 必须是整数")
        }
        if (number <= 0 || value.asDouble != number.toDouble())
            throw InvalidVoiceToolCall("$name 必须是正整数")
        return number
    }

    private fun JsonObject.sleepKind(): SessionKind = when (string("sleep_type")) {
        "nap" -> SessionKind.NAP
        "night_sleep" -> SessionKind.NIGHT_SLEEP
        else -> throw InvalidVoiceToolCall("sleep_type 必须是 nap 或 night_sleep")
    }

    private fun JsonObject.reminderOffset(): Int? = optionalPositiveInt("reminder_offset_minutes")?.also {
        if (it !in setOf(5, 10, 30, 60)) throw InvalidVoiceToolCall("提醒提前时间只支持 5、10、30 或 60 分钟")
    }

    private fun SessionKind.defaultSleepTitle(): String = when (this) {
        SessionKind.NAP -> "小憩"
        SessionKind.NIGHT_SLEEP -> "夜间睡眠"
        SessionKind.ACTIVITY -> throw InvalidVoiceToolCall("无效的睡眠类型")
    }

    private fun JsonObject.date(name: String, required: Boolean = false): LocalDate? {
        val raw = if (required) string(name) else optionalString(name) ?: return null
        return try { LocalDate.parse(raw) } catch (_: DateTimeException) {
            throw InvalidVoiceToolCall("$name 必须使用 YYYY-MM-DD")
        }
    }

    private fun JsonObject.offsetDateTime(name: String, zone: ZoneId): ZonedDateTime? {
        val raw = optionalString(name) ?: return null
        return try {
            OffsetDateTime.parse(raw).also {
                if (it.second != 0 || it.nano != 0)
                    throw InvalidVoiceToolCall("$name 只支持整分钟时间")
            }.atZoneSameInstant(zone)
        } catch (error: InvalidVoiceToolCall) {
            throw error
        } catch (_: DateTimeException) {
            throw InvalidVoiceToolCall("$name 必须是带时区偏移的 ISO-8601 时间")
        }
    }

    private fun JsonObject.instantMillis(name: String): Long? {
        val raw = optionalString(name) ?: return null
        return try { OffsetDateTime.parse(raw).toInstant().toEpochMilli() } catch (_: DateTimeException) {
            throw InvalidVoiceToolCall("$name 必须是带时区偏移的 ISO-8601 时间")
        }
    }

    private fun JsonObject.clearFields(allowed: Set<String>): Set<String> {
        val value = get("clear_fields") ?: return emptySet()
        if (!value.isJsonArray) throw InvalidVoiceToolCall("clear_fields 必须是字符串数组")
        return value.asJsonArray.map {
            if (!it.isJsonPrimitive || !it.asJsonPrimitive.isString)
                throw InvalidVoiceToolCall("clear_fields 必须是字符串数组")
            it.asString
        }.toSet().also { fields ->
            val unknown = fields - allowed
            if (unknown.isNotEmpty()) throw InvalidVoiceToolCall("不支持清空字段：${unknown.joinToString()}")
        }
    }
}
