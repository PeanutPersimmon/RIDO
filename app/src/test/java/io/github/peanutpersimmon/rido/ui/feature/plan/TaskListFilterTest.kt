package io.github.peanutpersimmon.rido.ui.feature.plan

import io.github.peanutpersimmon.rido.domain.model.Task
import io.github.peanutpersimmon.rido.domain.model.TaskCompletionSource
import io.github.peanutpersimmon.rido.domain.model.TaskStatus
import org.junit.Assert.*
import org.junit.Test

class TaskListFilterTest {
    private fun task(id: Long, status: TaskStatus, parent: Long? = null,
        source: TaskCompletionSource? = null) = Task(id = id, title = "任务$id", status = status,
        parentTaskId = parent, completionSource = source, createdAtMillis = 0,
        updatedAtMillis = 0)

    @Test fun filtersIncludeOnlyExpectedStatuses() {
        val tasks = listOf(task(1, TaskStatus.TODO), task(2, TaskStatus.IN_PROGRESS),
            task(3, TaskStatus.DONE), task(4, TaskStatus.CANCELLED))
        fun ids(filter: TaskListFilter) = taskGroups(tasks, filter).flatten().map { it.task.id }.toSet()
        assertEquals(setOf(1L, 2L), ids(TaskListFilter.UNFINISHED))
        assertEquals(setOf(3L), ids(TaskListFilter.DONE))
        assertEquals(setOf(1L, 2L, 3L), ids(TaskListFilter.ALL))
    }

    @Test fun filteredParentPromotesChildWithContext() {
        val doneParent = task(1, TaskStatus.DONE)
        val activeChild = task(2, TaskStatus.IN_PROGRESS, 1)
        val unfinished = taskGroups(listOf(doneParent, activeChild), TaskListFilter.UNFINISHED)
        assertEquals(listOf(2L), unfinished.flatten().map { it.task.id })
        assertEquals(0, unfinished.single().single().depth)
        assertEquals(doneParent.title, unfinished.single().single().parentContextTitle)

        val activeParent = task(3, TaskStatus.TODO)
        val doneChild = task(4, TaskStatus.DONE, 3)
        val done = taskGroups(listOf(activeParent, doneChild), TaskListFilter.DONE)
        assertEquals(listOf(4L), done.flatten().map { it.task.id })
        assertEquals(activeParent.title, done.single().single().parentContextTitle)
    }

    @Test fun childrenCompletedParentRemainsInDoneFilter() {
        val parent = task(1, TaskStatus.DONE, source = TaskCompletionSource.CHILDREN)
        val child = task(2, TaskStatus.DONE, parent = 1)
        val rows = taskGroups(listOf(parent, child), TaskListFilter.DONE).single()
        assertEquals(listOf(1L, 2L), rows.map { it.task.id })
        assertEquals(listOf(0, 1), rows.map { it.depth })
    }
}
