package com.example.locallens.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

/** Permissions to ask for, by Android version. Android 14 also lets the user share only some photos. */
fun photoPermissions(): Array<String> = when {
  Build.VERSION.SDK_INT >= 34 ->
    arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
  Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
  else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}

/** Checked live each time, never cached, because the user can revoke access in Settings at any moment. */
fun hasPhotoAccess(context: Context): Boolean {
  fun granted(permission: String) =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
  return photoPermissions().any(::granted)
}

private enum class AskState { First, Rationale, PermanentlyDenied }

/** Explains why photo access is needed and walks through ask, rationale, and "open Settings". */
@Composable
fun PhotoPermissionScreen(onResult: () -> Unit, modifier: Modifier = Modifier) {
  val context = androidx.compose.ui.platform.LocalContext.current
  val activity: Activity? = LocalActivity.current
  var state by remember { mutableStateOf(AskState.First) }

  fun shouldShowRationale() = activity != null &&
    photoPermissions().any { activity.shouldShowRequestPermissionRationale(it) }

  val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
    if (hasPhotoAccess(context)) {
      onResult()
    } else {
      state = if (shouldShowRationale()) AskState.Rationale else AskState.PermanentlyDenied
    }
  }

  Column(
    modifier = modifier.fillMaxSize().padding(24.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text("Find any photo by typing", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
    Text(
      when (state) {
        AskState.First -> "LocalLens reads your photos on this phone to build a private search index. Nothing leaves your device."
        AskState.Rationale -> "Without photo access there is nothing to search. Your photos stay on this device."
        AskState.PermanentlyDenied -> "Photo access is turned off. Turn it on in Settings to use search."
      },
      textAlign = TextAlign.Center,
    )
    if (state == AskState.PermanentlyDenied) {
      Button(onClick = {
        context.startActivity(
          Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
          },
        )
      }) { Text("Open Settings") }
    } else {
      Button(onClick = { launcher.launch(photoPermissions()) }) { Text("Allow photo access") }
    }
  }
}
