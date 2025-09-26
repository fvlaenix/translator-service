package com.fvlaenix.translation.systemdialog

/**
 * Defines a pluggable detector/stripper for game-specific system dialog markup.
 *
 * Implementations parse a raw line, optionally recognise a system dialog wrapper
 * (speaker names, icons, brackets, etc.), strip it from the text to be translated,
 * and remember enough information to restore the wrapper after translation.
 *
 * @param TYPE Concrete dialog descriptor returned when a wrapper is recognised.
 */
interface SystemDialogProvider<TYPE : SystemDialogProvider.SystemDialog> {
  /**
   * Holds the parsed system dialog wrapper and the remaining text to translate.
   *
   * @property systemDialog Descriptor capable of restoring the original wrapper.
   * @property result Input string with the wrapper removed.
   */
  data class SystemDialogProviderResult<TYPE : SystemDialog>(
    val systemDialog: TYPE,
    val result: String
  )

  /**
   * Describes a recognised system dialog wrapper with logic to restore it.
   */
  interface SystemDialog {
    /**
     * Restores the original wrapper around a translated text.
     *
     * @param s Translated text without the wrapper.
     * @return Text with the wrapper reapplied.
     */
    fun returnTransform(s: String): String
  }

  /**
   * Parses [s] and returns a recognised dialog wrapper with the cleaned text.
   *
   * Implementations should return `null` when no wrapper is detected.
   *
   * @param s Raw input line possibly containing a system dialog wrapper.
   * @return Parsed wrapper and stripped text, or `null` if not recognised.
   */
  fun getSystemDialog(s: String): SystemDialogProviderResult<TYPE>?
}