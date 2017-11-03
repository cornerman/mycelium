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
          case None => //TODO log error
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
        // TODO: log error
        None
      }
    }

    connected.foreach(_.foreach(_ => listener.onConnect())) //TODO: do we need to close
    closed.foreach(_ => listener.onClose())
  }
}

object NativeWebsocketConnection {
  type System = ActorSystem
  type Builder[PickleType] = AkkaMessageBuilder[PickleType]

  def apply[PickleType](implicit system: ActorSystem, builder: AkkaMessageBuilder[PickleType]): WebsocketConnection[PickleType] =
    new AkkaWebsocketConnection[PickleType]
}
