package com.example.locallens.search

/** A search query split into the part to embed and an optional date filter. */
data class ParsedQuery(
  val text: String,
  val month: Int? = null,
  val day: Int? = null,
  val year: Int? = null,
) {
  val hasDateFilter: Boolean get() = month != null || year != null
}

/**
 * Pulls dates such as "august 5", "5th of august 2024", "in march" or "in 2023" out of a query, because
 * embeddings do not match dates reliably. The rest of the query is what gets embedded.
 */
object QueryParser {
  private const val MONTH =
    "(january|february|march|april|may|june|july|august|september|october|november|december|" +
      "jan|feb|mar|apr|jun|jul|aug|sept|sep|oct|nov|dec)"
  private val options = setOf(RegexOption.IGNORE_CASE)

  private val monthDay = Regex("""\b(?:(?:on|from)\s+)?$MONTH\.?\s+(\d{1,2})(?:st|nd|rd|th)?(?:,?\s+(\d{4}))?\b""", options)
  private val dayMonth = Regex("""\b(?:(?:on|from)\s+)?(\d{1,2})(?:st|nd|rd|th)?\s+(?:of\s+)?$MONTH\b\.?(?:,?\s+(\d{4}))?""", options)
  private val monthOnly = Regex("""\b(?:in|during|from)\s+$MONTH\b\.?(?:\s+(\d{4}))?""", options)
  private val yearOnly = Regex("""\b(?:in|from|during)\s+((?:19|20)\d{2})\b""", options)

  fun parse(query: String): ParsedQuery {
    val q = query.trim()
    monthDay.find(q)?.let { m ->
      build(q, m, month = m.groupValues[1], day = m.groupValues[2], year = m.groupValues[3])?.let { return it }
    }
    dayMonth.find(q)?.let { m ->
      build(q, m, month = m.groupValues[2], day = m.groupValues[1], year = m.groupValues[3])?.let { return it }
    }
    monthOnly.find(q)?.let { m ->
      build(q, m, month = m.groupValues[1], day = "", year = m.groupValues[2])?.let { return it }
    }
    yearOnly.find(q)?.let { m ->
      build(q, m, month = "", day = "", year = m.groupValues[1])?.let { return it }
    }
    return ParsedQuery(text = q)
  }

  private fun build(q: String, match: MatchResult, month: String, day: String, year: String): ParsedQuery? {
    val dayValue = day.toIntOrNull()
    if (dayValue != null && dayValue !in 1..31) return null
    val rest = q.removeRange(match.range).replace(Regex("""\s+"""), " ").trim()
    return ParsedQuery(
      text = rest,
      month = monthNumber(month),
      day = dayValue,
      year = year.toIntOrNull(),
    )
  }

  private fun monthNumber(name: String): Int? {
    if (name.isEmpty()) return null
    val index = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
      .indexOf(name.lowercase().take(3))
    return if (index >= 0) index + 1 else null
  }
}
