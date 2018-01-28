package mycelium.client

import mycelium.core.message._
import chameleon._

import java.util.{Timer, TimerTask}

trait WebsocketListener[PickleType] {
  def onConnect(): Unit
  def onMessage(value: PickleType): Unit
  def onClose(): Unit
}

trait WebsocketConnection[PickleType] {
  def send(value: WebsocketMessage[PickleType]): Unit

  def withPing[Payload](pingIdleMillis: Int)(implicit serializer: Serializer[ClientMessage[Payload], PickleType]) = {
    val serializedPing = serializer.serialize(Ping())
    new PingingWebsocketConnection[PickleType](this, serializedPing, pingIdleMillis)
  }

  //TODO: can return error? in general: better pattern for a 'runnable thing'?
  private[mycelium] def run(location: String, listener: WebsocketListener[PickleType]): Unit
}

//TODO: cancel timers? akka schedule?
class PingingWebsocketConnection[PickleType](connection: WebsocketConnection[PickleType], ping: PickleType, pingIdleMillis: Int) extends WebsocketConnection[PickleType] {
  private val acknowledgeTraffic: () => Unit = {
    val timer = new Timer
    () => {
      timer.cancel()
      timer.purge()
      val task = new TimerTask {
        def run() = send(WebsocketMessage.Direct(ping, () => (), () => ()))
      }

      timer.schedule(task, pingIdleMillis)
    }
  }

  def send(value: WebsocketMessage[PickleType]): Unit = {
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
