package com.fvlaenix.translation

import com.fvlaenix.translation.TranslationTxtService.Companion.COUNT_WORDS
import com.fvlaenix.translation.translator.TextTranslation
import com.fvlaenix.translation.translator.Translator
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import java.nio.file.Path
import kotlin.io.path.appendLines
import kotlin.io.path.appendText
import kotlin.io.path.readLines

/**
 * Translates a plain‑text file and writes results to output.txt.
 *
 * Splits input into chunks of COUNT_WORDS words, translates using the provided [Translator], and
 * appends translations to a file named "output.txt" in the working directory.
 *
 * This type is not thread‑safe. It performs file I/O as a side effect during [translate].
 *
 * @param path Path to the source text file to read.
 * @param translator Translator used to perform the translation.
 */
class TranslationTxtService(
  path: Path,
  private val translator: Translator
) {

  companion object {
    /**
     * Number of words per chunk when splitting the input text.
     */
    const val COUNT_WORDS = 250
  }

  /**
   * Lines of the input text as read from [path] during initialisation.
   */
  val text = path.readLines()

  /**
   * Translates the loaded [text] and appends results to output.txt.
   *
   * Splits lines into word chunks of size [COUNT_WORDS], delegates translation to [translator],
   * and appends each translated chunk to a file named "output.txt". Cooperative cancellation is
   * honoured via coroutine context checks.
   */
  suspend fun translate() {
    coroutineScope {
      ensureActive()
      val toTranslate = Util.splitWords(text, COUNT_WORDS).flatten()
      val data = toTranslate.map { TextTranslation(original = it) }
      val newData = translator.translate(data)
      Path.of("output.txt")
        .appendLines(newData.map { it.translation ?: "<FAILED TRANSLATION>" })
        .appendText("\n\n")
    }
  }
}