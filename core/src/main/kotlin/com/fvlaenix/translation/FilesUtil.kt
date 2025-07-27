package com.fvlaenix.translation

import java.io.File
import java.nio.file.Path
import kotlin.io.path.isDirectory

object FilesUtil {
  private fun Sequence<File>.defaultSorter(): Sequence<File> {
    return if (this.all { it.nameWithoutExtension.toIntOrNull() != null }) {
      this.sortedBy { it.nameWithoutExtension.toInt() }
    } else {
      this.sortedBy { it.path }
    }
  }

  fun getPaths(
    path: Path,
    filter: ((File) -> Boolean) = { true },
    sorter: (Sequence<File>).() -> Sequence<File> = { this.defaultSorter() }
  ): List<Path> {
    return if (path.isDirectory()) {
      val directory = path.toFile()

      directory.walkTopDown()
        .filter { filter(it) }
        .sorter()
        .map { it.toPath() }
        .toList()
    } else {
      listOf(path).filter { filter(it.toFile()) }
    }
  }
}