package mycelium.client

import mycelium.core._
import mycelium.core.message._

import java.util.{Timer, TimerTask}

trait WebsocketListener[PickleType] {
  def onConnect(): Unit
  def onMessage(value: PickleType): Unit
  def onClose(): Unit
}

trait WebsocketConnection[PickleType] {
  def send(value: PickleType): Unit

  def withPing[Payload](pingIdleMillis: Int)(implicit writer: Writer[ClientMessage[Payload], PickleType]) = {
    val serializedPing = writer.write(Ping())
    new PingingWebsocketConnection[PickleType](this, serializedPing, pingIdleMillis)
  }

  //TODO: can return error? in general: better pattern for a 'runnable thing'?
  private[mycelium] def run(location: String, listener: WebsocketListener[PickleType]): Unit
}

//TODO: cancel timers? akka schedule?
class PingingWebsocketConnection[PickleType](connection: WebsocketConnection[PickleType], ping: PickleType, pingIdleMillis: Int) extends WebsocketConnection[PickleType] {
  private val timer = new Timer
  private val acknowledgeTraffic: () => Unit = {
    var currTask = Option.empty[TimerTask]
    () => {
      currTask.foreach(_.cancel())
      timer.purge()
      val task = new TimerTask { def run() = send(ping) }
      timer.schedule(task, pingIdleMillis)
      currTask = Some(task)
    }
  }

  def send(value: PickleType): Unit = {
    acknowledgeTraffic()
    connection.send(value)
  }

  def run(location: String, listener: WebsocketListener[PickleType]) = {
    val awareListener = new WebsocketListener[PickleType] { wsThis =>
      def onConnect(): Unit = listener.onConnect()
      def onMessage(value: PickleType): Unit = {
        acknowledgeTraffic()
        listener.onMessage(value)
      }
      def onClose(): Unit = listener.onClose()
    }

    connection.run(location, awareListener)
  }
}
