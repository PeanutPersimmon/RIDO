package io.github.peanutpersimmon.rido.data

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.peanutpersimmon.rido.data.local.room.RidoDatabase
import io.github.peanutpersimmon.rido.data.local.room.entity.CategoryEntity
import io.github.peanutpersimmon.rido.data.local.room.entity.EventEntity
import io.github.peanutpersimmon.rido.data.local.room.entity.EventTagCrossRef
import io.github.peanutpersimmon.rido.data.local.room.entity.SessionEntity
import io.github.peanutpersimmon.rido.data.local.room.entity.SessionTagCrossRef
import io.github.peanutpersimmon.rido.data.local.room.entity.TagEntity
import io.github.peanutpersimmon.rido.data.local.room.entity.TaskEntity
import io.github.peanutpersimmon.rido.data.local.room.entity.TaskTagCrossRef
import io.github.peanutpersimmon.rido.data.repository.RoomRidoRepository
import io.github.peanutpersimmon.rido.domain.model.SessionKind
import io.github.peanutpersimmon.rido.domain.model.SessionTemporalType
import io.github.peanutpersimmon.rido.domain.model.TaskStatus
import java.util.Collections
import java.util.concurrent.Executor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DataChangeInvalidationTest {
    @Test
    fun initialSignalIsEmittedOnceWithoutBusinessSelects() = runBlocking {
        withDatabase { database, queries ->
            queries.clear()
            val probe = SignalProbe.start(this, RoomRidoRepository(database).observeDataChanges())
            try {
                probe.awaitSignal()
                probe.assertNoSignal()
                assertNoBusinessSelects(queries)
            } finally {
                probe.close()
            }
        }
    }

    @Test
    fun allTodayAndPlanTablesSignalWithoutObserverBusinessSelects() = runBlocking {
        withDatabase { database, queries ->
            val repository = RoomRidoRepository(database)
            val probe = SignalProbe.start(this, repository.observeDataChanges())
            try {
                probe.awaitSignal()
                queries.clear()

                val task = taskEntity(title = "Task")
                val taskId = database.taskDao().insert(task)
                probe.awaitSignal()
                database.taskDao().update(task.copy(id = taskId, title = "Updated task"))
                probe.awaitSignal()

                val tag = tagEntity(name = "Tag")
                val tagId = database.tagDao().insert(tag)
                probe.awaitSignal()
                database.taskDao().insertTagRefs(listOf(TaskTagCrossRef(taskId, tagId)))
                probe.awaitSignal()

                val event = eventEntity(title = "Event")
                val eventId = database.eventDao().insert(event)
                probe.awaitSignal()
                database.eventDao().update(event.copy(id = eventId, title = "Updated event"))
                probe.awaitSignal()
                database.eventDao().insertTagRefs(listOf(EventTagCrossRef(eventId, tagId)))
                probe.awaitSignal()

                val session = sessionEntity(title = "Session", kind = SessionKind.ACTIVITY, start = 1_000L)
                val sessionId = database.sessionDao().insert(session)
                probe.awaitSignal()
                database.sessionDao().finish(sessionId, endAtMillis = 2_000L, updatedAtMillis = 2_000L)
                probe.awaitSignal()
                database.sessionDao().insertTagRefs(listOf(SessionTagCrossRef(sessionId, tagId)))
                probe.awaitSignal()

                val sleep = sessionEntity(title = "Sleep", kind = SessionKind.NIGHT_SLEEP, start = 3_000L)
                val sleepId = database.sessionDao().insert(sleep)
                probe.awaitSignal()
                database.sessionDao().finish(sleepId, endAtMillis = 4_000L, updatedAtMillis = 4_000L)
                probe.awaitSignal()

                val category = categoryEntity(name = "Category")
                val categoryId = database.categoryDao().insert(category)
                probe.awaitSignal()
                database.categoryDao().update(category.copy(id = categoryId, name = "Updated category"))
                probe.awaitSignal()

                database.tagDao().update(tag.copy(id = tagId, name = "Updated tag"))
                probe.awaitSignal()

                probe.assertNoSignal()
                assertNoBusinessSelects(queries)
            } finally {
                probe.close()
            }
        }
    }

    @Test
    fun oneTransactionAcrossMainAndJunctionTablesEmitsOnce() = runBlocking {
        withDatabase { database, _ ->
            val tagId = database.tagDao().insert(tagEntity(name = "Tag"))
            val probe = SignalProbe.start(this, RoomRidoRepository(database).observeDataChanges())
            try {
                probe.awaitSignal()

                database.withTransaction {
                    val taskId = database.taskDao().insert(taskEntity(title = "Task"))
                    database.taskDao().insertTagRefs(listOf(TaskTagCrossRef(taskId, tagId)))
                }

                probe.awaitSignal()
                probe.assertNoSignal()
            } finally {
                probe.close()
            }
        }
    }

    @Test
    fun tagAndThreeJunctionChangesInOneTransactionEmitOnce() = runBlocking {
        withDatabase { database, _ ->
            val taskId = database.taskDao().insert(taskEntity(title = "Task"))
            val eventId = database.eventDao().insert(eventEntity(title = "Event"))
            val sessionId = database.sessionDao().insert(
                sessionEntity(title = "Session", kind = SessionKind.ACTIVITY, start = 1_000L),
            )
            val tag = tagEntity(name = "Tag")
            val tagId = database.tagDao().insert(tag)
            val probe = SignalProbe.start(this, RoomRidoRepository(database).observeDataChanges())
            try {
                probe.awaitSignal()

                database.withTransaction {
                    database.tagDao().update(tag.copy(id = tagId, name = "Updated tag"))
                    database.taskDao().replaceTags(taskId, setOf(tagId))
                    database.eventDao().replaceTags(eventId, setOf(tagId))
                    database.sessionDao().replaceTags(sessionId, setOf(tagId))
                }

                probe.awaitSignal()
                probe.assertNoSignal()
            } finally {
                probe.close()
            }
        }
    }

    private suspend fun withDatabase(
        test: suspend (RidoDatabase, MutableList<String>) -> Unit,
    ) {
        val queries = Collections.synchronizedList(mutableListOf<String>())
        val directExecutor = Executor { command -> command.run() }
        val database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            RidoDatabase::class.java,
        ).setQueryCallback(
            RoomDatabase.QueryCallback { sql, _ -> queries.add(sql) },
            directExecutor,
        ).build()
        try {
            test(database, queries)
        } finally {
            database.close()
        }
    }

    private fun assertNoBusinessSelects(queries: List<String>) {
        val businessTables = listOf(
            "sessions",
            "events",
            "tasks",
            "categories",
            "tags",
            "session_tag_cross_refs",
            "event_tag_cross_refs",
            "task_tag_cross_refs",
        )
        val businessSelects = synchronized(queries) {
            queries.filter { sql ->
                val normalized = sql.trim().lowercase()
                normalized.startsWith("select") && businessTables.any { table ->
                    Regex("\\b${Regex.escape(table)}\\b").containsMatchIn(normalized)
                }
            }
        }
        assertFalse("Observer executed business SELECTs: $businessSelects", businessSelects.isNotEmpty())
    }

    private fun taskEntity(title: String) = TaskEntity(
        title = title,
        status = TaskStatus.TODO,
        createdAtMillis = 1L,
        updatedAtMillis = 1L,
    )

    private fun eventEntity(title: String) = EventEntity(
        title = title,
        dateEpochDay = 1L,
        createdAtMillis = 1L,
        updatedAtMillis = 1L,
    )

    private fun sessionEntity(title: String, kind: SessionKind, start: Long) = SessionEntity(
        title = title,
        kind = kind,
        temporalType = SessionTemporalType.INTERVAL,
        startAtMillis = start,
        createdAtMillis = start,
        updatedAtMillis = start,
    )

    private fun categoryEntity(name: String) = CategoryEntity(
        name = name,
        createdAtMillis = 1L,
        updatedAtMillis = 1L,
    )

    private fun tagEntity(name: String) = TagEntity(
        name = name,
        createdAtMillis = 1L,
        updatedAtMillis = 1L,
    )

    private class SignalProbe(
        private val channel: Channel<Unit>,
        private val job: Job,
    ) {
        suspend fun awaitSignal() {
            withTimeout(5_000L) { channel.receive() }
        }

        suspend fun assertNoSignal() {
            assertNull(withTimeoutOrNull(300L) { channel.receive() })
        }

        suspend fun close() {
            job.cancelAndJoin()
            channel.close()
        }

        companion object {
            fun start(scope: CoroutineScope, flow: Flow<Unit>): SignalProbe {
                val channel = Channel<Unit>(Channel.UNLIMITED)
                val job = scope.launch { flow.collect { channel.send(Unit) } }
                return SignalProbe(channel, job)
            }
        }
    }
}
