package io.github.peanutpersimmon.rido.presentation.time

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive

fun interface CurrentTimeProvider {
    fun nowMillis(): Long
}

object SystemCurrentTimeProvider : CurrentTimeProvider {
    override fun nowMillis(): Long = System.currentTimeMillis()
}

/** Re-reads wall time after every aligned delay; elapsed time never depends on tick count. */
fun alignedTicks(
    timeProvider: CurrentTimeProvider,
    periodMillis: Long,
): Flow<Long> = flow {
    require(periodMillis > 0)
    while (currentCoroutineContext().isActive) {
        val now = timeProvider.nowMillis()
        emit(now)
        val remainder = Math.floorMod(now, periodMillis)
        delay(if (remainder == 0L) periodMillis else periodMillis - remainder)
    }
}

fun alignedSecondTicks(timeProvider: CurrentTimeProvider): Flow<Long> =
    alignedTicks(timeProvider, 1_000L)
