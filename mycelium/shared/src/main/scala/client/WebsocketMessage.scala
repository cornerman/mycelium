package mycelium.client

sealed trait WebsocketMessage[PickleType] {
  val pickled: PickleType
  val startTimeout: () => Unit
}
object WebsocketMessage {
  case class Buffered[PickleType](pickled: PickleType, startTimeout: () => Unit, priority: SendType.Priority) extends WebsocketMessage[PickleType]
  case class Direct[PickleType](pickled: PickleType, startTimeout: () => Unit, fail: () => Unit) extends WebsocketMessage[PickleType]
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
