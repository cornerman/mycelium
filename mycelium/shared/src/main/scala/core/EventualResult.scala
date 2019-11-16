package mycelium.core

import monix.reactive.Observable

sealed trait EventualResult[+Payload, +ErrorType] extends Any
object EventualResult {
  case class Error[ErrorType](failure: ErrorType) extends AnyVal with EventualResult[Nothing, ErrorType]
  case class Single[Payload](value: Payload) extends AnyVal with EventualResult[Payload, Nothing]
  case class Stream[Payload](observable: Observable[Payload]) extends AnyVal with EventualResult[Payload, Nothing]
}
