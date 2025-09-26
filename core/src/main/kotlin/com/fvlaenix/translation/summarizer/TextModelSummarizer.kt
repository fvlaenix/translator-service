package com.fvlaenix.translation.summarizer

import com.fvlaenix.text.TextModelService

/**
 * Summarises incremental text by delegating to a text model service.
 *
 * Maintains an internal running summary and incorporates new text on demand using a prompt built
 * from the current summary and the provided text. The resulting summary is truncated to
 * [maxSummaryLength] characters, with an ellipsis appended when truncation occurs.
 */
class TextModelSummarizer(
  private val textModelService: TextModelService,
  private val maxSummaryLength: Int = 2000,
  private val summaryPrompt: String = TextModelSummarizer::class.java.getResource("/summarizer-prompt.txt")!!.readText()
) : Summarizer {
  private var currentSummary: String = ""

  /**
   * Returns the current in-memory summary.
   *
   * Returns an empty string when no summary has been produced yet.
   *
   * @return The latest summary text, possibly empty.
   */
  override fun getCurrentSummary(): String = currentSummary

  /**
   * Updates the running summary by incorporating the provided text.
   *
   * Builds a prompt from the current summary and [newText], sends it to the underlying
   * [TextModelService], and stores the returned summary. The stored summary is truncated to
   * [maxSummaryLength] characters with an appended ellipsis when longer.
   * If both the current summary and [newText] are blank, returns an empty string without calling the service.
   *
   * @param newText New text to incorporate into the summary; may be blank.
   * @return The updated summary, possibly truncated and possibly empty.
   */
  override suspend fun updateSummary(newText: String): String {
    if (currentSummary.isBlank() && newText.isBlank()) {
      return ""
    }

    val prompt = buildSummaryPrompt(currentSummary, newText)
    val updatedSummary = textModelService.sendRequest(summaryPrompt, prompt)

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

  /**
   * Clears the stored summary, returning the summariser to its initial state.
   */
  override fun reset() {
    currentSummary = ""
  }
}
