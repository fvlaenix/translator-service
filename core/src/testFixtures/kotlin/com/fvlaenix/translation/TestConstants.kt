package com.fvlaenix.translation

/**
 * Common constants for testing translation functionality
 */

object TestConstants {
  /**
   * Standard test prompt used across all translation tests.
   * Simple and consistent to avoid prompt-related test issues.
   */
  const val TEST_PROMPT = "Translate the following text line by line:"

  /**
   * Default fallback response for unmocked translations
   */
  const val DEFAULT_TEST_RESPONSE = "Test translation"
}