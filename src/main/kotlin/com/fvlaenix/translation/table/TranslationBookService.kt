package com.fvlaenix.translation.table

import com.fvlaenix.translation.FilesUtil
import com.fvlaenix.translation.NamesService
import com.fvlaenix.translation.systemdialog.Bo10FNameDialogProvider
import com.fvlaenix.translation.systemdialog.ElmiaNameDialogProvider
import com.fvlaenix.translation.systemdialog.ProvidersCollection
import com.fvlaenix.translation.systemdialog.SylphNameDialogProvider
import com.fvlaenix.translation.textmodel.OpenAIServiceImpl
import com.fvlaenix.translation.translator.DialogTranslation
import com.fvlaenix.translation.translator.OpenAIGPTTranslator
import com.fvlaenix.translation.translator.TextTranslation
import com.fvlaenix.translation.translator.Translator
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import java.nio.file.Path
import kotlin.io.path.inputStream
import kotlin.io.path.writeLines

class TranslationBookService(
  path: Path,
  language: String,
  gameId: String,
  private val namesService: NamesService = NamesService("${gameId}_$language.properties"),
  private val dialogProvider: ProvidersCollection = ProvidersCollection.defaultProvidersCollection(namesService),
  private val translator: Translator = OpenAIGPTTranslator(OpenAIServiceImpl())
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

  private fun filterMapKeys(map: Map<String, String>): Map<String, String> {
    val keys = map.keys.toList() // Получаем список ключей
    val filteredKeys = keys.filter { key ->
      // Проверяем, что ключ не является подстрокой другого ключа
      keys.none { other ->
        other != key && other.contains(key)
      }
    }
    // Создаем новую мапу только с отфильтрованными ключами
    return map.filterKeys { it in filteredKeys }
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
    // check translation for critical names
    book.translationBook.forEachIndexed { index, translationData ->
      val namesTranslation = filterMapKeys(namesService.checkForName(translationData.toTranslate))
      namesTranslation.forEach { toTranslateName, translatedName ->
        // TODO remove somehow exceptions
        if (translatedName == "Me") return@forEach
        if (translatedName == "Volunteer Army") return@forEach
        if (translatedName == "Common") return@forEach
        if (translationData.translate?.contains(translatedName, ignoreCase = true) == false) {
          println("Name \"$translatedName\" should be inside line \"${translationData.translate}\", but it isn't. Book: ${book.path}, line: $index")
        }
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

  suspend fun translateSubbook(book: TranslationBook, startLine: Int, endLine: Int) {
    val linesTalk = mutableMapOf<Int, ProvidersCollection.ProvidersResult>()
    val linesWithTranslation = book.translationBook.subList(startLine, endLine)
      .mapIndexed { index, translationData -> Pair(index, translationData) }
    linesWithTranslation.forEach {
      try {
        val translation = namesService[it.second.toTranslate]
        it.second.translate = translation
        return@forEach
      } catch (_: KeyNotFoundException) {
        // ignore
      }

      if (it.second.translate != null) return@forEach

      if (cache.containsKey(it.second.toTranslate)) {
        it.second.translate = cache[it.second.toTranslate]
      }
    }
    if (linesWithTranslation.isEmpty()) return
    val lines = linesWithTranslation.mapIndexed { index, (number, translateData) ->
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
            is Bo10FNameDialogProvider.Bo10FDialog -> DialogTranslation(firstSystem.name, startResult.result, translateData.translate)
            is ElmiaNameDialogProvider.ElmiaDialog -> DialogTranslation(firstSystem.name, startResult.result, translateData.translate)
            is SylphNameDialogProvider.SylphDialog -> DialogTranslation(firstSystem.name, startResult.result, translateData.translate)
            else -> TextTranslation(startResult.result, translateData.translate)
          }
        }
        is TranslationData.TranslationDataWithNameData -> DialogTranslation(translateData.name, startResult.result, translateData.translate)
      }
    }
    val result = try {
      translator.translate(lines)
    } catch (e: Exception) {
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
          cache[pair.first.second.toTranslate] = resultLine
          pair.first.second.translate = resultLine
        }
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
