package com.fvlaenix.translation.translator

import TOKEN
import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatRole
import com.aallam.openai.api.http.Timeout
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.time.Duration.Companion.seconds

/**
 * Implementation of the Translator interface using OpenAI's GPT model.
 * Supports both text and dialog translations using different prompts for each type.
 *
 * @property model The GPT model to use for translations (default: "gpt-4-turbo")
 * @property jsonPrompt The prompt template for JSON-based dialog translations
 * @property textPrompt The prompt template for simple text translations
 */
class GPTTranslator(
    private val model: String = "gpt-4-turbo",
    private val jsonPrompt: String = GPTTranslator::class.java.getResource("/jsonPrompt.txt")!!.readText(),
    private val textPrompt: String = GPTTranslator::class.java.getResource("/prompt_EN.txt")!!.readText()
) : Translator {

    /**
     * Exception thrown when the number of translated lines doesn't match the input.
     */
    class GPTLinesNotMatchException(expected: Int, found: Int) : 
        Exception("Count of lines not match in translation. Expected: $expected, found: $found")

    /**
     * Performs a raw translation request to the GPT API.
     * 
     * @param prompt The system prompt to use for translation
     * @param lines The list of text lines to translate
     * @return List of translated lines
     * @throws GPTLinesNotMatchException if the number of translated lines doesn't match the input
     */
    private suspend fun translateRaw(prompt: String, lines: List<String>): List<String> {
        check(model == "gpt-4" || model == "gpt-4-turbo") { "Can't use old API with anything except gpt-4. Found: $model" }
        val emptyLines = mutableSetOf<Int>()
        val filteredLines = lines.filterIndexed { index, s ->
            if (s.isBlank()) {
                emptyLines.add(index)
                false
            } else {
                true
            }
        }
        val result = mutableListOf<String>()
        OpenAI(
            token = TOKEN,
            timeout = Timeout(socket = 180.seconds)
        ).use { openAI ->
            val systemMessage = ChatMessage(
                role = ChatRole.System,
                content = prompt
            )
            val translateMessage = ChatMessage(
                role = ChatRole.User,
                content = filteredLines.joinToString(separator = "\n")
            )

            val chatCompletionRequest = ChatCompletionRequest(
                model = ModelId(model),
                messages = listOf(systemMessage, translateMessage)
            )

            val completion = openAI.chatCompletion(chatCompletionRequest)
            completion.choices.forEach { choice ->
                result.addAll(choice.message.content!!.split("\n").map { it.trim() })
            }
        }
        result.removeAll { it.isBlank() }
        if (result.size != filteredLines.size) {
            throw GPTLinesNotMatchException(lines.size, result.size)
        }
        emptyLines.sorted().forEach { result.add(it, "") }
        return result
    }

    /**
     * Attempts to translate text with multiple retries in case of line count mismatch.
     * 
     * @param prompt The system prompt to use for translation
     * @param lines The list of text lines to translate
     * @param attempts Maximum number of retry attempts (default: 5)
     * @return List of translated lines, or null if all attempts failed
     */
    private suspend fun translateWithRetries(prompt: String, lines: List<String>, attempts: Int = 5): List<String>? {
        var tries = 0
        while (tries < attempts) {
            try {
                return translateRaw(prompt, lines)
            } catch (ignored: GPTLinesNotMatchException) {}
            tries++
        }
        return null
    }

    /**
     * Exception thrown when the translation result is invalid or cannot be processed.
     */
    class IncorrectTranslation(message: String?) : Exception(message)

    @Suppress("UNCHECKED_CAST")
    override suspend fun translate(data: List<Translation>): List<Translation> {
        return if (data.all { it is TextTranslation }) {
            translateText(data.filterIsInstance<TextTranslation>())
        } else {
            translateJson(data)
        }
    }

    override suspend fun translateJson(data: List<Translation>): List<Translation> {
        val untranslated = data.filter { it.translation == null }
        if (untranslated.isEmpty()) return data

        val jsonData = untranslated.map { translation ->
            when (translation) {
                is TextTranslation -> """{"text": "${translation.original}"}"""
                is DialogTranslation -> """{"name": "${translation.name}", "text": "${translation.original}"}"""
            }
        }.joinToString(",\n", "[\n", "\n]")

        val translatedJson = translateWithRetries(jsonPrompt, listOf(jsonData))
            ?: throw IllegalStateException("Failed to translate after multiple attempts")

        if (translatedJson.size != 1) {
            throw IncorrectTranslation("Expected single JSON array response")
        }

        val translations = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            .decodeFromString<List<kotlinx.serialization.json.JsonObject>>(translatedJson[0])

        if (translations.size != untranslated.size) {
            throw IncorrectTranslation("Response size mismatch")
        }

        var translationIndex = 0
        return data.map { original ->
            if (original.translation != null) {
                original
            } else {
                val translation = translations[translationIndex++]
                when (original) {
                    is TextTranslation -> TextTranslation(
                        original = original.original,
                        translation = translation["text"]?.toString()?.trim('"')
                    )
                    is DialogTranslation -> DialogTranslation(
                        name = translation["name"]?.toString()?.trim('"') ?: original.name,
                        original = original.original,
                        translation = translation["text"]?.toString()?.trim('"')
                    )
                }
            }
        }
    }

    override suspend fun translateText(data: List<TextTranslation>): List<TextTranslation> {
        val untranslated = data.filter { it.translation == null }
        if (untranslated.isEmpty()) return data

        val translations = translateWithRetries(textPrompt, untranslated.map { it.original })
            ?: throw IllegalStateException("Failed to translate after multiple attempts")

        if (translations.size != untranslated.size) {
            throw IncorrectTranslation("Response size mismatch")
        }

        var translationIndex = 0
        return data.map { original ->
            if (original.translation != null) {
                original
            } else {
                TextTranslation(
                    original = original.original,
                    translation = translations[translationIndex++]
                )
            }
        }
    }

    companion object {
        fun create(): GPTTranslator = GPTTranslator()
    }
}
