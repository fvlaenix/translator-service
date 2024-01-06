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

  suspend fun translate(prompt: String, model: String, lines: List<String>, attempts: Int): List<String>? {
    var tries = 0
    while (tries < attempts) {
      try {
        return translate(prompt, model, lines)
      } catch (ignored: GPTLinesNotMatchException) {}
      tries--
    }
    return null
  }
  
  suspend fun translate(prompt: String, model: String, lines: List<String>): List<String> {
    val emptyLines = mutableSetOf<Int>()
    val filteredLines = lines.filterIndexed { index, s ->
      if (s.isBlank()) {
        emptyLines.add(index)
        return@filterIndexed false
      } else {
        return@filterIndexed true
      }
    }
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
        content = filteredLines.joinToString(separator = "\n")
      )

      val chatCompletionRequest = ChatCompletionRequest(
        model = ModelId(model),
        messages = listOf(systemMessage, translateMessage)
      )

      val completion = openAI.chatCompletion(chatCompletionRequest)
      completion.choices.forEach { choice ->
        result.addAll(choice.message.content!!.split("\n").map { it.trim() })
      }
    }
    result.removeAll { it.isBlank() }
    if (result.size != filteredLines.size) throw GPTLinesNotMatchException(lines.size, result.size)
    emptyLines.sorted().forEach { result.add(it, "") }
    return result
  }

  class GPTLinesNotMatchException(expected: Int, found: Int) : Exception("Count of lines not match in translation. Expected: $expected, found: $found")
}