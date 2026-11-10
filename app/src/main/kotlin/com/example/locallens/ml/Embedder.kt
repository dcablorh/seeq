package com.example.locallens.ml

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
    val created = try {
      create(Backend.GPU())
    } catch (e: Exception) {
      create(Backend.CPU())
    }
    engine = created
    return created
  }

  private fun create(backend: Backend): EmbeddingEngine {
    val created = EmbeddingEngine(EmbeddingEngineConfig(modelPath = modelPath, backend = backend, visionBackend = backend))
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
}
