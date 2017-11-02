package test

import mycelium.client._

import org.scalatest._
import boopickle.Default._
import java.nio.ByteBuffer

class MyceliumSpec extends AsyncFreeSpec with MustMatchers {
  //TODO: why does it need executionContext
  implicit override def executionContext = scala.concurrent.ExecutionContext.Implicits.global

  implicit val serializer = test.BoopickleSerializer

  type Payload = String
  type Event = String
  type Failure = Int
  type State = String

  "client" in {
    val config = ClientConfig(
      ClientConfig.Request(timeoutMillis = 1))

    val client = WebsocketClient[Pickler, Pickler, ByteBuffer, Payload, Event, Failure, State](config)

    val res = client.send("foo" :: "bar" :: Nil, "harals")

    res.failed.map(_ mustEqual TimeoutException)
  }
}
