package com.fvlaenix.translation.translator

import com.aallam.openai.api.chat.ChatMessage
import com.fvlaenix.text.TextModelService

abstract class AbstractTranslatorTest {

  class MockTextModelService(private val maxChars: Int) : TextModelService {
    var countOfRequests = 0
    var lastRequest: String? = null
    var lastSystemMessage: String? = null

    override suspend fun sendRequest(prompt: String?, userMessage: String): String {
      countOfRequests++
      // AbstractTextModelTranslator calls sendRequest(batchString, completeSystemMessage)
      // So the first param is actually the user message (JSON array) and second is system message
      val actualUserMessage = prompt ?: ""
      val actualSystemMessage = userMessage

      lastRequest = actualSystemMessage
      lastSystemMessage = actualUserMessage

      if (fractionOfTokenLimit(actualUserMessage) > 0.8f) {
        throw IllegalArgumentException("Text exceeds token limit fraction")
      }

      return when {
        actualUserMessage.trim().startsWith("[") && actualUserMessage.trim().endsWith("]") -> {
          // Handle JSON format - parse and return translated JSON
          try {
            val jsonInput = actualUserMessage.trim()
            // Simple JSON parsing for the test - replace text values with translated versions
            val translatedJson = jsonInput.replace(Regex("\"text\":\\s*\"([^\"]+)\"")) { matchResult ->
              "\"text\": \"translated: ${matchResult.groupValues[1]}\""
            }
            translatedJson
          } catch (e: Exception) {
            // Fallback to simple replacement if JSON parsing fails
            actualUserMessage.replace("\"text\": \"", "\"text\": \"translated: ")
          }
        }

        else -> {
          actualUserMessage.split("\n").joinToString("\n") { "translated: $it" }
        }
      }
    }

    override suspend fun sendRequest(messages: List<ChatMessage>): String {
      countOfRequests++

      // Extract user message from the chat messages - user message is usually the one with JSON content
      val userMessage = messages.find { it.content?.trim()?.startsWith("[") == true }?.content
        ?: messages.lastOrNull { it.content != null }?.content ?: ""
      val systemMessage = messages.find { it.content != null && it.content != userMessage }?.content

      lastRequest = systemMessage
      lastSystemMessage = userMessage

      if (fractionOfTokenLimit(userMessage) > 0.8f) {
        throw IllegalArgumentException("Text exceeds token limit fraction")
      }

      return when {
        userMessage.trim().startsWith("[") && userMessage.trim().endsWith("]") -> {
          // Handle JSON format - parse and return translated JSON
          try {
            val jsonInput = userMessage.trim()
            // Simple JSON parsing for the test - replace text values with translated versions
            val translatedJson = jsonInput.replace(Regex("\"text\":\\s*\"([^\"]+)\"")) { matchResult ->
              "\"text\": \"translated: ${matchResult.groupValues[1]}\""
            }
            translatedJson
          } catch (e: Exception) {
            // Fallback to simple replacement if JSON parsing fails
            userMessage.replace("\"text\": \"", "\"text\": \"translated: ")
          }
        }

        else -> {
          userMessage.split("\n").joinToString("\n") { "translated: $it" }
        }
      }
    }

    override suspend fun fractionOfTokenLimit(text: String): Float {
      val totalLength = text.length
      return totalLength.toFloat() / maxChars
    }
  }
}