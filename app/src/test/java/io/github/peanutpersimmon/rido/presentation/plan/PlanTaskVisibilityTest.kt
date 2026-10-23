package io.github.peanutpersimmon.rido.presentation.plan

import io.github.peanutpersimmon.rido.domain.model.Task
import io.github.peanutpersimmon.rido.domain.model.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanTaskVisibilityTest {
    private val todo = task(1, TaskStatus.TODO)
    private val inProgress = task(2, TaskStatus.IN_PROGRESS)
    private val done = task(3, TaskStatus.DONE)
    private val cancelled = task(4, TaskStatus.CANCELLED)
    private val tasks = listOf(todo, inProgress, done, cancelled)

    @Test fun boardExcludesCancelledTasks() {
        assertVisibleStates(PlanViewMode.BOARD, MonthContent.PLAN)
    }

    @Test fun todoDoneStateExcludesCancelledTasks() {
        assertVisibleStates(PlanViewMode.TODO_DONE, MonthContent.PLAN)
    }

    @Test fun monthPlanExcludesCancelledTasks() {
        assertVisibleStates(PlanViewMode.MONTH, MonthContent.PLAN)
    }

    @Test fun taskListExcludesCancelledTasks() {
        assertVisibleStates(PlanViewMode.TASK_LIST, MonthContent.PLAN)
    }

    @Test fun monthRecordDoesNotLoadPlanTasks() {
        assertTrue(planTasksForView(PlanViewMode.MONTH, MonthContent.RECORD, tasks).isEmpty())
    }

    private fun assertVisibleStates(view: PlanViewMode, content: MonthContent) {
        assertEquals(
            listOf(TaskStatus.TODO, TaskStatus.IN_PROGRESS, TaskStatus.DONE),
            planTasksForView(view, content, tasks).map { it.status },
        )
    }

    private fun task(id: Long, status: TaskStatus) = Task(
        id = id,
        title = "Task $id",
        status = status,
        createdAtMillis = 0,
        updatedAtMillis = 0,
    )
}
