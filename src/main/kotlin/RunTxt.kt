import com.fvlaenix.translation.TranslationTxtService
import com.fvlaenix.translation.context.FileGlobalContext
import com.fvlaenix.translation.summarizer.TextModelSummarizer
import com.fvlaenix.translation.textmodel.OpenAIServiceImpl
import com.fvlaenix.translation.translator.TextModelServiceTranslator
import java.nio.file.Path

suspend fun main(args: Array<String>) {
  if (args.isEmpty()) {
    println("Arguments: <path_to_txt>")
  }
  val path = Path.of(args[0])
  val textModel = OpenAIServiceImpl()

  val context = FileGlobalContext("context.txt")

  val translationTxtService = TranslationTxtService(
    path = path,
    translator = TextModelServiceTranslator(
      textModel,
      globalContext = context,
      summarizer = TextModelSummarizer(textModel)
    )
  )
  translationTxtService.translate()
}