package com.fvlaenix.translation.summarizer

interface Summarizer {
  /**
   * Returns the current summary
   */
  fun getCurrentSummary(): String

  /**
   * Updates the summary taking into account the new text
   * @param newText New text to be incorporated into the summary
   * @return Updated summary
   */
  suspend fun updateSummary(newText: String): String

  /**
   * Resets the current summary
   */
  fun reset()
}
