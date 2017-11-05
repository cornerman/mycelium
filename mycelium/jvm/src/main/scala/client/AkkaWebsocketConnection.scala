package mycelium.client

import mycelium.core.AkkaMessageBuilder
import mycelium.util.AkkaHelper._

import akka.actor.ActorSystem
import akka.Done
import akka.http.scaladsl.Http
import akka.stream.{ ActorMaterializer, OverflowStrategy }
import akka.stream.scaladsl._
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.ws._

import scala.concurrent.Future

class AkkaWebsocketConnection[PickleType](implicit system: ActorSystem, builder: AkkaMessageBuilder[PickleType]) extends WebsocketConnection[PickleType] {
  private implicit val materializer = ActorMaterializer()
  import system.dispatcher

  private val (outgoing, queue) = {
    //TODO configurable
    val bufferSize = 250
    val overflowStrategy = OverflowStrategy.fail
    Source
      .queue[Message](bufferSize, overflowStrategy)
      .peekMaterializedValue
  }

  def send(value: PickleType): Unit = queue.foreach { queue =>
    val message = builder.pack(value)
    queue offer message
  }

  def run(location: String, listener: WebsocketListener[PickleType]) = {
    val incoming: Sink[Message, Future[Done]] =
      Sink.foreach[Message] { message =>
        builder.unpack(message) match {
          case Some(value) => listener.onMessage(value)
          case None => scribe.error(s"Unsupported websocket message: $message")
        }
      }

    val webSocketFlow = Http().webSocketClientFlow(WebSocketRequest(location))

    val (upgradeResponse, closed) =
      outgoing
        .viaMat(webSocketFlow)(Keep.right)
        .toMat(incoming)(Keep.both)
        .run()

    val connected = upgradeResponse.map { upgrade =>
      if (upgrade.response.status == StatusCodes.SwitchingProtocols) Some(Done)
      else {
        scribe.error("Failed to open websocket connection: unable to upgrade request")
        None
      }
    }

    connected.foreach(_.foreach(_ => listener.onConnect())) //TODO: do we need to close, if connect failed?
    closed.foreach(_ => listener.onClose())
  }
}
