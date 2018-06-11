package test

import java.nio.ByteBuffer

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.stream.{ActorMaterializer, OverflowStrategy}
import boopickle.Default._
import chameleon.ext.boopickle._
import mycelium.client._
import mycelium.server._
import org.scalatest._
import monix.reactive.Observable

import scala.concurrent.Future
import scala.concurrent.duration._

class MyceliumRealSpec extends AsyncFreeSpec with MustMatchers with BeforeAndAfterAll {
  import monix.execution.Scheduler.Implicits.global

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

  val config = WebsocketServerConfig(bufferSize = 5, overflowStrategy = OverflowStrategy.fail)
  val handler = new StatelessRequestHandler[Payload, Event, Failure] {
    def onRequest(client: Client[Event], path: List[String], payload: Payload) = path match {
      case "single" :: Nil => Response(Future.successful(Right(payload)))
      case "stream" :: Nil => Response(Observable.fromIterable(List(1,2).map(i => Right(i))))
    }
  }
  val server = WebsocketServer.withPayload(config, handler)
  val route = handleWebSocketMessages(server.flow())
  Http().bindAndHandle(route, interface = "0.0.0.0", port = port)

  "client with akka" - {
    val client = WebsocketClient.withPayload[ByteBuffer, Payload, Event, Failure](
      new AkkaWebsocketConnection(bufferSize = 100, overflowStrategy = OverflowStrategy.fail), WebsocketClientConfig(), new IncidentHandler[Event])

    client.run(s"ws://localhost:$port")

    "single result" in {
      val res = client.send("single" :: Nil, 1, SendType.WhenConnected, Some(30 seconds))
      res.lastL.runAsync.map(_ mustEqual Right(1))
    }

    "stream result" in {
      val res = client.send("stream" :: Nil, 0, SendType.WhenConnected, Some(30 seconds))
      res.foldLeftL[List[Either[Failure, Payload]]](Nil)((l,i) => l :+ i).runAsync.map(l => l mustEqual List(Right(1),Right(2)))
    }
  }
}
