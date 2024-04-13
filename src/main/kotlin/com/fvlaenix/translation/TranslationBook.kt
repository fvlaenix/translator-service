package com.fvlaenix.translation

import io.github.evanrupert.excelkt.workbook
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.InputStream
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.name

class TranslationBook(inputStream: InputStream, val path: Path, private val sourceColumn: Int, private val targetColumn: Int) {

  val translationBook: MutableList<TranslationData>
  val name: String = path.name

  init {
    val workbook = XSSFWorkbook(inputStream)
    translationBook = mutableListOf()
    val sheet = workbook.getSheetAt(0)
    val translations = sheet.toList()
      .mapNotNull { row ->
        val cells = row.toList().map { it.stringCellValue }
        when (cells.size) {
          0 -> null
          else -> TranslationData(cells.toMutableList(), sourceColumn, targetColumn)
        }
      }
    translationBook.addAll(translations)
  }

  fun write(parentDirectory: Path) {
    val writePath = parentDirectory.resolve(this.path)
    writePath.parent.createDirectories()
    workbook {
      sheet {
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

  class TranslationData(val data: MutableList<String>, private val sourceColumn: Int, private val targetColumn: Int) {
    val toTranslate: String
      get() = data[sourceColumn]
    var translate: String?
      get() = data.getOrNull(targetColumn)
      set(value) {
        while (data.size <= targetColumn) data.add("")
        data[targetColumn] = value ?: ""
      }
  }
}