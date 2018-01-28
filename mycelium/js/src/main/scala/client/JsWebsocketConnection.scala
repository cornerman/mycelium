package mycelium.client

import mycelium.core.JsMessageBuilder
import mycelium.client.raw._

import org.scalajs.dom._
import scala.scalajs.js
import scala.scalajs.js.typedarray.ArrayBuffer
import scala.util.Try
import scala.concurrent.{ExecutionContext, Future}

case class JsWebsocketConfig(maxReconnectionDelay: Int = 10000, minReconnectionDelay: Int = 1500, reconnectionDelayGrowFactor: Double = 1.3, connectionTimeout: Int = 4000, debug: Boolean = false)

class JsWebsocketConnection[PickleType](config: JsWebsocketConfig)(implicit builder: JsMessageBuilder[PickleType], ec: ExecutionContext) extends WebsocketConnection[PickleType] {

  private var wsOpt: Option[WebSocket] = None
  private val messageSender = new WebsocketMessageSender[PickleType, WebSocket] {
    override def senderOption = wsOpt
    override def doSend(ws: WebSocket, rawMessage: PickleType) = {
      val message = builder.pack(rawMessage)
      val tried = (message: Any) match {
        case s: String => Try(ws.send(s))
        case a: ArrayBuffer => Try(ws.send(a))
        case b: Blob => Try(ws.send(b))
      }

      tried.failed.foreach { t =>
        scribe.warn(s"Websocket connection could not send message: $t")
      }
    }
  }

  def send(value: WebsocketMessage[PickleType]): Unit = messageSender.sendOrBuffer(value)

  def run(location: String, listener: WebsocketListener[PickleType]): Unit = if (wsOpt.isEmpty) {
    val websocket = new ReconnectingWebSocket(location, options = new ReconnectingWebsocketOptions {
      override val maxReconnectionDelay: js.UndefOr[Int] = config.maxReconnectionDelay
      override val minReconnectionDelay: js.UndefOr[Int] = config.minReconnectionDelay
      override val reconnectionDelayGrowFactor: js.UndefOr[Double] = config.reconnectionDelayGrowFactor
      override val connectionTimeout: js.UndefOr[Int] = config.connectionTimeout
      override val debug: js.UndefOr[Boolean] = config.debug
    })

    websocket.onerror = { (e: ErrorEvent) =>
      scribe.warn(s"Error in websocket connection: ${e.message}")
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
      val value = e.data match {
        case s: String => builder.unpack(s)
        case a: ArrayBuffer => builder.unpack(a)
        case b: Blob => builder.unpack(b)
        case _ => Future.successful(None)
      }

      value.foreach {
        case Some(value) => listener.onMessage(value)
        case None => scribe.warn(s"Ignoring websocket message. Builder does not support message: ${e.data}")
      }
    }
  }
}

object JsWebsocketConnection {
  def apply[PickleType : JsMessageBuilder](config: JsWebsocketConfig)(implicit ec: ExecutionContext) = new JsWebsocketConnection(config)
}
