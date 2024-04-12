package com.fvlaenix.translation.systemdialog

interface SystemDialogProvider<TYPE: SystemDialogProvider.SystemDialog> {
  data class SystemDialogProviderResult<TYPE: SystemDialog>(
    val systemDialog: TYPE,
    val result: String
  )

  interface SystemDialog {
    fun returnTransform(s: String): String
  }
  
  fun getSystemDialog(s: String): SystemDialogProviderResult<TYPE>?
}