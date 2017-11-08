package mycelium.client

import mycelium.core._
import mycelium.core.message._
import akka.actor.ActorSystem

trait NativeWebsocketClient {
  def apply[PickleType : AkkaMessageBuilder, Event, Failure](
    config: ClientConfig,
    handler: IncidentHandler[Event])(implicit
    system: ActorSystem,
    writer: Writer[ClientMessage[PickleType], PickleType],
    reader: Reader[ServerMessage[PickleType, Event, Failure], PickleType]) =
      withPayload[PickleType, PickleType, Event, Failure](config, handler)

  def withPayload[PickleType : AkkaMessageBuilder, Payload, Event, Failure](
    config: ClientConfig,
    handler: IncidentHandler[Event])(implicit
    system: ActorSystem,
    writer: Writer[ClientMessage[Payload], PickleType],
    reader: Reader[ServerMessage[Payload, Event, Failure], PickleType]) =
      WebsocketClient.factory(new AkkaWebsocketConnection, config, handler)
}
