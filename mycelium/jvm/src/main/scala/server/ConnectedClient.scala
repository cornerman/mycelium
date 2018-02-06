package mycelium.server

import akka.actor._
import mycelium.core.message._

import scala.concurrent.Future

sealed trait DisconnectReason
object DisconnectReason {
  case object Stopped extends DisconnectReason
  case object Killed extends DisconnectReason
  case class StateFailed(failure: Throwable) extends DisconnectReason
}

class NotifiableClient[Event](actor: ActorRef) {
  private[mycelium] case class Notify(event: List[Event])
  def notify(events: List[Event]): Unit = actor ! Notify(events)
}

private[mycelium] class ConnectedClient[Payload, Event, Failure, State](
  handler: RequestHandler[Payload, Event, Failure, State]) extends Actor {
  import ConnectedClient._
  import handler._
  import context.dispatcher

  def connected(outgoing: ActorRef) = {
    val client = new NotifiableClient[Event](self)
    def sendEvents(events: Seq[Event]): Unit = if (events.nonEmpty) outgoing ! Notification(events.toList)
    def stopActor(state: Future[State], reason: DisconnectReason): Unit = {
      onClientDisconnect(client, state, reason)
      context.stop(self)
    }
    def safeWithState(state: Future[State]): Receive = {
      state.failed.foreach { t =>
        stopActor(state, DisconnectReason.StateFailed(t))
      }
      withState(state)
    }
    def withState(state: Future[State]): Receive = {
      case Ping() => outgoing ! Pong()

      case CallRequest(seqId, path, args: Payload@unchecked) =>
        val response = onRequest(client, state, path, args)
        response.value.foreach { value =>
          outgoing ! CallResponse(seqId, value.result)
          sendEvents(value.events)
        }
        context.become(safeWithState(response.state))

      case client.Notify(notifyEvents) =>
        val reaction = onEvent(client, state, notifyEvents)
        reaction.events.foreach(sendEvents)
        context.become(safeWithState(reaction.state))

      case Stop => stopActor(state, DisconnectReason.Stopped)
    }

    val firstState = initialState
    onClientConnect(client, firstState)
    safeWithState(firstState)
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
