package mycelium.core.client

import mycelium.core.Cancelable

trait WebsocketListener[PickleType] {
  def onConnect(): Unit
  def onMessage(value: PickleType): Unit
  def onClose(): Unit
}

trait WebsocketConnection[PickleType] {
  def send(value: WebsocketMessage[PickleType]): Unit

  def run(
      location: () => String,
      wsConfig: WebsocketClientConfig,
      pingMessage: PickleType,
      listener: WebsocketListener[PickleType]
  ): Cancelable
}
