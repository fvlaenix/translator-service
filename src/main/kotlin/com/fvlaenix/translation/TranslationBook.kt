import io.github.evanrupert.excelkt.workbook
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.nio.file.Path
import kotlin.io.path.name

class TranslationBook(path: Path, private val sourceColumn: Int, private val targetColumn: Int) {

  val translationBook: MutableList<TranslationData>
  val name: String = path.name

  init {
    val workbook = XSSFWorkbook(path.toFile().inputStream())
    translationBook = mutableListOf()
    val sheet = workbook.getSheetAt(0)
    val translations = sheet.toList()
      .mapIndexedNotNull { index, row ->
        val cells = row.toList().map { it.stringCellValue }
        when (cells.size) {
          0 -> null
          else -> TranslationData(cells.toMutableList(), sourceColumn, targetColumn)
        }
      }
    translationBook.addAll(translations)
  }

  fun write(parentDirectory: Path) {
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
    }.write(parentDirectory.resolve(name).toString())
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