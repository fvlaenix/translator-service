package com.fvlaenix.translation.translator

/**
 * A fake implementation of the Translator interface for testing purposes.
 * Uses predefined translations instead of making actual API calls.
 */
class FakeTranslator : Translator {
  /**
   * Predefined translations for simple text messages.
   * Maps original text to its translation.
   */
  private val textTranslations = mutableMapOf(
    "Hello world" to "Привет мир",
    "How are you?" to "Как дела?",
    "System message" to "Системное сообщение",
    "World" to "Мир"
  )

  /**
   * Predefined translations for dialog messages.
   * Maps original text to its translation.
   */
  private val dialogTranslations = mutableMapOf(
    "Hello Bob!" to "Привет, Боб!",
    "Hi Alice, how are you?" to "Привет, Алиса, как дела?",
    "Hello there!" to "Привет всем!"
  )

  /**
   * Adds a new text translation pair to the translator.
   * @param original The original text
   * @param translation The translated text
   */
  fun addTextTranslation(original: String, translation: String) {
    textTranslations[original] = translation
  }

  /**
   * Adds a new dialog translation pair to the translator.
   * @param original The original text
   * @param translation The translated text
   */
  fun addDialogTranslation(original: String, translation: String) {
    dialogTranslations[original] = translation
  }

  override suspend fun translate(data: List<Translation>): List<Translation> {
    return if (data.all { it is TextTranslation }) {
      translateText(data.filterIsInstance<TextTranslation>())
    } else {
      translateJson(data)
    }
  }

  override suspend fun translateJson(data: List<Translation>): List<Translation> {
    return data.map { translation ->
      when (translation) {
        is TextTranslation -> {
          val result = translation.translation ?: textTranslations[translation.original]
          TextTranslation(
            original = translation.original,
            translation = result
          )
        }

        is DialogTranslation -> {
          val result = translation.translation ?: dialogTranslations[translation.original]
          DialogTranslation(
            name = translation.name,
            original = translation.original,
            translation = result
          )
        }
      }
    }
  }

  override suspend fun translateText(data: List<TextTranslation>): List<TextTranslation> {
    return data.map { translation ->
      val result = translation.translation ?: textTranslations[translation.original]
      TextTranslation(
        original = translation.original,
        translation = result
      )
    }
  }

  /**
   * Clears all translations from the fake translator.
   * Useful for testing cache behavior.
   */
  fun clear() {
    textTranslations.clear()
    dialogTranslations.clear()
  }
}
