package com.fvlaenix.translation.systemdialog

class IconDialogProvider : SystemDialogProvider<IconDialogProvider.IconDialog> {
  companion object {
    val REGEX = "^\\\\{2,4}[I|i]\\[\\d+]\\s*".toRegex()
  }

  class IconDialog(private val iconText: String) : SystemDialogProvider.SystemDialog {
    override fun returnTransform(s: String): String {
      return "$iconText$s"
    }
  }

  override fun getSystemDialog(s: String): SystemDialogProvider.SystemDialogProviderResult<IconDialog>? {
    val iconFounder = REGEX.find(s)
    if (iconFounder != null) {
      val group = iconFounder.groups[0]!!
      return SystemDialogProvider.SystemDialogProviderResult(
        IconDialog(group.value),
        s.substring(0, iconFounder.range.first) + s.substring(iconFounder.range.last + 1)
      )
    }
    return null
  }
}