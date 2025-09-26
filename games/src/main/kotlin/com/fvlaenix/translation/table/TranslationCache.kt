package com.fvlaenix.translation.table

/**
 * Simple in-memory cache of original -> translated strings.
 *
 * The cache is populated from existing books and updated as new translations are produced.
 * Not thread-safe.
 */
class TranslationCache {
  private val cache: MutableMap<String, String> = mutableMapOf()

  /**
   * Returns an immutable snapshot of the current cache.
   */
  fun getCache(): Map<String, String> = cache.toMap()

  /**
   * Returns whether a translation exists for the given source text.
   *
   * @param key Original text.
   */
  fun contains(key: String): Boolean = cache.containsKey(key)

  /**
   * Returns a cached translation for [key], or `null` if absent.
   */
  operator fun get(key: String): String? = cache[key]

  /**
   * Puts or replaces a translation mapping.
   *
   * @param key Original text.
   * @param value Translated text.
   */
  fun put(key: String, value: String) {
    cache[key] = value
  }

  /**
   * Merges all entries from [sourceCache] into this cache, overwriting duplicates.
   */
  fun mergeFrom(sourceCache: TranslationCache) {
    cache.putAll(sourceCache.cache)
  }

  /**
   * Populates the cache from existing translated rows across the given [books].
   */
  fun buildFromBooks(books: List<TranslationBook>) {
    books.flatMap { book -> book.translationBook }
      .filter { it.translate != null }
      .forEach { cache[it.toTranslate] = it.translate!! }
  }

  /**
   * Clears all cached entries.
   */
  fun clear() {
    cache.clear()
  }

  /**
   * Returns the number of cached entries.
   */
  fun size(): Int = cache.size

  /**
   * Returns `true` if the cache has no entries.
   */
  fun isEmpty(): Boolean = cache.isEmpty()
}