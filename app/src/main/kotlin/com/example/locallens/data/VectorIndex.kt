package com.example.locallens.data

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.PriorityQueue

class PhotoRecord(val id: Long, val takenMs: Long)

class ScoredPhoto(val record: PhotoRecord, val score: Float)

/**
 * In-memory store of unit-length photo embeddings with brute-force search, persisted to one file.
 * Vectors are unit length, so a dot product is the cosine similarity.
 */
class VectorIndex private constructor(private val file: File, val dim: Int) {
  private val records = ArrayList<PhotoRecord>()
  private val ids = HashSet<Long>()
  private var vectors = FloatArray(0)

  @get:Synchronized
  val size: Int
    get() = records.size

  @Synchronized
  fun contains(id: Long): Boolean = id in ids

  @Synchronized
  fun add(record: PhotoRecord, vector: FloatArray) {
    require(vector.size == dim) { "Expected $dim dims, got ${vector.size}" }
    if (!ids.add(record.id)) return
    val needed = (records.size + 1) * dim
    if (needed > vectors.size) {
      vectors = vectors.copyOf(maxOf(needed, vectors.size * 2, dim * 64))
    }
    vector.copyInto(vectors, records.size * dim)
    records.add(record)
  }

  /** Drops photos that are no longer in the gallery. Returns how many were removed. */
  @Synchronized
  fun retainOnly(keep: Set<Long>): Int {
    val removed = records.count { it.id !in keep }
    if (removed == 0) return 0
    val oldRecords = records.toList()
    val oldVectors = vectors
    records.clear()
    ids.clear()
    vectors = FloatArray(0)
    oldRecords.forEachIndexed { i, record ->
      if (record.id in keep) add(record, oldVectors.copyOfRange(i * dim, (i + 1) * dim))
    }
    return removed
  }

  @Synchronized
  fun filter(accept: (PhotoRecord) -> Boolean): List<PhotoRecord> = records.filter(accept)

  @Synchronized
  fun search(query: FloatArray, topK: Int, accept: (PhotoRecord) -> Boolean = { true }): List<ScoredPhoto> {
    require(query.size == dim) { "Expected $dim dims, got ${query.size}" }
    if (topK <= 0) return emptyList()
    val heap = PriorityQueue(topK + 1, compareBy<ScoredPhoto> { it.score })
    for (i in records.indices) {
      val record = records[i]
      if (!accept(record)) continue
      val base = i * dim
      var score = 0f
      for (j in 0 until dim) score += vectors[base + j] * query[j]
      if (heap.size < topK) {
        heap.add(ScoredPhoto(record, score))
      } else if (score > heap.peek()!!.score) {
        heap.poll()
        heap.add(ScoredPhoto(record, score))
      }
    }
    return heap.sortedByDescending { it.score }
  }

  /** Writes the index to a temp file and swaps it in, so a crash never leaves a half-written index. */
  @Synchronized
  fun save() {
    file.parentFile?.mkdirs()
    val tmp = File(file.parentFile, file.name + ".tmp")
    DataOutputStream(tmp.outputStream().buffered()).use { out ->
      out.writeInt(MAGIC)
      out.writeInt(VERSION)
      out.writeInt(dim)
      out.writeInt(records.size)
      for (i in records.indices) {
        out.writeLong(records[i].id)
        out.writeLong(records[i].takenMs)
        val base = i * dim
        for (j in 0 until dim) out.writeFloat(vectors[base + j])
      }
    }
    try {
      Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    } catch (e: IOException) {
      Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }
  }

  companion object {
    private const val MAGIC = 0x4C4C4958 // "LLIX"
    private const val VERSION = 1

    /** Loads the index, or returns an empty one if the file is missing, corrupt, or uses another dimension. */
    fun load(file: File, dim: Int): VectorIndex {
      val index = VectorIndex(file, dim)
      if (!file.exists()) return index
      try {
        DataInputStream(file.inputStream().buffered()).use { input ->
          if (input.readInt() != MAGIC || input.readInt() != VERSION || input.readInt() != dim) return index
          val count = input.readInt()
          val loaded = ArrayList<PhotoRecord>(count)
          val loadedVectors = FloatArray(count * dim)
          for (i in 0 until count) {
            loaded.add(PhotoRecord(input.readLong(), input.readLong()))
            val base = i * dim
            for (j in 0 until dim) loadedVectors[base + j] = input.readFloat()
          }
          for (record in loaded) index.ids.add(record.id)
          index.records.addAll(loaded)
          index.vectors = loadedVectors
        }
      } catch (e: IOException) {
        return VectorIndex(file, dim)
      }
      return index
    }
  }
}
