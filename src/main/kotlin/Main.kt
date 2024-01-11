import com.fvlaenix.translation.ChatGPTServer
import com.fvlaenix.translation.TranslationJsonService
import java.nio.file.Path
import kotlin.io.path.Path

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

suspend fun jsonTranslator() {
  val path = Path("<text>")
  val translationJsonService = TranslationJsonService(path,  "gpt-4")
  try {
    translationJsonService.translate()
  } catch (e: Exception) {
    throw Exception(e)
  } finally {
    translationJsonService.write(path)
  }
}

fun runServer() {
  val server = ChatGPTServer(
    port = 50052,
    // model = "gpt-3.5-turbo-0613",
    model = "gpt-4-0613"
  )
  server.start()
  println("Started")
  server.blockUntilShutdown()
}

suspend fun main() {
  jsonTranslator()
}