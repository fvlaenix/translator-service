import com.fvlaenix.translation.ChatGPTServer

fun main(args: Array<String>) {
  val server = ChatGPTServer(
    port = 50052,
    // model = "gpt-3.5-turbo-0613",
    model = "gpt-4-0613"
  )
  server.start()
  println("Started")
  server.blockUntilShutdown()
}