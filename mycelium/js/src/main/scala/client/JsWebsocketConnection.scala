package mycelium.client

import monix.execution.Scheduler
import monix.reactive.subjects.{ConcurrentSubject, PublishSubject}
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
    pingMessage: PickleType): ReactiveWebsocketConnection[PickleType] = {
    val connectedSubject = ConcurrentSubject.publish[Boolean]
    val incomingMessages = ConcurrentSubject.publish[Future[Option[PickleType]]]
    val outgoingMessages = PublishSubject[PickleType]

    val websocket = new ReconnectingWebSocket(location, options = new ReconnectingWebsocketOptions {
      override val maxReconnectionDelay: js.UndefOr[Int] = wsConfig.maxReconnectDelay.toMillis.toInt
      override val minReconnectionDelay: js.UndefOr[Int] = wsConfig.minReconnectDelay.toMillis.toInt
      override val reconnectionDelayGrowFactor: js.UndefOr[Double] = wsConfig.delayReconnectFactor
      override val connectionTimeout: js.UndefOr[Int] = wsConfig.connectingTimeout.toMillis.toInt
      override val debug: js.UndefOr[Boolean] = false
    })

    val keepAliveTracker = new KeepAliveTracker(wsConfig.pingInterval, () => rawSend(websocket, pingMessage))
    def doSend(rawMessage: PickleType): Unit = {
      keepAliveTracker.acknowledgeTraffic()
      rawSend(websocket, rawMessage) match {
        case Success(_) => ()
        case Failure(t) => scribe.warn(s"Websocket connection could not send message: $t")
      }
    }

    outgoingMessages.foreach(doSend)

    websocket.onerror = { (e: Event) =>
      scribe.warn(s"Error in websocket connection: $e")
    }

    websocket.onopen = { (_: Event) =>
      connectedSubject.onNext(true)
    }

    websocket.onclose = { (_: Event) =>
      connectedSubject.onNext(false)
    }

    websocket.onmessage = { (e: MessageEvent) =>
      keepAliveTracker.acknowledgeTraffic()

      val value = e.data match {
        case s: String => builder.unpack(s)
        case a: ArrayBuffer => builder.unpack(a)
        case b: Blob => builder.unpack(b)
        case _ => Future.successful(None)
      }

      incomingMessages.onNext(value)
    }

    ReactiveWebsocketConnection(
      connected = connectedSubject,
      incomingMessages = incomingMessages,
      outgoingMessages = outgoingMessages
    )
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
