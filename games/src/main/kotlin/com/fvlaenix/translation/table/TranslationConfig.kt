package com.fvlaenix.translation.table

import com.fvlaenix.text.OpenAIAPIServiceImpl
import com.fvlaenix.text.OpenAIModelProvider
import com.fvlaenix.translation.NamesService
import com.fvlaenix.translation.TOKEN
import com.fvlaenix.translation.systemdialog.ProvidersCollection
import com.fvlaenix.translation.translator.TextModelTranslator
import com.fvlaenix.translation.translator.Translator
import java.nio.file.Path

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