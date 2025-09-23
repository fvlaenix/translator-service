package com.fvlaenix.translation.table

import com.fvlaenix.translation.NamesService
import com.fvlaenix.translation.systemdialog.Bo10FNameDialogProvider
import com.fvlaenix.translation.systemdialog.ElmiaNameDialogProvider
import com.fvlaenix.translation.systemdialog.ProvidersCollection
import com.fvlaenix.translation.systemdialog.SylphNameDialogProvider
import com.fvlaenix.translation.translator.DialogTranslation
import com.fvlaenix.translation.translator.TextTranslation
import com.fvlaenix.translation.translator.Translator
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import java.util.logging.Level
import java.util.logging.Logger

private val LOG = Logger.getLogger(TranslationBookProcessor::class.simpleName)

class TranslationBookProcessor(
  private val translator: Translator,
  private val namesService: NamesService,
  private val dialogProvider: ProvidersCollection,
  private val cache: TranslationCache
) {

  suspend fun processBook(book: TranslationBook) = coroutineScope {
    ensureActive()
    var currentLine = 0
    while (currentLine < book.translationBook.size) {
      ensureActive()
      val finishLine = getFinishLineForSubBook(book, currentLine)
      processSubbook(book, currentLine, finishLine)
      currentLine = finishLine
    }

    validateNameTranslations(book)
  }

  suspend fun processBooks(books: List<TranslationBook>) = coroutineScope {
    books.forEachIndexed { index, book ->
      println("Translate book ${book.path}")
      try {
        processBook(book)
      } catch (e: Exception) {
        throw Exception("Exception while processing book ${book.name}", e)
      }
    }
  }

  private fun getFinishLineForSubBook(book: TranslationBook, startLine: Int): Int {
    var currentLine = startLine
    var linesWithTranslation = 0
    while (currentLine < book.translationBook.size && linesWithTranslation < MAX_COUNT_LINES) {
      val currentString = book.translationBook[currentLine]
      if (currentString.translate == null) {
        linesWithTranslation++
      }
      currentLine++
    }
    return currentLine
  }

  private suspend fun processSubbook(book: TranslationBook, startLine: Int, endLine: Int) {
    val linesTalk = mutableMapOf<Int, ProvidersCollection.ProvidersResult>()
    val linesWithTranslation = book.translationBook.subList(startLine, endLine)
      .mapIndexed { index, translationData -> Pair(index, translationData) }

    linesWithTranslation.forEach { (_, translationData) ->
      try {
        val translation = namesService[translationData.toTranslate]
        translationData.translate = translation
        return@forEach
      } catch (_: TranslationBookService.KeyNotFoundException) {
      }

      if (translationData.translate != null) return@forEach

      if (cache.contains(translationData.toTranslate)) {
        translationData.translate = cache[translationData.toTranslate]
      }
    }

    if (linesWithTranslation.isEmpty()) return

    val lines = linesWithTranslation.mapIndexed { index, (number, translateData) ->
      val line = translateData.toTranslate
      val startResult = try {
        dialogProvider.get(line)
      } catch (e: Exception) {
        throw Exception("Exception while processing line ${startLine + number + 1}", e)
      }
      linesTalk[index] = startResult

      when (translateData) {
        is TranslationData.TranslationSimpleData -> {
          when (val firstSystem = startResult.system.firstOrNull()) {
            is Bo10FNameDialogProvider.Bo10FDialog -> DialogTranslation(
              firstSystem.name,
              startResult.result,
              translateData.translate
            )

            is ElmiaNameDialogProvider.ElmiaDialog -> DialogTranslation(
              firstSystem.name,
              startResult.result,
              translateData.translate
            )

            is SylphNameDialogProvider.SylphDialog -> DialogTranslation(
              firstSystem.name,
              startResult.result,
              translateData.translate
            )

            else -> TextTranslation(startResult.result, translateData.translate)
          }
        }

        is TranslationData.TranslationDataWithNameData -> DialogTranslation(
          translateData.name,
          startResult.result,
          translateData.translate
        )
      }
    }

    val result = try {
      translator.translate(lines)
    } catch (e: Exception) {
      LOG.log(Level.SEVERE, "Exception during translation of book ${book.name}", e)
      null
    }

    if (result != null) {
      linesWithTranslation.zip(result).forEachIndexed { index, pair ->
        val translation = pair.second.translation
        if (translation != null) {
          var resultLine = translation
          if (resultLine.startsWith("\"") && resultLine.endsWith("\"")) {
            resultLine = resultLine.substring(1, resultLine.length - 1).trim()
          }
          if (linesTalk.containsKey(index)) {
            resultLine = dialogProvider.restore(resultLine, linesTalk[index]!!)
          }
          cache.put(pair.first.second.toTranslate, resultLine)
          pair.first.second.translate = resultLine
        }
      }
    }
  }

  private fun validateNameTranslations(book: TranslationBook) {
    book.translationBook.forEachIndexed { index, translationData ->
      val namesTranslation = filterMapKeys(namesService.checkForName(translationData.toTranslate))
      namesTranslation.forEach { toTranslateName, translatedName ->
        if (translatedName == "Me") return@forEach
        if (translatedName == "Volunteer Army") return@forEach
        if (translatedName == "Common") return@forEach

        if (translationData.translate?.contains(translatedName, ignoreCase = true) == false) {
          println("Name \"$translatedName\" should be inside line \"${translationData.translate}\", but it isn't. Book: ${book.path}, line: $index")
        }
      }
    }
  }

  private fun filterMapKeys(map: Map<String, String>): Map<String, String> {
    val keys = map.keys.toList()
    val filteredKeys = keys.filter { key ->
      keys.none { other ->
        other != key && other.contains(key)
      }
    }
    return map.filterKeys { it in filteredKeys }
  }

  companion object {
    private const val MAX_COUNT_LINES = 55
  }
}