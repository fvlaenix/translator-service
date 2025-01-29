package com.fvlaenix.translation

import com.fvlaenix.translation.table.TranslationBookService

class NamesService(private val properties: Map<String, String>) {

  constructor(name: String) : this(
    NamesService::class.java.getResourceAsStream("/$name")!!.reader().readLines().associate {
      val splitted = it.split("=")
      splitted[0] to splitted[1]
    }
  )

  operator fun get(name: String): String {
    if (name == "") return ""
    return properties[name] ?: throw TranslationBookService.KeyNotFoundException(name)
  }

  /**
   * Returns translation of names what meets in this line
   */
  fun checkForName(s: String): Map<String, String> {
    return properties.entries
      .filter { s.contains(it.key, ignoreCase = true) }
      .associate { Pair(it.key, it.value) }
  }
}