package mycelium.core.message

import chameleon.{Serializer, Deserializer}

sealed trait ClientMessage[+Payload]
case object Ping                                                                         extends ClientMessage[Nothing]
case class CallRequest[Payload](seqId: SequenceId, path: List[String], payload: Payload) extends ClientMessage[Payload]

sealed trait ServerMessage[+Payload, +Event, +Failure]
case object Pong extends ServerMessage[Nothing, Nothing, Nothing]
// case class CallResponse[Payload, Failure](seqId: SequenceId, result: Either[Failure, Payload]) extends ServerMessage[Payload, Nothing, Failure]
case class CallResponse[Payload, Failure](seqId: SequenceId, result: Either[Failure, Payload]) extends ServerMessage[Payload, Nothing, Failure]
// case class CallResponseSuccess[Payload](seqId: SequenceId, result: Payload) extends ServerMessage[Payload, Nothing, Nothing]
// case class CallResponseFailure[Failure](seqId: SequenceId, result: Failure) extends ServerMessage[Nothing, Nothing, Failure]
case class Notification[Event](event: List[Event]) extends ServerMessage[Nothing, Event, Nothing]

// sealed trait ServerMessage[+Payload, +ErrorType]
// sealed trait ServerResponse { def seqId: SequenceId }
// case object Pong extends ServerMessage[Nothing, Nothing]
// case class SingleResponse[Payload](seqId: SequenceId, result: Payload) extends ServerMessage[Payload, Nothing] with ServerResponse
// case class StreamResponse[Payload](seqId: SequenceId, result: Payload) extends ServerMessage[Payload, Nothing] with ServerResponse
// case class StreamCloseResponse(seqId: SequenceId) extends ServerMessage[Nothing, Nothing] with ServerResponse
// case class ErrorResponse[ErrorType](seqId: SequenceId, message: ErrorType) extends ServerMessage[Nothing, ErrorType] with ServerResponse
// case class ExceptionResponse(seqId: SequenceId) extends ServerMessage[Nothing, Nothing] with ServerResponse

case class SerializablePayload[T, PickleType](payload: T, serializer: Serializer[T, PickleType])
object SerializablePayload {
  def from[T, PickleType](payload: T)(implicit serializer: Serializer[T, PickleType]): SerializablePayload[T, PickleType] =
    SerializablePayload(payload, serializer)
}

case class DeserializablePayload[T, PickleType](payload: T, serializer: Deserializer[T, PickleType])
object DeserializablePayload {
  def from[T, PickleType](payload: T)(implicit deserializer: Deserializer[T, PickleType]): DeserializablePayload[T, PickleType] =
    DeserializablePayload(payload, deserializer)
}
