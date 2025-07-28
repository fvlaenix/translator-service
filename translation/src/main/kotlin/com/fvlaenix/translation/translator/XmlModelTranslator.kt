package com.fvlaenix.translation.translator

import com.fvlaenix.translation.context.CharacterContext
import com.fvlaenix.translation.context.GlobalContext
import com.fvlaenix.translation.splitter.TextSplitter
import com.fvlaenix.translation.summarizer.NoOpSummarizer
import com.fvlaenix.translation.summarizer.Summarizer
import com.fvlaenix.translation.textmodel.TextModelService
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * Translator that handles translations using XML input format with JSON output.
 * Supports both TextTranslation and DialogTranslation objects.
 */
class XmlModelTranslator(
  textModelService: TextModelService,
  textSplitter: TextSplitter = TextSplitter(textModelService),
  globalContext: GlobalContext? = null,
  characterContexts: List<CharacterContext> = emptyList(),
  summarizer: Summarizer = NoOpSummarizer(),
  retries: Int = 3
) : AbstractTextModelTranslator(
  textModelService = textModelService,
  textSplitter = textSplitter,
  globalContext = globalContext,
  characterContexts = characterContexts,
  summarizer = summarizer,
  retries = retries
) {

  companion object {
    val JSON = Json { ignoreUnknownKeys = true }
  }

  override fun validateInput(data: List<Translation>) {
    // XML translator accepts both TextTranslation and DialogTranslation
    if (!data.all { it is TextTranslation || it is DialogTranslation }) {
      throw IllegalArgumentException("XmlModelTranslator only supports TextTranslation and DialogTranslation objects")
    }
  }

  override fun buildSystemPrompt(summary: String, translations: List<Translation>): String {
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

  override fun buildUserMessage(translations: List<Translation>): String {
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

  override fun parseResponse(response: String, originalBatch: List<Translation>): List<Translation> {
    try {
      val jsonTranslations = JSON.decodeFromString<List<JsonObject>>(response)

      if (jsonTranslations.size != originalBatch.size) {
        throw IncorrectTranslation("Response size mismatch: expected ${originalBatch.size}, got ${jsonTranslations.size}")
      }

      return originalBatch.mapIndexed { index, translation ->
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