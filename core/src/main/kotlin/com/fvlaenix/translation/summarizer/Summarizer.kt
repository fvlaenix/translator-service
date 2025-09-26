package com.fvlaenix.translation.summarizer

/**
 * Contract for components that maintain and update a running summary of text.
 *
 * Implementations may keep internal state representing the current summary and support
 * incremental updates. Thread-safety and persistence are unspecified unless stated by
 * the implementation.
 */
interface Summarizer {
  /**
   * Returns the current summary content.
   *
   * @return The latest summary text; content and emptiness are implementation-specific.
   */
  fun getCurrentSummary(): String

  /**
   * Updates the running summary to reflect the provided text.
   *
   * Implementations typically combine the existing summary with [newText] and return the new value.
   * The exact strategy, side effects, and truncation rules are unspecified.
   *
   * @param newText New text to be incorporated into the summary; may be blank.
   * @return The updated summary as maintained by this instance.
   */
  suspend fun updateSummary(newText: String): String

  /**
   * Resets internal state and clears any accumulated summary.
   */
  fun reset()
}
