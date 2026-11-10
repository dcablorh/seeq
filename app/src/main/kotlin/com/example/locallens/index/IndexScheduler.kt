package com.example.locallens.index

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.locallens.ml.ModelDownloadWorker
import java.util.concurrent.TimeUnit

object IndexScheduler {
  private const val NOW = "index-now"
  private const val PERIODIC = "index-periodic"
  private const val RESTART_DELAY_SECONDS = 10L

  /**
   * Indexes right away, unless the battery is low. Replaces a job that is only waiting: every time the system
   * interrupts a job WorkManager counts a failed attempt and delays the next start with growing backoff, so a
   * stale job could sit idle for an hour. The caller must not call this while indexing is already running.
   */
  fun runNow(context: Context) {
    val request = OneTimeWorkRequestBuilder<IndexWorker>()
      .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
      .setBackoffCriteria(BackoffPolicy.LINEAR, RESTART_DELAY_SECONDS, TimeUnit.SECONDS)
      .build()
    WorkManager.getInstance(context).enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, request)
  }

  /** Downloads the model in the background; it carries on, and retries when the network is back, if the app closes. */
  fun downloadModel(context: Context) {
    val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
      .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
      .build()
    WorkManager.getInstance(context).enqueueUniqueWork(ModelDownloadWorker.UNIQUE_NAME, ExistingWorkPolicy.KEEP, request)
  }

  /** Picks up new photos in the background, only while charging. */
  fun schedulePeriodic(context: Context) {
    val request = PeriodicWorkRequestBuilder<IndexWorker>(12, TimeUnit.HOURS)
      .setConstraints(Constraints.Builder().setRequiresCharging(true).build())
      .build()
    WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
  }
}
