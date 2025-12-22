package com.fvlaenix.translation.translator

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DialogTranslationTest : AbstractTranslatorTest() {

  private lateinit var jsonMockService: MockTextModelService
  private lateinit var dialogTranslator: JsonModelTranslator

  @BeforeEach
  fun setUp() {
    jsonMockService = MockTextModelService(100)
    dialogTranslator = JsonModelTranslator(
      textModelService = jsonMockService,
      jsonPrompt = "Translate the following JSON array of dialog entries."
    )
  }

  @Test
  fun `test dialog translation with no splitting needed`() = runBlocking {
    val translations = listOf(
      DialogTranslation("Alice", "Hello, how are you?"),
      DialogTranslation("Bob", "I'm fine, thank you!")
    )

    val result = dialogTranslator.translate(translations)

    assertEquals(2, result.size)
    assertTrue(result[0].translation!!.contains("translated: Hello"))
    assertTrue(result[1].translation!!.contains("translated: I'm fine"))
  }

  @Test
  fun `test dialog translation with splitting`() = runBlocking {
    val longDialog = "This is a dialog token limit. " +
        "We need to ensure multiple parts. " +
        "Then it should final result."
    val translations = listOf(
      DialogTranslation("Character", longDialog)
    )

    val result = dialogTranslator.translate(translations)

    assertEquals(1, result.size)
    assertTrue(result[0].translation!!.contains("translated:"))
    assertTrue(result[0].translation!!.contains("dialog"))
    assertTrue(result[0].translation!!.contains("limit"))
  }

  @Test
  fun `test mixed translation types`() = runBlocking {
    val translations = listOf(
      DialogTranslation("Narrator", "Narrator text"),
      DialogTranslation("Alice", "Hello there!"),
      DialogTranslation("Narrator", "More narrator text")
    )

    val result = dialogTranslator.translate(translations)

    assertEquals(3, result.size)
    assertTrue(result[0].translation!!.contains("translated:"))
    assertTrue(result[1].translation!!.contains("translated:"))
    assertTrue(result[2].translation!!.contains("translated:"))
  }

  @Test
  fun `test very big context`() = runBlocking {
    val richContextModel = MockTextModelService(800)
    val richContextTranslator = JsonModelTranslator(
      textModelService = richContextModel,
      jsonPrompt = "Translate the following JSON array of dialog entries."
    )
    val translations = listOf(
      DialogTranslation("Narrator", "Narrator text"),
      DialogTranslation("Alice", "Hello there!"),
      DialogTranslation("Alice", "Hello there!"),
      DialogTranslation("Alice", "Hello there!"),
      DialogTranslation("Alice", "Hello there!"),
      DialogTranslation("Alice", "Hello there!"),
      DialogTranslation("Alice", "Hello there!"),
      DialogTranslation("Alice", "Hello there!"),
      DialogTranslation("Alice", "Hello there!"),
      DialogTranslation("Narrator", "More narrator text")
    )

    val result = richContextTranslator.translate(translations)
    assertEquals(10, result.size)
    assertEquals(1, richContextModel.countOfRequests)
  }
}