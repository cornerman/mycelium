package mycelium.client

import mycelium.core._
import mycelium.core.message._

trait NativeWebsocketClient {
  def apply[PickleType : JsMessageBuilder, Payload, Event, Failure](
    config: ClientConfig,
    handler: IncidentHandler[Event])(implicit
    writer: Writer[ClientMessage[Payload], PickleType],
    reader: Reader[ServerMessage[Payload, Event, Failure], PickleType]) =
      WebsocketClientFactory(new JsWebsocketConnection, config, handler)
}
