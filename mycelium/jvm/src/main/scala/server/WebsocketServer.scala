package mycelium.server

import mycelium.core._
import mycelium.core.message._

import akka.actor.ActorSystem

class WebsocketServer(createFlow: () => WebsocketServerFlow.Type) {
  def flow() = createFlow()
}
object WebsocketServer {
  def apply[PickleType, Event, PublishEvent, Failure, State](
    config: ServerConfig,
    handler: RequestHandler[PickleType, Event, PublishEvent, Failure, State])(implicit
    system: ActorSystem,
    writer: Writer[ServerMessage[PickleType, Event, Failure], PickleType],
    reader: Reader[ClientMessage[PickleType], PickleType],
    builder: AkkaMessageBuilder[PickleType]): WebsocketServer =
    withPayload[PickleType, PickleType, Event, PublishEvent, Failure, State](config, handler)

  def withPayload[PickleType, Payload, Event, PublishEvent, Failure, State](
    config: ServerConfig,
    handler: RequestHandler[Payload, Event, PublishEvent, Failure, State])(implicit
    system: ActorSystem,
    writer: Writer[ServerMessage[Payload, Event, Failure], PickleType],
    reader: Reader[ClientMessage[Payload], PickleType],
    builder: AkkaMessageBuilder[PickleType]): WebsocketServer =
    new WebsocketServer(() => WebsocketServerFlow(config, handler))
}
