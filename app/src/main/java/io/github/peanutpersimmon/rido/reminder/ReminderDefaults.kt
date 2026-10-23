package io.github.peanutpersimmon.rido.reminder

import android.content.Context

data class ReminderDefaults(
    val offsetMinutes: Int = 10,
    val dueDayMinuteOfDay: Int = 9 * 60,
) {
    companion object {
        private const val PREFS = "reminder_defaults"
        fun read(context: Context): ReminderDefaults {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            return ReminderDefaults(
                prefs.getInt("offset_minutes", 10).coerceIn(0, 1440),
                prefs.getInt("due_day_minute", 9 * 60).coerceIn(0, 1439),
            )
        }
        fun write(context: Context, value: ReminderDefaults) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putInt("offset_minutes", value.offsetMinutes)
                .putInt("due_day_minute", value.dueDayMinuteOfDay).apply()
        }
    }
}
