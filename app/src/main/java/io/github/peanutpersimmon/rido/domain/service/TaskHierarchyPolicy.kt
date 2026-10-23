package io.github.peanutpersimmon.rido.domain.service

import io.github.peanutpersimmon.rido.domain.model.Task
import io.github.peanutpersimmon.rido.domain.model.TaskCompletionSource
import io.github.peanutpersimmon.rido.domain.model.TaskStatus

object TaskHierarchyPolicy {
    fun reconcileParent(
        parent: Task,
        children: List<Task>,
        nowMillis: Long,
    ): Task {
        if (children.isEmpty()) return parent
        val allChildrenDone = children.all { it.status == TaskStatus.DONE }

        return when {
            allChildrenDone && parent.status != TaskStatus.DONE && parent.status != TaskStatus.CANCELLED ->
                parent.copy(
                    status = TaskStatus.DONE,
                    completedAtMillis = nowMillis,
                    completionSource = TaskCompletionSource.CHILDREN,
                    updatedAtMillis = nowMillis,
                )

            !allChildrenDone &&
                parent.status == TaskStatus.DONE &&
                parent.completionSource == TaskCompletionSource.CHILDREN ->
                parent.copy(
                    status = TaskStatus.IN_PROGRESS,
                    completedAtMillis = null,
                    completionSource = null,
                    updatedAtMillis = nowMillis,
                )

            else -> parent
        }
    }
}

