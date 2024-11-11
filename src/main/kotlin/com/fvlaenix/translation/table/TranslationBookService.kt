package com.fvlaenix.translation.table

import com.fvlaenix.translation.FilesUtil
import com.fvlaenix.translation.GPTUtil
import com.fvlaenix.translation.NamesService
import com.fvlaenix.translation.gpt.GPT
import com.fvlaenix.translation.systemdialog.Bo10FNameDialogProvider
import com.fvlaenix.translation.systemdialog.ElmiaNameDialogProvider
import com.fvlaenix.translation.systemdialog.ProvidersCollection
import com.fvlaenix.translation.systemdialog.SylphNameDialogProvider
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import java.nio.file.Path
import kotlin.io.path.*

class TranslationBookService(
  path: Path,
  language: String,
  gameId: String,
  private val namesService: NamesService = NamesService("${gameId}_$language.properties"),
  private val dialogProvider: ProvidersCollection = ProvidersCollection.defaultProvidersCollection(namesService)
) {
  private val books : List<TranslationBook> = FilesUtil.getPaths(path, filter = { it.extension == "xlxs" || it.extension == "xlsx" })
    .map { TranslationBook(it.inputStream(), path.relativize(it)) }
  val cache : MutableMap<String, String> = books
    .flatMap { book -> book.translationBook }
    .filter { it.translate != null }
    .associate { it.toTranslate to it.translate!! }
    .toMutableMap()
  
  suspend fun translate() = coroutineScope {
    ensureActive()
    checkNames()
    val countBooks = 0
    books.forEachIndexed { index, book ->
      println("Translate book ${book.path}")
      try {
        translateBook(book)
      } catch (e: Exception) {
        throw Exception("Exception while book ${book.name}", e)
      }
      if (index > countBooks && countBooks != 0) return@coroutineScope
    }
  }

  private suspend fun translateBook(book: TranslationBook) = coroutineScope {
    ensureActive()
    var currentLine = 0
    val countLines = 0
    while (currentLine < book.translationBook.size) {
      ensureActive()
      val finishLine = getFinishLineForSubBook(book, currentLine)
      translateSubbook(book, currentLine, finishLine)
      currentLine = finishLine
      if (currentLine > countLines && countLines != 0) return@coroutineScope
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

  suspend fun translateSubbook(book: TranslationBook, startLine: Int, endLine: Int) {
    val linesTalk = mutableMapOf<Int, ProvidersCollection.ProvidersResult>()
    val linesWithTranslation = book.translationBook.subList(startLine, endLine)
      .mapIndexed { index, translationData -> Pair(index, translationData) }
    linesWithTranslation.forEach {
      if (it.second.translate != null) return@forEach
      if (cache.containsKey(it.second.toTranslate)) {
        it.second.translate = cache[it.second.toTranslate]
      }
    }
    if (linesWithTranslation.isEmpty()) return
    val lines: List<GPT.Translation> = linesWithTranslation.mapIndexed { index, (number, translateData) ->
      val line = translateData.toTranslate
      val startResult = try {
        dialogProvider.get(line)
      } catch (e: Exception) {
        throw Exception("Exception while ${startLine + number + 1} line", e)
      }
      linesTalk[index] = startResult
      when (translateData) {
        is TranslationData.TranslationSimpleData -> {
          when (val firstSystem = startResult.system.firstOrNull()) {
            is Bo10FNameDialogProvider.Bo10FDialog -> GPT.DialogTranslation(firstSystem.name, startResult.result, translateData.translate)
            is ElmiaNameDialogProvider.ElmiaDialog -> GPT.DialogTranslation(firstSystem.name, startResult.result, translateData.translate)
            is SylphNameDialogProvider.SylphDialog -> GPT.DialogTranslation(firstSystem.name, startResult.result, translateData.translate)
            else -> GPT.TextTranslation(startResult.result, translateData.translate)
          }
        }
        is TranslationData.TranslationDataWithNameData -> GPT.DialogTranslation(translateData.name, startResult.result, translateData.translate)
      }
    }
    val result = try {
      GPT.standardRequest(lines)
    } catch (e: GPTUtil.GPTLinesNotMatchException) {
      null
    }
    if (result != null) {
      linesWithTranslation.zip(result).forEachIndexed { index, pair ->
        var resultLine = pair.second.translation!!
        if (resultLine.startsWith("\"") && resultLine.endsWith("\"")) {
          resultLine = resultLine.substring(1, resultLine.length - 1).trim()
        }
        if (linesTalk.containsKey(index)) {
          resultLine = dialogProvider.restore(resultLine, linesTalk[index]!!)
        }
        cache[pair.first.second.toTranslate] = resultLine
        pair.first.second.translate = resultLine
      }
    }
  }

  private fun checkNames() {
    val notFoundKeys = mutableListOf<String>()
    books.forEachIndexed books@{ _, book ->
      notFoundKeys.addAll(book.checkNames(dialogProvider))
    }
    if (notFoundKeys.isNotEmpty()) {
      Path.of("output.txt").writeLines(notFoundKeys.distinct())
      throw IllegalStateException("A lot of keys not found. All written to output.txt")
    }
  }

  fun write(path: Path) {
    books.forEach {
      it.write(path)
    }
  }

  class KeyNotFoundException(val notFoundKey: String) : IllegalStateException("Can't found key for $notFoundKey")

  fun addToCache(anotherBookService: TranslationBookService) {
    cache.putAll(anotherBookService.cache)
  }
}