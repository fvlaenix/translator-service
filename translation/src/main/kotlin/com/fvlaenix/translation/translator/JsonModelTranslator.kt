package com.fvlaenix.translation.translator

import com.fvlaenix.text.TextModelService
import com.fvlaenix.translation.context.CharacterContext
import com.fvlaenix.translation.context.GlobalContext
import com.fvlaenix.translation.splitter.TextSplitter
import com.fvlaenix.translation.summarizer.NoOpSummarizer
import com.fvlaenix.translation.summarizer.Summarizer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * Translator that handles dialog translations using JSON format.
 * Only supports DialogTranslation objects.
 */
class JsonModelTranslator(
  textModelService: TextModelService,
  textSplitter: TextSplitter = TextSplitter(textModelService),
  private val jsonPrompt: String = JsonModelTranslator::class.java.getResource("/jsonPrompt.txt")!!.readText(),
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

  companion object {
    /**
     * Shared JSON instance configured to ignore unknown keys in model responses.
     */
    val JSON = Json { ignoreUnknownKeys = true }
  }

  override fun validateInput(data: List<Translation>) {
    if (!data.all { it is DialogTranslation }) {
      throw IllegalArgumentException("JsonModelTranslator only supports DialogTranslation objects")
    }
  }

  override fun buildSystemPrompt(summary: String, translations: List<Translation>): String {
    val promptBuilder = StringBuilder(jsonPrompt)

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
    return translations.joinToString(",\n", "[\n", "\n]") { translation ->
      when (translation) {
        is DialogTranslation -> """{"name": "${translation.name}", "text": "${translation.original}"}"""
        else -> throw IllegalStateException("Unexpected translation type: ${translation::class}")
      }
    }
  }

  override fun parseResponse(response: String, originalBatch: List<Translation>): List<Translation> {
    try {
      var responseWithoutTrash = response.trim()
      if (responseWithoutTrash.startsWith("```json") && responseWithoutTrash.endsWith("```")) {
        responseWithoutTrash = responseWithoutTrash.removePrefix("```json").removeSuffix("```").trim()
      }
      if (responseWithoutTrash.startsWith("```") && responseWithoutTrash.endsWith("```")) {
        responseWithoutTrash = responseWithoutTrash.removePrefix("```").removeSuffix("```").trim()
      }

      val jsonTranslations = JSON.decodeFromString<List<JsonObject>>(responseWithoutTrash)

      if (jsonTranslations.size != originalBatch.size) {
        throw IncorrectTranslation("Response size mismatch: expected ${originalBatch.size}, got ${jsonTranslations.size}")
      }

      return originalBatch.mapIndexed { index, translation ->
        val jsonTranslation = jsonTranslations[index]

        when (translation) {
          is DialogTranslation -> DialogTranslation(
            name = translation.name,
            original = translation.original,
            translation = jsonTranslation["text"]?.toString()?.trim('"')
          )

          else -> throw IllegalStateException("Unexpected translation type: ${translation::class}")
        }
      }
    } catch (e: Exception) {
      throw IncorrectTranslation("Failed to parse JSON response: ${e.message}")
    }
  }
}