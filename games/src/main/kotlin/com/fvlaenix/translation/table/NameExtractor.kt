package com.fvlaenix.translation.table

import com.fvlaenix.translation.systemdialog.Bo10FNameDialogProvider
import com.fvlaenix.translation.systemdialog.ElmiaNameDialogProvider
import com.fvlaenix.translation.systemdialog.ProvidersCollection
import com.fvlaenix.translation.systemdialog.SylphNameDialogProvider

class NameExtractor(
  private val dialogProvider: ProvidersCollection
) {

  fun extractUniqueNames(books: List<TranslationBook>): Set<String> {
    val uniqueNames = mutableSetOf<String>()

    books.forEach { book ->
      book.translationBook.forEach { translationData ->
        try {
          val result = dialogProvider.get(translationData.toTranslate)
          result.system.forEach { systemDialog ->
            when (systemDialog) {
              is ElmiaNameDialogProvider.ElmiaDialog -> {
                val originalText = translationData.toTranslate
                val positionOfSplit = originalText.indexOfFirst { it == '\n' }
                if (positionOfSplit != -1) {
                  val originalName = originalText.split("\n")[0]
                  uniqueNames.add(originalName)
                }
              }

              is SylphNameDialogProvider.SylphDialog -> {
                val originalText = translationData.toTranslate
                val match = SylphNameDialogProvider.REGEX.find(originalText)
                match?.let {
                  val originalName = it.groups[1]?.value
                  originalName?.let { name -> uniqueNames.add(name) }
                }
              }

              is Bo10FNameDialogProvider.Bo10FDialog -> {
                val originalText = translationData.toTranslate
                val match = Bo10FNameDialogProvider.REGEX.find(originalText)
                match?.let {
                  val originalName = it.groups[0]?.value?.trim()
                  originalName?.let { name -> uniqueNames.add(name) }
                }
              }

              else -> throw Exception("Unknown dialog type ${systemDialog::class.simpleName}")
            }
          }
        } catch (e: TranslationBookService.KeyNotFoundException) {
          uniqueNames.add(e.notFoundKey)
        } catch (e: Exception) {
          return@forEach
        }
      }
    }

    return uniqueNames
  }
}