package com.fvlaenix.translation.context

class GlobalContextCollection(private val contexts: List<GlobalContext>) : GlobalContext {
  override fun getContextText(): String {
    return contexts.joinToString("\n\n") { it.getContextText() }
  }
}