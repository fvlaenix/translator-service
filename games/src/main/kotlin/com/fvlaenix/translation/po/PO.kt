package com.fvlaenix.translation.po

import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.Base64
import kotlin.io.path.createDirectories
import kotlin.io.path.inputStream
import kotlin.io.path.name
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.outputStream

/** Converts a tree of singular gettext PO files to sidecar-bound, two-column workbooks. */
class PO(private val sourceRoot: Path) {
  fun createTranslationTables(outputRoot: Path) {
    sourceFiles().forEach { source ->
      val parsed = parse(source)
      val relative = sourceRoot.relativize(source)
      val workbookPath = outputRoot.resolve(relative.xlsxName())
      workbookPath.parent.createDirectories()
      writeWorkbook(workbookPath, parsed.entries.map {
        listOf(it.msgid, if (it.msgstr == it.msgid) "" else it.msgstr)
      })
      writeSidecar(outputRoot.resolve(relative.sidecarName()), parsed.entries.map { it.identity })
    }
  }

  fun patchFromTranslationTables(tablesRoot: Path, outputRoot: Path) {
    sourceFiles().forEach { source ->
      val relative = sourceRoot.relativize(source)
      val table = tablesRoot.resolve(relative.xlsxName())
      val sidecar = tablesRoot.resolve(relative.sidecarName())
      val parsed = parse(source)
      val identities = readSidecar(sidecar, source)
      if (identities != parsed.entries.map { it.identity }) {
        fail(source, "sidecar identity/order does not match the PO source: $sidecar")
      }
      val rows = readWorkbook(table, source, parsed.entries.size)
      if (rows.size != parsed.entries.size) {
        fail(source, "row count ${rows.size} does not match sidecar/source count ${parsed.entries.size}: $table")
      }
      rows.forEachIndexed { index, row ->
        if (row[0] != identities[index].msgid) {
          fail(source, "row ${index + 1} UNTRANSLATED value does not match sidecar msgid: $table")
        }
      }

      var result = parsed.text
      parsed.entries.zip(rows).asReversed().forEach { (entry, row) ->
        val translated = row[1]
        if (translated.isNotBlank() && translated != entry.msgstr) {
          result = result.replaceRange(entry.msgstrStart, entry.msgstrEnd, "msgstr ${quote(translated)}")
        }
      }
      val destination = outputRoot.resolve(relative)
      destination.parent.createDirectories()
      Files.writeString(destination, result, StandardCharsets.UTF_8)
    }
  }

  private fun sourceFiles(): List<Path> {
    if (!Files.isDirectory(sourceRoot)) fail(sourceRoot, "source root is not a directory")
    return Files.walk(sourceRoot).use { paths ->
      paths.filter { Files.isRegularFile(it) && it.name.endsWith(".po", ignoreCase = true) }
        .sorted()
        .toList()
    }
  }

  private fun Path.xlsxName(): Path = parent?.resolve("${fileName.nameWithoutExtension}.xlsx")
    ?: Path.of("${fileName.nameWithoutExtension}.xlsx")

  private fun Path.sidecarName(): Path = parent?.resolve("${fileName}.keys.txt") ?: Path.of("${fileName}.keys.txt")

  private fun writeWorkbook(path: Path, rows: List<List<String>>) {
    XSSFWorkbook().use { workbook ->
      val sheet = workbook.createSheet()
      rows.forEachIndexed { rowIndex, values ->
        val row = sheet.createRow(rowIndex)
        row.createCell(0).setCellValue(values[0])
        row.createCell(1).setCellValue(values[1])
      }
      path.outputStream().use(workbook::write)
    }
  }

  private fun readWorkbook(path: Path, source: Path, expectedRows: Int): List<List<String>> {
    if (!Files.isRegularFile(path)) fail(source, "missing translation workbook: $path")
    try {
      path.inputStream().use { input ->
        XSSFWorkbook(input).use { workbook ->
          if (workbook.numberOfSheets != 1) fail(source, "workbook must contain exactly one sheet: $path")
          val sheet = workbook.getSheetAt(0)
          val worksheetRows = sheet.toList().filterNot { row ->
            (0 until row.lastCellNum.coerceAtLeast(0)).all { cellText(row.getCell(it)).isBlank() }
          }
          val dataRows = if (
            worksheetRows.size >= 2 && isMagicRow(worksheetRows[0]) && isGenericHeader(worksheetRows[1])
          ) worksheetRows.drop(2) else worksheetRows
          val rows = dataRows.mapIndexed { index, row ->
            if (row.firstCellNum < 0 || row.lastCellNum != 2.toShort()) {
              fail(source, "worksheet row ${index + 1} must contain exactly two columns: $path")
            }
            listOf(cellText(row.getCell(0)), cellText(row.getCell(1)))
          }.toMutableList()
          if (rows.size == expectedRows + 1 && rows.firstOrNull()?.let(::isGenericHeader) == true) rows.removeAt(0)
          return rows
        }
      }
    } catch (e: Exception) {
      throw IllegalStateException("$source: cannot read workbook $path: ${e.message}", e)
    }
  }

  private fun cellText(cell: org.apache.poi.ss.usermodel.Cell?): String {
    if (cell == null || cell.cellType == CellType.BLANK) return ""
    if (cell.cellType != CellType.STRING) throw IllegalStateException("non-text worksheet cell")
    return cell.stringCellValue
  }

  private fun isMagicRow(row: org.apache.poi.ss.usermodel.Row): Boolean =
    row.firstCellNum == 0.toShort() && row.lastCellNum == 3.toShort() &&
      cellText(row.getCell(0)) == "fvlaenix-magic-words" &&
      cellText(row.getCell(1)) == "SRPG" && cellText(row.getCell(2)) == "SRPG"

  private fun isGenericHeader(row: org.apache.poi.ss.usermodel.Row): Boolean =
    row.firstCellNum == 0.toShort() && row.lastCellNum == 2.toShort() &&
      cellText(row.getCell(0)) == "totranslate" && cellText(row.getCell(1)) == "translated"

  private fun isGenericHeader(row: List<String>): Boolean =
    row[0].equals("totranslate", true) && row[1].equals("translated", true) ||
      row[0].equals("untranslated", true) && row[1].equals("translated", true)

  private fun writeSidecar(path: Path, identities: List<Identity>) {
    path.parent.createDirectories()
    val encoded = identities.map(::encodeIdentity)
    val payload = encoded.joinToString("\n")
    val digest = sha256(payload)
    Files.writeString(
      path,
      buildString {
        appendLine(SIDECAR_VERSION)
        appendLine("COUNT\t${identities.size}")
        appendLine("SHA256\t$digest")
        encoded.forEach(::appendLine)
      },
      StandardCharsets.UTF_8
    )
  }

  private fun readSidecar(path: Path, source: Path): List<Identity> {
    if (!Files.isRegularFile(path)) fail(source, "missing PO identity sidecar: $path")
    val lines = try {
      strictUtf8(path).lines().dropLastWhile(String::isEmpty)
    } catch (e: Exception) {
      throw IllegalStateException("$source: cannot read sidecar $path: ${e.message}", e)
    }
    if (lines.firstOrNull() != SIDECAR_VERSION) fail(source, "unsupported sidecar version: $path")
    val count = lines.getOrNull(1)?.takeIf { it.startsWith("COUNT\t") }
      ?.substringAfter('\t')?.toIntOrNull() ?: fail(source, "malformed sidecar count: $path")
    val expectedHash = lines.getOrNull(2)?.takeIf { it.startsWith("SHA256\t") }
      ?.substringAfter('\t') ?: fail(source, "malformed sidecar checksum: $path")
    val encoded = lines.drop(3)
    if (encoded.size != count) fail(source, "sidecar count $count does not match ${encoded.size} identities: $path")
    if (sha256(encoded.joinToString("\n")) != expectedHash) fail(source, "sidecar checksum mismatch: $path")
    return encoded.mapIndexed { index, line ->
      try {
        decodeIdentity(line)
      } catch (e: Exception) {
        throw IllegalStateException("$source: malformed sidecar identity ${index + 1}: $path", e)
      }
    }
  }

  private fun encodeIdentity(identity: Identity): String =
    encodeNullable(identity.context) + "\t" + encode(identity.msgid)

  private fun decodeIdentity(line: String): Identity {
    val parts = line.split('\t')
    require(parts.size == 2)
    return Identity(decodeNullable(parts[0]), decode(parts[1]))
  }

  private fun encodeNullable(value: String?): String = if (value == null) "N" else "S${encode(value)}"
  private fun decodeNullable(value: String): String? = when {
    value == "N" -> null
    value.startsWith("S") -> decode(value.drop(1))
    else -> error("invalid nullable value")
  }

  private fun encode(value: String): String = Base64.getUrlEncoder().withoutPadding()
    .encodeToString(value.toByteArray(StandardCharsets.UTF_8))

  private fun decode(value: String): String = String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8)
  private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(StandardCharsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 0xff) }

  private data class Identity(val context: String?, val msgid: String)
  private data class Entry(
    val identity: Identity,
    val msgstr: String,
    val msgstrStart: Int,
    val msgstrEnd: Int
  ) {
    val msgid: String get() = identity.msgid
  }
  private data class Parsed(val text: String, val entries: List<Entry>)
  private data class Line(val text: String, val start: Int, val contentEnd: Int)

  private fun parse(path: Path): Parsed {
    val text = try {
      strictUtf8(path)
    } catch (e: Exception) {
      throw IllegalStateException("$path: not valid UTF-8", e)
    }
    val lines = splitLines(text)
    val entries = mutableListOf<Entry>()
    var begin = 0
    for (end in 0..lines.size) {
      if (end == lines.size || lines[end].text.isBlank()) {
        if (begin < end) parseBlock(path, lines.subList(begin, end))?.let(entries::add)
        begin = end + 1
      }
    }
    return Parsed(text, entries)
  }

  private fun parseBlock(path: Path, lines: List<Line>): Entry? {
    data class Field(val name: String, val value: StringBuilder, val start: Int, var end: Int)
    val fields = linkedMapOf<String, Field>()
    var current: Field? = null
    var sawDirective = false
    lines.forEach { line ->
      val value = line.text.removePrefix("\uFEFF")
      if (value.startsWith("#")) {
        current = null
        return@forEach
      }
      if (value.matches(Regex("msgid_plural\\b.*|msgstr\\[.*"))) fail(path, "plural entry is not supported near offset ${line.start}")
      val directive = Regex("(msgctxt|msgid|msgstr)\\s+(.*)").matchEntire(value)
      if (directive != null) {
        sawDirective = true
        val name = directive.groupValues[1]
        if (fields.containsKey(name)) fail(path, "duplicate $name near offset ${line.start}")
        val field = Field(name, StringBuilder(unquote(directive.groupValues[2], path)), line.start, line.contentEnd)
        fields[name] = field
        current = field
      } else if (value.trimStart().startsWith('"')) {
        val field = current ?: fail(path, "orphan quoted continuation near offset ${line.start}")
        field.value.append(unquote(value.trimStart(), path))
        field.end = line.contentEnd
      } else {
        fail(path, "malformed PO line near offset ${line.start}: $value")
      }
    }
    if (!sawDirective) return null
    val msgid = fields["msgid"] ?: fail(path, "entry is missing msgid")
    val msgstr = fields["msgstr"] ?: fail(path, "entry is missing msgstr")
    val validOrder = fields.keys.toList() == listOf("msgid", "msgstr") ||
      fields.keys.toList() == listOf("msgctxt", "msgid", "msgstr")
    if (!validOrder) fail(path, "invalid gettext field order")
    if (msgid.value.isEmpty()) return null // gettext metadata header
    return Entry(
      Identity(fields["msgctxt"]?.value?.toString(), msgid.value.toString()),
      msgstr.value.toString(),
      msgstr.start,
      msgstr.end
    )
  }

  private fun unquote(raw: String, path: Path): String {
    val value = raw.trim()
    if (value.length < 2 || value.first() != '"' || value.last() != '"') fail(path, "malformed quoted PO string: $raw")
    val result = StringBuilder()
    var index = 1
    while (index < value.lastIndex) {
      val char = value[index++]
      if (char != '\\') {
        result.append(char)
        continue
      }
      if (index >= value.lastIndex) fail(path, "unfinished escape in PO string")
      val escaped = value[index++]
      result.append(when (escaped) {
        'a' -> '\u0007'; 'b' -> '\b'; 'f' -> '\u000c'; 'n' -> '\n'; 'r' -> '\r'
        't' -> '\t'; 'v' -> '\u000b'; '\\' -> '\\'; '"' -> '"'
        in '0'..'7' -> {
          var octal = escaped.toString()
          repeat(2) { if (index < value.lastIndex && value[index] in '0'..'7') octal += value[index++] }
          octal.toInt(8).toChar()
        }
        'x' -> {
          var hex = ""
          while (index < value.lastIndex && value[index].isDigit() || index < value.lastIndex && value[index].lowercaseChar() in 'a'..'f') {
            hex += value[index++]
          }
          if (hex.isEmpty()) fail(path, "empty hexadecimal escape in PO string")
          hex.toInt(16).toChar()
        }
        else -> fail(path, "unsupported escape \\$escaped in PO string")
      })
    }
    return result.toString()
  }

  private fun quote(value: String): String = buildString {
    append('"')
    value.forEach { char ->
      when (char) {
        '\\' -> append("\\\\"); '"' -> append("\\\""); '\n' -> append("\\n")
        '\r' -> append("\\r"); '\t' -> append("\\t"); '\b' -> append("\\b")
        '\u000c' -> append("\\f"); else -> if (char.code < 32 || char.code == 127) append("\\%03o".format(char.code)) else append(char)
      }
    }
    append('"')
  }

  private fun splitLines(text: String): List<Line> {
    val result = mutableListOf<Line>()
    var start = 0
    var index = 0
    while (index < text.length) {
      if (text[index] == '\n' || text[index] == '\r') {
        result += Line(text.substring(start, index), start, index)
        if (text[index] == '\r' && index + 1 < text.length && text[index + 1] == '\n') index++
        start = ++index
      } else index++
    }
    if (start < text.length) result += Line(text.substring(start), start, text.length)
    return result
  }

  private fun strictUtf8(path: Path): String = StandardCharsets.UTF_8.newDecoder()
    .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
    .decode(ByteBuffer.wrap(Files.readAllBytes(path))).toString()

  private fun fail(path: Path, message: String): Nothing = throw IllegalStateException("$path: $message")

  companion object {
    private const val SIDECAR_VERSION = "FVLAENIX_PO_KEYS\t1"
  }
}
