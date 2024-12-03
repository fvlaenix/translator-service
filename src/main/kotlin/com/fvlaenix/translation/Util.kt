package com.fvlaenix.translation

object Util {
  fun splitWords(lines: List<String>, countWordsLimit: Int): List<List<String>> {
    val toTranslateBatches = mutableListOf<List<String>>()
    var buffer = mutableListOf<String>(lines.first())
    for (line in lines.drop(1)) {
      if (buffer.sumOf { it.split("\\s+".toRegex()).count() } + line.split("\\s+".toRegex()).count() > countWordsLimit) {
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