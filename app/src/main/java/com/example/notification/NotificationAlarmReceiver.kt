package com.example.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * NotificationAlarmReceiver handles periodic background wakeups to fetch notifications
 * from Hostinger even when the app is completely closed and removed from recent apps.
 */
class NotificationAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = powerManager?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "CineArena::NotificationWakeLock"
        )
        wakeLock?.acquire(20000L) // Hold wake lock for at most 20 seconds

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d("NotifyAlarmReceiver", "Waking up in background to check cinexcricket.com notification")
                HostingerNotificationManager.checkAndDisplayNewNotifications(context.applicationContext)
            } catch (e: Exception) {
                Log.e("NotifyAlarmReceiver", "Error checking background notification", e)
            } finally {
                scheduleNextAlarm(context.applicationContext)
                try {
                    if (wakeLock?.isHeld == true) {
                        wakeLock.release()
                    }
                } catch (_: Exception) {}
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val REQUEST_CODE = 9021
        private const val INTERVAL_MS = 10_000L // Fast 10-second interval when in background

        fun scheduleAlarm(context: Context) {
            scheduleAlarmAt(context, System.currentTimeMillis() + 3_000L)
        }

        fun scheduleNextAlarm(context: Context) {
            scheduleAlarmAt(context, System.currentTimeMillis() + INTERVAL_MS)
        }

        private fun scheduleAlarmAt(context: Context, triggerAtMillis: Long) {
            try {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
                val intent = Intent(context, NotificationAlarmReceiver::class.java)
                val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                } else {
                    PendingIntent.FLAG_UPDATE_CURRENT
                }
                val pendingIntent = PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    val info = AlarmManager.AlarmClockInfo(triggerAtMillis, pendingIntent)
                    alarmManager.setAlarmClock(info, pendingIntent)
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                } else {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                }
                Log.d("NotifyAlarmReceiver", "Scheduled alarm at $triggerAtMillis")
            } catch (e: Exception) {
                Log.e("NotifyAlarmReceiver", "Could not schedule alarm: ${e.message}")
            }
        }
    }
}
