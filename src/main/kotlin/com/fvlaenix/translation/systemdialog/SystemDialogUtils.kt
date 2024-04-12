package com.fvlaenix.translation.systemdialog

object SystemDialogUtils {
  fun removeQuotesIfNeeded(s: String): String {
    if (s.startsWith("\"") && s.endsWith("\"")) {
      return s.substring(1, s.length - 1)
    }
    if (s.startsWith("「") && s.endsWith("」")) {
      return s.substring(1, s.length - 1)
    }
    return s
  }
}