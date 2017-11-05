package mycelium.client

import mycelium.core._
import mycelium.core.message._

import scala.concurrent.{ ExecutionContext, Future }

class WebsocketClient[PickleType, Payload, Event, Failure](
  ws: WebsocketConnection[PickleType],
  handler: IncidentHandler[Event],
  requestTimeoutMillis: Int)(implicit
  writer: Writer[ClientMessage[Payload], PickleType],
  reader: Reader[ServerMessage[Payload, Event, Failure], PickleType]) {

  private val callRequests = new OpenRequests[Either[Failure, Payload]](requestTimeoutMillis)

  def send(path: List[String], payload: Payload)(implicit ec: ExecutionContext): Future[Either[Failure, Payload]] = {
    val (id, promise) = callRequests.open()

    val request = CallRequest(id, path, payload)
    val serialized = writer.write(request)
    ws.send(serialized)

    promise.future
  }

  def run(location: String): Unit = ws.run(location, new WebsocketListener[PickleType] {
    private var wasClosed = false
    def onConnect() = handler.onConnect(wasClosed)
    def onClose() = wasClosed = true
    def onMessage(msg: PickleType): Unit = {
      reader.read(msg) match {
        case Right(CallResponse(seqId, result: Either[Failure@unchecked, Payload@unchecked])) =>
          callRequests.get(seqId).foreach(_ trySuccess result)
        case Right(Notification(events: List[Event@unchecked])) =>
          handler.onEvents(events)
        case Right(Pong()) =>
          // do nothing
        case Left(error) =>
          scribe.warn(s"Error reading incoming websocket message: ${error.getMessage}")
      }
    }
  })
}

object WebsocketClient extends NativeWebsocketClient

private[mycelium] object WebsocketClientFactory {
  def apply[PickleType, Payload, Event, Failure](
    connection: WebsocketConnection[PickleType],
    config: ClientConfig,
    handler: IncidentHandler[Event])(implicit
    writer: Writer[ClientMessage[Payload], PickleType],
    reader: Reader[ServerMessage[Payload, Event, Failure], PickleType]) = {
      val wrapper =
        { conn: WebsocketConnection[PickleType] =>
          config.ping.fold(conn)(c => WebsocketConnection.withPing(conn)(c.timeoutMillis))
        } andThen
        { conn: WebsocketConnection[PickleType] =>
          config.reconnect.fold(conn)(c => WebsocketConnection.withReconnect(conn)(c.minimumBackoffMillis))
        }

      new WebsocketClient(wrapper(connection), handler, config.request.timeoutMillis)
    }
}
