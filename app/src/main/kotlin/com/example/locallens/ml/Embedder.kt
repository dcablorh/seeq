package com.example.locallens.ml

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.graphics.Bitmap
import android.util.Log
import java.io.File
import java.nio.FloatBuffer
import java.nio.LongBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Turns text and images into unit-length vectors in one shared space. */
interface Embedder : AutoCloseable {
  suspend fun embedText(query: String): FloatArray

  suspend fun embedImage(bitmap: Bitmap): FloatArray
}

/**
 * OpenAI CLIP ViT-B/32 on-device through ONNX Runtime: one model for photos, one for text, both 8-bit quantised.
 * A session is created on first use, because that reads and optimises the whole model.
 */
class OnnxEmbedder(visionModel: File, textModel: File, private val tokenizer: ClipTokenizer) : Embedder {
  private val env = OrtEnvironment.getEnvironment()
  private val options = OrtSession.SessionOptions().apply {
    setIntraOpNumThreads(THREADS)
    setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
  }
  private val visionSession = lazy { open(visionModel, "vision") }
  private val textSession = lazy { open(textModel, "text") }

  override suspend fun embedText(query: String): FloatArray = withContext(Dispatchers.Default) {
    val session = textSession.value
    val ids = tokenizer.encode(query).map { it.toLong() }.toLongArray()
    val shape = longArrayOf(1, ids.size.toLong())
    val inputs = HashMap<String, OnnxTensor>()
    try {
      for (name in session.inputNames) {
        val values = if ("mask" in name) LongArray(ids.size) { 1L } else ids
        inputs[name] = OnnxTensor.createTensor(env, LongBuffer.wrap(values), shape)
      }
      session.run(inputs).use { VectorMath.truncateAndNormalize(it.embedding(session)) }
    } finally {
      inputs.values.forEach { it.close() }
    }
  }

  override suspend fun embedImage(bitmap: Bitmap): FloatArray = withContext(Dispatchers.Default) {
    val session = visionSession.value
    val data = ClipImage.toTensorData(bitmap)
    val shape = longArrayOf(1, 3, ClipImage.SIZE.toLong(), ClipImage.SIZE.toLong())
    OnnxTensor.createTensor(env, FloatBuffer.wrap(data), shape).use { pixels ->
      session.run(mapOf(session.inputNames.first() to pixels)).use { VectorMath.truncateAndNormalize(it.embedding(session)) }
    }
  }

  private fun open(model: File, label: String): OrtSession {
    val started = System.nanoTime()
    return env.createSession(model.absolutePath, options).also {
      Log.i(TAG, "$label model ready in ${(System.nanoTime() - started) / 1_000_000} ms, inputs=${it.inputNames} outputs=${it.outputNames}")
    }
  }

  /** The projected embedding, not the per-token hidden states that these exports also return. */
  private fun OrtSession.Result.embedding(session: OrtSession): FloatArray {
    val names = session.outputNames.toList()
    val index = names.indexOfFirst { "embeds" in it }.takeIf { it >= 0 } ?: 0
    val buffer = (get(index) as OnnxTensor).floatBuffer
    return FloatArray(buffer.remaining()).also { buffer.get(it) }
  }

  override fun close() {
    if (visionSession.isInitialized()) visionSession.value.close()
    if (textSession.isInitialized()) textSession.value.close()
    options.close()
  }

  private companion object {
    const val TAG = "seeq"
    const val THREADS = 4
  }
}
