package com.example.locallens.ml

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.locallens.ProgressNotification
import com.example.locallens.R
import com.example.locallens.graph
import com.example.locallens.index.IndexScheduler
import java.io.IOException

/**
 * Downloads the model as a foreground job, so it keeps going when the app is closed. A dropped connection is
 * retried and resumes from the partial file.
 */
class ModelDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

  override suspend fun doWork(): Result {
    val models = applicationContext.graph.models
    if (models.isReady()) return Result.success()

    setForeground(foregroundInfo(percent = -1))
    var lastPercent = -1
    try {
      models.download { done, total ->
        val percent = if (total > 0) (done * 100 / total).toInt() else -1
        if (percent != lastPercent) {
          lastPercent = percent
          setProgress(workDataOf(KEY_PERCENT to percent))
          setForeground(foregroundInfo(percent))
        }
      }
    } catch (e: HttpStatusException) {
      return Result.failure(errorData(e))
    } catch (e: IOException) {
      return if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure(errorData(e))
    }
    IndexScheduler.runNow(applicationContext)
    return Result.success()
  }

  private fun errorData(e: Exception): Data = workDataOf(KEY_ERROR to (e.message ?: "Download failed"))

  private fun foregroundInfo(percent: Int) = ProgressNotification.foregroundInfo(
    applicationContext,
    notificationId = NOTIFICATION_ID,
    channelId = CHANNEL,
    channelName = R.string.download_channel,
    title = R.string.download_notification_title,
    current = percent.coerceAtLeast(0),
    indeterminate = percent < 0,
  )

  companion object {
    const val UNIQUE_NAME = "model-download"
    const val KEY_PERCENT = "percent"
    const val KEY_ERROR = "error"
    private const val CHANNEL = "model-download"
    private const val NOTIFICATION_ID = 1
    private const val MAX_ATTEMPTS = 5
  }
}
