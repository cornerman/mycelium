package mycelium.util

import akka.stream.scaladsl.Source
import scala.concurrent.{ Promise, Future }

object AkkaHelper {
  implicit class PeekableSource[T, M](val src: Source[T, M]) extends AnyVal {
    def peekMaterializedValue: (Source[T, M], Future[M]) = {
      val p = Promise[M]
      val s = src.mapMaterializedValue { m => p.trySuccess(m); m }
      (s, p.future)
    }
  }
}
