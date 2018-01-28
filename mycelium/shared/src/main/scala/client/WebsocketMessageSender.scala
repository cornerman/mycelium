package mycelium.client

import scala.collection.mutable

trait WebsocketMessageSender[PickleType, Sender] {
  protected def senderOption: Option[Sender]
  protected def doSend(sender: Sender, message: PickleType): Unit

  private val queue = new mutable.ArrayBuffer[WebsocketMessage.Buffered[PickleType]]

  private def sendMessage(sender: Sender, message: WebsocketMessage[PickleType]): Unit = {
    message.startTimeout()
    doSend(sender, message.pickled)
  }

  def sendOrBuffer(message: WebsocketMessage[PickleType]): Unit = senderOption match {
    case Some(sender) => sendMessage(sender, message)
    case None => message match {
      case message: WebsocketMessage.Direct[PickleType] => message.fail()
      case message: WebsocketMessage.Buffered[PickleType] => queue.append(message)
    }
  }

  def trySendBuffer(): Unit = senderOption.foreach { sender =>
    val priorityQueue = queue.sortBy(- _.priority)
    priorityQueue.foreach(sendMessage(sender, _))
    queue.clear()
  }
}
