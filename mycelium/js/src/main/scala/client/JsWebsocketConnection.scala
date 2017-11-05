package mycelium.client

import mycelium.core.JsMessageBuilder
import mycelium.util.BufferedFunction

import org.scalajs.dom._
import scala.scalajs.js.typedarray.ArrayBuffer
import scala.util.Try

class JsWebsocketConnection[PickleType](implicit builder: JsMessageBuilder[PickleType]) extends WebsocketConnection[PickleType] {
  private var wsOpt: Option[WebSocket] = None

  private def rawSend(ws: WebSocket, value: PickleType): Try[Unit] = Try {
    val msg = builder.pack(value)
    (msg: Any) match {
      case s: String => Try(ws.send(s))
      case a: ArrayBuffer => Try(ws.send(a))
      case b: Blob => Try(ws.send(b))
    }
  }

  private val sendMessages = BufferedFunction[PickleType] { msg =>
    wsOpt.fold(false) { ws =>
      rawSend(ws, msg).fold(_ => false, _ => true)
    }
  }

  def send(value: PickleType) = {
    sendMessages(value)
    sendMessages.flush()
  }

  def run(location: String, listener: WebsocketListener[PickleType]): Unit = {
    import listener._

    val websocket = new WebSocket(location)

    websocket.onerror = (e: ErrorEvent) => console.log("error", e)

    websocket.onopen = { (_: Event) =>
      wsOpt = Option(websocket)
      onConnect()
      sendMessages.flush()
    }

    websocket.onclose = { (_: Event) =>
      wsOpt = None
      onClose()
    }

    websocket.onmessage = { (e: MessageEvent) =>
      val value = e.data match {
        case s: String => builder.unpack(s)
        case a: ArrayBuffer => builder.unpack(a)
        case b: Blob => builder.unpack(b)
        case _ => None
      }

      value match {
        case Some(value) => onMessage(value)
        case None => scribe.warn(s"Unsupported websocket message: ${e.data}")
      }
    }
  }
}
