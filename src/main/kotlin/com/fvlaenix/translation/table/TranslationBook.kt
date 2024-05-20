package com.fvlaenix.translation.table

import com.fvlaenix.translation.systemdialog.ProvidersCollection
import com.fvlaenix.translation.table.TranslationBookService.KeyNotFoundException
import io.github.evanrupert.excelkt.workbook
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.InputStream
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.name

const val MAX_COUNT_LINES = 50

class TranslationBook(inputStream: InputStream, val path: Path) {

  val translationBook: List<TranslationData>
  val name: String = path.name
  private val prefix: MutableList<List<String>> = mutableListOf()

  init {
    val workbook = XSSFWorkbook(inputStream)
    val localTranslationBook = mutableListOf<TranslationData>()
    val sheet = workbook.getSheetAt(0)
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
            prefix.add(listOf("totranslate", "name", "translated"));
            { TranslationData.TranslationDataWithNameData(it, 1, 0, 2) }
          } else {
            if (
              secondRow.string(0).equals("totranslate", true) &&
              secondRow.string(1).equals("translated", true)
            ) {
              prefix.add(listOf("totranslate", "translated"));
              { TranslationData.TranslationSimpleData(it, 0, 1) }
            } else {
              throw IllegalStateException("Can't parse row: $secondRow")
            }
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
    translationBook = localTranslationBook
  }

  fun write(parentDirectory: Path) {
    val writePath = parentDirectory.resolve(this.path)
    writePath.parent.createDirectories()
    workbook {
      sheet {
        prefix.forEach { 
          row {
            it.forEach { 
              cell(it)
            }
          }
        }
        translationBook.forEach { data ->
          row {
            for (cellData in data.data) {
              cell(cellData)
            }
          }
        }
      }
    }.write(writePath.toString())
  }
  
  fun checkNames(dialogProvider: ProvidersCollection): MutableList<String> {
    val notFoundKeys = mutableListOf<String>()
    translationBook.forEachIndexed data@{ dataIndex, data ->
      if (data.translate != null) return@data
      try {
        dialogProvider.get(data.toTranslate)
      } catch (e: KeyNotFoundException) {
        notFoundKeys.add("${name}|${dataIndex + 1}")
        notFoundKeys.add(e.notFoundKey)
      }
    }
    return notFoundKeys
  }
}