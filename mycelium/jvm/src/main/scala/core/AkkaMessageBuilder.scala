package mycelium.core

import akka.http.scaladsl.model.ws.{BinaryMessage, TextMessage, Message}
import akka.util.ByteString

import java.nio.ByteBuffer

trait AkkaMessageBuilder[PickleType] {
  def pack(msg: PickleType): Message
  def unpack(m: Message): Option[PickleType]
}

object AkkaMessageBuilder {
  implicit val AkkaMessageBuilderString = new AkkaMessageBuilder[String] {
    def pack(payload: String): Message = TextMessage(payload)
    def unpack(m: Message): Option[String] = m match {
      case TextMessage.Strict(payload) => Some(payload)
      //TODO streamed
      case _ => None
    }
  }
  implicit val AkkaMessageBuilderByteBuffer = new AkkaMessageBuilder[ByteBuffer] {
    def pack(payload: ByteBuffer): Message = BinaryMessage(ByteString(payload))
    def unpack(m: Message): Option[ByteBuffer] = m match {
      case BinaryMessage.Strict(payload) => Some(payload.asByteBuffer)
      //TODO streamed
      case _ => None
    }
  }
}
