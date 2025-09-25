package com.fvlaenix.translation.table

import com.fvlaenix.translation.NamesService
import com.fvlaenix.translation.systemdialog.ProvidersCollection
import com.fvlaenix.translation.table.TranslationBookRoundTripTest.TestTextSplitter
import com.fvlaenix.translation.textmodel.TestTextModelService
import com.fvlaenix.translation.translator.XmlModelTranslator
import kotlinx.coroutines.test.runTest
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream

@DisplayName("TranslationBookService Dialog Duplication Bug Tests")
class TranslationBookServiceDialogDuplicationTest {

  @TempDir
  lateinit var tempDir: Path

  private lateinit var testTextModelService: TestTextModelService
  private lateinit var namesService: NamesService

  @BeforeEach
  fun setUp() {
    testTextModelService = TestTextModelService()
    testTextModelService.reset()

    // Set up name service with test character
    namesService = NamesService(
      mapOf(
        "Alama" to "Alama"
      )
    )

    // Set up XML test response for dialog translation
    testTextModelService.setXmlTestResponse(
      listOf("Alama" to "Привет!"),
      listOf("Hello!")
    )
  }

  private fun createTranslationService(path: Path): TranslationBookService {
    return TranslationBookService(
      TranslationConfig(
        path = path,
        namesService = namesService,
        dialogProvider = ProvidersCollection.defaultProvidersCollection(namesService),
        translator = XmlModelTranslator(
          textModelService = testTextModelService,
          textSplitter = TestTextSplitter(testTextModelService)
        )
      )
    )
  }

  private fun createTestFile(file: Path, originalText: String, existingTranslation: String? = null) {
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
        createRow(2).apply {
          createCell(0).setCellValue(originalText)
          if (existingTranslation != null) {
            createCell(1).setCellValue(existingTranslation)
          }
        }
      }
    }
    file.outputStream().use { workbook.write(it) }
  }

  @Test
  @DisplayName("Dialog markers should not be duplicated when using cache")
  fun testDialogMarkersNotDuplicatedWithCache() = runTest {
    // PHASE 1: Create initial file and translate it
    val initialDir = tempDir.resolve("initial")
    initialDir.createDirectories()

    val initialFile = initialDir.resolve("test.xlsx")
    createTestFile(initialFile, "\\n<Alama>Привет!")

    val initialService = createTranslationService(initialDir)
    initialService.translate()

    val initialOutputDir = tempDir.resolve("initial_output")
    initialService.write(initialOutputDir)

    // Verify initial translation is correct
    val initialResultFile = initialOutputDir.resolve("test.xlsx")
    val initialBook = TranslationBookIO().read(initialResultFile.inputStream(), initialResultFile.fileName)
    val initialTranslation = initialBook.translationBook[0].translate!!

    // Should have exactly one dialog marker
    assertThat(initialTranslation).startsWith("\\n<Alama>")
    assertThat(initialTranslation).doesNotContain("\\n<Alama>\\n<Alama>")
    println("Initial translation: $initialTranslation")

    // PHASE 2: Create second file with same content and use first as cache
    val secondDir = tempDir.resolve("second")
    secondDir.createDirectories()

    val secondFile = secondDir.resolve("test.xlsx")
    createTestFile(secondFile, "\\n<Alama>Привет!")

    val secondService = createTranslationService(secondDir)

    // Use initial service as cache
    secondService.addToCache(initialService)
    secondService.translate()

    val secondOutputDir = tempDir.resolve("second_output")
    secondService.write(secondOutputDir)

    // Verify second translation doesn't have duplicated dialog markers
    val secondResultFile = secondOutputDir.resolve("test.xlsx")
    val secondBook = TranslationBookIO().read(secondResultFile.inputStream(), secondResultFile.fileName)
    val secondTranslation = secondBook.translationBook[0].translate!!

    println("Second translation: $secondTranslation")

    // Should still have exactly one dialog marker, not duplicated
    assertThat(secondTranslation).startsWith("\\n<Alama>")
    assertThat(secondTranslation).doesNotContain("\\n<Alama>\\n<Alama>")
    assertThat(secondTranslation).isEqualTo(initialTranslation)

    // PHASE 3: Use second service as cache for third service (multiple cache cycles)
    val thirdDir = tempDir.resolve("third")
    thirdDir.createDirectories()

    val thirdFile = thirdDir.resolve("test.xlsx")
    createTestFile(thirdFile, "\\n<Alama>Привет!")

    val thirdService = createTranslationService(thirdDir)

    // Use second service as cache (which was already cached from first)
    thirdService.addToCache(secondService)
    thirdService.translate()

    val thirdOutputDir = tempDir.resolve("third_output")
    thirdService.write(thirdOutputDir)

    // Verify third translation still doesn't have duplicated dialog markers
    val thirdResultFile = thirdOutputDir.resolve("test.xlsx")
    val thirdBook = TranslationBookIO().read(thirdResultFile.inputStream(), thirdResultFile.fileName)
    val thirdTranslation = thirdBook.translationBook[0].translate!!

    println("Third translation: $thirdTranslation")

    // Should STILL have exactly one dialog marker, even after multiple cache cycles
    assertThat(thirdTranslation).startsWith("\\n<Alama>")
    assertThat(thirdTranslation).doesNotContain("\\n<Alama>\\n<Alama>")
    assertThat(thirdTranslation).isEqualTo(initialTranslation)
  }

  @Test
  @DisplayName("Dialog markers should not be duplicated with multiple different dialogs in cache")
  fun testMultipleDialogsNotDuplicatedWithCache() = runTest {
    // Set up multiple dialog responses
    testTextModelService.apply {
      setXmlTestResponse(listOf("Alama" to "Привет!"), listOf("Hello!"))
      setXmlTestResponse(listOf("Alama" to "Как дела?"), listOf("How are you?"))
      setTestBatchResponse(
        listOf("\\n<Alama>Привет!", "\\n<Alama>Как дела?"),
        listOf("\\n<Alama>Hello!", "\\n<Alama>How are you?")
      )
    }

    // PHASE 1: Create initial file with multiple dialogs
    val initialDir = tempDir.resolve("multi_initial")
    initialDir.createDirectories()

    val initialFile = initialDir.resolve("multi_test.xlsx")
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
        createRow(2).apply {
          createCell(0).setCellValue("\\n<Alama>Привет!")
        }
        createRow(3).apply {
          createCell(0).setCellValue("\\n<Alama>Как дела?")
        }
      }
    }
    initialFile.outputStream().use { workbook.write(it) }

    val initialService = createTranslationService(initialDir)
    initialService.translate()

    // PHASE 2: Use as cache for second service
    val secondDir = tempDir.resolve("multi_second")
    secondDir.createDirectories()

    val secondFile = secondDir.resolve("multi_test.xlsx")
    val secondWorkbook = XSSFWorkbook().apply {
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
        createRow(2).apply {
          createCell(0).setCellValue("\\n<Alama>Привет!")
        }
        createRow(3).apply {
          createCell(0).setCellValue("\\n<Alama>Как дела?")
        }
      }
    }
    secondFile.outputStream().use { secondWorkbook.write(it) }

    val secondService = createTranslationService(secondDir)
    secondService.addToCache(initialService)
    secondService.translate()

    val outputDir = tempDir.resolve("multi_output")
    secondService.write(outputDir)

    // Verify both dialogs don't have duplicated markers
    val resultFile = outputDir.resolve("multi_test.xlsx")
    val resultBook = TranslationBookIO().read(resultFile.inputStream(), resultFile.fileName)

    val firstDialog = resultBook.translationBook[0].translate!!
    val secondDialog = resultBook.translationBook[1].translate!!

    println("First dialog: $firstDialog")
    println("Second dialog: $secondDialog")

    // Both should have exactly one dialog marker each
    assertThat(firstDialog).startsWith("\\n<Alama>")
    assertThat(firstDialog).doesNotContain("\\n<Alama>\\n<Alama>")

    assertThat(secondDialog).startsWith("\\n<Alama>")
    assertThat(secondDialog).doesNotContain("\\n<Alama>\\n<Alama>")
  }
}
