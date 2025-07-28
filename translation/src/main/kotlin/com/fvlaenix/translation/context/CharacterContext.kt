package com.fvlaenix.translation.context

import com.fvlaenix.translation.translator.DialogTranslation
import com.fvlaenix.translation.translator.Translation
import kotlin.io.path.Path
import kotlin.io.path.readText

class CharacterContext : Context {
  val characterName: String
  private val contextText: String

  constructor(characterName: String, filePath: String) {
    this.characterName = characterName
    this.contextText = CharacterContext::class.java.getResourceAsStream(filePath)?.reader()?.readText()
      ?: Path(filePath).readText()
  }

  override fun getContextText(): String = contextText

  fun shouldIncludeContext(translations: List<Translation>): Boolean {
    return translations.any { translation ->
      translation is DialogTranslation && translation.name == characterName
    }
  }
}