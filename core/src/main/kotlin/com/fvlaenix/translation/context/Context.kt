package com.fvlaenix.translation.context

/**
 * Abstraction for providing contextual text used during translation or summarisation.
 */
interface Context {
  /**
   * Returns the context text as a single string.
   *
   * @return The context text; format and source are implementation-specific.
   */
  fun getContextText(): String
}
