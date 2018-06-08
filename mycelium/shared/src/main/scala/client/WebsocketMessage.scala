package mycelium.client

import monix.reactive.subjects.Subject

import scala.concurrent.duration.FiniteDuration

sealed trait WebsocketMessage[PickleType] {
  val pickled: PickleType
  val subject: Subject[_,_]
  val timeout: Option[FiniteDuration]
}
object WebsocketMessage {
  case class Buffered[PickleType](pickled: PickleType, subject: Subject[_,_], timeout: Option[FiniteDuration], priority: SendType.Priority) extends WebsocketMessage[PickleType]
  case class Direct[PickleType](pickled: PickleType, subject: Subject[_,_], timeout: Option[FiniteDuration]) extends WebsocketMessage[PickleType]
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
