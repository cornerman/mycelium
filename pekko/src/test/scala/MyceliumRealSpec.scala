package test

import org.scalatest._

import mycelium.core.client._
import mycelium.pekko.client._
import mycelium.pekko.server._
import boopickle.Default._
import java.nio.ByteBuffer
import chameleon.ext.boopickle._
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.OverflowStrategy
import org.apache.pekko.http.scaladsl.server.RouteResult._
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.http.scaladsl.Http

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

  "client with pekko" in {
    val client =
      WebsocketClient.withPayload[ByteBuffer, Payload, Event, Failure](
        new PekkoWebsocketConnection(
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
