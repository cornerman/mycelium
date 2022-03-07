package mycelium.core.client

import mycelium.core.Cancelable
import scala.concurrent.Future

trait WebsocketListener[PickleType] {
  def onConnect(): Unit
  def onMessage(value: PickleType): Unit
  def onClose(): Unit
}

trait WebsocketConnection[PickleType] {
  def rawSend(value: PickleType): Unit
  def send(value: WebsocketMessage[PickleType]): Unit

  def run(
      location: () => Future[String],
      wsConfig: WebsocketClientConfig,
      pingMessage: PickleType,
      listener: WebsocketListener[PickleType],
  ): Cancelable
}
