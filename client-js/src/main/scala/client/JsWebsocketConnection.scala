package mycelium.js.client

import mycelium.js.core.JsMessageBuilder
import mycelium.js.client.raw._
import mycelium.core.Cancelable
import mycelium.core.client._

import org.scalajs.dom._
import scala.scalajs.js
import scala.scalajs.js.typedarray.ArrayBuffer
import scala.util.{Try, Success, Failure}
import scala.concurrent.{ExecutionContext, Future}

class JsWebsocketConnection[PickleType](implicit
    builder: JsMessageBuilder[PickleType],
    ec: ExecutionContext,
) extends WebsocketConnection[PickleType] {

  private var wsOpt: Option[WebSocket]                   = None
  private var keepAliveTracker: Option[KeepAliveTracker] = None
  private def rawSend(ws: WebSocket, rawMessage: PickleType): Try[Unit] = {
    val message = builder.pack(rawMessage)
    (message: Any) match {
      case s: String      => Try(ws.send(s))
      case a: ArrayBuffer => Try(ws.send(a))
      case b: Blob        => Try(ws.send(b))
      case _              => ???
    }
  }
  private val messageSender =
    new WebsocketMessageSender[PickleType, WebSocket] {
      override def senderOption = wsOpt
      override def doSend(ws: WebSocket, rawMessage: PickleType) = {
        keepAliveTracker.foreach(_.acknowledgeTraffic())
        val tried = rawSend(ws, rawMessage)
        tried.failed.foreach { t =>
          scribe.warn(s"Websocket connection could not send message: $t")
        }
        Future.successful(tried.isSuccess)
      }
    }

  def rawSend(rawMessage: PickleType): Unit = messageSender.senderOption.foreach(messageSender.doSend(_, rawMessage))

  def send(value: WebsocketMessage[PickleType]): Unit =
    messageSender.sendOrBuffer(value)

  def run(
      location: () => String,
      wsConfig: WebsocketClientConfig,
      pingMessage: PickleType,
      listener: WebsocketListener[PickleType],
  ): Cancelable = if (wsOpt.isEmpty) {
    def sendPing(): Unit = wsOpt.foreach(rawSend(_, pingMessage))
    val keepAliveTracker = new KeepAliveTracker(wsConfig.pingInterval, sendPing _)
    this.keepAliveTracker = Some(keepAliveTracker) //TODO should not set this here

    val websocket = new ReconnectingWebSocket(
      location: js.Function0[String],
      options = new ReconnectingWebsocketOptions {
        override val maxReconnectionDelay: js.UndefOr[Int] =
          wsConfig.maxReconnectDelay.toMillis.toInt
        override val minReconnectionDelay: js.UndefOr[Int] =
          wsConfig.minReconnectDelay.toMillis.toInt
        override val reconnectionDelayGrowFactor: js.UndefOr[Double] =
          wsConfig.delayReconnectFactor
        override val connectionTimeout: js.UndefOr[Int] =
          wsConfig.connectingTimeout.toMillis.toInt
        override val debug: js.UndefOr[Boolean] = false
      },
    )

    websocket.onerror = { (e: Event) =>
      scribe.warn(s"Error in websocket connection: ${e.`type`}")
    }

    websocket.onopen = { (_: Event) =>
      listener.onConnect()
      wsOpt = Option(websocket)
      messageSender.trySendBuffer()
    }

    websocket.onclose = { (_: Event) =>
      wsOpt = None
      listener.onClose()
    }

    websocket.onmessage = { (e: MessageEvent) =>
      keepAliveTracker.acknowledgeTraffic()

      val value = e.data match {
        case s: String      => builder.unpack(s)
        case a: ArrayBuffer => builder.unpack(a)
        case b: Blob        => builder.unpack(b)
        case _              => Future.successful(None)
      }

      value.onComplete {
        case Success(value) =>
          value match {
            case Some(value) => listener.onMessage(value)
            case None =>
              scribe.warn(
                s"Ignoring websocket message. Builder does not support message (${e.data})",
              )
          }
        case Failure(t) =>
          scribe.warn(
            s"Ignoring websocket message. Builder failed to unpack message (${e.data}): $t",
          )
      }
    }

    Cancelable { () =>
      websocket.close()
      this.keepAliveTracker = None
      this.wsOpt = None
    }
  } else throw new Exception("Websocket already running")
}
