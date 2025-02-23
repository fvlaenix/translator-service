package com.fvlaenix.translation.systemdialog

import com.fvlaenix.translation.NamesService

class ProvidersCollection(private val providers: List<SystemDialogProvider<*>>) {
  fun get(s: String): ProvidersResult {
    var current = s
    var isChanged = true
    val systemDialogs = mutableListOf<SystemDialogProvider.SystemDialog>()
    while (isChanged) {
      isChanged = false
      providers.forEach { provider ->
        val dialogResult = provider.getSystemDialog(current)
        if (dialogResult != null) {
          current = dialogResult.result
          systemDialogs.add(dialogResult.systemDialog)
          isChanged = true
        }
      }
    }
    if (current.contains("[\r\n]".toRegex())) {
      current = current.replace("\r", "")
      current = current.replace("\n", " ")
    }
    return ProvidersResult(current, systemDialogs)
  }

  fun restore(s: String, providersResult: ProvidersResult): String {
    var current = s
    providersResult.system.asReversed().forEach { systemDialog ->
      current = systemDialog.returnTransform(current)
    }
    return current
  }

  companion object {
    fun defaultProvidersCollection(namesService: NamesService): ProvidersCollection =
      ProvidersCollection(listOf(
        ElmiaNameDialogProvider(namesService),
        SylphNameDialogProvider(namesService),
        Bo10FNameDialogProvider(),
        IconDialogProvider()
      ))
  }

  class ProvidersResult(
    val result: String,
    val system: List<SystemDialogProvider.SystemDialog>
  )

}
