package mycelium.core.message

sealed trait ClientMessage[Payload]
case class Ping[Payload]() extends ClientMessage[Payload]
case class CallRequest[Payload](seqId: SequenceId, path: List[String], payload: Payload) extends ClientMessage[Payload]

sealed trait ServerMessage[Payload, Failure]
case class Pong[Payload, Failure]() extends ServerMessage[Payload, Failure]
case class SingleResponse[Payload, Failure](seqId: SequenceId, result: Either[Failure, Payload]) extends ServerMessage[Payload, Failure]
case class StreamResponse[Payload, Failure](seqId: SequenceId, result: Either[Failure, Payload]) extends ServerMessage[Payload, Failure]
case class StreamCloseResponse[Payload, Failure](seqId: SequenceId) extends ServerMessage[Payload, Failure]
case class ErrorResponse[Payload, Failure](seqId: SequenceId, message: String) extends ServerMessage[Payload, Failure]
