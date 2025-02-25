package com.fvlaenix.translation.summarizer

import com.fvlaenix.translation.textmodel.TextModelService

class TextModelSummarizer(
  private val textModelService: TextModelService,
  private val maxSummaryLength: Int = 2000,
  private val summaryPrompt: String = TextModelSummarizer::class.java.getResource("/summaryPrompt.txt")!!.readText()
) : Summarizer {
  private var currentSummary: String = ""

  override fun getCurrentSummary(): String = currentSummary

  override suspend fun updateSummary(newText: String): String {
    if (currentSummary.isBlank() && newText.isBlank()) {
      return ""
    }

    val prompt = buildSummaryPrompt(currentSummary, newText)
    val updatedSummary = textModelService.sendRequest(prompt, summaryPrompt)

    // Potentially add logic for summary size limitation
    currentSummary = if (updatedSummary.length > maxSummaryLength) {
      updatedSummary.substring(0, maxSummaryLength) + "..."
    } else {
      updatedSummary
    }

    return currentSummary
  }

  /**
   * Builds a prompt for summarization
   */
  private fun buildSummaryPrompt(currentSummary: String, newText: String): String {
    return if (currentSummary.isBlank()) {
      "Text to summarize:\n$newText"
    } else {
      "Current summary:\n$currentSummary\n\nNew text to incorporate into the summary:\n$newText"
    }
  }

  override fun reset() {
    currentSummary = ""
  }
}
