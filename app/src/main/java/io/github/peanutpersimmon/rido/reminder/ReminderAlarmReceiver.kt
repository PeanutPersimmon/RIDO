package io.github.peanutpersimmon.rido.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import io.github.peanutpersimmon.rido.data.local.room.RidoDatabase
import io.github.peanutpersimmon.rido.data.mapper.toDomain
import io.github.peanutpersimmon.rido.backup.BackupOperationCoordinator
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                BackupOperationCoordinator.mutation {
                val key = intent.data?.lastPathSegment ?: return@mutation
                val parts = key.split(':')
                if (parts.size != 2) return@mutation
                val anchor = runCatching { ReminderAnchor.valueOf(parts[0]) }.getOrNull() ?: return@mutation
                val id = parts[1].toLongOrNull()?.takeIf { it > 0 } ?: return@mutation
                val db = RidoDatabase.getInstance(context)
                val zone = ZoneId.systemDefault()
                val defaults = ReminderDefaults.read(context)
                val current = when (anchor) {
                    ReminderAnchor.TASK_PLANNED, ReminderAnchor.TASK_DUE ->
                        db.taskDao().getById(id)?.toDomain()?.let { ReminderPlan.forTask(it, zone, defaults) }
                    ReminderAnchor.EVENT_START ->
                        db.eventDao().getById(id)?.toDomain()?.let { ReminderPlan.forEvent(it, zone, defaults) }
                }?.singleOrNull { it.anchor == anchor } ?: return@mutation
                if (!ReminderPlan.matchesDeliveredAlarm(current,
                    intent.getLongExtra(EXTRA_ANCHOR_MILLIS, Long.MIN_VALUE),
                    intent.getIntExtra(EXTRA_OFFSET, -1),
                    intent.getLongExtra(EXTRA_TRIGGER_MILLIS, Long.MIN_VALUE))) return@mutation
                if (current.triggerMillis > System.currentTimeMillis()) return@mutation
                val title = when (anchor) {
                    ReminderAnchor.TASK_PLANNED, ReminderAnchor.TASK_DUE -> db.taskDao().getById(id)?.title
                    ReminderAnchor.EVENT_START -> db.eventDao().getById(id)?.title
                } ?: return@mutation
                ReminderNotifications.show(context, current, title)
                }
            } catch (error: Exception) {
                Log.e("RidoReminder", "Alarm delivery failed", error)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val EXTRA_ANCHOR_MILLIS = "anchorMillis"
        const val EXTRA_OFFSET = "offsetMinutes"
        const val EXTRA_TRIGGER_MILLIS = "triggerMillis"
    }
}
