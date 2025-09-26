package com.fvlaenix.translation.context

import kotlin.io.path.Path
import kotlin.io.path.readText

/**
 * Context implementation that loads its text from a file path or classpath resource.
 *
 * Attempts to read from the classpath first (via `getResourceAsStream`), falling back to the
 * file system if the resource is not available.
 */
class GlobalContext : Context {
  private val contextText: String

  /**
   * Creates a context by loading text from the given path or resource.
   *
   * First tries to read a classpath resource at [filePath]; if not found, reads a file from the
   * local file system at the same path.
   *
   * @param filePath Resource or file system path to the context text.
   */
  constructor(filePath: String) {
    this.contextText = GlobalContext::class.java.getResourceAsStream(filePath)?.reader()?.readText()
      ?: Path(filePath).readText()
  }

  /**
   * Returns the loaded context text.
   *
   * @return The text content read during construction.
   */
  override fun getContextText(): String = contextText
}