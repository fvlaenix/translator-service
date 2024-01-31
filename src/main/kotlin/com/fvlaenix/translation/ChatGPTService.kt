package com.fvlaenix.translation

import com.fvlaenix.translation.protobuf.TranslationRequest
import com.fvlaenix.translation.protobuf.TranslationResponse
import com.fvlaenix.translation.protobuf.TranslationServiceGrpcKt
import com.fvlaenix.translation.protobuf.translationResponse
import java.util.logging.Level
import java.util.logging.Logger

private val LOGGER = Logger.getLogger(ChatGPTService::class.java.name)

class ChatGPTService(private val model: String): TranslationServiceGrpcKt.TranslationServiceCoroutineImplBase() {

  companion object {
    val PERSONA_TRANSLATION_REQUEST = ChatGPTService::class.java.getResourceAsStream("/prompt.txt")!!.bufferedReader().readText()
  }

  override suspend fun translation(request: TranslationRequest): TranslationResponse {
    val untranslatedText = request.text
    return translationResponse {
      runCatching {
        this@translationResponse.text = GPTUtil.translate(
          PERSONA_TRANSLATION_REQUEST, model,
          untranslatedText.split("\n"), 5)?.joinToString(separator = "\n") ?: throw Exception("Can't translate with such count of attempts")
      }
        .getOrElse {
          LOGGER.log(Level.SEVERE, "Exception while trying to get translation", it)
          this.error = "Exception while trying to get translation. You can tag the person who ran the bot to see what the problem might be"
        }
    }
  }
}