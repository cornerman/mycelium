package mycelium.client

import java.util.{Timer, TimerTask}

import monix.execution.Scheduler
import monix.eval.{Callback, Task}

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Success

sealed trait MessageSendStatus
object MessageSendStatus {
  case object Sent extends MessageSendStatus
  case object Rejected extends MessageSendStatus
  case object Disconnected extends MessageSendStatus
}

trait WebsocketMessageSender[PickleType] {
  protected def doSend(message: PickleType): MessageSendStatus

  private val queue = new mutable.ArrayBuffer[WebsocketMessage.Buffered[PickleType]]

  def sendOrBuffer(message: WebsocketMessage[PickleType])(implicit scheduler: Scheduler): Unit = doSend(message.pickled) match {
    case MessageSendStatus.Sent => startMessageTimeout(message)
    case MessageSendStatus.Rejected => signalDroppedMessage(message)
    case MessageSendStatus.Disconnected => message match {
      case message: WebsocketMessage.Direct[PickleType] => signalDroppedMessage(message)
      case message: WebsocketMessage.Buffered[PickleType] => queue.append(message)
    }
  }

  def trySendBuffer()(implicit scheduler: Scheduler): Unit = {
    val priorityQueue = queue.sortBy(- _.priority)
    queue.clear()
    priorityQueue.foreach(sendOrBuffer(_))
  }

  private def signalDroppedMessage(message: WebsocketMessage[PickleType]): Unit = {
    message.promise tryFailure RequestException.Dropped
    ()
  }

  private def startMessageTimeout(message: WebsocketMessage[PickleType])(implicit scheduler: Scheduler): Unit = message.timeout.foreach { timeout =>
    Task.timer.sleep(timeout).runAsync.foreach { _ =>
      message.promise tryFailure RequestException.Timeout
    }
  }
}
