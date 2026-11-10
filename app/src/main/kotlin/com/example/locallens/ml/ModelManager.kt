package com.example.locallens.ml

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Downloads the embedding model once into app-private storage. After that the app needs no network. */
class ModelManager(private val dir: File) {
  val modelFile = File(dir, MODEL_NAME)
  private val partFile = File(dir, "$MODEL_NAME.part")

  fun isReady(): Boolean = modelFile.exists() && modelFile.length() > 0

  /** Resumes a partial download if one exists. [onProgress] gets (bytesSoFar, totalBytes or -1). */
  suspend fun download(onProgress: (Long, Long) -> Unit) = withContext(Dispatchers.IO) {
    dir.mkdirs()
    val resumeFrom = if (partFile.exists()) partFile.length() else 0L
    val connection = URL(MODEL_URL).openConnection() as HttpURLConnection
    try {
      connection.connectTimeout = 15_000
      connection.readTimeout = 30_000
      if (resumeFrom > 0) connection.setRequestProperty("Range", "bytes=$resumeFrom-")
      val code = connection.responseCode
      val resuming = code == HttpURLConnection.HTTP_PARTIAL
      if (code != HttpURLConnection.HTTP_OK && !resuming) throw IOException("Download failed: HTTP $code")

      val start = if (resuming) resumeFrom else 0L
      val length = connection.contentLengthLong
      val total = if (length > 0) start + length else -1L
      var done = start
      connection.inputStream.use { input ->
        java.io.FileOutputStream(partFile, resuming).use { output ->
          val buffer = ByteArray(64 * 1024)
          while (true) {
            coroutineContext.ensureActive()
            val read = input.read(buffer)
            if (read < 0) break
            output.write(buffer, 0, read)
            done += read
            onProgress(done, total)
          }
        }
      }
      if (total > 0 && done != total) throw IOException("Download incomplete: $done of $total bytes")
      if (!partFile.renameTo(modelFile)) throw IOException("Could not save the model file")
    } finally {
      connection.disconnect()
    }
  }

  companion object {
    const val MODEL_NAME = "embeddinggemma-2-740m.litertlm"
    const val MODEL_URL =
      "https://huggingface.co/litert-community/embeddinggemma-2-740m-litert-lm/resolve/main/embeddinggemma-2-740m.litertlm"
  }
}
