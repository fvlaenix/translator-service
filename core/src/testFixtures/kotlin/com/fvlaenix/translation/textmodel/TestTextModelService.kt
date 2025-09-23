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
    defaultResponse = null
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

  fun setJsonTestResponse(originalTexts: List<String>, translatedTexts: List<String>) {
    require(originalTexts.size == translatedTexts.size) { "Original and translated texts must have same size" }

    val jsonResponse = translatedTexts.mapIndexed { index, translation ->
      """{"text": "$translation"}"""
    }.joinToString(",\n", "[\n", "\n]")

    setTestResponse(originalTexts.joinToString("\n"), jsonResponse)
  }

  fun setJsonTestResponseWithNames(originalTexts: List<String>, names: List<String>, translatedTexts: List<String>) {
    require(originalTexts.size == translatedTexts.size && originalTexts.size == names.size) {
      "Original texts, names, and translated texts must have same size"
    }

    val jsonResponse = translatedTexts.mapIndexed { index, translation ->
      """{"name": "${names[index]}", "text": "$translation"}"""
    }.joinToString(",\n", "[\n", "\n]")

    setTestResponse(originalTexts.joinToString("\n"), jsonResponse)
  }

  fun setXmlTestResponse(dialogues: List<Pair<String?, String>>, responses: List<String>) {
    require(dialogues.size == responses.size) { "Dialogues and responses must have same size" }

    val xmlBuilder = StringBuilder("<dialogues>")
    dialogues.forEach { (speaker, text) ->
      if (speaker != null) {
        xmlBuilder.append("\n  <dialogue><speaker>${escapeXml(speaker)}</speaker><text>${escapeXml(text)}</text></dialogue>")
      } else {
        xmlBuilder.append("\n  <dialogue><text>${escapeXml(text)}</text></dialogue>")
      }
    }
    xmlBuilder.append("\n</dialogues>")

    val jsonResponse = responses.joinToString(",\n", "[\n", "\n]") { response ->
      """{"text": "$response"}"""
    }

    setResponse(xmlBuilder.toString(), jsonResponse)
  }

  private fun escapeXml(text: String): String {
    return text
      .replace("&", "&amp;")
      .replace("<", "&lt;")
      .replace(">", "&gt;")
      .replace("\"", "&quot;")
      .replace("'", "&apos;")
  }
}
