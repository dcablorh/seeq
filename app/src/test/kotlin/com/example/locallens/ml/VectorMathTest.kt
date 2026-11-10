package com.example.locallens.ml

import org.junit.Assert.assertEquals
import org.junit.Test

class VectorMathTest {
  @Test
  fun `result has unit length`() {
    val out = VectorMath.truncateAndNormalize(floatArrayOf(3f, 4f, 12f), dim = 3)
    assertEquals(1f, out.sumOf { (it * it).toDouble() }.toFloat(), 1e-5f)
  }

  @Test
  fun `keeps the first values then normalizes`() {
    val out = VectorMath.truncateAndNormalize(floatArrayOf(3f, 4f, 99f), dim = 2)
    assertEquals(2, out.size)
    assertEquals(0.6f, out[0], 1e-6f)
    assertEquals(0.8f, out[1], 1e-6f)
  }

  @Test
  fun `shorter vectors are not padded`() {
    assertEquals(2, VectorMath.truncateAndNormalize(floatArrayOf(1f, 0f), dim = 256).size)
  }

  @Test
  fun `zero vector stays zero`() {
    val out = VectorMath.truncateAndNormalize(floatArrayOf(0f, 0f), dim = 2)
    assertEquals(0f, out[0], 0f)
    assertEquals(0f, out[1], 0f)
  }
}
