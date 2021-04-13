package mycelium.core.client

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.Promise

sealed trait WebsocketMessage[PickleType] {
  val pickled: PickleType
  val promise: Promise[_]
  val timeout: FiniteDuration
}
object WebsocketMessage {
  case class Buffered[PickleType](pickled: PickleType, promise: Promise[_], timeout: FiniteDuration, priority: SendType.Priority) extends WebsocketMessage[PickleType]
  case class Direct[PickleType](pickled: PickleType, promise: Promise[_], timeout: FiniteDuration) extends WebsocketMessage[PickleType]
}

sealed trait SendType
object SendType {
  type Priority = Int

  case object NowOrFail extends SendType
  case class WhenConnected(priority: Priority) extends SendType
  object WhenConnected extends WhenConnected(0) {
    def lowPriority = WhenConnected(Integer.MIN_VALUE)
    def highPriority = WhenConnected(Integer.MAX_VALUE)
  }
}
