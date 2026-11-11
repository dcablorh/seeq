package com.example.locallens.index

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.locallens.IndexProgress
import com.example.locallens.ProgressNotification
import com.example.locallens.R
import com.example.locallens.data.PhotoRecord
import com.example.locallens.data.uri
import com.example.locallens.graph
import com.example.locallens.ml.ClipImage
import java.io.IOException
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
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
        startForeground(pending.size)
        val embedder = graph.embedder()
        coroutineScope {
          // Photos are decoded a few ahead of the one being embedded, so neither step waits for the other.
          val decoded = Channel<Pair<PhotoRecord, Bitmap?>>(PREFETCH)
          val producer = launch(Dispatchers.IO) {
            for (photo in pending) decoded.send(photo to decode(photo))
            decoded.close()
          }
          var failures = 0
          try {
            for ((photo, bitmap) in decoded) {
              ensureActive()
              val started = System.nanoTime()
              if (bitmap != null) {
                try {
                  index.add(photo, embedder.embedImage(bitmap))
                  failures = 0
                } catch (e: CancellationException) {
                  throw e
                } catch (e: Exception) {
                  // One bad photo is skipped; a run of failures means the model itself is broken.
                  Log.w(TAG, "could not embed photo ${photo.id}", e)
                  if (++failures >= MAX_FAILURES_IN_A_ROW) throw e
                } finally {
                  bitmap.recycle()
                }
              }
              done++
              Log.i(TAG, "photo $done/${pending.size} in ${(System.nanoTime() - started) / 1_000_000} ms")
              graph.setProgress(IndexProgress(running = true, done = done, total = pending.size))
              if (done % SAVE_EVERY == 0) withContext(Dispatchers.IO) { index.save() }
              if (done % NOTIFY_EVERY == 0) showProgress(done, pending.size)
            }
          } finally {
            producer.cancel()
          }
        }
      }
    } finally {
      withContext(NonCancellable + Dispatchers.IO) { index.save() }
      graph.setProgress(IndexProgress(running = false, done = done, total = pending.size))
    }
    return Result.success()
  }

  private val notification = ProgressNotification(
    applicationContext,
    notificationId = NOTIFICATION_ID,
    channelId = CHANNEL,
    channelName = R.string.index_channel,
    title = R.string.index_notification_title,
  )

  /**
   * Runs as a foreground job so Android does not throttle it once the app is closed or another app is in front.
   * Without the notification the work crawls in the background.
   */
  private suspend fun startForeground(total: Int) {
    try {
      setForeground(notification.foregroundInfo(text = "0 of $total photos", max = total))
    } catch (e: IllegalStateException) {
      // Android refuses to start a foreground service from the background (the charging-only periodic run);
      // carry on without it.
    }
  }

  private fun showProgress(done: Int, total: Int) =
    notification.update(text = "$done of $total photos", current = done, max = total)

  /**
   * Decodes straight to the size the model needs, so the system never builds a full-size bitmap and there is no
   * JPEG round trip. Software memory, because the pixels are read back on the CPU.
   */
  private fun decode(photo: PhotoRecord): Bitmap? = try {
    val source = ImageDecoder.createSource(applicationContext.contentResolver, photo.uri)
    ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
      val width = info.size.width
      val height = info.size.height
      val scale = ClipImage.SIZE.toFloat() / minOf(width, height)
      if (scale < 1f) {
        decoder.setTargetSize(maxOf(ClipImage.SIZE, (width * scale).roundToInt()), maxOf(ClipImage.SIZE, (height * scale).roundToInt()))
      }
      decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
    }
  } catch (e: IOException) {
    null // the photo was deleted or is unreadable; skip it
  } catch (e: SecurityException) {
    null // access to this photo was revoked
  }

  private companion object {
    const val TAG = "seeq"
    const val PREFETCH = 4
    const val MAX_FAILURES_IN_A_ROW = 5
    const val SAVE_EVERY = 25
    const val NOTIFY_EVERY = 25
    const val NOTIFICATION_ID = 2
    const val CHANNEL = "photo-indexing"
  }
}
