package mycelium.server

import mycelium.core._
import mycelium.core.message._

import akka.NotUsed
import akka.actor._
import akka.http.scaladsl.model.ws.Message
import akka.stream.scaladsl._

object WebsocketServerFlow {
  def apply[Encoder[_], Decoder[_], PickleType, Payload, Event, PublishEvent, Failure, State](
    config: ServerConfig,
    handler: RequestHandler[Payload, Event, PublishEvent, Failure, State])(implicit
    system: ActorSystem,
    encoder: Encoder[ServerMessage[Payload, Event, Failure]],
    decoder: Decoder[ClientMessage[Payload]],
    serializer: Serializer[Encoder, Decoder, PickleType],
    builder: AkkaMessageBuilder[PickleType]): Flow[Message, Message, NotUsed] = {
    import config._

    val connectedClientActor = system.actorOf(Props(new ConnectedClient(handler)))

    val incoming: Sink[Message, NotUsed] =
      Flow[Message].mapConcat {
        case m: Message =>
          val result = for {
            value <- builder.unpack(m).toRight(s"Cannot handle message: $m")
            msg <- serializer.deserialize[ClientMessage[Payload]](value).left.map(_.getMessage)
          } yield msg

          result match {
            case Right(r) => List(r)
            case Left(t) =>
              //TODO log error
              List.empty
          }
      }.to(Sink.actorRef[ClientMessage[Payload]](connectedClientActor, ConnectedClient.Stop))

    val outgoing: Source[Message, NotUsed] =
      Source.actorRef[ServerMessage[Payload, Event, Failure]](flowConfig.bufferSize, flowConfig.overflowStrategy)
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
