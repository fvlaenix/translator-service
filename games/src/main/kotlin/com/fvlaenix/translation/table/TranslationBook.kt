package com.fvlaenix.translation.table

import java.nio.file.Path

/**
 * Represents a single translation workbook loaded from or written to disk.
 *
 * A book aggregates rows of translation data parsed from an XLSX sheet.
 *
 * @property name Logical name of the book, typically the file name.
 * @property path Path to the book relative to a base directory.
 * @property translationBook Ordered collection of rows to translate and their results.
 */
data class TranslationBook(
  val name: String,
  val path: Path,
  val translationBook: List<TranslationData>
)