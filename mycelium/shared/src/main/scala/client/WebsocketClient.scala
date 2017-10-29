package mycelium.client

import mycelium.core._
import mycelium.core.message._

import scala.concurrent.{ ExecutionContext, Future }

class WebsocketClient[Encoder[_], Decoder[_], PickleType, Payload, Event, Failure, State](
  ws: WebsocketConnection[PickleType])(implicit
  ec: ExecutionContext,
  encoder: Encoder[ClientMessage[Payload]],
  decoder: Decoder[ServerMessage[Payload, Event, Failure]],
  serializer: Serializer[Encoder, Decoder, PickleType]) {

  private val callRequests = new OpenRequests[Either[Failure, Payload]]

  def send(path: List[String], payload: Payload): Future[Either[Failure, Payload]] = {
    val (id, promise) = callRequests.open()

    val request = CallRequest(id, path, payload)
    val serialized = serializer.serialize[ClientMessage[Payload]](request)
    ws.send(serialized)

    promise.future
  }

  def run(location: String, handler: IncidentHandler[Event, Failure]): Unit = ws.run(location, new WebsocketListener[PickleType] {
    private var wasClosed = false
    def onConnect() = handler.onConnect(wasClosed)
    def onClose() = wasClosed = true
    def onMessage(msg: PickleType): Unit = {
      serializer.deserialize[ServerMessage[Payload, Event, Failure]](msg) match {
        case Right(CallResponse(seqId, result: Either[Failure@unchecked, Payload@unchecked])) =>
          callRequests.get(seqId).foreach(_ trySuccess result)
        case Right(Notification(events: List[Event@unchecked])) =>
          handler.onEvents(events)
        case Right(Pong()) =>
          // do nothing
        case Left(error) =>
          //TODO: log error
      }
    }
  })
}
