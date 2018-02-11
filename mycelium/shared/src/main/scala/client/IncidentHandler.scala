package mycelium.client

class IncidentHandler[Event] {
  def onConnect(): Unit = {}
  def onClose(): Unit = {}
  def onEvents(events: List[Event]): Unit = {}
}
