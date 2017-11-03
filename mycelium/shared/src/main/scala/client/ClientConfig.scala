package mycelium.client

object ClientConfig {
  case class Request(timeoutMillis: Int)
}

case class ClientConfig(
  request: ClientConfig.Request)
