package io.github.peanutpersimmon.rido.domain.usecase

import io.github.peanutpersimmon.rido.domain.model.DomainError
import io.github.peanutpersimmon.rido.domain.model.DomainResult
import io.github.peanutpersimmon.rido.domain.model.Event
import io.github.peanutpersimmon.rido.domain.model.KeywordRule
import io.github.peanutpersimmon.rido.domain.model.Session
import io.github.peanutpersimmon.rido.domain.model.SessionTemporalType
import io.github.peanutpersimmon.rido.domain.model.TaggedEvent
import io.github.peanutpersimmon.rido.domain.model.TaggedSession
import io.github.peanutpersimmon.rido.domain.model.TaggedTask
import io.github.peanutpersimmon.rido.domain.model.Task
import io.github.peanutpersimmon.rido.domain.model.TaskCompletionSource
import io.github.peanutpersimmon.rido.domain.model.TaskStatus
import io.github.peanutpersimmon.rido.domain.model.Tag
import io.github.peanutpersimmon.rido.domain.repository.RidoCoreRepository
import io.github.peanutpersimmon.rido.domain.repository.DeletedEventSnapshot
import io.github.peanutpersimmon.rido.domain.repository.DeletedSessionSnapshot
import io.github.peanutpersimmon.rido.domain.validation.DataValidator
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreUseCasesTest {
    @Test fun statisticsUseCaseRequestsOnlyPurposeSpecificSessionQueries() = runBlocking {
        val repository = FakeRepository(sessions = mutableListOf(session(1, 10, 20)))
        val day = java.time.LocalDate.of(1970, 1, 1)
        val result = GetStatisticsUseCase(repository)(
            io.github.peanutpersimmon.rido.domain.service.StatisticsPeriod(
                io.github.peanutpersimmon.rido.domain.service.StatisticsGranularity.DAY, day),
            java.time.YearMonth.from(day), java.time.ZoneOffset.UTC, 100,
        )
        assertTrue(result is DomainResult.Success)
        assertEquals(0, repository.genericSessionReads)
        assertEquals(1, repository.activitySessionReads)
        assertEquals(1, repository.napSessionReads)
    }

    @Test fun startWithoutActiveCreatesActiveSession() = runBlocking {
        val repository = FakeRepository()
        val result = StartSessionUseCase(repository)(input(start = 10))
        assertTrue(result is DomainResult.Success)
        assertEquals(null, repository.sessions.single().endAtMillis)
    }

    @Test fun startWithActiveStopsOldThenCreatesNew() = runBlocking {
        val repository = FakeRepository(sessions = mutableListOf(session(1, 8, null)))
        StartSessionUseCase(repository)(input(start = 10))
        assertEquals(10L, repository.sessions.first { it.id == 1L }.endAtMillis)
        assertEquals(null, repository.sessions.first { it.id != 1L }.endAtMillis)
    }

    @Test fun startEqualToOldStartIsRejected() = runBlocking {
        val repository = FakeRepository(sessions = mutableListOf(session(1, 10, null)))
        assertFailure(StartSessionUseCase(repository)(input(start = 10)), DomainError.InvalidTime)
    }

    @Test fun pastSessionOverlapIsRejected() = runBlocking {
        val repository = FakeRepository(sessions = mutableListOf(session(1, 10, 12)))
        assertFailure(CreatePastSessionUseCase(repository)(input(11, 13)), DomainError.SessionOverlap)
    }

    @Test fun pointInsideIntervalIsSavedAndNeverStoppedAsActive() = runBlocking {
        val repository = FakeRepository(sessions = mutableListOf(session(1, 10, 20)))
        val result = CreatePastSessionUseCase(repository)(input(15).copy(temporalType = SessionTemporalType.POINT))
        assertTrue(result is DomainResult.Success)
        assertEquals(SessionTemporalType.POINT, repository.sessions.last().temporalType)
        assertFailure(StopActiveSessionUseCase(repository)(16), DomainError.NoActiveSession)
        assertFailure(StartSessionUseCase(repository)(input(30).copy(temporalType = SessionTemporalType.POINT)), DomainError.InvalidTime)
    }

    @Test fun sleepUseCaseRejectsPointAndKeepsIntervalsUnclassified() = runBlocking {
        val repository = FakeRepository()
        val sleep = NewSessionInput(title = "Nap", startAtMillis = 10, endAtMillis = 20,
            kind = io.github.peanutpersimmon.rido.domain.model.SessionKind.NAP,
            categoryId = 7, taskId = 8, sourceEventId = 9, tagIds = setOf(5))
        assertTrue(CreateSleepSessionUseCase(repository).create(sleep.copy(temporalType = SessionTemporalType.POINT))
            is DomainResult.Failure)
        assertTrue(CreateSleepSessionUseCase(repository).create(sleep) is DomainResult.Success)
        val saved = repository.sessions.single()
        assertEquals(SessionTemporalType.INTERVAL, saved.temporalType)
        assertEquals(null, saved.categoryId)
        assertEquals(null, saved.taskId)
        assertEquals(null, saved.sourceEventId)
    }

    @Test fun pastSessionMayOverlapImplicitEvent() = runBlocking {
        val repository = FakeRepository(events = mutableListOf(event(1, 10, 12)))
        assertTrue(CreatePastSessionUseCase(repository)(input(11, 13)) is DomainResult.Success)
    }

    @Test fun deleteRemovesOnlySession() = runBlocking {
        val repository = FakeRepository(
            sessions = mutableListOf(session(1, 10, 12, taskId = 7)),
            tasks = mutableListOf(task(7)),
        )
        DeleteSessionUseCase(repository)(1)
        assertTrue(repository.sessions.isEmpty())
        assertEquals(TaskStatus.IN_PROGRESS, repository.tasks.single().status)
    }

    @Test fun stopDoesNotCompleteLinkedTask() = runBlocking {
        val repository = FakeRepository(
            sessions = mutableListOf(session(1, 10, null, taskId = 7)),
            tasks = mutableListOf(task(7)),
        )
        StopActiveSessionUseCase(repository)(12)
        assertEquals(TaskStatus.IN_PROGRESS, repository.tasks.single().status)
    }

    @Test fun manualInitialClassificationWinsOverKeywordRules() = runBlocking {
        val repository = FakeRepository(
            rules = listOf(keywordRule(categoryId = 1, tagIds = setOf(2))),
        )
        val result = InitialClassificationUseCase(repository)(
            InitialClassificationInput(
                title = "Make PPT",
                explicitCategoryId = 9,
                categoryWasExplicitlySet = true,
                explicitTagIds = emptySet(),
                tagsWereExplicitlySet = true,
                applyKeywordRules = true,
            ),
        )
        assertEquals(9L, result.categoryId)
        assertTrue(result.tagIds.isEmpty())
    }

    @Test fun keywordRulesFillOnlyMissingInitialValues() = runBlocking {
        val repository = FakeRepository(rules = listOf(keywordRule(categoryId = 1, tagIds = setOf(2))))
        val result = InitialClassificationUseCase(repository)(
            InitialClassificationInput(title = "Make PPT", applyKeywordRules = true),
        )
        assertEquals(1L, result.categoryId)
        assertEquals(setOf(2L), result.tagIds)
    }

    @Test fun noRuleStillAllowsEmptyClassification() = runBlocking {
        val result = InitialClassificationUseCase(FakeRepository())(
            InitialClassificationInput(title = "Anything", applyKeywordRules = true),
        )
        assertEquals(null, result.categoryId)
        assertTrue(result.tagIds.isEmpty())
    }

    @Test fun archivedTagRuleStopsNewClassificationButHistoricalIdentityRemains() = runBlocking {
        val historical = TaggedSession(session(4, 10, 20), setOf(2))
        val repository = FakeRepository(
            sessions = mutableListOf(historical.session),
            rules = listOf(keywordRule(categoryId = null, tagIds = setOf(2))),
            tags = mutableListOf(Tag(id = 2, name = "文献", createdAtMillis = 0, updatedAtMillis = 0)),
        )
        val input = InitialClassificationInput("Make PPT", applyKeywordRules = true)
        assertEquals(setOf(2L), InitialClassificationUseCase(repository)(input).tagIds)
        repository.tags[0] = repository.tags[0].copy(isArchived = true)
        assertTrue(InitialClassificationUseCase(repository)(input).tagIds.isEmpty())
        assertEquals(setOf(2L), historical.tagIds)
        assertEquals(2L, repository.getTags().single().id)
        val stats = io.github.peanutpersimmon.rido.domain.service.ActualStatisticsCalculator.calculate(
            listOf(historical),
            io.github.peanutpersimmon.rido.domain.service.StatisticsPeriod(
                io.github.peanutpersimmon.rido.domain.service.StatisticsGranularity.DAY,
                java.time.LocalDate.of(1970, 1, 1),
            ), java.time.ZoneOffset.UTC, 1000,
        ) as DomainResult.Success
        assertEquals(10L, stats.value.rank(category = false).single { it.id == 2L }.durationMillis)
    }

    private fun input(start: Long, end: Long? = null) = NewSessionInput(
        title = "Activity",
        startAtMillis = start,
        endAtMillis = end,
    )

    private fun session(id: Long, start: Long, end: Long?, taskId: Long? = null) = Session(
        id = id,
        title = "Session $id",
        startAtMillis = start,
        endAtMillis = end,
        taskId = taskId,
        createdAtMillis = start,
        updatedAtMillis = start,
    )

    private fun event(id: Long, start: Int, end: Int) = Event(
        id = id,
        title = "Event $id",
        dateEpochDay = 0,
        startMinuteOfDay = start,
        endMinuteOfDay = end,
        createdAtMillis = 0,
        updatedAtMillis = 0,
    )

    private fun task(id: Long) = Task(id = id, title = "Task $id", status = TaskStatus.IN_PROGRESS,
        createdAtMillis = 0, updatedAtMillis = 0)

    private fun keywordRule(categoryId: Long?, tagIds: Set<Long>) = KeywordRule(
        id = 1, keyword = "PPT", categoryId = categoryId, tagIds = tagIds,
        isEnabled = true, priority = 1, createdAtMillis = 0, updatedAtMillis = 0)

    private fun assertFailure(result: DomainResult<*>, error: DomainError) {
        assertTrue(result is DomainResult.Failure)
        assertEquals(error, (result as DomainResult.Failure).error)
    }

    private class FakeRepository(
        val sessions: MutableList<Session> = mutableListOf(),
        val events: MutableList<Event> = mutableListOf(),
        val tasks: MutableList<Task> = mutableListOf(),
        private val rules: List<KeywordRule> = emptyList(),
        val tags: MutableList<Tag> = rules.flatMap { it.tagIds }.distinct().map {
            Tag(id = it, name = "Tag $it", createdAtMillis = 0, updatedAtMillis = 0)
        }.toMutableList(),
    ) : RidoCoreRepository {
        private var nextSessionId = (sessions.maxOfOrNull { it.id } ?: 0) + 1
        var genericSessionReads = 0
        var activitySessionReads = 0
        var napSessionReads = 0

        override fun observeDataChanges() = flowOf(Unit)
        override fun observeActiveSession() = flowOf(
            sessions.singleOrNull { it.temporalType == SessionTemporalType.INTERVAL && it.endAtMillis == null }?.let(::TaggedSession),
        )

        override suspend fun startSession(session: Session, tagIds: Set<Long>): DomainResult<Long> {
            val active = sessions.filter { it.temporalType == SessionTemporalType.INTERVAL && it.endAtMillis == null }
            if (active.size > 1) return DomainResult.Failure(DomainError.DataIntegrity("active"))
            val current = active.singleOrNull()
            if (current != null && session.startAtMillis <= current.startAtMillis) {
                return DomainResult.Failure(DomainError.InvalidTime)
            }
            current?.let {
                sessions[sessions.indexOf(it)] = it.copy(endAtMillis = session.startAtMillis)
            }
            val id = nextSessionId++
            sessions += session.copy(id = id, endAtMillis = null)
            return DomainResult.Success(id)
        }

        override suspend fun stopActiveSession(endAtMillis: Long): DomainResult<Session> {
            val active = sessions.singleOrNull { it.temporalType == SessionTemporalType.INTERVAL && it.endAtMillis == null }
                ?: return DomainResult.Failure(DomainError.NoActiveSession)
            if (endAtMillis <= active.startAtMillis) return DomainResult.Failure(DomainError.InvalidTime)
            val stopped = active.copy(endAtMillis = endAtMillis)
            sessions[sessions.indexOf(active)] = stopped
            return DomainResult.Success(stopped)
        }

        override suspend fun stopSessionAndCompleteTask(endAtMillis: Long): DomainResult<Session> {
            val stopped = stopActiveSession(endAtMillis)
            if (stopped is DomainResult.Success) {
                stopped.value.taskId?.let { completeTask(it, endAtMillis) }
            }
            return stopped
        }

        override suspend fun createPastSession(session: Session, tagIds: Set<Long>): DomainResult<Long> {
            val end = session.endAtMillis ?: if (session.temporalType == SessionTemporalType.POINT) session.startAtMillis else return DomainResult.Failure(DomainError.InvalidTime)
            if (session.temporalType == SessionTemporalType.INTERVAL && end <= session.startAtMillis) return DomainResult.Failure(DomainError.InvalidTime)
            if (sessions.any { it.temporalType == SessionTemporalType.INTERVAL && session.temporalType == SessionTemporalType.INTERVAL && DataValidator.sessionsOverlap(it.startAtMillis, it.endAtMillis, session.startAtMillis, end) }) {
                return DomainResult.Failure(DomainError.SessionOverlap)
            }
            val id = nextSessionId++
            sessions += session.copy(id = id)
            return DomainResult.Success(id)
        }

        override suspend fun updateSession(session: Session, tagIds: Set<Long>): DomainResult<Unit> {
            val index = sessions.indexOfFirst { it.id == session.id }
            if (index < 0) return DomainResult.Failure(DomainError.NotFound("Session", session.id))
            val end = session.endAtMillis
            if (sessions.any {
                    it.id != session.id && it.temporalType == SessionTemporalType.INTERVAL && session.temporalType == SessionTemporalType.INTERVAL && DataValidator.sessionsOverlap(
                        it.startAtMillis, it.endAtMillis, session.startAtMillis, end,
                    )
                }
            ) return DomainResult.Failure(DomainError.SessionOverlap)
            sessions[index] = session
            return DomainResult.Success(Unit)
        }

        override suspend fun deleteSession(sessionId: Long): DomainResult<Unit> {
            val removed = sessions.removeAll { it.id == sessionId }
            return if (removed) DomainResult.Success(Unit) else {
                DomainResult.Failure(DomainError.NotFound("Session", sessionId))
            }
        }

        override suspend fun completeTask(taskId: Long, completedAtMillis: Long): DomainResult<Unit> {
            val index = tasks.indexOfFirst { it.id == taskId }
            if (index < 0) return DomainResult.Failure(DomainError.NotFound("Task", taskId))
            tasks[index] = tasks[index].copy(
                status = TaskStatus.DONE,
                completionSource = TaskCompletionSource.MANUAL,
                completedAtMillis = completedAtMillis,
            )
            return DomainResult.Success(Unit)
        }

        override suspend fun reopenTask(taskId: Long, updatedAtMillis: Long): DomainResult<Unit> {
            val index = tasks.indexOfFirst { it.id == taskId }
            if (index < 0) return DomainResult.Failure(DomainError.NotFound("Task", taskId))
            tasks[index] = tasks[index].copy(
                status = TaskStatus.IN_PROGRESS,
                completionSource = null,
                completedAtMillis = null,
            )
            return DomainResult.Success(Unit)
        }

        override suspend fun getNightSleeps(): List<Session> = sessions
        override suspend fun getSessions(rangeStartMillis: Long, rangeEndMillis: Long): List<TaggedSession> {
            genericSessionReads++
            return sessions.map(::TaggedSession)
        }
        override suspend fun getCompletedActivityIntervalsInRange(rangeStartMillis: Long, rangeEndMillis: Long) =
            sessions.filter { it.kind == io.github.peanutpersimmon.rido.domain.model.SessionKind.ACTIVITY &&
                it.temporalType == SessionTemporalType.INTERVAL && it.endAtMillis != null &&
                it.startAtMillis < rangeEndMillis && it.endAtMillis > rangeStartMillis }.map(::TaggedSession)
                .also { activitySessionReads++ }
        override suspend fun getActivityFactsInRange(rangeStartMillis: Long, rangeEndMillis: Long) =
            sessions.filter { it.kind == io.github.peanutpersimmon.rido.domain.model.SessionKind.ACTIVITY &&
                if (it.temporalType == SessionTemporalType.POINT)
                    it.startAtMillis >= rangeStartMillis && it.startAtMillis < rangeEndMillis
                else it.endAtMillis != null && it.startAtMillis < rangeEndMillis && it.endAtMillis > rangeStartMillis
            }.map(::TaggedSession)
        override suspend fun getNapsInRange(rangeStartMillis: Long, rangeEndMillis: Long) =
            sessions.filter { it.kind == io.github.peanutpersimmon.rido.domain.model.SessionKind.NAP &&
                it.temporalType == SessionTemporalType.INTERVAL && it.startAtMillis < rangeEndMillis &&
                (it.endAtMillis ?: Long.MAX_VALUE) > rangeStartMillis }.map(::TaggedSession)
                .also { napSessionReads++ }
        override suspend fun getEvents(
            firstEpochDay: Long,
            lastEpochDay: Long,
            rangeStartMillis: Long,
            rangeEndMillis: Long,
        ) = events.map(::TaggedEvent)
        override suspend fun getTasks(
            firstEpochDay: Long,
            lastEpochDay: Long,
            rangeStartMillis: Long,
            rangeEndMillis: Long,
        ) = tasks.map(::TaggedTask)
        override suspend fun getEnabledKeywordRules(): List<KeywordRule> = rules
        override suspend fun getCategories() = emptyList<io.github.peanutpersimmon.rido.domain.model.Category>()
        override suspend fun getTags() = tags
        override suspend fun getTask(taskId: Long) = tasks.firstOrNull { it.id == taskId }?.let(::TaggedTask)
        override suspend fun getEvent(eventId: Long) = events.firstOrNull { it.id == eventId }?.let(::TaggedEvent)
        override suspend fun getEventsByIds(eventIds: Set<Long>) = events.filter { it.id in eventIds }
        override suspend fun getSession(sessionId: Long) = sessions.firstOrNull { it.id == sessionId }?.let(::TaggedSession)
        override suspend fun getAllTasks(): List<Task> = tasks
        override suspend fun upsertTask(task: Task, tagIds: Set<Long>): DomainResult<Long> {
            val id = task.id.takeIf { it != 0L } ?: ((tasks.maxOfOrNull { it.id } ?: 0L) + 1L)
            tasks.removeAll { it.id == id }; tasks += task.copy(id = id)
            return DomainResult.Success(id)
        }
        override suspend fun upsertEvent(event: Event, tagIds: Set<Long>): DomainResult<Long> {
            val id = event.id.takeIf { it != 0L } ?: ((events.maxOfOrNull { it.id } ?: 0L) + 1L)
            events.removeAll { it.id == id }; events += event.copy(id = id)
            return DomainResult.Success(id)
        }
        override suspend fun deleteTask(taskId: Long): DomainResult<Unit> =
            if (tasks.removeAll { it.id == taskId }) DomainResult.Success(Unit)
            else DomainResult.Failure(DomainError.NotFound("Task", taskId))
        override suspend fun deleteEvent(eventId: Long): DomainResult<DeletedEventSnapshot> {
            val event = events.firstOrNull { it.id == eventId }
                ?: return DomainResult.Failure(DomainError.NotFound("Event", eventId))
            events.remove(event)
            return DomainResult.Success(DeletedEventSnapshot(TaggedEvent(event)))
        }
        override suspend fun restoreEvent(snapshot: DeletedEventSnapshot): DomainResult<Unit> {
            events += snapshot.record.event
            return DomainResult.Success(Unit)
        }
        override suspend fun deleteSessionWithSnapshot(sessionId: Long): DomainResult<DeletedSessionSnapshot> {
            val session = sessions.firstOrNull { it.id == sessionId }
                ?: return DomainResult.Failure(DomainError.NotFound("Session", sessionId))
            sessions.remove(session)
            return DomainResult.Success(DeletedSessionSnapshot(TaggedSession(session)))
        }
        override suspend fun restoreSession(snapshot: DeletedSessionSnapshot): DomainResult<Unit> {
            sessions += snapshot.record.session
            return DomainResult.Success(Unit)
        }
    }
}
