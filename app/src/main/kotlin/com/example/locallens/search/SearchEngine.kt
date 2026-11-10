package com.example.locallens.search

import com.example.locallens.data.PhotoRecord
import com.example.locallens.data.VectorIndex
import com.example.locallens.ml.Embedder
import java.time.Instant
import java.time.ZoneId

/** True when a photo taken at [takenMs] passes this query's date filter. */
fun ParsedQuery.matches(takenMs: Long, zone: ZoneId = ZoneId.systemDefault()): Boolean {
  if (!hasDateFilter) return true
  val date = Instant.ofEpochMilli(takenMs).atZone(zone)
  return (month == null || date.monthValue == month) &&
    (day == null || date.dayOfMonth == day) &&
    (year == null || date.year == year)
}

/** Combines the date filter from the query text with vector similarity on the rest of it. */
class SearchEngine(private val index: VectorIndex, private val embedder: () -> Embedder) {

  suspend fun search(raw: String, topK: Int = 60): List<PhotoRecord> {
    val parsed = QueryParser.parse(raw)
    val accept = { record: PhotoRecord -> parsed.matches(record.takenMs) }
    if (parsed.text.isBlank()) {
      // A pure date query such as "august 5" lists that day's photos, newest first.
      if (!parsed.hasDateFilter) return emptyList()
      return index.filter(accept).sortedByDescending { it.takenMs }.take(topK)
    }
    val queryVector = embedder().embedText(parsed.text)
    return index.search(queryVector, topK, accept).map { it.record }
  }
}
