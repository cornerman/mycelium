package mycelium.client

import mycelium.core.AkkaMessageBuilder
import mycelium.util.AkkaHelper._

import akka.actor._
import akka.http.scaladsl.Http
import akka.stream.{ ActorMaterializer, OverflowStrategy, QueueOfferResult }
import akka.stream.scaladsl._
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.ws._
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

case class AkkaWebsocketConfig(bufferSize: Int, overflowStrategy: OverflowStrategy, minBackoff: FiniteDuration = 1.seconds, maxBackoff: FiniteDuration = 10.seconds, randomFactor: Double = 0.2)

class AkkaWebsocketConnection[PickleType](config: AkkaWebsocketConfig)(implicit system: ActorSystem, materializer: ActorMaterializer, builder: AkkaMessageBuilder[PickleType]) extends WebsocketConnection[PickleType] {
  import system.dispatcher

  private val (outgoing, outgoingMaterialized) = Source.queue[Message](config.bufferSize, config.overflowStrategy).peekMaterializedValue
  private val sendActor = system.actorOf(Props(new SendActor(outgoingMaterialized)))

  def send(msg: WebsocketMessage[PickleType]): Unit = sendActor ! msg

  //TODO return result signaling closed
  def run(location: String, listener: WebsocketListener[PickleType]) = {
    val incoming = Sink.foreach[Message] { message =>
      builder.unpack(message).foreach { //TODO we are breaking the order here, better sequence the future[m] inside the sink? foldasync?
        case Some(value) => listener.onMessage(value)
        case None => scribe.warn(s"Ignoring websocket message. Builder does not support message: $message")
      }
    }

    val wsFlow = RestartFlow.withBackoff(minBackoff = config.minBackoff, maxBackoff = config.maxBackoff, randomFactor = config.randomFactor) { () =>
      Http()
        .webSocketClientFlow(WebSocketRequest(location))
        .mapMaterializedValue { upgrade =>
          upgrade.foreach { upgrade =>
            if (upgrade.response.status == StatusCodes.SwitchingProtocols) {
              outgoingMaterialized.foreach { _ => // TODO: need to wait for materialized queue, as actor depends on it...
                listener.onConnect()
                sendActor ! SendActor.Connected
              }
            }
          }
          upgrade
        }
        .mapError { case t =>
          scribe.warn(s"Error in websocket connection: $t")
          sendActor ! SendActor.Closed
          listener.onClose()
          t
        }
    }

    val closed = outgoing
      .viaMat(wsFlow)(Keep.left)
      .toMat(incoming)(Keep.right)
      .run()

    closed.onComplete { res =>
      scribe.error(s"Websocket connection finally closed: $res")
    }
  }
}

object AkkaWebsocketConnection {
  def apply[PickleType : AkkaMessageBuilder](config: AkkaWebsocketConfig)(implicit system: ActorSystem, materializer: ActorMaterializer) = new AkkaWebsocketConnection(config)
}

//TODO future source is dangerous as it might complete before we receive a Connected message
private[client] class SendActor[PickleType](queue: Future[SourceQueue[Message]])(implicit ec: ExecutionContext, builder: AkkaMessageBuilder[PickleType]) extends Actor {
  import SendActor._

  private var isConnected = false
  private val messageSender = new WebsocketMessageSender[PickleType, SourceQueue[Message]] {
    override def senderOption = if (isConnected) queue.value.flatMap(_.toOption) else None
    override def doSend(queue: SourceQueue[Message], rawMessage: PickleType) = {
      val message = builder.pack(rawMessage)
      queue.offer(message).foreach {
        case QueueOfferResult.Enqueued => ()
        case res => scribe.warn(s"Websocket connection could not send message: $res")
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
