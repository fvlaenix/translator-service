package com.fvlaenix.translation.table

import com.fvlaenix.translation.NamesService
import com.fvlaenix.translation.textmodel.TestTextModelService
import com.fvlaenix.translation.translator.TextModelServiceTranslator
import com.fvlaenix.translation.translator.Translator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createFile
import kotlin.io.path.outputStream
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class TestServiceTranslationBookServiceTest {
  @TempDir
  lateinit var tempDir: Path

  private lateinit var testOpenAIService: TestTextModelService
  private lateinit var translator: Translator
  private lateinit var namesService: NamesService
  private lateinit var translationBookService: TranslationBookService

  @BeforeEach
  fun setup() {
    testOpenAIService = TestTextModelService()
    testOpenAIService.reset() // Ensure clean state

    // Set default response for any unknown request
    testOpenAIService.setDefaultResponse("Test response")

    translator = TextModelServiceTranslator(testOpenAIService)

    // Create a test Excel file
    val testFile = tempDir.resolve("simple_test.xlsx")
    testFile.createFile()

    // Create workbook with test data
    val workbook = XSSFWorkbook().apply {
      createSheet("Test").apply {
        // Header row
        createRow(0).apply {
          createCell(0).setCellValue("fvlaenix-magic-words")
          createCell(1).setCellValue("SRPG")
          createCell(2).setCellValue("SRPG")
        }
        // Column names
        createRow(1).apply {
          createCell(0).setCellValue("totranslate")
          createCell(1).setCellValue("translated")
        }
        // Test data
        createRow(2).apply {
          createCell(0).setCellValue("Original text to translate")
          createCell(1).setCellValue("")  // Empty string for untranslated text
        }
      }
    }

    // Write the workbook to file
    testFile.outputStream().use { workbook.write(it) }

    // Initialize NamesService with test data
    namesService = NamesService(mapOf())

    translationBookService = TranslationBookService(
      path = tempDir,
      language = "en",
      gameId = "test",
      translator = translator,
      namesService = namesService
    )
  }

  @Test
  fun testSimpleTextTranslation() = runTest {
    // Setup test response with system message
    testOpenAIService.setResponse(
      "Original text to translate",
      "Translated text",
      textPrompt = TextModelServiceTranslator::class.java.getResource("/prompt.txt")!!.readText()
    )

    // Perform translation
    translationBookService.translate()

    // Verify translation was cached
    assertEquals(
      "Translated text",
      translationBookService.cache["Original text to translate"]
    )
  }

  @Test
  fun testBatchTranslation() = runTest {
    // Create a test Excel file with multiple entries
    val testFile = tempDir.resolve("batch_test.xlsx")
    testFile.createFile()

    // Create workbook with test data
    val workbook = XSSFWorkbook().apply {
      createSheet("Test").apply {
        // Header row
        createRow(0).apply {
          createCell(0).setCellValue("fvlaenix-magic-words")
          createCell(1).setCellValue("SRPG")
          createCell(2).setCellValue("SRPG")
        }
        // Column names
        createRow(1).apply {
          createCell(0).setCellValue("totranslate")
          createCell(1).setCellValue("translated")
        }
        // Test data
        createRow(2).apply {
          createCell(0).setCellValue("First text")
          createCell(1).setCellValue("")
        }
        createRow(3).apply {
          createCell(0).setCellValue("Second text")
          createCell(1).setCellValue("")
        }
      }
    }

    // Write the workbook to file
    testFile.outputStream().use { workbook.write(it) }

    // Setup test response with system message
    testOpenAIService.setResponse(
      "First text\nSecond text",
      "First translation\nSecond translation",
      textPrompt = TextModelServiceTranslator::class.java.getResource("/prompt.txt")!!.readText()
    )

    // Create service with new file
    translationBookService = TranslationBookService(
      path = tempDir,
      language = "en",
      gameId = "test",
      translator = translator,
      namesService = namesService
    )

    translationBookService.translate()

    assertEquals(
      "First translation",
      translationBookService.cache["First text"]
    )
    assertEquals(
      "Second translation",
      translationBookService.cache["Second text"]
    )
  }

  @Test
  fun testJsonTranslationWithMultipleLines() = runTest {
    // Create a test Excel file with multiple entries
    val testFile = tempDir.resolve("json_test.xlsx")
    testFile.createFile()

    // Create workbook with test data
    val workbook = XSSFWorkbook().apply {
      createSheet("Test").apply {
        // Header row
        createRow(0).apply {
          createCell(0).setCellValue("fvlaenix-magic-words")
          createCell(1).setCellValue("SRPG")
          createCell(2).setCellValue("SRPG")
        }
        // Column names
        createRow(1).apply {
          createCell(0).setCellValue("totranslate")
          createCell(1).setCellValue("translated")
        }
        // Test data with multiple lines
        createRow(2).apply {
          createCell(0).setCellValue("JOHN Line one to translate")
          createCell(1).setCellValue("")
        }
        createRow(3).apply {
          createCell(0).setCellValue("MARY Line two to translate")
          createCell(1).setCellValue("")
        }
        createRow(4).apply {
          createCell(0).setCellValue("PETER Line three to translate")
          createCell(1).setCellValue("")
        }
      }
    }

    // Write the workbook to file
    testFile.outputStream().use { workbook.write(it) }

    // Setup JSON-style response for all requests
    testOpenAIService.setDefaultResponse(
      """[
      {"name": "JOHN", "text": "First line translated"},
      {"name": "MARY", "text": "Second line translated"},
      {"name": "PETER", "text": "Third line translated"}
    ]""".trimIndent()
    )

    // Create service with new file
    translationBookService = TranslationBookService(
      path = tempDir,
      language = "en",
      gameId = "test",
      translator = translator,
      namesService = namesService
    )

    translationBookService.translate()

    // Verify translations
    assertEquals(
      "JOHN\nFirst line translated",
      translationBookService.cache["JOHN Line one to translate"]
    )
    assertEquals(
      "MARY\nSecond line translated",
      translationBookService.cache["MARY Line two to translate"]
    )
    assertEquals(
      "PETER\nThird line translated",
      translationBookService.cache["PETER Line three to translate"]
    )
  }
}
