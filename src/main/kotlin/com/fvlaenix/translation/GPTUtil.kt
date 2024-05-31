package com.fvlaenix.translation

import TOKEN
import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatRole
import com.aallam.openai.api.http.Timeout
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import com.fvlaenix.translation.gpt.GPT
import kotlin.time.Duration.Companion.seconds

@Deprecated(message = "Use GPT for this now")
object GPTUtil {

  data class TranslationData(
    val original: String,
    val translation: String?
  )

  suspend fun translate(
    prompt: String,
    model: String,
    linesWithTranslation: List<TranslationData>,
    attempts: Int,
    grouper: (List<String>) -> List<List<String>>
  ): List<TranslationData> {
    val alreadyTranslatedData: MutableMap<Int, TranslationData> = mutableMapOf()
    val notTranslatedData: MutableList<String> = mutableListOf()
    linesWithTranslation.forEachIndexed { index, line ->
      if (line.translation == null) {
        notTranslatedData.add(line.original)
      } else {
        alreadyTranslatedData[index] = line
      }
    }
    val batchedData = grouper(notTranslatedData)
    var itOnTranslation = 0
    var itOnLines = 0
    val result: MutableList<TranslationData> = mutableListOf()
    while (itOnLines < linesWithTranslation.size) {
      if (alreadyTranslatedData.containsKey(itOnLines)) {
        result.add(alreadyTranslatedData[itOnLines]!!)
        itOnLines++
      } else {
        val toTranslateData = batchedData[itOnTranslation]
        itOnTranslation++
        val translationData = translate(prompt, model, toTranslateData, attempts)
        if (translationData != null) {
          toTranslateData.zip(translationData).forEach { pair ->
            result.add(TranslationData(pair.first, pair.second))
            itOnLines++
          }
        } else {
          toTranslateData.forEach { toTranslate ->
            result.add(TranslationData(toTranslate, null))
            itOnLines++
          }
        }
      }
    }
    return result
  }

  suspend fun translate(prompt: String, model: String, lines: List<String>, attempts: Int): List<String>? {
    var tries = 0
    while (tries < attempts) {
      try {
        return translate(prompt, model, lines)
      } catch (ignored: GPTLinesNotMatchException) {}
      tries++
    }
    return null
  }
  
  suspend fun translateNew(prompt: String, model: String, lines: List<GPT.TextTranslation>): List<GPT.TextTranslation> {
    val toTranslateWithoutFilter = lines.map { GPT.TextTranslation(it.original, it.translation) }
    val toTranslate = toTranslateWithoutFilter.filter { it.translation == null }
    if (toTranslate.isNotEmpty()) {
      val response = translate(prompt, model, toTranslate.map { it.original })
      if (response.size != toTranslate.size) throw GPT.IncorrectTranslation("Not matched sizes")
      toTranslate.zip(response).forEach { (translation1, translation2) ->
        translation1.translation = translation2
      }
    }
    return toTranslateWithoutFilter
  }
  
  suspend fun translate(prompt: String, model: String, lines: List<String>): List<String> {
    check(model == "gpt-4" || model == "gpt-4-turbo") { "Can't use old API with anything except gpt-4. Found: $model" }
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
    if (result.size != filteredLines.size) {
      throw GPTLinesNotMatchException(lines.size, result.size)
    }
    emptyLines.sorted().forEach { result.add(it, "") }
    return result
  }

  class GPTLinesNotMatchException(expected: Int, found: Int) : Exception("Count of lines not match in translation. Expected: $expected, found: $found")
}