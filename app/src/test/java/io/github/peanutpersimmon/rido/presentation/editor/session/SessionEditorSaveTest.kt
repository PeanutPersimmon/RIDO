package io.github.peanutpersimmon.rido.presentation.editor.session

import io.github.peanutpersimmon.rido.domain.model.*
import io.github.peanutpersimmon.rido.domain.repository.RidoCoreRepository
import io.github.peanutpersimmon.rido.domain.usecase.*
import io.github.peanutpersimmon.rido.presentation.time.CurrentTimeProvider
import java.lang.reflect.Proxy
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionEditorSaveTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val date = LocalDate.of(2026, 10, 5)
    private val now = at(18)
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun occurrenceOnlyCreatesPointAndStartNowCreatesActiveInterval() = runTest {
        val repo = RecordingRepository()
        val editor = editor(repo)
        editor.load(null)
        advanceUntilIdle()
        editor.update { it.copy(title = "取快递", startDate = date, startTime = LocalTime.of(17, 26)) }
        editor.save { }
        advanceUntilIdle()
        assertEquals(SessionTemporalType.POINT, repo.created!!.temporalType)
        assertEquals(at(17, 26), repo.created!!.startAtMillis)
        assertNull(repo.created!!.endAtMillis)

        editor.load(null)
        advanceUntilIdle()
        editor.update { it.copy(title = "做实验") }
        editor.startNow { }
        advanceUntilIdle()
        assertEquals(SessionTemporalType.INTERVAL, repo.started!!.temporalType)
        assertEquals(now, repo.started!!.startAtMillis)
        assertNull(repo.started!!.endAtMillis)
    }

    @Test fun startAndEndCreateCompletedInterval() = runTest {
        val repo = RecordingRepository()
        val editor = editor(repo)
        editor.load(null)
        advanceUntilIdle()
        editor.update { it.copy(title = "做实验", startDate = date, startTime = LocalTime.of(14, 0)) }
        editor.setEnd(date, LocalTime.of(16, 0))
        editor.save { }
        advanceUntilIdle()
        assertEquals(SessionTemporalType.INTERVAL, repo.created!!.temporalType)
        assertEquals(at(16), repo.created!!.endAtMillis)
    }

    @Test fun pointLoadSaveAndAddingEndPreserveThenConvertType() = runTest {
        val repo = RecordingRepository(record = session(SessionTemporalType.POINT, null))
        val editor = editor(repo)
        editor.load(7)
        advanceUntilIdle()
        assertEquals(SessionTemporalType.POINT, editor.uiState.value.temporalType)
        assertFalse(editor.uiState.value.isActive)
        editor.save { }
        advanceUntilIdle()
        assertEquals(SessionTemporalType.POINT, repo.updated!!.temporalType)
        assertNull(repo.updated!!.endAtMillis)

        editor.load(7)
        advanceUntilIdle()
        editor.setEnd(date, LocalTime.of(16, 0))
        editor.save { }
        advanceUntilIdle()
        assertEquals(SessionTemporalType.INTERVAL, repo.updated!!.temporalType)
        assertEquals(at(16), repo.updated!!.endAtMillis)
    }

    @Test fun completedIntervalRetainsTypeAndClearingEndCreatesPoint() = runTest {
        val repo = RecordingRepository(record = session(SessionTemporalType.INTERVAL, at(16)))
        val editor = editor(repo)
        editor.load(7)
        advanceUntilIdle()
        assertFalse(editor.uiState.value.isActive)
        editor.save { }
        advanceUntilIdle()
        assertEquals(SessionTemporalType.INTERVAL, repo.updated!!.temporalType)
        assertEquals(at(16), repo.updated!!.endAtMillis)

        editor.load(7)
        advanceUntilIdle()
        editor.clearEnd()
        assertEquals(SessionTemporalType.POINT, editor.uiState.value.temporalType)
        assertFalse(editor.uiState.value.isActive)
        editor.save { }
        advanceUntilIdle()
        assertEquals(SessionTemporalType.POINT, repo.updated!!.temporalType)
        assertNull(repo.updated!!.endAtMillis)
    }

    @Test fun activeIntervalRemainsActiveWhenEndIsAbsent() = runTest {
        val repo = RecordingRepository(record = session(SessionTemporalType.INTERVAL, null))
        val editor = editor(repo)
        editor.load(7)
        advanceUntilIdle()
        assertTrue(editor.uiState.value.isActive)
        editor.setEnd(date, LocalTime.of(16, 0))
        assertFalse(editor.uiState.value.isActive)
        editor.clearEnd()
        assertTrue(editor.uiState.value.isActive)
        editor.save { }
        advanceUntilIdle()
        assertEquals(SessionTemporalType.INTERVAL, repo.updated!!.temporalType)
        assertNull(repo.updated!!.endAtMillis)
    }

    @Test fun eventPointClearsOldEndAndResetClearsAssociations() = runTest {
        val repo = RecordingRepository(event = event(15 * 60, null))
        val editor = editor(repo)
        editor.load(null)
        advanceUntilIdle()
        editor.update { it.copy(endDate = date, endTime = LocalTime.of(17, 0), taskId = 3) }
        editor.load(null, 9)
        advanceUntilIdle()
        val prefilled = editor.uiState.value
        assertEquals(LocalTime.of(15, 0), prefilled.startTime)
        assertNull(prefilled.endDate)
        assertNull(prefilled.endTime)
        assertEquals(SessionTemporalType.POINT, prefilled.temporalType)
        assertEquals(9L, prefilled.sourceEventId)
        assertNull(prefilled.taskId)
        editor.save { }
        advanceUntilIdle()
        assertEquals(9L, repo.created!!.sourceEventId)
        assertEquals(SessionTemporalType.POINT, repo.created!!.temporalType)

        editor.load(null)
        advanceUntilIdle()
        assertNull(editor.uiState.value.sourceEventId)
        assertNull(editor.uiState.value.taskId)
        assertNull(editor.uiState.value.endDate)
        assertNull(editor.uiState.value.endTime)
    }

    @Test fun eventIntervalPrefillsCompletedIntervalAndDateOnlyRequiresTimeSelection() = runTest {
        val repo = RecordingRepository(event = event(14 * 60, 15 * 60))
        val editor = editor(repo)
        editor.load(null, 9)
        advanceUntilIdle()
        assertEquals(SessionTemporalType.INTERVAL, editor.uiState.value.temporalType)
        assertEquals(LocalTime.of(14, 0), editor.uiState.value.startTime)
        assertEquals(LocalTime.of(15, 0), editor.uiState.value.endTime)
        editor.save { }
        advanceUntilIdle()
        assertEquals(SessionTemporalType.INTERVAL, repo.created!!.temporalType)
        assertEquals(9L, repo.created!!.sourceEventId)

        repo.event = event(null, null)
        editor.load(null, 9)
        advanceUntilIdle()
        assertFalse(editor.uiState.value.startTimeConfirmed)
        assertFalse(editor.uiState.value.canSavePast)
    }

    @Test fun eventIntervalStillInProgressRequiresActualEndBeforeSaving() = runTest {
        val repo = RecordingRepository(event = event(14 * 60, 16 * 60))
        val editor = editor(repo, at(15))
        editor.load(null, 9)
        advanceUntilIdle()
        val seeded = editor.uiState.value
        assertEquals(SessionTemporalType.INTERVAL, seeded.temporalType)
        assertEquals(LocalTime.of(14, 0), seeded.startTime)
        assertNull(seeded.endDate)
        assertNull(seeded.endTime)
        assertFalse(seeded.isActive)
        assertFalse(seeded.canSavePast)
        assertFalse(seeded.canStartNow)
        assertEquals(9L, seeded.sourceEventId)
        editor.save { }
        advanceUntilIdle()
        assertNull(repo.created)

        editor.setEnd(date, LocalTime.of(13, 59))
        assertFalse(editor.uiState.value.canSavePast)
        editor.setEnd(date, LocalTime.of(15, 1))
        assertFalse(editor.uiState.value.canSavePast)
        editor.setEnd(date, LocalTime.of(14, 45))
        assertTrue(editor.uiState.value.canSavePast)
        editor.save { }
        advanceUntilIdle()
        assertEquals(SessionTemporalType.INTERVAL, repo.created!!.temporalType)
        assertEquals(at(14, 45), repo.created!!.endAtMillis)
        assertEquals(9L, repo.created!!.sourceEventId)
    }

    @Test fun sleepEditorCreatesOnlyInterval() = runTest {
        val repo = RecordingRepository()
        val editor = editor(repo)
        editor.loadSleep(null)
        advanceUntilIdle()
        assertEquals(SessionTemporalType.INTERVAL, editor.uiState.value.temporalType)
        assertEquals(SessionKind.NIGHT_SLEEP, editor.uiState.value.kind)
        val endDate = requireNotNull(editor.uiState.value.endDate)
        val endTime = requireNotNull(editor.uiState.value.endTime)
        editor.clearEnd()
        assertEquals(SessionTemporalType.INTERVAL, editor.uiState.value.temporalType)
        assertFalse(editor.uiState.value.canSavePast)
        editor.setEnd(endDate, endTime)
        editor.save { }
        advanceUntilIdle()
        assertEquals(SessionTemporalType.INTERVAL, repo.created!!.temporalType)
    }

    @Test fun historicalIntervalAndPointRequireExplicitCompoundCompletion() = runTest {
        val repo = RecordingRepository(tasks = listOf(Task(id = 3, title = "看文献", status = TaskStatus.TODO,
            createdAtMillis = 0, updatedAtMillis = 0)))
        val editor = editor(repo)
        editor.load(null); advanceUntilIdle()
        editor.update { it.copy(title = "看文献", taskId = 3, startDate = date, startTime = LocalTime.of(15, 30)) }
        editor.setEnd(date, LocalTime.of(17, 30))
        assertTrue(editor.uiState.value.canSaveAndCompleteTask)
        editor.save { }; advanceUntilIdle()
        assertNull(repo.compound)
        assertEquals(SessionTemporalType.INTERVAL, repo.created?.temporalType)
        editor.load(null); advanceUntilIdle()
        editor.update { it.copy(title = "看文献", taskId = 3, startDate = date, startTime = LocalTime.of(15, 30)) }
        editor.setEnd(date, LocalTime.of(17, 30))
        editor.save(completeTask = true) { }; advanceUntilIdle()
        assertEquals(at(17, 30), repo.compound?.endAtMillis)
        assertEquals(3L, repo.compound?.taskId)

        editor.load(null); advanceUntilIdle()
        editor.update { it.copy(title = "取快递", taskId = 3, startDate = date, startTime = LocalTime.of(17, 26)) }
        editor.save(completeTask = true) { }; advanceUntilIdle()
        assertEquals(SessionTemporalType.POINT, repo.compound?.temporalType)
        assertEquals(at(17, 26), repo.compound?.startAtMillis)
        assertNull(repo.compound?.endAtMillis)
    }

    @Test fun doneTaskCanLinkHistoricalButCannotStartActiveOrOverwriteCompletion() = runTest {
        val done = Task(id = 3, title = "已完成", status = TaskStatus.DONE,
            completedAtMillis = at(12), createdAtMillis = 0, updatedAtMillis = 0)
        val repo = RecordingRepository(tasks = listOf(done))
        val editor = editor(repo)
        editor.load(null); advanceUntilIdle()
        editor.update { it.copy(title = "补录", taskId = 3, startDate = date, startTime = LocalTime.of(14, 0)) }
        assertTrue(editor.uiState.value.taskChoices.any { it.id == 3L })
        assertFalse(editor.uiState.value.canStartNow)
        assertFalse(editor.uiState.value.canSaveAndCompleteTask)
        editor.save(completeTask = true) { }; advanceUntilIdle()
        assertNull(repo.compound)
        editor.save { }; advanceUntilIdle()
        assertEquals(3L, repo.created?.taskId)
        editor.load(null); advanceUntilIdle()
        editor.update { it.copy(title = "补录", taskId = 3, startDate = date, startTime = LocalTime.of(14, 0)) }
        editor.setEnd(date, LocalTime.of(15, 0))
        editor.save { }; advanceUntilIdle()
        assertEquals(3L, repo.created?.taskId)
        assertEquals(SessionTemporalType.INTERVAL, repo.created?.temporalType)
    }

    @Test fun sleepAndUnlinkedRecordNeverOfferCompoundCompletion() = runTest {
        val editor = editor(RecordingRepository())
        editor.load(null); advanceUntilIdle()
        editor.update { it.copy(title = "记录") }
        assertFalse(editor.uiState.value.canSaveAndCompleteTask)
        editor.loadSleep(null); advanceUntilIdle()
        assertFalse(editor.uiState.value.canSaveAndCompleteTask)
    }

    @Test fun cancelledTaskIsNeverNewChoiceAndExistingDoneLinkSurvivesEdit() = runTest {
        val done = Task(id = 3, title = "后来完成", status = TaskStatus.DONE,
            completedAtMillis = at(12), createdAtMillis = 0, updatedAtMillis = 0)
        val cancelled = Task(id = 4, title = "取消", status = TaskStatus.CANCELLED,
            createdAtMillis = 0, updatedAtMillis = 0)
        val existing = session(SessionTemporalType.INTERVAL, at(16)).copy(taskId = 3)
        val repo = RecordingRepository(record = existing, tasks = listOf(done, cancelled))
        val editor = editor(repo)
        editor.load(null); advanceUntilIdle()
        assertEquals(listOf(3L), editor.uiState.value.taskChoices.map { it.id })
        editor.load(7); advanceUntilIdle()
        assertEquals(3L, editor.uiState.value.taskId)
        assertTrue(editor.uiState.value.taskChoices.any { it.id == 3L })
        editor.save { }; advanceUntilIdle()
        assertEquals(3L, repo.updated?.taskId)
    }

    private fun editor(repo: RecordingRepository, currentTime: Long = now) = SessionEditorViewModel(
        GetSessionForEditorUseCase(repo.core), GetEditorOptionsUseCase(repo.core),
        CreatePastSessionUseCase(repo.core), StartSessionUseCase(repo.core),
        SaveSessionEditUseCase(repo.core), DeleteSessionWithUndoUseCase(repo.core),
        RestoreSessionUseCase(repo.core), zoneId = zone, clock = CurrentTimeProvider { currentTime },
        getEvent = GetEventForEditorUseCase(repo.core),
        createPastAndComplete = CreatePastSessionAndCompleteTaskUseCase(repo.core),
    )

    private fun session(type: SessionTemporalType, end: Long?) = Session(
        id = 7, title = "记录", temporalType = type,
        startAtMillis = at(14), endAtMillis = end,
        createdAtMillis = at(14), updatedAtMillis = at(14),
    )

    private fun event(start: Int?, end: Int?) = TaggedEvent(Event(
        id = 9, title = "组会", dateEpochDay = date.toEpochDay(),
        startMinuteOfDay = start, endMinuteOfDay = end,
        createdAtMillis = at(9), updatedAtMillis = at(9),
    ))

    private fun at(hour: Int, minute: Int = 0) = date.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

    private class RecordingRepository(
        var record: Session? = null,
        var event: TaggedEvent? = null,
        val tasks: List<Task> = emptyList(),
    ) {
        var created: Session? = null
        var compound: Session? = null
        var updated: Session? = null
        var started: Session? = null
        val core: RidoCoreRepository = Proxy.newProxyInstance(
            RidoCoreRepository::class.java.classLoader,
            arrayOf(RidoCoreRepository::class.java),
        ) { _, method, args ->
            when (method.name) {
                "getAllTasks" -> tasks
                "getCategories", "getTags" -> emptyList<Any>()
                "getSession" -> record?.let(::TaggedSession)
                "getEvent" -> event
                "createPastSession" -> {
                    created = args!![0] as Session
                    DomainResult.Success(11L)
                }
                "createPastSessionAndCompleteTask" -> {
                    compound = args!![0] as Session
                    DomainResult.Success(13L)
                }
                "updateSession" -> {
                    updated = args!![0] as Session
                    DomainResult.Success(Unit)
                }
                "startSession", "startSessionIfIdle" -> {
                    started = args!![0] as Session
                    DomainResult.Success(12L)
                }
                else -> error("Unexpected repository call: ${method.name}")
            }
        } as RidoCoreRepository
    }
}
