package com.example.locallens.data

import android.content.ContentResolver
import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore

val PhotoRecord.uri: Uri
  get() = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)

/** Reads the device gallery through MediaStore. */
class MediaStoreSource(private val resolver: ContentResolver) {

  /** Lists every image the app can currently see, newest first. Empty if photo access is missing. */
  fun listImages(): List<PhotoRecord> {
    val projection = arrayOf(
      MediaStore.Images.Media._ID,
      MediaStore.Images.Media.DATE_TAKEN,
      MediaStore.Images.Media.DATE_MODIFIED,
    )
    val out = ArrayList<PhotoRecord>()
    try {
      resolver.query(
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        projection,
        null,
        null,
        "${MediaStore.Images.Media.DATE_TAKEN} DESC",
      )?.use { cursor ->
        val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
        val takenCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
        val modifiedCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
        while (cursor.moveToNext()) {
          val taken = cursor.getLong(takenCol)
          val takenMs = if (taken > 0) taken else cursor.getLong(modifiedCol) * 1000
          out.add(PhotoRecord(cursor.getLong(idCol), takenMs))
        }
      }
    } catch (e: SecurityException) {
      return emptyList()
    }
    return out
  }
}
