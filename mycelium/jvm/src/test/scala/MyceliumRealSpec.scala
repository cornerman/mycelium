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
import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.stream.scaladsl._
import akka.http.scaladsl.server.RouteResult._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.Http

import scala.concurrent.Future
import scala.concurrent.duration._

class MyceliumRealSpec extends AsyncFreeSpec with MustMatchers with BeforeAndAfterAll {
  //TODO: why does it need executionContext
  implicit override def executionContext = scala.concurrent.ExecutionContext.Implicits.global

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  val port = 9899

  override def afterAll(): Unit = {
    system.terminate()
    ()
  }

  type Payload = Int
  type Event = String
  type Failure = Int
  type State = String

  val config = WebsocketServerConfig(bufferSize = 5, overflowStrategy = OverflowStrategy.fail)
  val handler = new SimpleStatelessRequestHandler[Payload, Event, Failure] {
    def onRequest(path: List[String], payload: Payload) = Response(Future.successful(ReturnValue(Right(payload))))
  }
  val server = WebsocketServer.withPayload(config, handler)
  val route = handleWebSocketMessages(server.flow())
  Http().bindAndHandle(route, interface = "0.0.0.0", port = port)

  // "client with akka" in {
  //   val client = WebsocketClient.withPayload[ByteBuffer, Payload, Event, Failure](
  //     new AkkaWebsocketConnection(bufferSize = 100, overflowStrategy = OverflowStrategy.fail), WebsocketClientConfig(), new IncidentHandler[Event])

  //   client.run(s"ws://localhost:$port")
  //   val res = client.send("foo" :: "bar" :: Nil, 1, SendType.WhenConnected, 30 seconds)
  //   res.map(_ mustEqual Right(1))
  // }

  "client with okhttp" in {
    val client = WebsocketClient.withPayload[ByteBuffer, Payload, Event, Failure](
      new OkHttpWebsocketConnection, WebsocketClientConfig(), new IncidentHandler[Event])

    client.run(s"ws://localhost:$port")
    val res = client.send("foo" :: "bar" :: Nil, 1, SendType.WhenConnected, 30 seconds)
    res.map(_ mustEqual Right(1))
  }
}
