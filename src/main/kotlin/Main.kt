class Main

val TOKEN =
  Main::class.java.getResourceAsStream("/token.txt")?.bufferedReader()?.readText()?.trim() ?:
  System.getenv("OPENAI_TOKEN") ?:
  throw IllegalStateException("Can't retrieve token")
