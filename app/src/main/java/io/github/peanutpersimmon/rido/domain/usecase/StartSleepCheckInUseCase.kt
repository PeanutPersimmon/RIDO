package io.github.peanutpersimmon.rido.domain.usecase

import io.github.peanutpersimmon.rido.domain.model.DomainResult
import io.github.peanutpersimmon.rido.domain.model.SessionKind
import io.github.peanutpersimmon.rido.domain.repository.RidoCoreRepository

class StartSleepCheckInUseCase(
    repository: RidoCoreRepository,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    private val createSleepSession = CreateSleepSessionUseCase(repository)

    suspend operator fun invoke(): DomainResult<Long> {
        val now = nowMillis()
        return createSleepSession.start(
            NewSessionInput(
                title = NIGHT_SLEEP_TITLE,
                startAtMillis = now,
                endAtMillis = null,
                taskId = null,
                categoryId = null,
                tagIds = emptySet(),
                note = null,
                location = null,
                kind = SessionKind.NIGHT_SLEEP,
                createdAtMillis = now,
                sourceEventId = null,
            ),
        )
    }

    private companion object {
        const val NIGHT_SLEEP_TITLE = "夜间睡眠"
    }
}
