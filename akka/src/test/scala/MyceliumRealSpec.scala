package test

import org.scalatest._

import mycelium.core.client._
import mycelium.akka.client._
import mycelium.akka.server._
import boopickle.Default._
import java.nio.ByteBuffer
import chameleon.ext.boopickle._
import akka.actor.ActorSystem
import akka.stream.OverflowStrategy
import akka.http.scaladsl.server.RouteResult._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.Http

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import org.scalatest.freespec.AsyncFreeSpec
import org.scalatest.matchers.must.Matchers

class MyceliumRealSpec extends AsyncFreeSpec with Matchers with BeforeAndAfterAll {
  implicit val system = ActorSystem()
  val port            = 9899

  override def afterAll(): Unit = {
    val b = Await.result(binding, 1.second)
    b.terminate(hardDeadline = 1.second).flatMap { _ =>
      system.terminate()
    }
    ()
  }

  type Payload = Int
  type Event   = String
  type Failure = Int
  type State   = String

  val config = WebsocketServerConfig(
    bufferSize = 5,
    overflowStrategy = OverflowStrategy.fail,
  )
  val handler = new SimpleStatelessRequestHandler[Payload, Event, Failure] {
    def onRequest(path: List[String], payload: Payload) = Response(
      Future.successful(ReturnValue(Right(payload))),
    )
  }
  val server  = WebsocketServer.withPayload(config, handler)
  val route   = handleWebSocketMessages(server.flow())
  val binding = Http().newServerAt("0.0.0.0", port).bindFlow(route)

  "client with akka" in {
    val client =
      WebsocketClient.withPayload[ByteBuffer, Payload, Event, Failure](
        new AkkaWebsocketConnection(
          bufferSize = 100,
          overflowStrategy = OverflowStrategy.fail,
        ),
        WebsocketClientConfig(),
        new IncidentHandler[Event],
      )

    client.run(() => s"ws://localhost:$port")
    val res =
      client.send("foo" :: "bar" :: Nil, 1, SendType.WhenConnected, 30.seconds)
    res.map(_ mustEqual Right(1))
  }
}
