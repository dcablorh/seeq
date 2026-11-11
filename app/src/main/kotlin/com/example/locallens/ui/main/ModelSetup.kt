package com.example.locallens.ui.main

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.example.locallens.ui.theme.InfoRow
import com.example.locallens.ui.theme.Ink
import com.example.locallens.ui.theme.InkButton
import com.example.locallens.ui.theme.Mist
import com.example.locallens.ui.theme.StepLayout

/** The one-time model download, in its three states: ready to start, downloading, and failed. */
@Composable
fun ModelSetupScreen(viewModel: MainViewModel, state: ModelState, step: String? = null) {
  val context = LocalContext.current
  // The notification only shows download progress; the download runs even if it is denied.
  val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
    viewModel.downloadModel()
  }
  val onDownload = {
    val needsAsk = Build.VERSION.SDK_INT >= 33 &&
      ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
        PackageManager.PERMISSION_GRANTED
    if (needsAsk) notifications.launch(Manifest.permission.POST_NOTIFICATIONS) else viewModel.downloadModel()
  }
  ModelSetup(state, step, onDownload)
}

@Composable
private fun ModelSetup(state: ModelState, step: String?, onDownload: () -> Unit) {
  fun eyebrow(label: String) = listOfNotNull(step, label).joinToString(" · ")
  val body = MaterialTheme.typography.bodyLarge
  when (state) {
    is ModelState.Downloading -> {
      val fraction = state.fraction
      StepLayout(
        eyebrow = eyebrow(if (fraction != null) "Downloading ${(fraction * 100).toInt()}%" else "Downloading"),
        headline = "Downloading the search model",
      ) {
        Text(
          "Keep the app open for now. The download continues in the notification if you lock the screen.",
          style = body,
        )
        if (fraction != null) {
          LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth(),
            color = Ink,
            trackColor = Mist,
          )
        } else {
          LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Ink, trackColor = Mist)
        }
      }
    }
    is ModelState.Failed -> StepLayout(
      eyebrow = eyebrow("Download stopped"),
      headline = "The model didn't finish",
      actions = { InkButton("Try again", modifier = Modifier.fillMaxWidth(), onClick = onDownload) },
    ) {
      Text("Your progress is kept, so the download picks up where it stopped.", style = body)
      Text(state.message, style = body, color = MaterialTheme.colorScheme.error)
    }
    else -> StepLayout(
      eyebrow = eyebrow("One-time setup"),
      headline = "Download the search model",
      actions = { InkButton("Download model", modifier = Modifier.fillMaxWidth(), onClick = onDownload) },
    ) {
      Text(
        "seeq needs to download its on-device search model. Use Wi-Fi. " +
          "After this, search works with no internet connection.",
        style = body,
      )
      Column {
        InfoRow("Size", "About 155 MB")
        InfoRow("Runs on", "This phone")
        InfoRow("Network", "Needed once")
      }
    }
  }
}
