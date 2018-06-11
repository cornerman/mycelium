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

case class ClientId(id: Int) extends AnyVal {
  override def toString = s"Client(${Integer.toString(client.hashCode, 36)})"
}

private[mycelium] class ConnectedClient[Payload, Failure, State](
  handler: RequestHandler[Payload, Failure, State])(implicit scheduler: MonixScheduler) extends Actor {
  import ConnectedClient._
  import handler._

  //TODO: observable state?
  def connected(outgoing: ActorRef) = {
    val cancelables = CompositeCancelable()
    val clientId = new ClientId(self.hashCode)
    def stopActor(state: Future[State], reason: DisconnectReason): Unit = {
      onClientDisconnect(clientId, state, reason)
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
        val response = onRequest(clientId, state, path, args)
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

      case Stop => stopActor(state, DisconnectReason.Stopped)
    }

    val firstState = initialState
    onClientConnect(clientId, firstState)
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
