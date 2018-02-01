package mycelium.client

import mycelium.core.JsMessageBuilder
import mycelium.client.raw._

import org.scalajs.dom._
import scala.scalajs.js
import scala.scalajs.js.typedarray.ArrayBuffer
import scala.util.{Try, Success, Failure}
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.FiniteDuration
import java.util.{Timer, TimerTask}

class JsWebsocketConnection[PickleType](implicit builder: JsMessageBuilder[PickleType], ec: ExecutionContext) extends WebsocketConnection[PickleType] {

  private var wsOpt: Option[WebSocket] = None
  private var keepAliveTracker: Option[KeepAliveTracker] = None
  private val messageSender = new WebsocketMessageSender[PickleType, WebSocket] {
    override def senderOption = wsOpt
    override def doSend(ws: WebSocket, rawMessage: PickleType) = {
      keepAliveTracker.foreach(_.acknowledgeTraffic())

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

  def run(location: String, wsConfig: WebsocketClientConfig, pingMessage: PickleType, listener: WebsocketListener[PickleType]): Unit = if (wsOpt.isEmpty) {
    val keepAliveTracker = new KeepAliveTracker(wsConfig.pingInterval, () => send(WebsocketMessage.Direct(pingMessage, () => (), () => ())))
    this.keepAliveTracker = Some(keepAliveTracker) //TODO should not set this here

    val websocket = new ReconnectingWebSocket(location, options = new ReconnectingWebsocketOptions {
      override val maxReconnectionDelay: js.UndefOr[Int] = wsConfig.maxReconnectDelay.toMillis.toInt
      override val minReconnectionDelay: js.UndefOr[Int] = wsConfig.minReconnectDelay.toMillis.toInt
      override val reconnectionDelayGrowFactor: js.UndefOr[Double] = wsConfig.delayReconnectFactor
      override val connectionTimeout: js.UndefOr[Int] = wsConfig.connectingTimeout.toMillis.toInt
      override val debug: js.UndefOr[Boolean] = false
    })

    websocket.onerror = { (e: Event) =>
      scribe.warn(s"Error in websocket connection: $e")
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
        case s: String => builder.unpack(s)
        case a: ArrayBuffer => builder.unpack(a)
        case b: Blob => builder.unpack(b)
        case _ => Future.successful(None)
      }

      value.onComplete {
        case Success(value) => value match {
          case Some(value) => listener.onMessage(value)
          case None => scribe.warn(s"Ignoring websocket message. Builder does not support message (${e.data})")
        }
        case Failure(t) => scribe.warn(s"Ignoring websocket message. Builder failed to unpack message (${e.data}): $t")
      }
    }
  }
}

private[client] class KeepAliveTracker(pingInterval: FiniteDuration, sendPing: () => Unit) {
  private val timer = new Timer
  private var currentTask = Option.empty[TimerTask]
  def acknowledgeTraffic(): Unit = {
    currentTask.foreach(_.cancel())
    timer.purge()
    val task = new TimerTask { def run() = sendPing() }
    timer.schedule(task, pingInterval.toMillis)
    currentTask = Some(task)
  }
}
