package com.fvlaenix.translation

import com.fvlaenix.translation.translator.TextTranslation
import com.fvlaenix.translation.translator.Translator
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import java.nio.file.Path
import kotlin.io.path.appendLines
import kotlin.io.path.readLines

class TranslationTxtService(
  path: Path,
  private val translator: Translator
) {

  companion object {
    const val COUNT_WORDS = 250
  }

  val text = path.readLines()

  suspend fun translate() {
    coroutineScope {
      ensureActive()
      val toTranslate = Util.splitWords(text, COUNT_WORDS).flatten()
      val data = toTranslate.map { TextTranslation(original = it) }
      translator.translate(data)
      Path.of("output.txt").appendLines(data.map { it.translation ?: "<FAILED TRANSLATION>" })
    }
  }
}