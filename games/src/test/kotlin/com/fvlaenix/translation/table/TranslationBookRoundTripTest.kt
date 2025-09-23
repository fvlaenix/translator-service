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
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createFile
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream

@DisplayName("TranslationBook Round-Trip Tests")
class TranslationBookRoundTripTest {

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

    setupRealisticAIResponses()

    namesService = NamesService(
      mapOf(
        "Alice" to "Алиса",
        "Bob" to "Боб",
        "Charlie" to "Чарли",
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

  private fun setupRealisticAIResponses() {
    testTextModelService.apply {
      // Plain text responses for TextModelTranslator
      setTestResponse("Hello world!", "Привет мир!")
      setTestResponse("How are you today?", "Как дела сегодня?")
      setTestResponse("Good morning everyone.", "Доброе утро всем.")
      setTestResponse("The weather is nice.", "Погода хорошая.")
      setTestResponse("I need to go shopping.", "Мне нужно идти в магазин.")
      setTestResponse("Complete item", "Готовый элемент")
      setTestResponse("Incomplete item", "Незавершенный элемент")

      // XML responses for XmlModelTranslator using helper method
      setXmlTestResponse(
        listOf("Alice" to "Hello world!"),
        listOf("Привет мир!")
      )

      setXmlTestResponse(
        listOf("Bob" to "How are you today?"),
        listOf("Как дела сегодня?")
      )

      setXmlTestResponse(
        listOf("Narrator" to "Good morning everyone."),
        listOf("Доброе утро всем.")
      )

      setXmlTestResponse(
        listOf("Charlie" to "The weather is nice."),
        listOf("Погода хорошая.")
      )

      setXmlTestResponse(
        listOf("Alice" to "I need to go shopping."),
        listOf("Мне нужно идти в магазин.")
      )

      setXmlTestResponse(
        listOf("Speaker1" to "Complete item"),
        listOf("Готовый элемент")
      )

      setXmlTestResponse(
        listOf("Speaker2" to "Incomplete item"),
        listOf("Незавершенный элемент")
      )

      // Multi-line batch for plain text
      setTestBatchResponse(
        listOf("Hello world!", "How are you today?", "Good morning everyone."),
        listOf("Привет мир!", "Как дела сегодня?", "Доброе утро всем.")
      )
    }
  }

  @Test
  @DisplayName("Round-trip: Simple format - Read → Add translations → Write → Read")
  fun testSimpleFormatRoundTrip() = runTest {
    val originalFile = tempDir.resolve("original_simple.xlsx")
    createSimpleFormatFile(
      originalFile, listOf(
        "Hello world!" to null,
        "How are you today?" to null,
        "Good morning everyone." to "Доброе утро всем.",
        "The weather is nice." to null,
        "I need to go shopping." to null
      )
    )

    val translationService = createTranslationService(tempDir, "test-game")

    val initialBook = TranslationBook(originalFile.inputStream(), originalFile.fileName)
    assertThat(initialBook.translationBook).hasSize(5)

    assertThat(initialBook.translationBook[2].translate).isEqualTo("Доброе утро всем.")
    assertThat(initialBook.translationBook[0].translate).isNull()
    assertThat(initialBook.translationBook[1].translate).isNull()

    translationService.translate()

    val outputDir = tempDir.resolve("output")
    translationService.write(outputDir)

    val outputFile = outputDir.resolve("original_simple.xlsx")
    val finalBook = TranslationBook(outputFile.inputStream(), outputFile.fileName)

    assertThat(finalBook.translationBook).hasSize(5)

    assertThat(finalBook.translationBook[0].translate).isEqualTo("Привет мир!")
    assertThat(finalBook.translationBook[1].translate).isEqualTo("Как дела сегодня?")
    assertThat(finalBook.translationBook[2].translate).isEqualTo("Доброе утро всем.")
    assertThat(finalBook.translationBook[3].translate).isEqualTo("Погода хорошая.")
    assertThat(finalBook.translationBook[4].translate).isEqualTo("Мне нужно идти в магазин.")

    assertThat(finalBook.translationBook[0].toTranslate).isEqualTo("Hello world!")
    assertThat(finalBook.translationBook[1].toTranslate).isEqualTo("How are you today?")
  }

  @Test
  @DisplayName("Round-trip: Extended format with names - Read → Add translations → Write → Read")
  fun testExtendedFormatRoundTrip() = runTest {
    val originalFile = tempDir.resolve("original_extended.xlsx")
    createExtendedFormatFile(
      originalFile, listOf(
        Triple("Hello world!", "Alice", null),
        Triple("How are you today?", "Bob", null),
        Triple("Good morning everyone.", "Narrator", "Доброе утро всем."),
        Triple("The weather is nice.", "Charlie", null),
        Triple("I need to go shopping.", "Alice", null)
      )
    )

    val translationService = createTranslationService(tempDir, "test-game", useExtendedFormat = true)

    translationService.translate()

    val outputDir = tempDir.resolve("output_extended")
    translationService.write(outputDir)

    val outputFile = outputDir.resolve("original_extended.xlsx")
    val finalBook = TranslationBook(outputFile.inputStream(), outputFile.fileName)

    assertThat(finalBook.translationBook).hasSize(5)

    finalBook.translationBook.forEach { data ->
      assertThat(data).isInstanceOf(TranslationData.TranslationDataWithNameData::class.java)
    }

    val firstItem = finalBook.translationBook[0] as TranslationData.TranslationDataWithNameData
    assertThat(firstItem.toTranslate).isEqualTo("Hello world!")
    assertThat(firstItem.name).isEqualTo("Alice")
    assertThat(firstItem.translate).isEqualTo("Привет мир!")

    val thirdItem = finalBook.translationBook[2] as TranslationData.TranslationDataWithNameData
    assertThat(thirdItem.toTranslate).isEqualTo("Good morning everyone.")
    assertThat(thirdItem.name).isEqualTo("Narrator")
    assertThat(thirdItem.translate).isEqualTo("Доброе утро всем.")
  }

  @Test
  @DisplayName("Round-trip: Incremental translation across multiple sessions")
  fun testIncrementalTranslationRoundTrip() = runTest {
    val workingFile = tempDir.resolve("incremental.xlsx")
    createSimpleFormatFile(
      workingFile, listOf(
        "Hello world!" to null,
        "How are you today?" to null,
        "Good morning everyone." to null,
        "The weather is nice." to null,
        "I need to go shopping." to null
      )
    )

    // PHASE 1: First translation session - translate first 3 items
    testTextModelService.reset()
    testTextModelService.setTestResponse("Hello world!", "Привет мир!")
    testTextModelService.setTestResponse("How are you today?", "Как дела сегодня?")
    testTextModelService.setTestResponse("Good morning everyone.", "Доброе утро всем.")

    var translationService = createTranslationService(tempDir, "test-incremental")

    // PHASE 2: Second translation session - load and complete remaining items
    testTextModelService.reset()
    testTextModelService.setTestResponse("The weather is nice.", "Погода хорошая.")
    testTextModelService.setTestResponse("I need to go shopping.", "Мне нужно идти в магазин.")

    // Update the existing file instead of creating a new one
    val partialFile = tempDir.resolve("incremental.xlsx")
    partialFile.toFile().delete() // Remove the old file first
    createSimpleFormatFile(
      partialFile, listOf(
        "Hello world!" to "Привет мир!",
        "How are you today?" to "Как дела сегодня?",
        "Good morning everyone." to "Доброе утро всем.",
        "The weather is nice." to null,
        "I need to go shopping." to null
      )
    )

    // Create new service instance (simulates restart)
    val secondSessionService = createTranslationService(tempDir, "test-incremental")

    // Verify first 3 items are already translated by reading the file
    val intermediateBook = TranslationBook(partialFile.inputStream(), partialFile.fileName)
    assertThat(intermediateBook.translationBook[0].translate).isEqualTo("Привет мир!")
    assertThat(intermediateBook.translationBook[1].translate).isEqualTo("Как дела сегодня?")
    assertThat(intermediateBook.translationBook[2].translate).isEqualTo("Доброе утро всем.")
    assertThat(intermediateBook.translationBook[3].translate).isNull()
    assertThat(intermediateBook.translationBook[4].translate).isNull()

    // Complete translation
    secondSessionService.translate()

    // PHASE 3: Final verification
    val finalOutputDir = tempDir.resolve("final_output")
    secondSessionService.write(finalOutputDir)

    val finalFile = finalOutputDir.resolve("incremental.xlsx")
    val finalBook = TranslationBook(finalFile.inputStream(), finalFile.fileName)

    assertThat(finalBook.translationBook).hasSize(5)
    finalBook.translationBook.forEach { item ->
      assertThat(item.translate).isNotNull()
      assertThat(item.translate).isNotEmpty()
    }

    assertThat(finalBook.translationBook[3].translate).isEqualTo("Погода хорошая.")
    assertThat(finalBook.translationBook[4].translate).isEqualTo("Мне нужно идти в магазин.")
  }

  @Test
  @DisplayName("Round-trip: Format preservation and integrity")
  fun testFormatPreservationRoundTrip() = runTest {
    val originalFile = tempDir.resolve("format_test.xlsx")
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
        createRow(2).apply {
          createCell(0).setCellValue("Complete item")
          createCell(1).setCellValue("Speaker1")
          createCell(2).setCellValue("Готовый элемент")
        }
        createRow(3).apply {
          createCell(0).setCellValue("Incomplete item")
          createCell(1).setCellValue("Speaker2")
          createCell(2).setCellValue("")
        }
      }
    }
    originalFile.outputStream().use { workbook.write(it) }

    testTextModelService.setResponse("Incomplete item", "Незавершенный элемент")

    val translationService = createTranslationService(tempDir, "format-test", useExtendedFormat = true)

    translationService.translate()

    val outputDir = tempDir.resolve("format_output")
    translationService.write(outputDir)

    val outputFile = outputDir.resolve("format_test.xlsx")
    val resultBook = TranslationBook(outputFile.inputStream(), outputFile.fileName)

    assertThat(resultBook.translationBook).hasSize(2)
    resultBook.translationBook.forEach { item ->
      assertThat(item).isInstanceOf(TranslationData.TranslationDataWithNameData::class.java)
    }

    val firstItem = resultBook.translationBook[0] as TranslationData.TranslationDataWithNameData
    assertThat(firstItem.toTranslate).isEqualTo("Complete item")
    assertThat(firstItem.name).isEqualTo("Speaker1")
    assertThat(firstItem.translate).isEqualTo("Готовый элемент")

    val secondItem = resultBook.translationBook[1] as TranslationData.TranslationDataWithNameData
    assertThat(secondItem.toTranslate).isEqualTo("Incomplete item")
    assertThat(secondItem.name).isEqualTo("Speaker2")
    assertThat(secondItem.translate).isEqualTo("Незавершенный элемент")

    val verificationBook = TranslationBook(outputFile.inputStream(), outputFile.fileName)
    assertThat(verificationBook.translationBook).hasSize(2)

    val verifyFirst = verificationBook.translationBook[0] as TranslationData.TranslationDataWithNameData
    assertThat(verifyFirst.toTranslate).isEqualTo("Complete item")
    assertThat(verifyFirst.name).isEqualTo("Speaker1")
    assertThat(verifyFirst.translate).isEqualTo("Готовый элемент")
  }

  private fun createSimpleFormatFile(file: Path, data: List<Pair<String, String?>>) {
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

        data.forEachIndexed { index, (original, translation) ->
          createRow(index + 2).apply {
            createCell(0).setCellValue(original)
            if (translation != null) {
              createCell(1).setCellValue(translation)
            }
          }
        }
      }
    }
    file.outputStream().use { workbook.write(it) }
  }

  private fun createExtendedFormatFile(file: Path, data: List<Triple<String, String, String?>>) {
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

        data.forEachIndexed { index, (original, name, translation) ->
          createRow(index + 2).apply {
            createCell(0).setCellValue(original)
            createCell(1).setCellValue(name)
            if (translation != null) {
              createCell(2).setCellValue(translation)
            }
          }
        }
      }
    }
    file.outputStream().use { workbook.write(it) }
  }
}