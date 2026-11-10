package com.example.locallens.index

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
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

  /** Picks up new photos in the background, only while charging. */
  fun schedulePeriodic(context: Context) {
    val request = PeriodicWorkRequestBuilder<IndexWorker>(12, TimeUnit.HOURS)
      .setConstraints(Constraints.Builder().setRequiresCharging(true).build())
      .build()
    WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
  }
}
