package mycelium.client

import scala.concurrent.Future

trait WebsocketListener[PickleType] {
  def onConnect(): Unit
  def onMessage(value: PickleType): Future[Unit]
  def onClose(): Unit
}

trait WebsocketConnection[PickleType] {
  private[mycelium] def send(value: WebsocketMessage[PickleType]): Unit

  //TODO improve interface for runnable thing
  private[mycelium] def run(location: String, wsConfig: WebsocketClientConfig, pingMessage: PickleType, listener: WebsocketListener[PickleType]): Unit
}
