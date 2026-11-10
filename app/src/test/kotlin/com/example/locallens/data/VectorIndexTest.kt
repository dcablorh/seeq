package com.example.locallens.data

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VectorIndexTest {
  private fun tempFile(): File = File(Files.createTempDirectory("index").toFile(), "index.bin")

  private fun filled(): VectorIndex {
    val index = VectorIndex.load(tempFile(), dim = 2)
    index.add(PhotoRecord(1, 1000), floatArrayOf(1f, 0f))
    index.add(PhotoRecord(2, 2000), floatArrayOf(0f, 1f))
    index.add(PhotoRecord(3, 3000), floatArrayOf(0.8f, 0.6f))
    return index
  }

  @Test
  fun `search ranks by similarity`() {
    val ids = filled().search(floatArrayOf(1f, 0f), topK = 3).map { it.record.id }
    assertEquals(listOf(1L, 3L, 2L), ids)
  }

  @Test
  fun `search respects topK and the filter`() {
    val index = filled()
    assertEquals(1, index.search(floatArrayOf(1f, 0f), topK = 1).size)
    val ids = index.search(floatArrayOf(1f, 0f), topK = 3) { it.id != 1L }.map { it.record.id }
    assertEquals(listOf(3L, 2L), ids)
  }

  @Test
  fun `newestFirst orders by date taken`() {
    assertEquals(listOf(3L, 2L, 1L), filled().newestFirst().map { it.id })
  }

  @Test
  fun `duplicate ids are ignored`() {
    val index = filled()
    index.add(PhotoRecord(1, 9999), floatArrayOf(0f, 1f))
    assertEquals(3, index.size)
  }

  @Test
  fun `save then load keeps everything`() {
    val file = tempFile()
    val index = VectorIndex.load(file, dim = 2)
    index.add(PhotoRecord(7, 777), floatArrayOf(0f, 1f))
    index.save()

    val loaded = VectorIndex.load(file, dim = 2)
    assertEquals(1, loaded.size)
    assertTrue(loaded.contains(7))
    assertEquals(777L, loaded.filter { true }.single().takenMs)
    assertEquals(7L, loaded.search(floatArrayOf(0f, 1f), topK = 1).single().record.id)
  }

  @Test
  fun `a different dimension starts empty`() {
    val file = tempFile()
    VectorIndex.load(file, dim = 2).also { it.add(PhotoRecord(1, 1), floatArrayOf(1f, 0f)) }.save()
    assertEquals(0, VectorIndex.load(file, dim = 3).size)
  }

  @Test
  fun `a corrupt file starts empty`() {
    val file = tempFile()
    file.writeBytes(byteArrayOf(1, 2, 3))
    assertEquals(0, VectorIndex.load(file, dim = 2).size)
  }

  @Test
  fun `retainOnly drops photos that left the gallery`() {
    val index = filled()
    assertEquals(1, index.retainOnly(setOf(1L, 3L)))
    assertFalse(index.contains(2))
    assertEquals(listOf(1L, 3L), index.search(floatArrayOf(1f, 0f), topK = 5).map { it.record.id })
  }
}
