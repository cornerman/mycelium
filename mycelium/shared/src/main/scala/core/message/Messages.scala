package mycelium.core.message

sealed trait ClientMessage[+Payload]
case object Ping extends ClientMessage[Nothing]
case class CallRequest[Payload](seqId: SequenceId, path: List[String], payload: Payload) extends ClientMessage[Payload]

sealed trait ServerMessage[+Payload, +Failure]
sealed trait ServerResponse { def seqId: SequenceId }
case object Pong extends ServerMessage[Nothing, Nothing]
case class SingleResponse[Payload](seqId: SequenceId, result: Payload) extends ServerMessage[Payload, Nothing] with ServerResponse
case class StreamResponse[Payload](seqId: SequenceId, result: Payload) extends ServerMessage[Payload, Nothing] with ServerResponse
case class StreamCloseResponse(seqId: SequenceId) extends ServerMessage[Nothing, Nothing] with ServerResponse
case class FailureResponse[Failure](seqId: SequenceId, message: Failure) extends ServerMessage[Nothing, Failure] with ServerResponse
case class ErrorResponse(seqId: SequenceId) extends ServerMessage[Nothing, Nothing] with ServerResponse
