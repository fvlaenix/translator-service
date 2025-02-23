package com.fvlaenix.translation.translator

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class GPTTranslatorTest {
    private val translator = FakeTranslator()

    @Test
    fun `test adding and translating new text translations`() = runBlocking {
        // Add new translation
        translator.addTextTranslation("Good morning", "Доброе утро")

        val texts = listOf(
            TextTranslation("Good morning")
        )
        val results = translator.translate(texts)
        assertEquals("Доброе утро", results[0].translation)
    }

    @Test
    fun `test adding and translating new dialog translations`() = runBlocking {
        // Add new translation
        translator.addDialogTranslation("See you later!", "До встречи!")

        val dialogs = listOf(
            DialogTranslation(name = "Alice", original = "See you later!")
        )
        val results = translator.translate(dialogs)
        assertEquals("До встречи!", results[0].translation)
    }

    @Test
    fun `test text translation`() = runBlocking {
        val texts = listOf(
            TextTranslation("Hello world"),
            TextTranslation("How are you?")
        )
        val results = translator.translate(texts)
        assertEquals(2, results.size)
        assertEquals("Привет мир", results[0].translation)
        assertEquals("Как дела?", results[1].translation)
    }

    @Test
    fun `test dialog translation`() = runBlocking {
        val dialogs = listOf(
            DialogTranslation("Alice", "Hello Bob!"),
            DialogTranslation("Bob", "Hi Alice, how are you?")
        )
        val results = translator.translate(dialogs)
        assertEquals(2, results.size)
        results.forEachIndexed { index, translation ->
            translation as DialogTranslation
            assertEquals(dialogs[index].name, translation.name, "Names should match")
        }
        assertEquals("Привет, Боб!", results[0].translation)
        assertEquals("Привет, Алиса, как дела?", results[1].translation)
    }

    @Test
    fun `test mixed translation`() = runBlocking {
        val mixed = listOf(
            TextTranslation("System message"),
            DialogTranslation("Alice", "Hello there!")
        )
        val results = translator.translate(mixed)
        assertEquals(2, results.size)
        assertEquals("Системное сообщение", results[0].translation)
        results[1].let { translation ->
            translation as DialogTranslation
            assertEquals("Alice", translation.name)
            assertEquals("Привет всем!", translation.translation)
        }
    }

    @Test
    fun `test empty translation list`() = runBlocking {
        val results = translator.translate(emptyList<Translation>())
        assertEquals(0, results.size)
    }

    @Test
    fun `test already translated items`() = runBlocking {
        val texts = listOf(
            TextTranslation("Hello", "Привет"),
            TextTranslation("World", null),
            TextTranslation("Test", "Тест")
        )
        val results = translator.translate(texts)
        assertEquals(3, results.size)
        assertEquals("Привет", results[0].translation)
        assertEquals("Мир", results[1].translation)
        assertEquals("Тест", results[2].translation)
    }
}
