import com.fvlaenix.translation.FilesUtil
import com.fvlaenix.translation.GPTUtil
import com.fvlaenix.translation.NamesService
import com.fvlaenix.translation.TranslationBook
import com.fvlaenix.translation.systemdialog.ProvidersCollection
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import java.nio.file.Path
import kotlin.io.path.*

const val MAX_COUNT_LINES = 10

class TranslationBookService(
  path: Path,
  private val sourceColumn: Int = 0,
  private val targetColumn: Int = 1,
  language: String,
  gameId: String,
  private val model: String,
  private val namesService: NamesService = NamesService("${gameId}_$language.properties"),
  private val dialogProvider: ProvidersCollection = ProvidersCollection.defaultProvidersCollection(namesService)
) {
  private val books : List<TranslationBook> = FilesUtil.getPaths(path, filter = { it.extension == "xlxs" || it.extension == "xlsx" })
    .map { TranslationBook(it.inputStream(), path.relativize(it), sourceColumn, targetColumn) }
  private val cache : MutableMap<String, String> = books
    .flatMap { book -> book.translationBook }
    .filter { it.translate != null }
    .associate { it.toTranslate to it.translate!! }
    .toMutableMap()
  private val prompt = TranslationBookService::class.java.getResourceAsStream("/prompt_$language.txt")!!.reader().readText()

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
    val lines: List<String> = linesWithTranslation.mapIndexed { index, untranslated ->
      val line = untranslated.second.toTranslate
      val startResult = try {
        dialogProvider.get(line)
      } catch (e: Exception) {
        throw Exception("Exception while ${startLine + untranslated.first + 1} line", e)
      }
      linesTalk[index] = startResult
      startResult.result
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
      book.translationBook.forEachIndexed data@{ dataIndex, data ->
        if (data.translate != null) return@data
        try {
          dialogProvider.get(data.toTranslate)
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