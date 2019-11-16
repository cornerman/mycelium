package mycelium.server

import akka.actor.ActorSystem
import akka.stream.OverflowStrategy
import chameleon._
import monix.execution.{Scheduler => MonixScheduler}
import mycelium.core._
import mycelium.core.message._

case class WebsocketServerConfig(bufferSize: Int, overflowStrategy: OverflowStrategy, parallelism: Int)
class WebsocketServer(val flow: () => WebsocketServerFlow.Type)
object WebsocketServer {
  def apply[PickleType, ErrorType, State](
    config: WebsocketServerConfig,
    handler: RequestHandler[PickleType, ErrorType, State])(implicit
    system: ActorSystem,
    scheduler: MonixScheduler,
    serializer: Serializer[ServerMessage[PickleType, ErrorType], PickleType],
    deserializer: Deserializer[ClientMessage[PickleType], PickleType],
    builder: AkkaMessageBuilder[PickleType]): WebsocketServer =
    withPayload[PickleType, PickleType, ErrorType, State](config, handler)

  def withPayload[PickleType, Payload, ErrorType, State](
    config: WebsocketServerConfig,
    handler: RequestHandler[Payload, ErrorType, State])(implicit
    system: ActorSystem,
    scheduler: MonixScheduler,
    serializer: Serializer[ServerMessage[Payload, ErrorType], PickleType],
    deserializer: Deserializer[ClientMessage[Payload], PickleType],
    builder: AkkaMessageBuilder[PickleType]): WebsocketServer =
    new WebsocketServer(() => WebsocketServerFlow(config, handler))
}
