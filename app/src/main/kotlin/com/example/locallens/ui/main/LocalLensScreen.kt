package com.example.locallens.ui.main

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.ui.res.painterResource
import com.example.locallens.R
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.locallens.data.PhotoRecord
import com.example.locallens.data.uri
import com.example.locallens.ui.PhotoPermissionScreen
import com.example.locallens.ui.hasPhotoAccess
import com.example.locallens.ui.theme.CardShape
import com.example.locallens.ui.theme.Display
import com.example.locallens.ui.theme.EyebrowText
import com.example.locallens.ui.theme.Hairline
import com.example.locallens.ui.theme.Ink
import com.example.locallens.ui.theme.InkButton
import com.example.locallens.ui.theme.Mist
import com.example.locallens.ui.theme.PillSearchBar
import com.example.locallens.ui.theme.paperGrid
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun LocalLensScreen(modifier: Modifier = Modifier, viewModel: MainViewModel = viewModel()) {
  val context = LocalContext.current
  var hasAccess by remember { mutableStateOf(hasPhotoAccess(context)) }
  // Re-check on every return to the app: access can be revoked in Settings.
  LifecycleResumeEffect(Unit) {
    hasAccess = hasPhotoAccess(context)
    onPauseOrDispose {}
  }

  Scaffold(
    modifier = modifier.paperGrid(),
    containerColor = Color.Transparent,
    topBar = { TopBar() },
  ) { innerPadding ->
    Box(Modifier.padding(innerPadding).fillMaxSize()) {
      if (!hasAccess) {
        PhotoPermissionScreen(onResult = { hasAccess = true })
      } else {
        SearchContent(viewModel)
      }
    }
  }
}

/** Oversized wordmark with the eye logo mark. */
@Composable
private fun TopBar() {
  Row(
    Modifier.fillMaxWidth().statusBarsPadding().padding(start = 24.dp, end = 24.dp, top = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text("seeq", style = Display, color = Ink)
    Spacer(Modifier.size(16.dp))
    Image(painterResource(R.drawable.ic_seeq_eye), contentDescription = null)
  }
}

@Composable
private fun SearchContent(viewModel: MainViewModel) {
  val model by viewModel.model.collectAsStateWithLifecycle()
  when (val state = model) {
    ModelState.Ready -> {
      LaunchedEffect(Unit) { viewModel.startIndexing() }
      ReadyContent(viewModel)
    }
    else -> {
      val context = LocalContext.current
      // The notification only shows download progress; the download runs even if it is denied.
      val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.downloadModel()
      }
      ModelSetup(
        state,
        onDownload = {
          val needsAsk = Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
              PackageManager.PERMISSION_GRANTED
          if (needsAsk) notifications.launch(Manifest.permission.POST_NOTIFICATIONS) else viewModel.downloadModel()
        },
      )
    }
  }
}

@Composable
private fun ModelSetup(state: ModelState, onDownload: () -> Unit) {
  Column(
    modifier = Modifier.fillMaxSize().padding(24.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    horizontalAlignment = Alignment.Start,
  ) {
    EyebrowText("One-time setup")
    Text("Download the search model", style = MaterialTheme.typography.headlineSmall)
    Text(
      "seeq needs to download its on-device search model, about 500 MB. Use Wi-Fi. " +
        "After this, search works with no internet connection.",
    )
    when (state) {
      is ModelState.Downloading -> {
        val fraction = state.fraction
        if (fraction != null) {
          LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth(),
            color = Ink,
            trackColor = Mist,
          )
          EyebrowText("${(fraction * 100).toInt()}%")
        } else {
          LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Ink, trackColor = Mist)
        }
      }
      is ModelState.Failed -> {
        Text(state.message, color = MaterialTheme.colorScheme.error)
        InkButton("Try again", onClick = onDownload)
      }
      else -> InkButton("Download model", onClick = onDownload)
    }
  }
}

@Composable
private fun ReadyContent(viewModel: MainViewModel) {
  val search by viewModel.search.collectAsStateWithLifecycle()
  val progress by viewModel.indexProgress.collectAsStateWithLifecycle()
  val indexed by viewModel.indexed.collectAsStateWithLifecycle()
  val context = LocalContext.current

  Column(Modifier.fillMaxSize()) {
    PillSearchBar(
      value = search.query,
      onValueChange = viewModel::onQueryChange,
      placeholder = "Search your photos",
      modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
    )
    if (progress.running && progress.total > 0) {
      Column(Modifier.padding(horizontal = 24.dp)) {
        EyebrowText("Indexing photos ${progress.done} of ${progress.total}")
        LinearProgressIndicator(
          progress = { progress.done.toFloat() / progress.total },
          modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
          color = Ink,
          trackColor = Mist,
        )
      }
    }
    when {
      search.error != null ->
        Message(search.error!!, isError = true)
      search.query.isBlank() && indexed.isEmpty() ->
        Message("Describe what you're looking for, like \"blue car at night\" or \"receipt from August 5\".")
      search.query.isBlank() -> {
        EyebrowText("${indexed.size} photos indexed", modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp))
        PhotoGrid(indexed) { openPhoto(context, it) }
      }
      search.results.isEmpty() && !search.searching ->
        Message(if (progress.running) "No matches yet. Still indexing, so results will improve." else "No matches found.")
      else -> PhotoGrid(search.results) { openPhoto(context, it) }
    }
  }
}

@Composable
private fun PhotoGrid(records: List<PhotoRecord>, onClick: (PhotoRecord) -> Unit) {
  LazyVerticalStaggeredGrid(
    columns = StaggeredGridCells.Fixed(2),
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalItemSpacing = 12.dp,
  ) {
    items(records, key = { it.id }) { record -> Thumbnail(record, onClick = { onClick(record) }) }
  }
}

@Composable
private fun Message(text: String, isError: Boolean = false) {
  Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.TopStart) {
    Text(
      text,
      color = if (isError) MaterialTheme.colorScheme.error else Ink,
    )
  }
}

// Stable per-photo ratios give the loose, uneven polaroid layout.
private val ThumbnailRatios = floatArrayOf(1f, 0.8f, 1.25f, 0.9f, 1.1f)

@Composable
private fun Thumbnail(record: PhotoRecord, onClick: () -> Unit) {
  val context = LocalContext.current
  val bitmap by produceState<ImageBitmap?>(null, record.id) {
    value = withContext(Dispatchers.IO) {
      try {
        context.contentResolver.loadThumbnail(record.uri, Size(256, 256), null).asImageBitmap()
      } catch (e: Exception) {
        null // the photo may have been deleted since it was indexed
      }
    }
  }
  Box(
    Modifier
      .aspectRatio(ThumbnailRatios[(record.id.hashCode() and Int.MAX_VALUE) % ThumbnailRatios.size])
      .clip(CardShape)
      .background(Mist)
      .border(1.dp, Hairline, CardShape)
      .clickable(onClick = onClick),
  ) {
    bitmap?.let {
      Image(
        bitmap = it,
        contentDescription = "Photo from ${DateFormat.getDateInstance().format(Date(record.takenMs))}",
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize(),
      )
    }
  }
}

private fun openPhoto(context: Context, record: PhotoRecord) {
  val intent = Intent(Intent.ACTION_VIEW)
    .setDataAndType(record.uri, "image/*")
    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
  try {
    context.startActivity(intent)
  } catch (e: ActivityNotFoundException) {
    // No app can open images; nothing more to do.
  }
}
