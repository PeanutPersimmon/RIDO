package io.github.peanutpersimmon.rido.domain.service

import io.github.peanutpersimmon.rido.domain.model.Task
import io.github.peanutpersimmon.rido.domain.model.TaskCompletionSource
import io.github.peanutpersimmon.rido.domain.model.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TaskHierarchyPolicyTest {
    @Test
    fun allChildrenDone_completesParentFromChildren() {
        val result = TaskHierarchyPolicy.reconcileParent(
            parent = task(id = 1, status = TaskStatus.IN_PROGRESS),
            children = listOf(task(id = 2, status = TaskStatus.DONE), task(id = 3, status = TaskStatus.DONE)),
            nowMillis = 99,
        )

        assertEquals(TaskStatus.DONE, result.status)
        assertEquals(TaskCompletionSource.CHILDREN, result.completionSource)
        assertEquals(99L, result.completedAtMillis)
    }

    @Test
    fun reopenedChild_reopensAutomaticallyCompletedParent() {
        val parent = task(
            id = 1,
            status = TaskStatus.DONE,
            completionSource = TaskCompletionSource.CHILDREN,
            completedAtMillis = 50,
        )
        val result = TaskHierarchyPolicy.reconcileParent(
            parent = parent,
            children = listOf(task(id = 2, status = TaskStatus.IN_PROGRESS)),
            nowMillis = 100,
        )

        assertEquals(TaskStatus.IN_PROGRESS, result.status)
        assertNull(result.completionSource)
        assertNull(result.completedAtMillis)
    }

    @Test
    fun reopenedChild_doesNotChangeManuallyCompletedParent() {
        val parent = task(
            id = 1,
            status = TaskStatus.DONE,
            completionSource = TaskCompletionSource.MANUAL,
            completedAtMillis = 50,
        )
        val result = TaskHierarchyPolicy.reconcileParent(
            parent = parent,
            children = listOf(task(id = 2, status = TaskStatus.TODO)),
            nowMillis = 100,
        )

        assertEquals(parent, result)
    }

    private fun task(
        id: Long,
        status: TaskStatus,
        completionSource: TaskCompletionSource? = null,
        completedAtMillis: Long? = null,
    ) = Task(
        id = id,
        title = "Task $id",
        status = status,
        createdAtMillis = 1,
        updatedAtMillis = 1,
        completionSource = completionSource,
        completedAtMillis = completedAtMillis,
    )
}

