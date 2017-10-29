package mycelium.core.message

sealed trait ClientMessage[Payload]
case class Ping[Payload]() extends ClientMessage[Payload]
case class CallRequest[Payload](seqId: SequenceId, path: List[String], payload: Payload) extends ClientMessage[Payload]

sealed trait ServerMessage[Payload, Event, Failure]
case class Pong[Payload, Event, Failure]() extends ServerMessage[Payload, Event, Failure]
case class CallResponse[Payload, Event, Failure](seqId: SequenceId, result: Either[Failure, Payload]) extends ServerMessage[Payload, Event, Failure]
case class Notification[Payload, Event, Failure](event: List[Event]) extends ServerMessage[Payload, Event, Failure]
