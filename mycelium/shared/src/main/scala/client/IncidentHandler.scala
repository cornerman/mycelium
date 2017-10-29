package mycelium.client

trait IncidentHandler[Event, Failure] {
  def onConnect(reconnect: Boolean): Unit
  def onEvents(events: Seq[Event]): Unit
}
