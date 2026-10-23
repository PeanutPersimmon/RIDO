package io.github.peanutpersimmon.rido.domain.usecase

import io.github.peanutpersimmon.rido.domain.model.DomainResult
import io.github.peanutpersimmon.rido.domain.repository.RidoCoreRepository

class CompleteTaskUseCase(private val repository: RidoCoreRepository) {
    suspend operator fun invoke(taskId: Long, completedAtMillis: Long): DomainResult<Unit> =
        repository.completeTask(taskId, completedAtMillis)
}

class ReopenTaskUseCase(private val repository: RidoCoreRepository) {
    suspend operator fun invoke(taskId: Long, updatedAtMillis: Long): DomainResult<Unit> =
        repository.reopenTask(taskId, updatedAtMillis)
}


