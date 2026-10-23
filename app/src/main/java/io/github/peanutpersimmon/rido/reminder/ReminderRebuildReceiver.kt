package io.github.peanutpersimmon.rido.reminder

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ReminderRebuildReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        ReminderRuntime.rebuild(context.applicationContext as Application) { pending.finish() }
    }
}
