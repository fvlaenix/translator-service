package com.fvlaenix.translation.table

import io.github.evanrupert.excelkt.workbook
import java.nio.file.Path
import kotlin.io.path.createTempFile

class TestXlsxGenerator {
  companion object {
    fun createSimpleXlsx(content: List<Pair<String, String?>>): Path {
      val tempFile = createTempFile(suffix = ".xlsx")
      workbook {
        sheet {
          row {
            cell("totranslate")
            cell("translated")
          }
          content.forEach { (toTranslate, translated) ->
            row {
              cell(toTranslate)
              if (translated != null) {
                cell(translated)
              }
            }
          }
        }
      }.write(tempFile.toString())
      return tempFile
    }

    fun createExtendedXlsx(content: List<Triple<String, String, String?>>): Path {
      val tempFile = createTempFile(suffix = ".xlsx")
      workbook {
        sheet {
          row {
            cell("fvlaenix-magic-words")
            cell("SRPG")
            cell("SRPG")
          }
          row {
            cell("totranslate")
            cell("name")
            cell("translated")
          }
          content.forEach { (toTranslate, name, translated) ->
            row {
              cell(toTranslate)
              cell(name)
              if (translated != null) {
                cell(translated)
              }
            }
          }
        }
      }.write(tempFile.toString())
      return tempFile
    }
  }
}