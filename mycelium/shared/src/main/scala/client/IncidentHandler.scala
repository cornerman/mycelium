package mycelium.client

trait IncidentHandler[Event] {
  def onConnect(reconnect: Boolean): Unit
  def onEvents(events: Seq[Event]): Unit
}
