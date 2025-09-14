package com.fvlaenix.translation

import com.fvlaenix.text.ModelInfo
import io.grpc.Server
import io.grpc.ServerBuilder

class ChatGPTServer(port: Int, model: ModelInfo) {
  private val server: Server = ServerBuilder
    .forPort(port)
    .addService(ChatGPTService(model))
    .maxInboundMessageSize(50 * 1024 * 1024) // 50mb
    .build()

  fun start() {
    server.start()
    Runtime.getRuntime().addShutdownHook(
      Thread {
        this@ChatGPTServer.stop()
      }
    )
  }

  private fun stop() {
    server.shutdown()
  }

  fun blockUntilShutdown() {
    server.awaitTermination()
  }
}