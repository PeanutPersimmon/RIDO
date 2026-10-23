package io.github.peanutpersimmon.rido.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.peanutpersimmon.rido.data.local.room.RidoDatabase
import io.github.peanutpersimmon.rido.data.local.room.entity.TaskEntity
import io.github.peanutpersimmon.rido.data.repository.RoomRidoRepository
import io.github.peanutpersimmon.rido.domain.model.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TaskRecordCompoundRoomTest {
    private suspend fun withRepository(block: suspend (RidoDatabase, RoomRidoRepository) -> Unit) {
        val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,
            RidoDatabase::class.java).build()
        try { block(db, RoomRidoRepository(db)) } finally { db.close() }
    }

    @Test fun intervalAndPointCompoundUseExactRecordTimeWhilePlainSaveDoesNotComplete() = runBlocking {
        withRepository { _, repository ->
            val taskId = repository.saveTask(Task(title = "看文献", status = TaskStatus.TODO,
                createdAtMillis = 1, updatedAtMillis = 1))
            val plain = Session(title = "先补录", startAtMillis = 100, endAtMillis = 200,
                taskId = taskId, createdAtMillis = 1, updatedAtMillis = 1)
            assertTrue(repository.createPastSession(plain, emptySet()) is DomainResult.Success)
            assertNull(repository.getTask(taskId)?.task?.completedAtMillis)
            val interval = plain.copy(title = "再补录", startAtMillis = 300, endAtMillis = 400)
            val intervalId = (repository.createPastSessionAndCompleteTask(interval, emptySet()) as DomainResult.Success).value
            assertEquals(400L, repository.getTask(taskId)?.task?.completedAtMillis)
            assertEquals(400L, repository.getSession(intervalId)?.session?.endAtMillis)

            val pointTaskId = repository.saveTask(Task(title = "取快递", createdAtMillis = 1, updatedAtMillis = 1))
            val point = Session(title = "取快递", temporalType = SessionTemporalType.POINT,
                startAtMillis = 500, taskId = pointTaskId, createdAtMillis = 1, updatedAtMillis = 1)
            val pointId = (repository.createPastSessionAndCompleteTask(point, emptySet()) as DomainResult.Success).value
            assertEquals(500L, repository.getTask(pointTaskId)?.task?.completedAtMillis)
            assertNull(repository.getSession(pointId)?.session?.endAtMillis)
        }
    }

    @Test fun doneTaskAcceptsHistoricalLinksButRejectsCompoundAndActiveStart() = runBlocking {
        withRepository { _, repository ->
            val taskId = repository.saveTask(Task(title = "已完成", status = TaskStatus.DONE,
                completedAtMillis = 50, completionSource = TaskCompletionSource.MANUAL,
                createdAtMillis = 1, updatedAtMillis = 50))
            val point = Session(title = "补录", temporalType = SessionTemporalType.POINT,
                startAtMillis = 100, taskId = taskId, createdAtMillis = 1, updatedAtMillis = 1)
            assertTrue(repository.createPastSession(point, emptySet()) is DomainResult.Success)
            assertTrue(repository.createPastSessionAndCompleteTask(point.copy(startAtMillis = 120), emptySet()) is DomainResult.Failure)
            assertEquals(50L, repository.getTask(taskId)?.task?.completedAtMillis)
            assertTrue(repository.startSession(point.copy(temporalType = SessionTemporalType.INTERVAL,
                startAtMillis = 200), emptySet()) is DomainResult.Failure)
            assertTrue(repository.getSession(2) == null)
        }
    }

    @Test fun failureAfterRecordInsertRollsBackBothWrites() = runBlocking {
        withRepository { db, repository ->
            // A self-parented legacy-corrupt row forces ancestor reconciliation to fail after insert.
            db.taskDao().insert(TaskEntity(id = 9, title = "循环", status = TaskStatus.TODO,
                parentTaskId = 9, createdAtMillis = 1, updatedAtMillis = 1))
            val record = Session(title = "补录", temporalType = SessionTemporalType.POINT,
                startAtMillis = 100, taskId = 9, createdAtMillis = 1, updatedAtMillis = 1)
            assertTrue(runCatching { repository.createPastSessionAndCompleteTask(record, emptySet()) }.isFailure)
            assertEquals(TaskStatus.TODO, repository.getTask(9)?.task?.status)
            assertNull(repository.getTask(9)?.task?.completedAtMillis)
            assertTrue(db.sessionDao().getIntersecting(0, 200).isEmpty())
        }
    }
}
