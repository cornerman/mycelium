package mycelium.client

import scala.concurrent.Future

class IncidentHandler[Event] {
  def handshake(): Future[Boolean] = Future.successful(true)
  def onConnect(): Unit = {}
  def onClose(): Unit = {}
  def onEvents(events: List[Event]): Unit = {}
}
