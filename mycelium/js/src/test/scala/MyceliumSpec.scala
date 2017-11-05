package test

import mycelium.client._

import org.scalatest._
import boopickle.Default._
import java.nio.ByteBuffer

class MyceliumSpec extends AsyncFreeSpec with MustMatchers {
  //TODO: why does it need executionContext
  implicit override def executionContext = scala.concurrent.ExecutionContext.Implicits.global

  implicit def serializer[T : Pickler] = new BoopickleSerializer[T]

  type Payload = String
  type Event = String
  type Failure = Int

  "client" in {
    val config = ClientConfig(
      ClientConfig.Request(timeoutMillis = 1))

    val handler = new IncidentHandler[Event] {
      def onConnect(reconnect: Boolean): Unit = ???
      def onEvents(events: Seq[Event]): Unit = ???
    }

    val client = WebsocketClient[ByteBuffer, Payload, Event, Failure](config, handler)

    val res = client.send("foo" :: "bar" :: Nil, "harals")

    res.failed.map(_ mustEqual TimeoutException)
  }
}
