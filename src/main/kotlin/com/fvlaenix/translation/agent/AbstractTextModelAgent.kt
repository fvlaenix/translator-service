package com.fvlaenix.translation.agent

import com.fvlaenix.translation.textmodel.TextModelService
import java.io.Closeable

/**
 * Abstract class for agents working with text models.
 * The agent receives text, constructs a query based on its prompt,
 * sends it to the text model, and then returns the result.
 *
 * @property textModelService Service for interacting with the text model
 * @property maxRetries Maximum number of retry attempts when errors occur
 */
abstract class AbstractTextModelAgent(
  protected val textModelService: TextModelService,
  protected val maxRetries: Int = 3
) : Closeable {

  /**
   * Processes text using the text model and returns the result.
   *
   * @param text Text to process
   * @param additionalContext Additional context that can be used when forming the request (optional)
   * @return Result of text processing
   */
  suspend fun process(text: String, additionalContext: String? = null): String {
    val prompt = buildPrompt(text, additionalContext)
    val systemMessage = getPromptTemplate()

    var attempts = maxRetries
    var lastException: Exception? = null

    while (attempts > 0) {
      attempts--
      try {
        return textModelService.sendRequest(prompt, systemMessage)
      } catch (e: Exception) {
        lastException = e
        if (attempts == 0) {
          throw Exception("Failed to get response after $maxRetries attempts", lastException)
        }
      }
    }

    throw lastException ?: IllegalStateException("Failed to get response")
  }

  /**
   * Processes multiple texts in batch mode and returns corresponding results.
   *
   * @param texts List of texts to process
   * @param additionalContext Additional context for all texts (optional)
   * @return List of text processing results
   */
  suspend fun processBatch(texts: List<String>, additionalContext: String? = null): List<String> {
    return texts.map { process(it, additionalContext) }
  }

  /**
   * Abstract method that returns the prompt template used to configure
   * the behavior of the text model. Usually this is the content of the system message.
   *
   * @return Prompt template as a string
   */
  protected abstract fun getPromptTemplate(): String

  /**
   * Builds a prompt for the text model request based on the provided text
   * and additional context.
   *
   * @param text Main text to process
   * @param additionalContext Additional context (optional)
   * @return Ready prompt to send to the text model
   */
  protected open fun buildPrompt(text: String, additionalContext: String?): String {
    val promptBuilder = StringBuilder()
        
    if (!additionalContext.isNullOrBlank()) {
      promptBuilder.append("Context:\n")
      promptBuilder.append(additionalContext)
      promptBuilder.append("\n\n")
    }

    promptBuilder.append("Text to process:\n")
    promptBuilder.append(text)

    return promptBuilder.toString()
  }

  /**
   * Closes resources used by the agent.
   * In particular, if TextModelService implements Closeable,
   * its close() method will be called.
   */
  override fun close() {
    if (textModelService is Closeable) {
      textModelService.close()
    }
  }
}