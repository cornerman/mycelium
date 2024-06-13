package mycelium.pekko.core

import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.http.scaladsl.model.ws.{BinaryMessage, TextMessage, Message}
import org.apache.pekko.util.{ByteString, ByteStringBuilder}

import scala.concurrent.duration._
import scala.concurrent.Future
import java.nio.ByteBuffer
import scala.concurrent.ExecutionContext

trait PekkoMessageBuilder[PickleType] {
  def pack(msg: PickleType): Message
  def unpack(m: Message): Future[Option[PickleType]]
}

object PekkoMessageBuilder {
  private def limitStream[A, B](stream: Source[A, B]): Source[A, B] = {
    //TODO: configurable limit/timeout
    stream.limit(100).completionTimeout(5.seconds)
  }

  implicit def PekkoMessageBuilderString(implicit ec: ExecutionContext, materializer: Materializer): PekkoMessageBuilder[String] = new PekkoMessageBuilder[String] {
    override def pack(payload: String): Message = TextMessage(payload)
    override def unpack(m: Message): Future[Option[String]] = m match {
      case TextMessage.Strict(payload)  => Future.successful(Some(payload))
      case TextMessage.Streamed(stream) => limitStream(stream).runFold("")(_ + _).map(Some(_))
      case _                            => Future.successful(None)
    }
  }
  implicit def PekkoMessageBuilderByteBuffer(implicit ec: ExecutionContext, materializer: Materializer): PekkoMessageBuilder[ByteBuffer] = new PekkoMessageBuilder[ByteBuffer] {
    override def pack(payload: ByteBuffer): Message = BinaryMessage(ByteString(payload))
    override def unpack(m: Message): Future[Option[ByteBuffer]] = m match {
      case BinaryMessage.Strict(payload)  => Future.successful(Some(payload.asByteBuffer))
      case BinaryMessage.Streamed(stream) => limitStream(stream).runFold(new ByteStringBuilder)(_ append _).map(b => Some(b.result().asByteBuffer))
      case _                              => Future.successful(None)
    }
  }
}
