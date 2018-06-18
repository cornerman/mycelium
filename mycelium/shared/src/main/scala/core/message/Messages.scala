package mycelium.core.message

sealed trait ClientMessage[Payload]
case class Ping[Payload]() extends ClientMessage[Payload]
case class CallRequest[Payload](seqId: SequenceId, path: List[String], payload: Payload) extends ClientMessage[Payload]

sealed trait ServerMessage[Payload, Failure]
sealed trait ServerResponse { def seqId: SequenceId }
case class Pong[Payload, Failure]() extends ServerMessage[Payload, Failure]
case class SingleResponse[Payload, Failure](seqId: SequenceId, result: Payload) extends ServerMessage[Payload, Failure] with ServerResponse
case class StreamResponse[Payload, Failure](seqId: SequenceId, result: Payload) extends ServerMessage[Payload, Failure] with ServerResponse
case class StreamCloseResponse[Payload, Failure](seqId: SequenceId) extends ServerMessage[Payload, Failure] with ServerResponse
case class FailureResponse[Payload, Failure](seqId: SequenceId, message: Failure) extends ServerMessage[Payload, Failure] with ServerResponse
case class ErrorResponse[Payload, Failure](seqId: SequenceId) extends ServerMessage[Payload, Failure] with ServerResponse
