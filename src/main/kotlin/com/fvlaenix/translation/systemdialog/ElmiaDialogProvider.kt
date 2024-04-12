package com.fvlaenix.translation.systemdialog

import com.fvlaenix.translation.NamesService
import com.fvlaenix.translation.systemdialog.SystemDialogUtils.removeQuotesIfNeeded

class ElmiaDialogProvider(private val namesService: NamesService) : SystemDialogProvider<ElmiaDialogProvider.ElmiaDialog> {
  data class ElmiaDialog(private val name: String) : SystemDialogProvider.SystemDialog {
    override fun returnTransform(s: String): String {
      return "$name\n$s"
    }
  }
  
  override fun getSystemDialog(s: String): SystemDialogProvider.SystemDialogProviderResult<ElmiaDialog>? {
    val positionOfOpen = s.indexOfFirst { it == '「' }
    val positionOfSplit = s.indexOfFirst { it == '\n' }
    if (positionOfSplit + 1 == positionOfOpen && positionOfSplit != -1) {
      val name = s.split("\n")[0]
      val replacement = namesService[name]
      val isLastSymbolQuot = s.indexOfLast { it == '」' } == s.length - 1
      return SystemDialogProvider.SystemDialogProviderResult(
        ElmiaDialog(replacement),
        "「" + s.substring(positionOfOpen + 1, s.length - if (isLastSymbolQuot) 1 else 0).trim() + "」"
      )
    }
    return null
  }
}