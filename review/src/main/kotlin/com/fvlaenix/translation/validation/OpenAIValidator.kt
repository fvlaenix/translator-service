package com.fvlaenix.translation.validation

import com.aallam.openai.api.http.Timeout
import com.aallam.openai.api.logging.LogLevel
import com.aallam.openai.api.moderation.ModerationModel
import com.aallam.openai.api.moderation.ModerationRequest
import com.aallam.openai.api.moderation.ModerationResult
import com.aallam.openai.client.LoggingConfig
import com.aallam.openai.client.OpenAI
import com.fvlaenix.translation.TOKEN
import java.io.Closeable
import kotlin.time.Duration.Companion.seconds

class OpenAIValidator(
  private val model: String = "omni-moderation-2024-09-26",
) : Closeable {

  val openAI = OpenAI(
    logging = LoggingConfig(LogLevel.None),
    token = TOKEN,
    timeout = Timeout(socket = 120.seconds)
  )

  suspend fun makeStrictRequestFlagged(message: List<String>): Boolean {
    val rawResult = makeRawRequest(message)
    return rawResult.any { it.flagged }
  }

  suspend fun makeNotStrictRequestFlagged(message: List<String>): Boolean {
    val rawResult = makeRawRequest(message)
    return rawResult.any { it.categories.sexualMinors }
  }

  suspend fun makeRawRequest(message: List<String>): List<ModerationResult> {
    if (message.isEmpty()) throw IllegalArgumentException("Message is empty")
    val moderationRequest = ModerationRequest(
      input = message,
      model = ModerationModel(model)
    )
    val moderation = openAI.moderations(moderationRequest)
    return moderation.results
  }

  override fun close() {
    openAI.close()
  }
}