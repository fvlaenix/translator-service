package com.fvlaenix.translation.translator

import com.aallam.openai.api.chat.ChatMessage
import com.fvlaenix.text.TextModelService
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class XmlModelTranslatorPromptTest {

  @Test
  fun `defaults to the existing English guidance`() = runBlocking {
    val service = CapturingService()

    XmlModelTranslator(service).translate(listOf(TextTranslation("synthetic input")))

    assertContains(service.systemPrompt, "You are translating to English.")
    assertContains(service.systemPrompt, "Do not use archaic or outdated words; favor contemporary, natural language.")
  }

  @Test
  fun `supports French target language and guidance`() = runBlocking {
    val service = CapturingService()

    XmlModelTranslator(
      textModelService = service,
      targetLanguage = "Metropolitan French",
      translationGuidance = listOf("Synthetic French guidance.")
    ).translate(listOf(TextTranslation("synthetic input")))

    assertContains(service.systemPrompt, "You are translating to Metropolitan French.")
    assertContains(service.systemPrompt, "Synthetic French guidance.")
    assertFalse(service.systemPrompt.contains("You are translating to English."))
    assertFalse(service.systemPrompt.contains("favor contemporary, natural language"))
  }

  @Test
  fun `decodes JSON string escapes`() = runBlocking {
    val service = CapturingService("""[{"text":"l\u0027ami"}]""")

    val result = XmlModelTranslator(service).translate(listOf(TextTranslation("synthetic input")))

    assertEquals("l'ami", result.single().translation)
  }

  @Test
  fun `rejects a response with missing text`() = runBlocking {
    assertFailsWith<AbstractTextModelTranslator.IncorrectTranslation> {
      XmlModelTranslator(CapturingService("""[{}]"""), retries = 1)
        .translate(listOf(TextTranslation("synthetic input")))
    }
  }

  @Test
  fun `rejects a response with blank text`() = runBlocking {
    assertFailsWith<AbstractTextModelTranslator.IncorrectTranslation> {
      XmlModelTranslator(CapturingService("""[{"text":"  "}]"""), retries = 1)
        .translate(listOf(TextTranslation("synthetic input")))
    }
  }

  private class CapturingService(
    private val response: String = """[{"text":"translated"}]"""
  ) : TextModelService {
    lateinit var systemPrompt: String

    override suspend fun fractionOfTokenLimit(text: String): Float = 0.01f

    override suspend fun sendRequest(prompt: String?, userMessage: String): String {
      systemPrompt = checkNotNull(prompt)
      return response
    }

    override suspend fun sendRequest(messages: List<ChatMessage>): String =
      error("Unexpected overload")
  }
}
