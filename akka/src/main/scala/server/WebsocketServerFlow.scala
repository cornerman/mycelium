package mycelium.akka.server

import mycelium.akka.core.AkkaMessageBuilder
import mycelium.core.message._
import chameleon._

import akka.NotUsed
import akka.actor._
import akka.http.scaladsl.model.ws.Message
import akka.stream.scaladsl._
import scala.util.control.NonFatal

object WebsocketServerFlow {
  type Type = Flow[Message, Message, NotUsed]

  def apply[PickleType, Payload, Event, Failure, State](
      config: WebsocketServerConfig,
      handler: RequestHandler[Payload, Event, Failure, State],
  )(implicit
      system: ActorSystem,
      serializer: Serializer[
        ServerMessage[Payload, Event, Failure],
        PickleType,
      ],
      deserializer: Deserializer[ClientMessage[Payload], PickleType],
      builder: AkkaMessageBuilder[PickleType],
  ): Type = {
    import system.dispatcher

    val connectedClientActor =
      system.actorOf(Props(new ConnectedClient(handler)))

    @annotation.nowarn("cat=deprecation")
    val incoming: Sink[Message, NotUsed] =
      Flow[Message]
        .mapAsync(parallelism = 1) { case m: Message =>
          builder
            .unpack(m)
            .map(_.toRight(s"Builder does not support message: $m"))
            .recover { case NonFatal(t) =>
              Left(s"Builder threw exception: $t")
            }
        }
        .mapConcat { unpackedValue =>
          val result = for {
            value <- unpackedValue.right
            msg <- deserializer
              .deserialize(value)
              .left
              .map(t => s"Deserializer failed: $t")
              .right
          } yield msg

          result match {
            case Right(res) =>
              res :: Nil
            case Left(err) =>
              scribe.warn(s"Ignoring websocket message. $err")
              Nil
          }
        }
        .to(
          Sink.actorRef[ClientMessage[Payload]](
            connectedClientActor,
            ConnectedClient.Stop,
          ),
        )

    @annotation.nowarn("cat=deprecation")
    val outgoing: Source[Message, NotUsed] =
      Source
        .actorRef[ServerMessage[Payload, Event, Failure]](
          config.bufferSize,
          config.overflowStrategy,
        )
        .mapMaterializedValue { outActor =>
          connectedClientActor ! ConnectedClient.Connect(outActor)
          NotUsed
        }
        .map { msg =>
          val value = serializer.serialize(msg)
          builder.pack(value)
        }

    Flow.fromSinkAndSource(incoming, outgoing)
  }
}
