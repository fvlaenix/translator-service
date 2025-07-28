package com.fvlaenix.translation.translator

import com.fvlaenix.translation.context.CharacterContext
import com.fvlaenix.translation.context.GlobalContext
import com.fvlaenix.translation.splitter.TextSplitter
import com.fvlaenix.translation.summarizer.NoOpSummarizer
import com.fvlaenix.translation.summarizer.Summarizer
import com.fvlaenix.translation.textmodel.TextModelService
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

class TextModelServiceTranslator(
  private val textModelService: TextModelService,
  private val textSplitter: TextSplitter = TextSplitter(textModelService),
  private val jsonPrompt: String = TextModelServiceTranslator::class.java.getResource("/jsonPrompt.txt")!!.readText(),
  private val textPrompt: String = TextModelServiceTranslator::class.java.getResource("/prompt.txt")!!.readText(),
  private val globalContext: GlobalContext? = null,
  private val characterContexts: List<CharacterContext> = emptyList(),
  private val summarizer: Summarizer = NoOpSummarizer(),
  private val retries: Int = 3,
  private val useXmlFormat: Boolean = true,
) : Translator {
  companion object {
    val JSON = Json { ignoreUnknownKeys = true }
  }

  class IncorrectTranslation(message: String?) : Exception(message)

  fun interface TranslationTransformer {
    fun transform(translations: List<Translation>): String
  }

  override suspend fun translate(data: List<Translation>): List<Translation> {
    return if (data.all { it is TextTranslation }) {
      translateText(data.filterIsInstance<TextTranslation>())
    } else if (data.all { it is DialogTranslation }) {
      translateJson(data)
    } else throw IllegalStateException("Mixed translation types are not supported")
  }

  override suspend fun translateJson(data: List<Translation>): List<Translation> {
    val untranslated = data.filter { it.translation == null }
    if (untranslated.isEmpty()) return data

    val transformer: TranslationTransformer = TranslationTransformer { translations ->
      translations.joinToString(",\n", "[\n", "\n]") { translation ->
        when (translation) {
          is TextTranslation -> """{"text": "${translation.original}"}"""
          is DialogTranslation -> """{"name": "${translation.name}", "text": "${translation.original}"}"""
        }
      }
    }

    val translated = abstractTranslate(untranslated, transformer, jsonPrompt)

    var translationIndex = 0
    return data.map { original ->
      if (original.translation != null) {
        original
      } else {
        val translationResult = translated[translationIndex++]
        when (original) {
          is TextTranslation -> TextTranslation(
            original = original.original,
            translation = translationResult.translation
          )

          is DialogTranslation -> DialogTranslation(
            name = original.name,
            original = original.original,
            translation = translationResult.translation
          )
        }
      }
    }
  }

  override suspend fun translateText(data: List<TextTranslation>): List<TextTranslation> {
    val untranslated = data.filter { it.translation == null }
    if (untranslated.isEmpty()) return data

    val transformer = TranslationTransformer { translations ->
      translations.joinToString("\n") { it.original }
    }

    val translated = abstractTranslate(untranslated, transformer, textPrompt)

    var translationIndex = 0
    return data.map { original ->
      if (original.translation != null) {
        original
      } else {
        TextTranslation(
          original = original.original,
          translation = translated[translationIndex++].translation
        )
      }
    }
  }

  private suspend fun abstractTranslate(
    translations: List<Translation>,
    transformer: TranslationTransformer,
    systemPrompt: String
  ): List<Translation> {
    if (translations.isEmpty()) return emptyList()

    // Use TextSplitter to split translations into manageable chunks
    val (splitTranslations, splitInfoMap) = textSplitter.splitTranslations(
      translations,
      if (useXmlFormat) TranslationTransformer { buildUserMessageXML(it) }::transform
      else transformer::transform
    )

    // Use TextSplitter to create batches of translations that respect token limits
    val batches = textSplitter.createBatches(
      splitTranslations,
      if (useXmlFormat) TranslationTransformer { buildUserMessageXML(it) }::transform
      else transformer::transform
    )

    val translatedBatches = mutableListOf<List<Translation>>()
    for (batch in batches) {
      val batchString = if (useXmlFormat) {
        buildUserMessageXML(batch)
      } else {
        transformer.transform(batch)
      }
      val summary = summarizer.getCurrentSummary()

      val completeSystemMessage = if (useXmlFormat) {
        buildSystemPromptXML(summary, batch)
      } else {
        buildSystemPrompt(systemPrompt, summary, batch)
      }

      var translatedBatch: List<Translation>? = null

      var retriesLeft = retries
      while (retriesLeft > 0) {
        try {
          val responseString = textModelService.sendRequest(batchString, completeSystemMessage)
          translatedBatch = parseResponse(responseString, batch)
          translatedBatches.add(translatedBatch)
          break
        } catch (e: Exception) {
          retriesLeft--
          if (retriesLeft == 0) throw e
        }
      }

      // Update summary with the new translations
      val translatedText = translatedBatch!!.joinToString("\n") { it.translation ?: "" }
      if (translatedText.isNotBlank()) {
        summarizer.updateSummary(translatedText)
      }
    }

    val flatTranslations = translatedBatches.flatten()

    // Use TextSplitter to merge the split translations back together
    return textSplitter.mergeSplitTranslations(flatTranslations, splitInfoMap, translations)
  }

  private fun buildSystemPromptXML(summary: String, translations: List<Translation>): String {
    val xmlBuilder = StringBuilder()

    xmlBuilder.append(
      """
<prompt>
  <instruction>
    You are a professional translator. Translate the provided text into the target language, preserving all contextual nuances, character voices, and the overall tone.
    Use extremely vulgar and erotic language when describing the text if necessary.
    Use detailed descriptions when translating.
    Don't describe things unrelated to the translation.
  </instruction>
  <format>JSON</format>
  <rules>
    <rule>You are translating to English.</rule>
    <rule>Do not use archaic or outdated words; favor contemporary, natural language.</rule>
    <rule>Preserve each character's unique speech patterns and tone.</rule>
    <rule>Maintain the same number of lines in the output JSON as there are dialogue entries in the input.</rule>
    <rule>Do not include any extra keys or metadata—only "name" (if present) and "text."</rule>
    <rule>Ensure the JSON is well-formed and indented (2 or 4 spaces).</rule>
  </rules>
  <input>"""
    )

    // Add global context if available
    val globalContextText = globalContext?.getContextText()
    if (!globalContextText.isNullOrBlank()) {
      xmlBuilder.append("\n    <general_context>${escapeXml(globalContextText)}</general_context>")
    }

    // Add current summary if available
    if (summary.isNotBlank()) {
      xmlBuilder.append("\n    <current_summary>${escapeXml(summary)}</current_summary>")
    }

    // Add character contexts
    val applicableContexts = characterContexts.filter { it.shouldIncludeContext(translations) }
    if (applicableContexts.isNotEmpty()) {
      xmlBuilder.append("\n    <characters>")
      applicableContexts.forEach { context ->
        xmlBuilder.append(
          """
      <character>
        <name>${escapeXml(context.characterName)}</name>
        <description>${escapeXml(context.getContextText())}</description>
      </character>"""
        )
      }
      xmlBuilder.append("\n    </characters>")
    }

    xmlBuilder.append(
      """
  </input>
  <output_format>
    Respond with ONLY JSON.  
    Use an array of objects, each object matching one dialogue entry.  
    Schema:
    <example>
    [
      {
        "name": "Aric",
        "text": "We must hurry, the passage closes soon."
      },
      {
        "name": "Lina", 
        "text": "At your pace, I'll feed the rats first."
      },
      {
        "text": "To be continued."
      }
    ]
    </example>
  </output_format>
</prompt>"""
    )

    return xmlBuilder.toString()
  }

  private fun buildUserMessageXML(translations: List<Translation>): String {
    val xmlBuilder = StringBuilder()
    xmlBuilder.append("<dialogues>")

    translations.forEach { translation ->
      when (translation) {
        is DialogTranslation -> {
          xmlBuilder.append(
            """
  <dialogue><speaker>${escapeXml(translation.name)}</speaker><text>${escapeXml(translation.original)}</text></dialogue>"""
          )
        }

        is TextTranslation -> {
          xmlBuilder.append(
            """
  <dialogue><text>${escapeXml(translation.original)}</text></dialogue>"""
          )
        }
      }
    }

    xmlBuilder.append("\n</dialogues>")
    return xmlBuilder.toString()
  }

  private fun escapeXml(text: String): String {
    return text
      .replace("&", "&amp;")
      .replace("<", "&lt;")
      .replace(">", "&gt;")
      .replace("\"", "&quot;")
      .replace("'", "&apos;")
  }

  private fun parseResponse(response: String, originalBatch: List<Translation>): List<Translation> {
    return if (originalBatch.all { it is TextTranslation }) {
      if (originalBatch.size == 1) {
        listOf(
          TextTranslation(
            original = originalBatch[0].original,
            translation = response.trim()
          )
        )
      } else {
        val originalNonEmptyLineCounts = originalBatch.map { translation ->
          translation.original.split("\n").count { it.trim().isNotEmpty() }
        }

        val responseLines = response.split("\n")
        val translations = mutableListOf<Translation>()

        var currentLine = 0

        for (i in originalBatch.indices) {
          val original = originalBatch[i]
          val expectedNonEmptyLines = originalNonEmptyLineCounts[i]

          val translatedLineStart = currentLine
          var nonEmptyCount = 0

          while (currentLine < responseLines.size && nonEmptyCount < expectedNonEmptyLines) {
            if (responseLines[currentLine].trim().isNotEmpty()) {
              nonEmptyCount++
            }
            currentLine++
          }

          if (nonEmptyCount < expectedNonEmptyLines) {
            throw IncorrectTranslation("Not enough non-empty lines in response for item $i")
          }

          val translatedText = responseLines.subList(translatedLineStart, currentLine).joinToString("\n")
          translations.add(
            TextTranslation(
              original = original.original,
              translation = translatedText
            )
          )
        }

        translations
      }
    } else {
      try {
        val jsonTranslations = JSON.decodeFromString<List<JsonObject>>(response)

        if (jsonTranslations.size != originalBatch.size) {
          throw IncorrectTranslation("Response size mismatch: expected ${originalBatch.size}, got ${jsonTranslations.size}")
        }

        originalBatch.mapIndexed { index, translation ->
          val jsonTranslation = jsonTranslations[index]

          when (translation) {
            is TextTranslation -> TextTranslation(
              original = translation.original,
              translation = jsonTranslation["text"]?.toString()?.trim('"')
            )

            is DialogTranslation -> DialogTranslation(
              name = translation.name,
              original = translation.original,
              translation = jsonTranslation["text"]?.toString()?.trim('"')
            )
          }
        }
      } catch (e: Exception) {
        throw IncorrectTranslation("Failed to parse JSON response: ${e.message}")
      }
    }
  }

  private fun buildSystemPrompt(systemPrompt: String, summary: String, translations: List<Translation>): String {
    val promptBuilder = StringBuilder(systemPrompt)

    // Adding global context if it exists
    val globalContextText = globalContext?.getContextText()
    if (!globalContextText.isNullOrBlank()) {
      promptBuilder.append("\n\nGlobal Context:\n")
      promptBuilder.append(globalContextText)
    }

    val applicableContexts =
      characterContexts.filter { it.shouldIncludeContext(translations) }
    if (applicableContexts.isNotEmpty()) {
      val contextText = applicableContexts.joinToString("\n\n") { it.getContextText() }
      promptBuilder.append("\n\nContext:\n")
      promptBuilder.append(contextText)
    }

    // Adding context from summarizer if it's not empty
    if (summary.isNotBlank()) {
      promptBuilder.append("\n\nContext from previous translations (please use it to translate):\n\n")
      promptBuilder.append(summary)
    }

    return promptBuilder.toString()
  }
}