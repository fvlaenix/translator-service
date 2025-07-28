package com.fvlaenix.translation.context

import kotlin.io.path.Path
import kotlin.io.path.readText

class GlobalContext : Context {
  private val contextText: String

  constructor(filePath: String) {
    this.contextText = GlobalContext::class.java.getResourceAsStream(filePath)?.reader()?.readText()
      ?: Path(filePath).readText()
  }

  override fun getContextText(): String = contextText
}