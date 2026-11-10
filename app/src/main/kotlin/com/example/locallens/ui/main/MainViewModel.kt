package com.example.locallens.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.locallens.IndexProgress
import com.example.locallens.data.PhotoRecord
import com.example.locallens.graph
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.locallens.index.IndexScheduler
import com.example.locallens.ml.ModelDownloadWorker
import com.example.locallens.search.SearchEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface ModelState {
  data object Missing : ModelState

  data class Downloading(val fraction: Float?) : ModelState

  data class Failed(val message: String) : ModelState

  data object Ready : ModelState
}

data class SearchState(
  val query: String = "",
  val searching: Boolean = false,
  val results: List<PhotoRecord> = emptyList(),
  val error: String? = null,
)

@OptIn(FlowPreview::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {
  private val graph = application.graph

  private val _model = MutableStateFlow<ModelState>(if (graph.models.isReady()) ModelState.Ready else ModelState.Missing)
  val model: StateFlow<ModelState> = _model

  private val _search = MutableStateFlow(SearchState())
  val search: StateFlow<SearchState> = _search

  val indexProgress: StateFlow<IndexProgress> = graph.progress

  private val queries = MutableStateFlow("")

  init {
    observeDownload()
    // Wait for a pause in typing, then search; a newer query cancels the one still running.
    viewModelScope.launch {
      queries.debounce(300).collectLatest { runSearch(it) }
    }
  }

  fun onQueryChange(value: String) {
    _search.update { it.copy(query = value) }
    queries.value = value
  }

  fun startIndexing() {
    if (_model.value == ModelState.Ready) IndexScheduler.runNow(getApplication())
  }

  fun downloadModel() {
    _model.value = ModelState.Downloading(null)
    IndexScheduler.downloadModel(getApplication())
  }

  /** The download runs in a background worker; this just mirrors its state, so it survives leaving the app. */
  private fun observeDownload() {
    viewModelScope.launch {
      WorkManager.getInstance(getApplication<Application>())
        .getWorkInfosForUniqueWorkFlow(ModelDownloadWorker.UNIQUE_NAME)
        .collect { infos ->
          if (graph.models.isReady()) {
            _model.value = ModelState.Ready
            return@collect
          }
          val info = infos.firstOrNull() ?: return@collect
          _model.value = when (info.state) {
            WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING, WorkInfo.State.BLOCKED -> {
              val percent = info.progress.getInt(ModelDownloadWorker.KEY_PERCENT, -1)
              ModelState.Downloading(if (percent >= 0) percent / 100f else null)
            }
            WorkInfo.State.FAILED ->
              ModelState.Failed(info.outputData.getString(ModelDownloadWorker.KEY_ERROR) ?: "Download failed")
            WorkInfo.State.CANCELLED, WorkInfo.State.SUCCEEDED -> ModelState.Missing
          }
        }
    }
  }

  private suspend fun runSearch(query: String) {
    if (query.isBlank() || _model.value != ModelState.Ready) {
      _search.update { it.copy(searching = false, results = emptyList(), error = null) }
      return
    }
    _search.update { it.copy(searching = true, error = null) }
    try {
      val index = withContext(Dispatchers.IO) { graph.index }
      val results = SearchEngine(index) { graph.embedder() }.search(query)
      _search.update { it.copy(searching = false, results = results) }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      _search.update { it.copy(searching = false, results = emptyList(), error = e.message ?: "Search failed") }
    }
  }
}
