package com.fvlaenix.translation.context

import kotlin.io.path.Path
import kotlin.io.path.readText

class FileGlobalContext(filePath: String) : GlobalContext {
  // Loading context text during object creation
  private val contextText: String = FileGlobalContext::class.java.getResourceAsStream(filePath)?.reader()?.readText()
    ?: Path(filePath).readText()

  override fun getContextText(): String = contextText
}
