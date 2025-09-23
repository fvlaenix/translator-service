package com.fvlaenix.translation.table

import com.fvlaenix.translation.translator.JsonModelTranslator
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class JsonTranslationBookServiceTest : AbstractTranslationBookServiceTest() {

  @BeforeEach
  fun setUp() {
    translator = JsonModelTranslator(testOpenAIService)
  }

  @Test
  fun testJsonTranslationWithMultipleLines() = runTest {
    // Create Excel file with character-based entries
    val textsToTranslate = listOf(
      "JOHN Line one to translate",
      "MARY Line two to translate",
      "PETER Line three to translate"
    )
    createBatchExcelFile("json_test.xlsx", textsToTranslate)

    // Setup JSON-style response for all requests
    testOpenAIService.setDefaultResponse(
      """[
      {"name": "JOHN", "text": "First line translated"},
      {"name": "MARY", "text": "Second line translated"},
      {"name": "PETER", "text": "Third line translated"}
    ]""".trimIndent()
    )

    // Create translation service
    translationBookService = createTranslationBookService()

    // Perform translation
    translationBookService.translate()

    // Verify translations
    assertEquals(
      "JOHN\nFirst line translated",
      translationBookService.getCache()["JOHN Line one to translate"]
    )
    assertEquals(
      "MARY\nSecond line translated",
      translationBookService.getCache()["MARY Line two to translate"]
    )
    assertEquals(
      "PETER\nThird line translated",
      translationBookService.getCache()["PETER Line three to translate"]
    )
  }
}