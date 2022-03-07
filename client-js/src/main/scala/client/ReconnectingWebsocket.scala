package mycelium.js.client.raw

import org.scalajs.dom._

import scala.scalajs.js
import scala.scalajs.js.|
import scala.scalajs.js.annotation.JSImport

object WebSocketConstructor {
  type Type = (String, String) => WebSocket

  def apply(f: Type) = {
    val x: js.Function2[String, String, WebSocket] = f
    x.asInstanceOf[js.Dynamic].CLOSING = 2
    x
  }
}

trait ReconnectingWebsocketOptions extends js.Object {
  val maxReconnectionDelay: js.UndefOr[Int]           = js.undefined
  val minReconnectionDelay: js.UndefOr[Int]           = js.undefined
  val reconnectionDelayGrowFactor: js.UndefOr[Double] = js.undefined
  val connectionTimeout: js.UndefOr[Int]              = js.undefined
  val maxRetries: js.UndefOr[Int]                     = js.undefined
  val debug: js.UndefOr[Boolean]                      = js.undefined
}

@js.native
@JSImport("reconnecting-websocket", JSImport.Default)
class ReconnectingWebSocket(
    url: String | js.Function0[String | js.Promise[String]],
    protocols: String | Array[String] = null,
    options: ReconnectingWebsocketOptions = null,
) extends WebSocket
