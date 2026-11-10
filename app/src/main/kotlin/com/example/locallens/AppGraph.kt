package com.example.locallens

import android.app.Application
import android.content.Context
import com.example.locallens.data.MediaStoreSource
import com.example.locallens.data.VectorIndex
import com.example.locallens.index.IndexScheduler
import com.example.locallens.ml.Embedder
import com.example.locallens.ml.LiteRtEmbedder
import com.example.locallens.ml.ModelManager
import com.example.locallens.ml.VectorMath
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class IndexProgress(val running: Boolean = false, val done: Int = 0, val total: Int = 0)

class LocalLensApp : Application() {
  val graph: AppGraph by lazy { AppGraph(this) }

  override fun onCreate() {
    super.onCreate()
    IndexScheduler.schedulePeriodic(this)
  }
}

val Context.graph: AppGraph
  get() = (applicationContext as LocalLensApp).graph

/** Shared app singletons: model files, the vector index, the embedder and indexing progress. */
class AppGraph(context: Context) {
  val models = ModelManager(File(context.filesDir, "models"))
  val gallery = MediaStoreSource(context.contentResolver)
  val index: VectorIndex by lazy { VectorIndex.load(File(context.filesDir, "index.bin"), VectorMath.TARGET_DIM) }

  private val _progress = MutableStateFlow(IndexProgress())
  val progress: StateFlow<IndexProgress> = _progress

  fun setProgress(value: IndexProgress) {
    _progress.value = value
  }

  private var embedderInstance: Embedder? = null

  /** The model is loaded on first use and kept for the life of the process. */
  @Synchronized
  fun embedder(): Embedder = embedderInstance ?: LiteRtEmbedder(models.modelFile.absolutePath).also { embedderInstance = it }
}
