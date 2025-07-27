import com.fvlaenix.translation.NamesService
import com.fvlaenix.translation.systemdialog.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class DialogProviderTest {

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
    assertEquals(expectedToTranslate, resultParse.result)
    assertEquals(expectedSystem, resultParse.system)

    val resultTranslate = dialogProvider.restore(translateOfExpected, resultParse)
    assertEquals(expectedResult, resultTranslate)
  }

  @Test
  fun `test icon`() = assertTranslationWithNames(
    "\\n<a>\\I[205]Hello...",
    "\"\\I[205]Hello...\"",
    "\"\\I[205]Привет...\"",
    listOf(
      SylphNameDialogProvider.SylphDialog("ф")
    ),
    "\\n<ф>\\I[205]Привет...",
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
    listOf(ElmiaNameDialogProvider.ElmiaDialog("имя")),
    "имя\n\"Привет\""
  )

  @Test
  fun `test double quotes Elmia`() = assertTranslationWithNames(
    "name\n" +
            "「きゃーっ、姫様ーっ！」\n" +
            "「う、美しい……舞うような剣さばきだ……」",
    "「きゃーっ、姫様ーっ！」 「う、美しい……舞うような剣さばきだ……」",
    "\"Типа перевод\"",
    listOf(ElmiaNameDialogProvider.ElmiaDialog("имя")),
    "имя\n\"Типа перевод\""
  )

  @Test
  fun `test bo 10f name`() = assertTranslationWithNames(
      "ROSE\n" + "Abracadabra\nAbracadabra",
      "\"Abracadabra Abracadabra\"",
    "\"Абракадабра абракадабра\"",
    listOf(Bo10FNameDialogProvider.Bo10FDialog("ROSE")),
    "ROSE\nАбракадабра абракадабра"
    )
}
