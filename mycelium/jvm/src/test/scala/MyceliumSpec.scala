package test

import mycelium.client._
import mycelium.server._
import mycelium.core._

import akka.actor.ActorSystem
import akka.stream.OverflowStrategy
import org.scalatest._
import boopickle.Default._
import scala.concurrent.Future
import java.nio.ByteBuffer

class MyceliumSpec extends AsyncFreeSpec with MustMatchers {
  //TODO: why does it need executionContext
  implicit override def executionContext = scala.concurrent.ExecutionContext.Implicits.global

  implicit val serializer = test.BoopickleSerializer
  implicit val system = ActorSystem()

  type Payload = String
  type Event = String
  type PublishEvent = String
  type Failure = Int
  type State = String

  "client" in {
    val config = ClientConfig(
      ClientConfig.Request(timeoutMillis = 1))

    val client = WebsocketClient[Pickler, Pickler, ByteBuffer, Payload, Event, Failure, State](config)

    val res = client.send("foo" :: "bar" :: Nil, "harals")

    res.failed.map(_ mustEqual TimeoutException)
  }

  "server" in {
    val config = ServerConfig(
      ServerConfig.Flow(bufferSize = 5, overflowStrategy = OverflowStrategy.dropNew))

    val handler = new RequestHandler[Payload, Event, PublishEvent, Failure, State] {
      def onClientConnect(client: NotifiableClient[PublishEvent]): State = ???
      def onClientDisconnect(client: ClientIdentity, state: Future[State]): Unit = ???
      def onRequest(client: ClientIdentity, state: Future[State], path: List[String], payload: Payload): Response = ???
      def onEvent(client: ClientIdentity, state: Future[State], event: PublishEvent): Reaction = ???
    }

    val client = WebsocketServerFlow[Pickler, Pickler, ByteBuffer, Payload, Event, PublishEvent, Failure, State](config, handler)

    true mustEqual true
  }
}
