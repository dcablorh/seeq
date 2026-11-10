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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.locallens.data.PhotoRecord
import com.example.locallens.data.uri
import com.example.locallens.ui.PhotoPermissionScreen
import com.example.locallens.ui.hasPhotoAccess
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
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
    modifier = modifier,
    topBar = { CenterAlignedTopAppBar(title = { Text("seeq") }) },
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
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text("One-time setup", style = MaterialTheme.typography.headlineSmall)
    Text(
      "seeq needs to download its on-device search model, about 500 MB. Use Wi-Fi. " +
        "After this, search works with no internet connection.",
      textAlign = TextAlign.Center,
    )
    when (state) {
      is ModelState.Downloading -> {
        val fraction = state.fraction
        if (fraction != null) {
          LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
          Text("${(fraction * 100).toInt()}%")
        } else {
          LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
      }
      is ModelState.Failed -> {
        Text(state.message, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
        Button(onClick = onDownload) { Text("Try again") }
      }
      else -> Button(onClick = onDownload) { Text("Download model") }
    }
  }
}

@Composable
private fun ReadyContent(viewModel: MainViewModel) {
  val search by viewModel.search.collectAsStateWithLifecycle()
  val progress by viewModel.indexProgress.collectAsStateWithLifecycle()
  val context = LocalContext.current

  Column(Modifier.fillMaxSize()) {
    OutlinedTextField(
      value = search.query,
      onValueChange = viewModel::onQueryChange,
      modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
      placeholder = { Text("Search your photos") },
      singleLine = true,
      keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
      keyboardActions = KeyboardActions(onSearch = {}),
    )
    if (progress.running && progress.total > 0) {
      Column(Modifier.padding(horizontal = 16.dp)) {
        Text("Indexing photos ${progress.done} of ${progress.total}", style = MaterialTheme.typography.bodySmall)
        LinearProgressIndicator(
          progress = { progress.done.toFloat() / progress.total },
          modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        )
      }
    }
    when {
      search.error != null ->
        Message(search.error!!, isError = true)
      search.query.isBlank() ->
        Message("Describe what you're looking for, like \"blue car at night\" or \"receipt from August 5\".")
      search.results.isEmpty() && !search.searching ->
        Message(if (progress.running) "No matches yet. Still indexing, so results will improve." else "No matches found.")
      else ->
        LazyVerticalGrid(
          columns = GridCells.Adaptive(110.dp),
          modifier = Modifier.fillMaxSize(),
          contentPadding = PaddingValues(16.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          items(search.results, key = { it.id }) { record ->
            Thumbnail(record, onClick = { openPhoto(context, record) })
          }
        }
    }
  }
}

@Composable
private fun Message(text: String, isError: Boolean = false) {
  Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
    Text(
      text,
      textAlign = TextAlign.Center,
      color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

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
      .aspectRatio(1f)
      .clip(RoundedCornerShape(8.dp))
      .background(MaterialTheme.colorScheme.surfaceVariant)
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
