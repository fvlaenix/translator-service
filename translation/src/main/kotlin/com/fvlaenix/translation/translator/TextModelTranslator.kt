package com.fvlaenix.translation.translator

import com.fvlaenix.text.TextModelService
import com.fvlaenix.translation.context.CharacterContext
import com.fvlaenix.translation.context.GlobalContext
import com.fvlaenix.translation.splitter.TextSplitter
import com.fvlaenix.translation.summarizer.NoOpSummarizer
import com.fvlaenix.translation.summarizer.Summarizer

/**
 * Translator that handles simple text translations using plain text format.
 * Only supports TextTranslation objects.
 */
class TextModelTranslator(
  textModelService: TextModelService,
  textSplitter: TextSplitter = TextSplitter(textModelService),
  private val textPrompt: String = TextModelTranslator::class.java.getResource("/prompt.txt")!!.readText(),
  globalContext: GlobalContext? = null,
  characterContexts: List<CharacterContext> = emptyList(),
  summarizer: Summarizer = NoOpSummarizer(),
  retries: Int = 3
) : AbstractTextModelTranslator(
  textModelService = textModelService,
  textSplitter = textSplitter,
  globalContext = globalContext,
  characterContexts = characterContexts,
  summarizer = summarizer,
  retries = retries
) {

  override fun validateInput(data: List<Translation>) {
    if (!data.all { it is TextTranslation }) {
      throw IllegalArgumentException("TextModelTranslator only supports TextTranslation objects")
    }
  }

  override fun buildSystemPrompt(summary: String, translations: List<Translation>): String {
    val promptBuilder = StringBuilder(textPrompt)

    // Add global context if it exists
    val globalContextText = globalContext?.getContextText()
    if (!globalContextText.isNullOrBlank()) {
      promptBuilder.append("\n\nGlobal Context:\n")
      promptBuilder.append(globalContextText)
    }

    // Add applicable character contexts
    val applicableContexts = characterContexts.filter { it.shouldIncludeContext(translations) }
    if (applicableContexts.isNotEmpty()) {
      val contextText = applicableContexts.joinToString("\n\n") { it.getContextText() }
      promptBuilder.append("\n\nContext:\n")
      promptBuilder.append(contextText)
    }

    // Add context from summarizer if it's not empty
    if (summary.isNotBlank()) {
      promptBuilder.append("\n\nContext from previous translations (please use it to translate):\n\n")
      promptBuilder.append(summary)
    }

    return promptBuilder.toString()
  }

  override fun buildUserMessage(translations: List<Translation>): String {
    return translations.joinToString("\n") { it.original }
  }

  override fun parseResponse(response: String, originalBatch: List<Translation>): List<Translation> {
    if (originalBatch.size == 1) {
      return listOf(
        TextTranslation(
          original = originalBatch[0].original,
          translation = response.trim()
        )
      )
    }

    val originalNonEmptyLineCounts = originalBatch.map { translation ->
      translation.original.split("\n").count { it.trim().isNotEmpty() }
    }

    val responseLines = response.split("\n")
    val translations = mutableListOf<Translation>()

    var currentLine = 0

    for (i in originalBatch.indices) {
      val original = originalBatch[i]
      val expectedNonEmptyLines = originalNonEmptyLineCounts[i]

      val translatedLineStart = currentLine
      var nonEmptyCount = 0

      while (currentLine < responseLines.size && nonEmptyCount < expectedNonEmptyLines) {
        if (responseLines[currentLine].trim().isNotEmpty()) {
          nonEmptyCount++
        }
        currentLine++
      }

      if (nonEmptyCount < expectedNonEmptyLines) {
        throw IncorrectTranslation("Not enough non-empty lines in response for item $i")
      }

      val translatedText = responseLines.subList(translatedLineStart, currentLine).joinToString("\n")
      translations.add(
        TextTranslation(
          original = original.original,
          translation = translatedText
        )
      )
    }

    return translations
  }
}