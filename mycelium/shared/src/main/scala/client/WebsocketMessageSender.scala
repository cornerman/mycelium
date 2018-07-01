package mycelium.client

import monix.eval.Task
import monix.execution.{Ack, Scheduler}
import monix.reactive.Observer

import scala.collection.mutable
import scala.concurrent.Future

sealed trait SenderAction[+PickleType]
object SenderAction {
  case class ConnectionChanged(connected: Boolean) extends SenderAction[Nothing]
  case class SendMessage[PickleType](message: WebsocketMessage[PickleType]) extends SenderAction[PickleType]
}

class WebsocketMessageSender[PickleType](outgoingMessages: Observer[PickleType])(implicit scheduler: Scheduler) extends Observer[SenderAction[PickleType]] {
  private val queue = new mutable.ArrayBuffer[WebsocketMessage.Buffered[PickleType]]
  private var isConnected = false

  def onNext(m: SenderAction[PickleType]): Future[Ack] = m match {
    case SenderAction.SendMessage(message) =>
      if (isConnected) sendMessage(message)
      else {
        message match {
          case message: WebsocketMessage.Direct[PickleType] => signalDroppedMessage(message)
          case message: WebsocketMessage.Buffered[PickleType] => queue.append(message)
        }
        Ack.Continue
      }
    case SenderAction.ConnectionChanged(connected) =>
      if (connected != isConnected) {
        isConnected = true
        tryFlushBuffer()
      } else Ack.Continue

  }

  override def onError(ex: Throwable): Unit = outgoingMessages.onError(ex)
  override def onComplete(): Unit = outgoingMessages.onComplete()

  private def tryFlushBuffer(): Future[Ack] = if (isConnected && queue.nonEmpty) {
    val priorityQueue = queue.sortBy(- _.priority)
    queue.clear()
    sendMessages(priorityQueue)
  } else Ack.Continue

  private def sendMessage(message: WebsocketMessage[PickleType]): Future[Ack] = {
    startMessageTimeout(message)
    outgoingMessages.onNext(message.pickled)
  }

  private def sendMessages(messages: Seq[WebsocketMessage[PickleType]]): Future[Ack] = {
    messages.foreach(startMessageTimeout)
    Observer.feed(outgoingMessages, messages.map(_.pickled))
  }

  private def signalDroppedMessage(message: WebsocketMessage[PickleType]): Unit = {
    message.promise tryFailure RequestException.Dropped
    ()
  }

  private def startMessageTimeout(message: WebsocketMessage[PickleType]): Unit = message.timeout.foreach { timeout =>
    Task.timer.sleep(timeout).runAsync.foreach { _ =>
      message.promise tryFailure RequestException.Timeout
    }
  }
}
