package com.fvlaenix.translation.translator

import com.fvlaenix.translation.textmodel.TextModelService
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class TextModelServiceTranslatorTest {

  class MockTextModelService(private val maxChars: Int) : TextModelService {
    var countOfRequests = 0
    var lastRequest: String? = null
    var lastSystemMessage: String? = null

    override suspend fun sendRequest(prompt: String, systemMessage: String?): String {
      countOfRequests++
      lastRequest = prompt
      lastSystemMessage = systemMessage

      if (fractionOfTokenLimit(prompt, systemMessage) > 0.8f) {
        throw IllegalArgumentException("Text exceeds token limit fraction")
      }

      return when {
        prompt.trim().startsWith("[") && prompt.trim().endsWith("]") -> {
          prompt.replace("\"text\": \"", "\"text\": \"translated: ")
        }

        else -> {
          prompt.split("\n").joinToString("\n") { "translated: $it" }
        }
      }
    }

    override suspend fun sendBatchRequest(prompts: List<String>, systemMessage: String?): List<String> {
      return prompts.map { sendRequest(it, systemMessage) }
    }

    override suspend fun fractionOfTokenLimit(prompt: String, systemMessage: String?): Float {
      val totalLength = prompt.length + (systemMessage?.length ?: 0)
      return totalLength.toFloat() / maxChars
    }
  }

  private lateinit var mockService: MockTextModelService
  private lateinit var translator: TextModelServiceTranslator
  private val textPrompt = ""
  private val jsonPrompt = ""

  @BeforeEach
  fun setUp() {
    mockService = MockTextModelService(100)
    translator = TextModelServiceTranslator(mockService, jsonPrompt, textPrompt)
  }

  @Nested
  inner class TextTranslationTests {
    @Test
    fun `test text translation with no splitting needed`() = runBlocking {
      val translations = listOf(
        TextTranslation("Short text 1"),
        TextTranslation("Short text 2")
      )

      val result = translator.translateText(translations)

      assertEquals(2, result.size)
      assertTrue(result[0].translation!!.contains("translated: Short text 1"))
      assertTrue(result[1].translation!!.contains("translated: Short text 2"))
    }

    @Test
    fun `test text translation with paragraph splitting`() = runBlocking {
      val longText = "This is the first paragraph.\n\nThis is the second paragraph.\n\nThis is the third paragraph."
      val translations = listOf(
        TextTranslation(longText)
      )

      val result = translator.translateText(translations)

      assertEquals(1, result.size)
      assertTrue(result[0].translation!!.contains("translated:"))
      assertTrue(result[0].translation!!.contains("first paragraph"))
      assertTrue(result[0].translation!!.contains("second paragraph"))
      assertTrue(result[0].translation!!.contains("third paragraph"))
    }

    @Test
    fun `test text translation with sentence splitting`() = runBlocking {
      val longSentence = "This is a very long sentence that will definitely not fit into our token limit. " +
          "We need to ensure that the translator properly splits it into multiple parts. " +
          "Then it should correctly merge these parts back together in the final result."
      val translations = listOf(
        TextTranslation(longSentence)
      )

      val result = translator.translateText(translations)

      assertEquals(1, result.size)
      assertTrue(result[0].translation!!.contains("translated:"))
      assertTrue(result[0].translation!!.contains("long sentence"))
      assertTrue(result[0].translation!!.contains("multiple parts"))
    }

    @Test
    fun `test text translation with mixed content`() = runBlocking {
      val translations = listOf(
        TextTranslation("Short text 1"),
        TextTranslation(
          "This is a very long text that will need to be split into multiple parts. " +
              "We want to ensure that splitting works and the results are merged."
        ),
        TextTranslation("Short text 2")
      )

      val result = translator.translateText(translations)

      assertEquals(3, result.size)
      assertTrue(result[0].translation!!.contains("translated: Short text 1"))
      assertTrue(result[1].translation!!.contains("translated:"))
      assertTrue(result[1].translation!!.contains("very long text"))
      assertTrue(result[1].translation!!.contains("multiple parts"))
      assertTrue(result[2].translation!!.contains("translated: Short text 2"))
    }

    @Test
    fun `test skipping already translated texts`() = runBlocking {
      val translations = listOf(
        TextTranslation("Short text 1", "Already translated 1"),
        TextTranslation("Short text 2"),
        TextTranslation("Short text 3", "Already translated 3")
      )

      val result = translator.translateText(translations)

      assertEquals(3, result.size)
      assertEquals("Already translated 1", result[0].translation)
      assertTrue(result[1].translation!!.contains("translated: Short text 2"))
      assertEquals("Already translated 3", result[2].translation)
    }
  }

  @Nested
  inner class DialogTranslationTests {
    @Test
    fun `test dialog translation with no splitting needed`() = runBlocking {
      val translations = listOf(
        DialogTranslation("Alice", "Hello, how are you?"),
        DialogTranslation("Bob", "I'm fine, thank you!")
      )

      val result = translator.translateJson(translations)

      assertEquals(2, result.size)
      assertTrue(result[0].translation!!.contains("translated: Hello"))
      assertTrue(result[1].translation!!.contains("translated: I'm fine"))
    }

    @Test
    fun `test dialog translation with splitting`() = runBlocking {
      val longDialog = "This is a dialog token limit. " +
          "We need to ensure multiple parts. " +
          "Then it should final result."
      val translations = listOf(
        DialogTranslation("Character", longDialog)
      )

      val result = translator.translateJson(translations)

      assertEquals(1, result.size)
      assertTrue(result[0].translation!!.contains("translated:"))
      assertTrue(result[0].translation!!.contains("dialog"))
      assertTrue(result[0].translation!!.contains("limit"))
    }

    @Test
    fun `test mixed translation types`() = runBlocking {
      val translations = listOf(
        DialogTranslation("Narrator", "Narrator text"),
        DialogTranslation("Alice", "Hello there!"),
        DialogTranslation("Narrator", "More narrator text")
      )
            
      val result = translator.translate(translations)

      assertEquals(3, result.size)
      assertTrue(result[0].translation!!.contains("translated:"))
      assertTrue(result[1].translation!!.contains("translated:"))
      assertTrue(result[2].translation!!.contains("translated:"))
    }

    @Test
    fun `test very big context`() = runBlocking {
      val richContextModel = MockTextModelService(800)
      val richContextTranslator = TextModelServiceTranslator(richContextModel, jsonPrompt, textPrompt)
      val translations = listOf(
        DialogTranslation("Narrator", "Narrator text"),
        DialogTranslation("Alice", "Hello there!"),
        DialogTranslation("Alice", "Hello there!"),
        DialogTranslation("Alice", "Hello there!"),
        DialogTranslation("Alice", "Hello there!"),
        DialogTranslation("Alice", "Hello there!"),
        DialogTranslation("Alice", "Hello there!"),
        DialogTranslation("Alice", "Hello there!"),
        DialogTranslation("Alice", "Hello there!"),
        DialogTranslation("Narrator", "More narrator text")
      )

      val result = richContextTranslator.translate(translations)
      assertEquals(10, result.size)
      assertEquals(1, richContextModel.countOfRequests)
    }
  }

  @Nested
  inner class EdgeCaseTests {
    @Test
    fun `test with empty translation list`() = runBlocking {
      val result = translator.translate(emptyList())
      assertTrue(result.isEmpty())
    }

    @Test
    fun `test with extremely long single line text`() = runBlocking {
      val veryLongText = "a".repeat(200)
      val translations = listOf(
        TextTranslation(veryLongText)
      )

      try {
        val result = translator.translateText(translations)
        assertEquals(1, result.size)
        assertTrue(result[0].translation!!.contains("translated:"))
      } catch (e: Exception) {
        assertTrue(e is IllegalArgumentException || e is TextModelServiceTranslator.IncorrectTranslation)
      }
    }

    @Test
    fun `test with text near token limit`() = runBlocking {
      val largerMockService = MockTextModelService(500)
      val newTranslator = TextModelServiceTranslator(largerMockService, jsonPrompt, textPrompt)

      val nearLimitText = "a".repeat(350)
      val translations = listOf(
        TextTranslation(nearLimitText)
      )

      val result = newTranslator.translateText(translations)

      assertEquals(1, result.size)
      assertTrue(result[0].translation!!.contains("translated:"))
    }
  }

  @Nested
  inner class AdvancedSplittingTests {
    @Test
    fun `test correct paragraph splitting and merging`() = runBlocking {
      val strictMockService = MockTextModelService(50)
      val strictTranslator = TextModelServiceTranslator(strictMockService, jsonPrompt, textPrompt)

      val paragraphText =
        "First paragraph with content.\n\nSecond paragraph with more content.\n\nThird paragraph with even more content."
      val translations = listOf(
        TextTranslation(paragraphText)
      )

      val result = strictTranslator.translateText(translations)

      assertEquals(1, result.size)
      val translation = result[0].translation ?: ""

      val paragraphs = translation.split("\n\n")
      assertTrue(paragraphs.size >= 2)

      assertTrue(paragraphs.any { it.contains("translated:") && it.contains("First paragraph") })
      assertTrue(paragraphs.any { it.contains("translated:") && it.contains("Second paragraph") })

      assertTrue(translation.contains("First paragraph"))
      assertTrue(translation.contains("Second paragraph"))
      assertTrue(translation.contains("Third paragraph"))
    }

    @Test
    fun `test correct sentence splitting and merging`() = runBlocking {
      val strictMockService = MockTextModelService(50)
      val strictTranslator = TextModelServiceTranslator(strictMockService, jsonPrompt, textPrompt)

      val sentenceText = "This is the first sentence. This is the second sentence. This is the third sentence."
      val translations = listOf(
        TextTranslation(sentenceText)
      )

      val result = strictTranslator.translateText(translations)

      assertEquals(1, result.size)
      val translation = result[0].translation ?: ""

      assertTrue(translation.contains("translated:") && translation.contains("first sentence"))
      assertTrue(translation.contains("second sentence"))
      assertTrue(translation.contains("third sentence"))
    }

    @Test
    fun `test multiple translations requiring multiple splits`() = runBlocking {
      val strictMockService = MockTextModelService(60)
      val strictTranslator = TextModelServiceTranslator(strictMockService, jsonPrompt, textPrompt)

      val translations = listOf(
        TextTranslation("First long sentence that needs splitting. Second part of the first."),
        TextTranslation("Another long sentence that also needs splitting. More content here.")
      )

      val result = strictTranslator.translateText(translations)

      assertEquals(2, result.size)

      val firstTranslation = result[0].translation ?: ""
      assertTrue(firstTranslation.contains("translated:") && firstTranslation.contains("First long"))
      assertTrue(firstTranslation.contains("Second part"))

      val secondTranslation = result[1].translation ?: ""
      assertTrue(secondTranslation.contains("translated:") && secondTranslation.contains("Another long"))
      assertTrue(secondTranslation.contains("More content"))
    }
  }
}