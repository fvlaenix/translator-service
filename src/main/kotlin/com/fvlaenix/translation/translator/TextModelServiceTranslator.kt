package com.fvlaenix.translation.translator

import com.fvlaenix.translation.context.GlobalContext
import com.fvlaenix.translation.splitter.TextSplitter
import com.fvlaenix.translation.summarizer.NoOpSummarizer
import com.fvlaenix.translation.summarizer.Summarizer
import com.fvlaenix.translation.textmodel.TextModelService
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

class TextModelServiceTranslator(
  private val textModelService: TextModelService,
  private val textSplitter: TextSplitter = TextSplitter(textModelService),
  private val jsonPrompt: String = TextModelServiceTranslator::class.java.getResource("/jsonPrompt.txt")!!.readText(),
  private val textPrompt: String = TextModelServiceTranslator::class.java.getResource("/prompt.txt")!!.readText(),
  private val globalContext: GlobalContext? = null,
  private val summarizer: Summarizer = NoOpSummarizer(),
  private val retries: Int = 3,
) : Translator {
  companion object {
    val JSON = Json { ignoreUnknownKeys = true }
  }

  class IncorrectTranslation(message: String?) : Exception(message)

  fun interface TranslationTransformer {
    fun transform(translations: List<Translation>): String
  }

  override suspend fun translate(data: List<Translation>): List<Translation> {
    return if (data.all { it is TextTranslation }) {
      translateText(data.filterIsInstance<TextTranslation>())
    } else if (data.all { it is DialogTranslation }) {
      translateJson(data)
    } else throw IllegalStateException("Mixed translation types are not supported")
  }

  override suspend fun translateJson(data: List<Translation>): List<Translation> {
    val untranslated = data.filter { it.translation == null }
    if (untranslated.isEmpty()) return data

    val transformer: TranslationTransformer = TranslationTransformer { translations ->
      translations.joinToString(",\n", "[\n", "\n]") { translation ->
        when (translation) {
          is TextTranslation -> """{"text": "${translation.original}"}"""
          is DialogTranslation -> """{"name": "${translation.name}", "text": "${translation.original}"}"""
        }
      }
    }

    val translated = abstractTranslate(untranslated, transformer, jsonPrompt)

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

  override suspend fun translateText(data: List<TextTranslation>): List<TextTranslation> {
    val untranslated = data.filter { it.translation == null }
    if (untranslated.isEmpty()) return data

    val transformer = TranslationTransformer { translations ->
      translations.joinToString("\n") { it.original }
    }

    val translated = abstractTranslate(untranslated, transformer, textPrompt)

    var translationIndex = 0
    return data.map { original ->
      if (original.translation != null) {
        original
      } else {
        TextTranslation(
          original = original.original,
          translation = translated[translationIndex++].translation
        )
      }
    }
  }

  private suspend fun abstractTranslate(
    translations: List<Translation>,
    transformer: TranslationTransformer,
    systemPrompt: String
  ): List<Translation> {
    if (translations.isEmpty()) return emptyList()

    // Use TextSplitter to split translations into manageable chunks
    val (splitTranslations, splitInfoMap) = textSplitter.splitTranslations(
      translations,
      transformer::transform
    )

    // Use TextSplitter to create batches of translations that respect token limits
    val batches = textSplitter.createBatches(splitTranslations, transformer::transform)

    val translatedBatches = mutableListOf<List<Translation>>()
    for (batch in batches) {
      val batchString = transformer.transform(batch)
      val summary = summarizer.getCurrentSummary()

      val completeSystemMessage = buildSystemPrompt(systemPrompt, summary)

      var translatedBatch: List<Translation>? = null

      var retriesLeft = retries
      while (retriesLeft > 0) {
        try {
          val responseString = textModelService.sendRequest(batchString, completeSystemMessage)
          translatedBatch = parseResponse(responseString, batch)
          translatedBatches.add(translatedBatch)
          break
        } catch (e: Exception) {
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

  private fun parseResponse(response: String, originalBatch: List<Translation>): List<Translation> {
    return if (originalBatch.all { it is TextTranslation }) {
      val lines = response.split("\n").map { it.trim() }
      if (lines.size != originalBatch.size) {
        throw IncorrectTranslation("Response size mismatch: expected ${originalBatch.size}, got ${lines.size}")
      }

      originalBatch.mapIndexed { index, translation ->
        TextTranslation(
          original = translation.original,
          translation = lines[index]
        )
      }
    } else {
      try {
        val jsonTranslations = JSON.decodeFromString<List<JsonObject>>(response)

        if (jsonTranslations.size != originalBatch.size) {
          throw IncorrectTranslation("Response size mismatch: expected ${originalBatch.size}, got ${jsonTranslations.size}")
        }

        originalBatch.mapIndexed { index, translation ->
          val jsonTranslation = jsonTranslations[index]

          when (translation) {
            is TextTranslation -> TextTranslation(
              original = translation.original,
              translation = jsonTranslation["text"]?.toString()?.trim('"')
            )
            is DialogTranslation -> DialogTranslation(
              name = translation.name,
              original = translation.original,
              translation = jsonTranslation["text"]?.toString()?.trim('"')
            )
          }
        }
      } catch (e: Exception) {
        throw IncorrectTranslation("Failed to parse JSON response: ${e.message}")
      }
    }
  }

  private fun buildSystemPrompt(systemPrompt: String, summary: String): String {
    val promptBuilder = StringBuilder(systemPrompt)

    // Adding global context if it exists
    val globalContextText = globalContext?.getContextText()
    if (!globalContextText.isNullOrBlank()) {
      promptBuilder.append("\n\nGlobal Context:\n")
      promptBuilder.append(globalContextText)
    }

    // Adding context from summarizer if it's not empty
    if (summary.isNotBlank()) {
      promptBuilder.append("\n\nContext from previous translations (please use it to translate):\n\n")
      promptBuilder.append(summary)
    }

    return promptBuilder.toString()
  }
}