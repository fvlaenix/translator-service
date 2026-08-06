package com.fvlaenix.translation.po

import com.fvlaenix.translation.table.TranslationBookIO
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream
import kotlin.io.path.readText
import kotlin.io.path.writeText

class POTest {
  @TempDir
  lateinit var temp: Path

  @Test
  fun `export recursively writes bare two-column workbooks understood by generic reader`() {
    val source = temp.resolve("source")
    writePo(source.resolve("a/a.po"), simplePo("One", "Un"))
    writePo(source.resolve("deep/b/b.po"), simplePo("Two", "Two"))
    val tables = temp.resolve("tables")

    PO(source).createTranslationTables(tables)

    assertThat(tables.resolve("a/a.xlsx")).isRegularFile
    assertThat(tables.resolve("a/a.po.keys.txt")).isRegularFile
    assertThat(tables.resolve("deep/b/b.xlsx")).isRegularFile
    assertThat(tables.resolve("deep/b/b.po.keys.txt")).isRegularFile
    val exported = XSSFWorkbook(tables.resolve("a/a.xlsx").inputStream())
    exported.use { workbook: XSSFWorkbook ->
      assertThat(workbook.numberOfSheets).isEqualTo(1)
      assertThat(workbook.getSheetAt(0).physicalNumberOfRows).isEqualTo(1)
      val row = workbook.getSheetAt(0).getRow(0)
      assertThat(row.lastCellNum).isEqualTo(2)
      assertThat(row.getCell(0).stringCellValue).isEqualTo("One")
      assertThat(row.getCell(1).stringCellValue).isEqualTo("Un")
      assertThat(workbook.getSheetAt(0).getRow(1) == null).isTrue()
    }
    val generic = TranslationBookIO().read(tables.resolve("a/a.xlsx").inputStream(), Path.of("a.xlsx"))
    assertThat(generic.translationBook).hasSize(1)
    assertThat(generic.translationBook.single().toTranslate).isEqualTo("One")
    assertThat(generic.translationBook.single().translate).isEqualTo("Un")
    XSSFWorkbook(tables.resolve("deep/b/b.xlsx").inputStream()).use { workbook ->
      assertThat(workbook.getSheetAt(0).getRow(0).getCell(1).stringCellValue).isEmpty()
    }
  }

  @Test
  fun `patch preserves all original text and uses context sidecar for duplicate sources`() {
    val source = temp.resolve("source")
    val original = """
      msgid ""
      msgstr ""
      "Project-Id-Version: synthetic\\n"
      "Language: fr\\n"

      #. translator note
      #: ui:1
      msgctxt "noun"
      msgid "Open"
      msgstr "Ouverture"

      # duplicate source, distinct identity
      msgctxt "verb"
      msgid "Open"
      msgstr "Open"

      # multiline and escaped source
      msgid ""
      "Say \\"hi\\" "
      "on C:\\\\tmp\\nnext"
      msgstr "Déjà traduit"

    """.trimIndent()
    writePo(source.resolve("locale/game.po"), original)
    val tables = temp.resolve("tables")
    PO(source).createTranslationTables(tables)
    val workbook = tables.resolve("locale/game.xlsx")

    editCell(workbook, 1, 1, "Ouvrir") // only msgctxt "verb"
    // Rows 0 and 2 remain blank/nonblank exactly as exported; a source-only map cannot select row 1.
    editCell(workbook, 0, 1, "")
    editCell(workbook, 2, 1, "")
    val output = temp.resolve("output")
    PO(source).patchFromTranslationTables(tables, output)

    val expected = original.replace("msgctxt \"verb\"\nmsgid \"Open\"\nmsgstr \"Open\"", "msgctxt \"verb\"\nmsgid \"Open\"\nmsgstr \"Ouvrir\"")
    assertThat(output.resolve("locale/game.po").readText()).isEqualTo(expected)
    assertThat(output.resolve("locale/game.po").readText())
      .contains("msgctxt \"noun\"\nmsgid \"Open\"\nmsgstr \"Ouverture\"")
  }

  @Test
  fun `changed multiline target is emitted as one safely escaped msgstr`() {
    val source = temp.resolve("source")
    val original = """
      # comments stay put
      msgctxt "dialog"
      msgid "Greeting"
      msgstr ""
      "old line\\n"
      "old \\"quote\\" and \\\\slash"

    """.trimIndent()
    writePo(source.resolve("game.po"), original)
    val tables = temp.resolve("tables")
    PO(source).createTranslationTables(tables)
    editCell(tables.resolve("game.xlsx"), 0, 1, "new line\nnew \"quote\" and \\slash")

    PO(source).patchFromTranslationTables(tables, temp.resolve("output"))

    assertThat(temp.resolve("output/game.po").readText()).isEqualTo(
      "# comments stay put\nmsgctxt \"dialog\"\nmsgid \"Greeting\"\n" +
        "msgstr \"new line\\nnew \\\"quote\\\" and \\\\slash\"\n"
    )
  }

  @Test
  fun `ordinary two-column generic header is accepted`() {
    val source = temp.resolve("source")
    writePo(source.resolve("game.po"), simplePo("Hello", "Hello"))
    val tables = temp.resolve("tables")
    PO(source).createTranslationTables(tables)
    val workbookPath = tables.resolve("game.xlsx")
    XSSFWorkbook(workbookPath.inputStream()).use { workbook ->
      val sheet = workbook.getSheetAt(0)
      sheet.shiftRows(0, sheet.lastRowNum, 1)
      sheet.createRow(0).also { row ->
        row.createCell(0).setCellValue("totranslate")
        row.createCell(1).setCellValue("translated")
      }
      sheet.getRow(1).getCell(1).setCellValue("Bonjour")
      workbookPath.outputStream().use(workbook::write)
    }

    PO(source).patchFromTranslationTables(tables, temp.resolve("output"))

    assertThat(temp.resolve("output/game.po").readText()).contains("msgstr \"Bonjour\"")
  }

  @Test
  fun `missing tampered changed-source count and worksheet-source mismatches fail closed`() {
    fun fixture(name: String): Triple<Path, Path, PO> {
      val source = temp.resolve(name).resolve("source")
      writePo(source.resolve("game.po"), simplePo("Hello", "Hello"))
      val tables = temp.resolve(name).resolve("tables")
      return Triple(source, tables, PO(source).also { it.createTranslationTables(tables) })
    }

    fixture("missing").let { (_, tables, po) ->
      Files.delete(tables.resolve("game.po.keys.txt"))
      assertFailure("missing PO identity sidecar") { po.patchFromTranslationTables(tables, temp.resolve("out1")) }
    }
    fixture("tampered").let { (_, tables, po) ->
      tables.resolve("game.po.keys.txt").writeText(tables.resolve("game.po.keys.txt").readText().replace("SHA256", "BROKEN"))
      assertFailure("malformed sidecar checksum") { po.patchFromTranslationTables(tables, temp.resolve("out2")) }
    }
    fixture("changed").let { (source, tables, po) ->
      source.resolve("game.po").writeText(simplePo("Changed", "Hello"))
      assertFailure("sidecar identity/order") { po.patchFromTranslationTables(tables, temp.resolve("out3")) }
    }
    fixture("count").let { (_, tables, po) ->
      appendWorkbookRow(tables.resolve("game.xlsx"), "Extra", "")
      assertFailure("row count") { po.patchFromTranslationTables(tables, temp.resolve("out4")) }
    }
    fixture("worksheet-source").let { (_, tables, po) ->
      editCell(tables.resolve("game.xlsx"), 0, 0, "Changed")
      assertFailure("UNTRANSLATED") { po.patchFromTranslationTables(tables, temp.resolve("out5")) }
    }
    fixture("columns").let { (_, tables, po) ->
      editCell(tables.resolve("game.xlsx"), 0, 2, "metadata")
      assertFailure("exactly two columns") { po.patchFromTranslationTables(tables, temp.resolve("out6")) }
    }
  }

  @Test
  fun `malformed and plural PO files fail closed with file diagnostics`() {
    val malformed = temp.resolve("malformed")
    writePo(malformed.resolve("bad.po"), "msgid \"A\"\nnot-a-field\nmsgstr \"B\"\n")
    assertFailure("bad.po: malformed PO line") { PO(malformed).createTranslationTables(temp.resolve("bad-tables")) }

    val plural = temp.resolve("plural")
    writePo(plural.resolve("plural.po"), "msgid \"cat\"\nmsgid_plural \"cats\"\nmsgstr[0] \"chat\"\n")
    assertFailure("plural entry is not supported") { PO(plural).createTranslationTables(temp.resolve("plural-tables")) }
  }

  private fun simplePo(msgid: String, msgstr: String) = "msgid \"$msgid\"\nmsgstr \"$msgstr\"\n"

  private fun writePo(path: Path, content: String) {
    path.parent.createDirectories()
    path.writeText(content)
  }

  private fun editCell(path: Path, row: Int, column: Int, value: String) {
    XSSFWorkbook(path.inputStream()).use { workbook ->
      workbook.getSheetAt(0).getRow(row).createCell(column).setCellValue(value)
      path.outputStream().use(workbook::write)
    }
  }

  private fun appendWorkbookRow(path: Path, source: String, target: String) {
    XSSFWorkbook(path.inputStream()).use { workbook ->
      val sheet = workbook.getSheetAt(0)
      sheet.createRow(sheet.lastRowNum + 1).also { row ->
        row.createCell(0).setCellValue(source)
        row.createCell(1).setCellValue(target)
      }
      path.outputStream().use(workbook::write)
    }
  }

  private fun assertFailure(message: String, action: () -> Unit) {
    assertThatThrownBy(action).isInstanceOf(IllegalStateException::class.java).hasMessageContaining(message)
  }
}
