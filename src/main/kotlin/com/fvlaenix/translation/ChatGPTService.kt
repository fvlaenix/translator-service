package com.fvlaenix.translation

import com.fvlaenix.alive.protobuf.IsAliveRequest
import com.fvlaenix.alive.protobuf.IsAliveResponse
import com.fvlaenix.alive.protobuf.isAliveResponse
import com.fvlaenix.translation.protobuf.*
import java.nio.file.Path
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.io.path.appendLines

private val LOGGER = Logger.getLogger(ChatGPTService::class.java.name)

class ChatGPTService(private val model: String): TranslationServiceGrpcKt.TranslationServiceCoroutineImplBase() {

  companion object {
    val PERSONA_TRANSLATION_REQUEST = ChatGPTService::class.java.getResourceAsStream("/prompt.txt")!!.bufferedReader().readText()
    const val COUNT_WORDS_LIMIT = 250
    const val COUNT_ATTEMPTS_TO_TRANSLATE = 5
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

  override suspend fun translationFile(request: TranslationFilesRequest): TranslationFilesResponse {
    val files = request.requestsList
    val original = files.flatMap { file -> file.blocksList.map { block -> GPTUtil.TranslationData(block.text, if (block.hasTranslation()) block.translation else null) } }
    val translation = GPTUtil.translate(PERSONA_TRANSLATION_REQUEST, model, original, COUNT_ATTEMPTS_TO_TRANSLATE) { Util.splitWords(it, COUNT_WORDS_LIMIT) }
    if (translation.size != original.size) {
      LOGGER.severe("Sizes of translation and original is not matched: $original\n\n$translation")
      return translationFilesResponse { this.error = "Internal error: Sizes of translation and original is not matched. Report this to person who run bot" }
    }
    var it = 0
    val errors = mutableListOf<Pair<String, Int>>()
    val result = files.map { file ->
      val fileName = file.fileName
      val blockList = List(file.blocksList.size) { index ->
        val currentTranslation = translation[it]
        it++
        translationBlock {
          this.text = currentTranslation.original
          if (currentTranslation.translation != null) {
            this.translation = currentTranslation.translation
          } else {
            errors.add(Pair(fileName, index))
          }
        }
      }
      TranslationFile.newBuilder().setFileName(fileName).addAllBlocks(blockList).build()
    }
    val responseBuilder = TranslationFilesResponse.newBuilder().addAllResponse(result)
    if (errors.isNotEmpty()) {
      responseBuilder.setError("Not translated because of errors (you can send it again): ${errors.joinToString{ "${it.first}:${it.second}" }}")
    }
    return responseBuilder.build()
  }

  override suspend fun isAlive(request: IsAliveRequest): IsAliveResponse {
    return isAliveResponse {  }
  }
}