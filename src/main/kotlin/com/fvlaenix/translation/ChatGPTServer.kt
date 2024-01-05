package com.fvlaenix.translation

import io.grpc.Server
import io.grpc.ServerBuilder

class ChatGPTServer(port: Int, model: String) {
  private val server: Server = ServerBuilder.forPort(port).addService(ChatGPTService(model)).build()

  fun start() {
    server.start()
    Runtime.getRuntime().addShutdownHook(
      Thread {
        this@ChatGPTServer.stop()
      }
    )
  }

  fun stop() {
    server.shutdown()
  }

  fun blockUntilShutdown() {
    server.awaitTermination()
  }
}