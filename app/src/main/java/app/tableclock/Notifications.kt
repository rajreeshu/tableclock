package app.tableclock

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat

object Notifications {
    const val WATCH = "watch"
    const val CLOCK = "clock"

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val watch = NotificationChannel(
            WATCH,
            "Listening",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Quiet notice while Table Clock is waiting for the charger"
            setShowBadge(false)
        }
        val clock = NotificationChannel(
            CLOCK,
            "Table clock",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Wakes the screen when the phone is held on the charger"
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setSound(null, null)
            enableVibration(false)
        }
        manager.createNotificationChannel(watch)
        manager.createNotificationChannel(clock)
    }

    fun watching(context: Context, tap: PendingIntent): Notification {
        return NotificationCompat.Builder(context, WATCH)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("Table Clock")
            .setContentText("Waiting for the phone to lie on its side")
            .setContentIntent(tap)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    fun clock(context: Context, fullScreen: PendingIntent): Notification {
        return NotificationCompat.Builder(context, CLOCK)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("Table clock")
            .setContentText("On the charger")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(fullScreen, true)
            .setContentIntent(fullScreen)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }
}
