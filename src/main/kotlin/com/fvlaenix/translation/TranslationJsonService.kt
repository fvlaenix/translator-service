package com.fvlaenix.translation

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import java.io.InputStream
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.inputStream
import kotlin.io.path.name
import kotlin.io.path.writeText
import kotlin.math.min

const val MAX_COUNT_LINES = 4

class TranslationJsonService(
  path: Path,
  private val model: String
) {
  private val files = FilesUtil
    .getPaths(path, filter = { it.extension == "json" })
    .map { file -> runCatching { JsonBook(file.inputStream(), path.relativize(file)) }.getOrElse { throw Exception("On reading ${file.name}", it) } }
  val prompt = TranslationJsonService::class.java.getResourceAsStream("/prompt.txt")!!.bufferedReader().readText()

  suspend fun translate() {
    val booksLines = mutableMapOf<JsonBook, Int>()
    var it = 0
    val lines = files.flatMap { book ->
      val text = book.getText()
      booksLines[book] = text.size
      it += text.size
      text
    }
    it = 0
    var bookIt = 0
    var accumulator = mutableListOf<String>()
    try {
      while (it < lines.size) {
        val finish = min(lines.size, it + MAX_COUNT_LINES)
        val linesToTranslate = lines.subList(it, finish)
        val translation = GPTUtil.translate(prompt, model, linesToTranslate, 5)
          ?: throw Exception("Can't get translation of lines $linesToTranslate")
        accumulator.addAll(translation)
        while (files.size > bookIt && accumulator.size >= booksLines[files[bookIt]]!!) {
          files[bookIt].setText(accumulator)
          val nextAccumulator = accumulator.subList(booksLines[files[bookIt]]!!, accumulator.size)
          accumulator = nextAccumulator
          bookIt++
        }
        it = finish
      }
    } catch (e: Exception) {
      throw Exception("Exception while trying get lines started from ${files[bookIt].relativePath.fileName}", e)
    }
  }

  fun write(path: Path) {
    files.forEach { file ->
      file.write(path)
    }
  }

  class JsonBook(
    val inputStream: InputStream,
    val relativePath: Path
  ) {
    val file = ObjectMapper().readTree(inputStream)

    private fun getText(o: ObjectNode): List<String> {
      if (o.has("text")) {
        return if (o.has("translation")) {
          emptyList()
        } else {
          listOf(o["text"].asText())
        }
      } else {
        val keys = o.fieldNames()
        return keys.asSequence().toList().flatMap { key ->
          val node = o[key] as ArrayNode
          getText(node)
        }
      }
    }

    private fun getText(o: ArrayNode): List<String> {
      return o.flatMap { getText(it as ObjectNode) }
    }

    private fun setText(o: ObjectNode, lines: List<String>, it: Int): Int {
      var it = it
      if (o.has("text")) {
        if (!o.has("translation")) {
          o.put("translation", lines[it])
          return it + 1
        } else {
          return it
        }
      } else {
        val keys = o.fieldNames()
        keys.asSequence().toList().forEach { key ->
          val node = o[key] as ArrayNode
          it = setText(node, lines, it)
        }
        return it
      }
    }

    private fun setText(o: ArrayNode, lines: List<String>, it: Int): Int {
      var it = it
      o.forEach { subO ->
        subO as ObjectNode
        it = setText(subO, lines, it)
      }
      return it
    }

    fun getText(): List<String> {
      return when (file) {
        is ObjectNode -> getText(file)
        is ArrayNode -> getText(file)
        else -> throw IllegalStateException("Unknown type: ${file::class.java.name}")
      }
    }

    fun setText(lines: List<String>) {
      when (file) {
        is ObjectNode -> setText(file, lines, 0)
        is ArrayNode -> setText(file, lines, 0)
        else -> throw IllegalStateException("Unknown type: ${file::class.java.name}")
      }
    }

    fun write(parentDirectory: Path) {
      val writePath = parentDirectory.resolve(this.relativePath)
      writePath.parent.createDirectories()
      writePath.writeText(file.toPrettyString())
    }
  }
}