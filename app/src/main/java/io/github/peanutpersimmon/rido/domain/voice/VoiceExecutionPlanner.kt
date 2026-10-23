package io.github.peanutpersimmon.rido.domain.voice

import io.github.peanutpersimmon.rido.domain.model.*
import io.github.peanutpersimmon.rido.domain.service.ResolvedClassification
import io.github.peanutpersimmon.rido.domain.validation.DataValidator
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

enum class VoiceMissingField { Date, Start, EndDate, End, DueDate, DueTime, CompletionDate, CompletionTime }
data class VoicePrepared(
    val task: TaggedTask? = null, val event: TaggedEvent? = null, val record: TaggedSession? = null,
    val missing: Set<VoiceMissingField> = emptySet(), val error: String? = null,
    val futureRecord: Boolean = false,
) {
    val canExecuteDirectly: Boolean get() = missing.isEmpty() && error == null
}

/** Pure preparation shared by direct execution and fallback. Optional editor fields are not requirements. */
object VoiceExecutionPlanner {
    fun prepare(d: VoiceActionDraft, task: TaggedTask? = null, event: TaggedEvent? = null,
        record: TaggedSession? = null, linked: VoiceFieldPatch<Long> = VoiceFieldPatch.Keep,
        classification: ResolvedClassification? = null, zone: ZoneId = ZoneId.systemDefault(),
        now: Long = System.currentTimeMillis()): VoicePrepared {
        val f = d.fields
        val missing = mutableSetOf<VoiceMissingField>()
        if (d.ambiguousStart) missing += if (d.action == VoiceAction.CompleteTask) VoiceMissingField.CompletionTime else VoiceMissingField.Start
        if (d.ambiguousEnd) missing += VoiceMissingField.End
        if (d.ambiguousDue) missing += VoiceMissingField.DueTime
        fun date(day: Long?) = day?.let(LocalDate::ofEpochDay)
        fun minute(value: Int?) = value?.let { LocalTime.MIN.plusMinutes(it.toLong()) }
        fun millis(day: LocalDate?, time: LocalTime?): Long? = if (day != null && time != null) day.atTime(time).atZone(zone).toInstant().toEpochMilli() else null
        val today = Instant.ofEpochMilli(d.nowMillis).atZone(zone).toLocalDate()
        when (d.action) {
            VoiceAction.CompleteTask -> {
                if (f.completedAt == null) missing += VoiceMissingField.CompletionTime
                return VoicePrepared(task = task, missing = missing, error = when {
                    task == null -> "没有找到要完成的任务"
                    f.completedAt?.let { it > now } == true -> "完成时间不能在未来"
                    else -> null
                })
            }
            VoiceAction.UncompleteTask -> return VoicePrepared(error = if (task == null) "没有找到要修改的任务" else null)
            VoiceAction.StopRecord, VoiceAction.StopRecordAndCompleteTask -> return VoicePrepared(record = record,
                error = when {
                    record == null || record.session.temporalType != SessionTemporalType.INTERVAL || record.session.endAtMillis != null -> "没有找到进行中的记录"
                    d.action == VoiceAction.StopRecordAndCompleteTask && record.session.taskId == null -> "这条记录没有关联任务"
                    else -> null
                })
            VoiceAction.CreateSleepRecord, VoiceAction.StartSleep -> {
                val range = VoiceIntentPolicy.temporalRange(d, zone)
                val running = d.action == VoiceAction.StartSleep
                val start = range.startAt
                val end = if (running) null else range.endAt
                if (start == null) missing += VoiceMissingField.Start
                if (!running && end == null) missing += VoiceMissingField.End
                val kind = VoiceIntentPolicy.resolvedSleepKind(d, zone)
                if (start == null || !running && end == null) return VoicePrepared(missing = missing)
                val result = Session(
                    title = f.title.applyTo(null) ?: when (kind) {
                        SessionKind.NIGHT_SLEEP -> "夜间睡眠"
                        else -> "小憩"
                    },
                    kind = kind ?: SessionKind.NAP,
                    startAtMillis = start,
                    endAtMillis = end,
                    taskId = null,
                    categoryId = null,
                    note = f.note.applyTo(null),
                    location = f.location.applyTo(null),
                    createdAtMillis = now,
                    updatedAtMillis = now,
                    sourceEventId = null,
                )
                val future = start?.let { it > now } == true || end?.let { it > now } == true
                return VoicePrepared(
                    record = TaggedSession(result, emptySet()),
                    missing = missing,
                    futureRecord = future,
                    error = when {
                        kind !in setOf(SessionKind.NAP, SessionKind.NIGHT_SLEEP) -> "当前无法创建睡眠记录"
                        future -> "睡眠记录不能发生在未来"
                        missing.isEmpty() && DataValidator.validateActualSession(result, now) != null -> "睡眠时间无效，请修改"
                        else -> null
                    },
                )
            }
            VoiceAction.CreateTask, VoiceAction.UpdateTask -> {
                val base = task?.task ?: Task(title = d.targetText, createdAtMillis = now, updatedAtMillis = now)
                val day = f.date.applyTo(date(base.plannedDateEpochDay))
                val time = f.start.applyTo(minute(base.plannedStartMinuteOfDay))
                val dueDay = f.dueDate.applyTo(date(base.dueDateEpochDay))
                val dueTime = f.dueTime.applyTo(minute(base.dueMinuteOfDay))
                if (day == null && time != null) missing += VoiceMissingField.Date
                if (dueDay == null && dueTime != null) missing += VoiceMissingField.DueDate
                val result = base.copy(title = f.title.applyTo(base.title).orEmpty(),
                    note = f.note.applyTo(base.note),
                    location = DataValidator.normalizedTaskLocation(f.location.applyTo(base.location)),
                    plannedDateEpochDay = day?.toEpochDay(), plannedStartMinuteOfDay = time?.toSecondOfDay()?.div(60),
                    dueDateEpochDay = dueDay?.toEpochDay(), dueMinuteOfDay = dueTime?.toSecondOfDay()?.div(60),
                    estimatedDurationMinutes = f.estimatedDuration.applyTo(base.estimatedDurationMinutes),
                    plannedReminderOffsetMinutes = if (time != null) f.reminderOffsetMinutes ?: base.plannedReminderOffsetMinutes else null,
                    dueReminderOffsetMinutes = if (time == null && dueTime != null) f.reminderOffsetMinutes ?: base.dueReminderOffsetMinutes else base.dueReminderOffsetMinutes,
                    parentTaskId = linked.applyTo(base.parentTaskId),
                    categoryId = classification?.categoryId ?: base.categoryId, updatedAtMillis = now)
                return VoicePrepared(task = TaggedTask(result, classification?.tagIds ?: task?.tagIds.orEmpty()), missing = missing,
                    error = if (missing.isEmpty() && DataValidator.validateTask(result).isNotEmpty()) "任务字段无效，请修改" else null)
            }
            VoiceAction.CreateEvent, VoiceAction.UpdateEvent -> {
                val base = event?.event
                val day = f.date.applyTo(date(base?.dateEpochDay))
                if (day == null) missing += VoiceMissingField.Date
                val start = f.start.applyTo(minute(base?.startMinuteOfDay))
                val end = f.end.applyTo(minute(base?.endMinuteOfDay))
                if (end != null && start == null) missing += VoiceMissingField.Start
                val shiftedEnd = base?.endDateEpochDay?.let { old ->
                    if (day != null && f.date is VoiceFieldPatch.Set) old + day.toEpochDay() - base.dateEpochDay else old
                }
                if (day == null) return VoicePrepared(missing = missing)
                val result = (base ?: Event(title = d.targetText, dateEpochDay = day.toEpochDay(), createdAtMillis = now, updatedAtMillis = now)).copy(
                    title = f.title.applyTo(base?.title ?: d.targetText).orEmpty(), dateEpochDay = day.toEpochDay(),
                    startMinuteOfDay = start?.toSecondOfDay()?.div(60), endMinuteOfDay = end?.toSecondOfDay()?.div(60),
                    endDateEpochDay = if (end == null) null else f.endDate.applyTo(date(shiftedEnd))?.toEpochDay(),
                    startReminderOffsetMinutes = f.reminderOffsetMinutes ?: base?.startReminderOffsetMinutes,
                    location = f.location.applyTo(base?.location), note = f.note.applyTo(base?.note),
                    categoryId = classification?.categoryId ?: base?.categoryId, updatedAtMillis = now)
                return VoicePrepared(event = TaggedEvent(result, classification?.tagIds ?: event?.tagIds.orEmpty()), missing = missing,
                    error = if (missing.isEmpty() && DataValidator.validateEvent(result).isNotEmpty()) "日程结束时间不能早于开始时间" else null)
            }
            VoiceAction.CreateRecord, VoiceAction.CreateRecordAndCompleteTask, VoiceAction.UpdateRecord, VoiceAction.StartRecord,
            VoiceAction.CreateRecordFromEvent -> {
                val copy = if (d.action == VoiceAction.CreateRecordFromEvent) event?.event else null
                val base = record?.session
                val wasActive = base?.temporalType == SessionTemporalType.INTERVAL && base.endAtMillis == null
                val clearsCompletedEnd = d.action == VoiceAction.UpdateRecord &&
                    f.end == VoiceFieldPatch.Clear && base?.temporalType == SessionTemporalType.INTERVAL &&
                    base.endAtMillis != null
                val addsEnd = d.action == VoiceAction.UpdateRecord &&
                    (f.endAt != null || f.end is VoiceFieldPatch.Set)
                val shape = when {
                    d.action == VoiceAction.StartRecord -> SessionTemporalType.INTERVAL
                    clearsCompletedEnd -> SessionTemporalType.POINT
                    addsEnd -> SessionTemporalType.INTERVAL
                    else -> d.recordTemporalType ?: base?.temporalType
                } ?: return VoicePrepared(error = "记录缺少时间形态")
                val localDate = f.date.applyTo(base?.startAtMillis?.let {
                    Instant.ofEpochMilli(it).atZone(zone).toLocalDate()
                })
                val localTime = f.start.applyTo(base?.startAtMillis?.let {
                    Instant.ofEpochMilli(it).atZone(zone).toLocalTime()
                })
                val start = if (d.action == VoiceAction.CreateRecordFromEvent) f.startAt
                    else f.startAt ?: millis(localDate, localTime) ?: base?.startAtMillis
                if (start == null) missing += VoiceMissingField.Start
                val explicitEndDay = f.endDate.applyTo(base?.endAtMillis?.let {
                    Instant.ofEpochMilli(it).atZone(zone).toLocalDate()
                }) ?: localDate
                val explicitEndTime = f.end.applyTo(base?.endAtMillis?.let {
                    Instant.ofEpochMilli(it).atZone(zone).toLocalTime()
                })
                val running = d.action == VoiceAction.StartRecord ||
                    d.action == VoiceAction.UpdateRecord && wasActive && !addsEnd
                val end = if (shape == SessionTemporalType.POINT || running) null
                    else if (d.action == VoiceAction.CreateRecordFromEvent) f.endAt
                    else f.endAt ?: millis(explicitEndDay, explicitEndTime) ?: base?.endAtMillis
                if (shape == SessionTemporalType.INTERVAL && !running && end == null) missing += VoiceMissingField.End
                if (missing.isNotEmpty()) return VoicePrepared(missing = missing)
                val actualStart = requireNotNull(start)
                val result = (base ?: Session(
                    title = copy?.title ?: d.targetText, startAtMillis = actualStart,
                    createdAtMillis = now, updatedAtMillis = now,
                )).copy(
                    title = f.title.applyTo(base?.title ?: copy?.title ?: d.targetText).orEmpty(),
                    temporalType = shape, startAtMillis = actualStart, endAtMillis = end,
                    location = f.location.applyTo(base?.location ?: copy?.location),
                    note = f.note.applyTo(base?.note ?: copy?.note),
                    taskId = linked.applyTo(base?.taskId),
                    categoryId = classification?.categoryId ?: base?.categoryId ?: copy?.categoryId,
                    sourceEventId = copy?.id ?: base?.sourceEventId,
                    updatedAtMillis = now,
                )
                val future = actualStart > now || end?.let { it > now } == true
                if (d.action == VoiceAction.CreateRecordAndCompleteTask && linked !is VoiceFieldPatch.Set)
                    return VoicePrepared(error = "请先明确要完成的已有任务")
                return VoicePrepared(
                    record = TaggedSession(result, classification?.tagIds ?: record?.tagIds ?: event?.tagIds.orEmpty()),
                    futureRecord = future,
                    error = when {
                        future -> "记录不能发生在未来"
                        DataValidator.validateActualSession(result, now) != null -> "记录时间无效，请修改"
                        else -> null
                    },
                )
            }
            VoiceAction.Clarify -> return VoicePrepared(error = "请先澄清输入")
        }
    }
}
