package io.github.peanutpersimmon.rido.presentation.today

/** Future: replace these application defaults with Settings DataStore values. */
data class DefaultAwakeTimeConfig(
    val wakeMinute: Int,
    val sleepMinute: Int,
) {
    companion object {
        val Standard = DefaultAwakeTimeConfig(
            wakeMinute = 7 * 60,
            sleepMinute = 22 * 60,
        )
    }
}
