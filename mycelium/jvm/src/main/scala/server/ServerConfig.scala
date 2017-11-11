package mycelium.server

import akka.stream.OverflowStrategy

case class ServerConfig(bufferSize: Int, overflowStrategy: OverflowStrategy)
