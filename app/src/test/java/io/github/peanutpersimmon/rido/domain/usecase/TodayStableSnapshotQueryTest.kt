package io.github.peanutpersimmon.rido.domain.usecase

import io.github.peanutpersimmon.rido.domain.model.Category
import io.github.peanutpersimmon.rido.domain.model.DomainResult
import io.github.peanutpersimmon.rido.domain.model.Event
import io.github.peanutpersimmon.rido.domain.model.KeywordRule
import io.github.peanutpersimmon.rido.domain.model.Session
import io.github.peanutpersimmon.rido.domain.model.Tag
import io.github.peanutpersimmon.rido.domain.model.TaggedEvent
import io.github.peanutpersimmon.rido.domain.model.TaggedSession
import io.github.peanutpersimmon.rido.domain.model.TaggedTask
import io.github.peanutpersimmon.rido.domain.model.Task
import io.github.peanutpersimmon.rido.domain.repository.DeletedEventSnapshot
import io.github.peanutpersimmon.rido.domain.repository.DeletedSessionSnapshot
import io.github.peanutpersimmon.rido.domain.repository.RidoCoreRepository
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class TodayStableSnapshotQueryTest {
    private val zone = ZoneId.of("UTC")
    private val date = LocalDate.of(2026, 1, 1)
    private val now = date.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `N tasks use one all-task query and no per-task lookup`() = runBlocking {
        val tasks = (1L..50L).map { id ->
            Task(
                id = id,
                title = "Task $id",
                categoryId = 10,
                createdAtMillis = 0,
                updatedAtMillis = 0,
            )
        }
        val event = Event(
            id = 70,
            title = "Candidate event",
            dateEpochDay = date.toEpochDay(),
            createdAtMillis = 0,
            updatedAtMillis = 0,
        )
        val session = Session(
            id = 80,
            title = "Session",
            startAtMillis = now - 60_000,
            endAtMillis = now,
            sourceEventId = event.id,
            createdAtMillis = 0,
            updatedAtMillis = 0,
        )
        val repository = CountingRepository(
            allTasks = tasks,
            timelineTasks = tasks.map { TaggedTask(it, setOf(20)) },
            timelineEvents = listOf(TaggedEvent(event, setOf(20))),
            timelineSessions = listOf(TaggedSession(session, setOf(20))),
            categories = listOf(Category(id = 10, name = "Work", createdAtMillis = 0, updatedAtMillis = 0)),
            tags = listOf(Tag(id = 20, name = "Focus", createdAtMillis = 0, updatedAtMillis = 0)),
        )

        val snapshot = GetTodayStableSnapshotUseCase(repository)(request())

        assertEquals(1, repository.counts.nightSleeps)
        assertEquals(1, repository.counts.sessions)
        assertEquals(1, repository.counts.events)
        assertEquals(1, repository.counts.timelineTasks)
        assertEquals(1, repository.counts.allTasks)
        assertEquals(0, repository.counts.singleTask)
        assertEquals(0, repository.counts.singleEvent)
        assertEquals(0, repository.counts.eventBatch)
        assertEquals(1, repository.counts.categories)
        assertEquals(1, repository.counts.tags)
        assertEquals(50, snapshot.allTasks.size)
        assertEquals(50, snapshot.metadataSources.tasksById.size)
        assertEquals("Task 1", snapshot.labels.tasks.getValue(1).title)
        assertEquals("Work", snapshot.labels.categoriesById.getValue(10).name)
        assertEquals("Focus", snapshot.labels.tagNames.getValue(20))
        assertEquals("Candidate event", snapshot.metadataSources.sourceEventTitlesBySessionId[session.id])
    }

    @Test
    fun `source events outside timeline candidates use one batch query`() = runBlocking {
        val sessions = listOf(
            session(id = 1, sourceEventId = 101),
            session(id = 2, sourceEventId = 102),
            session(id = 3, sourceEventId = 101),
        )
        val repository = CountingRepository(
            timelineSessions = sessions.map(::TaggedSession),
            sourceEvents = listOf(
                event(id = 101, title = "Imported A"),
                event(id = 102, title = "Imported B"),
            ),
        )

        val snapshot = GetTodayStableSnapshotUseCase(repository)(request())

        assertEquals(1, repository.counts.sessions)
        assertEquals(1, repository.counts.events)
        assertEquals(1, repository.counts.eventBatch)
        assertEquals(0, repository.counts.singleEvent)
        assertEquals(setOf(101L, 102L), repository.requestedSourceEventIds)
        assertEquals("Imported A", snapshot.metadataSources.sourceEventTitlesBySessionId[1])
        assertEquals("Imported B", snapshot.metadataSources.sourceEventTitlesBySessionId[2])
        assertEquals("Imported A", snapshot.metadataSources.sourceEventTitlesBySessionId[3])
    }

    private fun request() = AwakeDayRequest(
        calendarDate = date,
        defaultWakeMinute = 7 * 60,
        defaultSleepMinute = 23 * 60,
        zoneId = zone,
        asOfMillis = now,
    )

    private fun session(id: Long, sourceEventId: Long) = Session(
        id = id,
        title = "Session $id",
        startAtMillis = now - id * 60_000,
        endAtMillis = now,
        sourceEventId = sourceEventId,
        createdAtMillis = 0,
        updatedAtMillis = 0,
    )

    private fun event(id: Long, title: String) = Event(
        id = id,
        title = title,
        dateEpochDay = date.minusDays(30).toEpochDay(),
        createdAtMillis = 0,
        updatedAtMillis = 0,
    )
}

private data class QueryCounts(
    var nightSleeps: Int = 0,
    var sessions: Int = 0,
    var events: Int = 0,
    var timelineTasks: Int = 0,
    var allTasks: Int = 0,
    var singleTask: Int = 0,
    var singleEvent: Int = 0,
    var eventBatch: Int = 0,
    var categories: Int = 0,
    var tags: Int = 0,
)

private class CountingRepository(
    private val allTasks: List<Task> = emptyList(),
    private val timelineTasks: List<TaggedTask> = emptyList(),
    private val timelineEvents: List<TaggedEvent> = emptyList(),
    private val timelineSessions: List<TaggedSession> = emptyList(),
    private val sourceEvents: List<Event> = emptyList(),
    private val categories: List<Category> = emptyList(),
    private val tags: List<Tag> = emptyList(),
) : RidoCoreRepository {
    val counts = QueryCounts()
    var requestedSourceEventIds: Set<Long> = emptySet()
        private set

    override fun observeDataChanges(): Flow<Unit> = flowOf(Unit)
    override fun observeActiveSession(): Flow<TaggedSession?> = flowOf(null)

    override suspend fun getNightSleeps(): List<Session> {
        counts.nightSleeps += 1
        return emptyList()
    }

    override suspend fun getSessions(rangeStartMillis: Long, rangeEndMillis: Long): List<TaggedSession> {
        counts.sessions += 1
        return timelineSessions
    }

    override suspend fun getCompletedActivityIntervalsInRange(rangeStartMillis: Long, rangeEndMillis: Long): List<TaggedSession> = emptyList()
    override suspend fun getActivityFactsInRange(rangeStartMillis: Long, rangeEndMillis: Long): List<TaggedSession> = emptyList()
    override suspend fun getNapsInRange(rangeStartMillis: Long, rangeEndMillis: Long): List<TaggedSession> = emptyList()

    override suspend fun getEvents(
        firstEpochDay: Long,
        lastEpochDay: Long,
        rangeStartMillis: Long,
        rangeEndMillis: Long,
    ): List<TaggedEvent> {
        counts.events += 1
        return timelineEvents
    }

    override suspend fun getTasks(
        firstEpochDay: Long,
        lastEpochDay: Long,
        rangeStartMillis: Long,
        rangeEndMillis: Long,
    ): List<TaggedTask> {
        counts.timelineTasks += 1
        return timelineTasks
    }

    override suspend fun getAllTasks(): List<Task> {
        counts.allTasks += 1
        return allTasks
    }

    override suspend fun getTask(taskId: Long): TaggedTask? {
        counts.singleTask += 1
        return null
    }

    override suspend fun getEvent(eventId: Long): TaggedEvent? {
        counts.singleEvent += 1
        return null
    }

    override suspend fun getEventsByIds(eventIds: Set<Long>): List<Event> {
        counts.eventBatch += 1
        requestedSourceEventIds = eventIds
        return sourceEvents.filter { it.id in eventIds }
    }

    override suspend fun getCategories(): List<Category> {
        counts.categories += 1
        return categories
    }

    override suspend fun getTags(): List<Tag> {
        counts.tags += 1
        return tags
    }

    override suspend fun getEnabledKeywordRules(): List<KeywordRule> = unused()
    override suspend fun getSession(sessionId: Long): TaggedSession? = unused()
    override suspend fun startSession(session: Session, tagIds: Set<Long>): DomainResult<Long> = unused()
    override suspend fun stopActiveSession(endAtMillis: Long): DomainResult<Session> = unused()
    override suspend fun stopSessionAndCompleteTask(endAtMillis: Long): DomainResult<Session> = unused()
    override suspend fun createPastSession(session: Session, tagIds: Set<Long>): DomainResult<Long> = unused()
    override suspend fun updateSession(session: Session, tagIds: Set<Long>): DomainResult<Unit> = unused()
    override suspend fun deleteSession(sessionId: Long): DomainResult<Unit> = unused()
    override suspend fun completeTask(taskId: Long, completedAtMillis: Long): DomainResult<Unit> = unused()
    override suspend fun reopenTask(taskId: Long, updatedAtMillis: Long): DomainResult<Unit> = unused()
    override suspend fun upsertTask(task: Task, tagIds: Set<Long>): DomainResult<Long> = unused()
    override suspend fun upsertEvent(event: Event, tagIds: Set<Long>): DomainResult<Long> = unused()
    override suspend fun deleteTask(taskId: Long): DomainResult<Unit> = unused()
    override suspend fun deleteEvent(eventId: Long): DomainResult<DeletedEventSnapshot> = unused()
    override suspend fun restoreEvent(snapshot: DeletedEventSnapshot): DomainResult<Unit> = unused()
    override suspend fun deleteSessionWithSnapshot(sessionId: Long): DomainResult<DeletedSessionSnapshot> = unused()
    override suspend fun restoreSession(snapshot: DeletedSessionSnapshot): DomainResult<Unit> = unused()

    private fun <T> unused(): T = error("Unexpected repository call")
}
