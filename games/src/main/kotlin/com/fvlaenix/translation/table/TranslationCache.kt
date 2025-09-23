package com.fvlaenix.translation.table

class TranslationCache {
  private val cache: MutableMap<String, String> = mutableMapOf()

  fun getCache(): Map<String, String> = cache.toMap()

  fun contains(key: String): Boolean = cache.containsKey(key)

  operator fun get(key: String): String? = cache[key]

  fun put(key: String, value: String) {
    cache[key] = value
  }

  fun mergeFrom(sourceCache: TranslationCache) {
    cache.putAll(sourceCache.cache)
  }

  fun buildFromBooks(books: List<TranslationBook>) {
    books.flatMap { book -> book.translationBook }
      .filter { it.translate != null }
      .forEach { cache[it.toTranslate] = it.translate!! }
  }

  fun clear() {
    cache.clear()
  }

  fun size(): Int = cache.size

  fun isEmpty(): Boolean = cache.isEmpty()
}