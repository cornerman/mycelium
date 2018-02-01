package test

import mycelium.client._
import chameleon.boopickle._

import org.scalatest._
import boopickle.Default._
import java.nio.ByteBuffer
import scala.concurrent.duration._

class MyceliumSpec extends AsyncFreeSpec with MustMatchers {
  WebSocketMock.setup()

  //TODO: why does it need executionContext
  implicit override def executionContext = scala.concurrent.ExecutionContext.Implicits.global

  type Payload = String
  type Event = String
  type Failure = Int

  "client" in {
    val config = ClientConfig(requestTimeout = 1 milliseconds)
    val handler = new IncidentHandler[Event]
    val client = WebsocketClient.withPayload[ByteBuffer, Payload, Event, Failure](new JsWebsocketConnection, config, handler)

    // client.run("ws://hans")

    val res = client.send("foo" :: "bar" :: Nil, "harals", SendType.NowOrFail)
    val res2 = client.send("foo" :: "bar" :: Nil, "harals", SendType.WhenConnected)

    res.failed.map(_ mustEqual DroppedMessageException)
    res2.value mustEqual None
  }
}
