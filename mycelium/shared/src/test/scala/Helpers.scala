package test

import mycelium.core.Serializer

import boopickle.Default._
import scala.util.{Success, Failure, Try}
import java.nio.ByteBuffer

object BoopickleSerializer extends Serializer[Pickler, Pickler, ByteBuffer] {
  override def serialize[T : Pickler](arg: T): ByteBuffer = Pickle.intoBytes(arg)
  override def deserialize[T : Pickler](arg: ByteBuffer): Either[Throwable, T] =
    Try(Unpickle[T].fromBytes(arg)) match {
      case Success(arg) => Right(arg)
      case Failure(t) => Left(t)
    }
}
