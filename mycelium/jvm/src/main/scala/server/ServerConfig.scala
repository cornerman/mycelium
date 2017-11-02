package mycelium.server

import akka.stream.OverflowStrategy

object ServerConfig {
  case class Flow(bufferSize: Int, overflowStrategy: OverflowStrategy)
}

case class ServerConfig(
  flowConfig: ServerConfig.Flow)
