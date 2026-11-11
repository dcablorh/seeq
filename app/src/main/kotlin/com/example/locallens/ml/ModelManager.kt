package com.example.locallens.ml

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** The server answered, but not with the file; retrying the same request will not help. */
class HttpStatusException(val code: Int) : IOException("Download failed: HTTP $code")

/** Downloads the two CLIP models once into app-private storage. After that the app needs no network. */
class ModelManager(private val dir: File) {
  val visionFile = File(dir, VISION_NAME)
  val textFile = File(dir, TEXT_NAME)

  fun isReady(): Boolean = visionFile.length() > 0 && textFile.length() > 0

  /** Resumes partial downloads. [onProgress] gets (bytes so far, total bytes) across both files. */
  suspend fun download(onProgress: suspend (Long, Long) -> Unit) = withContext(Dispatchers.IO) {
    dir.mkdirs()
    val total = VISION_BYTES + TEXT_BYTES
    var finished = 0L
    for (model in listOf(Model(VISION_URL, visionFile, VISION_BYTES), Model(TEXT_URL, textFile, TEXT_BYTES))) {
      if (model.file.length() == 0L) downloadFile(model.url, model.file) { onProgress(finished + it, total) }
      finished += model.bytes
    }
    onProgress(total, total)
    deleteLegacyModel()
  }

  private class Model(val url: String, val file: File, val bytes: Long)

  private suspend fun downloadFile(url: String, target: File, onBytes: suspend (Long) -> Unit) {
    val partFile = File(target.path + ".part")
    val resumeFrom = if (partFile.exists()) partFile.length() else 0L
    val connection = URL(url).openConnection() as HttpURLConnection
    try {
      connection.connectTimeout = 15_000
      connection.readTimeout = 30_000
      if (resumeFrom > 0) connection.setRequestProperty("Range", "bytes=$resumeFrom-")
      val code = connection.responseCode
      val resuming = code == HttpURLConnection.HTTP_PARTIAL
      if (code != HttpURLConnection.HTTP_OK && !resuming) throw HttpStatusException(code)

      val start = if (resuming) resumeFrom else 0L
      val length = connection.contentLengthLong
      val expected = if (length > 0) start + length else -1L
      var done = start
      connection.inputStream.use { input ->
        FileOutputStream(partFile, resuming).use { output ->
          val buffer = ByteArray(64 * 1024)
          while (true) {
            coroutineContext.ensureActive()
            val read = input.read(buffer)
            if (read < 0) break
            output.write(buffer, 0, read)
            done += read
            onBytes(done)
          }
        }
      }
      if (expected > 0 && done != expected) throw IOException("Download incomplete: $done of $expected bytes")
      if (!partFile.renameTo(target)) throw IOException("Could not save ${target.name}")
    } finally {
      connection.disconnect()
    }
  }

  /** The earlier EmbeddingGemma model and its GPU caches are about 700 MB and no longer used. */
  private fun deleteLegacyModel() {
    dir.listFiles { file -> file.name.startsWith("embeddinggemma") }?.forEach { it.delete() }
  }

  companion object {
    const val VISION_NAME = "clip-vit-b32-vision-q8.onnx"
    const val TEXT_NAME = "clip-vit-b32-text-q8.onnx"

    private const val BASE = "https://huggingface.co/Xenova/clip-vit-base-patch32/resolve/main/onnx"
    const val VISION_URL = "$BASE/vision_model_quantized.onnx"
    const val TEXT_URL = "$BASE/text_model_quantized.onnx"

    // Exact sizes of the two files, used only for the combined progress bar.
    const val VISION_BYTES = 89_117_001L
    const val TEXT_BYTES = 64_504_507L
  }
}
