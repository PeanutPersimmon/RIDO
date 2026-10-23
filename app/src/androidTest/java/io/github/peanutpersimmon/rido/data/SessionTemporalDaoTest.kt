package io.github.peanutpersimmon.rido.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.peanutpersimmon.rido.data.local.room.RidoDatabase
import io.github.peanutpersimmon.rido.data.local.room.entity.SessionEntity
import io.github.peanutpersimmon.rido.data.repository.RoomRidoRepository
import io.github.peanutpersimmon.rido.domain.model.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SessionTemporalDaoTest {
    @Test fun activityPurposeQueriesUseHalfOpenRangesAndExcludeSleepAndActive() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, RidoDatabase::class.java).build()
        try {
            val dao = db.sessionDao()
            val completed = dao.insert(entity("completed", 120, 180, SessionTemporalType.INTERVAL))
            val crossing = dao.insert(entity("crossing", 90, 110, SessionTemporalType.INTERVAL))
            val endingAtStart = dao.insert(entity("ending", 60, 100, SessionTemporalType.INTERVAL))
            val startingAtEnd = dao.insert(entity("starting", 200, 230, SessionTemporalType.INTERVAL))
            val point = dao.insert(entity("point", 199, null, SessionTemporalType.POINT))
            dao.insert(entity("next point", 200, null, SessionTemporalType.POINT))
            dao.insert(entity("active", 150, null, SessionTemporalType.INTERVAL))
            dao.insert(entity("night", 130, 170, SessionTemporalType.INTERVAL, SessionKind.NIGHT_SLEEP))
            dao.insert(entity("nap", 130, 170, SessionTemporalType.INTERVAL, SessionKind.NAP))
            assertEquals(setOf(completed, crossing),
                dao.getCompletedActivityIntervalsInRange(100, 200).map { it.session.id }.toSet())
            assertEquals(setOf(completed, crossing, point),
                dao.getActivityFactsInRange(100, 200).map { it.session.id }.toSet())
            assertFalse(dao.getCompletedActivityIntervalsInRange(100, 200).any {
                it.session.id == endingAtStart || it.session.id == startingAtEnd
            })
            val repository = RoomRidoRepository(db)
            assertEquals(setOf(completed, crossing), repository.getCompletedActivityIntervalsInRange(100, 200)
                .map { it.session.id }.toSet())
        } finally { db.close() }
    }

    @Test fun monthPointEndBoundaryAndSleepWriteInvariant() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, RidoDatabase::class.java).build()
        try {
            val dao = db.sessionDao()
            val lastPoint = dao.insert(entity("last", 999, null, SessionTemporalType.POINT))
            val nextPoint = dao.insert(entity("next", 1000, null, SessionTemporalType.POINT))
            assertEquals(listOf(lastPoint), dao.getActivityFactsInRange(0, 1000).map { it.session.id })
            assertEquals(listOf(nextPoint), dao.getActivityFactsInRange(1000, 2000).map { it.session.id })

            val repository = RoomRidoRepository(db)
            for ((index, kind) in listOf(SessionKind.NIGHT_SLEEP, SessionKind.NAP).withIndex()) {
                val base = Session(title = "sleep", kind = kind, temporalType = SessionTemporalType.INTERVAL,
                    startAtMillis = 100L + index * 200L, endAtMillis = 200L + index * 200L,
                    createdAtMillis = 1, updatedAtMillis = 1)
                assertEquals(DomainError.InvalidTime, (repository.createPastSession(
                    base.copy(temporalType = SessionTemporalType.POINT, endAtMillis = null), emptySet()
                ) as DomainResult.Failure).error)
                val invalid = listOf(
                    base.copy(taskId = 1), base.copy(categoryId = 1), base.copy(sourceEventId = 1)
                )
                invalid.forEach { session ->
                    assertEquals(DomainError.InvalidTime, (repository.createPastSession(session, emptySet())
                        as DomainResult.Failure).error)
                }
                assertEquals(DomainError.InvalidTime, (repository.createPastSession(base, setOf(1))
                    as DomainResult.Failure).error)
                val savedId = (repository.createPastSession(base, emptySet()) as DomainResult.Success).value
                assertTrue(repository.updateSession(base.copy(id = savedId, title = "edited sleep"), emptySet())
                    is DomainResult.Success)
                assertEquals("edited sleep", repository.getSession(savedId)?.session?.title)
            }
            assertEquals(1, repository.getNightSleeps().size)
            assertEquals(1, repository.getNapsInRange(0, 1000).size)
        } finally { db.close() }
    }

    @Test fun pointRangeActiveOverlapAndStopSemantics() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, RidoDatabase::class.java).build()
        try {
            val dao = db.sessionDao()
            val interval = dao.insert(entity("interval", 100, 300, SessionTemporalType.INTERVAL))
            val point = dao.insert(entity("point", 200, null, SessionTemporalType.POINT))
            val farPoint = dao.insert(entity("far point", 1000, null, SessionTemporalType.POINT))
            val active = dao.insert(entity("active", 500, null, SessionTemporalType.INTERVAL))
            assertEquals(setOf(interval, point), dao.getIntersecting(150, 250).map { it.id }.toSet())
            assertEquals(setOf(active, farPoint), dao.getIntersecting(900, 1100).map { it.id }.toSet())
            assertFalse(dao.getIntersecting(2000, 2100).any { it.id == point || it.id == farPoint })
            assertEquals(listOf(active), dao.getActiveSessions().map { it.id })
            assertEquals(1, dao.countActiveSessions())
            assertEquals(1, dao.countOverlaps(200, 250))
            assertEquals(0, dao.countOverlaps(300, 400))
            val repository = RoomRidoRepository(db)
            val savedPoint = repository.createPastSession(Session(title = "inside", temporalType = SessionTemporalType.POINT,
                startAtMillis = 220, createdAtMillis = 1, updatedAtMillis = 1), emptySet())
            assertTrue(savedPoint is DomainResult.Success)
            val overlap = repository.createPastSession(Session(title = "overlap", startAtMillis = 220,
                endAtMillis = 320, createdAtMillis = 1, updatedAtMillis = 1), emptySet())
            assertEquals(DomainError.SessionOverlap, (overlap as DomainResult.Failure).error)
            val stopped = repository.stopActiveSession(600)
            assertEquals(active, (stopped as DomainResult.Success).value.id)
            assertEquals(SessionTemporalType.POINT, dao.getById(point)!!.temporalType)
            assertNull(dao.getById(point)!!.endAtMillis)
            assertTrue(dao.getActiveSessions().isEmpty())
            assertTrue(repository.stopActiveSession(700) is DomainResult.Failure)
        } finally {
            db.close()
        }
    }

    private fun entity(title: String, start: Long, end: Long?, type: SessionTemporalType,
        kind: SessionKind = SessionKind.ACTIVITY) =
        SessionEntity(title = title, kind = kind, temporalType = type,
            startAtMillis = start, endAtMillis = end, createdAtMillis = 1, updatedAtMillis = 1)
}

