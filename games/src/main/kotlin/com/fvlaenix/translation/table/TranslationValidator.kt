package com.fvlaenix.translation.table

import com.fvlaenix.translation.systemdialog.ProvidersCollection

/**
 * Validates that system-dialog-dependent keys can be resolved for untranslated rows.
 *
 * Uses [ProvidersCollection] to parse lines and reports missing name keys, aggregating errors
 * across books.
 */
class TranslationValidator(
  private val dialogProvider: ProvidersCollection
) {

  /**
   * Validates a single [book] and returns a list of missing keys with context.
   *
   * The result contains alternating entries of the form `bookName|lineNumber` and the actual
   * missing key value.
   *
   * @param book Book to validate.
   * @return Flat list describing all missing keys in the book.
   */
  fun validateBook(book: TranslationBook): List<String> {
    val notFoundKeys = mutableListOf<String>()

    book.translationBook.forEachIndexed { dataIndex, data ->
      if (data.translate != null) return@forEachIndexed
      try {
        dialogProvider.get(data.toTranslate)
      } catch (e: TranslationBookService.KeyNotFoundException) {
        notFoundKeys.add("${book.name}|${dataIndex + 1}")
        notFoundKeys.add(e.notFoundKey)
      }
    }

    return notFoundKeys
  }

  /**
   * Validates multiple books and returns a combined error list.
   *
   * @param books Books to validate.
   * @return Flat list with entries produced by [validateBook] for all books.
   */
  fun validateBooks(books: List<TranslationBook>): List<String> {
    return books.flatMap { validateBook(it) }
  }

  /**
   * Validates [books] and throws when there are unresolved keys.
   *
   * Aggregates distinct errors into the exception message, one per line.
   *
   * @param books Books to validate.
   * @throws IllegalStateException When any missing keys are detected.
   */
  fun validateBooksOrThrow(books: List<TranslationBook>) {
    val errors = validateBooks(books)
    if (errors.isNotEmpty()) {
      val distinctErrors = errors.distinct().joinToString("\n")
      throw IllegalStateException("A lot of keys not found. All written to err\n$distinctErrors")
    }
  }
}