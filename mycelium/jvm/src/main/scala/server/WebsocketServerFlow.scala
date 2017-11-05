package mycelium.server

import mycelium.core._
import mycelium.core.message._

import akka.NotUsed
import akka.actor._
import akka.http.scaladsl.model.ws.Message
import akka.stream.scaladsl._

object WebsocketServerFlow {
  type Type = Flow[Message, Message, NotUsed]

  def apply[PickleType, Event, PublishEvent, Failure, State](
    config: ServerConfig,
    handler: RequestHandler[PickleType, Event, PublishEvent, Failure, State])(implicit
    system: ActorSystem,
    writer: Writer[ServerMessage[PickleType, Event, Failure], PickleType],
    reader: Reader[ClientMessage[PickleType], PickleType],
    builder: AkkaMessageBuilder[PickleType]): Type =
      withPayload[PickleType, PickleType, Event, PublishEvent, Failure, State](config, handler)

  def withPayload[PickleType, Payload, Event, PublishEvent, Failure, State](
    config: ServerConfig,
    handler: RequestHandler[Payload, Event, PublishEvent, Failure, State])(implicit
    system: ActorSystem,
    writer: Writer[ServerMessage[Payload, Event, Failure], PickleType],
    reader: Reader[ClientMessage[Payload], PickleType],
    builder: AkkaMessageBuilder[PickleType]): Type = {

    val connectedClientActor = system.actorOf(Props(new ConnectedClient(handler)))

    val incoming: Sink[Message, NotUsed] =
      Flow[Message].mapConcat {
        case m: Message =>
          val result = for {
            value <- builder.unpack(m).toRight(s"Unsupported '$m'")
            msg <- reader.read(value).left.map(_.getMessage)
          } yield msg

          result match {
            case Right(res) =>
              res :: Nil
            case Left(err) =>
              scribe.warn(s"Error reading incoming websocket message: $err")
              Nil
          }
      }.to(Sink.actorRef[ClientMessage[Payload]](connectedClientActor, ConnectedClient.Stop))

    val outgoing: Source[Message, NotUsed] =
      Source.actorRef[ServerMessage[Payload, Event, Failure]](config.flow.bufferSize, config.flow.overflowStrategy)
        .mapMaterializedValue { outActor =>
          connectedClientActor ! ConnectedClient.Connect(outActor)
          NotUsed
        }.map { msg =>
          val value = writer.write(msg)
          builder.pack(value)
        }

    Flow.fromSinkAndSource(incoming, outgoing)
  }
}
