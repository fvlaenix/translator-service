package com.fvlaenix.translation.review

import com.fvlaenix.translation.textmodel.TextModelService
import com.fvlaenix.translation.translator.Translation
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import java.io.Closeable

/**
 * Reviewer that uses a language model to evaluate translation quality
 */
class TextModelReviewer(
  private val textModelService: TextModelService,
  private val reviewerPrompt: String = TextModelReviewer::class.java.getResource("/reviewer-prompt.txt")?.readText()
    ?: throw IllegalStateException("Cannot load reviewer prompt"),
  private val maxRetries: Int = 3
) : Reviewer, Closeable {

  private val jsonFormat = Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = true
  }

  override suspend fun review(original: String, translation: String, context: String?): ReviewResult {
    val prompt = buildReviewPrompt(original, translation, context)
    var attempts = maxRetries
    var lastException: Exception? = null

    while (attempts > 0) {
      attempts--
      try {
        val response = textModelService.sendRequest(prompt, reviewerPrompt)
        return parseReviewResponse(response)
      } catch (e: Exception) {
        lastException = e
        if (attempts == 0) throw Exception("Failed to get review response after $maxRetries attempts", e)
      }
    }

    throw lastException ?: IllegalStateException("Failed to get review response")
  }

  override suspend fun reviewBatch(translations: List<Translation>, context: String?): List<ReviewResult> =
    coroutineScope {
      // Process each translation individually
      translations.map { translation ->
        review(
          original = translation.original,
          translation = translation.translation ?: throw IllegalArgumentException("Translation cannot be null"),
          context = context
        )
      }
    }

  /**
   * Builds a prompt for requesting translation evaluation
   */
  private fun buildReviewPrompt(original: String, translation: String, context: String?): String {
    val promptBuilder = StringBuilder()

    promptBuilder.append("REVIEW TRANSLATION QUALITY\n\n")

    // Add context if it exists
    if (!context.isNullOrBlank()) {
      promptBuilder.append("Context from previous translations:\n")
      promptBuilder.append(context)
      promptBuilder.append("\n\n")
    }

    promptBuilder.append("Translated text:\n")
    promptBuilder.append(translation)
    promptBuilder.append("\n\n")

    promptBuilder.append("Evaluate this translation based on the following criteria:\n")
    promptBuilder.append("1. Coherence (1-10): How well the text flows logically and maintains connections between sentences and ideas\n")
    promptBuilder.append("2. Grammar (1-10): Grammatical correctness and proper language usage\n")
    promptBuilder.append("3. Modernity (1-10): How contemporary and natural the language sounds to modern speakers\n\n")

    promptBuilder.append("Return your evaluation in JSON format with scores and detailed feedback for each criterion.")

    return promptBuilder.toString()
  }

  /**
   * Parses model response into ReviewResult structure
   */
  private fun parseReviewResponse(response: String): ReviewResult {
    try {
      // Clean JSON from markdown formatting if present
      val cleanResponse = cleanJsonResponse(response)

      return jsonFormat.decodeFromString(ReviewResult.serializer(), cleanResponse)
    } catch (e: Exception) {
      throw Exception("Failed to parse review response: ${e.message}", e)
    }
  }

  /**
   * Cleans JSON response from possible markdown formatting and other artifacts
   */
  private fun cleanJsonResponse(response: String): String {
    // Remove possible markdown wrappers for JSON
    val json = response.trim()
      .replace(Regex("```json\\s*"), "")
      .replace(Regex("```\\s*$"), "")
      .trim()

    // Check if the string starts with '{'
    return if (!json.startsWith("{")) {
      // If not, find the start of JSON object
      val startIndex = json.indexOf("{")
      if (startIndex >= 0) {
        json.substring(startIndex)
      } else {
        throw IllegalArgumentException("Cannot find JSON object in response")
      }
    } else {
      json
    }
  }

  override fun close() {
    if (textModelService is Closeable) {
      textModelService.close()
    }
  }
}
