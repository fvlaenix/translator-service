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
 * Implementations should handle both simple text translations and structured dialog translations.
 */
interface Translator {
    /**
     * Translates a list of texts. Automatically determines whether to use text or JSON-based translation
     * based on the type of Translation objects.
     */
    suspend fun translate(data: List<Translation>): List<Translation>

    /**
     * Translates a list of texts using JSON format, suitable for structured data like dialogs
     */
    suspend fun translateJson(data: List<Translation>): List<Translation>

    /**
     * Translates a list of simple texts
     */
    suspend fun translateText(data: List<TextTranslation>): List<TextTranslation>
}
