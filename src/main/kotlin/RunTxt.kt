import java.nio.file.Path

suspend fun main(args: Array<String>) {
  if (args.isEmpty()) {
    println("Arguments: <path_to_txt>")
  }
  val path = Path.of(args[0])
  val translationTxtService = TranslationTxtService(path, "gpt-4")
  translationTxtService.translate()
}