package mycelium.server

import akka.actor._
import akka.pattern.pipe
import mycelium.core.message._

import scala.concurrent.Future

class NotifiableClient[PublishEvent](actor: ActorRef) {
  private[mycelium] case class Notify(event: PublishEvent)
  def notify(event: PublishEvent): Unit = actor ! Notify(event)
}

private[mycelium] class ConnectedClient[Payload, Event, PublishEvent, Failure, State](
  handler: RequestHandler[Payload, Event, PublishEvent, Failure, State]) extends Actor {
  import ConnectedClient._
  import handler._
  import context.dispatcher

  def connected(outgoing: ActorRef) = {
    val client = new NotifiableClient[PublishEvent](self)
    def sendEvents(events: Seq[Event]): Unit = if (events.nonEmpty) outgoing ! Notification(events.toList)
    def stopActor(state: Future[State], reason: DisconnectReason): Unit = {
      onClientDisconnect(client, state, reason)
      context.stop(self)
    }
    def react(reaction: HandlerReaction[Event, State]): Receive = {
      reaction.state.failed.foreach { t =>
        stopActor(reaction.state, DisconnectReason.StateFailed(t))
      }

      reaction.events.foreach(sendEvents)
      withState(reaction.state)
    }

    def withState(state: Future[State]): Receive = {
      case Ping() => outgoing ! Pong()

      case CallRequest(seqId, path, args: Payload@unchecked) =>
        val response = onRequest(client, state, path, args)
        response.result.map(r => CallResponse(seqId, r)).pipeTo(outgoing)
        context.become(react(response.reaction))

      case client.Notify(event) =>
        val reaction = onEvent(client, state, event)
        context.become(react(reaction))

      case Stop => stopActor(state, DisconnectReason.Stopped)
    }

    val initial = initialReaction
    onClientConnect(client, initial.state)
    react(initial)
  }

  def receive = {
    case Connect(outgoing) => context.become(connected(outgoing))
    case Stop => context.stop(self)
  }
}
private[mycelium] object ConnectedClient {
  case class Connect(actor: ActorRef)
  case object Stop
}
