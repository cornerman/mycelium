package mycelium.client

import scala.concurrent.Future

trait WebsocketListener[PickleType] {
  def handshake(): Future[Boolean]
  def onConnect(): Unit
  def onMessage(value: PickleType): Unit
  def onClose(): Unit
}

trait WebsocketConnection[PickleType] {
  private[mycelium] def send(value: WebsocketMessage[PickleType], sendType: SendType): Unit

  //TODO improve interface for runnable thing
  private[mycelium] def run(location: String, wsConfig: WebsocketClientConfig, pingMessage: PickleType, listener: WebsocketListener[PickleType]): Unit
}
