package com.example.locallens

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo

/** The ongoing notification a background job shows while it runs. Tapping it opens the app. */
object ProgressNotification {
  fun foregroundInfo(
    context: Context,
    notificationId: Int,
    channelId: String,
    channelName: Int,
    title: Int,
    text: String? = null,
    current: Int = 0,
    max: Int = 100,
    indeterminate: Boolean = false,
  ): ForegroundInfo {
    context.getSystemService(NotificationManager::class.java)
      .createNotificationChannel(NotificationChannel(channelId, context.getString(channelName), NotificationManager.IMPORTANCE_LOW))
    val open = PendingIntent.getActivity(
      context,
      0,
      Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
      PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
    val notification = NotificationCompat.Builder(context, channelId)
      .setSmallIcon(android.R.drawable.stat_sys_download)
      .setContentTitle(context.getString(title))
      .setContentText(text)
      .setContentIntent(open)
      .setOngoing(true)
      .setOnlyAlertOnce(true)
      .setProgress(max, current, indeterminate)
      .build()
    return if (Build.VERSION.SDK_INT >= 29) {
      ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    } else {
      ForegroundInfo(notificationId, notification)
    }
  }
}
