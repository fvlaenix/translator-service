import com.fvlaenix.translation.ChatGPTServer
import java.nio.file.Path

class Main

val TOKEN = Main::class.java.getResourceAsStream("/token.txt")!!.bufferedReader().readText().trim()

const val EXCEL_PATH = "<excel_path>"
const val TXT = "input.txt"

suspend fun txtTranslator() {
  val path = Path.of(TXT)
  val translationTxtService = TranslationTxtService(path, "gpt-4")
  translationTxtService.translate()
}

suspend fun gameTranslator() {
  val path = Path.of(EXCEL_PATH)
  val translationBookService = TranslationBookService(path, 0, 1, "EN", "game_id", "gpt-4")
  try {
    translationBookService.translate()
  } catch (e: Exception) {
    throw Exception(e)
  } finally {
    translationBookService.write(path)
  }
}

fun main() {
  val server = ChatGPTServer(
    port = 50052,
    // model = "gpt-3.5-turbo-0613",
    model = "gpt-4-0613"
  )
  server.start()
  println("Started")
  server.blockUntilShutdown()
}