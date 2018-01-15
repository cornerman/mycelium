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
  def send(value: PickleType): Unit

  // def stop(): Unit
}

trait WebsocketConnectionRunner[PickleType] {
    //TODO: can return error?
  def run(location: String, listener: WebsocketListener[PickleType]): WebsocketConnection[PickleType]

  def withPing[Payload](pingIdleMillis: Int)(implicit serializer: Serializer[ClientMessage[Payload], PickleType]) = {
    val serializedPing = serializer.serialize(Ping())
    new PingingWebsocketConnectionRunner[PickleType](this, serializedPing, pingIdleMillis)
  }
}

//TODO: cancel timers? akka schedule?
class PingingWebsocketConnectionRunner[PickleType](runner: WebsocketConnectionRunner[PickleType], ping: PickleType, pingIdleMillis: Int) extends WebsocketConnectionRunner[PickleType] {

  def run(location: String, listener: WebsocketListener[PickleType]) = {
    val timer = new Timer
    var send: PickleType => Unit = t => ()
    val acknowledgeTraffic: () => Unit = {
      var currTask = Option.empty[TimerTask]
      () => {
        currTask.foreach(_.cancel())
        timer.purge()
        val task = new TimerTask { def run() = send(ping) }
        timer.schedule(task, pingIdleMillis)
        currTask = Some(task)
      }
    }

    val connection = runner.run(location, new WebsocketListener[PickleType] { wsThis =>
      def onConnect(): Unit = listener.onConnect()
      def onMessage(value: PickleType): Unit = {
        acknowledgeTraffic()
        listener.onMessage(value)
      }
      def onClose(): Unit = listener.onClose()
    })

    val wrapped = new WebsocketConnection[PickleType] {
      def send(value: PickleType): Unit = {
        acknowledgeTraffic()
        connection.send(value)
      }

      // def stop() {
      //   timer.cancel()
      //   timer.purge()
      //   connection.stop()
      // }
    }

    send = wrapped.send
    wrapped
  }
}
