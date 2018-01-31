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

class MyceliumSpec extends AsyncFreeSpec with MustMatchers with BeforeAndAfterAll {
  //TODO: why does it need executionContext
  implicit override def executionContext = scala.concurrent.ExecutionContext.Implicits.global

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()

  override def afterAll(): Unit = {
    system.terminate()
    ()
  }

  type Payload = Int
  type Event = String
  type Failure = Int
  type State = String

  "client" in {
    val config = ClientConfig(requestTimeoutMillis = 40000)
    val akkaConfig = AkkaWebsocketConfig(bufferSize = 100, overflowStrategy = OverflowStrategy.fail)

    val handler = new IncidentHandler[Event]
    val client = WebsocketClient.withPayload[ByteBuffer, Payload, Event, Failure](
      AkkaWebsocketConnection[ByteBuffer](akkaConfig), config, handler)

    // client.run("ws://hans")

    val res = client.send("foo" :: "bar" :: Nil, 1, SendBehaviour.NowOrFail)
    val res2 = client.send("foo" :: "bar" :: Nil, 1, SendBehaviour.WhenConnected)

    res.failed.map(_ mustEqual DroppedMessageException)
    res2.value mustEqual None
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
    val response = received.flatMap { msg =>
      builder.unpack(msg).map(_.map(s => deserializer.deserialize(s).right.get))
    }

    val expected = CallResponse(1, Right(payloadValue))
    response.map(_ mustEqual Some(expected))
  }
}
