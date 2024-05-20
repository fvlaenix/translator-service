package com.fvlaenix.translation.gpt

import TOKEN
import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatRole
import com.aallam.openai.api.http.Timeout
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import com.fvlaenix.translation.GPTUtil
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.seconds

object GPT {
  private val jsonPrompt = GPT::class.java.getResource("/jsonPrompt.txt")!!.readText()
  private val txtPrompt = GPT::class.java.getResource("/prompt_EN.txt")!!.readText()
  
  sealed class Translation(
    val original: String,
    var translation: String?
  )
  
  class TextTranslation(
    original: String,
    translation: String?
  ) : Translation(original, translation)
  
  class DialogTranslation(
    val name: String,
    original: String,
    translation: String?
  ) : Translation(original, translation)
  
  class IncorrectTranslation : Exception()

  @Suppress("UNCHECKED_CAST")
  suspend fun standardRequest(data: List<GPT.Translation>): List<GPT.Translation> {
    return when (data.first()) {
      is DialogTranslation -> standardJsonRequest(data as List<DialogTranslation>)
      is TextTranslation -> standardTextRequest(data as List<TextTranslation>)
    }
  }
  
  suspend fun standardJsonRequest(data: List<DialogTranslation>) =
    jsonRequest("gpt-4-turbo", jsonPrompt, data)
  
  suspend fun standardTextRequest(data: List<TextTranslation>): List<TextTranslation> =
    GPTUtil.translateNew(txtPrompt, "gpt-4-turbo", data)
  
  suspend fun jsonRequest(model: String, prompt: String, data: List<DialogTranslation>): List<DialogTranslation> {
    @Serializable
    data class Request(
      val name: String,
      val text: String
    )
    val alreadyTranslated = mutableListOf<Pair<Int, DialogTranslation>>()
    val requestData = data
      .filterIndexed { index, dialogTranslation -> 
        if (dialogTranslation.translation != null) {
          alreadyTranslated.add(index to dialogTranslation)
          false
        } else {
          true
        }
      }
      .map { Request(it.name, it.original) }
    
    val response: List<Request> = if (requestData.isEmpty()) {
      return emptyList()
    } else {
      val json = Json.encodeToString(requestData)
      val answer = request(model, prompt, json)
      Json.decodeFromString(answer)
    }
    if (response.size != requestData.size) {
      throw IncorrectTranslation()
    }
    
    val translatedData: List<DialogTranslation> = requestData.zip(response).map { (request, response) ->
      if (request.name != response.name) throw IncorrectTranslation()
      DialogTranslation(
        name = response.name,
        original = request.text,
        translation = response.text
      )
    }
    val merged: List<DialogTranslation> = buildList { 
      translatedData.forEach { translated ->
        while (alreadyTranslated.isNotEmpty() && alreadyTranslated[0].first == size) {
          add(alreadyTranslated[0].second)
          alreadyTranslated.removeAt(0)
        }
        add(translated)
      }
    }
    return merged
  }
  
  /**
   * Stupid request to OpenAI without checking
   */
  suspend fun request(model: String, prompt: String, lines: String): String {
    val result = OpenAI(
      token = TOKEN,
      timeout = Timeout(socket = 180.seconds)
    ).use { openAI: OpenAI ->
      val systemMessage = ChatMessage(
        role = ChatRole.System,
        content = prompt
      )
      val translateMessage = ChatMessage(
        role = ChatRole.User,
        content = lines
      )

      val chatCompletionRequest = ChatCompletionRequest(
        model = ModelId(model),
        messages = listOf(systemMessage, translateMessage)
      )
      val completion = openAI.chatCompletion(chatCompletionRequest)
      assert(completion.choices.size == 1)
      completion.choices[0].message.content!!
    }
    return result
  }
}