package com.example.locallens.ml

import android.content.Context
import java.io.InputStream
import org.json.JSONObject

/**
 * OpenAI CLIP's byte-pair tokenizer. The text model only gives sensible vectors when it sees exactly the ids
 * CLIP was trained on, so this follows the reference implementation step for step.
 */
class ClipTokenizer(private val vocab: Map<String, Int>, merges: List<String>) {
  private val ranks: Map<Pair<String, String>, Int> = HashMap<Pair<String, String>, Int>().also { map ->
    merges.forEachIndexed { rank, line ->
      val parts = line.split(' ')
      if (parts.size == 2) map[parts[0] to parts[1]] = rank
    }
  }
  private val byteEncoder = byteToUnicode()
  private val cache = HashMap<String, List<String>>()

  /** Token ids for [text], wrapped in the start and end markers and cut to CLIP's 77-token limit. */
  fun encode(text: String): IntArray {
    val ids = ArrayList<Int>()
    ids.add(START)
    val cleaned = text.trim().replace(WHITESPACE, " ").lowercase()
    for (match in PATTERN.findAll(cleaned)) {
      val token = match.value.toByteArray(Charsets.UTF_8).joinToString("") { byteEncoder.getValue(it.toInt() and 0xFF).toString() }
      for (piece in bpe(token)) vocab[piece]?.let(ids::add)
      if (ids.size >= MAX_LENGTH - 1) break
    }
    val kept = ids.take(MAX_LENGTH - 1)
    return (kept + END).toIntArray()
  }

  private fun bpe(token: String): List<String> = cache.getOrPut(token) {
    var word = token.map { it.toString() }.toMutableList()
    word[word.lastIndex] = word.last() + "</w>"
    while (word.size > 1) {
      var best: Pair<String, String>? = null
      var bestRank = Int.MAX_VALUE
      for (i in 0 until word.size - 1) {
        val pair = word[i] to word[i + 1]
        val rank = ranks[pair] ?: continue
        if (rank < bestRank) {
          bestRank = rank
          best = pair
        }
      }
      val (first, second) = best ?: break
      val merged = ArrayList<String>(word.size)
      var i = 0
      while (i < word.size) {
        if (i < word.size - 1 && word[i] == first && word[i + 1] == second) {
          merged.add(first + second)
          i += 2
        } else {
          merged.add(word[i])
          i++
        }
      }
      word = merged
    }
    word
  }

  companion object {
    const val START = 49406
    const val END = 49407
    const val MAX_LENGTH = 77

    private val WHITESPACE = Regex("\\s+")
    private val PATTERN =
      Regex("""<\|startoftext\|>|<\|endoftext\|>|'s|'t|'re|'ve|'m|'ll|'d|[\p{L}]+|[\p{N}]|[^\s\p{L}\p{N}]+""", RegexOption.IGNORE_CASE)

    fun load(vocabJson: InputStream, mergesTxt: InputStream): ClipTokenizer {
      val json = JSONObject(vocabJson.bufferedReader().use { it.readText() })
      val vocab = HashMap<String, Int>(json.length() * 2)
      for (key in json.keys()) vocab[key] = json.getInt(key)
      // The first line is a version header, not a merge.
      val merges = mergesTxt.bufferedReader().use { it.readLines() }.drop(1)
      return ClipTokenizer(vocab, merges)
    }

    fun fromAssets(context: Context): ClipTokenizer =
      context.assets.open("clip/vocab.json").use { vocab ->
        context.assets.open("clip/merges.txt").use { merges -> load(vocab, merges) }
      }

    /** CLIP's reversible byte-to-printable-character table, so every byte can be written as a vocabulary symbol. */
    private fun byteToUnicode(): Map<Int, Char> {
      val printable = (('!'.code..'~'.code) + ('¡'.code..'¬'.code) + ('®'.code..'ÿ'.code)).toMutableList()
      val chars = printable.toMutableList()
      var extra = 0
      for (b in 0 until 256) {
        if (b !in printable) {
          printable.add(b)
          chars.add(256 + extra)
          extra++
        }
      }
      return printable.indices.associate { printable[it] to chars[it].toChar() }
    }
  }
}
