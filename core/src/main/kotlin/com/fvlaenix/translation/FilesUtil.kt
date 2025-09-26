package com.fvlaenix.translation

import java.io.File
import java.nio.file.Path
import kotlin.io.path.isDirectory

/**
 * File-system utilities for collecting paths with optional filtering and sorting.
 */
object FilesUtil {
  private fun Sequence<File>.defaultSorter(): Sequence<File> {
    return if (this.all { it.nameWithoutExtension.toIntOrNull() != null }) {
      this.sortedBy { it.nameWithoutExtension.toInt() }
    } else {
      this.sortedBy { it.path }
    }
  }

  /**
   * Returns a list of paths under the given [path], optionally filtered and sorted.
   *
   * If [path] is a directory, performs a top-down traversal and applies [filter] to files and
   * directories before mapping them to [Path]s. The resulting sequence is ordered using [sorter].
   * When [path] is a file, returns a single-element list if it matches [filter], or an empty list otherwise.
   * The default [sorter] orders files numerically by name when all names are numeric, otherwise by path.
   *
   * @param path A directory to walk or a single file path.
   * @param filter Predicate to include only desired files.
   * @param sorter Ordering to apply to the discovered files.
   * @return The collected paths after filtering and sorting.
   */
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