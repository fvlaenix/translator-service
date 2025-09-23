package com.fvlaenix.translation.table

import com.fvlaenix.text.TextModelService
import com.fvlaenix.translation.NamesService
import com.fvlaenix.translation.TestConstants
import com.fvlaenix.translation.splitter.TextSplitter
import com.fvlaenix.translation.systemdialog.ProvidersCollection
import com.fvlaenix.translation.textmodel.TestTextModelService
import com.fvlaenix.translation.translator.TextModelTranslator
import com.fvlaenix.translation.translator.Translation
import com.fvlaenix.translation.translator.XmlModelTranslator
import kotlinx.coroutines.test.runTest
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.createFile
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream
import kotlin.system.measureTimeMillis

@DisplayName("TranslationBook Large File Processing Tests")
class TranslationBookLargeFileTest {

  @TempDir
  lateinit var tempDir: Path

  private lateinit var testTextModelService: TestTextModelService
  private lateinit var namesService: NamesService

  /**
   * Test implementation that wraps TextSplitter but disables batching.
   * Each translation is processed individually to make tests more predictable.
   */
  private class TestTextSplitter(textModelService: TextModelService) :
    TextSplitter(textModelService, 0.8f) {

    override suspend fun createBatches(
      translations: List<Translation>,
      transformer: (List<Translation>) -> String
    ): List<List<Translation>> {
      return translations.map { listOf(it) }
    }
  }

  @BeforeEach
  fun setUp() {
    testTextModelService = TestTextModelService()
    testTextModelService.reset()

    setupBatchTranslationResponses()

    namesService = NamesService(
      mapOf(
        "Character1" to "Персонаж1",
        "Character2" to "Персонаж2",
        "Character3" to "Персонаж3",
        "Narrator" to "Рассказчик"
      )
    )
  }

  private fun createTranslationService(
    path: Path,
    gameId: String,
    useExtendedFormat: Boolean = false
  ): TranslationBookService {
    val translator = if (useExtendedFormat) {
      XmlModelTranslator(
        textModelService = testTextModelService,
        textSplitter = TestTextSplitter(testTextModelService)
      )
    } else {
      TextModelTranslator(
        textModelService = testTextModelService,
        textSplitter = TestTextSplitter(testTextModelService),
        textPrompt = TestConstants.TEST_PROMPT
      )
    }

    return TranslationBookService(
      path = path,
      language = "RU",
      gameId = gameId,
      namesService = namesService,
      dialogProvider = ProvidersCollection(emptyList()),
      translator = translator
    )
  }

  private fun setupBatchTranslationResponses() {
    testTextModelService.apply {
      (1..100).forEach { batchNumber ->
        val batch = (1..55).map { lineNumber ->
          "Generated text line ${(batchNumber - 1) * 55 + lineNumber}"
        }
        val translation = (1..55).map { lineNumber ->
          "Сгенерированная строка ${(batchNumber - 1) * 55 + lineNumber}"
        }
        setTestBatchResponse(batch, translation)
      }

      (1..10000).forEach { lineNumber ->
        setTestResponse(
          "Generated text line $lineNumber",
          "Сгенерированная строка $lineNumber"
        )
        setTestResponse(
          "Generated dialog line $lineNumber",
          "Сгенерированный диалог $lineNumber"
        )
      }

      (1..55).forEach { batchSize ->
        val lines = (1..batchSize).map { "Generated text line $it" }
        val translations = (1..batchSize).map { "Сгенерированная строка $it" }
        setTestBatchResponse(lines, translations)
      }
    }
  }

  @Test
  @DisplayName("Process 1000 rows - Simple format")
  @Timeout(value = 5, unit = TimeUnit.MINUTES)
  fun testProcess1000RowsSimpleFormat() = runTest {
    val rowCount = 1000

    val largeFile = tempDir.resolve("large_simple_1000.xlsx")
    createLargeSimpleFormatFile(largeFile, rowCount)

    val initialBook = TranslationBook(largeFile.inputStream(), largeFile.fileName)
    assertThat(initialBook.translationBook).hasSize(rowCount)

    val translationService = createTranslationService(tempDir, "large-test")

    val processingTime = measureTimeMillis {
      translationService.translate()
    }

    println("Processing $rowCount rows took: ${processingTime}ms")

    val outputDir = tempDir.resolve("large_output")
    translationService.write(outputDir)

    val outputFile = outputDir.resolve("large_simple_1000.xlsx")
    val resultBook = TranslationBook(outputFile.inputStream(), outputFile.fileName)

    assertThat(resultBook.translationBook).hasSize(rowCount)

    val sampleIndices = listOf(0, 100, 500, 750, 999)
    sampleIndices.forEach { index ->
      val item = resultBook.translationBook[index]
      assertThat(item.toTranslate).isEqualTo("Generated text line ${index + 1}")
      assertThat(item.translate).isNotNull()
      assertThat(item.translate).isNotEmpty()
    }

    assertThat(resultBook.translationBook.size).isEqualTo(rowCount)
  }

  @Test
  @DisplayName("Process 2000 rows - Extended format")
  @Timeout(value = 5, unit = TimeUnit.MINUTES)
  fun testProcess2000RowsExtendedFormat() = runTest {
    val rowCount = 2000

    val largeFile = tempDir.resolve("large_extended_2000.xlsx")
    createLargeExtendedFormatFile(largeFile, rowCount)

    testTextModelService.reset()
    setupExtendedFormatResponses(rowCount)

    val translationService = createTranslationService(tempDir, "large-extended-test", useExtendedFormat = true)

    val processingTime = measureTimeMillis {
      translationService.translate()
    }

    println("Processing $rowCount extended rows took: ${processingTime}ms")

    val outputDir = tempDir.resolve("large_extended_output")
    translationService.write(outputDir)

    val outputFile = outputDir.resolve("large_extended_2000.xlsx")
    val resultBook = TranslationBook(outputFile.inputStream(), outputFile.fileName)

    assertThat(resultBook.translationBook).hasSize(rowCount)

    resultBook.translationBook.forEach { item ->
      assertThat(item).isInstanceOf(TranslationData.TranslationDataWithNameData::class.java)
    }

    val sampleIndices = listOf(0, 500, 1000, 1500, 1999)
    sampleIndices.forEach { index ->
      val item = resultBook.translationBook[index] as TranslationData.TranslationDataWithNameData
      assertThat(item.toTranslate).isEqualTo("Generated dialog line ${index + 1}")
      assertThat(item.name).isEqualTo("Character${((index + 1) % 3) + 1}")
      assertThat(item.translate).isNotNull()
      assertThat(item.translate).isNotEmpty()
    }
  }

  @Test
  @DisplayName("Process 5000 rows - Memory efficiency test")
  @Timeout(value = 10, unit = TimeUnit.MINUTES)
  fun testProcess5000RowsMemoryEfficiency() = runTest {
    val rowCount = 5000

    val veryLargeFile = tempDir.resolve("very_large_5000.xlsx")
    createLargeSimpleFormatFile(veryLargeFile, rowCount)

    val runtime = Runtime.getRuntime()
    val initialMemory = runtime.totalMemory() - runtime.freeMemory()

    val translationService = createTranslationService(tempDir, "memory-test")

    val processingTime = measureTimeMillis {
      translationService.translate()
    }

    val peakMemory = runtime.totalMemory() - runtime.freeMemory()
    val memoryIncrease = peakMemory - initialMemory

    println("Processing $rowCount rows took: ${processingTime}ms")
    println("Memory increase: ${memoryIncrease / 1024 / 1024}MB")

    val outputDir = tempDir.resolve("memory_test_output")
    translationService.write(outputDir)

    val outputFile = outputDir.resolve("very_large_5000.xlsx")
    val resultBook = TranslationBook(outputFile.inputStream(), outputFile.fileName)

    assertThat(resultBook.translationBook).hasSize(rowCount)

    assertThat(resultBook.translationBook[0].translate).isNotNull()
    assertThat(resultBook.translationBook[rowCount - 1].translate).isNotNull()

    assertThat(memoryIncrease).isLessThan(500 * 1024 * 1024)
  }

  @Test
  @DisplayName("Process mixed format file with varied content lengths")
  @Timeout(value = 3, unit = TimeUnit.MINUTES)
  fun testProcessMixedContentLengthsFile() = runTest {
    val rowCount = 1500

    val mixedFile = tempDir.resolve("mixed_content_1500.xlsx")
    createMixedContentLengthFile(mixedFile, rowCount)

    testTextModelService.reset()
    setupVariedContentResponses()

    val translationService = createTranslationService(tempDir, "mixed-content-test")

    val processingTime = measureTimeMillis {
      translationService.translate()
    }

    println("Processing $rowCount mixed content rows took: ${processingTime}ms")

    val outputDir = tempDir.resolve("mixed_content_output")
    translationService.write(outputDir)

    val outputFile = outputDir.resolve("mixed_content_1500.xlsx")
    val resultBook = TranslationBook(outputFile.inputStream(), outputFile.fileName)

    assertThat(resultBook.translationBook).hasSize(rowCount)

    val shortItem = resultBook.translationBook[0]
    assertThat(shortItem.translate).isNotNull()
    assertThat(shortItem.translate).contains("Короткий")

    val mediumItem = resultBook.translationBook[500]
    assertThat(mediumItem.translate).isNotNull()
    assertThat(mediumItem.translate).contains("средний")

    val longItem = resultBook.translationBook[1000]
    assertThat(longItem.translate).isNotNull()
    assertThat(longItem.translate).contains("длинный")
  }

  private fun setupVariedContentResponses() {
    testTextModelService.apply {
      
      (1..500).forEach { i ->
        setTestResponse(
          "Short text $i",
          "Короткий текст $i"
        )
      }

      (501..1000).forEach { i ->
        setTestResponse(
          "This is a medium length text item number $i with some additional content to make it longer than short items.",
          "Это средний по длине текстовый элемент номер $i с дополнительным содержимым, чтобы сделать его длиннее коротких элементов."
        )
      }

      (1001..1500).forEach { i ->
        setTestResponse(
          "This is a significantly longer text item number $i. It contains multiple sentences to simulate complex translation scenarios. The content is designed to test how the system handles larger blocks of text. This should challenge the batching and memory management systems. Additional content is included to ensure we reach meaningful text lengths that would be representative of real-world translation tasks.",
          "Это значительно более длинный текстовый элемент номер $i. Он содержит несколько предложений для моделирования сложных сценариев перевода. Содержимое разработано для проверки того, как система обрабатывает большие блоки текста. Это должно бросить вызов системам пакетной обработки и управления памятью. Дополнительное содержимое включено, чтобы обеспечить достижение значимых длин текста, которые были бы репрезентативными для реальных задач перевода."
        )
      }

      (1..2000).forEach { i ->
        val characterName = "Character${(i % 3) + 1}"
        setXmlTestResponse(
          listOf(characterName to "Generated dialog line $i"),
          listOf("Сгенерированный диалог $i")
        )
      }
    }
  }

  private fun setupExtendedFormatResponses(rowCount: Int) {
    testTextModelService.apply {
      (1..rowCount).forEach { i ->
        val characterName = "Character${(i % 3) + 1}"
        setXmlTestResponse(
          listOf(characterName to "Generated dialog line $i"),
          listOf("Сгенерированный диалог $i")
        )
      }
    }
  }

  private fun createLargeSimpleFormatFile(file: Path, rowCount: Int) {
    file.createFile()
    val workbook = XSSFWorkbook().apply {
      createSheet("Test").apply {
        createRow(0).apply {
          createCell(0).setCellValue("fvlaenix-magic-words")
          createCell(1).setCellValue("SRPG")
          createCell(2).setCellValue("SRPG")
        }
        createRow(1).apply {
          createCell(0).setCellValue("totranslate")
          createCell(1).setCellValue("translated")
        }

        (1..rowCount).forEach { i ->
          createRow(i + 1).apply {
            createCell(0).setCellValue("Generated text line $i")
          }
        }
      }
    }
    file.outputStream().use { workbook.write(it) }
  }

  private fun createLargeExtendedFormatFile(file: Path, rowCount: Int) {
    file.createFile()
    val workbook = XSSFWorkbook().apply {
      createSheet("Test").apply {
        createRow(0).apply {
          createCell(0).setCellValue("fvlaenix-magic-words")
          createCell(1).setCellValue("SRPG")
          createCell(2).setCellValue("SRPG")
        }
        createRow(1).apply {
          createCell(0).setCellValue("totranslate")
          createCell(1).setCellValue("name")
          createCell(2).setCellValue("translated")
        }

        (1..rowCount).forEach { i ->
          createRow(i + 1).apply {
            createCell(0).setCellValue("Generated dialog line $i")
            createCell(1).setCellValue("Character${(i % 3) + 1}")
          }
        }
      }
    }
    file.outputStream().use { workbook.write(it) }
  }

  private fun createMixedContentLengthFile(file: Path, rowCount: Int) {
    file.createFile()
    val workbook = XSSFWorkbook().apply {
      createSheet("Test").apply {
        createRow(0).apply {
          createCell(0).setCellValue("fvlaenix-magic-words")
          createCell(1).setCellValue("SRPG")
          createCell(2).setCellValue("SRPG")
        }
        createRow(1).apply {
          createCell(0).setCellValue("totranslate")
          createCell(1).setCellValue("translated")
        }

        (1..rowCount).forEach { i ->
          createRow(i + 1).apply {
            val content = when {
              i <= 500 -> "Short text $i"
              i <= 1000 -> "This is a medium length text item number $i with some additional content to make it longer than short items."
              else -> "This is a significantly longer text item number $i. It contains multiple sentences to simulate complex translation scenarios. The content is designed to test how the system handles larger blocks of text. This should challenge the batching and memory management systems. Additional content is included to ensure we reach meaningful text lengths that would be representative of real-world translation tasks."
            }
            createCell(0).setCellValue(content)
          }
        }
      }
    }
    file.outputStream().use { workbook.write(it) }
  }
}