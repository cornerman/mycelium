package test

import mycelium.client._
import mycelium.server._
import mycelium.core._
import mycelium.core.message._

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.stream.scaladsl._
import org.scalatest._
import boopickle.Default._
import scala.concurrent.Future
import java.nio.ByteBuffer

class MyceliumSpec extends AsyncFreeSpec with MustMatchers {
  //TODO: why does it need executionContext
  implicit override def executionContext = scala.concurrent.ExecutionContext.Implicits.global

  implicit def serializer[T : Pickler] = new BoopickleSerializer[T]
  implicit val system = ActorSystem()

  type Payload = Int
  type Event = String
  type PublishEvent = String
  type Failure = Int
  type State = String

  "client" in {
    val config = ClientConfig(
      ClientConfig.Request(timeoutMillis = 1))

    val handler = new IncidentHandler[Event] {
      def onConnect(reconnect: Boolean): Unit = ???
      def onEvents(events: Seq[Event]): Unit = ???
    }

    val client = WebsocketClient[ByteBuffer, Payload, Event, Failure](NativeWebsocket.akka, config, handler)

    val res = client.send("foo" :: "bar" :: Nil, 1)

    res.failed.map(_ mustEqual TimeoutException)
  }

  "server" in {
    val config = ServerConfig(
      ServerConfig.Flow(bufferSize = 5, overflowStrategy = OverflowStrategy.dropNew))

    val handler = new RequestHandler[Payload, Event, PublishEvent, Failure, State] {
      def onClientConnect(client: NotifiableClient[PublishEvent]): State = "empty"
      def onClientDisconnect(client: ClientIdentity, state: Future[State]): Unit = {}
      def onRequest(client: ClientIdentity, state: Future[State], path: List[String], payload: Payload): Response =
        Response(Reaction(state, Future.successful(Nil)), Future.successful(Right(payload)))
      def onEvent(client: ClientIdentity, state: Future[State], event: PublishEvent): Reaction = ???
    }

    val flow = WebsocketServerFlow[ByteBuffer, Payload, Event, PublishEvent, Failure, State](config, handler)

    val payloadValue = 1
    val builder = implicitly[AkkaMessageBuilder[ByteBuffer]]
    val request = CallRequest(1, "foo" :: "bar" :: Nil, payloadValue)
    val msg = builder.pack(Pickle.intoBytes[ClientMessage[Payload]](request))

    implicit val materializer = ActorMaterializer()
    val (_, received) = flow.runWith(Source(msg :: Nil), Sink.head)
    val response = received.map { msg =>
      builder.unpack(msg).map { buf =>
        Unpickle[ServerMessage[Payload, Event, Failure]].fromBytes(buf)
      }
    }

    val expected = CallResponse(1, Right(payloadValue))
    response.map(_ mustEqual Some(expected))
  }
}
