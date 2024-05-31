package com.fvlaenix.translation.gpt

import TOKEN
import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatRole
import com.aallam.openai.api.http.Timeout
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import com.fvlaenix.translation.GPTUtil
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
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
  
  class IncorrectTranslation(val s: String?) : Exception(s)

  @Suppress("UNCHECKED_CAST")
  suspend fun standardRequest(data: List<GPT.Translation>): List<GPT.Translation> {
    return if (data.all { it is TextTranslation }) standardTextRequest(data as List<TextTranslation>)
    else standardJsonRequest(data)
  }
  
  suspend fun standardJsonRequest(data: List<GPT.Translation>): List<GPT.Translation> {
    var attempts = 5
    while (attempts > 0) {
      attempts--
      try {
        return jsonRequest("gpt-4-turbo", jsonPrompt, data)
      } catch (e: Exception) {
        if (attempts == 0) throw e
      }
    }
    throw IllegalStateException()
  }
  
  suspend fun standardTextRequest(data: List<TextTranslation>): List<TextTranslation> =
    GPTUtil.translateNew(txtPrompt, "gpt-4-turbo", data)
  
  @OptIn(ExperimentalSerializationApi::class)
  suspend fun jsonRequest(model: String, prompt: String, data: List<GPT.Translation>): List<GPT.Translation> {
    @Serializable
    data class Request(
      @EncodeDefault(EncodeDefault.Mode.NEVER) val name: String? = null,
      val text: String
    )
    val alreadyTranslated = mutableListOf<Pair<Int, GPT.Translation>>()
    val requestData = data
      .filterIndexed { index, dialogTranslation -> 
        if (dialogTranslation.translation != null) {
          alreadyTranslated.add(index to dialogTranslation)
          false
        } else {
          true
        }
      }
      .map {
        when (it) {
          is TextTranslation -> Request(null, it.original)
          is DialogTranslation -> Request(it.name, it.original)
        }
      }
    
    val response: List<Request> = if (requestData.isEmpty()) {
      return emptyList()
    } else {
      val json = Json.encodeToString(requestData)
      val answer = request(model, prompt, json)
      Json { ignoreUnknownKeys = true }.decodeFromString(answer)
    }
    if (response.size != requestData.size) {
      throw IncorrectTranslation("Not matched sizes")
    }
    
    val translatedData: List<GPT.Translation> = requestData.zip(response).map { (request, response) ->
      // if (request.name != response.name) throw IncorrectTranslation("Names not met: ${request.name} - ${response.name}")
      if (response.name == null) {
        TextTranslation(
          original = request.text,
          translation = response.text
        )
      } else {
        DialogTranslation(
          name = response.name,
          original = request.text,
          translation = response.text
        )
      }
    }
    val merged: List<GPT.Translation> = buildList { 
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