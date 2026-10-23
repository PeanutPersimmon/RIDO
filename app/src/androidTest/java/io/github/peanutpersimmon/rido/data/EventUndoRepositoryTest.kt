package io.github.peanutpersimmon.rido.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.peanutpersimmon.rido.data.local.room.RidoDatabase
import io.github.peanutpersimmon.rido.data.repository.RoomRidoRepository
import io.github.peanutpersimmon.rido.domain.model.DomainResult
import io.github.peanutpersimmon.rido.domain.model.Event
import io.github.peanutpersimmon.rido.domain.model.Session
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EventUndoRepositoryTest {
    @Test
    fun deleteAndUndoRestoresAllOriginalSessionLinks() = runBlocking {
        withRepository { repository ->
            val eventId = repository.createEvent("Event")
            val first = repository.createSession("A", 1, eventId)
            val second = repository.createSession("B", 2, eventId)

            val snapshot = repository.deleteEvent(eventId).value()

            assertNull(repository.getEvent(eventId))
            assertNull(repository.getSession(first)!!.session.sourceEventId)
            assertNull(repository.getSession(second)!!.session.sourceEventId)
            assertEquals(listOf(first, second), snapshot.linkedSessionIds)

            repository.restoreEvent(snapshot).value()

            assertNotNull(repository.getEvent(eventId))
            assertEquals(eventId, repository.getSession(first)!!.session.sourceEventId)
            assertEquals(eventId, repository.getSession(second)!!.session.sourceEventId)
        }
    }

    @Test
    fun deleteAndUndoWithoutLinkedSessionsCompletesNormally() = runBlocking {
        withRepository { repository ->
            val eventId = repository.createEvent("Unlinked")

            val snapshot = repository.deleteEvent(eventId).value()
            assertEquals(emptyList<Long>(), snapshot.linkedSessionIds)

            repository.restoreEvent(snapshot).value()
            assertNotNull(repository.getEvent(eventId))
        }
    }

    @Test
    fun undoOnlyRestoresSessionsCapturedForThatEvent() = runBlocking {
        withRepository { repository ->
            val targetEventId = repository.createEvent("Target")
            val otherEventId = repository.createEvent("Other")
            val linked = repository.createSession("A", 1, targetEventId)
            val unlinked = repository.createSession("B", 2, null)
            val linkedElsewhere = repository.createSession("C", 3, otherEventId)

            val snapshot = repository.deleteEvent(targetEventId).value()
            repository.restoreEvent(snapshot).value()

            assertEquals(targetEventId, repository.getSession(linked)!!.session.sourceEventId)
            assertNull(repository.getSession(unlinked)!!.session.sourceEventId)
            assertEquals(otherEventId, repository.getSession(linkedElsewhere)!!.session.sourceEventId)
        }
    }

    @Test
    fun undoDoesNotRecreateSessionDeletedAfterEventDeletion() = runBlocking {
        withRepository { repository ->
            val eventId = repository.createEvent("Event")
            val sessionId = repository.createSession("A", 1, eventId)

            val snapshot = repository.deleteEvent(eventId).value()
            repository.deleteSession(sessionId).value()
            repository.restoreEvent(snapshot).value()

            assertNotNull(repository.getEvent(eventId))
            assertNull(repository.getSession(sessionId))
        }
    }

    @Test
    fun undoDoesNotOverwriteSessionReassignedAfterEventDeletion() = runBlocking {
        withRepository { repository ->
            val originalEventId = repository.createEvent("Original")
            val replacementEventId = repository.createEvent("Replacement")
            val sessionId = repository.createSession("A", 1, originalEventId)

            val snapshot = repository.deleteEvent(originalEventId).value()
            val session = requireNotNull(repository.getSession(sessionId))
            repository.updateSession(
                session.session.copy(sourceEventId = replacementEventId),
                session.tagIds,
            ).value()

            repository.restoreEvent(snapshot).value()

            assertEquals(
                replacementEventId,
                repository.getSession(sessionId)!!.session.sourceEventId,
            )
        }
    }

    @Test
    fun undoRestoresEverySessionInAMultiSessionSnapshot() = runBlocking {
        withRepository { repository ->
            val eventId = repository.createEvent("Event")
            val sessionIds = (1L..5L).map { index ->
                repository.createSession("Session $index", index, eventId)
            }

            val snapshot = repository.deleteEvent(eventId).value()
            assertEquals(sessionIds, snapshot.linkedSessionIds)
            repository.restoreEvent(snapshot).value()

            assertEquals(
                List(sessionIds.size) { eventId },
                sessionIds.map { repository.getSession(it)!!.session.sourceEventId },
            )
        }
    }

    private suspend fun RoomRidoRepository.createEvent(title: String): Long =
        saveEvent(
            Event(
                title = title,
                dateEpochDay = 0,
                createdAtMillis = 1,
                updatedAtMillis = 1,
            ),
        )

    private suspend fun RoomRidoRepository.createSession(
        title: String,
        slot: Long,
        sourceEventId: Long?,
    ): Long {
        val start = slot * 10_000
        return saveSession(
            Session(
                title = title,
                startAtMillis = start,
                endAtMillis = start + 1_000,
                sourceEventId = sourceEventId,
                createdAtMillis = start,
                updatedAtMillis = start,
            ),
        )
    }

    private suspend fun withRepository(block: suspend (RoomRidoRepository) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, RidoDatabase::class.java).build()
        try {
            block(RoomRidoRepository(database))
        } finally {
            database.close()
        }
    }

    private fun <T> DomainResult<T>.value(): T = (this as DomainResult.Success<T>).value
}
