import java.util.Properties

class NamesService(private val properties: Map<String, String>) {

  constructor(name: String) : this(
    NamesService::class.java.getResourceAsStream("/$name")!!.reader().readLines().associate {
      val splitted = it.split("=")
      splitted[0] to splitted[1]
    }
  )

  operator fun get(name: String): String? {
    return properties[name]
  }
}