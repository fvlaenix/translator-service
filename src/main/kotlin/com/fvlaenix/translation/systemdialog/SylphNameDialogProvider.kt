package com.fvlaenix.translation.systemdialog

import com.fvlaenix.translation.NamesService
import com.fvlaenix.translation.systemdialog.SystemDialogUtils.removeQuotesIfNeeded

class SylphNameDialogProvider(private val namesService: NamesService) : SystemDialogProvider<SylphNameDialogProvider.SylphDialog> {
  companion object {
    val REGEX = "\\\\n<([^>]+)>".toRegex()
  }
  
  data class SylphDialog(val name: String) : SystemDialogProvider.SystemDialog {
    override fun returnTransform(s: String): String {
      return "\\n<$name>${removeQuotesIfNeeded(s)}"
    }
  }

  override fun getSystemDialog(s: String): SystemDialogProvider.SystemDialogProviderResult<SylphDialog>? {
    val nameFounder = REGEX.find(s)
    if (nameFounder != null) {
      val group = nameFounder.groups[1]!!
      val replacement = namesService[group.value]
      return SystemDialogProvider.SystemDialogProviderResult(
        SylphDialog(replacement),
        "\"" + s.substring(0, nameFounder.range.first) + s.substring(nameFounder.range.last + 1) + "\""
      )
    }
    return null
  }
}