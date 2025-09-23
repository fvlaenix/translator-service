package com.fvlaenix.translation.table

import java.nio.file.Path

data class TranslationBook(
  val name: String,
  val path: Path,
  val translationBook: List<TranslationData>
)