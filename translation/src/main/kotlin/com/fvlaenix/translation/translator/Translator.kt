package com.fvlaenix.translation.translator

/**
 * Base class for all translation types.
 * @property original The original text to be translated
 * @property translation The translated text, null if not yet translated
 */
sealed class Translation(
  val original: String,
  var translation: String?
)

/**
 * Represents a simple text translation without any additional context.
 * @param original The original text to be translated
 * @param translation The translated text, null if not yet translated
 */
class TextTranslation(
  original: String,
  translation: String? = null
) : Translation(original, translation)

/**
 * Represents a dialog translation with a speaker name.
 * @property name The name of the speaker in the dialog
 * @param original The original text to be translated
 * @param translation The translated text, null if not yet translated
 */
class DialogTranslation(
  val name: String,
  original: String,
  translation: String? = null
) : Translation(original, translation)

/**
 * Interface for text translation services.
 */
interface Translator {
  /**
   * Translates a list of texts.
   * @param data List of translations to translate
   * @return List of translated results
   */
  suspend fun translate(data: List<Translation>): List<Translation>
}
