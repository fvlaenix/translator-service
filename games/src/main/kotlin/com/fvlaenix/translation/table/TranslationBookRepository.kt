package com.fvlaenix.translation.table

import com.fvlaenix.translation.FilesUtil
import java.nio.file.Path
import kotlin.io.path.inputStream

/**
 * Loads and saves translation books from/to XLSX files.
 *
 * Delegates the actual read/write logic to [TranslationBookIO].
 */
class TranslationBookRepository(
  private val io: TranslationBookIO = TranslationBookIO()
) {

  /**
   * Loads all translation books from the given directory.
   *
   * Scans recursively for files with `xlsx` or `xlxs` extensions.
   *
   * @param path Root directory to search.
   * @return The list of parsed books.
   */
  fun loadBooks(path: Path): List<TranslationBook> {
    val xlsxPaths = FilesUtil.getPaths(
      path,
      filter = { it.extension == "xlxs" || it.extension == "xlsx" }
    )

    return xlsxPaths.map { xlsxPath ->
      loadBook(xlsxPath, path)
    }
  }

  /**
   * Loads a single book from the given file.
   *
   * @param filePath Path to the XLSX file.
   * @param basePath Base directory used to compute a relative [TranslationBook.path].
   * @return The parsed book.
   */
  fun loadBook(filePath: Path, basePath: Path): TranslationBook {
    return filePath.inputStream().use { inputStream ->
      io.read(inputStream, basePath.relativize(filePath))
    }
  }

  /**
   * Saves a single book as an XLSX file under the output directory.
   *
   * @param book Book to write.
   * @param outputDirectory Root directory where the file is created.
   */
  fun saveBook(book: TranslationBook, outputDirectory: Path) {
    io.write(book, outputDirectory)
  }

  fun saveBooks(books: List<TranslationBook>, outputDirectory: Path) {
    books.forEach { book ->
      saveBook(book, outputDirectory)
    }
  }

  fun hasTranslationFiles(path: Path): Boolean {
    return FilesUtil.getPaths(
      path,
      filter = { it.extension == "xlxs" || it.extension == "xlsx" }
    ).isNotEmpty()
  }
}