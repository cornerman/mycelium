package mycelium.core

import org.scalajs.dom.{FileReader, Blob}
import scala.scalajs.js.|
import scala.scalajs.js.typedarray._, TypedArrayBufferOps._

import java.nio.ByteBuffer
import scala.concurrent.{Promise, Future}

trait JsMessageBuilder[PickleType] {
  import JsMessageBuilder._

  def pack(msg: PickleType): SendType
  def unpack(m: SendType): Future[Option[PickleType]]
}

object JsMessageBuilder {
  type SendType = String | ArrayBuffer | Blob

  implicit val JsMessageBuilderString = new JsMessageBuilder[String] {
    def pack(msg: String): SendType = msg
    def unpack(m: SendType): Future[Option[String]] = (m: Any) match {
      case s: String => Future.successful(Some(s))
      case b: Blob =>
        val promise = Promise[Option[String]]()
        val reader = new FileReader
        reader.onload = _ => {
          val s = reader.result.asInstanceOf[String];
          promise.success(Option(s))
        }
        reader.onerror = _ => {
          promise.success(None)
        }
        reader.readAsText(b)
        promise.future
      case _ => Future.successful(None)
    }
  }
  implicit val JsMessageBuilderByteBuffer = new JsMessageBuilder[ByteBuffer] {
    def pack(msg: ByteBuffer): SendType = msg.arrayBuffer
    def unpack(m: SendType): Future[Option[ByteBuffer]] = (m: Any) match {
      case a: ArrayBuffer => Future.successful(Some(TypedArrayBuffer.wrap(a)))
      case b: Blob =>
        val promise = Promise[Option[ByteBuffer]]()
        val reader = new FileReader
        reader.onload = _ => {
          val a = reader.result.asInstanceOf[ArrayBuffer];
          promise.success(Option(TypedArrayBuffer.wrap(a)))
        }
        reader.onerror = _ => {
          promise.success(None)
        }
        reader.readAsArrayBuffer(b)
        promise.future
      case _ => Future.successful(None)
    }
  }
}
