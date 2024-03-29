package test

import mycelium.core.client._
import mycelium.js.client._
import chameleon.ext.boopickle._

import boopickle.Default._
import java.nio.ByteBuffer
import scala.concurrent.duration._
import org.scalatest.freespec.AsyncFreeSpec
import org.scalatest.matchers.must.Matchers

class MyceliumSpec extends AsyncFreeSpec with Matchers {
  type Payload = String
  type Event   = String
  type Failure = Int

  "client" in {
    val client =
      WebsocketClient.withPayload[ByteBuffer, Payload, Event, Failure](
        new JsWebsocketConnection,
        WebsocketClientConfig(),
        new IncidentHandler[Event],
      )

    // client.run("ws://hans")

    val res = client.send(
      "foo" :: "bar" :: Nil,
      "harals",
      SendType.NowOrFail,
      30.seconds,
    )
    val res2 = client.send(
      "foo" :: "bar" :: Nil,
      "harals",
      SendType.WhenConnected,
      30.seconds,
    )

    res.failed.map(_ mustEqual DroppedMessageException)
    res2.value mustEqual None
  }
}
