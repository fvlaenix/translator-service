package com.fvlaenix.translation

/**
 * Holds core constants and tokens for accessing external services.
 */
class Main

// TODO redo this trash token
/**
 * Access token for OpenAI-compatible services.
 *
 * Loads from the classpath resource `/token.txt` if present; otherwise falls back to the
 * `OPENAI_TOKEN` environment variable. Throws [IllegalStateException] when no token is found.
 */
val TOKEN =
  Main::class.java.getResourceAsStream("/token.txt")?.bufferedReader()?.readText()?.trim()
    ?: System.getenv("OPENAI_TOKEN") ?: throw IllegalStateException("Can't retrieve token")

/**
 * Access token for OpenRouter-compatible services.
 *
 * Loads from the classpath resource `/openrouter-token.txt` if present; otherwise falls back to the
 * `OPENROUTER_TOKEN` environment variable. Throws [IllegalStateException] when no token is found.
 */
val OPENROUTER_TOKEN =
  Main::class.java.getResourceAsStream("/openrouter-token.txt")?.bufferedReader()?.readText()?.trim()
    ?: System.getenv("OPENROUTER_TOKEN") ?: throw IllegalStateException("Can't retrieve OpenRouter token")