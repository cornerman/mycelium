package mycelium.client

import java.util.{Timer, TimerTask}

import monix.execution.Scheduler
import scala.collection.mutable
import scala.concurrent.Future

//TODO: better. we are using concurrent buffers, variables, futures and timers.
// instead compose an observable with these properties?
trait WebsocketMessageSender[PickleType, Sender] {
  protected def senderOption: Option[Sender]
  protected def doSend(sender: Sender, message: PickleType): Future[Boolean]

  private val queue = new mutable.ArrayBuffer[WebsocketMessage[PickleType]]
  private var isHandshakeDone: Boolean = false

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

  final def tryConnect(handshake: Task[Unit])(implicit ec: ExecutionContext): Unit = senderOption.foreach { sender =>
    currentHandshake = handshake.runAsync.onComplete {
    queue.foreach(sendMessage(sender, _))
    queue.clear()
  }

  private def sendMessage(sender: Sender, message: WebsocketMessage[PickleType])(implicit scheduler: Scheduler): Unit = {
    startMessageTimeout(message)
    doSend(sender, message.pickled).foreach { success =>
      if (!success) signalDroppedMessage(message)
    }
  }

  private def signalDroppedMessage(message: WebsocketMessage[PickleType]): Unit = {
    message.subject onError RequestException.Dropped
    ()
  }

  private def startMessageTimeout(message: WebsocketMessage[PickleType])(implicit scheduler: Scheduler): Unit = message.timeout.foreach { timeout =>
    val timer = new Timer
    val task: TimerTask = new TimerTask {
      def run(): Unit = {
        message.subject onError RequestException.Timeout
        ()
      }
    }

    timer.schedule(task, timeout.toMillis)
    val _ = message.subject.completedL.runOnComplete { _ =>
      timer.cancel()
      val _ = timer.purge()
    }
  }
}
