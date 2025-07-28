package com.fvlaenix.translation

import com.fvlaenix.translation.table.TranslationBookService
import kotlin.io.path.Path
import kotlin.io.path.readLines

class NamesService(private val properties: Map<String, String>) {

  constructor(name: String) : this(
    parsePropertiesFile(Path(name).readLines())
  )

  companion object {
    /**
     * Parses properties file format, handling comments, empty lines, and escaped characters
     */
    private fun parsePropertiesFile(lines: List<String>): Map<String, String> {
      return lines
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") } // Skip empty lines and comments
        .mapNotNull { line ->
          val equalIndex = line.indexOf('=')
          if (equalIndex == -1) return@mapNotNull null

          val key = line.substring(0, equalIndex).trim()
            .replace("\\=", "=")
            .replace("\\:", ":")
          val value = line.substring(equalIndex + 1).trim()
            .replace("\\=", "=")
            .replace("\\:", ":")

          key to value
        }
        .toMap()
    }
  }

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