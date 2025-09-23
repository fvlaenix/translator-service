package com.fvlaenix.translation.table

import com.fvlaenix.translation.systemdialog.ProvidersCollection

class TranslationValidator(
  private val dialogProvider: ProvidersCollection
) {

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

  fun validateBooks(books: List<TranslationBook>): List<String> {
    return books.flatMap { validateBook(it) }
  }

  fun validateBooksOrThrow(books: List<TranslationBook>) {
    val errors = validateBooks(books)
    if (errors.isNotEmpty()) {
      val distinctErrors = errors.distinct().joinToString("\n")
      throw IllegalStateException("A lot of keys not found. All written to err\n$distinctErrors")
    }
  }
}