package mycelium.core

import org.scalajs.dom.Blob
import scala.scalajs.js.|
import scala.scalajs.js.typedarray._, TypedArrayBufferOps._

import java.nio.ByteBuffer

trait JsMessageBuilder[PickleType] {
  import JsMessageBuilder._

  def pack(msg: PickleType): SendType
  def unpack(m: SendType): Option[PickleType]
}

object JsMessageBuilder {
  type SendType = String | ArrayBuffer | Blob

  implicit val JsMessageBuilderString = new JsMessageBuilder[String] {
    def pack(msg: String): SendType = msg
    def unpack(m: SendType): Option[String] = (m: Any) match {
      case s: String => Some(s)
      case _ => None
    }
  }
  implicit val JsMessageBuilderByteBuffer = new JsMessageBuilder[ByteBuffer] {
    def pack(msg: ByteBuffer): SendType = msg.arrayBuffer
    def unpack(m: SendType): Option[ByteBuffer] = (m: Any) match {
      case a: ArrayBuffer => Some(TypedArrayBuffer.wrap(a))
      case _ => None
    }
  }
}
