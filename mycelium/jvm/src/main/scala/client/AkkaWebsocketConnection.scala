package mycelium.client

import akka.actor.{Actor, ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.ws._
import akka.http.scaladsl.settings.ClientConnectionSettings
import akka.stream.scaladsl._
import akka.stream.{ActorMaterializer, OverflowStrategy, QueueOfferResult}
import monix.execution.{Ack, Scheduler}
import monix.reactive.Observable
import monix.reactive.subjects.{ConcurrentSubject, PublishSubject}
import mycelium.core.AkkaMessageBuilder

import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Success}

class AkkaWebsocketConnection[PickleType](bufferSize: Int, overflowStrategy: OverflowStrategy)(implicit system: ActorSystem, scheduler: Scheduler, materializer: ActorMaterializer, builder: AkkaMessageBuilder[PickleType]) extends WebsocketConnection[PickleType] {

  override def run(
                    location: String,
                    wsConfig: WebsocketClientConfig,
                    pingMessage: PickleType,
                    outgoingMessages: Observable[WebsocketMessage[PickleType]],
                    listener: WebsocketListener[PickleType]): Unit = {

    val sendingMessages = ConcurrentSubject.publish[Message]

    var isConnected = false
    val messageSender = new WebsocketMessageSender[PickleType] {
      override def doSend(rawMessage: PickleType) = {
        if (isConnected) {
          val message = builder.pack(rawMessage)
          sendingMessages.onNext(message) match {
            case Ack.Continue => MessageSendStatus.Sent
            case Ack.Stop =>
              scribe.warn(s"Websocket connection could not send message")
              MessageSendStatus.Rejected
          }
        } else MessageSendStatus.Disconnected
      }
    }

    val incoming = Sink.foreach[Message] { message =>
      val value = builder.unpack(message)
      listener.onMessage(value)
    }

    val wsFlow = RestartFlow.withBackoff(minBackoff = wsConfig.minReconnectDelay, maxBackoff = wsConfig.maxReconnectDelay, randomFactor = wsConfig.delayReconnectFactor - 1) { () =>
      Http()
        .webSocketClientFlow(WebSocketRequest(location), settings = ClientConnectionSettings(system).withConnectingTimeout(wsConfig.connectingTimeout))
        .mapMaterializedValue(_.map { upgrade =>
          if (upgrade.response.status == StatusCodes.SwitchingProtocols) {
            listener.onConnect()
            isConnected = true
            messageSender.trySendBuffer()
          }
          upgrade
        })
        .mapError { case t =>
          isConnected = false
          scribe.warn(s"Error in websocket connection: $t")
          listener.onClose()
          t
        }
    }

    val websocketPingMessage = builder.pack(pingMessage)
    val closed = Source.fromPublisher(sendingMessages.toReactivePublisher)
      .keepAlive(wsConfig.pingInterval, () => websocketPingMessage)
      .viaMat(wsFlow)(Keep.left)
      .toMat(incoming)(Keep.right)
      .run()

    closed.onComplete { res =>
      scribe.error(s"Websocket connection finally closed: $res")
    }
  }
}
