import com.fvlaenix.translation.GPTUtil
import com.fvlaenix.translation.Util
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import java.nio.file.Path
import kotlin.io.path.appendLines
import kotlin.io.path.readLines

class TranslationTxtService(
  path: Path,
  private val model: String
) {

  companion object {
    const val COUNT_WORDS = 250
  }

  val text = path.readLines()
  val prompt = TranslationTxtService::class.java.getResourceAsStream("/prompt_txt.txt")!!.bufferedReader().readText()

  suspend fun translate() {
    coroutineScope {
      ensureActive()
      val toTranslateBatches = Util.splitWords(text, COUNT_WORDS)
      val maxAttempts = 3
      for (batch in toTranslateBatches) {
        var result: List<String>? = null
        var attempts = 0
        while (attempts < maxAttempts) {
          attempts++
          result = try {
            GPTUtil.translate(prompt, model, batch)
          } catch (_: GPTUtil.GPTLinesNotMatchException) {
            null
          }
          if (result != null) break
        }
        if (result == null) {
          return@coroutineScope
        }
        Path.of("output.txt").appendLines(result)
      }
    }
  }
}