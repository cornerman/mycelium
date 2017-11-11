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

case class AkkaWebsocketConfig(bufferSize: Int, overflowStrategy: OverflowStrategy)
class AkkaWebsocketConnection[PickleType](config: AkkaWebsocketConfig)(implicit system: ActorSystem, materializer: ActorMaterializer, builder: AkkaMessageBuilder[PickleType]) extends WebsocketConnection[PickleType] {
  import system.dispatcher

  private val (outgoing, queue) =
    Source.queue[Message](config.bufferSize, config.overflowStrategy).peekMaterializedValue

  def send(value: PickleType): Unit = queue.foreach { queue =>
    val message = builder.pack(value)
    queue offer message
  }

  def run(location: String, listener: WebsocketListener[PickleType]) = {
    val incoming: Sink[Message, Future[Done]] =
      Sink.foreach[Message] { message =>
        builder.unpack(message) match {
          case Some(value) => listener.onMessage(value)
          case None => scribe.warn(s"Ignoring websocket message. Builder does not support message: $message")
        }
      }

    // val bcast = Broadcast[Message]

    //TODO reconnecting:
    //singleWebSocketRequest(..., Flow[Message].alsoTo(Sink.onComplete(...)).via(yourHandlerFlow))
    // https://groups.google.com/forum/#!topic/akka-user/cWwxrx5APqI/discussion
    val webSocketFlow = Http()
      .webSocketClientFlow(WebSocketRequest(location))

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

object AkkaWebsocketConnection {
  def apply[PickleType : AkkaMessageBuilder](config: AkkaWebsocketConfig)(implicit system: ActorSystem, materializer: ActorMaterializer) = new AkkaWebsocketConnection(config)
}
