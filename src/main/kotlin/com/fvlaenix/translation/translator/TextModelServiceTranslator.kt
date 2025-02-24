package com.fvlaenix.translation.translator

import com.fvlaenix.translation.textmodel.TextModelService
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

class TextModelServiceTranslator(
  private val textModelService: TextModelService,
  private val jsonPrompt: String = TextModelServiceTranslator::class.java.getResource("/jsonPrompt.txt")!!.readText(),
  private val textPrompt: String = TextModelServiceTranslator::class.java.getResource("/prompt.txt")!!.readText()
) : Translator {
  companion object {
    val JSON = Json { ignoreUnknownKeys = true }
    private const val TOKEN_LIMIT_FRACTION = 0.8f
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
    } else throw IllegalStateException()
  }

  override suspend fun translateJson(data: List<Translation>): List<Translation> {
    val untranslated = data.filter { it.translation == null }
    if (untranslated.isEmpty()) return data

    val transformer: TranslationTransformer = object : TranslationTransformer {
      override fun transform(translations: List<Translation>): String =
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

    val transformer: TranslationTransformer = object : TranslationTransformer {
      override fun transform(translations: List<Translation>): String =
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

    val splitTranslations = mutableListOf<Translation>()
    val splitInfoMap = mutableMapOf<Int, List<Int>>() // Map<OriginalIndex, SplitCounts>

    translations.forEachIndexed { index, translation ->
      if (textModelService.fractionOfTokenLimit(
          transformer.transform(listOf(translation)),
          systemPrompt
        ) <= TOKEN_LIMIT_FRACTION
      ) {
        splitTranslations.add(translation)
      } else {
        val (parts, splitCounts) = splitLargeTranslation(translation, systemPrompt, transformer)

        splitInfoMap[index] = splitCounts

        splitTranslations.addAll(parts)
      }
    }

    val batches = mutableListOf<List<Translation>>()
    var currentBatch = mutableListOf<Translation>()
    var currentString = ""

    for (translation in splitTranslations) {
      val potentialString = if (currentBatch.isEmpty()) {
        transformer.transform(listOf(translation))
      } else {
        transformer.transform(currentBatch + translation)
      }

      if (textModelService.fractionOfTokenLimit(potentialString, systemPrompt) <= TOKEN_LIMIT_FRACTION) {
        currentBatch.add(translation)
        currentString = potentialString
      } else {
        if (currentBatch.isNotEmpty()) {
          batches.add(currentBatch)
          currentBatch = mutableListOf()
          currentString = ""
        }

        val singleString = transformer.transform(listOf(translation))
        if (textModelService.fractionOfTokenLimit(singleString, systemPrompt) <= TOKEN_LIMIT_FRACTION) {
          currentBatch.add(translation)
          currentString = singleString
        } else {
          throw IncorrectTranslation("Translation too large even after splitting")
        }
      }
    }

    if (currentBatch.isNotEmpty()) {
      batches.add(currentBatch)
    }

    val translatedBatches = mutableListOf<List<Translation>>()
    for (batch in batches) {
      val batchString = transformer.transform(batch)
      val responseString = textModelService.sendRequest(batchString, systemPrompt)

      val translatedBatch = parseResponse(responseString, batch)
      translatedBatches.add(translatedBatch)
    }

    val flatTranslations = translatedBatches.flatten()
    return mergeSplitTranslations(flatTranslations, splitInfoMap, translations)
  }

  private suspend fun splitLargeTranslation(
    translation: Translation,
    systemPrompt: String,
    transformer: TranslationTransformer
  ): Pair<List<Translation>, List<Int>> {
    val result = mutableListOf<Translation>()
    val splitCounts = mutableListOf<Int>()

    val paragraphs = translation.original.split("\n\n")

    if (paragraphs.size <= 1) {
      val sentenceSplitResult = splitBySentences(translation, systemPrompt, transformer)
      result.addAll(sentenceSplitResult.first)
      splitCounts.add(sentenceSplitResult.second) // Добавляем количество частей
      return result to splitCounts
    }

    for (paragraph in paragraphs) {
      val paragraphTranslation = createTranslationWithText(translation, paragraph)

      if (textModelService.fractionOfTokenLimit(
          transformer.transform(listOf(paragraphTranslation)),
          systemPrompt
        ) <= TOKEN_LIMIT_FRACTION
      ) {
        result.add(paragraphTranslation)
        splitCounts.add(1) // Этот абзац не разбивался
      } else {
        val sentenceSplitResult = splitBySentences(paragraphTranslation, systemPrompt, transformer)
        result.addAll(sentenceSplitResult.first)
        splitCounts.add(sentenceSplitResult.second) // Добавляем количество частей для этого абзаца
      }
    }

    return result to splitCounts
  }

  private suspend fun splitBySentences(
    translation: Translation,
    systemPrompt: String,
    transformer: TranslationTransformer
  ): Pair<List<Translation>, Int> {
    val result = mutableListOf<Translation>()

    val sentencePattern = Regex("(?<=[.!?;])\\s+")
    val sentences = sentencePattern.split(translation.original)
      .filter { it.isNotBlank() }

    if (sentences.size <= 1) {
      result.add(translation)
      return result to 1
    }

    var currentText = ""
    var currentSentences = mutableListOf<String>()

    for (sentence in sentences) {
      val potentialText = if (currentText.isEmpty()) sentence else "$currentText $sentence"
      val potentialTranslation = createTranslationWithText(translation, potentialText)

      if (textModelService.fractionOfTokenLimit(
          transformer.transform(listOf(potentialTranslation)),
          systemPrompt
        ) <= TOKEN_LIMIT_FRACTION
      ) {
        currentText = potentialText
        currentSentences.add(sentence)
      } else {
        if (currentText.isNotEmpty()) {
          result.add(createTranslationWithText(translation, currentText))
        }

        currentText = sentence
        currentSentences = mutableListOf(sentence)

        if (textModelService.fractionOfTokenLimit(
            transformer.transform(listOf(createTranslationWithText(translation, currentText))), systemPrompt
          ) > TOKEN_LIMIT_FRACTION
        ) {
          throw IllegalStateException("Sentence not fit into context!")
        }
      }
    }

    if (currentText.isNotEmpty()) {
      result.add(createTranslationWithText(translation, currentText))
    }

    return result to result.size
  }

  private fun createTranslationWithText(translation: Translation, text: String): Translation {
    return when (translation) {
      is TextTranslation -> TextTranslation(
        original = text,
        translation = null
      )

      is DialogTranslation -> DialogTranslation(
        name = translation.name,
        original = text,
        translation = null
      )
    }
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

  private fun mergeSplitTranslations(
    translations: List<Translation>,
    splitInfoMap: Map<Int, List<Int>>,
    originalTranslations: List<Translation>
  ): List<Translation> {
    if (splitInfoMap.isEmpty()) return translations

    val translationsCopy = translations.toMutableList()
    val result = mutableListOf<Translation>()

    var currentIndex = 0
    for (originalIndex in originalTranslations.indices) {
      if (splitInfoMap.containsKey(originalIndex)) {
        val originalTranslation = originalTranslations[originalIndex]
        val splitCounts = splitInfoMap[originalIndex]!!

        val allParts = mutableListOf<Translation>()
        splitCounts.forEach { count ->
          val parts = translationsCopy.subList(currentIndex, currentIndex + count)
          allParts.addAll(parts)
          currentIndex += count
        }

        val mergedText = if (splitCounts.size > 1) {
          val paragraphs = mutableListOf<String>()
          var partIndex = 0

          for (paragraphCount in splitCounts) {
            if (paragraphCount == 1) {
              paragraphs.add(allParts[partIndex++].translation ?: "")
            } else {
              val sentenceParts = allParts.subList(partIndex, partIndex + paragraphCount)
              paragraphs.add(sentenceParts.joinToString(" ") { it.translation ?: "" })
              partIndex += paragraphCount
            }
          }

          paragraphs.joinToString("\n\n")
        } else {
          allParts.joinToString(" ") { it.translation ?: "" }
        }

        val mergedTranslation = when (originalTranslation) {
          is TextTranslation -> TextTranslation(
            original = originalTranslation.original,
            translation = mergedText
          )

          is DialogTranslation -> DialogTranslation(
            name = originalTranslation.name,
            original = originalTranslation.original,
            translation = mergedText
          )
        }

        result.add(mergedTranslation)
      } else {
        result.add(translationsCopy[currentIndex++])
      }
    }

    return result
  }
}
