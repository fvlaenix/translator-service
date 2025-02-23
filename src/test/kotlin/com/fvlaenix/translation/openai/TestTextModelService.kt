package com.fvlaenix.translation.openai

class TestTextModelService : TextModelService {
  private val responses = mutableMapOf<String, String>()
  private var defaultResponse: String = "Test response"
  private var shouldThrowError = false
  private var errorToThrow: Exception? = null

  fun setResponse(prompt: String, response: String, textPrompt: String? = null) {
    responses[prompt] = response
    if (textPrompt != null) {
      responses[prompt + textPrompt] = response
    }
  }

  fun setDefaultResponse(response: String) {
    defaultResponse = response
  }

  fun simulateError(error: Exception? = IllegalStateException("Test error")) {
    shouldThrowError = true
    errorToThrow = error
  }

  fun reset() {
    responses.clear()
    defaultResponse = "Test response"
    shouldThrowError = false
    errorToThrow = null
  }

  override suspend fun sendRequest(prompt: String, systemMessage: String?): String {
    if (shouldThrowError) {
      throw errorToThrow ?: IllegalStateException("Test error")
    }
    // Try to find response with system message first
    val response = if (systemMessage != null) {
      responses[prompt + systemMessage] ?: responses[prompt] ?: defaultResponse
    } else {
      responses[prompt] ?: defaultResponse
    }
    return response
  }

  override suspend fun sendBatchRequest(prompts: List<String>, systemMessage: String?): List<String> {
    return prompts.map { prompt -> sendRequest(prompt, systemMessage) }
  }
}
