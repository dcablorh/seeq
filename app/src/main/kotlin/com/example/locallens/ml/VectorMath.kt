package com.example.locallens.ml

import kotlin.math.sqrt

object VectorMath {
  /** CLIP ViT-B/32 produces 512-value vectors for both photos and text. */
  const val TARGET_DIM = 512

  /** Keeps the first [dim] values and scales the result to unit length. */
  fun truncateAndNormalize(vector: FloatArray, dim: Int = TARGET_DIM): FloatArray {
    val out = vector.copyOf(minOf(dim, vector.size))
    var sumSquares = 0f
    for (v in out) sumSquares += v * v
    val norm = sqrt(sumSquares)
    if (norm > 0f) {
      for (i in out.indices) out[i] /= norm
    }
    return out
  }
}
