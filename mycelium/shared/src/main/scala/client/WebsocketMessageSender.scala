package mycelium.client

import java.util.{Timer, TimerTask}

import monix.execution.Scheduler

import scala.collection.mutable
import scala.concurrent.Future

case object TimeoutException extends Exception
case object DroppedMessageException extends Exception

trait WebsocketMessageSender[PickleType, Sender] {
  protected def senderOption: Option[Sender]
  protected def doSend(sender: Sender, message: PickleType): Future[Boolean]

  private val queue = new mutable.ArrayBuffer[WebsocketMessage.Buffered[PickleType]]

  def sendOrBuffer(message: WebsocketMessage[PickleType])(implicit scheduler: Scheduler): Unit = senderOption match {
    case Some(sender) => sendMessage(sender, message)
    case None => message match {
      case message: WebsocketMessage.Direct[PickleType] => signalDroppedMessage(message)
      case message: WebsocketMessage.Buffered[PickleType] => queue.append(message)
    }
  }

  def trySendBuffer()(implicit scheduler: Scheduler): Unit = senderOption.foreach { sender =>
    val priorityQueue = queue.sortBy(- _.priority)
    priorityQueue.foreach(sendMessage(sender, _))
    queue.clear()
  }

  private def sendMessage(sender: Sender, message: WebsocketMessage[PickleType])(implicit scheduler: Scheduler): Unit = {
    startMessageTimeout(message)
    doSend(sender, message.pickled).foreach { success =>
      if (!success) signalDroppedMessage(message)
    }
  }

  private def signalDroppedMessage(message: WebsocketMessage[PickleType]): Unit = {
    message.subject.onError(DroppedMessageException)
    ()
  }

  private def startMessageTimeout(message: WebsocketMessage[PickleType])(implicit scheduler: Scheduler): Unit = message.timeout.foreach { timeout =>
    val timer = new Timer
    val task: TimerTask = new TimerTask {
      def run(): Unit = {
        message.subject.onError(TimeoutException)
        ()
      }
    }

    timer.schedule(task, timeout.toMillis)
    message.subject.completedL.runAsync.onComplete { _ =>
      timer.cancel()
      timer.purge()
    }
  }
}
