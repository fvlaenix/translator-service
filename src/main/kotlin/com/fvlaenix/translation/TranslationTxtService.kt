import com.fvlaenix.translation.GPTUtil
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
      val toTranslateBatches = mutableListOf<List<String>>()
      var buffer = mutableListOf<String>()
      for (line in text) {
        if (buffer.sumOf { it.split("\\s+".toRegex()).count() } + line.split("\\s+".toRegex()).count() > COUNT_WORDS) {
          toTranslateBatches.add(buffer)
          buffer = mutableListOf()
        }
        buffer.add(line)
      }
      if (buffer.isNotEmpty()) {
        toTranslateBatches.add(buffer)
      }
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