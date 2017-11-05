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
  def run(location: String, listener: WebsocketListener[PickleType]): Unit
}
object WebsocketConnection {
  def withPing[PickleType, Payload](conn: WebsocketConnection[PickleType])(pingIdleMillis: Int)(implicit writer: Writer[ClientMessage[Payload], PickleType]) = {
    val serializedPing = writer.write(Ping())
    new PingingWebsocketConnection[PickleType](conn, serializedPing, pingIdleMillis)
  }
  def withReconnect[PickleType](conn: WebsocketConnection[PickleType])(minimumBackoffMillis: Int) = new ReconnectingWebsocketConnection[PickleType](conn, minimumBackoffMillis)
}

//TODO: cancel timers? akka schedule?

class ReconnectingWebsocketConnection[PickleType](connection: WebsocketConnection[PickleType], minimumBackoffMillis: Int) extends WebsocketConnection[PickleType] {
  private val timer = new Timer
  private var connectionAttempts = 0
  private def backoffInterval: Long = {
    val maxInterval = math.pow(2, connectionAttempts) * 1000.0
    val truncated = maxInterval.min(minimumBackoffMillis).toInt
    (scala.util.Random.nextDouble * truncated).toLong
  }

  def send(value: PickleType) = connection.send(value)

  def run(location: String, listener: WebsocketListener[PickleType]) = {
    val awareListener = new WebsocketListener[PickleType] { wsThis =>
      def onConnect(): Unit = {
        connectionAttempts = 0
        listener.onConnect()
        scribe.info(s"websocket is open: $location")
      }
      def onMessage(value: PickleType): Unit = listener.onMessage(value)
      def onClose(): Unit = {
        connectionAttempts += 1
        listener.onClose()
        scribe.info(s"Websocket is closed, will attempt to reconnect in ${(backoffInterval / 1000.0).ceil} seconds")
        val task = new TimerTask { def run() = connection.run(location, wsThis) }
        timer.schedule(task, backoffInterval)
      }
    }

    connection.run(location, awareListener)
  }
}

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
