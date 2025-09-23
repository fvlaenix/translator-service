package com.fvlaenix.translation.table

import com.fvlaenix.translation.translator.TextModelTranslator
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class TextTranslationBookServiceTest : AbstractTranslationBookServiceTest() {

  @BeforeEach
  fun setUp() {
    translator = TextModelTranslator(testOpenAIService)
  }

  @Test
  fun testSimpleTextTranslation() = runTest {
    // Create Excel file with simple text
    createSimpleExcelFile("simple_test.xlsx", "Original text to translate")

    // Setup test response with system message
    testOpenAIService.setResponse(
      "Original text to translate",
      "Translated text"
    )

    // Create translation service
    translationBookService = createTranslationBookService()

    // Perform translation
    translationBookService.translate()

    // Verify translation was cached
    assertEquals(
      "Translated text",
      translationBookService.getCache()["Original text to translate"]
    )
  }

  @Test
  fun testBatchTranslation() = runTest {
    // Create Excel file with multiple entries
    val textsToTranslate = listOf("First text", "Second text")
    createBatchExcelFile("batch_test.xlsx", textsToTranslate)

    // Setup test response with system message
    testOpenAIService.setResponse(
      "First text\nSecond text",
      "First translation\nSecond translation"
    )

    // Create translation service
    translationBookService = createTranslationBookService()

    // Perform translation
    translationBookService.translate()

    // Verify translations were cached
    assertEquals(
      "First translation",
      translationBookService.getCache()["First text"]
    )
    assertEquals(
      "Second translation",
      translationBookService.getCache()["Second text"]
    )
  }
}