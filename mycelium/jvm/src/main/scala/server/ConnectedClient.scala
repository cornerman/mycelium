package mycelium.server

import akka.actor.{Actor, ActorRef}
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

class NotifiableClient[Event, State](actor: ActorRef) {
  private[mycelium] case class Notify(event: Future[State] => Future[List[Event]])
  def notify(eventsf: Future[State] => Future[List[Event]]): Unit = actor ! Notify(eventsf)
}

private[mycelium] class ConnectedClient[Payload, Event, Failure, State](
  handler: RequestHandler[Payload, Event, Failure, State])(implicit scheduler: MonixScheduler) extends Actor {
  import ConnectedClient._
  import handler._

  def connected(outgoing: ActorRef) = {
    val client = new NotifiableClient[Event,State](self)
    def sendEvents(events: List[Event]): Unit = if (events.nonEmpty) outgoing ! Notification(events)
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
        response.value match {
          case EventualResult.Single(future) =>
            future.foreach {value =>
                outgoing ! SingleResponse(seqId, value.result)
                sendEvents(value.events)
            }
            // future.onComplete {
            //   case Success(value) =>
            //     outgoing ! SingleResponse(seqId, value.result)
            //     sendEvents(value.events)
            //   case Failure(t) => //outgoing ! ErrorResponse(seqId, t.getMessage)
            // }
            // future.onComplete { _.foreach{ value =>
            //     outgoing ! SingleResponse(seqId, value.result)
            //     sendEvents(value.events)
            // }}
          case EventualResult.Stream(observable) =>
            observable.foreach { value =>
              outgoing ! StreamResponse(seqId, value.result)
              sendEvents(value.events)
            }
            observable.completedL.runAsync.onComplete {
              case Success(_) => outgoing ! StreamCloseResponse(seqId)
              case Failure(t) => outgoing ! ErrorResponse(seqId, t.getMessage)

            }
        }
        context.become(safeWithState(response.state))

      case client.Notify(notifyEvents) =>
        val newState = notifyEvents(state).flatMap { events =>
          if (events.nonEmpty) {
            val reaction = onEvent(client, state, events)
            reaction.events.foreach(sendEvents)
            reaction.state
          } else state
        }
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
