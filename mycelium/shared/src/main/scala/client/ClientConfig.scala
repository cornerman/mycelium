package mycelium.client

object ClientConfig {
  case class Request(timeoutMillis: Int)
  case class Ping(timeoutMillis: Int)
  case class Reconnect(minimumBackoffMillis: Int)
}

case class ClientConfig(
  requestConfig: ClientConfig.Request,
  pingConfig: Option[ClientConfig.Ping] = None,
  reconnectConfig: Option[ClientConfig.Reconnect] = None)
