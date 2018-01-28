package mycelium.server

import mycelium.core._
import mycelium.core.message._
import chameleon._

import akka.actor.ActorSystem

class WebsocketServer(val flow: () => WebsocketServerFlow.Type)
object WebsocketServer {
  def apply[PickleType, Event, PublishEvent, Failure, State](
    config: ServerConfig,
    handler: RequestHandler[PickleType, Event, PublishEvent, Failure, State])(implicit
    system: ActorSystem,
    serializer: Serializer[ServerMessage[PickleType, Event, Failure], PickleType],
    deserializer: Deserializer[ClientMessage[PickleType], PickleType],
    builder: AkkaMessageBuilder[PickleType]): WebsocketServer =
    withPayload[PickleType, PickleType, Event, PublishEvent, Failure, State](config, handler)

  def withPayload[PickleType, Payload, Event, PublishEvent, Failure, State](
    config: ServerConfig,
    handler: RequestHandler[Payload, Event, PublishEvent, Failure, State])(implicit
    system: ActorSystem,
    serializer: Serializer[ServerMessage[Payload, Event, Failure], PickleType],
    deserializer: Deserializer[ClientMessage[Payload], PickleType],
    builder: AkkaMessageBuilder[PickleType]): WebsocketServer =
    new WebsocketServer(() => WebsocketServerFlow(config, handler))
}
