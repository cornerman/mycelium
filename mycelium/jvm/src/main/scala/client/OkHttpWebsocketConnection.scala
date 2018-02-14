package mycelium.client

import okhttp3._
import okio.ByteString

import java.nio.ByteBuffer
import scala.util.Try
import scala.concurrent.{Future, ExecutionContext}
import scala.concurrent.duration._
import java.util.{Timer, TimerTask}

class OkHttpWebsocketConnection[PickleType](implicit ec: ExecutionContext, builder: OkHttpMessageBuilder[PickleType]) extends WebsocketConnection[PickleType] {

  private val timer = new Timer
  private var currentTimerTask: Option[TimerTask] = None
  private var reconnectTries = 0

  var wsOpt: Option[WebSocket] = None
  private var keepAliveTracker: Option[KeepAliveTracker] = None
  private def rawSend(ws: WebSocket, rawMessage: PickleType): Boolean = {
    val message = builder.pack(rawMessage)
    message match {
      case OkHttpMessage.Bytes(bytes) => Try(ws.send(bytes)).toOption.getOrElse(false)
      case OkHttpMessage.Text(text) => Try(ws.send(text)).toOption.getOrElse(false)
    }
  }

  private val messageSender = new WebsocketMessageSender[PickleType, WebSocket] {
    override def senderOption = wsOpt
    override def doSend(ws: WebSocket, rawMessage: PickleType) = {
      keepAliveTracker.foreach(_.acknowledgeTraffic())
      Future.successful(rawSend(ws, rawMessage))
    }
  }

  def send(value: WebsocketMessage[PickleType]): Unit = messageSender.sendOrBuffer(value)

  //TODO: reconnect
  //TODO: ping
  def run(location: String, wsConfig: WebsocketClientConfig, pingMessage: PickleType, listener: WebsocketListener[PickleType]) = {
    def sendPing(): Unit = wsOpt.foreach(rawSend(_, pingMessage))
    val keepAliveTracker = new KeepAliveTracker(wsConfig.pingInterval, sendPing _)
    this.keepAliveTracker = Some(keepAliveTracker)

    doRun(location, wsConfig, listener)
  }

  private def doRun(location: String, wsConfig: WebsocketClientConfig, listener: WebsocketListener[PickleType]): Unit = {
    currentTimerTask.foreach(_.cancel())
    currentTimerTask = None
    reconnectTries += 1

    def reconnectDelay = {
      import wsConfig._
      //TODO proper backoff
      val delay = util.Random.nextInt((maxReconnectDelay.toMillis - minReconnectDelay.toMillis).toInt) + minReconnectDelay.toMillis
      delay millis
    }

    val client = new OkHttpClient()
    val request = new Request.Builder().url(location).build()

    val okListener = new WebSocketListener {
      private def onMessage(message: OkHttpMessage): Unit = {
        keepAliveTracker.foreach(_.acknowledgeTraffic())
        builder.unpack(message) match {
          case Some(value) => listener.onMessage(value)
          case None => scribe.warn(s"Ignoring websocket message. Builder does not support message")
        }
      }
      override def onMessage(ws: WebSocket, bytes: ByteString): Unit = onMessage(OkHttpMessage.Bytes(bytes))
      override def onMessage(ws: WebSocket, text: String): Unit = onMessage(OkHttpMessage.Text(text))
      override def onOpen(ws: WebSocket, response: Response): Unit = {
        scribe.info(s"Websocket opened: $response")
        listener.onConnect()
        wsOpt = Option(ws)
        messageSender.trySendBuffer()
      }
      override def onClosing(ws: WebSocket, code: Int, reason: String): Unit = {
        scribe.info(s"Websocket is closing: $reason")
        listener.onClose()
        wsOpt = None

        if (currentTimerTask.isEmpty) {
          timer.purge()
          val task = new TimerTask { def run() = doRun(location, wsConfig, listener) }
          timer.schedule(task, reconnectDelay.toMillis)
          currentTimerTask = Some(task)
        }
      }
      override def onFailure(ws: WebSocket, t: Throwable, response: Response): Unit = {
        scribe.info(s"Error in websocket: $t")
        wsOpt = None
      }
    }

    client.newWebSocket(request, okListener)
    ()
  }
}

sealed trait OkHttpMessage extends Any
object OkHttpMessage {
  case class Text(text: String) extends AnyVal with OkHttpMessage
  case class Bytes(bytes: ByteString) extends AnyVal with OkHttpMessage
}

trait OkHttpMessageBuilder[PickleType] {
  def pack(msg: PickleType): OkHttpMessage
  def unpack(m: OkHttpMessage): Option[PickleType]
}

object OkHttpMessageBuilder {
  implicit val OkHttpMessageBuilderString = new OkHttpMessageBuilder[String] {
    def pack(msg: String): OkHttpMessage = OkHttpMessage.Text(msg)
    def unpack(m: OkHttpMessage): Option[String] = m match {
      case OkHttpMessage.Text(text) => Some(text)
      case _ => None
    }
  }
  implicit val OkHttpMessageBuilderByteBuffer = new OkHttpMessageBuilder[ByteBuffer] {
    def pack(msg: ByteBuffer): OkHttpMessage = OkHttpMessage.Bytes(ByteString.of(msg))
    def unpack(m: OkHttpMessage): Option[ByteBuffer] = m match {
      case OkHttpMessage.Bytes(bytes) => Some(bytes.asByteBuffer)
      case _ => None
    }
  }
}
