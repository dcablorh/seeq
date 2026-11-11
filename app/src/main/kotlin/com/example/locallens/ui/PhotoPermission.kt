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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import com.example.locallens.ui.theme.BulletRow
import com.example.locallens.ui.theme.InkButton
import com.example.locallens.ui.theme.StepLayout
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
fun PhotoPermissionScreen(onResult: () -> Unit, modifier: Modifier = Modifier, step: String? = null) {
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

  StepLayout(
    eyebrow = listOfNotNull(step, "Private by design").joinToString(" · "),
    headline = "Your photos never leave this phone.",
    modifier = modifier,
    actions = {
      if (state == AskState.PermanentlyDenied) {
        InkButton("Open Settings", modifier = Modifier.fillMaxWidth(), onClick = {
          context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
              data = Uri.fromParts("package", context.packageName, null)
            },
          )
        })
      } else {
        InkButton(
          "Allow photo access",
          modifier = Modifier.fillMaxWidth(),
          onClick = { launcher.launch(photoPermissions()) },
        )
      }
    },
  ) {
    Text(
      when (state) {
        AskState.First -> "seeq reads your photos on this device to build a private search index."
        AskState.Rationale -> "Without photo access there is nothing to search. Your photos stay on this device."
        AskState.PermanentlyDenied -> "Photo access is turned off. Turn it on in Settings to use search."
      },
      style = MaterialTheme.typography.bodyLarge,
    )
    Column {
      BulletRow("Search index stored on device")
      BulletRow("No upload, no account")
      BulletRow("Works offline after setup")
    }
  }
}
