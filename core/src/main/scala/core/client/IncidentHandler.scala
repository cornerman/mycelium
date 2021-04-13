package mycelium.core.client

class IncidentHandler[Event] {
  def onConnect(): Unit = {}
  def onClose(): Unit = {}
  def onEvents(events: List[Event]): Unit = {}
}
