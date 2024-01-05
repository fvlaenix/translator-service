package com.fvlaenix.translation

import TOKEN
import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatRole
import com.aallam.openai.api.http.Timeout
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import kotlin.time.Duration.Companion.seconds

object GPTUtil {
  suspend fun translate(prompt: String, lines: List<String>): List<String> {
    val result = mutableListOf<String>()
    OpenAI(
      token = TOKEN,
      timeout = Timeout(socket = 180.seconds)
    ).use { openAI ->
      val systemMessage = ChatMessage(
        role = ChatRole.System,
        content = prompt
      )
      val translateMessage = ChatMessage(
        role = ChatRole.User,
        content = lines.joinToString(separator = "\n")
      )

      val chatCompletionRequest = ChatCompletionRequest(
        model = ModelId("gpt-4"),
        messages = listOf(systemMessage, translateMessage)
      )

      val completion = openAI.chatCompletion(chatCompletionRequest)
      completion.choices.forEach { choice ->
        result.addAll(choice.message.content!!.split("\n").map { it.trim() })
      }
    }
    result.removeAll { it.isBlank() }
    if (result.size != lines.size) throw GPTLinesNotMatchException(lines.size, result.size)
    return result
  }

  class GPTLinesNotMatchException(expected: Int, found: Int) : Exception("Count of lines not match in translation. Expected: $expected, found: $found")
}