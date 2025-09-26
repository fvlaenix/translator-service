package com.fvlaenix.translation.table

import io.github.evanrupert.excelkt.workbook
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.InputStream
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.name

/**
 * Reads and writes translation books in XLSX format.
 *
 * Uses Apache POI for reading and the excelkt DSL for writing.
 */
class TranslationBookIO {

  /**
   * Reads a translation book from an XLSX input stream.
   *
   * The method attempts to detect an optional header and chooses the appropriate
   * row layout for [TranslationData]. Errors are wrapped into [TranslationBookIOException].
   *
   * @param inputStream Source XLSX stream. The caller must provide a valid workbook.
   * @param path Relative path used to populate [TranslationBook.path] and [TranslationBook.name].
   * @return Parsed [TranslationBook] instance.
   * @throws TranslationBookIOException When the input cannot be parsed as a supported workbook.
   */
  fun read(inputStream: InputStream, path: Path): TranslationBook {
    return try {
      val workbook = XSSFWorkbook(inputStream)
      val localTranslationBook = mutableListOf<TranslationData>()
      val sheet = workbook.getSheetAt(0)
      val prefix = mutableListOf<List<String>>()
      var rowsIt = 0

      fun org.apache.poi.xssf.usermodel.XSSFCell.string(): String {
        return this.stringCellValue ?: "<null>"
      }

      fun org.apache.poi.xssf.usermodel.XSSFRow.string(index: Int): String {
        return this.getCell(index).string()
      }

      val firstRow = sheet.getRow(rowsIt)
      if (firstRow != null) {
        val translatorDataCreator: (MutableList<String>) -> TranslationData =
          if (firstRow.string(0).startsWith("fvlaenix-magic-words")) {
            prefix.add(listOf("fvlaenix-magic-words", "SRPG", "SRPG"))
            rowsIt++
            val secondRow = sheet.getRow(rowsIt)
            rowsIt++
            if (
              secondRow.string(0).equals("totranslate", true) &&
              secondRow.string(1).equals("name", true) &&
              secondRow.string(2).equals("translated", true)
            ) {
              prefix.add(listOf("totranslate", "name", "translated"))
            } else if (
              secondRow.string(0).equals("totranslate", true) &&
              secondRow.string(1).equals("translated", true)
            ) {
              prefix.add(listOf("totranslate", "translated"))
            } else {
              throw IllegalStateException("Can't parse row: $secondRow")
            }

            if (
              secondRow.string(0).equals("totranslate", true) &&
              secondRow.string(1).equals("name", true) &&
              secondRow.string(2).equals("translated", true)
            ) {
              { TranslationData.TranslationDataWithNameData(it, 1, 0, 2) }
            } else {
              { TranslationData.TranslationSimpleData(it, 0, 1) }
            }
          } else {
            { TranslationData.TranslationSimpleData(it, 0, 1) }
          }
        val translations = sheet.toList().drop(rowsIt)
          .mapNotNull { row ->
            val cells = row.toList().map { it.stringCellValue }
            when (cells.size) {
              0 -> null
              else -> translatorDataCreator(cells.toMutableList())
            }
          }
        localTranslationBook.addAll(translations)
      }

      TranslationBook(
        name = path.name,
        path = path,
        translationBook = localTranslationBook
      )
    } catch (e: Exception) {
      throw TranslationBookIOException("Failed to read translation book from $path", e)
    }
  }

  /**
   * Writes a translation book to an XLSX file under the given directory.
   *
   * The directory structure is created as needed. Errors are wrapped into [TranslationBookIOException].
   *
   * @param book Book to serialise.
   * @param parentDirectory Root directory where the book path will be created.
   * @throws TranslationBookIOException When writing fails.
   */
  fun write(book: TranslationBook, parentDirectory: Path) {
    try {
      val writePath = parentDirectory.resolve(book.path)
      writePath.parent.createDirectories()

      val prefix = determinePrefix(book.translationBook)

      workbook {
        sheet {
          prefix.forEach { prefixRow ->
            row {
              prefixRow.forEach { cellValue ->
                cell(cellValue)
              }
            }
          }
          book.translationBook.forEach { data ->
            row {
              for (cellData in data.data) {
                cell(cellData)
              }
            }
          }
        }
      }.write(writePath.toString())
    } catch (e: Exception) {
      throw TranslationBookIOException("Failed to write translation book to $parentDirectory/${book.path}", e)
    }
  }

  private fun determinePrefix(translations: List<TranslationData>): List<List<String>> {
    if (translations.isEmpty()) return listOf(listOf("totranslate", "translated"))

    return when (translations.first()) {
      is TranslationData.TranslationDataWithNameData -> listOf(
        listOf("fvlaenix-magic-words", "SRPG", "SRPG"),
        listOf("totranslate", "name", "translated")
      )

      is TranslationData.TranslationSimpleData -> listOf(
        listOf("fvlaenix-magic-words", "SRPG", "SRPG"),
        listOf("totranslate", "translated")
      )
    }
  }
}

/**
 * Signals failures during reading or writing of translation books.
 *
 * Wraps underlying IO and parsing exceptions with a descriptive message.
 */
class TranslationBookIOException(message: String, cause: Throwable? = null) : Exception(message, cause)