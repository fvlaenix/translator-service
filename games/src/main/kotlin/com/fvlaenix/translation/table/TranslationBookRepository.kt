package com.fvlaenix.translation.table

import com.fvlaenix.translation.FilesUtil
import java.nio.file.Path
import kotlin.io.path.inputStream

class TranslationBookRepository(
  private val io: TranslationBookIO = TranslationBookIO()
) {

  fun loadBooks(path: Path): List<TranslationBook> {
    val xlsxPaths = FilesUtil.getPaths(
      path,
      filter = { it.extension == "xlxs" || it.extension == "xlsx" }
    )

    return xlsxPaths.map { xlsxPath ->
      loadBook(xlsxPath, path)
    }
  }

  fun loadBook(filePath: Path, basePath: Path): TranslationBook {
    return filePath.inputStream().use { inputStream ->
      io.read(inputStream, basePath.relativize(filePath))
    }
  }

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