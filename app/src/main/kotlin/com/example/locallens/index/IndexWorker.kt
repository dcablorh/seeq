package com.example.locallens.index

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import android.util.Size
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.locallens.IndexProgress
import com.example.locallens.ProgressNotification
import com.example.locallens.R
import com.example.locallens.data.PhotoRecord
import com.example.locallens.data.uri
import com.example.locallens.graph
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Embeds gallery photos that are not in the index yet. It saves every few photos, so being stopped by the
 * system only costs the work since the last save; the next run picks up the rest.
 */
class IndexWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

  override suspend fun doWork(): Result {
    val graph = applicationContext.graph
    if (!graph.models.isReady()) return Result.success()

    val images = graph.gallery.listImages()
    if (images.isEmpty()) return Result.success()
    val index = withContext(Dispatchers.IO) { graph.index }
    index.retainOnly(images.mapTo(HashSet()) { it.id })

    val pending = images.filter { !index.contains(it.id) }
    var done = 0
    graph.setProgress(IndexProgress(running = true, done = 0, total = pending.size))
    try {
      if (pending.isNotEmpty()) {
        showProgress(done, pending.size)
        val embedder = graph.embedder()
        for (photo in pending) {
          currentCoroutineContext().ensureActive()
          val started = System.nanoTime()
          val jpeg = withContext(Dispatchers.IO) { loadJpeg(photo) }
          if (jpeg != null) index.add(photo, embedder.embedImage(jpeg))
          done++
          Log.d(TAG, "photo $done/${pending.size} in ${(System.nanoTime() - started) / 1_000_000} ms")
          graph.setProgress(IndexProgress(running = true, done = done, total = pending.size))
          if (done % SAVE_EVERY == 0) withContext(Dispatchers.IO) { index.save() }
          if (done % NOTIFY_EVERY == 0) showProgress(done, pending.size)
        }
      }
    } finally {
      withContext(NonCancellable + Dispatchers.IO) { index.save() }
      graph.setProgress(IndexProgress(running = false, done = done, total = pending.size))
    }
    return Result.success()
  }

  /**
   * Runs as a foreground job so Android does not throttle it once the app is closed or another app is in front.
   * Without the notification the work crawls in the background.
   */
  private suspend fun showProgress(done: Int, total: Int) {
    val info = ProgressNotification.foregroundInfo(
      applicationContext,
      notificationId = NOTIFICATION_ID,
      channelId = CHANNEL,
      channelName = R.string.index_channel,
      title = R.string.index_notification_title,
      text = "$done of $total photos",
      current = done,
      max = total,
    )
    try {
      setForeground(info)
    } catch (e: IllegalStateException) {
      // Android refuses to start a foreground service from the background (the charging-only periodic run);
      // carry on without it.
    }
  }

  /** A 768px JPEG is plenty for the vision encoder and much cheaper than a full-size photo. */
  private fun loadJpeg(photo: PhotoRecord): ByteArray? = try {
    val bitmap = applicationContext.contentResolver.loadThumbnail(photo.uri, Size(MAX_SIDE, MAX_SIDE), null)
    ByteArrayOutputStream().use { out ->
      bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
      out.toByteArray()
    }
  } catch (e: IOException) {
    null // the photo was deleted or is unreadable; skip it
  } catch (e: SecurityException) {
    null // access to this photo was revoked
  }

  private companion object {
    const val MAX_SIDE = 768
    const val TAG = "seeq"
    const val SAVE_EVERY = 25
    const val NOTIFY_EVERY = 5
    const val NOTIFICATION_ID = 2
    const val CHANNEL = "photo-indexing"
  }
}
