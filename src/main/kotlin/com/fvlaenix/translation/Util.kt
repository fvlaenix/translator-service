package com.fvlaenix.translation

object Util {
  fun splitWords(lines: List<String>, countWordsLimit: Int): List<List<String>> {
    val toTranslateBatches = mutableListOf<List<String>>()
    var buffer = mutableListOf<String>()
    for (line in lines) {
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
}