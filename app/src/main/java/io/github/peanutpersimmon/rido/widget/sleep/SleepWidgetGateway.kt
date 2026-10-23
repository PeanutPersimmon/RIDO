package io.github.peanutpersimmon.rido.widget.sleep

import android.content.Context
import io.github.peanutpersimmon.rido.data.local.room.RidoDatabase
import io.github.peanutpersimmon.rido.data.repository.RoomRidoRepository
import io.github.peanutpersimmon.rido.domain.model.DomainResult
import io.github.peanutpersimmon.rido.domain.model.Session
import io.github.peanutpersimmon.rido.domain.usecase.FinishSleepCheckInUseCase
import io.github.peanutpersimmon.rido.domain.usecase.StartSleepCheckInUseCase

class SleepWidgetGateway(context: Context) {
    private val applicationContext = context.applicationContext
    private val repository = RoomRidoRepository(RidoDatabase.getInstance(applicationContext))
    private val startSleepCheckIn = StartSleepCheckInUseCase(repository)
    private val finishSleepCheckIn = FinishSleepCheckInUseCase(repository)

    suspend fun sleepCheckIn(): DomainResult<Long> = startSleepCheckIn()

    suspend fun wakeCheckIn(): DomainResult<Session> = finishSleepCheckIn()
}
