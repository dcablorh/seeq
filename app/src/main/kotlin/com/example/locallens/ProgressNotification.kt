package com.example.locallens

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo

/**
 * The ongoing notification a background job shows while it runs. Tapping it opens the app.
 *
 * Call [foregroundInfo] once to promote the job to a foreground service, then [update] to change the text and
 * progress. Calling `setForeground` again for every update can hang once the app is in the background.
 */
class ProgressNotification(
  private val context: Context,
  private val notificationId: Int,
  private val channelId: String,
  private val channelName: Int,
  private val title: Int,
) {
  fun foregroundInfo(text: String? = null, current: Int = 0, max: Int = 100, indeterminate: Boolean = false): ForegroundInfo {
    val notification = build(text, current, max, indeterminate)
    return if (Build.VERSION.SDK_INT >= 29) {
      ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    } else {
      ForegroundInfo(notificationId, notification)
    }
  }

  fun update(text: String? = null, current: Int = 0, max: Int = 100, indeterminate: Boolean = false) {
    // Silently does nothing while the user has notifications turned off; the job itself is unaffected.
    context.getSystemService(NotificationManager::class.java).notify(notificationId, build(text, current, max, indeterminate))
  }

  private fun build(text: String?, current: Int, max: Int, indeterminate: Boolean): Notification {
    context.getSystemService(NotificationManager::class.java)
      .createNotificationChannel(NotificationChannel(channelId, context.getString(channelName), NotificationManager.IMPORTANCE_LOW))
    val open = PendingIntent.getActivity(
      context,
      0,
      Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
      PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
    return NotificationCompat.Builder(context, channelId)
      .setSmallIcon(android.R.drawable.stat_sys_download)
      .setContentTitle(context.getString(title))
      .setContentText(text)
      .setContentIntent(open)
      .setOngoing(true)
      .setOnlyAlertOnce(true)
      .setProgress(max, current, indeterminate)
      .build()
  }
}
