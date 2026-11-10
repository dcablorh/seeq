package com.example.locallens.search

import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QueryParserTest {
  @Test
  fun `month and day after from`() {
    val parsed = QueryParser.parse("receipt from August 5")
    assertEquals("receipt", parsed.text)
    assertEquals(8, parsed.month)
    assertEquals(5, parsed.day)
    assertNull(parsed.year)
  }

  @Test
  fun `day first with ordinal and year`() {
    val parsed = QueryParser.parse("5th of august 2024")
    assertEquals("", parsed.text)
    assertEquals(8, parsed.month)
    assertEquals(5, parsed.day)
    assertEquals(2024, parsed.year)
  }

  @Test
  fun `month only needs a preposition`() {
    val parsed = QueryParser.parse("beach photos in March")
    assertEquals("beach photos", parsed.text)
    assertEquals(3, parsed.month)
    assertNull(parsed.day)
  }

  @Test
  fun `year only`() {
    val parsed = QueryParser.parse("birthday in 2023")
    assertEquals("birthday", parsed.text)
    assertNull(parsed.month)
    assertEquals(2023, parsed.year)
  }

  @Test
  fun `plain query has no date filter`() {
    val parsed = QueryParser.parse("blue car at night")
    assertEquals("blue car at night", parsed.text)
    assertFalse(parsed.hasDateFilter)
  }

  @Test
  fun `the word may alone is not a date`() {
    assertFalse(QueryParser.parse("you may like this flower").hasDateFilter)
  }

  @Test
  fun `impossible day is ignored`() {
    val parsed = QueryParser.parse("dog on august 45")
    assertFalse(parsed.hasDateFilter)
    assertEquals("dog on august 45", parsed.text)
  }

  @Test
  fun `date filter matches the right photos`() {
    val parsed = QueryParser.parse("receipt from August 5")
    val aug5 = java.time.LocalDate.of(2024, 8, 5).atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
    val aug6 = java.time.LocalDate.of(2024, 8, 6).atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
    assertTrue(parsed.matches(aug5, ZoneOffset.UTC))
    assertFalse(parsed.matches(aug6, ZoneOffset.UTC))
  }
}
