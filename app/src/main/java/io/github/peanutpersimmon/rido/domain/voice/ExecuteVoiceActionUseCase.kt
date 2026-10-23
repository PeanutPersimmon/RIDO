package io.github.peanutpersimmon.rido.domain.voice

import io.github.peanutpersimmon.rido.domain.model.*
import io.github.peanutpersimmon.rido.domain.repository.RidoCoreRepository
import io.github.peanutpersimmon.rido.domain.usecase.*
import java.time.ZoneId

sealed interface VoiceExecutionResult {
    data class Executed(val receipt: VoiceExecutionReceipt) : VoiceExecutionResult
    data class Fallback(val prepared: VoicePrepared) : VoiceExecutionResult
}

/** Preparation and writes occur in one repository transaction; all saves still use existing business operations. */
class ExecuteVoiceActionUseCase(private val repository: RidoCoreRepository,
    private val zone: ZoneId = ZoneId.systemDefault(), private val now: () -> Long = System::currentTimeMillis) {
    suspend operator fun invoke(d: VoiceActionDraft, targetId: Long? = null,
        linked: VoiceFieldPatch<Long> = VoiceFieldPatch.Keep,
        attempt: VoiceExecutionAttempt = VoiceExecutionAttempt()): VoiceExecutionResult =
        attempt.run { perform(d, targetId, linked) }

    private suspend fun perform(d: VoiceActionDraft, targetId: Long?, linked: VoiceFieldPatch<Long>): VoiceExecutionResult = try {
        repository.voiceTransaction {
            val taskAction = d.action in setOf(VoiceAction.UpdateTask, VoiceAction.CompleteTask, VoiceAction.UncompleteTask)
            val eventAction = d.action in setOf(VoiceAction.UpdateEvent, VoiceAction.CreateRecordFromEvent)
            val recordAction = d.action in setOf(VoiceAction.UpdateRecord, VoiceAction.StopRecord,
                VoiceAction.StopRecordAndCompleteTask)
            val task = if (taskAction) targetId?.let { repository.getTask(it) } else null
            if (d.action == VoiceAction.CompleteTask && task != null && task.task.status in setOf(TaskStatus.DONE, TaskStatus.CANCELLED))
                return@voiceTransaction VoiceExecutionResult.Fallback(VoicePrepared(error = "任务状态已变化，请重新输入"))
            val event = if (eventAction) targetId?.let { repository.getEvent(it) } else null
            val record = if (recordAction) targetId?.let { repository.getSession(it) } else null
            if (taskAction && task == null || eventAction && event == null || recordAction && record == null)
                return@voiceTransaction VoiceExecutionResult.Fallback(VoicePrepared(error = "内容已不存在，请重新输入"))
            val linkedTask = (linked as? VoiceFieldPatch.Set)?.let { repository.getTask(it.value)?.task }
            if (linked is VoiceFieldPatch.Set && linkedTask == null)
                return@voiceTransaction VoiceExecutionResult.Fallback(VoicePrepared(error = "关联任务已不存在"))
            if (d.action in setOf(VoiceAction.CreateRecord, VoiceAction.CreateRecordAndCompleteTask,
                    VoiceAction.StartRecord) && linkedTask?.status == TaskStatus.CANCELLED)
                return@voiceTransaction VoiceExecutionResult.Fallback(VoicePrepared(error = "不能关联已取消任务"))
            if (d.action == VoiceAction.StartRecord && linkedTask?.status == TaskStatus.DONE)
                return@voiceTransaction VoiceExecutionResult.Fallback(VoicePrepared(error = "进行中记录不能关联已完成任务"))
            if (d.action == VoiceAction.CreateRecordAndCompleteTask) {
                if (d.fields.linkedTask !is VoiceFieldPatch.Set)
                    return@voiceTransaction VoiceExecutionResult.Fallback(VoicePrepared(error = "请明确指定已有任务"))
                val linkedId = (linked as? VoiceFieldPatch.Set)?.value
                    ?: return@voiceTransaction VoiceExecutionResult.Fallback(VoicePrepared(error = "请先明确要完成的已有任务"))
                if (linkedTask?.id != linkedId)
                    return@voiceTransaction VoiceExecutionResult.Fallback(VoicePrepared(error = "关联任务已不存在"))
                if (linkedTask.status == TaskStatus.DONE || linkedTask.status == TaskStatus.CANCELLED)
                    return@voiceTransaction VoiceExecutionResult.Fallback(VoicePrepared(error = "任务已完成或已取消，请先重新打开"))
            }
            val classifiedCreate = d.action in setOf(VoiceAction.CreateTask, VoiceAction.CreateEvent,
                VoiceAction.CreateRecord, VoiceAction.CreateRecordAndCompleteTask, VoiceAction.StartRecord)
            val classification = if (classifiedCreate) {
                val categoryHint = d.fields.categoryHint
                if (categoryHint.isNullOrBlank()) {
                    InitialClassificationUseCase(repository)(InitialClassificationInput(
                        fTitle(d),
                        applyKeywordRules = true,
                    ))
                } else {
                    CategoryHintResolver.resolve(
                        categoryHint = categoryHint,
                        categories = repository.getCategories(),
                        rules = repository.getEnabledKeywordRules(),
                    )
                }
            } else null
            val plan = VoiceExecutionPlanner.prepare(d, task, event, record, linked, classification, zone, now())
            if (!plan.canExecuteDirectly) return@voiceTransaction VoiceExecutionResult.Fallback(plan)
            val completion = d.action == VoiceAction.CreateRecordAndCompleteTask ||
                task != null && d.action in setOf(VoiceAction.CompleteTask, VoiceAction.UncompleteTask) ||
                d.action == VoiceAction.StopRecordAndCompleteTask && record?.session?.taskId != null
            // Completing/reopening can change ancestors. Capture those existing business effects for undo too.
            val taskBefore = if (completion) repository.getAllTasks().mapNotNull { repository.getTask(it.id) } else listOfNotNull(task)
            val before = VoiceDataSnapshot(taskBefore, if (d.action == VoiceAction.UpdateEvent) listOfNotNull(event) else emptyList(), listOfNotNull(record))
            var taskId: Long? = task?.task?.id
            var eventId: Long? = null
            var recordId: Long? = record?.session?.id
            when (d.action) {
                VoiceAction.CreateTask, VoiceAction.UpdateTask -> plan.task!!.let { taskId = SaveTaskUseCase(repository)(it.task, it.tagIds).requireVoiceSuccess() }
                VoiceAction.CreateEvent, VoiceAction.UpdateEvent -> plan.event!!.let { eventId = SaveEventUseCase(repository)(it.event, it.tagIds).requireVoiceSuccess() }
                VoiceAction.CompleteTask -> CompleteTaskUseCase(repository)(requireNotNull(task).task.id,
                    d.fields.completedAt!!).requireVoiceSuccess()
                VoiceAction.UncompleteTask -> ReopenTaskUseCase(repository)(task!!.task.id, now()).requireVoiceSuccess()
                VoiceAction.StopRecord, VoiceAction.StopRecordAndCompleteTask ->
                    repository.stopConfirmedSession(record!!.session.id, record.session.taskId,
                        d.fields.endAt ?: d.nowMillis,
                        d.action == VoiceAction.StopRecordAndCompleteTask).requireVoiceSuccess()
                VoiceAction.UpdateRecord -> plan.record!!.let { SaveSessionEditUseCase(repository)(it.session, it.tagIds).requireVoiceSuccess() }
                VoiceAction.CreateSleepRecord, VoiceAction.StartSleep -> {
                    val s = plan.record!!.session
                    val input = NewSessionInput(
                        title = s.title,
                        startAtMillis = s.startAtMillis,
                        endAtMillis = s.endAtMillis,
                        taskId = null,
                        categoryId = null,
                        tagIds = emptySet(),
                        note = s.note,
                        location = s.location,
                        kind = s.kind,
                        createdAtMillis = s.createdAtMillis,
                        sourceEventId = null,
                    )
                    val sleep = CreateSleepSessionUseCase(repository)
                    recordId = if (d.action == VoiceAction.StartSleep) sleep.start(input).requireVoiceSuccess()
                        else sleep.create(input).requireVoiceSuccess()
                }
                VoiceAction.CreateRecord, VoiceAction.CreateRecordAndCompleteTask, VoiceAction.StartRecord, VoiceAction.CreateRecordFromEvent -> {
                    val r = plan.record!!
                    val s = r.session
                    val input = NewSessionInput(s.title, s.startAtMillis, s.endAtMillis, s.taskId, s.categoryId, r.tagIds,
                        s.note, s.location, s.kind, s.createdAtMillis, sourceEventId = s.sourceEventId,
                        temporalType = s.temporalType)
                    recordId = if (d.action == VoiceAction.StartRecord) StartSessionUseCase(repository)(input, requireIdle = true).requireVoiceSuccess()
                        else if (d.action == VoiceAction.CreateRecordAndCompleteTask)
                            CreatePastSessionAndCompleteTaskUseCase(repository)(input).requireVoiceSuccess()
                        else CreatePastSessionUseCase(repository)(input).requireVoiceSuccess()
                }
                VoiceAction.Clarify -> return@voiceTransaction VoiceExecutionResult.Fallback(VoicePrepared(error = "请先澄清输入"))
            }
            val tasksAfter = if (completion) taskBefore.mapNotNull { repository.getTask(it.task.id) }
                .filter { updated -> taskBefore.first { it.task.id == updated.task.id } != updated }
                else listOfNotNull(taskId?.let { repository.getTask(it) })
            val exactBefore = before.copy(tasks = if (completion) taskBefore.filter { old -> tasksAfter.any { it.task.id == old.task.id } } else taskBefore)
            val after = VoiceDataSnapshot(tasksAfter, listOfNotNull(eventId?.let { repository.getEvent(it) }), listOfNotNull(recordId?.let { repository.getSession(it) }))
            val title = plan.task?.task?.title ?: plan.event?.event?.title ?: plan.record?.session?.title ?: d.targetText
            val verb = when (d.action) {
                VoiceAction.CreateTask -> "已创建"; VoiceAction.CreateEvent -> "已创建"
                VoiceAction.CreateRecord, VoiceAction.CreateRecordFromEvent -> "已创建"
                VoiceAction.CreateRecordAndCompleteTask -> "已创建并完成任务"
                VoiceAction.CreateSleepRecord -> "已创建"
                VoiceAction.StartRecord, VoiceAction.StartSleep -> "已开始"
                VoiceAction.StopRecord, VoiceAction.StopRecordAndCompleteTask -> "已结束"
                VoiceAction.CompleteTask -> "已完成"; VoiceAction.UncompleteTask -> "已重新打开"
                else -> "已修改"
            }
            VoiceExecutionResult.Executed(VoiceExecutionReceipt("$verb「$title」", VoiceUndo(exactBefore, after)))
        }
    } catch (e: VoiceWriteRejected) { VoiceExecutionResult.Fallback(VoicePrepared(error = when (val error = e.error) {
        is DomainError.DataIntegrity -> error.reason
        DomainError.SessionOverlap -> "时间与已有记录重叠"
        else -> "业务校验未通过，请修改输入或打开编辑器"
    })) }
    private fun fTitle(d: VoiceActionDraft) = d.fields.title.applyTo(null) ?: d.targetText
}
