package io.github.peanutpersimmon.rido.domain.usecase

import io.github.peanutpersimmon.rido.domain.model.DomainResult
import io.github.peanutpersimmon.rido.domain.model.Session
import io.github.peanutpersimmon.rido.domain.repository.RidoCoreRepository
import java.time.ZoneId

class FinishSleepCheckInUseCase(
    private val repository: RidoCoreRepository,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val zoneId: () -> ZoneId = ZoneId::systemDefault,
) {
    suspend operator fun invoke(): DomainResult<Session> =
        repository.finishSleepCheckIn(
            endAtMillis = nowMillis(),
            zoneId = zoneId(),
        )
}
