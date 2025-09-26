package com.fvlaenix.translation.context

import com.fvlaenix.translation.translator.DialogTranslation
import com.fvlaenix.translation.translator.Translation
import kotlin.io.path.Path
import kotlin.io.path.readText

/**
 * Provides per‑character context text to guide translations.
 *
 * Loads context text from the classpath resource or file system, and can be conditionally
 * included based on whether a batch references the character.
 */
class CharacterContext : Context {
  /**
   * Name of the character whose context this instance represents.
   */
  val characterName: String
  private val contextText: String

  constructor(characterName: String, filePath: String) {
    this.characterName = characterName
    this.contextText = CharacterContext::class.java.getResourceAsStream(filePath)?.reader()?.readText()
      ?: Path(filePath).readText()
  }

  /**
   * Returns the context text associated with [characterName].
   *
   * @return The full context string loaded during construction.
   */
  override fun getContextText(): String = contextText

  /**
   * Returns whether this context should be included for the given batch.
   *
   * Includes the context when any [DialogTranslation] belongs to [characterName].
   *
   * @param translations Batch to inspect.
   * @return True if at least one item targets this character; false otherwise.
   */
  fun shouldIncludeContext(translations: List<Translation>): Boolean {
    return translations.any { translation ->
      translation is DialogTranslation && translation.name == characterName
    }
  }
}