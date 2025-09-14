import com.fvlaenix.text.OpenAIModelProvider
import com.fvlaenix.translation.ChatGPTServer
import java.util.logging.Level
import java.util.logging.LogManager
import java.util.logging.Logger

const val LOGGING_PATH = "/logging.properties"

class RunServer

fun main() {
  try {
    LogManager.getLogManager().readConfiguration(RunServer::class.java.getResourceAsStream(LOGGING_PATH))
  } catch (e: Exception) {
    throw IllegalStateException("Failed while trying to read logs", e)
  }
  val runServerLog = Logger.getLogger(RunServer::class.java.name)
  TOKEN
  runServerLog.log(Level.INFO, "Starting server")
  val server = ChatGPTServer(
    port = 50052,
    // model = "gpt-3.5-turbo-0613",
    model = OpenAIModelProvider.GPT_4_TURBO
  )
  server.start()
  runServerLog.log(Level.INFO, "Started server")
  server.blockUntilShutdown()
}
