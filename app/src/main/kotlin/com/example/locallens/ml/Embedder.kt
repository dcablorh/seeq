package com.example.locallens.ml

import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.EmbeddingEngine
import com.google.ai.edge.litertlm.EmbeddingEngineConfig
import com.google.ai.edge.litertlm.InputData
import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext

/** Turns text and images into unit-length vectors in one shared space. */
interface Embedder : AutoCloseable {
  suspend fun embedText(query: String): FloatArray

  suspend fun embedImage(jpeg: ByteArray): FloatArray
}

/**
 * EmbeddingGemma 2 on-device through LiteRT-LM. One thread owns the engine because creating, using and
 * closing it must all happen on the same thread; loading the weights blocks for seconds.
 */
class LiteRtEmbedder(private val modelPath: String) : Embedder {
  private val executor = Executors.newSingleThreadExecutor()
  private val dispatcher = executor.asCoroutineDispatcher()
  private var engine: EmbeddingEngine? = null

  override suspend fun embedText(query: String): FloatArray = withContext(dispatcher) {
    // Queries use the search-query prefix; the model card says to trim before prefixing.
    val raw = engine().computeEmbedding(listOf(InputData.Text("task: search query | text: ${query.trim()}"))).embedding
    VectorMath.truncateAndNormalize(raw)
  }

  override suspend fun embedImage(jpeg: ByteArray): FloatArray = withContext(dispatcher) {
    val raw = engine().computeEmbedding(listOf(InputData.Image(jpeg))).embedding
    VectorMath.truncateAndNormalize(raw)
  }

  /** Runs on the engine thread only. Tries the GPU first and falls back to the CPU. */
  private fun engine(): EmbeddingEngine {
    engine?.let { return it }
    val started = System.nanoTime()
    val created = try {
      create(Backend.GPU()).also { Log.i(TAG, "engine on GPU in ${elapsedMs(started)} ms") }
    } catch (e: Exception) {
      Log.w(TAG, "GPU engine failed after ${elapsedMs(started)} ms, using CPU", e)
      create(Backend.CPU()).also { Log.i(TAG, "engine on CPU in ${elapsedMs(started)} ms") }
    }
    engine = created
    return created
  }

  private fun create(backend: Backend): EmbeddingEngine {
    val config = EmbeddingEngineConfig(
      modelPath = modelPath,
      backend = backend,
      visionBackend = backend,
      visionTokensPerImage = VISION_TOKENS_PER_IMAGE,
    )
    val created = EmbeddingEngine(config)
    // An engine whose initialize() threw must not be closed, so only keep it once this returns.
    created.initialize()
    return created
  }

  override fun close() {
    executor.execute {
      engine?.close()
      engine = null
    }
    executor.shutdown()
  }

  private fun elapsedMs(startNanos: Long) = (System.nanoTime() - startNanos) / 1_000_000

  companion object {
    private const val TAG = "seeq"

    /**
     * Fewer vision tokens per photo means much faster indexing at a small cost in detail. Changing this changes
     * every image vector, so the index file name carries it and a new value starts a fresh index.
     */
    const val VISION_TOKENS_PER_IMAGE = 70
  }
}
