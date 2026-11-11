package com.example.locallens.ml

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ClipImageTest {
  private val plane = ClipImage.SIZE * ClipImage.SIZE

  @Test
  fun `a black image maps to the negative mean over the std for each channel`() {
    val out = ClipImage.normalize(IntArray(plane) { 0xFF000000.toInt() })
    assertEquals(-0.48145466f / 0.26862954f, out[0], 1e-5f)
    assertEquals(-0.4578275f / 0.26130258f, out[plane], 1e-5f)
    assertEquals(-0.40821073f / 0.27577711f, out[2 * plane], 1e-5f)
  }

  @Test
  fun `channels are written as separate planes in red green blue order`() {
    val out = ClipImage.normalize(IntArray(plane) { 0xFFFF0000.toInt() })
    val red = (1f - 0.48145466f) / 0.26862954f
    val green = -0.4578275f / 0.26130258f
    assertEquals(red, out[plane - 1], 1e-5f)
    assertEquals(green, out[plane], 1e-5f)
    assertEquals(3 * plane, out.size)
  }

  @Test
  fun `a wrongly sized pixel array is rejected`() {
    assertThrows(IllegalArgumentException::class.java) { ClipImage.normalize(IntArray(10)) }
  }
}
