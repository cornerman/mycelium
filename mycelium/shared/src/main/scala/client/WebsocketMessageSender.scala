package mycelium.client

import scala.collection.mutable
import scala.concurrent.{ ExecutionContext, Future }
import java.util.{ Timer, TimerTask }

case object TimeoutException extends Exception
case object DroppedMessageException extends Exception

trait WebsocketMessageSender[PickleType, Sender] {
  protected def senderOption: Option[Sender]
  protected def doSend(sender: Sender, message: PickleType): Future[Boolean]

  private val queue = new mutable.ArrayBuffer[WebsocketMessage.Buffered[PickleType]]

  def sendOrBuffer(message: WebsocketMessage[PickleType])(implicit ec: ExecutionContext): Unit = senderOption match {
    case Some(sender) => sendMessage(sender, message)
    case None => message match {
      case message: WebsocketMessage.Direct[PickleType] => signalDroppedMessage(message)
      case message: WebsocketMessage.Buffered[PickleType] => queue.append(message)
    }
  }

  def trySendBuffer()(implicit ec: ExecutionContext): Unit = senderOption.foreach { sender =>
    val priorityQueue = queue.sortBy(- _.priority)
    priorityQueue.foreach(sendMessage(sender, _))
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
