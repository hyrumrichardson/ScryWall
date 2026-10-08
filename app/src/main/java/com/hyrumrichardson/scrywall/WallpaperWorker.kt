package com.hyrumrichardson.scrywall

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Background job that swaps the wallpaper on the user's chosen schedule. */
class WallpaperWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        WallpaperSetter.changeNow(applicationContext)
        Result.success()
    } catch (e: IOException) {
        if (runAttemptCount < 3) Result.retry() else Result.failure()
    } catch (e: Exception) {
        Result.failure()
    }

    companion object {
        private const val WORK_NAME = "scrywall-rotation"

        /** Starts (or restarts) rotation. The first change after this happens one interval from now. */
        fun schedule(context: Context, interval: Interval) {
            val wm = WorkManager.getInstance(context)
            if (interval == Interval.NEVER) {
                wm.cancelUniqueWork(WORK_NAME)
                return
            }
            val request = PeriodicWorkRequestBuilder<WallpaperWorker>(interval.minutes, TimeUnit.MINUTES)
                .setInitialDelay(interval.minutes, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            wm.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
