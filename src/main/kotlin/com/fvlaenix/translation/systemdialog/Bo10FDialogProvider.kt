package com.fvlaenix.translation.systemdialog

import com.fvlaenix.translation.systemdialog.SystemDialogUtils.removeQuotesIfNeeded

class Bo10FDialogProvider : SystemDialogProvider<Bo10FDialogProvider.Bo10FDialog> {
  companion object {
    val REGEX = "^[A-Z]{3,}\\s*".toRegex()
  }
  
  data class Bo10FDialog(val name: String) : SystemDialogProvider.SystemDialog {
    override fun returnTransform(s: String): String {
      return "$name\n${removeQuotesIfNeeded(s)}"
    }
  }

  override fun getSystemDialog(s: String): SystemDialogProvider.SystemDialogProviderResult<Bo10FDialog>? {
    val nameHighFounder = REGEX.find(s)
    if (nameHighFounder != null) {
      val group = nameHighFounder.groups[0]!!
      return SystemDialogProvider.SystemDialogProviderResult(
        Bo10FDialog(group.value.trim()),
        "\"" + s.substring(0, nameHighFounder.range.first) + s.substring(nameHighFounder.range.last + 1) + "\""
      )
    }
    return null
  }
}