package com.fvlaenix.translation.table

import com.fvlaenix.text.OpenAIAPIServiceImpl
import com.fvlaenix.text.OpenAIModelProvider
import com.fvlaenix.translation.NamesService
import com.fvlaenix.translation.TOKEN
import com.fvlaenix.translation.systemdialog.ProvidersCollection
import com.fvlaenix.translation.translator.TextModelTranslator
import com.fvlaenix.translation.translator.Translator
import java.nio.file.Path

/**
 * Configuration for translation processing.
 *
 * Bundles IO path, naming rules, dialog parsing, and the translator implementation to use.
 *
 * @property path Base directory containing translation books or game files.
 * @property namesService Service for resolving name mappings.
 * @property dialogProvider Provider pipeline for extracting/removing system dialog markers.
 * @property translator Translator used to produce text translations.
 */
data class TranslationConfig(
  val path: Path,
  val namesService: NamesService,
  val dialogProvider: ProvidersCollection = ProvidersCollection.defaultProvidersCollection(namesService),
  val translator: Translator = TextModelTranslator(
    OpenAIAPIServiceImpl(
      openAI = OpenAIModelProvider.createDefaultOpenAiApi(TOKEN),
      modelInfo = OpenAIModelProvider.GPT_4_TURBO
    )
  )
)