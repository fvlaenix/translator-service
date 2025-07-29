package com.fvlaenix.translation

class Main

val TOKEN =
  Main::class.java.getResourceAsStream("/token.txt")?.bufferedReader()?.readText()?.trim()
    ?: System.getenv("OPENAI_TOKEN") ?: throw IllegalStateException("Can't retrieve token")

val OPENROUTER_TOKEN =
  Main::class.java.getResourceAsStream("/openrouter-token.txt")?.bufferedReader()?.readText()?.trim()
    ?: System.getenv("OPENROUTER_TOKEN") ?: throw IllegalStateException("Can't retrieve OpenRouter token")