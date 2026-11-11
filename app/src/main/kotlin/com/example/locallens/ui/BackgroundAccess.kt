package com.example.locallens.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.example.locallens.ui.theme.EyebrowText
import com.example.locallens.ui.theme.GhostLink
import com.example.locallens.ui.theme.Ink
import com.example.locallens.ui.theme.InkButton

/** Whether the ongoing indexing notification can be shown. Android 13+ asks; older versions always allow it. */
fun notificationsAllowed(context: Context): Boolean =
  Build.VERSION.SDK_INT < 33 ||
    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
      PackageManager.PERMISSION_GRANTED

/** Checked live, because the user can change it in system settings at any moment. */
fun ignoresBatteryOptimizations(context: Context): Boolean =
  context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

/**
 * Asks for what indexing needs to keep going once seeq is no longer on screen: a visible notification for the
 * foreground job, and an exemption from battery optimization. Phones that pause background apps, TECNO's
 * included, leave an app alone only when both are in place. Hides itself once both are granted.
 */
@Composable
fun BackgroundAccessPrompt(modifier: Modifier = Modifier) {
  val context = LocalContext.current
  var notifications by remember { mutableStateOf(notificationsAllowed(context)) }
  var battery by remember { mutableStateOf(ignoresBatteryOptimizations(context)) }
  // After one refusal Android stops showing the dialog, so the button has to open Settings instead.
  var notificationsRefused by remember { mutableStateOf(false) }

  // Re-check on every return to the app: both are changed on system screens.
  LifecycleResumeEffect(Unit) {
    notifications = notificationsAllowed(context)
    battery = ignoresBatteryOptimizations(context)
    onPauseOrDispose {}
  }

  val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
    notifications = notificationsAllowed(context)
    notificationsRefused = !granted
  }

  if (notifications && battery) return

  Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
    EyebrowText("Keep indexing in the background")
    Text(
      "Your phone may pause seeq when you leave it. Allow these so indexing keeps going with the app closed.",
      color = Ink,
    )
    if (!notifications) {
      InkButton(
        "Allow notifications",
        onClick = {
          if (notificationsRefused) openNotificationSettings(context) else askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        },
      )
    }
    if (!battery) {
      InkButton("Allow background running", onClick = { requestIgnoreBatteryOptimizations(context) })
    }
    GhostLink("App settings", onClick = { openAppSettings(context) })
  }
}

private fun requestIgnoreBatteryOptimizations(context: Context) {
  val request = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
  try {
    context.startActivity(request)
  } catch (e: ActivityNotFoundException) {
    // Some phones lack the one-tap dialog; fall back to the full list, then to this app's settings page.
    try {
      context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    } catch (e: ActivityNotFoundException) {
      openAppSettings(context)
    }
  }
}

private fun openNotificationSettings(context: Context) {
  val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
  try {
    context.startActivity(intent)
  } catch (e: ActivityNotFoundException) {
    openAppSettings(context)
  }
}

private fun openAppSettings(context: Context) {
  val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
  try {
    context.startActivity(intent)
  } catch (e: ActivityNotFoundException) {
    // No settings app to open; nothing more to do.
  }
}
