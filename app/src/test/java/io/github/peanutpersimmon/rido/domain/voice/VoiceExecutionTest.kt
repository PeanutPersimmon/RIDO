package io.github.peanutpersimmon.rido.domain.voice

import io.github.peanutpersimmon.rido.domain.model.*
import io.github.peanutpersimmon.rido.domain.repository.*
import io.github.peanutpersimmon.rido.domain.voice.agent.QwenToolCallMapper
import io.github.peanutpersimmon.rido.domain.voice.agent.VoiceAgentContext
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class VoiceExecutionTest {
    private val now = Instant.parse("2026-09-22T10:00:00Z")
    private val zone = ZoneId.of("Asia/Shanghai")
    private fun draft(text: String): VoiceActionDraft {
        val command = when (text) {
            "拿到快递了" -> "create_record" to """{"title":"拿快递","temporal_type":"POINT","occurred_at":"2026-09-22T18:00:00+08:00"}"""
            "补录看文献" -> "create_record" to """{"title":"看文献","temporal_type":"INTERVAL","start_at":"2026-09-22T15:30:00+08:00","end_at":"2026-09-22T17:30:00+08:00","task_query":"看文献"}"""
            "补录并完成看文献" -> "create_record_and_complete_task" to """{"title":"看文献","temporal_type":"INTERVAL","start_at":"2026-09-22T15:30:00+08:00","end_at":"2026-09-22T17:30:00+08:00","task_query":"看文献"}"""
            "取快递并完成任务" -> "create_record_and_complete_task" to """{"title":"取快递","temporal_type":"POINT","occurred_at":"2026-09-22T17:26:00+08:00","task_query":"取快递"}"""
            "完成快递任务" -> "complete_task" to """{"target_query":"快递"}"""
            "完成论文任务" -> "complete_task" to """{"target_query":"论文"}"""
            "今晚19-20在父亲楼有一节选修课" -> "create_event" to """{"title":"选修课","temporal_type":"INTERVAL","start_at":"2026-09-22T19:00:00+08:00","end_at":"2026-09-22T20:00:00+08:00","location":"父亲楼"}"""
            "任务 买牙膏" -> "create_task" to """{"title":"买牙膏"}"""
            "去医院取报告" -> "create_task" to """{"title":"取报告","location":"医院"}"""
            "取报告改在实验室" -> "update_task" to """{"target_query":"取报告","location":"实验室"}"""
            "明天有组会" -> "create_event" to """{"title":"组会","temporal_type":"DATE","date":"2026-09-23"}"""
            "论文截止改到下周一" -> "update_task" to """{"target_query":"论文","due_date":"2026-09-28"}"""
            "论文截止改到周五" -> "update_task" to """{"target_query":"论文","due_date":"2026-09-25"}"""
            "开始写论文" -> "start_record" to """{"title":"写论文"}"""
            "开始跑步" -> "start_record" to """{"title":"跑步"}"""
            "午睡了半小时" -> "create_sleep_record" to """{"sleep_type":"nap","start_at":"2026-09-22T12:00:00+08:00","end_at":"2026-09-22T12:30:00+08:00"}"""
            "开始午睡" -> "start_sleep" to """{"sleep_type":"nap"}"""
            "把选修课记成记录" -> "create_record_from_event" to """{"event_query":"选修课","temporal_type":"INTERVAL","actual_start_at":"2026-09-22T14:00:00+08:00","actual_end_at":"2026-09-22T15:00:00+08:00"}"""
            "记录明天下午三点到四点组会" -> "create_record" to """{"title":"组会","temporal_type":"INTERVAL","start_at":"2026-09-23T15:00:00+08:00","end_at":"2026-09-23T16:00:00+08:00"}"""
            else -> error("No structured fixture for $text")
        }
        return QwenToolCallMapper().map(command.first, command.second, text, VoiceAgentContext(now, zone))
    }
    private fun executor(repo: MemoryVoiceRepository) = ExecuteVoiceActionUseCase(repo, zone) { now.toEpochMilli() }

    @Test fun sleepCreationUsesNapBoundarySupportsUndoAndProtectsActiveSession() = runBlocking {
        val repo = MemoryVoiceRepository().apply {
            rules = listOf(KeywordRule(keyword = "午睡", categoryId = 9, tagIds = setOf(8),
                createdAtMillis = 0, updatedAtMillis = 0))
        }
        val completed = executor(repo)(draft("午睡了半小时")) as VoiceExecutionResult.Executed
        val nap = repo.records.values.single()
        assertEquals(SessionKind.NAP, nap.session.kind)
        assertNull(nap.session.categoryId)
        assertNull(nap.session.taskId)
        assertTrue(nap.tagIds.isEmpty())
        assertEquals(0, repo.ruleReads)
        assertTrue(UndoVoiceActionUseCase(repo)(completed.receipt.undo) is DomainResult.Success)
        assertTrue(repo.records.isEmpty())

        val started = executor(repo)(draft("开始午睡")) as VoiceExecutionResult.Executed
        assertNull(repo.records.values.single().session.endAtMillis)
        assertEquals("已开始「小憩」", started.receipt.message)
        assertTrue(executor(repo)(draft("开始午睡")) is VoiceExecutionResult.Fallback)
        assertEquals(1, repo.records.size)
    }

    @Test fun actualFactCreatesOnlyPointRecordAndUndoDeletesIt() = runBlocking {
        val repo = MemoryVoiceRepository()
        val command = draft("拿到快递了")
        val attempt = VoiceExecutionAttempt()
        val result = executor(repo)(command, attempt = attempt) as VoiceExecutionResult.Executed
        assertSame(result, executor(repo)(command, attempt = attempt))
        assertEquals(SessionTemporalType.POINT, repo.records.values.single().session.temporalType)
        assertTrue(repo.tasks.isEmpty())
        assertEquals(0, repo.completeCalls)
        assertTrue(UndoVoiceActionUseCase(repo)(result.receipt.undo) is DomainResult.Success)
        assertTrue(repo.records.isEmpty())
    }
    @Test fun historicalRecordCompletionRequiresExplicitActionAndUsesActualBoundary() = runBlocking {
        val repo = MemoryVoiceRepository()
        repo.tasks[7] = TaggedTask(Task(id = 7, title = "看文献", status = TaskStatus.TODO,
            createdAtMillis = 0, updatedAtMillis = 0))
        val linked = VoiceFieldPatch.Set(7L)
        assertTrue(executor(repo)(draft("补录看文献"), linked = linked) is VoiceExecutionResult.Executed)
        assertEquals(TaskStatus.TODO, repo.tasks[7]!!.task.status)
        assertEquals(0, repo.completeCalls)
        repo.records.clear()
        assertTrue(executor(repo)(draft("补录并完成看文献"), linked = linked) is VoiceExecutionResult.Executed)
        val completed = repo.tasks[7]!!.task
        assertEquals(TaskStatus.DONE, completed.status)
        assertEquals(draft("补录并完成看文献").fields.endAt, completed.completedAtMillis)
        assertEquals(1, repo.records.size)
    }
    @Test fun pointCompoundUsesOccurrenceAndRejectsMissingOrDoneTask() = runBlocking {
        val repo = MemoryVoiceRepository()
        repo.tasks[7] = TaggedTask(Task(id = 7, title = "取快递", status = TaskStatus.TODO,
            createdAtMillis = 0, updatedAtMillis = 0))
        val command = draft("取快递并完成任务")
        assertTrue(executor(repo)(command) is VoiceExecutionResult.Fallback)
        assertTrue(repo.records.isEmpty())
        assertTrue(executor(repo)(command, linked = VoiceFieldPatch.Set(7)) is VoiceExecutionResult.Executed)
        assertEquals(command.fields.startAt, repo.tasks[7]!!.task.completedAtMillis)
        val original = repo.tasks[7]!!.task.completedAtMillis
        assertTrue(executor(repo)(command, linked = VoiceFieldPatch.Set(7)) is VoiceExecutionResult.Fallback)
        assertEquals(original, repo.tasks[7]!!.task.completedAtMillis)
        assertEquals(1, repo.records.size)
        assertTrue(executor(repo)(command, linked = VoiceFieldPatch.Set(99)) is VoiceExecutionResult.Fallback)
        assertEquals(1, repo.tasks.size)
    }
    @Test fun voiceLinkedTaskCandidatesDependOnHistoricalVersusActiveMode() = runBlocking {
        val repo = MemoryVoiceRepository()
        for ((id, status) in listOf(TaskStatus.TODO, TaskStatus.DONE, TaskStatus.CANCELLED).withIndex()) {
            val taskId = id + 1L
            repo.tasks[taskId] = TaggedTask(Task(id = taskId, title = "看文献", status = status,
                completedAtMillis = if (status == TaskStatus.DONE) 1 else null,
                createdAtMillis = 0, updatedAtMillis = 0))
        }
        val resolver = VoiceTargetResolver(repo, zone)
        assertEquals(setOf(1L, 2L), resolver.linkedTasks("看文献", allowDone = true).map { it.id }.toSet())
        assertEquals(listOf(1L), resolver.linkedTasks("看文献").map { it.id })
        assertTrue(executor(repo)(draft("补录看文献"), linked = VoiceFieldPatch.Set(2L)) is VoiceExecutionResult.Executed)
        assertEquals(2L, repo.records.values.single().session.taskId)
        assertEquals(1L, repo.tasks[2]!!.task.completedAtMillis)
    }
    @Test fun uniqueUnfinishedTaskCompletesWithoutDuplicateOrReclassificationAndUndoRestores() = runBlocking {
        val repo = MemoryVoiceRepository()
        val before = TaggedTask(Task(id = 7, title = "拿快递", status = TaskStatus.IN_PROGRESS,
            categoryId = 4, note = "保留", createdAtMillis = 0, updatedAtMillis = 0), setOf(8L))
        repo.tasks[7] = before
        repo.tasks[8] = TaggedTask(before.task.copy(id = 8, status = TaskStatus.DONE, completedAtMillis = 10))
        val command = draft("完成快递任务")
        assertEquals(listOf(7L), VoiceTargetResolver(repo, zone).targets(command).map { it.id })
        val result = executor(repo)(command, 7) as VoiceExecutionResult.Executed
        assertEquals(2, repo.tasks.size)
        assertEquals(1, repo.completeCalls)
        assertEquals(0, repo.ruleReads)
        assertEquals(before.tagIds, repo.tasks[7]!!.tagIds)
        assertEquals(4L, repo.tasks[7]!!.task.categoryId)
        assertEquals("保留", repo.tasks[7]!!.task.note)
        assertTrue(UndoVoiceActionUseCase(repo)(result.receipt.undo) is DomainResult.Success)
        assertEquals(before, repo.tasks[7])
    }
    @Test fun multipleMatchesIncludeExactAndQualifiedTitlesAndRequireChoice() = runBlocking {
        val repo = MemoryVoiceRepository()
        listOf("拿快递", "拿实验材料快递", "拿药品快递").forEachIndexed { i, title ->
            val id = i + 1L
            repo.tasks[id] = TaggedTask(Task(id = id, title = title, createdAtMillis = 0, updatedAtMillis = 0))
        }
        val command = draft("完成快递任务")
        assertEquals(3, VoiceTargetResolver(repo, zone).targets(command).size)
        assertTrue(executor(repo)(command) is VoiceExecutionResult.Fallback)
        assertEquals(0, repo.completeCalls)
        assertTrue(repo.taskWrites.isEmpty())
        val result = executor(repo)(command, 2) as VoiceExecutionResult.Executed
        assertEquals(TaskStatus.DONE, repo.tasks[2]!!.task.status)
        assertEquals(3, repo.tasks.size)
        assertTrue(UndoVoiceActionUseCase(repo)(result.receipt.undo) is DomainResult.Success)
        assertEquals(3, repo.tasks.size)
    }
    @Test fun missingOrFutureCompletionTimeDoesNotWrite() = runBlocking {
        val repo = MemoryVoiceRepository()
        val missing = draft("完成快递任务")
        assertTrue(executor(repo)(missing.copy(fields = missing.fields.copy(completedAt = null))) is VoiceExecutionResult.Fallback)
        val d = draft("完成快递任务")
        assertTrue(executor(repo)(d.copy(fields = d.fields.copy(completedAt = now.toEpochMilli() + 1))) is VoiceExecutionResult.Fallback)
        assertTrue(repo.tasks.isEmpty())
        assertTrue(repo.records.isEmpty())
    }
    @Test fun objectOnlyTaskTitleIsAReasonableCompletionMatch() = runBlocking {
        val repo = MemoryVoiceRepository()
        repo.tasks[1] = TaggedTask(Task(id = 1, title = "论文", createdAtMillis = 0, updatedAtMillis = 0))
        assertEquals(1L, VoiceTargetResolver(repo, zone).targets(draft("完成论文任务")).single().id)
    }

    @Test fun coreSentenceExecutesExactlyOneEventWriteAndCanBeUndone() = runBlocking {
        val repo = MemoryVoiceRepository()
        val result = executor(repo)(draft("今晚19-20在父亲楼有一节选修课")) as VoiceExecutionResult.Executed
        assertEquals(1, repo.eventWrites)
        val event = repo.events.values.single().event
        assertEquals("选修课", event.title)
        assertEquals("父亲楼", event.location)
        assertEquals(1140, event.startMinuteOfDay)
        assertEquals(1200, event.endMinuteOfDay)
        assertEquals(now.atZone(zone).toLocalDate().toEpochDay(), event.dateEpochDay)
        assertEquals("已创建「选修课」", result.receipt.message)
        assertTrue(UndoVoiceActionUseCase(repo)(result.receipt.undo) is DomainResult.Success)
        assertTrue(repo.events.isEmpty())
    }
    @Test fun crossingMidnightToolCallWritesExplicitNextDayAndCanUndo() = runBlocking {
        val repo = MemoryVoiceRepository()
        val command = QwenToolCallMapper().map("create_event", """{
            "title":"看电影",
            "temporal_type":"INTERVAL",
            "start_at":"2026-09-24T22:00:00+08:00",
            "end_at":"2026-09-25T00:00:00+08:00"
        }""", "今晚十点到十二点看电影", VoiceAgentContext(now, zone))
        val result = executor(repo)(command) as VoiceExecutionResult.Executed
        val saved = repo.events.values.single().event
        assertEquals(java.time.LocalDate.of(2026, 9, 24).toEpochDay(), saved.dateEpochDay)
        assertEquals(1320, saved.startMinuteOfDay)
        assertEquals(java.time.LocalDate.of(2026, 9, 25).toEpochDay(), saved.endDateEpochDay)
        assertEquals(0, saved.endMinuteOfDay)
        assertTrue(UndoVoiceActionUseCase(repo)(result.receipt.undo) is DomainResult.Success)
        assertTrue(repo.events.isEmpty())
    }
    @Test fun repeatedUseCaseCallForSameAttemptWritesOnce() = runBlocking {
        val repo = MemoryVoiceRepository()
        val attempt = VoiceExecutionAttempt()
        val command = draft("今晚19-20在父亲楼有一节选修课")
        val first = executor(repo)(command, attempt = attempt)
        val second = executor(repo)(command, attempt = attempt)
        assertSame(first, second)
        assertEquals(1, repo.eventWrites)
        assertEquals(1, repo.events.size)
    }
    @Test fun titleOnlyTaskAndDateOnlyEventSaveWithoutClassification() = runBlocking {
        val repo = MemoryVoiceRepository()
        assertTrue(executor(repo)(draft("任务 买牙膏")) is VoiceExecutionResult.Executed)
        assertNull(repo.tasks.values.single().task.categoryId)
        assertTrue(executor(repo)(draft("明天有组会")) is VoiceExecutionResult.Executed)
        assertNull(repo.events.values.single().event.startMinuteOfDay)
    }
    @Test fun voiceTaskLocationIsStructuredAndDoesNotRewriteExistingNote() = runBlocking {
        val repo = MemoryVoiceRepository()
        assertTrue(executor(repo)(draft("去医院取报告")) is VoiceExecutionResult.Executed)
        val created = repo.tasks.values.single().task
        assertEquals("医院", created.location)
        assertNull(created.note)

        repo.tasks[created.id] = TaggedTask(created.copy(note = "地点：旧备注"))
        assertTrue(executor(repo)(draft("取报告改在实验室"), created.id) is VoiceExecutionResult.Executed)
        val updated = repo.tasks[created.id]!!.task
        assertEquals("实验室", updated.location)
        assertEquals("地点：旧备注", updated.note)
    }
    @Test fun uniqueTaskPatchExecutesAndUndoRestoresOriginalMetadata() = runBlocking {
        val repo = MemoryVoiceRepository()
        val original = TaggedTask(Task(id = 7, title = "论文", note = "原笔记", dueDateEpochDay = 100, categoryId = 4,
            createdAtMillis = 10, updatedAtMillis = 20), setOf(9))
        repo.tasks[7] = original
        val result = executor(repo)(draft("论文截止改到下周一"), 7) as VoiceExecutionResult.Executed
        assertEquals("原笔记", repo.tasks[7]!!.task.note)
        assertEquals(setOf(9L), repo.tasks[7]!!.tagIds)
        assertTrue(UndoVoiceActionUseCase(repo)(result.receipt.undo) is DomainResult.Success)
        assertEquals(original, repo.tasks[7])
    }
    @Test fun ambiguityAndValidationFailureNeverWrite() = runBlocking {
        val repo = MemoryVoiceRepository()
        val incomplete = VoiceActionDraft(VoiceAction.CreateEvent, "组会",
            VoiceFields(title = VoiceFieldPatch.Set("组会")), "组会", now.toEpochMilli())
        assertTrue(executor(repo)(incomplete) is VoiceExecutionResult.Fallback)
        assertTrue(executor(repo)(draft("记录明天下午三点到四点组会")) is VoiceExecutionResult.Fallback)
        assertTrue(repo.events.isEmpty())
        assertTrue(repo.records.isEmpty())
        repo.tasks[1] = TaggedTask(Task(id = 1, title = "论文第一章", createdAtMillis = 0, updatedAtMillis = 0))
        repo.tasks[2] = TaggedTask(Task(id = 2, title = "论文第二章", createdAtMillis = 0, updatedAtMillis = 0))
        val candidates = VoiceTargetResolver(repo, zone).targets(draft("论文截止改到周五"))
        assertEquals(2, candidates.size)
        assertNull(VoiceTargetMatching.unique(candidates))
        assertTrue(executor(repo)(draft("论文截止改到周五")) is VoiceExecutionResult.Fallback)
    }
    @Test fun undoDoesNotOverwriteLaterEditsOrDeleteNewDependencies() = runBlocking {
        val repo = MemoryVoiceRepository()
        val receipt = (executor(repo)(draft("明天有组会")) as VoiceExecutionResult.Executed).receipt
        val event = repo.events.values.single()
        repo.events[event.event.id] = event.copy(event = event.event.copy(location = "后续修改"))
        assertTrue(UndoVoiceActionUseCase(repo)(receipt.undo) is DomainResult.Failure)
        assertEquals("后续修改", repo.events.values.single().event.location)
        val taskReceipt = (executor(repo)(draft("任务 买牙膏")) as VoiceExecutionResult.Executed).receipt
        val taskId = repo.tasks.keys.single()
        repo.records[1] = TaggedSession(Session(id = 1, title = "后来关联", taskId = taskId,
            startAtMillis = 0, createdAtMillis = 0, updatedAtMillis = 0))
        assertTrue(UndoVoiceActionUseCase(repo)(taskReceipt.undo) is DomainResult.Failure)
        assertTrue(repo.tasks.containsKey(taskId))
    }
    @Test fun startWithoutLinkedTaskIsExecutableAndExistingActiveRecordIsProtected() = runBlocking {
        val repo = MemoryVoiceRepository()
        assertTrue(executor(repo)(draft("开始写论文"), linked = VoiceFieldPatch.Clear) is VoiceExecutionResult.Executed)
        assertNull(repo.records.values.single().session.taskId)
        assertTrue(executor(repo)(draft("开始跑步")) is VoiceExecutionResult.Fallback)
        assertNull(repo.records.values.single().session.endAtMillis)
    }
    @Test fun eventCopyRetainsSourceId() = runBlocking {
        val repo = MemoryVoiceRepository()
        repo.events[9] = TaggedEvent(Event(id = 9, title = "选修课", dateEpochDay = now.atZone(zone).toLocalDate().toEpochDay(),
            startMinuteOfDay = 840, endMinuteOfDay = 900, location = "父亲楼", createdAtMillis = 0, updatedAtMillis = 0))
        assertTrue(executor(repo)(draft("把选修课记成记录"), 9) is VoiceExecutionResult.Executed)
        assertEquals(9L, repo.records.values.single().session.sourceEventId)
        assertEquals("父亲楼", repo.records.values.single().session.location)
        assertEquals(840, repo.events[9]!!.event.startMinuteOfDay)
    }

    @Test fun eventCopyWithoutActualTimeNeverWritesRecord() = runBlocking {
        val repo = MemoryVoiceRepository()
        repo.events[9] = TaggedEvent(Event(id = 9, title = "选修课",
            dateEpochDay = now.atZone(zone).toLocalDate().toEpochDay(),
            startMinuteOfDay = 840, endMinuteOfDay = 900, createdAtMillis = 0, updatedAtMillis = 0))
        val mapper = QwenToolCallMapper()
        val context = VoiceAgentContext(now, zone)
        val point = mapper.map("create_record_from_event",
            """{"event_query":"选修课","temporal_type":"POINT"}""", "记下选修课", context)
        val pointResult = executor(repo)(point, 9) as VoiceExecutionResult.Fallback
        assertTrue(pointResult.prepared.missing.contains(VoiceMissingField.Start))
        assertTrue(repo.records.isEmpty())

        val interval = mapper.map("create_record_from_event",
            """{"event_query":"选修课","temporal_type":"INTERVAL"}""", "记下选修课", context)
        val intervalResult = executor(repo)(interval, 9) as VoiceExecutionResult.Fallback
        assertTrue(intervalResult.prepared.missing.contains(VoiceMissingField.Start))
        assertTrue(intervalResult.prepared.missing.contains(VoiceMissingField.End))
        assertTrue(repo.records.isEmpty())
    }

    @Test fun eventCopyUsesOnlyExplicitActualBoundaries() = runBlocking {
        val repo = MemoryVoiceRepository()
        repo.events[9] = TaggedEvent(Event(id = 9, title = "选修课",
            dateEpochDay = now.atZone(zone).toLocalDate().toEpochDay(),
            startMinuteOfDay = 840, endMinuteOfDay = 900, createdAtMillis = 0, updatedAtMillis = 0))
        val command = QwenToolCallMapper().map("create_record_from_event", """{
            "event_query":"选修课","temporal_type":"INTERVAL",
            "actual_start_at":"2026-09-22T14:18:00+08:00",
            "actual_end_at":"2026-09-22T15:37:00+08:00"
        }""", "记下选修课", VoiceAgentContext(now, zone))
        assertTrue(executor(repo)(command, 9) is VoiceExecutionResult.Executed)
        val saved = repo.records.values.single().session
        assertEquals(SessionTemporalType.INTERVAL, saved.temporalType)
        assertEquals(Instant.parse("2026-09-22T06:18:00Z").toEpochMilli(), saved.startAtMillis)
        assertEquals(Instant.parse("2026-09-22T07:37:00Z").toEpochMilli(), saved.endAtMillis)
        assertEquals(9L, saved.sourceEventId)
    }

    @Test fun sourceEventResolverFindsTodayYesterdayAndOlderDatesWithoutGuessing() = runBlocking {
        val repo = MemoryVoiceRepository()
        val today = now.atZone(zone).toLocalDate()
        listOf(today, today.minusDays(1), today.minusDays(30)).forEachIndexed { index, date ->
            val id = index + 1L
            repo.events[id] = TaggedEvent(Event(id = id, title = "组会", dateEpochDay = date.toEpochDay(),
                startMinuteOfDay = 840, endMinuteOfDay = 900, createdAtMillis = 0, updatedAtMillis = 0))
        }
        val broad = draft("把选修课记成记录").copy(targetText = "组会")
        assertEquals(3, VoiceTargetResolver(repo, zone).targets(broad).size)
        assertNull(VoiceTargetMatching.unique(VoiceTargetResolver(repo, zone).targets(broad)))
        val dated = broad.copy(targetDate = today.minusDays(30))
        assertEquals(3L, VoiceTargetResolver(repo, zone).targets(dated).single().id)
        assertEquals(2L, VoiceTargetResolver(repo, zone).targets(broad.copy(targetDate = today.minusDays(1))).single().id)
        assertEquals(1L, VoiceTargetResolver(repo, zone).targets(broad.copy(targetDate = today)).single().id)
        val missing = broad.copy(targetDate = today.minusYears(1))
        assertTrue(VoiceTargetResolver(repo, zone).targets(missing).isEmpty())
        assertTrue(executor(repo)(missing) is VoiceExecutionResult.Fallback)
        assertTrue(executor(repo)(broad) is VoiceExecutionResult.Fallback)
        assertTrue(repo.records.isEmpty())
    }

    @Test fun ordinaryRecordNeverAutoLinksSameTitleTask() = runBlocking {
        val repo = MemoryVoiceRepository()
        repo.tasks[7] = TaggedTask(Task(id = 7, title = "拿快递", createdAtMillis = 0, updatedAtMillis = 0))
        assertTrue(executor(repo)(draft("拿到快递了")) is VoiceExecutionResult.Executed)
        assertNull(repo.records.values.single().session.taskId)
        assertEquals(TaskStatus.TODO, repo.tasks[7]!!.task.status)
    }

    @Test fun missingCompletionTargetNeverCreatesDoneTask() = runBlocking {
        val repo = MemoryVoiceRepository()
        val command = draft("完成快递任务")
        assertTrue(VoiceTargetResolver(repo, zone).targets(command).isEmpty())
        assertTrue(executor(repo)(command) is VoiceExecutionResult.Fallback)
        assertTrue(repo.tasks.isEmpty())
        assertTrue(repo.records.isEmpty())
    }

    @Test fun dateOnlyPastFactCannotWriteWithoutClockTime() = runBlocking {
        val repo = MemoryVoiceRepository()
        val d = QwenToolCallMapper().map("request_clarification", """{
            "reason":"RECORD_MISSING_TIME","record_title":"取快递","known_date":"2026-09-21"
        }""", "昨天取快递了", VoiceAgentContext(now, zone))
        assertEquals(VoiceClarification.RecordMissingTime,
            (VoiceIntentPolicy.evaluate(d, zone) as VoiceIntentDecision.NeedsClarification).reason)
        assertTrue(executor(repo)(d) is VoiceExecutionResult.Fallback)
        assertTrue(repo.records.isEmpty())
        assertTrue(repo.events.isEmpty())
    }

    @Test fun stopRecordDoesNotCompleteTaskButExplicitCombinedActionDoes() = runBlocking {
        val repo = MemoryVoiceRepository()
        repo.tasks[7] = TaggedTask(Task(id = 7, title = "写论文", createdAtMillis = 0, updatedAtMillis = 0))
        fun active() = TaggedSession(Session(id = 9, title = "写论文", taskId = 7,
            startAtMillis = now.toEpochMilli() - 60_000, createdAtMillis = 0, updatedAtMillis = 0))
        repo.records[9] = active()
        val stop = QwenToolCallMapper().map("stop_record", "{}", "停止记录", VoiceAgentContext(now, zone))
        assertTrue(executor(repo)(stop, 9) is VoiceExecutionResult.Executed)
        assertNotNull(repo.records[9]!!.session.endAtMillis)
        assertEquals(TaskStatus.TODO, repo.tasks[7]!!.task.status)
        repo.records[9] = active()
        val combined = QwenToolCallMapper().map("stop_record_and_complete_task", "{}",
            "停止记录并完成任务", VoiceAgentContext(now, zone))
        assertTrue(executor(repo)(combined, 9) is VoiceExecutionResult.Executed)
        assertEquals(TaskStatus.DONE, repo.tasks[7]!!.task.status)
    }

    @Test fun emptyAudioTextStillClassifiesFromHintAndMissingHintFallsBackToTitle() = runBlocking {
        val hintedRepo = MemoryVoiceRepository().apply {
            categories = listOf(Category(7, "科研", sortOrder = 0, createdAtMillis = 0, updatedAtMillis = 0))
            rules = listOf(KeywordRule(
                id = 11,
                keyword = "实验",
                categoryId = 7,
                tagIds = setOf(5),
                createdAtMillis = 0,
                updatedAtMillis = 0,
            ))
        }
        val audioDraft = QwenToolCallMapper().map(
            "create_event",
            """{"title":"做实验","temporal_type":"DATE","date":"2026-09-23","category_hint":"实验"}""",
            "",
            VoiceAgentContext(now, zone),
        )
        assertTrue(executor(hintedRepo)(audioDraft) is VoiceExecutionResult.Executed)
        assertEquals(7L, hintedRepo.events.values.single().event.categoryId)
        assertEquals(setOf(5L), hintedRepo.events.values.single().tagIds)

        val fallbackRepo = MemoryVoiceRepository().apply {
            categories = hintedRepo.categories
            rules = hintedRepo.rules.map { it.copy(keyword = "论文") }
        }
        val noHintDraft = QwenToolCallMapper().map(
            "create_task",
            """{"title":"交论文"}""",
            "",
            VoiceAgentContext(now, zone),
        )
        assertTrue(executor(fallbackRepo)(noHintDraft) is VoiceExecutionResult.Executed)
        assertEquals(7L, fallbackRepo.tasks.values.single().task.categoryId)
    }
}

/** In-memory test boundary; production uses Room transactions and its existing validators. */
private class MemoryVoiceRepository : RidoCoreRepository {
    val tasks = mutableMapOf<Long, TaggedTask>()
    val events = mutableMapOf<Long, TaggedEvent>()
    val records = mutableMapOf<Long, TaggedSession>()
    var eventWrites = 0
    val taskWrites = mutableListOf<Task>()
    var completeCalls = 0
    var rules = emptyList<KeywordRule>()
    var categories = emptyList<Category>()
    var ruleReads = 0
    override suspend fun <T> voiceTransaction(block: suspend () -> T): T {
        val t = tasks.toMap(); val e = events.toMap(); val r = records.toMap()
        return try { block() } catch (error: Throwable) {
            tasks.clear(); tasks.putAll(t); events.clear(); events.putAll(e); records.clear(); records.putAll(r); throw error
        }
    }
    override fun observeDataChanges() = flowOf(Unit)
    override fun observeActiveSession() = flowOf(records.values.singleOrNull {
        it.session.temporalType == SessionTemporalType.INTERVAL && it.session.endAtMillis == null })
    override suspend fun getTask(taskId: Long) = tasks[taskId]
    override suspend fun getEvent(eventId: Long) = events[eventId]
    override suspend fun getEventsByIds(eventIds: Set<Long>) =
        eventIds.mapNotNull(events::get).map { it.event }
    override suspend fun getSession(sessionId: Long) = records[sessionId]
    override suspend fun getAllTasks() = tasks.values.map { it.task }
    override suspend fun getEnabledKeywordRules(): List<KeywordRule> { ruleReads++; return rules }
    override suspend fun getCategories() = categories
    override suspend fun getTags() = emptyList<Tag>()
    override suspend fun getNightSleeps() = emptyList<Session>()
    override suspend fun getSessions(rangeStartMillis: Long, rangeEndMillis: Long) = records.values.toList()
    override suspend fun getTasks(firstEpochDay: Long, lastEpochDay: Long, rangeStartMillis: Long, rangeEndMillis: Long) = tasks.values.toList()
    override suspend fun getEvents(firstEpochDay: Long, lastEpochDay: Long, rangeStartMillis: Long, rangeEndMillis: Long) =
        events.values.filter { it.event.dateEpochDay in firstEpochDay..lastEpochDay }
    override suspend fun upsertTask(task: Task, tagIds: Set<Long>): DomainResult<Long> {
        val id = task.id.takeIf { it != 0L } ?: ((tasks.keys.maxOrNull() ?: 0) + 1)
        taskWrites += task
        tasks[id] = TaggedTask(task.copy(id = id), tagIds); return DomainResult.Success(id)
    }
    override suspend fun upsertEvent(event: Event, tagIds: Set<Long>): DomainResult<Long> {
        val id = event.id.takeIf { it != 0L } ?: ((events.keys.maxOrNull() ?: 0) + 1)
        events[id] = TaggedEvent(event.copy(id = id), tagIds); eventWrites++; return DomainResult.Success(id)
    }
    override suspend fun deleteTask(taskId: Long): DomainResult<Unit> { tasks.remove(taskId); return DomainResult.Success(Unit) }
    override suspend fun deleteEvent(eventId: Long) = DomainResult.Success(DeletedEventSnapshot(events.remove(eventId)!!))
    override suspend fun deleteSession(sessionId: Long): DomainResult<Unit> { records.remove(sessionId); return DomainResult.Success(Unit) }
    override suspend fun deleteSessionWithSnapshot(sessionId: Long) = DomainResult.Success(DeletedSessionSnapshot(records.remove(sessionId)!!))
    override suspend fun restoreEvent(snapshot: DeletedEventSnapshot): DomainResult<Unit> { events[snapshot.record.event.id] = snapshot.record; return DomainResult.Success(Unit) }
    override suspend fun restoreSession(snapshot: DeletedSessionSnapshot): DomainResult<Unit> { records[snapshot.record.session.id] = snapshot.record; return DomainResult.Success(Unit) }
    override suspend fun startSessionIfIdle(session: Session, tagIds: Set<Long>): DomainResult<Long> =
        if (records.values.any { it.session.endAtMillis == null }) DomainResult.Failure(DomainError.SessionOverlap) else startSession(session, tagIds)
    override suspend fun startSession(session: Session, tagIds: Set<Long>): DomainResult<Long> = createPastSession(session, tagIds)
    override suspend fun createPastSession(session: Session, tagIds: Set<Long>): DomainResult<Long> {
        val id = (records.keys.maxOrNull() ?: 0) + 1; records[id] = TaggedSession(session.copy(id = id), tagIds); return DomainResult.Success(id)
    }
    override suspend fun createPastSessionAndCompleteTask(session: Session, tagIds: Set<Long>): DomainResult<Long> {
        val taskId = session.taskId ?: return DomainResult.Failure(DomainError.DataIntegrity("missing task"))
        val task = tasks[taskId]?.task ?: return DomainResult.Failure(DomainError.NotFound("Task", taskId))
        if (task.status == TaskStatus.DONE || task.status == TaskStatus.CANCELLED)
            return DomainResult.Failure(DomainError.DataIntegrity("already done"))
        val result = createPastSession(session, tagIds)
        if (result is DomainResult.Success) {
            val at = if (session.temporalType == SessionTemporalType.POINT) session.startAtMillis else requireNotNull(session.endAtMillis)
            completeTask(taskId, at)
        }
        return result
    }
    override suspend fun updateSession(session: Session, tagIds: Set<Long>): DomainResult<Unit> {
        records[session.id] = TaggedSession(session, tagIds); return DomainResult.Success(Unit)
    }
    override suspend fun completeTask(taskId: Long, completedAtMillis: Long): DomainResult<Unit> {
        completeCalls++
        val old = tasks[taskId]!!; tasks[taskId] = old.copy(task = old.task.copy(status = TaskStatus.DONE, completedAtMillis = completedAtMillis))
        return DomainResult.Success(Unit)
    }
    override suspend fun reopenTask(taskId: Long, updatedAtMillis: Long): DomainResult<Unit> {
        val old = tasks[taskId]!!; tasks[taskId] = old.copy(task = old.task.copy(status = TaskStatus.TODO, completedAtMillis = null)); return DomainResult.Success(Unit)
    }
    override suspend fun stopActiveSession(endAtMillis: Long): DomainResult<Session> {
        val old = records.values.single { it.session.endAtMillis == null }
        val changed = old.session.copy(endAtMillis = endAtMillis); records[changed.id] = old.copy(session = changed); return DomainResult.Success(changed)
    }
    override suspend fun stopConfirmedSession(id: Long, taskId: Long?, end: Long, completeTask: Boolean): DomainResult<Session> {
        val active = records[id]?.session ?: return DomainResult.Failure(DomainError.NotFound("Session", id))
        if (active.taskId != taskId || active.endAtMillis != null)
            return DomainResult.Failure(DomainError.NotFound("Session", id))
        val stopped = active.copy(endAtMillis = end)
        records[id] = records[id]!!.copy(session = stopped)
        if (completeTask && taskId != null) this.completeTask(taskId, end)
        return DomainResult.Success(stopped)
    }
    override suspend fun stopSessionAndCompleteTask(endAtMillis: Long) = stopActiveSession(endAtMillis)
}
