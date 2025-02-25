package com.fvlaenix.translation.summarizer

class NoOpSummarizer : Summarizer {
  override fun getCurrentSummary(): String = ""

  override suspend fun updateSummary(newText: String): String = ""

  override fun reset() { /* Do nothing */
  }
}
