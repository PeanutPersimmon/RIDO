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
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.ZoneId

/** Written for later device execution; no schema or migration is introduced by voice input. */
@RunWith(AndroidJUnit4::class)
class VoiceSessionSafetyTest {
    private suspend fun withRepository(test: suspend (RoomRidoRepository) -> Unit) {
        val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, RidoDatabase::class.java).build()
        try { test(RoomRidoRepository(db)) } finally { db.close() }
    }
    private fun session(title: String, start: Long, task: Long? = null) = Session(
        title = title, startAtMillis = start, taskId = task, createdAtMillis = start, updatedAtMillis = start)
    private fun <T> DomainResult<T>.value(): T = (this as DomainResult.Success<T>).value

    @Test fun voiceStartCannotImplicitlyStopAnExistingRecord() = runBlocking {
        withRepository { repo ->
            val now = System.currentTimeMillis()
            val id = repo.startSession(session("原记录", now - 60_000), emptySet()).value()
            assertTrue(repo.startSessionIfIdle(session("写论文", now - 10_000), emptySet()) is DomainResult.Failure)
            assertNull(repo.getSession(id)!!.session.endAtMillis)
            assertEquals(id, repo.getActiveSession()!!.id)
        }
    }
    @Test fun qwenMappedStartCannotCreateASecondActiveRecord() = runBlocking {
        withRepository { repo ->
            val now = Instant.now()
            val first = repo.startSession(session("原记录", now.minusSeconds(60).toEpochMilli()), emptySet()).value()
            val draft = QwenToolCallMapper().map("start_record", """{"title":"写论文"}""",
                "开始写论文", VoiceAgentContext(now, ZoneId.systemDefault()))
            val result = ExecuteVoiceActionUseCase(repo)(draft)
            assertTrue(result is VoiceExecutionResult.Fallback)
            assertNull(repo.getSession(first)!!.session.endAtMillis)
            assertEquals(first, repo.getActiveSession()!!.id)
        }
    }
    @Test fun confirmationCannotStopAReplacedActiveRecord() = runBlocking {
        withRepository { repo ->
            val now = System.currentTimeMillis()
            val old = repo.startSession(session("写论文", now - 60_000), emptySet()).value()
            repo.stopActiveSession(now - 40_000).value()
            val current = repo.startSession(session("跑步", now - 20_000), emptySet()).value()
            assertTrue(repo.stopConfirmedSession(old, null, now, false) is DomainResult.Failure)
            assertNull(repo.getSession(current)!!.session.endAtMillis)
        }
    }
    @Test fun stopOnlyDoesNotCompleteTaskButExplicitCombinedConfirmationDoes() = runBlocking {
        withRepository { repo ->
            val now = System.currentTimeMillis()
            val task = repo.upsertTask(Task(title = "写论文", createdAtMillis = now - 120_000, updatedAtMillis = now - 120_000), emptySet()).value()
            val first = repo.startSession(session("写论文", now - 100_000, task), emptySet()).value()
            repo.stopConfirmedSession(first, task, now - 80_000, false).value()
            assertNull(repo.getTask(task)!!.task.completedAtMillis)
            val second = repo.startSession(session("写论文", now - 60_000, task), emptySet()).value()
            repo.stopConfirmedSession(second, task, now - 20_000, true).value()
            assertEquals(now - 20_000, repo.getTask(task)!!.task.completedAtMillis)
            assertEquals(now - 20_000, repo.getSession(second)!!.session.endAtMillis)
        }
    }
    @Test fun multipleTaskCandidatesAndFutureRecordsRemainSafe() = runBlocking {
        withRepository { repo ->
            val now = System.currentTimeMillis()
            listOf("写论文第一章", "写论文讨论部分", "写论文图表").forEach {
                repo.upsertTask(Task(title = it, createdAtMillis = now, updatedAtMillis = now), emptySet()).value()
            }
            val candidates = VoiceTargetResolver(repo).linkedTasks("写论文")
            assertEquals(3, candidates.size)
            assertNull(VoiceTargetMatching.unique(candidates))
            val draft = io.github.peanutpersimmon.rido.domain.voice.agent.QwenToolCallMapper().map(
                "complete_task", """{"target_query":"写论文"}""", "完成写论文",
                io.github.peanutpersimmon.rido.domain.voice.agent.VoiceAgentContext(Instant.ofEpochMilli(now), ZoneId.systemDefault()))
            assertEquals(3, VoiceTargetResolver(repo).targets(draft).size)
            assertTrue(repo.createPastSession(session("未来记录", now + 60_000).copy(endAtMillis = now + 120_000), emptySet()) is DomainResult.Failure)
            assertTrue(repo.getSessions(now, now + 200_000).isEmpty())
        }
    }
}
