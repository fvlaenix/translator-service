import com.fvlaenix.translation.FilesUtil
import com.fvlaenix.translation.GPTUtil
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import java.nio.file.Path
import kotlin.io.path.*

val ICON_FOUND_REGEX = "^\\\\[I|i]\\[\\d+]".toRegex()

const val MAX_COUNT_LINES = 10

val NAMES_BO_REGEX = "\\\\n<([^>]+)>".toRegex()

class TranslationBookService(
  path: Path,
  private val sourceColumn: Int = 0,
  private val targetColumn: Int = 1,
  language: String,
  gameId: String,
  private val model: String
) {
  private val books : List<TranslationBook> = FilesUtil.getPaths(path, filter = { it.extension == "xlxs" })
    .map { TranslationBook(path.inputStream(), path.relativize(it), sourceColumn, targetColumn) }
  private val cache : MutableMap<String, String> = books
    .flatMap { book -> book.translationBook }
    .filter { it.translate != null }
    .associate { it.toTranslate to it.translate!! }
    .toMutableMap()
  private val prompt = TranslationBookService::class.java.getResourceAsStream("/prompt_$language.txt")!!.reader().readText()
  private val namesService = NamesService("${gameId}_$language.properties")

  suspend fun translate() = coroutineScope {
    ensureActive()
    checkNames()
    val countBooks = 0
    books.forEachIndexed { index, book ->
      println("Translate book ${book.name}")
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

  companion object {
    data class StartResult(val resultLine: String, val start: String?)

    fun getStart(namesService: NamesService, line: String): StartResult {
      var resultStart: String? = null
      var resultLine = line
      var isChanged = true
      while (isChanged) {
        isChanged = false

        // name Elmia found
        val positionOfOpen = resultLine.indexOfFirst { it == '「' }
        val positionOfSplit = resultLine.indexOfFirst { it == '\n' }
        if (positionOfSplit + 1 == positionOfOpen && positionOfSplit != -1) {
          val name = resultLine.split("\n")[0]
          val replacement = namesService[name]
          if (replacement == null) {
            throw KeyNotFoundException(name)
          } else {
            resultStart = (resultStart ?: "") + "$replacement\n"
          }
          val isLastSymbolQuot = resultLine.indexOfLast { it == '」' } == resultLine.length - 1
          resultLine = "「" + resultLine.substring(positionOfOpen + 1, resultLine.length - if (isLastSymbolQuot) 1 else 0) + "」"
          isChanged = true
          continue
        }

        // line separator found
        if (resultLine.contains("[\r\n]".toRegex())) {
          resultLine = resultLine.replace("\r", "")
          resultLine = resultLine.replace("\n", " ")
          isChanged = true
          continue
        }

        // name Bo found
        val nameFounder = NAMES_BO_REGEX.find(resultLine)
        if (nameFounder != null) {
          val group = nameFounder.groups[1]!!
          val replacement = namesService[group.value]
          if (replacement == null) {
            throw KeyNotFoundException(group.value)
          } else {
            resultStart = (resultStart ?: "") + "\\n<${replacement}>"
          }
          resultLine =
            "\"" + resultLine.substring(0, nameFounder.range.first) + resultLine.substring(nameFounder.range.last + 1) + "\""
          isChanged = true
          continue
        }

        // icon found
        val iconFounder = ICON_FOUND_REGEX.find(resultLine)
        if (iconFounder != null) {
          val group = iconFounder.groups[0]!!
          resultStart = (resultStart ?: "") + group.value
          resultLine =
            resultLine.substring(0, iconFounder.range.first) + resultLine.substring(iconFounder.range.last + 1)
          isChanged = true
          continue
        }
      }

      return StartResult(resultLine, resultStart)
    }
  }

  suspend fun translateSubbook(book: TranslationBook, startLine: Int, endLine: Int) {
    val linesTalk = mutableMapOf<Int, String>()
    val linesWithTranslation = book.translationBook.subList(startLine, endLine)
      .mapIndexed { index, translationData -> Pair(index, translationData) }
      .filter {
        if (it.second.translate != null) false
        else {
          if (cache.containsKey(it.second.toTranslate)) {
            it.second.translate = cache[it.second.toTranslate]
            false
          } else {
            true
          }
        }
      }
    if (linesWithTranslation.isEmpty()) return
    val lines = linesWithTranslation.mapIndexed { index, untranslated ->
      val line = untranslated.second.toTranslate
      val startResult = try {
        getStart(namesService, line)
      } catch (e: Exception) {
        throw Exception("Exception while ${startLine + untranslated.first + 1} line", e)
      }
      if (startResult.start != null) {
        linesTalk[index] = startResult.start
      }
      startResult.resultLine
    }
    val result = try {
      GPTUtil.translate(prompt, model, lines)
    } catch (e: GPTUtil.GPTLinesNotMatchException) {
      null
    }
    if (result != null) {
      linesWithTranslation.zip(result).forEachIndexed { index, pair ->
        var resultLine = pair.second
        if (resultLine.startsWith("\"") && resultLine.endsWith("\"")) {
          resultLine = resultLine.substring(1, resultLine.length - 1).trim()
        }
        if (linesTalk.containsKey(index)) {
          resultLine = "${linesTalk[index]!!}${pair.second}"
        }
        cache[pair.first.second.toTranslate] = resultLine
        pair.first.second.translate = resultLine
      }
    }
  }

  fun checkNames() {
    val notFoundKeys = mutableListOf<String>()
    books.forEachIndexed books@{ _, book ->
      book.translationBook.forEachIndexed data@{ dataIndex, data ->
        if (data.translate != null) return@data
        try {
          getStart(namesService, data.toTranslate)
        } catch (e: KeyNotFoundException) {
          notFoundKeys.add("${book.name}|${dataIndex + 1}")
          notFoundKeys.add(e.notFoundKey)
        }
      }
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
}