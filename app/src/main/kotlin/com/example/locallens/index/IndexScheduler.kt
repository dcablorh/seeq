package com.example.locallens.index

import android.content.Context
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

  /** Indexes right away (used while the app is open), unless the battery is low. */
  fun runNow(context: Context) {
    val request = OneTimeWorkRequestBuilder<IndexWorker>()
      .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
      .build()
    WorkManager.getInstance(context).enqueueUniqueWork(NOW, ExistingWorkPolicy.KEEP, request)
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
