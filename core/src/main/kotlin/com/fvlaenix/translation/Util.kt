package com.fvlaenix.translation

/**
 * Small text utilities for splitting by words or symbol limits.
 */
object Util {
  /**
   * Splits a list of lines into batches constrained by a total word count limit.
   *
   * Aggregates consecutive lines into batches such that adding the next line would exceed
   * [countWordsLimit] words across the batch; when that happens, starts a new batch.
   * Words are detected using whitespace (`\s+`).
   *
   * @param lines Source lines to group; must contain at least one element.
   * @param countWordsLimit Maximum number of words allowed per batch; must be positive.
   * @return Ordered batches of lines, each intended to stay within the word limit.
   */
  fun splitWords(lines: List<String>, countWordsLimit: Int): List<List<String>> {
    val toTranslateBatches = mutableListOf<List<String>>()
    var buffer = mutableListOf<String>(lines.first())
    for (line in lines.drop(1)) {
      val wordsCount = buffer.sumOf { it.split("\\s+".toRegex()).count() } + line.split("\\s+".toRegex()).count()
      if (wordsCount > countWordsLimit) {
        toTranslateBatches.add(buffer)
        buffer = mutableListOf()
      }
      buffer.add(line)
    }
    if (buffer.isNotEmpty()) {
      toTranslateBatches.add(buffer)
    }
    return toTranslateBatches
  }

  /**
   * Splits a line into chunks constrained by a character limit.
   *
   * Splits the input by whitespace and greedily appends words to the current chunk while the
   * combined characters plus spaces do not exceed [countSymbolsLimit]; otherwise starts a new chunk.
   *
   * @param line Source text to split.
   * @param countSymbolsLimit Maximum number of characters per chunk; must be positive.
   * @return A list of chunks preserving word order.
   */
  fun splitSymbols(line: String, countSymbolsLimit: Int): List<String> {
    val result = mutableListOf<String>()
    val accumulator = mutableListOf<String>()
    val splitted = line.split("\\s+".toRegex())
    for (word in splitted) {
      if (accumulator.sumOf { it.length } + word.length + accumulator.size > countSymbolsLimit) {
        result.add(accumulator.joinToString(" "))
        accumulator.clear()
      }
      accumulator.add(word)
    }
    if (accumulator.isNotEmpty()) {
      result.add(accumulator.joinToString(" "))
    }
    return result
  }
}