package test

import mycelium.client._
import chameleon.ext.boopickle._

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
    val client = WebsocketClient.withPayload[ByteBuffer, Payload, Event, Failure](
      new JsWebsocketConnection, WebsocketClientConfig(), new IncidentHandler[Event])

    // client.run("ws://hans")

    val res = client.send("foo" :: "bar" :: Nil, "harals", SendType.NowOrFail, 30 seconds)
    val res2 = client.send("foo" :: "bar" :: Nil, "harals", SendType.WhenConnected, 30 seconds)

    res.failed.map(_ mustEqual DroppedMessageException)
    res2.value mustEqual None
  }
}
