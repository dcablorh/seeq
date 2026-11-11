package com.example.locallens.ml

import android.graphics.Bitmap
import kotlin.math.roundToInt

/** Turns a photo into the 224 x 224 input CLIP expects: short side scaled to 224, centre crop, normalised. */
object ClipImage {
  const val SIZE = 224

  private val MEAN = floatArrayOf(0.48145466f, 0.4578275f, 0.40821073f)
  private val STD = floatArrayOf(0.26862954f, 0.26130258f, 0.27577711f)

  /** Planar red, green and blue values for [SIZE] x [SIZE] ARGB [pixels], normalised the way CLIP was trained. */
  fun normalize(pixels: IntArray): FloatArray {
    val plane = SIZE * SIZE
    require(pixels.size == plane) { "Expected $plane pixels, got ${pixels.size}" }
    val out = FloatArray(3 * plane)
    for (i in 0 until plane) {
      val p = pixels[i]
      out[i] = (((p shr 16) and 0xFF) / 255f - MEAN[0]) / STD[0]
      out[plane + i] = (((p shr 8) and 0xFF) / 255f - MEAN[1]) / STD[1]
      out[2 * plane + i] = ((p and 0xFF) / 255f - MEAN[2]) / STD[2]
    }
    return out
  }

  /** [bitmap] must be a software bitmap; decode it with that allocator. Photos are usually already near size. */
  fun toTensorData(bitmap: Bitmap): FloatArray {
    val width = bitmap.width
    val height = bitmap.height
    val scale = SIZE.toFloat() / minOf(width, height)
    val scaledWidth = maxOf(SIZE, (width * scale).roundToInt())
    val scaledHeight = maxOf(SIZE, (height * scale).roundToInt())
    val scaled =
      if (scaledWidth == width && scaledHeight == height) bitmap else Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)
    val pixels = IntArray(SIZE * SIZE)
    scaled.getPixels(pixels, 0, SIZE, (scaledWidth - SIZE) / 2, (scaledHeight - SIZE) / 2, SIZE, SIZE)
    if (scaled !== bitmap) scaled.recycle()
    return normalize(pixels)
  }
}
