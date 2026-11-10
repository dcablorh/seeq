package com.example.locallens.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.locallens.IndexProgress
import com.example.locallens.data.PhotoRecord
import com.example.locallens.graph
import com.example.locallens.index.IndexScheduler
import com.example.locallens.search.SearchEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
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
  private var downloadJob: Job? = null

  init {
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
    if (downloadJob?.isActive == true) return
    _model.value = ModelState.Downloading(null)
    downloadJob = viewModelScope.launch {
      try {
        graph.models.download { done, total ->
          _model.value = ModelState.Downloading(if (total > 0) done.toFloat() / total else null)
        }
        _model.value = ModelState.Ready
        startIndexing()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _model.value = ModelState.Failed(e.message ?: "Download failed")
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
