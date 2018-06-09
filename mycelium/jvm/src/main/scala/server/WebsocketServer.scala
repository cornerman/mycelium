package mycelium.server

import akka.actor.ActorSystem
import akka.stream.OverflowStrategy
import chameleon._
import monix.execution.{Scheduler => MonixScheduler}
import mycelium.core._
import mycelium.core.message._

case class WebsocketServerConfig(bufferSize: Int, overflowStrategy: OverflowStrategy)
class WebsocketServer(val flow: () => WebsocketServerFlow.Type)
object WebsocketServer {
  def apply[PickleType, Failure, State](
    config: WebsocketServerConfig,
    handler: RequestHandler[PickleType, Failure, State])(implicit
    system: ActorSystem,
    scheduler: MonixScheduler,
    serializer: Serializer[ServerMessage[PickleType, Failure], PickleType],
    deserializer: Deserializer[ClientMessage[PickleType], PickleType],
    builder: AkkaMessageBuilder[PickleType]): WebsocketServer =
    withPayload[PickleType, PickleType, Failure, State](config, handler)

  def withPayload[PickleType, Payload, Failure, State](
    config: WebsocketServerConfig,
    handler: RequestHandler[Payload, Failure, State])(implicit
    system: ActorSystem,
    scheduler: MonixScheduler,
    serializer: Serializer[ServerMessage[Payload, Failure], PickleType],
    deserializer: Deserializer[ClientMessage[Payload], PickleType],
    builder: AkkaMessageBuilder[PickleType]): WebsocketServer =
    new WebsocketServer(() => WebsocketServerFlow(config, handler))
}
