package mycelium.core

import akka.stream.Materializer
import akka.stream.scaladsl.Source
import akka.http.scaladsl.model.ws.{BinaryMessage, TextMessage, Message}
import akka.util.{ByteString, ByteStringBuilder}

import scala.concurrent.duration._
import scala.concurrent.Future
import java.nio.ByteBuffer
import scala.concurrent.ExecutionContext

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
    override def pack(payload: ByteBuffer): Message = BinaryMessage(ByteString(payload))
    override def unpack(m: Message): Future[Option[ByteBuffer]] = m match {
      case BinaryMessage.Strict(payload) => Future.successful(Some(payload.asByteBuffer))
      case BinaryMessage.Streamed(stream) => limitStream(stream).runFold(new ByteStringBuilder)(_ append _).map(b => Some(b.result().asByteBuffer))
      case _ => Future.successful(None)
    }
  }
}
