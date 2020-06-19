package test

import org.scalatest._

import mycelium.client._
import mycelium.server._
import mycelium.core._
import mycelium.core.message._
import boopickle.Default._
import java.nio.ByteBuffer
import chameleon._
import chameleon.ext.boopickle._
import akka.actor.ActorSystem
import akka.stream.OverflowStrategy
import akka.stream.scaladsl._

import scala.concurrent.Future
import scala.concurrent.duration._
import org.scalatest.freespec.AsyncFreeSpec
import org.scalatest.matchers.must.Matchers

class MyceliumSpec extends AsyncFreeSpec with Matchers with BeforeAndAfterAll {
  implicit val system = ActorSystem()

  override def afterAll(): Unit = {
    system.terminate()
    ()
  }

  type Payload = Int
  type Event = String
  type Failure = Int
  type State = String

  "client" in {
    val client = WebsocketClient.withPayload[ByteBuffer, Payload, Event, Failure](
      new AkkaWebsocketConnection(bufferSize = 100, overflowStrategy = OverflowStrategy.fail), WebsocketClientConfig(), new IncidentHandler[Event])

    // client.run("ws://hans")

    val res = client.send("foo" :: "bar" :: Nil, 1, SendType.NowOrFail, 30 seconds)
    val res2 = client.send("foo" :: "bar" :: Nil, 1, SendType.WhenConnected, 30 seconds)

    res.failed.map(_ mustEqual DroppedMessageException)
    res2.value mustEqual None
  }

  "server" in {
    val config = WebsocketServerConfig(bufferSize = 5, overflowStrategy = OverflowStrategy.fail)
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
