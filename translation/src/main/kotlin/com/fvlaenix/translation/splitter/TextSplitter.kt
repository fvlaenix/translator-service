package com.fvlaenix.translation.splitter

import com.fvlaenix.text.TextModelService
import com.fvlaenix.translation.translator.DialogTranslation
import com.fvlaenix.translation.translator.TextTranslation
import com.fvlaenix.translation.translator.Translation

/**
 * A service that handles splitting large texts into smaller chunks that can be processed
 * by language models with token limitations.
 *
 * @property textModelService Service for text model operations, used for token counting
 * @property tokenLimitFraction Maximum fraction of the model's token limit to use for each chunk
 */
open class TextSplitter(
  private val textModelService: TextModelService,
  private val tokenLimitFraction: Float = 0.8f
) {
  /**
   * Splits a list of translations into manageable chunks based on token limits.
   *
   * @param translations List of translations to split
   * @param transformer Function to transform translations into a string for token counting
   * @return Pair of (list of split translations, map of original indices to split counts)
   */
  suspend fun splitTranslations(
    translations: List<Translation>,
    transformer: (List<Translation>) -> String
  ): Pair<List<Translation>, Map<Int, List<Int>>> {
    if (translations.isEmpty()) return Pair(emptyList(), emptyMap())

    val splitTranslations = mutableListOf<Translation>()
    val splitInfoMap = mutableMapOf<Int, List<Int>>() // Map<OriginalIndex, SplitCounts>

    translations.forEachIndexed { index, translation ->
      if (textModelService.fractionOfTokenLimit(transformer(listOf(translation))) <= tokenLimitFraction) {
        splitTranslations.add(translation)
      } else {
        val (parts, splitCounts) = splitLargeTranslation(translation, transformer)
        splitInfoMap[index] = splitCounts
        splitTranslations.addAll(parts)
      }
    }

    return Pair(splitTranslations, splitInfoMap)
  }

  /**
   * Groups split translations into batches that respect token limits.
   *
   * @param translations List of translations (potentially already split)
   * @param transformer Function to transform translations into a string for token counting
   * @return List of batches of translations
   */
  open suspend fun createBatches(
    translations: List<Translation>,
    transformer: (List<Translation>) -> String
  ): List<List<Translation>> {
    val batches = mutableListOf<List<Translation>>()
    var currentBatch = mutableListOf<Translation>()

    for (translation in translations) {
      val potentialString = if (currentBatch.isEmpty()) {
        transformer(listOf(translation))
      } else {
        transformer(currentBatch + translation)
      }

      if (textModelService.fractionOfTokenLimit(potentialString) <= tokenLimitFraction) {
        currentBatch.add(translation)
      } else {
        if (currentBatch.isNotEmpty()) {
          batches.add(currentBatch)
          currentBatch = mutableListOf()
        }

        val singleString = transformer(listOf(translation))
        if (textModelService.fractionOfTokenLimit(singleString) <= tokenLimitFraction) {
          currentBatch.add(translation)
        } else {
          throw IllegalStateException("Translation too large even after splitting")
        }
      }
    }

    if (currentBatch.isNotEmpty()) {
      batches.add(currentBatch)
    }

    return batches
  }

  /**
   * Merges previously split translations back together.
   *
   * @param translations List of split translations
   * @param splitInfoMap Map of original indices to split counts
   * @param originalTranslations Original unsplit translations
   * @return List of merged translations
   */
  fun mergeSplitTranslations(
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

  /**
   * Splits a large translation into smaller parts.
   *
   * @param translation Translation to split
   * @param transformer Function to transform translations into a string for token counting
   * @return Pair of (list of split translations, list of split counts per paragraph)
   */
  private suspend fun splitLargeTranslation(
    translation: Translation,
    transformer: (List<Translation>) -> String
  ): Pair<List<Translation>, List<Int>> {
    val result = mutableListOf<Translation>()
    val splitCounts = mutableListOf<Int>()

    val paragraphs = translation.original.split("\n\n")

    if (paragraphs.size <= 1) {
      val sentenceSplitResult = splitBySentences(translation, transformer)
      result.addAll(sentenceSplitResult.first)
      splitCounts.add(sentenceSplitResult.second) // Adding the number of parts
      return result to splitCounts
    }

    for (paragraph in paragraphs) {
      val paragraphTranslation = createTranslationWithText(translation, paragraph)

      if (textModelService.fractionOfTokenLimit(
          transformer(listOf(paragraphTranslation))
        ) <= tokenLimitFraction
      ) {
        result.add(paragraphTranslation)
        splitCounts.add(1) // This paragraph was not split
      } else {
        val sentenceSplitResult = splitBySentences(paragraphTranslation, transformer)
        result.addAll(sentenceSplitResult.first)
        splitCounts.add(sentenceSplitResult.second) // Adding the number of parts for this paragraph
      }
    }

    return result to splitCounts
  }

  /**
   * Splits a translation by sentences when paragraphs are too large.
   *
   * @param translation Translation to split
   * @param transformer Function to transform translations into a string for token counting
   * @return Pair of (list of split translations, number of parts)
   */
  private suspend fun splitBySentences(
    translation: Translation,
    transformer: (List<Translation>) -> String
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
          transformer(listOf(potentialTranslation))
        ) <= tokenLimitFraction
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
            transformer(listOf(createTranslationWithText(translation, currentText)))
          ) > tokenLimitFraction
        ) {
          throw IllegalStateException("Sentence too large to fit into token limit: $sentence")
        }
      }
    }

    if (currentText.isNotEmpty()) {
      result.add(createTranslationWithText(translation, currentText))
    }

    return result to result.size
  }

  /**
   * Creates a new translation with the given text.
   *
   * @param translation Original translation to copy type from
   * @param text New text for the translation
   * @return New translation of the same type as the original but with the new text
   */
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
}