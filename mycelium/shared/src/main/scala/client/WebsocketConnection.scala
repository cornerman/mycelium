package mycelium.client

trait WebsocketListener[PickleType] {
  def onConnect(): Unit
  def onMessage(value: PickleType): Unit
  def onClose(): Unit
}

trait WebsocketConnection[PickleType] {
  private[mycelium] def send(value: WebsocketMessage[PickleType]): Unit

  //TODO improve interface for runnable thing
  private[mycelium] def run(location: String, wsConfig: WebsocketClientConfig, pingMessage: PickleType, listener: WebsocketListener[PickleType]): Unit
}
