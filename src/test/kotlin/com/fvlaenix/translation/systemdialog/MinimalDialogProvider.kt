package com.fvlaenix.translation.systemdialog

class MinimalDialogProvider : SystemDialogProvider<MinimalDialogProvider.MinimalDialog> {
  data class MinimalDialog(val text: String) : SystemDialogProvider.SystemDialog {
    override fun returnTransform(s: String): String {
      val result = if (s.startsWith("\"") && s.endsWith("\"")) {
        s.substring(1, s.length - 1)
      } else {
        s
      }
      return result
    }
  }

  override fun getSystemDialog(s: String): SystemDialogProvider.SystemDialogProviderResult<MinimalDialog>? {
    if (s.startsWith("\"") && s.endsWith("\"")) {
      return null
    }
    val result = SystemDialogProvider.SystemDialogProviderResult(
      systemDialog = MinimalDialog(s),
      result = "\"$s\""
    )
    return result
  }
}
