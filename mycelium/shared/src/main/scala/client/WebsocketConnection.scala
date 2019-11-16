package mycelium.client

import monix.execution.Cancelable
import monix.reactive.{Observable, Observer}

import scala.concurrent.Future

case class ReactiveWebsocketConnection[PickleType](
  connected: Observable[Boolean],
  incomingMessages: Observable[Future[Option[PickleType]]],
  outgoingMessages: Observer[PickleType],
  cancelable: Cancelable
)

trait WebsocketConnection[PickleType] {
  private[mycelium] def run(
    location: String,
    wsConfig: WebsocketClientConfig,
    pingMessage: PickleType): ReactiveWebsocketConnection[PickleType]
}
