package com.fvlaenix.translation.textmodel

import com.aallam.openai.api.chat.ChatMessage
import com.fvlaenix.text.TextModelService
import com.fvlaenix.translation.TestConstants

class TestTextModelService : TextModelService {
  private val responses = mutableMapOf<String, String>()
  private var defaultResponse: String? = null
  private var shouldThrowError = false
  private var errorToThrow: Exception? = null

  fun setResponse(request: String, response: String) {
    responses[request] = response
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

  override suspend fun fractionOfTokenLimit(text: String): Float =
    0.5f

  override suspend fun sendRequest(prompt: String?, userMessage: String): String {
    if (shouldThrowError) {
      throw errorToThrow ?: IllegalStateException("Test error")
    }
    return responses[userMessage] ?: defaultResponse
    ?: throw IllegalStateException("No response found for prompt: $userMessage")
  }

  override suspend fun sendRequest(messages: List<ChatMessage>): String {
    if (shouldThrowError) {
      throw errorToThrow ?: IllegalStateException("Test error")
    }
    val key = messages.joinToString(separator = "|") { "${it.role}:${it.content}" }
    return responses[key] ?: defaultResponse ?: throw IllegalStateException("No response found for prompt: $messages")
  }

  fun setTestResponse(prompt: String, response: String) {
    setResponse(prompt, response)
  }

  fun setTestBatchResponse(prompts: List<String>, responses: List<String>) {
    require(prompts.size == responses.size) { "Prompts and responses must have same size" }
    val batchPrompt = prompts.joinToString("\n")
    val batchResponse = responses.joinToString("\n")
    setTestResponse(batchPrompt, batchResponse)
  }

  fun setupTestDefaults() {
    setDefaultResponse(TestConstants.DEFAULT_TEST_RESPONSE)
  }
}
