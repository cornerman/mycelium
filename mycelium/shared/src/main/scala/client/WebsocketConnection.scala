package mycelium.client

import monix.reactive.Observable
import monix.reactive.subjects.{ConcurrentSubject, PublishSubject}

import scala.concurrent.Future

trait WebsocketListener[PickleType] {
  def onConnect(): Unit
  def onMessage(value: Future[Option[PickleType]]): Unit
  def onClose(): Unit
}

trait WebsocketConnection[PickleType] {
  private[mycelium] def run(
    location: String,
    wsConfig: WebsocketClientConfig,
    pingMessage: PickleType,
    messageSubject: Observable[WebsocketMessage[PickleType]],
    listener: WebsocketListener[PickleType]): Unit
}
