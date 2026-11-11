package com.example.locallens.ml

import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Runs against the real CLIP vocabulary shipped in assets, so a wrong id here means wrong search results. */
class ClipTokenizerTest {
  private val tokenizer: ClipTokenizer = run {
    val assets = File("src/main/assets/clip")
    ClipTokenizer.load(File(assets, "vocab.json").inputStream(), File(assets, "merges.txt").inputStream())
  }

  @Test
  fun `short phrases match the reference CLIP ids`() {
    assertArrayEquals(intArrayOf(49406, 320, 22697, 49407), tokenizer.encode("a diagram"))
    assertArrayEquals(intArrayOf(49406, 320, 2368, 49407), tokenizer.encode("a cat"))
    assertArrayEquals(intArrayOf(49406, 320, 1929, 49407), tokenizer.encode("a dog"))
  }

  @Test
  fun `case and extra spaces do not change the ids`() {
    assertArrayEquals(tokenizer.encode("a cat"), tokenizer.encode("  A   CAT "))
  }

  @Test
  fun `empty text is just the two markers`() {
    assertArrayEquals(intArrayOf(ClipTokenizer.START, ClipTokenizer.END), tokenizer.encode(""))
  }

  @Test
  fun `long text is cut to 77 tokens and still ends with the end marker`() {
    val ids = tokenizer.encode("blue car at night ".repeat(60))
    assertEquals(ClipTokenizer.MAX_LENGTH, ids.size)
    assertEquals(ClipTokenizer.START, ids.first())
    assertEquals(ClipTokenizer.END, ids.last())
  }

  @Test
  fun `a word missing from the vocabulary is built from smaller pieces`() {
    val ids = tokenizer.encode("seeq")
    assertTrue("expected several pieces plus markers, got ${ids.size}", ids.size > 3)
  }
}
