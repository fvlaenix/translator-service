package com.fvlaenix.translation.srpg

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.node.TextNode
import com.fvlaenix.translation.NamesService
import com.fvlaenix.translation.Util
import io.github.evanrupert.excelkt.workbook
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.*

class SRPG(
  private val baseDirectory: Path
) {
  // Everything is the file (c) Linus
  abstract class File(val path: Path) {
    init { if (path.notExists()) throw IllegalStateException("Path $path not exists") }
    abstract fun createTranslationTables(output: Path)
    abstract fun patchFromTranslationTables(namesService: NamesService, tables: Path, output: Path)
  }
  
  class Folder(path: Path, val sub: MutableList<File> = mutableListOf()): File(path) {
    override fun patchFromTranslationTables(namesService: NamesService, tables: Path, output: Path) {
      Files.createDirectories(output.resolve(this.path.fileName))
      sub.forEach { file ->
        file.patchFromTranslationTables(
          namesService,
          tables.resolve(this.path.fileName),
          output.resolve(this.path.fileName)
        )
      }
    }

    override fun createTranslationTables(output: Path) {
      Files.createDirectories(output.resolve(this.path.fileName))
      sub.forEach { file ->
        file.createTranslationTables(output.resolve(this.path.fileName))
      }
    }
  }
  
  abstract class TextFile(path: Path): File(path) {
    fun saveTranslationMap(output: Path, list: List<Pair<String, String?>>) {
      workbook { 
        sheet { 
          list.filter { it.first.isNotEmpty() }.forEach { phrase ->
            row {
              cell(phrase.first)
              if (phrase.second != null) cell(phrase.second!!)
              else println("Warning")
            }
          }
        }
      }.write(output.resolve("${path.nameWithoutExtension}.xlsx").toString())
    } 
    
    fun getTranslationMap(tablePath: Path): Map<String, String> {
      val translationMap = mutableMapOf<String, String>()
      val xlsxPath = tablePath.resolve("${path.nameWithoutExtension}.xlsx")
      if (xlsxPath.notExists()) {
        return emptyMap()
      }
      val workbook = XSSFWorkbook(xlsxPath.inputStream())
      val sheet = workbook.getSheetAt(0)
      sheet.toList().forEachIndexed { indexRow, row ->
        val cells = row.toList().map { it.stringCellValue }
        when (cells.size) {
          0 -> Unit
          1 -> println("Not translated line ${indexRow + 1} in $path")
          2 -> translationMap[cells[0].trim()] = cells[1].trim()
          else -> throw IllegalStateException("Cells too much in line ${indexRow + 1} in $path")
        }
      }
      translationMap[""] = ""
      return translationMap
    }
  }
  
  open class Json(path: Path): TextFile(path) {
    override fun createTranslationTables(output: Path) {
      val objectMapper = ObjectMapper()
      val translationMap = getTranslationMap(output)
      val content = objectMapper.readTree(path.inputStream())
      if (content !is ArrayNode) throw IllegalStateException("File should be Array Json: $path")
      val toTranslate = mutableListOf<Pair<String, String?>>()
      content.forEach { jsonNode ->
        jsonNode["name"]?.let { toTranslate.add(Pair(it.asText(), translationMap[it.asText()])) }
        jsonNode["desc"]?.let { toTranslate.add(Pair(it.asText(), translationMap[it.asText()])) }
        jsonNode["mapName"]?.let { toTranslate.add(Pair(it.asText(), translationMap[it.asText()])) }
        jsonNode["victories"]?.let { node -> (node as ArrayNode).forEach { toTranslate.add(Pair(it.asText(), translationMap[it.asText()])) } }
        jsonNode["defeats"]?.let { node -> (node as ArrayNode).forEach { toTranslate.add(Pair(it.asText(), translationMap[it.asText()])) } }
      }
      saveTranslationMap(output, toTranslate)
    }

    override fun patchFromTranslationTables(namesService: NamesService, tables: Path, output: Path) {
      val translationMap = getTranslationMap(tables)
      val objectMapper = ObjectMapper()
      val content = objectMapper.readTree(path.inputStream())
      if (content !is ArrayNode) throw IllegalStateException("File should be Array Json: $path")
      content.forEach { jsonNode ->
        jsonNode as ObjectNode
        fun JsonNode.translateAsTextNode(): JsonNode {
          check(this is TextNode) { "In json $path node is not text" }
          val translation = translationMap[this.asText().trim()]
            ?: throw IllegalStateException("Can't find translation for ${this.asText()} in json $path")
          return TextNode(translation)
        }
        fun JsonNode.translateAsArrayNodeWithTexts(): ArrayNode {
          check(this is ArrayNode) { "In json $path node is not array" }
          val newArrayNode = objectMapper.createArrayNode()
          this.forEach { newArrayNode.add(it.translateAsTextNode()) }
          return newArrayNode
        }
        fun ObjectNode.replaceTextNode(name: String) {
          if (this[name] != null) this.replace(name, this[name].translateAsTextNode())
        }
        fun ObjectNode.replaceArrayNode(name: String) {
          if (this[name] != null) this.replace(name, this[name].translateAsArrayNodeWithTexts())
        }
        jsonNode.replaceTextNode("name")
        jsonNode.replaceTextNode("desc")
        jsonNode.replaceTextNode("mapName")
        jsonNode.replaceArrayNode("victories")
        jsonNode.replaceArrayNode("defeats")
      }
      objectMapper.writer().writeValue(output.resolve("${path.fileName}").outputStream(), content)
    }
  }
  
  open class EventText(path: Path): TextFile(path) {
    
    companion object {
      private val SIDES = listOf("Top", "Bottom", "Right", "Left", "Center", "Middle", "None", "Information", "1")
      private val SYSTEM_DIALOG_REGEX = "<\\(\\d+\\)([\\p{script=Han}\\w]+)@\\d+>".toRegex()
    }
    
    override fun createTranslationTables(output: Path) {
      val translationMap = getTranslationMap(output)
      val fullText = path.readText()
      val texts = fullText.split("\r\n\r\n")
      val toTranslate = mutableListOf<Pair<String, String?>>()
      texts.forEachIndexed { index, block ->
        var lines = block.split("\r\n")
        if (lines.isEmpty() || (lines.size == 1 && lines[0].isBlank())) return@forEachIndexed
        if (lines[0].startsWith("<") && lines[0].endsWith(">")) lines = lines.drop(1)
        val ends = SIDES.filter { lines[0].endsWith(":$it") || lines[0].endsWith("：$it") }
        if (ends.isEmpty()) throw IllegalStateException("Line $index doesn't have side thing in $path")
        val end = ends.single()
        val author = lines[0].substringBeforeLast(end).let { it.substring(0, it.length - 1) }
        
        if (author == "Choice") {
          lines = lines.drop(1)
          lines.forEach { toTranslate.add(Pair(it, translationMap[it])) }
        } else {
          lines = lines.drop(1)
          var accumulator: String? = null
          var countLines = 0
          lines.forEach { line ->
            accumulator = (accumulator?.let { "$accumulator " } ?: "") + line
            countLines++
            while (accumulator!!.endsWith("*")) {
              accumulator = accumulator!!.substring(0, accumulator!!.length - 1)
              countLines++
            }
            if (countLines > 3) throw IllegalStateException("Count of lines more than 3")
            if (countLines == 3) {
              toTranslate.add(Pair(accumulator!!, translationMap[accumulator!!]))
              accumulator = null
              countLines = 0
            }
          }
          if (accumulator != null) {
            toTranslate.add(Pair(accumulator!!, translationMap[accumulator!!]))
          }
        }
      }
      saveTranslationMap(output, toTranslate)
    }

    override fun patchFromTranslationTables(namesService: NamesService, tables: Path, output: Path) {
      val translationMap = getTranslationMap(tables)
      val fullText = path.readText()
      val texts = fullText.split("\r\n\r\n")
      val translatedTexts = texts.mapIndexed { index, block -> 
        try {
          val translatedLines = mutableListOf<String>()
          val lines = block.split("\r\n")
          var iter = 0
          if (lines.isEmpty() || (lines.size == 1 && lines[0].isBlank())) return@mapIndexed ""
          if (lines[iter].startsWith("<") && lines[iter].endsWith(">")) {
            val systemLine = lines[iter]
            val match = SYSTEM_DIALOG_REGEX.matchEntire(systemLine)
            if (match != null) {
              val name = match.groups[1]!!.value
              val translatedName: String = namesService[name]
              translatedLines.add(systemLine.replace(name, translatedName))
            } else {
              translatedLines.add(systemLine)
            }
            iter++
          }
          if (SIDES.none { lines[iter].endsWith(":$it") || lines[iter].endsWith("：$it") }) throw IllegalStateException("Line $index doesn't have side thing")
          val blockBoxName = lines[iter].split("[:：]".toRegex())[0]
          if (blockBoxName == "Choice") {
            translatedLines.add(lines[iter])
            iter++
            while (iter < lines.size) {
              val translation = translationMap[lines[iter]] ?: throw IllegalStateException("Can't find translation for ${lines[iter]}")
              translatedLines.add(translation)
              iter++
            }
            translatedLines.joinToString(separator = "\r\n")
          } else {
            val translatedBlockBoxName: String = namesService[blockBoxName]
            translatedLines.add(lines[iter].replace(blockBoxName, translatedBlockBoxName))
            iter++
            var accumulator: String? = null
            var countLines = 0

            fun addAccumulator(withStars: Boolean) {
              val translate = translationMap[accumulator!!.trim()]
                ?: throw IllegalStateException("Can't find translation for $accumulator")
              val words = Util.splitSymbols(translate, COUNT_SYMBOLS_IN_LINE)
              if (words.size > 3) throw IllegalStateException("Can't make it in 3 lines")
              translatedLines.add(words.joinToString(separator = "\r\n") + if (withStars) "*".repeat(3 - words.size) else "")
              accumulator = null
              countLines = 0
            }

            while (iter < lines.size) {
              val line = lines[iter]
              accumulator = (accumulator?.let { "$accumulator " } ?: "") + line
              countLines++
              while (accumulator!!.endsWith("*")) {
                accumulator = accumulator!!.substring(0, accumulator!!.length - 1)
                countLines++
              }
              if (countLines > 3) throw IllegalStateException("Count of lines more than 3")
              if (countLines == 3) {
                addAccumulator(true)
              }
              iter++
            }
            if (accumulator != null) {
              addAccumulator(false)
            }
            translatedLines.joinToString(separator = "\r\n")
          }
        } catch (e: Exception) {
          throw Exception("Exception while patch block $index from file $path", e)
        }
      }
      output.resolve(path.fileName).writeText(translatedTexts.joinToString(separator = "\r\n\r\n"))
    }

  }
  
  open class CopyPasteFile(path: Path): TextFile(path) {
    override fun createTranslationTables(output: Path) {
      saveTranslationMap(output, emptyList())
    }

    override fun patchFromTranslationTables(namesService: NamesService, tables: Path, output: Path) {
      output.resolve(this.path.fileName).writeBytes(path.readBytes())
    }

  }
  
  private fun Folder.eventText(name: String) {
    val eventText = EventText(this.path.resolve(name))
    this.sub.add(eventText)
  }
  
  private fun Folder.eventText(regex: Regex) {
    val listFiles = this.path.listDirectoryEntries().filter { it.name.matches(regex) && !it.isDirectory() }
    listFiles.forEach { path ->
      val folder = EventText(path)
      this.sub.add(folder)
    }
  }

  private fun Folder.simpleJson(name: String) {
    val simpleJson = Json(this.path.resolve(name))
    this.sub.add(simpleJson)
  }
  
  private fun Folder.simpleJson(regex: Regex) {
    val listFiles = this.path.listDirectoryEntries().filter { it.name.matches(regex) && !it.isDirectory() }
    listFiles.forEach { path ->
      val folder = Json(path)
      this.sub.add(folder)
    }
  }

  private fun Folder.folder(name: String, body: Folder.() -> Unit) {
    val folder = Folder(this.path.resolve(name))
    this.sub.add(folder)
    body(folder)
  }
  
  private fun Folder.copyPasteFile(name: String) {
    val copyPasteFile = CopyPasteFile(this.path.resolve(name))
    this.sub.add(copyPasteFile)
  }

  private fun Folder.folder(regex: Regex, body: Folder.() -> Unit) {
    val listFiles = this.path.listDirectoryEntries().filter { it.name.matches(regex) && it.isDirectory() }
    listFiles.forEach { path ->
      val folder = Folder(path)
      this.sub.add(folder)
      body(folder)
    }
  }
  
  fun createTranslationTables(path: Path) {
    getStructure().forEach { it.createTranslationTables(path) }
  }
  
  fun patchFromTranslationTables(namesService: NamesService, tablesPath: Path, path: Path) {
    getStructure().forEach { it.patchFromTranslationTables(namesService, tablesPath, path) }
  }

  companion object {
    private const val COUNT_SYMBOLS_IN_LINE = 50
  }
  
  private fun getStructure(): List<File> {
    val folder = Folder(baseDirectory)
    return folder.apply { 
      folder("Base") {
        simpleJson("bonuses.json")
        simpleJson("communication.json")
        simpleJson("quests.json")
        simpleJson("shops.json")
        simpleJson("switches.json")
        simpleJson("talk.json")
      }
      folder("CommandLayout") {
        simpleJson("base.json")
        simpleJson("battleprep.json")
        simpleJson("manage.json")
        simpleJson("mapcommands.json")
        simpleJson("title.json")
      }
      folder("CommandStrings") {
        simpleJson("placeevents.json")
        simpleJson("talkevents.json")
      }
      folder("constants") {
        copyPasteFile("constants-stringtable.js")
      }
      folder("_EventText") {
        folder("id\\d+".toRegex()) {
          eventText("ally.txt")
          eventText("allyevent.txt")
          eventText("enemy.txt")
          eventText("enemyevent.txt")
          eventText("guest.txt")
          eventText("guestevent.txt")
          eventText("reinforce.txt")
        }
        eventText("id\\d+.txt".toRegex())
        eventText("mapcommon.txt")
        eventText("recollection.txt")
        eventText("rest.txt")
        eventText("story_character.txt")
        eventText("story_word.txt")
        eventText("unitevent.txt")
      }
      folder("Extra") {
        simpleJson("characters.json")
        simpleJson("gallery.json")
        simpleJson("glossary.json")
        simpleJson("recollection.json")
        simpleJson("soundroom.json")
      }
      folder("Map\\d+".toRegex()) {
        simpleJson("allies.json")
        simpleJson("communicationevents.json")
        simpleJson("enemies.json")
        simpleJson("evallies.json")
        simpleJson("evenemies.json")
        simpleJson("evguests.json")
        simpleJson("guests.json")
        simpleJson("reinforcements.json")
        simpleJson("switches.json")
      }
      folder("NPC Settings") {
        simpleJson("npc\\d+.json".toRegex())
      }
      folder("OriginalData") {
        simpleJson("originaldata\\d+.json".toRegex())
      }
      folder("ResourceLocation") {
        simpleJson("effectivetargets.json")
        simpleJson("screens.json")
        simpleJson("strings.json")
      }
      folder("Terrain") {
        simpleJson("OriginalTerrain\\d+.json".toRegex())
      }
      folder("Variables") {
        simpleJson("variable\\d+.json".toRegex())
      }
      folder("WeaponTypes") {
        simpleJson("archers.json")
        simpleJson("fighters.json")
        simpleJson("items.json")
        simpleJson("mages.json")
      }
      simpleJson("bookmark.json")
      simpleJson("changeinfo.json")
      simpleJson("classes.json")
      simpleJson("classgroups.json")
      simpleJson("classtypes.json")
      simpleJson("difficulties.json")
      simpleJson("fonts.json")
      simpleJson("fusionsettings.json")
      simpleJson("globalswitches.json")
      simpleJson("items.json")
      simpleJson("mapcolors.json")
      simpleJson("maps.json")
      simpleJson("players.json")
      simpleJson("races.json")
      simpleJson("shoplayout.json")
      simpleJson("skills.json")
      simpleJson("states.json")
      simpleJson("terraingroups.json")
      simpleJson("texttags.json")
      simpleJson("titles.json")
      simpleJson("transformation.json")
      simpleJson("weapons.json")
    }.sub
  }
}