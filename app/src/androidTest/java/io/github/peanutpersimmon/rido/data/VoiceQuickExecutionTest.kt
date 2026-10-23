package io.github.peanutpersimmon.rido.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.peanutpersimmon.rido.data.local.room.RidoDatabase
import io.github.peanutpersimmon.rido.data.repository.RoomRidoRepository
import io.github.peanutpersimmon.rido.domain.model.*
import io.github.peanutpersimmon.rido.domain.voice.*
import io.github.peanutpersimmon.rido.domain.voice.agent.QwenToolCallMapper
import io.github.peanutpersimmon.rido.domain.voice.agent.VoiceAgentContext
import io.github.peanutpersimmon.rido.domain.voice.agent.VoiceAgentEngine
import io.github.peanutpersimmon.rido.domain.voice.agent.VoiceAgentResult
import io.github.peanutpersimmon.rido.domain.voice.agent.VoiceAgentUnavailableReason
import io.github.peanutpersimmon.rido.domain.voice.agent.QwenAudioTurnController
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import io.github.peanutpersimmon.rido.presentation.voice.VoiceViewModel
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VoiceQuickExecutionTest {
    private suspend fun withRepository(block: suspend (RoomRidoRepository) -> Unit) {
        val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, RidoDatabase::class.java).build()
        try { block(RoomRidoRepository(db)) } finally { db.close() }
    }

    @Test fun historicalFactCreatesPointRecordAndUndoDeletesIt() = runBlocking {
        withRepository { repo ->
            val now = Instant.now()
            val zone = ZoneId.systemDefault()
            val completedAt = now.atZone(zone).toLocalDate().minusDays(1)
                .atTime(20, 0).atZone(zone).toOffsetDateTime()
            val draft = QwenToolCallMapper().map("create_record",
                """{"title":"拿快递","temporal_type":"POINT","occurred_at":"$completedAt"}""",
                "昨天晚上八点拿到快递了", VoiceAgentContext(now, zone))
            val result = ExecuteVoiceActionUseCase(repo, zone)(draft) as VoiceExecutionResult.Executed
            val record = result.receipt.undo.after.records.single().session
            assertEquals(SessionTemporalType.POINT, record.temporalType)
            assertTrue(repo.getAllTasks().isEmpty())
            assertTrue(UndoVoiceActionUseCase(repo)(result.receipt.undo) is DomainResult.Success)
            assertNull(repo.getSession(record.id))
        }
    }

    @Test fun completingExistingChildAndUndoRestoresAncestorEffects() = runBlocking {
        withRepository { repo ->
            val now = Instant.now()
            val parent = (repo.upsertTask(Task(title = "杂务", createdAtMillis = 0, updatedAtMillis = 0), emptySet()) as DomainResult.Success).value
            val child = (repo.upsertTask(Task(title = "拿快递", parentTaskId = parent,
                createdAtMillis = 0, updatedAtMillis = 0), emptySet()) as DomainResult.Success).value
            val before = repo.getAllTasks()
            val draft = QwenToolCallMapper().map("complete_task", """{"target_query":"拿快递"}""",
                "完成拿快递任务", VoiceAgentContext(now))
            val result = ExecuteVoiceActionUseCase(repo)(draft, child) as VoiceExecutionResult.Executed
            assertEquals(TaskStatus.DONE, repo.getTask(child)!!.task.status)
            assertEquals(TaskStatus.DONE, repo.getTask(parent)!!.task.status)
            assertEquals(2, repo.getAllTasks().size)
            assertTrue(UndoVoiceActionUseCase(repo)(result.receipt.undo) is DomainResult.Success)
            assertEquals(before, repo.getAllTasks())
        }
    }

    @Test fun rawCoreSentenceSavesToRoomAndUndoRemovesOnlyCreatedEvent() = runBlocking {
        withRepository { repo ->
            val now = Instant.now()
            val zone = ZoneId.systemDefault()
            val day = now.atZone(zone).toLocalDate()
            val start = day.atTime(19, 0).atZone(zone).toOffsetDateTime()
            val end = day.atTime(20, 0).atZone(zone).toOffsetDateTime()
            val draft = QwenToolCallMapper().map("create_event",
                """{"title":"选修课","temporal_type":"INTERVAL","start_at":"$start","end_at":"$end","location":"父亲楼"}""",
                "今晚19-20在父亲楼有一节选修课", VoiceAgentContext(now, zone))
            val result = ExecuteVoiceActionUseCase(repo, zone)(draft) as VoiceExecutionResult.Executed
            val saved = result.receipt.undo.after.events.single()
            assertEquals("选修课", saved.event.title)
            assertEquals("父亲楼", saved.event.location)
            assertEquals(now.atZone(zone).toLocalDate().toEpochDay(), saved.event.dateEpochDay)
            assertEquals(1140, saved.event.startMinuteOfDay)
            assertEquals(1200, saved.event.endMinuteOfDay)
            assertEquals(saved, repo.getEvent(saved.event.id))
            assertTrue(UndoVoiceActionUseCase(repo)(result.receipt.undo) is DomainResult.Success)
            assertNull(repo.getEvent(saved.event.id))
        }
    }

    @Test fun unavailableAiKeepsTextAndDoesNotWrite() = runBlocking {
        withRepository { repo ->
            withContext(Dispatchers.Main) {
                val unavailable = object : VoiceAgentEngine {
                    override suspend fun executeText(text: String, context: VoiceAgentContext) =
                        VoiceAgentResult.Unavailable(VoiceAgentUnavailableReason.NetworkOrService, "offline")
                    override suspend fun executeAudio(turn: QwenAudioTurnController, context: VoiceAgentContext) =
                        VoiceAgentResult.Unavailable(VoiceAgentUnavailableReason.NetworkOrService, "offline")
                }
                val model = VoiceViewModel(repo, unavailable)
                model.editText("今晚19-20在父亲楼有一节选修课")
                model.submit()
                val error = withTimeout(5_000) { model.uiState.first { it.error != null && !it.busy } }
                assertEquals("AI 暂不可用，请稍后重试或使用 + 手动创建", error.error)
                assertEquals("今晚19-20在父亲楼有一节选修课", error.text)
                assertTrue(repo.getEvents(Long.MIN_VALUE, Long.MAX_VALUE, Long.MIN_VALUE, Long.MAX_VALUE).isEmpty())
            }
        }
    }

    @Test fun uniqueTaskPatchUsesExistingSaveAndUndoRestoresSnapshot() = runBlocking {
        withRepository { repo ->
            val now = Instant.now()
            val original = Task(title = "论文", note = "保留笔记", createdAtMillis = now.toEpochMilli(), updatedAtMillis = now.toEpochMilli())
            val id = (repo.upsertTask(original, emptySet()) as DomainResult.Success).value
            val before = repo.getTask(id)
            val due = now.atZone(ZoneId.systemDefault()).toLocalDate().plusDays(7)
            val draft = QwenToolCallMapper().map("update_task",
                """{"target_query":"论文","due_date":"$due"}""", "论文截止改到下周一", VoiceAgentContext(now))
            val candidate = VoiceTargetResolver(repo).targets(draft).single()
            val result = ExecuteVoiceActionUseCase(repo)(draft, candidate.id) as VoiceExecutionResult.Executed
            assertNotNull(repo.getTask(id)!!.task.dueDateEpochDay)
            assertEquals("保留笔记", repo.getTask(id)!!.task.note)
            assertTrue(UndoVoiceActionUseCase(repo)(result.receipt.undo) is DomainResult.Success)
            assertEquals(before, repo.getTask(id))
        }
    }

    @Test fun explicitlyReportedPastRangeSavesSessionAndUndoRemovesIt() = runBlocking {
        withRepository { repo ->
            val zone = ZoneId.of("Asia/Shanghai")
            val now = Instant.parse("2026-09-26T07:00:00Z")
            val mapped = QwenToolCallMapper().map("create_record", """{
                "title":"找蜡块",
                "temporal_type":"INTERVAL",
                "start_at":"2026-09-26T09:00:00+08:00",
                "end_at":"2026-09-26T12:00:00+08:00",
                "location":"病理科",
                "note":"补记"
            }""", "今天早上9-12点在病理科找蜡块", VoiceAgentContext(now, zone))
            val ready = (VoiceIntentPolicy.evaluate(mapped, zone) as VoiceIntentDecision.Ready).draft
            assertEquals(VoiceAction.CreateRecord, ready.action)
            val result = ExecuteVoiceActionUseCase(repo, zone) { now.toEpochMilli() }(ready)
                as VoiceExecutionResult.Executed
            val saved = result.receipt.undo.after.records.single()
            assertEquals("病理科", saved.session.location)
            assertEquals("补记", saved.session.note)
            assertEquals(Instant.parse("2026-09-26T01:00:00Z").toEpochMilli(), saved.session.startAtMillis)
            assertEquals(Instant.parse("2026-09-26T04:00:00Z").toEpochMilli(), saved.session.endAtMillis)
            assertTrue(repo.getEvents(Long.MIN_VALUE, Long.MAX_VALUE, Long.MIN_VALUE, Long.MAX_VALUE).isEmpty())
            assertTrue(UndoVoiceActionUseCase(repo)(result.receipt.undo) is DomainResult.Success)
            assertTrue(repo.getSessions(Long.MIN_VALUE, Long.MAX_VALUE).isEmpty())
        }
    }

    @Test fun pointRecordDirectlySavesPointAndUndoRemovesIt() = runBlocking {
        withRepository { repo ->
            val zone = ZoneId.of("Asia/Shanghai")
            val now = Instant.parse("2026-09-26T07:00:00Z")
            val mapped = QwenToolCallMapper().map("create_record", """{
                "title":"买早饭","temporal_type":"POINT","occurred_at":"2026-09-26T09:30:00+08:00"
            }""", "早上9点半买早饭了", VoiceAgentContext(now, zone))
            val direct = (VoiceIntentPolicy.evaluate(mapped, zone) as VoiceIntentDecision.Ready).draft
            assertEquals(SessionTemporalType.POINT, direct.recordTemporalType)
            assertNull(direct.fields.endAt)
            val result = ExecuteVoiceActionUseCase(repo, zone) { now.toEpochMilli() }(direct)
                as VoiceExecutionResult.Executed
            val saved = result.receipt.undo.after.records.single()
            assertEquals(Instant.parse("2026-09-26T01:30:00Z").toEpochMilli(),
                saved.session.startAtMillis)
            assertEquals(io.github.peanutpersimmon.rido.domain.model.SessionTemporalType.POINT, saved.session.temporalType)
            assertNull(saved.session.endAtMillis)
            assertTrue(repo.getEvents(Long.MIN_VALUE, Long.MAX_VALUE, Long.MIN_VALUE, Long.MAX_VALUE).isEmpty())
            assertTrue(UndoVoiceActionUseCase(repo)(result.receipt.undo) is DomainResult.Success)
            assertNull(repo.getSession(saved.session.id))
        }
    }

    @Test fun eventCopyWithoutActualTimeDoesNotWriteRoomSession() = runBlocking {
        withRepository { repo ->
            val zone = ZoneId.of("Asia/Shanghai")
            val now = Instant.parse("2026-09-26T10:00:00Z")
            val eventId = (repo.upsertEvent(Event(title = "组会",
                dateEpochDay = now.atZone(zone).toLocalDate().toEpochDay(),
                startMinuteOfDay = 14 * 60, endMinuteOfDay = 16 * 60,
                createdAtMillis = 0, updatedAtMillis = 0), emptySet()) as DomainResult.Success).value
            val draft = QwenToolCallMapper().map("create_record_from_event",
                """{"event_query":"组会","temporal_type":"INTERVAL"}""",
                "把组会记为记录", VoiceAgentContext(now, zone))
            val result = ExecuteVoiceActionUseCase(repo, zone) { now.toEpochMilli() }(draft, eventId)
            assertTrue(result is VoiceExecutionResult.Fallback)
            assertTrue(repo.getSessions(Long.MIN_VALUE, Long.MAX_VALUE).isEmpty())
            assertNotNull(repo.getEvent(eventId))
        }
    }

    @Test fun completedNapUsesSleepKindClearsClassificationAndUndoDeletesIt() = runBlocking {
        withRepository { repo ->
            val zone = ZoneId.of("Asia/Shanghai")
            val now = Instant.parse("2026-09-26T07:00:00Z")
            val mapped = QwenToolCallMapper().map("create_sleep_record", """{
                "sleep_type":"nap","title":"午睡",
                "start_at":"2026-09-26T12:30:00+08:00",
                "end_at":"2026-09-26T13:00:00+08:00",
                "category_hint":"休息"
            }""", "午睡了半小时", VoiceAgentContext(now, zone))
            val ready = (VoiceIntentPolicy.evaluate(mapped, zone) as VoiceIntentDecision.Ready).draft
            val result = ExecuteVoiceActionUseCase(repo, zone) { now.toEpochMilli() }(ready)
                as VoiceExecutionResult.Executed
            val saved = result.receipt.undo.after.records.single()
            assertEquals(SessionKind.NAP, saved.session.kind)
            assertNull(saved.session.categoryId)
            assertNull(saved.session.taskId)
            assertTrue(saved.tagIds.isEmpty())
            assertTrue(UndoVoiceActionUseCase(repo)(result.receipt.undo) is DomainResult.Success)
            assertTrue(repo.getSessions(Long.MIN_VALUE, Long.MAX_VALUE).isEmpty())
        }
    }

    @Test fun startNapCreatesOpenSleepAndRespectsActiveSessionConflict() = runBlocking {
        withRepository { repo ->
            val zone = ZoneId.of("Asia/Shanghai")
            val now = Instant.parse("2026-09-26T07:00:00Z")
            val start = QwenToolCallMapper().map("start_sleep", """{"sleep_type":"nap"}""",
                "开始午睡", VoiceAgentContext(now, zone))
            val first = ExecuteVoiceActionUseCase(repo, zone) { now.toEpochMilli() }(start)
                as VoiceExecutionResult.Executed
            val saved = first.receipt.undo.after.records.single()
            assertEquals(SessionKind.NAP, saved.session.kind)
            assertNull(saved.session.endAtMillis)

            val second = ExecuteVoiceActionUseCase(repo, zone) { now.toEpochMilli() }(start)
            assertTrue(second is VoiceExecutionResult.Fallback)
            assertEquals(1, repo.getSessions(Long.MIN_VALUE, Long.MAX_VALUE).size)
        }
    }
}
