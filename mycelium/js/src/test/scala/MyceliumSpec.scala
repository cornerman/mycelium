package test

import mycelium.client._
import chameleon.boopickle._

import org.scalatest._
import boopickle.Default._
import java.nio.ByteBuffer

class MyceliumSpec extends AsyncFreeSpec with MustMatchers {
  WebSocketMock.setup()

  //TODO: why does it need executionContext
  implicit override def executionContext = scala.concurrent.ExecutionContext.Implicits.global

  type Payload = String
  type Event = String
  type Failure = Int

  "client" in {
    val config = ClientConfig(requestTimeoutMillis = 1)
    val jsConfig = JsWebsocketConfig()

    val handler = new IncidentHandler[Event] {
      def onConnect(reconnect: Boolean): Unit = ???
      def onEvents(events: Seq[Event]): Unit = ???
    }

    val client = WebsocketClient.withPayload[ByteBuffer, Payload, Event, Failure](
      JsWebsocketConnection[ByteBuffer](jsConfig), config, handler).run("ws://localhost")

    val client = clientRunner.run("ws://hans")
    val res = client.send("foo" :: "bar" :: Nil, "harals")


    res.failed.map(_ mustEqual TimeoutException)
  }
}
