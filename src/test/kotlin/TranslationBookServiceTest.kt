import com.fvlaenix.translation.NamesService
import com.fvlaenix.translation.systemdialog.*
import junit.framework.TestCase
import org.junit.Test

class TranslationBookServiceTest {

  companion object {
    val CLASSIC_NAMES_SERVICE = NamesService(mapOf("a" to "ф", "b" to "б", "name" to "имя"))
    
    fun defaultDialogProvider(): ProvidersCollection =
      ProvidersCollection.defaultProvidersCollection(CLASSIC_NAMES_SERVICE)
  }

  private fun assertTranslationWithNames(
    test: String,
    expectedToTranslate: String,
    translateOfExpected: String,
    expectedSystem: List<SystemDialogProvider.SystemDialog>,
    expectedResult: String,
    dialogProvider: ProvidersCollection = defaultDialogProvider()
  ) {
    val resultParse = dialogProvider.get(test)
    TestCase.assertEquals(expectedToTranslate, resultParse.result)
    TestCase.assertEquals(expectedSystem, resultParse.system)
    val resultTranslate = dialogProvider.restore(translateOfExpected, resultParse)
    TestCase.assertEquals(expectedResult, resultTranslate)
  }
  
  @Test
  fun `test icon`() = assertTranslationWithNames(
    "\\n<a>\\I[205]Hello",
    "\"\\I[205]Hello\"",
    "\"\\I[205]Привет\"",
    listOf(
      SylphDialogProvider.SylphDialog("ф")
    ),
    "\\n<ф>\\I[205]Привет",
  )

  @Test
  fun `test remove line separator`() = assertTranslationWithNames(
    "a\nb",
    "a b",
    "а б",
    listOf(),
    "а б"
  )

  @Test
  fun `test remove name of Elmia`() = assertTranslationWithNames(
    "name\n「Hello...」",
    "「Hello...」",
    "\"Привет\"",
    listOf(ElmiaDialogProvider.ElmiaDialog("имя")),
    "имя\n\"Привет\""
  )

  @Test
  fun `test double quotes Elmia`() = assertTranslationWithNames(
    "name\n" +
            "「きゃーっ、姫様ーっ！」\n" +
            "「う、美しい……舞うような剣さばきだ……」",
    "「きゃーっ、姫様ーっ！」 「う、美しい……舞うような剣さばきだ……」",
    "\"Типа перевод\"",
    listOf(ElmiaDialogProvider.ElmiaDialog("имя")),
    "имя\n\"Типа перевод\""
  )
  
  @Test
  fun `test bo 10f name`() = assertTranslationWithNames(
      "ROSE\n" + "Abracadabra\nAbracadabra",
      "\"Abracadabra Abracadabra\"",
    "\"Абракадабра абракадабра\"",
    listOf(Bo10FDialogProvider.Bo10FDialog("ROSE")),
    "ROSE\nАбракадабра абракадабра"
    )
}