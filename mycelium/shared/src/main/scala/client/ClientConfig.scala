package mycelium.client

import scala.concurrent.duration._

case class WebsocketConfig(minReconnectDelay: FiniteDuration = 60.seconds, maxReconnectDelay: FiniteDuration = 1.seconds, delayReconnectFactor: Double = 1.3, connectingTimeout: FiniteDuration = 5.seconds, pingInterval: FiniteDuration = 45.seconds)

case class ClientConfig(requestTimeout: FiniteDuration, websocketConfig: WebsocketConfig = WebsocketConfig())
