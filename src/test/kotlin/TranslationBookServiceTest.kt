import com.fvlaenix.translation.NamesService
import junit.framework.TestCase
import org.junit.Test

class TranslationBookServiceTest {

  companion object {
    val CLASSIC_NAMES_SERVICE = NamesService(mapOf("a" to "ф", "b" to "б", "name" to "имя"))
  }

  @Test
  fun `test icon`() {
    val start = TranslationBookService.getStart(CLASSIC_NAMES_SERVICE, "\\n<a>\\I[205]Hello")
    TestCase.assertEquals("\\n<ф>", start.start)
    TestCase.assertEquals("\"\\I[205]Hello\"", start.resultLine)
  }

  @Test
  fun `test remove line separator`() {
    val start = TranslationBookService.getStart(CLASSIC_NAMES_SERVICE, "a\nb")
    TestCase.assertEquals(null, start.start)
    TestCase.assertEquals("a b", start.resultLine)
  }

  @Test
  fun `test remove name of Elmia`() {
    val start = TranslationBookService.getStart(CLASSIC_NAMES_SERVICE, "name\n「Hello...」")
    TestCase.assertEquals("имя\n", start.start)
    TestCase.assertEquals("「Hello...」", start.resultLine)
  }

  @Test
  fun `test double quotes Elmia`() {
    val start = TranslationBookService.getStart(CLASSIC_NAMES_SERVICE, "name\n" +
        "「きゃーっ、姫様ーっ！」\n" +
        "「う、美しい……舞うような剣さばきだ……」")
    TestCase.assertEquals("имя\n", start.start)
    TestCase.assertEquals("「きゃーっ、姫様ーっ！」 「う、美しい……舞うような剣さばきだ……」", start.resultLine)
  }
}