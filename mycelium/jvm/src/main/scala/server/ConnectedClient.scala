package mycelium.server

import akka.actor.{Actor, ActorRef}
import monix.execution.cancelables.CompositeCancelable
import monix.execution.{Scheduler => MonixScheduler}
import mycelium.core.message._

import scala.concurrent.Future
import scala.util.{Failure, Success}

sealed trait DisconnectReason
object DisconnectReason {
  case object Stopped extends DisconnectReason
  case object Killed extends DisconnectReason
  case class StateFailed(failure: Throwable) extends DisconnectReason
}

class NotifiableClient[Event](actor: ActorRef, downstreamActor: ActorRef) {
  private[mycelium] case class Notify(events: List[Event])
  def notifyWithReaction(events: List[Event]): Unit = actor ! Notify(events)
  def notify(events: List[Event]): Unit = if (events.nonEmpty) downstreamActor ! Notification(events)
}

private[mycelium] class ConnectedClient[Payload, Event, Failure, State](
  handler: RequestHandler[Payload, Event, Failure, State])(implicit scheduler: MonixScheduler) extends Actor {
  import ConnectedClient._
  import handler._

  def connected(outgoing: ActorRef) = {
    val cancelables = CompositeCancelable()
    val client = new NotifiableClient[Event](self, outgoing)
    def stopActor(state: Future[State], reason: DisconnectReason): Unit = {
      onClientDisconnect(client, state, reason)
      cancelables.cancel()
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
        response.value match {
          case EventualResult.Single(future) =>
            future.onComplete {
              case Success(value) => outgoing ! SingleResponse(seqId, value)
              case Failure(t) => outgoing ! ErrorResponse(seqId, t.getMessage)
            }
          case EventualResult.Stream(observable) =>
            cancelables += observable
              .map(StreamResponse(seqId, _))
              .endWith(StreamCloseResponse(seqId) :: Nil)
              .doOnError(t => outgoing ! ErrorResponse(seqId, t.getMessage))
              .foreach(outgoing ! _)
        }
        context.become(safeWithState(response.state))

      case client.Notify(events) =>
        val newState = if (events.nonEmpty) {
          val reaction = onEvent(client, state, events)
          reaction.events.foreach { events =>
            if (events.nonEmpty) outgoing ! Notification(events)
          }
          reaction.state
        } else state
        context.become(safeWithState(newState))

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
