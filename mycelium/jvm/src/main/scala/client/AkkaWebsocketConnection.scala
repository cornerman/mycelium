package mycelium.client

import akka.actor.{Actor, ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.ws._
import akka.http.scaladsl.settings.ClientConnectionSettings
<<<<<<< HEAD
import akka.stream.scaladsl._
import akka.stream.{ActorMaterializer, OverflowStrategy, QueueOfferResult}
import monix.execution.Scheduler
import mycelium.core.AkkaMessageBuilder

import scala.concurrent.{Future, Promise}
||||||| merged common ancestors
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Success, Failure}
=======
import scala.concurrent.Promise
import scala.util.{Success, Failure}
>>>>>>> wip

<<<<<<< HEAD
class AkkaWebsocketConnection[PickleType](bufferSize: Int, overflowStrategy: OverflowStrategy)(implicit system: ActorSystem, scheduler: Scheduler, materializer: ActorMaterializer, builder: AkkaMessageBuilder[PickleType]) extends WebsocketConnection[PickleType] {
||||||| merged common ancestors
class AkkaWebsocketConnection[PickleType](bufferSize: Int, overflowStrategy: OverflowStrategy)(implicit system: ActorSystem, materializer: ActorMaterializer, builder: AkkaMessageBuilder[PickleType]) extends WebsocketConnection[PickleType] {
  import system.dispatcher
=======
class AkkaWebsocketConnection[PickleType](bufferSize: Int, overflowStrategy: OverflowStrategy)(implicit system: ActorSystem, materializer: ActorMaterializer, builder: AkkaMessageBuilder[PickleType]) extends WebsocketConnection[PickleType] { wsConn =>
  import system.dispatcher
>>>>>>> wip

  private val (outgoing, outgoingMaterialized) = {
    val promise = Promise[SourceQueue[Message]]
    val source = Source.queue[Message](bufferSize, overflowStrategy)
                       .mapMaterializedValue { m => promise.success(m); m }
    (source, promise.future)
  }

  private var isConnected = false
  private val messageSender = new WebsocketMessageSender[PickleType, SourceQueue[Message]] {
    override def senderOption = if (isConnected) outgoingMaterialized.value.flatMap(_.toOption) else None
    override def doSend(queue: SourceQueue[Message], rawMessage: PickleType) = {
      val message = builder.pack(rawMessage)
      queue.offer(message).map {
        case QueueOfferResult.Enqueued => true
        case res =>
          scribe.warn(s"Websocket connection could not send message: $res")
          false
      }
    }
  }

  private def connect(listener: WebsocketListener[PickleType]): Unit = synchronized {
    isConnected = true
    messageSender.tryConnect(listener.handshake())
    listener.onConnect()
  }

  private def disconnect(listener: WebsocketListener[PickleType]): Unit = synchronized {
    isConnected = false
    listener.onClose()
  }

  def send(message: WebsocketMessage[PickleType], sendType: SendType): Unit = synchronized {
    messageSender.sendOrBuffer(message, sendType)
  }

  //TODO return result signaling closed
  def run(location: String, wsConfig: WebsocketClientConfig, pingMessage: PickleType, listener: WebsocketListener[PickleType]) = {
    val incoming = Sink.foldAsync[Unit, Message](()) { (_, message) =>
      builder.unpack(message).flatMap {
        case Some(value) =>
          listener.onMessage(value)
        case None =>
          scribe.warn(s"Ignoring websocket message. Builder does not support message ($message)")
          Future.successful(())
      }
    }

    val wsFlow = RestartFlow.withBackoff(minBackoff = wsConfig.minReconnectDelay, maxBackoff = wsConfig.maxReconnectDelay, randomFactor = wsConfig.delayReconnectFactor - 1) { () =>
      Http()
        .webSocketClientFlow(WebSocketRequest(location), settings = ClientConnectionSettings(system).withConnectingTimeout(wsConfig.connectingTimeout))
        .mapMaterializedValue { upgrade =>
          upgrade.foreach { upgrade =>
            if (upgrade.response.status == StatusCodes.SwitchingProtocols) {
              outgoingMaterialized.foreach { _ => // TODO: need to wait for materialized queue, as actor depends on it...
                connect(listener)
              }
            }
          }
          upgrade
        }
        .mapError { case t =>
          scribe.warn(s"Error in websocket connection: $t")
          disconnect(listener)
          t
        }
    }

    val websocketPingMessage = builder.pack(pingMessage)
    val closed = outgoing
      .keepAlive(wsConfig.pingInterval, () => websocketPingMessage)
      .viaMat(wsFlow)(Keep.left)
      .toMat(incoming)(Keep.right)
      .run()

    closed.onComplete { res =>
      scribe.error(s"Websocket connection finally closed: $res")
    }
  }
}
<<<<<<< HEAD

//TODO future source is dangerous as it might complete before we receive a Connected message
private[client] class SendActor[PickleType](queue: Future[SourceQueue[Message]])(implicit scheduler: Scheduler, builder: AkkaMessageBuilder[PickleType]) extends Actor {
  import SendActor._

  private var isConnected = false
  private val messageSender = new WebsocketMessageSender[PickleType, SourceQueue[Message]] {
    override def senderOption = if (isConnected) queue.value.flatMap(_.toOption) else None
    override def doSend(queue: SourceQueue[Message], rawMessage: PickleType) = {
      val message = builder.pack(rawMessage)
      queue.offer(message).map {
        case QueueOfferResult.Enqueued => true
        case res =>
          scribe.warn(s"Websocket connection could not send message: $res")
          false
      }
    }
  }

  def receive = {
    case Connected =>
      isConnected = true
      messageSender.trySendBuffer()
    case Closed =>
      isConnected = false
    case message: WebsocketMessage[PickleType] =>
      messageSender.sendOrBuffer(message)
  }
}
private[client] object SendActor {
  case object Connected
  case object Closed
}
||||||| merged common ancestors

//TODO future source is dangerous as it might complete before we receive a Connected message
private[client] class SendActor[PickleType](queue: Future[SourceQueue[Message]])(implicit ec: ExecutionContext, builder: AkkaMessageBuilder[PickleType]) extends Actor {
  import SendActor._

  private var isConnected = false
  private val messageSender = new WebsocketMessageSender[PickleType, SourceQueue[Message]] {
    override def senderOption = if (isConnected) queue.value.flatMap(_.toOption) else None
    override def doSend(queue: SourceQueue[Message], rawMessage: PickleType) = {
      val message = builder.pack(rawMessage)
      queue.offer(message).map {
        case QueueOfferResult.Enqueued => true
        case res =>
          scribe.warn(s"Websocket connection could not send message: $res")
          false
      }
    }
  }

  def receive = {
    case Connected =>
      isConnected = true
      messageSender.trySendBuffer()
    case Closed =>
      isConnected = false
    case message: WebsocketMessage[PickleType] =>
      messageSender.sendOrBuffer(message)
  }
}
private[client] object SendActor {
  case object Connected
  case object Closed
}
=======
>>>>>>> wip
