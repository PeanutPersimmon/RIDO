package io.github.peanutpersimmon.rido.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import io.github.peanutpersimmon.rido.MainActivity
import java.time.ZoneId

object ReminderNotifications {
    const val CHANNEL_ID = "rido_reminders"
    const val EXTRA_OWNER_ID = "reminderOwnerId"
    const val EXTRA_ANCHOR = "reminderAnchor"

    fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL_ID, "RIDO 提醒", NotificationManager.IMPORTANCE_DEFAULT)
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun show(context: Context, reminder: ScheduledReminder, title: String) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        createChannel(context)
        val body = ReminderNotificationContent.body(reminder, System.currentTimeMillis(), ZoneId.systemDefault())
        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            data = Uri.parse("rido-reminder://open/${reminder.key}")
            putExtra(EXTRA_OWNER_ID, reminder.ownerId)
            putExtra(EXTRA_ANCHOR, reminder.anchor.name)
        }
        val pending = PendingIntent.getActivity(context, 0, open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(reminder.key.hashCode(), notification)
    }
}
