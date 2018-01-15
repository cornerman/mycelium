package mycelium.client

import mycelium.core.message._
import chameleon._

import scala.concurrent.{ ExecutionContext, Future }

class WebsocketClient[PickleType, Payload, Failure](
  ws: WebsocketConnection[PickleType],
  callRequests: OpenRequests[Either[Failure, Payload]])(implicit
  serializer: Serializer[ClientMessage[Payload], PickleType]) {

  def send(path: List[String], payload: Payload)(implicit ec: ExecutionContext): Future[Either[Failure, Payload]] = {
    val (id, promise) = callRequests.open()

    val request = CallRequest(id, path, payload)
    val serialized = serializer.serialize(request)
    ws.send(serialized)

    promise.future
  }
}

class WebsocketClientRunner[PickleType, Payload, Event, Failure](
  runner: WebsocketConnectionRunner[PickleType],
  handler: IncidentHandler[Event],
  callRequests: OpenRequests[Either[Failure, Payload]])(implicit
  serializer: Serializer[ClientMessage[Payload], PickleType],
  deserializer: Deserializer[ServerMessage[Payload, Event, Failure], PickleType]) {

  def run(location: String): WebsocketClient[PickleType, Payload, Failure] = {
    val ws = runner.run(location, new WebsocketListener[PickleType] {
      private var wasClosed = false
      def onConnect() = handler.onConnect(wasClosed)
      def onClose() = wasClosed = true
      def onMessage(msg: PickleType): Unit = {
        deserializer.deserialize(msg) match {
          case Right(CallResponse(seqId, result: Either[Failure@unchecked, Payload@unchecked])) =>
            callRequests.get(seqId).foreach(_ trySuccess result)
          case Right(Notification(events: List[Event@unchecked])) =>
            handler.onEvents(events)
          case Right(Pong()) =>
            // do nothing
          case Left(error) =>
            scribe.warn(s"Ignoring message. Deserializer failed: $error")
        }
      }
    })

    new WebsocketClient(ws, callRequests)
  }
}

object WebsocketClient {
  def apply[PickleType, Event, Failure](
    connection: WebsocketConnectionRunner[PickleType],
    config: ClientConfig,
    handler: IncidentHandler[Event])(implicit
    serializer: Serializer[ClientMessage[PickleType], PickleType],
    deserializer: Deserializer[ServerMessage[PickleType, Event, Failure], PickleType]) =
    withPayload[PickleType, PickleType, Event, Failure](connection, config, handler)

  def withPayload[PickleType, Payload, Event, Failure](
    connection: WebsocketConnectionRunner[PickleType],
    config: ClientConfig,
    handler: IncidentHandler[Event])(implicit
    serializer: Serializer[ClientMessage[Payload], PickleType],
    deserializer: Deserializer[ServerMessage[Payload, Event, Failure], PickleType]) = {
    val callRequests = new OpenRequests[Either[Failure, Payload]](config.requestTimeoutMillis)
    new WebsocketClientRunner(connection, handler, callRequests)
  }
}
