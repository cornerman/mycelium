package mycelium.core

import java.nio.ByteBuffer

import akka.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage}
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import akka.util.{ByteString, ByteStringBuilder}

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._

trait AkkaMessageBuilder[PickleType] {
  def pack(msg: PickleType): Message
  def unpack(m: Message): Future[Option[PickleType]]
}

object AkkaMessageBuilder {
  private def limitStream[A,B](stream: Source[A,B]): Source[A,B] = {
    //TODO: configurable limit/timeout
    stream.limit(100).completionTimeout(5.seconds)
  }

  implicit def AkkaMessageBuilderString(implicit ec: ExecutionContext, materializer: Materializer) = new AkkaMessageBuilder[String] {
    override def pack(payload: String): Message = TextMessage(payload)
    override def unpack(m: Message): Future[Option[String]] = m match {
      case TextMessage.Strict(payload) => Future.successful(Some(payload))
      case TextMessage.Streamed(stream) => limitStream(stream).runFold("")(_ + _).map(Some(_))
      case _ => Future.successful(None)
    }
  }
  implicit def AkkaMessageBuilderByteBuffer(implicit ec: ExecutionContext, materializer: Materializer) = new AkkaMessageBuilder[ByteBuffer] {
    override def pack(payload: ByteBuffer): Message = {
      val length = payload.limit() - payload.position()
      val bytes = new Array[Byte](length)
      payload.get(bytes, payload.position(), length)
      BinaryMessage(ByteString(bytes))
    }
    override def unpack(m: Message): Future[Option[ByteBuffer]] = m match {
      case BinaryMessage.Strict(payload) => Future.successful(Some(payload.asByteBuffer))
      case BinaryMessage.Streamed(stream) => limitStream(stream).runFold(new ByteStringBuilder)(_ append _).map(b => Some(b.result().asByteBuffer))
      case _ => Future.successful(None)
    }
  }
}
