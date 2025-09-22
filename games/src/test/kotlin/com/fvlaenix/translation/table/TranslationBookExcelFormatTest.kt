package com.fvlaenix.translation.table

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

@DisplayName("TranslationBook Excel Format Tests")
class TranslationBookExcelFormatTest {

  @TempDir
  lateinit var tempDir: Path

  @BeforeEach
  fun setUp() {
  }

  @Test
  @DisplayName("Read simple format without magic words")
  fun testReadSimpleFormatWithoutMagicWords() {
    val testFile = tempDir.resolve("simple_no_magic.xlsx")
    testFile.createFile()

    val workbook = XSSFWorkbook().apply {
      createSheet("Test").apply {
        createRow(0).apply {
          createCell(0).setCellValue("Hello world")
          createCell(1).setCellValue("Привет мир")
        }
        createRow(1).apply {
          createCell(0).setCellValue("How are you?")
          createCell(1).setCellValue("")
        }
      }
    }
    testFile.outputStream().use { workbook.write(it) }

    val translationBook = TranslationBook(testFile.inputStream(), testFile.fileName)

    assertThat(translationBook.translationBook).hasSize(2)

    val firstData = translationBook.translationBook[0]
    assertThat(firstData).isInstanceOf(TranslationData.TranslationSimpleData::class.java)
    assertThat(firstData.toTranslate).isEqualTo("Hello world")
    assertThat(firstData.translate).isEqualTo("Привет мир")

    val secondData = translationBook.translationBook[1]
    assertThat(secondData.toTranslate).isEqualTo("How are you?")
    assertThat(secondData.translate).isNull()
  }

  @Test
  @DisplayName("Read simple format with magic words")
  fun testReadSimpleFormatWithMagicWords() {
    val testFile = tempDir.resolve("simple_with_magic.xlsx")
    testFile.createFile()

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
          createCell(0).setCellValue("Good morning")
          createCell(1).setCellValue("Доброе утро")
        }
        createRow(3).apply {
          createCell(0).setCellValue("Good evening")
          createCell(1).setCellValue("")
        }
      }
    }
    testFile.outputStream().use { workbook.write(it) }

    val translationBook = TranslationBook(testFile.inputStream(), testFile.fileName)

    assertThat(translationBook.translationBook).hasSize(2)

    val firstData = translationBook.translationBook[0]
    assertThat(firstData).isInstanceOf(TranslationData.TranslationSimpleData::class.java)
    assertThat(firstData.toTranslate).isEqualTo("Good morning")
    assertThat(firstData.translate).isEqualTo("Доброе утро")

    val secondData = translationBook.translationBook[1]
    assertThat(secondData.toTranslate).isEqualTo("Good evening")
    assertThat(secondData.translate).isNull()
  }

  @Test
  @DisplayName("Read extended format without magic words")
  fun testReadExtendedFormatWithoutMagicWords() {
    val testFile = tempDir.resolve("extended_no_magic.xlsx")
    testFile.createFile()

    val workbook = XSSFWorkbook().apply {
      createSheet("Test").apply {
        createRow(0).apply {
          createCell(0).setCellValue("Hello there!")
          createCell(1).setCellValue("Alice")
          createCell(2).setCellValue("Привет!")
        }
        createRow(1).apply {
          createCell(0).setCellValue("How are you today?")
          createCell(1).setCellValue("Bob")
          createCell(2).setCellValue("")
        }
      }
    }
    testFile.outputStream().use { workbook.write(it) }

    val translationBook = TranslationBook(testFile.inputStream(), testFile.fileName)

    assertThat(translationBook.translationBook).hasSize(2)

    val firstData = translationBook.translationBook[0]
    assertThat(firstData).isInstanceOf(TranslationData.TranslationSimpleData::class.java)
    assertThat(firstData.toTranslate).isEqualTo("Hello there!")
    assertThat(firstData.translate).isEqualTo("Alice")
  }

  @Test
  @DisplayName("Read extended format with magic words")
  fun testReadExtendedFormatWithMagicWords() {
    val testFile = tempDir.resolve("extended_with_magic.xlsx")
    testFile.createFile()

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
          createCell(0).setCellValue("Hello there!")
          createCell(1).setCellValue("Alice")
          createCell(2).setCellValue("Привет!")
        }
        createRow(3).apply {
          createCell(0).setCellValue("How are you today?")
          createCell(1).setCellValue("Bob")
          createCell(2).setCellValue("")
        }
      }
    }
    testFile.outputStream().use { workbook.write(it) }

    val translationBook = TranslationBook(testFile.inputStream(), testFile.fileName)

    assertThat(translationBook.translationBook).hasSize(2)

    val firstData = translationBook.translationBook[0] as TranslationData.TranslationDataWithNameData
    assertThat(firstData).isInstanceOf(TranslationData.TranslationDataWithNameData::class.java)
    assertThat(firstData.toTranslate).isEqualTo("Hello there!")
    assertThat(firstData.name).isEqualTo("Alice")
    assertThat(firstData.translate).isEqualTo("Привет!")

    val secondData = translationBook.translationBook[1] as TranslationData.TranslationDataWithNameData
    assertThat(secondData.toTranslate).isEqualTo("How are you today?")
    assertThat(secondData.name).isEqualTo("Bob")
    assertThat(secondData.translate).isNull()
  }

  @Test
  @DisplayName("Write and verify simple format with magic words")
  fun testWriteSimpleFormatWithMagicWords() {
    val testFile = tempDir.resolve("write_simple_magic.xlsx")
    testFile.createFile()

    val originalWorkbook = XSSFWorkbook().apply {
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
          createCell(0).setCellValue("Test text")
          createCell(1).setCellValue("Тестовый текст")
        }
      }
    }
    testFile.outputStream().use { originalWorkbook.write(it) }

    val translationBook = TranslationBook(testFile.inputStream(), testFile.fileName)

    translationBook.translationBook[0].translate = "Измененный текст"

    val outputFile = tempDir.resolve("output_simple_magic.xlsx")
    translationBook.write(tempDir)

    val rereadBook = TranslationBook(outputFile.inputStream(), outputFile.fileName)

    assertThat(rereadBook.translationBook).hasSize(1)
    assertThat(rereadBook.translationBook[0].toTranslate).isEqualTo("Test text")
    assertThat(rereadBook.translationBook[0].translate).isEqualTo("Измененный текст")
  }

  @Test
  @DisplayName("Write and verify extended format with magic words")
  fun testWriteExtendedFormatWithMagicWords() {
    val testFile = tempDir.resolve("write_extended_magic.xlsx")
    testFile.createFile()

    val originalWorkbook = XSSFWorkbook().apply {
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
          createCell(0).setCellValue("Hello world")
          createCell(1).setCellValue("Character1")
          createCell(2).setCellValue("Привет мир")
        }
        createRow(3).apply {
          createCell(0).setCellValue("Goodbye")
          createCell(1).setCellValue("Character2")
          createCell(2).setCellValue("")
        }
      }
    }
    testFile.outputStream().use { originalWorkbook.write(it) }

    val translationBook = TranslationBook(testFile.inputStream(), testFile.fileName)

    (translationBook.translationBook[0] as TranslationData.TranslationDataWithNameData).translate = "Привет вселенная"
    (translationBook.translationBook[1] as TranslationData.TranslationDataWithNameData).translate = "До свидания"

    val outputFile = tempDir.resolve("output_extended_magic.xlsx")
    translationBook.write(tempDir)

    val rereadBook = TranslationBook(outputFile.inputStream(), outputFile.fileName)

    assertThat(rereadBook.translationBook).hasSize(2)

    val firstData = rereadBook.translationBook[0] as TranslationData.TranslationDataWithNameData
    assertThat(firstData.toTranslate).isEqualTo("Hello world")
    assertThat(firstData.name).isEqualTo("Character1")
    assertThat(firstData.translate).isEqualTo("Привет вселенная")

    val secondData = rereadBook.translationBook[1] as TranslationData.TranslationDataWithNameData
    assertThat(secondData.toTranslate).isEqualTo("Goodbye")
    assertThat(secondData.name).isEqualTo("Character2")
    assertThat(secondData.translate).isEqualTo("До свидания")
  }

  @Test
  @DisplayName("Handle mixed empty and non-empty cells")
  fun testMixedEmptyAndNonEmptyCells() {
    val testFile = tempDir.resolve("mixed_cells.xlsx")
    testFile.createFile()

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
          createCell(0).setCellValue("Text with translation")
          createCell(1).setCellValue("Текст с переводом")
        }
        createRow(3).apply {
          createCell(0).setCellValue("Text without translation")
        }
        createRow(4).apply {
          createCell(0).setCellValue("Text with empty string")
          createCell(1).setCellValue("")
        }
      }
    }
    testFile.outputStream().use { workbook.write(it) }

    val translationBook = TranslationBook(testFile.inputStream(), testFile.fileName)

    assertThat(translationBook.translationBook).hasSize(3)

    assertThat(translationBook.translationBook[0].toTranslate).isEqualTo("Text with translation")
    assertThat(translationBook.translationBook[0].translate).isEqualTo("Текст с переводом")

    assertThat(translationBook.translationBook[1].toTranslate).isEqualTo("Text without translation")
    assertThat(translationBook.translationBook[1].translate).isNull()

    assertThat(translationBook.translationBook[2].toTranslate).isEqualTo("Text with empty string")
    assertThat(translationBook.translationBook[2].translate).isNull()
  }
}