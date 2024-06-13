package mycelium.pekko.server

import mycelium.pekko.core.PekkoMessageBuilder
import mycelium.core.message._
import chameleon._

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.OverflowStrategy

case class WebsocketServerConfig(
    bufferSize: Int,
    overflowStrategy: OverflowStrategy,
)
class WebsocketServer(val flow: () => WebsocketServerFlow.Type)
object WebsocketServer {
  def apply[PickleType, Event, Failure, State](
      config: WebsocketServerConfig,
      handler: RequestHandler[PickleType, Event, Failure, State],
  )(implicit
      system: ActorSystem,
      serializer: Serializer[
        ServerMessage[PickleType, Event, Failure],
        PickleType,
      ],
      deserializer: Deserializer[ClientMessage[PickleType], PickleType],
      builder: PekkoMessageBuilder[PickleType],
  ): WebsocketServer =
    withPayload[PickleType, PickleType, Event, Failure, State](config, handler)

  def withPayload[PickleType, Payload, Event, Failure, State](
      config: WebsocketServerConfig,
      handler: RequestHandler[Payload, Event, Failure, State],
  )(implicit
      system: ActorSystem,
      serializer: Serializer[
        ServerMessage[Payload, Event, Failure],
        PickleType,
      ],
      deserializer: Deserializer[ClientMessage[Payload], PickleType],
      builder: PekkoMessageBuilder[PickleType],
  ): WebsocketServer =
    new WebsocketServer(() => WebsocketServerFlow(config, handler))
}
