package test

import java.nio.ByteBuffer

import boopickle.Default._
import chameleon.ext.boopickle._
import mycelium.client._
import org.scalatest._

import scala.concurrent.duration._

class MyceliumSpec extends AsyncFreeSpec with MustMatchers {
  import monix.execution.Scheduler.Implicits.global

  WebSocketMock.setup()

  type Payload = String
  type Failure = Int

  "client" in {
    val client = WebsocketClient.withPayload[ByteBuffer, Payload, Failure](
      new JsWebsocketConnection, WebsocketClientConfig())

    // client.run("ws://hans")

    val res = client.send("foo" :: "bar" :: Nil, "harals", SendType.NowOrFail, Some(30 seconds))
    val res2 = client.send("foo" :: "bar" :: Nil, "harals", SendType.WhenConnected, Some(30 seconds))

    res.runAsync.failed.map(_ mustEqual RequestException.Dropped)
    res2.runAsync.value mustEqual None
  }
}
