package mycelium.client

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.ws._
import akka.http.scaladsl.settings.ClientConnectionSettings
import akka.stream.scaladsl._
import akka.stream.{ActorMaterializer, OverflowStrategy}
import monix.execution.{Ack, Scheduler}
import monix.reactive.subjects.{ConcurrentSubject, PublishSubject}
import mycelium.core.AkkaMessageBuilder

import scala.concurrent.Future

class AkkaWebsocketConnection[PickleType](bufferSize: Int, overflowStrategy: OverflowStrategy)(implicit system: ActorSystem, scheduler: Scheduler, materializer: ActorMaterializer, builder: AkkaMessageBuilder[PickleType]) extends WebsocketConnection[PickleType] {

  override def run(
    location: String,
    wsConfig: WebsocketClientConfig,
    pingMessage: PickleType): ReactiveWebsocketConnection[PickleType] = {
    val connectedSubject = ConcurrentSubject.publish[Boolean]
    val incomingMessages = PublishSubject[Future[Option[PickleType]]]
    val outgoingMessages = PublishSubject[PickleType]

    val incoming = Sink.foldAsync[Ack, Message](Ack.Continue) { case (_, message) =>
      val value = builder.unpack(message)
      incomingMessages.onNext(value)
    }

    val wsFlow = RestartFlow.withBackoff(minBackoff = wsConfig.minReconnectDelay, maxBackoff = wsConfig.maxReconnectDelay, randomFactor = wsConfig.delayReconnectFactor - 1) { () =>
      Http()
        .webSocketClientFlow(WebSocketRequest(location), settings = ClientConnectionSettings(system).withConnectingTimeout(wsConfig.connectingTimeout))
        .mapMaterializedValue(_.map { upgrade =>
          if (upgrade.response.status == StatusCodes.SwitchingProtocols) {
            connectedSubject.onNext(true)
          }
          upgrade
        })
        .mapError { case t =>
          scribe.warn(s"Error in websocket connection: $t")
          connectedSubject.onNext(false)
          t
        }
    }

    val websocketPingMessage = builder.pack(pingMessage)
    val closed = Source.fromPublisher(outgoingMessages.map(builder.pack).toReactivePublisher)
      .keepAlive(wsConfig.pingInterval, () => websocketPingMessage)
      .viaMat(wsFlow)(Keep.left)
      .toMat(incoming)(Keep.right)
      .run()

    closed.onComplete { res =>
      scribe.error(s"Websocket connection finally closed: $res")
    }

    ReactiveWebsocketConnection(
      connected = connectedSubject,
      incomingMessages = incomingMessages,
      outgoingMessages = outgoingMessages
    )
  }
}
