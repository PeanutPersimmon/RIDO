package io.github.peanutpersimmon.rido.presentation.editor.task

import io.github.peanutpersimmon.rido.domain.model.*
import io.github.peanutpersimmon.rido.domain.repository.RidoCoreRepository
import io.github.peanutpersimmon.rido.domain.usecase.*
import io.github.peanutpersimmon.rido.presentation.time.CurrentTimeProvider
import java.lang.reflect.Proxy
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TaskEditorSaveTest {
    private val date = LocalDate.of(2026, 1, 1)
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun dateOnlyPlanSavesWithoutTime() = runTest {
        val repository = RecordingRepository()
        val editor = editor(repository)
        advanceUntilIdle()
        editor.update { it.copy(title = "任务", plannedDate = date) }
        editor.save { }
        advanceUntilIdle()
        assertEquals(date.toEpochDay(), repository.saved!!.plannedDateEpochDay)
        assertNull(repository.saved!!.plannedStartMinuteOfDay)
    }

    @Test fun timedPlanSavesAsOnePointWhileDueAndEstimateRemainMetadata() = runTest {
        val repository = RecordingRepository()
        val editor = editor(repository)
        advanceUntilIdle()
        editor.update { it.copy(title = "任务", plannedDate = date, plannedStart = LocalTime.of(14, 0),
            dueDate = date.plusDays(1), dueTime = LocalTime.of(18, 0),
            estimatedHours = "2") }
        editor.save { }
        advanceUntilIdle()
        val saved = repository.saved!!
        assertEquals(840, saved.plannedStartMinuteOfDay)
        assertEquals(1080, saved.dueMinuteOfDay)
        assertEquals(120, saved.estimatedDurationMinutes)
    }

    @Test fun clearingStartLeavesDateDueAndEstimate() = runTest {
        val repository = RecordingRepository()
        val editor = editor(repository)
        advanceUntilIdle()
        editor.update { it.copy(title = "任务", plannedDate = date, plannedStart = LocalTime.of(14, 0),
            dueDate = date.plusDays(1), estimatedHours = "1") }
        editor.clearPlannedStart()
        editor.save { }
        advanceUntilIdle()
        val saved = repository.saved!!
        assertNull(saved.plannedStartMinuteOfDay)
        assertEquals(date.toEpochDay(), saved.plannedDateEpochDay)
        assertEquals(date.plusDays(1).toEpochDay(), saved.dueDateEpochDay)
        assertEquals(60, saved.estimatedDurationMinutes)
    }

    @Test fun plannedAndDueRemindersRoundTripFromEditor() = runTest {
        val repository = RecordingRepository()
        val editor = editor(repository)
        advanceUntilIdle()
        editor.update { it.copy(title = "任务", plannedDate = date,
            plannedStart = LocalTime.of(10, 0), plannedReminderOffsetMinutes = 10,
            dueDate = date, dueTime = LocalTime.of(12, 0), dueReminderOffsetMinutes = 30) }
        editor.save { }
        advanceUntilIdle()
        assertEquals(10, repository.saved!!.plannedReminderOffsetMinutes)
        assertEquals(30, repository.saved!!.dueReminderOffsetMinutes)
    }

    @Test fun manualLocationCreateEditAndClearRoundTrip() = runTest {
        val repository = RecordingRepository()
        val creator = editor(repository)
        advanceUntilIdle()
        creator.update { it.copy(title = "取报告", location = " 医院 ") }
        creator.save { }
        advanceUntilIdle()
        assertEquals("医院", repository.saved!!.location)

        val editRepository = RecordingRepository(repository.saved!!.copy(id = 8))
        val editViewModel = editor(editRepository)
        advanceUntilIdle()
        editViewModel.load(8)
        advanceUntilIdle()
        assertEquals("医院", editViewModel.uiState.value.location)
        editViewModel.update { it.copy(location = "实验室") }
        editViewModel.save { }
        advanceUntilIdle()
        assertEquals("实验室", editRepository.saved!!.location)

        val clearRepository = RecordingRepository(editRepository.saved!!.copy(id = 8))
        val clearer = editor(clearRepository)
        advanceUntilIdle()
        clearer.load(8)
        advanceUntilIdle()
        clearer.update { it.copy(location = "   ") }
        clearer.save { }
        advanceUntilIdle()
        assertNull(clearRepository.saved!!.location)
    }

    @Test fun deletingClockTimesClearsReminderValuesOnSave() = runTest {
        val repository = RecordingRepository()
        val editor = editor(repository)
        advanceUntilIdle()
        editor.update { it.copy(title = "任务", plannedDate = date,
            plannedStart = LocalTime.of(10, 0), plannedReminderOffsetMinutes = 10,
            dueDate = date, dueTime = LocalTime.of(12, 0), dueReminderOffsetMinutes = 30) }
        editor.update { it.copy(plannedStart = null, dueTime = null) }
        editor.save { }
        advanceUntilIdle()
        assertNull(repository.saved!!.plannedReminderOffsetMinutes)
        assertNull(repository.saved!!.dueReminderOffsetMinutes)
    }

    @Test fun editingExistingTaskKeepsBothReminderAnchors() = runTest {
        val existing = Task(id = 8, title = "旧任务", plannedDateEpochDay = date.toEpochDay(),
            plannedStartMinuteOfDay = 600, plannedReminderOffsetMinutes = 10,
            dueDateEpochDay = date.toEpochDay(), dueMinuteOfDay = 720,
            dueReminderOffsetMinutes = 30, createdAtMillis = 1, updatedAtMillis = 1)
        val repository = RecordingRepository(existing)
        val editor = editor(repository)
        advanceUntilIdle()
        editor.load(8)
        advanceUntilIdle()
        assertEquals(10, editor.uiState.value.plannedReminderOffsetMinutes)
        assertEquals(30, editor.uiState.value.dueReminderOffsetMinutes)
        editor.update { it.copy(title = "新任务") }
        editor.save { }
        advanceUntilIdle()
        assertEquals(10, repository.saved!!.plannedReminderOffsetMinutes)
        assertEquals(30, repository.saved!!.dueReminderOffsetMinutes)
    }

    private fun editor(repository: RecordingRepository): TaskEditorViewModel =
        TaskEditorViewModel(GetTaskForEditorUseCase(repository.core), GetEditorOptionsUseCase(repository.core),
            SaveTaskUseCase(repository.core), DeleteTaskUseCase(repository.core),
            CompleteTaskUseCase(repository.core), ReopenTaskUseCase(repository.core),
            CurrentTimeProvider { 1000L }).also { it.load(null) }

    private class RecordingRepository(private val initial: Task? = null) {
        var saved: Task? = null
        val core: RidoCoreRepository = Proxy.newProxyInstance(
            RidoCoreRepository::class.java.classLoader,
            arrayOf(RidoCoreRepository::class.java),
        ) { _, method, args ->
            when (method.name) {
                "getAllTasks", "getCategories", "getTags" -> emptyList<Any>()
                "getTask" -> initial?.takeIf { it.id == args!![0] as Long }?.let { TaggedTask(it) }
                "upsertTask" -> {
                    saved = (args!![0] as Task).copy(id = 1)
                    DomainResult.Success(1L)
                }
                else -> error("Unexpected repository call: ${method.name}")
            }
        } as RidoCoreRepository
    }
}

