package com.fvlaenix.translation.table

import com.fvlaenix.translation.NamesService
import com.fvlaenix.translation.textmodel.TestTextModelService
import com.fvlaenix.translation.translator.Translator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createFile
import kotlin.io.path.outputStream

@OptIn(ExperimentalCoroutinesApi::class)
abstract class AbstractTranslationBookServiceTest {
  @TempDir
  lateinit var tempDir: Path

  protected lateinit var testOpenAIService: TestTextModelService
  protected lateinit var translator: Translator
  protected lateinit var namesService: NamesService
  protected lateinit var translationBookService: TranslationBookService

  @BeforeEach
  fun baseSetup() {
    testOpenAIService = TestTextModelService()
    testOpenAIService.reset() // Ensure clean state

    // Initialize NamesService with test data
    namesService = NamesService(mapOf())
  }

  protected fun createSimpleExcelFile(filename: String, textToTranslate: String): Path {
    val testFile = tempDir.resolve(filename)
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
          createCell(0).setCellValue(textToTranslate)
          createCell(1).setCellValue("")  // Empty string for untranslated text
        }
      }
    }

    // Write the workbook to file
    testFile.outputStream().use { workbook.write(it) }
    return testFile
  }

  protected fun createBatchExcelFile(filename: String, textsToTranslate: List<String>): Path {
    val testFile = tempDir.resolve(filename)
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
        textsToTranslate.forEachIndexed { index, text ->
          createRow(index + 2).apply {
            createCell(0).setCellValue(text)
            createCell(1).setCellValue("")
          }
        }
      }
    }

    // Write the workbook to file
    testFile.outputStream().use { workbook.write(it) }
    return testFile
  }

  protected fun createTranslationBookService(): TranslationBookService {
    return TranslationBookService(
      path = tempDir,
      language = "en",
      gameId = "test",
      translator = translator,
      namesService = namesService
    )
  }
}