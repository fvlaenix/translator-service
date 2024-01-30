import com.fvlaenix.translation.TranslationJsonService
import java.nio.file.Path
import kotlin.io.path.Path

class Main

val TOKEN =
  Main::class.java.getResourceAsStream("/token.txt")?.bufferedReader()?.readText()?.trim() ?:
  System.getenv("openai.token") ?:
  throw IllegalStateException("Can't retrieve token")

suspend fun gameTranslator(excelPath: String) {
  val path = Path.of(excelPath)
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