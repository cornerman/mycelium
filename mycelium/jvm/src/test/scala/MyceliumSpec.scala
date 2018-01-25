package test

import org.scalatest._

import mycelium.client._
import mycelium.server._
import mycelium.core._
import mycelium.core.message._
import boopickle.Default._
import java.nio.ByteBuffer
import chameleon._
import chameleon.boopickle._
import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.stream.scaladsl._

import scala.concurrent.Future

class MyceliumSpec extends AsyncFreeSpec with MustMatchers {
  //TODO: why does it need executionContext
  implicit override def executionContext = scala.concurrent.ExecutionContext.Implicits.global

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()

  type Payload = Int
  type Event = String
  type Failure = Int
  type State = String

  "client" in {
    val config = ClientConfig(requestTimeoutMillis = 1)
    val akkaConfig = AkkaWebsocketConfig(bufferSize = 5, overflowStrategy = OverflowStrategy.fail)

    val handler = new IncidentHandler[Event] {
      def onConnect(reconnect: Boolean): Unit = ???
      def onEvents(events: Seq[Event]): Unit = ???
    }

    val client = WebsocketClient.withPayload[ByteBuffer, Payload, Event, Failure](
      AkkaWebsocketConnection(akkaConfig), config, handler)

    val res = client.send("foo" :: "bar" :: Nil, 1)

    res.failed.map(_ mustEqual TimeoutException)
  }

  "server" in {
    val config = ServerConfig(bufferSize = 5, overflowStrategy = OverflowStrategy.fail)

    val handler = new SimpleStatelessRequestHandler[Payload, Event, Failure] {
      def onRequest(path: List[String], payload: Payload) = Response(Future.successful(ReturnValue(Right(payload))))
    }

    val server = WebsocketServer.withPayload(config, handler)
    val flow = server.flow()

    val payloadValue = 1
    val builder = implicitly[AkkaMessageBuilder[ByteBuffer]]
    val serializer = implicitly[Serializer[ClientMessage[Payload], ByteBuffer]]
    val deserializer = implicitly[Deserializer[ServerMessage[Payload, Event, Failure], ByteBuffer]]
    val request = CallRequest(1, "foo" :: "bar" :: Nil, payloadValue)
    val msg = builder.pack(serializer.serialize(request))

    val (_, received) = flow.runWith(Source(msg :: Nil), Sink.head)
    val response = received.map { msg =>
      builder.unpack(msg).map(s => deserializer.deserialize(s).right.get)
    }

    val expected = CallResponse(1, Right(payloadValue))
    response.map(_ mustEqual Some(expected))
  }
}
