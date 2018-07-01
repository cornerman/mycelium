package mycelium.client

import monix.execution.Scheduler
import monix.reactive.Observable
import monix.reactive.subjects.{ConcurrentSubject, PublishSubject, ReplaySubject}
import mycelium.client.raw._
import mycelium.core.JsMessageBuilder
import org.scalajs.dom._

import scala.concurrent.Future
import scala.scalajs.js
import scala.scalajs.js.typedarray.ArrayBuffer
import scala.util.{Failure, Success, Try}

class JsWebsocketConnection[PickleType](implicit builder: JsMessageBuilder[PickleType], scheduler: Scheduler) extends WebsocketConnection[PickleType] {

  override def run(
    location: String,
    wsConfig: WebsocketClientConfig,
    pingMessage: PickleType,
    outgoingMessages: Observable[WebsocketMessage[PickleType]],
    listener: WebsocketListener[PickleType]): Unit = {

    val websocket = new ReconnectingWebSocket(location, options = new ReconnectingWebsocketOptions {
      override val maxReconnectionDelay: js.UndefOr[Int] = wsConfig.maxReconnectDelay.toMillis.toInt
      override val minReconnectionDelay: js.UndefOr[Int] = wsConfig.minReconnectDelay.toMillis.toInt
      override val reconnectionDelayGrowFactor: js.UndefOr[Double] = wsConfig.delayReconnectFactor
      override val connectionTimeout: js.UndefOr[Int] = wsConfig.connectingTimeout.toMillis.toInt
      override val debug: js.UndefOr[Boolean] = false
    })

    var isConnected = false
    val keepAliveTracker = new KeepAliveTracker(wsConfig.pingInterval, () => rawSend(websocket, pingMessage))
    val messageSender = new WebsocketMessageSender[PickleType] {
      override def doSend(rawMessage: PickleType) = {
        if (isConnected) {
          keepAliveTracker.acknowledgeTraffic()
          rawSend(websocket, rawMessage) match {
            case Success(_) => MessageSendStatus.Sent
            case Failure(t) =>
              scribe.warn(s"Websocket connection could not send message: $t")
              MessageSendStatus.Rejected
          }
        } else MessageSendStatus.Disconnected
      }
    }

    val _ = outgoingMessages.foreach(messageSender.sendOrBuffer)

    websocket.onerror = { (e: Event) =>
      scribe.warn(s"Error in websocket connection: $e")
    }

    websocket.onopen = { (_: Event) =>
      listener.onConnect()
      isConnected = true
      messageSender.trySendBuffer()
    }

    websocket.onclose = { (_: Event) =>
      isConnected = false
      listener.onClose()
    }

    websocket.onmessage = { (e: MessageEvent) =>
      keepAliveTracker.acknowledgeTraffic()

      val value = e.data match {
        case s: String => builder.unpack(s)
        case a: ArrayBuffer => builder.unpack(a)
        case b: Blob => builder.unpack(b)
        case _ => Future.successful(None)
      }

      listener.onMessage(value)
    }
  }

  private def rawSend(ws: WebSocket, rawMessage: PickleType): Try[Unit] = {
    val message = builder.pack(rawMessage)
    (message: Any) match {
      case s: String => Try(ws.send(s))
      case a: ArrayBuffer => Try(ws.send(a))
      case b: Blob => Try(ws.send(b))
    }
  }
}
