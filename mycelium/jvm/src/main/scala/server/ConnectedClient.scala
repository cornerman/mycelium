package mycelium.server

import akka.actor.{Actor, ActorRef}
import monix.execution.{Scheduler => MonixScheduler}
import monix.execution.cancelables.CompositeCancelable
import monix.reactive.Observer
import monix.reactive.subjects.PublishSubject
import mycelium.core.message._

import scala.concurrent.Future
import scala.util.{Failure, Success}

sealed trait DisconnectReason
object DisconnectReason {
  case object Stopped extends DisconnectReason
  case object Killed extends DisconnectReason
  case class StateFailed(failure: Throwable) extends DisconnectReason
}

sealed trait DownstreamEvent[Event] extends Any
object DownstreamEvent {
  case class Notify[Event](events: List[Event]) extends AnyVal with DownstreamEvent[Event]
  case class NotifyWithReaction[Event](events: List[Event]) extends AnyVal with DownstreamEvent[Event]
}

class Client[Event](id: Int, observer: Observer[DownstreamEvent[Event]])

private[mycelium] class ConnectedClient[Payload, Event, Failure, State](
  handler: RequestHandler[Payload, Event, Failure, State])(implicit scheduler: MonixScheduler) extends Actor {
  import ConnectedClient._
  import handler._

  def connected(outgoing: ActorRef) = {
    val cancelables = CompositeCancelable()
    val clientSubject = PublishSubject[DownstreamEvent[Event]]()
    val client = new Client[Event](self.hashCode, clientSubject)
    cancelables += clientSubject.foreach {
      case DownstreamEvent.Notify(events) => if (events.nonEmpty) outgoing ! Notification(events)
      case DownstreamEvent.NotifyWithReaction(events) => if (events.nonEmpty) self ! ActorNotification(events)
    }

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

<<<<<<< Updated upstream
            }
          case EventualResult.Stream(observable) =>
            cancelables += observable
              .map(StreamResponse(seqId, _))
              .endWith(StreamCloseResponse(seqId) :: Nil)
              .doOnError(t => outgoing ! ErrorResponse(seqId, t.getMessage))
              .foreach(outgoing ! _)
||||||| merged common ancestors
            }
=======
          case EventualResult.Stream(observable) =>
            cancelables += observable
              .map(StreamResponse(seqId, _))
              .endWith(StreamCloseResponse(seqId) :: Nil)
              .doOnError(t => outgoing ! ErrorResponse(seqId, t.getMessage))
              .foreach(outgoing ! _)
>>>>>>> Stashed changes
        }
        context.become(safeWithState(response.state))

      case ActorNotification(events: List[Event]@unchecked) =>
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
  case class ActorNotification[Event](events: List[Event])
  case object Stop
}
