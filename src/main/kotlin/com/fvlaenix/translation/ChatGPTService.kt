package com.fvlaenix.translation

import com.fvlaenix.translation.protobuf.TranslationRequest
import com.fvlaenix.translation.protobuf.TranslationResponse
import com.fvlaenix.translation.protobuf.TranslationServiceGrpcKt
import com.fvlaenix.translation.protobuf.translationResponse

class ChatGPTService(private val model: String): TranslationServiceGrpcKt.TranslationServiceCoroutineImplBase() {

  companion object {
    val PERSONA_TRANSLATION_REQUEST = ChatGPTService::class.java.getResourceAsStream("/prompt.txt")!!.bufferedReader().readText()
  }

  override suspend fun translation(request: TranslationRequest): TranslationResponse {
    val untranslatedText = request.text
    return translationResponse {
      this.text = runCatching { GPTUtil.translate(PERSONA_TRANSLATION_REQUEST, model, untranslatedText.split("\n")).joinToString(separator = "\n") }
        .getOrElse { "Exception!" }
    }
  }
}