package com.fvlaenix.translation.table

import com.fvlaenix.translation.NamesService
import com.fvlaenix.translation.systemdialog.MinimalDialogProvider
import com.fvlaenix.translation.systemdialog.ProvidersCollection
import com.fvlaenix.translation.translator.FakeTranslator
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.*
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.inputStream
import kotlin.io.path.moveTo
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class TranslationBookServiceIntegrationTest {
  @TempDir
  lateinit var tempDir: Path

  private val fakeTranslator = FakeTranslator()
  private val namesService = NamesService(
    mapOf(
      "Alice" to "Алиса",
      "Bob" to "Боб",
      "Hello" to "Привет"
    )
  )

  @AfterEach
  fun cleanup() {
    tempDir.toFile().deleteRecursively()
    fakeTranslator.clear()
  }

  @Test
  @DisplayName("Test simple XLSX translation")
  @Order(1)
  fun testSimpleXlsxTranslation() = runBlocking {
    fakeTranslator.clear()

    // Prepare test data - MinimalDialogProvider will add quotes, so we need to register translations with quotes
    fakeTranslator.addTextTranslation("\"Hello world!\"", "\"Привет мир!\"")
    fakeTranslator.addTextTranslation("\"How are you?\"", "\"Как дела?\"")

    val content = listOf(
      "Hello world!" to null,
      "How are you?" to null
    )
    val xlsxFile = TestXlsxGenerator.createSimpleXlsx(content)

    // Ensure temp directory exists
    tempDir.toFile().mkdirs()

    val testFile = tempDir.resolve("test.xlsx")
    xlsxFile.moveTo(testFile)

    // Create service with minimal dialog provider to handle quotes
    val service = TranslationBookService(
      tempDir,
      "EN",
      "test-game",
      namesService = NamesService(emptyMap()), // Empty names service to avoid name processing
      dialogProvider = ProvidersCollection(listOf(MinimalDialogProvider())), // Use minimal provider to handle quotes
      translator = fakeTranslator
    )

    service.translate()

    // Verify results
    val outputDir = createTempDirectory()

    val outputParent = outputDir.resolve("test.xlsx").parent
    outputParent?.createDirectories()

    service.write(outputDir)

    val outputFile = outputDir.resolve("test.xlsx")
    val translatedBook = TranslationBook(outputFile.inputStream(), Path.of("test.xlsx"))

    // Skip header row (index 0) and check actual translations
    // MinimalDialogProvider.returnTransform removes quotes from the final result
    val expected1 = "Привет мир!"  // No quotes in expected result
    assertEquals(expected1, translatedBook.translationBook[1].translate)

    val expected2 = "Как дела?"  // No quotes in expected result
    assertEquals(expected2, translatedBook.translationBook[2].translate)
  }

  @Test
  @Order(2)
  @DisplayName("Test extended XLSX translation with names")
  fun `test extended XLSX translation with names`() = runBlocking {
    // Prepare test data
    fakeTranslator.addDialogTranslation("Hello!", "Привет!")
    fakeTranslator.addDialogTranslation("How are you today?", "Как ты сегодня?")

    val content = listOf(
      Triple("Hello!", "Alice", null),
      Triple("How are you today?", "Bob", null)
    )
    val xlsxFile = TestXlsxGenerator.createExtendedXlsx(content)
    xlsxFile.moveTo(tempDir.resolve("test_with_names.xlsx"))

    // Create service and translate
    val service = TranslationBookService(
      tempDir,
      "EN",
      "test-game",
      namesService,
      translator = fakeTranslator
    )
    service.translate()

    // Verify results
    val outputDir = createTempDirectory()
    service.write(outputDir)

    val translatedBook =
      TranslationBook(outputDir.resolve("test_with_names.xlsx").inputStream(), Path.of("test_with_names.xlsx"))
    assertEquals(2, translatedBook.translationBook.size)
    assertTrue(translatedBook.translationBook[0] is TranslationData.TranslationDataWithNameData)
    assertEquals("Привет!", translatedBook.translationBook[0].translate)
    assertEquals("Как ты сегодня?", translatedBook.translationBook[1].translate)
  }

  @Test
  @Order(3)
  @DisplayName("Test cache functionality")
  fun `test cache functionality`() = runBlocking {
    // First create and translate a file to populate the cache
    val content = listOf("Hello world!" to null)

    // Create and translate first file
    val xlsxFile1 = TestXlsxGenerator.createSimpleXlsx(content)
    val firstFile = tempDir.resolve("test.xlsx")
    xlsxFile1.moveTo(firstFile)

    // Add translation to fake translator with quotes as dialog provider adds them
    fakeTranslator.addTextTranslation("\"Hello world!\"", "\"Привет мир!\"")

    // Create service with minimal dialog provider to handle quotes
    val service = TranslationBookService(
      tempDir,
      "EN",
      "test-game",
      namesService = NamesService(emptyMap()),
      dialogProvider = ProvidersCollection(listOf(MinimalDialogProvider())),
      translator = fakeTranslator
    )
    service.translate()

    // Create second file with same content
    val xlsxFile2 = TestXlsxGenerator.createSimpleXlsx(content)

    // Clear first file and move second file to its place
    firstFile.toFile().delete()
    xlsxFile2.moveTo(firstFile)

    // Clear translator to ensure cache is used
    fakeTranslator.clear()

    // Translate second file
    service.translate()

    // Verify translation was taken from cache
    val outputDir = createTempDirectory()

    // Ensure output directory exists
    outputDir.toFile().mkdirs()

    service.write(outputDir)

    val outputFile = outputDir.resolve("test.xlsx")  // Use same filename as input

    // Wait a bit to ensure file is written
    Thread.sleep(1000)

    val translatedBook = TranslationBook(outputFile.inputStream(), Path.of("test.xlsx"))

    // Skip header row (index 0) and check actual translation
    // MinimalDialogProvider.returnTransform removes quotes from the final result
    val actual = translatedBook.translationBook[1].translate
    val expected = "Привет мир!"  // No quotes in expected result
    assertEquals(expected, actual)
  }

  @Test
  @Order(4)
  @DisplayName("Test name validation")
  fun `test name validation`() {
    // Prepare test data with dialog containing unknown name
    val content = listOf(
      Triple("\\n<UnknownPerson>Hello!", "Alice", null)
    )
    val xlsxFile = TestXlsxGenerator.createExtendedXlsx(content)
    xlsxFile.moveTo(tempDir.resolve("test_invalid.xlsx"))

    // Create service and try to translate
    val service = TranslationBookService(
      tempDir,
      "EN",
      "test-game",
      namesService,
      translator = fakeTranslator
    )

    // Should throw exception due to unknown name in dialog
    assertThrows<IllegalStateException> {
      runBlocking {
        service.translate()
      }
    }
  }
}
