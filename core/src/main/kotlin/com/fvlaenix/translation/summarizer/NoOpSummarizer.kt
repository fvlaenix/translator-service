package com.fvlaenix.translation.summarizer

/**
 * No-op implementation of [Summarizer] that never accumulates state.
 *
 * All methods return empty results and have no side effects.
 */
class NoOpSummarizer : Summarizer {
  /**
   * Returns an empty string.
   *
   * @return Always an empty string.
   */
  override fun getCurrentSummary(): String = ""

  /**
   * Ignores the input and returns an empty string.
   *
   * @param newText Ignored input text.
   * @return Always an empty string.
   */
  override suspend fun updateSummary(newText: String): String = ""

  /**
   * Performs no action.
   */
  override fun reset() { /* Do nothing */
  }
}
