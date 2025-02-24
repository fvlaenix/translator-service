package com.fvlaenix.translation.translator

import com.fvlaenix.translation.textmodel.TextModelService
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

class OpenAIGPTTranslator(
  private val textModelService: TextModelService,
  private val jsonPrompt: String = OpenAIGPTTranslator::class.java.getResource("/jsonPrompt.txt")!!.readText(),
  private val textPrompt: String = OpenAIGPTTranslator::class.java.getResource("/prompt.txt")!!.readText()
) : Translator {

  class IncorrectTranslation(message: String?) : Exception(message)

  @Suppress("UNCHECKED_CAST")
  override suspend fun translate(data: List<Translation>): List<Translation> {
    return if (data.all { it is TextTranslation }) {
      translateText(data.filterIsInstance<TextTranslation>())
    } else {
      translateJson(data)
    }
  }

  override suspend fun translateJson(data: List<Translation>): List<Translation> {
    val untranslated = data.filter { it.translation == null }
    if (untranslated.isEmpty()) return data

    val jsonData = untranslated.joinToString(",\n", "[\n", "\n]") { translation ->
      when (translation) {
        is TextTranslation -> """{"text": "${translation.original}"}"""
        is DialogTranslation -> """{"name": "${translation.name}", "text": "${translation.original}"}"""
      }
    }

    val translatedJson = try {
      textModelService.sendRequest(jsonData, jsonPrompt)
    } catch (e: Exception) {
      throw IllegalStateException("Failed to translate: ${e.message}")
    }

    val translations = Json { ignoreUnknownKeys = true }
      .decodeFromString<List<JsonObject>>(translatedJson)

    if (translations.size != untranslated.size) {
      throw IncorrectTranslation("Response size mismatch")
    }

    var translationIndex = 0
    return data.map { original ->
      if (original.translation != null) {
        original
      } else {
        val translation = translations[translationIndex++]
        when (original) {
          is TextTranslation -> TextTranslation(
            original = original.original,
            translation = translation["text"]?.toString()?.trim('"')
          )

          is DialogTranslation -> DialogTranslation(
            name = translation["name"]?.toString()?.trim('"') ?: original.name,
            original = original.original,
            translation = translation["text"]?.toString()?.trim('"')
          )
        }
      }
    }
  }

  override suspend fun translateText(data: List<TextTranslation>): List<TextTranslation> {
    val untranslated = data.filter { it.translation == null }
    if (untranslated.isEmpty()) return data

    val request = untranslated.joinToString("\n") { it.original }
    val translations = try {
      val response = textModelService.sendRequest(request, textPrompt)
      response.split("\n").map { it.trim() }
    } catch (e: Exception) {
      throw IllegalStateException("Failed to translate: ${e.message}")
    }

    if (translations.size != untranslated.size) {
      throw IncorrectTranslation("Response size mismatch")
    }

    var translationIndex = 0
    return data.map { original ->
      if (original.translation != null) {
        original
      } else {
        TextTranslation(
          original = original.original,
          translation = translations[translationIndex++]
        )
      }
    }
  }

  companion object {
    fun create(textModelService: TextModelService): OpenAIGPTTranslator = OpenAIGPTTranslator(textModelService)
  }
}
