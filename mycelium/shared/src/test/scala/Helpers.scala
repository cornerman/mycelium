package test

import mycelium.core.{Reader, Writer}

import boopickle.Default._
import scala.util.{Success, Failure, Try}
import java.nio.ByteBuffer

class BoopickleSerializer[T : Pickler] extends Reader[T, ByteBuffer] with Writer[T, ByteBuffer] {
  override def write(arg: T): ByteBuffer = Pickle.intoBytes(arg)
  override def read(arg: ByteBuffer): Either[Throwable, T] =
    Try(Unpickle[T].fromBytes(arg)) match {
      case Success(arg) => Right(arg)
      case Failure(t) => Left(t)
    }
}
