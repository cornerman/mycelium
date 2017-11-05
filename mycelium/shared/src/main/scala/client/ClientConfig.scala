package mycelium.client

object ClientConfig {
  case class Request(timeoutMillis: Int)
  case class Ping(timeoutMillis: Int)
  case class Reconnect(minimumBackoffMillis: Int)
}

case class ClientConfig(
  request: ClientConfig.Request,
  ping: Option[ClientConfig.Ping] = None,
  reconnect: Option[ClientConfig.Reconnect] = None)
