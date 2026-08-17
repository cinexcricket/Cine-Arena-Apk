package com.example.notification

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * NotificationWorker runs in background via Android WorkManager to fetch
 * and display push notifications in the device status bar even when the app is completely closed.
 */
class NotificationWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        Log.d(TAG, "NotificationWorker running in background...")
        return try {
            HostingerNotificationManager.checkAndDisplayNewNotifications(applicationContext)
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error in NotificationWorker: ${e.message}", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "NotificationWorker"
        private const val UNIQUE_WORK_NAME = "cine_arena_background_notification_work"

        fun enqueuePeriodicWork(context: Context) {
            try {
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

                val workRequest = PeriodicWorkRequestBuilder<NotificationWorker>(
                    15, TimeUnit.MINUTES,
                    5, TimeUnit.MINUTES
                )
                    .setConstraints(constraints)
                    .build()

                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    UNIQUE_WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    workRequest
                )
                Log.d(TAG, "Successfully enqueued PeriodicWork for background notifications")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to enqueue WorkManager work: ${e.message}", e)
            }
        }
    }
}
