package com.fvlaenix.translation.textmodel

import TOKEN
import com.aallam.ktoken.Tokenizer
import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatRole
import com.aallam.openai.api.http.Timeout
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import java.io.Closeable
import kotlin.time.Duration.Companion.seconds

class OpenAIServiceImpl(
  private val model: String = "gpt-4-turbo",
  private val maxRetries: Int = 3
) : TextModelService, Closeable {
  data class ModelInfo(val name: String, val maxTokenCount: Int)

  init {
    assert(MODELS.containsKey(model)) { "Model $model does not exist in database" }
  }

  companion object {
    val MODELS = mapOf(
      "gpt-4-turbo" to ModelInfo("gpt-4-turbo", 4096)
    )
  }

  private val openAI = OpenAI(
    token = TOKEN,
    timeout = Timeout(socket = 180.seconds)
  )

  suspend fun countOfTokens(prompt: String): Int =
    Tokenizer.of(model).encode(prompt).count()

  override suspend fun fractionOfTokenLimit(text: String): Float =
    countOfTokens(text).toFloat() / MODELS[model]!!.maxTokenCount


  override suspend fun sendRequest(data: String, systemMessage: String?): String {
    var attempts = maxRetries
    while (attempts > 0) {
      attempts--
      try {
        return makeRequest(data, systemMessage)
      } catch (e: Exception) {
        if (attempts == 0) throw e
      }
    }
    throw IllegalStateException("Failed to get response after $maxRetries attempts")
  }

  override suspend fun sendBatchRequest(data: List<String>, systemMessage: String?): List<String> {
    return data.map { prompt -> sendRequest(prompt, systemMessage) }
  }

  private suspend fun makeRequest(data: String, systemMessage: String?): String {
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
          content = data
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

  override fun close() {
    openAI.close()
  }
}