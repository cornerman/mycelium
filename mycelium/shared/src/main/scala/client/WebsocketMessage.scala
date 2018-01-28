package mycelium.client

sealed trait WebsocketMessage[PickleType] {
  val pickled: PickleType
  val startTimeout: () => Unit
}
object WebsocketMessage {
  case class Buffered[PickleType](pickled: PickleType, startTimeout: () => Unit, priority: Priority.Type) extends WebsocketMessage[PickleType]
  case class Direct[PickleType](pickled: PickleType, startTimeout: () => Unit, fail: () => Unit) extends WebsocketMessage[PickleType]
}

sealed trait SendBehaviour
object SendBehaviour {
  case object NowOrFail extends SendBehaviour
  case class WhenConnected(priority: Priority.Type) extends SendBehaviour
  object WhenConnected extends WhenConnected(Priority.default)
}
object Priority {
  type Type = Int

  def low = 0
  def default = 50
  def high = 100
}
