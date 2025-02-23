package com.fvlaenix.translation.openai

import TOKEN
import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatRole
import com.aallam.openai.api.http.Timeout
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import kotlin.time.Duration.Companion.seconds

class OpenAIServiceImpl(
  private val model: String = "gpt-4-turbo",
  private val maxRetries: Int = 3
) : TextModelService {
  private val openAI = OpenAI(
    token = TOKEN,
    timeout = Timeout(socket = 180.seconds)
  )

  override suspend fun sendRequest(prompt: String, systemMessage: String?): String {
    var attempts = maxRetries
    while (attempts > 0) {
      attempts--
      try {
        return makeRequest(prompt, systemMessage)
      } catch (e: Exception) {
        if (attempts == 0) throw e
      }
    }
    throw IllegalStateException("Failed to get response after $maxRetries attempts")
  }

  override suspend fun sendBatchRequest(prompts: List<String>, systemMessage: String?): List<String> {
    return prompts.map { prompt -> sendRequest(prompt, systemMessage) }
  }

  private suspend fun makeRequest(prompt: String, systemMessage: String?): String {
    val messages = buildList {
      if (systemMessage != null) {
        add(
          ChatMessage(
            role = ChatRole.System,
            content = systemMessage
          )
        )
      }
      add(
        ChatMessage(
          role = ChatRole.User,
          content = prompt
        )
      )
    }

    val chatCompletionRequest = ChatCompletionRequest(
      model = ModelId(model),
      messages = messages
    )

    val completion = openAI.chatCompletion(chatCompletionRequest)
    return completion.choices[0].message.content
      ?: throw IllegalStateException("Received null response from OpenAI")
  }
}