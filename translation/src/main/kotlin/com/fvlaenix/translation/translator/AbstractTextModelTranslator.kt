package com.fvlaenix.translation.translator

import com.fvlaenix.text.TextModelService
import com.fvlaenix.translation.context.CharacterContext
import com.fvlaenix.translation.context.GlobalContext
import com.fvlaenix.translation.splitter.TextSplitter
import com.fvlaenix.translation.summarizer.NoOpSummarizer
import com.fvlaenix.translation.summarizer.Summarizer

/**
 * Abstract base class for text model-based translators.
 * Contains shared functionality and defines abstract methods for format-specific implementations.
 */
abstract class AbstractTextModelTranslator(
  protected val textModelService: TextModelService,
  protected val textSplitter: TextSplitter = TextSplitter(textModelService),
  protected val globalContext: GlobalContext? = null,
  protected val characterContexts: List<CharacterContext> = emptyList(),
  protected val summarizer: Summarizer = NoOpSummarizer(),
  protected val retries: Int = 3
) : Translator {

  class IncorrectTranslation(message: String?) : Exception(message)

  override suspend fun translate(data: List<Translation>): List<Translation> {
    validateInput(data)
    val untranslated = data.filter { it.translation == null }
    if (untranslated.isEmpty()) return data

    val transformer = TranslationTransformer { translations ->
      buildUserMessage(translations)
    }

    val translated = abstractTranslate(untranslated, transformer)

    var translationIndex = 0
    return data.map { original ->
      if (original.translation != null) {
        original
      } else {
        val translationResult = translated[translationIndex++]
        when (original) {
          is TextTranslation -> TextTranslation(
            original = original.original,
            translation = translationResult.translation
          )

          is DialogTranslation -> DialogTranslation(
            name = original.name,
            original = original.original,
            translation = translationResult.translation
          )
        }
      }
    }
  }

  /**
   * Validates that the input data contains only supported translation types.
   * Each concrete implementation should check for its supported types.
   */
  protected abstract fun validateInput(data: List<Translation>)

  /**
   * Builds the system prompt for the specific translation format.
   */
  protected abstract fun buildSystemPrompt(summary: String, translations: List<Translation>): String

  /**
   * Builds the user message for the specific translation format.
   */
  protected abstract fun buildUserMessage(translations: List<Translation>): String

  /**
   * Parses the response from the text model into Translation objects.
   */
  protected abstract fun parseResponse(response: String, originalBatch: List<Translation>): List<Translation>

  /**
   * Core translation logic shared across all formats.
   */
  private suspend fun abstractTranslate(
    translations: List<Translation>,
    transformer: TranslationTransformer
  ): List<Translation> {
    if (translations.isEmpty()) return emptyList()

    // Use TextSplitter to split translations into manageable chunks
    val (splitTranslations, splitInfoMap) = textSplitter.splitTranslations(
      translations,
      transformer::transform
    )

    // Use TextSplitter to create batches of translations that respect token limits
    val batches = textSplitter.createBatches(
      splitTranslations,
      transformer::transform
    )

    val translatedBatches = mutableListOf<List<Translation>>()
    for (batch in batches) {
      val summary = summarizer.getCurrentSummary()
      val completeSystemMessage = buildSystemPrompt(summary, batch)
      val batchString = buildUserMessage(batch)

      var translatedBatch: List<Translation>? = null

      var retriesLeft = retries
      while (retriesLeft > 0) {
        try {
          val responseString = textModelService.sendRequest(batchString, completeSystemMessage)
          translatedBatch = parseResponse(responseString, batch)
          translatedBatches.add(translatedBatch)
          break
        } catch (e: Exception) {
          println("Batch rejected: ${e.message}")
          retriesLeft--
          if (retriesLeft == 0) throw e
        }
      }

      // Update summary with the new translations
      val translatedText = translatedBatch!!.joinToString("\n") { it.translation ?: "" }
      if (translatedText.isNotBlank()) {
        summarizer.updateSummary(translatedText)
      }
    }

    val flatTranslations = translatedBatches.flatten()

    // Use TextSplitter to merge the split translations back together
    return textSplitter.mergeSplitTranslations(flatTranslations, splitInfoMap, translations)
  }

  /**
   * Utility method to escape XML characters.
   */
  protected fun escapeXml(text: String): String {
    return text
      .replace("&", "&amp;")
      .replace("<", "&lt;")
      .replace(">", "&gt;")
      .replace("\"", "&quot;")
      .replace("'", "&apos;")
  }

  fun interface TranslationTransformer {
    fun transform(translations: List<Translation>): String
  }
}