package mycelium.client

import monix.reactive.subjects.Subject
import scala.concurrent.duration.FiniteDuration

sealed trait SendType
object SendType {
  private[client] case object Handshake extends SendType
  case object NowOrFail extends SendType
  case object WhenConnected extends SendType
}

case class WebsocketMessage[PickleType](pickled: PickleType, promise: Promise[_], timeout: FiniteDuration)
