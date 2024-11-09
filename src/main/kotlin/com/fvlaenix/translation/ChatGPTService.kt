package com.fvlaenix.translation

import com.fvlaenix.alive.protobuf.IsAliveRequest
import com.fvlaenix.alive.protobuf.IsAliveResponse
import com.fvlaenix.alive.protobuf.isAliveResponse
import com.fvlaenix.translation.gpt.GPT
import com.fvlaenix.translation.protobuf.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.logging.Level
import java.util.logging.Logger

private val LOG = Logger.getLogger(ChatGPTService::class.java.name)

class ChatGPTService(private val model: String): TranslationServiceGrpcKt.TranslationServiceCoroutineImplBase() {

  private val atomicId = AtomicInteger(0)

  private fun logEnter(method: String, id: Int, info: String) {
    LOG.info("Called $method with id $id: $info")
  }

  private fun logEnd(method: String, id: Int) {
    LOG.info("Completed $method with id $id")
  }

  private suspend fun <T> withLog(method: String, info: String, body: suspend () -> T): T {
    val id = atomicId.incrementAndGet()
    logEnter(method, id, info)
    val result = try {
      body()
    } catch (e: Exception) {
      LOG.log(Level.SEVERE, "Failed to complete $method with id $id", e)
      throw e
    } finally {
      logEnd(method, id)
    }
    return result
  }

  companion object {
    val PERSONA_TRANSLATION_REQUEST = ChatGPTService::class.java.getResourceAsStream("/prompt.txt")!!.bufferedReader().readText()
    const val COUNT_WORDS_LIMIT = 250
    const val COUNT_ATTEMPTS_TO_TRANSLATE = 5
  }

  override suspend fun translation(request: TranslationRequest): TranslationResponse {
    return withLog("translation", "text:${request.text.take(256)}") {
      val untranslatedText = request.text
      translationResponse {
        runCatching {
          this@translationResponse.text = GPTUtil.translate(
            PERSONA_TRANSLATION_REQUEST, model,
            untranslatedText.split("\n"), 5)?.joinToString(separator = "\n") ?: throw Exception("Can't translate with such count of attempts")
        }
          .getOrElse {
            LOG.log(Level.SEVERE, "Exception while trying to get translation", it)
            this.error = "Exception while trying to get translation. You can tag the person who ran the bot to see what the problem might be"
          }
      }
    }
  }

  override suspend fun translationFile(request: TranslationFilesRequest): TranslationFilesResponse {
    return withLog("translationFile", "filenames:${request.requestsList.joinToString { it.fileName }}") {
      val files = request.requestsList
      val original = files.flatMap { file -> file.blocksList.map { block -> GPT.TextTranslation(block.text, if (block.hasTranslation()) block.translation else null) } }
      val translation = GPT.standardRequest(original)
      if (translation.size != original.size) {
        LOG.severe("Sizes of translation and original is not matched: $original\n\n$translation")
        return@withLog translationFilesResponse { this.error = "Internal error: Sizes of translation and original is not matched. Report this to person who run bot" }
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
              this.translation = currentTranslation.translation!!
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
      return@withLog responseBuilder.build()
    }
  }

  override suspend fun isAlive(request: IsAliveRequest): IsAliveResponse {
    return withLog("isAlive", "") {
      isAliveResponse {  }
    }
  }
}