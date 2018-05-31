package mycelium.client

import scala.collection.mutable
import scala.concurrent.{ ExecutionContext, Future }
import java.util.{ Timer, TimerTask }

case object TimeoutException extends Exception
case object DroppedMessageException extends Exception

trait WebsocketMessageSender[PickleType, Sender] {
  protected def senderOption: Option[Sender]
  protected def doSend(sender: Sender, message: PickleType): Future[Boolean]

  private val queue = new mutable.ArrayBuffer[WebsocketMessage[PickleType]]
  private var currentHandshake: Future[Boolean] = Future.successful(false)
  private def isHandshakeDone: Boolean = currentHandshake.value.flatMap(_.toOption).getOrElse(false)

  final def sendOrBuffer(message: WebsocketMessage[PickleType], sendType: SendType)(implicit ec: ExecutionContext): Unit = sendType match {
    case SendType.WhenConnected => senderOption match {
      case Some(sender) if isHandshakeDone => sendMessage(sender, message)
      case _ => queue.append(message)
    }
    case SendType.NowOrFail => senderOption match {
      case Some(sender) if isHandshakeDone => sendMessage(sender, message)
      case _ => signalDroppedMessage(message)
    }
    case SendType.Handshake => senderOption match {
      case Some(sender) if !isHandshakeDone => sendMessage(sender, message)
      case _ => signalDroppedMessage(message)
    }
  }

  final def tryConnect(handshake: Future[Boolean])(implicit ec: ExecutionContext): Unit = senderOption.foreach { sender =>
    currentHandshake = handshake
    queue.foreach(sendMessage(sender, _))
    queue.clear()
  }

  private def sendMessage(sender: Sender, message: WebsocketMessage[PickleType])(implicit ec: ExecutionContext): Unit = {
    startMessageTimeout(message)
    doSend(sender, message.pickled).foreach { success =>
      if (!success) signalDroppedMessage(message)
    }
  }

  private def signalDroppedMessage(message: WebsocketMessage[PickleType]): Unit = {
    message.promise tryFailure DroppedMessageException
    ()
  }

  private def startMessageTimeout(message: WebsocketMessage[PickleType])(implicit ctx: ExecutionContext): Unit = {
    val timer = new Timer
    val task = new TimerTask {
      def run(): Unit = {
        message.promise tryFailure TimeoutException
        ()
      }
    }

    timer.schedule(task, message.timeout.toMillis)
    message.promise.future.onComplete { _ =>
      timer.cancel()
      timer.purge()
    }
  }
}
