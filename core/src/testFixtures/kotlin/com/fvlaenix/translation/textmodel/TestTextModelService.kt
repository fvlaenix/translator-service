package com.fvlaenix.translation.textmodel

import com.aallam.openai.api.chat.ChatMessage
import com.fvlaenix.text.TextModelService

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

  override suspend fun fractionOfTokenLimit(text: String): Float =
    0.5f

  override suspend fun sendRequest(prompt: String?, userMessage: String): String {
    if (shouldThrowError) {
      throw errorToThrow ?: IllegalStateException("Test error")
    }
    val key = (prompt ?: "") + userMessage
    return responses[key] ?: responses[userMessage] ?: defaultResponse
  }

  override suspend fun sendRequest(messages: List<ChatMessage>): String {
    if (shouldThrowError) {
      throw errorToThrow ?: IllegalStateException("Test error")
    }
    val key = messages.joinToString(separator = "|") { "${it.role}:${it.content}" }
    return responses[key] ?: defaultResponse
  }
}
