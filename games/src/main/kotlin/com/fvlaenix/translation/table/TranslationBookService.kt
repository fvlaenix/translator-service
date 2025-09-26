package com.fvlaenix.translation.table

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import java.nio.file.Path

/**
 * Orchestrates loading, validating, translating, caching, and saving translation books.
 *
 * The service builds an in-memory cache from loaded books to speed up subsequent runs.
 * Use [translate] to process books, then [write] to persist results.
 *
 * @see TranslationBookRepository
 * @see TranslationBookProcessor
 */
class TranslationBookService(
  private val config: TranslationConfig
) {
  private val repository = TranslationBookRepository()
  private val cache = TranslationCache()
  private val validator = TranslationValidator(config.dialogProvider)
  private val nameExtractor = NameExtractor(config.dialogProvider)
  private val processor = TranslationBookProcessor(
    translator = config.translator,
    namesService = config.namesService,
    dialogProvider = config.dialogProvider,
    cache = cache
  )

  private var books: List<TranslationBook> = emptyList()

  init {
    loadBooks()
    initializeCache()
  }

  private fun loadBooks() {
    books = repository.loadBooks(config.path)
  }

  private fun initializeCache() {
    cache.buildFromBooks(books)
  }

  /**
   * Translates all loaded books in the configured path.
   *
   * Validates input rows before translation. Cancelling the coroutine cancels the operation.
   */
  suspend fun translate() = coroutineScope {
    ensureActive()
    validator.validateBooksOrThrow(books)
    processor.processBooks(books)
  }

  /**
   * Writes the current books to the given output directory using the repository.
   *
   * @param outputPath Directory to store generated XLSX files.
   */
  fun write(outputPath: Path) {
    repository.saveBooks(books, outputPath)
  }

  /**
   * Merges cache entries from another service instance into this service.
   *
   * Existing keys are overwritten by values from [otherService].
   *
   * @param otherService Source of cache entries to merge.
   */
  fun addToCache(otherService: TranslationBookService) {
    cache.mergeFrom(otherService.cache)
  }

  /**
   * Extracts a set of unique character names encountered in all loaded books.
   *
   * The result may be used for pre-translation of name dictionaries.
   *
   * @return Unique names found across all rows.
   */
  fun extractUniqueNames(): Set<String> {
    return nameExtractor.extractUniqueNames(books)
  }

  /**
   * Returns an immutable snapshot of the current translation cache.
   *
   * @return Map of source text to translated text.
   */
  fun getCache(): Map<String, String> = cache.getCache()

  /**
   * Indicates that a required key for name resolution could not be found.
   *
   * @property notFoundKey The missing key value.
   */
  class KeyNotFoundException(val notFoundKey: String) : IllegalStateException("Can't found key for $notFoundKey")
}