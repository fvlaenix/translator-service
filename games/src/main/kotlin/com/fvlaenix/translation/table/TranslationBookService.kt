package com.fvlaenix.translation.table

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import java.nio.file.Path

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

  suspend fun translate() = coroutineScope {
    ensureActive()
    validator.validateBooksOrThrow(books)
    processor.processBooks(books)
  }

  fun write(outputPath: Path) {
    repository.saveBooks(books, outputPath)
  }

  fun addToCache(otherService: TranslationBookService) {
    cache.mergeFrom(otherService.cache)
  }

  fun extractUniqueNames(): Set<String> {
    return nameExtractor.extractUniqueNames(books)
  }

  fun getCache(): Map<String, String> = cache.getCache()

  class KeyNotFoundException(val notFoundKey: String) : IllegalStateException("Can't found key for $notFoundKey")
}