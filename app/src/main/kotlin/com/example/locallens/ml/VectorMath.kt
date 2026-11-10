package com.example.locallens.ml

import kotlin.math.sqrt

object VectorMath {
  /** Matryoshka size we keep; smaller vectors cut storage and search time with little quality loss. */
  const val TARGET_DIM = 256

  /** Keeps the first [dim] values (Matryoshka truncation) and scales the result to unit length. */
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
