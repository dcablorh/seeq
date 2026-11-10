package com.example.locallens.ui.main

import android.util.Size
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.locallens.data.PhotoRecord
import com.example.locallens.data.uri
import com.example.locallens.ui.theme.CardShape
import com.example.locallens.ui.theme.Hairline
import com.example.locallens.ui.theme.Ink
import com.example.locallens.ui.theme.Label
import com.example.locallens.ui.theme.Mist
import java.text.DateFormat
import java.util.Date
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A circular orbit carousel adapted for Jetpack Compose.
 *
 * Implements the 3D orbit preset from React Bits CircularCarousel:
 * - Cards arranged in a 3D ring with perspective depth, tilt, and curve
 * - Smooth gesture tracking without drag lag
 * - Focus on click: tapping adjacent cards animates them to center
 * - No percentage numbers shown
 */
@Composable
fun CircularCarousel(
  photos: List<PhotoRecord>,
  onPhotoClick: (PhotoRecord) -> Unit = {},
  modifier: Modifier = Modifier,
) {
  if (photos.isEmpty()) return

  val pagerState = rememberPagerState(pageCount = { photos.size })
  val coroutineScope = rememberCoroutineScope()

  Column(
    modifier = modifier.fillMaxWidth(),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    HorizontalPager(
      state = pagerState,
      contentPadding = PaddingValues(horizontal = 88.dp),
      pageSpacing = 16.dp,
      modifier = Modifier
        .fillMaxWidth()
        .height(290.dp),
    ) { page ->
      val photo = photos[page]

      Box(
        modifier = Modifier
          .fillMaxSize()
          .graphicsLayer {
            val pageOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
            val absOffset = abs(pageOffset).coerceIn(0f, 1f)

            // Orbit preset 3D transform:
            // - scale down side cards
            // - rotate around Y (orbit ring shape)
            // - tilt forward along X (looking slightly down into the ring)
            // - curve dip along Y
            val scale = 1f - 0.16f * absOffset
            scaleX = scale
            scaleY = scale
            rotationY = -24f * pageOffset.coerceIn(-1.2f, 1.2f)
            rotationX = -8f
            translationY = 18f * absOffset
            alpha = 1f - 0.38f * absOffset
            cameraDistance = 14f * density
          }
          .clip(CardShape)
          .clickable {
            if (pagerState.currentPage == page) {
              onPhotoClick(photo)
            } else {
              coroutineScope.launch {
                pagerState.animateScrollToPage(page)
              }
            }
          },
      ) {
        PhotoCard(photo)
      }
    }

    Spacer(Modifier.height(8.dp))

    // Caption row: date & position counter (e.g. "OCT 24, 2024   01 / 15")
    val currentPhoto = photos.getOrNull(pagerState.currentPage)
    if (currentPhoto != null) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        val dateText = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(currentPhoto.takenMs)).uppercase()
        Text(dateText, style = Label, color = Ink.copy(alpha = 0.7f))

        Row(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          PageIndicator(
            count = photos.size,
            current = pagerState.currentPage,
          )
          val countText = String.format("%02d / %02d", pagerState.currentPage + 1, photos.size)
          Text(countText, style = Label, color = Ink.copy(alpha = 0.5f))
        }
      }
    }
  }
}

// ---------------------------------------------------------------------------
// Private helpers
// ---------------------------------------------------------------------------

@Composable
private fun PhotoCard(photo: PhotoRecord) {
  val context = LocalContext.current
  val bitmap by produceState<ImageBitmap?>(null, photo.id) {
    value = withContext(Dispatchers.IO) {
      try {
        context.contentResolver
          .loadThumbnail(photo.uri, Size(512, 512), null)
          .asImageBitmap()
      } catch (e: Exception) {
        null
      }
    }
  }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(Mist)
      .border(1.dp, Hairline, CardShape),
  ) {
    bitmap?.let {
      Image(
        bitmap = it,
        contentDescription = "Photo taken on ${DateFormat.getDateInstance().format(Date(photo.takenMs))}",
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize(),
      )
    }
  }
}

/** Row of small dots indicating the current page. */
@Composable
private fun PageIndicator(count: Int, current: Int, modifier: Modifier = Modifier) {
  if (count <= 1 || count > 12) return
  Row(
    modifier = modifier,
    horizontalArrangement = Arrangement.spacedBy(4.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    repeat(count) { index ->
      val active = index == current
      Box(
        modifier = Modifier
          .size(if (active) 6.dp else 4.dp)
          .background(
            color = if (active) Ink else Mist,
            shape = RoundedCornerShape(50),
          ),
      )
    }
  }
}
