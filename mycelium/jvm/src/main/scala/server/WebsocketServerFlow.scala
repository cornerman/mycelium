package mycelium.server

import akka.NotUsed
import akka.actor._
import akka.http.scaladsl.model.ws.Message
import akka.stream.scaladsl._
import chameleon._
import monix.execution.{Scheduler => MonixScheduler}
import mycelium.core._
import mycelium.core.message._

import scala.util.control.NonFatal

object WebsocketServerFlow {
  type Type = Flow[Message, Message, NotUsed]

  def apply[PickleType, Payload, Failure, State](
    config: WebsocketServerConfig,
    handler: RequestHandler[Payload, Failure, State])(implicit
    system: ActorSystem,
    scheduler: MonixScheduler,
    serializer: Serializer[ServerMessage[Payload, Failure], PickleType],
    deserializer: Deserializer[ClientMessage[Payload], PickleType],
    builder: AkkaMessageBuilder[PickleType]): Type = {

    val connectedClientActor = system.actorOf(Props(new ConnectedClient(handler)))

    val incoming: Sink[Message, NotUsed] =
      Flow[Message].mapAsync(parallelism = config.parallelism) {
        case m: Message =>
          builder.unpack(m)
            .map {
              case Some(m) => deserializer.deserialize(m).left.map(t => s"Deserializer failed: $t")
              case None => Left(s"Builder does not support message: $m")
            }
            .recover { case NonFatal(t) => Left(s"Builder threw exception: $t") }
      }.mapConcat {
        case Right(msg) => msg :: Nil
        case Left(err) =>
          scribe.warn(s"Ignoring incoming websocket message. $err")
          Nil
      }.to(Sink.actorRef[ClientMessage[Payload]](connectedClientActor, ConnectedClient.Stop))

    val outgoing: Source[Message, NotUsed] =
      Source.actorRef[ServerMessage[Payload, Failure]](config.bufferSize, config.overflowStrategy)
        .mapMaterializedValue { outActor =>
          connectedClientActor ! ConnectedClient.Connect(outActor)
          NotUsed
        }.map { msg =>
          val value = serializer.serialize(msg)
          builder.pack(value)
        }

    Flow.fromSinkAndSource(incoming, outgoing)
  }
}
